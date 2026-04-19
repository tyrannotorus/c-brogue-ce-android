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

/** Community modal: browse the server-hosted Weekly Contest seed and the
 *  catalog of fun seeds. Tapping either pushes the internal Seed Detail
 *  modal with stats + a Play button. Session cache + fetch live here;
 *  chrome is shared via {@link ModalChrome}. */
final class CommunityModal {

    private static final int FUN_ROW_HEIGHT_DP = 50;
    private static final int FUN_ROWS_MAX      = 10;
    private static final int SEED_HEADER_SP    = 13;

    private final BrogueActivity activity;

    private boolean seedsFetchedThisSession;
    private long cachedWeeklySeed;                     // 0 = unknown
    private java.util.List<FunSeedRow> cachedFunSeeds; // null = unknown
    private boolean seedsFetchInFlight;

    private FrameLayout weeklyState;
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
        seedsFetchedThisSession = false;
        cachedWeeklySeed = 0;
        cachedFunSeeds = null;
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

    private static final class SeedDetail {
        final long seed;
        final String title;
        final String description;
        final String url;
        final String source;
        final int plays, deaths, wins;
        SeedDetail(long seed, String title, String description, String url,
                   String source, int plays, int deaths, int wins) {
            this.seed = seed;
            this.title = title;
            this.description = description;
            this.url = url;
            this.source = source;
            this.plays = plays;
            this.deaths = deaths;
            this.wins = wins;
        }
    }

    // ---- List overlay ----

    private View buildListOverlay() {
        FrameLayout root = new FrameLayout(activity);
        listRoot = root;

        LinearLayout panel = ModalChrome.buildPanel(activity, root, "COMMUNITY");

        // Your Stats — button that opens PlayerStatsModal. Hidden when
        // telemetry is off, since the installId is what the server would key
        // the stats on.
        if (activity.api.telemetryEnabled()) {
            addSectionHeader(panel, "Your Stats",
                "See how your runs have fared.", null);
            StartMenu.addButton(panel, "View Your Stats", true,
                v -> activity.playerStatsModal.show());
        }

        // Weekly Contest section — dynamic, state-managed.
        addSectionHeader(panel,
            "Weekly Contest",
            "A new weekly seed every Tuesday!",
            null);
        weeklyState = new FrameLayout(activity);
        LinearLayout.LayoutParams weeklyStateP = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        weeklyStateP.bottomMargin = activity.dpToPx(4);
        panel.addView(weeklyState, weeklyStateP);

        // Fun Seeds section — fixed height reserved for up to 10 rows so the
        // layout doesn't jump between loading, error, and data states.
        addSectionHeader(panel,
            "Play Fun seeds",
            "Brogue Fan-submitted seeds",
            null);
        funState = new FrameLayout(activity);
        funState.setMinimumHeight(activity.dpToPx(FUN_ROW_HEIGHT_DP * FUN_ROWS_MAX));
        panel.addView(funState, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));

        if (seedsFetchedThisSession) {
            renderWeeklyData(cachedWeeklySeed);
            renderFunData(cachedFunSeeds);
        } else {
            renderWeeklyLoading();
            renderFunLoading();
        }

        // Custom tail: ScrollView inside SwipeRefreshLayout (for pull-to-refresh).
        // ModalChrome.present() can't do this — it's specific to the list modal.
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(panel);

        swipe = new SwipeRefreshLayout(activity);
        swipe.addView(scroll);
        swipe.setOnRefreshListener(() -> fetchSeeds(true));
        root.addView(swipe, ModalChrome.centeredPanelParams(activity));

