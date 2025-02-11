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

    auto Release() -> void;

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
