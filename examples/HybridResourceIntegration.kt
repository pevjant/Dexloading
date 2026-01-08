package com.example.dexloadingtest.examples

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import java.io.File

/**
 * 하이브리드 방식: DexClassLoader + 통합 Resources
 *
 * 현재 방식의 장점 (재시작 불필요, 시스템 설치 불필요)과
 * SplitCompat 방식의 장점 (통합된 Resources)을 결합
 */
class HybridPluginLoader(private val context: Context) {

    companion object {
        private const val TAG = "HybridPluginLoader"
    }

    /**
     * 현재 방식: 별도 Resources 객체 생성
     */
    fun loadPluginTraditionalWay(apkPath: String) {
        Log.d(TAG, "=== Traditional Way (Separate Resources) ===")

        // 1. Plugin 전용 AssetManager 생성
        val pluginAssetManager = AssetManager::class.java.newInstance()
        val addAssetPath = AssetManager::class.java
            .getDeclaredMethod("addAssetPath", String::class.java)
        addAssetPath.invoke(pluginAssetManager, apkPath)

        // 2. Plugin 전용 Resources 생성
        val hostResources = context.resources
        val pluginResources = android.content.res.Resources(
            pluginAssetManager,
            hostResources.displayMetrics,
            hostResources.configuration
        )

        Log.d(TAG, "Created separate Resources object for plugin")

        // 3. 사용 예
        demonstrateTraditionalAccess(hostResources, pluginResources)
    }

    /**
     * 하이브리드 방식: Host Resources에 Plugin 리소스 통합
     * (SplitCompat이 하는 방식과 동일)
     */
    fun loadPluginIntegratedWay(apkPath: String) {
        Log.d(TAG, "=== Integrated Way (SplitCompat Style) ===")

        // 1. Host의 AssetManager에 Plugin APK 추가
        val hostAssetManager = context.resources.assets
        val addAssetPath = AssetManager::class.java
            .getDeclaredMethod("addAssetPath", String::class.java)
        addAssetPath.isAccessible = true

        val cookie = addAssetPath.invoke(hostAssetManager, apkPath) as? Int
        Log.d(TAG, "Added plugin APK to host AssetManager: cookie=$cookie")

        // 2. 이제 context.resources로 모든 리소스 접근 가능!
        val unifiedResources = context.resources

        Log.d(TAG, "Plugin resources integrated into host Resources")

        // 3. 사용 예
        demonstrateIntegratedAccess(unifiedResources)
    }

    /**
     * 전통 방식 사용 예제
     */
    private fun demonstrateTraditionalAccess(
        hostResources: android.content.res.Resources,
        pluginResources: android.content.res.Resources
    ) {
        Log.d(TAG, "--- Traditional Access Pattern ---")

        // Host 리소스 접근
        val hostColorId = hostResources.getIdentifier(
            "sample_red", "color", context.packageName
        )
        if (hostColorId != 0) {
            val hostColor = hostResources.getColor(hostColorId, null)
            Log.d(TAG, "Host color: 0x${Integer.toHexString(hostColor)}")
        }

        // Plugin 리소스 접근 (별도 Resources 객체 필요)
        val pluginColorId = pluginResources.getIdentifier(
            "plugin_coral", "color", "com.example.plugin"
        )
        if (pluginColorId != 0) {
            val pluginColor = pluginResources.getColor(pluginColorId, null)
            Log.d(TAG, "Plugin color: 0x${Integer.toHexString(pluginColor)}")
        }

        Log.d(TAG, "❌ Requires two separate Resources objects")
        Log.d(TAG, "❌ Complex: need to know which Resources to use")
    }

    /**
     * 통합 방식 사용 예제
     */
    private fun demonstrateIntegratedAccess(
        unifiedResources: android.content.res.Resources
    ) {
        Log.d(TAG, "--- Integrated Access Pattern ---")

        // Host 리소스 접근 (같은 Resources)
        val hostColorId = unifiedResources.getIdentifier(
            "sample_red", "color", context.packageName
        )
        if (hostColorId != 0) {
            val hostColor = unifiedResources.getColor(hostColorId, null)
            Log.d(TAG, "Host color: 0x${Integer.toHexString(hostColor)}")
        }

        // Plugin 리소스 접근 (같은 Resources!)
        val pluginColorId = unifiedResources.getIdentifier(
            "plugin_coral", "color", context.packageName  // ← 같은 packageName!
        )
        if (pluginColorId != 0) {
            val pluginColor = unifiedResources.getColor(pluginColorId, null)
            Log.d(TAG, "Plugin color: 0x${Integer.toHexString(pluginColor)}")
        }

        // ✅ 모든 drawable도 접근 가능
        val pluginDrawableId = unifiedResources.getIdentifier(
            "plugin_image", "drawable", context.packageName
        )
        if (pluginDrawableId != 0) {
            val drawable = unifiedResources.getDrawable(pluginDrawableId, null)
            Log.d(TAG, "Plugin drawable loaded: $drawable")
        }

        Log.d(TAG, "✅ Single unified Resources object")
        Log.d(TAG, "✅ Simple: just use context.resources for everything")
        Log.d(TAG, "✅ No shared module needed!")
    }

