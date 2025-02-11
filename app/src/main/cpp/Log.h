#pragma once

#include <android/log.h>
#include <cassert>

#define DEBUG 1

#define  LOG_TAG "native-lib"
#define LOG(severity, ...) ((void)__android_log_print(ANDROID_LOG_##severity, LOG_TAG, __VA_ARGS__))

#define LOGE(...) LOG(ERROR, __VA_ARGS__)
#if DEBUG
#define  LOGI(...)  LOG(INFO, __VA_ARGS__)
#define LOGV(...) LOG(VERBOSE, __VA_ARGS__)
#else
#define LOGI(...)
#define LOGV(...)
#endif
