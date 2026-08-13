package com.absinthe.libchecker

import android.app.Application
import android.content.Context
import android.content.pm.PackageParser
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.window.embedding.RuleController
import androidx.window.embedding.SplitController
import coil.Coil
import coil.ImageLoader
import coil.decode.SvgDecoder
import com.absinthe.libchecker.app.MainLooperFilter
import com.absinthe.libchecker.constant.GlobalValues
import com.absinthe.libchecker.database.RulesRepository
import com.absinthe.libchecker.di.appDetailModule
import com.absinthe.libchecker.di.appListModule
import com.absinthe.libchecker.di.appModule
import com.absinthe.libchecker.di.rulesModule
import com.absinthe.libchecker.di.settingsModule
import com.absinthe.libchecker.di.snapshotBackupModule
import com.absinthe.libchecker.di.snapshotComparisonModule
import com.absinthe.libchecker.di.snapshotCoreModule
import com.absinthe.libchecker.di.snapshotDisplayModule
import com.absinthe.libchecker.di.snapshotListModule
import com.absinthe.libchecker.di.snapshotTimeNodeModule
import com.absinthe.libchecker.di.snapshotTrackModule
import com.absinthe.libchecker.di.statisticsChartModule
import com.absinthe.libchecker.di.statisticsReferenceModule
import com.absinthe.libchecker.utils.OsUtils
import com.absinthe.libchecker.utils.Telemetry
import com.absinthe.libchecker.utils.UiUtils
import com.absinthe.libchecker.utils.extensions.dp
import com.absinthe.libchecker.utils.timber.FileLoggingTree
import com.absinthe.libchecker.utils.timber.ReleaseTree
import com.absinthe.libchecker.utils.timber.ThreadAwareDebugTree
import com.absinthe.libraries.utils.utils.Utility
import com.chloemlla.lumen.crash.LumenCrash
import com.google.android.material.color.DynamicColors
import com.jakewharton.processphoenix.ProcessPhoenix
import java.io.File
import jonathanfinerty.once.Once
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import me.zhanghai.android.appiconloader.coil.AppIconFetcher
import me.zhanghai.android.appiconloader.coil.AppIconKeyer
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.lsposed.hiddenapibypass.HiddenApiBypass
import timber.log.Timber

class LibCheckerApp : Application() {

  private val appScope = MainScope()

  override fun onCreate() {
    super.onCreate()

    if (ProcessPhoenix.isPhoenixProcess(this)) {
      return
    }

    bypass()

    app = this

    if (BuildConfig.DEBUG) {
      Timber.plant(ThreadAwareDebugTree())
    } else {
      Timber.plant(ReleaseTree())
    }
    Timber.plant(FileLoggingTree(this))
    startKoin {
      androidLogger()
      androidContext(this@LibCheckerApp)
      modules(
        appModule,
        appDetailModule,
        appListModule,
        rulesModule,
        snapshotCoreModule,
        snapshotComparisonModule,
        snapshotBackupModule,
        snapshotDisplayModule,
        snapshotListModule,
        snapshotTimeNodeModule,
        snapshotTrackModule,
        statisticsChartModule,
        statisticsReferenceModule,
        settingsModule
      )
    }
    Telemetry.setEnable(GlobalValues.isAnonymousAnalyticsEnabled)
    RulesRepository.init(this)
    Utility.init(this)
    if (OsUtils.atLeastT()) {
      AppCompatDelegate.setApplicationLocales(LocaleListCompat.create(GlobalValues.locale))
    }
    AppCompatDelegate.setDefaultNightMode(UiUtils.getNightMode())
    Once.initialise(this)
    DynamicColors.applyToActivitiesIfAvailable(this)
    initSplitController()

    Coil.setImageLoader {
      ImageLoader.Builder(this)
        .crossfade(true)
        .components {
          add(SvgDecoder.Factory())
          add(AppIconKeyer())
          add(AppIconFetcher.Factory(40.dp, false, this@LibCheckerApp))
        }
        .build()
    }
    appScope.launch(Dispatchers.IO) {
      clearCache()
    }
  }

  override fun attachBaseContext(base: Context?) {
    super.attachBaseContext(base)
    // LumenCrash must be the first host work after super, before any bootstrap
    // that can throw (see lumen-crash README "field lesson: cold-start
    // flash-exit / wrong install order"). Fail-soft: an SDK install failure
    // must never break app startup.
    if (!LumenCrash.isInstalled()) {
      val installed = LumenCrash.installSafely(this) {
        appDisplayName = this@LibCheckerApp.getString(R.string.app_name)
        versionName = BuildConfig.VERSION_NAME
        versionCode = BuildConfig.VERSION_CODE
        anrWatchdogEnabled = true
        anrWatchdogTimeoutMillis = 5_000L
        anrWatchdogCheckIntervalMillis = 1_000L
        startupHangWatchdogEnabled = true
        startupHangTimeoutMillis = 15_000L
        // foss privacy: keep all crash data on-device, never POST to the
        // crash-report backend.
        crashReportBackendEnabled = false
        onReportSaved = { report ->
          Timber.w("LumenCrash report saved: kind=${report.kind} rootCause=${report.rootCause}")
        }
        onAnrDetected = { report ->
          Timber.w("LumenCrash ANR/freeze detected: kind=${report.kind} durationMillis=${report.durationMillis}")
        }
      }
      if (!installed) {
        Timber.w("LumenCrash SDK failed to install")
      }
    }
    MainLooperFilter.start()
  }

  private fun initSplitController() {
    val ratio = UiUtils.getScreenAspectRatio()
    val hasHinge = UiUtils.hasHinge()
    val splitSupportStatus = SplitController.getInstance(this).splitSupportStatus
    Timber.d("initSplitController: getScreenAspectRatio: $ratio, hasHinge=$hasHinge, splitSupportStatus=$splitSupportStatus")
    runCatching {
      if (splitSupportStatus == SplitController.SplitSupportStatus.SPLIT_AVAILABLE) {
        RuleController.getInstance(this).setRules(
          if (hasHinge || ratio in 0.85f..1.15f) {
            RuleController.parseRules(this, R.xml.main_split_config_foldable)
          } else {
            RuleController.parseRules(this, R.xml.main_split_config)
          }
        )
      }
    }
  }

  @Suppress("SoonBlockedPrivateApi, DiscouragedPrivateApi")
  private fun bypass() {
    if (OsUtils.atLeastP()) {
      HiddenApiBypass.addHiddenApiExemptions("")
    }

    runCatching {
      // bypass PackageParser check
      // see also: https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/core/java/android/content/pm/PackageParser.java;l=2695
      PackageParser::class.java.getDeclaredField("SDK_VERSION").apply {
        isAccessible = true
        set(null, Integer.MAX_VALUE)
      }
    }.onFailure {
      Timber.w("bypass [PackageParser check] failed")
    }
  }

  private fun clearCache() {
    File(cacheDir, "shared_apk").takeIf { it.isDirectory }?.deleteRecursively()
    File(cacheDir, "shared_snapshot_reports").takeIf { it.isDirectory }?.deleteRecursively()
  }

  companion object {
    //noinspection StaticFieldLeak
    lateinit var app: Application
  }
}
