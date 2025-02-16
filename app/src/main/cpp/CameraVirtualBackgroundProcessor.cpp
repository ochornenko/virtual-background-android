#include "CameraVirtualBackgroundProcessor.h"

#include "GLUtils.h"
#include "Log.h"

#include <tuple>

#include <tensorflow/lite/core/interpreter_builder.h>
#include <tensorflow/lite/kernels/register.h>
#include <tensorflow/lite/model_builder.h>
#include <tensorflow/lite/core/api/op_resolver.h>

struct RGB {
    unsigned char red;
    unsigned char green;
    unsigned char blue;
};

static void AddPadding(const std::vector<GLubyte> &srcImage, int32_t srcWidth, int32_t srcHeight,
                       std::vector<GLubyte> &dstImage, int32_t dstWidth, int32_t dstHeight,
                       GLubyte padValue = 0) {
    // Ensure destination image has correct size and initialize with padding color (e.g., black)
    dstImage.resize(dstWidth * dstHeight * 3, padValue);

    // Copy each row from the source image to the destination image
    for (int y = 0; y < srcHeight; ++y) {
        // Source row starting index
        int srcIndex = y * srcWidth * 3;

        // Destination row starting index (we center it horizontally)
        int dstIndex = y * dstWidth * 3 + ((dstWidth - srcWidth) / 2) * 3;

        // Copy the row from source to destination
        std::copy(srcImage.begin() + srcIndex, srcImage.begin() + srcIndex + (srcWidth * 3),
                  dstImage.begin() + dstIndex);
    }
}

static void RemovePadding(const std::vector<GLubyte> &srcImage, int32_t srcWidth, int32_t srcHeight,
                          std::vector<GLubyte> &dstImage, int32_t dstWidth, int32_t dstHeight) {
    // Ensure destination image has correct size
    dstImage.resize(dstWidth * dstHeight * 3);

    // Calculate the horizontal and vertical padding
    int horizontalPadding = (srcWidth - dstWidth) / 2;

    // Copy each row from the source image to the destination image
    for (int y = 0; y < dstHeight; ++y) {
        // Source row starting index (we skip the padding on both sides horizontally)
        int srcIndex = y * srcWidth * 3 + horizontalPadding * 3;

        // Destination row starting index
        int dstIndex = y * dstWidth * 3;

        // Copy the row from source to destination (cropping the padding)
        std::copy(srcImage.begin() + srcIndex, srcImage.begin() + srcIndex + (dstWidth * 3),
                  dstImage.begin() + dstIndex);
    }
}

static std::tuple<int32_t, int32_t> ResizeImageToFit(int32_t originalWidth, int32_t originalHeight,
                                                     int32_t modelWidth, int32_t modelHeight) {
    auto aspectRatio = static_cast<float>(originalWidth) / static_cast<float>(originalHeight);
    int imageWidth, imageHeight;

    // Check if the image is wider (landscape) or taller (portrait)
    if (aspectRatio > 1.0f) {
        imageWidth = modelWidth;
        imageHeight = static_cast<int32_t>(static_cast<float>(modelWidth) / aspectRatio);
    } else {
        imageHeight = modelHeight;
        imageWidth = static_cast<int32_t>(static_cast<float>(modelHeight) * aspectRatio);
    }

    if (imageWidth > modelWidth) {
        imageWidth = modelWidth;
        imageHeight = static_cast<int32_t>(static_cast<float>(modelWidth) / aspectRatio);
    }

    if (imageHeight > modelHeight) {
        imageHeight = modelHeight;
        imageWidth = static_cast<int32_t>(static_cast<float>(modelHeight) * aspectRatio);
    }

    return std::make_tuple(imageWidth, imageHeight);
}

CameraVirtualBackgroundProcessor::CameraVirtualBackgroundProcessor()
        : m_outputFramebuffer(0),
          m_outputTexture(0),
          m_backgroundTexture(0),
          m_maskTexture(0),
          m_resizeProgram(0),
          m_resizePosition(0),
          m_resizeTexCoord(0),
          m_resizeFramebuffer(0),
          m_resizeTexture(0),
          m_mixProgram(0),
          m_mixPosition(0),
          m_mixTexCoord(0),
          m_modelWidth(0),
          m_modelHeight(0),
          m_imageWidth(0),
          m_imageHeight(0) {
}

