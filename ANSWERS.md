# 질문 답변 정리

## 질문 1: 플러그인에서 `R.id.xxx` 형태로 호스트 리소스 접근 가능한가?

### 결론: **가능합니다!** (여러 방법 존재)

### ❌ 불가능한 것:
```kotlin
// 플러그인 코드에서 직접 참조는 불가능
import com.example.dexloadingtest.R  // ❌ 컴파일 에러
val color = R.color.sample_red        // ❌ 플러그인에 호스트 R 클래스 없음
```

**이유:**
- 플러그인 빌드 시 호스트 앱(com.example.dexloadingtest)을 의존성으로 가지고 있지 않음
- plugin/build.gradle.kts에는 `implementation(project(":app"))` 없음
- 플러그인 APK에 호스트의 R 클래스가 포함되지 않음

---

### ✅ 가능한 방법들:

#### **방법 1: getIdentifier() - 실용적** ⭐ 추천
```kotlin
// 플러그인 코드
val hostResources = hostContext.resources
val hostPackage = hostContext.packageName  // "com.example.dexloadingtest"

// R.color.sample_red 접근
val colorId = hostResources.getIdentifier("sample_red", "color", hostPackage)
val color = hostResources.getColor(colorId, null)

// R.drawable.ic_launcher 접근
val iconId = hostResources.getIdentifier("ic_launcher", "drawable", hostPackage)
val icon = hostResources.getDrawable(iconId, null)

// R.id.button 접근
val viewId = hostResources.getIdentifier("button", "id", hostPackage)
val button = view.findViewById<Button>(viewId)
```

**장점:**
- 구현 간단
- 추가 의존성 불필요
- Android 표준 API

**단점:**
- 문자열 하드코딩
- 런타임 에러 가능 (리소스 없으면 0 반환)
- 타입 안전성 없음

**예제 위치:** `examples/ResourceAccessExample.kt`

---

#### **방법 2: Reflection으로 R 클래스 로드**
```kotlin
// 플러그인 코드
// 호스트 ClassLoader를 통해 R 클래스 로드
val hostClassLoader = hostContext.classLoader

// R.color 클래스 로드
val rColorClass = hostClassLoader.loadClass("com.example.dexloadingtest.R\$color")
val sampleRedField = rColorClass.getField("sample_red")
val colorId = sampleRedField.getInt(null)

// 실제 색상 가져오기
val color = hostContext.resources.getColor(colorId, null)
```

**장점:**
- R.xxx.yyy 구조 유지
- 동적으로 다양한 리소스 접근

**단점:**
- Reflection 오버헤드
- ProGuard/R8 난독화 시 문제
- 코드 복잡

**예제 위치:** `examples/ResourceAccessExample.kt`

---

#### **방법 3: 리소스 맵 전달 (인터페이스 확장)** ⭐ 가장 안전
```kotlin
// shared/PluginInterface.kt
data class HostResourceIds(
    val colors: Map<String, Int>,
    val drawables: Map<String, Int>,
    val ids: Map<String, Int>
)

interface PluginInterface {
    fun initialize(
        hostContext: Context,
        pluginResources: Resources,
        pluginAssets: AssetManager,
        hostResourceIds: HostResourceIds  // 추가
    )
}

// app/MainActivity.kt
val hostResourceIds = HostResourceIds(
    colors = mapOf(
        "primary" to R.color.sample_red,
        "accent" to R.color.sample_blue
    ),
    drawables = mapOf(
        "logo" to R.drawable.ic_launcher
    ),
    ids = mapOf(
        "mainButton" to R.id.btnLoadPlugin
    )
)
plugin.initialize(context, pluginRes, pluginAssets, hostResourceIds)

// plugin/PluginImpl.kt
override fun initialize(..., hostResourceIds: HostResourceIds) {
    this.hostResourceIds = hostResourceIds
}

fun useHostResource() {
    val colorId = hostResourceIds.colors["primary"]
    val color = hostContext.resources.getColor(colorId!!, null)
}
```

