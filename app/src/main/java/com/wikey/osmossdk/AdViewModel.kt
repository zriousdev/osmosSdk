package com.wikey.osmossdk

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ai.osmos.core.OsmosSDK
import com.ai.osmos.utils.error.ErrorCallback
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AdViewModel(application: Application) : AndroidViewModel(application) {

    private val _adState = MutableStateFlow<AdState>(AdState.Idle)
    val adState: StateFlow<AdState> = _adState.asStateFlow()

    private val _eventLogs = MutableStateFlow<List<String>>(emptyList())
    val eventLogs: StateFlow<List<String>> = _eventLogs.asStateFlow()

    private val _isImpressionRegistered = MutableStateFlow(false)
    val isImpressionRegistered: StateFlow<Boolean> = _isImpressionRegistered.asStateFlow()

    private val gson = Gson()
    private var isFetching = false
    
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun logEvent(message: String) {
        val timestamp = timeFormat.format(Date())
        val formattedLog = "[$timestamp] $message"
        Log.d("OsmosAdViewModel", formattedLog)
        
        // Prepend new logs to display them at the top of the scrollable list
        _eventLogs.value = listOf(formattedLog) + _eventLogs.value
    }

    fun clearLogs() {
        _eventLogs.value = emptyList()
        logEvent("Logs cleared")
    }

    fun loadAd() {
        if (isFetching) {
            logEvent("Ignored fetch request: ad fetch already in progress")
            return
        }
        isFetching = true
        _adState.value = AdState.Loading
        logEvent("Ad fetch started")

        viewModelScope.launch {
            var attempt = 1
            val maxAttempts = 3
            var success = false

            while (attempt <= maxAttempts && !success) {
                if (attempt > 1) {
                    logEvent("Auto-retry attempt $attempt/$maxAttempts in progress...")
                    kotlinx.coroutines.delay(1500L * (attempt - 1))
                }

                try {
                    val sdk = OsmosSDK.globalInstance()
                    val adFetcher = sdk.adFetcherSDK()

                    var fetchError: String? = null
                    val response = adFetcher.fetchDisplayAdsWithAu(
                        cliUbid = "Any",
                        pageType = "demo_page",
                        productCount = 1,
                        adUnits = listOf("banner_ads"),
                        targetingParams = emptyList(),
                        errorCallback = object : ErrorCallback {
                            override fun onError(
                                errorCode: String,
                                errorMessage: String,
                                throwable: Throwable?
                            ) {
                                fetchError = errorMessage
                            }
                        }
                    )

                    if (fetchError != null) {
                        logEvent("Attempt $attempt failed: $fetchError")
                        attempt++
                        continue
                    }

                    val parsedAd = parseAdResponse(response)
                    if (parsedAd != null) {
                        success = true
                        _isImpressionRegistered.value = false // Reset impression trigger for new ad
                        _adState.value = AdState.Success(parsedAd)
                        logEvent("Ad Loaded successfully (UCLID: ${parsedAd.uclid})")
                    } else {
                        logEvent("Attempt $attempt failed: No valid ad data found in response")
                        attempt++
                    }
                } catch (e: Exception) {
                    logEvent("Attempt $attempt exception: ${e.message}")
                    attempt++
                }
            }

            if (!success) {
                _adState.value = AdState.Error("Ad not available after $maxAttempts attempts")
                logEvent("Ad Failed: Unable to fetch ad")
            }
            isFetching = false
        }
    }

    fun fireImpression(ad: AdData) {
        if (_isImpressionRegistered.value) return
        _isImpressionRegistered.value = true

        logEvent("50% visibility detected. Registering impression...")
        viewModelScope.launch {
            try {
                val sdk = OsmosSDK.globalInstance()
                val registerEvent = sdk.registerEvent()

                registerEvent.registerAdImpressionEvent(
                    cliUbid = "Any",
                    uclid = ad.uclid,
                    position = 1,
                    trackingParams = null,
                    errorCallback = object : ErrorCallback {
                        override fun onError(
                            errorCode: String,
                            errorMessage: String,
                            throwable: Throwable?
                        ) {
                            logEvent("Impression register failed: $errorMessage")
                        }
                    }
                )
                logEvent("Impression Fired successfully")
            } catch (e: Exception) {
                logEvent("Impression register exception: ${e.message}")
                // Fallback: ping tracking URL directly
                pingTrackingUrl(ad.impressionTrackingUrl, "Impression")
            }
        }
    }

    fun fireClick(ad: AdData) {
        logEvent("Registering click event...")
        viewModelScope.launch {
            try {
                val sdk = OsmosSDK.globalInstance()
                val registerEvent = sdk.registerEvent()

                if (ad.clickTrackingUrl.contains("aclick", ignoreCase = true)) {
                    registerEvent.registerAClickEvent(
                        cliUbid = "Any",
                        url = ad.clickTrackingUrl,
                        errorCallback = object : ErrorCallback {
                            override fun onError(
                                errorCode: String,
                                errorMessage: String,
                                throwable: Throwable?
                            ) {
                                logEvent("Click register (aclick) failed: $errorMessage")
                            }
                        }
                    )
                } else {
                    registerEvent.registerAdClickEvent(
                        cliUbid = "Any",
                        uclid = ad.uclid,
                        trackingParams = null,
                        errorCallback = object : ErrorCallback {
                            override fun onError(
                                errorCode: String,
                                errorMessage: String,
                                throwable: Throwable?
                            ) {
                                logEvent("Click register (uclid) failed: $errorMessage")
                            }
                        }
                    )
                }
                logEvent("Click Fired successfully")
            } catch (e: Exception) {
                logEvent("Click register exception: ${e.message}")
                // Fallback: ping tracking URL directly
                pingTrackingUrl(ad.clickTrackingUrl, "Click")
            }
        }
    }

    private suspend fun pingTrackingUrl(urlString: String, eventType: String) {
        if (urlString.isBlank()) return
        withContext(Dispatchers.IO) {
            try {
                val url = URL(urlString)
                val urlConnection = url.openConnection() as HttpURLConnection
                try {
                    urlConnection.connectTimeout = 5000
                    urlConnection.readTimeout = 5000
                    urlConnection.requestMethod = "GET"
                    val code = urlConnection.responseCode
                    logEvent("$eventType URL pinged successfully (HTTP $code)")
                } finally {
                    urlConnection.disconnect()
                }
            } catch (e: Exception) {
                logEvent("Failed to ping $eventType tracking URL: ${e.message}")
            }
        }
    }

    private fun parseAdResponse(response: Any?): AdData? {
        return AdParser.parseAdResponse(response)
    }
}
