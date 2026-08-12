package org.broguece.game;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

/** On-screen 3x3 movement pad, shown instead of swipe-to-move when Movement is
 *  set to D-Pad. Each cell sends one vi-key step on release.
 *
 *  A long press puts the pad into edit mode: it takes on the toolbar's action
 *  chrome, grows a resize handle in its top-left corner, and its cells go inert.
 *  Dragging the body moves it, dragging the handle scales it, and a tap anywhere
 *  outside commits and leaves. Dropping the pad near its default slot snaps it
 *  home, which is how a botched placement is undone. */
final class DPadOverlay {

    static final String PREF_MOVEMENT_MODE = "movement_mode";
    static final int MOVEMENT_SWIPE = 0;
    static final int MOVEMENT_DPAD  = 1;

    static final int SIZE_DP = 180;

    /** Clears the toolbar (44dp button + 4dp padding each side) plus a gap. */
    static final int BOTTOM_MARGIN_DP = 60;

    /** Lines the pad's cells up with the toolbar's end button. The cells carry
     *  the toolbar's own button margin, so the pad only has to clear the bar's
     *  padding for the two right edges to meet. */
    static final int RIGHT_MARGIN_DP =
        BrogueActivity.EDGE_SAFE_DP + ActionsToolbar.BAR_PAD_DP;

    private static final String PREF_OFFSET_X = "dpad_offset_x";
    private static final String PREF_OFFSET_Y = "dpad_offset_y";
    private static final String PREF_SCALE_PCT = "dpad_scale_pct";

    /** How near the default slot a drag has to land to click back into it. */
    private static final int SNAP_TO_DEFAULT_DP = 20;

    // The floor keeps the pad big enough to grab and scale back up; the ceiling
    // keeps it and its bottom margin inside a landscape phone's short edge.
    private static final int MIN_SCALE_PCT = 50;
    private static final int MAX_SCALE_PCT = 150;

    private static final int HANDLE_DP = 22;
    private static final int HANDLE_TOUCH_DP = 44;
    private static final int GRID_LINE_DP = 1;

    /** Breathing room around a cell's glyph, so it shrinks with the pad rather
     *  than overflowing at the smallest scale. */
    private static final int GLYPH_PAD_DP = 6;

    /** Deliberately longer than the platform long-press, and fixed rather than
     *  following the accessibility touch-and-hold delay. A finger resting on a
     *  movement key between steps is normal, and arming on it costs the step
     *  as well as the mode. */
    private static final long EDIT_HOLD_MS = 1000L;

    private final BrogueActivity activity;
    private final Runnable onInteraction;
    private final DragGrid root;

    DPadOverlay(BrogueActivity activity, FrameLayout host, Runnable onInteraction) {
        this.activity = activity;
        this.onInteraction = onInteraction;
        this.root = build(host);
    }

    View getView() {
        return root;
    }

    /** Edge length of the pad at its saved scale. */
    int sizePx() {
        return activity.dpToPx(SIZE_DP) * savedScalePct() / 100;
    }

    /** Leaves edit mode if it is active, committing the current placement. */
    void exitEditMode() {
        root.endEdit();
    }

    /** Mirrors a hand-placed pad when the interface changes hands, so it keeps
     *  the same spot relative to its now-opposite anchor. */
    void mirrorPlacement() {
        root.mirrorPlacement();
    }

    private int savedScalePct() {
        int pct = GameSettings.getInt(activity, PREF_SCALE_PCT, 100);
        return Math.max(MIN_SCALE_PCT, Math.min(MAX_SCALE_PCT, pct));
    }

