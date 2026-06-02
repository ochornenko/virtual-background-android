ARG BUILD_PLATFORM=linux/amd64
FROM --platform=${BUILD_PLATFORM} ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV ANDROID_NDK_HOME=/opt/android-sdk/ndk/21.4.7075529
ENV ANDROID_SDK_HOME=/opt/android-sdk
ENV PATH=$PATH:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools

RUN apt-get update && apt-get install -y --no-install-recommends \
        curl unzip git ca-certificates openjdk-17-jdk-headless \
        python3 python3-pip python3-numpy build-essential \
        zip wget pkg-config && \
    rm -rf /var/lib/apt/lists/*

# Install Bazel
RUN curl -fsSL -o /usr/local/bin/bazel \
        "https://github.com/bazelbuild/bazel/releases/download/6.5.0/bazel-6.5.0-linux-x86_64" && \
    chmod +x /usr/local/bin/bazel

# Install Android SDK command-line tools + NDK
RUN mkdir -p $ANDROID_SDK_ROOT/cmdline-tools && \
    cd /tmp && \
    curl -fsSL -o cmdtools.zip \
        "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip" && \
    unzip -q cmdtools.zip -d $ANDROID_SDK_ROOT/cmdline-tools && \
    mv $ANDROID_SDK_ROOT/cmdline-tools/cmdline-tools $ANDROID_SDK_ROOT/cmdline-tools/latest && \
    rm cmdtools.zip && \
    yes | sdkmanager --licenses > /dev/null && \
    sdkmanager --install \
        "platform-tools" \
        "platforms;android-36" \
        "build-tools;36.0.0" > /dev/null && \
    curl -fsSL -o /tmp/ndk.zip \
        "https://dl.google.com/android/repository/android-ndk-r21e-linux-x86_64.zip" && \
    unzip -q /tmp/ndk.zip -d /tmp/ndk && \
    mkdir -p $ANDROID_SDK_ROOT/ndk && \
    mv /tmp/ndk/android-ndk-r21e $ANDROID_SDK_ROOT/ndk/21.4.7075529 && \
    rm -rf /tmp/ndk.zip /tmp/ndk

WORKDIR /work
