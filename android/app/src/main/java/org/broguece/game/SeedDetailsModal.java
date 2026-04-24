package org.broguece.game;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

/** Visual + behavior base for the seed-preview modal family (Fun / Weekly /
 *  New Game / Replay Recent). Every instance shows: title, seed (or subclass-
 *  supplied header label), description, three stat tiles (plays / deaths /
 *  wins), Play + Back. On show, fetches /seed/:seed and tweens stats from 0
 *  to the fetched values; description falls back to "No Description Yet". */
class SeedDetailsModal {

    static final long TWEEN_MS = 700;
    private static final String STAT_PENDING = "-";
    /** Shown only when a fetch *succeeds* with a null/empty description —
     *  "this seed has no curator description yet". Never shown during a
     *  pending fetch or after a failed fetch; the description area is
     *  blank in those cases. */
    private static final String DESCRIPTION_EMPTY_SUCCESS = "No Description Yet";

    protected final BrogueActivity activity;
    protected long seed;

    protected TextView headerLabelView;
    protected TextView descriptionView;
    protected TextView playsView, deathsView, winsView;

    private ValueAnimator statsAnimator;
    /** Incremented every time a fetch is kicked off; late callbacks from
     *  superseded fetches ignore themselves by comparing against this.
     *  Matters on NewGameSeedModal where the user can re-edit the seed
     *  faster than a prior /seed/:seed call finishes. */
    private int latestRequestId;

    SeedDetailsModal(BrogueActivity activity) {
        this.activity = activity;
    }

    // ---- Subclass hooks ---------------------------------------------------

    protected String getTitleUpper() { return "SEED"; }

    /** Text rendered in the big seed-number area. Default is the seed value;
     *  WeeklySeedModal overrides to show the contest date instead. */
    protected String getHeaderLabel() { return String.valueOf(seed); }

    /** Non-null forces a fixed description (WeeklySeedModal); null means
     *  "use whatever the server returns, or {@value #DESCRIPTION_FALLBACK}". */
    protected String getDescriptionOverride() { return null; }

    /** When non-null alongside a description override, makes the description
     *  tappable and opens this URL. */
    protected String getDescriptionUrl() { return null; }

    /** Called once buildOverlay has constructed the seed header view, giving
     *  subclasses a chance to attach click listeners or run intro animations
     *  (NewGameSeedModal wires tap-to-edit + the roll-up tween here). */
    protected void onSeedViewBuilt(TextView seedView) {}

    // ---- Public API -------------------------------------------------------

    void show(long seed) {
        this.seed = seed;
        activity.modalStack.push(this::buildOverlay);
    }

    // ---- Internals --------------------------------------------------------

    protected final View buildOverlay() {
        cancelStatsAnimator();

        FrameLayout root = new FrameLayout(activity);
        LinearLayout panel = ModalChrome.buildPanel(activity, root, getTitleUpper());

        headerLabelView = makeHeaderLabelView();
        panel.addView(headerLabelView);
        onSeedViewBuilt(headerLabelView);

        descriptionView = makeDescriptionView();
        panel.addView(descriptionView);

        LinearLayout statsRow = new LinearLayout(activity);
        statsRow.setOrientation(LinearLayout.HORIZONTAL);
        playsView  = addStatTile(statsRow, STAT_PENDING, "PLAYS");
        deathsView = addStatTile(statsRow, STAT_PENDING, "DEATHS");
        winsView   = addStatTile(statsRow, STAT_PENDING, "WINS");
        LinearLayout.LayoutParams statsP = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        statsP.setMargins(0, 0, 0, activity.dpToPx(16));
        panel.addView(statsRow, statsP);

        StartMenu.addButton(panel, "Play", true, v -> launchRun());
        StartMenu.addButton(panel, "Back", true, v -> activity.modalStack.pop());

        ModalChrome.present(activity, root, panel);
        // fetchAndPopulate handles all of: reset views to pending, gate on
        // network availability, and gate on seed being latched. Subclasses
        // that open without a seed (WeeklySeedModal) override this.
        fetchAndPopulate();
        return root;
    }

    private TextView makeHeaderLabelView() {
        TextView v = new TextView(activity);
        v.setText(getHeaderLabel());
        v.setTextColor(Palette.GHOST_WHITE);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        v.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        v.setGravity(Gravity.CENTER);
        v.setPadding(0, activity.dpToPx(6), 0, activity.dpToPx(6));
        return v;
    }

    private TextView makeDescriptionView() {
        TextView v = new TextView(activity);
        v.setTextColor(Palette.PALE_BLUE);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        v.setTypeface(Typeface.MONOSPACE);
        v.setGravity(Gravity.CENTER);
        v.setPadding(activity.dpToPx(8), activity.dpToPx(2),
                     activity.dpToPx(8), activity.dpToPx(16));
        // Reserve two lines of vertical space up front so the modal doesn't
        // resize when the fetched description lands. Most descriptions fit
        // in one or two lines; longer ones still wrap naturally.
        v.setMinLines(2);
        return v;
    }

