#version 330

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 oneTexel = 1.0 / InSize;

    vec3 ConvergeX = vec3(-1.0, 0.0, 0.5);
    vec3 ConvergeY = vec3(0.0, -1.0, 0.5);
    vec3 RadialConvergeX = vec3(1.0, 1.0, 1.0);
    vec3 RadialConvergeY = vec3(1.0, 1.0, 1.0);

    vec3 CoordX = vec3(texCoord.x) * RadialConvergeX;
    vec3 CoordY = vec3(texCoord.y) * RadialConvergeY;
    CoordX += ConvergeX * oneTexel.x - (RadialConvergeX - 1.0) * 0.5;
    CoordY += ConvergeY * oneTexel.y - (RadialConvergeY - 1.0) * 0.5;

    float RedValue   = texture(InSampler, vec2(CoordX.x, CoordY.x)).r;
    float GreenValue = texture(InSampler, vec2(CoordX.y, CoordY.y)).g;
    float BlueValue  = texture(InSampler, vec2(CoordX.z, CoordY.z)).b;

    fragColor = vec4(RedValue, GreenValue, BlueValue, 1.0);
}
