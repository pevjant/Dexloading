package com.example.plugin

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import android.graphics.BitmapFactory
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import com.example.dexloadingtest.plugin.PluginInterface

/**
 * Plugin implementation that demonstrates dynamic loading capabilities.
 * Uses plugin's own R class to access layout, drawable, and other resources.
 */
class PluginImpl : PluginInterface {

    companion object {
        private const val TAG = "PluginImpl"
    }

    private var hostContext: Context? = null
    private var pluginResources: Resources? = null
    private var pluginAssets: AssetManager? = null

    override fun initialize(
        hostContext: Context,
        pluginResources: Resources,
        pluginAssets: AssetManager
    ) {
        this.hostContext = hostContext
        this.pluginResources = pluginResources
        this.pluginAssets = pluginAssets
        Log.d(TAG, "Plugin initialized - Resources available: ${pluginResources != null}")
    }

    override fun getName(): String = pluginResources?.getString(R.string.plugin_name) ?: "Dynamic Plugin"

    override fun getVersion(): String = pluginResources?.getString(R.string.plugin_version) ?: "1.0.0"

    override fun createView(hostContext: Context): View {
        Log.d(TAG, "Creating plugin view using XML layout")

        // Create context wrapper that uses plugin's Resources for resolving
        // @color/, @drawable/, @string/ references during inflation
        val pluginContext = PluginContextWrapper(hostContext, pluginResources!!, pluginAssets!!)

        // CRITICAL: Get XmlResourceParser directly from plugin's Resources
        // LayoutInflater.inflate(int, ViewGroup) internally calls getLayout() on its own Resources,
        // which would use host's Resources (0x7f). We must use plugin's Resources (0x71).
        val parser = pluginResources!!.getLayout(R.layout.plugin_view)
        val inflater = LayoutInflater.from(pluginContext)
        val view = inflater.inflate(parser, null)

        // R.id.xxx matches because both use plugin's resource table (0x71)
        setupImageView(view)

        return view
    }

    private fun setupImageView(view: View) {
        try {
            // With XmlResourceParser, R.id.xxx directly matches the inflated view's IDs
            val imageView = view.findViewById<ImageView>(R.id.ivPluginImage)
            if (imageView == null) {
                Log.e(TAG, "ImageView not found")
                return
            }

            // Use plugin's R.drawable directly
            val drawable = pluginResources?.getDrawable(R.drawable.plugin_image, null)
            if (drawable != null) {
                imageView.setImageDrawable(drawable)
                Log.d(TAG, "Image loaded from drawable resource")
            } else {
                // Fallback: try loading from assets
                loadImageFromAssets(imageView)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set image", e)
        }
    }

    private fun loadImageFromAssets(imageView: ImageView) {
        try {
            val inputStream = pluginAssets?.open("plugin_image.png")
            if (inputStream != null) {
                val bitmap = BitmapFactory.decodeStream(inputStream)
                imageView.setImageBitmap(bitmap)
                inputStream.close()
                Log.d(TAG, "Image loaded from assets")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Image not found in assets", e)
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "Plugin destroyed")
        hostContext = null
        pluginResources = null
        pluginAssets = null
    }
}

/**
 * Context wrapper that provides plugin's own resources.
 * This allows LayoutInflater to use plugin's layout and drawable resources.
 */
class PluginContextWrapper(
    base: Context,
    private val pluginResources: Resources,
    private val pluginAssets: AssetManager
) : android.content.ContextWrapper(base) {

    private var cachedInflater: LayoutInflater? = null

    override fun getResources(): Resources = pluginResources

    override fun getAssets(): AssetManager = pluginAssets

    override fun getTheme(): Resources.Theme {
        // Create a theme from plugin resources
        return pluginResources.newTheme().apply {
            // Apply a basic theme
            applyStyle(android.R.style.Theme_Material_Light, true)
        }
    }

    override fun getSystemService(name: String): Any? {
        // Return a LayoutInflater bound to this context (with pluginResources)
        // This ensures attribute resolution (@color/, @drawable/, etc.) uses plugin's resources
        if (name == Context.LAYOUT_INFLATER_SERVICE) {
            if (cachedInflater == null) {
                val baseInflater = super.getSystemService(name) as LayoutInflater
                cachedInflater = baseInflater.cloneInContext(this)
            }
            return cachedInflater
        }
        return super.getSystemService(name)
    }
}
