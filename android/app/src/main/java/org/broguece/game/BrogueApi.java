package org.broguece.game;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Thin client for the brogue-api backend. Telemetry writes are fire-and-forget
 *  and every failure mode is swallowed silently — the app must behave exactly
 *  as if the backend didn't exist when the backend is unreachable. Reads return
 *  null on any failure and leave UI presentation entirely up to the caller. */
final class BrogueApi {

    // Injected at build time: assembleStaging reads API_STAGING_URL from .env,
    // assembleRelease uses the hardcoded production URL. See `buildTypes` in
    // app/build.gradle.kts.
    private static final String BASE_URL = BuildConfig.API_BASE_URL;

    private static final int CONNECT_TIMEOUT_MS     = 5000;
    private static final int WRITE_READ_TIMEOUT_MS  = 5000;
    // Reads are in response to direct user actions (they see a spinner).
    // Worst case connect + read = 15s before the caller paints an error.
    private static final int READ_READ_TIMEOUT_MS   = 10000;

    // Server endpoint paths. Must stay in lockstep with src/server/Routes.ts.
    private static final String PATH_WEEKLY      = "/weekly";
    private static final String PATH_FUN         = "/fun";
    private static final String PATH_SEED_PREFIX = "/seed/";
    private static final String PATH_GAME_START  = "/game/start";
    private static final String PATH_GAME_RESUME = "/game/resume";
    private static final String PATH_GAME_END    = "/game/end";

    private final BrogueActivity activity;
    private ExecutorService executor;

    BrogueApi(BrogueActivity activity) {
        this.activity = activity;
    }

    private ExecutorService executor() {
        if (executor == null) executor = Executors.newSingleThreadExecutor();
        return executor;
    }

    // ---- Device identity ----

    String deviceId() {
        android.content.ContentResolver cr = activity.getContentResolver();
        String a = Settings.Secure.getString(cr, Settings.Secure.ANDROID_ID);
        if (a == null) return null;
        String b = Settings.Secure.getString(cr, Settings.Secure.ANDROID_ID);
        return a.equals(b) ? a : null;
    }

    /** True when the device reports an active network with INTERNET capability.
     *  Wrapped in try/catch because ConnectivityManager throws SecurityException
     *  when ACCESS_NETWORK_STATE isn't granted — we'd rather optimistically
     *  assume "online" and let the request fail naturally than crash the app. */
    boolean hasInternetCapability() {
        try {
            ConnectivityManager cm =
                (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return true;
            Network network = cm.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            return caps != null
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Throwable t) {
            return true;
        }
    }

    private String appVersionString() {
        try {
            return activity.getPackageManager()
                .getPackageInfo(activity.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "unknown";
        }
    }

    // ---- High-level telemetry calls ----

    void gameStart(long seed) {
        String did = deviceId();
        if (did == null) return;
        try {
            JSONObject body = new JSONObject();
            body.put("installId", did);
            // Seed goes as a string: Brogue seeds are int64 and JSON Number
            // loses integer precision above 2^53 when Node.js parses it.
            body.put("seed", String.valueOf(seed));
            body.put("appVersion", appVersionString());
            postFireAndForget(PATH_GAME_START, body);
        } catch (Exception ignored) { }
    }

    /** Resume of a saved run. Same shape as gameStart but the server does not
     *  bump seeds.plays — resuming is a continuation of an already-counted run. */
    void gameResume(long seed) {
        String did = deviceId();
        if (did == null) return;
        try {
            JSONObject body = new JSONObject();
            body.put("installId", did);
            body.put("seed", String.valueOf(seed));
            body.put("appVersion", appVersionString());
            postFireAndForget(PATH_GAME_RESUME, body);
        } catch (Exception ignored) { }
    }

    /** outcome: "died", "won", or "quit" — matches the server's OutcomeEn. */
    void gameEnd(long seed, String outcome, int depth, int turns) {
        String did = deviceId();
        if (did == null) return;
        try {
            JSONObject body = new JSONObject();
            body.put("installId", did);
            body.put("seed", String.valueOf(seed));
            body.put("outcome", outcome);
            body.put("depth", depth);
            body.put("turns", turns);
            body.put("appVersion", appVersionString());
            postFireAndForget(PATH_GAME_END, body);
        } catch (Exception ignored) { }
    }

/** Current weekly contest seed as {@code {seed: "<digits>"}}. The seed
     *  number is all the client needs — WeeklySeedModal then hits /seed/:seed
     *  for description + stats via {@link #fetchSeed}. */
    void fetchWeekly(Consumer<JSONObject> onResult) {
        getJson(PATH_WEEKLY, JSONObject::new, onResult);
    }

    /** Curator-approved fun-seed catalog, newest first. Each row is
     *  {@code {seed, description}}; per-seed stats arrive via
     *  {@link #fetchSeed} when a row is tapped. */
    void fetchFun(Consumer<JSONArray> onResult) {
        getJson(PATH_FUN, JSONArray::new, onResult);
    }

    /** Per-seed detail for the seed-details modal family. Returns
     *  {description, plays, deaths, wins}; unknown seeds come back with zeros
     *  and null description. Callback runs on the UI thread; null on failure. */
    void fetchSeed(long seed, Consumer<JSONObject> onResult) {
        getJson(PATH_SEED_PREFIX + seed, JSONObject::new, onResult);
    }

    // ---- HTTP primitives ----

    private void postFireAndForget(final String path, final JSONObject body) {
        executor().execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(BASE_URL + path);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(WRITE_READ_TIMEOUT_MS);
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
                conn.setFixedLengthStreamingMode(payload.length);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload);
                }
                conn.getResponseCode();
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private interface JsonParser<T> {
        T parse(String body) throws Exception;
    }

    private <T> void getJson(final String path,
                             final JsonParser<T> parser,
                             final Consumer<T> callback) {
        executor().execute(() -> {
            T parsed = null;
            HttpURLConnection conn = null;
            try {
                URL url = new URL(BASE_URL + path);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(READ_READ_TIMEOUT_MS);
                conn.setRequestMethod("GET");
                int code = conn.getResponseCode();
                if (code >= 200 && code < 300) {
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = br.readLine()) != null) sb.append(line);
                    }
                    parsed = parser.parse(sb.toString());
                }
            } catch (Exception ignored) {
                // Leave parsed == null; callback receives null on any failure.
            } finally {
                if (conn != null) conn.disconnect();
            }
            final T result = parsed;
            activity.runOnUiThread(() -> callback.accept(result));
        });
    }
}
