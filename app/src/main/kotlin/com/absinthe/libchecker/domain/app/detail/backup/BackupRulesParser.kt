package com.absinthe.libchecker.domain.app.detail.backup

import android.content.res.XmlResourceParser
import org.xmlpull.v1.XmlPullParser

object BackupRulesParser {

  fun parseFullBackupContent(parser: XmlResourceParser): BackupRulesSection? {
    val rules = mutableListOf<BackupRule>()
    while (parser.eventType != XmlPullParser.END_DOCUMENT) {
      if (parser.eventType == XmlPullParser.START_TAG) {
        if (parser.name == "include" || parser.name == "exclude") {
          rules.add(parser.buildBackupRule())
        }
      }
      parser.next()
    }
    return BackupRulesSection(
      rules = rules,
      disableIfNoEncryptionCapabilities = null
    ).takeIf { it.rules.isNotEmpty() }
  }

  fun parseDataExtractionRules(parser: XmlResourceParser): Pair<BackupRulesSection?, BackupRulesSection?> {
    val cloudRules = mutableListOf<BackupRule>()
    val deviceRules = mutableListOf<BackupRule>()
    var disableIfNoEncryptionCapabilities: Boolean? = null
    var inCloudBackup = false
    var inDeviceTransfer = false

    while (parser.eventType != XmlPullParser.END_DOCUMENT) {
      if (parser.eventType == XmlPullParser.START_TAG) {
        when (parser.name) {
          "cloud-backup" -> {
            inCloudBackup = true
            inDeviceTransfer = false
            val disableValue = parser.getAttributeValueOrNull("disableIfNoEncryptionCapabilities")
            disableIfNoEncryptionCapabilities = disableValue?.toBooleanOrNull()
          }

          "device-transfer" -> {
            inDeviceTransfer = true
            inCloudBackup = false
          }

          "include", "exclude" -> {
            val rule = parser.buildBackupRule()
            if (inCloudBackup) {
              cloudRules.add(rule)
            } else if (inDeviceTransfer) {
              deviceRules.add(rule)
            }
          }
        }
      }
      parser.next()
    }

    val cloudSection = BackupRulesSection(
      rules = cloudRules,
      disableIfNoEncryptionCapabilities = disableIfNoEncryptionCapabilities
    ).takeIf { it.rules.isNotEmpty() }
    val deviceSection = BackupRulesSection(
      rules = deviceRules,
      disableIfNoEncryptionCapabilities = null
    ).takeIf { it.rules.isNotEmpty() }
    return Pair(cloudSection, deviceSection)
  }

  private fun XmlResourceParser.buildBackupRule(): BackupRule {
    val action = if (name == "include") {
      BackupRuleAction.INCLUDE
    } else {
      BackupRuleAction.EXCLUDE
    }
    return BackupRule(
      action = action,
      domain = getAttributeValueOrNull("domain"),
      path = getAttributeValueOrNull("path"),
      requireFlags = getAttributeValueOrNull("requireFlags")?.parseRequireFlags()
    )
  }

  private fun XmlResourceParser.getAttributeValueOrNull(name: String): String? {
    for (index in 0 until attributeCount) {
      if (getAttributeName(index) == name) {
        return getAttributeValue(index)
      }
    }
    return null
  }

  private fun String.parseRequireFlags(): Int? {
    toIntOrNull()?.let { return it }
    return if (startsWith("0x")) {
      substring(2).toIntOrNull(16)
    } else {
      null
    }
  }

  private fun String.toBooleanOrNull(): Boolean? {
    return when (this) {
      "true" -> true
      "false" -> false
      else -> null
    }
  }
}
