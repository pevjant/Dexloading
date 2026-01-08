package com.example.plugin.examples

import android.content.Context
import android.util.Log

/**
 * Strategy 패턴을 사용한 기능 Override 예제
 *
 * 핵심 아이디어:
 * 1. 기능을 인터페이스로 추상화
 * 2. 호스트 앱에 기본 구현 제공
 * 3. 플러그인이 새로운 구현을 등록하면 override
 * 4. 런타임에 사용할 구현 선택
 */

// ============================================================================
// 1. 기능 인터페이스 정의 (shared 모듈에 위치)
// ============================================================================

/**
 * 사용자 인증 기능 인터페이스
 */
interface AuthenticationStrategy {
    fun login(username: String, password: String): Boolean
    fun logout()
    fun isLoggedIn(): Boolean
    fun getCurrentUser(): String?
}

/**
 * 데이터 저장 기능 인터페이스
 */
interface DataStorageStrategy {
    fun save(key: String, value: String): Boolean
    fun load(key: String): String?
    fun delete(key: String): Boolean
}

/**
 * 이미지 로딩 기능 인터페이스
 */
interface ImageLoaderStrategy {
    fun loadImage(url: String, callback: (ByteArray?) -> Unit)
    fun clearCache()
}

// ============================================================================
// 2. 기능 레지스트리 (호스트 앱에 위치)
// ============================================================================

/**
 * 기능 제공자를 등록하고 관리하는 레지스트리
 *
 * 사용 방법:
 * 1. 호스트 앱 시작 시 기본 구현 등록
 * 2. 플러그인 로드 시 새로운 구현 등록 (override)
 * 3. 필요한 곳에서 get()으로 현재 구현 사용
 */
class FeatureRegistry private constructor() {

    companion object {
        @Volatile
        private var instance: FeatureRegistry? = null

        fun getInstance(): FeatureRegistry {
            return instance ?: synchronized(this) {
                instance ?: FeatureRegistry().also { instance = it }
            }
        }
    }

    private val authStrategies = mutableMapOf<String, AuthenticationStrategy>()
    private val storageStrategies = mutableMapOf<String, DataStorageStrategy>()
    private val imageLoaderStrategies = mutableMapOf<String, ImageLoaderStrategy>()

    private var activeAuthKey: String = "default"
    private var activeStorageKey: String = "default"
    private var activeImageLoaderKey: String = "default"

    // ------------------------------------------------------------------------
    // 인증 기능
    // ------------------------------------------------------------------------

    fun registerAuth(key: String, strategy: AuthenticationStrategy) {
        authStrategies[key] = strategy
        Log.d("FeatureRegistry", "Registered Auth strategy: $key")
    }

    fun setActiveAuth(key: String) {
        if (authStrategies.containsKey(key)) {
            activeAuthKey = key
            Log.d("FeatureRegistry", "Active Auth strategy changed to: $key")
        }
    }

    fun getAuth(): AuthenticationStrategy {
        return authStrategies[activeAuthKey]
            ?: throw IllegalStateException("No Auth strategy registered for: $activeAuthKey")
    }

    // ------------------------------------------------------------------------
    // 저장소 기능
    // ------------------------------------------------------------------------

    fun registerStorage(key: String, strategy: DataStorageStrategy) {
        storageStrategies[key] = strategy
        Log.d("FeatureRegistry", "Registered Storage strategy: $key")
    }

    fun setActiveStorage(key: String) {
        if (storageStrategies.containsKey(key)) {
            activeStorageKey = key
            Log.d("FeatureRegistry", "Active Storage strategy changed to: $key")
        }
    }

    fun getStorage(): DataStorageStrategy {
        return storageStrategies[activeStorageKey]
            ?: throw IllegalStateException("No Storage strategy registered for: $activeStorageKey")
    }

    // ------------------------------------------------------------------------
    // 이미지 로더 기능
    // ------------------------------------------------------------------------

    fun registerImageLoader(key: String, strategy: ImageLoaderStrategy) {
        imageLoaderStrategies[key] = strategy
        Log.d("FeatureRegistry", "Registered ImageLoader strategy: $key")
    }