        activity.addContentView(root, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));

        ModalChrome.animateIn(panel);

        if (!seedsFetchedThisSession) {
            fetchSeeds(false);
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

    private void renderWeeklyLoading() {
        if (weeklyState == null) return;
        weeklyState.removeAllViews();
        weeklyState.addView(makeStateSpinner());
    }

    private void renderWeeklyError(String message) {
        if (weeklyState == null) return;
        weeklyState.removeAllViews();
        weeklyState.addView(makeStateMessage(message));
    }

    private void renderWeeklyData(long weekly) {
        if (weeklyState == null) return;
        weeklyState.removeAllViews();
        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        StartMenu.addButton(container, "Play Weekly Contest", true, v -> {
            SeedDetail d = new SeedDetail(weekly,
                "Weekly Contest",
                "Follow the contest at r/brogueforum",
                "https://www.reddit.com/r/brogueforum/",
                "weekly", 127, 112, 15);
            activity.modalStack.push(() -> buildSeedDetailOverlay(d));
        });
        weeklyState.addView(container, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT));
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
            int[][] fakeStats = {
                {   47,   42,   5 },
                {  318,  280,  38 },
                {    8,    7,   1 },
                { 1204, 1071, 133 },
                {   62,   55,   7 },
                {   19,   16,   3 },
                {  446,  401,  45 },
                {   93,   84,   9 },
                {    3,    3,   0 },
                {  221,  194,  27 },
            };
            for (int i = 0; i < count; i++) {
                FunSeedRow r = rows.get(i);
                int[] s = fakeStats[i % fakeStats.length];
                addFunSeedRow(list, r.seed, r.description, s[0], s[1], s[2]);
            }
        }
        funState.addView(list, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT));
    }

    /** Fetch /seeds. Success updates the cache and repaints the two sections;
     *  any failure paints "No Network Connection" / "Server Unavailable". Not
     *  gated by the telemetry toggle — /seeds sends no identifying info. */
    private void fetchSeeds(boolean fromPullToRefresh) {
        if (seedsFetchInFlight) {
            if (fromPullToRefresh && swipe != null) {
                swipe.setRefreshing(false);
            }
            return;
        }

        if (!activity.api.hasInternetCapability()) {
            renderWeeklyError("No Network Connection");
            renderFunError("No Network Connection");
            if (swipe != null) swipe.setRefreshing(false);
            return;
        }

        if (!seedsFetchedThisSession) {
            renderWeeklyLoading();
            renderFunLoading();
        }

        seedsFetchInFlight = true;
        final View modalRoot = listRoot;

        activity.api.fetchSeeds(obj -> {
            seedsFetchInFlight = false;
            if (swipe != null) swipe.setRefreshing(false);

            boolean modalStillOpen = modalRoot != null && modalRoot.isAttachedToWindow();

            if (obj == null) {
                if (modalStillOpen) {
                    renderWeeklyError("Server Unavailable");
                    renderFunError("Server Unavailable");
                }
                return;
            }

            long weekly;
            java.util.List<FunSeedRow> fun;
            try {
                weekly = obj.getJSONObject("weekly").getLong("seed");
                JSONArray funArr = obj.getJSONArray("fun");
                fun = new java.util.ArrayList<>(funArr.length());
                for (int i = 0; i < funArr.length(); i++) {
                    JSONObject r = funArr.getJSONObject(i);
                    fun.add(new FunSeedRow(r.getLong("seed"),
                                            r.optString("description", "")));
                }
            } catch (Exception e) {
                if (modalStillOpen) {
                    renderWeeklyError("Server Unavailable");
                    renderFunError("Server Unavailable");
                }
                return;
            }

            cachedWeeklySeed = weekly;
            cachedFunSeeds = fun;
            seedsFetchedThisSession = true;

            if (modalStillOpen) {
                renderWeeklyData(weekly);
                renderFunData(fun);
            }
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

    private void addFunSeedRow(LinearLayout panel, long seed, String description,
                                int plays, int deaths, int wins) {
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

        row.setOnClickListener(v -> {
            SeedDetail d = new SeedDetail(seed, "Fun Seed", description, null,
                "fun", plays, deaths, wins);
            activity.modalStack.push(() -> buildSeedDetailOverlay(d));
        });

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, activity.dpToPx(2), 0, activity.dpToPx(2));
        panel.addView(row, p);
    }

    // ---- Seed detail overlay (private — reached only from the list) ----

    private View buildSeedDetailOverlay(SeedDetail d) {
        FrameLayout root = new FrameLayout(activity);
        LinearLayout panel = ModalChrome.buildPanel(activity, root, d.title.toUpperCase());

        TextView seedView = new TextView(activity);
        seedView.setText(String.valueOf(d.seed));
        seedView.setTextColor(Palette.GHOST_WHITE);
        seedView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        seedView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        seedView.setGravity(Gravity.CENTER);
        seedView.setPadding(0, activity.dpToPx(6), 0, activity.dpToPx(6));
        panel.addView(seedView);

        TextView descView = new TextView(activity);
        descView.setText(d.description);
        descView.setTextColor(Palette.PALE_BLUE);
        descView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        descView.setTypeface(Typeface.MONOSPACE);
        descView.setGravity(Gravity.CENTER);
        descView.setPadding(activity.dpToPx(8), activity.dpToPx(2),
                            activity.dpToPx(8), activity.dpToPx(16));
        if (d.url != null) {
            descView.setPaintFlags(descView.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
            descView.setOnClickListener(v -> Links.open(activity,d.url));
        }
        panel.addView(descView);

        LinearLayout statsRow = new LinearLayout(activity);
        statsRow.setOrientation(LinearLayout.HORIZONTAL);
        addStatTile(statsRow, d.plays, "PLAYS");
        addStatTile(statsRow, d.deaths, "DEATHS");
        addStatTile(statsRow, d.wins, "WINS");
        LinearLayout.LayoutParams statsP = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        statsP.setMargins(0, 0, 0, activity.dpToPx(16));
        panel.addView(statsRow, statsP);

        StartMenu.addButton(panel, "Play", true, v -> {
            activity.modalStack.clear();
            activity.startMenu.dismiss();
            activity.api.gameStart(d.seed, d.source);
            activity.nativeStartMenuResultWithSeed(StartMenu.CHOICE_PLAY_SEED, d.seed);
        });
        StartMenu.addButton(panel, "Back", true, v -> activity.modalStack.pop());

        ModalChrome.present(activity, root, panel);
        return root;
    }

    private void addStatTile(LinearLayout parent, int value, String label) {
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
        num.setText(String.valueOf(value));
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
    }
}
