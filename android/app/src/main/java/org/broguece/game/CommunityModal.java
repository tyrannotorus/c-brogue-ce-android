package org.broguece.game;

import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONArray;
import org.json.JSONObject;

/** Community modal: browse the fun-seed catalog + a static entry point for
 *  the weekly contest. Weekly discovery (which seed is current) is owned
 *  by {@link WeeklySeedModal}, so this modal only fetches /fun. Tapping a
 *  row pushes a {@link SeedDetailsModal} subclass which fetches its own
 *  /seed/:seed — this modal no longer owns stats rendering. */
final class CommunityModal {

    private static final int FUN_ROW_HEIGHT_DP = 50;
    private static final int FUN_ROWS_MAX      = 10;
    private static final int SEED_HEADER_SP    = 13;

    private final BrogueActivity activity;

    private boolean funFetchedThisSession;
    private java.util.List<FunSeedRow> cachedFunSeeds; // null = unknown
    private boolean funFetchInFlight;
    /** Full /weekly payload cached from the parallel fetch so tapping Play
     *  Weekly Contest opens with zero network calls. null = never succeeded
     *  this session; the weekly modal falls back to its own /weekly fetch. */
    private JSONObject cachedWeeklyPayload;

    private FrameLayout funState;
    private SwipeRefreshLayout swipe;
    private View listRoot;

    CommunityModal(BrogueActivity activity) {
        this.activity = activity;
    }

    void show() {
        activity.modalStack.push(this::buildListOverlay);
    }

    void clearCache() {
        funFetchedThisSession = false;
        cachedFunSeeds = null;
        cachedWeeklyPayload = null;
    }

    // ---- Data classes ----

    private static final class FunSeedRow {
        final long seed;
        final String description;
        FunSeedRow(long seed, String description) {
            this.seed = seed;
            this.description = description;
        }
    }

    // ---- List overlay ----

    private View buildListOverlay() {
        FrameLayout root = new FrameLayout(activity);
        listRoot = root;

        LinearLayout panel = ModalChrome.buildPanel(activity, root, "COMMUNITY");

        // Your Stats — local-only, stored in the app's private files dir.
        addSectionHeader(panel, "Personal Stats",
            "See how your runs have fared.", null);
        StartMenu.addButton(panel, "View Stats", true,
            v -> activity.playerStatsModal.show());

        // Weekly Contest — button is always enabled. On tap, we hand the
        // pre-fetched /weekly payload to the modal so it opens with zero
        // network calls. If /weekly failed at open time (rare — it fires
        // in parallel with /fun, same network condition), the modal falls
        // back to its own /weekly fetch.
        addSectionHeader(panel,
            "Weekly Contest",
            "A new weekly seed every Tuesday!",
            null);
        StartMenu.addButton(panel, "Play Weekly Contest", true, v -> {
            if (cachedWeeklyPayload != null) {
                activity.weeklySeedModal.show(cachedWeeklyPayload);
            } else {
                activity.weeklySeedModal.show();
            }
        });

        // Fun Seeds section — fixed height reserved for up to 10 rows so the
        // layout doesn't jump between loading, error, and data states.
        addSectionHeader(panel,
            "Fan-submitted Seeds",
            "Fun seeds posted to r/brogueforum",
            null);
        funState = new FrameLayout(activity);
        funState.setMinimumHeight(activity.dpToPx(FUN_ROW_HEIGHT_DP * FUN_ROWS_MAX));
        panel.addView(funState, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));

        if (funFetchedThisSession) {
            renderFunData(cachedFunSeeds);
        } else {
            renderFunLoading();
        }

        // Custom tail: ScrollView inside SwipeRefreshLayout (for pull-to-refresh).
        // ModalChrome.present() can't do this — it's specific to the list modal.
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(panel);

        swipe = new SwipeRefreshLayout(activity);
        swipe.addView(scroll);
        swipe.setOnRefreshListener(() -> fetchFun(true));
        root.addView(swipe, ModalChrome.centeredPanelParams(activity));

