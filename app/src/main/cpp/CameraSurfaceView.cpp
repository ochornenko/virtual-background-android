#include "CameraSurfaceView.h"
#include "GLUtils.h"
#include "Log.h"

auto CameraSurfaceView::create() -> std::unique_ptr<CameraSurfaceView> {
    auto processor = std::make_unique<CameraSurfaceView>();
    return std::move(processor);
}

CameraSurfaceView::CameraSurfaceView()
        : m_surfaceWidth(0),
          m_surfaceHeight(0),
          m_vertexBuffer(0),
          m_program(0),
          m_position(0),
          m_texCoord(0) {
}

CameraSurfaceView::~CameraSurfaceView() {
    if (m_vertexBuffer != 0) {
        glDeleteBuffers(1, &m_vertexBuffer);
        m_vertexBuffer = 0;
    }
}

auto CameraSurfaceView::OnSurfaceCreated() -> void {
    glGenBuffers(1, &m_vertexBuffer);
    glBindBuffer(GL_ARRAY_BUFFER, m_vertexBuffer);
    glBufferData(GL_ARRAY_BUFFER, 24 * sizeof(GLfloat), VertexData(), GL_STATIC_DRAW);

    m_program = CreateProgram(VertexShaderCode(), FragmentShaderCode());

    if (!m_program) {
        LOGE("Could not create program.");
        return;
    }

    glUseProgram(m_program);

    m_position = glGetAttribLocation(m_program, "aPosition");
    m_texCoord = glGetAttribLocation(m_program, "aTexCoord");

    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);

    if (glGetError() != GL_NO_ERROR) {
        DeleteProgram(m_program);
        LOGE("Could not create program.");
        return;
    }
}

auto CameraSurfaceView::OnSurfaceChanged(int32_t width, int32_t height) -> void {
    m_surfaceWidth = width;
    m_surfaceHeight = height;
}

auto CameraSurfaceView::OnDrawFrame() -> void {
    glBindFramebuffer(GL_FRAMEBUFFER, 0);

    glClearColor(0.0, 0.0, 0.0, 1.0);
    glClear(GL_COLOR_BUFFER_BIT);
}

auto CameraSurfaceView::DrawTexture(GLuint texture, int32_t textureWidth,
                                    int32_t textureHeight) const -> void {
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glBindTexture(GL_TEXTURE_2D, texture);

    int viewportX = 0;
    int viewportY = 0;
    int viewportWidth = m_surfaceWidth;
    int viewportHeight = m_surfaceHeight;

    int candidateWidth = (int) (((float) textureWidth / (float) textureHeight) *
                                (float) m_surfaceHeight);
    int candidateHeight = (int) (((float) textureHeight / (float) textureWidth) *
                                 (float) m_surfaceWidth);

    if (candidateWidth > m_surfaceWidth) {
        viewportX = -1 * (candidateWidth - m_surfaceWidth) / 2;
        viewportWidth = candidateWidth;
    } else if (candidateHeight > m_surfaceHeight) {
        viewportY = -1 * (candidateHeight - m_surfaceHeight) / 2;
        viewportHeight = candidateHeight;
    }

    glViewport(viewportX, viewportY, viewportWidth, viewportHeight);

    glUseProgram(m_program);
    glVertexAttribPointer(m_position, 4, GL_FLOAT, GL_FALSE, 6 * sizeof(GLfloat),
                          (const GLvoid *) (0 * sizeof(GLfloat)));
    glEnableVertexAttribArray(m_position);
    glVertexAttribPointer(m_texCoord, 2, GL_FLOAT, GL_FALSE, 6 * sizeof(GLfloat),
                          (const GLvoid *) (4 * sizeof(GLfloat)));
    glEnableVertexAttribArray(m_texCoord);
    glBindBuffer(GL_ARRAY_BUFFER, m_vertexBuffer);
    glDrawElements(GL_TRIANGLE_STRIP, 4, GL_UNSIGNED_SHORT, VertexIndices());
}

const char *CameraSurfaceView::VertexShaderCode() {
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

const char *CameraSurfaceView::FragmentShaderCode() {
    static const char fragmentShader[] =
            "precision mediump float;\n"
            "uniform sampler2D uTexture;\n"
            "varying vec2 vTexCoord;\n"
            "void main() {\n"
            "    vec4 color = texture2D(uTexture, vTexCoord);\n"
            "    gl_FragColor = color;\n"
            "}\n";

    return fragmentShader;
}
