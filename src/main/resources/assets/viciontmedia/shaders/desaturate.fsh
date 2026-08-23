#version 330

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 InTexel = texture(InSampler, texCoord);

    vec3 Gray = vec3(0.3, 0.59, 0.11);
    float Saturation = 0.0;

    float Luma = dot(InTexel.rgb, Gray);
    vec3 Chroma = InTexel.rgb - Luma;
    vec3 OutColor = Chroma * Saturation + Luma;

    fragColor = vec4(OutColor, 1.0);
}
