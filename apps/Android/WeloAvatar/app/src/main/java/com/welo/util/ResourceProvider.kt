import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.util.TypedValue
import androidx.annotation.*
import androidx.core.content.ContextCompat

/**
 * 资源统一管理类，所有资源获取通过此类代理
 */
class ResourceProvider private constructor(private val context: Context) {

    // ========== 字符串资源 ==========
    fun getString(@StringRes resId: Int): String {
        return context.getString(resId)
    }

    fun getString(@StringRes resId: Int, vararg formatArgs: Any): String {
        return context.getString(resId, *formatArgs)
    }

    // ========== 尺寸资源 ==========
    /**
     * 获取dp尺寸（返回像素值）
     */
    fun getDimenPixelSize(@DimenRes resId: Int): Int {
        return context.resources.getDimensionPixelSize(resId)
    }

    /**
     * 获取sp尺寸（返回像素值，用于文字大小）
     */
    fun getTextSize(@DimenRes resId: Int): Float {
        return context.resources.getDimension(resId)
    }

    /**
     * 获取原始dp值（未转换为像素）
     */
    fun getRawDimen(@DimenRes resId: Int): Float {
        return context.resources.getDimension(resId)
    }

    // ========== 颜色资源 ==========
    fun getColor(@ColorRes resId: Int): Int {
        return ContextCompat.getColor(context, resId)
    }

    fun getColorStateList(@ColorRes resId: Int): ColorStateList? {
        return ContextCompat.getColorStateList(context, resId)
    }

    // ========== Drawable资源 ==========
    fun getDrawable(@DrawableRes resId: Int): Drawable? {
        return ContextCompat.getDrawable(context, resId)
    }

    // ========== 数组资源 ==========
    fun getStringArray(@ArrayRes resId: Int): Array<String> {
        return context.resources.getStringArray(resId)
    }

    fun getIntArray(@ArrayRes resId: Int): IntArray {
        return context.resources.getIntArray(resId)
    }

    // ========== 主题相关（支持动态主题切换） ==========
    /**
     * 从当前主题中获取属性值（如主题中定义的colorPrimary）
     */
    fun getThemeColor(@AttrRes attrRes: Int): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(attrRes, typedValue, true)
        return typedValue.data
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: ResourceProvider? = null

        /**
         * 初始化（必须在Application中调用，确保使用Application Context）
         */
        fun init(context: Context) {
            if (instance == null) {
                synchronized(ResourceProvider::class.java) {
                    if (instance == null) {
                        // 使用Application Context避免内存泄漏
                        instance = ResourceProvider(context.applicationContext)
                    }
                }
            }
        }

        /**
         * 获取单例实例（必须先调用init()）
         */
        fun get(): ResourceProvider {
            return instance ?: throw IllegalStateException("ResourceProvider not initialized! Call init() first.")
        }
    }
}