/*
 * android-touch.c — Gesture recognition layer for BrogueCE on Android.
 *
 * This file hooks into SDL2's event stream and translates multi-touch gestures
 * into rogueEvents that the existing game code already understands. It does NOT
 * modify any game logic — it only produces the same event types that a mouse
 * and keyboard would.
 *
 * Gesture map:
 *   Tap              → MOUSE_DOWN + MOUSE_UP  (left click at cell)
 *   Long press       → RIGHT_MOUSE_DOWN       (examine / interact)
 *   Long press+drag  → MOUSE_ENTERED_CELL     (inspect / pathfind highlight)
 *   Swipe            → KEYSTROKE arrow key     (8-directional movement)
 *   Pinch            → resize window           (zoom)
 *   Two-finger tap   → KEYSTROKE ESCAPE_KEY    (cancel / back)
 */

#include <SDL.h>
#include <jni.h>
#include <math.h>
#include <pthread.h>
#include <string.h>
#include "platform.h"
#include "Rogue.h"
#include "tiles.h"

/* ---- Tuning constants ---- */

#define SWIPE_THRESHOLD_PX     40   /* min distance to register a swipe */
#define LONG_PRESS_MS         400   /* hold time for inspect mode */
#define TAP_MAX_MOVE_PX        20   /* max drift before a tap becomes a drag */
#define TWO_FINGER_TAP_MS     200   /* max duration for a two-finger tap */

/* ---- Internal state ---- */

typedef enum {
    TOUCH_IDLE,
    TOUCH_DOWN,         /* single finger down, waiting to classify */
    TOUCH_SWIPING,      /* finger moved past threshold — swipe pending */
    TOUCH_INSPECTING,   /* long press engaged — emitting hover events */
    TOUCH_TWO_FINGER,   /* two fingers down */
} TouchState;

static TouchState state = TOUCH_IDLE;

static float startX, startY;        /* first finger down position (px) */
static Uint32 startTime;            /* timestamp of first finger down */
static SDL_FingerID primaryFinger;

static float finger2StartX, finger2StartY;
static float initialPinchDist;
static float zoomAtPinchStart;
static SDL_FingerID secondFinger;
static float lastMidX, lastMidY;       /* midpoint of two fingers last frame */
static float curFinger1X, curFinger1Y;  /* live positions for both fingers */
static float curFinger2X, curFinger2Y;

static boolean pendingMouseUp = false;
static int pendingUpX, pendingUpY;

void androidResetTouchState(void) {
    state = TOUCH_IDLE;
    pendingMouseUp = false;
}

void androidSetOverlayVisible(boolean visible) {
    JNIEnv *env = (JNIEnv *)SDL_AndroidGetJNIEnv();
    jobject activity = (jobject)SDL_AndroidGetActivity();
    jclass cls = (*env)->GetObjectClass(env, activity);
    jmethodID mid = (*env)->GetMethodID(env, cls, "setOverlayVisible", "(Z)V");
    if (mid) (*env)->CallVoidMethod(env, activity, mid, (jboolean)visible);
    (*env)->DeleteLocalRef(env, cls);
    (*env)->DeleteLocalRef(env, activity);
}

void androidSetLoadingVisible(boolean visible) {
    JNIEnv *env = (JNIEnv *)SDL_AndroidGetJNIEnv();
    jobject activity = (jobject)SDL_AndroidGetActivity();
    jclass cls = (*env)->GetObjectClass(env, activity);
    jmethodID mid = (*env)->GetMethodID(env, cls, "setLoadingVisible", "(Z)V");
    if (mid) (*env)->CallVoidMethod(env, activity, mid, (jboolean)visible);
    (*env)->DeleteLocalRef(env, cls);
    (*env)->DeleteLocalRef(env, activity);
}

