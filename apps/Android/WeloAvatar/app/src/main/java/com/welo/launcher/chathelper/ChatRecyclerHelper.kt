package com.welo.launcher.chathelper

import android.graphics.Rect
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.welo.launcher.ChatActivity
import com.welo.launcher.adapter.ChatAdapter
import com.welo.room.table.ChatMessage
import com.welo.util.LogUtil
import kotlin.math.abs

class ChatRecyclerHelper(private val recyclerView: RecyclerView) {
    companion object{
        private const val TAG = "ChatRecyclerHelper"
    }

    private lateinit var chatAdapter: ChatAdapter
    private var isScrollingProgrammatically = false
    private var lastScrollTime = 0L

    private var gestureDetector: GestureDetector? = null

    // 添加空白点击监听器
    private var onEmptySpaceClickListener: (() -> Unit)? = null

    interface OnSwipeListener {
        fun onSwipeLeft()
        fun onSwipeRight()
    }

    private var swipeListener: OnSwipeListener? = null

    fun init(
        onRetry: (ChatMessage) -> Unit,
        onMessageLongClick: (ChatMessage) -> Unit,
        swipeListener: OnSwipeListener? = null,
        onEmptySpaceClick: (() -> Unit)? = null
    ) {
        this.swipeListener = swipeListener
        this.onEmptySpaceClickListener = onEmptySpaceClick // 保存监听器
        chatAdapter = ChatAdapter(
            onRetry = onRetry,
            onMessageLongClick = onMessageLongClick,
            onContentUpdated = { position ->
                if (position == chatAdapter.itemCount - 1) scheduleScrollToBottom()
            },
            shouldAutoScroll = {
                shouldAutoScroll()
            }
        )
        // 初始化手势检测器
        gestureDetector = GestureDetector(
            recyclerView.context,
            object : GestureDetector.SimpleOnGestureListener() {
                private val SWIPE_THRESHOLD = 100
                private val SWIPE_VELOCITY_THRESHOLD = 100

                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    if (e1 == null) return false

                    val diffX = e2.x - e1.x
                    val diffY = e2.y - e1.y

                    if (abs(diffX) > abs(diffY) &&
                        abs(diffX) > SWIPE_THRESHOLD &&
                        abs(velocityX) > SWIPE_VELOCITY_THRESHOLD
                    ) {

                        if (diffX > 0) {
                            // 右滑
                            swipeListener?.onSwipeRight()
                            return true
                        } else {
                            // 左滑
                            swipeListener?.onSwipeLeft()
                            return true
                        }
                    }
                    return false
                }

                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    val isEmptySpace = isTapOnEmptySpace(e)
                    // 检查是否点击在空白处
                    if (isEmptySpace) {
                        onEmptySpaceClickListener?.invoke()
                        return true
                    }
                    return false
                }

                override fun onScroll(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    distanceX: Float,
                    distanceY: Float
                ): Boolean {
                    return super.onScroll(e1, e2, distanceX, distanceY)
                }
            })
        recyclerView.apply {
            adapter = chatAdapter
            setItemViewCacheSize(20)
            setHasFixedSize(true)
            isNestedScrollingEnabled = false
            overScrollMode = RecyclerView.OVER_SCROLL_NEVER
            layoutManager = createLayoutManager()
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                        isScrollingProgrammatically = false
                    }
                }
            })
            addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
                override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                    gestureDetector?.onTouchEvent(e)
                    // 当RecyclerView显示时，阻止父ViewPager2拦截触摸事件
                    if (recyclerView.isVisible) {
                        rv.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    return super.onInterceptTouchEvent(rv, e)
                }

                override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
                    gestureDetector?.onTouchEvent(e)
                    super.onTouchEvent(rv, e)
                }
            })
        }
    }

    // 检查是否点击在空白处
    private fun isTapOnEmptySpace(e: MotionEvent): Boolean {
        if (recyclerView.layoutManager !is LinearLayoutManager) return false

        // 获取点击位置的View
        val childView = recyclerView.findChildViewUnder(e.x, e.y)
        LogUtil.d("ChatRecyclerHelper", "isTapOnEmptySpace: childView = $childView")
        if (childView == null) {
            return true
        }

        // 如果没有找到子View，说明点击在空白处
        return isTapOnConstraintLayoutEmptySpace(childView, e)
    }

    private fun isTapOnConstraintLayoutEmptySpace(constraintLayout: View, e: MotionEvent): Boolean {
        if (constraintLayout !is ViewGroup) return false

        // 获取全局坐标
        val globalRect = Rect()
        constraintLayout.getGlobalVisibleRect(globalRect)

        val relativeX = e.rawX - globalRect.left
        val relativeY = e.rawY - globalRect.top

        // 检查是否点击在任何可见的子视图上
        for (i in 0 until constraintLayout.childCount) {
            val child = constraintLayout.getChildAt(i)
            if (child.isVisible) {
                val childRect = Rect()
                child.getHitRect(childRect)

                if (childRect.contains(relativeX.toInt(), relativeY.toInt())) {
                    // 如果是可交互的视图，不算空白区域
                    if (child.isClickable || child.isLongClickable ||
                        child.isFocusableInTouchMode || child is EditText
                    ) {
                        return false
                    }
                    return false
                }
            }
        }
        return true
    }

    private fun createLayoutManager() = object : LinearLayoutManager(recyclerView.context) {
        override fun supportsPredictiveItemAnimations() = false
        override fun onLayoutChildren(
            recycler: RecyclerView.Recycler?,
            state: RecyclerView.State?
        ) {
            try {
                super.onLayoutChildren(recycler, state)
            } catch (e: Exception) {
                LogUtil.e("ChatRecycler", "布局错误: ${e.message}")
            }
        }
    }.apply {
        stackFromEnd = true
        reverseLayout = false
    }

    fun notifyItemChanged(payload: Any?) {
        val position = chatAdapter.itemCount - 1
        chatAdapter.notifyItemChanged(position, payload)
        if (position == chatAdapter.itemCount - 1) {
            scheduleScrollToBottom()
        }
    }

    fun submitMessages(messages: List<ChatMessage>, sessionId: Long) {
        chatAdapter.submitListWithSession(messages, sessionId)
        scheduleScrollToBottom()
    }

    fun scrollToBottom() {
        if (chatAdapter.itemCount > 0) {
            recyclerView.smoothScrollToPosition(chatAdapter.itemCount - 1)
        }
    }

    private fun shouldAutoScroll(): Boolean {
        if (isScrollingProgrammatically) return true
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return false
        val lastVisible = layoutManager.findLastVisibleItemPosition()
        return lastVisible >= layoutManager.itemCount - 3
    }

    private fun scheduleScrollToBottom() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastScrollTime < ChatActivity.SCROLL_DEBOUNCE_TIME) return
        lastScrollTime = currentTime
        if (chatAdapter.itemCount > 0 && shouldAutoScroll()) {
            scrollToBottomImmediately()
        }
    }

    fun scrollToBottomImmediately() {
        isScrollingProgrammatically = true
        recyclerView.post {
            if (chatAdapter.itemCount > 0) {
                recyclerView.layoutManager?.scrollToPosition(chatAdapter.itemCount - 1)
                isScrollingProgrammatically = false
            }
        }
    }

    fun updateLoadingState(isLoading: Boolean) {
        chatAdapter.setLoadingAIResponse(isLoading)
    }

    fun isLoading(): Boolean = chatAdapter.getLoadingAIResponse()

    // 设置滑动监听器的方法
    fun setOnSwipeListener(listener: OnSwipeListener) {
        this.swipeListener = listener
    }

    // 清除滑动监听器
    fun clearSwipeListener() {
        this.swipeListener = null
    }
}