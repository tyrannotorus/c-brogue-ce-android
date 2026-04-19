package org.broguece.game;

import android.graphics.Color;
import android.graphics.Paint;
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

/** Credits overlay, layered above the start menu via the modal stack. */
final class AboutModal {

    private final BrogueActivity activity;

    AboutModal(BrogueActivity activity) {
        this.activity = activity;
    }

    void show() {
        activity.modalStack.push(this::build);
    }

    private View build() {
        FrameLayout root = new FrameLayout(activity);

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
        header.setText("CREDITS");
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

        addCredit(panel,
            "Brogue",
            "Original game by Brian Walker (Pender)",
            "https://sites.google.com/site/broguegame/");
        addCredit(panel,
            "Brogue: Community Edition",
            "tmewett and the Brogue CE contributors",
            "https://github.com/tmewett/BrogueCE");
        addCredit(panel,
            "Android Port",
            "werewolf.camp",
            "https://werewolf.camp");

        ScrollView scroll = new ScrollView(activity);
        scroll.addView(panel);

        int panelWidth = Math.min(activity.dpToPx(320),
            (int)(activity.getResources().getDisplayMetrics().widthPixels * 0.8f));
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
            panelWidth, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        root.addView(scroll, panelParams);

        activity.addContentView(root, new FrameLayout.LayoutParams(
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

        return root;
    }

    private void addCredit(LinearLayout panel, String title, String line, String url) {
        LinearLayout block = new LinearLayout(activity);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(activity.dpToPx(4), activity.dpToPx(6),
                         activity.dpToPx(4), activity.dpToPx(6));

        TextView titleView = new TextView(activity);
        titleView.setText(title);
        titleView.setTextColor(Palette.GHOST_WHITE);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        titleView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        block.addView(titleView);

        TextView lineView = new TextView(activity);
        lineView.setText(line);
        lineView.setTextColor(Palette.PALE_BLUE);
        lineView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        lineView.setTypeface(Typeface.MONOSPACE);
        LinearLayout.LayoutParams lineP = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        lineP.setMargins(0, activity.dpToPx(2), 0, activity.dpToPx(2));
        block.addView(lineView, lineP);

        TextView linkView = new TextView(activity);
        linkView.setText(url);
        linkView.setTextColor(Palette.FLAME_EMBER);
        linkView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        linkView.setTypeface(Typeface.MONOSPACE);
        linkView.setPaintFlags(linkView.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        linkView.setPadding(0, activity.dpToPx(2), 0, activity.dpToPx(2));
        linkView.setOnClickListener(v -> Links.open(activity, url));
        block.addView(linkView);

        LinearLayout.LayoutParams blockP = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        blockP.setMargins(0, activity.dpToPx(4), 0, activity.dpToPx(4));
        panel.addView(block, blockP);
    }
}
