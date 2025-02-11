#pragma once

#include <GLES2/gl2.h>

auto CreateProgram(const char *pVertexSource, const char *pFragmentSource) -> GLuint;

auto DeleteProgram(GLuint &program) -> void;

auto VertexData() -> const GLfloat*;

auto VertexIndices() -> const GLushort*;
