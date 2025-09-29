package com.welo.util

import com.welo.constant.Constants
import com.welo.launcher.entity.AIOptionItem
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first

class AiToolsUtil {
    companion object{
        private const val TAG = "AiToolSUtil"
    }
    private val detector = LanguageDetector()
    private var currentAiToolOption: AIOptionItem? = null
    suspend fun getProcessedPrompt(inputText: String,aiOptionItem: AIOptionItem?): String {
        this.currentAiToolOption = aiOptionItem
        return if (currentAiToolOption?.title == "AI翻译") {
            detectLanguageFlow(inputText).first()
        } else {
            chatHeadSync(inputText)
        }
    }

    private fun detectLanguageFlow(inputText: String): Flow<String> = callbackFlow {
        detector.detectLanguage(inputText) { languageCode ->
            LogUtil.d(TAG, "detectLanguage: $languageCode")
            val prompt = when (languageCode) {
                "zh" -> "翻译成英文:$inputText"
                "en" -> "翻译成中文:$inputText"
                else -> inputText
            }
            trySend(prompt)
            close()
        }
        awaitClose()
    }

    private fun chatHeadSync(inputText: String): String {
        currentAiToolOption?.let {
            Constants.agentType = Constants.AGENT_TYPE_WELO_TOOLS
            return when (it.title) {
                "AI搜索" -> "搜索一下：$inputText"
                "AI作图" -> "AI作图：$inputText"
                "AI写作" -> "AI写作：$inputText"
                else -> {
                    Constants.agentType = Constants.AGENT_TYPE_WELO_CHAT
                    inputText
                }
            }
        }
        return inputText
    }
}