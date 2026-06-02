#!/bin/sh
# Docker-based TensorFlow Lite Android builder.
#
# Usage:
#   ./build_tensorflow_docker.sh                 # build all 4 architectures
#   ./build_tensorflow_docker.sh arm64-v8a       # build a single architecture
#
# Output: libs/<arch>/libtensorflowlite.so

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
LIBS_DIR="$SCRIPT_DIR/../../libs"
mkdir -p "$LIBS_DIR"
LIBS_DIR="$(cd "$LIBS_DIR" && pwd)"
IMAGE_TAG="tensorflow-android-builder:2.17.0"
TF_VERSION="v2.17.0"
FLATBUFFERS_VERSION="v24.3.25"
NDK_VERSION="21.4.7075529"
BAZEL_VERSION="6.5.0"
DOCKERFILE="$SCRIPT_DIR/tensorflow.Dockerfile"
INNER_SCRIPT="$SCRIPT_DIR/build_tensorflow_inside_docker.sh"

# --- Build Docker image (cached after first run) -------------------------
echo "==> Building Docker image: $IMAGE_TAG"
docker build --platform linux/amd64 -t "$IMAGE_TAG" -f "$DOCKERFILE" "$SCRIPT_DIR"

# --- Run the build inside Docker -----------------------------------------
echo "==> Ensuring build_tensorflow_inside_docker.sh is executable"
chmod +x "$INNER_SCRIPT"

TARGET_ARCH="${1:-all}"
mkdir -p "$LIBS_DIR/arm64-v8a" "$LIBS_DIR/armeabi-v7a" "$LIBS_DIR/x86_64" "$LIBS_DIR/x86"

echo "==> Running build inside Docker (TARGET_ARCH=$TARGET_ARCH)"
docker run --rm -it --platform linux/amd64 \
    -v "$SCRIPT_DIR:/work" \
    -v "$LIBS_DIR:/output" \
    -e TF_VERSION="$TF_VERSION" \
    -e FLATBUFFERS_VERSION="$FLATBUFFERS_VERSION" \
    -e TARGET_ARCH="$TARGET_ARCH" \
    "$IMAGE_TAG" \
    /work/build_tensorflow_inside_docker.sh



echo ""
echo "==> Done. Libraries written to:"
echo "    $LIBS_DIR/<arch>/libtensorflowlite.so"
