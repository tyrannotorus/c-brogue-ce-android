package org.broguece.game;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable snapshot of a player's run history for this install. Persisted
 *  as stats.json in the app's private files dir. All "with…" methods return
 *  a new instance with the update applied so this class is safe to publish
 *  across threads via a volatile reference. */
final class PlayerStats {

    /** Max distinct seeds retained in the "Recent Seeds" list. */
    static final int RECENT_SEEDS_MAX = 10;

    /** Depth bounds for the deaths-per-depth histogram. Brogue has 26 regular
     *  depths; deeper indices are reserved for Amulet-run descent levels. 40
     *  is a conservative upper bound — anything beyond is clamped on ingest. */
    static final int MAX_DEPTH = 40;

    final int gamesPlayed;
    final int wins;
    final int masteryWins;        // super / mastery victories
    final int deaths;
    final int deepestDepth;
    final int longestRunTurns;
    final int fastestWinTurns;    // 0 while no wins recorded yet
    final int amuletPickups;      // lifetime Amulet-of-Yendor pickups
    /** Sorted by count descending. */
    final List<Tally> kills;
    /** Sorted by count descending. */
    final List<Tally> deathCauses;
    /** Sorted by count descending. */
    final List<Tally> alliesFreed;
    /** Sorted by count descending. Allies killed by enemies — not player-caused. */
    final List<Tally> alliesLost;
    /** Most-recent-first, deduplicated, capped at RECENT_SEEDS_MAX. */
    final List<Long> recentSeeds;
    /** Count of player deaths at each depth. Index 0 unused; valid range 1..MAX_DEPTH. */
    private final int[] deathsPerDepth;

    private PlayerStats(int gamesPlayed, int wins, int masteryWins, int deaths,
                        int deepestDepth, int longestRunTurns, int fastestWinTurns,
                        int amuletPickups,
                        List<Tally> kills, List<Tally> deathCauses,
                        List<Tally> alliesFreed, List<Tally> alliesLost,
                        List<Long> recentSeeds, int[] deathsPerDepth) {
        this.gamesPlayed = gamesPlayed;
        this.wins = wins;
        this.masteryWins = masteryWins;
        this.deaths = deaths;
        this.deepestDepth = deepestDepth;
        this.longestRunTurns = longestRunTurns;
        this.fastestWinTurns = fastestWinTurns;
        this.amuletPickups = amuletPickups;
        this.kills = Collections.unmodifiableList(kills);
        this.deathCauses = Collections.unmodifiableList(deathCauses);
        this.alliesFreed = Collections.unmodifiableList(alliesFreed);
        this.alliesLost = Collections.unmodifiableList(alliesLost);
        this.recentSeeds = Collections.unmodifiableList(recentSeeds);
        this.deathsPerDepth = deathsPerDepth;
    }

    /** Depth with the most deaths. Ties broken by shallowest depth — the
     *  player is *still* dying there, which is more psychologically resonant
     *  than whichever tied depth happens to come later. Returns 0 if no
     *  deaths have been recorded. */
    int deadliestDepth() {
        int bestDepth = 0;
        int bestCount = 0;
        for (int d = 1; d <= MAX_DEPTH; d++) {
            if (deathsPerDepth[d] > bestCount) {
                bestCount = deathsPerDepth[d];
                bestDepth = d;
            }
        }
        return bestDepth;
    }

    int alliesFreedTotal() {
        int sum = 0;
        for (Tally t : alliesFreed) sum += t.count;
        return sum;
    }

    /** Named count — used for kill/death-cause/ally tallies. */
    static final class Tally {
        final String label;
        final int count;
        Tally(String label, int count) {
            this.label = label;
            this.count = count;
        }
    }

    static PlayerStats empty() {
        return new PlayerStats(
            0, 0, 0, 0,
            0, 0, 0,
            0,
            new ArrayList<Tally>(), new ArrayList<Tally>(),
            new ArrayList<Tally>(), new ArrayList<Tally>(),
            new ArrayList<Long>(),
            new int[MAX_DEPTH + 1]);
    }

    /** A "play" is a fresh run — any type (new game, weekly, custom seed,
     *  fun). Resumed saves and playback are not plays. gamesPlayed is only
     *  changed here; terminal events (died/won/quit) leave it untouched. */
    PlayerStats withGameStart() {
        return new PlayerStats(
            gamesPlayed + 1,
            wins, masteryWins, deaths,
            deepestDepth, longestRunTurns, fastestWinTurns, amuletPickups,
            new ArrayList<>(kills),
            new ArrayList<>(deathCauses),
            new ArrayList<>(alliesFreed),
            new ArrayList<>(alliesLost),
            new ArrayList<>(recentSeeds),
            copyDepths(deathsPerDepth));
    }

