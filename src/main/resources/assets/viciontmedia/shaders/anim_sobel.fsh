#version 330

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 center = texture(InSampler, texCoord);

    // Fade in over ~1 second using GameTime (ticks * 20 per second)
    // GameTime increments by 1/20 per tick, so multiply by 20 to get seconds
    float fade = clamp(GameTime * 20.0 * 2.0, 0.0, 1.0);

    vec2 oneTexel = 1.0 / InSize;

    vec4 left   = texture(InSampler, texCoord - vec2(oneTexel.x, 0.0));
    vec4 right  = texture(InSampler, texCoord + vec2(oneTexel.x, 0.0));
    vec4 up     = texture(InSampler, texCoord - vec2(0.0, oneTexel.y));
    vec4 down   = texture(InSampler, texCoord + vec2(0.0, oneTexel.y));

    vec4 leftDiff  = center - left;
    vec4 rightDiff = center - right;
    vec4 upDiff    = center - up;
    vec4 downDiff  = center - down;

    vec4 total = clamp(leftDiff + rightDiff + upDiff + downDiff, 0.0, 1.0);

    fragColor = mix(center, vec4(total.rgb, 1.0), fade);
}