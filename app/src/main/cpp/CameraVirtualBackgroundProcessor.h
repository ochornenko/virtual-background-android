#pragma once

#include <cstdlib>
#include <GLES2/gl2.h>
#include <android/asset_manager.h>
#include <tensorflow/lite/interpreter.h>

class CameraVirtualBackgroundProcessor {
public:
    CameraVirtualBackgroundProcessor();

    ~CameraVirtualBackgroundProcessor();

    auto Initialize(AAssetManager *assetManager, GLuint outputTexture) -> void;

    auto SetSize(int32_t width, int32_t height) -> void;

    auto SetBackgroundTexture(GLuint backgroundTexture) -> void;

    auto ProcessVideoFrame(int width, int height, GLuint vertexBuffer, GLuint textureId) -> void;

private:
    auto Invoke() const -> void;

    auto Resize(GLuint vertexBuffer, GLuint textureId) const -> void;

    auto Process() -> void;

    auto Mix(int32_t width, int32_t height, GLuint vertexBuffer, GLuint textureId) const -> void;

    static auto UpdateTexture(const std::vector<GLubyte> &pixelData, int32_t width, int32_t height,
                              GLuint textureId) -> void;

    static auto VertexResizerShaderCode() -> const char *;

    static auto FragmentResizerShaderCode() -> const char *;

    static auto VertexMixerShaderCode() -> const char *;

    static auto FragmentMixerShaderCode() -> const char *;

    std::unique_ptr<tflite::Interpreter> m_pInterpreter;

    std::vector<GLubyte> m_imageData;
    std::vector<GLubyte> m_modelData;

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