        activity.addContentView(root, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));

        ModalChrome.animateIn(panel);

        if (!funFetchedThisSession) {
            fetchFun(false);
        }

        return root;
    }

    // ---- State rendering ----

    private FrameLayout.LayoutParams centeredInState() {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT);
        p.gravity = Gravity.CENTER;
        return p;
    }

    private View makeStateSpinner() {
        ProgressBar pb = new ProgressBar(activity, null, android.R.attr.progressBarStyleSmall);
        pb.setIndeterminate(true);
        Drawable d = pb.getIndeterminateDrawable();
        if (d != null) {
            d.setColorFilter(Palette.FLAME_EMBER, PorterDuff.Mode.SRC_IN);
        }
        int size = activity.dpToPx(28);
        FrameLayout wrapper = new FrameLayout(activity);
        FrameLayout.LayoutParams wp = new FrameLayout.LayoutParams(size, size);
        wp.gravity = Gravity.CENTER;
        wp.topMargin = activity.dpToPx(12);
        wp.bottomMargin = activity.dpToPx(12);
        wrapper.addView(pb, wp);
        return wrapper;
    }

    private View makeStateMessage(String text) {
        TextView tv = new TextView(activity);
        tv.setText(text);
        tv.setTextColor(Palette.PALE_BLUE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(activity.dpToPx(12), activity.dpToPx(14),
                      activity.dpToPx(12), activity.dpToPx(14));
        FrameLayout wrapper = new FrameLayout(activity);
        wrapper.addView(tv, centeredInState());
        return wrapper;
    }

    private void renderFunLoading() {
        if (funState == null) return;
        funState.removeAllViews();
        funState.addView(makeStateSpinner());
    }

    private void renderFunError(String message) {
        if (funState == null) return;
        funState.removeAllViews();
        funState.addView(makeStateMessage(message));
    }

    private void renderFunData(java.util.List<FunSeedRow> rows) {
        if (funState == null) return;
        funState.removeAllViews();
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        if (rows != null) {
            int count = Math.min(rows.size(), FUN_ROWS_MAX);
            for (int i = 0; i < count; i++) {
                FunSeedRow r = rows.get(i);
                addFunSeedRow(list, r.seed, r.description);
            }
        }
        funState.addView(list, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT));
    }

    /** Fetch /fun and /weekly in parallel. /fun drives the visible list
     *  state (loading/error/data). /weekly quietly populates
     *  {@link #cachedWeeklyPayload} for a zero-round-trip handoff when
     *  the user taps Play Weekly Contest; its failure is silent (the
     *  weekly modal will retry /weekly if the cache is missing). */
    private void fetchFun(boolean fromPullToRefresh) {
        if (funFetchInFlight) {
            if (fromPullToRefresh && swipe != null) {
                swipe.setRefreshing(false);
            }
            return;
        }

        if (!activity.api.hasInternetCapability()) {
            renderFunError("No Network Connection");
            if (swipe != null) swipe.setRefreshing(false);
            return;
        }

        if (!funFetchedThisSession) {
            renderFunLoading();
        }

        funFetchInFlight = true;
        final View modalRoot = listRoot;

        // Weekly — fires alongside /fun, independent completion. Failure is
        // silent; the weekly modal falls back to its own fetch on tap.
        activity.api.fetchWeekly(obj -> {
            if (obj != null) cachedWeeklyPayload = obj;
        });

        activity.api.fetchFun(arr -> {
            funFetchInFlight = false;
            if (swipe != null) swipe.setRefreshing(false);

            boolean modalStillOpen = modalRoot != null && modalRoot.isAttachedToWindow();

            if (arr == null) {
                if (modalStillOpen) renderFunError("Server Unavailable");
                return;
            }

            java.util.List<FunSeedRow> fun;
            try {
                // Seeds are JSON strings to preserve int64 precision —
                // JSON Number truncates integers above 2^53 in Node/JS.
                fun = new java.util.ArrayList<>(arr.length());
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject r = arr.getJSONObject(i);
                    fun.add(new FunSeedRow(
                        Long.parseLong(r.getString("seed")),
                        r.optString("description", "")));
                }
            } catch (Exception e) {
                if (modalStillOpen) renderFunError("Server Unavailable");
                return;
            }

            cachedFunSeeds = fun;
            funFetchedThisSession = true;

            if (modalStillOpen) renderFunData(fun);
        });
    }

    private void addSectionHeader(LinearLayout panel, String title,
                                   String subtitle, String url) {
        TextView header = new TextView(activity);
        header.setText(title);
        header.setTextColor(Palette.FLAME_EMBER);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, SEED_HEADER_SP);
        header.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        header.setPadding(activity.dpToPx(4), activity.dpToPx(22), 0, activity.dpToPx(2));
        panel.addView(header);

        TextView sub = new TextView(activity);
        sub.setText(subtitle);
        sub.setTextColor(Palette.PALE_BLUE);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, SEED_HEADER_SP);
        sub.setTypeface(Typeface.MONOSPACE);
        sub.setPadding(activity.dpToPx(4), 0, 0, activity.dpToPx(4));
        if (url != null) {
            sub.setPaintFlags(sub.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
            sub.setOnClickListener(v -> Links.open(activity,url));
        }
        panel.addView(sub);

        panel.addView(ModalChrome.makeEmberSeparator(activity),
                      ModalChrome.emberSeparatorParams(activity, 4, 4, 0, 8));
    }

    private void addFunSeedRow(LinearLayout panel, long seed, String description) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(activity.dpToPx(12), activity.dpToPx(8),
                       activity.dpToPx(12), activity.dpToPx(8));
        row.setMinimumHeight(activity.dpToPx(44));

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(activity.dpToPx(4));
        bg.setColor(Palette.ITEM_BG);
        row.setBackground(new RippleDrawable(
            ColorStateList.valueOf(Palette.RIPPLE_GLOW), bg, null));

        TextView seedView = new TextView(activity);
        seedView.setText(String.valueOf(seed));
        seedView.setTextColor(Palette.GHOST_WHITE);
        seedView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        seedView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        LinearLayout.LayoutParams seedP = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        seedP.setMargins(0, 0, activity.dpToPx(10), 0);
        row.addView(seedView, seedP);

        TextView descView = new TextView(activity);
        descView.setText(description);
        descView.setTextColor(Palette.PALE_BLUE);
        descView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        descView.setTypeface(Typeface.MONOSPACE);
        row.addView(descView, new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        row.setOnClickListener(v -> activity.funSeedModal.show(seed));

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, activity.dpToPx(2), 0, activity.dpToPx(2));
        panel.addView(row, p);
    }
}