static boolean androidGetSettingBool(const char *key) {
    JNIEnv *env = (JNIEnv *)SDL_AndroidGetJNIEnv();
    jobject activity = (jobject)SDL_AndroidGetActivity();
    jclass cls = (*env)->GetObjectClass(env, activity);
    jmethodID mid = (*env)->GetMethodID(env, cls, "getSettingBool",
                                        "(Ljava/lang/String;)Z");
    boolean result = false;
    if (mid) {
        jstring jkey = (*env)->NewStringUTF(env, key);
        result = (*env)->CallBooleanMethod(env, activity, mid, jkey);
        (*env)->DeleteLocalRef(env, jkey);
    }
    (*env)->DeleteLocalRef(env, cls);
    (*env)->DeleteLocalRef(env, activity);
    return result;
}

static int androidGetSettingInt(const char *key, int defaultValue) {
    JNIEnv *env = (JNIEnv *)SDL_AndroidGetJNIEnv();
    jobject activity = (jobject)SDL_AndroidGetActivity();
    jclass cls = (*env)->GetObjectClass(env, activity);
    jmethodID mid = (*env)->GetMethodID(env, cls, "getSettingInt",
                                        "(Ljava/lang/String;I)I");
    int result = defaultValue;
    if (mid) {
        jstring jkey = (*env)->NewStringUTF(env, key);
        result = (*env)->CallIntMethod(env, activity, mid, jkey, (jint)defaultValue);
        (*env)->DeleteLocalRef(env, jkey);
    }
    (*env)->DeleteLocalRef(env, cls);
    (*env)->DeleteLocalRef(env, activity);
    return result;
}

void androidApplySettings(void) {
    extern enum graphicsModes graphicsMode;
    extern enum graphicsModes setGraphicsMode(enum graphicsModes mode);

    rogue.trueColorMode = androidGetSettingBool("hide_color_effects");
    rogue.displayStealthRangeMode = androidGetSettingBool("display_stealth_range");

    int mode = androidGetSettingInt("graphics_mode", TEXT_GRAPHICS);
    if (mode < TEXT_GRAPHICS || mode > HYBRID_GRAPHICS) mode = TEXT_GRAPHICS;
    if (mode != (int)graphicsMode) {
        graphicsMode = setGraphicsMode(mode);
    }
}

void androidShowInventory(const char *json) {
    JNIEnv *env = (JNIEnv *)SDL_AndroidGetJNIEnv();
    jobject activity = (jobject)SDL_AndroidGetActivity();
    jclass cls = (*env)->GetObjectClass(env, activity);
    jstring jstr = (*env)->NewStringUTF(env, json);
    jmethodID mid = (*env)->GetMethodID(env, cls, "showInventory", "(Ljava/lang/String;)V");
    if (mid) (*env)->CallVoidMethod(env, activity, mid, jstr);
    (*env)->DeleteLocalRef(env, jstr);
    (*env)->DeleteLocalRef(env, cls);
    (*env)->DeleteLocalRef(env, activity);
}

void androidHideInventory(void) {
    JNIEnv *env = (JNIEnv *)SDL_AndroidGetJNIEnv();
    jobject activity = (jobject)SDL_AndroidGetActivity();
    jclass cls = (*env)->GetObjectClass(env, activity);
    jmethodID mid = (*env)->GetMethodID(env, cls, "hideInventory", "()V");
    if (mid) (*env)->CallVoidMethod(env, activity, mid);
    (*env)->DeleteLocalRef(env, cls);
    (*env)->DeleteLocalRef(env, activity);
}

/* ---- Native text input dialog ---- */

static pthread_mutex_t textInputMutex = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t  textInputCond  = PTHREAD_COND_INITIALIZER;
static boolean textInputReady = false;
static boolean textInputConfirmed = false;
static char    textInputResult[256];

