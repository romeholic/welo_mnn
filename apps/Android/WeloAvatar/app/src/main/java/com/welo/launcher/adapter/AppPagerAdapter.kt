import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.PagerAdapter
import com.taobao.meta.avatar.R
import com.welo.entity.AppInfo
import com.welo.launcher.adapter.AllAppAdapter
import com.welo.util.GridSpacingItemDecoration

class AppPagerAdapter(
    context: Context,
    private val pagedAppList: List<List<AppInfo>>,
    private val onAppClicked: (AppInfo) -> Unit
) : PagerAdapter() {

    companion object{
        private const val SPACE_COUNT = 3
    }
    private val inflater: LayoutInflater = LayoutInflater.from(context)

    override fun getCount(): Int = pagedAppList.size

    override fun isViewFromObject(view: View, obj: Any): Boolean {
        return view == obj
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val appList = pagedAppList[position]
        val view = inflater.inflate(R.layout.layout_app_pager, container, false)
        val gridLayout = view.findViewById<RecyclerView>(R.id.recyclerView)

        setupAppGrid(gridLayout, appList)

        container.addView(view)
        return view
    }

    private fun setupAppGrid(recyclerView: RecyclerView, appList: List<AppInfo>) {
        recyclerView.removeAllViews()
        val allAppAdapter = AllAppAdapter(onAppClicked)
        val space = ResourceProvider.get().getDimenPixelSize(R.dimen.dp_16)
        recyclerView.apply {
            layoutManager = GridLayoutManager(context, SPACE_COUNT)
            addItemDecoration(GridSpacingItemDecoration(SPACE_COUNT, space, false))
            adapter = allAppAdapter
        }
        allAppAdapter.submitList(appList)
    }

    override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
        container.removeView(obj as View)
    }
}