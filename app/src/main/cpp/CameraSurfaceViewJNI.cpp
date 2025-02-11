#include "CameraSurfaceViewJNI.h"
#include "CameraSurfaceView.h"

namespace {
    CameraSurfaceView *castToSurfaceView(jlong handle) {
        return reinterpret_cast<CameraSurfaceView *>(static_cast<uintptr_t>(handle));
    }

}  // namespace

JNI_METHOD(jlong, create)(JNIEnv *env, jobject obj) {
    auto renderer = CameraSurfaceView::create();

    return static_cast<long>(reinterpret_cast<uintptr_t>(renderer.release()));
}

JNI_METHOD(void, nativeInit)(JNIEnv *env, jobject obj, jlong _surfaceView) {

}

JNI_METHOD(void, nativeOnSurfaceCreated)(JNIEnv *env, jobject obj, jlong _surfaceView) {
    castToSurfaceView(_surfaceView)->OnSurfaceCreated();
}

JNI_METHOD(void, nativeOnSurfaceChanged)(JNIEnv *env, jobject obj, jlong _surfaceView, jint width,
                                         jint height) {
    castToSurfaceView(_surfaceView)->OnSurfaceChanged(width, height);
}

JNI_METHOD(void, nativeOnDrawFrame)(JNIEnv *env, jobject obj, jlong _surfaceView) {
    castToSurfaceView(_surfaceView)->OnDrawFrame();
}

JNI_METHOD(void, nativeDrawTexture)(JNIEnv *env, jobject obj, jlong _surfaceView, jint texture,
                                    jint textureWidth, jint textureHeight) {
    castToSurfaceView(_surfaceView)->DrawTexture((GLuint) texture, textureWidth, textureHeight);
}

JNI_METHOD(void, nativeRelease)(JNIEnv *env, jobject obj, jlong _surfaceView) {
    castToSurfaceView(_surfaceView)->Release();
}
