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

#include "CameraVirtualBackgroundProcessor.h"

#include "GLUtils.h"
#include "Log.h"

#include <algorithm>
#include <chrono>
#include <tuple>

#include <tensorflow/lite/core/interpreter_builder.h>
#include <tensorflow/lite/kernels/register.h>
#include <tensorflow/lite/model_builder.h>
#include <tensorflow/lite/core/api/op_resolver.h>

static constexpr int32_t kInvokeLogInterval = 100;

// Converts an RGBA source image to RGB and centers it horizontally in the destination image.
static void AddPadding(const std::vector<GLubyte> &srcImage, int32_t srcWidth, int32_t srcHeight,
                       std::vector<GLubyte> &dstImage, int32_t dstWidth, int32_t dstHeight,
                       GLubyte padValue = 0) {
    // Reset the whole destination every frame, so the padding never keeps the previous mask
    dstImage.assign(dstWidth * dstHeight * 3, padValue);

    const int xOffset = (dstWidth - srcWidth) / 2;

    for (int y = 0; y < srcHeight; ++y) {
        for (int x = 0; x < srcWidth; ++x) {
            const int srcIndex = (y * srcWidth + x) * 4;
            const int dstIndex = (y * dstWidth + xOffset + x) * 3;

            dstImage[dstIndex] = srcImage[srcIndex];
            dstImage[dstIndex + 1] = srcImage[srcIndex + 1];
            dstImage[dstIndex + 2] = srcImage[srcIndex + 2];
        }
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
        : m_texture(0),
          m_outputFramebuffer(0),
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
          m_imageHeight(0),
          m_invokeTimeTotalUs(0),
          m_invokeCount(0) {
}

CameraVirtualBackgroundProcessor::~CameraVirtualBackgroundProcessor() {
    if (m_texture != 0) {
        glDeleteTextures(1, &m_texture);
        m_texture = 0;
    }

    if (m_maskTexture != 0) {
        glDeleteTextures(1, &m_maskTexture);
        m_maskTexture = 0;
    }

    if (m_outputFramebuffer != 0) {
        glDeleteFramebuffers(1, &m_outputFramebuffer);
        m_outputFramebuffer = 0;
    }

    if (m_resizeTexture != 0) {
        glDeleteTextures(1, &m_resizeTexture);
        m_resizeTexture = 0;
    }

    if (m_resizeFramebuffer != 0) {
        glDeleteFramebuffers(1, &m_resizeFramebuffer);
        m_resizeFramebuffer = 0;
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
    m_modelBuffer.resize(bufferSize);
    int status = AAsset_read(modelFile, m_modelBuffer.data(), bufferSize);
    AAsset_close(modelFile);
    CHECK(status == static_cast<int>(bufferSize));

    m_pModel = tflite::FlatBufferModel::BuildFromBuffer(m_modelBuffer.data(),
                                                        m_modelBuffer.size());

    CHECK(m_pModel);
    tflite::ops::builtin::BuiltinOpResolver resolver;
    tflite::InterpreterBuilder(*m_pModel, resolver)(&m_pInterpreter);

    CHECK(m_pInterpreter);
    CHECK(m_pInterpreter->AllocateTensors() == kTfLiteOk);

    auto tensorInputIndex = m_pInterpreter->inputs()[0];
    m_modelHeight = m_pInterpreter->tensor(tensorInputIndex)->dims->data[1];
    m_modelWidth = m_pInterpreter->tensor(tensorInputIndex)->dims->data[2];

    m_modelData.resize(m_modelWidth * m_modelHeight * 3);

    m_outputTexture = outputTexture;

    glGenTextures(1, &m_texture);
    glGenTextures(1, &m_maskTexture);

    m_resizeProgram = CreateProgram(VertexResizerShaderCode(), FragmentResizerShaderCode());

    m_resizePosition = glGetAttribLocation(m_resizeProgram, "aPosition");
    m_resizeTexCoord = glGetAttribLocation(m_resizeProgram, "aTexCoord");

    m_mixProgram = CreateProgram(VertexMixerShaderCode(), FragmentMixerShaderCode());

    m_mixPosition = glGetAttribLocation(m_mixProgram, "aPosition");
    m_mixTexCoord = glGetAttribLocation(m_mixProgram, "aTexCoord");

    // Texture units never change, so the sampler uniforms are set once
    glUseProgram(m_mixProgram);
    glUniform1i(glGetUniformLocation(m_mixProgram, "uTexture"), 0);
    glUniform1i(glGetUniformLocation(m_mixProgram, "uBackgroundTexture"), 1);
    glUniform1i(glGetUniformLocation(m_mixProgram, "uMaskTexture"), 2);
    glUseProgram(0);
}

auto CameraVirtualBackgroundProcessor::SetParams(int32_t width, int32_t height,
                                                 GLuint backgroundTexture,
                                                 GLuint framebuffer) -> void {
    if (!glIsTexture(backgroundTexture)) {
        BindFramebuffer(framebuffer, m_outputTexture, width, height);
        return;
    }

    BindFramebuffer(framebuffer, m_texture, width, height);

    m_backgroundTexture = backgroundTexture;

    auto [imageWidth, imageHeight] = ResizeImageToFit(width, height, m_modelWidth, m_modelHeight);

    m_imageWidth = imageWidth;
    m_imageHeight = imageHeight;

    m_imageData.resize(m_imageWidth * m_imageHeight * 4);
    m_maskData.resize(m_imageWidth * m_imageHeight);

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

    glBindTexture(GL_TEXTURE_2D, m_outputTexture);

    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);

    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, m_outputTexture, 0);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
}

auto CameraVirtualBackgroundProcessor::Invoke() -> void {
    using clock = std::chrono::steady_clock;

    const auto start = clock::now();
    const auto status = m_pInterpreter->Invoke();
    const auto end = clock::now();

    if (status != kTfLiteOk) {
        LOGE("Tensorflow invoke failed with status %d\n", status);
        return;
    }

    m_invokeTimeTotalUs += std::chrono::duration_cast<std::chrono::microseconds>(end - start).count();
    if (++m_invokeCount == kInvokeLogInterval) {
        LOGI("Tensorflow invoke time %.1f ms (average of %d frames)\n",
             static_cast<double>(m_invokeTimeTotalUs) / kInvokeLogInterval / 1000.0,
             kInvokeLogInterval);
        m_invokeTimeTotalUs = 0;
        m_invokeCount = 0;
    }
}

auto
CameraVirtualBackgroundProcessor::UpdateTexture(const std::vector<GLubyte> &pixelData,
                                                int32_t width, int32_t height,
                                                GLenum format, GLuint texture) -> void {
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, texture);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);

    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

    glTexImage2D(GL_TEXTURE_2D, 0, format, width, height, 0, format,
                 GL_UNSIGNED_BYTE, pixelData.data());

    glBindTexture(GL_TEXTURE_2D, 0);
}

