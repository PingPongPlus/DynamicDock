#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
layout(location = 0) in vec2 localUV;
layout(location = 1) in vec4 glassData;
layout(location = 2) in float shadowStrength;
layout(location = 0) out vec4 fragColor;
float roundedBox(vec2 p, vec2 halfSize, float radius) {
    vec2 q = abs(p) - halfSize + radius;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}
void main() {
    vec2 size = 1.0 / max(vec2(length(vec2(dFdx(localUV.x), dFdy(localUV.x))),
            length(vec2(dFdx(localUV.y), dFdy(localUV.y)))), vec2(0.00001));
    vec2 halfSize = size * .5;
    vec2 p = (localUV - .5) * size;
    float pixelScale = size.y / 22.0;
    float radius = min(halfSize.x, halfSize.y) * glassData.r;
    float distance = roundedBox(p, halfSize, radius);
    float aa = max(fwidth(distance), .75);
    vec2 screenNormal = normalize(vec2(dFdx(distance), dFdy(distance)) + vec2(.0001));
    float coverage = 1.0 - smoothstep(-aa, aa, distance);
    float shadowDistance = roundedBox(p - vec2(0.0, pixelScale * shadowStrength), halfSize, radius);
    float shadow = exp(-max(shadowDistance, 0.0) / (1.1 * pixelScale * max(shadowStrength, .001))) * .18 * shadowStrength;
    if (coverage < .001) {
        if (shadow < .005) discard;
        fragColor = vec4(vec3(.025), shadow * glassData.a);
        return;
    }
    vec2 texSize = vec2(textureSize(Sampler0, 0));
    vec2 screenUV = gl_FragCoord.xy / texSize;
    vec2 q = abs(p) - halfSize + radius;
    vec2 normal = normalize(max(q, vec2(0.0)) * sign(p) + vec2(.0001));
    if (q.x < 0.0 && q.y < 0.0) normal = q.x > q.y ? vec2(sign(p.x), 0.0) : vec2(0.0, sign(p.y));
    float rim = 1.0 - smoothstep(0.0, max(radius, 1.0), max(-distance, 0.0));
    vec2 bentUV = clamp(screenUV - screenNormal * rim * rim * pixelScale * 2.122 * glassData.g * 2.0 / texSize,
            .5 / texSize, 1.0 - .5 / texSize);
    vec3 color = texture(Sampler1, bentUV).rgb * .884;
    float edge = 1.0 - smoothstep(0.0, .7 * pixelScale, abs(distance + .45 * pixelScale));
    float light = pow(max(dot(normal, normalize(vec2(-.5, -.85))), 0.0), 2.0);
    float lowerRim = pow(max(dot(normal, normalize(vec2(.45, .9))), 0.0), 5.0);
    color = mix(color, vec3(.96), clamp(edge * (.20 + light * .32 + lowerRim * .14 + .01) * glassData.b * 2.0, 0.0, 1.0));
    color += exp(-pow((localUV.y - .08) / .20, 2.0)) * .017 * glassData.b * 2.0;
    fragColor = vec4(clamp(color, 0.0, 1.0), coverage * glassData.a);
}
