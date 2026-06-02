#!/bin/bash
set -e

TF_VERSION="${TF_VERSION:-v2.17.0}"
FLATBUFFERS_VERSION="${FLATBUFFERS_VERSION:-v24.3.25}"
TARGET_ARCH="${TARGET_ARCH:-all}"

# Required by TensorFlow 2.17 configure.py
export TF_PYTHON_VERSION=3.11
export PYTHON_BIN_PATH=/usr/bin/python3

# Android NDK / SDK env vars — required so WORKSPACE's android_ndk_repository
# and android_sdk_repository rules register the Android C++ toolchain.
export ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-/opt/android-sdk/ndk/21.4.7075529}"
export ANDROID_NDK_API_LEVEL=21
export ANDROID_SDK_HOME="${ANDROID_SDK_HOME:-/opt/android-sdk}"
export ANDROID_HOME="$ANDROID_SDK_HOME"
export ANDROID_SDK_API_LEVEL=36
export ANDROID_BUILD_TOOLS_VERSION=36.0.0

cd /work

clone_or_update() {
    folder="$1"; repo="$2"; version="$3"
    if [ -d "$folder/.git" ]; then
        echo "==> Reusing existing $folder, checking out $version"
        cd "$folder"
        git fetch --tags --quiet || true
        git checkout "$version"
        cd ..
    else
        echo "==> Cloning $folder@$version"
        git clone --depth 1 --branch "$version" "$repo" "$folder"
    fi
}

clone_or_update "flatbuffers" "https://github.com/google/flatbuffers.git" "$FLATBUFFERS_VERSION"
clone_or_update "tensorflow"  "https://github.com/tensorflow/tensorflow.git" "$TF_VERSION"

cd tensorflow

build_one() {
    platform="android_$1"; cpu="$2"; flags="$3"
    echo "==> Building TFLite for $platform ($cpu)"
    # shellcheck disable=SC2086
    bazel build -c opt --config="$platform" --cpu="$cpu" $flags \
        //tensorflow/lite:tensorflowlite
    mkdir -p "/output/$cpu"
    cp "./bazel-out/${cpu}-opt/bin/tensorflow/lite/libtensorflowlite.so" \
       "/output/$cpu/libtensorflowlite.so"
    echo "==> Copied libtensorflowlite.so for $cpu"
}

ARM64_FLAGS="--define xnn_enable_arm_i8mm=false --linkopt=-Wl,-z,max-page-size=16384 --linkopt=-Wl,-z,common-page-size=16384"
ARM_FLAGS="--define xnn_enable_arm_i8mm=false"
X86_64_FLAGS="--define=xnn_enable_avxvnni=false --define=xnn_enable_avxvnniint8=false --define=xnn_enable_avx512amx=false --define=xnn_enable_avx512fp16=false --linkopt=-Wl,-z,max-page-size=16384 --linkopt=-Wl,-z,common-page-size=16384"
X86_FLAGS="--define=xnn_enable_avxvnni=false --define=xnn_enable_avxvnniint8=false --define=xnn_enable_avx512amx=false --define=xnn_enable_avx512fp16=false"

case "$TARGET_ARCH" in
    arm64-v8a)   build_one "arm64"  "arm64-v8a"   "$ARM64_FLAGS" ;;
    armeabi-v7a) build_one "arm"    "armeabi-v7a" "$ARM_FLAGS" ;;
    x86_64)      build_one "x86_64" "x86_64"      "$X86_64_FLAGS" ;;
    x86)         build_one "x86"    "x86"         "$X86_FLAGS" ;;
    all)
        build_one "arm64"  "arm64-v8a"   "$ARM64_FLAGS"
        build_one "arm"    "armeabi-v7a" "$ARM_FLAGS"
        build_one "x86_64" "x86_64"      "$X86_64_FLAGS"
        build_one "x86"    "x86"         "$X86_FLAGS"
        ;;
    *) echo "Unknown TARGET_ARCH=$TARGET_ARCH"; exit 1 ;;
esac
