package com.example.dexloadingtest.dynamic

import android.app.Activity
import android.content.Context
import android.util.Log
import com.jeppeman.globallydynamic.globalsplitinstall.GlobalSplitInstallManager
import com.jeppeman.globallydynamic.globalsplitinstall.GlobalSplitInstallManagerFactory
import com.jeppeman.globallydynamic.globalsplitinstall.GlobalSplitInstallRequest
import com.jeppeman.globallydynamic.globalsplitinstall.GlobalSplitInstallSessionState
import com.jeppeman.globallydynamic.globalsplitinstall.GlobalSplitInstallSessionStatus
import com.jeppeman.globallydynamic.globalsplitinstall.GlobalSplitInstallUpdatedListener

/**
 * Generic Dynamic Module Loader using GloballyDynamic API.
 *
 * Copy this file to your production app and customize:
 * 1. Package name
 * 2. Module configurations in ModuleRegistry (or inline)
 *
 * Usage:
 * ```kotlin
 * val loader = DynamicModuleLoader(context)
 *
 * // Check if installed
 * if (loader.isInstalled("featureName")) {
 *     // Load and use
 * }
 *
 * // Install with progress
 * loader.install("featureName",
 *     onProgress = { progress -> updateUI(progress) },
 *     onSuccess = { loadFeature() },
 *     onFailure = { error -> showError(error) }
 * )
 * ```
 */
class DynamicModuleLoader(context: Context) {

    companion object {
        private const val TAG = "DynamicModuleLoader"
    }

    private val splitInstallManager: GlobalSplitInstallManager =
        GlobalSplitInstallManagerFactory.create(context.applicationContext)

    private var activeListener: GlobalSplitInstallUpdatedListener? = null

    /**
     * Get all currently installed dynamic modules.
     */
    val installedModules: Set<String>
        get() = splitInstallManager.installedModules

    /**
     * Check if a module is installed.
     *
     * @param moduleName The name of the dynamic feature module
     */
    fun isInstalled(moduleName: String): Boolean {
        return splitInstallManager.installedModules.contains(moduleName)
    }

    /**
     * Install a dynamic feature module with progress tracking.
     *
     * @param moduleName Module name as declared in settings.gradle.kts (without colon)
     * @param onProgress Called with download progress (0.0 to 1.0)
     * @param onSuccess Called when installation completes
     * @param onFailure Called when installation fails
     */
    fun install(
        moduleName: String,
        onProgress: ((Float) -> Unit)? = null,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (isInstalled(moduleName)) {
            Log.d(TAG, "Module '$moduleName' already installed")
            onSuccess()
            return
        }

        // Clean up any existing listener
        activeListener?.let { splitInstallManager.unregisterListener(it) }

        // Create progress listener
        val listener = object : GlobalSplitInstallUpdatedListener {
            override fun onStateUpdate(state: GlobalSplitInstallSessionState) {
                when (state.status()) {
                    GlobalSplitInstallSessionStatus.DOWNLOADING -> {
                        val progress = state.bytesDownloaded().toFloat() /
                            state.totalBytesToDownload().coerceAtLeast(1)
                        Log.d(TAG, "Downloading '$moduleName': ${(progress * 100).toInt()}%")
                        onProgress?.invoke(progress)
                    }

                    GlobalSplitInstallSessionStatus.INSTALLING -> {
                        Log.d(TAG, "Installing '$moduleName'")
                        onProgress?.invoke(0.95f)
                    }

                    GlobalSplitInstallSessionStatus.INSTALLED -> {
                        Log.d(TAG, "Module '$moduleName' installed successfully")
                        cleanup()
                        onProgress?.invoke(1.0f)
                        onSuccess()
                    }

                    GlobalSplitInstallSessionStatus.FAILED -> {
                        val errorCode = state.errorCode()
                        Log.e(TAG, "Installation failed for '$moduleName', error: $errorCode")
                        cleanup()
                        onFailure(ModuleInstallException(moduleName, errorCode))
                    }

                    GlobalSplitInstallSessionStatus.CANCELED -> {
                        Log.w(TAG, "Installation canceled for '$moduleName'")
                        cleanup()
                        onFailure(ModuleInstallException(moduleName, "Installation canceled"))
                    }

                    GlobalSplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION -> {
                        Log.w(TAG, "User confirmation required for '$moduleName'")
                        // Handle in production: splitInstallManager.startConfirmationDialogForResult(...)
                    }

                    else -> {
                        Log.d(TAG, "Status: ${state.status()} for '$moduleName'")
                    }
                }
            }

            private fun cleanup() {
                splitInstallManager.unregisterListener(this)
                activeListener = null
            }
        }

        activeListener = listener
        splitInstallManager.registerListener(listener)

        // Start installation
        val request = GlobalSplitInstallRequest.newBuilder()
            .addModule(moduleName)
            .build()

        splitInstallManager.startInstall(request)
            .addOnSuccessListener { sessionId ->
                Log.d(TAG, "Installation session started: $sessionId for '$moduleName'")
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Failed to start installation for '$moduleName'", exception)
                activeListener?.let { splitInstallManager.unregisterListener(it) }
                activeListener = null
                onFailure(exception)
            }
    }

