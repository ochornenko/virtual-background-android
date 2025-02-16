#include "CameraSurfaceTextureJNI.h"

#include "CameraSurfaceTexture.h"

#include <android/asset_manager_jni.h>

namespace {
    CameraSurfaceTexture *castToSurfaceTexture(jlong handle) {
        return reinterpret_cast<CameraSurfaceTexture *>(static_cast<uintptr_t>(handle));
    }
}  // namespace

JNI_METHOD(jlong, create)(JNIEnv *env, jobject obj) {
    auto surfaceTexture = CameraSurfaceTexture::create();

    return static_cast<long>(reinterpret_cast<uintptr_t>(surfaceTexture.release()));
}

JNI_METHOD(void, nativeInit)(JNIEnv *env, jobject obj, jobject _assetManager, jlong _surfaceView,
                             jint inputTexture, jint outputTexture) {
    if (_surfaceView == 0L) return;

    auto assetManager = AAssetManager_fromJava(env, _assetManager);
    castToSurfaceTexture(_surfaceView)->Initialize(assetManager, inputTexture, outputTexture);
}

JNI_METHOD(void, nativeSetSize)(JNIEnv *env, jobject obj, jlong _surfaceView, jint width,
                                jint height) {
    if (_surfaceView == 0L) return;

    castToSurfaceTexture(_surfaceView)->SetSize(width, height);
}

JNI_METHOD(void, nativeSetBackgroundTexture)(JNIEnv *env, jobject obj, jlong _surfaceView,
                                             jint backgroundTexture) {
    if (_surfaceView == 0L) return;

    castToSurfaceTexture(_surfaceView)->SetBackgroundTexture(backgroundTexture);
}

JNI_METHOD(void, nativeUpdateTexImage)(JNIEnv *env, jobject obj, jlong _surfaceView,
                                       jfloatArray transformMatrix,
                                       jfloatArray extraTransformMatrix) {
    if (_surfaceView == 0L) return;

    jfloat *matrix = env->GetFloatArrayElements(transformMatrix, nullptr);
    jfloat *extraMatrix = env->GetFloatArrayElements(extraTransformMatrix, nullptr);
    castToSurfaceTexture(_surfaceView)->UpdateTexImage(matrix, extraMatrix);
    env->ReleaseFloatArrayElements(transformMatrix, matrix, 0);
    env->ReleaseFloatArrayElements(extraTransformMatrix, extraMatrix, 0);
}

JNI_METHOD(void, nativeRelease)(JNIEnv *env, jobject obj, jlong _surfaceView) {
    if (_surfaceView == 0L) return;

    delete castToSurfaceTexture(_surfaceView);

    _surfaceView = 0L;
}
