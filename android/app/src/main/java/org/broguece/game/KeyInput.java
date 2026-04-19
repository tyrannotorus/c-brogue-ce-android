package org.broguece.game;

import android.app.Activity;
import android.os.SystemClock;
import android.view.KeyEvent;

/** Synthesizes key events and delivers them via the activity's dispatchKeyEvent.
 *  SDLActivity routes these into SDL's native key handler so the Brogue C code
 *  sees them exactly as if they had come from a hardware keyboard. */
final class KeyInput {

    private KeyInput() {}

    static void sendKey(Activity a, int keyCode) {
        a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
        a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
    }

    static void sendKeyWithShift(Activity a, int keyCode) {
        long now = SystemClock.uptimeMillis();
        a.dispatchKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode,
            0, KeyEvent.META_SHIFT_LEFT_ON));
        a.dispatchKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode,
            0, KeyEvent.META_SHIFT_LEFT_ON));
    }

    /** Sends a single printable character as a key event. Letters go through
     *  KEYCODE_A..Z (with shift for uppercase); symbols map to their dedicated
     *  keycodes. Unknown characters fall back to ESCAPE. */
    static void sendChar(Activity a, char c) {
        if (c >= 'a' && c <= 'z') {
            sendKey(a, KeyEvent.KEYCODE_A + (c - 'a'));
        } else if (c >= 'A' && c <= 'Z') {
            sendKeyWithShift(a, KeyEvent.KEYCODE_A + (c - 'A'));
        } else {
            switch (c) {
                case '\\': sendKey(a, KeyEvent.KEYCODE_BACKSLASH); break;
                case ']':  sendKey(a, KeyEvent.KEYCODE_RIGHT_BRACKET); break;
                case '[':  sendKey(a, KeyEvent.KEYCODE_LEFT_BRACKET); break;
                case '~':  sendKeyWithShift(a, KeyEvent.KEYCODE_GRAVE); break;
                case '`':  sendKey(a, KeyEvent.KEYCODE_GRAVE); break;
                case '-':  sendKey(a, KeyEvent.KEYCODE_MINUS); break;
                case '=':  sendKey(a, KeyEvent.KEYCODE_EQUALS); break;
                case ' ':  sendKey(a, KeyEvent.KEYCODE_SPACE); break;
                default:   sendKey(a, KeyEvent.KEYCODE_ESCAPE); break;
            }
        }
    }
}