**장점:**
- 타입 안전
- 컴파일 타임 체크
- 명시적 (어떤 리소스를 공유하는지 명확)
- IDE 자동완성

**단점:**
- 인터페이스 수정 필요
- 초기 설정 코드 증가

---

#### **방법 4: Shared 모듈에 공통 리소스 배치** ⭐ 중복 제거
```
shared/res/
├── drawable/common_logo.xml
├── values/colors.xml  (브랜드 색상)
└── values/strings.xml (공통 문자열)

app/build.gradle.kts:
dependencies {
    implementation(project(":shared"))  // shared 리소스 포함
}

plugin/build.gradle.kts:
dependencies {
    compileOnly(project(":shared"))  // 빌드만, APK 미포함
}

사용:
import com.example.shared.R as SharedR

val brandColor = context.getColor(SharedR.color.brand_primary)
val logo = context.getDrawable(SharedR.drawable.common_logo)
```

**장점:**
- 리소스 중복 완전 제거
- 빌드 타임 체크
- 양방향 접근 가능
- APK 크기 최소화

**단점:**
- 아키텍처 설계 필요
- 초기 모듈 구조화 필요

**예제 위치:** `examples/SharedResourceExample.md`

---

## 질문 2: Strategy 패턴은 런타임에 구현을 선택하는 건가?

### 결론: **맞습니다!**

Strategy 패턴의 핵심 개념:

### 1. **기능을 인터페이스로 추상화**
```kotlin
// shared 모듈
interface AuthenticationStrategy {
    fun login(username: String, password: String): Boolean
    fun logout()
    fun getCurrentUser(): String?
}
```

### 2. **호스트 앱이 기본 구현 제공**
```kotlin
// app 모듈
class DefaultAuthStrategy : AuthenticationStrategy {
    override fun login(username: String, password: String): Boolean {
        // 기본 로그인 로직 (SharedPreferences 등)
        return username == "admin" && password == "password"
    }
}

// Application.onCreate()
FeatureRegistry.getInstance().registerAuth("default", DefaultAuthStrategy(context))
```

### 3. **플러그인이 새로운 구현 제공**
```kotlin
// plugin 모듈
class EnhancedAuthStrategy : AuthenticationStrategy {
    override fun login(username: String, password: String): Boolean {
        // 향상된 로그인 (바이오메트릭, OAuth, 2FA)
        return performBiometricAuth() && username.isNotEmpty()
    }
}

// 플러그인 로드 시
FeatureRegistry.getInstance().registerAuth("enhanced", EnhancedAuthStrategy(context))
FeatureRegistry.getInstance().setActiveAuth("enhanced")  // override!
```

### 4. **앱의 비즈니스 로직은 변경 없음**
```kotlin
// 어디서든 사용
fun loginUser(username: String, password: String) {
    val auth = FeatureRegistry.getInstance().getAuth()  // 현재 활성 구현 가져오기

    if (auth.login(username, password)) {
        // 성공 처리
    }
}
```

---

### 타임라인 예제:

```
1. 앱 시작 (플러그인 없음)
   - FeatureRegistry.getAuth() → DefaultAuthStrategy 반환
   - 기본 로그인 동작

2. 사용자가 "플러그인 로드" 버튼 클릭
   - 플러그인 APK 로드
   - EnhancedAuthStrategy 등록
   - setActiveAuth("enhanced") 호출

3. 이후 로그인 시도
   - FeatureRegistry.getAuth() → EnhancedAuthStrategy 반환
   - 향상된 로그인 동작 (바이오메트릭 등)

4. 사용자가 설정에서 "기본 인증으로 전환" 선택
   - setActiveAuth("default") 호출
   - 다시 DefaultAuthStrategy 사용
```

---

### 실전 시나리오:

