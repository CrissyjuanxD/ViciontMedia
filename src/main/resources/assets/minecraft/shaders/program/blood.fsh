#version 120

uniform sampler2D DiffuseSampler;
varying vec2 texCoord;

void main() {
    // 1. Obtenemos el color original
    vec4 color = texture2D(DiffuseSampler, texCoord);

    // 2. Aumentamos el contraste para tener sombras más negras y marcadas
    float contrast = 1.3;
    color.rgb = (color.rgb - 0.5) * contrast + 0.5;
    color.rgb = clamp(color.rgb, 0.0, 1.0);

    // 3. Calculamos la luminosidad
    float luma = dot(color.rgb, vec3(0.299, 0.587, 0.114));
    vec3 desaturado = vec3(luma);

    // 4. Desaturamos fuertemente (75%)
    // Mantiene un rastro de los colores originales, pero la base se vuelve muy gris/negra
    color.rgb = mix(color.rgb, desaturado, 0.75);

    // 5. Tinte de Sangre Base
    // Disparamos el rojo al doble y aplastamos el verde y el azul a menos de la mitad
    vec3 bloodTint = vec3(2.0, 0.35, 0.35);
    color.rgb *= bloodTint;

    // 6. EL TOQUE MÁGICO PARA EL CIELO NOCTURNO:
    // Calculamos qué tan oscuro es el píxel (1.0 es negro total, 0.0 es blanco)
    float oscuridad = 1.0 - luma; 
    
    // Le "sumamos" rojo directamente a las zonas oscuras (como el cielo de noche)
    // Esto asegura que la noche brille en rojo en lugar de quedarse negra.
    color.r += oscuridad * 0.35; 
    color.g += oscuridad * 0.02; // Un toque minúsculo para que no sea un rojo plano
    color.b += oscuridad * 0.02;

    // 7. Oscurecemos un poquito el resultado general para dar ambiente de penumbra
    color.rgb *= 0.85;

    // Devolvemos el color final asegurándonos de que no pase los límites
    gl_FragColor = vec4(clamp(color.rgb, 0.0, 1.0), 1.0);
}