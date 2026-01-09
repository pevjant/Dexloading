package com.example.dexloadingtest.dynamic

/**
 * Registry for Dynamic Feature Modules.
 *
 * Centralized configuration for all dynamic modules in the app.
 * Copy this file to your production app and customize the entries.
 *
 * Usage:
 * ```kotlin
 * val module = ModuleRegistry.get("pluginFeature")
 * val entryPoint = module?.entryPoint
 * ```
 */
object ModuleRegistry {

    /**
     * Module configuration data class.
     *
     * @property moduleName Name as declared in settings.gradle.kts (without colon)
     * @property entryPoint Fully qualified name of the entry class
     * @property displayName Human-readable name for UI
     * @property description Module description
     */
    data class ModuleConfig(
        val moduleName: String,
        val entryPoint: String,
        val displayName: String = moduleName,
        val description: String = ""
    )

    /**
     * Registered modules.
     *
     * Add your dynamic feature modules here.
     * Module name must match the directory name in settings.gradle.kts
     */
    private val modules = mapOf(
        "pluginFeature" to ModuleConfig(
            moduleName = "pluginFeature",
            entryPoint = "com.example.plugin.PluginImpl",
            displayName = "Sample Plugin",
            description = "Sample dynamic feature module for demonstration"
        )
        // Add more modules:
        // "anotherFeature" to ModuleConfig(
        //     moduleName = "anotherFeature",
        //     entryPoint = "com.example.another.AnotherImpl",
        //     displayName = "Another Feature",
        //     description = "Description here"
        // )
    )

    /**
     * Get all registered module names.
     */
    val allModuleNames: Set<String>
        get() = modules.keys

    /**
     * Get all registered modules.
     */
    val allModules: Collection<ModuleConfig>
        get() = modules.values

    /**
     * Get configuration for a specific module.
     *
     * @param moduleName The module name
     * @return ModuleConfig or null if not registered
     */
    fun get(moduleName: String): ModuleConfig? = modules[moduleName]

    /**
     * Check if a module is registered.
     *
     * @param moduleName The module name
     */
    fun isRegistered(moduleName: String): Boolean = modules.containsKey(moduleName)

    /**
     * Get entry point class name for a module.
     *
     * @param moduleName The module name
     * @return Entry point class name or null
     */
    fun getEntryPoint(moduleName: String): String? = modules[moduleName]?.entryPoint
}