    /**
     * Install multiple modules at once.
     */
    fun installMultiple(
        moduleNames: List<String>,
        onProgress: ((Float) -> Unit)? = null,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val notInstalled = moduleNames.filter { !isInstalled(it) }

        if (notInstalled.isEmpty()) {
            onSuccess()
            return
        }

        val request = GlobalSplitInstallRequest.newBuilder().apply {
            notInstalled.forEach { addModule(it) }
        }.build()

        // Similar listener logic as single install
        // Omitted for brevity - implement if needed
        splitInstallManager.startInstall(request)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    /**
     * Request deferred installation (background, when idle).
     */
    fun deferredInstall(moduleName: String) {
        splitInstallManager.deferredInstall(listOf(moduleName))
            .addOnSuccessListener {
                Log.d(TAG, "Deferred installation requested for '$moduleName'")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Deferred installation failed for '$moduleName'", e)
            }
    }

    /**
     * Uninstall a dynamic feature module.
     *
     * Note: Only works for on-demand modules, not install-time.
     */
    fun uninstall(moduleName: String, onComplete: () -> Unit = {}) {
        splitInstallManager.deferredUninstall(listOf(moduleName))
            .addOnSuccessListener {
                Log.d(TAG, "Uninstall requested for '$moduleName'")
                onComplete()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Uninstall failed for '$moduleName'", e)
                onComplete()
            }
    }

    /**
     * Load a class from an installed dynamic module.
     *
     * @param className Fully qualified class name
     * @return The loaded class, or null if not found
     */
    fun <T> loadClass(className: String): Class<T>? {
        return try {
            @Suppress("UNCHECKED_CAST")
            Class.forName(className) as Class<T>
        } catch (e: ClassNotFoundException) {
            Log.e(TAG, "Class not found: $className", e)
            null
        }
    }

    /**
     * Load and instantiate a class from an installed dynamic module.
     *
     * @param className Fully qualified class name
     * @return Instance of the class, or null if failed
     */
    inline fun <reified T> loadAndInstantiate(className: String): T? {
        return try {
            val clazz = Class.forName(className)
            clazz.getDeclaredConstructor().newInstance() as T
        } catch (e: Exception) {
            Log.e("DynamicModuleLoader", "Failed to load and instantiate: $className", e)
            null
        }
    }

    /**
     * Cancel an ongoing installation.
     */
    fun cancelInstall(sessionId: Int) {
        splitInstallManager.cancelInstall(sessionId)
            .addOnSuccessListener { Log.d(TAG, "Installation canceled: $sessionId") }
            .addOnFailureListener { Log.e(TAG, "Cancel failed: $sessionId", it) }
    }

    /**
     * Clean up resources. Call when no longer needed.
     */
    fun cleanup() {
        activeListener?.let { splitInstallManager.unregisterListener(it) }
        activeListener = null
    }
}

/**
 * Exception for module installation failures.
 */
class ModuleInstallException : Exception {
    val moduleName: String
    val errorCode: Int?

    constructor(moduleName: String, errorCode: Int) : super(
        "Failed to install module '$moduleName', error code: $errorCode"
    ) {
        this.moduleName = moduleName
        this.errorCode = errorCode
    }

    constructor(moduleName: String, message: String) : super(
        "Failed to install module '$moduleName': $message"
    ) {
        this.moduleName = moduleName
        this.errorCode = null
    }
}