CameraVirtualBackgroundProcessor::~CameraVirtualBackgroundProcessor() {
    if (m_backgroundTexture != 0) {
        glDeleteTextures(1, &m_backgroundTexture);
        m_backgroundTexture = 0;
    }

    if (m_maskTexture != 0) {
        glDeleteTextures(1, &m_maskTexture);
        m_maskTexture = 0;
    }

    if (m_outputTexture != 0) {
        glDeleteTextures(1, &m_outputTexture);
        m_outputTexture = 0;
    }

    if (m_outputFramebuffer != 0) {
        glDeleteFramebuffers(1, &m_outputFramebuffer);
        m_outputFramebuffer = 0;
    }

    if (m_resizeProgram != 0) {
        DeleteProgram(m_resizeProgram);
    }

    if (m_mixProgram != 0) {
        DeleteProgram(m_mixProgram);
    }
}

auto CameraVirtualBackgroundProcessor::Initialize(AAssetManager *assetManager,
                                                  GLuint outputTexture) -> void {
    AAsset *modelFile = AAssetManager_open(assetManager, "model/selfie_segmenter.tflite",
                                           AASSET_MODE_BUFFER);
    CHECK(modelFile);
    const size_t bufferSize = AAsset_getLength(modelFile);
    std::vector<const char *> buffer(bufferSize);
    int status = AAsset_read(modelFile, buffer.data(), bufferSize);
    AAsset_close(modelFile);
    CHECK(status >= 0);

    auto model = tflite::FlatBufferModel::BuildFromBuffer(
            reinterpret_cast<const char *>(buffer.data()), bufferSize);

    CHECK(model);
    tflite::ops::builtin::BuiltinOpResolver resolver;
    tflite::InterpreterBuilder(*model, resolver)(&m_pInterpreter);

    CHECK(m_pInterpreter);
    CHECK(m_pInterpreter->AllocateTensors() == kTfLiteOk);

    auto tensorInputIndex = m_pInterpreter->inputs()[0];
    m_modelHeight = m_pInterpreter->tensor(tensorInputIndex)->dims->data[1];
    m_modelWidth = m_pInterpreter->tensor(tensorInputIndex)->dims->data[2];

    m_modelData.reserve(m_modelWidth * m_modelHeight * 3);

    m_outputTexture = outputTexture;

    glGenTextures(1, &m_maskTexture);

    m_resizeProgram = CreateProgram(VertexResizerShaderCode(), FragmentResizerShaderCode());

    m_resizePosition = glGetAttribLocation(m_resizeProgram, "aPosition");
    m_resizeTexCoord = glGetAttribLocation(m_resizeProgram, "aTexCoord");

    m_mixProgram = CreateProgram(VertexMixerShaderCode(), FragmentMixerShaderCode());

    m_mixPosition = glGetAttribLocation(m_mixProgram, "aPosition");
    m_mixTexCoord = glGetAttribLocation(m_mixProgram, "aTexCoord");
}

auto CameraVirtualBackgroundProcessor::SetSize(int32_t width, int32_t height) -> void {
    auto [imageWidth, imageHeight] = ResizeImageToFit(width, height, m_modelWidth, m_modelHeight);

    m_imageWidth = imageWidth;
    m_imageHeight = imageHeight;

    m_imageData.reserve(m_imageWidth * m_imageHeight * 3);

    if (glIsFramebuffer(m_resizeFramebuffer)) {
        glDeleteFramebuffers(1, &m_resizeFramebuffer);
    }
    glGenFramebuffers(1, &m_resizeFramebuffer);
    glBindFramebuffer(GL_FRAMEBUFFER, m_resizeFramebuffer);

    if (glIsTexture(m_resizeTexture)) {
        glDeleteTextures(1, &m_resizeTexture);
    }
    glGenTextures(1, &m_resizeTexture);
    glBindTexture(GL_TEXTURE_2D, m_resizeTexture);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, m_modelWidth, m_modelHeight, 0, GL_RGB, GL_UNSIGNED_BYTE,
                 nullptr);

    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, m_resizeTexture, 0);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);

    if (glIsFramebuffer(m_outputFramebuffer)) {
        glDeleteFramebuffers(1, &m_outputFramebuffer);
    }
    glGenFramebuffers(1, &m_outputFramebuffer);
    glBindFramebuffer(GL_FRAMEBUFFER, m_outputFramebuffer);

    if (glIsTexture(m_outputTexture)) {
        glDeleteTextures(1, &m_outputTexture);
    }
    glBindTexture(GL_TEXTURE_2D, m_outputTexture);

    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);

    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, m_outputTexture, 0);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
}

