package tools.interviews.android.util

import android.app.Activity
import android.content.pm.ActivityInfo
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Manages screen orientation based on device fold state.
 *
 * - Candybar (folded) mode: Locks to portrait orientation
 * - Tablet (unfolded/flat) mode: Allows free rotation
 *
 * Usage: Call [attach] in onCreate and the manager will automatically
 * handle lifecycle events and fold state changes.
 */
class FoldableOrientationManager(private val activity: Activity) {

    companion object {
        private const val TAG = "FoldableOrientationMgr"
    }

    private var windowInfoJob: Job? = null
    private var isUnfolded = false

    /**
     * Attaches the manager to the activity lifecycle.
     * Call this in onCreate after setContentView.
     */
    fun attach(lifecycleOwner: LifecycleOwner) {
        // Start with portrait locked (candybar mode default)
        lockToPortrait()

        lifecycleOwner.lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                when (event) {
                    Lifecycle.Event.ON_START -> startObserving()
                    Lifecycle.Event.ON_STOP -> stopObserving()
                    Lifecycle.Event.ON_DESTROY -> {
                        stopObserving()
                        source.lifecycle.removeObserver(this)
                    }
                    else -> {}
                }
            }
        })
    }

    private fun startObserving() {
        windowInfoJob = CoroutineScope(Dispatchers.Main).launch {
            WindowInfoTracker.getOrCreate(activity)
                .windowLayoutInfo(activity)
                .collectLatest { layoutInfo ->
                    handleLayoutInfo(layoutInfo)
                }
        }
    }

    private fun stopObserving() {
        windowInfoJob?.cancel()
        windowInfoJob = null
    }

    private fun handleLayoutInfo(layoutInfo: WindowLayoutInfo) {
        val foldingFeature = layoutInfo.displayFeatures
            .filterIsInstance<FoldingFeature>()
            .firstOrNull()

        if (foldingFeature != null) {
            // Device has a folding feature
            when (foldingFeature.state) {
                FoldingFeature.State.FLAT -> {
                    // Tablet/unfolded mode - allow rotation
                    Log.d(TAG, "Device unfolded (FLAT) - allowing rotation")
                    isUnfolded = true
                    allowRotation()
                }
                FoldingFeature.State.HALF_OPENED -> {
                    // Half-opened (tent/laptop mode) - allow rotation for flexibility
                    Log.d(TAG, "Device half-opened - allowing rotation")
                    isUnfolded = true
                    allowRotation()
                }
                else -> {
                    // Unknown state - default to portrait
                    Log.d(TAG, "Unknown fold state - locking to portrait")
                    isUnfolded = false
                    lockToPortrait()
                }
            }
        } else {
            // No folding feature detected - device is in candybar mode or not a foldable
            if (isUnfolded) {
                // Was unfolded, now folded - lock to portrait
                Log.d(TAG, "Device folded (candybar mode) - locking to portrait")
                isUnfolded = false
                lockToPortrait()
            }
            // If never unfolded, keep current orientation lock
        }
    }

    private fun lockToPortrait() {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    private fun allowRotation() {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
}