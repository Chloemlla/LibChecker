package com.absinthe.libchecker.domain.app.detail.backup

enum class BackupRuleAction { INCLUDE, EXCLUDE }

data class BackupRule(
  val action: BackupRuleAction,
  val domain: String?,
  val path: String?,
  val requireFlags: Int?
)

data class BackupRulesSection(
  val rules: List<BackupRule>,
  val disableIfNoEncryptionCapabilities: Boolean?
)

data class BackupRules(
  val allowBackup: Boolean?,
  val backupAgent: String?,
  val fullBackupOnly: Boolean?,
  val killAfterRestore: Boolean?,
  val restoreAnyVersion: Boolean?,
  val cloudBackup: BackupRulesSection?,
  val deviceTransfer: BackupRulesSection?
)
