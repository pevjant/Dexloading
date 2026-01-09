package com.example.plugin

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import com.example.dexloadingtest.plugin.PluginInterface

/**
 * Plugin implementation using Dynamic Feature Module with SplitCompat.
 *
 * With SplitCompat, resources are automatically merged into the Context.
 * No need for PluginContextWrapper or manual resource handling.
 */
class PluginImpl : PluginInterface {

    companion object {
        private const val TAG = "PluginImpl"
    }

    private var context: Context? = null

    override fun initialize(context: Context) {
        this.context = context
        Log.d(TAG, "Plugin initialized with SplitCompat context")
    }

    override fun getName(): String =
        context?.getString(R.string.plugin_name) ?: "Dynamic Plugin"

    override fun getVersion(): String =
        context?.getString(R.string.plugin_version) ?: "1.0.0"

    override fun createView(context: Context): View {
        Log.d(TAG, "Creating plugin view using standard inflation")

        // With SplitCompat, standard inflation works directly!
        // No need for PluginContextWrapper or XmlResourceParser workarounds
        return LayoutInflater.from(context)
            .inflate(R.layout.plugin_view, null)
    }

    override fun onDestroy() {
        Log.d(TAG, "Plugin destroyed")
        context = null
    }
}
