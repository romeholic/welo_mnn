package com.welo.launcher.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.taobao.meta.avatar.R
import com.welo.base.observeHeightChanges
import com.welo.entity.MessageData

class MessageAdapter() : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val VIEW_TYPE_SENT = 1
    private val VIEW_TYPE_RECEIVED = 2
    private var messages: List<MessageData> = mutableListOf()

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerView.ViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_SENT -> {
                val view = layoutInflater.inflate(R.layout.item_message_send, parent, false)
                SentMessageHolder(view)
            }

            VIEW_TYPE_RECEIVED -> {
                val view = layoutInflater.inflate(R.layout.item_message_received, parent, false)
                ReceivedMessageHolder(view)
            }

            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int
    ) {
        val messageData = messages[position]
        when (holder) {
            is SentMessageHolder -> holder.bind(messageData)
            is ReceivedMessageHolder -> holder.bind(messageData)
            else -> throw IllegalArgumentException("Invalid ViewHolder type")
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: List<Any?>
    ) {
        if (payloads.isNotEmpty() && payloads[0] == "textChanged") {
            val messageData = messages[position]
            when (holder) {
                is SentMessageHolder -> holder.bind(messageData)
                is ReceivedMessageHolder -> holder.updateMessageText(messageData)
                else -> throw IllegalArgumentException("Invalid ViewHolder type")
            }
        } else {
            // 完整绑定（payloads 为空时）
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun getItemViewType(position: Int): Int {
        val messageData = messages[position]
        return if (messageData.isSent) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
    }

    override fun getItemCount(): Int = messages.size

    @SuppressLint("NotifyDataSetChanged")
    fun setMessages(newMessages: MutableList<MessageData>) {
        messages = newMessages
        notifyDataSetChanged()
    }

    fun addMessage(message: MessageData) {
        messages = messages + message
        notifyItemInserted(messages.size - 1)
    }

    fun getList() = messages
    private var onHeightChanged: OnHeightChangedListener? = null
    fun observeHeightChanges(onHeightChanged: OnHeightChangedListener?) {
        this.onHeightChanged = onHeightChanged
    }

    interface OnHeightChangedListener {
        fun onHeightChanged(oldHeight: Int, newHeight: Int)
    }

    inner class SentMessageHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageTextView = itemView.findViewById<TextView>(R.id.message_content_send)

        fun bind(message: MessageData) {
            messageTextView.text = message.text
        }
    }

    inner class ReceivedMessageHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageTextView = itemView.findViewById<TextView>(R.id.message_content_received)

        fun bind(message: MessageData) {
            messageTextView.text = message.text
            messageTextView.observeHeightChanges { oldHeight, newHeight ->
                onHeightChanged?.onHeightChanged(oldHeight, newHeight)
            }
        }

        fun updateMessageText(message: MessageData) {
            messageTextView.text = message.text
            onScrollToBottom(messageTextView)
        }
    }

    private fun onScrollToBottom(textResponse: TextView) {
        if (textResponse.visibility != TextView.VISIBLE || textResponse.layout == null) {
            return
        }
        val scrollAmount =
            textResponse.layout.getLineTop(textResponse.lineCount) - textResponse.height
        if (scrollAmount > 0) {
            textResponse.scrollTo(
                0,
                scrollAmount + 40
            )
        } else {
            textResponse.scrollTo(0, 0)
        }
    }
}