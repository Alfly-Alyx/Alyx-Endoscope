/*
 * Copyright 2017-2022 Jiangdg
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
package com.jiangdg.demo

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.graphics.Typeface
import android.hardware.usb.UsbDevice
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.children
import androidx.core.widget.TextViewCompat
import androidx.constraintlayout.widget.ConstraintSet
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItems
import com.afollestad.materialdialogs.list.listItemsSingleChoice
import com.google.android.material.color.MaterialColors
import com.jiangdg.ausbc.MultiCameraClient
import com.jiangdg.ausbc.base.CameraFragment
import com.jiangdg.ausbc.callback.ICameraStateCallBack
import com.jiangdg.demo.databinding.FragmentDemoBinding
import com.jiangdg.ausbc.callback.ICaptureCallBack
import com.jiangdg.ausbc.camera.CameraUVC
import com.jiangdg.ausbc.render.effect.EffectBlackWhite
import com.jiangdg.ausbc.render.effect.EffectSoul
import com.jiangdg.ausbc.render.effect.EffectZoom
import com.jiangdg.ausbc.render.effect.bean.CameraEffect
import com.jiangdg.ausbc.utils.*
import com.jiangdg.ausbc.utils.bus.BusKey
import com.jiangdg.ausbc.utils.bus.EventBus
import com.jiangdg.utils.imageloader.ILoader
import com.jiangdg.utils.imageloader.ImageLoaders
import com.jiangdg.ausbc.widget.*
import com.jiangdg.demo.EffectListDialog.Companion.KEY_ANIMATION
import com.jiangdg.demo.EffectListDialog.Companion.KEY_FILTER
import com.jiangdg.utils.MMKVUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.*

/** CameraFragment Usage Demo
 *
 * @author Created by jiangdg on 2022/1/28
 */
