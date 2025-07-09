// Created by ruoyi.sjd on 2025/3/19.
// Copyright (c) 2024 Alibaba Group Holding Limited All rights reserved.

package com.taobao.meta.avatar.llm

import android.annotation.SuppressLint
import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.widget.TextView
import android.util.Log
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class LlmPresenter(private val textResponse: TextView) {

    private var builder = SpannableStringBuilder()
    private val MAX_LENGTH = 10000
    private var lastMessageRole: String? = null
    private var lastMessageStartIndex: Int = 0
    private var currentAIMessage = ""
    private var stopped = false
    private var currentSessionId = 0L

    // 在LlmPresenter类中添加一个成员变量来缓存每个会话的最后一次完整文本
    private val sessionTexts = mutableMapOf<Long, String>()

    fun reset() {
        stop()
        currentSessionId = 0L
        textResponse.text = ""
    }

    fun setCurrentSessionId(sessionId: Long) {
        currentSessionId = sessionId
    }

    fun stop() {
        stopped = true
    }

    fun start() {
        stopped = false
    }

    @SuppressLint("SetTextI18n")
    fun onLlmTextUpdate(newText: String, callingSessionId: Long) {
        MainScope().launch {
            if (callingSessionId != currentSessionId) {
                Log.w("WELOO#LlmPresenter", "忽略过期会话的文本更新: $callingSessionId (当前: $currentSessionId)")
                return@launch
            }

            // 获取上一次的完整文本（如果不存在则为空字符串）
            val lastFullText = sessionTexts.getOrPut(callingSessionId) { "" }
            Log.d("WELOO#LlmPresenter", "onLlmTextUpdate: 旧文本=${lastFullText}, 新文本=${newText}")
            Log.d("WELOO#LlmPresenter", "onLlmTextUpdate: 旧文本长度=${lastFullText.length}, 新文本长度=${newText.length}")

            // 提取真正的增量部分
            val addedText = extractAddedText(lastFullText, newText)

            // 如果有新增内容，才更新UI
            if (addedText.isNotEmpty()) {
                Log.d("WELOO#LlmPresenter", "onLlmTextUpdate: 新增文本长度=${addedText.length}")
                Log.d("WELOO#LlmPresenter", "onLlmTextUpdate show with addedText: $addedText")

                // 只向UI传递新增的文本部分
                addMessage("ai", addedText)

                // 更新缓存为最新的完整文本
                sessionTexts[callingSessionId] = newText
            } else {
                Log.d("WELOO#LlmPresenter", "onLlmTextUpdate: 没有新增文本")
            }
        }
    }

    /**
     * 从新旧文本中提取真正的增量部分
     */
    private fun extractAddedText(oldText: String, newText: String): String {
        // 情况1：新文本是旧文本的超集，直接提取新增部分
        if (newText.startsWith(oldText)) {
            return newText.substring(oldText.length)
        }

        // 情况2：新旧文本部分重叠，找到最长公共前缀
        val commonPrefixLength = findCommonPrefixLength(oldText, newText)

        // 如果有公共前缀，且新文本比旧文本长，提取超出部分
        if (commonPrefixLength > 0 && newText.length > commonPrefixLength) {
            Log.w("WELOO#LlmPresenter", "检测到部分重叠：旧文本长度=${oldText.length}, 新文本长度=${newText.length}, 公共前缀=${commonPrefixLength}")
            return newText.substring(commonPrefixLength)
        }

        // 情况3：完全不匹配（可能服务端重置了回复）
        Log.w("WELOO#LlmPresenter", "新文本与旧文本完全不匹配，可能服务端重置了回复")
        return newText
    }

    /**
     * 找到两个字符串的最长公共前缀长度
     */
    private fun findCommonPrefixLength(s1: String, s2: String): Int {
        val maxLength = minOf(s1.length, s2.length)
        var length = 0

        while (length < maxLength && s1[length] == s2[length]) {
            length++
        }

        return length
    }

    fun onUserTextUpdate(text: String) {
        MainScope().launch {
            addMessage("human", text)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun addMessage(role: String, message: String) {
        val lowerRole = role.lowercase()
        val color = when (lowerRole) {
            "ai" -> Color.BLACK
            "human" -> Color.GRAY
            else -> Color.BLACK
        }

        if (lowerRole == "ai") {
            if (lastMessageRole == "ai") {
                currentAIMessage += message
                builder.delete(lastMessageStartIndex, builder.length)
            } else {
                currentAIMessage = message
                lastMessageStartIndex = builder.length
            }
            val updatedMessage = currentAIMessage + "\n"
            val spannable = SpannableString(updatedMessage)
            spannable.setSpan(
                ForegroundColorSpan(color),
                0,
                spannable.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            builder.append(spannable)
        } else {
            currentAIMessage = ""
            lastMessageStartIndex = builder.length
            val formattedMessage = message + "\n"
            val spannable = SpannableString(formattedMessage)
            spannable.setSpan(
                ForegroundColorSpan(color),
                0,
                spannable.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            builder.append(spannable)
        }
        lastMessageRole = lowerRole
        trimIfNeeded()
        textResponse.text = builder
        onScrollToBottom()
    }

    private fun trimIfNeeded() {
        while (builder.length > MAX_LENGTH) {
            val firstNewline = builder.indexOf("\n")
            if (firstNewline != -1) {
                builder.delete(0, firstNewline + 1)
            } else {
                builder.clear()
            }
        }
    }

    private fun onScrollToBottom() {
        if (textResponse.visibility != TextView.VISIBLE || textResponse.layout == null) {
            return
        }
        val scrollAmount =
            textResponse.layout.getLineTop(textResponse!!.lineCount) - textResponse!!.height
        if (scrollAmount > 0) {
            textResponse.scrollTo(
                0,
                scrollAmount + 100
            )
        } else {
            textResponse.scrollTo(0, 0)
        }
    }

    fun onEndCall() {
        builder = SpannableStringBuilder()
        textResponse.text = ""
        lastMessageRole = null
        lastMessageStartIndex = 0
        currentAIMessage = ""
    }

}