#### **시나리오 A: 기능 단위 분리 + 사전 로드**
```kotlin
// 앱 시작 시 모든 기능 플러그인 로드
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // 기본 기능 등록
        registerDefaultFeatures()

        // 플러그인 디렉토리 스캔
        val plugins = scanPluginDirectory()
        plugins.forEach { loadPlugin(it) }

        // 사용자 설정에 따라 활성 구현 선택
        loadUserPreferences()
    }
}
```

#### **시나리오 B: 지연 로드 (첫 사용 시)**
```kotlin
// 기능 첫 사용 시 로드
fun getAuthStrategy(): AuthenticationStrategy {
    if (!FeatureRegistry.hasAuth()) {
        // 아직 로드 안 됨 - 플러그인 확인
        val pluginPath = findAuthPlugin()
        if (pluginPath != null) {
            loadAuthPlugin(pluginPath)
        } else {
            // 플러그인 없음 - 기본 구현 사용
            FeatureRegistry.registerAuth("default", DefaultAuthStrategy())
        }
    }

    return FeatureRegistry.getAuth()
}
```

#### **시나리오 C: A/B 테스트**
```kotlin
// 랜덤으로 구현 선택
fun setupABTest() {
    val useEnhanced = Random.nextBoolean()

    if (useEnhanced) {
        FeatureRegistry.setActiveAuth("enhanced")
        Log.d("ABTest", "User assigned to: Enhanced Auth")
    } else {
        FeatureRegistry.setActiveAuth("default")
        Log.d("ABTest", "User assigned to: Default Auth")
    }
}
```

---

### Override vs 추가 기능

| 구분 | Override (교체) | 추가 기능 |
|------|----------------|-----------|
| **개념** | 기존 기능을 새 구현으로 대체 | 새 기능을 추가만 |
| **예** | 로그인 방식 변경 | 새로운 화면 추가 |
| **구현** | Strategy 패턴 + Registry | Plugin UI 추가 |
| **앱 영향** | 기존 코드 동작 변경 | 기존 코드 무관 |

**현재 프로토타입:**
- ✅ 추가 기능 구현됨 (plugin UI 추가)
- ❌ Override 미구현

**Override 추가하려면:**
```kotlin
// 1. shared/PluginInterface.kt에 추가
interface PluginInterface {
    // 기존
    fun createView(context: Context): View

    // 추가
    fun registerFeatures(registry: FeatureRegistry)  // 플러그인이 제공하는 기능 등록
}

// 2. plugin/PluginImpl.kt
override fun registerFeatures(registry: FeatureRegistry) {
    registry.registerAuth("plugin", EnhancedAuthStrategy())
    registry.registerStorage("plugin", CloudStorageStrategy())

    // 자동으로 활성화하려면
    registry.setActiveAuth("plugin")
    registry.setActiveStorage("plugin")
}

// 3. app/MainActivity.kt
result.onSuccess { plugin ->
    plugin.registerFeatures(FeatureRegistry.getInstance())
    // 이제 앱의 모든 인증은 플러그인 구현 사용
}
```

**예제 위치:** `examples/StrategyPatternExample.kt`

---

## 요약

### 질문 1: R.id.xxx 접근
- ✅ 가능함 (하드코딩 필요 없음)
- 추천: **getIdentifier()** (간단) 또는 **리소스 맵 전달** (안전)
- 중복 제거: **shared 모듈** 사용

### 질문 2: 기능 Override
- ✅ Strategy 패턴으로 런타임 교체 가능
- 레지스트리에 여러 구현 등록 → 사용 시점에 선택
- 현재 프로토타입은 "추가" 방식, "Override" 추가 가능

### 모든 예제 파일:
1. `RESOURCE_SHARING.md` - 리소스 접근 방법 설명
2. `examples/ResourceAccessExample.kt` - 실제 코드
3. `examples/StrategyPatternExample.kt` - Override 패턴
4. `examples/SharedResourceExample.md` - 공유 모듈
