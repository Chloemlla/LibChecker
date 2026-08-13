plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.parcelize)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.protobuf)
  alias(libs.plugins.hiddenApiRefine)
  alias(libs.plugins.ksp)
  alias(libs.plugins.androidX.room3)
  alias(libs.plugins.moshiX)
  alias(libs.plugins.aboutlibraries)
  id("build-logic")
  id("res-opt")
}

ksp {
  arg("moshi.generated", "javax.annotation.Generated")
}

room3 {
  schemaDirectory("$projectDir/schemas")
}

setupAppModule {
  namespace = "com.absinthe.libchecker"
  defaultConfig {
    applicationId = "com.absinthe.libchecker"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildFeatures {
    aidl = true
    buildConfig = true
    viewBinding = true
    compose = true
  }

  buildTypes {
    release {
      optimization {
        enable = true
        keepRules {
          // https://github.com/AppDevNext/AndroidChart/blob/master/chartLib/proguard-lib.pro
          ignoreFrom(libs.mpAndroidChart.get().module.toString())
        }
      }
    }
    create("benchmark") {
      initWith(getByName("release"))
      applicationIdSuffix = ".debug"
      matchingFallbacks += listOf("release")
      proguardFiles("src/benchmark/keepRules/proguard-rules.keep")
      signingConfig = signingConfigs.getByName("debug")
    }
  }

  productFlavors {
    flavorDimensions += "channel"

    create("foss") {
      isDefault = true
      dimension = flavorDimensions[0]
      buildConfigField("Boolean", "IS_FOSS", "true")
    }
    configureEach {
      manifestPlaceholders["channel"] = this.name
    }
  }

  packaging {
    jniLibs {
      excludes += "lib/**/libdatastore_shared_counter.so" // Jetpack DataStore
    }
    resources {
      excludes += setOf(
        "META-INF/**",
        "okhttp3/**",
        "kotlin/**",
        "org/**",
        "**.properties",
        "**.bin",
        "**/*.proto"
      )
    }
  }

  lint {
    disable += setOf("AppCompatResource", "MissingTranslation")
  }

  dependenciesInfo.includeInApk = false
}

androidComponents {
  onVariants { variant ->
    variant.outputs.forEach { output ->
      output.outputFileName.set(
        output.versionName.zip(output.versionCode) { versionName, versionCode ->
          "LibChecker-$versionName-$versionCode-${variant.buildType}.apk"
        }
      )
    }
  }
}

// Lumen Crash SDK: version resolved from gradle property, env var, or local file (never hardcoded)
val lumenCrashVersion: String =
  providers.gradleProperty("lumenCrashVersion").orNull?.takeIf { it.isNotBlank() }
    ?: providers.environmentVariable("LUMEN_CRASH_VERSION").orNull?.takeIf { it.isNotBlank() }
    ?: runCatching { rootProject.file("lumen-crash.resolved.version").readText().trim() }
      .getOrNull()?.takeIf { it.isNotBlank() }
    ?: error("Resolve latest lumen-crash SDK first: run .github/scripts/fetch-lumen-crash-sdk.py")

dependencies {
  compileOnly(dependencies.project(":hidden-api"))

  implementation(projects.compat)
  implementation(libs.kotlinX.coroutines)
  implementation(platform(libs.koin.bom))
  implementation(libs.koin.android)
  implementation(libs.androidX.core)
  implementation(libs.androidX.activity)
  implementation(libs.androidX.activityCompose)
  implementation(libs.androidX.fragment)
  implementation(libs.androidX.constraintLayout)
  implementation(libs.androidX.browser)
  implementation(libs.androidX.viewPager2)
  implementation(libs.androidX.recyclerView)
  implementation(libs.androidX.preference)
  implementation(libs.androidX.window)
  implementation(libs.bundles.androidX.lifecycle)
  implementation(libs.bundles.androidX.room3)
  implementation(libs.google.material)
  implementation(libs.coil)
  implementation(libs.coil.svg)
  implementation(libs.square.okHttp)
  implementation(libs.square.okio)
  implementation(libs.square.retrofit)
  implementation(libs.square.retrofit.moshi)
  implementation(libs.square.moshi)
  implementation(libs.google.protobuf.javaLite)
  implementation(libs.google.dexlib2)
  implementation(libs.rikka.refine.runtime)
  implementation(libs.bundles.zhaobozhen)
  implementation(libs.lc.rules)
  ksp(libs.androidX.room3.compiler)

  testImplementation(libs.junit)

  androidTestImplementation(libs.androidX.test.ext.junit)
  androidTestImplementation(libs.androidX.test.runner)

  implementation(libs.aboutlibraries.core)
  implementation(libs.aboutlibraries.ui)
  implementation(libs.brvah)
  implementation(libs.mpAndroidChart)
  implementation(libs.timber)
  implementation(libs.processPhoenix)
  implementation(libs.once)
  implementation(libs.fastScroll)
  implementation(libs.appIconLoader)
  implementation(libs.appIconLoader.coil)
  implementation(libs.hiddenApiBypass)
  implementation(libs.commons.compress)
  implementation(libs.flexbox)

  implementation(libs.bundles.rikkax)

  implementation(libs.bundles.shizuku)

  // Lumen Crash SDK (Compose-based crash UI): Compose BOM + bundle. The SDK AAR
  // ships consumer R8 keep rules (proguard.txt) that AGP merges automatically.
  implementation(platform(libs.compose.bom))
  implementation("com.chloemlla.lumen:lumen-crash:$lumenCrashVersion")
}

protobuf {
  protoc {
    artifact = if (osdetector.os == "osx") {
      // support both Apple Silicon and Intel chipsets
      val arch = System.getProperty("os.arch")
      val suffix = if (arch == "x86_64") "x86_64" else "aarch_64"
      "${libs.google.protobuf.protoc.get()}:osx-$suffix"
    } else {
      libs.google.protobuf.protoc.get().toString()
    }
  }
  plugins {
    generateProtoTasks {
      all().configureEach {
        builtins {
          create("java") {
            option("lite")
          }
        }
      }
    }
  }
}
