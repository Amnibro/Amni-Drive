#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
extern int amni_he_stub_main(int argc, char **argv);
__attribute__((constructor)) static void amni_drive_he_ctor(void) {
    char *av[2] = { "amni_drive_he", NULL };
    amni_he_stub_main(1, av);
}
extern int32_t he_drive_gate(int32_t drive_state, int32_t gate_on, int32_t passenger, int32_t category);
extern double he_drive_haversine(double lat1, double lon1, double lat2, double lon2);
extern float he_drive_bearing(double lat1, double lon1, double lat2, double lon2);
extern uint32_t he_drive_palette_color(int32_t preset_idx, int32_t slot, uint32_t custom_acc, int32_t high_contrast);
extern float he_drive_type_metric(float screen_w, float screen_h, float user_scale, int32_t slot);
extern int64_t he_drive_cockpit_valid(float screen_w, float screen_h, float user_scale, int32_t layout_id, int32_t dock_mode, int32_t swap);
extern int32_t he_drive_scrub_buf(uint64_t in_ptr, int32_t in_len, uint64_t out_ptr, int32_t out_cap);
extern int32_t he_drive_load_font(uint64_t font_ptr, int32_t font_len);
extern int32_t he_drive_render_rgba(uint64_t out_ptr, int32_t w, int32_t h, int32_t preset_idx, int32_t layout_id, int32_t dock_mode, int32_t speed_mph, int32_t temp_f, int32_t active_btn, float roll_deg);
extern int32_t he_drive_drum_step(float angle_deg, float vel_deg, float dt, int32_t dragging, uint64_t out_ptr);
JNIEXPORT jint JNICALL Java_com_amni_drive_helium_HeliumCore_nativeGate(JNIEnv *env, jclass cls, jint ds, jint on, jint pass, jint cat) {
    (void)env; (void)cls;
    return he_drive_gate(ds, on, pass, cat);
}
JNIEXPORT jdouble JNICALL Java_com_amni_drive_helium_HeliumCore_nativeHaversine(JNIEnv *env, jclass cls, jdouble lat1, jdouble lon1, jdouble lat2, jdouble lon2) {
    (void)env; (void)cls;
    return he_drive_haversine(lat1, lon1, lat2, lon2);
}
JNIEXPORT jfloat JNICALL Java_com_amni_drive_helium_HeliumCore_nativeBearing(JNIEnv *env, jclass cls, jdouble lat1, jdouble lon1, jdouble lat2, jdouble lon2) {
    (void)env; (void)cls;
    return he_drive_bearing(lat1, lon1, lat2, lon2);
}
JNIEXPORT jint JNICALL Java_com_amni_drive_helium_HeliumCore_nativePaletteColor(JNIEnv *env, jclass cls, jint idx, jint slot, jint custom_acc, jint hc) {
    (void)env; (void)cls;
    return (jint)he_drive_palette_color(idx, slot, (uint32_t)custom_acc, hc);
}
JNIEXPORT jfloat JNICALL Java_com_amni_drive_helium_HeliumCore_nativeTypeMetric(JNIEnv *env, jclass cls, jfloat w, jfloat h, jfloat s, jint slot) {
    (void)env; (void)cls;
    return he_drive_type_metric(w, h, s, slot);
}
JNIEXPORT jint JNICALL Java_com_amni_drive_helium_HeliumCore_nativeCockpitValid(JNIEnv *env, jclass cls, jfloat w, jfloat h, jfloat s, jint lid, jint dock, jint swap) {
    (void)env; (void)cls;
    return (jint)he_drive_cockpit_valid(w, h, s, lid, dock, swap);
}
JNIEXPORT jint JNICALL Java_com_amni_drive_helium_HeliumCore_nativeLoadFont(JNIEnv *env, jclass cls, jbyteArray fontBytes) {
    (void)cls;
    if (!fontBytes) return 0;
    jsize n = (*env)->GetArrayLength(env, fontBytes);
    jbyte *ptr = (*env)->GetByteArrayElements(env, fontBytes, NULL);
    if (!ptr) return 0;
    int32_t ok = he_drive_load_font((uint64_t)(uintptr_t)ptr, (int32_t)n);
    (*env)->ReleaseByteArrayElements(env, fontBytes, ptr, JNI_ABORT);
    return ok;
}
JNIEXPORT jbyteArray JNICALL Java_com_amni_drive_helium_HeliumCore_nativeScrub(JNIEnv *env, jclass cls, jbyteArray input) {
    (void)cls;
    if (!input) return NULL;
    jsize n = (*env)->GetArrayLength(env, input);
    jbyte *src = (*env)->GetByteArrayElements(env, input, NULL);
    if (!src) return NULL;
    int32_t cap = n + 256;
    uint8_t *dst = (uint8_t *)malloc((size_t)cap);
    if (!dst) {
        (*env)->ReleaseByteArrayElements(env, input, src, JNI_ABORT);
        return NULL;
    }
    int32_t out_n = he_drive_scrub_buf((uint64_t)(uintptr_t)src, (int32_t)n, (uint64_t)(uintptr_t)dst, cap);
    (*env)->ReleaseByteArrayElements(env, input, src, JNI_ABORT);
    if (out_n < 0) out_n = 0;
    if (out_n > cap) out_n = cap;
    jbyteArray res = (*env)->NewByteArray(env, out_n);
    if (res && out_n > 0) (*env)->SetByteArrayRegion(env, res, 0, out_n, (const jbyte *)dst);
    free(dst);
    return res;
}
JNIEXPORT jint JNICALL Java_com_amni_drive_helium_HeliumCore_nativeRenderRgba(JNIEnv *env, jclass cls, jobject directBuf, jint w, jint h, jint presetIdx, jint layoutId, jint dockMode, jint speedMph, jint tempF, jint activeBtn, jfloat rollDeg) {
    (void)cls;
    void *ptr = (*env)->GetDirectBufferAddress(env, directBuf);
    jlong cap = (*env)->GetDirectBufferCapacity(env, directBuf);
    if (!ptr || w <= 0 || h <= 0 || cap < (jlong)w * (jlong)h * 4LL) return 0;
    return he_drive_render_rgba((uint64_t)(uintptr_t)ptr, w, h, presetIdx, layoutId, dockMode, speedMph, tempF, activeBtn, rollDeg);
}
JNIEXPORT jint JNICALL Java_com_amni_drive_helium_HeliumCore_nativeDrumStep(JNIEnv *env, jclass cls, jfloat angleDeg, jfloat velDeg, jfloat dt, jint dragging, jfloatArray outState) {
    (void)cls;
    uint8_t raw[12];
    memset(raw, 0, sizeof(raw));
    int32_t idx = he_drive_drum_step(angleDeg, velDeg, dt, dragging, (uint64_t)(uintptr_t)raw);
    if (outState && (*env)->GetArrayLength(env, outState) >= 2) {
        float vals[2];
        memcpy(&vals[0], raw, 4);
        memcpy(&vals[1], raw + 4, 4);
        (*env)->SetFloatArrayRegion(env, outState, 0, 2, vals);
    }
    return idx;
}
