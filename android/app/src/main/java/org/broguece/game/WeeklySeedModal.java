package org.broguece.game;

import org.json.JSONObject;

/** Weekly contest branch: renders the current-week seed with its date in
 *  place of the seed number and a fixed r/brogueforum blurb as the
 *  description. Data comes from /weekly (same shape as /seed/:seed plus
 *  the seed itself); CommunityModal prefetches it on open so the common
 *  path opens this modal with zero network calls. The no-arg fallback
 *  fetches /weekly itself if the prefetch missed. */
final class WeeklySeedModal extends SeedDetailsModal {

    private static final String SUBREDDIT_URL =
        "https://www.reddit.com/r/brogueforum/";
    private static final String DESCRIPTION =
        "Follow the contest at r/brogueforum";
    private static final String HEADER_PENDING = "—";

    private JSONObject prefetched;

    WeeklySeedModal(BrogueActivity activity) { super(activity); }

    /** Preferred entry point: data fetched by CommunityModal and handed
     *  straight through. No network calls happen on modal open. */
    void show(JSONObject prefetched) {
        this.prefetched = prefetched;
        this.seed = parseSeedOrZero(prefetched);
        activity.modalStack.push(this::buildOverlay);
    }

    /** Fallback when no prefetch is available (CommunityModal's /weekly
     *  failed, or modal opened from somewhere that doesn't prefetch). */
    void show() {
        this.prefetched = null;
        this.seed = 0;
        activity.modalStack.push(this::buildOverlay);
    }

    @Override protected String getTitleUpper() { return "WEEKLY CONTEST"; }

    /** Placeholder until the date label lands in {@link #applyFetchedData}. */
    @Override protected String getHeaderLabel() { return HEADER_PENDING; }

    @Override protected String getDescriptionOverride() { return DESCRIPTION; }
    @Override protected String getDescriptionUrl() { return SUBREDDIT_URL; }

    @Override
    protected void fetchAndPopulate() {
        resetStatsToPending();
        applyDescriptionPreFetch();
        if (prefetched != null) {
            applyFetchedData(prefetched);
            return;
        }
        if (!activity.api.hasInternetCapability()) return;
        final int myId = nextRequestId();
        activity.api.fetchWeekly(obj -> {
            if (!isCurrentRequest(myId)) return;
            if (obj == null || descriptionView == null) return;
            this.seed = parseSeedOrZero(obj);
            applyFetchedData(obj);
        });
    }

    /** The seeds.description column stores a human-readable date label
     *  ("April 22 – April 29") for weekly rows. Route it into the header
     *  instead of the description area. */
    @Override
    protected void applyFetchedData(JSONObject obj) {
        super.applyFetchedData(obj);
        String date = obj.isNull("description")
            ? null : obj.optString("description", null);
        if (date != null && !date.isEmpty() && headerLabelView != null) {
            headerLabelView.setText(date);
        }
    }

    private static long parseSeedOrZero(JSONObject obj) {
        if (obj == null) return 0;
        try {
            return Long.parseLong(obj.getString("seed"));
        } catch (Exception e) {
            return 0;
        }
    }
}
