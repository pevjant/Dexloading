package com.example.dexloadingtest.plugin

import android.app.Activity
import android.content.Context
import android.util.Log
import com.jeppeman.globallydynamic.globalsplitinstall.GlobalSplitInstallManagerFactory
import com.jeppeman.globallydynamic.globalsplitinstall.GlobalSplitInstallRequest

/**
 * Dynamic Plugin Loader using GloballyDynamic API.
 *
 * Replaces the legacy PluginLoader that used DexClassLoader and reflection.
 * With Dynamic Feature Modules, resource handling is automatic via SplitCompat.
 */
class DynamicPluginLoader(private val context: Context) {

    companion object {
        private const val TAG = "DynamicPluginLoader"
        const val MODULE_NAME = "pluginFeature"
        private const val PLUGIN_CLASS = "com.example.plugin.PluginImpl"
    }

    private val splitInstallManager = GlobalSplitInstallManagerFactory.create(context)

    /**
     * Check if the plugin module is installed.
     */
    fun isModuleInstalled(): Boolean {
        val installed = splitInstallManager.installedModules.contains(MODULE_NAME)
        Log.d(TAG, "Module '$MODULE_NAME' installed: $installed")
        return installed
    }

    /**
     * Install the plugin module.
     * Uses callback pattern for simplicity.
     *
     * @param onSuccess Called when installation completes
     * @param onFailure Called when installation fails
     */
    fun installModule(
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (isModuleInstalled()) {
            Log.d(TAG, "Module already installed")
            onSuccess()
            return
        }

        val request = GlobalSplitInstallRequest.newBuilder()
            .addModule(MODULE_NAME)
            .build()

        splitInstallManager.startInstall(request)
            .addOnSuccessListener { sessionId ->
                Log.d(TAG, "Module installation started, session: $sessionId")
                // Note: For real implementation, you would monitor session state
                // For local testing with --local-testing flag, installation is instant
                onSuccess()
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Failed to start installation", exception)
                onFailure(exception)
            }
    }

    /**
     * Load the plugin from the installed module.
     *
     * GloballyDynamic handles resource merging automatically.
     *
     * @param activityContext The Activity context
     * @return Result containing the PluginInterface or an error
     */
    fun loadPlugin(activityContext: Activity): Result<PluginInterface> {
        return try {
            if (!isModuleInstalled()) {
                return Result.failure(IllegalStateException("Module not installed"))
            }

            // Load the plugin class - GloballyDynamic handles resource access automatically
            val pluginClass = Class.forName(PLUGIN_CLASS)
            val plugin = pluginClass.getDeclaredConstructor().newInstance() as PluginInterface

            // Initialize with the Activity context
            plugin.initialize(activityContext)

            Log.d(TAG, "Plugin loaded: ${plugin.getName()} v${plugin.getVersion()}")
            Result.success(plugin)

        } catch (e: ClassNotFoundException) {
            Log.e(TAG, "Plugin class not found", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load plugin", e)
            Result.failure(e)
        }
    }

    /**
     * Request deferred installation of the module.
     * The module will be installed when the device is idle and connected to WiFi.
     */
    fun deferredInstall() {
        splitInstallManager.deferredInstall(listOf(MODULE_NAME))
            .addOnSuccessListener {
                Log.d(TAG, "Deferred installation requested")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Deferred installation failed", e)
            }
    }
}
