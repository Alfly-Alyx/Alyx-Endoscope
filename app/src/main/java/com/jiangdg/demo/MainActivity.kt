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

import android.Manifest.permission.*
import android.os.Bundle
import android.os.Build
import android.os.PowerManager
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.PermissionChecker
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import com.jiangdg.ausbc.utils.ToastUtils
import com.jiangdg.ausbc.utils.Utils
import com.jiangdg.demo.databinding.ActivityMainBinding

/**
 * Demos of camera usage
 *
 * @author Created by jiangdg on 2021/12/27
 */
class MainActivity : AppCompatActivity() {
    private var mWakeLock: PowerManager.WakeLock? = null
    private lateinit var viewBinding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(resolveAppearanceTheme(this))
        super.onCreate(savedInstanceState)
        setStatusBar()
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
        applySystemBarInsets()
        replaceDemoFragment(DemoFragment())
//        replaceDemoFragment(GlSurfaceFragment())
    }

    override fun onStart() {
        super.onStart()
        mWakeLock = Utils.wakeLock(this)
    }

    override fun onStop() {
        super.onStop()
        mWakeLock?.apply {
            Utils.wakeUnLock(this)
        }
    }

    private fun replaceDemoFragment(fragment: Fragment) {
        val hasCameraPermission = PermissionChecker.checkSelfPermission(this, CAMERA)
        val needsLegacyStoragePermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
        val hasStoragePermission = !needsLegacyStoragePermission ||
            PermissionChecker.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE) ==
            PermissionChecker.PERMISSION_GRANTED
        if (hasCameraPermission != PermissionChecker.PERMISSION_GRANTED || !hasStoragePermission) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, CAMERA)) {
                ToastUtils.show(R.string.permission_tip)
            }
            val permissions = mutableListOf(CAMERA)
            if (needsLegacyStoragePermission) permissions.add(WRITE_EXTERNAL_STORAGE)
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), REQUEST_CAMERA)
            return
        }
        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.fragment_container, fragment)
        transaction.commitAllowingStateLoss()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CAMERA -> {
                val hasCameraPermission = PermissionChecker.checkSelfPermission(this, CAMERA)
                if (hasCameraPermission == PermissionChecker.PERMISSION_DENIED) {
                    ToastUtils.show(R.string.permission_tip)
                    return
                }
                replaceDemoFragment(DemoFragment())
//                replaceDemoFragment(GlSurfaceFragment())
            }
            REQUEST_STORAGE -> {
                val hasCameraPermission =
                    PermissionChecker.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE)
                if (hasCameraPermission == PermissionChecker.PERMISSION_DENIED) {
                    ToastUtils.show(R.string.permission_tip)
                    return
                }
                // todo
            }
            else -> {
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun setStatusBar() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val attributes = theme.obtainStyledAttributes(
            intArrayOf(com.google.android.material.R.attr.colorPrimaryVariant)
        )
        val systemBarColor = attributes.getColor(0, getColor(R.color.black))
        attributes.recycle()
        val useDarkIcons = ColorUtils.calculateLuminance(systemBarColor) > 0.5
        window.statusBarColor = systemBarColor
        window.navigationBarColor = systemBarColor
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = useDarkIcons
            isAppearanceLightNavigationBars = useDarkIcons
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_CAMERA &&
            event.action == KeyEvent.ACTION_DOWN &&
            event.repeatCount == 0
        ) {
            val demoFragment = supportFragmentManager
                .findFragmentById(R.id.fragment_container) as? DemoFragment
            if (demoFragment?.requestHardwareCapture() == true) return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun applySystemBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(viewBinding.fragmentContainer) { view, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            windowInsets
        }
        ViewCompat.requestApplyInsets(viewBinding.fragmentContainer)
    }

    companion object {
        private const val REQUEST_CAMERA = 0
        private const val REQUEST_STORAGE = 1
        const val APPEARANCE_PREFS = "appearance"
        const val KEY_APPEARANCE = "selected_appearance"
        const val APPEARANCE_ATELIER = 0
        const val APPEARANCE_WORKSHOP = 2

        fun resolveAppearanceTheme(context: android.content.Context): Int {
            val preferences = context.getSharedPreferences(APPEARANCE_PREFS, android.content.Context.MODE_PRIVATE)
            return when (preferences.getInt(KEY_APPEARANCE, APPEARANCE_ATELIER)) {
                APPEARANCE_WORKSHOP -> R.style.Theme_Alyx_Workshop
                APPEARANCE_ATELIER -> R.style.Theme_Alyx_Atelier
                else -> {
                    preferences.edit().putInt(KEY_APPEARANCE, APPEARANCE_ATELIER).apply()
                    R.style.Theme_Alyx_Atelier
                }
            }
        }
    }
}
