package org.broguece.game;

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

    final int gamesPlayed;
    final int wins;
    final int deaths;
    final int deepestDepth;
    final int longestRunTurns;
    /** Sorted by count descending. */
    final List<Tally> kills;
    /** Sorted by count descending. */
    final List<Tally> deathCauses;
    /** Sorted by count descending. Per-monster in case we later want a
     *  breakdown — the modal currently just sums for the grid cell. */
    final List<Tally> alliesFreed;

    private PlayerStats(int gamesPlayed, int wins, int deaths,
                        int deepestDepth, int longestRunTurns,
                        List<Tally> kills, List<Tally> deathCauses,
                        List<Tally> alliesFreed) {
        this.gamesPlayed = gamesPlayed;
        this.wins = wins;
        this.deaths = deaths;
        this.deepestDepth = deepestDepth;
        this.longestRunTurns = longestRunTurns;
        this.kills = Collections.unmodifiableList(kills);
        this.deathCauses = Collections.unmodifiableList(deathCauses);
        this.alliesFreed = Collections.unmodifiableList(alliesFreed);
    }

    int alliesFreedTotal() {
        int sum = 0;
        for (Tally t : alliesFreed) sum += t.count;
        return sum;
    }

    /** Named count — used for both kill tallies and death-cause tallies. */
    static final class Tally {
        final String label;
        final int count;
        Tally(String label, int count) {
            this.label = label;
            this.count = count;
        }
    }

    static PlayerStats empty() {
        return new PlayerStats(0, 0, 0, 0, 0,
            new ArrayList<Tally>(), new ArrayList<Tally>(), new ArrayList<Tally>());
    }

    /** A "play" is a fresh run — any type (new game, weekly, custom seed,
     *  fun). Resumed saves and playback are not plays. gamesPlayed is only
     *  changed here; terminal events (died/won/quit) leave it untouched. */
    PlayerStats withGameStart() {
        return new PlayerStats(
            gamesPlayed + 1,
            wins, deaths,
            deepestDepth, longestRunTurns,
            new ArrayList<>(kills),
            new ArrayList<>(deathCauses),
            new ArrayList<>(alliesFreed));
    }

    PlayerStats withMonsterKilled(String monsterName) {
        return new PlayerStats(
            gamesPlayed, wins, deaths,
            deepestDepth, longestRunTurns,
            mergeTally(kills, monsterName),
            new ArrayList<>(deathCauses),
            new ArrayList<>(alliesFreed));
    }

    PlayerStats withAllyFreed(String monsterName) {
        return new PlayerStats(
            gamesPlayed, wins, deaths,
            deepestDepth, longestRunTurns,
            new ArrayList<>(kills),
            new ArrayList<>(deathCauses),
            mergeTally(alliesFreed, monsterName));
    }

    PlayerStats withPlayerDied(String killedBy, int depth, int turns) {
        return new PlayerStats(
            gamesPlayed,
            wins,
            deaths + 1,
            Math.max(deepestDepth, depth),
            Math.max(longestRunTurns, turns),
            new ArrayList<>(kills),
            mergeTally(deathCauses, killedBy),
            new ArrayList<>(alliesFreed));
    }

    PlayerStats withPlayerWon(int depth, int turns) {
        return new PlayerStats(
            gamesPlayed,
            wins + 1,
            deaths,
            Math.max(deepestDepth, depth),
            Math.max(longestRunTurns, turns),
            new ArrayList<>(kills),
            new ArrayList<>(deathCauses),
            new ArrayList<>(alliesFreed));
    }

    PlayerStats withPlayerQuit() {
        return new PlayerStats(
            gamesPlayed,
            wins, deaths,
            deepestDepth, longestRunTurns,
            new ArrayList<>(kills),
            new ArrayList<>(deathCauses),
            new ArrayList<>(alliesFreed));
    }

    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("gamesPlayed", gamesPlayed);
        o.put("wins", wins);
        o.put("deaths", deaths);
        o.put("deepestDepth", deepestDepth);
        o.put("longestRunTurns", longestRunTurns);
        o.put("kills", tallyListToJson(kills));
        o.put("deathCauses", tallyListToJson(deathCauses));
        o.put("alliesFreed", tallyListToJson(alliesFreed));
        return o;
    }

    static PlayerStats fromJson(JSONObject o) {
        return new PlayerStats(
            o.optInt("gamesPlayed"),
            o.optInt("wins"),
            o.optInt("deaths"),
            o.optInt("deepestDepth"),
            o.optInt("longestRunTurns"),
            tallyListFromJson(o.optJSONObject("kills")),
            tallyListFromJson(o.optJSONObject("deathCauses")),
            tallyListFromJson(o.optJSONObject("alliesFreed")));
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
