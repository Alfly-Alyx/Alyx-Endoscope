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
import android.graphics.Typeface
import android.hardware.usb.UsbDevice
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
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
import com.jiangdg.ausbc.camera.bean.CameraRequest
import com.jiangdg.ausbc.render.effect.EffectBlackWhite
import com.jiangdg.ausbc.render.effect.EffectRadialDistortion
import com.jiangdg.ausbc.render.effect.EffectSoul
import com.jiangdg.ausbc.render.effect.EffectTimestamp
import com.jiangdg.ausbc.render.effect.EffectZoom
import com.jiangdg.ausbc.render.effect.bean.CameraEffect
import com.jiangdg.ausbc.render.env.RotateType
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
    private var mTimestampEffect: EffectTimestamp? = null
    private var mVideoCapturedAt = 0L
    private var mVideoComment = ""
    private var mCaptureComment = ""
    private var mRadialDistortionEffect: EffectRadialDistortion? = null
    private var mRadialCoefficient = 0f

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
                    setToolbarVisibility(true)
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
        mCaptureComment = requireContext().getSharedPreferences(COMMENT_PREFS, 0)
            .getString(KEY_CAPTURE_COMMENT, "").orEmpty()
        loadPersistedUserSettings()
        attachDistortionOverlayToPreview()
        applySelectedAppearance()
        setupRadialDistortionControl()
        mViewBinding.cameraTypeBtn.setOnClickListener(this)
        mViewBinding.settingsBtn.setOnClickListener(this)
        mViewBinding.themeBtn.setOnClickListener(this)
        mViewBinding.headerSettingsBtn.setOnClickListener(this)
        mViewBinding.resolutionBtn.setOnClickListener(this)
        mViewBinding.albumPreviewIv.setOnClickListener(this)
        mViewBinding.galleryBtn.setOnClickListener(this)
        mViewBinding.atelierCommentBtn.setOnClickListener(this)
        mViewBinding.captureBtn.setOnViewClickListener(this)
        switchLayoutClick()
    }

    /** Place la commande exactement au-dessus du TextureView, y compris en mode letterbox. */
    private fun attachDistortionOverlayToPreview() {
        val container = mViewBinding.cameraViewContainer
        val overlay = mViewBinding.distortionOverlayLayout
        val preview = container.children.firstOrNull() ?: return
        (overlay.parent as? ViewGroup)?.removeView(overlay)
        container.addView(
            overlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        )

        fun matchPreviewBounds() {
            if (preview.width <= 0 || preview.height <= 0) return
            overlay.layoutParams = FrameLayout.LayoutParams(
                preview.width,
                preview.height,
                Gravity.CENTER
            )
            overlay.bringToFront()
        }
        preview.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> matchPreviewBounds() }
        container.post { matchPreviewBounds() }
    }

    /** Rend les deux apparences réellement distinctes, au-delà de la palette DayNight. */
    private fun applySelectedAppearance() {
        val context = requireContext()
        val selected = context.getSharedPreferences(MainActivity.APPEARANCE_PREFS, 0)
            .getInt(MainActivity.KEY_APPEARANCE, MainActivity.APPEARANCE_ATELIER)
        val surface = MaterialColors.getColor(mViewBinding.root, com.google.android.material.R.attr.colorSurface)
        val accent = MaterialColors.getColor(mViewBinding.root, com.google.android.material.R.attr.colorPrimary)
        val onSurface = MaterialColors.getColor(mViewBinding.root, com.google.android.material.R.attr.colorOnSurface)
        val outline = ColorUtils.setAlphaComponent(onSurface, 38)
        val density = resources.displayMetrics.density
        val (cornerDp, marginDp, strokeDp) = when (selected) {
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

        mViewBinding.headerTitleTv.text = getString(R.string.app_name)
        val workshop = selected == MainActivity.APPEARANCE_WORKSHOP
        val regularTint = ColorStateList.valueOf(onSurface)
        val accentTint = ColorStateList.valueOf(accent)
        mViewBinding.settingsBtn.imageTintList = if (workshop) accentTint else regularTint
        mViewBinding.cameraTypeBtn.imageTintList = if (workshop) accentTint else regularTint
        mViewBinding.themeBtn.imageTintList = if (workshop) accentTint else regularTint
        mViewBinding.resolutionBtn.imageTintList = if (workshop) accentTint else regularTint
        mViewBinding.galleryBtn.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(surface)
            setStroke(density.toInt().coerceAtLeast(1), outline)
        }
        mViewBinding.galleryBtn.imageTintList = accentTint
        val galleryPadding = (18 * density).toInt()
        mViewBinding.galleryBtn.setPadding(galleryPadding, galleryPadding, galleryPadding, galleryPadding)
        mViewBinding.distortionControlLayout.background = null
        mViewBinding.distortionValueTv.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 10f * density
            setColor(ColorUtils.setAlphaComponent(surface, if (workshop) 236 else 224))
            setStroke(density.toInt().coerceAtLeast(1), outline)
        }
        mViewBinding.distortionValueTv.setTextColor(onSurface)
        mViewBinding.distortionSlider.setColors(
            ColorUtils.setAlphaComponent(onSurface, 92),
            accent,
            surface
        )

        if (workshop) {
            mViewBinding.atelierCommentBtn.visibility = View.GONE
            mViewBinding.atelierCommentLabelTv.visibility = View.GONE
            mViewBinding.commentActionCard.visibility = View.GONE
            mViewBinding.atelierCommentGroup.visibility = View.GONE
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
                mViewBinding.resolutionActionCard,
                mViewBinding.commentActionCard
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
            mViewBinding.themeBtn.apply {
                setImageResource(R.drawable.ic_comment_alyx)
                contentDescription = getString(R.string.add_capture_comment)
            }
            mViewBinding.themeLabelTv.setText(R.string.comment_short)
            listOf(
                mViewBinding.settingsLabelTv,
                mViewBinding.cameraLabelTv,
                mViewBinding.themeLabelTv,
                mViewBinding.resolutionLabelTv
            ).forEach {
                it.alpha = 1f
                it.textSize = if (it === mViewBinding.themeLabelTv) 10f else 11f
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
        } else {
            mViewBinding.headerSettingsBtn.visibility = View.GONE
            mViewBinding.atelierCommentBtn.visibility = View.VISIBLE
            mViewBinding.atelierCommentLabelTv.visibility = View.VISIBLE
            mViewBinding.commentActionCard.visibility = View.VISIBLE
            mViewBinding.atelierCommentGroup.visibility = View.VISIBLE
            mViewBinding.themeBtn.apply {
                setImageResource(R.drawable.ic_palette)
                contentDescription = getString(R.string.choose_theme)
            }
            mViewBinding.themeLabelTv.setText(R.string.theme_short)
            listOf(
                mViewBinding.folderActionCard,
                mViewBinding.cameraActionCard,
                mViewBinding.themeActionCard,
                mViewBinding.resolutionActionCard,
                mViewBinding.commentActionCard
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
            val toolbarMargin = if (workshop) (16 * density).toInt() else 0
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

        mViewBinding.modeSwitchLayout.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 28f * density
            setColor(ColorUtils.blendARGB(surface, onSurface, 0.035f))
            setStroke(density.toInt().coerceAtLeast(1), outline)
        }
        mViewBinding.photoVideoDivider.visibility = View.VISIBLE
    }

    /**
     * Les propositions sont des compositions d'interface, pas uniquement des couleurs.
     * Atelier : composition claire et structurée, fidèle à la maquette de référence.
     * Workshop : commandes placées entre le viseur et le panneau de capture.
     */
    private fun applyAppearanceComposition(selected: Int) {
        val root = mViewBinding.root as androidx.constraintlayout.widget.ConstraintLayout
        val constraints = ConstraintSet().apply { clone(root) }
        when (selected) {
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
                constraints.constrainHeight(R.id.headerBg, dp(82))
                constraints.constrainWidth(R.id.headerLogoIv, dp(52))
                constraints.constrainHeight(R.id.headerLogoIv, dp(52))
                constraints.constrainHeight(R.id.controlPanelLayout, dp(180))
                constraints.constrainHeight(R.id.modeSwitchLayout, dp(54))
                constraints.constrainWidth(R.id.uvcLogoIv, dp(72))
                constraints.constrainHeight(R.id.uvcLogoIv, dp(72))
                constraints.constrainWidth(R.id.albumPreviewIv, dp(52))
                constraints.constrainHeight(R.id.albumPreviewIv, dp(52))
                constraints.constrainWidth(R.id.galleryBtn, dp(76))
                constraints.constrainHeight(R.id.galleryBtn, dp(76))

                // Workshop : Dossier, Caméra, Résolution, Commentaire.
                constraints.clear(R.id.cameraTypeBtn, ConstraintSet.END)
                constraints.connect(
                    R.id.cameraTypeBtn, ConstraintSet.END,
                    R.id.resolutionBtn, ConstraintSet.START
                )
                constraints.clear(R.id.resolutionBtn, ConstraintSet.START)
                constraints.clear(R.id.resolutionBtn, ConstraintSet.END)
                constraints.connect(
                    R.id.resolutionBtn, ConstraintSet.START,
                    R.id.cameraTypeBtn, ConstraintSet.END
                )
                constraints.connect(
                    R.id.resolutionBtn, ConstraintSet.END,
                    R.id.themeBtn, ConstraintSet.START
                )
                constraints.clear(R.id.themeBtn, ConstraintSet.START)
                constraints.clear(R.id.themeBtn, ConstraintSet.END)
                constraints.connect(
                    R.id.themeBtn, ConstraintSet.START,
                    R.id.resolutionBtn, ConstraintSet.END
                )
                constraints.connect(
                    R.id.themeBtn, ConstraintSet.END,
                    R.id.toolbarBg, ConstraintSet.END
                )
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
                    ConstraintSet.PARENT_ID, ConstraintSet.START, dp(8)
                )
                constraints.connect(
                    R.id.toolbarBg, ConstraintSet.END,
                    ConstraintSet.PARENT_ID, ConstraintSet.END, dp(8)
                )
                constraints.connect(
                    R.id.toolbarBg, ConstraintSet.TOP,
                    R.id.headerBg, ConstraintSet.BOTTOM, dp(0)
                )
                constraints.constrainWidth(R.id.toolbarBg, ConstraintSet.MATCH_CONSTRAINT)
                constraints.constrainHeight(R.id.toolbarBg, dp(76))

                constraints.clear(R.id.cameraViewContainer, ConstraintSet.TOP)
                constraints.clear(R.id.cameraViewContainer, ConstraintSet.BOTTOM)
                constraints.connect(
                    R.id.cameraViewContainer, ConstraintSet.TOP,
                    R.id.toolbarBg, ConstraintSet.BOTTOM, dp(8)
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
                constraints.constrainWidth(R.id.galleryBtn, dp(76))
                constraints.constrainHeight(R.id.galleryBtn, dp(76))
                mViewBinding.toolbarBg.alpha = 1f
                mViewBinding.toolbarBg.background = null
                constraints.constrainHeight(R.id.headerBg, dp(68))
                constraints.constrainWidth(R.id.headerLogoIv, dp(36))
                constraints.constrainHeight(R.id.headerLogoIv, dp(36))
                mViewBinding.headerTitleTv.textSize = 21f
            }
        }
        constraints.applyTo(root)
        mViewBinding.headerLogoIv.visibility = View.VISIBLE
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
            ensureRadialDistortionEffect()
        })
    }

    override fun onCameraState(
        self: MultiCameraClient.ICamera,
        code: ICameraStateCallBack.State,
        msg: String?
    ) {
        if (!isAdded || view == null || !::mViewBinding.isInitialized) {
            Logger.i(TAG, "Ignore camera state $code after view destruction")
            return
        }
        when (code) {
            ICameraStateCallBack.State.OPENED -> handleCameraOpened()
            ICameraStateCallBack.State.CLOSED -> handleCameraClosed()
            ICameraStateCallBack.State.ERROR -> handleCameraError(msg)
        }
    }

    private fun handleCameraError(msg: String?) {
        (getCurrentCamera() as? CameraUVC)?.setHardwareButtonCallback(null)
        showEmptyCameraState()
        mViewBinding.frameRateTv.visibility = View.GONE
        ToastUtils.show(getString(R.string.camera_open_error, msg ?: getString(R.string.unknown_error)))
    }

    private fun handleCameraClosed() {
        (getCurrentCamera() as? CameraUVC)?.setHardwareButtonCallback(null)
        showEmptyCameraState()
        mViewBinding.frameRateTv.visibility = View.GONE
        ToastUtils.show(R.string.camera_closed)
    }

    private fun handleCameraOpened() {
        val camera = getCurrentCamera() as? CameraUVC
        camera?.setHardwareButtonCallback { button, state ->
            Logger.i(TAG, "Endoscope button event: button=$button, state=$state")
            if (state == HARDWARE_BUTTON_PRESSED) requestHardwareCapture()
        }
        mViewBinding.uvcLogoIv.visibility = View.GONE
        mViewBinding.emptyCameraTitle.visibility = View.GONE
        mViewBinding.emptyCameraSubtitle.visibility = View.GONE
        mViewBinding.frameRateTv.visibility = View.VISIBLE
        val device = camera?.getUsbDevice()
        val userSettings = userSettings()
        getCurrentPreviewSize()?.let { size ->
            userSettings.edit()
                .putInt(deviceSettingKey(KEY_RESOLUTION_WIDTH, device), size.width)
                .putInt(deviceSettingKey(KEY_RESOLUTION_HEIGHT, device), size.height)
                .apply()
        }
        if (!userSettings.contains(KEY_CAMERA_VENDOR_ID) && device != null) {
            savePreferredCamera(device)
        }
        loadRadialCoefficientForDevice(device)
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
        userSettings().edit().putInt(
            KEY_CAPTURE_MODE,
            if (mode == CaptureMediaView.CaptureMode.MODE_CAPTURE_VIDEO) CAPTURE_MODE_VIDEO
            else CAPTURE_MODE_PHOTO
        ).apply()
        updateCameraModeSwitchUI()
    }

    override fun getCameraRequest(): CameraRequest {
        val device = (getCurrentCamera() as? CameraUVC)?.getUsbDevice()
        val preferences = userSettings()
        val width = preferences.getInt(deviceSettingKey(KEY_RESOLUTION_WIDTH, device), 0)
        val height = preferences.getInt(deviceSettingKey(KEY_RESOLUTION_HEIGHT, device), 0)
        return CameraRequest.Builder()
            .setPreviewWidth(width)
            .setPreviewHeight(height)
            .setRenderMode(CameraRequest.RenderMode.OPENGL)
            .setDefaultRotateType(RotateType.ANGLE_0)
            .setAudioSource(CameraRequest.AudioSource.NONE)
            .setPreviewFormat(CameraRequest.PreviewFormat.FORMAT_MJPEG)
            .setAspectRatioShow(true)
            .setCaptureRawImage(false)
            .setRawPreviewData(false)
            .create()
    }

    override fun getDefaultCamera(): UsbDevice? {
        val preferences = userSettings()
        if (!preferences.contains(KEY_CAMERA_VENDOR_ID) || !preferences.contains(KEY_CAMERA_PRODUCT_ID)) {
            return null
        }
        val vendorId = preferences.getInt(KEY_CAMERA_VENDOR_ID, -1)
        val productId = preferences.getInt(KEY_CAMERA_PRODUCT_ID, -1)
        return getDeviceList()?.firstOrNull {
            it.vendorId == vendorId && it.productId == productId
        }
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

    /** Handles both Android KEY_CAMERA events and UVC status-endpoint button events. */
    fun requestHardwareCapture(): Boolean {
        if (!isAdded || view == null || !::mViewBinding.isInitialized) return false
        mViewBinding.root.post {
            if (isAdded && view != null) {
                onViewClick(mCameraMode)
            }
        }
        return true
    }

    private fun captureVideo() {
        if (isCapturingVideo) {
            captureVideoStop()
            return
        }
        mVideoCapturedAt = System.currentTimeMillis()
        mVideoComment = mCaptureComment
        applyTimestampEffect(mVideoCapturedAt, mVideoComment)
        captureVideoStart(object : ICaptureCallBack {
            override fun onBegin() {
                isCapturingVideo = true
                mViewBinding.captureBtn.setCaptureVideoState(CaptureMediaView.CaptureVideoState.DOING)
                mViewBinding.modeSwitchLayout.visibility = View.GONE
                setToolbarVisibility(false)
                mViewBinding.recTimerLayout.visibility = View.VISIBLE
                startMediaTimer()
            }

            override fun onError(error: String?) {
                ToastUtils.show(error ?: getString(R.string.unknown_error))
                isCapturingVideo = false
                removeTimestampEffect()
                mViewBinding.captureBtn.setCaptureVideoState(CaptureMediaView.CaptureVideoState.UNDO)
                stopMediaTimer()
            }

            override fun onComplete(path: String?) {
                removeTimestampEffect()
                handleCapturedMedia(path, "video/mp4", mVideoCapturedAt, mVideoComment)
                isCapturingVideo = false
                mViewBinding.captureBtn.setCaptureVideoState(CaptureMediaView.CaptureVideoState.UNDO)
                mViewBinding.modeSwitchLayout.visibility = View.VISIBLE
                setToolbarVisibility(true)
                mViewBinding.recTimerLayout.visibility = View.GONE
                showRecentMedia(false)
                stopMediaTimer()
            }

        }, createTemporaryMediaPath("mp4", mVideoCapturedAt, extensionAddedByLibrary = true))
    }

    private fun captureImage() {
        val capturedAt = System.currentTimeMillis()
        val comment = mCaptureComment
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
                handleCapturedMedia(path, "image/jpeg", capturedAt, comment)
            }
        }, createTemporaryMediaPath("jpg", capturedAt))
    }

    private fun applyTimestampEffect(capturedAt: Long, comment: String) {
        removeTimestampEffect()
        mTimestampEffect = EffectTimestamp(requireContext(), capturedAt, comment).also(::addRenderEffect)
    }

    private fun removeTimestampEffect() {
        mTimestampEffect?.let(::removeRenderEffect)
        mTimestampEffect = null
    }

    override fun onDestroyView() {
        (getCurrentCamera() as? CameraUVC)?.setHardwareButtonCallback(null)
        removeTimestampEffect()
        mRadialDistortionEffect?.let(::removeRenderEffect)
        mRadialDistortionEffect = null
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
                        if (isWorkshopAppearance()) showCaptureCommentDialog() else showAppearanceDialog()
                    }
                    mViewBinding.resolutionBtn -> {
                        showResolutionDialog()
                    }
                    mViewBinding.albumPreviewIv -> {
                        goToGalley()
                    }
                    mViewBinding.galleryBtn -> {
                        goToGalley()
                    }
                    mViewBinding.atelierCommentBtn -> {
                        showCaptureCommentDialog()
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
                "${dev.productName}(${dev.deviceId})"
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
                savePreferredCamera(usbDeviceList[index])
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
        val appearances = listOf(
            MainActivity.APPEARANCE_ATELIER to getString(R.string.theme_atelier),
            MainActivity.APPEARANCE_WORKSHOP to getString(R.string.theme_workshop)
        )
        val choices = appearances.map { it.second }
        MaterialDialog(requireContext()).show {
            title(R.string.theme_title)
            listItems(items = choices) { dialog, index, _ ->
                dialog.dismiss()
                val selectedAppearance = appearances[index].first
                if (selectedAppearance == currentSelection) return@listItems
                preferences.edit().putInt(MainActivity.KEY_APPEARANCE, selectedAppearance).commit()
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
                    val selectedSize = sortedSizes[index]
                    val device = (getCurrentCamera() as? CameraUVC)?.getUsbDevice()
                    userSettings().edit()
                        .putInt(deviceSettingKey(KEY_RESOLUTION_WIDTH, device), selectedSize.width)
                        .putInt(deviceSettingKey(KEY_RESOLUTION_HEIGHT, device), selectedSize.height)
                        .apply()
                    updateResolution(selectedSize.width, selectedSize.height)
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
        capturedAt: Long,
        extensionAddedByLibrary: Boolean = false
    ): String? {
        if (selectedMediaFolderUri() == null) return null
        val directory = File(requireContext().cacheDir, "media").apply { mkdirs() }
        val baseName = "Alyx_${MediaRepository.fileNameDate(capturedAt)}"
        return File(directory, if (extensionAddedByLibrary) baseName else "$baseName.$extension").absolutePath
    }

    private fun handleCapturedMedia(
        path: String?,
        mimeType: String,
        capturedAt: Long,
        comment: String
    ) {
        if (path.isNullOrBlank()) {
            ToastUtils.show(R.string.unknown_error)
            return
        }
        val treeUri = selectedMediaFolderUri()
        val appContext = requireContext().applicationContext
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val source = File(path)
                if (mimeType == "image/jpeg") {
                    MediaStampUtils.stampPhoto(source, capturedAt, comment)
                }
                if (treeUri == null) {
                    MediaRepository.add(
                        appContext,
                        MediaRecord(
                            Uri.fromFile(source).toString(), mimeType, capturedAt, source.name,
                            comment, hasEmbeddedStamp = true, embeddedComment = comment
                        )
                    )
                    launch(Dispatchers.Main) { ToastUtils.show(path) }
                    return@launch
                }
                val tree = DocumentFile.fromTreeUri(appContext, treeUri)
                    ?: throw IllegalStateException("Invalid destination folder")
                val destination = tree.createFile(mimeType, source.name)
                    ?: throw IllegalStateException("Unable to create destination file")
                appContext.contentResolver.openOutputStream(destination.uri, "w").use { output ->
                    requireNotNull(output)
                    source.inputStream().use { input -> input.copyTo(output) }
                }
                source.delete()
                MediaRepository.add(
                    appContext,
                    MediaRecord(
                        destination.uri.toString(), mimeType, capturedAt,
                        destination.name ?: source.name, comment,
                        hasEmbeddedStamp = true, embeddedComment = comment
                    )
                )
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
            startActivity(Intent(requireContext(), GalleryActivity::class.java))
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
            tabTv.textSize = if (workshop) 15f else 12f
            tabTv.setShadowLayer(0F, 0F, 0F, android.graphics.Color.TRANSPARENT)
            if (workshop) {
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
            tabTv.background = if (isSelected) {
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

    private fun loadPersistedUserSettings() {
        val preferences = userSettings()
        mCameraMode = if (preferences.getInt(KEY_CAPTURE_MODE, CAPTURE_MODE_PHOTO) == CAPTURE_MODE_VIDEO) {
            CaptureMediaView.CaptureMode.MODE_CAPTURE_VIDEO
        } else {
            CaptureMediaView.CaptureMode.MODE_CAPTURE_PIC
        }
        mRadialCoefficient = preferences.getFloat(KEY_RADIAL_COEFFICIENT, 0f)
            .coerceIn(EffectRadialDistortion.MIN_COEFFICIENT, EffectRadialDistortion.MAX_COEFFICIENT)
    }

    private fun setupRadialDistortionControl() {
        updateRadialDistortionUi()
        mViewBinding.distortionSlider.setOnValueChangeListener { value, finished ->
            val coefficient = EffectRadialDistortion.MIN_COEFFICIENT * value
            setRadialCoefficient(coefficient, persist = finished)
        }
    }

    private fun ensureRadialDistortionEffect() {
        val effect = mRadialDistortionEffect ?: EffectRadialDistortion(
            requireContext(),
            mRadialCoefficient
        ).also { mRadialDistortionEffect = it }
        effect.setCoefficient(mRadialCoefficient)
        addRenderEffect(effect)
    }

    private fun setRadialCoefficient(value: Float, persist: Boolean) {
        mRadialCoefficient = value.coerceIn(
            EffectRadialDistortion.MIN_COEFFICIENT,
            EffectRadialDistortion.MAX_COEFFICIENT
        )
        mRadialDistortionEffect?.setCoefficient(mRadialCoefficient)
        updateRadialDistortionUi()
        if (!persist) return

        val editor = userSettings().edit().putFloat(KEY_RADIAL_COEFFICIENT, mRadialCoefficient)
        val device = (getCurrentCamera() as? CameraUVC)?.getUsbDevice()
        if (device != null) {
            editor.putFloat(deviceSettingKey(KEY_RADIAL_COEFFICIENT, device), mRadialCoefficient)
        }
        editor.apply()
    }

    private fun updateRadialDistortionUi() {
        val normalized = if (EffectRadialDistortion.MIN_COEFFICIENT == 0f) 0f
        else (mRadialCoefficient / EffectRadialDistortion.MIN_COEFFICIENT).coerceIn(0f, 1f)
        mViewBinding.distortionSlider.setValue(normalized)
        mViewBinding.distortionValueTv.text = getString(
            R.string.radial_coefficient_value,
            mRadialCoefficient
        )
        mViewBinding.distortionSlider.contentDescription = getString(
            R.string.radial_distortion_value_accessibility,
            mRadialCoefficient
        )
    }

    private fun loadRadialCoefficientForDevice(device: UsbDevice?) {
        val preferences = userSettings()
        val deviceKey = deviceSettingKey(KEY_RADIAL_COEFFICIENT, device)
        val coefficient = if (device != null && preferences.contains(deviceKey)) {
            preferences.getFloat(deviceKey, mRadialCoefficient)
        } else {
            preferences.getFloat(KEY_RADIAL_COEFFICIENT, mRadialCoefficient)
        }
        setRadialCoefficient(coefficient, persist = false)
    }

    private fun savePreferredCamera(device: UsbDevice) {
        userSettings().edit()
            .putInt(KEY_CAMERA_VENDOR_ID, device.vendorId)
            .putInt(KEY_CAMERA_PRODUCT_ID, device.productId)
            .apply()
    }

    private fun deviceSettingKey(base: String, device: UsbDevice?): String {
        return if (device == null) base else "${base}_${device.vendorId}_${device.productId}"
    }

    private fun userSettings() = requireContext().getSharedPreferences(USER_SETTINGS_PREFS, 0)

    private fun isWorkshopAppearance(): Boolean = requireContext()
        .getSharedPreferences(MainActivity.APPEARANCE_PREFS, 0)
        .getInt(MainActivity.KEY_APPEARANCE, MainActivity.APPEARANCE_ATELIER) ==
        MainActivity.APPEARANCE_WORKSHOP

    private fun setToolbarVisibility(visible: Boolean) {
        mViewBinding.toolbarGroup.visibility = if (visible) View.VISIBLE else View.GONE
        val atelierVisibility = if (visible && !isWorkshopAppearance()) View.VISIBLE else View.GONE
        mViewBinding.atelierCommentGroup.visibility = atelierVisibility
        mViewBinding.atelierCommentBtn.visibility = atelierVisibility
        mViewBinding.atelierCommentLabelTv.visibility = atelierVisibility
        mViewBinding.commentActionCard.visibility = atelierVisibility
    }

    private fun showCaptureCommentDialog() {
        val density = resources.displayMetrics.density
        val input = EditText(requireContext()).apply {
            setText(mCaptureComment)
            setSelection(text.length)
            hint = getString(R.string.capture_comment_hint)
            minLines = 2
            maxLines = 3
            filters = arrayOf(android.text.InputFilter.LengthFilter(MAX_CAPTURE_COMMENT_LENGTH))
            val horizontal = (24 * density).toInt()
            val vertical = (10 * density).toInt()
            setPadding(horizontal, vertical, horizontal, vertical)
        }
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.add_capture_comment)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.clear_comment) { _, _ -> saveCaptureComment("") }
            .setPositiveButton(R.string.save_comment) { _, _ -> saveCaptureComment(input.text.toString()) }
            .create()
        dialog.setOnShowListener {
            input.requestFocus()
            dialog.window?.setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
            )
        }
        dialog.show()
    }

    private fun saveCaptureComment(value: String) {
        mCaptureComment = value.trim()
        requireContext().getSharedPreferences(COMMENT_PREFS, 0)
            .edit().putString(KEY_CAPTURE_COMMENT, mCaptureComment).apply()
        ToastUtils.show(
            if (mCaptureComment.isBlank()) R.string.comment_cleared else R.string.comment_ready
        )
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
        private const val USER_SETTINGS_PREFS = "user_settings"
        private const val KEY_CAPTURE_MODE = "capture_mode"
        private const val KEY_RADIAL_COEFFICIENT = "radial_coefficient"
        private const val KEY_RESOLUTION_WIDTH = "resolution_width"
        private const val KEY_RESOLUTION_HEIGHT = "resolution_height"
        private const val KEY_CAMERA_VENDOR_ID = "camera_vendor_id"
        private const val KEY_CAMERA_PRODUCT_ID = "camera_product_id"
        private const val CAPTURE_MODE_PHOTO = 0
        private const val CAPTURE_MODE_VIDEO = 1
        private const val HARDWARE_BUTTON_PRESSED = 1
        private const val COMMENT_PREFS = "capture_comments"
        private const val KEY_CAPTURE_COMMENT = "current_comment"
        private const val MAX_CAPTURE_COMMENT_LENGTH = 120
        private const val TAG  = "DemoFragment"
        private const val WHAT_START_TIMER = 0x00
        private const val WHAT_STOP_TIMER = 0x01
    }
}