auto CameraVirtualBackgroundProcessor::BindFramebuffer(GLuint framebuffer, GLuint texture,
                                                       int32_t width, int32_t height) -> void {
    glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);

    glBindTexture(GL_TEXTURE_2D, texture);

    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);

    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
}

auto CameraVirtualBackgroundProcessor::Process(int32_t width, int32_t height,
                                               GLuint vertexBuffer) -> void {
    if (glIsTexture(m_backgroundTexture)) {
        Resize(vertexBuffer, m_texture);
        Process();
        Mix(width, height, vertexBuffer, m_texture);
    }
}

auto CameraVirtualBackgroundProcessor::Resize(GLuint vertexBuffer, GLuint texture) const -> void {
    glViewport(0, 0, m_imageWidth, m_imageHeight);

    glBindFramebuffer(GL_FRAMEBUFFER, m_resizeFramebuffer);

    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, texture);

    glUseProgram(m_resizeProgram);

    glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer);
    glVertexAttribPointer(m_resizePosition, 4, GL_FLOAT, GL_FALSE, 6 * sizeof(GLfloat),
                          (const GLvoid *) (0 * sizeof(GLfloat)));
    glEnableVertexAttribArray(m_resizePosition);
    glVertexAttribPointer(m_resizeTexCoord, 2, GL_FLOAT, GL_FALSE, 6 * sizeof(GLfloat),
                          (const GLvoid *) (4 * sizeof(GLfloat)));
    glEnableVertexAttribArray(m_resizeTexCoord);
    glDrawElements(GL_TRIANGLE_STRIP, 4, GL_UNSIGNED_SHORT, VertexIndices());
}

auto CameraVirtualBackgroundProcessor::Process() -> void {
    // GL_RGBA/GL_UNSIGNED_BYTE is the only readback format guaranteed by OpenGL ES 2.0
    glReadPixels(0, 0, m_imageWidth, m_imageHeight, GL_RGBA, GL_UNSIGNED_BYTE, m_imageData.data());

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

    // Keep the person probability (0..1) as an 8-bit mask, cropping the horizontal padding
    const int xOffset = (m_modelWidth - m_imageWidth) / 2;
    for (int y = 0; y < m_imageHeight; ++y) {
        const float *row = data + y * m_modelWidth + xOffset;
        GLubyte *maskRow = m_maskData.data() + y * m_imageWidth;
        for (int x = 0; x < m_imageWidth; ++x) {
            maskRow[x] = static_cast<GLubyte>(std::clamp(row[x], 0.0f, 1.0f) * 255.0f + 0.5f);
        }
    }

    UpdateTexture(m_maskData, m_imageWidth, m_imageHeight, GL_LUMINANCE, m_maskTexture);
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

    glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer);
    glVertexAttribPointer(m_mixPosition, 4, GL_FLOAT, GL_FALSE, 6 * sizeof(GLfloat),
                          (const GLvoid *) (0 * sizeof(GLfloat)));
    glEnableVertexAttribArray(m_mixPosition);
    glVertexAttribPointer(m_mixTexCoord, 2, GL_FLOAT, GL_FALSE, 6 * sizeof(GLfloat),
                          (const GLvoid *) (4 * sizeof(GLfloat)));
    glEnableVertexAttribArray(m_mixTexCoord);
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
            "    float personWeight = smoothstep(0.3, 0.7, maskColor.r);\n"
            "    gl_FragColor = mix(backgroundColor, inputColor, personWeight);\n"
            "}\n";

    return fragmentShader;
}
