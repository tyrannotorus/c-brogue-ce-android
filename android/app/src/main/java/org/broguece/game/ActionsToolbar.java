package org.broguece.game;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;

/** Owns the bottom action toolbar: pinned icon buttons + hamburger menu with
 *  its Actions / Settings / Exit submenu, and the full Actions panel (the
 *  reorderable list of every action with a pin toggle). Settings and Exit
 *  taps delegate back to BrogueActivity via the callbacks supplied at
 *  construction; everything else is internal. */
final class ActionsToolbar {

    private static final String PREFS        = "brogue_toolbar";
    private static final String PREF_PINNED  = "pinned_actions";
    private static final String PREF_ORDER   = "action_order";

    // Registered actions: {key, human label}. The pinned subset of this set
    // appears in the toolbar; the full set appears in the Actions panel.
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

    private final BrogueActivity activity;
    private final FrameLayout gameOverlay;       // host for submenu-dismiss backdrop
    private final FrameLayout inventoryOverlay;  // host for the Actions panel
    private final Runnable onSettingsClicked;
    private final Runnable onExitClicked;

    private LinearLayout toolbarContainer;
    private LinearLayout submenu;
    private View menuBtn;
    private View submenuBackdrop;
    private java.util.List<String> cachedActionOrder;

    ActionsToolbar(BrogueActivity activity,
                   FrameLayout gameOverlay,
                   FrameLayout inventoryOverlay,
                   Runnable onSettingsClicked,
                   Runnable onExitClicked) {
        this.activity = activity;
        this.gameOverlay = gameOverlay;
        this.inventoryOverlay = inventoryOverlay;
        this.onSettingsClicked = onSettingsClicked;
        this.onExitClicked = onExitClicked;
    }

    // ---- Public surface -----------------------------------------------------

    /** Builds the entire bottom group (submenu card stacked above the bar) and
     *  returns it for BrogueActivity to add to gameOverlay. */
    View build() {
        submenu = new LinearLayout(activity);
        submenu.setOrientation(LinearLayout.VERTICAL);
        submenu.setBackground(makeSubmenuBackground());
        submenu.setPadding(dp(3), dp(4), dp(3), dp(3));
        submenu.setVisibility(View.GONE);
        submenu.setElevation(dp(8));

        menuBtn = makeIconBarButton(R.drawable.ic_menu);

        submenu.addView(makeSubmenuItem("Actions", v -> {
            collapseSubmenu();
            showActionsPanel();
        }), submenuItemParams());

        submenu.addView(makeSubmenuItem("Settings", v -> {
            collapseSubmenu();
            onSettingsClicked.run();
        }), submenuItemParams());

        View divider = new View(activity);
        divider.setBackgroundColor(Palette.BORDER_DIM);
        LinearLayout.LayoutParams divP = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1);
        divP.setMargins(dp(4), dp(4), dp(4), dp(4));
        submenu.addView(divider, divP);

        submenu.addView(makeSubmenuItem("Exit", v -> {
            collapseSubmenu();
            onExitClicked.run();
        }), submenuItemParams());

        menuBtn.setOnClickListener(v -> {
            resetMouseToggle();
            if (submenu.getVisibility() == View.VISIBLE) {
                collapseSubmenu();
            } else {
                expandSubmenu();
            }
        });

        toolbarContainer = new LinearLayout(activity);
        toolbarContainer.setOrientation(LinearLayout.HORIZONTAL);
        toolbarContainer.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        toolbarContainer.setBackground(makeBarBackground());
        int barPad = dp(4);
        toolbarContainer.setPadding(barPad, barPad, barPad, barPad);