auto CameraVirtualBackgroundProcessor::SetBackgroundTexture(GLuint backgroundTexture) -> void {
    m_backgroundTexture = backgroundTexture;
}

auto CameraVirtualBackgroundProcessor::Invoke() const -> void {
    using clock = std::chrono::steady_clock;

    const auto start = clock::now();
    {
        const auto status = m_pInterpreter->Invoke();
        assert(status == kTfLiteOk);
    }
    const auto end = clock::now();

    auto time = std::chrono::duration_cast<std::chrono::milliseconds>(end - start).count();

    LOGI("Tensorflow invoke time %lld ms\n", time);
}

auto
CameraVirtualBackgroundProcessor::UpdateTexture(const std::vector<GLubyte> &pixelData,
                                                int32_t width,
                                                int32_t height, GLuint textureId) -> void {
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, textureId);

    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, width, height, 0, GL_RGB,
                 GL_UNSIGNED_BYTE, pixelData.data());

    glBindTexture(GL_TEXTURE_2D, 0);
}

auto CameraVirtualBackgroundProcessor::ProcessVideoFrame(int32_t width,
                                                         int32_t height,
                                                         GLuint vertexBuffer,
                                                         GLuint textureId) -> void {
    Resize(vertexBuffer, textureId);
    Process();
    Mix(width, height, vertexBuffer, textureId);
}

auto CameraVirtualBackgroundProcessor::Resize(GLuint vertexBuffer, GLuint textureId) const -> void {
    glViewport(0, 0, m_imageWidth, m_imageHeight);

    glBindFramebuffer(GL_FRAMEBUFFER, m_resizeFramebuffer);

    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, textureId);

    glUseProgram(m_resizeProgram);

    glVertexAttribPointer(m_resizePosition, 4, GL_FLOAT, GL_FALSE, 6 * sizeof(GLfloat),
                          (const GLvoid *) (0 * sizeof(GLfloat)));
    glEnableVertexAttribArray(m_resizePosition);
    glVertexAttribPointer(m_resizeTexCoord, 2, GL_FLOAT, GL_FALSE, 6 * sizeof(GLfloat),
                          (const GLvoid *) (4 * sizeof(GLfloat)));
    glEnableVertexAttribArray(m_resizeTexCoord);
    glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer);
    glDrawElements(GL_TRIANGLE_STRIP, 4, GL_UNSIGNED_SHORT, VertexIndices());
}

auto CameraVirtualBackgroundProcessor::Process() -> void {
    glReadPixels(0, 0, m_imageWidth, m_imageHeight, GL_RGB, GL_UNSIGNED_BYTE, m_imageData.data());

    AddPadding(m_imageData, m_imageWidth, m_imageHeight, m_modelData, m_modelWidth, m_modelHeight,
               0);

    auto size = m_modelData.size();
    auto tensorInputIndex = m_pInterpreter->inputs()[0];
    auto input = m_pInterpreter->typed_tensor<float>(tensorInputIndex);

    for (auto i = 0; i < size; i++) {
        float f = m_modelData[i];
        input[i] = (f - 127.5f) / 127.5f;
    }

    Invoke();

    auto tensorOutputIndex = m_pInterpreter->outputs()[0];
    auto data = (float *) m_pInterpreter->tensor(tensorOutputIndex)->data.data;
    auto rgb = (RGB *) m_modelData.data();

    for (int i = 0; i < m_modelHeight; i++) {
        for (int j = 0; j < m_modelWidth; j++) {
            // Get the probability value for the person class (assuming it's the first class)
            float personProbability = data[i * m_modelWidth + j];

            // Set the pixel color based on the probability threshold (e.g., 0.5)
            if (personProbability > 0.5) {
                rgb[j + i * m_modelWidth] = {255, 0, 0};  // Person color
            } else {
                rgb[j + i * m_modelWidth] = {0};  // Background color
            }
        }
    }

    RemovePadding(m_modelData, m_modelWidth, m_modelHeight, m_imageData, m_imageWidth,
                  m_imageHeight);

    UpdateTexture(m_imageData, m_imageWidth, m_imageHeight, m_maskTexture);
}

