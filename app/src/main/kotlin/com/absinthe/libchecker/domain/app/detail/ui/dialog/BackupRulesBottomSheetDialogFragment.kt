package com.absinthe.libchecker.domain.app.detail.ui.dialog

import android.content.pm.PackageInfo
import androidx.core.os.BundleCompat
import androidx.lifecycle.lifecycleScope
import com.absinthe.libchecker.domain.app.detail.navigation.EXTRA_PACKAGE_INFO
import com.absinthe.libchecker.domain.app.detail.presentation.DetailViewModel
import com.absinthe.libchecker.domain.app.detail.ui.view.BackupRulesBottomSheetView
import com.absinthe.libchecker.ui.base.BaseBottomSheetViewDialogFragment
import com.absinthe.libraries.utils.view.BottomSheetHeaderView
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class BackupRulesBottomSheetDialogFragment : BaseBottomSheetViewDialogFragment<BackupRulesBottomSheetView>() {

  private val viewModel: DetailViewModel by activityViewModel()

  private val packageInfo by lazy {
    BundleCompat.getParcelable(
      requireArguments(),
      EXTRA_PACKAGE_INFO,
      PackageInfo::class.java
    )
  }

  override fun initRootView(): BackupRulesBottomSheetView = BackupRulesBottomSheetView(requireContext())

  override fun getHeaderView(): BottomSheetHeaderView = root.getHeaderView()

  override fun init() {
    maxPeekHeightPercentage = 0.75f
    lifecycleScope.launch {
      root.bind(viewModel.getAppBackupRules(packageInfo))
    }
  }
}