    PlayerStats withMonsterKilled(String monsterName) {
        return new PlayerStats(
            gamesPlayed, wins, masteryWins, deaths,
            deepestDepth, longestRunTurns, fastestWinTurns, amuletPickups,
            mergeTally(kills, monsterName),
            new ArrayList<>(deathCauses),
            new ArrayList<>(alliesFreed),
            new ArrayList<>(alliesLost),
            new ArrayList<>(recentSeeds),
            copyDepths(deathsPerDepth));
    }

    PlayerStats withAllyFreed(String monsterName) {
        return new PlayerStats(
            gamesPlayed, wins, masteryWins, deaths,
            deepestDepth, longestRunTurns, fastestWinTurns, amuletPickups,
            new ArrayList<>(kills),
            new ArrayList<>(deathCauses),
            mergeTally(alliesFreed, monsterName),
            new ArrayList<>(alliesLost),
            new ArrayList<>(recentSeeds),
            copyDepths(deathsPerDepth));
    }

    PlayerStats withAllyDied(String monsterName) {
        return new PlayerStats(
            gamesPlayed, wins, masteryWins, deaths,
            deepestDepth, longestRunTurns, fastestWinTurns, amuletPickups,
            new ArrayList<>(kills),
            new ArrayList<>(deathCauses),
            new ArrayList<>(alliesFreed),
            mergeTally(alliesLost, monsterName),
            new ArrayList<>(recentSeeds),
            copyDepths(deathsPerDepth));
    }

    PlayerStats withPlayerDied(String killedBy, int depth, int turns) {
        int[] nextDepths = copyDepths(deathsPerDepth);
        if (depth >= 1 && depth <= MAX_DEPTH) {
            nextDepths[depth]++;
        }
        return new PlayerStats(
            gamesPlayed,
            wins, masteryWins,
            deaths + 1,
            Math.max(deepestDepth, depth),
            Math.max(longestRunTurns, turns),
            fastestWinTurns,
            amuletPickups,
            new ArrayList<>(kills),
            mergeTally(deathCauses, killedBy),
            new ArrayList<>(alliesFreed),
            new ArrayList<>(alliesLost),
            new ArrayList<>(recentSeeds),
            nextDepths);
    }

    PlayerStats withPlayerWon(boolean superVictory, int depth, int turns) {
        int nextFastest = (fastestWinTurns == 0 || turns < fastestWinTurns)
            ? turns : fastestWinTurns;
        return new PlayerStats(
            gamesPlayed,
            wins + 1,
            masteryWins + (superVictory ? 1 : 0),
            deaths,
            Math.max(deepestDepth, depth),
            Math.max(longestRunTurns, turns),
            nextFastest,
            amuletPickups,
            new ArrayList<>(kills),
            new ArrayList<>(deathCauses),
            new ArrayList<>(alliesFreed),
            new ArrayList<>(alliesLost),
            new ArrayList<>(recentSeeds),
            copyDepths(deathsPerDepth));
    }

    /** Fires when the player first picks up the Amulet of Yendor in a game.
     *  C-side hook gates on `!rogue.yendorWarden` (the warden is generated
     *  exactly once at pickup) and `!rogue.playbackMode`, so a replay can't
     *  inflate the counter. */
    PlayerStats withAmuletPickedUp() {
        return new PlayerStats(
            gamesPlayed, wins, masteryWins, deaths,
            deepestDepth, longestRunTurns, fastestWinTurns,
            amuletPickups + 1,
            new ArrayList<>(kills),
            new ArrayList<>(deathCauses),
            new ArrayList<>(alliesFreed),
            new ArrayList<>(alliesLost),
            new ArrayList<>(recentSeeds),
            copyDepths(deathsPerDepth));
    }

    /** Adds a seed to the front of `recentSeeds`, removing any prior
     *  occurrence so each seed appears once, then truncates to the cap. */
    PlayerStats withSeedPlayed(long seed) {
        if (seed <= 0) return this;
        List<Long> next = new ArrayList<>(recentSeeds.size() + 1);
        next.add(seed);
        for (Long existing : recentSeeds) {
            if (existing == null || existing == seed) continue;
            next.add(existing);
            if (next.size() >= RECENT_SEEDS_MAX) break;
        }
        return new PlayerStats(
            gamesPlayed, wins, masteryWins, deaths,
            deepestDepth, longestRunTurns, fastestWinTurns, amuletPickups,
            new ArrayList<>(kills),
            new ArrayList<>(deathCauses),
            new ArrayList<>(alliesFreed),
            new ArrayList<>(alliesLost),
            next,
            copyDepths(deathsPerDepth));
    }

