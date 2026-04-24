#ifndef ANDROID_STATS_H
#define ANDROID_STATS_H

#include "Rogue.h"

/*
 * Fire-and-forget game-lifecycle stat events.
 *
 * Each function calls into Java via CallVoidMethod and returns immediately;
 * the Java side posts the event onto a background HandlerThread so the game
 * loop isn't perturbed. Call sites must guard on !rogue.playbackMode — brogue
 * replays saves and recordings through the same code paths, and we don't want
 * a load-from-save to re-count every kill in the player's history.
 */

void androidNotifyGameStart(unsigned long long seed);
void androidNotifyMonsterKilled(const char *monsterName);
void androidNotifyAllyFreed(const char *monsterName);
void androidNotifyAllyDied(const char *monsterName);
void androidNotifyPlayerDied(const char *killedBy, int depth, int turns);
void androidNotifyPlayerWon(boolean superVictory, int depth, int turns);
void androidNotifyPlayerQuit(int depth, int turns);
void androidNotifyAmuletPickedUp(void);

#endif
