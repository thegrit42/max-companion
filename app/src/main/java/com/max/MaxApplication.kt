package com.max

import android.app.Application

/**
 * Max Application.
 * Initializes core components on app start.
 */
class MaxApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        // Initialize any global state here
        // Max is designed to be local-first, so no remote initialization
    }
}
