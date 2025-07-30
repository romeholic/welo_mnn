package com.welo.security

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.util.Base64
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import java.util.UUID
import javax.crypto.Cipher

object EncryptionUtils {
    private const val RSA_ALGORITHM = "RSA/ECB/PKCS1Padding"
    private const val PUBLIC_KEY = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCqGKukO1De7zhZj6+H0qtjTkVxwTCpvKe4eCZ0\n" +
            "FPqri0cb2JZfXJ/DgYSF6vUpwmJG8wVQZKjeGcjDOL5UlsuusFncCzWBQ7RKNUSesmQRMSGkVb1/\n" +
            "3j+skZ6UtW+5u09lHNsj6tQ51s1SPrCBkedbNf0Tp0GbMJDyR4e9T04ZZwIDAQAB"

    /**
     * 使用RSA加密密码
     */
    fun encryptRSA(data: String): String {
        return try {
            // 解码公钥
            val keyBytes = Base64.decode(PUBLIC_KEY, Base64.DEFAULT)
            val keySpec = X509EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            val publicKey = keyFactory.generatePublic(keySpec)

            // 加密数据
            val cipher = Cipher.getInstance(RSA_ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, publicKey)
            val encryptedData = cipher.doFinal(data.toByteArray(Charsets.UTF_8))

            Base64.encodeToString(encryptedData, Base64.NO_WRAP)
        } catch (e: Exception) {
            // 加密失败时返回原始数据（实际应用中应处理此异常）
            data
        }
    }

    /**
     * 获取设备唯一标识
     */
    @SuppressLint("HardwareIds")
    fun getDeviceId(context: Context): String {
        return try {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: UUID.randomUUID().toString()
        } catch (e: Exception) {
            UUID.randomUUID().toString()
        }
    }
}