boolean androidGetTextInput(const char *prompt, const char *defaultText,
                            int maxLen, char *outBuf) {
    outBuf[0] = '\0';

    pthread_mutex_lock(&textInputMutex);
    textInputReady = false;
    textInputConfirmed = false;
    textInputResult[0] = '\0';
    pthread_mutex_unlock(&textInputMutex);

    JNIEnv *env = (JNIEnv *)SDL_AndroidGetJNIEnv();
    jobject activity = (jobject)SDL_AndroidGetActivity();
    jclass cls = (*env)->GetObjectClass(env, activity);
    jstring jPrompt = (*env)->NewStringUTF(env, prompt);
    jstring jDefault = (*env)->NewStringUTF(env, defaultText);
    jmethodID mid = (*env)->GetMethodID(env, cls, "showTextInputDialog",
        "(Ljava/lang/String;Ljava/lang/String;I)V");
    if (mid) (*env)->CallVoidMethod(env, activity, mid, jPrompt, jDefault, (jint)maxLen);
    (*env)->DeleteLocalRef(env, jPrompt);
    (*env)->DeleteLocalRef(env, jDefault);
    (*env)->DeleteLocalRef(env, cls);
    (*env)->DeleteLocalRef(env, activity);

    /* Block until the Java dialog signals a result. */
    pthread_mutex_lock(&textInputMutex);
    while (!textInputReady) {
        pthread_cond_wait(&textInputCond, &textInputMutex);
    }
    boolean confirmed = textInputConfirmed;
    if (confirmed) {
        int len = (int)strlen(textInputResult);
        if (len >= maxLen) len = maxLen - 1;
        memcpy(outBuf, textInputResult, len);
        outBuf[len] = '\0';
    }
    pthread_mutex_unlock(&textInputMutex);

    return confirmed;
}

/* Called from Java when the text input dialog is dismissed. */
JNIEXPORT void JNICALL
Java_org_broguece_game_BrogueActivity_nativeTextInputResult(
        JNIEnv *env, jobject thiz, jboolean confirmed, jstring text) {
    pthread_mutex_lock(&textInputMutex);
    textInputConfirmed = (boolean)confirmed;
    if (confirmed && text) {
        const char *utf = (*env)->GetStringUTFChars(env, text, NULL);
        strncpy(textInputResult, utf, sizeof(textInputResult) - 1);
        textInputResult[sizeof(textInputResult) - 1] = '\0';
        (*env)->ReleaseStringUTFChars(env, text, utf);
    } else {
        textInputResult[0] = '\0';
    }
    textInputReady = true;
    pthread_cond_signal(&textInputCond);
    pthread_mutex_unlock(&textInputMutex);
}

float androidZoomLevel = 2.0f;
float androidPanX = 0.0f, androidPanY = 0.0f;
boolean androidCameraSnap = false;
boolean androidPanOverride = false;

/* ---- Helpers ---- */

static float dist(float x1, float y1, float x2, float y2) {
    float dx = x2 - x1, dy = y2 - y1;
    return sqrtf(dx * dx + dy * dy);
}

static void cellFromPixel(float px, float py, int *cx, int *cy) {
    extern int windowWidth, windowHeight;

    int fitW = windowHeight * 16 / 10;
    int fitH = windowHeight;
    if (fitW > windowWidth) { fitW = windowWidth; fitH = windowWidth * 10 / 16; }

    float zoom;
    int ofsX, ofsY, w, h;

    if (getRenderMode() == RENDER_MODAL || !rogue.gameInProgress) {
        // UI layer: 1x, centered
        zoom = 1.0f;
        w = fitW;
        h = fitH;
        ofsX = (windowWidth - w) / 2;
        ofsY = (windowHeight - h) / 2;
    } else {
        // Dungeon layer: zoomed + panned
        zoom = androidZoomLevel;
        w = (int)(fitW * zoom);
        h = (int)(fitH * zoom);
        ofsX = (windowWidth - w) / 2 + (int)androidPanX;
        ofsY = (windowHeight - h) / 2 + (int)androidPanY;
        if (ofsX > 0) ofsX = 0;
        if (ofsY > 0) ofsY = 0;
        if (ofsX + w < windowWidth) ofsX = windowWidth - w;
        if (ofsY + h < windowHeight) ofsY = windowHeight - h;
    }

    *cx = (int)((px - ofsX) * COLS / w);
    *cy = (int)((py - ofsY) * ROWS / h);
    if (*cx < 0) *cx = 0;
    if (*cx >= COLS) *cx = COLS - 1;
    if (*cy < 0) *cy = 0;
    if (*cy >= ROWS) *cy = ROWS - 1;
}

