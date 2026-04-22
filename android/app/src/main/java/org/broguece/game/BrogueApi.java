package org.broguece.game;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Thin client for the brogue-api backend. Telemetry writes are fire-and-forget
 *  and every failure mode is swallowed silently — the app must behave exactly
 *  as if the backend didn't exist when the backend is unreachable. Reads return
 *  null on any failure and leave UI presentation entirely up to the caller. */
final class BrogueApi {

    // Injected at build time from .env.staging (assembleStaging) or
    // .env.production (assembleRelease). See BuildConfig.API_BASE_URL and
    // the `buildTypes` block in app/build.gradle.kts.
    private static final String BASE_URL = BuildConfig.API_BASE_URL;

    // String literal kept as-is (not "brogue_install") so existing installs
    // don't lose their install_uuid on update — SharedPreferences are
    // keyed by this file name.
    private static final String PREFS_INSTALL    = "brogue_telemetry";
    private static final String KEY_INSTALL_UUID = "install_uuid";

    private static final int CONNECT_TIMEOUT_MS     = 5000;
    private static final int WRITE_READ_TIMEOUT_MS  = 5000;
    // Reads are in response to direct user actions (they see a spinner), so we
    // give the server more headroom before giving up.
    private static final int READ_READ_TIMEOUT_MS   = 15000;

    private final BrogueActivity activity;
    private ExecutorService executor;

    BrogueApi(BrogueActivity activity) {
        this.activity = activity;
    }

    private ExecutorService executor() {
        if (executor == null) executor = Executors.newSingleThreadExecutor();
        return executor;
    }

    // ---- Install identity ----

    String installId() {
        SharedPreferences prefs = prefs();
        String id = prefs.getString(KEY_INSTALL_UUID, null);
        if (id == null) {
            id = UUID.randomUUID().toString();
            prefs.edit().putString(KEY_INSTALL_UUID, id).apply();
        }
        return id;
    }

    private SharedPreferences prefs() {
        return activity.getSharedPreferences(PREFS_INSTALL, Context.MODE_PRIVATE);
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
        try {
            JSONObject body = new JSONObject();
            body.put("installId", installId());
            // Seed goes as a string: Brogue seeds are int64 and JSON Number
            // loses integer precision above 2^53 when Node.js parses it.
            body.put("seed", String.valueOf(seed));
            body.put("appVersion", appVersionString());
            postFireAndForget("/game/start", body);
        } catch (Exception ignored) { }
    }

/** Fetches the combined seeds payload. Callback runs on the UI thread with
     *  null for any failure. */
    void fetchSeeds(Consumer<JSONObject> onResult) {
        getJson("/seeds", JSONObject::new, onResult);
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
                // Swallow: graceful-failure contract.
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