        LinearLayout bottomGroup = new LinearLayout(activity);
        bottomGroup.setOrientation(LinearLayout.VERTICAL);
        bottomGroup.setGravity(Gravity.END);
        bottomGroup.addView(submenu, new LinearLayout.LayoutParams(
            dp(170), LinearLayout.LayoutParams.WRAP_CONTENT));
        bottomGroup.addView(toolbarContainer, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));

        rebuildToolbar();
        return bottomGroup;
    }

    /** Dismisses the submenu if open. No-op otherwise. */
    void collapseSubmenu() {
        if (submenu == null || submenu.getVisibility() != View.VISIBLE) return;
        if (submenuBackdrop != null && submenuBackdrop.getParent() != null) {
            ((ViewGroup) submenuBackdrop.getParent()).removeView(submenuBackdrop);
        }
        submenu.animate()
            .alpha(0f).translationY(dp(6))
            .setDuration(100)
            .withEndAction(() -> submenu.setVisibility(View.GONE))
            .start();
        animateToggle(menuBtn, false);
    }

    // ---- Pinned state / action order persistence ---------------------------

    private SharedPreferences toolbarPrefs() {
        return activity.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE);
    }

    private java.util.Set<String> getPinned() {
        SharedPreferences prefs = toolbarPrefs();
        if (!prefs.contains(PREF_PINNED)) return new java.util.HashSet<>(DEFAULT_PINNED);
        return new java.util.HashSet<>(prefs.getStringSet(PREF_PINNED, DEFAULT_PINNED));
    }

    private void setPinned(java.util.Set<String> pinned) {
        toolbarPrefs().edit().putStringSet(PREF_PINNED, pinned).apply();
    }

    private boolean isPinned(String key) {
        return getPinned().contains(key);
    }

    private void togglePin(String key) {
        java.util.Set<String> pinned = getPinned();
        if (!pinned.remove(key)) pinned.add(key);
        setPinned(pinned);
        rebuildToolbar();
    }

    /** Master display order for the Actions panel and pinned toolbar ordering.
     *  Unknown / missing keys are appended in registry order so newly-added
     *  actions always appear. */
    private java.util.List<String> getActionOrder() {
        if (cachedActionOrder != null) return cachedActionOrder;

        String json = toolbarPrefs().getString(PREF_ORDER, null);
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
        toolbarPrefs().edit().putString(PREF_ORDER, arr.toString()).apply();
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

    // ---- Action execution --------------------------------------------------

    private void executeAction(String key) {
        switch (key) {
            case "inventory": KeyInput.sendKey(activity, KeyEvent.KEYCODE_I); break;
            case "mouse":     KeyInput.sendKey(activity, KeyEvent.KEYCODE_MOVE_HOME); break;
            case "click":     KeyInput.sendKey(activity, KeyEvent.KEYCODE_ENTER); break;
            case "search":    KeyInput.sendChar(activity, 's'); break;
            case "explore":   KeyInput.sendChar(activity, 'x'); break;
            case "wait":      KeyInput.sendChar(activity, 'z'); break;
            case "rest":      KeyInput.sendChar(activity, 'Z'); break;
            case "autopilot": KeyInput.sendChar(activity, 'A'); break;
        }
    }

    // ---- Toolbar rebuild ---------------------------------------------------

    private void rebuildToolbar() {
        if (toolbarContainer == null) return;
        toolbarContainer.removeAllViews();

        java.util.Set<String> pinned = getPinned();
        int btnSize = dp(44);
        int btnMargin = dp(3);

        for (String key : getActionOrder()) {
            if (!pinned.contains(key)) continue;

            View btn = makeIconBarButton(actionIconRes(key));
            btn.setTag(R.id.action_key_tag, key);

            if ("mouse".equals(key)) {
                btn.setOnClickListener(v -> {
                    collapseSubmenu();
                    executeAction("mouse");
                    boolean active = v.getTag() instanceof Boolean && (boolean) v.getTag();
                    active = !active;
                    v.setTag(active);
                    animateToggle(v, active);
                });
            } else {
                btn.setOnClickListener(v -> {
                    collapseSubmenu();
                    resetMouseToggle();
                    executeAction(key);
                    pulseButton(v);
                });
            }

            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(btnSize, btnSize);
            p.setMargins(btnMargin, 0, btnMargin, 0);
            toolbarContainer.addView(btn, p);
        }

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
            if (key.equals(child.getTag(R.id.action_key_tag))) return child;
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

    // ---- Actions panel -----------------------------------------------------

    private void showActionsPanel() {
        inventoryOverlay.removeAllViews();

        View backdrop = new View(activity);
        backdrop.setBackgroundColor(Color.argb(140, 0, 0, 0));
        backdrop.setOnClickListener(v -> hideActionsPanel());
        inventoryOverlay.addView(backdrop, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));
        backdrop.setAlpha(0f);
        backdrop.animate().alpha(1f).setDuration(280).start();

        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(10);
        panel.setPadding(pad, pad, pad, pad);

        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setShape(GradientDrawable.RECTANGLE);
        panelBg.setCornerRadii(new float[]{
            dp(4), dp(4), 0, 0, 0, 0, dp(4), dp(4)});
        panelBg.setColor(Palette.INVENTORY_BG);
        panelBg.setStroke(1, Palette.BORDER_DIM);

        ScrollView scrollView = new ScrollView(activity);
        scrollView.setBackground(panelBg);
        scrollView.addView(panel);

        TextView header = new TextView(activity);
        header.setText("ACTIONS");
        header.setTextColor(Palette.FLAME_EMBER);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        header.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        header.setLetterSpacing(0.15f);
        header.setPadding(dp(4), dp(2), 0, dp(4));
        panel.addView(header);

        View headerSep = new View(activity);
        GradientDrawable sepGrad = new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{ Palette.FLAME_DIM, Palette.FLAME_EMBER, Palette.FLAME_DIM });
        headerSep.setBackground(sepGrad);
        LinearLayout.LayoutParams hSepP = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1);
        hSepP.setMargins(dp(4), 0, dp(4), dp(8));
        panel.addView(headerSep, hSepP);

        LinearLayout rowsContainer = new LinearLayout(activity);
        rowsContainer.setOrientation(LinearLayout.VERTICAL);
        rowsContainer.setOnDragListener(rowsDragListener);
        panel.addView(rowsContainer, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));

        for (String key : getActionOrder()) {
            addActionRow(rowsContainer, key, actionLabel(key));
        }

        int panelWidth = Math.min(dp(280),
            (int)(activity.getResources().getDisplayMetrics().widthPixels * 0.6f));

        FrameLayout.LayoutParams scrollParams = new FrameLayout.LayoutParams(
            panelWidth, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM | Gravity.END);
        scrollParams.setMargins(0, dp(8), dp(BrogueActivity.EDGE_SAFE_DP), dp(52));

        inventoryOverlay.addView(scrollView, scrollParams);
        inventoryOverlay.setVisibility(View.VISIBLE);

        scrollView.setTranslationY(dp(40));
        scrollView.setAlpha(0f);
        scrollView.animate()
            .translationY(0).alpha(1f)
            .setDuration(200)
            .setInterpolator(new DecelerateInterpolator(1.5f))
            .start();
    }

    private void hideActionsPanel() { hideActionsPanel(null); }

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
            .translationY(dp(30)).alpha(0f)
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

    private void addActionRow(LinearLayout panel, String key, String label) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(6), dp(10), dp(12), dp(10));
        row.setMinimumHeight(dp(44));
        row.setTag(R.id.action_key_tag, key);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(3));
        bg.setColor(Palette.ITEM_BG);
        row.setBackground(new RippleDrawable(
            ColorStateList.valueOf(Palette.RIPPLE_GLOW), bg, null));

        row.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(0.98f).scaleY(0.98f).setDuration(60).start();
            } else if (e.getAction() == MotionEvent.ACTION_UP
                    || e.getAction() == MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
            }
            return false;
        });

        ImageView handle = new ImageView(activity);
        handle.setImageResource(R.drawable.ic_drag_handle);
        handle.setColorFilter(Palette.BORDER_ACTIVE);
        handle.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int handleSize = dp(32);
        LinearLayout.LayoutParams handleP = new LinearLayout.LayoutParams(handleSize, handleSize);
        handleP.setMargins(0, 0, dp(4), 0);
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

        ImageButton icon = new ImageButton(activity);
        icon.setImageResource(actionIconRes(key));
        icon.setColorFilter(Palette.PALE_BLUE);
        icon.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        icon.setBackground(null);
        int iconSize = dp(24);
        LinearLayout.LayoutParams iconP = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconP.setMargins(0, 0, dp(10), 0);
        row.addView(icon, iconP);

        TextView labelView = new TextView(activity);
        labelView.setText(label);
        labelView.setTextColor(Palette.GHOST_WHITE);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        labelView.setTypeface(Typeface.MONOSPACE);
        row.addView(labelView, new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        boolean pinned = isPinned(key);
        TextView check = new TextView(activity);
        check.setText(pinned ? "\u2713" : "");
        check.setTextColor(Palette.FLAME_EMBER);
        check.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        check.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        check.setGravity(Gravity.CENTER);
        int checkSize = dp(28);

        GradientDrawable checkBg = new GradientDrawable();
        checkBg.setShape(GradientDrawable.RECTANGLE);
        checkBg.setCornerRadius(dp(3));
        checkBg.setColor(pinned ? Palette.ACTION_BG : Color.TRANSPARENT);
        checkBg.setStroke(1, Palette.BORDER_ACTIVE);
        check.setBackground(checkBg);

        row.addView(check, new LinearLayout.LayoutParams(checkSize, checkSize));

        check.setOnClickListener(v -> {
            togglePin(key);
            boolean nowPinned = isPinned(key);
            check.setText(nowPinned ? "\u2713" : "");
            GradientDrawable cbg = (GradientDrawable) check.getBackground();
            cbg.setColor(nowPinned ? Palette.ACTION_BG : Color.TRANSPARENT);
            cbg.setStroke(1, Palette.BORDER_ACTIVE);
        });

        row.setOnClickListener(v -> hideActionsPanel(() -> executeAction(key)));

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(2), 0, dp(2));
        panel.addView(row, p);
    }

    // ---- Drag-reorder ------------------------------------------------------

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

    // Index-based slot math (rather than live getY per row) avoids the stale
    // child position that live reordering would otherwise produce for one
    // frame after a swap, which causes thrashing. While the finger is still
    // within the dragged row's own slot we don't swap — that's the dead-zone.
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

    // ---- Submenu animation -------------------------------------------------

    private void expandSubmenu() {
        if (submenuBackdrop == null) submenuBackdrop = new View(activity);
        submenuBackdrop.setOnClickListener(v -> collapseSubmenu());
        if (submenuBackdrop.getParent() != null) {
            ((ViewGroup) submenuBackdrop.getParent()).removeView(submenuBackdrop);
        }
        gameOverlay.addView(submenuBackdrop, 0, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));

        submenu.setAlpha(0f);
        submenu.setTranslationY(dp(8));
        submenu.setVisibility(View.VISIBLE);
        submenu.animate()
            .alpha(1f).translationY(0)
            .setDuration(150)
            .setInterpolator(new DecelerateInterpolator())
            .start();
        animateToggle(menuBtn, true);
    }

    private void animateToggle(View v, boolean active) {
        GradientDrawable bg = extractBackground(v);
        if (bg == null) return;

        int from = active ? Palette.DEEP_INDIGO : Palette.TOGGLE_ACTIVE;
        int to   = active ? Palette.TOGGLE_ACTIVE : Palette.DEEP_INDIGO;
        ValueAnimator anim = ValueAnimator.ofObject(new ArgbEvaluator(), from, to);
        anim.setDuration(150);
        anim.addUpdateListener(a -> bg.setColor((int) a.getAnimatedValue()));
        anim.start();

        bg.setStroke(1, active ? Palette.BORDER_ACTIVE : Palette.BORDER_DIM);

        if (v instanceof ImageButton) {
            ((ImageButton) v).setColorFilter(active ? Palette.FLAME_EMBER : Palette.PALE_BLUE);
        } else if (v instanceof TextView) {
            ((TextView) v).setTextColor(active ? Palette.FLAME_EMBER : Palette.PALE_BLUE);
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

    // ---- View factories ----------------------------------------------------

    private ImageButton makeIconBarButton(int drawableResId) {
        ImageButton btn = new ImageButton(activity);
        btn.setImageResource(drawableResId);
        btn.setColorFilter(Palette.PALE_BLUE);
        btn.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        int pad = dp(10);
        btn.setPadding(pad, pad, pad, pad);
        btn.setStateListAnimator(null);
        btn.setElevation(0);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(2));
        bg.setColor(Palette.DEEP_INDIGO);
        bg.setStroke(1, Palette.BORDER_DIM);

        btn.setBackground(new RippleDrawable(
            ColorStateList.valueOf(Palette.RIPPLE_GLOW), bg, null));

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

    private View makeSubmenuItem(String label, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(6), dp(10), dp(6));
        row.setMinimumHeight(dp(40));
        row.setClickable(true);
        row.setFocusable(true);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(2));
        bg.setColor(Color.TRANSPARENT);
        row.setBackground(new RippleDrawable(
            ColorStateList.valueOf(Palette.RIPPLE_GLOW), bg, null));

        row.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                v.animate().translationX(dp(2)).setDuration(60).start();
            } else if (e.getAction() == MotionEvent.ACTION_UP
                    || e.getAction() == MotionEvent.ACTION_CANCEL) {
                v.animate().translationX(0).setDuration(100).start();
            }
            return false;
        });

        TextView labelView = new TextView(activity);
        labelView.setText(label);
        labelView.setTextColor(Palette.GHOST_WHITE);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        labelView.setTypeface(Typeface.MONOSPACE);
        row.addView(labelView, new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        row.setOnClickListener(listener);
        return row;
    }

    private LinearLayout.LayoutParams submenuItemParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(1), 0, dp(1));
        return p;
    }

    private GradientDrawable makeSubmenuBackground() {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(3));
        bg.setColor(Palette.SUBMENU_BG);
        bg.setStroke(1, Palette.BORDER_DIM);
        return bg;
    }

    private GradientDrawable makeBarBackground() {
        GradientDrawable bg = new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{ Color.TRANSPARENT, Palette.VOID_BLACK, Palette.VOID_BLACK });
        bg.setCornerRadius(0);
        return bg;
    }

    private int dp(int v) { return activity.dpToPx(v); }
}