    fun setActiveImageLoader(key: String) {
        if (imageLoaderStrategies.containsKey(key)) {
            activeImageLoaderKey = key
            Log.d("FeatureRegistry", "Active ImageLoader strategy changed to: $key")
        }
    }

    fun getImageLoader(): ImageLoaderStrategy {
        return imageLoaderStrategies[activeImageLoaderKey]
            ?: throw IllegalStateException("No ImageLoader strategy registered for: $activeImageLoaderKey")
    }

    // ------------------------------------------------------------------------
    // 유틸리티
    // ------------------------------------------------------------------------

    fun listRegisteredFeatures(): Map<String, List<String>> {
        return mapOf(
            "auth" to authStrategies.keys.toList(),
            "storage" to storageStrategies.keys.toList(),
            "imageLoader" to imageLoaderStrategies.keys.toList()
        )
    }
}

// ============================================================================
// 3. 호스트 앱의 기본 구현 (app 모듈)
// ============================================================================

/**
 * 호스트 앱의 기본 인증 구현
 */
class DefaultAuthStrategy(private val context: Context) : AuthenticationStrategy {
    private var loggedIn = false
    private var currentUser: String? = null

    override fun login(username: String, password: String): Boolean {
        Log.d("DefaultAuth", "Login attempt: $username")

        // 간단한 더미 로직
        if (username == "admin" && password == "password") {
            loggedIn = true
            currentUser = username
            Log.d("DefaultAuth", "Login successful")
            return true
        }

        Log.d("DefaultAuth", "Login failed")
        return false
    }

    override fun logout() {
        loggedIn = false
        currentUser = null
        Log.d("DefaultAuth", "Logged out")
    }

    override fun isLoggedIn(): Boolean = loggedIn

    override fun getCurrentUser(): String? = currentUser
}

/**
 * 호스트 앱의 기본 저장소 구현 (SharedPreferences 사용)
 */
class DefaultStorageStrategy(private val context: Context) : DataStorageStrategy {
    private val prefs = context.getSharedPreferences("default_storage", Context.MODE_PRIVATE)

    override fun save(key: String, value: String): Boolean {
        return prefs.edit().putString(key, value).commit()
    }

    override fun load(key: String): String? {
        return prefs.getString(key, null)
    }

    override fun delete(key: String): Boolean {
        return prefs.edit().remove(key).commit()
    }
}

// ============================================================================
// 4. 플러그인의 새로운 구현 (plugin 모듈)
// ============================================================================

/**
 * 플러그인이 제공하는 향상된 인증 구현
 * 예: 바이오메트릭, OAuth, 2FA 등
 */
class PluginAuthStrategy(private val context: Context) : AuthenticationStrategy {
    private var loggedIn = false
    private var currentUser: String? = null

    override fun login(username: String, password: String): Boolean {
        Log.d("PluginAuth", "🔐 Enhanced login with biometric: $username")

        // 플러그인의 향상된 로직
        // - 바이오메트릭 인증
        // - 2단계 인증
        // - OAuth 통합 등

        if (username.isNotEmpty() && password.length >= 8) {
            loggedIn = true
            currentUser = username
            Log.d("PluginAuth", "✅ Enhanced login successful with plugin")
            return true
        }

        Log.d("PluginAuth", "❌ Enhanced login failed")
        return false
    }

    override fun logout() {
        loggedIn = false
        currentUser = null
        Log.d("PluginAuth", "🔓 Enhanced logout from plugin")
    }

    override fun isLoggedIn(): Boolean = loggedIn

    override fun getCurrentUser(): String? = currentUser
}

/**
 * 플러그인이 제공하는 클라우드 저장소 구현
 */
class PluginCloudStorageStrategy(private val context: Context) : DataStorageStrategy {
    private val cache = mutableMapOf<String, String>()

    override fun save(key: String, value: String): Boolean {
        Log.d("PluginStorage", "☁️ Saving to cloud: $key")
        cache[key] = value

        // 실제로는 서버에 업로드
        // uploadToServer(key, value)

        return true
    }

