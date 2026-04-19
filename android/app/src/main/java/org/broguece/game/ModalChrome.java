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

/** Static chrome helpers shared by the seed-family modals (Community, Custom Seed,
 *  Seed Detail). No state; each call takes the {@link BrogueActivity}. */
final class ModalChrome {

    private ModalChrome() {}

    /** Attaches a translucent backdrop to {@code root} (tap to popModal),
     *  creates the panel with background + header + ember separator, and
     *  returns the panel for callers to append content to.
     *  The caller is responsible for the scroll wrap / addContentView /
     *  animation — see {@link #present(BrogueActivity, FrameLayout, LinearLayout)}. */
    static LinearLayout buildPanel(BrogueActivity activity, FrameLayout root, String titleUpper) {
        View backdrop = new View(activity);
        backdrop.setBackgroundColor(Color.argb(160, 0, 0, 0));
        backdrop.setOnClickListener(v -> activity.modalStack.pop());
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
        header.setText(titleUpper);
        header.setTextColor(Palette.FLAME_EMBER);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        header.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        header.setLetterSpacing(0.2f);
        header.setGravity(Gravity.CENTER);
        header.setPadding(0, activity.dpToPx(4), 0, activity.dpToPx(8));
        panel.addView(header);

        panel.addView(makeEmberSeparator(activity),
                      emberSeparatorParams(activity, 8, 8, 0, 12));
        return panel;
    }

    static View makeEmberSeparator(BrogueActivity activity) {
        View sep = new View(activity);
        GradientDrawable g = new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{ Palette.FLAME_DIM,
                       Palette.FLAME_EMBER,
                       Palette.FLAME_DIM });
        sep.setBackground(g);
        return sep;
    }

    static LinearLayout.LayoutParams emberSeparatorParams(BrogueActivity activity,
            int leftDp, int rightDp, int topDp, int bottomDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1);
        p.setMargins(activity.dpToPx(leftDp), activity.dpToPx(topDp),
                     activity.dpToPx(rightDp), activity.dpToPx(bottomDp));
        return p;
    }

    /** Wraps the panel in a ScrollView, attaches at center at ~85% width, calls
     *  addContentView, and runs the standard fade+scale-in. For modals needing
     *  a SwipeRefreshLayout wrap (community list), inline the tail instead. */
    static void present(BrogueActivity activity, FrameLayout root, LinearLayout panel) {
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(panel);

        int panelWidth = Math.min(activity.dpToPx(340),
            (int)(activity.getResources().getDisplayMetrics().widthPixels * 0.85f));
        int maxHeight = (int)(activity.getResources().getDisplayMetrics().heightPixels * 0.85f);
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
            panelWidth, Math.min(maxHeight, FrameLayout.LayoutParams.WRAP_CONTENT),
            Gravity.CENTER);
        root.addView(scroll, panelParams);

        activity.addContentView(root, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));

        animateIn(panel);
    }

    static void animateIn(LinearLayout panel) {
        panel.setAlpha(0f);
        panel.setScaleX(0.94f);
        panel.setScaleY(0.94f);
        panel.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(220)
            .setInterpolator(new DecelerateInterpolator(1.5f))
            .start();
    }

    /** Panel size (width, max height) shared by the community list's custom
     *  SwipeRefreshLayout-wrapped tail. */
    static FrameLayout.LayoutParams centeredPanelParams(BrogueActivity activity) {
        int panelWidth = Math.min(activity.dpToPx(340),
            (int)(activity.getResources().getDisplayMetrics().widthPixels * 0.85f));
        int maxHeight = (int)(activity.getResources().getDisplayMetrics().heightPixels * 0.85f);
        return new FrameLayout.LayoutParams(
            panelWidth, Math.min(maxHeight, FrameLayout.LayoutParams.WRAP_CONTENT),
            Gravity.CENTER);
    }
}
