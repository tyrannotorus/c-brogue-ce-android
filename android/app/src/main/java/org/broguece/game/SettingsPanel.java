package org.broguece.game;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** In-game Settings panel — bottom-right card launched from the hamburger
 *  submenu. Toggles mirror C-side state by sending the corresponding keystroke
 *  (e.g. '\\' for hide color effects) so the engine and SharedPreferences stay
 *  in sync. Also exposes telemetry controls and seed copy. */
final class SettingsPanel {

    private static final String[] GRAPHICS_MODE_LABELS = {
        "Graphics: ASCII", "Graphics: Tiles", "Graphics: Hybrid"
    };

    private final BrogueActivity activity;
    private final FrameLayout host;

    SettingsPanel(BrogueActivity activity, FrameLayout host) {
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
        header.setText("SETTINGS");
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

        // Game-state toggles — sendChar notifies the engine of the change.
        addGameToggle(panel, "Hide Color Effects", "hide_color_effects", '\\');
        addGameToggle(panel, "Display Stealth Range", "display_stealth_range", ']');
        addGraphicsModeCycler(panel);

        addSeparator(panel);

        // Privacy / telemetry section
        addPrefToggleRow(panel, "Send Anonymous Usage Data",
            activity.api.telemetryPrefsName(), activity.api.telemetryPrefsKey(), true);

        addSeparator(panel);

        addAction(activity, panel, "Copy Seed to Clipboard", v -> {
            long seed = activity.nativeGetSeed();
            ClipboardManager clipboard =
                (ClipboardManager) activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("Brogue Seed", String.valueOf(seed)));
            Toast.makeText(activity,
                "Seed " + seed + " copied", Toast.LENGTH_SHORT).show();
            hide();
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

    // ---- Row builders ----

    private LinearLayout addRow(LinearLayout panel, String label) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(activity.dpToPx(12), activity.dpToPx(10),
                       activity.dpToPx(12), activity.dpToPx(10));
        row.setMinimumHeight(activity.dpToPx(44));

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(activity.dpToPx(3));
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

        TextView labelView = new TextView(activity);
        labelView.setText(label);
        labelView.setTextColor(Palette.GHOST_WHITE);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        labelView.setTypeface(Typeface.MONOSPACE);
        row.addView(labelView, new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, activity.dpToPx(2), 0, activity.dpToPx(2));
        panel.addView(row, p);

        return row;
    }

    /** Shared action-row builder — also used by {@link ExitPanel}. */
    static void addAction(BrogueActivity activity, LinearLayout panel, String label,
                          View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(activity.dpToPx(12), activity.dpToPx(10),
                       activity.dpToPx(12), activity.dpToPx(10));
        row.setMinimumHeight(activity.dpToPx(44));

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(activity.dpToPx(3));
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

        TextView labelView = new TextView(activity);
        labelView.setText(label);
        labelView.setTextColor(Palette.GHOST_WHITE);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        labelView.setTypeface(Typeface.MONOSPACE);
        row.addView(labelView, new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        row.setOnClickListener(listener);

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, activity.dpToPx(2), 0, activity.dpToPx(2));
        panel.addView(row, p);
    }

    private void addSeparator(LinearLayout panel) {
        View sep = new View(activity);
        sep.setBackgroundColor(Palette.BORDER_DIM);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1);
        p.setMargins(activity.dpToPx(4), activity.dpToPx(4),
                     activity.dpToPx(4), activity.dpToPx(4));
        panel.addView(sep, p);
    }

    /** Toggle backed by {@link GameSettings}; sends {@code gameKey} to the
     *  engine on change so the C side picks up the new state. */
    private void addGameToggle(LinearLayout panel, String label, String prefKey, char gameKey) {
        LinearLayout row = addRow(panel, label);

        boolean on = GameSettings.getBool(activity, prefKey);
        TextView check = makeCheckIndicator(on);
        row.addView(check, new LinearLayout.LayoutParams(
            activity.dpToPx(28), activity.dpToPx(28)));

        row.setOnClickListener(v -> {
            boolean nowOn = !GameSettings.getBool(activity, prefKey);
            GameSettings.setBool(activity, prefKey, nowOn);
            updateCheckIndicator(check, nowOn);
            KeyInput.sendChar(activity, gameKey);
        });
    }

    private void addGraphicsModeCycler(LinearLayout panel) {
        int mode = GameSettings.getInt(activity, "graphics_mode", 0);
        if (mode < 0 || mode > 2) mode = 0;

        LinearLayout row = addRow(panel, GRAPHICS_MODE_LABELS[mode]);
        TextView labelView = (TextView) row.getChildAt(0);

        final int[] currentMode = {mode};
        row.setOnClickListener(v -> {
            currentMode[0] = (currentMode[0] + 1) % GRAPHICS_MODE_LABELS.length;
            GameSettings.setInt(activity, "graphics_mode", currentMode[0]);
            labelView.setText(GRAPHICS_MODE_LABELS[currentMode[0]]);
            KeyInput.sendChar(activity, 'G');
        });
    }

    /** Toggle tied purely to a SharedPreferences key; the engine is not
     *  notified. Used for Java-only switches like the telemetry opt-out. */
    private void addPrefToggleRow(LinearLayout panel, String label,
                                   String prefsName, String key, boolean defaultValue) {
        LinearLayout row = addRow(panel, label);

        boolean on = activity.getSharedPreferences(prefsName, android.content.Context.MODE_PRIVATE)
            .getBoolean(key, defaultValue);

        TextView check = makeCheckIndicator(on);
        row.addView(check, new LinearLayout.LayoutParams(
            activity.dpToPx(28), activity.dpToPx(28)));

        row.setOnClickListener(v -> {
            boolean nowOn = !activity.getSharedPreferences(prefsName, android.content.Context.MODE_PRIVATE)
                .getBoolean(key, defaultValue);
            activity.getSharedPreferences(prefsName, android.content.Context.MODE_PRIVATE)
                .edit().putBoolean(key, nowOn).apply();
            updateCheckIndicator(check, nowOn);
        });
    }

    private TextView makeCheckIndicator(boolean on) {
        TextView check = new TextView(activity);
        check.setText(on ? "\u2713" : "");
        check.setTextColor(Palette.FLAME_EMBER);
        check.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        check.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        check.setGravity(Gravity.CENTER);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(activity.dpToPx(3));
        bg.setColor(on ? Palette.ACTION_BG : Color.TRANSPARENT);
        bg.setStroke(1, Palette.BORDER_ACTIVE);
        check.setBackground(bg);
        return check;
    }

    private void updateCheckIndicator(TextView check, boolean on) {
        check.setText(on ? "\u2713" : "");
        GradientDrawable bg = (GradientDrawable) check.getBackground();
        bg.setColor(on ? Palette.ACTION_BG : Color.TRANSPARENT);
        bg.setStroke(1, Palette.BORDER_ACTIVE);
    }
}
