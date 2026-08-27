/*
 * Copyright 2017-2023 Jiangdg
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jiangdg.ausbc.render.internal

import android.content.Context
import android.opengl.EGLContext
import android.view.Surface
import com.jiangdg.ausbc.R
import com.jiangdg.ausbc.render.env.EGLEvn

/** Inherit from AbstractFboRender
 *      render data to EGL from fbo and encode it
 *
 * @author Created by jiangdg on 2021/12/27
 */
class EncodeRender(
    context: Context,
    private val sourceAspectRatio: Float
): AbstractRender(context) {

    private var mEgl: EGLEvn? = null

    fun initEGLEvn(glContext: EGLContext) {
        mEgl = EGLEvn()
        mEgl?.initEgl(glContext)
    }

    fun setupSurface(surface: Surface) {
        mEgl?.setupSurface(surface)
        mEgl?.eglMakeCurrent()
    }

    fun swapBuffers(timeStamp: Long) {
        mEgl?.setPresentationTime(timeStamp)
        mEgl?.swapBuffers()
    }

    override fun setSize(width: Int, height: Int) {
        super.setSize(width, height)
        val targetAspect = width.toFloat() / height.coerceAtLeast(1)
        var left = 0f
        var right = 1f
        var bottom = 0f
        var top = 1f
        if (sourceAspectRatio > targetAspect) {
            val visible = targetAspect / sourceAspectRatio
            left = (1f - visible) / 2f
            right = 1f - left
        } else if (sourceAspectRatio < targetAspect) {
            val visible = sourceAspectRatio / targetAspect
            bottom = (1f - visible) / 2f
            top = 1f - bottom
        }
        mTriangleVertices.clear()
        mTriangleVertices.put(floatArrayOf(
            -1.0f, -1.0f, 0f, left, bottom,
             1.0f, -1.0f, 0f, right, bottom,
            -1.0f,  1.0f, 0f, left, top,
             1.0f,  1.0f, 0f, right, top
        )).position(0)
    }

    override fun clear() {
        mEgl?.releaseElg()
        mEgl = null
    }

    override fun getVertexSourceId(): Int = R.raw.base_vertex

    override fun getFragmentSourceId(): Int = R.raw.base_fragment
}
