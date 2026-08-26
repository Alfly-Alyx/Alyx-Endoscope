precision highp float;

uniform sampler2D uTextureSampler;
uniform float uRadialCoefficient;
uniform float uAspectRatio;
varying vec2 vTextureCoord;

void main()
{
    vec2 centered = vTextureCoord * 2.0 - 1.0;
    float aspect = max(uAspectRatio, 0.0001);
    vec2 radialPoint = vec2(centered.x * aspect, centered.y);

    float aspectSquared = aspect * aspect;
    float dominantRadiusSquared = max(aspectSquared, 1.0);
    float nearestEdgeRadiusSquared = min(aspectSquared, 1.0) / dominantRadiusSquared;
    float normalizedRadiusSquared = dot(radialPoint, radialPoint) / dominantRadiusSquared;

    // Le facteur de remplissage évite les bordures vides sur le côté le plus proche.
    float fillScale = max(1.0 + uRadialCoefficient * nearestEdgeRadiusSquared, 0.05);
    float radialScale = (1.0 + uRadialCoefficient * normalizedRadiusSquared) / fillScale;
    radialPoint *= radialScale;
    radialPoint.x /= aspect;

    vec2 sampleCoordinate = clamp(radialPoint * 0.5 + 0.5, 0.0, 1.0);
    gl_FragColor = texture2D(uTextureSampler, sampleCoordinate);
}
