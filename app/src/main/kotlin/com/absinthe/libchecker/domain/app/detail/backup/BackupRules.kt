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

enum class BackupPolicy {
  // <include> present: the default backup set is discarded and only the listed
  // paths are backed up; every unlisted path is excluded (opt-in allowlist).
  ALLOWLIST,

  // Only <exclude> rules: the full default backup set applies minus the listed
  // exclusions.
  DENYLIST
}

// Android Auto Backup semantics: any <include> flips the default to opt-in, so a
// section is an allowlist whenever it contains an include rule (exclude then only
// narrows it further). A section with no include rules is a denylist on top of the
// default full backup set.
val BackupRulesSection.policy: BackupPolicy
  get() = if (rules.any { it.action == BackupRuleAction.INCLUDE }) {
    BackupPolicy.ALLOWLIST
  } else {
    BackupPolicy.DENYLIST
  }

data class BackupRules(
  val allowBackup: Boolean?,
  val backupAgent: String?,
  val fullBackupOnly: Boolean?,
  val killAfterRestore: Boolean?,
  val restoreAnyVersion: Boolean?,
  val cloudBackup: BackupRulesSection?,
  val deviceTransfer: BackupRulesSection?
)
