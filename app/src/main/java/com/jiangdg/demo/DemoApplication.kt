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

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import com.jiangdg.ausbc.base.BaseApplication
import com.jiangdg.utils.MMKVUtils
import java.util.WeakHashMap

/**
 *
 * @author Created by jiangdg on 2022/2/28
 */
class DemoApplication: BaseApplication() {
    private val previousScreenBrightness = WeakHashMap<Activity, Float>()

    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        MMKVUtils.init(this)
        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                previousScreenBrightness[activity] = activity.window.attributes.screenBrightness
            }

            override fun onActivityResumed(activity: Activity) {
                previousScreenBrightness.putIfAbsent(
                    activity,
                    activity.window.attributes.screenBrightness
                )
                activity.window.attributes = activity.window.attributes.apply {
                    screenBrightness = 1f
                }
            }

            override fun onActivityPaused(activity: Activity) {
                restoreScreenBrightness(activity)
            }

            override fun onActivityDestroyed(activity: Activity) {
                restoreScreenBrightness(activity)
                previousScreenBrightness.remove(activity)
            }

            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        })
    }

    private fun restoreScreenBrightness(activity: Activity) {
        val previous = previousScreenBrightness[activity] ?: return
        activity.window.attributes = activity.window.attributes.apply {
            screenBrightness = previous
        }
    }
}
