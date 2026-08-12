package org.broguece.game;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;
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

    /** Matches the sidebar's own slide in the renderer. */
    private static final int HANDEDNESS_SLIDE_MS = 210;

    private static final String PREF_SIDEBAR_ON_RIGHT = "sidebar_on_right";

    // Overlay roots — allocated in onCreate, shared with feature classes.
    FrameLayout gameOverlay;
    private View bottomGroup;
    FrameLayout inventoryOverlay;
    private View loadingOverlay;
    private View transitionVeil;

    // Feature classes. Package-private so other features can reference them
    // directly (e.g. StartMenu → extrasModal.show()).
    final BrogueApi api = new BrogueApi(this);
    final ModalStack modalStack = new ModalStack(this);
    final AboutModal aboutModal = new AboutModal(this);
    final ExtrasModal extrasModal = new ExtrasModal(this);
    final PlayerStatsModal playerStatsModal = new PlayerStatsModal(this);
    final StartMenu startMenu = new StartMenu(this);
    // Seed-details modal family — all inherit from SeedDetailsModal and
    // share one visual frame; subclasses differ only in title, header
    // label source, and a few hooks.
    final FunSeedModal funSeedModal = new FunSeedModal(this);
    final WeeklySeedModal weeklySeedModal = new WeeklySeedModal(this);
    final NewGameSeedModal newGameSeedModal = new NewGameSeedModal(this);
    final ReplayRecentSeedModal replayRecentSeedModal = new ReplayRecentSeedModal(this);
    final DeathModal deathModal = new DeathModal(this);
    final VictoryModal victoryModal = new VictoryModal(this);
    private SettingsPanel settingsPanel;
    private ExitPanel exitPanel;
    private ActionsToolbar actionsToolbar;
    private DPadOverlay dpadOverlay;
    private InventoryOverlay inventoryRenderer;
    private DiscoveriesOverlay discoveriesRenderer;
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
        discoveriesRenderer = new DiscoveriesOverlay(this, inventoryOverlay);
        textInputDialog = new TextInputDialog(this);

        actionsToolbar = new ActionsToolbar(this, gameOverlay, inventoryOverlay,
            settingsPanel::show, exitPanel::show, this::setSubmenuOpen);
        bottomGroup = actionsToolbar.build();

        // Added before the toolbar: the hamburger submenu expands into the
        // pad's default slot, and must draw and take touches above it.
        dpadOverlay = new DPadOverlay(this, gameOverlay, actionsToolbar::collapseSubmenu);
        int dpadSize = dpadOverlay.sizePx();
        FrameLayout.LayoutParams dpadParams = new FrameLayout.LayoutParams(
            dpadSize, dpadSize, Gravity.BOTTOM | Gravity.END);
        dpadParams.setMargins(0, 0, dpToPx(DPadOverlay.RIGHT_MARGIN_DP),
            dpToPx(DPadOverlay.BOTTOM_MARGIN_DP));
        gameOverlay.addView(dpadOverlay.getView(), dpadParams);

        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM | Gravity.END);
        bottomParams.setMargins(0, 0, dpToPx(EDGE_SAFE_DP), 0);
        gameOverlay.addView(bottomGroup, bottomParams);

        applyMovementMode(GameSettings.getInt(this, DPadOverlay.PREF_MOVEMENT_MODE,
            DPadOverlay.MOVEMENT_SWIPE));
        applyHandedness(GameSettings.getBool(this, PREF_SIDEBAR_ON_RIGHT), false);

        achievementToast = new AchievementToast(this, gameOverlay);
        // Listener fires on the StatsStore handler thread; marshal to UI.
        StatsStore.get(this).setUnlockListener(
            a -> runOnUiThread(() -> achievementToast.show(a)));

    }

    private boolean dpadMovement;
    private boolean submenuOpen;
    private boolean sidebarOnRight;

    /** Called from C when the player swipes the sidebar off its own edge.
     *  Arrives on the engine thread. */
    public void onHandednessSwiped() {
        runOnUiThread(() -> applyHandedness(!sidebarOnRight, true));
    }

    /** Mirrors the whole interface. The sidebar and the toolbar/D-Pad always
     *  live on opposite edges, so one flag drives both sides — and the C
     *  renderer's camera bias with them. */
    private void applyHandedness(boolean onRight, boolean flipped) {
        sidebarOnRight = onRight;

        float toolbarStart = slideStart(bottomGroup, dpToPx(EDGE_SAFE_DP), 0);
        float dpadStart = slideStart(dpadOverlay.getView(),
            dpToPx(DPadOverlay.RIGHT_MARGIN_DP), dpadOverlay.leadingInsetPx());

        actionsToolbar.setMirrored(onRight);
        // Only on a real flip: at launch the saved placement is already in the
        // right frame, and mirroring it would negate it on every start.
        if (flipped) {
            GameSettings.setBool(this, PREF_SIDEBAR_ON_RIGHT, onRight);
            dpadOverlay.exitEditMode();
            dpadOverlay.mirrorPlacement();
        }
        layoutSideDependentViews();
        nativeSetSidebarOnRight(onRight, flipped);

        slideIntoPlace(bottomGroup, toolbarStart);
        slideIntoPlace(dpadOverlay.getView(), dpadStart);
    }

    /** The translation that leaves the view looking where it does now, once its
     *  anchor has swapped sides — i.e. where the slide starts. Must be read
     *  before the placement is mirrored, since that moves it too. */
    private float slideStart(View view, int edgePx, int leadingInset) {
        if (view.getWidth() == 0) return view.getTranslationX();
        int jump = getWindow().getDecorView().getWidth() - view.getWidth()
            - edgePx * 2 + leadingInset;
        return view.getTranslationX() + (sidebarOnRight ? jump : -jump);
    }

    private void slideIntoPlace(View view, float start) {
        float target = view.getTranslationX();
        if (start == target) return;
        view.setTranslationX(start);
        view.animate().translationX(target)
            .setDuration(HANDEDNESS_SLIDE_MS)
            .setInterpolator(new DecelerateInterpolator(1.5f))
            .setUpdateListener(a -> refreshTransparentRegion(view))
            .start();
    }

    /** The game's SurfaceView shows through wherever the window is transparent,
     *  and that region is only recomputed on layout. A view that moves without
     *  one — or faster than one — stays invisible until something else redraws. */
    private void refreshTransparentRegion(View view) {
        ViewParent parent = view.getParent();
        if (parent != null) parent.requestTransparentRegion(view);
    }

    private void layoutSideDependentViews() {
        int side = sidebarOnRight ? Gravity.START : Gravity.END;

        int barEdge = dpToPx(EDGE_SAFE_DP);
        FrameLayout.LayoutParams bar =
            (FrameLayout.LayoutParams) bottomGroup.getLayoutParams();
        bar.gravity = Gravity.BOTTOM | side;
        bar.setMargins(sidebarOnRight ? barEdge : 0, 0,
                       sidebarOnRight ? 0 : barEdge, 0);
        bottomGroup.setLayoutParams(bar);

        // The pad's view overhangs its grid on the left, so anchoring it there
        // has to discount that or the grid lands inset by it.
        int padEdge = dpToPx(DPadOverlay.RIGHT_MARGIN_DP);
        View pad = dpadOverlay.getView();
        FrameLayout.LayoutParams padParams =
            (FrameLayout.LayoutParams) pad.getLayoutParams();
        padParams.gravity = Gravity.BOTTOM | side;
        padParams.setMargins(
            sidebarOnRight ? padEdge - dpadOverlay.leadingInsetPx() : 0, 0,
            sidebarOnRight ? 0 : padEdge,
            dpToPx(DPadOverlay.BOTTOM_MARGIN_DP));
        pad.setLayoutParams(padParams);

        refreshTransparentRegion(bottomGroup);
        refreshTransparentRegion(pad);
    }

    /** Bottom popups (Settings, Exit, Actions) sit against the toolbar's edge,
     *  clearing the bar itself. */
    FrameLayout.LayoutParams toolbarSidePanelParams(int width) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            width, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM | (sidebarOnRight ? Gravity.START : Gravity.END));
        int edge = dpToPx(EDGE_SAFE_DP);
        params.setMargins(sidebarOnRight ? edge : 0, dpToPx(8),
                          sidebarOnRight ? 0 : edge, dpToPx(52));
        return params;
    }

    /** Applies the Movement setting. D-Pad mode shows the pad and stops swipes
     *  from producing direction keys, so the two can't both drive movement.
     *  The camera bias is measured from the pad's default slot — repositioning
     *  it deliberately doesn't re-bias the view. */
    void applyMovementMode(int mode) {
        dpadMovement = mode == DPadOverlay.MOVEMENT_DPAD;
        updateDpadVisibility();
        nativeSetDpadMovement(dpadMovement,
            dpToPx(DPadOverlay.SIZE_DP + DPadOverlay.RIGHT_MARGIN_DP));
    }

    /** The hamburger submenu expands into the pad's default slot, so the pad
     *  steps aside for as long as it is up. */
    private void setSubmenuOpen(boolean open) {
        submenuOpen = open;
        updateDpadVisibility();
    }

    private void updateDpadVisibility() {
        boolean visible = dpadMovement && !submenuOpen;
        // Hiding the pad mid-edit would strand gameOverlay swallowing every tap.
        if (!visible) dpadOverlay.exitEditMode();
        dpadOverlay.getView().setVisibility(visible ? View.VISIBLE : View.GONE);
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

    public void showDiscoveries(final String json) {
        discoveriesRenderer.show(json);
    }

    public void hideDiscoveries() {
        discoveriesRenderer.hide();
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

    // Set by StartMenu's Resume button immediately before the JNI hop into
    // NG_OPEN_GAME. Read-and-cleared on the next onGameStart so resumes can
    // be told apart from fresh runs without threading a flag through C.
    boolean nextGameIsResume;

    public void onGameStart(long seed) {
        currentSeed = seed;
        currentSeedValid = true;
        boolean isResume = nextGameIsResume;
        nextGameIsResume = false;
        if (isResume) {
            // Resuming continues an already-counted run — no new "play" on
            // either server (seeds.plays) or local Personal Stats.
            api.gameResume(seed);
        } else {
            StatsStore.get(this).recordGameStart();
            StatsStore.get(this).recordSeedPlayed(seed);
            api.gameStart(seed);
        }
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

    public void onPlayerDied(final String killedBy, final int depth, final int deepest,
            final int turns) {
        StatsStore.get(this).recordPlayerDied(killedBy, depth, deepest, turns);
        reportGameEnd("died", depth, turns);
    }

    public void showDeathScreen(String description, int turns) {
        deathModal.show(description, turns);
    }

    public native void nativeDeathFadeDone();
    public native void nativeDeathScreenDismissed();

    public void showVictorySequence(final String json) {
        victoryModal.showSequence(json);
    }

    public native void nativeVictorySequenceDismissed();

    public void onDeathFlamesReady() {
        deathModal.onFlamesReady();
    }

    public void onPlayerWon(final boolean superVictory, final int depth, final int deepest,
            final int turns) {
        StatsStore.get(this).recordPlayerWon(superVictory, deepest, turns);
        reportGameEnd("won", depth, turns);
    }

    public void onPlayerQuit(final int depth, final int deepest, final int turns) {
        StatsStore.get(this).recordPlayerQuit(deepest);
        reportGameEnd("quit", depth, turns);
    }

    private void reportGameEnd(String outcome, int depth, int turns) {
        if (!currentSeedValid) return;
        api.gameEnd(currentSeed, outcome, depth, turns);
        currentSeedValid = false;
    }

    public void hideGameUI() {
        final java.util.concurrent.CountDownLatch latch =
            new java.util.concurrent.CountDownLatch(1);
        runOnUiThread(() -> {
            gameOverlay.setVisibility(View.GONE);
            inventoryOverlay.setVisibility(View.GONE);
            latch.countDown();
        });
        try { latch.await(); } catch (InterruptedException ignored) {}
    }

    public void setOverlayVisible(final boolean visible) {
        android.util.Log.d("BrogueModal", "setOverlayVisible(" + visible + ")");
        runOnUiThread(() -> {
            if (visible) {
                clearTransitionVeil();
                // A game is on-screen — drop title-menu modals so they can't
                // resurface after the engine returns to the title later.
                modalStack.clear();
            } else {
                // Engine is returning to the title menu. This fires at the
                // top of titleMenu() in C, before its Phase 1 tap-to-continue
                // flame loop, so restoring here puts the modal back up
                // immediately instead of making the user tap through flames.
                modalStack.restore();
                deathModal.fadeOutOverlay();
                victoryModal.fadeOutOverlay();
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
        loadingOverlay.bringToFront(); // above any transition veil already up
    }

    /** Fades the screen out before a menu choice hands control to the engine,
     *  which otherwise cuts straight from the title to loading. Cleared by
     *  setOverlayVisible once the game is on screen. */
    void fadeToBlackThen(final Runnable action) {
        runOnUiThread(() -> {
            if (transitionVeil == null) {
                transitionVeil = new View(this);
                transitionVeil.setBackgroundColor(Color.BLACK);
                transitionVeil.setClickable(true);
                addContentView(transitionVeil, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            }
            transitionVeil.bringToFront();
            transitionVeil.setAlpha(0f);
            transitionVeil.animate().alpha(1f).setDuration(400)
                .withEndAction(action).start();
        });
    }

    private void clearTransitionVeil() {
        if (transitionVeil == null) return;
        final View veil = transitionVeil;
        transitionVeil = null;
        veil.animate().alpha(0f).setDuration(400).withEndAction(() -> {
            if (veil.getParent() != null) {
                ((android.view.ViewGroup) veil.getParent()).removeView(veil);
            }
        }).start();
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

    /** Called from C while replaying a save file; percent advances in ~1% steps. */
    public void setLoadingProgress(final int percent) {
        runOnUiThread(() -> {
            if (loadingOverlay != null && loadingOverlay.getVisibility() == View.VISIBLE) {
                ((TextView) loadingOverlay).setText("LOADING " + percent + "%");
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
    native void nativeSetDpadMovement(boolean enabled, int reservedWidthPx);
    native void nativeSetSidebarOnRight(boolean onRight, boolean animate);

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
