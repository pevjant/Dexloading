package com.example.dexloadingtest

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.dexloadingtest.plugin.DynamicPluginLoader
import com.example.dexloadingtest.plugin.PluginInterface
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var pluginLoader: DynamicPluginLoader
    private lateinit var btnLoadPlugin: MaterialButton
    private lateinit var tvStatus: TextView
    private lateinit var pluginContainer: FrameLayout

    private var loadedPlugin: PluginInterface? = null
    // GloballyDynamic handles resource loading automatically - no SplitCompat needed

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        setupWindowInsets()
        initViews()
        initPluginLoader()
        setupClickListeners()

        // Log current resources for demonstration
        logCurrentResources()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun initViews() {
        btnLoadPlugin = findViewById(R.id.btnLoadPlugin)
        tvStatus = findViewById(R.id.tvStatus)
        pluginContainer = findViewById(R.id.pluginContainer)
    }

    private fun initPluginLoader() {
        pluginLoader = DynamicPluginLoader(this)

        // Check if module is installed
        val isInstalled = pluginLoader.isModuleInstalled()
        Log.d(TAG, "Plugin module installed: $isInstalled")

        if (isInstalled) {
            tvStatus.text = "플러그인 모듈 설치됨 - 로드 가능"
            tvStatus.setTextColor(getColor(R.color.sample_green))
        } else {
            tvStatus.text = "플러그인 모듈 미설치 - 설치 필요"
            tvStatus.setTextColor(getColor(R.color.sample_yellow))
        }
    }

    private fun setupClickListeners() {
        btnLoadPlugin.setOnClickListener {
            if (loadedPlugin != null) {
                unloadPlugin()
            } else {
                loadPlugin()
            }
        }
    }

    private fun loadPlugin() {
        if (!pluginLoader.isModuleInstalled()) {
            // Need to install module first
            tvStatus.text = "모듈 설치 중..."
            tvStatus.setTextColor(getColor(R.color.sample_yellow))

            pluginLoader.installModule(
                onSuccess = {
                    runOnUiThread {
                        performPluginLoad()
                    }
                },
                onFailure = { error ->
                    runOnUiThread {
                        tvStatus.text = "모듈 설치 실패: ${error.message}"
                        tvStatus.setTextColor(getColor(R.color.sample_red))
                        Log.e(TAG, "Module installation failed", error)
                        Toast.makeText(this, "모듈 설치 실패: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                }
            )
        } else {
            performPluginLoad()
        }
    }

    private fun performPluginLoad() {
        tvStatus.text = getString(R.string.status_loading)
        tvStatus.setTextColor(getColor(R.color.sample_yellow))

        val result = pluginLoader.loadPlugin(this)

        result.fold(
            onSuccess = { plugin ->
                loadedPlugin = plugin
                showPluginUI(plugin)
                tvStatus.text = "${getString(R.string.status_success)} - ${plugin.getName()} v${plugin.getVersion()}"
                tvStatus.setTextColor(getColor(R.color.sample_green))
                btnLoadPlugin.text = "플러그인 언로드"
                Toast.makeText(this, "플러그인 로드 완료!", Toast.LENGTH_SHORT).show()
            },
            onFailure = { error ->
                tvStatus.text = "${getString(R.string.status_error)}: ${error.message}"
                tvStatus.setTextColor(getColor(R.color.sample_red))
                Log.e(TAG, "Plugin load failed", error)
                Toast.makeText(this, "플러그인 로드 실패: ${error.message}", Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun showPluginUI(plugin: PluginInterface) {
        try {
            // Create plugin view
            val pluginView = plugin.createView(this)

            // Clear container and add plugin view
            pluginContainer.removeAllViews()
            pluginContainer.addView(pluginView)
            pluginContainer.visibility = View.VISIBLE

            Log.d(TAG, "Plugin UI displayed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show plugin UI", e)
            Toast.makeText(this, "플러그인 UI 표시 실패", Toast.LENGTH_SHORT).show()
        }
    }

    private fun unloadPlugin() {
        loadedPlugin?.onDestroy()
        loadedPlugin = null

        pluginContainer.removeAllViews()
        pluginContainer.visibility = View.GONE

        tvStatus.text = getString(R.string.status_ready)
        tvStatus.setTextColor(getColor(R.color.accent))
        btnLoadPlugin.text = getString(R.string.btn_load_plugin)

        Toast.makeText(this, "플러그인 언로드 완료", Toast.LENGTH_SHORT).show()
    }

    private fun logCurrentResources() {
        // Log colors from resources
        Log.d(TAG, "=== Current App Resources ===")
        Log.d(TAG, "Color - sample_red: ${Integer.toHexString(getColor(R.color.sample_red))}")
        Log.d(TAG, "Color - sample_green: ${Integer.toHexString(getColor(R.color.sample_green))}")
        Log.d(TAG, "Color - sample_blue: ${Integer.toHexString(getColor(R.color.sample_blue))}")
        Log.d(TAG, "Color - sample_yellow: ${Integer.toHexString(getColor(R.color.sample_yellow))}")

        // Log strings from resources
        Log.d(TAG, "String - title_main: ${getString(R.string.title_main)}")
        Log.d(TAG, "String - subtitle_main: ${getString(R.string.subtitle_main)}")
        Log.d(TAG, "String - info_dex_loading: ${getString(R.string.info_dex_loading)}")
        Log.d(TAG, "=============================")
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up plugin when activity is destroyed
        loadedPlugin?.onDestroy()
        loadedPlugin = null
    }
}
