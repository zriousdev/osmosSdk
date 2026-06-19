package com.wikey.osmossdk

import android.app.Application
import android.util.Log
import com.ai.osmos.core.OsmosSDK

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            // Initialize Osmos SDK globally
            OsmosSDK.clientId("10088010")
                .displayAdsHost("demo-ba.o-s.io")
                .productAdsHost("demo.o-s.io")
                .debug(true)
                .buildGlobalInstance()
            Log.d("MyApplication", "OsmosSDK global instance initialized successfully")
        } catch (e: Exception) {
            Log.e("MyApplication", "OsmosSDK initialization failed: ${e.message}", e)
        }
    }
}
