package org.broguece.game;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Point;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.app.AlertDialog;
import android.text.InputFilter;
import android.text.InputType;

import org.json.JSONArray;
import org.json.JSONObject;
import org.libsdl.app.SDLActivity;

public class BrogueActivity extends SDLActivity {

    // Brogue's palette — derived from the game's actual color definitions
    private static final int DEEP_INDIGO   = Color.argb(230, 18, 15, 38);   // interfaceBoxColor
    private static final int FLAME_EMBER   = Color.argb(255, 180, 100, 40); // warm flame highlight
    private static final int FLAME_DIM     = Color.argb(255, 100, 55, 20);  // subdued flame
    private static final int PALE_BLUE     = Color.argb(255, 140, 150, 190);// flameTitleColor text
    private static final int GHOST_WHITE   = Color.argb(255, 210, 205, 220);
    private static final int VOID_BLACK    = Color.argb(240, 8, 6, 16);
    private static final int SUBMENU_BG    = Color.argb(235, 12, 10, 25);
    private static final int RIPPLE_GLOW   = Color.argb(80, 180, 120, 50);
    private static final int BORDER_DIM    = Color.argb(120, 80, 65, 40);
    private static final int BORDER_ACTIVE = Color.argb(200, 180, 120, 50);

    private static final int INVENTORY_BG  = Color.argb(245, 10, 8, 22);
    private static final int ITEM_BG       = Color.argb(200, 20, 17, 42);
    private static final int EQUIPPED_GLOW = Color.argb(220, 45, 35, 55);
    private static final int ACTION_BG     = Color.argb(220, 30, 25, 55);
    private static final int GOOD_MAGIC    = Color.argb(255, 153, 128, 255); // Brogue {60,50,100}
    private static final int BAD_MAGIC     = Color.argb(255, 255, 128, 153); // Brogue {100,50,60}

    // Right-edge safe margin — accommodates devices (e.g. Poco M3 / MIUI)
    // where the navigation bar area is reserved even in immersive mode.
    private static final int EDGE_SAFE_DP  = 48;

    // Start menu choice constants — must match android-touch.c
    private static final int START_MENU_NEW_GAME  = 0;
    private static final int START_MENU_RESUME    = 1;
    private static final int START_MENU_PLAY_SEED = 2;

    private static final int DISABLED_BG   = Color.argb(100, 15, 13, 30);
    private static final int DISABLED_TEXT  = Color.argb(100, 100, 95, 110);

    private FrameLayout gameOverlay;
    private FrameLayout inventoryOverlay;
    private View currentlyExpandedDetail;
    private View loadingOverlay;
    private LinearLayout toolbarContainer; // holds pinned icon buttons + menu button
    private LinearLayout submenu;
    private View menuBtn;

    // Action registry: key, label, icon resource, action to perform
    private static final String[][] ACTION_KEYS = {
        {"inventory",  "Inventory"},
        {"mouse",      "Mouse Toggle"},
        {"click",      "Mouse Click"},
        {"search",     "Search"},
        {"explore",    "Explore"},
        {"wait",       "Rest One Turn"},
        {"rest",       "Rest Until Better"},
        {"autopilot",  "Autopilot"},
    };
    private static final java.util.Set<String> DEFAULT_PINNED =
        new java.util.HashSet<>(java.util.Arrays.asList("inventory", "mouse", "click"));

    // Master display order for the Actions panel. The toolbar renders the
    // pinned subset in this order. User-reorderable via drag handle.
    private java.util.List<String> cachedActionOrder;

    private int actionIconRes(String key) {
        switch (key) {
            case "inventory": return R.drawable.ic_money_bag;
            case "mouse":     return R.drawable.ic_mouse;
            case "click":     return R.drawable.ic_left_click;
            case "search":    return R.drawable.ic_search;
            case "explore":   return R.drawable.ic_explore;
            case "wait":      return R.drawable.ic_hourglass;
            case "rest":      return R.drawable.ic_heart_check;
            case "autopilot": return R.drawable.ic_autoplay;
            default:          return R.drawable.ic_menu;
        }
    }

    private java.util.Set<String> getPinnedActions() {
        android.content.SharedPreferences prefs =
            getSharedPreferences("brogue_toolbar", MODE_PRIVATE);
        if (!prefs.contains("pinned_actions")) {
            return new java.util.HashSet<>(DEFAULT_PINNED);
        }
        return new java.util.HashSet<>(
            prefs.getStringSet("pinned_actions", DEFAULT_PINNED));
    }

    private void setPinnedActions(java.util.Set<String> pinned) {
        getSharedPreferences("brogue_toolbar", MODE_PRIVATE)
            .edit()
            .putStringSet("pinned_actions", pinned)
            .apply();
    }

    private boolean isPinned(String key) {
        return getPinnedActions().contains(key);
    }

    private void togglePin(String key) {
        java.util.Set<String> pinned = getPinnedActions();
        if (pinned.contains(key)) {
            pinned.remove(key);
        } else {
            pinned.add(key);
        }
        setPinnedActions(pinned);
        rebuildToolbar();
    }

