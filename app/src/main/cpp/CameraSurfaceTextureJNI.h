#pragma once

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

#define JNI_METHOD(rettype, name) \
  rettype JNIEXPORT JNICALL Java_com_ml_virtualbackground_camera_preview_CameraSurfaceTexture_##name

JNI_METHOD(jlong, create)(JNIEnv *env, jobject obj);

JNI_METHOD(void, nativeInit)(JNIEnv *env, jobject obj, jobject _assetManager, jlong _surfaceView,
                             jint inputTexture, jint outputTexture);

JNI_METHOD(void, nativeSetParams)(JNIEnv *env, jobject obj, jlong _surfaceView, jint width,
                                  jint height, jint backgroundTexture);

JNI_METHOD(void, nativeUpdateTexImage)(JNIEnv *env, jobject obj, jlong _surfaceView,
                                       jfloatArray transformMatrix,
                                       jfloatArray extraTransformMatrix);

JNI_METHOD(void, nativeRelease)(JNIEnv *env, jobject obj, jlong _surfaceView);

#ifdef __cplusplus
}
#endif
