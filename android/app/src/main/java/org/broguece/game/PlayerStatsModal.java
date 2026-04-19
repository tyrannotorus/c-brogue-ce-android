package org.broguece.game;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Detailed player-stats view, pushed over the Community modal when the user
 *  taps "View Your Stats". */
final class PlayerStatsModal {

    private final BrogueActivity activity;

    PlayerStatsModal(BrogueActivity activity) {
        this.activity = activity;
    }

    void show() {
        activity.modalStack.push(this::build);
    }

    private View build() {
        FrameLayout root = new FrameLayout(activity);
        LinearLayout panel = ModalChrome.buildPanel(activity, root, "YOUR STATS");

        PlayerStats stats = StatsStore.get(activity).snapshot();

        // Top: 3-cell stat grid (played / won / died).
        LinearLayout grid = new LinearLayout(activity);
        grid.setOrientation(LinearLayout.HORIZONTAL);
        grid.setBaselineAligned(false);
        addStatCell(grid, stats.gamesPlayed,         "Games");
        addStatCell(grid, stats.wins,                "Wins");
        addStatCell(grid, stats.deaths,              "Deaths");
        addStatCell(grid, stats.alliesFreedTotal(),  "Allies");
        panel.addView(grid, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));

        // Highest-watermark stats.
        addKeyValueRow(panel, "Deepest dungeon",
            stats.deepestDepth > 0 ? "Depth " + stats.deepestDepth : "—");
        addKeyValueRow(panel, "Longest run",
            stats.longestRunTurns > 0 ? formatTurns(stats.longestRunTurns) : "—");

        addTallySection(panel, "Monsters Slain",          stats.kills);
        addTallySection(panel, "Primary Causes of Death", stats.deathCauses);

        StartMenu.addButton(panel, "Back", true, v -> activity.modalStack.pop());

        ModalChrome.present(activity, root, panel);
        return root;
    }

    private void addKeyValueRow(LinearLayout panel, String key, String value) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(activity.dpToPx(8), activity.dpToPx(8),
                       activity.dpToPx(8), activity.dpToPx(8));

        TextView keyView = new TextView(activity);
        keyView.setText(key);
        keyView.setTextColor(Palette.PALE_BLUE);
        keyView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        keyView.setTypeface(Typeface.MONOSPACE);
        row.addView(keyView, new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView valueView = new TextView(activity);
        valueView.setText(value);
        valueView.setTextColor(Palette.GHOST_WHITE);
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        valueView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        row.addView(valueView);

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = activity.dpToPx(2);
        panel.addView(row, p);
    }

    private void addTallySection(LinearLayout panel, String title,
                                  java.util.List<PlayerStats.Tally> tallies) {
        if (tallies == null || tallies.isEmpty()) return;

        TextView subhead = new TextView(activity);
        subhead.setText(title);
        subhead.setTextColor(Palette.FLAME_EMBER);
        subhead.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        subhead.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        subhead.setLetterSpacing(0.1f);
        subhead.setPadding(activity.dpToPx(4), activity.dpToPx(20),
                           activity.dpToPx(4), activity.dpToPx(6));
        panel.addView(subhead);

        int max = 1;
        for (PlayerStats.Tally t : tallies) {
            if (t.count > max) max = t.count;
        }
        for (PlayerStats.Tally t : tallies) {
            panel.addView(makeTallyRow(t, max));
        }
    }

    private View makeTallyRow(PlayerStats.Tally t, int maxCount) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(activity.dpToPx(6), activity.dpToPx(5),
                       activity.dpToPx(6), activity.dpToPx(5));

        TextView countView = new TextView(activity);
        countView.setText(t.count + "\u00D7");
        countView.setTextColor(Palette.FLAME_EMBER);
        countView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        countView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        countView.setMinimumWidth(activity.dpToPx(40));
        row.addView(countView);

        // Proportional bar, scaled against the section max.
        FrameLayout bar = new FrameLayout(activity);
        GradientDrawable track = new GradientDrawable();
        track.setShape(GradientDrawable.RECTANGLE);
        track.setCornerRadius(activity.dpToPx(2));
        track.setColor(Palette.ITEM_BG);
        bar.setBackground(track);

        View fill = new View(activity);
        GradientDrawable fillBg = new GradientDrawable();
        fillBg.setShape(GradientDrawable.RECTANGLE);
        fillBg.setCornerRadius(activity.dpToPx(2));
        fillBg.setColor(Palette.FLAME_DIM);
        fill.setBackground(fillBg);

        float ratio = maxCount > 0 ? (float) t.count / maxCount : 0f;
        FrameLayout.LayoutParams fillP = new FrameLayout.LayoutParams(
            0, activity.dpToPx(6));
        bar.addView(fill, fillP);
        bar.post(() -> {
            int w = bar.getWidth();
            fill.getLayoutParams().width = Math.max(1, (int) (w * ratio));
            fill.requestLayout();
        });

        LinearLayout.LayoutParams barP = new LinearLayout.LayoutParams(
            activity.dpToPx(48), activity.dpToPx(6));
        barP.setMargins(activity.dpToPx(6), 0, activity.dpToPx(8), 0);
        row.addView(bar, barP);

        TextView labelView = new TextView(activity);
        labelView.setText(t.label);
        labelView.setTextColor(Palette.GHOST_WHITE);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        labelView.setTypeface(Typeface.MONOSPACE);
        labelView.setMaxLines(1);
        labelView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(labelView, new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        return row;
    }

    private static String formatTurns(int turns) {
        return String.format(java.util.Locale.US, "%,d turns", turns);
    }

    private void addStatCell(LinearLayout grid, int value, String label) {
        LinearLayout cell = new LinearLayout(activity);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setPadding(activity.dpToPx(6), activity.dpToPx(10),
                        activity.dpToPx(6), activity.dpToPx(10));

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(activity.dpToPx(3));
        bg.setColor(Palette.ITEM_BG);
        cell.setBackground(bg);

        TextView number = new TextView(activity);
        number.setText(String.valueOf(value));
        number.setTextColor(Palette.GHOST_WHITE);
        number.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        number.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        number.setGravity(Gravity.CENTER);
        cell.addView(number, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView caption = new TextView(activity);
        caption.setText(label);
        caption.setTextColor(Palette.PALE_BLUE);
        caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        caption.setTypeface(Typeface.MONOSPACE);
        caption.setLetterSpacing(0.05f);
        caption.setGravity(Gravity.CENTER);
        caption.setPadding(0, activity.dpToPx(2), 0, 0);
        cell.addView(caption, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams cellP = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        int margin = activity.dpToPx(2);
        cellP.setMargins(margin, 0, margin, 0);
        grid.addView(cell, cellP);
    }
}
