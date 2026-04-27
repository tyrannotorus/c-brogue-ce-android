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

final class DeathModal {

    private final BrogueActivity activity;
    private FrameLayout root;
    private View fadeView;
    private String pendingDescription;
    private int pendingTurns;

    DeathModal(BrogueActivity activity) {
        this.activity = activity;
    }

    void show(String description, int turns) {
        this.pendingDescription = description;
        this.pendingTurns = turns;
        activity.runOnUiThread(this::fadeToBlack);
    }

    void onFlamesReady() {
        activity.runOnUiThread(this::revealAndShowModal);
    }

    void fadeOutOverlay() {
        if (root == null || fadeView == null) {
            removeOverlay();
            return;
        }
        fadeView.setAlpha(1f);
        fadeView.animate()
            .alpha(0f)
            .setDuration(500)
            .setInterpolator(new DecelerateInterpolator(1.5f))
            .withEndAction(this::removeOverlay)
            .start();
    }

    void removeOverlay() {
        if (root != null && root.getParent() != null) {
            ((android.view.ViewGroup) root.getParent()).removeView(root);
        }
        root = null;
        fadeView = null;
    }

    private void fadeToBlack() {
        root = new FrameLayout(activity);

        fadeView = new View(activity);
        fadeView.setBackgroundColor(Color.BLACK);
        fadeView.setAlpha(0f);
        root.addView(fadeView, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));

        activity.addContentView(root, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));

        fadeView.animate()
            .alpha(1f)
            .setDuration(3000)
            .setInterpolator(new DecelerateInterpolator(2f))
            .withEndAction(() -> activity.nativeDeathFadeDone())
            .start();
    }

    private void revealAndShowModal() {
        if (root == null) return;

        if (fadeView != null) {
            fadeView.animate()
                .alpha(0f)
                .setDuration(220)
                .setInterpolator(new DecelerateInterpolator(1.5f))
                .start();
        }

        buildModal(pendingDescription, pendingTurns);
    }

    private void dismiss() {
        // Remove the modal panel, keep the black overlay for transition.
        if (root != null) {
            View scroll = root.getChildAt(1);
            if (scroll != null) root.removeView(scroll);
        }
        // Fade to black over the red flames.
        if (fadeView != null) {
            fadeView.animate().cancel();
            fadeView.setAlpha(0f);
            fadeView.animate()
                .alpha(1f)
                .setDuration(500)
                .setInterpolator(new DecelerateInterpolator(1.5f))
                .withEndAction(() -> activity.nativeDeathScreenDismissed())
                .start();
        } else {
            activity.nativeDeathScreenDismissed();
        }
    }

    private void buildModal(String description, int turns) {
        if (root == null) return;

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
        header.setText("YOU DIED");
        header.setTextColor(Palette.FLAME_EMBER);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        header.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        header.setLetterSpacing(0.2f);
        header.setGravity(Gravity.CENTER);
        header.setPadding(0, activity.dpToPx(4), 0, activity.dpToPx(8));
        panel.addView(header);

        panel.addView(ModalChrome.makeEmberSeparator(activity),
                      ModalChrome.emberSeparatorParams(activity, 8, 8, 0, 12));

        addLine(panel, description, Palette.GHOST_WHITE, 13);
        addLine(panel, turns + " turns", Palette.PALE_BLUE, 12);

        panel.addView(new View(activity), new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, activity.dpToPx(16)));

        StartMenu.addButton(panel, "Continue", true, v -> dismiss());

        ScrollView scroll = new ScrollView(activity);
        scroll.addView(panel);

        int panelWidth = Math.min(activity.dpToPx(340),
            (int)(activity.getResources().getDisplayMetrics().widthPixels * 0.85f));
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
            panelWidth, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        root.addView(scroll, panelParams);

        ModalChrome.animateIn(panel);
    }

    private void addLine(LinearLayout panel, String text, int color, int sp) {
        TextView tv = new TextView(activity);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, activity.dpToPx(4), 0, 0);
        panel.addView(tv, p);
    }
}
