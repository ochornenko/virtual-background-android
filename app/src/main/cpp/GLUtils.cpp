#include "GLUtils.h"
#include "Log.h"

#include <cstdlib>

static auto CheckGlError(const char *op) -> void {
    for (auto error = glGetError(); error; error = glGetError()) {
        LOGI("after %s() glError (0x%x)\n", op, error);
    }
}

static auto LoadShader(GLenum shaderType, const char *pSource) -> GLuint {
    GLuint shader = glCreateShader(shaderType);
    if (shader) {
        glShaderSource(shader, 1, &pSource, nullptr);
        glCompileShader(shader);
        GLint compiled = 0;
        glGetShaderiv(shader, GL_COMPILE_STATUS, &compiled);
        if (!compiled) {
            GLint infoLen = 0;
            glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &infoLen);
            if (infoLen) {
                char *buf = (char *) malloc((size_t) infoLen);
                if (buf) {
                    glGetShaderInfoLog(shader, infoLen, nullptr, buf);
                    LOGE("Could not compile shader %d:\n%s\n", shaderType, buf);
                    free(buf);
                }
                glDeleteShader(shader);
                shader = 0;
            }
        }
    }
    return shader;
}

auto CreateProgram(const char *pVertexSource, const char *pFragmentSource) -> GLuint {
    auto vertexShader = LoadShader(GL_VERTEX_SHADER, pVertexSource);
    if (!vertexShader) return 0;

    auto pixelShader = LoadShader(GL_FRAGMENT_SHADER, pFragmentSource);
    if (!pixelShader) return 0;

    GLuint program = glCreateProgram();
    if (program) {
        glAttachShader(program, vertexShader);
        CheckGlError("glAttachShader");
        glAttachShader(program, pixelShader);
        CheckGlError("glAttachShader");
        glLinkProgram(program);
        GLint linkStatus = GL_FALSE;
        glGetProgramiv(program, GL_LINK_STATUS, &linkStatus);

        glDetachShader(program, vertexShader);
        glDeleteShader(vertexShader);
        vertexShader = 0;
        glDetachShader(program, pixelShader);
        glDeleteShader(pixelShader);
        pixelShader = 0;
        if (linkStatus != GL_TRUE) {
            GLint bufLength = 0;
            glGetProgramiv(program, GL_INFO_LOG_LENGTH, &bufLength);
            if (bufLength) {
                char *buf = (char *) malloc((size_t) bufLength);
                if (buf) {
                    glGetProgramInfoLog(program, bufLength, nullptr, buf);
                    LOGE("Could not link program:\n%s\n", buf);
                    free(buf);
                }
            }

            glDeleteProgram(program);
            program = 0;
        }
    }
    return program;
}

auto DeleteProgram(GLuint &program) -> void {
    if (program) {
        glUseProgram(0);
        glDeleteProgram(program);
        program = 0;
    }
}

auto VertexData() -> const GLfloat * {
    static const GLfloat vertexData[] = {
            -1.0f, -1.0f, 0.0, 1.0, 0.0f, 0.0f,
            +1.0f, -1.0f, 0.0, 1.0, 1.0f, 0.0f,
            -1.0f, +1.0f, 0.0, 1.0, 0.0f, 1.0f,
            +1.0f, +1.0f, 0.0, 1.0, 1.0f, 1.0f,
    };

    return vertexData;
}

auto VertexIndices() -> const GLushort * {
    static const GLushort vertexIndices[] = {
            0, 1, 2, 3
    };

    return vertexIndices;
}
