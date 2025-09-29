package com.welo.entity

import java.io.File

data class FilePart(
    val partName: String,  // 表单字段名
    val file: File,        // 文件对象
    val contentType: String = "application/octet-stream" // 可选，默认二进制流
)
