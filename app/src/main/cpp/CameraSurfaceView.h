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

#include <memory>

class CameraSurfaceView {
public:
    static auto create() -> std::unique_ptr<CameraSurfaceView>;

    CameraSurfaceView();

    virtual ~CameraSurfaceView();

    auto OnSurfaceCreated() -> void;

    auto OnSurfaceChanged(int32_t width, int32_t height) -> void;

    auto OnDrawFrame() -> void;

    auto DrawTexture(GLuint texture, int32_t textureWidth, int32_t textureHeight) const -> void;

private:
    int32_t m_surfaceWidth;
    int32_t m_surfaceHeight;
    GLuint m_vertexBuffer;
    GLuint m_program;
    GLint m_position;
    GLint m_texCoord;

private:
    static auto VertexShaderCode() -> const char *;

    static auto FragmentShaderCode() -> const char *;
};
