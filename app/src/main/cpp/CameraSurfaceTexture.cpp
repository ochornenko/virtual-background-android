#include "CameraSurfaceTexture.h"
#include "GLUtils.h"
#include "Log.h"

auto CameraSurfaceTexture::create() -> std::unique_ptr<CameraSurfaceTexture> {
    auto processor = std::make_unique<CameraSurfaceTexture>();
    return std::move(processor);
}

CameraSurfaceTexture::CameraSurfaceTexture()
        : m_pProcessor(std::make_unique<CameraVirtualBackgroundProcessor>()),
          m_width(0),
          m_height(0),
          m_inputTexture(0),
          m_framebuffer(0),
          m_vertexBuffer(0),
          m_program(0),
          m_position(0),
          m_texCoord(0),
          m_transformMatrix(0),
          m_rotationMatrix(0) {
}

CameraSurfaceTexture::~CameraSurfaceTexture() {
    if (m_inputTexture != 0) {
        glDeleteTextures(1, &m_inputTexture);
        m_inputTexture = 0;
    }

    if (m_framebuffer != 0) {
        glDeleteFramebuffers(1, &m_framebuffer);
        m_framebuffer = 0;
    }

    if (m_vertexBuffer != 0) {
        glDeleteBuffers(1, &m_vertexBuffer);
        m_vertexBuffer = 0;
    }

    if (m_program != 0) {
        DeleteProgram(m_program);
    }

    m_pProcessor.reset();
}

auto CameraSurfaceTexture::Initialize(AAssetManager *assetManager, GLuint inputTexture,
                                      GLuint outputTexture) -> void {
    m_inputTexture = inputTexture;

    glBindTexture(GL_TEXTURE_EXTERNAL_OES, inputTexture);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

    glGenBuffers(1, &m_vertexBuffer);
    glBindBuffer(GL_ARRAY_BUFFER, m_vertexBuffer);
    glBufferData(GL_ARRAY_BUFFER, 24 * sizeof(GLfloat), VertexData(), GL_STATIC_DRAW);

    glGenFramebuffers(1, &m_framebuffer);

    m_program = CreateProgram(VertexShaderCode(), FragmentShaderCode());

    glUseProgram(m_program);

    m_position = glGetAttribLocation(m_program, "aPosition");
    m_texCoord = glGetAttribLocation(m_program, "aTexCoord");
    m_transformMatrix = glGetUniformLocation(m_program, "uTransformMatrix");
    m_rotationMatrix = glGetUniformLocation(m_program, "uRotationMatrix");

    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);

    m_pProcessor->Initialize(assetManager, outputTexture);
}

auto CameraSurfaceTexture::SetParams(int32_t width, int32_t height,
                                     GLuint backgroundTexture) -> void {
    m_width = width;
    m_height = height;

    m_pProcessor->SetParams(width, height, backgroundTexture, m_framebuffer);
}

auto CameraSurfaceTexture::UpdateTexImage(float *transformMatrix,
                                          float *rotationMatrix) const -> void {
    glViewport(0, 0, m_width, m_height);

    glBindTexture(GL_TEXTURE_2D, 0);
    glBindFramebuffer(GL_FRAMEBUFFER, m_framebuffer);

    glDisable(GL_BLEND);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, m_inputTexture);

    glUseProgram(m_program);
    glUniformMatrix4fv(m_transformMatrix, 1, GL_FALSE, transformMatrix);
    glUniformMatrix4fv(m_rotationMatrix, 1, GL_FALSE, rotationMatrix);
    glVertexAttribPointer(m_position, 4, GL_FLOAT, GL_FALSE, 6 * sizeof(GLfloat),
                          (const GLvoid *) (0 * sizeof(GLfloat)));
    glEnableVertexAttribArray(m_position);
    glVertexAttribPointer(m_texCoord, 2, GL_FLOAT, GL_FALSE, 6 * sizeof(GLfloat),
                          (const GLvoid *) (4 * sizeof(GLfloat)));
    glEnableVertexAttribArray(m_texCoord);
    glBindBuffer(GL_ARRAY_BUFFER, m_vertexBuffer);
    glDrawElements(GL_TRIANGLE_STRIP, 4, GL_UNSIGNED_SHORT, VertexIndices());

    m_pProcessor->Process(m_width, m_height, m_vertexBuffer);
}

auto CameraSurfaceTexture::VertexShaderCode() -> const char * {
    static const char vertexShader[] =
            "uniform mat4 uTransformMatrix;\n"
            "uniform mat4 uRotationMatrix;\n"
            "attribute vec4 aPosition;\n"
            "attribute vec4 aTexCoord;\n"
            "varying vec2 vTexCoord;\n"
            "void main() {\n"
            "    gl_Position = uRotationMatrix * aPosition;\n"
            "    vTexCoord = (uTransformMatrix * aTexCoord).xy;\n"
            "}\n";

    return vertexShader;
}

auto CameraSurfaceTexture::FragmentShaderCode() -> const char * {
    static const char fragmentShader[] =
            "#extension GL_OES_EGL_image_external:require\n"
            "precision mediump float;\n"
            "uniform samplerExternalOES uTexture;\n"
            "varying vec2 vTexCoord;\n"
            "void main() {\n"
            "    gl_FragColor = texture2D(uTexture, vTexCoord);\n"
            "}\n";

    return fragmentShader;
}