    override fun load(key: String): String? {
        Log.d("PluginStorage", "☁️ Loading from cloud: $key")

        // 실제로는 서버에서 다운로드
        // return downloadFromServer(key)

        return cache[key]
    }

    override fun delete(key: String): Boolean {
        Log.d("PluginStorage", "☁️ Deleting from cloud: $key")
        cache.remove(key)

        // 실제로는 서버에서 삭제
        // deleteFromServer(key)

        return true
    }
}

// ============================================================================
// 5. 사용 예제
// ============================================================================

/**
 * 호스트 앱에서 기본 구현 등록 (Application.onCreate() 등에서)
 */
fun initializeHostApp(context: Context) {
    val registry = FeatureRegistry.getInstance()

    // 기본 구현 등록
    registry.registerAuth("default", DefaultAuthStrategy(context))
    registry.registerStorage("default", DefaultStorageStrategy(context))

    Log.d("HostApp", "Default features registered")
}

/**
 * 플러그인 로드 시 새로운 구현 등록
 */
fun onPluginLoaded(context: Context) {
    val registry = FeatureRegistry.getInstance()

    // 플러그인의 새로운 구현 등록
    registry.registerAuth("plugin_enhanced", PluginAuthStrategy(context))
    registry.registerStorage("plugin_cloud", PluginCloudStorageStrategy(context))

    // 활성 구현을 플러그인으로 변경 (override)
    registry.setActiveAuth("plugin_enhanced")
    registry.setActiveStorage("plugin_cloud")

    Log.d("Plugin", "Plugin features registered and activated")
}

/**
 * 앱의 비즈니스 로직에서 사용
 * - 어떤 구현이 활성화되어 있는지 몰라도 됨
 * - 런타임에 플러그인이 로드되면 자동으로 새 구현 사용
 */
fun businessLogicExample() {
    val registry = FeatureRegistry.getInstance()

    // 인증 기능 사용 (기본 or 플러그인 구현)
    val auth = registry.getAuth()
    val loginSuccess = auth.login("testuser", "password123")

    if (loginSuccess) {
        val user = auth.getCurrentUser()
        Log.d("App", "Logged in as: $user")

        // 저장소 기능 사용 (기본 or 플러그인 구현)
        val storage = registry.getStorage()
        storage.save("last_login", System.currentTimeMillis().toString())
    }
}

/**
 * 설정 화면에서 사용자가 구현 선택 가능
 */
fun userSelectsImplementation(featureType: String, implementation: String) {
    val registry = FeatureRegistry.getInstance()

    when (featureType) {
        "auth" -> registry.setActiveAuth(implementation)
        "storage" -> registry.setActiveStorage(implementation)
        "imageLoader" -> registry.setActiveImageLoader(implementation)
    }

    Log.d("Settings", "User selected $featureType implementation: $implementation")
}

// ============================================================================
// 6. 고급 패턴: 기능 조합 (Decorator Pattern)
// ============================================================================

/**
 * 기존 구현을 래핑하여 기능 추가
 */
class CachedStorageStrategy(
    private val baseStrategy: DataStorageStrategy
) : DataStorageStrategy {
    private val cache = mutableMapOf<String, String>()

    override fun save(key: String, value: String): Boolean {
        cache[key] = value
        return baseStrategy.save(key, value)
    }

    override fun load(key: String): String? {
        // 캐시 우선 확인
        return cache[key] ?: baseStrategy.load(key)?.also {
            cache[key] = it
        }
    }

    override fun delete(key: String): Boolean {
        cache.remove(key)
        return baseStrategy.delete(key)
    }
}

/**
 * 조합 예제
 */
fun demonstrateComposition(context: Context) {
    val registry = FeatureRegistry.getInstance()

    // 기본 저장소를 캐싱으로 래핑
    val baseStorage = DefaultStorageStrategy(context)
    val cachedStorage = CachedStorageStrategy(baseStorage)

    registry.registerStorage("cached_default", cachedStorage)
    registry.setActiveStorage("cached_default")

    Log.d("Composition", "Using cached storage wrapper")
}
