/*
 * android-stats.c — JNI dispatchers for game-lifecycle stat events.
 *
 * Bridges monster-killed / player-died / player-won / player-quit from the
 * brogue engine to BrogueActivity's corresponding Java handlers. All calls
 * are fire-and-forget: the Java handlers immediately post onto a background
 * HandlerThread and return, so the game loop's timing is unaffected.
 *
 * Call sites in the engine (Combat.c killCreature, RogueMain.c gameOver /
 * victory) guard these with !rogue.playbackMode so that save-load and
 * recording playback don't re-dispatch historical events.
 */

#include <SDL.h>
#include <jni.h>
#include "android-stats.h"

void androidNotifyGameStart(void) {
    JNIEnv *env = (JNIEnv *)SDL_AndroidGetJNIEnv();
    jobject activity = (jobject)SDL_AndroidGetActivity();
    jclass cls = (*env)->GetObjectClass(env, activity);
    jmethodID mid = (*env)->GetMethodID(env, cls, "onGameStart", "()V");
    if (mid) (*env)->CallVoidMethod(env, activity, mid);
    (*env)->DeleteLocalRef(env, cls);
    (*env)->DeleteLocalRef(env, activity);
}

void androidNotifyMonsterKilled(const char *monsterName) {
    JNIEnv *env = (JNIEnv *)SDL_AndroidGetJNIEnv();
    jobject activity = (jobject)SDL_AndroidGetActivity();
    jclass cls = (*env)->GetObjectClass(env, activity);
    jmethodID mid = (*env)->GetMethodID(env, cls, "onMonsterKilled",
                                        "(Ljava/lang/String;)V");
    if (mid) {
        jstring jname = (*env)->NewStringUTF(env, monsterName ? monsterName : "");
        (*env)->CallVoidMethod(env, activity, mid, jname);
        (*env)->DeleteLocalRef(env, jname);
    }
    (*env)->DeleteLocalRef(env, cls);
    (*env)->DeleteLocalRef(env, activity);
}

void androidNotifyAllyFreed(const char *monsterName) {
    JNIEnv *env = (JNIEnv *)SDL_AndroidGetJNIEnv();
    jobject activity = (jobject)SDL_AndroidGetActivity();
    jclass cls = (*env)->GetObjectClass(env, activity);
    jmethodID mid = (*env)->GetMethodID(env, cls, "onAllyFreed",
                                        "(Ljava/lang/String;)V");
    if (mid) {
        jstring jname = (*env)->NewStringUTF(env, monsterName ? monsterName : "");
        (*env)->CallVoidMethod(env, activity, mid, jname);
        (*env)->DeleteLocalRef(env, jname);
    }
    (*env)->DeleteLocalRef(env, cls);
    (*env)->DeleteLocalRef(env, activity);
}

void androidNotifyPlayerDied(const char *killedBy, int depth, int turns) {
    JNIEnv *env = (JNIEnv *)SDL_AndroidGetJNIEnv();
    jobject activity = (jobject)SDL_AndroidGetActivity();
    jclass cls = (*env)->GetObjectClass(env, activity);
    jmethodID mid = (*env)->GetMethodID(env, cls, "onPlayerDied",
                                        "(Ljava/lang/String;II)V");
    if (mid) {
        jstring jcause = (*env)->NewStringUTF(env, killedBy ? killedBy : "");
        (*env)->CallVoidMethod(env, activity, mid, jcause, (jint)depth, (jint)turns);
        (*env)->DeleteLocalRef(env, jcause);
    }
    (*env)->DeleteLocalRef(env, cls);
    (*env)->DeleteLocalRef(env, activity);
}

void androidNotifyPlayerWon(boolean superVictory, int depth, int turns) {
    JNIEnv *env = (JNIEnv *)SDL_AndroidGetJNIEnv();
    jobject activity = (jobject)SDL_AndroidGetActivity();
    jclass cls = (*env)->GetObjectClass(env, activity);
    jmethodID mid = (*env)->GetMethodID(env, cls, "onPlayerWon", "(ZII)V");
    if (mid) (*env)->CallVoidMethod(env, activity, mid,
                                    (jboolean)superVictory, (jint)depth, (jint)turns);
    (*env)->DeleteLocalRef(env, cls);
    (*env)->DeleteLocalRef(env, activity);
}

void androidNotifyPlayerQuit(void) {
    JNIEnv *env = (JNIEnv *)SDL_AndroidGetJNIEnv();
    jobject activity = (jobject)SDL_AndroidGetActivity();
    jclass cls = (*env)->GetObjectClass(env, activity);
    jmethodID mid = (*env)->GetMethodID(env, cls, "onPlayerQuit", "()V");
    if (mid) (*env)->CallVoidMethod(env, activity, mid);
    (*env)->DeleteLocalRef(env, cls);
    (*env)->DeleteLocalRef(env, activity);
}
