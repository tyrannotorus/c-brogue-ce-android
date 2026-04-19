package org.broguece.game;

import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

/** Stack of layered title-screen overlays (Credits, Community, Custom Seed, ...)
 *  rendered above the engine-owned start menu. Each push captures a builder so
 *  the modal can be rebuilt after a C round-trip — e.g. cancelling the native
 *  seed prompt bounces us back through showStartMenu; on re-entry we restore
 *  whatever was on top before. When the engine signals a game has begun
 *  (setOverlayVisible(true)) the stack is dropped so overlays can't outlive
 *  the run. */
final class ModalStack {

    private static final class Frame {
        final java.util.function.Supplier<View> builder;
        View overlay;
        Frame(java.util.function.Supplier<View> b) { this.builder = b; }
    }

    private final BrogueActivity activity;
    private final java.util.Deque<Frame> frames = new java.util.ArrayDeque<>();

    ModalStack(BrogueActivity activity) {
        this.activity = activity;
    }

    boolean isEmpty() { return frames.isEmpty(); }

    void push(java.util.function.Supplier<View> builder) {
        Frame f = new Frame(builder);
        f.overlay = builder.get();
        frames.push(f);
        android.util.Log.d("BrogueModal", "push -> depth=" + frames.size());
    }

    void pop() {
        if (frames.isEmpty()) return;
        Frame f = frames.pop();
        detach(f.overlay);
        hideSoftKeyboard();
        android.util.Log.d("BrogueModal", "pop -> depth=" + frames.size());
    }

    /** Drop everything — used when we know a game is starting. */
    void clear() {
        android.util.Log.d("BrogueModal", "clear depth=" + frames.size());
        for (Frame f : frames) detach(f.overlay);
        frames.clear();
        hideSoftKeyboard();
    }

    /** Rebuild overlays from bottom up. Called from setOverlayVisible(false)
     *  when the engine returns to the title menu. */
    void restore() {
        android.util.Log.d("BrogueModal", "restore? depth=" + frames.size());
        if (frames.isEmpty()) return;
        java.util.ArrayDeque<Frame> saved = new java.util.ArrayDeque<>(frames);
        frames.clear();
        for (java.util.Iterator<Frame> it = saved.descendingIterator(); it.hasNext(); ) {
            push(it.next().builder);
        }
    }

    private void detach(View v) {
        if (v != null && v.getParent() != null) {
            ((ViewGroup) v.getParent()).removeView(v);
        }
    }

    /** Dismisses the soft keyboard if it's up. No-op otherwise. The Custom
     *  Seed modal's EditText is the one flow that can leave an IME visible
     *  after the view is detached — only clear focus on an EditText, never
     *  on anything else (notably: never on the SDL surface, which must keep
     *  focus so dispatchKeyEvent routes to SDL's native key handler). */
    private void hideSoftKeyboard() {
        View content = activity.findViewById(android.R.id.content);
        if (content == null || content.getWindowToken() == null) return;
        android.view.inputmethod.InputMethodManager imm =
            (android.view.inputmethod.InputMethodManager)
                activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(content.getWindowToken(), 0);
        }
        View focused = activity.getCurrentFocus();
        if (focused instanceof EditText) focused.clearFocus();
    }
}
