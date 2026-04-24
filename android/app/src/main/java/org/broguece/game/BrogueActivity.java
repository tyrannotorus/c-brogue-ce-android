package org.broguece.game;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.libsdl.app.SDLActivity;

/** Thin coordinator. Owns the two overlay roots, holds the feature classes,
 *  and forwards the JNI entry points the engine calls into. Everything else
 *  lives in a dedicated class under this package. */
public class BrogueActivity extends SDLActivity {

    /** Right-edge safe margin — accommodates devices (e.g. Poco M3 / MIUI)
     *  where the navigation bar area is reserved even in immersive mode. */
    static final int EDGE_SAFE_DP = 48;

    // Overlay roots — allocated in onCreate, shared with feature classes.
    FrameLayout gameOverlay;
    FrameLayout inventoryOverlay;
    private View loadingOverlay;

    // Feature classes. Package-private so other features can reference them
    // directly (e.g. StartMenu → communityModal.show()).
    final BrogueApi api = new BrogueApi(this);
    final ModalStack modalStack = new ModalStack(this);
    final AboutModal aboutModal = new AboutModal(this);
    final CommunityModal communityModal = new CommunityModal(this);
    final PlayerStatsModal playerStatsModal = new PlayerStatsModal(this);
    final StartMenu startMenu = new StartMenu(this);
    // Seed-details modal family — all inherit from SeedDetailsModal and
    // share one visual frame; subclasses differ only in title, header
    // label source, and a few hooks.
    final FunSeedModal funSeedModal = new FunSeedModal(this);
    final WeeklySeedModal weeklySeedModal = new WeeklySeedModal(this);
    final NewGameSeedModal newGameSeedModal = new NewGameSeedModal(this);
    final ReplayRecentSeedModal replayRecentSeedModal = new ReplayRecentSeedModal(this);
    private SettingsPanel settingsPanel;
    private ExitPanel exitPanel;
    private ActionsToolbar actionsToolbar;
    private InventoryOverlay inventoryRenderer;
    TextInputDialog textInputDialog;
    private AchievementToast achievementToast;

    @Override
    protected String[] getLibraries() {
        return new String[]{ "SDL2", "SDL2_image", "brogue" };
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        gameOverlay = new FrameLayout(this);
        gameOverlay.setVisibility(View.GONE);
        addContentView(gameOverlay, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));

