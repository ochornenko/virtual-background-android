/*
 * Copyright 2025 Oleg Chornenko.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

JNI_METHOD(void, nativeOnSurfaceCreated)(JNIEnv *env, jobject obj, jlong _surfaceView) {
    if (_surfaceView == 0L) return;

    castToSurfaceView(_surfaceView)->OnSurfaceCreated();
}

JNI_METHOD(void, nativeOnSurfaceChanged)(JNIEnv *env, jobject obj, jlong _surfaceView, jint width,
                                         jint height) {
    if (_surfaceView == 0L) return;

    castToSurfaceView(_surfaceView)->OnSurfaceChanged(width, height);
}

JNI_METHOD(void, nativeOnDrawFrame)(JNIEnv *env, jobject obj, jlong _surfaceView) {
    if (_surfaceView == 0L) return;

    castToSurfaceView(_surfaceView)->OnDrawFrame();
}

JNI_METHOD(void, nativeDrawTexture)(JNIEnv *env, jobject obj, jlong _surfaceView, jint texture,
                                    jint textureWidth, jint textureHeight) {
    if (_surfaceView == 0L) return;

    castToSurfaceView(_surfaceView)->DrawTexture((GLuint) texture, textureWidth, textureHeight);
}

JNI_METHOD(void, nativeRelease)(JNIEnv *env, jobject obj, jlong _surfaceView) {
    if (_surfaceView == 0L) return;

    delete castToSurfaceView(_surfaceView);

    _surfaceView = 0L;
}
