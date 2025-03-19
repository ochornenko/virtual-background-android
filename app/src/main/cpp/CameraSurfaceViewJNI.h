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
