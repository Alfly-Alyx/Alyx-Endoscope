precision highp float;
uniform sampler2D uTextureSampler;
uniform sampler2D uWatermarkSampler;
varying vec2 vTextureCoord;
void main()
{
    vec4 cameraColor = texture2D(uTextureSampler, vTextureCoord);
    vec4 watermarkColor = texture2D(
        uWatermarkSampler,
        vec2(vTextureCoord.x, 1.0 - vTextureCoord.y)
    );
    gl_FragColor = vec4(
        mix(cameraColor.rgb, watermarkColor.rgb, watermarkColor.a),
        cameraColor.a
    );
}
