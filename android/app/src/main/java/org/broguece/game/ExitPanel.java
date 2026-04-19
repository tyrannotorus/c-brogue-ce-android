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

/** In-game Exit panel — "Abandon Game", "Save and Exit", "Exit". Launched
 *  from the hamburger submenu. Abandon and Save send the corresponding
 *  engine keystroke (Q / S) so Brogue handles the actual save/quit. */
final class ExitPanel {

    private final BrogueActivity activity;
    private final FrameLayout host;

    ExitPanel(BrogueActivity activity, FrameLayout host) {
        this.activity = activity;
        this.host = host;
    }

    void show() {
        host.removeAllViews();

        View backdrop = new View(activity);
        backdrop.setBackgroundColor(Color.argb(140, 0, 0, 0));
        backdrop.setOnClickListener(v -> hide());
        host.addView(backdrop, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));
        backdrop.setAlpha(0f);
        backdrop.animate().alpha(1f).setDuration(280).start();

        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad = activity.dpToPx(10);
        panel.setPadding(pad, pad, pad, pad);

        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setShape(GradientDrawable.RECTANGLE);
        panelBg.setCornerRadii(new float[]{
            activity.dpToPx(4), activity.dpToPx(4), 0, 0, 0, 0,
            activity.dpToPx(4), activity.dpToPx(4)});
        panelBg.setColor(Palette.INVENTORY_BG);
        panelBg.setStroke(1, Palette.BORDER_DIM);

        ScrollView scrollView = new ScrollView(activity);
        scrollView.setBackground(panelBg);
        scrollView.addView(panel);

        TextView header = new TextView(activity);
        header.setText("EXIT");
        header.setTextColor(Palette.FLAME_EMBER);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        header.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        header.setLetterSpacing(0.15f);
        header.setPadding(activity.dpToPx(4), activity.dpToPx(2), 0, activity.dpToPx(4));
        panel.addView(header);

        View headerSep = new View(activity);
        GradientDrawable sepGrad = new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{ Palette.FLAME_DIM, Palette.FLAME_EMBER, Palette.FLAME_DIM });
        headerSep.setBackground(sepGrad);
        LinearLayout.LayoutParams hSepP = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1);
        hSepP.setMargins(activity.dpToPx(4), 0, activity.dpToPx(4), activity.dpToPx(8));
        panel.addView(headerSep, hSepP);

        SettingsPanel.addAction(activity, panel, "Abandon Game", v -> {
            hide();
            KeyInput.sendChar(activity, 'Q');
        });
        SettingsPanel.addAction(activity, panel, "Save and Exit", v -> {
            hide();
            KeyInput.sendChar(activity, 'S');
        });
        SettingsPanel.addAction(activity, panel, "Exit", v -> {
            hide();
            activity.finishAndRemoveTask();
        });

        int panelWidth = Math.min(activity.dpToPx(280),
            (int)(activity.getResources().getDisplayMetrics().widthPixels * 0.6f));

        FrameLayout.LayoutParams scrollParams = new FrameLayout.LayoutParams(
            panelWidth, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM | Gravity.END);
        scrollParams.setMargins(0, activity.dpToPx(8),
            activity.dpToPx(BrogueActivity.EDGE_SAFE_DP), activity.dpToPx(52));

        host.addView(scrollView, scrollParams);
        host.setVisibility(View.VISIBLE);

        scrollView.setTranslationY(activity.dpToPx(40));
        scrollView.setAlpha(0f);
        scrollView.animate()
            .translationY(0).alpha(1f)
            .setDuration(200)
            .setInterpolator(new DecelerateInterpolator(1.5f))
            .start();
    }

    void hide() {
        if (host.getChildCount() < 2) {
            host.setVisibility(View.GONE);
            host.removeAllViews();
            return;
        }
        View backdrop = host.getChildAt(0);
        View panel = host.getChildAt(1);

        panel.animate()
            .translationY(activity.dpToPx(30)).alpha(0f)
            .setDuration(150)
            .setInterpolator(new DecelerateInterpolator())
            .start();

        backdrop.animate()
            .alpha(0f)
            .setDuration(150)
            .withEndAction(() -> {
                host.setVisibility(View.GONE);
                host.removeAllViews();
            })
            .start();
    }
}