auto CameraVirtualBackgroundProcessor::Mix(int32_t width,
                                           int32_t height,
                                           GLuint vertexBuffer,
                                           GLuint textureId) const -> void {
    glViewport(0, 0, width, height);

    glBindFramebuffer(GL_FRAMEBUFFER, m_outputFramebuffer);

    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, textureId);

    glActiveTexture(GL_TEXTURE1);
    glBindTexture(GL_TEXTURE_2D, m_backgroundTexture);

    glActiveTexture(GL_TEXTURE2);
    glBindTexture(GL_TEXTURE_2D, m_maskTexture);

    glUseProgram(m_mixProgram);

    glUniform1i(glGetUniformLocation(m_mixProgram, "uTexture"), 0);  // 0 = GL_TEXTURE0
    glUniform1i(glGetUniformLocation(m_mixProgram, "uBackgroundTexture"), 1);  // 1 = GL_TEXTURE1
    glUniform1i(glGetUniformLocation(m_mixProgram, "uMaskTexture"), 2);  // 2 = GL_TEXTURE2

    glVertexAttribPointer(m_mixPosition, 4, GL_FLOAT, GL_FALSE, 6 * sizeof(GLfloat),
                          (const GLvoid *) (0 * sizeof(GLfloat)));
    glEnableVertexAttribArray(m_mixPosition);
    glVertexAttribPointer(m_mixTexCoord, 2, GL_FLOAT, GL_FALSE, 6 * sizeof(GLfloat),
                          (const GLvoid *) (4 * sizeof(GLfloat)));
    glEnableVertexAttribArray(m_mixTexCoord);
    glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer);
    glDrawElements(GL_TRIANGLE_STRIP, 4, GL_UNSIGNED_SHORT, VertexIndices());

    glBindFramebuffer(GL_FRAMEBUFFER, 0);
}

auto CameraVirtualBackgroundProcessor::VertexResizerShaderCode() -> const char * {
    static const char vertexShader[] =
            "attribute vec4 aPosition;\n"
            "attribute vec4 aTexCoord;\n"
            "varying vec2 vTexCoord;\n"
            "void main() {\n"
            "    gl_Position = aPosition;\n"
            "    vTexCoord = aTexCoord.xy;\n"
            "}\n";

    return vertexShader;
}

auto CameraVirtualBackgroundProcessor::FragmentResizerShaderCode() -> const char * {
    static const char fragmentShader[] =
            "precision mediump float;\n"
            "uniform sampler2D uTexture;\n"
            "varying vec2 vTexCoord;\n"
            "void main() {\n"
            "    gl_FragColor = texture2D(uTexture, vec2(vTexCoord.x, 1.0 - vTexCoord.y));\n"
            "}\n";

    return fragmentShader;
}

auto CameraVirtualBackgroundProcessor::VertexMixerShaderCode() -> const char * {
    static const char vertexShader[] =
            "attribute vec4 aPosition;\n"
            "attribute vec4 aTexCoord;\n"
            "varying vec2 vTexCoord;\n"
            "void main() {\n"
            "    gl_Position = aPosition;\n"
            "    vTexCoord = aTexCoord.xy;\n"
            "}\n";

    return vertexShader;
}

auto CameraVirtualBackgroundProcessor::FragmentMixerShaderCode() -> const char * {
    static const char fragmentShader[] =
            "precision mediump float;\n"
            "uniform sampler2D uTexture;\n"
            "uniform sampler2D uMaskTexture;\n"
            "uniform sampler2D uBackgroundTexture;\n"
            "varying vec2 vTexCoord;\n"
            "void main() {\n"
            "    vec4 backgroundColor = texture2D(uBackgroundTexture, vec2(vTexCoord.x, 1.0 - vTexCoord.y));\n"
            "    vec4 maskColor = texture2D(uMaskTexture, vec2(vTexCoord.x, 1.0 - vTexCoord.y));\n"
            "    vec4 inputColor = texture2D(uTexture, vTexCoord);\n"
            "    gl_FragColor = mix(backgroundColor, inputColor, maskColor.r);\n"
            "}\n";

    return fragmentShader;
}
