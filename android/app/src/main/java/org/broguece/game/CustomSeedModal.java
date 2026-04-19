package org.broguece.game;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Local-only numeric seed entry. Does not hit the server and does not send
 *  telemetry — plays are not tagged with a recognised source. */
final class CustomSeedModal {

    private final BrogueActivity activity;

    CustomSeedModal(BrogueActivity activity) {
        this.activity = activity;
    }

    void show() {
        activity.modalStack.push(this::buildOverlay);
    }

    private View buildOverlay() {
        FrameLayout root = new FrameLayout(activity);
        LinearLayout panel = ModalChrome.buildPanel(activity, root, "CUSTOM SEED");

        TextView desc = new TextView(activity);
        desc.setText("Enter a seed to generate a specific dungeon.");
        desc.setTextColor(Palette.PALE_BLUE);
        desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        desc.setTypeface(Typeface.MONOSPACE);
        desc.setGravity(Gravity.CENTER);
        desc.setPadding(activity.dpToPx(8), activity.dpToPx(2),
                        activity.dpToPx(8), activity.dpToPx(16));
        panel.addView(desc);

        EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("enter seed");
        input.setTextColor(Palette.GHOST_WHITE);
        input.setHintTextColor(Palette.FLAME_DIM);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        input.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        input.setGravity(Gravity.CENTER);
        input.setSingleLine(true);
        input.setLetterSpacing(0.05f);
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setShape(GradientDrawable.RECTANGLE);
        inputBg.setCornerRadius(activity.dpToPx(4));
        inputBg.setColor(Palette.ITEM_BG);
        inputBg.setStroke(1, Palette.BORDER_DIM);
        input.setBackground(inputBg);
        input.setPadding(activity.dpToPx(12), activity.dpToPx(12),
                         activity.dpToPx(12), activity.dpToPx(12));
        LinearLayout.LayoutParams inputP = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        inputP.setMargins(0, 0, 0, activity.dpToPx(16));
        panel.addView(input, inputP);

        View playBtn = StartMenu.addButton(panel, "Play", false, null);
        View.OnClickListener playListener = v -> {
            long seed = parseSeed(input.getText().toString());
            if (seed <= 0) return;
            activity.modalStack.clear();
            activity.startMenu.dismiss();
            activity.nativeStartMenuResultWithSeed(StartMenu.CHOICE_PLAY_SEED, seed);
        };
        input.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                boolean valid = parseSeed(s.toString()) > 0;
                StartMenu.setButtonEnabled(playBtn, valid, valid ? playListener : null);
            }
        });
        StartMenu.addButton(panel, "Back", true, v -> activity.modalStack.pop());

        ModalChrome.present(activity, root, panel);
        return root;
    }

    /** Returns the parsed seed, or 0 if empty / non-numeric / non-positive. */
    private static long parseSeed(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return 0;
        try {
            long seed = Long.parseLong(trimmed);
            return seed > 0 ? seed : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
