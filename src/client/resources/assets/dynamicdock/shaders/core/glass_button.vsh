#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;
layout(location = 0) out vec2 localUV;
layout(location = 1) out vec4 glassData;
layout(location = 2) out float shadowStrength;
void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position.xy, 0.0, 1.0);
    localUV = UV0;
    glassData = vec4(Color.rgb, Color.a * ColorModulator.a);
    shadowStrength = Position.z;
}
