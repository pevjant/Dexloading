package com.example.dexloadingtest.plugin

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import android.os.Build
import android.util.Log
import dalvik.system.DexClassLoader
import java.io.File
import java.io.FileOutputStream

/**
 * Handles dynamic loading of plugin APK files.
 * Loads both DEX classes and plugin resources (drawable, layout, values, assets).
 */
class PluginLoader(private val context: Context) {

    companion object {
        private const val TAG = "PluginLoader"
        private const val PLUGIN_APK_NAME = "plugin.apk"
        private const val PLUGIN_CLASS_NAME = "com.example.plugin.PluginImpl"
    }

    private var pluginClassLoader: DexClassLoader? = null
    private var pluginInstance: PluginInterface? = null
    private var pluginResources: Resources? = null
    private var pluginAssetManager: AssetManager? = null

    /**
     * Load plugin from assets folder.
     * The plugin APK file should be placed in app/src/main/assets/plugin.apk
     */
    fun loadPluginFromAssets(): Result<PluginInterface> {
        return try {
            // Copy APK from assets to internal storage (required for DexClassLoader)
            val apkFile = copyApkFromAssets()
            if (apkFile == null) {
                return Result.failure(Exception("Failed to copy APK file from assets"))
            }

            loadPluginFromFile(apkFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load plugin from assets", e)
            Result.failure(e)
        }
    }

    /**
     * Load plugin from a specific APK file path.
     */
    fun loadPluginFromFile(apkFile: File): Result<PluginInterface> {
        return try {
            val apkPath = apkFile.absolutePath

            // Step 1: Create plugin's Resources and AssetManager
            createPluginResources(apkPath)

            // Step 2: Create optimized DEX output directory
            val optimizedDir = context.getDir("dex_opt", Context.MODE_PRIVATE)

            // Step 3: Create DexClassLoader
            pluginClassLoader = DexClassLoader(
                apkPath,
                optimizedDir.absolutePath,
                null, // No native library path
                context.classLoader
            )

            // Step 4: Load the plugin class
            val pluginClass = pluginClassLoader!!.loadClass(PLUGIN_CLASS_NAME)

            // Step 5: Create instance
            val instance = pluginClass.getDeclaredConstructor().newInstance()

            if (instance is PluginInterface) {
                pluginInstance = instance

                // Initialize with host context AND plugin's own resources
                pluginInstance!!.initialize(
                    context,
                    pluginResources!!,
                    pluginAssetManager!!
                )

                Log.i(TAG, "Plugin loaded successfully: ${pluginInstance!!.getName()} v${pluginInstance!!.getVersion()}")
                Result.success(pluginInstance!!)
            } else {
                Result.failure(Exception("Plugin class does not implement PluginInterface"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load plugin from file", e)
            Result.failure(e)
        }
    }

    /**
     * Create Resources and AssetManager for the plugin APK.
     * This allows the plugin to access its own drawable, layout, values, and assets.
     */
    @Suppress("DiscouragedPrivateApi")
    private fun createPluginResources(apkPath: String) {
        try {
            // Create a new AssetManager and add the plugin APK path
            val assetManager = AssetManager::class.java.newInstance()

            // Use reflection to call addAssetPath (hidden API)
            val addAssetPathMethod = AssetManager::class.java.getDeclaredMethod(
                "addAssetPath",
                String::class.java
            )
            addAssetPathMethod.isAccessible = true
            addAssetPathMethod.invoke(assetManager, apkPath)

            pluginAssetManager = assetManager

            // Create Resources using the plugin's AssetManager
            val hostResources = context.resources
            pluginResources = Resources(
                assetManager,
                hostResources.displayMetrics,
                hostResources.configuration
            )

            Log.d(TAG, "Plugin resources created successfully for: $apkPath")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create plugin resources", e)
            throw e
        }
    }

    /**
     * Load plugin from external storage or download location.
     *
     * Note: For Android 14+, the file should already be read-only or will be set automatically.
     * If loading from a writable location, consider copying to app's internal storage first.
     */
    fun loadPluginFromPath(path: String): Result<PluginInterface> {
        val apkFile = File(path)
        if (!apkFile.exists()) {
            return Result.failure(Exception("Plugin file not found: $path"))
        }

        // Android 14+ (API 34): Ensure file is read-only
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (apkFile.canWrite()) {
                Log.d(TAG, "Setting external APK to read-only for Android 14+ compatibility")
                apkFile.setReadOnly()
            }
        }

        return loadPluginFromFile(apkFile)
    }

    /**
     * Get the currently loaded plugin instance.
     */
    fun getPlugin(): PluginInterface? = pluginInstance

    /**
     * Get the plugin's Resources object.
     */
    fun getPluginResources(): Resources? = pluginResources

    /**
     * Get the plugin's AssetManager.
     */
    fun getPluginAssetManager(): AssetManager? = pluginAssetManager

    /**
     * Check if a plugin is currently loaded.
     */
    fun isPluginLoaded(): Boolean = pluginInstance != null

    /**
     * Unload the current plugin.
     */
    fun unloadPlugin() {
        pluginInstance?.onDestroy()
        pluginInstance = null
        pluginClassLoader = null
        pluginResources = null
        pluginAssetManager = null
        Log.i(TAG, "Plugin unloaded")
    }

    /**
     * Copy APK file from assets to internal storage.
     *
     * Android 14 (API 34)+ Security Requirement:
     * - Dynamically loaded files MUST be set to read-only
     * - Otherwise, SecurityException will be thrown when DexClassLoader attempts to load
     * - Reference: https://developer.android.com/about/versions/14/behavior-changes-14#safer-dynamic-code-loading
     */
    private fun copyApkFromAssets(): File? {
        return try {
            val outputFile = File(context.filesDir, PLUGIN_APK_NAME)

            // Delete existing file first (required for Android 14+ when file is read-only)
            if (outputFile.exists()) {
                // Read-only files cannot be deleted directly, make writable first
                outputFile.setWritable(true)
                val deleted = outputFile.delete()
                Log.d(TAG, "Existing APK file deleted: $deleted")
            }

            // Copy APK from assets
            context.assets.open(PLUGIN_APK_NAME).use { input ->
                FileOutputStream(outputFile).use { output ->
                    input.copyTo(output)
                }
            }

            // Android 14+ (API 34) REQUIRED: Set file to read-only before loading DEX
            // Without this, DexClassLoader will throw SecurityException
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val readOnlySet = outputFile.setReadOnly()
                Log.d(TAG, "setReadOnly() result: $readOnlySet, canWrite: ${outputFile.canWrite()}")
                if (!readOnlySet || outputFile.canWrite()) {
                    Log.e(TAG, "CRITICAL: Failed to set APK as read-only - DEX loading will fail on Android 14+")
                }
            }

            Log.d(TAG, "APK file copied to: ${outputFile.absolutePath}")
            outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy APK from assets", e)
            null
        }
    }

    /**
     * Get available plugin files in assets.
     */
    fun getAvailablePlugins(): List<String> {
        return try {
            context.assets.list("")?.filter {
                it.endsWith(".apk") || it.endsWith(".dex")
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list plugins", e)
            emptyList()
        }
    }
}