/*
 * Classify a swipe into one of 8 directions based on angle.
 * Returns the corresponding key constant.
 */
static signed long swipeDirectionKey(float dx, float dy) {
    /* atan2 gives angle in radians; convert to 0..7 octant */
    float angle = atan2f(-dy, dx); /* negative dy because screen Y is inverted */
    if (angle < 0) angle += 2.0f * (float)M_PI;

    /* Each octant is PI/4 wide, offset by PI/8 so 0 = right */
    int octant = (int)((angle + (float)M_PI / 8.0f) / ((float)M_PI / 4.0f)) % 8;

    switch (octant) {
        case 0: return RIGHT_ARROW;  /* right */
        case 1: return UPRIGHT_KEY;  /* up-right */
        case 2: return UP_ARROW;     /* up */
        case 3: return UPLEFT_KEY;   /* up-left */
        case 4: return LEFT_ARROW;   /* left */
        case 5: return DOWNLEFT_KEY; /* down-left */
        case 6: return DOWN_ARROW;   /* down */
        case 7: return DOWNRIGHT_KEY;/* down-right */
        default: return UP_ARROW;
    }
}

/* ---- Public API ---- */

boolean androidTouchEvent(SDL_Event *event, rogueEvent *out) {
    extern int windowWidth, windowHeight;

    /* Deliver queued MOUSE_UP from a previous tap */
    if (pendingMouseUp) {
        pendingMouseUp = false;
        out->eventType = MOUSE_UP;
        out->param1 = pendingUpX;
        out->param2 = pendingUpY;
        out->shiftKey = false;
        out->controlKey = false;
        return true;
    }

    /* Called with NULL event just to check pending queue */
    if (!event) return false;

    /* Convert normalized touch coords to pixels */
    float px, py;

    switch (event->type) {

    case SDL_FINGERDOWN:
        px = event->tfinger.x * windowWidth;
        py = event->tfinger.y * windowHeight;

        if (state == TOUCH_IDLE || state == TOUCH_DOWN) {
            if (state == TOUCH_IDLE) {
                state = TOUCH_DOWN;
                primaryFinger = event->tfinger.fingerId;
                startX = px;
                startY = py;
                startTime = event->tfinger.timestamp;
            } else {
                /* Second finger while first is still down */
                state = TOUCH_TWO_FINGER;
                androidPanOverride = true;
                secondFinger = event->tfinger.fingerId;
                finger2StartX = px;
                finger2StartY = py;
                curFinger1X = startX; curFinger1Y = startY;
                curFinger2X = px;     curFinger2Y = py;
                lastMidX = (startX + px) / 2.0f;
                lastMidY = (startY + py) / 2.0f;
                initialPinchDist = dist(startX, startY, px, py);
                zoomAtPinchStart = androidZoomLevel;
            }
        }
        return false;

    case SDL_FINGERMOTION:
        px = event->tfinger.x * windowWidth;
        py = event->tfinger.y * windowHeight;

        if (state == TOUCH_DOWN && event->tfinger.fingerId == primaryFinger) {
            Uint32 elapsed = event->tfinger.timestamp - startTime;
            float moved = dist(startX, startY, px, py);

            if (elapsed >= LONG_PRESS_MS) {
                /* Held long enough — enter inspect mode */
                state = TOUCH_INSPECTING;
                int cx, cy;
                cellFromPixel(px, py, &cx, &cy);
                out->eventType = MOUSE_ENTERED_CELL;
                out->param1 = cx;
                out->param2 = cy;
                out->shiftKey = false;
                out->controlKey = false;
                return true;
            } else if (moved > TAP_MAX_MOVE_PX) {
                /* Moved too far for a tap — it's a swipe */
                state = TOUCH_SWIPING;
            }
        } else if (state == TOUCH_INSPECTING && event->tfinger.fingerId == primaryFinger) {
            /* Drag during inspect — emit hover events */
            int cx, cy;
            cellFromPixel(px, py, &cx, &cy);
            out->eventType = MOUSE_ENTERED_CELL;
            out->param1 = cx;
            out->param2 = cy;
            out->shiftKey = false;
            out->controlKey = false;
            return true;
        }
        if (state == TOUCH_TWO_FINGER) {
            /* Update tracked finger positions */
            if (event->tfinger.fingerId == primaryFinger) {
                curFinger1X = px; curFinger1Y = py;
            } else if (event->tfinger.fingerId == secondFinger) {
                curFinger2X = px; curFinger2Y = py;
            }

            /* Two-finger drag → pan */
            float midX = (curFinger1X + curFinger2X) / 2.0f;
            float midY = (curFinger1Y + curFinger2Y) / 2.0f;
            androidPanX += midX - lastMidX;
            androidPanY += midY - lastMidY;
            lastMidX = midX;
            lastMidY = midY;
        }
        /* TOUCH_SWIPING: consume motion silently */
        return false;

    case SDL_FINGERUP:
        px = event->tfinger.x * windowWidth;
        py = event->tfinger.y * windowHeight;

        if (state == TOUCH_DOWN && event->tfinger.fingerId == primaryFinger) {
            Uint32 elapsed = event->tfinger.timestamp - startTime;
            float moved = dist(startX, startY, px, py);

            if (moved <= TAP_MAX_MOVE_PX) {
                if (elapsed >= LONG_PRESS_MS) {
                    /* Long press → right click (examine) */
                    int cx, cy;
                    cellFromPixel(startX, startY, &cx, &cy);
                    out->eventType = RIGHT_MOUSE_DOWN;
                    out->param1 = cx;
                    out->param2 = cy;
                    out->shiftKey = false;
                    out->controlKey = false;
                    state = TOUCH_IDLE;
                    return true;
                } else {
                    /* Short tap → left click (down now, up queued) */
                    int cx, cy;
                    cellFromPixel(startX, startY, &cx, &cy);
                    out->eventType = MOUSE_DOWN;
                    out->param1 = cx;
                    out->param2 = cy;
                    out->shiftKey = false;
                    out->controlKey = false;
                    pendingMouseUp = true;
                    pendingUpX = cx;
                    pendingUpY = cy;
                    state = TOUCH_IDLE;
                    return true;
                }
            }
            state = TOUCH_IDLE;
            return false;
        }

        if (state == TOUCH_SWIPING && event->tfinger.fingerId == primaryFinger) {
            /* Swipe → arrow key (8-directional) */
            float dx = px - startX;
            float dy = py - startY;

            if (dist(startX, startY, px, py) >= SWIPE_THRESHOLD_PX) {
                out->eventType = KEYSTROKE;
                out->param1 = swipeDirectionKey(dx, dy);
                out->param2 = 0;
                out->shiftKey = false;
                out->controlKey = false;
                state = TOUCH_IDLE;
                return true;
            }
            state = TOUCH_IDLE;
            return false;
        }

        if (state == TOUCH_INSPECTING && event->tfinger.fingerId == primaryFinger) {
            /* Lift after inspecting — click where we lifted */
            int cx, cy;
            cellFromPixel(px, py, &cx, &cy);
            out->eventType = MOUSE_DOWN;
            out->param1 = cx;
            out->param2 = cy;
            out->shiftKey = false;
            out->controlKey = false;
            pendingMouseUp = true;
            pendingUpX = cx;
            pendingUpY = cy;
            state = TOUCH_IDLE;
            return true;
        }

        if (state == TOUCH_TWO_FINGER) {
            androidPanOverride = false;
            Uint32 elapsed = event->tfinger.timestamp - startTime;
            float pinchDelta = fabsf(androidZoomLevel - zoomAtPinchStart);

            /* Short two-finger tap (no significant zoom) → escape */
            if (elapsed <= TWO_FINGER_TAP_MS && pinchDelta < 0.05f) {
                out->eventType = KEYSTROKE;
                out->param1 = ESCAPE_KEY;
                out->param2 = 0;
                out->shiftKey = false;
                out->controlKey = false;
                state = TOUCH_IDLE;
                return true;
            }

            state = TOUCH_IDLE;
            return false;
        }

        state = TOUCH_IDLE;
        return false;
    }

    return false;
}
