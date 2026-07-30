#version 120

uniform sampler2D DiffuseSampler;

varying vec2 texCoord;
varying vec2 oneTexel;

uniform vec2 InSize;

uniform float Time;
uniform vec2 Frequency;
uniform vec2 WobbleAmount;

vec3 hue(float h) {
    float r = abs(h * 6.0 - 3.0) - 1.0;
    float g = 2.0 - abs(h * 6.0 - 2.0);
    float b = 2.0 - abs(h * 6.0 - 4.0);
    return clamp(vec3(r,g,b), 0.0, 1.0);
}

vec3 HSVtoRGB(vec3 hsv) {
    return ((hue(hsv.x) - 1.0) * hsv.y + 1.0) * hsv.z;
}

vec3 RGBtoHSV(vec3 rgb) {
    vec3 hsv = vec3(0.0);
    hsv.z = max(rgb.r, max(rgb.g, rgb.b));
    float minVal = min(rgb.r, min(rgb.g, rgb.b));
    float c = hsv.z - minVal;
    if (c != 0.0) {
        hsv.y = c / hsv.z;
        vec3 delta = (hsv.z - rgb) / c;
        delta.rgb -= delta.brg;
        delta.rg += vec2(2.0, 4.0);
        if (rgb.r >= hsv.z) {
            hsv.x = delta.b;
        } else if (rgb.g >= hsv.z) {
            hsv.x = delta.r;
        } else {
            hsv.x = delta.g;
        }
        hsv.x = fract(hsv.x / 6.0);
    }
    return hsv;
}

void main() {
    // Ondulaciones muy bajas (* 0.15), idéntico a wobblelava
    float xOffset = sin(texCoord.y * Frequency.x + Time * 3.1415926535 * 2.0) * WobbleAmount.x * 0.15;
    float yOffset = cos(texCoord.x * Frequency.y + Time * 3.1415926535 * 2.0) * WobbleAmount.y * 0.15;
    vec2 offset = vec2(xOffset, yOffset);

    vec4 rgb = texture2D(DiffuseSampler, texCoord + offset);
    vec3 hsv = RGBtoHSV(rgb.rgb);

    // EL TRUCO FINAL: Usamos un péndulo (sin) que se sincroniza perfectamente con el reinicio del reloj.
    // Pasa por TODOS los colores (0.0 a 1.0) y regresa, haciendo la transición invisible y continua.
    hsv.x = (sin((hsv.x + Time) * 3.1415926535 * 1.0) * 0.5 + 0.5);

    gl_FragColor = vec4(HSVtoRGB(hsv), 1.0);
}