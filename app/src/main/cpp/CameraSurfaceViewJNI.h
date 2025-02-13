#pragma once

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

#define JNI_METHOD(rettype, name)                                             \
  rettype JNIEXPORT JNICALL Java_com_ml_virtualbackground_camera_preview_CameraSurfaceView_##name

JNI_METHOD(jlong, create)(JNIEnv *env, jobject obj);

JNI_METHOD(void, nativeOnSurfaceCreated)(JNIEnv *env, jobject obj, jlong _surfaceView);

JNI_METHOD(void, nativeOnSurfaceChanged)(JNIEnv *env, jobject obj, jlong _surfaceView, jint width,
                                         jint height);

JNI_METHOD(void, nativeOnDrawFrame)(JNIEnv *env, jobject obj, jlong _surfaceView);

JNI_METHOD(void, nativeDrawTexture)(JNIEnv *env, jobject obj, jlong _surfaceView, jint texture,
                                    jint textureWidth, jint textureHeight);

JNI_METHOD(void, nativeRelease)(JNIEnv *env, jobject obj, jlong _surfaceView);

#ifdef __cplusplus
}
#endif
