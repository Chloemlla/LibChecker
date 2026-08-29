package com.absinthe.libchecker.domain.app.detail.backup

import org.junit.Assert.assertEquals
import org.junit.Test

class BackupRulesPolicyTest {

  private fun section(vararg rules: BackupRule) = BackupRulesSection(
    rules = rules.toList(),
    disableIfNoEncryptionCapabilities = null
  )

  private fun rule(action: BackupRuleAction, path: String) = BackupRule(
    action = action,
    domain = "file",
    path = path,
    requireFlags = null
  )

  @Test
  fun `include-only rules mean allowlist - every unlisted path is excluded`() {
    val s = section(
      rule(BackupRuleAction.INCLUDE, "mmkv/pili_plus_setting.mmkv"),
      rule(BackupRuleAction.INCLUDE, "mmkv/pili_plus_video.mmkv")
    )

    assertEquals(BackupPolicy.ALLOWLIST, s.policy)
  }

  @Test
  fun `exclude-only rules mean denylist - default backup set minus the listed exclusions`() {
    val s = section(rule(BackupRuleAction.EXCLUDE, "cache/"))

    assertEquals(BackupPolicy.DENYLIST, s.policy)
  }

  @Test
  fun `mixed include and exclude is still allowlist - include flips the default to opt-in`() {
    val s = section(
      rule(BackupRuleAction.INCLUDE, "mmkv/"),
      rule(BackupRuleAction.EXCLUDE, "mmkv/pili_plus_secret.mmkv")
    )

    assertEquals(BackupPolicy.ALLOWLIST, s.policy)
  }
}
