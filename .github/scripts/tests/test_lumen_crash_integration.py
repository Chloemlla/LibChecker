import sys
import unittest
from pathlib import Path

sys.dont_write_bytecode = True
REPO_ROOT = Path(__file__).resolve().parents[3]


class LumenCrashIntegrationTest(unittest.TestCase):
  """Checks that the Lumen Crash SDK build-system wiring is in place.

  These are content assertions for the build/config files the main session is
  responsible for. They run without Gradle or network access.
  """

  def test_settings_registers_local_maven_and_github_packages(self):
    settings = (REPO_ROOT / "settings.gradle.kts").read_text(encoding="utf-8")
    self.assertIn("LumenCrashLocal", settings)
    self.assertIn("local-maven", settings)
    self.assertIn("GitHubPackagesProjectLumen", settings)
    self.assertIn("maven.pkg.github.com/Chloemlla/Project-Lumen", settings)

  def test_app_declares_lumen_crash_dependency_and_compose_bom(self):
    app_build = (REPO_ROOT / "app" / "build.gradle.kts").read_text(encoding="utf-8")
    self.assertIn("compose = true", app_build)
    self.assertIn("com.chloemlla.lumen:lumen-crash:$lumenCrashVersion", app_build)
    self.assertIn("lumenCrashVersion", app_build)
    self.assertIn("implementation(platform(libs.compose.bom))", app_build)

  def test_app_applies_compose_compiler_plugin(self):
    app_build = (REPO_ROOT / "app" / "build.gradle.kts").read_text(encoding="utf-8")
    self.assertIn("alias(libs.plugins.kotlin.compose)", app_build)
    catalog = (REPO_ROOT / "gradle" / "libs.versions.toml").read_text(encoding="utf-8")
    self.assertIn('kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose"', catalog)

  def test_version_catalog_has_compose_bom(self):
    catalog = (REPO_ROOT / "gradle" / "libs.versions.toml").read_text(encoding="utf-8")
    self.assertIn("compose-bom = \"2024.12.01\"", catalog)
    self.assertIn('module = "androidx.compose:compose-bom"', catalog)

  def test_fetch_script_exists_and_stages_local_maven(self):
    script = REPO_ROOT / ".github" / "scripts" / "fetch-lumen-crash-sdk.py"
    self.assertTrue(script.is_file())
    content = script.read_text(encoding="utf-8")
    self.assertIn("local-maven", content)
    self.assertIn("lumen-crash.resolved.version", content)
    self.assertIn("--print-version", content)

  def test_workflow_resolves_and_injects_lumen_crash_version(self):
    workflow = (REPO_ROOT / ".github" / "workflows" / "android.yml").read_text(encoding="utf-8")
    self.assertIn("fetch-lumen-crash-sdk.py", workflow)
    self.assertIn("LUMEN_CRASH_VERSION", workflow)
    self.assertIn("lumen-crash.resolved.version", workflow)

  def test_gitignore_excludes_staging_and_resolved_version(self):
    gitignore = (REPO_ROOT / ".gitignore").read_text(encoding="utf-8")
    self.assertIn("local-maven/", gitignore)
    self.assertIn("lumen-crash.resolved.version", gitignore)


if __name__ == "__main__":
  unittest.main()
