package com.example.util

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

object LoroFirebaseLogger {

    private var analytics: FirebaseAnalytics? = null
    private var crashlytics: FirebaseCrashlytics? = null

    fun initialize(context: Context) {
        try {
            analytics = FirebaseAnalytics.getInstance(context)
        } catch (e: Exception) {
            android.util.Log.w("LoroFirebaseLogger", "Firebase Analytics initialization bypassed: ${e.message}")
        }
        try {
            crashlytics = FirebaseCrashlytics.getInstance()
        } catch (e: Exception) {
            android.util.Log.w("LoroFirebaseLogger", "Firebase Crashlytics initialization bypassed: ${e.message}")
        }
    }

    fun logNonFatal(throwable: Throwable, message: String? = null) {
        android.util.Log.e("LoroFirebaseLogger", "Non-Fatal Logged: $message", throwable)
        try {
            message?.let { crashlytics?.log(it) }
            crashlytics?.recordException(throwable)
        } catch (e: Exception) {
            // Graceful fallback
        }
    }

    fun logEvent(name: String, params: Bundle = Bundle()) {
        android.util.Log.i("LoroFirebaseLogger", "Event Logged: $name with parameters: $params")
        try {
            analytics?.logEvent(name, params)
        } catch (e: Exception) {
            // Graceful fallback
        }
    }
}
