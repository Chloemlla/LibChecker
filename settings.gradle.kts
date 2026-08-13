pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex(".*google.*")
        includeGroupByRegex(".*android.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }

  includeBuild("build-logic")
}

dependencyResolutionManagement {
  repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
  repositories {
    // Lumen Crash SDK local-maven staging (must precede GitHub Packages)
    maven {
      name = "LumenCrashLocal"
      url = uri(rootDir.resolve("local-maven"))
    }
    // Lumen Crash SDK on GitHub Packages (conditional: empty credentials skip)
    val gprUser = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
    val gprKey = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
    if (!gprUser.isNullOrBlank() && !gprKey.isNullOrBlank()) {
      maven {
        name = "GitHubPackagesProjectLumen"
        url = uri("https://maven.pkg.github.com/Chloemlla/Project-Lumen")
        credentials {
          username = gprUser
          password = gprKey
        }
      }
    }
    google {
      content {
        includeGroupByRegex(".*google.*")
        includeGroupByRegex(".*android.*")
      }
    }
    maven("https://jitpack.io") {
      content {
        includeGroupByRegex("com.github.*")
      }
    }
    mavenCentral()
  }
}

plugins {
  id("com.gradle.develocity") version "4.5.0"
  id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

develocity {
  buildScan {
    termsOfUseUrl = "https://gradle.com/terms-of-service"
    termsOfUseAgree = "yes"
    // TODO: workaround for https://github.com/gradle/gradle/issues/22879.
    val isCI = providers.environmentVariable("CI").isPresent
    publishing.onlyIf { isCI }
  }
}

enableFeaturePreview("STABLE_CONFIGURATION_CACHE")
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
enableFeaturePreview("NO_IMPLICIT_LOOKUP_IN_PARENT_PROJECTS")

include(":app", ":compat", ":hidden-api", ":macrobenchmark")

rootProject.name = "LibChecker"