        inventoryOverlay = new FrameLayout(this);
        inventoryOverlay.setVisibility(View.GONE);
        // Absorb touches so they don't reach the game underneath.
        inventoryOverlay.setClickable(true);
        addContentView(inventoryOverlay, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));

        settingsPanel = new SettingsPanel(this, inventoryOverlay);
        exitPanel = new ExitPanel(this, inventoryOverlay);
        inventoryRenderer = new InventoryOverlay(this, inventoryOverlay);
        textInputDialog = new TextInputDialog(this);

        actionsToolbar = new ActionsToolbar(this, gameOverlay, inventoryOverlay,
            settingsPanel::show, exitPanel::show);
        View bottomGroup = actionsToolbar.build();
        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM | Gravity.END);
        bottomParams.setMargins(0, 0, dpToPx(EDGE_SAFE_DP), 0);
        gameOverlay.addView(bottomGroup, bottomParams);

        achievementToast = new AchievementToast(this, gameOverlay);
        // Listener fires on the StatsStore handler thread; marshal to UI.
        StatsStore.get(this).setUnlockListener(
            a -> runOnUiThread(() -> achievementToast.show(a)));

    }

    @Override
    protected void onStop() {
        super.onStop();
        showStatusOverlay("RESTORING");
    }

    // ---- JNI entry points -------------------------------------------------
    // These methods are called from C. Keep them on the activity so the
    // Java_org_broguece_game_BrogueActivity_* binding names match. Each is
    // a thin forward to the feature class that actually handles the work.

    public void showStartMenu(final boolean hasSave, final boolean saveCompatible) {
        startMenu.show(hasSave, saveCompatible);
    }

    public void showInventory(final String json) {
        inventoryRenderer.show(json);
    }

    public void hideInventory() {
        inventoryRenderer.hide();
    }

    public void showTextInputDialog(final String prompt, final String defaultText,
                                     final int maxLen, final boolean numericOnly) {
        textInputDialog.show(prompt, defaultText, maxLen, numericOnly);
    }

    // Stat-event callbacks dispatched from android-stats.c. These are on the
    // engine thread — do no real work here; hand off to StatsStore's own
    // background HandlerThread so we return fast and don't perturb the game
    // loop. Call sites in C already guard !rogue.playbackMode, so save-load
    // and recording playback don't re-dispatch historical events.
    // Latched from onGameStart so end-of-run handlers know which seed to
    // report to /game/end. Cleared after reporting so a spurious end callback
    // without a prior start can't double-report a stale seed.
    private long currentSeed;
    private boolean currentSeedValid;

    public void onGameStart(long seed) {
        currentSeed = seed;
        currentSeedValid = true;
        StatsStore.get(this).recordGameStart();
        StatsStore.get(this).recordSeedPlayed(seed);
        api.gameStart(seed);
    }

    public void onMonsterKilled(final String monsterName) {
        StatsStore.get(this).recordMonsterKilled(monsterName);
    }

    public void onAllyFreed(final String monsterName) {
        StatsStore.get(this).recordAllyFreed(monsterName);
    }

    public void onAllyDied(final String monsterName) {
        StatsStore.get(this).recordAllyDied(monsterName);
    }

    public void onAmuletPickedUp() {
        StatsStore.get(this).recordAmuletPickedUp();
    }

    public void onPlayerDied(final String killedBy, final int depth, final int turns) {
        StatsStore.get(this).recordPlayerDied(killedBy, depth, turns);
        reportGameEnd("died", depth, turns);
    }

    public void onPlayerWon(final boolean superVictory, final int depth, final int turns) {
        StatsStore.get(this).recordPlayerWon(superVictory, depth, turns);
        reportGameEnd("won", depth, turns);
    }

    public void onPlayerQuit(final int depth, final int turns) {
        StatsStore.get(this).recordPlayerQuit();
        reportGameEnd("quit", depth, turns);
    }

    private void reportGameEnd(String outcome, int depth, int turns) {
        if (!currentSeedValid) return;
        api.gameEnd(currentSeed, outcome, depth, turns);
        currentSeedValid = false;
    }

    public void setOverlayVisible(final boolean visible) {
        android.util.Log.d("BrogueModal", "setOverlayVisible(" + visible + ")");
        runOnUiThread(() -> {
            if (visible) {
                // A game is on-screen — drop title-menu modals so they can't
                // resurface after the engine returns to the title later.
                modalStack.clear();
            } else {
                // Engine is returning to the title menu. This fires at the
                // top of titleMenu() in C, before its Phase 1 tap-to-continue
                // flame loop, so restoring here puts the modal back up
                // immediately instead of making the user tap through flames.
                modalStack.restore();
            }
            gameOverlay.setVisibility(visible ? View.VISIBLE : View.GONE);
        });
    }

    private void showStatusOverlay(String text) {
        if (loadingOverlay == null) {
            TextView tv = new TextView(this);
            tv.setTextColor(Palette.PALE_BLUE);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            tv.setTypeface(Typeface.MONOSPACE);
            tv.setLetterSpacing(0.15f);
            tv.setGravity(Gravity.CENTER);
            tv.setBackgroundColor(Color.BLACK);

            loadingOverlay = tv;
            addContentView(loadingOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        }
        ((TextView) loadingOverlay).setText(text);
        loadingOverlay.setVisibility(View.VISIBLE);
    }

    public void setLoadingVisible(final boolean visible) {
        runOnUiThread(() -> {
            if (visible) {
                showStatusOverlay("LOADING");
            } else if (loadingOverlay != null) {
                loadingOverlay.setVisibility(View.GONE);
            }
        });
    }

    public void setRestoringVisible(final boolean visible) {
        runOnUiThread(() -> {
            if (visible) {
                showStatusOverlay("RESTORING");
            } else if (loadingOverlay != null) {
                loadingOverlay.setVisibility(View.GONE);
            }
        });
    }

    /** Called from C to read a saved boolean setting. */
    public boolean getSettingBool(String key) {
        return GameSettings.getBool(this, key);
    }

    /** Called from C to read a saved int setting. */
    public int getSettingInt(String key, int defaultValue) {
        return GameSettings.getInt(this, key, defaultValue);
    }

    // ---- Native declarations ---------------------------------------------
    // Implemented in C (android-touch.c). Must stay on BrogueActivity so the
    // Java_org_broguece_game_BrogueActivity_* binding names match.

    native void nativeStartMenuResult(int choice);
    native void nativeStartMenuResultWithSeed(int choice, long seed);
    native void nativeStartMenuCancel();
    native void nativeTextInputResult(boolean confirmed, String text);
    native long nativeGetSeed();
    native void nativeDeleteSaveFile();

    // ---- Navigation ------------------------------------------------------

    @Override
    public void onBackPressed() {
        if (!modalStack.isEmpty()) {
            modalStack.pop();
            return;
        }
        super.onBackPressed();
    }

    // ---- Utilities -------------------------------------------------------

    int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
