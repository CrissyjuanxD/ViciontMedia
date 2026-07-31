#version 120

uniform sampler2D DiffuseSampler;
uniform float Fade; // Nuestro valor de transición (0.0 = Normal, 1.0 = Sobel)

varying vec2 texCoord;
varying vec2 oneTexel;

void main(){
    // Color original del juego (sin filtros)
    vec4 center = texture2D(DiffuseSampler, texCoord);

    // Cálculos del Sobel
    vec4 left   = texture2D(DiffuseSampler, texCoord - vec2(oneTexel.x, 0.0));
    vec4 right  = texture2D(DiffuseSampler, texCoord + vec2(oneTexel.x, 0.0));
    vec4 up     = texture2D(DiffuseSampler, texCoord - vec2(0.0, oneTexel.y));
    vec4 down   = texture2D(DiffuseSampler, texCoord + vec2(0.0, oneTexel.y));

    vec4 leftDiff  = center - left;
    vec4 rightDiff = center - right;
    vec4 upDiff    = center - up;
    vec4 downDiff  = center - down;

    vec4 total = clamp(leftDiff + rightDiff + upDiff + downDiff, 0.0, 1.0);

    // Aquí ocurre la magia: Mezclamos el original con el efecto según el valor de 'Fade'
    gl_FragColor = mix(center, vec4(total.rgb, 1.0), Fade);
}