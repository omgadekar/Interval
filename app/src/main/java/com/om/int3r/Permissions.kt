package com.om.int3r

import android.Manifest
import android.os.Build

object Permissions {
    /**
     * Wear OS 6 (Android 16) replaced BODY_SENSORS with a dedicated heart-rate permission. Checking the
     * old name there reports "denied" until the app restarts, even right after you allowed it.
     */
    val HEART_RATE: String =
        if (Build.VERSION.SDK_INT >= 36) "android.permission.health.READ_HEART_RATE" else Manifest.permission.BODY_SENSORS

    val REQUIRED: Array<String> = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        add(HEART_RATE)
        add(Manifest.permission.ACTIVITY_RECOGNITION)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()
}