package org.broguece.game;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.text.InputFilter;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.Random;

/** Main-menu "New Game" entry. Picks a random seed client-side, tweens the
 *  header label up from 0 → chosen seed, and lets the player tap the pencil
 *  (or the seed itself) to override via the soft keyboard. */
final class NewGameSeedModal extends SeedDetailsModal {

    // Java signed-long range caps user-entered seeds at 19 digits; the
    // server's schema matches. Brogue C accepts 20 (uint64_t) but we can't
    // round-trip those through Long.parseLong without overflow.
    private static final int MAX_SEED_DIGITS = 19;
    // Client-picked random seeds match the shape of Brogue's clock-based
    // seeds: `(uint64_t) time(NULL) - 1352700000` at Math.c:186 produces
    // ~9-digit values at the current date range. A 9-digit random keeps
    // the header visually consistent with what players see in-engine.
    private static final long RANDOM_SEED_MIN = 100_000_000L;
    private static final long RANDOM_SEED_MAX = 999_999_999L;

    private final Random random = new Random();
    private ValueAnimator seedAnimator;
    /** One-shot flag so the intro roll-up only plays on a fresh show(),
     *  not on ModalStack.restore() rebuilds. */
    private boolean playIntroTween;
    private EditText seedEdit;

    NewGameSeedModal(BrogueActivity activity) { super(activity); }

    @Override protected String getTitleUpper() { return "NEW GAME"; }

    /** Opens with a fresh random seed and the intro roll-up animation. */
    void show() {
        playIntroTween = true;
        show(pickRandomSeed());
    }

    @Override
    protected void onSeedViewBuilt(TextView seedView) {
        LinearLayout panel = (LinearLayout) seedView.getParent();
        int seedIndex = panel.indexOfChild(seedView);
        panel.removeView(seedView);

        seedEdit = makeSeedEditText();
        ImageView pencil = makePencilIcon();

        int seedId = View.generateViewId();
        seedEdit.setId(seedId);

        RelativeLayout wrapper = new RelativeLayout(activity);
        wrapper.setClipChildren(false);

        RelativeLayout.LayoutParams seedP = new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT);
        seedP.addRule(RelativeLayout.CENTER_IN_PARENT);
        wrapper.addView(seedEdit, seedP);

        int hitPx = activity.dpToPx(48);
        RelativeLayout.LayoutParams pencilP = new RelativeLayout.LayoutParams(hitPx, hitPx);
        pencilP.addRule(RelativeLayout.RIGHT_OF, seedId);
        pencilP.addRule(RelativeLayout.CENTER_VERTICAL);
        wrapper.addView(pencil, pencilP);

        panel.addView(wrapper, seedIndex, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));

        headerLabelView = seedEdit;

        pencil.setOnClickListener(v -> focusEditor());
        seedEdit.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                commitEdit();
                return true;
            }
            return false;
        });

        if (playIntroTween) {
            playIntroTween = false;
            startSeedRollTween(seedEdit, seed);
        } else {
            seedEdit.setText(String.valueOf(seed));
        }
    }

    private EditText makeSeedEditText() {
        EditText v = new EditText(activity);
        v.setTextColor(Palette.GHOST_WHITE);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        v.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        v.setGravity(Gravity.CENTER);
        v.setPadding(0, activity.dpToPx(6), 0, activity.dpToPx(6));
        v.setBackground(null);
        v.setInputType(InputType.TYPE_CLASS_NUMBER);
        v.setFilters(new InputFilter[]{ new InputFilter.LengthFilter(MAX_SEED_DIGITS) });
        v.setImeOptions(EditorInfo.IME_ACTION_DONE);
        v.setSingleLine(true);
        return v;
    }

    private ImageView makePencilIcon() {
        ImageView v = new ImageView(activity);
        v.setImageResource(R.drawable.ic_edit);
        v.setImageTintList(ColorStateList.valueOf(Palette.PALE_BLUE));
        // 48dp outer bounds = comfortable touch target; 15dp padding shrinks
        // the visible drawable to ~18dp via CENTER_INSIDE.
        v.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int padding = activity.dpToPx(15);
        v.setPadding(padding, padding, padding, padding);
        v.setClickable(true);
        return v;
    }

    private void focusEditor() {
        if (seedEdit == null) return;
        cancelSeedAnimator();
        seedEdit.setText(String.valueOf(seed));
        seedEdit.requestFocus();
        seedEdit.selectAll();
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(seedEdit, 0);
    }

    /** Called only when the user presses IME Done — the sole "submit" signal.
     *  Empty / non-numeric / non-positive input retains the original seed;
     *  the display is rewritten from {@code seed} so it matches what's
     *  actually committed. */
    private void commitEdit() {
        if (seedEdit == null || seedEdit.getWindowToken() == null) return;

        String trimmed = seedEdit.getText().toString().trim();
        long parsed = 0L;
        if (!trimmed.isEmpty()) {
            try { parsed = Long.parseLong(trimmed); }
            catch (NumberFormatException ignored) { /* parsed stays 0 */ }
        }
        if (parsed > 0L && parsed != seed) {
            seed = parsed;
            // fetchAndPopulate resets stats + description to pending before
            // firing the new /seed/:seed, so stale previous-seed data can't
            // leak into the tile row or description area.
            fetchAndPopulate();
        }
        seedEdit.setText(String.valueOf(seed));

        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(seedEdit.getWindowToken(), 0);
    }

    private void startSeedRollTween(TextView seedView, long target) {
        cancelSeedAnimator();
        seedAnimator = ValueAnimator.ofFloat(0f, 1f);
        seedAnimator.setDuration(TWEEN_MS);
        seedAnimator.setInterpolator(new DecelerateInterpolator());
        seedAnimator.addUpdateListener(anim -> {
            float t = (float) anim.getAnimatedValue();
            // Long math — seeds can exceed Integer.MAX_VALUE.
            long current = (long) (t * target);
            seedView.setText(String.valueOf(current));
        });
        seedAnimator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) {
                seedView.setText(String.valueOf(target));
            }
        });
        seedAnimator.start();
    }

    private void cancelSeedAnimator() {
        if (seedAnimator != null && seedAnimator.isRunning()) {
            seedAnimator.cancel();
        }
        seedAnimator = null;
    }

    /** 9-digit random seed matching Brogue's clock-based seed shape at
     *  the current date range. See RANDOM_SEED_MIN/MAX for rationale. */
    private long pickRandomSeed() {
        long span = RANDOM_SEED_MAX - RANDOM_SEED_MIN + 1;
        return RANDOM_SEED_MIN + (Math.abs(random.nextLong()) % span);
    }
}
