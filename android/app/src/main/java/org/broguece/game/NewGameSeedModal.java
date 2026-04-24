package org.broguece.game;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import java.util.Random;

/** Main-menu "New Game" entry. Picks a random seed client-side, tweens the
 *  header label up from 0 → chosen seed, and lets the player tap the seed
 *  to override it via the numeric keyboard. Also handles the custom-seed
 *  entry point — tapping the seed from the fresh state is identical to
 *  entering one from scratch. */
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

    NewGameSeedModal(BrogueActivity activity) { super(activity); }

    @Override protected String getTitleUpper() { return "NEW GAME"; }

    /** Opens with a fresh random seed and the intro roll-up animation. */
    void show() {
        playIntroTween = true;
        show(pickRandomSeed());
    }

    @Override
    protected void onSeedViewBuilt(TextView seedView) {
        // Visually inline with the other seed-details modals: white,
        // no underline. Tap discoverability comes from the ripple on press.
        seedView.setClickable(true);
        seedView.setOnClickListener(v -> openSeedEditor());

        if (playIntroTween) {
            playIntroTween = false;
            startSeedRollTween(seedView, seed);
        } else {
            seedView.setText(String.valueOf(seed));
        }
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

    private void openSeedEditor() {
        // Tap during the roll kills the tween and snaps to the final seed
        // so the keyboard pre-fills with the number the player saw resolve.
        cancelSeedAnimator();
        if (headerLabelView != null) headerLabelView.setText(String.valueOf(seed));

        activity.textInputDialog.show(
            "Enter Seed", String.valueOf(seed), MAX_SEED_DIGITS, true,
            result -> {
                if (result == null) return;              // cancel
                String trimmed = result.trim();
                if (trimmed.isEmpty()) return;           // blank submit == cancel
                long parsed;
                try {
                    parsed = Long.parseLong(trimmed);
                } catch (NumberFormatException e) {
                    return;
                }
                if (parsed <= 0) return;                 // engine requires seed >= 1
                if (parsed == seed) return;              // no change
                seed = parsed;
                if (headerLabelView != null) headerLabelView.setText(String.valueOf(parsed));
                // fetchAndPopulate resets stats + description to pending
                // before firing the new /seed/:seed, so stale previous-seed
                // data can't leak into the tile row or description area.
                fetchAndPopulate();
            });
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
