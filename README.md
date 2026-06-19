# Osmos Display Banner Ads Integration Demo App

A production-ready Android application in Kotlin that integrates the Osmos Ads SDK, fetches display banner ads dynamically, handles aspect-ratio-correct rendering, tracks on-screen impressions using a custom visibility tracker (50% visibility rule), handles clicks, and displays interactive real-time event logs.

---

## Architecture Overview

This app follows clean design principles using the **MVVM (Model-View-ViewModel)** architectural pattern:

```
┌───────────────────────────────────────────────────────────────┐
│                          MainActivity                         │
│   (Binds UI, manages lifecycle, renders views with Glide)     │
└───────────────┬───────────────────────────────▲───────────────┘
                │ Registers visibility          │ Collects StateFlows
                │ callbacks                     │ (AdState, EventLogs)
┌───────────────▼───────────────────────────────┴───────────────┐
│                        AdViewModel                            │
│  (Manages state machine, auto-retries, event registrations)   │
└───────────────┬───────────────────────────────▲───────────────┘
                │ Fetches & Registers           │ Returns Parsed
                │ Events                        │ AdData Models
┌───────────────▼───────────────────────────────┴───────────────┐
│           Osmos SDK & AdParser / VisibilityTracker             │
│ (SDK networking, resilient JSON parsing, visibility triggers) │
└───────────────────────────────────────────────────────────────┘
```

### Components Description

1. **`MyApplication`**: Set up at application startup. Initializes `OsmosSDK` globally using the `buildGlobalInstance()` builder.
2. **`MainActivity`**: Manages the UI layout, binds views, collects state from the ViewModel, and adjusts the banner container dynamically to maintain the ad's aspect ratio. Handles Android lifecycle states and screen rotations.
3. **`AdViewModel`**: Manages UI state flow (`AdState`), keeps an interactive buffer of live logs (`eventLogs`), handles duplicate fetch prevention, implements a 3-attempt auto-retry fetching mechanism, and manages event registration with the Osmos SDK.
4. **`VisibilityTracker`**: A reusable, lifecycle-aware visibility helper. It attaches layout and scroll listeners to the ad view's parent container and calculates the visible pixel area. It fires the impression trigger when $\geq 50\%$ visibility is reached, exactly once per ad load.
5. **`AdParser`**: Decouples response parsing from the ViewModel. Performs resilient parsing using Gson to support both standard Map elements and List elements safely, preventing crashes if the SDK returns slightly different JSON schemas.
6. **`AdData`**: Model class representing the parsed ad parameters (images, URLs, tracking URLs, dimensions, uclid).

---

## Core Feature Implementations

### 1. SDK Initialization
We initialize the Osmos SDK globally in the custom [MyApplication](app/src/main/java/com/wikey/osmossdk/MyApplication.kt) class:
```kotlin
OsmosSDK.clientId("10088010")
    .displayAdsHost("demo-ba.o-s.io")
    .productAdsHost("demo.o-s.io")
    .debug(true)
    .buildGlobalInstance()
```

### 2. Resilient Ad Fetching & Parsing
We use the SDK's `fetchDisplayAdsWithAu` within a coroutine inside `AdViewModel`. 
- **Auto-Retries**: If a fetch fails due to network issues or empty responses, the ViewModel automatically retries up to 3 times with a delay: `1500L * (attempt - 1)` before transitioning to the error state.
- **Robust Parsing**: Our `AdParser` serializes the response to a JSON string and parses it to a tree map. This guarantees that whether the SDK returns elements as a `Map` (e.g. `elements: { value: "url" }`) or a `List` (e.g. `elements: [{ value: "url" }]`), it parses correctly without throwing exceptions.

### 3. Aspect-Ratio-Preserved Banner Rendering
To prevent banner stretching or visual distortion, we dynamically compute and set the banner container's height on successful ad loading:
$$\text{Calculated Height} = \frac{\text{Container Width} \times \text{Ad Height}}{\text{Ad Width}}$$
This matches the exact aspect ratio of the image loaded via **Glide**.

### 4. 50% Visibility Tracking (Impression)
Our `VisibilityTracker` implements layout and scroll listeners on the view hierarchy. When active, it computes:
```kotlin
val rect = Rect()
val isVisible = view.getLocalVisibleRect(rect)
if (isVisible) {
    val visibleArea = rect.width().toLong() * rect.height().toLong()
    val totalArea = view.width.toLong() * view.height.toLong()
    val ratio = visibleArea.toFloat() / totalArea.toFloat()
    if (ratio >= 0.5f) {
        // Trigger impression once
    }
}
```
This checks if at least 50% of the ad's actual pixels are inside the visible window viewport. Once the threshold is met, it fires the impression event:
- It calls `OsmosSDK.globalInstance().registerEvent().registerAdImpressionEvent(...)`.
- It logs the event, updates the StateFlow, and overlays a green badge on the UI indicating **"✓ 50% Visible - Impression Registered"**.
- It ensures the event is fired **only once** per ad load.

### 5. Click Registration & Navigation
On ad click, we execute two operations:
1. Open the landing URL (`destinationUrl`) in an external browser using an `ACTION_VIEW` intent.
2. Fire the click event via the SDK's `registerEvent().registerAdClickEvent(...)` or `registerAClickEvent(...)` (for offsite ads containing `aclick` query patterns).

---

## Technical Challenges & Solutions

1. **SDK Package Names Difference**: 
   - *Challenge*: The reference documentation stated that the package name is `com.ai.onlinesales.core.OsmosSDK`. However, compiling with version `2.6.0` threw `Unresolved reference 'onlinesales'`.
   - *Solution*: We extracted the `.aar` package classes using Gradle/PowerShell and discovered that the actual package name deployed on Maven Central is `com.ai.osmos.core.OsmosSDK`. We updated the imports accordingly, resolving the build failures.
2. **AGP 9.0+ Built-in Kotlin conflict**:
   - *Challenge*: Adding `org.jetbrains.kotlin.android` plugin explicitly to the plugins block resulted in a Gradle configuration error: `Cannot add extension with name 'kotlin', as there is an extension already registered with that name.`
   - *Solution*: AGP 9.0+ has built-in Kotlin support and automatically registers the Kotlin extension. We removed the explicit plugin application from our build files, allowing AGP to configure compilation out-of-the-box.
3. **Resilience to SDK Network Failures**:
   - *Challenge*: If the SDK's tracking event registration fails (e.g. network timeout or invalid SDK state), tracking could fail.
   - *Solution*: We added a network-level fallback inside `AdViewModel`. If the SDK event call throws an exception, the ViewModel automatically performs a direct HTTP GET request to the `impression_tracking_url` or `click_tracking_url` to guarantee that the events are delivered.

---

## How to Run the App

### Prerequisites
- Android Studio installed.
- Android device/emulator running API 25 (Android 7.0) or higher.
- Active Internet connection.

### Steps
1. Clone this repository:
   ```bash
   git clone https://github.com/zriousdev/osmosSdk.git
   ```
2. Open the project in Android Studio.
3. Sync Gradle dependencies.
4. Run the project on an emulator or connected device.
5. Tap the **Load Ad** button to fetch and display the ad.
6. Verify the logs console updates dynamically showing the ad loading lifecycle and visibility states.
