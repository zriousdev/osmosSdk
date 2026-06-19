package com.wikey.osmossdk

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val viewModel: AdViewModel by viewModels()
    private var visibilityTracker: VisibilityTracker? = null

    // UI View references
    private lateinit var cardAdContainer: View
    private lateinit var adRenderArea: FrameLayout
    private lateinit var ivAdBanner: ImageView
    private lateinit var progressBarAd: ProgressBar
    private lateinit var layoutFallback: LinearLayout
    private lateinit var tvFallbackText: TextView
    private lateinit var layoutImpressionOverlay: LinearLayout
    private lateinit var btnLoadAd: Button
    private lateinit var btnClearLogs: Button
    private lateinit var tvConsoleLogs: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()
        observeViewModel()
    }

    private fun initViews() {
        cardAdContainer = findViewById(R.id.card_ad_container)
        adRenderArea = findViewById(R.id.ad_render_area)
        ivAdBanner = findViewById(R.id.iv_ad_banner)
        progressBarAd = findViewById(R.id.progress_bar_ad)
        layoutFallback = findViewById(R.id.layout_fallback)
        tvFallbackText = findViewById(R.id.tv_fallback_text)
        layoutImpressionOverlay = findViewById(R.id.layout_impression_overlay)
        btnLoadAd = findViewById(R.id.btn_load_ad)
        btnClearLogs = findViewById(R.id.btn_clear_logs)
        tvConsoleLogs = findViewById(R.id.tv_console_logs)
    }

    private fun setupListeners() {
        btnLoadAd.setOnClickListener {
            viewModel.loadAd()
        }

        btnClearLogs.setOnClickListener {
            viewModel.clearLogs()
        }
    }

    private fun observeViewModel() {
        // Collect state in lifecycle-aware manner
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.adState.collect { state ->
                        handleAdState(state)
                    }
                }

                launch {
                    viewModel.eventLogs.collect { logs ->
                        val formattedLogs = if (logs.isEmpty()) {
                            "Console initialized...\nWaiting for events..."
                        } else {
                            logs.joinToString("\n")
                        }
                        tvConsoleLogs.text = formattedLogs
                    }
                }

                launch {
                    viewModel.isImpressionRegistered.collect { registered ->
                        if (registered) {
                            layoutImpressionOverlay.visibility = View.VISIBLE
                        } else {
                            layoutImpressionOverlay.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    private fun handleAdState(state: AdState) {
        // Stop current visibility tracker if active
        visibilityTracker?.stop()
        visibilityTracker = null

        when (state) {
            is AdState.Idle -> {
                progressBarAd.visibility = View.GONE
                ivAdBanner.visibility = View.GONE
                layoutFallback.visibility = View.VISIBLE
                tvFallbackText.text = "No ad loaded. Tap Load Ad."
                resetAdContainerHeight()
            }
            is AdState.Loading -> {
                progressBarAd.visibility = View.VISIBLE
                ivAdBanner.visibility = View.GONE
                layoutFallback.visibility = View.GONE
                resetAdContainerHeight()
            }
            is AdState.Success -> {
                progressBarAd.visibility = View.GONE
                ivAdBanner.visibility = View.VISIBLE
                layoutFallback.visibility = View.GONE

                val ad = state.ad
                
                // Adjust container size dynamically to maintain aspect ratio
                adjustContainerAspectRatio(ad.width, ad.height, adRenderArea)

                // Load banner image using Glide
                Glide.with(this)
                    .load(ad.imageUrl)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_dialog_alert)
                    .into(ivAdBanner)

                // Set up click handler
                ivAdBanner.setOnClickListener {
                    viewModel.fireClick(ad)
                    openLandingPage(ad.destinationUrl)
                }

                // Initialize visibility tracking for this success ad
                visibilityTracker = VisibilityTracker(
                    view = ivAdBanner,
                    threshold = 0.5f,
                    onVisible = {
                        viewModel.fireImpression(ad)
                    }
                ).apply {
                    start()
                }
            }
            is AdState.Error -> {
                progressBarAd.visibility = View.GONE
                ivAdBanner.visibility = View.GONE
                layoutFallback.visibility = View.VISIBLE
                tvFallbackText.text = "Ad not available\n(Error: ${state.message})"
                resetAdContainerHeight()
            }
            is AdState.Empty -> {
                progressBarAd.visibility = View.GONE
                ivAdBanner.visibility = View.GONE
                layoutFallback.visibility = View.VISIBLE
                tvFallbackText.text = "Ad not available"
                resetAdContainerHeight()
            }
        }
    }

    private fun adjustContainerAspectRatio(adWidth: Int, adHeight: Int, container: View) {
        if (adWidth <= 0 || adHeight <= 0) return
        container.post {
            val containerWidth = container.width
            if (containerWidth > 0) {
                val calculatedHeight = (containerWidth.toFloat() * adHeight.toFloat() / adWidth.toFloat()).toInt()
                val lp = container.layoutParams
                lp.height = calculatedHeight
                container.layoutParams = lp
            }
        }
    }

    private fun resetAdContainerHeight() {
        adRenderArea.post {
            val lp = adRenderArea.layoutParams
            // Reset to default height defined in layout (180dp converted to pixels, or just direct default value)
            val density = resources.displayMetrics.density
            lp.height = (180 * density).toInt()
            adRenderArea.layoutParams = lp
        }
    }

    private fun openLandingPage(url: String) {
        if (url.isBlank()) return
        try {
            val parsedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                "https://$url"
            } else {
                url
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(parsedUrl))
            startActivity(intent)
        } catch (e: Exception) {
            viewModel.logEvent("Failed to open destination landing page: ${e.message}")
        }
    }

    override fun onStart() {
        super.onStart()
        visibilityTracker?.start()
    }

    override fun onStop() {
        super.onStop()
        visibilityTracker?.stop()
    }
}
