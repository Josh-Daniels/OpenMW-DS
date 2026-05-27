/* src/jni_engine.c – UPDATED FOR UQMKt */
#include <jni.h>
#include "uqm/globdata.h"
#include "uqm/sis.h"
#include "uqm/encount.h"
#include "uqm/units.h"
#include "uqm/setup.h"
#include "uqm/ship.h"

#ifdef __cplusplus
extern "C" {
#endif

/* ---------- Activity & Battle ---------- */
JNIEXPORT jint JNICALL
Java_org_openmw_utils_UQMKt_getActivity(JNIEnv *env, jclass clazz) {
    (void)env; (void)clazz;
    return (jint)GLOBAL(CurrentActivity);
}

JNIEXPORT jboolean JNICALL
Java_org_openmw_utils_UQMKt_isInBattle(JNIEnv *env, jclass clazz) {
    (void)env; (void)clazz;
    return (GLOBAL(CurrentActivity) & IN_BATTLE) != 0;
}

/* ---------- Ship screen position ---------- */
JNIEXPORT jint JNICALL
Java_org_openmw_utils_UQMKt_getShipScreenX(JNIEnv *env, jclass clazz) {
    return (jint)GLOBAL(ShipStamp.origin.x);
}

JNIEXPORT jint JNICALL
Java_org_openmw_utils_UQMKt_getShipScreenY(JNIEnv *env, jclass clazz) {
    return (jint)GLOBAL(ShipStamp.origin.y);
}

/* ---------- True universe position (log) ---------- */
JNIEXPORT jint JNICALL
Java_org_openmw_utils_UQMKt_getLogX(JNIEnv *env, jclass clazz) {
    return (jint)GLOBAL_SIS(log_x);
}

JNIEXPORT jint JNICALL
Java_org_openmw_utils_UQMKt_getLogY(JNIEnv *env, jclass clazz) {
    return (jint)GLOBAL_SIS(log_y);
}

/* ---------- Autopilot destination ---------- */
JNIEXPORT jint JNICALL
Java_org_openmw_utils_UQMKt_getAutopilotX(JNIEnv *env, jclass clazz) {
    return (jint)GLOBAL(autopilot.x);
}

JNIEXPORT jint JNICALL
Java_org_openmw_utils_UQMKt_getAutopilotY(JNIEnv *env, jclass clazz) {
    return (jint)GLOBAL(autopilot.y);
}

/* ---------- Inter-planetary cursor ---------- */
JNIEXPORT jint JNICALL
Java_org_openmw_utils_UQMKt_getIPLocationX(JNIEnv *env, jclass clazz) {
    return (jint)GLOBAL(ip_location.x);
}

JNIEXPORT jint JNICALL
Java_org_openmw_utils_UQMKt_getIPLocationY(JNIEnv *env, jclass clazz) {
    return (jint)GLOBAL(ip_location.y);
}

/* ---------- Resources ---------- */
JNIEXPORT jint JNICALL
Java_org_openmw_utils_UQMKt_getCrew(JNIEnv *env, jclass clazz) {
    return (jint)GLOBAL_SIS(CrewEnlisted);
}

JNIEXPORT jint JNICALL
Java_org_openmw_utils_UQMKt_getFuel(JNIEnv *env, jclass clazz) {
    return (jint)GLOBAL_SIS(FuelOnBoard);
}

JNIEXPORT jint JNICALL
Java_org_openmw_utils_UQMKt_getRU(JNIEnv *env, jclass clazz) {
    return (jint)GLOBAL_SIS(ResUnits);
}

/* ---------- Minerals ---------- */
JNIEXPORT void JNICALL
Java_org_openmw_utils_UQMKt_getMinerals(JNIEnv *env, jclass clazz, jintArray out) {
    jint tmp[8];
    for (int i = 0; i < 8; ++i)
        tmp[i] = (jint)GLOBAL_SIS(ElementAmounts[i]);
    (*env)->SetIntArrayRegion(env, out, 0, 8, tmp);
}

/* ---------- Drive Slots ---------- */
JNIEXPORT void JNICALL
Java_org_openmw_utils_UQMKt_getDriveSlots(JNIEnv *env, jclass clazz, jbyteArray out) {
    jbyte tmp[11]; // NUM_DRIVE_SLOTS
    for (int i = 0; i < 11; ++i)
        tmp[i] = (jbyte)GLOBAL_SIS(DriveSlots[i]);
    (*env)->SetByteArrayRegion(env, out, 0, 11, tmp);
}

/* ---------- Jet Slots ---------- */
JNIEXPORT void JNICALL
Java_org_openmw_utils_UQMKt_getJetSlots(JNIEnv *env, jclass clazz, jbyteArray out) {
    jbyte tmp[8]; // NUM_JET_SLOTS
    for (int i = 0; i < 8; ++i)
        tmp[i] = (jbyte)GLOBAL_SIS(JetSlots[i]);
    (*env)->SetByteArrayRegion(env, out, 0, 8, tmp);
}

/* ---------- Modules ---------- */
JNIEXPORT void JNICALL
Java_org_openmw_utils_UQMKt_getModules(JNIEnv *env, jclass clazz, jbyteArray out) {
    jbyte tmp[16];
    for (int i = 0; i < 16; ++i)
        tmp[i] = (jbyte)GLOBAL_SIS(ModuleSlots[i]);
    (*env)->SetByteArrayRegion(env, out, 0, 16, tmp);
}

/* ---------- Names ---------- */
JNIEXPORT jstring JNICALL
Java_org_openmw_utils_UQMKt_getShipName(JNIEnv *env, jclass clazz) {
    (void)clazz;
    return (*env)->NewStringUTF(env, GLOBAL_SIS(ShipName));
}

JNIEXPORT jstring JNICALL
Java_org_openmw_utils_UQMKt_getCommanderName(JNIEnv *env, jclass clazz) {
    (void)clazz;
    return (*env)->NewStringUTF(env, GLOBAL_SIS(CommanderName));
}

/* ---------- Ship movement & state ---------- */
JNIEXPORT jint JNICALL
Java_org_openmw_utils_UQMKt_getShipFacing(JNIEnv *env, jclass clazz) {
    return (jint)GLOBAL(ShipFacing);
}

JNIEXPORT jint JNICALL
Java_org_openmw_utils_UQMKt_getTravelAngle(JNIEnv *env, jclass clazz) {
    return (jint)GLOBAL(velocity.TravelAngle);
}

JNIEXPORT jfloat JNICALL
Java_org_openmw_utils_UQMKt_getSpeedX(JNIEnv *env, jclass clazz) {
    return (jfloat)GLOBAL(velocity.vector.width) / 32.0f;
}

JNIEXPORT jfloat JNICALL
Java_org_openmw_utils_UQMKt_getSpeedY(JNIEnv *env, jclass clazz) {
    return (jfloat)GLOBAL(velocity.vector.height) / 32.0f;
}

JNIEXPORT jboolean JNICALL
Java_org_openmw_utils_UQMKt_isInOrbit(JNIEnv *env, jclass clazz) {
    return (jboolean)GLOBAL(in_orbit);
}

JNIEXPORT jint JNICALL
Java_org_openmw_utils_UQMKt_getIPPlanet(JNIEnv *env, jclass clazz) {
    return (jint)GLOBAL(ip_planet);
}

JNIEXPORT jint JNICALL
Java_org_openmw_utils_UQMKt_getEncounterRace(JNIEnv *env, jclass clazz) {
    extern SIZE EncounterRace;
    return (jint)EncounterRace;
}

JNIEXPORT jint JNICALL
Java_org_openmw_utils_UQMKt_getBattleRace(JNIEnv *env, jclass clazz, jint side) {
    (void)env; (void)clazz;
    if (side < 0 || side >= 2) return -1;

    HSTARSHIP hStarShip = GetHeadLink(&race_q[side]);
    if (hStarShip == 0) return -1;

    STARSHIP *StarShipPtr = LockStarShip(&race_q[side], hStarShip);
    jint species = (jint)StarShipPtr->SpeciesID;
    UnlockStarShip(&race_q[side], hStarShip);

    return species;
}

#ifdef __cplusplus
}
#endif
