package com.example.plugin.examples

import android.content.Context
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Log

/**
 * 플러그인에서 호스트 앱의 리소스에 접근하는 방법 예제
 */
class ResourceAccessExample {

    companion object {
        private const val TAG = "ResourceAccessExample"
    }

    /**
     * 방법 1: getIdentifier() - 리소스 이름으로 조회
     *
     * 장점: 간단하고 실용적
     * 단점: 런타임 에러 가능, 타입 안전성 없음
     */
    fun accessHostResourceByName(
        hostContext: Context,
        resourceName: String,
        resourceType: String  // "drawable", "color", "string", "layout" etc.
    ): Int {
        val hostResources = hostContext.resources
        val hostPackageName = hostContext.packageName

        // 리소스 ID 조회
        val resourceId = hostResources.getIdentifier(
            resourceName,
            resourceType,
            hostPackageName
        )

        if (resourceId == 0) {
            Log.w(TAG, "Resource not found: $resourceType/$resourceName in $hostPackageName")
            return 0
        }

        Log.d(TAG, "Found resource: $resourceType/$resourceName = 0x${Integer.toHexString(resourceId)}")
        return resourceId
    }

    /**
     * 실제 사용 예제
     */
    fun demonstrateHostResourceAccess(hostContext: Context) {
        val hostResources = hostContext.resources

        // 1. Drawable 접근
        val iconId = accessHostResourceByName(hostContext, "ic_launcher", "drawable")
        if (iconId != 0) {
            val icon: Drawable? = hostResources.getDrawable(iconId, null)
            Log.d(TAG, "Loaded host icon: $icon")
        }

        // 2. Color 접근
        val colorId = accessHostResourceByName(hostContext, "sample_red", "color")
        if (colorId != 0) {
            val color = hostResources.getColor(colorId, null)
            Log.d(TAG, "Loaded host color: #${Integer.toHexString(color)}")
        }

        // 3. String 접근
        val stringId = accessHostResourceByName(hostContext, "app_name", "string")
        if (stringId != 0) {
            val appName = hostResources.getString(stringId)
            Log.d(TAG, "Host app name: $appName")
        }

        // 4. Layout 접근
        val layoutId = accessHostResourceByName(hostContext, "activity_main", "layout")
        if (layoutId != 0) {
            Log.d(TAG, "Found host layout: 0x${Integer.toHexString(layoutId)}")
            // LayoutInflater.from(hostContext).inflate(layoutId, container, false)
        }
    }

    /**
     * 방법 2: Reflection으로 호스트의 R 클래스 로드
     *
     * 장점: R.xxx.yyy 구조 유지
     * 단점: Reflection 오버헤드, ProGuard/R8에 취약
     */
    fun accessHostResourceByReflection(
        hostPackageName: String,
        resourceClass: String,  // "drawable", "color", "string" etc.
        resourceField: String   // "ic_launcher", "sample_red" etc.
    ): Int? {
        return try {
            // 플러그인의 ClassLoader의 parent는 호스트의 ClassLoader
            val classLoader = this.javaClass.classLoader?.parent

            // 호스트의 R.drawable, R.color 등 클래스 로드
            val rClass = classLoader?.loadClass("$hostPackageName.R\$$resourceClass")

            // 필드 값 조회
            val field = rClass?.getField(resourceField)
            val resourceId = field?.getInt(null)

            Log.d(TAG, "Reflection access: R.$resourceClass.$resourceField = 0x${Integer.toHexString(resourceId ?: 0)}")
            resourceId
        } catch (e: ClassNotFoundException) {
            Log.e(TAG, "R class not found: $hostPackageName.R\$$resourceClass", e)
            null
        } catch (e: NoSuchFieldException) {
            Log.e(TAG, "Field not found: R.$resourceClass.$resourceField", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Reflection failed", e)
            null
        }
    }

    /**
     * Reflection 사용 예제
     */
    fun demonstrateReflectionAccess(hostContext: Context) {
        val hostPackageName = hostContext.packageName
        val hostResources = hostContext.resources

        // 1. R.drawable.ic_launcher 접근
        val iconId = accessHostResourceByReflection(
            hostPackageName,
            "drawable",
            "ic_launcher"
        )
        if (iconId != null) {
            val icon = hostResources.getDrawable(iconId, null)
            Log.d(TAG, "Reflection loaded icon: $icon")
        }

        // 2. R.color.sample_red 접근
        val colorId = accessHostResourceByReflection(
            hostPackageName,
            "color",
            "sample_red"
        )
        if (colorId != null) {
            val color = hostResources.getColor(colorId, null)
            Log.d(TAG, "Reflection loaded color: #${Integer.toHexString(color)}")
        }

        // 3. R.string.app_name 접근
        val stringId = accessHostResourceByReflection(
            hostPackageName,
            "string",
            "app_name"
        )
        if (stringId != null) {
            val appName = hostResources.getString(stringId)
            Log.d(TAG, "Reflection loaded string: $appName")
        }
    }

    /**
     * 안전한 래퍼 클래스 - getIdentifier 방식을 타입 안전하게 래핑
     */
    class HostResourceAccessor(private val context: Context) {
        private val resources: Resources = context.resources
        private val packageName: String = context.packageName

        fun getDrawable(name: String): Drawable? {
            val id = resources.getIdentifier(name, "drawable", packageName)
            return if (id != 0) resources.getDrawable(id, null) else null
        }

        fun getColor(name: String): Int? {
            val id = resources.getIdentifier(name, "color", packageName)
            return if (id != 0) resources.getColor(id, null) else null
        }

        fun getString(name: String): String? {
            val id = resources.getIdentifier(name, "string", packageName)
            return if (id != 0) resources.getString(id) else null
        }

        fun getLayoutId(name: String): Int {
            return resources.getIdentifier(name, "layout", packageName)
        }

        fun getId(name: String): Int {
            return resources.getIdentifier(name, "id", packageName)
        }
    }
}
