package org.broguece.game;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Local-only, per-install player stats. All mutation and disk I/O runs on
 *  a single background HandlerThread so the JNI callbacks from the engine
 *  return in microseconds. Reads are lock-free via a volatile reference to
 *  the immutable PlayerStats snapshot.
 *
 *  Writes are atomic (tmp + rename) so a crash mid-write can't leave the
 *  file corrupt. If the file is unreadable at load time, we start fresh
 *  rather than crash — loss of stats is preferable to a crashed app. */
final class StatsStore {

    private static final String TAG = "BrogueStats";
    private static final String FILENAME = "stats.json";

    private static volatile StatsStore instance;

    static StatsStore get(Context ctx) {
        if (instance == null) {
            synchronized (StatsStore.class) {
                if (instance == null) {
                    instance = new StatsStore(ctx.getApplicationContext());
                }
            }
        }
        return instance;
    }

    /** Invoked each time a record* call promotes an achievement from
     *  locked → earned. Called on StatsStore's background HandlerThread;
     *  implementations must marshal to the UI thread if they touch views. */
    interface UnlockListener {
        void onAchievementUnlocked(Achievement achievement);
    }

    private final File statsFile;
    private final Handler handler;
    private volatile PlayerStats current;
    private volatile UnlockListener unlockListener;

    private StatsStore(Context appContext) {
        this.statsFile = new File(appContext.getFilesDir(), FILENAME);
        HandlerThread thread = new HandlerThread("BrogueStatsIO");
        thread.start();
        this.handler = new Handler(thread.getLooper());
        this.current = loadFromDisk();
    }

    /** UI-thread safe. Returns the most recent immutable snapshot. */
    PlayerStats snapshot() {
        return current;
    }

    /** Set at most one listener for achievement unlock transitions. Pass null
     *  to clear. Listener is invoked on the stats background thread. */
    void setUnlockListener(UnlockListener listener) {
        this.unlockListener = listener;
    }

    /** Functional shape of every PlayerStats mutator — takes the current
     *  snapshot, returns the next. Lets every record* method funnel through
     *  the same publish-and-diff pipeline so no handler can skip the unlock
     *  check by accident. */
    private interface Mutator {
        PlayerStats apply(PlayerStats s);
    }

    private void post(final String tag, final Mutator mutator) {
        handler.post(new Runnable() {
            @Override public void run() {
                try {
                    PlayerStats prev = current;
                    PlayerStats next = mutator.apply(prev);
                    current = next;
                    // Flush every event: if the app is OS-killed mid-run,
                    // brogue's save will replay the run under rogue.playbackMode
                    // and the replay guard correctly skips those events — but
                    // that guarantee only holds if the events were already
                    // persisted. Deferring the flush would silently lose them.
                    writeToDisk(next);
                    fireUnlocks(prev, next);
                } catch (Throwable t) {
                    Log.w(TAG, tag + " failed", t);
                }
            }
        });
    }

    /** Diffs pre- and post-write snapshots for each achievement rule. Any
     *  false → true transition fires the listener. O(rules) per event — six
     *  rules today, trivially cheap. */
    private void fireUnlocks(PlayerStats prev, PlayerStats next) {
        UnlockListener l = unlockListener;
        if (l == null) return;
        List<Achievement> all = AchievementManager.all();
        for (int i = 0, n = all.size(); i < n; i++) {
            Achievement a = all.get(i);
            if (!a.isEarned(prev) && a.isEarned(next)) {
                try {
                    l.onAchievementUnlocked(a);
                } catch (Throwable t) {
                    Log.w(TAG, "unlock listener threw for " + a.id, t);
                }
            }
        }
    }

    void recordGameStart() {
        post("recordGameStart", PlayerStats::withGameStart);
    }

    void recordAllyFreed(final String name) {
        if (name == null || name.isEmpty()) return;
        post("recordAllyFreed", s -> s.withAllyFreed(name));
    }

    void recordAllyDied(final String name) {
        if (name == null || name.isEmpty()) return;
        post("recordAllyDied", s -> s.withAllyDied(name));
    }

    void recordMonsterKilled(final String name) {
        if (name == null || name.isEmpty()) return;
        post("recordMonsterKilled", s -> s.withMonsterKilled(name));
    }

    void recordPlayerDied(final String killedBy, final int depth, final int turns) {
        final String safeKilledBy = killedBy == null ? "" : killedBy;
        post("recordPlayerDied", s -> s.withPlayerDied(safeKilledBy, depth, turns));
    }

    void recordPlayerWon(final boolean superVictory, final int depth, final int turns) {
        post("recordPlayerWon", s -> s.withPlayerWon(superVictory, depth, turns));
    }

    void recordAmuletPickedUp() {
        post("recordAmuletPickedUp", PlayerStats::withAmuletPickedUp);
    }

    void recordSeedPlayed(final long seed) {
        if (seed <= 0) return;
        post("recordSeedPlayed", s -> s.withSeedPlayed(seed));
    }

    void recordPlayerQuit() {
        post("recordPlayerQuit", PlayerStats::withPlayerQuit);
    }

    private PlayerStats loadFromDisk() {
        try {
            if (!statsFile.exists()) return PlayerStats.empty();
            byte[] bytes = new byte[(int) statsFile.length()];
            try (FileInputStream in = new FileInputStream(statsFile)) {
                int read = 0;
                while (read < bytes.length) {
                    int n = in.read(bytes, read, bytes.length - read);
                    if (n < 0) break;
                    read += n;
                }
            }
            String s = new String(bytes, StandardCharsets.UTF_8);
            Object parsed = new JSONTokener(s).nextValue();
            if (!(parsed instanceof JSONObject)) return PlayerStats.empty();
            return PlayerStats.fromJson((JSONObject) parsed);
        } catch (Throwable t) {
            Log.w(TAG, "failed to load " + FILENAME + "; starting fresh", t);
            return PlayerStats.empty();
        }
    }

    private void writeToDisk(PlayerStats stats) {
        File tmp = new File(statsFile.getParentFile(), FILENAME + ".tmp");
        try {
            String json = stats.toJson().toString();
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(json.getBytes(StandardCharsets.UTF_8));
                // fsync the tmp file before renaming — otherwise an OS crash
                // between rename and flush could leave us with a zero-byte file.
                out.getFD().sync();
            }
            if (!tmp.renameTo(statsFile)) {
                Log.w(TAG, FILENAME + " rename failed");
                tmp.delete();
            }
        } catch (Throwable t) {
            Log.w(TAG, "writeToDisk failed", t);
            tmp.delete();
        }
    }
}
