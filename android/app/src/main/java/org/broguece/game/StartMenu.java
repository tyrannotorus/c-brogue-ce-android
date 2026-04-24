package org.broguece.game;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Title-screen start menu: "New Game", "Resume Game", "Community",
 *  "Credits". Shown by the engine via showStartMenu() and dismissed when the
 *  user chooses a path. Button-row styling is exposed statically so modals
 *  layered over the start menu (e.g. {@link CommunityModal} and the
 *  SeedDetailsModal family) can reuse the exact visual style. */
final class StartMenu {

    // Constants shared with android-touch.c — must match.
    static final int CHOICE_NEW_GAME  = 0;
    static final int CHOICE_RESUME    = 1;
    static final int CHOICE_PLAY_SEED = 2;

    private final BrogueActivity activity;
    private View overlay;

    StartMenu(BrogueActivity activity) {
        this.activity = activity;
    }

    void show(final boolean hasSave, final boolean saveCompatible) {
        android.util.Log.d("BrogueModal", "showStartMenu(hasSave=" + hasSave + ")");
        activity.runOnUiThread(() -> {
            dismiss();

            FrameLayout root = new FrameLayout(activity);
            overlay = root;

            View backdrop = new View(activity);
            backdrop.setBackgroundColor(Color.argb(120, 0, 0, 0));
            // Tap-off dismisses the menu and bounces the engine back to Phase 1
            // (title flames). The C side's Phase 2 loop watches the cancel flag.
            backdrop.setOnClickListener(v -> {
                dismiss();
                activity.nativeStartMenuCancel();
            });
            root.addView(backdrop, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

            LinearLayout panel = new LinearLayout(activity);
            panel.setOrientation(LinearLayout.VERTICAL);
            int pad = activity.dpToPx(16);
            panel.setPadding(pad, pad, pad, pad);

            GradientDrawable panelBg = new GradientDrawable();
            panelBg.setShape(GradientDrawable.RECTANGLE);
            panelBg.setCornerRadius(activity.dpToPx(6));
            panelBg.setColor(Palette.INVENTORY_BG);
            panelBg.setStroke(1, Palette.BORDER_DIM);
            panel.setBackground(panelBg);
            panel.setElevation(activity.dpToPx(12));

            TextView header = new TextView(activity);
            header.setText("BROGUE CE");
            header.setTextColor(Palette.FLAME_EMBER);
            header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            header.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            header.setLetterSpacing(0.2f);
            header.setGravity(Gravity.CENTER);
            header.setPadding(0, activity.dpToPx(4), 0, activity.dpToPx(8));
            panel.addView(header);

            View sep = new View(activity);
            GradientDrawable sepGrad = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{ Palette.FLAME_DIM, Palette.FLAME_EMBER, Palette.FLAME_DIM });
            sep.setBackground(sepGrad);
            LinearLayout.LayoutParams sepP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
            sepP.setMargins(activity.dpToPx(8), 0, activity.dpToPx(8), activity.dpToPx(12));
            panel.addView(sep, sepP);

            // New Game opens the seed-details modal with a client-picked
            // random seed; Play is what actually fires CHOICE_PLAY_SEED.
            addButton(panel, "New Game", true,
                v -> activity.newGameSeedModal.show());

            // Resume Game is a direct engine call — no modal. Disabled when
            // there's no save, or the save predates a version bump.
            boolean canResume = hasSave && saveCompatible;
            addButton(panel, "Resume Game", canResume, v -> {
                activity.modalStack.clear();
                dismiss();
                activity.nativeStartMenuResult(CHOICE_RESUME);
            });

            addButton(panel, "Community", true,
                v -> activity.communityModal.show());
            addButton(panel, "Credits", true,
                v -> activity.aboutModal.show());

            int panelWidth = Math.min(activity.dpToPx(300),
                (int)(activity.getResources().getDisplayMetrics().widthPixels * 0.7f));
            FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                panelWidth, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
            root.addView(panel, panelParams);

            activity.addContentView(root, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

            panel.setAlpha(0f);
            panel.setScaleX(0.92f);
            panel.setScaleY(0.92f);
            panel.animate()
                .alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(250)
                .setInterpolator(new DecelerateInterpolator(1.5f))
                .start();

            // Modal restore happens in setOverlayVisible(false), which fires
            // earlier in titleMenu() and avoids forcing the user to tap
            // through the title flame phase.
        });
    }

    void dismiss() {
        if (overlay != null && overlay.getParent() != null) {
            ((ViewGroup) overlay.getParent()).removeView(overlay);
        }
        overlay = null;
    }

    /** Adds a row-styled button to a start-menu or submodal panel. Returns the
     *  row view so callers can flip enabled state later via
     *  {@link #setButtonEnabled}. */
    static View addButton(LinearLayout panel, String label, boolean enabled,
                          View.OnClickListener listener) {
        BrogueActivity activity = (BrogueActivity) panel.getContext();
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(activity.dpToPx(16), activity.dpToPx(12),
                       activity.dpToPx(16), activity.dpToPx(12));
        row.setMinimumHeight(activity.dpToPx(48));

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(activity.dpToPx(4));
        bg.setColor(enabled ? Palette.ITEM_BG : Palette.DISABLED_BG);
        if (enabled) {
            row.setBackground(new RippleDrawable(
                ColorStateList.valueOf(Palette.RIPPLE_GLOW), bg, null));
        } else {
            row.setBackground(bg);
        }

        TextView labelView = new TextView(activity);
        labelView.setText(label);
        labelView.setTextColor(enabled ? Palette.GHOST_WHITE : Palette.DISABLED_TEXT);
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
        p.setMargins(0, activity.dpToPx(3), 0, activity.dpToPx(3));
        panel.addView(row, p);
        return row;
    }

    /** Re-applies enabled/disabled styling to a button produced by
     *  {@link #addButton}. The button must have been built with the same
     *  structure (LinearLayout row with a single TextView child). */
    static void setButtonEnabled(View row, boolean enabled, View.OnClickListener listener) {
        BrogueActivity activity = (BrogueActivity) row.getContext();
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(activity.dpToPx(4));
        bg.setColor(enabled ? Palette.ITEM_BG : Palette.DISABLED_BG);
        if (enabled) {
            row.setBackground(new RippleDrawable(
                ColorStateList.valueOf(Palette.RIPPLE_GLOW), bg, null));
        } else {
            row.setBackground(bg);
        }
        if (row instanceof LinearLayout) {
            LinearLayout ll = (LinearLayout) row;
            View first = ll.getChildCount() > 0 ? ll.getChildAt(0) : null;
            if (first instanceof TextView) {
                ((TextView) first).setTextColor(enabled ? Palette.GHOST_WHITE : Palette.DISABLED_TEXT);
            }
        }
        row.setEnabled(enabled);
        row.setClickable(enabled);
        row.setOnClickListener(enabled ? listener : null);
    }
}
