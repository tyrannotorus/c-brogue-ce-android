package org.broguece.game;

import android.graphics.Color;

/** Brogue's UI color palette — derived from the game's actual color definitions. */
final class Palette {

    private Palette() {}

    static final int DEEP_INDIGO    = Color.argb(230, 18, 15, 38);   // interfaceBoxColor
    static final int FLAME_EMBER    = Color.argb(255, 180, 100, 40); // warm flame highlight
    static final int FLAME_DIM      = Color.argb(255, 100, 55, 20);  // subdued flame
    static final int PALE_BLUE      = Color.argb(255, 140, 150, 190);// flameTitleColor text
    static final int GHOST_WHITE    = Color.argb(255, 210, 205, 220);
    static final int VOID_BLACK     = Color.argb(240, 8, 6, 16);
    static final int SUBMENU_BG     = Color.argb(235, 12, 10, 25);
    static final int RIPPLE_GLOW    = Color.argb(80, 180, 120, 50);
    static final int BORDER_DIM     = Color.argb(120, 80, 65, 40);
    static final int BORDER_ACTIVE  = Color.argb(200, 180, 120, 50);

    static final int INVENTORY_BG   = Color.argb(245, 10, 8, 22);
    static final int ITEM_BG        = Color.argb(200, 20, 17, 42);
    static final int EQUIPPED_GLOW  = Color.argb(220, 45, 35, 55);
    static final int ACTION_BG      = Color.argb(220, 30, 25, 55);
    static final int GOOD_MAGIC     = Color.argb(255, 153, 128, 255); // Brogue {60,50,100}
    static final int BAD_MAGIC      = Color.argb(255, 255, 128, 153); // Brogue {100,50,60}

    static final int DISABLED_BG    = Color.argb(100, 15, 13, 30);
    static final int DISABLED_TEXT  = Color.argb(100, 100, 95, 110);

    /** Toggle-active background used by animateToggle's "active" state. */
    static final int TOGGLE_ACTIVE  = Color.argb(200, 50, 35, 15);
}