    /**
     * 실제 비교 데모
     */
    fun runComparison(apkPath: String) {
        Log.d(TAG, "\n========================================")
        Log.d(TAG, "Resource Access Comparison")
        Log.d(TAG, "========================================\n")

        // 방법 1: 전통 방식
        loadPluginTraditionalWay(apkPath)

        Log.d(TAG, "\n")

        // 방법 2: 통합 방식
        loadPluginIntegratedWay(apkPath)

        Log.d(TAG, "\n========================================")
        Log.d(TAG, "Comparison Complete")
        Log.d(TAG, "========================================")
    }
}

/**
 * 실제 PluginLoader 개선 예제
 */
class ImprovedPluginLoader(private val context: Context) {

    private var pluginClassLoader: dalvik.system.DexClassLoader? = null

    /**
     * 개선된 로딩: Resources 통합
     */
    fun loadPlugin(apkPath: String): Result<Any> {
        return try {
            // 1. Host Resources에 Plugin 리소스 통합 (SplitCompat 방식)
            integratePluginResources(apkPath)

            // 2. DexClassLoader (기존 방식 유지)
            val optimizedDir = context.getDir("dex_opt", Context.MODE_PRIVATE)
            pluginClassLoader = dalvik.system.DexClassLoader(
                apkPath,
                optimizedDir.absolutePath,
                null,
                context.classLoader
            )

            // 3. 플러그인 클래스 로드
            val pluginClass = pluginClassLoader!!.loadClass("com.example.plugin.PluginImpl")
            val plugin = pluginClass.getDeclaredConstructor().newInstance()

            // 4. 초기화 - 이제 별도 Resources 전달 불필요!
            if (plugin is PluginInterfaceSimplified) {
                plugin.initialize(context)  // ← context.resources로 모든 접근 가능!
            }

            Log.d("ImprovedPluginLoader", "✅ Plugin loaded with integrated resources")
            Result.success(plugin)
        } catch (e: Exception) {
            Log.e("ImprovedPluginLoader", "Failed to load plugin", e)
            Result.failure(e)
        }
    }

    /**
     * SplitCompat 방식으로 리소스 통합
     */
    private fun integratePluginResources(apkPath: String) {
        try {
            val assetManager = context.resources.assets
            val addAssetPath = AssetManager::class.java
                .getDeclaredMethod("addAssetPath", String::class.java)
            addAssetPath.isAccessible = true

            val cookie = addAssetPath.invoke(assetManager, apkPath)
            Log.d("ImprovedPluginLoader", "Integrated plugin resources: cookie=$cookie")

            // ✅ 이제 context.resources로 Host + Plugin 모든 리소스 접근 가능!
        } catch (e: Exception) {
            Log.e("ImprovedPluginLoader", "Failed to integrate resources", e)
            throw e
        }
    }
}

/**
 * 단순화된 PluginInterface
 * (별도 Resources/AssetManager 전달 불필요!)
 */
interface PluginInterfaceSimplified {
    /**
     * context.resources로 모든 리소스 접근 가능!
     */
    fun initialize(context: Context)

    fun getName(): String
    fun getVersion(): String
    fun createView(context: Context): android.view.View
    fun onDestroy()
}

/**
 * 사용 예제
 */
fun demonstrateImprovedUsage(context: Context) {
    val loader = ImprovedPluginLoader(context)
    val apkPath = File(context.filesDir, "plugin.apk").absolutePath

    loader.loadPlugin(apkPath).onSuccess { plugin ->
        if (plugin is PluginInterfaceSimplified) {
            // Plugin 내부에서 자유롭게 리소스 접근
            val view = plugin.createView(context)

            // Plugin은 context.resources로 Host + Plugin 리소스 모두 접근 가능!
        }
    }
}