class DemoFragment : CameraFragment(), View.OnClickListener, CaptureMediaView.OnViewClickListener {
    private var isCapturingVideo: Boolean = false
    private var mRecTimer: Timer? = null
    private var mRecSeconds = 0
    private var mRecMinute = 0
    private var mRecHours = 0

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@registerForActivityResult
        try {
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                FLAG_GRANT_READ_URI_PERMISSION or FLAG_GRANT_WRITE_URI_PERMISSION
            )
            requireContext().getSharedPreferences(STORAGE_PREFS, 0)
                .edit().putString(KEY_MEDIA_FOLDER, uri.toString()).apply()
            val folderName = DocumentFile.fromTreeUri(requireContext(), uri)?.name ?: uri.lastPathSegment
            ToastUtils.show(getString(R.string.media_folder_selected, folderName ?: "Dossier sélectionné"))
        } catch (e: Exception) {
            Logger.e(TAG, "Unable to persist media folder", e)
            ToastUtils.show(R.string.media_folder_error)
        }
    }

    private val mCameraModeTabMap = mapOf(
        CaptureMediaView.CaptureMode.MODE_CAPTURE_PIC to R.id.takePictureModeTv,
        CaptureMediaView.CaptureMode.MODE_CAPTURE_VIDEO to R.id.recordVideoModeTv
    )

    private val mEffectDataList by lazy {
        arrayListOf(
            CameraEffect.NONE_FILTER,
            CameraEffect(
                EffectBlackWhite.ID,
                "Noir et blanc",
                CameraEffect.CLASSIFY_ID_FILTER,
                effect = EffectBlackWhite(requireActivity()),
                coverResId = R.mipmap.filter0
            ),
            CameraEffect.NONE_ANIMATION,
            CameraEffect(
                EffectZoom.ID,
                "Zoom",
                CameraEffect.CLASSIFY_ID_ANIMATION,
                effect = EffectZoom(requireActivity()),
                coverResId = R.mipmap.filter2
            ),
            CameraEffect(
                EffectSoul.ID,
                "Fantôme",
                CameraEffect.CLASSIFY_ID_ANIMATION,
                effect = EffectSoul(requireActivity()),
                coverResId = R.mipmap.filter1
            ),
        )
    }

    private val mTakePictureTipView: TipView by lazy {
        mViewBinding.takePictureTipViewStub.inflate() as TipView
    }

    private val mMainHandler: Handler by lazy {
        Handler(Looper.getMainLooper()) {
            when(it.what) {
                WHAT_START_TIMER -> {
                    if (mRecSeconds % 2 != 0) {
                        mViewBinding.recStateIv.visibility = View.VISIBLE
                    } else {
                        mViewBinding.recStateIv.visibility = View.INVISIBLE
                    }
                    mViewBinding.recTimeTv.text = calculateTime(mRecSeconds, mRecMinute)
                }
                WHAT_STOP_TIMER -> {
                    mViewBinding.modeSwitchLayout.visibility = View.VISIBLE
                    mViewBinding.toolbarGroup.visibility = View.VISIBLE
                    mViewBinding.albumPreviewIv.visibility = View.VISIBLE
                    mViewBinding.recTimerLayout.visibility = View.GONE
                    mViewBinding.recTimeTv.text = calculateTime(0, 0)
                }
            }
            true
        }
    }

    private var mCameraMode = CaptureMediaView.CaptureMode.MODE_CAPTURE_PIC

    private lateinit var mViewBinding: FragmentDemoBinding

    override fun initView() {
        super.initView()
        applySelectedAppearance()
        mViewBinding.cameraTypeBtn.setOnClickListener(this)
        mViewBinding.settingsBtn.setOnClickListener(this)
        mViewBinding.themeBtn.setOnClickListener(this)
        mViewBinding.headerSettingsBtn.setOnClickListener(this)
        mViewBinding.resolutionBtn.setOnClickListener(this)
        mViewBinding.albumPreviewIv.setOnClickListener(this)
        mViewBinding.captureBtn.setOnViewClickListener(this)
        switchLayoutClick()
    }

    /** Rend les trois apparences réellement distinctes, au-delà de la palette DayNight. */
    private fun applySelectedAppearance() {
        val context = requireContext()
        val selected = context.getSharedPreferences(MainActivity.APPEARANCE_PREFS, 0)
            .getInt(MainActivity.KEY_APPEARANCE, MainActivity.APPEARANCE_ATELIER)
        val surface = MaterialColors.getColor(mViewBinding.root, com.google.android.material.R.attr.colorSurface)
        val accent = MaterialColors.getColor(mViewBinding.root, com.google.android.material.R.attr.colorPrimary)
        val onSurface = MaterialColors.getColor(mViewBinding.root, com.google.android.material.R.attr.colorOnSurface)
        val outline = ColorUtils.setAlphaComponent(onSurface, if (selected == MainActivity.APPEARANCE_VISEE) 90 else 38)
        val density = resources.displayMetrics.density
        val (cornerDp, marginDp, strokeDp) = when (selected) {
            MainActivity.APPEARANCE_VISEE -> Triple(30f, 16, 1.5f)
            MainActivity.APPEARANCE_WORKSHOP -> Triple(24f, 20, 1f)
            else -> Triple(16f, 10, 1f)
        }
        fun panel(view: View, corners: Float = cornerDp, stroke: Float = strokeDp) {
            view.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = corners * density
                setColor(surface)
                if (stroke > 0f) {
                    setStroke((stroke * density).toInt().coerceAtLeast(1), outline)
                }
            }
            // Ces vues servent de fonds : une élévation les placerait devant les boutons voisins.
            view.elevation = 0f
            if (view === mViewBinding.cameraViewContainer) {
                view.clipToOutline = true
            }
        }
        panel(
            mViewBinding.toolbarBg,
            if (selected == MainActivity.APPEARANCE_ATELIER) 0f else cornerDp,
            if (selected == MainActivity.APPEARANCE_ATELIER) 0f else strokeDp
        )
        panel(mViewBinding.cameraViewContainer)
        panel(mViewBinding.controlPanelLayout, if (selected == MainActivity.APPEARANCE_ATELIER) 16f else cornerDp)
        mViewBinding.headerBg.setBackgroundColor(
            MaterialColors.getColor(mViewBinding.root, android.R.attr.colorBackground)
        )

        val appTitle = getString(R.string.app_name)
        mViewBinding.headerTitleTv.text = if (selected == MainActivity.APPEARANCE_VISEE) {
            SpannableString(appTitle).apply {
                setSpan(
                    ForegroundColorSpan(accent), 0,
                    appTitle.indexOf(' ').takeIf { it > 0 } ?: appTitle.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        } else appTitle

        val visee = selected == MainActivity.APPEARANCE_VISEE
        val workshop = selected == MainActivity.APPEARANCE_WORKSHOP
        mViewBinding.cameraStatusTv.visibility = if (visee) View.VISIBLE else View.GONE
        if (visee) {
            mViewBinding.cameraStatusTv.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 22f * density
                setColor(ColorUtils.setAlphaComponent(surface, 238))
                setStroke(density.toInt().coerceAtLeast(1), outline)
            }
            setViseeCameraStatusText(false)
        }
        val regularTint = ColorStateList.valueOf(onSurface)
        val accentTint = ColorStateList.valueOf(accent)
        mViewBinding.settingsBtn.imageTintList = if (visee || workshop) accentTint else regularTint
        mViewBinding.cameraTypeBtn.imageTintList = if (workshop) accentTint else regularTint
        mViewBinding.themeBtn.imageTintList = if (visee || workshop) accentTint else regularTint
        mViewBinding.resolutionBtn.imageTintList = if (visee || workshop) accentTint else regularTint

        if (visee) {
            val actionSurface = ColorUtils.blendARGB(surface, onSurface, 0.075f)
            fun floatingButton(view: View, radiusDp: Float = 16f) {
                view.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = radiusDp * density
                    setColor(actionSurface)
                    setStroke(density.toInt().coerceAtLeast(1), outline)
                }
            }
            mViewBinding.headerSettingsBtn.visibility = View.VISIBLE
            mViewBinding.headerSettingsBtn.imageTintList = regularTint
            floatingButton(mViewBinding.headerSettingsBtn)

            listOf(
                mViewBinding.settingsBtn,
                mViewBinding.cameraTypeBtn,
                mViewBinding.themeBtn,
                mViewBinding.resolutionBtn
            ).forEach { floatingButton(it, 18f) }
            mViewBinding.toolbarBg.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = cornerDp * density
                setColor(ColorUtils.setAlphaComponent(surface, 232))
                setStroke(density.toInt().coerceAtLeast(1), outline)
            }
            listOf(
                mViewBinding.settingsLabelTv,
                mViewBinding.cameraLabelTv,
                mViewBinding.themeLabelTv,
                mViewBinding.resolutionLabelTv
            ).forEach { it.alpha = 0.9f }
            mViewBinding.albumPreviewIv.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16f * density
                setColor(actionSurface)
                setStroke(density.toInt().coerceAtLeast(1), outline)
            }
            val albumPadding = (8 * density).toInt()
            mViewBinding.albumPreviewIv.setPadding(albumPadding, albumPadding, albumPadding, albumPadding)
        } else if (workshop) {
            mViewBinding.headerSettingsBtn.apply {
                visibility = View.VISIBLE
                setImageResource(R.drawable.ic_palette)
                imageTintList = regularTint
                background = null
            }
            val actionCards = listOf(
                mViewBinding.folderActionCard,
                mViewBinding.cameraActionCard,
                mViewBinding.themeActionCard,
                mViewBinding.resolutionActionCard
            )
            actionCards.forEach { card ->
                card.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 20f * density
                    setColor(surface)
                    setStroke(density.toInt().coerceAtLeast(1), outline)
                }
            }
            mViewBinding.toolbarBg.background = null
            listOf(
                mViewBinding.settingsLabelTv,
                mViewBinding.cameraLabelTv,
                mViewBinding.themeLabelTv,
                mViewBinding.resolutionLabelTv
            ).forEach {
                it.alpha = 1f
                it.textSize = 13f
            }
            mViewBinding.cameraViewContainer.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = cornerDp * density
                setColor(ColorUtils.blendARGB(surface, onSurface, 0.035f))
                setStroke(density.toInt().coerceAtLeast(1), outline)
            }
            mViewBinding.uvcLogoIv.apply {
                setImageResource(R.drawable.ic_usb_endoscope)
                imageTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(onSurface, 118))
                strokeWidth = 0f
                scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            }
            mViewBinding.emptyCameraTitle.visibility = View.GONE
            mViewBinding.emptyCameraSubtitle.apply {
                text = getString(R.string.connect_usb_endoscope)
                textSize = 15f
                alpha = 0.82f
            }
            mViewBinding.albumPreviewIv.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(surface)
                setStroke(density.toInt().coerceAtLeast(1), outline)
            }
            val albumPadding = (9 * density).toInt()
            mViewBinding.albumPreviewIv.setPadding(albumPadding, albumPadding, albumPadding, albumPadding)
        } else {
            mViewBinding.headerSettingsBtn.visibility = View.GONE
            listOf(
                mViewBinding.folderActionCard,
                mViewBinding.cameraActionCard,
                mViewBinding.themeActionCard,
                mViewBinding.resolutionActionCard
            ).forEach { it.background = null }
        }

        listOf(mViewBinding.cameraViewContainer, mViewBinding.controlPanelLayout).forEach { view ->
            (view.layoutParams as? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams)?.let { params ->
                params.marginStart = marginDp * density.toInt()
                params.marginEnd = marginDp * density.toInt()
                view.layoutParams = params
            }
        }
        (mViewBinding.toolbarBg.layoutParams as? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams)?.let { params ->
            val toolbarMargin = when {
                visee -> (28 * density).toInt()
                workshop -> (16 * density).toInt()
                else -> 0
            }
            params.marginStart = toolbarMargin
            params.marginEnd = toolbarMargin
            mViewBinding.toolbarBg.layoutParams = params
        }
        applyAppearanceComposition(selected)
        mViewBinding.albumPreviewIv.setTheme(
            if (ColorUtils.calculateLuminance(surface) < 0.5) PreviewImageView.Theme.DARK
            else PreviewImageView.Theme.LIGHT
        )
        mViewBinding.captureBtn.setCaptureViewTheme(CaptureMediaView.CaptureViewTheme.THEME_BLUE)

        if (visee) {
            mViewBinding.modeSwitchLayout.background = null
            mViewBinding.photoVideoDivider.visibility = View.GONE
        } else {
            mViewBinding.modeSwitchLayout.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 28f * density
                setColor(ColorUtils.blendARGB(surface, onSurface, 0.035f))
                setStroke(density.toInt().coerceAtLeast(1), outline)
            }
            mViewBinding.photoVideoDivider.visibility = View.VISIBLE
        }
    }

    /**
     * Les propositions sont des compositions d'interface, pas uniquement des couleurs.
     * Atelier : composition claire et structurée, fidèle à la maquette de référence.
     * Visée : commandes superposées au viseur.
     * Workshop : commandes placées entre le viseur et le panneau de capture.
     */
    private fun applyAppearanceComposition(selected: Int) {
        val root = mViewBinding.root as androidx.constraintlayout.widget.ConstraintLayout
        val constraints = ConstraintSet().apply { clone(root) }
        when (selected) {
            MainActivity.APPEARANCE_VISEE -> {
                val density = resources.displayMetrics.density
                val dp: (Int) -> Int = { (it * density).toInt() }
                constraints.clear(R.id.cameraViewContainer, ConstraintSet.TOP)
                constraints.clear(R.id.cameraViewContainer, ConstraintSet.BOTTOM)
                constraints.connect(
                    R.id.cameraViewContainer, ConstraintSet.TOP,
                    R.id.headerBg, ConstraintSet.BOTTOM, dp(24)
                )
                constraints.connect(
                    R.id.cameraViewContainer, ConstraintSet.BOTTOM,
                    R.id.controlPanelLayout, ConstraintSet.TOP, dp(12)
                )
                constraints.clear(R.id.toolbarBg, ConstraintSet.TOP)
                constraints.clear(R.id.cameraStatusTv, ConstraintSet.TOP)
                constraints.connect(
                    R.id.cameraStatusTv, ConstraintSet.TOP,
                    R.id.headerBg, ConstraintSet.BOTTOM, dp(6)
                )
                constraints.connect(
                    R.id.toolbarBg, ConstraintSet.TOP,
                    R.id.cameraStatusTv, ConstraintSet.BOTTOM, dp(10)
                )
                constraints.clear(R.id.headerTitleTv, ConstraintSet.START)
                constraints.connect(R.id.headerTitleTv, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                constraints.connect(R.id.headerTitleTv, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                constraints.constrainHeight(R.id.headerBg, dp(72))
                constraints.clear(R.id.controlPanelLayout, ConstraintSet.BOTTOM)
                constraints.connect(
                    R.id.controlPanelLayout, ConstraintSet.BOTTOM,
                    ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, dp(12)
                )
                constraints.constrainHeight(R.id.controlPanelLayout, dp(180))
                constraints.constrainHeight(R.id.modeSwitchLayout, dp(48))
                constraints.constrainWidth(R.id.captureBtn, dp(84))
                constraints.constrainHeight(R.id.captureBtn, dp(84))
                constraints.constrainWidth(R.id.albumPreviewIv, dp(52))
                constraints.constrainHeight(R.id.albumPreviewIv, dp(52))
                // La barre reste en haut, par-dessus la zone caméra comme dans la maquette Visée.
                mViewBinding.toolbarBg.alpha = 1f
                mViewBinding.headerTitleTv.textSize = 22f
            }
            MainActivity.APPEARANCE_WORKSHOP -> {
                val density = resources.displayMetrics.density
                val dp: (Int) -> Int = { (it * density).toInt() }
                constraints.clear(R.id.toolbarBg, ConstraintSet.TOP)
                constraints.connect(
                    R.id.toolbarBg, ConstraintSet.BOTTOM,
                    R.id.controlPanelLayout, ConstraintSet.TOP, dp(10)
                )
                constraints.constrainHeight(R.id.toolbarBg, dp(96))
                constraints.clear(R.id.cameraViewContainer, ConstraintSet.TOP)
                constraints.clear(R.id.cameraViewContainer, ConstraintSet.BOTTOM)
                constraints.connect(
                    R.id.cameraViewContainer, ConstraintSet.TOP,
                    R.id.headerBg, ConstraintSet.BOTTOM, dp(6)
                )
                constraints.connect(
                    R.id.cameraViewContainer, ConstraintSet.BOTTOM,
                    R.id.toolbarBg, ConstraintSet.TOP, dp(12)
                )
                constraints.clear(R.id.brightnessSb, ConstraintSet.BOTTOM)
                constraints.connect(
                    R.id.brightnessSb, ConstraintSet.BOTTOM,
                    R.id.toolbarBg, ConstraintSet.TOP, dp(12)
                )
                constraints.constrainHeight(R.id.headerBg, dp(82))
                constraints.constrainWidth(R.id.headerLogoIv, dp(52))
                constraints.constrainHeight(R.id.headerLogoIv, dp(52))
                constraints.constrainHeight(R.id.controlPanelLayout, dp(180))
                constraints.constrainHeight(R.id.modeSwitchLayout, dp(54))
                constraints.constrainWidth(R.id.uvcLogoIv, dp(72))
                constraints.constrainHeight(R.id.uvcLogoIv, dp(72))
                constraints.constrainWidth(R.id.captureBtn, dp(88))
                constraints.constrainHeight(R.id.captureBtn, dp(88))
                constraints.constrainWidth(R.id.albumPreviewIv, dp(52))
                constraints.constrainHeight(R.id.albumPreviewIv, dp(52))
                mViewBinding.toolbarBg.alpha = 1f
                mViewBinding.headerTitleTv.textSize = 24f
            }
            else -> {
                val density = resources.displayMetrics.density
                val dp: (Int) -> Int = { (it * density).toInt() }
                constraints.clear(R.id.toolbarBg, ConstraintSet.TOP)
                constraints.clear(R.id.toolbarBg, ConstraintSet.START)
                constraints.clear(R.id.toolbarBg, ConstraintSet.END)
                constraints.connect(
                    R.id.toolbarBg, ConstraintSet.START,
                    R.id.headerTitleTv, ConstraintSet.END, dp(2)
                )
                constraints.connect(
                    R.id.toolbarBg, ConstraintSet.END,
                    ConstraintSet.PARENT_ID, ConstraintSet.END, dp(4)
                )
                constraints.connect(
                    R.id.toolbarBg, ConstraintSet.TOP,
                    R.id.headerBg, ConstraintSet.TOP, dp(3)
                )
                constraints.constrainWidth(R.id.toolbarBg, ConstraintSet.MATCH_CONSTRAINT)
                constraints.constrainHeight(R.id.toolbarBg, dp(70))

                constraints.clear(R.id.cameraViewContainer, ConstraintSet.TOP)
                constraints.clear(R.id.cameraViewContainer, ConstraintSet.BOTTOM)
                constraints.connect(
                    R.id.cameraViewContainer, ConstraintSet.TOP,
                    R.id.headerBg, ConstraintSet.BOTTOM, dp(8)
                )
                constraints.connect(
                    R.id.cameraViewContainer, ConstraintSet.BOTTOM,
                    R.id.controlPanelLayout, ConstraintSet.TOP, dp(8)
                )
                constraints.clear(R.id.controlPanelLayout, ConstraintSet.BOTTOM)
                constraints.connect(
                    R.id.controlPanelLayout, ConstraintSet.BOTTOM,
                    ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, dp(10)
                )
                constraints.constrainHeight(R.id.controlPanelLayout, dp(174))
                mViewBinding.toolbarBg.alpha = 1f
                mViewBinding.toolbarBg.background = null
                constraints.constrainHeight(R.id.headerBg, dp(76))
                constraints.constrainWidth(R.id.headerLogoIv, dp(36))
                constraints.constrainHeight(R.id.headerLogoIv, dp(36))
                mViewBinding.headerTitleTv.textSize = 17f
            }
        }
        constraints.applyTo(root)
        mViewBinding.headerLogoIv.visibility =
            if (selected == MainActivity.APPEARANCE_VISEE) View.GONE else View.VISIBLE
    }

    override fun initData() {
        super.initData()
        EventBus.with<Int>(BusKey.KEY_FRAME_RATE).observe(this, {
            mViewBinding.frameRateTv.text = getString(R.string.frame_rate, it)
        })

        EventBus.with<Boolean>(BusKey.KEY_RENDER_READY).observe(this, { ready ->
            if (! ready) return@observe
            getDefaultEffect()?.apply {
                when(getClassifyId()) {
                    CameraEffect.CLASSIFY_ID_FILTER -> {
                        // check if need to set anim
                        val animId = MMKVUtils.getInt(KEY_ANIMATION, -99)
                        if (animId != -99) {
                            mEffectDataList.find {
                                it.id == animId
                            }?.also {
                                if (it.effect != null) {
                                    addRenderEffect(it.effect!!)
                                }
                            }
                        }
                        // set effect
                        val filterId = MMKVUtils.getInt(KEY_FILTER, -99)
                        if (filterId != -99) {
                            removeRenderEffect(this)
                            mEffectDataList.find {
                                it.id == filterId
                            }?.also {
                                if (it.effect != null) {
                                    addRenderEffect(it.effect!!)
                                }
                            }
                            return@apply
                        }
                        MMKVUtils.set(KEY_FILTER, getId())
                    }
                    CameraEffect.CLASSIFY_ID_ANIMATION -> {
                        // check if need to set filter
                        val filterId = MMKVUtils.getInt(KEY_ANIMATION, -99)
                        if (filterId != -99) {
                            mEffectDataList.find {
                                it.id == filterId
                            }?.also {
                                if (it.effect != null) {
                                    addRenderEffect(it.effect!!)
                                }
                            }
                        }
                        // set anim
                        val animId = MMKVUtils.getInt(KEY_ANIMATION, -99)
                        if (animId != -99) {
                            removeRenderEffect(this)
                            mEffectDataList.find {
                                it.id == animId
                            }?.also {
                                if (it.effect != null) {
                                    addRenderEffect(it.effect!!)
                                }
                            }
                            return@apply
                        }
                        MMKVUtils.set(KEY_ANIMATION, getId())
                    }
                    else -> throw IllegalStateException("Unsupported classify")
                }
            }
        })
    }

    override fun onCameraState(
        self: MultiCameraClient.ICamera,
        code: ICameraStateCallBack.State,
        msg: String?
    ) {
        when (code) {
            ICameraStateCallBack.State.OPENED -> handleCameraOpened()
            ICameraStateCallBack.State.CLOSED -> handleCameraClosed()
            ICameraStateCallBack.State.ERROR -> handleCameraError(msg)
        }
    }

    private fun handleCameraError(msg: String?) {
        updateViseeCameraStatus(false)
        showEmptyCameraState()
        mViewBinding.frameRateTv.visibility = View.GONE
        ToastUtils.show(getString(R.string.camera_open_error, msg ?: getString(R.string.unknown_error)))
    }

    private fun handleCameraClosed() {
        updateViseeCameraStatus(false)
        showEmptyCameraState()
        mViewBinding.frameRateTv.visibility = View.GONE
        ToastUtils.show(R.string.camera_closed)
    }

    private fun handleCameraOpened() {
        updateViseeCameraStatus(true)
        mViewBinding.uvcLogoIv.visibility = View.GONE
        mViewBinding.emptyCameraTitle.visibility = View.GONE
        mViewBinding.emptyCameraSubtitle.visibility = View.GONE
        mViewBinding.frameRateTv.visibility = View.VISIBLE
        mViewBinding.brightnessSb.max = (getCurrentCamera() as? CameraUVC)?.getBrightnessMax() ?: 100
        mViewBinding.brightnessSb.progress = (getCurrentCamera() as? CameraUVC)?.getBrightness() ?: 0
        Logger.i(TAG, "max = ${mViewBinding.brightnessSb.max}, progress = ${mViewBinding.brightnessSb.progress}")
        mViewBinding.brightnessSb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                (getCurrentCamera() as? CameraUVC)?.setBrightness(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {

            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {

            }
        })
        ToastUtils.show(R.string.camera_opened)
    }

    private fun showEmptyCameraState() {
        val selected = requireContext()
            .getSharedPreferences(MainActivity.APPEARANCE_PREFS, 0)
            .getInt(MainActivity.KEY_APPEARANCE, MainActivity.APPEARANCE_ATELIER)
        mViewBinding.uvcLogoIv.visibility = View.VISIBLE
        mViewBinding.emptyCameraTitle.visibility =
            if (selected == MainActivity.APPEARANCE_WORKSHOP) View.GONE else View.VISIBLE
        mViewBinding.emptyCameraSubtitle.visibility = View.VISIBLE
    }

    private fun updateViseeCameraStatus(connected: Boolean) {
        if (!::mViewBinding.isInitialized) return
        val selected = requireContext().getSharedPreferences(MainActivity.APPEARANCE_PREFS, 0)
            .getInt(MainActivity.KEY_APPEARANCE, MainActivity.APPEARANCE_ATELIER)
        if (selected != MainActivity.APPEARANCE_VISEE) return
        setViseeCameraStatusText(connected)
    }

    private fun setViseeCameraStatusText(connected: Boolean) {
        val text = getString(if (connected) R.string.camera_connected else R.string.camera_disconnected)
        val accent = MaterialColors.getColor(
            mViewBinding.cameraStatusTv,
            com.google.android.material.R.attr.colorPrimary
        )
        mViewBinding.cameraStatusTv.text = SpannableString(text).apply {
            setSpan(ForegroundColorSpan(accent), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun switchLayoutClick() {
        mViewBinding.takePictureModeTv.setOnClickListener {
            setCameraMode(CaptureMediaView.CaptureMode.MODE_CAPTURE_PIC)
        }
        mViewBinding.recordVideoModeTv.setOnClickListener {
            setCameraMode(CaptureMediaView.CaptureMode.MODE_CAPTURE_VIDEO)
        }
        updateCameraModeSwitchUI()
        showRecentMedia()
    }

    private fun setCameraMode(mode: CaptureMediaView.CaptureMode) {
        if (mCameraMode == mode) return
        mCameraMode = mode
        updateCameraModeSwitchUI()
    }

    override fun getCameraView(): IAspectRatio {
        return AspectRatioTextureView(requireContext())
    }

    override fun getCameraViewContainer(): ViewGroup {
        return mViewBinding.cameraViewContainer
    }

    override fun getRootView(inflater: LayoutInflater, container: ViewGroup?): View {
        mViewBinding = FragmentDemoBinding.inflate(inflater, container, false)
        return mViewBinding.root
    }

    override fun getGravity(): Int = Gravity.CENTER

    override fun onViewClick(mode: CaptureMediaView.CaptureMode?) {
        if (! isCameraOpened()) {
            ToastUtils.show(R.string.camera_not_working)
            return
        }
        when (mode) {
            CaptureMediaView.CaptureMode.MODE_CAPTURE_PIC -> {
                captureImage()
            }
            CaptureMediaView.CaptureMode.MODE_CAPTURE_VIDEO -> {
                captureVideo()
            }
            else -> Unit
        }
    }

    private fun captureVideo() {
        if (isCapturingVideo) {
            captureVideoStop()
            return
        }
        captureVideoStart(object : ICaptureCallBack {
            override fun onBegin() {
                isCapturingVideo = true
                mViewBinding.captureBtn.setCaptureVideoState(CaptureMediaView.CaptureVideoState.DOING)
                mViewBinding.modeSwitchLayout.visibility = View.GONE
                mViewBinding.toolbarGroup.visibility = View.GONE
                mViewBinding.albumPreviewIv.visibility = View.GONE
                mViewBinding.recTimerLayout.visibility = View.VISIBLE
                startMediaTimer()
            }

            override fun onError(error: String?) {
                ToastUtils.show(error ?: getString(R.string.unknown_error))
                isCapturingVideo = false
                mViewBinding.captureBtn.setCaptureVideoState(CaptureMediaView.CaptureVideoState.UNDO)
                stopMediaTimer()
            }

            override fun onComplete(path: String?) {
                handleCapturedMedia(path, "video/mp4")
                isCapturingVideo = false
                mViewBinding.captureBtn.setCaptureVideoState(CaptureMediaView.CaptureVideoState.UNDO)
                mViewBinding.modeSwitchLayout.visibility = View.VISIBLE
                mViewBinding.toolbarGroup.visibility = View.VISIBLE
                mViewBinding.albumPreviewIv.visibility = View.VISIBLE
                mViewBinding.recTimerLayout.visibility = View.GONE
                showRecentMedia(false)
                stopMediaTimer()
            }

        }, createTemporaryMediaPath("mp4", extensionAddedByLibrary = true))
    }

    private fun captureImage() {
        captureImage(object : ICaptureCallBack {
            override fun onBegin() {
                mTakePictureTipView.show("", 100)
                mViewBinding.albumPreviewIv.showImageLoadProgress()
                mViewBinding.albumPreviewIv.setNewImageFlag(true)
            }

            override fun onError(error: String?) {
                ToastUtils.show(error ?: getString(R.string.unknown_error))
                mViewBinding.albumPreviewIv.cancelAnimation()
                mViewBinding.albumPreviewIv.setNewImageFlag(false)
            }

            override fun onComplete(path: String?) {
                showRecentMedia(true)
                mViewBinding.albumPreviewIv.setNewImageFlag(false)
                handleCapturedMedia(path, "image/jpeg")
            }
        }, createTemporaryMediaPath("jpg"))
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }

    override fun onClick(v: View?) {
//        if (! isCameraOpened()) {
//            ToastUtils.show("camera not worked!")
//            return
//        }
        clickAnimation(v!!, object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                when (v) {
                    mViewBinding.headerSettingsBtn -> {
                        showAppearanceDialog()
                    }
                    mViewBinding.cameraTypeBtn -> {
                        val currentDevice = (getCurrentCamera() as? CameraUVC)?.getUsbDevice()
                        showUsbDevicesDialog(getDeviceList(), currentDevice)
                    }
                    mViewBinding.settingsBtn -> {
                        folderPicker.launch(selectedMediaFolderUri())
                    }
                    mViewBinding.themeBtn -> {
                        showAppearanceDialog()
                    }
                    mViewBinding.resolutionBtn -> {
                        showResolutionDialog()
                    }
                    mViewBinding.albumPreviewIv -> {
                        goToGalley()
                    }
                    else -> {
                    }
                }
            }
        })
    }

    @SuppressLint("CheckResult")
    private fun showUsbDevicesDialog(usbDeviceList: MutableList<UsbDevice>?, curDevice: UsbDevice?) {
        if (usbDeviceList.isNullOrEmpty()) {
            ToastUtils.show(R.string.usb_device_error)
            return
        }
        val list = arrayListOf<String>()
        var selectedIndex: Int = -1
        for (index in (0 until usbDeviceList.size)) {
            val dev = usbDeviceList[index]
            val devName = if (Build.VERSION.SDK_INT >=Build.VERSION_CODES.LOLLIPOP && !dev.productName.isNullOrEmpty()) {
                "${dev.productName}(${curDevice?.deviceId})"
            } else {
                dev.deviceName
            }
            val curDevName = if (Build.VERSION.SDK_INT >=Build.VERSION_CODES.LOLLIPOP && !curDevice?.productName.isNullOrEmpty()) {
                "${curDevice!!.productName}(${curDevice.deviceId})"
            } else {
                curDevice?.deviceName
            }
            if (devName == curDevName) {
                selectedIndex = index
            }
            list.add(devName)
        }
        MaterialDialog(requireContext()).show {
            listItemsSingleChoice(
                items = list,
                initialSelection = selectedIndex
            ) { dialog, index, text ->
                if (selectedIndex == index) {
                    return@listItemsSingleChoice
                }
                switchCamera(usbDeviceList[index])
            }
        }
    }

    private fun showEffectDialog() {
        EffectListDialog(requireActivity()).apply {
            setData(mEffectDataList, object : EffectListDialog.OnEffectClickListener {
                override fun onEffectClick(effect: CameraEffect) {
                    mEffectDataList.find {it.id == effect.id}.also {
                        if (it == null) {
                            ToastUtils.show(R.string.effect_error)
                            return@also
                        }
                        updateRenderEffect(it.classifyId, it.effect)
                        // save to sp
                        if (effect.classifyId == CameraEffect.CLASSIFY_ID_ANIMATION) {
                            KEY_ANIMATION
                        } else {
                            KEY_FILTER
                        }.also { key ->
                            MMKVUtils.set(key, effect.id)
                        }
                    }
                }
            })
            show()
        }
    }

    private fun showAppearanceDialog() {
        val preferences = requireContext().getSharedPreferences(
            MainActivity.APPEARANCE_PREFS,
            0
        )
        val currentSelection = preferences.getInt(
            MainActivity.KEY_APPEARANCE,
            MainActivity.APPEARANCE_ATELIER
        )
        val choices = listOf(
            getString(R.string.theme_atelier),
            getString(R.string.theme_visee),
            getString(R.string.theme_workshop)
        )
        MaterialDialog(requireContext()).show {
            title(R.string.theme_title)
            listItems(items = choices) { dialog, index, _ ->
                dialog.dismiss()
                if (index == currentSelection) return@listItems
                preferences.edit().putInt(MainActivity.KEY_APPEARANCE, index).commit()
                ToastUtils.show(getString(R.string.theme_applied, choices[index]))
                requireActivity().recreate()
            }
        }
    }

    @SuppressLint("CheckResult")
    private fun showResolutionDialog() {
        getAllPreviewSizes().let { previewSizes ->
            if (previewSizes.isNullOrEmpty()) {
                ToastUtils.show(R.string.preview_size_error)
                return
            }
            val sortedSizes = previewSizes.distinctBy { it.width to it.height }
                .sortedByDescending { it.width.toLong() * it.height }
            val list = arrayListOf<String>()
            var selectedIndex: Int = -1
            for (index in sortedSizes.indices) {
                val w = sortedSizes[index].width
                val h = sortedSizes[index].height
                getCurrentPreviewSize()?.apply {
                    if (width == w && height == h) {
                        selectedIndex = index
                    }
                }
                list.add("$w x $h")
            }
            MaterialDialog(requireContext()).show {
                title(R.string.resolution_title)
                listItemsSingleChoice(
                    items = list,
                    initialSelection = selectedIndex
                ) { dialog, index, text ->
                    if (selectedIndex == index) {
                        return@listItemsSingleChoice
                    }
                    updateResolution(sortedSizes[index].width, sortedSizes[index].height)
                }
            }
        }
    }

    private fun selectedMediaFolderUri(): Uri? {
        val value = requireContext().getSharedPreferences(STORAGE_PREFS, 0)
            .getString(KEY_MEDIA_FOLDER, null)
        return value?.let(Uri::parse)
    }

    private fun createTemporaryMediaPath(
        extension: String,
        extensionAddedByLibrary: Boolean = false
    ): String? {
        if (selectedMediaFolderUri() == null) return null
        val directory = File(requireContext().cacheDir, "media").apply { mkdirs() }
        val baseName = "Alyx_${System.currentTimeMillis()}"
        return File(directory, if (extensionAddedByLibrary) baseName else "$baseName.$extension").absolutePath
    }

    private fun handleCapturedMedia(path: String?, mimeType: String) {
        if (path.isNullOrBlank()) {
            ToastUtils.show(R.string.unknown_error)
            return
        }
        val treeUri = selectedMediaFolderUri()
        if (treeUri == null) {
            ToastUtils.show(path)
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val source = File(path)
                val tree = DocumentFile.fromTreeUri(requireContext(), treeUri)
                    ?: throw IllegalStateException("Invalid destination folder")
                val destination = tree.createFile(mimeType, source.name)
                    ?: throw IllegalStateException("Unable to create destination file")
                requireContext().contentResolver.openOutputStream(destination.uri, "w").use { output ->
                    requireNotNull(output)
                    source.inputStream().use { input -> input.copyTo(output) }
                }
                source.delete()
                launch(Dispatchers.Main) {
                    ToastUtils.show(getString(R.string.media_saved, tree.name ?: "le dossier sélectionné"))
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Unable to export captured media", e)
                launch(Dispatchers.Main) { ToastUtils.show(R.string.media_folder_error) }
            }
        }
    }

    private fun goToGalley() {
        try {
            Intent(
                Intent.ACTION_VIEW,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            ).apply {
                startActivity(this)
            }
        } catch (e: Exception) {
            ToastUtils.show(getString(R.string.open_error, e.localizedMessage ?: getString(R.string.unknown_error)))
        }
    }

    private fun showRecentMedia(isImage: Boolean? = null) {
        lifecycleScope.launch(Dispatchers.IO) {
            context ?: return@launch
            if (! isFragmentAttached()) {
                return@launch
            }
            try {
                if (isImage != null) {
                    MediaUtils.findRecentMedia(requireContext(), isImage)
                } else {
                    MediaUtils.findRecentMedia(requireContext())
                }?.also { path ->
                    val size = Utils.dp2px(requireContext(), 38F)
                    ImageLoaders.of(this@DemoFragment)
                        .loadAsBitmap(path, size, size, object : ILoader.OnLoadedResultListener {
                            override fun onLoadedSuccess(bitmap: Bitmap?) {
                                lifecycleScope.launch(Dispatchers.Main) {
                                    mViewBinding.albumPreviewIv.canShowImageBorder = true
                                    mViewBinding.albumPreviewIv.setImageBitmap(bitmap)
                                }
                            }

                            override fun onLoadedFailed(error: Exception?) {
                                lifecycleScope.launch(Dispatchers.Main) {
                                    mViewBinding.albumPreviewIv.cancelAnimation()
                                }
                            }
                        })
                }
            } catch (e: Exception) {
                activity?.runOnUiThread {
                    ToastUtils.show("${e.localizedMessage}")
                }
                Logger.e(TAG, "showRecentMedia failed", e)
            }
        }
    }

    private fun updateCameraModeSwitchUI() {
        val surface = MaterialColors.getColor(
            mViewBinding.modeSwitchLayout,
            com.google.android.material.R.attr.colorSurface
        )
        val accent = MaterialColors.getColor(
            mViewBinding.modeSwitchLayout,
            com.google.android.material.R.attr.colorPrimary
        )
        val density = resources.displayMetrics.density
        val selectedAppearance = requireContext()
            .getSharedPreferences(MainActivity.APPEARANCE_PREFS, 0)
            .getInt(MainActivity.KEY_APPEARANCE, MainActivity.APPEARANCE_ATELIER)
        val visee = selectedAppearance == MainActivity.APPEARANCE_VISEE
        val workshop = selectedAppearance == MainActivity.APPEARANCE_WORKSHOP
        mViewBinding.modeSwitchLayout.children.filterIsInstance<TextView>().forEach { tabTv ->
            val isSelected = tabTv.id == mCameraModeTabMap[mCameraMode]
            val typeface = if (isSelected) Typeface.BOLD else Typeface.NORMAL
            tabTv.typeface = Typeface.defaultFromStyle(typeface)
            val onSurface = MaterialColors.getColor(
                tabTv,
                com.google.android.material.R.attr.colorOnSurface
            )
            val textColor = if (isSelected) accent else ColorUtils.setAlphaComponent(onSurface, 178)
            tabTv.setTextColor(textColor)
            tabTv.textSize = if (visee || workshop) 15f else 12f
            tabTv.setShadowLayer(0F, 0F, 0F, android.graphics.Color.TRANSPARENT)
            if (visee) {
                TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    tabTv,
                    0,
                    0,
                    0,
                    if (isSelected) R.drawable.camera_mode_line_selected
                    else R.drawable.camera_mode_line_transparent
                )
                tabTv.compoundDrawablePadding = (5 * density).toInt()
            } else if (workshop) {
                TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(tabTv, 0, 0, 0, 0)
                tabTv.compoundDrawablePadding = 0
            } else {
                TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    tabTv,
                    0,
                    if (tabTv.id == R.id.takePictureModeTv) R.drawable.ic_mode_photo
                    else R.drawable.ic_mode_video,
                    0,
                    0
                )
                tabTv.compoundDrawablePadding = (3 * density).toInt()
            }
            TextViewCompat.setCompoundDrawableTintList(tabTv, ColorStateList.valueOf(textColor))
            tabTv.background = if (isSelected && !visee) {
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 24f * density
                    setColor(ColorUtils.blendARGB(surface, accent, 0.10f))
                }
            } else {
                null
            }
        }
        mViewBinding.captureBtn.setCaptureViewTheme(CaptureMediaView.CaptureViewTheme.THEME_BLUE)
        mViewBinding.captureBtn.setCaptureMode(mCameraMode)
        mViewBinding.controlPanelLayout.visibility = View.VISIBLE
        mViewBinding.controlPanelLayout.translationY = 0f
    }

    private fun clickAnimation(v: View, listener: Animator.AnimatorListener) {
        val scaleXAnim: ObjectAnimator = ObjectAnimator.ofFloat(v, "scaleX", 1.0f, 0.4f, 1.0f)
        val scaleYAnim: ObjectAnimator = ObjectAnimator.ofFloat(v, "scaleY", 1.0f, 0.4f, 1.0f)
        val alphaAnim: ObjectAnimator = ObjectAnimator.ofFloat(v, "alpha", 1.0f, 0.4f, 1.0f)
        val animatorSet = AnimatorSet()
        animatorSet.duration = 150
        animatorSet.addListener(listener)
        animatorSet.playTogether(scaleXAnim, scaleYAnim, alphaAnim)
        animatorSet.start()
    }

    private fun startMediaTimer() {
        val pushTask: TimerTask = object : TimerTask() {
            override fun run() {
                //秒
                mRecSeconds++
                //分
                if (mRecSeconds >= 60) {
                    mRecSeconds = 0
                    mRecMinute++
                }
                //时
                if (mRecMinute >= 60) {
                    mRecMinute = 0
                    mRecHours++
                    if (mRecHours >= 24) {
                        mRecHours = 0
                        mRecMinute = 0
                        mRecSeconds = 0
                    }
                }
                mMainHandler.sendEmptyMessage(WHAT_START_TIMER)
            }
        }
        if (mRecTimer != null) {
            stopMediaTimer()
        }
        mRecTimer = Timer()
        //执行schedule后1s后运行run，之后每隔1s运行run
        mRecTimer?.schedule(pushTask, 1000, 1000)
    }

    private fun stopMediaTimer() {
        if (mRecTimer != null) {
            mRecTimer?.cancel()
            mRecTimer = null
        }
        mRecHours = 0
        mRecMinute = 0
        mRecSeconds = 0
        mMainHandler.sendEmptyMessage(WHAT_STOP_TIMER)
    }

    private fun calculateTime(seconds: Int, minute: Int, hour: Int? = null): String {
        val mBuilder = java.lang.StringBuilder()
        //时
        if (hour != null) {
            if (hour < 10) {
                mBuilder.append("0")
                mBuilder.append(hour)
            } else {
                mBuilder.append(hour)
            }
            mBuilder.append(":")
        }
        // 分
        if (minute < 10) {
            mBuilder.append("0")
            mBuilder.append(minute)
        } else {
            mBuilder.append(minute)
        }
        //秒
        mBuilder.append(":")
        if (seconds < 10) {
            mBuilder.append("0")
            mBuilder.append(seconds)
        } else {
            mBuilder.append(seconds)
        }
        return mBuilder.toString()
    }

    companion object {
        private const val STORAGE_PREFS = "media_storage"
        private const val KEY_MEDIA_FOLDER = "media_folder_uri"
        private const val TAG  = "DemoFragment"
        private const val WHAT_START_TIMER = 0x00
        private const val WHAT_STOP_TIMER = 0x01
    }
}
