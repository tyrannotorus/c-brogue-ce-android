package org.broguece.game;

/** One achievement definition — id, display strings, and the rule that
 *  determines whether it's earned given a PlayerStats snapshot.
 *
 *  Achievements derive their earned state from PlayerStats rather than
 *  carrying their own separate state. This keeps a single source of truth
 *  (StatsStore receives JNI events, updates PlayerStats, the manager reads
 *  PlayerStats) so the two can never drift. */
final class Achievement {

    /** Pure function over a PlayerStats snapshot. Implementations must be
     *  side-effect-free and deterministic — the manager may evaluate a rule
     *  multiple times per render pass. */
    interface Rule {
        boolean isEarned(PlayerStats stats);
    }

    final String id;
    final String title;
    final String description;
    final Rule rule;

    Achievement(String id, String title, String description, Rule rule) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.rule = rule;
    }

    boolean isEarned(PlayerStats stats) {
        return rule.isEarned(stats);
    }
}
