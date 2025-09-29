import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.fragment.app.DialogFragment
import androidx.viewpager.widget.ViewPager
import com.taobao.meta.avatar.R
import com.taobao.meta.avatar.databinding.DialogAppChooserBinding
import com.welo.entity.AppInfo

class AppChooserDialog(
    private val appList: List<AppInfo>,
    private val onAppClicked: (AppInfo) -> Unit
) : DialogFragment() {

    companion object {
        private const val APPS_PER_PAGE = 9
    }

    private var _binding: DialogAppChooserBinding? = null
    private val binding get() = _binding!!

    private val indicators = mutableListOf<ImageView>()
    private var currentPage = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, R.style.RoundedDialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAppChooserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViewPager()
        setupIndicators()
    }

    override fun onStart() {
        super.onStart()
        setupDialogWindow()
    }

    private fun setupDialogWindow() {
        dialog?.window?.apply {
            setLayout(
                ResourceProvider.get().getDimenPixelSize(R.dimen.dp_300),
                ResourceProvider.get().getDimenPixelSize(R.dimen.dp_298)
            )
            setGravity(Gravity.CENTER)
            setWindowAnimations(R.style.DialogAnimation)
            setBackgroundDrawableResource(android.R.color.transparent)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
    }

    private fun setupViewPager() {
        val pagerAdapter = createPagerAdapter()
        binding.appPager.adapter = pagerAdapter
        binding.appPager.addOnPageChangeListener(createPageChangeListener())
    }

    private fun createPagerAdapter(): AppPagerAdapter {
        val pagedAppList = createPagedAppList()
        return AppPagerAdapter(requireContext(), pagedAppList) { app ->
            onAppClicked(app)
            dismiss()
        }
    }

    private fun createPagedAppList(): List<List<AppInfo>> {
        return appList.chunked(APPS_PER_PAGE)
    }

    private fun setupIndicators() {
        val pageCount = calculatePageCount()
        initIndicators(pageCount)
    }

    private fun calculatePageCount(): Int {
        return (appList.size + APPS_PER_PAGE - 1) / APPS_PER_PAGE
    }

    private fun initIndicators(count: Int) {
        if (count <= 1) {
            binding.indicatorLayout.visibility = View.GONE
            return
        }

        binding.indicatorLayout.visibility = View.VISIBLE
        binding.indicatorLayout.removeAllViews()
        indicators.clear()

        val indicatorSize = ResourceProvider.get().getDimenPixelSize(R.dimen.dp_4)
        val indicatorMargin = ResourceProvider.get().getDimenPixelSize(R.dimen.dp_4)

        repeat(count) { index ->
            val indicator = createIndicator(indicatorSize, indicatorMargin, index)
            indicators.add(indicator)
            binding.indicatorLayout.addView(indicator)
        }

        updateIndicators(0)
    }

    private fun createIndicator(size: Int, margin: Int, index: Int): ImageView {
        return ImageView(requireContext()).apply {
            layoutParams = createIndicatorLayoutParams(size, margin, index)
            setImageResource(R.drawable.indicator_unselected)
        }
    }

    private fun createIndicatorLayoutParams(size: Int, margin: Int, index: Int): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(size, size).apply {
            if (index > 0) {
                marginStart = margin
            }
        }
    }

    private fun createPageChangeListener(): ViewPager.OnPageChangeListener {
        return object : ViewPager.SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) {
                currentPage = position
                updateIndicators(position)
            }
        }
    }

    private fun updateIndicators(selectedPosition: Int) {
        indicators.forEachIndexed { index, imageView ->
            val drawableRes = if (index == selectedPosition) {
                R.drawable.indicator_selected
            } else {
                R.drawable.indicator_unselected
            }
            imageView.setImageResource(drawableRes)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}