    // Master ordering across all registered actions. Any missing keys are
    // appended in registry order so a newly-added action always shows up.
    private java.util.List<String> getActionOrder() {
        if (cachedActionOrder != null) return cachedActionOrder;

        android.content.SharedPreferences prefs =
            getSharedPreferences("brogue_toolbar", MODE_PRIVATE);
        String json = prefs.getString("action_order", null);
        java.util.List<String> list = new java.util.ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();

        if (json != null) {
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    String k = arr.optString(i, null);
                    if (k != null && !seen.contains(k) && isKnownAction(k)) {
                        list.add(k);
                        seen.add(k);
                    }
                }
            } catch (Exception ignored) { }
        }
        for (String[] entry : ACTION_KEYS) {
            if (!seen.contains(entry[0])) list.add(entry[0]);
        }
        cachedActionOrder = list;
        return cachedActionOrder;
    }

    private void setActionOrder(java.util.List<String> order) {
        java.util.List<String> clean = new java.util.ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String k : order) {
            if (k != null && !seen.contains(k) && isKnownAction(k)) {
                clean.add(k);
                seen.add(k);
            }
        }
        for (String[] entry : ACTION_KEYS) {
            if (!seen.contains(entry[0])) clean.add(entry[0]);
        }
        cachedActionOrder = clean;

        JSONArray arr = new JSONArray();
        for (String k : clean) arr.put(k);
        getSharedPreferences("brogue_toolbar", MODE_PRIVATE)
            .edit()
            .putString("action_order", arr.toString())
            .apply();
    }

    private boolean isKnownAction(String key) {
        for (String[] entry : ACTION_KEYS) {
            if (entry[0].equals(key)) return true;
        }
        return false;
    }

    private String actionLabel(String key) {
        for (String[] entry : ACTION_KEYS) {
            if (entry[0].equals(key)) return entry[1];
        }
        return key;
    }

    private void executeAction(String key) {
        switch (key) {
            case "inventory": sendKey(KeyEvent.KEYCODE_I); break;
            case "mouse":     sendKey(KeyEvent.KEYCODE_MOVE_HOME); break;
            case "click":     sendKey(KeyEvent.KEYCODE_ENTER); break;
            case "search":    sendChar('s'); break;
            case "explore":   sendChar('x'); break;
            case "wait":      sendChar('z'); break;
            case "rest":      sendChar('Z'); break;
            case "autopilot": sendChar('A'); break;
        }
    }

    private void rebuildToolbar() {
        if (toolbarContainer == null) return;
        toolbarContainer.removeAllViews();

        java.util.Set<String> pinned = getPinnedActions();
        int btnSize = dpToPx(44);
        int btnMargin = dpToPx(3);

        // Add pinned action buttons in master order
        for (String key : getActionOrder()) {
            if (!pinned.contains(key)) continue;

            View btn = makeIconBarButton(actionIconRes(key));
            btn.setTag(R.id.action_key_tag, key);

            if ("mouse".equals(key)) {
                btn.setOnClickListener(v -> {
                    collapseSubmenu(submenu, menuBtn);
                    executeAction("mouse");
                    boolean active = v.getTag() != null
                        && v.getTag() instanceof Boolean && (boolean) v.getTag();
                    active = !active;
                    v.setTag(active);
                    animateToggle(v, active);
                });
            } else {
                btn.setOnClickListener(v -> {
                    collapseSubmenu(submenu, menuBtn);
                    resetMouseToggle();
                    executeAction(key);
                    pulseButton(v);
                });
            }

            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(btnSize, btnSize);
            p.setMargins(btnMargin, 0, btnMargin, 0);
            toolbarContainer.addView(btn, p);
        }

        // Menu button always last
        if (menuBtn.getParent() != null) {
            ((ViewGroup) menuBtn.getParent()).removeView(menuBtn);
        }
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(btnSize, btnSize);
        mp.setMargins(btnMargin, 0, btnMargin, 0);
        toolbarContainer.addView(menuBtn, mp);
    }

    private View findToolbarButton(String key) {
        if (toolbarContainer == null) return null;
        for (int i = 0; i < toolbarContainer.getChildCount(); i++) {
            View child = toolbarContainer.getChildAt(i);
            Object tag = child.getTag(R.id.action_key_tag);
            if (key.equals(tag)) return child;
        }
        return null;
    }

    private void resetMouseToggle() {
        View mouseView = findToolbarButton("mouse");
        if (mouseView != null && mouseView.getTag() instanceof Boolean && (boolean) mouseView.getTag()) {
            mouseView.setTag(false);
            animateToggle(mouseView, false);
        }
    }

    // Drag listener for the Actions-panel rows container. Moves the dragged
    // row to the hovered index for live feedback, then persists master order
    // and rebuilds the toolbar on drag end.
    private final View.OnDragListener rowsDragListener = (host, event) -> {
        if (!(host instanceof LinearLayout)) return false;
        LinearLayout rows = (LinearLayout) host;
        Object local = event.getLocalState();
        if (!(local instanceof View)) return false;
        View dragged = (View) local;

        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                return event.getClipDescription() != null
                    && event.getClipDescription().getLabel() != null
                    && "brogue_action".contentEquals(event.getClipDescription().getLabel());
            case DragEvent.ACTION_DRAG_LOCATION: {
                int target = indexForDragY(rows, event.getY(), dragged);
                int current = rows.indexOfChild(dragged);
                if (target >= 0 && target != current) {
                    rows.removeView(dragged);
                    rows.addView(dragged, target);
                }
                return true;
            }
            case DragEvent.ACTION_DRAG_ENDED:
                dragged.setAlpha(1f);
                persistRowOrder(rows);
                rebuildToolbar();
                return true;
            case DragEvent.ACTION_DROP:
                return true;
            default:
                return true;
        }
    };

    // Target index for the dragged row. Rows are uniform height + margins, so
    // we compute a single slot size and use index-based slot bounds rather
    // than each child's live getY() (which is stale for one frame after a
    // swap and causes thrash). While the finger is still within the dragged
    // row's own slot we don't swap — that's the dead-zone that kills jitter.
    private int indexForDragY(LinearLayout rows, float y, View dragged) {
        int n = rows.getChildCount();
        if (n == 0) return 0;
        int current = rows.indexOfChild(dragged);

        int slot = dragged.getHeight();
        ViewGroup.LayoutParams lp = dragged.getLayoutParams();
        if (lp instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams llp = (LinearLayout.LayoutParams) lp;
            slot += llp.topMargin + llp.bottomMargin;
        }
        if (slot <= 0) return current;

        float slotTop = current * slot;
        float slotBottom = slotTop + slot;
        if (y >= slotTop && y < slotBottom) return current;

        int target = (int) (y / slot);
        if (target < 0) target = 0;
        if (target >= n) target = n - 1;
        return target;
    }

    private void persistRowOrder(LinearLayout rows) {
        java.util.List<String> order = new java.util.ArrayList<>();
        for (int i = 0; i < rows.getChildCount(); i++) {
            Object tag = rows.getChildAt(i).getTag(R.id.action_key_tag);
            if (tag instanceof String) order.add((String) tag);
        }
        setActionOrder(order);
    }

    // Drag shadow mirrors the row at actual size, anchored at the touch point
    // under the finger so the shadow tracks cleanly.
    private static class ReorderDragShadow extends View.DragShadowBuilder {
        ReorderDragShadow(View v) { super(v); }
        @Override
        public void onProvideShadowMetrics(Point outSize, Point outTouch) {
            View v = getView();
            outSize.set(v.getWidth(), v.getHeight());
            outTouch.set(v.getWidth() / 2, v.getHeight() / 2);
        }
        @Override
        public void onDrawShadow(Canvas canvas) {
            getView().draw(canvas);
        }
    }

    @Override
    protected String[] getLibraries() {
        return new String[]{ "SDL2", "SDL2_image", "brogue" };
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ===== Game overlay =====
        gameOverlay = new FrameLayout(this);

        // Submenu (hamburger menu contents)
        submenu = new LinearLayout(this);
        submenu.setOrientation(LinearLayout.VERTICAL);
        submenu.setBackground(makeSubmenuBackground());
        submenu.setPadding(dpToPx(3), dpToPx(4), dpToPx(3), dpToPx(3));
        submenu.setVisibility(View.GONE);
        submenu.setElevation(dpToPx(8));

        menuBtn = makeIconBarButton(R.drawable.ic_menu);

        // Actions opens native panel
        View actionsItem = makeSubmenuItem("Actions", "");
        actionsItem.setOnClickListener(v -> {
            collapseSubmenu(submenu, menuBtn);
            showActionsPanel();
        });
        LinearLayout.LayoutParams actionsP = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        actionsP.setMargins(0, dpToPx(1), 0, dpToPx(1));
        submenu.addView(actionsItem, actionsP);

        // Settings opens native panel
        View settingsItem = makeSubmenuItem("Settings", "");
        settingsItem.setOnClickListener(v -> {
            collapseSubmenu(submenu, menuBtn);
            showSettingsPanel();
        });
        LinearLayout.LayoutParams settingsP = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        settingsP.setMargins(0, dpToPx(1), 0, dpToPx(1));
        submenu.addView(settingsItem, settingsP);

        // Divider before Exit
        View menuDivider = new View(this);
        menuDivider.setBackgroundColor(BORDER_DIM);
        LinearLayout.LayoutParams divP = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1);
        divP.setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
        submenu.addView(menuDivider, divP);

        // Exit opens native panel
        View exitItem = makeSubmenuItem("Exit", "");
        exitItem.setOnClickListener(v -> {
            collapseSubmenu(submenu, menuBtn);
            showExitPanel();
        });
        LinearLayout.LayoutParams exitP = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        exitP.setMargins(0, dpToPx(1), 0, dpToPx(1));
        submenu.addView(exitItem, exitP);

        menuBtn.setOnClickListener(v -> {
            resetMouseToggle();
            boolean open = submenu.getVisibility() == View.VISIBLE;
            if (open) {
                collapseSubmenu(submenu, menuBtn);
            } else {
                expandSubmenu(submenu, menuBtn);
            }
        });

        // Bottom bar — toolbar container holds pinned buttons, menu always last
        toolbarContainer = new LinearLayout(this);
        toolbarContainer.setOrientation(LinearLayout.HORIZONTAL);
        toolbarContainer.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        toolbarContainer.setBackground(makeBarBackground());
        int barPad = dpToPx(4);
        toolbarContainer.setPadding(barPad, barPad, barPad, barPad);

        // Stack submenu above bar
        LinearLayout bottomGroup = new LinearLayout(this);
        bottomGroup.setOrientation(LinearLayout.VERTICAL);
        bottomGroup.setGravity(Gravity.END);
        bottomGroup.addView(submenu, new LinearLayout.LayoutParams(
            dpToPx(170), LinearLayout.LayoutParams.WRAP_CONTENT));
        bottomGroup.addView(toolbarContainer, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM | Gravity.END);
        bottomParams.setMargins(0, 0, dpToPx(EDGE_SAFE_DP), 0);
        gameOverlay.addView(bottomGroup, bottomParams);

        rebuildToolbar();

        gameOverlay.setVisibility(View.GONE);

        addContentView(gameOverlay, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));

        // ===== Inventory overlay (created once, populated dynamically) =====
        inventoryOverlay = new FrameLayout(this);
        inventoryOverlay.setVisibility(View.GONE);
        inventoryOverlay.setClickable(true); // absorb touches so they don't reach the game
        addContentView(inventoryOverlay, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));
    }

    // ---- Settings panel ----

    private void showSettingsPanel() {
        inventoryOverlay.removeAllViews();
        currentlyExpandedDetail = null;

        // Backdrop
        View backdrop = new View(this);
        backdrop.setBackgroundColor(Color.argb(140, 0, 0, 0));
        backdrop.setOnClickListener(v -> hideSettingsPanel());
        inventoryOverlay.addView(backdrop, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));
        backdrop.setAlpha(0f);
        backdrop.animate().alpha(1f).setDuration(280).start();

        // Panel
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad = dpToPx(10);
        panel.setPadding(pad, pad, pad, pad);

        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setShape(GradientDrawable.RECTANGLE);
        panelBg.setCornerRadii(new float[]{
            dpToPx(4), dpToPx(4), 0, 0, 0, 0, dpToPx(4), dpToPx(4)});
        panelBg.setColor(INVENTORY_BG);
        panelBg.setStroke(1, BORDER_DIM);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackground(panelBg);
        scrollView.addView(panel);

        // Header
        TextView header = new TextView(this);
        header.setText("SETTINGS");
        header.setTextColor(FLAME_EMBER);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        header.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        header.setLetterSpacing(0.15f);
        header.setPadding(dpToPx(4), dpToPx(2), 0, dpToPx(4));
        panel.addView(header);

        View headerSep = new View(this);
        GradientDrawable sepGrad = new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{ FLAME_DIM, FLAME_EMBER, FLAME_DIM });
        headerSep.setBackground(sepGrad);
        LinearLayout.LayoutParams hSepP = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1);
        hSepP.setMargins(dpToPx(4), 0, dpToPx(4), dpToPx(8));
        panel.addView(headerSep, hSepP);

        // Toggles section
        addSettingsToggle(panel, "Hide Color Effects", "hide_color_effects", '\\');
        addSettingsToggle(panel, "Display Stealth Range", "display_stealth_range", ']');
        addGraphicsModeCycler(panel);

        // Separator
        addSettingsSeparator(panel);

        // Info section
        addSettingsAction(panel, "Copy Seed to Clipboard", v -> {
            long seed = nativeGetSeed();
            android.content.ClipboardManager clipboard =
                (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(
                android.content.ClipData.newPlainText("Brogue Seed", String.valueOf(seed)));
            android.widget.Toast.makeText(this,
                "Seed " + seed + " copied", android.widget.Toast.LENGTH_SHORT).show();
            hideSettingsPanel();
        });

        int panelWidth = Math.min(dpToPx(280),
            (int)(getResources().getDisplayMetrics().widthPixels * 0.6f));

        FrameLayout.LayoutParams scrollParams = new FrameLayout.LayoutParams(
            panelWidth, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM | Gravity.END);
        scrollParams.setMargins(0, dpToPx(8), dpToPx(EDGE_SAFE_DP), dpToPx(52));

        inventoryOverlay.addView(scrollView, scrollParams);
        inventoryOverlay.setVisibility(View.VISIBLE);

        // Slide-up animation
        scrollView.setTranslationY(dpToPx(40));
        scrollView.setAlpha(0f);
        scrollView.animate()
            .translationY(0).alpha(1f)
            .setDuration(200)
            .setInterpolator(new DecelerateInterpolator(1.5f))
            .start();
    }

    private void hideSettingsPanel() {
        if (inventoryOverlay.getChildCount() < 2) {
            inventoryOverlay.setVisibility(View.GONE);
            inventoryOverlay.removeAllViews();
            return;
        }
        View backdrop = inventoryOverlay.getChildAt(0);
        View panel = inventoryOverlay.getChildAt(1);

        panel.animate()
            .translationY(dpToPx(30)).alpha(0f)
            .setDuration(150)
            .setInterpolator(new DecelerateInterpolator())
            .start();

        backdrop.animate()
            .alpha(0f)
            .setDuration(150)
            .withEndAction(() -> {
                inventoryOverlay.setVisibility(View.GONE);
                inventoryOverlay.removeAllViews();
            })
            .start();
    }

    // ---- Actions panel ----

    private void showActionsPanel() {
        inventoryOverlay.removeAllViews();
        currentlyExpandedDetail = null;

        // Backdrop
        View backdrop = new View(this);
        backdrop.setBackgroundColor(Color.argb(140, 0, 0, 0));
        backdrop.setOnClickListener(v -> hideActionsPanel());
        inventoryOverlay.addView(backdrop, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));
        backdrop.setAlpha(0f);
        backdrop.animate().alpha(1f).setDuration(280).start();

        // Panel
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad = dpToPx(10);
        panel.setPadding(pad, pad, pad, pad);

        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setShape(GradientDrawable.RECTANGLE);
        panelBg.setCornerRadii(new float[]{
            dpToPx(4), dpToPx(4), 0, 0, 0, 0, dpToPx(4), dpToPx(4)});
        panelBg.setColor(INVENTORY_BG);
        panelBg.setStroke(1, BORDER_DIM);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackground(panelBg);
        scrollView.addView(panel);

        // Header
        TextView header = new TextView(this);
        header.setText("ACTIONS");
        header.setTextColor(FLAME_EMBER);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        header.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        header.setLetterSpacing(0.15f);
        header.setPadding(dpToPx(4), dpToPx(2), 0, dpToPx(4));
        panel.addView(header);

        View headerSep = new View(this);
        GradientDrawable sepGrad = new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{ FLAME_DIM, FLAME_EMBER, FLAME_DIM });
        headerSep.setBackground(sepGrad);
        LinearLayout.LayoutParams hSepP = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1);
        hSepP.setMargins(dpToPx(4), 0, dpToPx(4), dpToPx(8));
        panel.addView(headerSep, hSepP);

        LinearLayout rowsContainer = new LinearLayout(this);
        rowsContainer.setOrientation(LinearLayout.VERTICAL);
        rowsContainer.setOnDragListener(rowsDragListener);
        panel.addView(rowsContainer, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));

        for (String key : getActionOrder()) {
            addActionRow(rowsContainer, key, actionLabel(key));
        }

        int panelWidth = Math.min(dpToPx(280),
            (int)(getResources().getDisplayMetrics().widthPixels * 0.6f));

        FrameLayout.LayoutParams scrollParams = new FrameLayout.LayoutParams(
            panelWidth, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM | Gravity.END);
        scrollParams.setMargins(0, dpToPx(8), dpToPx(EDGE_SAFE_DP), dpToPx(52));

        inventoryOverlay.addView(scrollView, scrollParams);
        inventoryOverlay.setVisibility(View.VISIBLE);

        // Slide-up animation
        scrollView.setTranslationY(dpToPx(40));
        scrollView.setAlpha(0f);
        scrollView.animate()
            .translationY(0).alpha(1f)
            .setDuration(200)
            .setInterpolator(new DecelerateInterpolator(1.5f))
            .start();
    }

    private void hideActionsPanel() {
        hideActionsPanel(null);
    }

    private void hideActionsPanel(Runnable onDone) {
        if (inventoryOverlay.getChildCount() < 2) {
            inventoryOverlay.setVisibility(View.GONE);
            inventoryOverlay.removeAllViews();
            if (onDone != null) onDone.run();
            return;
        }
        View backdrop = inventoryOverlay.getChildAt(0);
        View panel = inventoryOverlay.getChildAt(1);

        panel.animate()
            .translationY(dpToPx(30)).alpha(0f)
            .setDuration(150)
            .setInterpolator(new DecelerateInterpolator())
            .start();

        backdrop.animate()
            .alpha(0f)
            .setDuration(150)
            .withEndAction(() -> {
                inventoryOverlay.setVisibility(View.GONE);
                inventoryOverlay.removeAllViews();
                if (onDone != null) onDone.run();
            })
            .start();
    }

    // ---- Exit panel ----

    private void showExitPanel() {
        inventoryOverlay.removeAllViews();
        currentlyExpandedDetail = null;

        View backdrop = new View(this);
        backdrop.setBackgroundColor(Color.argb(140, 0, 0, 0));
        backdrop.setOnClickListener(v -> hideExitPanel());
        inventoryOverlay.addView(backdrop, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));
        backdrop.setAlpha(0f);
        backdrop.animate().alpha(1f).setDuration(280).start();

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad = dpToPx(10);
        panel.setPadding(pad, pad, pad, pad);

        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setShape(GradientDrawable.RECTANGLE);
        panelBg.setCornerRadii(new float[]{
            dpToPx(4), dpToPx(4), 0, 0, 0, 0, dpToPx(4), dpToPx(4)});
        panelBg.setColor(INVENTORY_BG);
        panelBg.setStroke(1, BORDER_DIM);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackground(panelBg);
        scrollView.addView(panel);

        TextView header = new TextView(this);
        header.setText("EXIT");
        header.setTextColor(FLAME_EMBER);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        header.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        header.setLetterSpacing(0.15f);
        header.setPadding(dpToPx(4), dpToPx(2), 0, dpToPx(4));
        panel.addView(header);

        View headerSep = new View(this);
        GradientDrawable sepGrad = new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{ FLAME_DIM, FLAME_EMBER, FLAME_DIM });
        headerSep.setBackground(sepGrad);
        LinearLayout.LayoutParams hSepP = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1);
        hSepP.setMargins(dpToPx(4), 0, dpToPx(4), dpToPx(8));
        panel.addView(headerSep, hSepP);

        addSettingsAction(panel, "Abandon Game", v -> {
            hideExitPanel();
            sendChar('Q');
        });
        addSettingsAction(panel, "Save and Exit", v -> {
            hideExitPanel();
            sendChar('S');
        });
        addSettingsAction(panel, "Exit", v -> {
            hideExitPanel();
            finishAndRemoveTask();
        });

        int panelWidth = Math.min(dpToPx(280),
            (int)(getResources().getDisplayMetrics().widthPixels * 0.6f));

        FrameLayout.LayoutParams scrollParams = new FrameLayout.LayoutParams(
            panelWidth, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM | Gravity.END);
        scrollParams.setMargins(0, dpToPx(8), dpToPx(EDGE_SAFE_DP), dpToPx(52));

        inventoryOverlay.addView(scrollView, scrollParams);
        inventoryOverlay.setVisibility(View.VISIBLE);

        scrollView.setTranslationY(dpToPx(40));
        scrollView.setAlpha(0f);
        scrollView.animate()
            .translationY(0).alpha(1f)
            .setDuration(200)
            .setInterpolator(new DecelerateInterpolator(1.5f))
            .start();
    }

    private void hideExitPanel() {
        if (inventoryOverlay.getChildCount() < 2) {
            inventoryOverlay.setVisibility(View.GONE);
            inventoryOverlay.removeAllViews();
            return;
        }
        View backdrop = inventoryOverlay.getChildAt(0);
        View panel = inventoryOverlay.getChildAt(1);

        panel.animate()
            .translationY(dpToPx(30)).alpha(0f)
            .setDuration(150)
            .setInterpolator(new DecelerateInterpolator())
            .start();

        backdrop.animate()
            .alpha(0f)
            .setDuration(150)
            .withEndAction(() -> {
                inventoryOverlay.setVisibility(View.GONE);
                inventoryOverlay.removeAllViews();
            })
            .start();
    }

    // ---- Start menu (shown over title flames) ----

    private View startMenuOverlay;

    public void showStartMenu(final boolean hasSave, final boolean saveCompatible) {
        runOnUiThread(() -> {
            if (startMenuOverlay != null && startMenuOverlay.getParent() != null) {
                ((ViewGroup) startMenuOverlay.getParent()).removeView(startMenuOverlay);
            }

            FrameLayout root = new FrameLayout(this);
            startMenuOverlay = root;

            // Semi-transparent backdrop — no dismiss on tap (menu is mandatory)
            View backdrop = new View(this);
            backdrop.setBackgroundColor(Color.argb(120, 0, 0, 0));
            root.addView(backdrop, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

            // Panel
            LinearLayout panel = new LinearLayout(this);
            panel.setOrientation(LinearLayout.VERTICAL);
            int pad = dpToPx(16);
            panel.setPadding(pad, pad, pad, pad);

            GradientDrawable panelBg = new GradientDrawable();
            panelBg.setShape(GradientDrawable.RECTANGLE);
            panelBg.setCornerRadius(dpToPx(6));
            panelBg.setColor(INVENTORY_BG);
            panelBg.setStroke(1, BORDER_DIM);
            panel.setBackground(panelBg);
            panel.setElevation(dpToPx(12));

            // Header
            TextView header = new TextView(this);
            header.setText("BROGUE CE");
            header.setTextColor(FLAME_EMBER);
            header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            header.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            header.setLetterSpacing(0.2f);
            header.setGravity(Gravity.CENTER);
            header.setPadding(0, dpToPx(4), 0, dpToPx(8));
            panel.addView(header);

            // Separator
            View sep = new View(this);
            GradientDrawable sepGrad = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{ FLAME_DIM, FLAME_EMBER, FLAME_DIM });
            sep.setBackground(sepGrad);
            LinearLayout.LayoutParams sepP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
            sepP.setMargins(dpToPx(8), 0, dpToPx(8), dpToPx(12));
            panel.addView(sep, sepP);

            // New Game button
            addStartMenuButton(panel, "New Game", true, v -> {
                dismissStartMenu();
                nativeStartMenuResult(START_MENU_NEW_GAME);
            });

            // Resume Game button (enabled only if compatible save exists)
            addStartMenuButton(panel, hasSave && !saveCompatible
                    ? "Resume Game  (incompatible save)"
                    : "Resume Game",
                hasSave && saveCompatible,
                v -> {
                    dismissStartMenu();
                    nativeStartMenuResult(START_MENU_RESUME);
                });

            // Play Seeds button — opens submodal with custom/weekly/curated
            addStartMenuButton(panel, "Play Seeds", true, v -> showPlaySeedsModal());

            // Credits button — overlays a credits modal; start menu stays behind
            addStartMenuButton(panel, "Credits", true, v -> showAboutModal());

            int panelWidth = Math.min(dpToPx(300),
                (int)(getResources().getDisplayMetrics().widthPixels * 0.7f));

            FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                panelWidth, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
            root.addView(panel, panelParams);

            addContentView(root, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

            // Fade-in animation
            panel.setAlpha(0f);
            panel.setScaleX(0.92f);
            panel.setScaleY(0.92f);
            panel.animate()
                .alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(250)
                .setInterpolator(new DecelerateInterpolator(1.5f))
                .start();

            // If the user cancelled out of the custom-seed prompt, return
            // them to the Play Seeds modal they came from instead of the
            // bare title menu.
            if (reshowPlaySeedsOnStartMenu) {
                reshowPlaySeedsOnStartMenu = false;
                showPlaySeedsModal();
            }
        });
    }

    private void dismissStartMenu() {
        if (startMenuOverlay != null && startMenuOverlay.getParent() != null) {
            ((ViewGroup) startMenuOverlay.getParent()).removeView(startMenuOverlay);
        }
        startMenuOverlay = null;
    }

    private void addStartMenuButton(LinearLayout panel, String label,
                                     boolean enabled, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));
        row.setMinimumHeight(dpToPx(48));

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dpToPx(4));
        bg.setColor(enabled ? ITEM_BG : DISABLED_BG);
        if (enabled) {
            row.setBackground(new RippleDrawable(
                ColorStateList.valueOf(RIPPLE_GLOW), bg, null));
        } else {
            row.setBackground(bg);
        }

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(enabled ? GHOST_WHITE : DISABLED_TEXT);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        labelView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        labelView.setGravity(Gravity.CENTER);
        row.addView(labelView, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));

        row.setEnabled(enabled);
        row.setClickable(enabled);
        if (enabled) row.setOnClickListener(listener);

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dpToPx(3), 0, dpToPx(3));
        panel.addView(row, p);
    }

    private native void nativeStartMenuResult(int choice);

    // Start the "Play Seed" flow with a specific seed (skipping the in-engine
    // seed prompt). A non-zero seed pre-populates rogue.nextGameSeed.
    private native void nativeStartMenuResultWithSeed(int choice, long seed);

    // ---- About modal ----

    private View aboutOverlay;

    private void showAboutModal() {
        if (aboutOverlay != null && aboutOverlay.getParent() != null) {
            ((ViewGroup) aboutOverlay.getParent()).removeView(aboutOverlay);
        }

        FrameLayout root = new FrameLayout(this);
        aboutOverlay = root;

        View backdrop = new View(this);
        backdrop.setBackgroundColor(Color.argb(160, 0, 0, 0));
        backdrop.setOnClickListener(v -> dismissAboutModal());
        root.addView(backdrop, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad = dpToPx(16);
        panel.setPadding(pad, pad, pad, pad);

        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setShape(GradientDrawable.RECTANGLE);
        panelBg.setCornerRadius(dpToPx(6));
        panelBg.setColor(INVENTORY_BG);
        panelBg.setStroke(1, BORDER_DIM);
        panel.setBackground(panelBg);
        panel.setElevation(dpToPx(12));

        TextView header = new TextView(this);
        header.setText("CREDITS");
        header.setTextColor(FLAME_EMBER);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        header.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        header.setLetterSpacing(0.2f);
        header.setGravity(Gravity.CENTER);
        header.setPadding(0, dpToPx(4), 0, dpToPx(8));
        panel.addView(header);

        View sep = new View(this);
        GradientDrawable sepGrad = new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{ FLAME_DIM, FLAME_EMBER, FLAME_DIM });
        sep.setBackground(sepGrad);
        LinearLayout.LayoutParams sepP = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1);
        sepP.setMargins(dpToPx(8), 0, dpToPx(8), dpToPx(12));
        panel.addView(sep, sepP);

        addAboutCredit(panel,
            "Brogue",
            "Original game by Brian Walker (Pender)",
            "https://sites.google.com/site/broguegame/");
        addAboutCredit(panel,
            "Brogue: Community Edition",
            "tmewett and the Brogue CE contributors",
            "https://github.com/tmewett/BrogueCE");
        addAboutCredit(panel,
            "Android Port",
            "werewolf.camp",
            "https://werewolf.camp");

        ScrollView scroll = new ScrollView(this);
        scroll.addView(panel);

        int panelWidth = Math.min(dpToPx(320),
            (int)(getResources().getDisplayMetrics().widthPixels * 0.8f));
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
            panelWidth, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        root.addView(scroll, panelParams);

        addContentView(root, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));

        panel.setAlpha(0f);
        panel.setScaleX(0.94f);
        panel.setScaleY(0.94f);
        panel.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(220)
            .setInterpolator(new DecelerateInterpolator(1.5f))
            .start();
    }

    private void dismissAboutModal() {
        if (aboutOverlay != null && aboutOverlay.getParent() != null) {
            ((ViewGroup) aboutOverlay.getParent()).removeView(aboutOverlay);
        }
        aboutOverlay = null;
    }

    private void addAboutCredit(LinearLayout panel, String title, String line, String url) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(dpToPx(4), dpToPx(6), dpToPx(4), dpToPx(6));

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(GHOST_WHITE);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        titleView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        block.addView(titleView);

        TextView lineView = new TextView(this);
        lineView.setText(line);
        lineView.setTextColor(PALE_BLUE);
        lineView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        lineView.setTypeface(Typeface.MONOSPACE);
        LinearLayout.LayoutParams lineP = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        lineP.setMargins(0, dpToPx(2), 0, dpToPx(2));
        block.addView(lineView, lineP);

        TextView linkView = new TextView(this);
        linkView.setText(url);
        linkView.setTextColor(FLAME_EMBER);
        linkView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        linkView.setTypeface(Typeface.MONOSPACE);
        linkView.setPaintFlags(linkView.getPaintFlags()
            | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        linkView.setPadding(0, dpToPx(2), 0, dpToPx(2));
        linkView.setOnClickListener(v -> openUrl(url));
        block.addView(linkView);

        LinearLayout.LayoutParams blockP = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        blockP.setMargins(0, dpToPx(4), 0, dpToPx(4));
        panel.addView(block, blockP);
    }

    private void openUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception ignored) { }
    }

    // ---- Play Seeds modal ----

    // Hardcoded interesting seeds (placeholder — V1 for UI; V2 will fetch).
    // Each row is { seed, short description (~5 words) }.
    private static final String[][] INTERESTING_SEEDS = {
        {"20260414", "This week's contest seed"},
        {"20260407", "Artifact-rich early corridors"},
        {"20260331", "Tight layout, many snakes"},
        {"20260324", "Bountiful alchemy and scrolls"},
        {"20260303", "Speedrun-friendly routes"},
        {"1337",     "Community classic, vault on D2"},
        {"42",       "Legendary first-timer run"},
        {"7777",     "Lucky rune gauntlet"},
    };

    private View playSeedsOverlay;

    // When true, showStartMenu will re-open the Play Seeds modal on top of
    // itself and clear the flag. Set before routing to the C-side custom-seed
    // prompt so cancelling the prompt returns the user to Play Seeds rather
    // than stranding them on the bare title menu.
    private boolean reshowPlaySeedsOnStartMenu;

    /**
     * Current weekly-contest seed on r/brogueforum: the most-recent Tuesday
     * (≤ today), formatted as yyyymmdd. Threads are posted every Tuesday.
     */
    private long currentWeeklySeed() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        int dow = cal.get(java.util.Calendar.DAY_OF_WEEK); // Sun=1..Sat=7
        int daysSinceTuesday = (dow - java.util.Calendar.TUESDAY + 7) % 7;
        cal.add(java.util.Calendar.DAY_OF_MONTH, -daysSinceTuesday);
        int y = cal.get(java.util.Calendar.YEAR);
        int m = cal.get(java.util.Calendar.MONTH) + 1;
        int d = cal.get(java.util.Calendar.DAY_OF_MONTH);
        return (long) y * 10000L + (long) m * 100L + (long) d;
    }

    private void showPlaySeedsModal() {
        if (playSeedsOverlay != null && playSeedsOverlay.getParent() != null) {
            ((ViewGroup) playSeedsOverlay.getParent()).removeView(playSeedsOverlay);
        }

        FrameLayout root = new FrameLayout(this);
        playSeedsOverlay = root;

        View backdrop = new View(this);
        backdrop.setBackgroundColor(Color.argb(160, 0, 0, 0));
        backdrop.setOnClickListener(v -> dismissPlaySeedsModal());
        root.addView(backdrop, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad = dpToPx(16);
        panel.setPadding(pad, pad, pad, pad);

        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setShape(GradientDrawable.RECTANGLE);
        panelBg.setCornerRadius(dpToPx(6));
        panelBg.setColor(INVENTORY_BG);
        panelBg.setStroke(1, BORDER_DIM);
        panel.setBackground(panelBg);
        panel.setElevation(dpToPx(12));

        TextView header = new TextView(this);
        header.setText("PLAY SEEDS");
        header.setTextColor(FLAME_EMBER);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        header.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        header.setLetterSpacing(0.2f);
        header.setGravity(Gravity.CENTER);
        header.setPadding(0, dpToPx(4), 0, dpToPx(8));
        panel.addView(header);

        View sep = new View(this);
        GradientDrawable sepGrad = new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{ FLAME_DIM, FLAME_EMBER, FLAME_DIM });
        sep.setBackground(sepGrad);
        LinearLayout.LayoutParams sepP = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1);
        sepP.setMargins(dpToPx(8), 0, dpToPx(8), dpToPx(12));
        panel.addView(sep, sepP);

        // Custom Seed section
        addSeedSectionHeader(panel, "CUSTOM SEED", null, null);
        addStartMenuButton(panel, "Enter Seed Number...", true, v -> {
            reshowPlaySeedsOnStartMenu = true;
            dismissPlaySeedsModal();
            dismissStartMenu();
            nativeStartMenuResult(START_MENU_PLAY_SEED);
        });

        // Weekly Seed section — follows r/brogueforum's weekly contest
        final long weekly = currentWeeklySeed();
        addSeedSectionHeader(panel,
            "THIS WEEK'S SEED",
            "Follow the contest at r/brogueforum",
            "https://www.reddit.com/r/brogueforum/");
        addStartMenuButton(panel, String.valueOf(weekly), true, v -> {
            dismissPlaySeedsModal();
            dismissStartMenu();
            nativeStartMenuResultWithSeed(START_MENU_PLAY_SEED, weekly);
        });

        // Interesting Seeds section
        addSeedSectionHeader(panel, "INTERESTING SEEDS", null, null);
        for (String[] entry : INTERESTING_SEEDS) {
            final long seed;
            try { seed = Long.parseLong(entry[0]); }
            catch (NumberFormatException e) { continue; }
            addInterestingSeedRow(panel, seed, entry[1]);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.addView(panel);

        int panelWidth = Math.min(dpToPx(340),
            (int)(getResources().getDisplayMetrics().widthPixels * 0.85f));
        int maxHeight = (int)(getResources().getDisplayMetrics().heightPixels * 0.85f);
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
            panelWidth, Math.min(maxHeight, FrameLayout.LayoutParams.WRAP_CONTENT),
            Gravity.CENTER);
        root.addView(scroll, panelParams);

        addContentView(root, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));

        panel.setAlpha(0f);
        panel.setScaleX(0.94f);
        panel.setScaleY(0.94f);
        panel.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(220)
            .setInterpolator(new DecelerateInterpolator(1.5f))
            .start();
    }

    private void dismissPlaySeedsModal() {
        if (playSeedsOverlay != null && playSeedsOverlay.getParent() != null) {
            ((ViewGroup) playSeedsOverlay.getParent()).removeView(playSeedsOverlay);
        }
        playSeedsOverlay = null;
    }

    private void addSeedSectionHeader(LinearLayout panel, String title,
                                       String subtitle, String url) {
        TextView header = new TextView(this);
        header.setText(title);
        header.setTextColor(FLAME_EMBER);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        header.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        header.setLetterSpacing(0.15f);
        header.setPadding(dpToPx(4), dpToPx(14), 0,
            subtitle == null ? dpToPx(4) : dpToPx(2));
        panel.addView(header);

        if (subtitle != null) {
            TextView sub = new TextView(this);
            sub.setText(subtitle);
            sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            sub.setTypeface(Typeface.MONOSPACE);
            sub.setPadding(dpToPx(4), 0, 0, dpToPx(4));
            if (url != null) {
                sub.setTextColor(FLAME_EMBER);
                sub.setPaintFlags(sub.getPaintFlags()
                    | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
                sub.setOnClickListener(v -> openUrl(url));
            } else {
                sub.setTextColor(PALE_BLUE);
            }
            panel.addView(sub);
        }

        View sep = new View(this);
        GradientDrawable sepGrad = new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{ FLAME_DIM, FLAME_EMBER, FLAME_DIM });
        sep.setBackground(sepGrad);
        LinearLayout.LayoutParams sepP = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1);
        sepP.setMargins(dpToPx(4), 0, dpToPx(4), dpToPx(8));
        panel.addView(sep, sepP);
    }

    private void addInterestingSeedRow(LinearLayout panel, long seed, String description) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8));
        row.setMinimumHeight(dpToPx(44));

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dpToPx(4));
        bg.setColor(ITEM_BG);
        row.setBackground(new RippleDrawable(
            ColorStateList.valueOf(RIPPLE_GLOW), bg, null));

        TextView seedView = new TextView(this);
        seedView.setText(String.valueOf(seed));
        seedView.setTextColor(GHOST_WHITE);
        seedView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        seedView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        LinearLayout.LayoutParams seedP = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        seedP.setMargins(0, 0, dpToPx(10), 0);
        row.addView(seedView, seedP);

        TextView descView = new TextView(this);
        descView.setText(description);
        descView.setTextColor(PALE_BLUE);
        descView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        descView.setTypeface(Typeface.MONOSPACE);
        row.addView(descView, new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        row.setOnClickListener(v -> {
            dismissPlaySeedsModal();
            dismissStartMenu();
            nativeStartMenuResultWithSeed(START_MENU_PLAY_SEED, seed);
        });

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dpToPx(2), 0, dpToPx(2));
        panel.addView(row, p);
    }

    private void addActionRow(LinearLayout panel, String key, String label) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dpToPx(6), dpToPx(10), dpToPx(12), dpToPx(10));
        row.setMinimumHeight(dpToPx(44));
        row.setTag(R.id.action_key_tag, key);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dpToPx(3));
        bg.setColor(ITEM_BG);
        row.setBackground(new RippleDrawable(
            ColorStateList.valueOf(RIPPLE_GLOW), bg, null));

        row.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(0.98f).scaleY(0.98f).setDuration(60).start();
            } else if (e.getAction() == MotionEvent.ACTION_UP
                    || e.getAction() == MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
            }
            return false;
        });

        // Drag handle — starts row drag on touch-down (no long-press needed).
        ImageView handle = new ImageView(this);
        handle.setImageResource(R.drawable.ic_drag_handle);
        handle.setColorFilter(BORDER_ACTIVE);
        handle.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int handleSize = dpToPx(32);
        LinearLayout.LayoutParams handleP = new LinearLayout.LayoutParams(handleSize, handleSize);
        handleP.setMargins(0, 0, dpToPx(4), 0);
        row.addView(handle, handleP);
        handle.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                ClipData data = ClipData.newPlainText("brogue_action", key);
                View.DragShadowBuilder shadow = new ReorderDragShadow(row);
                if (row.startDragAndDrop(data, shadow, row, 0)) {
                    row.setAlpha(0.3f);
                }
                return true;
            }
            return false;
        });

        // Icon
        ImageButton icon = new ImageButton(this);
        icon.setImageResource(actionIconRes(key));
        icon.setColorFilter(PALE_BLUE);
        icon.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        icon.setBackground(null);
        int iconSize = dpToPx(24);
        LinearLayout.LayoutParams iconP = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconP.setMargins(0, 0, dpToPx(10), 0);
        row.addView(icon, iconP);

        // Label
        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(GHOST_WHITE);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        labelView.setTypeface(Typeface.MONOSPACE);
        row.addView(labelView, new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        // Checkmark toggle
        boolean pinned = isPinned(key);
        TextView check = new TextView(this);
        check.setText(pinned ? "\u2713" : "");
        check.setTextColor(FLAME_EMBER);
        check.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        check.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        check.setGravity(Gravity.CENTER);
        int checkSize = dpToPx(28);

        GradientDrawable checkBg = new GradientDrawable();
        checkBg.setShape(GradientDrawable.RECTANGLE);
        checkBg.setCornerRadius(dpToPx(3));
        checkBg.setColor(pinned ? ACTION_BG : Color.TRANSPARENT);
        checkBg.setStroke(1, BORDER_ACTIVE);
        check.setBackground(checkBg);

        row.addView(check, new LinearLayout.LayoutParams(checkSize, checkSize));

        // Tap the checkmark to toggle pin
        check.setOnClickListener(v -> {
            togglePin(key);
            boolean nowPinned = isPinned(key);
            check.setText(nowPinned ? "\u2713" : "");
            GradientDrawable cbg = (GradientDrawable) check.getBackground();
            cbg.setColor(nowPinned ? ACTION_BG : Color.TRANSPARENT);
            cbg.setStroke(1, BORDER_ACTIVE);
        });

        // Tap the row (not the checkmark) to execute the action
        row.setOnClickListener(v -> {
            hideActionsPanel(() -> executeAction(key));
        });

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dpToPx(2), 0, dpToPx(2));
        panel.addView(row, p);
    }

    private boolean getGameSetting(String key) {
        return getSharedPreferences("brogue_settings", MODE_PRIVATE)
            .getBoolean(key, false);
    }

    private void setGameSetting(String key, boolean value) {
        getSharedPreferences("brogue_settings", MODE_PRIVATE)
            .edit().putBoolean(key, value).apply();
    }

    private int getGameSettingInt(String key, int defaultValue) {
        return getSharedPreferences("brogue_settings", MODE_PRIVATE)
            .getInt(key, defaultValue);
    }

    private void setGameSettingInt(String key, int value) {
        getSharedPreferences("brogue_settings", MODE_PRIVATE)
            .edit().putInt(key, value).apply();
    }

    // Called from C via JNI to read a saved setting.
    public boolean getSettingBool(String key) {
        return getGameSetting(key);
    }

    // Called from C via JNI to read a saved int setting.
    public int getSettingInt(String key, int defaultValue) {
        return getGameSettingInt(key, defaultValue);
    }

    private static final String[] GRAPHICS_MODE_LABELS = {"Graphics: ASCII", "Graphics: Tiles", "Graphics: Hybrid"};

    private LinearLayout addSettingsRow(LinearLayout panel, String label) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));
        row.setMinimumHeight(dpToPx(44));

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dpToPx(3));
        bg.setColor(ITEM_BG);
        row.setBackground(new RippleDrawable(
            ColorStateList.valueOf(RIPPLE_GLOW), bg, null));

        row.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(0.98f).scaleY(0.98f).setDuration(60).start();
            } else if (e.getAction() == MotionEvent.ACTION_UP
                    || e.getAction() == MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
            }
            return false;
        });

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(GHOST_WHITE);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        labelView.setTypeface(Typeface.MONOSPACE);
        row.addView(labelView, new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dpToPx(2), 0, dpToPx(2));
        panel.addView(row, p);

        return row;
    }

    private void addGraphicsModeCycler(LinearLayout panel) {
        int mode = getGameSettingInt("graphics_mode", 0);
        if (mode < 0 || mode > 2) mode = 0;

        LinearLayout row = addSettingsRow(panel, GRAPHICS_MODE_LABELS[mode]);
        TextView labelView = (TextView) row.getChildAt(0);

        final int[] currentMode = {mode};
        row.setOnClickListener(v -> {
            currentMode[0] = (currentMode[0] + 1) % 3;
            setGameSettingInt("graphics_mode", currentMode[0]);
            labelView.setText(GRAPHICS_MODE_LABELS[currentMode[0]]);
            sendChar('G');
        });
    }

    private void addSettingsToggle(LinearLayout panel, String label, String prefKey, char gameKey) {
        LinearLayout row = addSettingsRow(panel, label);

        boolean on = getGameSetting(prefKey);
        TextView check = new TextView(this);
        check.setText(on ? "\u2713" : "");
        check.setTextColor(FLAME_EMBER);
        check.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        check.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        check.setGravity(Gravity.CENTER);
        int checkSize = dpToPx(28);

        GradientDrawable checkBg = new GradientDrawable();
        checkBg.setShape(GradientDrawable.RECTANGLE);
        checkBg.setCornerRadius(dpToPx(3));
        checkBg.setColor(on ? ACTION_BG : Color.TRANSPARENT);
        checkBg.setStroke(1, BORDER_ACTIVE);
        check.setBackground(checkBg);

        row.addView(check, new LinearLayout.LayoutParams(checkSize, checkSize));

        row.setOnClickListener(v -> {
            boolean nowOn = !getGameSetting(prefKey);
            setGameSetting(prefKey, nowOn);
            check.setText(nowOn ? "\u2713" : "");
            GradientDrawable cbg = (GradientDrawable) check.getBackground();
            cbg.setColor(nowOn ? ACTION_BG : Color.TRANSPARENT);
            cbg.setStroke(1, BORDER_ACTIVE);
            sendChar(gameKey);
        });
    }

    private void addSettingsAction(LinearLayout panel, String label, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));
        row.setMinimumHeight(dpToPx(44));

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dpToPx(3));
        bg.setColor(ITEM_BG);
        row.setBackground(new RippleDrawable(
            ColorStateList.valueOf(RIPPLE_GLOW), bg, null));

        row.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(0.98f).scaleY(0.98f).setDuration(60).start();
            } else if (e.getAction() == MotionEvent.ACTION_UP
                    || e.getAction() == MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
            }
            return false;
        });

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(GHOST_WHITE);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        labelView.setTypeface(Typeface.MONOSPACE);
        row.addView(labelView, new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        row.setOnClickListener(listener);

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dpToPx(2), 0, dpToPx(2));
        panel.addView(row, p);
    }

    private void addSettingsSeparator(LinearLayout panel) {
        View sep = new View(this);
        sep.setBackgroundColor(BORDER_DIM);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1);
        p.setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
        panel.addView(sep, p);
    }

    // ---- Inventory UI ----

    public void showInventory(final String json) {
        runOnUiThread(() -> {
            // Cancel any in-flight hide animation so its withEndAction
            // doesn't wipe our new content (e.g. nested ring-swap prompt).
            for (int i = 0; i < inventoryOverlay.getChildCount(); i++) {
                inventoryOverlay.getChildAt(i).animate().cancel();
            }
            inventoryOverlay.removeAllViews();
            currentlyExpandedDetail = null;

            try {
                JSONObject root = new JSONObject(json);
                String mode = root.optString("mode", "inventory");
                String prompt = root.optString("prompt", "");
                boolean selectMode = "select".equals(mode);
                JSONArray items = root.getJSONArray("items");

                int equippedCount = 0;
                if (items.length() > 0) {
                    equippedCount = items.getJSONObject(0).optInt("equippedCount", 0);
                }

                // Semi-transparent backdrop — tap to close, fades in
                View backdrop = new View(this);
                backdrop.setBackgroundColor(Color.argb(140, 0, 0, 0));
                backdrop.setOnClickListener(v -> sendKey(KeyEvent.KEYCODE_ESCAPE));
                inventoryOverlay.addView(backdrop, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
                backdrop.setAlpha(0f);
                backdrop.animate().alpha(1f).setDuration(280).start();

                // Inventory panel — right-aligned, scrollable
                ScrollView scrollView = new ScrollView(this);
                scrollView.setFillViewport(false);
                scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
                scrollView.setFadingEdgeLength(dpToPx(24));
                scrollView.setVerticalFadingEdgeEnabled(true);

                LinearLayout panel = new LinearLayout(this);
                panel.setOrientation(LinearLayout.VERTICAL);
                int pad = dpToPx(8);
                panel.setPadding(pad, pad, pad, pad);

                GradientDrawable panelBg = new GradientDrawable();
                panelBg.setShape(GradientDrawable.RECTANGLE);
                panelBg.setCornerRadii(new float[]{
                    dpToPx(4), dpToPx(4), 0, 0, 0, 0, dpToPx(4), dpToPx(4)});
                panelBg.setColor(INVENTORY_BG);
                panelBg.setStroke(1, BORDER_DIM);
                scrollView.setBackground(panelBg);

                // Header
                TextView header = new TextView(this);
                header.setText(selectMode && !prompt.isEmpty() ? prompt
                    : selectMode ? "SELECT ITEM" : "INVENTORY");
                header.setTextColor(selectMode ? PALE_BLUE : FLAME_EMBER);
                header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                header.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
                header.setLetterSpacing(0.15f);
                header.setPadding(dpToPx(4), dpToPx(2), 0, dpToPx(4));
                panel.addView(header);

                // Header separator
                View headerSep = new View(this);
                GradientDrawable sepGrad = new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{ FLAME_DIM, FLAME_EMBER, FLAME_DIM });
                headerSep.setBackground(sepGrad);
                LinearLayout.LayoutParams hSepP = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1);
                hSepP.setMargins(dpToPx(4), 0, dpToPx(4), dpToPx(6));
                panel.addView(headerSep, hSepP);

                if (items.length() == 0) {
                    TextView emptyMsg = new TextView(this);
                    emptyMsg.setText("Your pack is empty.");
                    emptyMsg.setTextColor(Color.argb(160, 140, 150, 190));
                    emptyMsg.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                    emptyMsg.setTypeface(Typeface.MONOSPACE, Typeface.ITALIC);
                    emptyMsg.setPadding(dpToPx(8), dpToPx(16), dpToPx(8), dpToPx(16));
                    emptyMsg.setGravity(Gravity.CENTER);
                    panel.addView(emptyMsg);
                }

                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.getJSONObject(i);

                    // Separator between equipped and unequipped
                    if (i == equippedCount && equippedCount > 0) {
                        View sep = new View(this);
                        sep.setBackgroundColor(BORDER_DIM);
                        LinearLayout.LayoutParams sepP = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 1);
                        sepP.setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
                        panel.addView(sep, sepP);
                    }

                    panel.addView(makeInventoryRow(item, scrollView, selectMode));
                }

                scrollView.addView(panel);

                int panelWidth = Math.min(dpToPx(340),
                    (int)(getResources().getDisplayMetrics().widthPixels * 0.75f));

                FrameLayout.LayoutParams scrollParams = new FrameLayout.LayoutParams(
                    panelWidth, FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.END);
                scrollParams.setMargins(0, dpToPx(8), dpToPx(EDGE_SAFE_DP), dpToPx(52));

                inventoryOverlay.addView(scrollView, scrollParams);
                inventoryOverlay.setVisibility(View.VISIBLE);

                // Slide-in animation
                scrollView.setTranslationX(panelWidth);
                scrollView.animate()
                    .translationX(0)
                    .setDuration(280)
                    .setInterpolator(new DecelerateInterpolator(1.5f))
                    .start();

            } catch (Exception e) {
                sendKey(KeyEvent.KEYCODE_ESCAPE);
            }
        });
    }

    public void hideInventory() {
        runOnUiThread(() -> {
            currentlyExpandedDetail = null;
            if (inventoryOverlay.getChildCount() < 2) {
                inventoryOverlay.setVisibility(View.GONE);
                inventoryOverlay.removeAllViews();
                return;
            }
            View backdrop = inventoryOverlay.getChildAt(0);
            View scrollView = inventoryOverlay.getChildAt(1);

            scrollView.animate()
                .translationX(scrollView.getWidth())
                .setDuration(200)
                .setInterpolator(new DecelerateInterpolator())
                .start();

            backdrop.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction(() -> {
                    inventoryOverlay.setVisibility(View.GONE);
                    inventoryOverlay.removeAllViews();
                })
                .start();
        });
    }

    // ---- Native text input dialog ----

    // Called from native code (JNI) to show a text input dialog.
    public void showTextInputDialog(final String prompt, final String defaultText,
                                     final int maxLen, final boolean numericOnly) {
        runOnUiThread(() -> {
            EditText input = new EditText(this);
            input.setTextColor(GHOST_WHITE);
            input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            input.setTypeface(Typeface.MONOSPACE);
            input.setInputType(numericOnly ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT);
            input.setFilters(new InputFilter[]{ new InputFilter.LengthFilter(maxLen) });
            input.setText(defaultText);
            input.setSelectAllOnFocus(true);
            input.setHighlightColor(Color.argb(80, 180, 120, 50));

            GradientDrawable inputBg = new GradientDrawable();
            inputBg.setShape(GradientDrawable.RECTANGLE);
            inputBg.setCornerRadius(dpToPx(3));
            inputBg.setColor(Color.argb(255, 20, 17, 42));
            inputBg.setStroke(1, FLAME_DIM);
            input.setBackground(inputBg);
            input.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));

            // Title
            TextView titleView = new TextView(this);
            titleView.setText(prompt);
            titleView.setTextColor(FLAME_EMBER);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            titleView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            titleView.setLetterSpacing(0.1f);
            titleView.setPadding(dpToPx(20), dpToPx(16), dpToPx(20), dpToPx(8));

            // Button row — inside the layout so they share the modal background
            LinearLayout buttonRow = new LinearLayout(this);
            buttonRow.setOrientation(LinearLayout.HORIZONTAL);
            buttonRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);

            Button cancelBtn = new Button(this);
            cancelBtn.setText("CANCEL");
            cancelBtn.setTextColor(PALE_BLUE);
            cancelBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            cancelBtn.setTypeface(Typeface.MONOSPACE);
            cancelBtn.setAllCaps(true);
            cancelBtn.setStateListAnimator(null);
            cancelBtn.setElevation(0);
            cancelBtn.setPadding(dpToPx(16), dpToPx(10), dpToPx(16), dpToPx(10));
            cancelBtn.setMinWidth(0);
            cancelBtn.setMinimumWidth(0);
            cancelBtn.setMinHeight(dpToPx(36));
            cancelBtn.setMinimumHeight(dpToPx(36));
            GradientDrawable cancelBg = new GradientDrawable();
            cancelBg.setShape(GradientDrawable.RECTANGLE);
            cancelBg.setCornerRadius(dpToPx(3));
            cancelBg.setColor(Color.TRANSPARENT);
            cancelBtn.setBackground(new RippleDrawable(
                ColorStateList.valueOf(RIPPLE_GLOW), cancelBg, null));

            Button okBtn = new Button(this);
            okBtn.setText("OK");
            okBtn.setTextColor(FLAME_EMBER);
            okBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            okBtn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            okBtn.setAllCaps(true);
            okBtn.setStateListAnimator(null);
            okBtn.setElevation(0);
            okBtn.setPadding(dpToPx(16), dpToPx(10), dpToPx(16), dpToPx(10));
            okBtn.setMinWidth(0);
            okBtn.setMinimumWidth(0);
            okBtn.setMinHeight(dpToPx(36));
            okBtn.setMinimumHeight(dpToPx(36));
            GradientDrawable okBg = new GradientDrawable();
            okBg.setShape(GradientDrawable.RECTANGLE);
            okBg.setCornerRadius(dpToPx(3));
            okBg.setColor(ACTION_BG);
            okBg.setStroke(1, BORDER_DIM);
            okBtn.setBackground(new RippleDrawable(
                ColorStateList.valueOf(RIPPLE_GLOW), okBg, null));

            LinearLayout.LayoutParams cancelP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
            cancelP.setMargins(0, 0, dpToPx(8), 0);
            buttonRow.addView(cancelBtn, cancelP);
            buttonRow.addView(okBtn);

            LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.addView(titleView);
            int hPad = dpToPx(20);
            LinearLayout.LayoutParams inputP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
            inputP.setMargins(hPad, dpToPx(4), hPad, dpToPx(8));
            layout.addView(input, inputP);
            LinearLayout.LayoutParams btnRowP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
            btnRowP.setMargins(hPad, 0, hPad, dpToPx(16));
            layout.addView(buttonRow, btnRowP);

            GradientDrawable dialogBg = new GradientDrawable();
            dialogBg.setShape(GradientDrawable.RECTANGLE);
            dialogBg.setCornerRadius(dpToPx(6));
            dialogBg.setColor(INVENTORY_BG);
            dialogBg.setStroke(1, BORDER_DIM);
            layout.setBackground(dialogBg);

            // Use the real full-screen dimensions (including nav bar area)
            // so the dialog position doesn't shift when the keyboard or
            // navigation bar appears/disappears.
            android.graphics.Point realSize = new android.graphics.Point();
            getWindowManager().getDefaultDisplay().getRealSize(realSize);
            int fullScreenWidth = realSize.x;

            AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
                .setView(layout)
                .setCancelable(true)
                .setOnCancelListener(d -> {
                    nativeTextInputResult(false, "");
                })
                .create();

            cancelBtn.setOnClickListener(v -> {
                nativeTextInputResult(false, "");
                dialog.dismiss();
            });
            okBtn.setOnClickListener(v -> {
                nativeTextInputResult(true, input.getText().toString());
                dialog.dismiss();
            });

            // Configure window before show() so there is no default
            // slide animation or gravity snap.
            android.view.Window window = dialog.getWindow();
            if (window != null) {
                window.setWindowAnimations(0);
                window.setBackgroundDrawable(dialogBg);
                window.setGravity(Gravity.CENTER);
                window.setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
                int minWidth = fullScreenWidth / 3;
                window.setLayout(Math.max(minWidth, ViewGroup.LayoutParams.WRAP_CONTENT),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            }

            dialog.setOnShowListener(d -> {
                if (window != null) {
                    // Match the activity's immersive flags so the nav bar
                    // stays hidden and the dialog doesn't shift.
                    window.getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
                }
                // Don't auto-show keyboard; let the user tap the input field.
                input.setFocusable(true);
                input.setFocusableInTouchMode(true);
                input.clearFocus();
            });

            dialog.show();
        });
    }

    // Native callback — signals the C thread with the dialog result.
    private native void nativeTextInputResult(boolean confirmed, String text);
    private native long nativeGetSeed();

    private View makeInventoryRow(JSONObject item, ScrollView scrollView, boolean selectMode) {
        boolean equipped = item.optBoolean("equipped", false);
        boolean selectable = item.optBoolean("selectable", true);
        char letter = item.optString("letter", "?").charAt(0);
        String name = item.optString("name", "???");
        String desc = item.optString("desc", "");
        String actions = item.optString("actions", "");
        int magicPolarity = item.optInt("magicPolarity", 0);
        String displayName = name + (equipped ? " (equipped)" : "");

        // Header row: magic indicator + name (+ chevron in inventory mode)
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
        headerRow.setMinimumHeight(dpToPx(44));
        headerRow.setContentDescription(displayName);

        GradientDrawable rowBg = new GradientDrawable();
        rowBg.setShape(GradientDrawable.RECTANGLE);
        rowBg.setCornerRadius(dpToPx(3));
        if (selectMode) {
            rowBg.setColor(selectable ? ITEM_BG : DISABLED_BG);
        } else {
            rowBg.setColor(equipped ? EQUIPPED_GLOW : ITEM_BG);
            if (equipped) rowBg.setStroke(dpToPx(1), FLAME_DIM);
        }
        headerRow.setBackground(new RippleDrawable(
            ColorStateList.valueOf(RIPPLE_GLOW), rowBg, null));

        headerRow.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(0.98f).scaleY(0.98f).setDuration(60).start();
            } else if (e.getAction() == MotionEvent.ACTION_UP
                    || e.getAction() == MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
            }
            return false;
        });

        addMagicIndicator(headerRow, magicPolarity);

        TextView nameView = new TextView(this);
        nameView.setText(displayName);
        nameView.setTextColor(selectable ? GHOST_WHITE
            : (selectMode ? DISABLED_TEXT : Color.argb(150, 120, 115, 130)));
        nameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        nameView.setTypeface(Typeface.MONOSPACE);
        nameView.setPadding(dpToPx(6), 0, 0, 0);
        nameView.setMaxLines(1);
        nameView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        headerRow.addView(nameView, new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        // Select mode: tap sends just the letter, no expandable detail
        if (selectMode) {
            if (selectable) {
                headerRow.setOnClickListener(v -> sendChar(letter));
            } else {
                headerRow.setClickable(false);
            }

            LinearLayout.LayoutParams rowP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
            rowP.setMargins(0, dpToPx(2), 0, dpToPx(2));
            headerRow.setLayoutParams(rowP);
            return headerRow;
        }

        // Inventory mode: wrap in vertical layout with expandable detail section
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);

        // Chevron
        TextView chevron = new TextView(this);
        chevron.setText("\u25BE"); // ▾
        chevron.setTextColor(Color.argb(120, 140, 150, 190));
        chevron.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        chevron.setPadding(dpToPx(4), 0, 0, 0);
        headerRow.addView(chevron);

        row.addView(headerRow);

        // Expandable detail section
        LinearLayout detailSection = new LinearLayout(this);
        detailSection.setOrientation(LinearLayout.VERTICAL);
        detailSection.setVisibility(View.GONE);
        detailSection.setPadding(dpToPx(12), dpToPx(4), dpToPx(8), dpToPx(8));

        GradientDrawable detailBg = new GradientDrawable();
        detailBg.setShape(GradientDrawable.RECTANGLE);
        detailBg.setCornerRadii(new float[]{
            0, 0, 0, 0, dpToPx(3), dpToPx(3), dpToPx(3), dpToPx(3)});
        detailBg.setColor(Color.argb(180, 15, 12, 32));
        detailSection.setBackground(detailBg);

        if (!desc.isEmpty()) {
            TextView descView = new TextView(this);
            descView.setText(desc);
            descView.setTextColor(PALE_BLUE);
            descView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            descView.setTypeface(Typeface.MONOSPACE);
            descView.setLineSpacing(0, 1.3f);
            descView.setPadding(0, dpToPx(4), 0, dpToPx(8));
            detailSection.addView(descView);
        }

        if (!actions.isEmpty()) {
            LinearLayout actionRow = new LinearLayout(this);
            actionRow.setOrientation(LinearLayout.HORIZONTAL);
            actionRow.setGravity(Gravity.START);

            for (String ak : actions.split(",")) {
                if (ak.isEmpty()) continue;
                char actionChar = ak.charAt(0);
                String label = actionLabel(actionChar);

                Button actionBtn = new Button(this);
                actionBtn.setText(label);
                actionBtn.setTextColor(GHOST_WHITE);
                actionBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                actionBtn.setTypeface(Typeface.MONOSPACE);
                actionBtn.setAllCaps(true);
                actionBtn.setStateListAnimator(null);
                actionBtn.setElevation(0);
                actionBtn.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8));
                actionBtn.setMinWidth(0);
                actionBtn.setMinimumWidth(0);
                actionBtn.setMinHeight(dpToPx(36));
                actionBtn.setMinimumHeight(dpToPx(36));
                actionBtn.setContentDescription(label + " " + name);

                GradientDrawable abg = new GradientDrawable();
                abg.setShape(GradientDrawable.RECTANGLE);
                abg.setCornerRadius(dpToPx(3));
                abg.setColor(ACTION_BG);
                abg.setStroke(1, BORDER_DIM);
                actionBtn.setBackground(new RippleDrawable(
                    ColorStateList.valueOf(RIPPLE_GLOW), abg, null));

                actionBtn.setOnTouchListener((v, e) -> {
                    if (e.getAction() == MotionEvent.ACTION_DOWN) {
                        v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(60).start();
                    } else if (e.getAction() == MotionEvent.ACTION_UP
                            || e.getAction() == MotionEvent.ACTION_CANCEL) {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(100)
                            .setInterpolator(new OvershootInterpolator(2f)).start();
                    }
                    return false;
                });

                final char actionKey = actionChar;
                actionBtn.setOnClickListener(v -> {
                    sendChar(letter);
                    v.postDelayed(() -> sendChar(actionKey), 50);
                });

                LinearLayout.LayoutParams btnP = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
                btnP.setMargins(0, 0, dpToPx(4), 0);
                actionRow.addView(actionBtn, btnP);
            }
            detailSection.addView(actionRow);
        }

        row.addView(detailSection);

        headerRow.setOnClickListener(v -> {
            if (detailSection.getVisibility() == View.GONE) {
                if (currentlyExpandedDetail != null
                        && currentlyExpandedDetail != detailSection) {
                    View prev = currentlyExpandedDetail;
                    prev.animate().alpha(0f).setDuration(120)
                        .withEndAction(() -> prev.setVisibility(View.GONE)).start();
                }
                currentlyExpandedDetail = detailSection;
                detailSection.setVisibility(View.VISIBLE);
                detailSection.setAlpha(0f);
                detailSection.animate().alpha(1f).setDuration(150).start();
                chevron.setText("\u25B4"); // ▴
                row.post(() -> {
                    int rowBottom = row.getTop() + row.getHeight();
                    int visibleBottom = scrollView.getScrollY() + scrollView.getHeight();
                    if (rowBottom > visibleBottom) {
                        scrollView.smoothScrollTo(0, rowBottom - scrollView.getHeight());
                    }
                });
            } else {
                detailSection.animate().alpha(0f).setDuration(120)
                    .withEndAction(() -> detailSection.setVisibility(View.GONE)).start();
                currentlyExpandedDetail = null;
                chevron.setText("\u25BE"); // ▾
            }
        });

        LinearLayout.LayoutParams rowP = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        rowP.setMargins(0, dpToPx(2), 0, dpToPx(2));
        row.setLayoutParams(rowP);

        return row;
    }

    // Simplified row for item selection mode — tap sends just the letter.
    private void addMagicIndicator(LinearLayout row, int magicPolarity) {
        if (magicPolarity == 0) return;
        TextView v = new TextView(this);
        v.setText(magicPolarity > 0 ? "\u29F3" : "\u29F2");
        v.setTextColor(magicPolarity > 0 ? GOOD_MAGIC : BAD_MAGIC);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        v.setTypeface(Typeface.MONOSPACE);
        v.setPadding(dpToPx(4), 0, 0, 0);
        row.addView(v);
    }

    private String actionLabel(char key) {
        switch (key) {
            case 'a': return "Apply";
            case 'e': return "Equip";
            case 'r': return "Remove";
            case 'c': return "Call";
            case 'd': return "Drop";
            case 't': return "Throw";
            default:  return String.valueOf(key);
        }
    }

    // Send a character as an SDL text input event via key dispatch.
    // For letters a-z, we use the corresponding KEYCODE_A..Z.
    private void sendChar(char c) {
        if (c >= 'a' && c <= 'z') {
            sendKey(KeyEvent.KEYCODE_A + (c - 'a'));
        } else if (c >= 'A' && c <= 'Z') {
            sendKeyWithShift(KeyEvent.KEYCODE_A + (c - 'A'));
        } else {
            // Map symbols to their keycodes, with shift where needed
            switch (c) {
                case '\\': sendKey(KeyEvent.KEYCODE_BACKSLASH); break;
                case ']':  sendKey(KeyEvent.KEYCODE_RIGHT_BRACKET); break;
                case '[':  sendKey(KeyEvent.KEYCODE_LEFT_BRACKET); break;
                case '~':  sendKeyWithShift(KeyEvent.KEYCODE_GRAVE); break;
                case '`':  sendKey(KeyEvent.KEYCODE_GRAVE); break;
                case '-':  sendKey(KeyEvent.KEYCODE_MINUS); break;
                case '=':  sendKey(KeyEvent.KEYCODE_EQUALS); break;
                case ' ':  sendKey(KeyEvent.KEYCODE_SPACE); break;
                default:   sendKey(KeyEvent.KEYCODE_ESCAPE); break;
            }
        }
    }

    private void sendKeyWithShift(int keyCode) {
        long now = android.os.SystemClock.uptimeMillis();
        dispatchKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode,
            0, KeyEvent.META_SHIFT_LEFT_ON));
        dispatchKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode,
            0, KeyEvent.META_SHIFT_LEFT_ON));
    }

    public void setOverlayVisible(final boolean visible) {
        runOnUiThread(() -> {
            gameOverlay.setVisibility(visible ? View.VISIBLE : View.GONE);
        });
    }

    public void setLoadingVisible(final boolean visible) {
        runOnUiThread(() -> {
            if (visible) {
                if (loadingOverlay == null) {
                    TextView tv = new TextView(this);
                    tv.setText("LOADING");
                    tv.setTextColor(PALE_BLUE);
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                    tv.setTypeface(Typeface.MONOSPACE);
                    tv.setLetterSpacing(0.15f);
                    tv.setGravity(Gravity.CENTER);
                    tv.setBackgroundColor(Color.BLACK);

                    loadingOverlay = tv;
                    addContentView(loadingOverlay, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
                }
                loadingOverlay.setVisibility(View.VISIBLE);
            } else {
                if (loadingOverlay != null) {
                    loadingOverlay.setVisibility(View.GONE);
                }
            }
        });
    }

    // ---- Bottom bar buttons ----

    private Button makeBarButton(String label) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setTextColor(PALE_BLUE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        btn.setTypeface(Typeface.MONOSPACE);
        btn.setLetterSpacing(0.05f);
        btn.setAllCaps(true);
        btn.setStateListAnimator(null);
        btn.setElevation(0);
        btn.setPadding(dpToPx(2), 0, dpToPx(2), 0);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dpToPx(2));
        bg.setColor(DEEP_INDIGO);
        bg.setStroke(1, BORDER_DIM);

        btn.setBackground(new RippleDrawable(
            ColorStateList.valueOf(RIPPLE_GLOW), bg, null));

        btn.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(60).start();
            } else if (e.getAction() == MotionEvent.ACTION_UP
                    || e.getAction() == MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1f).scaleY(1f).setDuration(120)
                    .setInterpolator(new OvershootInterpolator(2.5f)).start();
            }
            return false;
        });

        return btn;
    }

    private ImageButton makeIconBarButton(int drawableResId) {
        ImageButton btn = new ImageButton(this);
        btn.setImageResource(drawableResId);
        btn.setColorFilter(PALE_BLUE);
        btn.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        int pad = dpToPx(10);
        btn.setPadding(pad, pad, pad, pad);
        btn.setStateListAnimator(null);
        btn.setElevation(0);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dpToPx(2));
        bg.setColor(DEEP_INDIGO);
        bg.setStroke(1, BORDER_DIM);

        btn.setBackground(new RippleDrawable(
            ColorStateList.valueOf(RIPPLE_GLOW), bg, null));

        btn.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(60).start();
            } else if (e.getAction() == MotionEvent.ACTION_UP
                    || e.getAction() == MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1f).scaleY(1f).setDuration(120)
                    .setInterpolator(new OvershootInterpolator(2.5f)).start();
            }
            return false;
        });

        return btn;
    }

    // ---- Submenu items: label on left, key hint on right ----

    private View makeSubmenuItem(String label, String hint) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dpToPx(12), dpToPx(6), dpToPx(10), dpToPx(6));
        row.setMinimumHeight(dpToPx(40));
        row.setClickable(true);
        row.setFocusable(true);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dpToPx(2));
        bg.setColor(Color.TRANSPARENT);

        row.setBackground(new RippleDrawable(
            ColorStateList.valueOf(RIPPLE_GLOW), bg, null));

        row.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                v.animate().translationX(dpToPx(2)).setDuration(60).start();
            } else if (e.getAction() == MotionEvent.ACTION_UP
                    || e.getAction() == MotionEvent.ACTION_CANCEL) {
                v.animate().translationX(0).setDuration(100).start();
            }
            return false;
        });

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(GHOST_WHITE);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        labelView.setTypeface(Typeface.MONOSPACE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(labelView, lp);

        TextView hintView = new TextView(this);
        hintView.setText(hint);
        hintView.setTextColor(FLAME_DIM);
        hintView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        hintView.setTypeface(Typeface.MONOSPACE);
        row.addView(hintView);

        return row;
    }

    // ---- Drawables ----

    private GradientDrawable makeSubmenuBackground() {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dpToPx(3));
        bg.setColor(SUBMENU_BG);
        bg.setStroke(1, BORDER_DIM);
        return bg;
    }

    private GradientDrawable makeBarBackground() {
        GradientDrawable bg = new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{ Color.TRANSPARENT, VOID_BLACK, VOID_BLACK });
        bg.setCornerRadius(0);
        return bg;
    }

    // ---- Animations ----

    private View submenuBackdrop;

    private void expandSubmenu(View submenu, View toggle) {
        if (submenuBackdrop == null) {
            submenuBackdrop = new View(this);
        }
        submenuBackdrop.setOnClickListener(v -> collapseSubmenu(submenu, toggle));
        if (submenuBackdrop.getParent() != null) {
            ((ViewGroup) submenuBackdrop.getParent()).removeView(submenuBackdrop);
        }
        gameOverlay.addView(submenuBackdrop, 0, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));

        submenu.setAlpha(0f);
        submenu.setTranslationY(dpToPx(8));
        submenu.setVisibility(View.VISIBLE);
        submenu.animate()
            .alpha(1f).translationY(0)
            .setDuration(150)
            .setInterpolator(new DecelerateInterpolator())
            .start();
        animateToggle(toggle, true);
    }

    private void collapseSubmenu(View submenu, View toggle) {
        if (submenu.getVisibility() != View.VISIBLE) return;
        if (submenuBackdrop != null && submenuBackdrop.getParent() != null) {
            ((ViewGroup) submenuBackdrop.getParent()).removeView(submenuBackdrop);
        }
        submenu.animate()
            .alpha(0f).translationY(dpToPx(6))
            .setDuration(100)
            .withEndAction(() -> submenu.setVisibility(View.GONE))
            .start();
        animateToggle(toggle, false);
    }

    private void animateToggle(View v, boolean active) {
        GradientDrawable bg = extractBackground(v);
        if (bg == null) return;

        int from = active ? DEEP_INDIGO : Color.argb(200, 50, 35, 15);
        int to   = active ? Color.argb(200, 50, 35, 15) : DEEP_INDIGO;
        ValueAnimator anim = ValueAnimator.ofObject(new ArgbEvaluator(), from, to);
        anim.setDuration(150);
        anim.addUpdateListener(a -> bg.setColor((int) a.getAnimatedValue()));
        anim.start();

        bg.setStroke(1, active ? BORDER_ACTIVE : BORDER_DIM);

        if (v instanceof ImageButton) {
            ((ImageButton) v).setColorFilter(active ? FLAME_EMBER : PALE_BLUE);
        } else if (v instanceof TextView) {
            ((TextView) v).setTextColor(active ? FLAME_EMBER : PALE_BLUE);
        }
    }

    private void pulseButton(View v) {
        v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(80)
            .withEndAction(() ->
                v.animate().scaleX(1f).scaleY(1f).setDuration(120)
                    .setInterpolator(new OvershootInterpolator(3f)).start()
            ).start();
    }

    private GradientDrawable extractBackground(View v) {
        if (v.getBackground() instanceof RippleDrawable) {
            RippleDrawable rd = (RippleDrawable) v.getBackground();
            if (rd.getNumberOfLayers() > 0
                    && rd.getDrawable(0) instanceof GradientDrawable) {
                return (GradientDrawable) rd.getDrawable(0);
            }
        }
        return null;
    }

    // ---- Utilities ----

    private void sendKey(int keyCode) {
        dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
        dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
