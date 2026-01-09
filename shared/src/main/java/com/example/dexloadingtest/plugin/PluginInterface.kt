package com.example.dexloadingtest.plugin

import android.content.Context
import android.view.View

/**
 * Plugin interface that all plugins must implement.
 * This interface allows the host app to communicate with dynamically loaded plugins.
 *
 * With Dynamic Feature Modules and SplitCompat, resource handling is automatic.
 * The Context provided already has merged resources from all installed splits.
 */
interface PluginInterface {

    /**
     * Initialize the plugin with the context.
     * Called once when the plugin is loaded.
     *
     * With SplitCompat, the context already has access to plugin resources
     * through automatic resource merging.
     *
     * @param context The context (Activity context with SplitCompat.installActivity called)
     */
    fun initialize(context: Context)

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
     * With SplitCompat, standard inflation works directly.
     *
     * @param context The context to use for creating views
     * @return The plugin's root view
     */
    fun createView(context: Context): View

    /**
     * Called when the plugin is being unloaded.
     * Perform any cleanup here.
     */
    fun onDestroy()
}
