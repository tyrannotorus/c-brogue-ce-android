#ifndef ANDROID_TOUCH_H
#define ANDROID_TOUCH_H

#include <SDL.h>
#include "Rogue.h"

/*
 * Process an SDL event for Android touch gestures.
 * Returns true and fills `out` if a rogueEvent was produced.
 */
boolean androidTouchEvent(SDL_Event *event, rogueEvent *out);

/*
 * Extract bundled assets from the APK to internal storage so that
 * fopen-based game code can access them as regular files.
 * `destDir` is the app's internal files path (from SDL_GetPrefPath).
 */
void androidExtractAssets(const char *destDir);

/* Clear pending touch state. Call on game state transitions to prevent
 * events from one screen leaking into the next. */
void androidResetTouchState(void);

/* Show/hide the native Android inventory UI.
 * json is a JSON array of item objects. */
void androidShowInventory(const char *json);
void androidHideInventory(void);

/* Show/hide the native read-only Discovered Items UI. json is
 * { "sections": [ { "label", "items": [ { name, identified, polarity, pct } ] } ] }. */
void androidShowDiscoveries(const char *json);
void androidHideDiscoveries(void);

/* Show the start menu overlay (New Game / Resume / Play Seed).
 * Non-blocking — the Java callback sets rogue.nextGame when the user picks. */
void androidShowStartMenu(boolean hasSave, boolean saveCompatible);

/* Show a native Android text input dialog.
 * Blocks until the user confirms or cancels.
 * On confirm, copies input into `outBuf` (up to maxLen-1 chars) and returns true.
 * On cancel, returns false (outBuf is set to empty string). */
boolean androidGetTextInput(const char *prompt, const char *defaultText,
                            int maxLen, boolean numericOnly, char *outBuf);

/* Zoom level for pinch-to-zoom. 1.0 = full grid, >1.0 = zoomed in. */
extern float androidZoomLevel;

/* Pan offset in pixels, applied when zoomed in. */
extern float androidPanX, androidPanY;

/* Set during a two-finger drag and held after release — suppresses
 * auto-center until player movement, a swipe, or a camera snap. */
extern boolean androidPanOverride;

/* True while two fingers are currently down panning. */
boolean androidTwoFingerActive(void);

/* False in D-Pad movement mode: swipes are still recognised (so they don't
 * degrade into taps) but no longer emit a direction keystroke. */
extern volatile boolean androidSwipeMovementEnabled;

/* Width the D-Pad occupies at its default position, or 0 when it is hidden. The
 * gameplay camera centres the player in the strip this leaves free, so a
 * repositioned pad keeps the default bias. */
extern volatile int androidDpadReservedWidthPx;

/* Left-handed layout: the sidebar hugs the right edge and the toolbar and D-Pad
 * move to the left. Everything that has a side — sidebar, camera bias, pan
 * clamp — mirrors on this one flag. */
extern volatile boolean androidSidebarOnRight;

/* When true, snap the camera to the player immediately instead of tweening.
 * Set on game load; consumed after the first frame. */
extern boolean androidCameraSnap;

#endif
