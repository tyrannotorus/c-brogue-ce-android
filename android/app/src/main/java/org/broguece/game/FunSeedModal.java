package org.broguece.game;

/** Fun-seed branch of the seed-details modal. Only difference vs. base is
 *  the title; everything else (header label, description, stats, buttons)
 *  is inherited. */
final class FunSeedModal extends SeedDetailsModal {
    FunSeedModal(BrogueActivity activity) { super(activity); }
    @Override protected String getTitleUpper() { return "FUN SEED"; }
}
