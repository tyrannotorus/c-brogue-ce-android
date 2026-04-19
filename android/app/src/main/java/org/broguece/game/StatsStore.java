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

    private final File statsFile;
    private final Handler handler;
    private volatile PlayerStats current;

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

    void recordGameStart() {
        handler.post(new Runnable() {
            @Override public void run() {
                try {
                    current = current.withGameStart();
                    writeToDisk(current);
                } catch (Throwable t) {
                    Log.w(TAG, "recordGameStart failed", t);
                }
            }
        });
    }

    void recordAllyFreed(final String name) {
        if (name == null || name.isEmpty()) return;
        handler.post(new Runnable() {
            @Override public void run() {
                try {
                    current = current.withAllyFreed(name);
                    writeToDisk(current);
                } catch (Throwable t) {
                    Log.w(TAG, "recordAllyFreed failed", t);
                }
            }
        });
    }

    void recordMonsterKilled(final String name) {
        if (name == null || name.isEmpty()) return;
        handler.post(new Runnable() {
            @Override public void run() {
                try {
                    current = current.withMonsterKilled(name);
                    // Flush every event: if the app is OS-killed mid-run,
                    // brogue's save will replay the run under rogue.playbackMode
                    // and the replay guard correctly skips those events — but
                    // that guarantee only holds if the events were already
                    // persisted. Deferring the flush would silently lose them.
                    writeToDisk(current);
                } catch (Throwable t) {
                    Log.w(TAG, "recordMonsterKilled failed", t);
                }
            }
        });
    }

    void recordPlayerDied(final String killedBy, final int depth, final int turns) {
        handler.post(new Runnable() {
            @Override public void run() {
                try {
                    current = current.withPlayerDied(
                        killedBy == null ? "" : killedBy, depth, turns);
                    writeToDisk(current);
                } catch (Throwable t) {
                    Log.w(TAG, "recordPlayerDied failed", t);
                }
            }
        });
    }

    void recordPlayerWon(final int depth, final int turns) {
        handler.post(new Runnable() {
            @Override public void run() {
                try {
                    current = current.withPlayerWon(depth, turns);
                    writeToDisk(current);
                } catch (Throwable t) {
                    Log.w(TAG, "recordPlayerWon failed", t);
                }
            }
        });
    }

    void recordPlayerQuit() {
        handler.post(new Runnable() {
            @Override public void run() {
                try {
                    current = current.withPlayerQuit();
                    writeToDisk(current);
                } catch (Throwable t) {
                    Log.w(TAG, "recordPlayerQuit failed", t);
                }
            }
        });
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
