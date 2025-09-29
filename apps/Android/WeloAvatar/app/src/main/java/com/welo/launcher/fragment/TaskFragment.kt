package com.welo.launcher.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.taobao.meta.avatar.R
import com.taobao.meta.avatar.databinding.FragmentTaskBinding
import com.welo.base.BaseFragment
import com.welo.base.gone
import com.welo.base.visible
import com.welo.launcher.adapter.TaskAdapter
import com.welo.launcher.entity.TaskBean
import com.welo.launcher.entity.TaskType
import com.welo.launcher.listener.IInputBarListener
import com.welo.launcher.viewmodel.ChatViewModel
import com.welo.room.table.ChatMessage
import com.welo.util.GridSpacingItemDecoration
import com.welo.util.LogUtil
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class TaskFragment : BaseFragment<FragmentTaskBinding, ChatViewModel>() {

    private val chatViewModel: ChatViewModel by viewModels()
    private lateinit var taskAdapter: TaskAdapter

    private var isScrollingUp = false
    private var isViewVisible = false
    private var lastScrollY = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            // 处理参数
        }
    }

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentTaskBinding = FragmentTaskBinding.inflate(inflater, container, false)

    override fun initView() {
        setupRecyclerView()
//        observeData()
        setupInputBar()
    }

    private fun setupRecyclerView() {
        taskAdapter = TaskAdapter(
            onItemClick = { task -> handleTaskClick(task) },
            onItemLongClick = { task -> handleTaskLongClick(task) }
        )
        val gridLayoutManager = GridLayoutManager(context, 2)
        val space = resources.getDimensionPixelSize(R.dimen.dp_12)
        binding.appRecyclerView.apply {
            adapter = taskAdapter
            layoutManager = gridLayoutManager
            addItemDecoration(GridSpacingItemDecoration(2, space, false))
        }
        gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return when (taskAdapter.getItemViewType(position)) {
                    TaskType.DEFAULT.ordinal -> 1 // 占满一行
                    TaskType.PROGRESS.ordinal -> 2
                    else -> 1 // 默认占1列
                }
            }
        }
        binding.appRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                // dy > 0 表示向下滚动，dy < 0 表示向上滚动
                if (dy > 0) {
                    // 向下滚动 - 显示view
                    if (!isViewVisible) {
                        showInputBar()
                        isViewVisible = true
                    }
                    isScrollingUp = false
                } else if (dy < 0) {
                    // 向上滚动
                    isScrollingUp = true
                    // 检查是否滚动到顶部
                    if (!recyclerView.canScrollVertically(-1)) {
                        // 到达顶部 - 隐藏view
                        if (isViewVisible) {
                            hideInputBar()
                            isViewVisible = false
                        }
                    }
                }
            }
        })
        binding.nestedScrollView.setOnScrollChangeListener { v, scrollX, scrollY, oldScrollX, oldScrollY ->
            if (scrollY > oldScrollY) {
                showInputBar();
            }
            // 向上滚动并且到达顶部
            else if (scrollY < oldScrollY && scrollY == 0) {
                hideInputBar()
            }

            lastScrollY = scrollY
        }
//        taskAdapter.submitListWithAnimation(createMessages())
    }

    private fun setupInputBar() {
        binding.taskInputBar.setViewListener(false, object : IInputBarListener {

        })
    }

    private fun showInputBar() {
        binding.taskInputBar.visible()
        binding.taskInputBar.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(300)
            .start()
    }

    private fun hideInputBar() {
        binding.taskInputBar.animate()
            .translationY(binding.taskInputBar.height.toFloat())
            .alpha(0f)
            .setDuration(300)
            .withEndAction {
                binding.taskInputBar.gone()
            }
            .start()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeData() {
        chatViewModel.allMessage()
            .flatMapLatest { chatSessions ->
                if (chatSessions.isNotEmpty()) {
                    val sessionId = chatSessions[0].id
                    chatViewModel.observeSessionMessages(sessionId)
                } else {
                    flowOf(emptyList())
                }
            }
            .onEach { messages ->
                if (messages.isNotEmpty()) {
                    val taskList = createTaskList(messages)
                    LogUtil.d(TAG, "Task list size: ${taskList.size}")
                    taskAdapter.submitListWithAnimation(taskList)
                } else {
                    taskAdapter.submitListWithAnimation(emptyList())
                }
            }
            .launchIn(lifecycleScope)
    }

    private fun createTaskList(messages: List<ChatMessage>): List<TaskBean> {
        return listOf(
            TaskBean(TaskType.DEFAULT, 1, messages),
            TaskBean(TaskType.PROGRESS, 5, messages),
            TaskBean(TaskType.DEFAULT, 3, messages),
            TaskBean(TaskType.PROGRESS, 4, messages),
        )
    }

    private val resList =
        listOf(
            R.drawable.music_m,
            R.drawable.workspace_m,
            R.drawable.family_l,
            R.drawable.health_l,
            R.drawable.family_m,
            R.drawable.health_m,
            R.drawable.music_l,
            R.drawable.workspace_l
        )

    private fun createMessages(): List<TaskBean> {
        val messages = mutableListOf<TaskBean>()
        messages.add(TaskBean(TaskType.DEFAULT, 1, emptyList(), resList[0]))
        messages.add(TaskBean(TaskType.DEFAULT, 1, emptyList(), resList[1]))
        messages.add(TaskBean(TaskType.PROGRESS, 1, emptyList(), resList[2]))
        messages.add(TaskBean(TaskType.PROGRESS, 1, emptyList(), resList[3]))
        messages.add(TaskBean(TaskType.DEFAULT, 1, emptyList(), resList[4]))
        messages.add(TaskBean(TaskType.DEFAULT, 1, emptyList(), resList[5]))
        messages.add(TaskBean(TaskType.PROGRESS, 1, emptyList(), resList[6]))
        messages.add(TaskBean(TaskType.PROGRESS, 1, emptyList(), resList[7]))
        return messages
    }

    private fun handleTaskClick(task: TaskBean) {
        // 处理点击事件
    }

    private fun handleTaskLongClick(task: TaskBean): Boolean {
        // 处理长按事件
        return true
    }

    override fun observeViewModel() {
        // ViewModel 观察逻辑
    }

    companion object {
        private const val TAG = "TaskFragment"

        fun newInstance(): TaskFragment = TaskFragment()
    }
}