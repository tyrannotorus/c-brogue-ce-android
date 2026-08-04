package org.broguece.game;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

/** The win sequence, shown once the engine's whiteout has filled the screen:
 *  two sunlit text pages over a white backdrop, then a fade to black and the
 *  treasure, achievement and final-score panels. Every page advances on tap;
 *  the last one leaves the screen black for the title to fade in under. */
final class VictoryModal {

    private final BrogueActivity activity;
    private FrameLayout root;
    private View whiteVeil;
    private View blackVeil;
    private View currentPage;
    private JSONObject data;
    private int pageIndex;

    VictoryModal(BrogueActivity activity) {
        this.activity = activity;
    }

    /** Called from C after the flood completes; blocks the engine thread
     *  until the last page is tapped. */
    void showSequence(final String json) {
        activity.runOnUiThread(() -> {
            ensureRoot();
            try {
                data = new JSONObject(json);
            } catch (Exception e) {
                data = new JSONObject();
            }
            pageIndex = 0;
            showPage();
        });
    }

    /** Engine returned to the title screen — reveal it under the overlay. */
    void fadeOutOverlay() {
        if (root == null) {
            return;
        }
        if (currentPage != null) {
            root.removeView(currentPage);
            currentPage = null;
        }
        // The restored start menu is added above this overlay, so raise the
        // black again or the menu pops in over the fade instead of under it.
        root.bringToFront();
        // Fading the root group can composite per-child, flashing the white
        // veil through the black; drop it and fade the black leaf alone.
        if (whiteVeil != null) {
            root.removeView(whiteVeil);
            whiteVeil = null;
        }
        // Let the title's flame loop put up a frame before revealing it.
        blackVeil.animate()
            .alpha(0f)
            .setStartDelay(300)
            .setDuration(900)
            .setInterpolator(new DecelerateInterpolator(1.5f))
            .withEndAction(this::removeOverlay)
            .start();
    }

    void removeOverlay() {
        if (root != null && root.getParent() != null) {
            ((android.view.ViewGroup) root.getParent()).removeView(root);
        }
        root = null;
        whiteVeil = null;
        blackVeil = null;
        currentPage = null;
    }

    private void ensureRoot() {
        if (root != null) return;

        root = new FrameLayout(activity);
        // Swallow every touch that no page consumes — nothing may reach the
        // game surface during the sequence or the fades.
        root.setClickable(true);

        whiteVeil = new View(activity);
        whiteVeil.setBackgroundColor(Color.WHITE);
        root.addView(whiteVeil, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));

