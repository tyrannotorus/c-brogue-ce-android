package org.broguece.game;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

/** Read-only "Discovered Items" screen, opened from the eye icon in the
 *  inventory header. Lists every item kind per category in a single column:
 *  identified kinds in white, undiscovered kinds dimmed with their good/bad
 *  magic indicator and their spawn-probability among the still-unknown kinds.
 *  The inventory icon in the header sends the player back to the inventory;
 *  tapping the backdrop closes the screen like any other menu. */
final class DiscoveriesOverlay {

    private final BrogueActivity activity;
    private final FrameLayout host;

    DiscoveriesOverlay(BrogueActivity activity, FrameLayout host) {
        this.activity = activity;
        this.host = host;
    }

    void show(final String json) {
        activity.runOnUiThread(() -> {
            // Cancel any in-flight hide animation (e.g. the inventory sliding
            // out) so its withEndAction doesn't wipe the content we add here.
            for (int i = 0; i < host.getChildCount(); i++) {
                host.getChildAt(i).animate().cancel();
            }
            host.removeAllViews();

            try {
                JSONArray sections = new JSONObject(json).getJSONArray("sections");

                View backdrop = new View(activity);
                backdrop.setBackgroundColor(Color.argb(140, 0, 0, 0));
                backdrop.setOnClickListener(v -> KeyInput.sendKey(activity, KeyEvent.KEYCODE_ESCAPE));
                host.addView(backdrop, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
                backdrop.setAlpha(0f);
                backdrop.animate().alpha(1f).setDuration(280).start();

                ScrollView scrollView = new ScrollView(activity);
                scrollView.setFillViewport(false);
                scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
                scrollView.setFadingEdgeLength(activity.dpToPx(24));
                scrollView.setVerticalFadingEdgeEnabled(true);

                LinearLayout panel = new LinearLayout(activity);
                panel.setOrientation(LinearLayout.VERTICAL);
                int pad = activity.dpToPx(8);
                panel.setPadding(pad, pad, pad, pad);

                GradientDrawable panelBg = new GradientDrawable();
                panelBg.setShape(GradientDrawable.RECTANGLE);
                panelBg.setCornerRadii(new float[]{
                    activity.dpToPx(4), activity.dpToPx(4), 0, 0, 0, 0,
                    activity.dpToPx(4), activity.dpToPx(4)});
                panelBg.setColor(Palette.INVENTORY_BG);
                panelBg.setStroke(1, Palette.BORDER_DIM);
                scrollView.setBackground(panelBg);

                panel.addView(makeHeader());

                for (int s = 0; s < sections.length(); s++) {
                    JSONObject section = sections.getJSONObject(s);
                    panel.addView(makeSectionLabel(section.optString("label", ""),
                                                   s == 0));
                    JSONArray items = section.getJSONArray("items");
                    for (int i = 0; i < items.length(); i++) {
                        panel.addView(makeItemRow(items.getJSONObject(i)));
                    }
                }

                scrollView.addView(panel);

                int panelWidth = Math.min(activity.dpToPx(340),
                    (int)(activity.getResources().getDisplayMetrics().widthPixels * 0.75f));

                FrameLayout.LayoutParams scrollParams = new FrameLayout.LayoutParams(
                    panelWidth, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.END);
                scrollParams.setMargins(0, activity.dpToPx(8),
                    activity.dpToPx(BrogueActivity.EDGE_SAFE_DP), activity.dpToPx(52));

                host.addView(scrollView, scrollParams);
                host.setVisibility(View.VISIBLE);

                scrollView.setTranslationX(panelWidth);
                scrollView.animate()
                    .translationX(0)
                    .setDuration(280)
                    .setInterpolator(new DecelerateInterpolator(1.5f))
                    .start();

            } catch (Exception e) {
                KeyInput.sendKey(activity, KeyEvent.KEYCODE_ESCAPE);
            }
        });
    }

    void hide() {
        activity.runOnUiThread(() -> {
            if (host.getChildCount() < 2) {
                host.setVisibility(View.GONE);
                host.removeAllViews();
                return;
            }
            View backdrop = host.getChildAt(0);
            View scrollView = host.getChildAt(1);

            scrollView.animate()
                .translationX(scrollView.getWidth())
                .setDuration(200)
                .setInterpolator(new DecelerateInterpolator())
                .start();

            backdrop.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction(() -> {
                    host.setVisibility(View.GONE);
                    host.removeAllViews();
                })
                .start();
        });
    }

    /** Title row with the inventory icon right-aligned; tapping it sends the
     *  inventory key so the engine swaps back to the inventory panel. */
    private View makeHeader() {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(activity);
        title.setText("DISCOVERED ITEMS");
        title.setTextColor(Palette.FLAME_EMBER);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        title.setLetterSpacing(0.15f);
        title.setPadding(activity.dpToPx(4), activity.dpToPx(2), 0, activity.dpToPx(4));
        row.addView(title, new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        ImageButton inventoryBtn = makeHeaderIcon(R.drawable.ic_money_bag, "Back to inventory");
        inventoryBtn.setOnClickListener(v -> KeyInput.sendChar(activity, 'i'));
        row.addView(inventoryBtn);

        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.addView(row);

        View sep = new View(activity);
        GradientDrawable sepGrad = new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{ Palette.FLAME_DIM, Palette.FLAME_EMBER, Palette.FLAME_DIM });
        sep.setBackground(sepGrad);
        LinearLayout.LayoutParams sepP = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1);
        sepP.setMargins(activity.dpToPx(4), 0, activity.dpToPx(4), activity.dpToPx(6));
        column.addView(sep, sepP);

        return column;
    }

