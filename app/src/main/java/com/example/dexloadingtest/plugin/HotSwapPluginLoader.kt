package com.example.dexloadingtest.plugin

import android.content.Context
import android.util.Log
import dalvik.system.DexClassLoader
import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * A plugin loader that supports hot swapping.
 *
 * This loader uses DexClassLoader to load a plugin from a DEX file.
 * It allows replacing the plugin with a new version without restarting the app.
 *
 * Resources are accessed via shared.R which is included in the host APK.
 * Plugin DEX files reference shared.R constants, and the host Context resolves them.
 */
class HotSwapPluginLoader(private val context: Context) {

    companion object {
        private const val TAG = "HotSwapPluginLoader"
    }

    private val lock = ReentrantReadWriteLock()
    private var currentPlugin: PluginInterface? = null
    private var currentClassLoader: DexClassLoader? = null
    private var loadCount = 0

    /**
     * Gets the current plugin, or null if none is loaded.
     */
    val plugin: PluginInterface?
        get() = currentPlugin

    /**
     * Checks if a plugin is currently loaded.
     */
    val isPluginLoaded: Boolean
        get() = currentPlugin != null

    /**
     * Executes an action with the current plugin in a thread-safe manner.
     */
    fun usePlugin(action: (PluginInterface) -> Unit) {
        lock.readLock().lock()
        try {
            currentPlugin?.let(action)
        } finally {
            lock.readLock().unlock()
        }
    }

    /**
     * Loads or swaps a plugin from a given DEX file.
     *
     * Each load creates a new ClassLoader with a unique cache directory to bypass
     * Android's class caching, enabling same-named classes to be reloaded.
     *
     * @param dexPath The path to the DEX/APK file.
     * @param pluginClassName The fully qualified name of the plugin's entry point class.
     */
    fun loadOrSwapPlugin(dexPath: String, pluginClassName: String): Result<PluginInterface> {
        lock.writeLock().lock()
        try {
            // 1. Clean up the old plugin and ClassLoader
            currentPlugin?.onDestroy()
            currentPlugin = null
            currentClassLoader = null
            System.gc() // Suggest garbage collection to help unload old classes

            // 2. Create a unique cache directory for this load
            // This bypasses Android's class caching mechanism
            loadCount++
            val dexOutputDir = File(context.cacheDir, "dex_$loadCount")
            if (!dexOutputDir.exists()) {
                dexOutputDir.mkdirs()
            }

            // 3. Create a new ChildFirstClassLoader
            val newClassLoader = ChildFirstClassLoader(
                dexPath,
                dexOutputDir.absolutePath,
                null,
                context.classLoader
            )
            currentClassLoader = newClassLoader

            // 4. Load the new plugin class and instantiate it
            val pluginClass = newClassLoader.loadClass(pluginClassName)
            val newPlugin = pluginClass.getDeclaredConstructor().newInstance() as PluginInterface
            newPlugin.initialize(context)

            currentPlugin = newPlugin

            Log.d(TAG, "Plugin loaded successfully: ${newPlugin.getName()} v${newPlugin.getVersion()}")
            return Result.success(newPlugin)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load or swap plugin", e)
            return Result.failure(e)
        } finally {
            lock.writeLock().unlock()
        }
    }

    /**
     * Unloads the current plugin.
     */
    fun unloadPlugin() {
        lock.writeLock().lock()
        try {
            currentPlugin?.onDestroy()
            currentPlugin = null
            currentClassLoader = null
            System.gc()
            Log.d(TAG, "Plugin unloaded")
        } finally {
            lock.writeLock().unlock()
        }
    }
}