    private DragGrid build(FrameLayout host) {
        // The resize handle overhangs the pad's top-left corner, and a parent
        // clips each child to its own bounds unless told otherwise.
        host.setClipChildren(false);

        DragGrid pad = new DragGrid(activity, host);
        pad.setTranslationX(GameSettings.getInt(activity, PREF_OFFSET_X, 0));
        pad.setTranslationY(GameSettings.getInt(activity, PREF_OFFSET_Y, 0));

        LinearLayout grid = new LinearLayout(activity);
        grid.setOrientation(LinearLayout.VERTICAL);
        addRow(grid, new Cell(-135f, "Move up-left", 'y'),
                     new Cell(-90f, "Move up", 'k'),
                     new Cell(-45f, "Move up-right", 'u'));
        addRow(grid, new Cell(180f, "Move left", 'h'),
                     new Cell("Rest one turn", 'z'),
                     new Cell(0f, "Move right", 'l'));
        addRow(grid, new Cell(135f, "Move down-left", 'b'),
                     new Cell(90f, "Move down", 'j'),
                     new Cell(45f, "Move down-right", 'n'));

        // Index 0: the pad's constructor already added the resize handle, which
        // has to stay on top of the grid.
        pad.addView(grid, 0, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));
        return pad;
    }

    private void addRow(LinearLayout grid, Cell... cells) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        grid.addView(row, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        int margin = activity.dpToPx(ActionsToolbar.BTN_MARGIN_DP);
        int glyphPad = activity.dpToPx(GLYPH_PAD_DP);

        for (Cell cell : cells) {

            // The glyph rotates, so it can't carry the cell's background —
            // rotation would take the background with it.
            ImageView glyph = new ImageView(activity);
            glyph.setImageResource(cell.center
                ? R.drawable.ic_dpad_center : R.drawable.ic_dpad_arrow);
            glyph.setColorFilter(Palette.PALE_BLUE);
            glyph.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            glyph.setPadding(glyphPad, glyphPad, glyphPad, glyphPad);
            glyph.setRotation(cell.rotationDegrees);

            // Same chrome as a toolbar action button, so the pad reads as more
            // of the same control set rather than as a separate widget.
            GradientDrawable face = new GradientDrawable();
            face.setShape(GradientDrawable.RECTANGLE);
            face.setCornerRadius(activity.dpToPx(2));
            face.setColor(Palette.DEEP_INDIGO);
            face.setStroke(1, Palette.BORDER_DIM);

            FrameLayout button = new FrameLayout(activity);
            button.setBackground(new RippleDrawable(
                ColorStateList.valueOf(Palette.RIPPLE_GLOW), face, null));
            button.setContentDescription(cell.contentDescription);
            button.setOnClickListener(v -> {
                onInteraction.run();
                KeyInput.sendChar(activity, cell.command);
            });
            button.addView(glyph, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            params.setMargins(margin, margin, margin, margin);
            row.addView(button, params);
        }
    }

    private static final class Cell {
        final boolean center;
        final float rotationDegrees;
        final String contentDescription;
        final char command;

        Cell(float rotationDegrees, String contentDescription, char command) {
            this.center = false;
            this.rotationDegrees = rotationDegrees;
            this.contentDescription = contentDescription;
            this.command = command;
        }

        Cell(String contentDescription, char command) {
            this.center = true;
            this.rotationDegrees = 0f;
            this.contentDescription = contentDescription;
            this.command = command;
        }
    }

    /** Hosts the grid and the resize handle, and owns the edit-mode gesture. */
    private static final class DragGrid extends FrameLayout {

        private final BrogueActivity activity;
        private final FrameLayout host;
        private final ImageView handle;
        private final Drawable editChrome;
        private final Paint gridPaint;
        private final float gridStroke;
        private final Runnable armEdit = this::beginEdit;
        private final int touchSlop;
        private final int snapRadius;
        private final int handleTouchPx;
        private final int baseSizePx;

        private float downRawX, downRawY;
        private float dragStartTranslationX, dragStartTranslationY;
        private int dragStartSizePx;
        private boolean editing;
        private boolean resizing;
        private boolean snappedToDefault;

        DragGrid(BrogueActivity activity, FrameLayout host) {
            super(activity);
            this.activity = activity;
            this.host = host;
            touchSlop = ViewConfiguration.get(activity).getScaledTouchSlop();
            snapRadius = activity.dpToPx(SNAP_TO_DEFAULT_DP);
            handleTouchPx = activity.dpToPx(HANDLE_TOUCH_DP);
            baseSizePx = activity.dpToPx(SIZE_DP);

            // Deliberately not anti-aliased. A hairline centred on a subpixel
            // boundary dissolves into two half-covered rows, which reads as a
            // dim line at rest and flickers as the pad is dragged.
            gridStroke = Math.max(1, activity.dpToPx(GRID_LINE_DP));
            gridPaint = new Paint();
            gridPaint.setColor(Palette.BORDER_ACTIVE);
            gridPaint.setStyle(Paint.Style.STROKE);
            gridPaint.setStrokeWidth(gridStroke);
            setWillNotDraw(false);

            // Worn only in edit mode: the pad is otherwise transparent, which is
            // unreadable against a lit dungeon. Same fill and active outline as
            // a toolbar action button.
            GradientDrawable chrome = new GradientDrawable();
            chrome.setShape(GradientDrawable.RECTANGLE);
            chrome.setCornerRadius(activity.dpToPx(2));
            chrome.setColor(Palette.DEEP_INDIGO);
            chrome.setStroke(activity.dpToPx(2), Palette.BORDER_ACTIVE);
            editChrome = chrome;

            handle = new ImageView(activity);
            handle.setImageResource(R.drawable.ic_dpad_resize);
            handle.setColorFilter(Palette.BORDER_ACTIVE);
            handle.setVisibility(GONE);

            // Centred on the corner it drags, the way transform handles sit on a
            // selection box. Fully inside would read as part of the up-left key.
            int handlePx = activity.dpToPx(HANDLE_DP);
            FrameLayout.LayoutParams handleParams = new FrameLayout.LayoutParams(
                handlePx, handlePx, Gravity.TOP | Gravity.START);
            handleParams.setMargins(-handlePx / 2, -handlePx / 2, 0, 0);
            setClipChildren(false);
            setClipToPadding(false);
            addView(handle, handleParams);
        }

        /** Divider lines, drawn only while the pad is being arranged — at rest
         *  the cells are self-contained buttons and need no lattice. They land
         *  in the gaps between cells; the chrome supplies the outer box. */
        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (!editing) return;

            float inset = gridStroke / 2f;
            float w = getWidth();
            float h = getHeight();

            for (int i = 1; i < 3; i++) {
                float x = Math.round(w * i / 3f);
                float y = Math.round(h * i / 3f);
                canvas.drawLine(x, inset, x, h - inset, gridPaint);
                canvas.drawLine(inset, y, w - inset, y, gridPaint);
            }
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent event) {
            // While arranging, the cells are inert and every touch drives the pad.
            if (editing) return true;

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downRawX = event.getRawX();
                    downRawY = event.getRawY();
                    postDelayed(armEdit, EDIT_HOLD_MS);
                    return false;
                case MotionEvent.ACTION_MOVE:
                    if (movedPastSlop(event)) removeCallbacks(armEdit);
                    return false;
                default:
                    removeCallbacks(armEdit);
                    return false;
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (editing) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        resizing = event.getX() < handleTouchPx
                                && event.getY() < handleTouchPx;
                        dragStartTranslationX = getTranslationX();
                        dragStartTranslationY = getTranslationY();
                        dragStartSizePx = getWidth();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (resizing) resizeFrom(event); else moveFrom(event);
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        persist();
                        break;
                }
            }
            // Swallow whatever the cells didn't take so it can't reach the map.
            return true;
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            super.onLayout(changed, l, t, r, b);
            if (changed) clampTranslation();
        }

        private boolean movedPastSlop(MotionEvent event) {
            return Math.hypot(event.getRawX() - downRawX,
                              event.getRawY() - downRawY) > touchSlop;
        }

        private void moveFrom(MotionEvent event) {
            // Whole pixels only: touch coordinates are fractional, and a
            // fractional offset resamples the grid lines every frame.
            setTranslationX(Math.round(dragStartTranslationX + event.getRawX() - downRawX));
            setTranslationY(Math.round(dragStartTranslationY + event.getRawY() - downRawY));
            clampTranslation();
            snapToDefault();
            // The game's SurfaceView shows through wherever the window is
            // transparent, and that region is only recomputed on layout. Without
            // this the pad vanishes as it leaves the slot it was laid out in.
            if (getParent() != null) getParent().requestTransparentRegion(this);
        }

        /** The handle sits at the top-left and the pad is anchored bottom-right,
         *  so dragging up and left grows it about a fixed corner. Both axes feed
         *  one edge length, since the pad is square. */
        private void resizeFrom(MotionEvent event) {
            float grow = ((downRawX - event.getRawX())
                        + (downRawY - event.getRawY())) / 2f;
            int size = Math.round(dragStartSizePx + grow);
            size = Math.max(baseSizePx * MIN_SCALE_PCT / 100,
                   Math.min(baseSizePx * MAX_SCALE_PCT / 100, size));

            ViewGroup.LayoutParams params = getLayoutParams();
            if (params.width == size) return;
            params.width = size;
            params.height = size;
            // Relayout also refreshes the SurfaceView's transparent region.
            setLayoutParams(params);
        }

        private void beginEdit() {
            editing = true;
            resizing = false;
            dragStartTranslationX = getTranslationX();
            dragStartTranslationY = getTranslationY();
            dragStartSizePx = getWidth();

            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            setBackground(editChrome);
            handle.setVisibility(VISIBLE);

            // Everything outside the pad becomes the "done" target, and absorbs
            // the tap so it can't also reach the map as a travel-to.
            host.setOnClickListener(v -> endEdit());
        }

        void mirrorPlacement() {
            setTranslationX(-getTranslationX());
            persist();
        }

        void endEdit() {
            if (!editing) return;
            editing = false;
            setBackground(null);
            handle.setVisibility(GONE);
            host.setOnClickListener(null);
            host.setClickable(false);
            persist();
        }

        private void persist() {
            GameSettings.setInt(activity, PREF_OFFSET_X, Math.round(getTranslationX()));
            GameSettings.setInt(activity, PREF_OFFSET_Y, Math.round(getTranslationY()));
            GameSettings.setInt(activity, PREF_SCALE_PCT,
                Math.round(getWidth() * 100f / baseSizePx));
        }

        /** The default slot is translation zero, so dropping the pad near it
         *  clicks it back home — a reset without a menu entry. Each move
         *  recomputes the translation from the finger, so the zeroing here
         *  never traps the drag inside the snap radius. */
        private void snapToDefault() {
            boolean within = Math.hypot(getTranslationX(), getTranslationY()) < snapRadius;
            if (within) {
                setTranslationX(0f);
                setTranslationY(0f);
            }
            if (within != snappedToDefault) {
                snappedToDefault = within;
                if (within) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            }
        }

        /** Keeps the pad fully on screen after a drag, resize or layout change. */
        private void clampTranslation() {
            View parent = (View) getParent();
            if (parent == null || parent.getWidth() == 0) return;
            setTranslationX(Math.max(-getLeft(),
                Math.min(parent.getWidth() - getRight(), getTranslationX())));
            setTranslationY(Math.max(-getTop(),
                Math.min(parent.getHeight() - getBottom(), getTranslationY())));
        }
    }

}