    private TextView makeSectionLabel(String label, boolean first) {
        TextView view = new TextView(activity);
        view.setText("-- " + label + " --");
        view.setTextColor(Palette.PALE_BLUE);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        view.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        view.setLetterSpacing(0.1f);
        view.setPadding(activity.dpToPx(4), activity.dpToPx(first ? 2 : 12),
                        0, activity.dpToPx(4));
        return view;
    }

    private View makeItemRow(JSONObject item) {
        boolean identified = item.optBoolean("identified", false);
        int polarity = item.optInt("polarity", 0);
        int pct = item.optInt("pct", 0);
        String name = item.optString("name", "???");

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(activity.dpToPx(6), activity.dpToPx(4),
                       activity.dpToPx(6), activity.dpToPx(4));

        // Fixed-width magic indicator slot so names align across rows.
        TextView glyph = new TextView(activity);
        glyph.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        glyph.setTypeface(Typeface.MONOSPACE);
        glyph.setGravity(Gravity.CENTER);
        if (!identified && polarity != 0) {
            glyph.setText(polarity > 0 ? Palette.GOOD_MAGIC_GLYPH : Palette.BAD_MAGIC_GLYPH);
            glyph.setTextColor(polarity > 0 ? Palette.GOOD_MAGIC : Palette.BAD_MAGIC);
        }
        row.addView(glyph, new LinearLayout.LayoutParams(
            activity.dpToPx(18), LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView nameView = new TextView(activity);
        nameView.setText(name);
        nameView.setTextColor(identified ? Palette.GHOST_WHITE
                                          : Color.argb(160, 130, 125, 145));
        nameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        nameView.setTypeface(Typeface.MONOSPACE);
        nameView.setPadding(activity.dpToPx(4), 0, 0, 0);
        row.addView(nameView, new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        if (!identified && pct > 0) {
            TextView pctView = new TextView(activity);
            pctView.setText("(" + pct + "%)");
            pctView.setTextColor(Color.argb(140, 110, 105, 125));
            pctView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            pctView.setTypeface(Typeface.MONOSPACE);
            pctView.setPadding(activity.dpToPx(6), 0, activity.dpToPx(2), 0);
            row.addView(pctView);
        }

        return row;
    }

    private ImageButton makeHeaderIcon(int drawableRes, String contentDesc) {
        ImageButton btn = new ImageButton(activity);
        btn.setImageResource(drawableRes);
        btn.setColorFilter(Palette.PALE_BLUE);
        btn.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        btn.setBackground(null);
        btn.setStateListAnimator(null);
        btn.setElevation(0);
        btn.setContentDescription(contentDesc);
        int size = activity.dpToPx(36);
        btn.setMinimumWidth(size);
        btn.setMinimumHeight(size);
        int p = activity.dpToPx(6);
        btn.setPadding(p, p, p, p);
        btn.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(60).start();
            } else if (e.getAction() == MotionEvent.ACTION_UP
                    || e.getAction() == MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
            }
            return false;
        });
        return btn;
    }
}
