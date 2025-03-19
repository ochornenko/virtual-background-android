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

#include <GLES2/gl2.h>
#include <android/asset_manager.h>
#include <tensorflow/lite/interpreter.h>

class CameraVirtualBackgroundProcessor {
public:
    CameraVirtualBackgroundProcessor();

    ~CameraVirtualBackgroundProcessor();

    auto Initialize(AAssetManager *assetManager, GLuint outputTexture) -> void;

    auto SetParams(int32_t width, int32_t height, GLuint backgroundTexture,
                   GLuint framebuffer) -> void;

    auto Process(int width, int height, GLuint vertexBuffer) -> void;

private:
    auto Invoke() const -> void;

    auto Resize(GLuint vertexBuffer, GLuint textureId) const -> void;

    auto Process() -> void;

    auto Mix(int32_t width, int32_t height, GLuint vertexBuffer, GLuint textureId) const -> void;

    static auto UpdateTexture(const std::vector<GLubyte> &pixelData, int32_t width, int32_t height,
                              GLuint texture) -> void;

    static auto BindFramebuffer(GLuint framebuffer, GLuint texture,
                                int32_t width, int32_t height) -> void;

    static auto VertexResizerShaderCode() -> const char *;

    static auto FragmentResizerShaderCode() -> const char *;

    static auto VertexMixerShaderCode() -> const char *;

    static auto FragmentMixerShaderCode() -> const char *;

    std::unique_ptr<tflite::Interpreter> m_pInterpreter;

    std::vector<GLubyte> m_imageData;
    std::vector<GLubyte> m_modelData;

    GLuint m_texture;
    GLuint m_outputFramebuffer;
    GLuint m_outputTexture;
    GLuint m_backgroundTexture;
    GLuint m_maskTexture;
    GLuint m_resizeProgram;
    GLint m_resizePosition;
    GLint m_resizeTexCoord;
    GLuint m_resizeFramebuffer;
    GLuint m_resizeTexture;
    GLuint m_mixProgram;
    GLint m_mixPosition;
    GLint m_mixTexCoord;
    int32_t m_modelWidth;
    int32_t m_modelHeight;
    int32_t m_imageWidth;
    int32_t m_imageHeight;
};
