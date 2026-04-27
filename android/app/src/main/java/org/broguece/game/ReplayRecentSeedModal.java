package org.broguece.game;

/** Reached from the Recent Seeds row on PlayerStatsModal. Identical to the
 *  base in every way except the title. */
final class ReplayRecentSeedModal extends SeedDetailsModal {
    ReplayRecentSeedModal(BrogueActivity activity) { super(activity); }
    @Override protected String getTitleUpper() { return "REPLAY SEED"; }
}
