package org.broguece.game;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.InputFilter;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Engine-driven text prompt (e.g. name, custom seed). Invoked from C when
 *  the engine wants a string from the user. OK/Cancel and the system back
 *  dispatch feed the result back into the native thread via
 *  {@link BrogueActivity#nativeTextInputResult}. */
final class TextInputDialog {

    private final BrogueActivity activity;

    TextInputDialog(BrogueActivity activity) {
        this.activity = activity;
    }

    void show(final String prompt, final String defaultText,
              final int maxLen, final boolean numericOnly) {
        activity.runOnUiThread(() -> {
            EditText input = new EditText(activity);
            input.setTextColor(Palette.GHOST_WHITE);
            input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            input.setTypeface(Typeface.MONOSPACE);
            input.setInputType(numericOnly ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT);
            input.setFilters(new InputFilter[]{ new InputFilter.LengthFilter(maxLen) });
            input.setText(defaultText);
            input.setSelectAllOnFocus(true);
            input.setHighlightColor(Color.argb(80, 180, 120, 50));

            GradientDrawable inputBg = new GradientDrawable();
            inputBg.setShape(GradientDrawable.RECTANGLE);
            inputBg.setCornerRadius(activity.dpToPx(3));
            inputBg.setColor(Color.argb(255, 20, 17, 42));
            inputBg.setStroke(1, Palette.FLAME_DIM);
            input.setBackground(inputBg);
            input.setPadding(activity.dpToPx(12), activity.dpToPx(10),
                             activity.dpToPx(12), activity.dpToPx(10));

            TextView titleView = new TextView(activity);
            titleView.setText(prompt);
            titleView.setTextColor(Palette.FLAME_EMBER);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            titleView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            titleView.setLetterSpacing(0.1f);
            titleView.setPadding(activity.dpToPx(20), activity.dpToPx(16),
                                 activity.dpToPx(20), activity.dpToPx(8));

            Button cancelBtn = makeDialogButton("CANCEL", Palette.PALE_BLUE,
                Typeface.MONOSPACE, Color.TRANSPARENT, false);
            Button okBtn = makeDialogButton("OK", Palette.FLAME_EMBER,
                Typeface.create(Typeface.MONOSPACE, Typeface.BOLD),
                Palette.ACTION_BG, true);

            LinearLayout buttonRow = new LinearLayout(activity);
            buttonRow.setOrientation(LinearLayout.HORIZONTAL);
            buttonRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);

            LinearLayout.LayoutParams cancelP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
            cancelP.setMargins(0, 0, activity.dpToPx(8), 0);
            buttonRow.addView(cancelBtn, cancelP);
            buttonRow.addView(okBtn);

            LinearLayout layout = new LinearLayout(activity);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.addView(titleView);
            int hPad = activity.dpToPx(20);
            LinearLayout.LayoutParams inputP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
            inputP.setMargins(hPad, activity.dpToPx(4), hPad, activity.dpToPx(8));
            layout.addView(input, inputP);
            LinearLayout.LayoutParams btnRowP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
            btnRowP.setMargins(hPad, 0, hPad, activity.dpToPx(16));
            layout.addView(buttonRow, btnRowP);

            GradientDrawable dialogBg = new GradientDrawable();
            dialogBg.setShape(GradientDrawable.RECTANGLE);
            dialogBg.setCornerRadius(activity.dpToPx(6));
            dialogBg.setColor(Palette.INVENTORY_BG);
            dialogBg.setStroke(1, Palette.BORDER_DIM);
            layout.setBackground(dialogBg);

            // Use the real full-screen dimensions (including nav bar area) so
            // the dialog position doesn't shift when the keyboard or
            // navigation bar appears/disappears.
            Point realSize = new Point();
            activity.getWindowManager().getDefaultDisplay().getRealSize(realSize);
            int fullScreenWidth = realSize.x;

            AlertDialog dialog = new AlertDialog.Builder(activity,
                    android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
                .setView(layout)
                .setCancelable(true)
                .setOnCancelListener(d -> activity.nativeTextInputResult(false, ""))
                .create();

            cancelBtn.setOnClickListener(v -> {
                activity.nativeTextInputResult(false, "");
                dialog.dismiss();
            });
            okBtn.setOnClickListener(v -> {
                activity.nativeTextInputResult(true, input.getText().toString());
                dialog.dismiss();
            });

            // Configure window before show() so there is no default slide
            // animation or gravity snap.
            Window window = dialog.getWindow();
            if (window != null) {
                window.setWindowAnimations(0);
                window.setBackgroundDrawable(dialogBg);
                window.setGravity(Gravity.CENTER);
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
                int minWidth = fullScreenWidth / 3;
                window.setLayout(Math.max(minWidth, ViewGroup.LayoutParams.WRAP_CONTENT),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            }

            dialog.setOnShowListener(d -> {
                if (window != null) {
                    // Match the activity's immersive flags so the nav bar
                    // stays hidden and the dialog doesn't shift.
                    window.getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
                }
                // Don't auto-show keyboard; let the user tap the input field.
                input.setFocusable(true);
                input.setFocusableInTouchMode(true);
                input.clearFocus();
            });

            dialog.show();
        });
    }

    private Button makeDialogButton(String label, int textColor, Typeface face,
                                     int bgColor, boolean stroked) {
        Button btn = new Button(activity);
        btn.setText(label);
        btn.setTextColor(textColor);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        btn.setTypeface(face);
        btn.setAllCaps(true);
        btn.setStateListAnimator(null);
        btn.setElevation(0);
        btn.setPadding(activity.dpToPx(16), activity.dpToPx(10),
                       activity.dpToPx(16), activity.dpToPx(10));
        btn.setMinWidth(0);
        btn.setMinimumWidth(0);
        btn.setMinHeight(activity.dpToPx(36));
        btn.setMinimumHeight(activity.dpToPx(36));

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(activity.dpToPx(3));
        bg.setColor(bgColor);
        if (stroked) bg.setStroke(1, Palette.BORDER_DIM);
        btn.setBackground(new RippleDrawable(
            ColorStateList.valueOf(Palette.RIPPLE_GLOW), bg, null));
        return btn;
    }
}
