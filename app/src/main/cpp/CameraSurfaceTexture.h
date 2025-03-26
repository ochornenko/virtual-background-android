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

#include "CameraVirtualBackgroundProcessor.h"

#include <memory>

class CameraSurfaceTexture {
public:
    static auto create() -> std::unique_ptr<CameraSurfaceTexture>;

    CameraSurfaceTexture();

    virtual ~CameraSurfaceTexture();

    auto Initialize(AAssetManager *assetManager, GLuint inputTexture, GLuint outputTexture) -> void;

    auto SetParams(int32_t width, int32_t height, GLuint backgroundTexture) -> void;

    auto UpdateTexImage(float *transformMatrix, float *rotationMatrix) const -> void;

private:
    std::unique_ptr<CameraVirtualBackgroundProcessor> m_pProcessor;
    int32_t m_width;
    int32_t m_height;
    GLuint m_inputTexture;
    GLuint m_framebuffer;
    GLuint m_vertexBuffer;
    GLuint m_program;
    GLint m_position;
    GLint m_texCoord;
    GLint m_transformMatrix;
    GLint m_rotationMatrix;

private:
    static auto VertexShaderCode() -> const char *;

    static auto FragmentShaderCode() -> const char *;
};
