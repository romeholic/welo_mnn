package com.welo.constant

import android.os.Environment
import com.alibaba.mls.api.ApplicationProvider

object Constants {
    /**
     * LLM模型存储路径
     */
//    val SD_CARD_PATH = Environment.getExternalStorageDirectory().absolutePath
    val SD_CARD_PATH = ApplicationProvider.get().filesDir.absolutePath

}