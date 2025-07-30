package com.welo.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.taobao.meta.avatar.R
import com.welo.adapter.AppAdapter
import com.welo.entity.AppInfo
import com.welo.util.AppUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [AppFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class AppFragment : Fragment() {
    // TODO: Rename and change types of parameters
    private lateinit var appList: List<AppInfo>
    private var param1: String? = null
    private var param2: String? = null
    private lateinit var recyclerView: RecyclerView
    private val appAdapter: AppAdapter by lazy { AppAdapter() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_app, container, false)
        recyclerView = view.findViewById(R.id.app_recycler_view)

        // 设置GridLayoutManager，指定4列
        recyclerView.setLayoutManager(GridLayoutManager(context, 4))
        recyclerView.adapter = appAdapter
        getAndUpdateAppList()
        return view
    }
    private fun getAndUpdateAppList() {
        lifecycleScope.launch {
            appList = withContext(Dispatchers.IO){
                AppUtil.getInstalledApps()
            }
            appAdapter.submitList(appList)
        }
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment AppFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            AppFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}