        blackVeil = new View(activity);
        blackVeil.setBackgroundColor(Color.BLACK);
        blackVeil.setAlpha(0f);
        root.addView(blackVeil, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));

        activity.addContentView(root, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));
    }

    /** Detach a page's tap handling so it can't fire again mid-fade. */
    private static void deafen(View page) {
        if (page == null) return;
        page.setOnClickListener(null);
        page.setClickable(false);
    }

    private void advance() {
        pageIndex++;

        if (pageIndex == 2) {
            // Sunlit pages done — fade to black, then the dark panels.
            final View old = currentPage;
            currentPage = null;
            deafen(old);
            if (old != null) {
                old.animate().alpha(0f).setDuration(250)
                    .withEndAction(() -> { if (root != null) root.removeView(old); })
                    .start();
            }
            blackVeil.animate()
                .alpha(1f)
                .setDuration(800)
                .setInterpolator(new DecelerateInterpolator(1.5f))
                .withEndAction(this::showPage)
                .start();
            return;
        }

        if (pageIndex == 5) {
            // Let the last panel fade away completely, then cut the stinger in.
            final View old = currentPage;
            currentPage = null;
            deafen(old);
            if (old != null) {
                old.animate().alpha(0f).setDuration(800)
                    .withEndAction(() -> {
                        if (root != null) root.removeView(old);
                        showPage();
                    })
                    .start();
            } else {
                showPage();
            }
            return;
        }

        if (pageIndex > 5) {
            // Fade to solid black before releasing the engine, so teardown
            // and title startup happen unseen; fadeOutOverlay reveals them.
            final View old = currentPage;
            currentPage = null;
            deafen(old);
            if (old != null) {
                old.animate().alpha(0f).setDuration(1200)
                    .withEndAction(() -> {
                        if (root != null) root.removeView(old);
                        activity.nativeVictorySequenceDismissed();
                    })
                    .start();
            } else {
                activity.nativeVictorySequenceDismissed();
            }
            return;
        }

        showPage();
    }

    private void showPage() {
        if (root == null) return;

        if (currentPage != null) {
            final View old = currentPage;
            deafen(old);
            old.animate().alpha(0f).setDuration(200)
                .withEndAction(() -> { if (root != null) root.removeView(old); })
                .start();
        }

        View page;
        switch (pageIndex) {
            case 0:  page = buildSunlitPage(data.optString("headline", "")); break;
            case 1:  page = buildSunlitPage(data.optString("congrats", "")); break;
            case 5:  page = buildStingerPage(data.optString("stinger", "")); break;
            default: page = buildPanelPage(); break;
        }

        root.addView(page, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));
        currentPage = page;
    }

    /** Raw centered sepia text on the white veil — no panel chrome. */
    private View buildSunlitPage(String text) {
        final FrameLayout page = new FrameLayout(activity);
        page.setOnClickListener(v -> { if (page == currentPage) advance(); });

        TextView tv = new TextView(activity);
        tv.setText(text);
        tv.setTextColor(Palette.SUNLIT_TEXT);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setGravity(Gravity.CENTER);
        tv.setLineSpacing(0, 1.3f);
        int pad = activity.dpToPx(40);
        tv.setPadding(pad, pad, pad, pad);
        page.addView(tv, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER));

        TextView hint = new TextView(activity);
        hint.setText("tap to continue");
        hint.setTextColor(Palette.SUNLIT_HINT);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        hint.setTypeface(Typeface.MONOSPACE, Typeface.ITALIC);
        FrameLayout.LayoutParams hintParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        hintParams.setMargins(0, 0, 0, activity.dpToPx(28));
        page.addView(hint, hintParams);

        page.setAlpha(0f);
        page.animate().alpha(1f).setDuration(450).start();
        return page;
    }

    /** Closing line on the black, cut in rather than faded so it lands. */
    private View buildStingerPage(String text) {
        final FrameLayout page = new FrameLayout(activity);
        page.setOnClickListener(v -> { if (page == currentPage) advance(); });

        TextView tv = new TextView(activity);
        tv.setText(text);
        tv.setTextColor(Palette.PALE_BLUE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tv.setTypeface(Typeface.MONOSPACE, Typeface.ITALIC);
        tv.setGravity(Gravity.CENTER);
        tv.setLineSpacing(0, 1.3f);
        int pad = activity.dpToPx(40);
        tv.setPadding(pad, pad, pad, pad);
        page.addView(tv, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER));
        return page;
    }

    /** Dark Brogue-styled panel pages: 2 = treasures, 3 = achievements,
     *  4 = final score. */
    private View buildPanelPage() {
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad = activity.dpToPx(16);
        panel.setPadding(pad, pad, pad, pad);

        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setShape(GradientDrawable.RECTANGLE);
        panelBg.setCornerRadius(activity.dpToPx(6));
        panelBg.setColor(Palette.INVENTORY_BG);
        panelBg.setStroke(1, Palette.BORDER_ACTIVE);
        panel.setBackground(panelBg);
        panel.setElevation(activity.dpToPx(12));

        if (pageIndex == 2) {
            addHeader(panel, "TREASURES");
            addLine(panel, data.optString("epilogue", ""), Palette.GHOST_WHITE, 12, 0);
            addTreasureRows(panel);
        } else if (pageIndex == 3) {
            addHeader(panel, "ACHIEVEMENTS");
            JSONArray feats = data.optJSONArray("achievements");
            if (feats == null || feats.length() == 0) {
                addLine(panel, "None this run.", Palette.PALE_BLUE, 12, 0);
            } else {
                for (int i = 0; i < feats.length(); i++) {
                    addLine(panel, feats.optString(i, ""), Palette.GOOD_MAGIC, 12, i == 0 ? 0 : 4);
                }
            }
        } else {
            addHeader(panel, "FINAL SCORE");
            addLine(panel, String.valueOf(data.optLong("score", 0)), Palette.GOLD_TEXT, 18, 0);
            addLine(panel, data.optLong("turns", 0) + " turns", Palette.PALE_BLUE, 12, 8);
        }

        TextView hint = new TextView(activity);
        hint.setText(pageIndex == 4 ? "tap to finish" : "tap to continue");
        hint.setTextColor(Palette.PALE_BLUE);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        hint.setTypeface(Typeface.MONOSPACE, Typeface.ITALIC);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, activity.dpToPx(14), 0, 0);
        panel.addView(hint);

        ScrollView scroll = new ScrollView(activity);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.addView(panel);

        final FrameLayout page = new FrameLayout(activity);
        page.setOnClickListener(v -> { if (page == currentPage) advance(); });

        // A tap on the panel advances, a drag still scrolls. ScrollView
        // swallows click listeners, so detect the tap with a slop check.
        final int slop = android.view.ViewConfiguration.get(activity).getScaledTouchSlop();
        scroll.setOnTouchListener(new View.OnTouchListener() {
            private float downX, downY;
            private boolean tapping;
            @Override public boolean onTouch(View v, android.view.MotionEvent e) {
                switch (e.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        downX = e.getRawX(); downY = e.getRawY();
                        tapping = true;
                        break;
                    case android.view.MotionEvent.ACTION_MOVE:
                        if (Math.abs(e.getRawX() - downX) > slop
                                || Math.abs(e.getRawY() - downY) > slop) tapping = false;
                        break;
                    case android.view.MotionEvent.ACTION_UP:
                        if (tapping && page == currentPage) advance();
                        break;
                }
                return false;
            }
        });

        int panelWidth = Math.min(activity.dpToPx(380),
            (int)(activity.getResources().getDisplayMetrics().widthPixels * 0.85f));
        // The padding caps the panel's height: a WRAP_CONTENT child is
        // measured against the space left over, so a tall list scrolls.
        int inset = activity.dpToPx(24);
        page.setPadding(0, inset, 0, inset);
        page.addView(scroll, new FrameLayout.LayoutParams(
            panelWidth, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        ModalChrome.animateIn(panel);
        return page;
    }

    private void addTreasureRows(LinearLayout panel) {
        JSONArray rows = data.optJSONArray("treasure");
        if (rows == null) return;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;

            LinearLayout line = new LinearLayout(activity);
            line.setOrientation(LinearLayout.HORIZONTAL);

            TextView name = new TextView(activity);
            name.setText(row.optString("name", ""));
            name.setTextColor(Palette.GHOST_WHITE);
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            name.setTypeface(Typeface.MONOSPACE);
            line.addView(name, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            long value = row.optLong("value", 0);
            if (value > 0) {
                TextView val = new TextView(activity);
                val.setText(String.valueOf(value));
                val.setTextColor(Palette.GOLD_TEXT);
                val.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                val.setTypeface(Typeface.MONOSPACE);
                val.setPadding(activity.dpToPx(12), 0, 0, 0);
                line.addView(val);
            }

            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
            p.setMargins(0, activity.dpToPx(i == 0 ? 12 : 3), 0, 0);
            panel.addView(line, p);
        }

        panel.addView(ModalChrome.makeEmberSeparator(activity),
                      ModalChrome.emberSeparatorParams(activity, 8, 8, 12, 8));

        LinearLayout totalLine = new LinearLayout(activity);
        totalLine.setOrientation(LinearLayout.HORIZONTAL);
        TextView totalLabel = new TextView(activity);
        totalLabel.setText("TOTAL:");
        totalLabel.setTextColor(Palette.PALE_BLUE);
        totalLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        totalLabel.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        totalLine.addView(totalLabel, new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView totalVal = new TextView(activity);
        totalVal.setText(String.valueOf(data.optLong("total", 0)));
        totalVal.setTextColor(Palette.GOLD_TEXT);
        totalVal.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        totalVal.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        totalLine.addView(totalVal);
        panel.addView(totalLine);
    }

    private void addHeader(LinearLayout panel, String text) {
        TextView header = new TextView(activity);
        header.setText(text);
        header.setTextColor(Palette.GOLD_TEXT);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        header.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        header.setLetterSpacing(0.2f);
        header.setGravity(Gravity.CENTER);
        header.setPadding(0, activity.dpToPx(4), 0, activity.dpToPx(8));
        panel.addView(header);
        panel.addView(ModalChrome.makeEmberSeparator(activity),
                      ModalChrome.emberSeparatorParams(activity, 8, 8, 0, 12));
    }

    private void addLine(LinearLayout panel, String text, int color, int sp, int topDp) {
        TextView tv = new TextView(activity);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, activity.dpToPx(Math.max(topDp, 4)), 0, 0);
        panel.addView(tv, p);
    }

}