    PlayerStats withPlayerQuit() {
        return new PlayerStats(
            gamesPlayed,
            wins, masteryWins, deaths,
            deepestDepth, longestRunTurns, fastestWinTurns, amuletPickups,
            new ArrayList<>(kills),
            new ArrayList<>(deathCauses),
            new ArrayList<>(alliesFreed),
            new ArrayList<>(alliesLost),
            new ArrayList<>(recentSeeds),
            copyDepths(deathsPerDepth));
    }

    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("gamesPlayed", gamesPlayed);
        o.put("wins", wins);
        o.put("masteryWins", masteryWins);
        o.put("deaths", deaths);
        o.put("deepestDepth", deepestDepth);
        o.put("longestRunTurns", longestRunTurns);
        o.put("fastestWinTurns", fastestWinTurns);
        o.put("amuletPickups", amuletPickups);
        o.put("kills", tallyListToJson(kills));
        o.put("deathCauses", tallyListToJson(deathCauses));
        o.put("alliesFreed", tallyListToJson(alliesFreed));
        o.put("alliesLost", tallyListToJson(alliesLost));
        JSONArray seedsArr = new JSONArray();
        for (Long s : recentSeeds) seedsArr.put(s);
        o.put("recentSeeds", seedsArr);
        o.put("deathsPerDepth", deathsPerDepthToJson(deathsPerDepth));
        return o;
    }

    static PlayerStats fromJson(JSONObject o) {
        return new PlayerStats(
            o.optInt("gamesPlayed"),
            o.optInt("wins"),
            o.optInt("masteryWins"),
            o.optInt("deaths"),
            o.optInt("deepestDepth"),
            o.optInt("longestRunTurns"),
            o.optInt("fastestWinTurns"),
            o.optInt("amuletPickups"),
            tallyListFromJson(o.optJSONObject("kills")),
            tallyListFromJson(o.optJSONObject("deathCauses")),
            tallyListFromJson(o.optJSONObject("alliesFreed")),
            tallyListFromJson(o.optJSONObject("alliesLost")),
            recentSeedsFromJson(o.optJSONArray("recentSeeds")),
            deathsPerDepthFromJson(o.optJSONObject("deathsPerDepth")));
    }

    private static JSONObject deathsPerDepthToJson(int[] depths) throws JSONException {
        JSONObject o = new JSONObject();
        for (int d = 1; d <= MAX_DEPTH; d++) {
            if (depths[d] > 0) o.put(Integer.toString(d), depths[d]);
        }
        return o;
    }

    private static int[] deathsPerDepthFromJson(JSONObject o) {
        int[] out = new int[MAX_DEPTH + 1];
        if (o == null) return out;
        Iterator<String> keys = o.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            try {
                int depth = Integer.parseInt(k);
                if (depth >= 1 && depth <= MAX_DEPTH) {
                    out[depth] = Math.max(0, o.optInt(k));
                }
            } catch (NumberFormatException ignored) { }
        }
        return out;
    }

    private static int[] copyDepths(int[] src) {
        int[] out = new int[src.length];
        System.arraycopy(src, 0, out, 0, src.length);
        return out;
    }

    private static List<Long> recentSeedsFromJson(JSONArray arr) {
        List<Long> out = new ArrayList<>();
        if (arr == null) return out;
        int n = Math.min(arr.length(), RECENT_SEEDS_MAX);
        for (int i = 0; i < n; i++) {
            long v = arr.optLong(i, 0L);
            if (v > 0) out.add(v);
        }
        return out;
    }

    private static JSONObject tallyListToJson(List<Tally> tallies) throws JSONException {
        JSONObject o = new JSONObject();
        for (Tally t : tallies) {
            o.put(t.label, t.count);
        }
        return o;
    }

    private static List<Tally> tallyListFromJson(JSONObject o) {
        List<Tally> out = new ArrayList<>();
        if (o == null) return out;
        Iterator<String> keys = o.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            out.add(new Tally(k, o.optInt(k)));
        }
        sortByCountDesc(out);
        return out;
    }

    private static List<Tally> mergeTally(List<Tally> existing, String label) {
        if (label == null || label.isEmpty()) return new ArrayList<>(existing);
        Map<String, Integer> m = new LinkedHashMap<>();
        for (Tally t : existing) m.put(t.label, t.count);
        Integer prev = m.get(label);
        m.put(label, (prev == null ? 0 : prev) + 1);
        List<Tally> out = new ArrayList<>(m.size());
        for (Map.Entry<String, Integer> e : m.entrySet()) {
            out.add(new Tally(e.getKey(), e.getValue()));
        }
        sortByCountDesc(out);
        return out;
    }

    private static void sortByCountDesc(List<Tally> tallies) {
        Collections.sort(tallies, new Comparator<Tally>() {
            @Override
            public int compare(Tally a, Tally b) {
                return Integer.compare(b.count, a.count);
            }
        });
    }
}
