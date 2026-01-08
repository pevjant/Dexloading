package com.example.dexloadingtest.plugin

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import android.view.View

/**
 * Plugin interface that all plugins must implement.
 * This interface allows the host app to communicate with dynamically loaded plugins.
 */
interface PluginInterface {

    /**
     * Initialize the plugin with the host context and plugin's own resources.
     * Called once when the plugin is loaded.
     *
     * @param hostContext The host application context
     * @param pluginResources The plugin's own Resources object (for accessing plugin's drawable, layout, values, etc.)
     * @param pluginAssets The plugin's own AssetManager (for accessing plugin's assets folder)
     */
    fun initialize(
        hostContext: Context,
        pluginResources: Resources,
        pluginAssets: AssetManager
    )

    /**
     * Get the plugin name.
     * @return The display name of the plugin
     */
    fun getName(): String

    /**
     * Get the plugin version.
     * @return The version string of the plugin
     */
    fun getVersion(): String

    /**
     * Create the plugin's UI view.
     * The plugin should use its own Resources to inflate layouts and access drawables.
     *
     * @param hostContext The host context to use for creating views
     * @return The plugin's root view
     */
    fun createView(hostContext: Context): View

    /**
     * Called when the plugin is being unloaded.
     * Perform any cleanup here.
     */
    fun onDestroy()
}
