package com.absinthe.libchecker.domain.home.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import com.chloemlla.lumen.crash.LumenCrash
import com.chloemlla.lumen.crash.ui.LumenCrashGate
import timber.log.Timber

/**
 * Dedicated Compose-only surface for the Lumen Crash SDK pending-report UI.
 *
 * This is the only Compose activity in the app: LibChecker is an XML/ViewBinding
 * host, but the crash SDK ships an adaptive Material3 report screen, and the SDK
 * explicitly recommends a dedicated report Activity for multi-activity hosts.
 *
 * The whole render path is fail-soft: if loading or rendering the report fails,
 * the activity clears the pending report and restarts the launcher normally so a
 * crash-report surface problem can never block startup.
 */
class LumenCrashReportActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    runCatching { enableEdgeToEdge() }

    // Back on the crash report screen discards the pending report and restarts
    // the launcher cleanly: the partially created MainActivity left below this
    // surface by the startup gate cannot host normal UI.
    onBackPressedDispatcher.addCallback(this) {
      runCatching { LumenCrash.clearPendingReport() }
      restartLauncher()
    }

    val report = runCatching { LumenCrash.loadPendingReportSafely() }.getOrNull()
    if (report == null) {
      Timber.w("LumenCrashReportActivity: no pending crash report to show")
      restartLauncher()
      return
    }

    val opened = runCatching {
      setContent {
        // The crash report UI is the host's first rendered frame while a report
        // is pending; stop the startup-hang watchdog so it does not emit a
        // spurious STARTUP_HANG report while the user reviews the crash.
        LaunchedEffect(report.reportId) {
          runCatching { LumenCrash.markStartupComplete() }
        }
        LumenCrashGate(
          initialReport = report,
          clearStoredReportOnContinue = true,
          onContinue = {
            // Report screen already clears storage via clearStoredReportOnContinue;
            // clear again defensively, then restart the launcher cleanly.
            runCatching { LumenCrash.clearPendingReport() }
            restartLauncher()
          },
        ) {
          // Gate content is only composed when there is no pending report.
          // Unreachable here because we restart the launcher when report == null.
        }
      }
      true
    }.getOrDefault(false)

    if (!opened) {
      // Fail-soft: never let the crash report surface break startup.
      Timber.w("LumenCrashReportActivity: failed to render crash report UI")
      runCatching { LumenCrash.clearPendingReport() }
      restartLauncher()
    }
  }

  /**
   * Starts [MainActivity] in a fresh, empty task (clearing any partially created
   * launcher left behind by the startup gate) and finishes this Activity.
   */
  private fun restartLauncher() {
    runCatching {
      startActivity(
        Intent(this, MainActivity::class.java).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
      )
    }
    finish()
  }
}
