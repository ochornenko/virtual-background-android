#pragma once

#include "CameraVirtualBackgroundProcessor.h"

#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>

#include <memory>

class CameraSurfaceTexture {
public:
    static auto create() -> std::unique_ptr<CameraSurfaceTexture>;

    CameraSurfaceTexture();

    virtual ~CameraSurfaceTexture();

    auto Initialize(AAssetManager *assetManager, GLuint inputTexture,
                    GLuint outputTexture, GLuint backgroundTexture) -> void;

    auto SetSize(int32_t width, int32_t height) -> void;

    auto UpdateTexImage(float *transformMatrix, float *rotationMatrix) const -> void;

private:
    std::unique_ptr<CameraVirtualBackgroundProcessor> m_pProcessor;
    int32_t m_width;
    int32_t m_height;
    GLuint m_inputTexture;
    GLuint m_framebuffer;
    GLuint m_texture;
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
