package org.broguece.game;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Canonical list of achievements for the app. Each entry declares its own
 *  rule inline against PlayerStats; no per-achievement persistent state.
 *
 *  Evaluation order at render time: roughly by difficulty / natural game
 *  progression, so the PersonalStats modal reads top-to-bottom as a ladder
 *  of what the player has done and what's ahead. */
final class AchievementManager {

    private AchievementManager() {}

    private static final List<Achievement> ALL = Collections.unmodifiableList(
        Arrays.asList(
            new Achievement("first_blood",
                "First Blood",
                "Slay your first monster",
                s -> !s.kills.isEmpty()),

            new Achievement("you_only_live_twice",
                "You Only Live Twice",
                "Die for the first time",
                s -> s.deaths >= 1),

            new Achievement("rambo_iii",
                "Rambo III",
                "Free an ally",
                s -> s.alliesFreedTotal() >= 1),

            new Achievement("raiders_of_the_lost_ark",
                "Raiders of the Lost Ark",
                "Pick up the Amulet of Yendor",
                s -> s.amuletPickups >= 1),

            new Achievement("the_great_escape",
                "The Great Escape",
                "Win a game with the Amulet of Yendor",
                // Both a regular win and a master victory count — `wins` is
                // incremented in both paths, `masteryWins` is the subset.
                s -> s.wins >= 1),

            new Achievement("hail_to_the_king",
                "Hail to the King, Baby",
                "Achieve a Mastery victory",
                s -> s.masteryWins >= 1)
        )
    );

    /** All achievements in display order. Immutable. */
    static List<Achievement> all() {
        return ALL;
    }
}
