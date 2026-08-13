package com.absinthe.libchecker.domain.app.detail.ui.view

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.annotation.StringRes
import androidx.appcompat.widget.AppCompatTextView
import com.absinthe.libchecker.R
import com.absinthe.libchecker.domain.app.detail.backup.BackupRule
import com.absinthe.libchecker.domain.app.detail.backup.BackupRuleAction
import com.absinthe.libchecker.domain.app.detail.backup.BackupRules
import com.absinthe.libchecker.domain.app.detail.backup.BackupRulesSection
import com.absinthe.libchecker.utils.extensions.dp
import com.absinthe.libchecker.utils.extensions.getColorByAttr
import com.absinthe.libchecker.utils.extensions.paddingBottomCompat
import com.absinthe.libchecker.view.app.IHeaderView
import com.absinthe.libraries.utils.view.BottomSheetHeaderView

class BackupRulesBottomSheetView(context: Context) :
  LinearLayout(context),
  IHeaderView {

  private val header = BottomSheetHeaderView(context).apply {
    layoutParams =
      LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    title.text = context.getString(R.string.lib_detail_backup_rules_title)
  }

  private val contentContainer = LinearLayout(context).apply {
    orientation = VERTICAL
    layoutParams = FrameLayout.LayoutParams(
      FrameLayout.LayoutParams.MATCH_PARENT,
      FrameLayout.LayoutParams.WRAP_CONTENT
    )
    paddingBottomCompat = 16.dp
  }

  private val scrollView = ScrollView(context).apply {
    layoutParams = LayoutParams(
      LayoutParams.MATCH_PARENT,
      (resources.displayMetrics.heightPixels * CONTENT_HEIGHT_PERCENTAGE).toInt()
    ).also {
      it.topMargin = 24.dp
    }
    overScrollMode = OVER_SCROLL_NEVER
    isVerticalScrollBarEnabled = false
    clipToPadding = false
    clipChildren = false
    addView(contentContainer)
  }

  init {
    orientation = VERTICAL
    val padding = 16.dp
    setPadding(padding, padding, padding, 0)
    addView(header)
    addView(scrollView)
  }

  fun bind(rules: BackupRules?) {
    contentContainer.removeAllViews()
    if (rules == null || (rules.cloudBackup == null && rules.deviceTransfer == null)) {
      contentContainer.addView(createNoteRow(context.getString(R.string.lib_detail_backup_no_rules)))
      return
    }

    addSectionHeader(R.string.lib_detail_backup_section_summary)
    val notSet = context.getString(R.string.lib_detail_backup_not_set)
    addSummaryRow(R.string.lib_detail_backup_allow_backup, rules.allowBackup.toYesNoText())
    addSummaryRow(R.string.lib_detail_backup_backup_agent, rules.backupAgent ?: notSet)
    addSummaryRow(R.string.lib_detail_backup_full_backup_only, rules.fullBackupOnly.toYesNoText())
    addSummaryRow(R.string.lib_detail_backup_kill_after_restore, rules.killAfterRestore.toYesNoText())
    addSummaryRow(R.string.lib_detail_backup_restore_any_version, rules.restoreAnyVersion.toYesNoText())

    rules.cloudBackup?.let {
      addSectionHeader(R.string.lib_detail_backup_section_cloud)
      addRuleSection(it)
    }
    rules.deviceTransfer?.let {
      addSectionHeader(R.string.lib_detail_backup_section_device)
      addRuleSection(it)
    }
  }

  override fun getHeaderView(): BottomSheetHeaderView = header

  private fun addSummaryRow(@StringRes labelRes: Int, value: String) {
    contentContainer.addView(createSummaryRow(context.getString(labelRes), value))
  }

  private fun addSectionHeader(@StringRes res: Int) {
    contentContainer.addView(
      AppCompatTextView(context).apply {
        layoutParams = LayoutParams(
          LayoutParams.MATCH_PARENT,
          LayoutParams.WRAP_CONTENT
        ).also {
          it.topMargin = 16.dp
          it.bottomMargin = 8.dp
        }
        text = context.getString(res)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, SECTION_TEXT_SIZE)
        setTypeface(null, Typeface.BOLD)
        setTextColor(context.getColorByAttr(android.R.attr.textColorPrimary))
      }
    )
  }

  private fun addRuleSection(section: BackupRulesSection) {
    section.rules.forEach { rule ->
      contentContainer.addView(createRuleRow(rule))
    }
    if (section.disableIfNoEncryptionCapabilities == true) {
      contentContainer.addView(createNoteRow("disableIfNoEncryptionCapabilities"))
    }
  }

  private fun createSummaryRow(label: String, value: String): LinearLayout {
    return LinearLayout(context).apply {
      orientation = HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      layoutParams = LayoutParams(
        LayoutParams.MATCH_PARENT,
        LayoutParams.WRAP_CONTENT
      ).also {
        it.bottomMargin = 4.dp
      }
      val labelView = AppCompatTextView(context).apply {
        layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        text = label
        setTextSize(TypedValue.COMPLEX_UNIT_SP, ROW_TEXT_SIZE)
        setTextColor(context.getColorByAttr(android.R.attr.textColorPrimary))
      }
      val valueView = AppCompatTextView(context).apply {
        layoutParams = LayoutParams(
          LayoutParams.WRAP_CONTENT,
          LayoutParams.WRAP_CONTENT
        ).also {
          it.marginStart = 8.dp
        }
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, ROW_TEXT_SIZE)
        setTextColor(context.getColorByAttr(android.R.attr.textColorSecondary))
      }
      addView(labelView)
      addView(valueView)
    }
  }

  private fun createRuleRow(rule: BackupRule): LinearLayout {
    val label = context.getString(rule.action.labelRes())
    val value = listOfNotNull(rule.domain, rule.path).joinToString("  ")
    return LinearLayout(context).apply {
      orientation = HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      layoutParams = LayoutParams(
        LayoutParams.MATCH_PARENT,
        LayoutParams.WRAP_CONTENT
      ).also {
        it.bottomMargin = 4.dp
      }
      val labelView = AppCompatTextView(context).apply {
        layoutParams = LayoutParams(
          LayoutParams.WRAP_CONTENT,
          LayoutParams.WRAP_CONTENT
        )
        text = label
        setTextSize(TypedValue.COMPLEX_UNIT_SP, ROW_TEXT_SIZE)
        setTypeface(null, Typeface.BOLD)
        setTextColor(context.getColorByAttr(android.R.attr.textColorPrimary))
      }
      val valueView = AppCompatTextView(context).apply {
        layoutParams = LayoutParams(
          LayoutParams.WRAP_CONTENT,
          LayoutParams.WRAP_CONTENT
        ).also {
          it.marginStart = 8.dp
        }
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, ROW_TEXT_SIZE)
        setTextColor(context.getColorByAttr(android.R.attr.textColorSecondary))
      }
      addView(labelView)
      addView(valueView)
    }
  }

  private fun createNoteRow(text: String): AppCompatTextView {
    return AppCompatTextView(context).apply {
      layoutParams = LayoutParams(
        LayoutParams.MATCH_PARENT,
        LayoutParams.WRAP_CONTENT
      ).also {
        it.topMargin = 8.dp
      }
      this.text = text
      setTextSize(TypedValue.COMPLEX_UNIT_SP, ROW_TEXT_SIZE)
      setTextColor(context.getColorByAttr(android.R.attr.textColorSecondary))
    }
  }

  private fun BackupRuleAction.labelRes(): Int = when (this) {
    BackupRuleAction.INCLUDE -> R.string.lib_detail_backup_include
    BackupRuleAction.EXCLUDE -> R.string.lib_detail_backup_exclude
  }

  private fun Boolean?.toYesNoText(): String = when (this) {
    true -> context.getString(R.string.lib_detail_backup_yes)
    false -> context.getString(R.string.lib_detail_backup_no)
    null -> context.getString(R.string.lib_detail_backup_not_set)
  }

  private companion object {
    const val CONTENT_HEIGHT_PERCENTAGE = 0.6f
    const val SECTION_TEXT_SIZE = 14f
    const val ROW_TEXT_SIZE = 13f
  }
}
