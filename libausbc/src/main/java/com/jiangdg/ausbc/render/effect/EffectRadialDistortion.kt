package com.jiangdg.ausbc.render.effect

import android.content.Context
import android.opengl.GLES20
import com.jiangdg.ausbc.R

/** Corrige en temps réel la distorsion radiale de type fisheye. */
class EffectRadialDistortion(
    context: Context,
    coefficient: Float = 0f
) : AbstractEffect(context) {
    @Volatile
    private var radialCoefficient = coefficient.coerceIn(MIN_COEFFICIENT, MAX_COEFFICIENT)
    private var coefficientHandle = -1
    private var aspectRatioHandle = -1

    override fun getId(): Int = ID

    override fun getClassifyId(): Int = CLASSIFY_ID_RADIAL_DISTORTION

    override fun getVertexSourceId(): Int = R.raw.base_vertex

    override fun getFragmentSourceId(): Int = R.raw.effect_radial_distortion_fragment

    override fun init() {
        coefficientHandle = GLES20.glGetUniformLocation(mProgram, "uRadialCoefficient")
        aspectRatioHandle = GLES20.glGetUniformLocation(mProgram, "uAspectRatio")
    }

    override fun beforeDraw() {
        val aspectRatio = if (mHeight > 0) mWidth.toFloat() / mHeight.toFloat() else 1f
        GLES20.glUniform1f(coefficientHandle, radialCoefficient)
        GLES20.glUniform1f(aspectRatioHandle, aspectRatio)
    }

    fun setCoefficient(value: Float) {
        radialCoefficient = value.coerceIn(MIN_COEFFICIENT, MAX_COEFFICIENT)
    }

    companion object {
        const val ID = 500
        const val CLASSIFY_ID_RADIAL_DISTORTION = 50
        const val MIN_COEFFICIENT = -0.40f
        const val MAX_COEFFICIENT = 0f
    }
}
