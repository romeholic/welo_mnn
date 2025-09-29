package com.welo.launcher.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import com.taobao.meta.avatar.databinding.FragmentCalendarMemoryBinding
import com.welo.base.BaseFragment
import com.welo.launcher.viewmodel.MemoryModel

private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"
class MemoryCalendarFragment:BaseFragment<FragmentCalendarMemoryBinding, MemoryModel>() {
    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentCalendarMemoryBinding {
            return FragmentCalendarMemoryBinding.inflate(layoutInflater)
    }

    override fun initView() {

    }

    override fun observeViewModel() {

    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment MemoryCalendarFragment.
         */
        // TODO: Rename and change types and number of parameters
        private const val TAG = "CalendarMemoryFragment"

        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            MemoryCalendarFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}