package org.broguece.game;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.Deque;

/** In-game achievement unlock toast. Slides down from the top of the game
 *  overlay, dwells, slides out. Multiple unlocks from the same event queue
 *  up so neither stomps the other. All show() calls must be on the UI thread.
 *
 *  Non-interactive: the view doesn't consume touches, so the game underneath
 *  keeps receiving input while the toast animates. */
final class AchievementToast {

    private static final long SLIDE_MS = 260;
    private static final long DWELL_MS = 2800;

    private final BrogueActivity activity;
    private final FrameLayout parent;
    private final Deque<Achievement> queue = new ArrayDeque<>();
    private View current;

    AchievementToast(BrogueActivity activity, FrameLayout parent) {
        this.activity = activity;
        this.parent = parent;
    }

    void show(Achievement achievement) {
        if (achievement == null) return;
        queue.addLast(achievement);
        if (current == null) drain();
    }

    private void drain() {
        Achievement next = queue.pollFirst();
        if (next == null) {
            current = null;
            return;
        }
        current = build(next);
        animateIn(current);
    }

    private View build(Achievement a) {
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Palette.ITEM_BG);
        int padH = activity.dpToPx(16);
        int padV = activity.dpToPx(12);
        panel.setPadding(padH, padV, padH, padV);

        TextView header = new TextView(activity);
        header.setText("ACHIEVEMENT UNLOCKED");
        header.setTextColor(Palette.FLAME_EMBER);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        header.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        header.setLetterSpacing(0.2f);
        panel.addView(header);

        TextView title = new TextView(activity);
        title.setText(a.title);
        title.setTextColor(Palette.GHOST_WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = activity.dpToPx(2);
        panel.addView(title, titleLp);

        TextView desc = new TextView(activity);
        desc.setText(a.description);
        desc.setTextColor(Palette.PALE_BLUE);
        desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        desc.setTypeface(Typeface.MONOSPACE);
        panel.addView(desc);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = activity.dpToPx(24);
        parent.addView(panel, lp);
        return panel;
    }

    private void animateIn(View v) {
        float enterFrom = -activity.dpToPx(20);
        v.setAlpha(0f);
        v.setTranslationY(enterFrom);
        v.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(SLIDE_MS)
            .setListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator a) {
                    v.postDelayed(() -> animateOut(v), DWELL_MS);
                }
            })
            .start();
    }

    private void animateOut(View v) {
        float exitTo = -activity.dpToPx(20);
        v.animate()
            .alpha(0f)
            .translationY(exitTo)
            .setDuration(SLIDE_MS)
            .setListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator a) {
                    parent.removeView(v);
                    drain();
                }
            })
            .start();
    }
}