    /** Blank for non-override seeds (height reserved via setMinLines(2));
     *  hardcoded text + link for subclasses with an override. Called at
     *  start of every fetchAndPopulate so re-fetches (NewGameSeedModal
     *  edits) clear stale data from the previous seed. Idempotent. */
    protected final void applyDescriptionPreFetch() {
        String override = getDescriptionOverride();
        String url = getDescriptionUrl();
        if (override != null) {
            descriptionView.setText(override);
            if (url != null) {
                descriptionView.setPaintFlags(
                    descriptionView.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
                descriptionView.setOnClickListener(v -> Links.open(activity, url));
            }
            return;
        }
        descriptionView.setText("");
    }

    /** Reset views to their pending state, then kick off /seed/:seed when
     *  network is available and seed is latched. Subclasses override to
     *  swap the fetch endpoint (WeeklySeedModal → /weekly) or to consume
     *  a prefetched payload handed in by the caller. */
    protected void fetchAndPopulate() {
        resetStatsToPending();
        applyDescriptionPreFetch();
        if (seed <= 0) return;
        if (!activity.api.hasInternetCapability()) return;
        final int myId = ++latestRequestId;
        activity.api.fetchSeed(seed, obj -> {
            if (myId != latestRequestId) return;  // superseded by a later fetch
            if (obj == null || descriptionView == null) return;
            applyFetchedData(obj);
        });
    }

    protected void applyFetchedData(JSONObject obj) {
        if (getDescriptionOverride() == null) {
            String desc = obj.isNull("description")
                ? null : obj.optString("description", null);
            descriptionView.setText(
                desc != null && !desc.isEmpty() ? desc : DESCRIPTION_EMPTY_SUCCESS);
        }
        tweenStats(
            obj.optInt("plays", 0),
            obj.optInt("deaths", 0),
            obj.optInt("wins", 0));
    }

    /** Bumps the request-id so any in-flight callback discards itself. Used
     *  by subclasses that manage their own fetch path (WeeklySeedModal)
     *  and still need the race-safe gate. */
    protected final int nextRequestId() {
        return ++latestRequestId;
    }

    protected final boolean isCurrentRequest(int id) {
        return id == latestRequestId;
    }

    /** Restores the three stat tiles to their placeholder state. Used when
     *  the seed changes mid-modal (NewGameSeedModal after a user edit). */
    protected final void resetStatsToPending() {
        cancelStatsAnimator();
        if (playsView  != null) playsView.setText(STAT_PENDING);
        if (deathsView != null) deathsView.setText(STAT_PENDING);
        if (winsView   != null) winsView.setText(STAT_PENDING);
    }

    private void tweenStats(int plays, int deaths, int wins) {
        cancelStatsAnimator();
        statsAnimator = ValueAnimator.ofFloat(0f, 1f);
        statsAnimator.setDuration(TWEEN_MS);
        statsAnimator.setInterpolator(new DecelerateInterpolator());
        statsAnimator.addUpdateListener(anim -> {
            float t = (float) anim.getAnimatedValue();
            if (playsView  != null) playsView.setText(String.valueOf((int)(plays  * t)));
            if (deathsView != null) deathsView.setText(String.valueOf((int)(deaths * t)));
            if (winsView   != null) winsView.setText(String.valueOf((int)(wins   * t)));
        });
        statsAnimator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (playsView  != null) playsView.setText(String.valueOf(plays));
                if (deathsView != null) deathsView.setText(String.valueOf(deaths));
                if (winsView   != null) winsView.setText(String.valueOf(wins));
            }
        });
        statsAnimator.start();
    }

    private void cancelStatsAnimator() {
        if (statsAnimator != null && statsAnimator.isRunning()) {
            statsAnimator.cancel();
        }
        statsAnimator = null;
    }

    private void launchRun() {
        if (seed <= 0) return;   // weekly modal: /weekly hasn't landed yet
        activity.modalStack.clear();
        activity.startMenu.dismiss();
        // Starting a new run overwrites the save slot; drop the existing
        // save first so /game/start telemetry and the fresh run agree.
        activity.nativeDeleteSaveFile();
        activity.nativeStartMenuResultWithSeed(StartMenu.CHOICE_PLAY_SEED, seed);
    }

    private TextView addStatTile(LinearLayout parent, String value, String label) {
        LinearLayout tile = new LinearLayout(activity);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(activity.dpToPx(6), activity.dpToPx(12),
                        activity.dpToPx(6), activity.dpToPx(12));

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(activity.dpToPx(4));
        bg.setColor(Palette.ITEM_BG);
        tile.setBackground(bg);

        TextView num = new TextView(activity);
        num.setText(value);
        num.setTextColor(Palette.GHOST_WHITE);
        num.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        num.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        num.setGravity(Gravity.CENTER);
        tile.addView(num);

        TextView lbl = new TextView(activity);
        lbl.setText(label);
        lbl.setTextColor(Palette.PALE_BLUE);
        lbl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
        lbl.setTypeface(Typeface.MONOSPACE);
        lbl.setLetterSpacing(0.2f);
        lbl.setGravity(Gravity.CENTER);
        lbl.setPadding(0, activity.dpToPx(4), 0, 0);
        tile.addView(lbl);

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(activity.dpToPx(3), 0, activity.dpToPx(3), 0);
        parent.addView(tile, p);
        return num;
    }
}
