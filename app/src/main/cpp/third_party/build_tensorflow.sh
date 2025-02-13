#!/bin/sh

export ANDROID_SDK_HOME="$HOME/Library/Android/sdk"
export ANDROID_NDK_HOME="$HOME/Library/Android/sdk/ndk/21.4.7075529"

clone_and_checkout() {
    folder="$1"   # Folder name (e.g., flatbuffers, tensorflow)
    repo="$2"     # Repository URL (e.g., https://github.com/google/flatbuffers.git)
    version="$3"  # Version to checkout (e.g., v24.3.25, v2.17.0)

    # Remove the folder if it exists
    rm -rf "$folder"

    # Clone the repository
    git clone "$repo" "$folder"

    # Navigate to the folder and handle errors if it fails
    cd "$folder" || { echo "Failed to change directory to $folder"; exit 1; }

    # Checkout the specified version
    git checkout "$version"
}

build_and_copy() {
    platform=$1 # Platform (e.g., arm64, arm, x86_64, x86)
    cpu=$2      # CPU (e.g., arm64-v8a, armeabi-v7a, x86_64, x86)
    flags=$3    # Additional Bazel flags (e.g., xnn_enable flags)

    # Build with Bazel
    bazel build -c opt --config=android_$platform --cpu=$cpu $flags //tensorflow/lite:tensorflowlite

    # Copy the generated library to the correct output directory
    mkdir -p ../../../libs/"${cpu}" && cp ./bazel-out/"${cpu}"-opt/bin/tensorflow/lite/libtensorflowlite.so ../../../libs/"${cpu}"/libtensorflowlite.so
}

clone_and_checkout "flatbuffers" "https://github.com/google/flatbuffers.git" "v24.3.25"
cd ..
clone_and_checkout "tensorflow" "https://github.com/tensorflow/tensorflow.git" "v2.17.0"

build_and_copy "arm64" "arm64-v8a" "--define xnn_enable_arm_i8mm=false"
build_and_copy "arm" "armeabi-v7a" "--define xnn_enable_arm_i8mm=false"
build_and_copy "x86_64" "x86_64" "--define=xnn_enable_avxvnni=false --define=xnn_enable_avxvnniint8=false --define=xnn_enable_avx512amx=false --define=xnn_enable_avx512fp16=false"
build_and_copy "x86" "x86" "--define=xnn_enable_avxvnni=false --define=xnn_enable_avxvnniint8=false --define=xnn_enable_avx512amx=false --define=xnn_enable_avx512fp16=false"
