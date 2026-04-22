package org.broguece.game;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Detailed player-stats view, pushed over the Community modal when the user
 *  taps "View Personal Stats". */
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
        LinearLayout panel = ModalChrome.buildPanel(activity, root, "PERSONAL STATS");

        PlayerStats stats = StatsStore.get(activity).snapshot();

        // Top: 3-cell stat grid (played / won / died).
        LinearLayout grid = new LinearLayout(activity);
        grid.setOrientation(LinearLayout.HORIZONTAL);
        grid.setBaselineAligned(false);
        addStatCell(grid, stats.gamesPlayed, "Games");
        addStatCell(grid, stats.wins,        "Wins");
        addStatCell(grid, stats.deaths,      "Deaths");
        panel.addView(grid, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));

        // Highest-watermark stats.
        addKeyValueRow(panel, "Deepest depth",
            stats.deepestDepth > 0 ? "Depth " + stats.deepestDepth : "—");
        int deadliest = stats.deadliestDepth();
        addKeyValueRow(panel, "Deadliest depth",
            deadliest > 0 ? "Depth " + deadliest : "—");
        addKeyValueRow(panel, "Longest game",
            stats.longestRunTurns > 0 ? formatTurns(stats.longestRunTurns) : "—");
        addKeyValueRow(panel, "Mastery wins",
            stats.masteryWins > 0 ? String.valueOf(stats.masteryWins) : "—");
        addKeyValueRow(panel, "Fastest win",
            stats.fastestWinTurns > 0 ? formatTurns(stats.fastestWinTurns) : "—");

        addRecentSeedsSection(panel, stats);

        addTallySection(panel, "Primary Causes of Death", stats.deathCauses);
        addTallySection(panel, "Monsters Slain",          stats.kills);
        addTallySection(panel, "Allies Freed",            stats.alliesFreed);
        addTallySection(panel, "Allies you let die",      stats.alliesLost);

        addAchievementsSection(panel, stats);

        // Breathing room between the last section and the Back button so the
        // button doesn't hug the last row of content when scrolled to bottom.
        View spacer = new View(activity);
        panel.addView(spacer, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, activity.dpToPx(20)));

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
        TextView subhead = new TextView(activity);
        subhead.setText(title);
        subhead.setTextColor(Palette.FLAME_EMBER);
        subhead.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        subhead.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        subhead.setLetterSpacing(0.1f);
        subhead.setPadding(activity.dpToPx(4), activity.dpToPx(20),
                           activity.dpToPx(4), activity.dpToPx(6));
        panel.addView(subhead);

        if (tallies == null || tallies.isEmpty()) {
            panel.addView(makeEmptyPlaceholder());
            return;
        }

        TagFlowLayout flow = new TagFlowLayout(activity, activity.dpToPx(6));
        for (PlayerStats.Tally t : tallies) {
            flow.addView(makeTagPill(t));
        }
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        fp.setMargins(activity.dpToPx(4), 0, activity.dpToPx(4), 0);
        panel.addView(flow, fp);
    }

    /** Shown inside a section whose content list is empty, so every section's
     *  header is always visible regardless of player progress. */
    private TextView makeEmptyPlaceholder() {
        TextView empty = new TextView(activity);
        empty.setText("None yet");
        empty.setTextColor(Palette.DISABLED_TEXT);
        empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        empty.setTypeface(Typeface.MONOSPACE);
        empty.setPadding(activity.dpToPx(4), activity.dpToPx(2),
                         activity.dpToPx(4), activity.dpToPx(2));
        return empty;
    }

    /** Last ten distinct seeds this install has played, newest first. Each
     *  row is clickable — it opens CustomSeedModal with the seed pre-filled
     *  so the player can replay it without retyping a 19-digit number. */
    private void addRecentSeedsSection(LinearLayout panel, PlayerStats stats) {
        TextView subhead = new TextView(activity);
        subhead.setText("Recently Played Seeds");
        subhead.setTextColor(Palette.FLAME_EMBER);
        subhead.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        subhead.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        subhead.setLetterSpacing(0.1f);
        subhead.setPadding(activity.dpToPx(4), activity.dpToPx(20),
                           activity.dpToPx(4), activity.dpToPx(6));
        panel.addView(subhead);

        if (stats.recentSeeds.isEmpty()) {
            panel.addView(makeEmptyPlaceholder());
            return;
        }

        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        for (Long seed : stats.recentSeeds) {
            list.addView(makeRecentSeedRow(seed));
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(activity.dpToPx(4), 0, activity.dpToPx(4), 0);
        panel.addView(list, lp);
    }

    private View makeRecentSeedRow(long seed) {
        TextView row = new TextView(activity);
        row.setText(String.valueOf(seed));
        row.setTextColor(Palette.GHOST_WHITE);
        row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        row.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        row.setPadding(activity.dpToPx(12), activity.dpToPx(10),
                       activity.dpToPx(12), activity.dpToPx(10));

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(activity.dpToPx(4));
        bg.setColor(Palette.ITEM_BG);
        row.setBackground(new RippleDrawable(
            ColorStateList.valueOf(Palette.RIPPLE_GLOW), bg, null));

        row.setOnClickListener(v -> {
            activity.modalStack.pop();
            activity.customSeedModal.show(seed);
        });

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, activity.dpToPx(2), 0, activity.dpToPx(2));
        row.setLayoutParams(p);
        return row;
    }

    /** Achievements — rules and metadata live in AchievementManager; this
     *  method just renders them. Earned entries use full palette colors;
     *  locked ones dim to DISABLED_TEXT / DISABLED_BG so the player can see
     *  what's still ahead. */
    private void addAchievementsSection(LinearLayout panel, PlayerStats stats) {
        TextView subhead = new TextView(activity);
        subhead.setText("Achievements");
        subhead.setTextColor(Palette.FLAME_EMBER);
        subhead.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        subhead.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        subhead.setLetterSpacing(0.1f);
        subhead.setPadding(activity.dpToPx(4), activity.dpToPx(20),
                           activity.dpToPx(4), activity.dpToPx(6));
        panel.addView(subhead);

        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        for (Achievement ach : AchievementManager.all()) {
            list.addView(makeAchievementRow(ach, ach.isEarned(stats)));
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(activity.dpToPx(4), 0, activity.dpToPx(4), 0);
        panel.addView(list, lp);
    }

    private View makeAchievementRow(Achievement ach, boolean earned) {
        int titleColor = earned ? Palette.GHOST_WHITE : Palette.DISABLED_TEXT;
        int starColor  = earned ? Palette.FLAME_EMBER : Palette.DISABLED_TEXT;
        int descColor  = earned ? Palette.PALE_BLUE   : Palette.DISABLED_TEXT;
        int bgColor    = earned ? Palette.ITEM_BG     : Palette.DISABLED_BG;

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(activity.dpToPx(12), activity.dpToPx(10),
                       activity.dpToPx(12), activity.dpToPx(10));

        TextView star = new TextView(activity);
        star.setText("\u2605");
        star.setTextColor(starColor);
        star.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        star.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        LinearLayout.LayoutParams starP = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        starP.setMargins(0, 0, activity.dpToPx(10), 0);
        row.addView(star, starP);

        LinearLayout textCol = new LinearLayout(activity);
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(activity);
        title.setText(ach.title);
        title.setTextColor(titleColor);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        textCol.addView(title);

        // Locked achievements hide the description — mystery is part of the
        // reward loop. Once earned, the "how" reveals itself.
        if (earned) {
            TextView desc = new TextView(activity);
            desc.setText(ach.description);
            desc.setTextColor(descColor);
            desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            desc.setTypeface(Typeface.MONOSPACE);
            textCol.addView(desc);
        }

        row.addView(textCol, new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(activity.dpToPx(4));
        bg.setColor(bgColor);
        row.setBackground(bg);

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, activity.dpToPx(2), 0, activity.dpToPx(2));
        row.setLayoutParams(p);
        return row;
    }

    private View makeTagPill(PlayerStats.Tally t) {
        // Count portion is ember+bold, label portion is white+regular, packed
        // into one TextView so the whole pill sizes and wraps as one unit.
        String countPart = t.count + "\u00D7 ";
        SpannableString text = new SpannableString(countPart + capitalize(t.label));
        text.setSpan(new ForegroundColorSpan(Palette.FLAME_EMBER),
                     0, countPart.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new StyleSpan(Typeface.BOLD),
                     0, countPart.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        TextView pill = new TextView(activity);
        pill.setText(text);
        pill.setTextColor(Palette.GHOST_WHITE);
        pill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        pill.setTypeface(Typeface.MONOSPACE);
        pill.setPadding(activity.dpToPx(10), activity.dpToPx(5),
                        activity.dpToPx(10), activity.dpToPx(5));

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(activity.dpToPx(4));
        bg.setColor(Palette.ITEM_BG);
        pill.setBackground(bg);

        return pill;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        char first = s.charAt(0);
        if (Character.isUpperCase(first)) return s;
        return Character.toUpperCase(first) + s.substring(1);
    }

    /** Lays children left-to-right, wrapping to a new line when the next
     *  child would overflow the parent width. `gap` is used for both inter-
     *  pill horizontal spacing and inter-row vertical spacing. */
    private static final class TagFlowLayout extends ViewGroup {
        private final int gap;

        TagFlowLayout(Context ctx, int gapPx) {
            super(ctx);
            this.gap = gapPx;
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int maxWidth = MeasureSpec.getSize(widthSpec);
            int rowWidth = 0, rowHeight = 0, totalHeight = 0;
            int n = getChildCount();
            for (int i = 0; i < n; i++) {
                View c = getChildAt(i);
                if (c.getVisibility() == GONE) continue;
                measureChild(c, widthSpec, heightSpec);
                int cw = c.getMeasuredWidth();
                int ch = c.getMeasuredHeight();
                if (rowWidth > 0 && rowWidth + gap + cw > maxWidth) {
                    totalHeight += rowHeight + gap;
                    rowWidth = 0;
                    rowHeight = 0;
                }
                rowWidth += (rowWidth > 0 ? gap : 0) + cw;
                rowHeight = Math.max(rowHeight, ch);
            }
            totalHeight += rowHeight;
            setMeasuredDimension(maxWidth, totalHeight);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int maxWidth = r - l;
            int x = 0, y = 0, rowHeight = 0;
            int n = getChildCount();
            for (int i = 0; i < n; i++) {
                View c = getChildAt(i);
                if (c.getVisibility() == GONE) continue;
                int cw = c.getMeasuredWidth();
                int ch = c.getMeasuredHeight();
                if (x > 0 && x + gap + cw > maxWidth) {
                    y += rowHeight + gap;
                    x = 0;
                    rowHeight = 0;
                }
                int left = x + (x > 0 ? gap : 0);
                c.layout(left, y, left + cw, y + ch);
                x = left + cw;
                rowHeight = Math.max(rowHeight, ch);
            }
        }
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
