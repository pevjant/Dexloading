# SplitCompat 사용 시 리소스 접근

## 질문: SplitCompat 쓰면 shared 모듈 없이 리소스 접근 자유로워지나?

**답: 네, 맞습니다! 100% 자유롭게 접근 가능합니다!**

---

## 현재 프로젝트 방식의 제약

### 문제: 분리된 Resources 객체

```kotlin
// Host App
val hostResources = context.resources  // 0x7f 패키지 ID 사용
val hostColor = hostResources.getColor(R.color.sample_red, null)

// Plugin (별도 Resources 객체)
val pluginResources = Resources(pluginAssetManager, ...)  // 0x71 패키지 ID
val pluginColor = pluginResources.getColor(R.color.plugin_coral, null)

// ❌ 서로 다른 Resources 객체
// ❌ Host가 Plugin 리소스 접근 불가
// ❌ Plugin이 Host 리소스 접근 불가
```

**구조:**
```
┌─────────────────┐
│   Host Context  │
│  Resources(0x7f)│ ← Host 전용
└─────────────────┘

┌─────────────────┐
│  Plugin Context │
│ Resources(0x71) │ ← Plugin 전용
└─────────────────┘

❌ 분리됨
❌ 상호 접근 불가
```

---

## SplitCompat 방식

### 해결: 하나의 통합된 Resources

```kotlin
// Application.attachBaseContext()
override fun attachBaseContext(base: Context) {
    super.attachBaseContext(base)
    SplitCompat.install(this)  // ← 마법!
}

// 이후 어디서든
val resources = context.resources  // ← 하나의 Resources!

// Host 리소스
val hostColor = resources.getColor(R.color.sample_red, null)  // 0x7f...

// Plugin 리소스 (동일한 resources 객체!)
val pluginColor = resources.getIdentifier("plugin_coral", "color", packageName)
val color = resources.getColor(pluginColor, null)  // 0x71...

// ✅ 같은 Resources 객체
// ✅ 모든 리소스 접근 가능!
```

**구조:**
```
┌──────────────────────────┐
│   App Context            │
│  Resources (통합)         │
│  ├── 0x7f (base)         │ ← Host 리소스
│  ├── 0x71 (split_plugin) │ ← Plugin 리소스
│  └── 0x72 (split_feature)│ ← 추가 feature
└──────────────────────────┘

✅ 통합됨
✅ 모든 모듈이 같은 Resources 사용
✅ 자유로운 상호 접근
```

---

## 코드 비교

### 현재 방식 (수동 DexClassLoader)

```kotlin
// PluginImpl.kt
class PluginImpl : PluginInterface {
    private var pluginResources: Resources? = null  // ← 별도 객체!

    override fun initialize(
        hostContext: Context,
        pluginResources: Resources,  // ← 따로 전달받아야 함
        pluginAssets: AssetManager
    ) {
        this.pluginResources = pluginResources
    }

    override fun createView(hostContext: Context): View {
        // ❌ Plugin 리소스 접근: 별도 Resources 객체 사용
        val pluginColor = pluginResources!!.getColor(R.color.plugin_coral, null)

        // ❌ Host 리소스 접근: getIdentifier 또는 리소스 맵 필요
        val hostColorId = hostContext.resources.getIdentifier(
            "sample_red", "color", hostContext.packageName
        )
        val hostColor = hostContext.resources.getColor(hostColorId, null)
    }
}
```

### SplitCompat 방식

```kotlin
// PluginImpl.kt
class PluginImpl : PluginInterface {
    // ✅ Resources 객체 따로 저장 불필요!

    override fun initialize(hostContext: Context) {
        // 설정 없음 - 이미 통합됨
    }

    override fun createView(hostContext: Context): View {
        val resources = hostContext.resources  // ← 하나의 통합된 Resources!

        // ✅ Plugin 리소스 접근: 그냥 사용
        val pluginColor = resources.getColor(
            com.example.plugin.R.color.plugin_coral,  // ← 직접 참조!
            null
        )

        // ✅ Host 리소스 접근: 그냥 사용
        val hostColor = resources.getColor(
            com.example.dexloadingtest.R.color.sample_red,  // ← 직접 참조!
            null
        )

        // ✅ 또는 이름으로 조회 (더 간단)
        val colorId = resources.getIdentifier("sample_red", "color", packageName)
        val color = resources.getColor(colorId, null)
    }
}
```

---

## 실전 예제

### 시나리오: Plugin이 Host의 로고 사용

#### 현재 방식 (복잡)

```kotlin
// 1. shared 모듈에 공통 리소스 배치
shared/res/drawable/logo.xml

// 2. 양쪽에서 import
import com.example.shared.R as SharedR
val logo = resources.getDrawable(SharedR.drawable.logo, null)

// 또는 getIdentifier
val logoId = hostResources.getIdentifier("logo", "drawable", "com.example.dexloadingtest")
val logo = hostResources.getDrawable(logoId, null)

// 또는 리소스 맵 전달
val hostResourceMap = mapOf("logo" to R.drawable.logo)
plugin.initialize(context, pluginRes, pluginAssets, hostResourceMap)
```

#### SplitCompat 방식 (간단)

```kotlin
// Plugin에서 바로 사용
val resources = context.resources

// Host의 리소스 직접 접근
val logo = resources.getDrawable(
    resources.getIdentifier("logo", "drawable", packageName),
    null
)

// 또는 R 클래스 직접 참조 (plugin build.gradle에 host 의존성 추가 시)
import com.example.dexloadingtest.R as HostR
val logo = resources.getDrawable(HostR.drawable.logo, null)
```

---

## 패키지 ID 충돌 문제 해결

### 현재 방식: 패키지 ID 분리 필요

```gradle
// plugin/build.gradle.kts
androidResources {
    additionalParameters += listOf("--package-id", "0x71", "--allow-reserved-package-id")
}
```

**이유:**
- 별도 Resources 객체 사용
- 충돌 방지 필요

### SplitCompat 방식: 충돌 걱정 없음

```gradle
// plugin/build.gradle.kts
// ❌ --package-id 설정 불필요!
// ✅ 자동으로 고유 ID 할당됨
```

**이유:**
- Split APK는 각자 고유 ID 자동 할당
- SplitCompat이 통합 관리
- 시스템이 충돌 방지

---

## 장단점 비교

### 현재 방식 (수동 DexClassLoader)

**장점:**
- ✅ 완전한 격리 (보안)
- ✅ 언제든 로드/언로드
- ✅ 재시작 불필요
- ✅ 시스템 설치 불필요

**단점:**
- ❌ 리소스 접근 복잡
- ❌ shared 모듈 필요 (공통 리소스)
- ❌ getIdentifier() 또는 리소스 맵 필요
- ❌ 수동 작업 많음

### SplitCompat 방식

**장점:**
- ✅ 리소스 접근 완전 자유
- ✅ shared 모듈 불필요
- ✅ R 클래스 직접 참조 가능
- ✅ 자동화

**단점:**
- ❌ 시스템 설치 필요 (/data/app/)
- ❌ 격리 불가능 (모든 리소스 공유)
- ❌ 재시작 필요 (Android O 이하)
- ❌ Play Store 또는 PackageInstaller 필요

---

## 실제 적용 방법

### 프로젝트를 SplitCompat 방식으로 변경하려면?

#### 1. 모듈 구조 변경

```gradle
// settings.gradle.kts
include(":app")
include(":dynamicfeature")  // plugin → dynamicfeature로 변경
```

#### 2. Dynamic Feature Module로 변경

```gradle
// dynamicfeature/build.gradle.kts
plugins {
    id("com.android.dynamic-feature")  // ← 변경!
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.dynamicfeature"

    // ❌ package-id 설정 불필요
}

dependencies {
    implementation(project(":app"))  // ← base 모듈 의존
}
```

#### 3. Application에 SplitCompat 추가

```kotlin
// app/src/main/java/.../MyApplication.kt
class MyApplication : Application() {
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        SplitCompat.install(this)  // ← 추가!
    }
}
```

#### 4. Dynamic Feature 로드

```kotlin
// MainActivity.kt
private fun loadDynamicFeature() {
    val manager = SplitInstallManagerFactory.create(this)

    val request = SplitInstallRequest.newBuilder()
        .addModule("dynamicfeature")
        .build()

    manager.startInstall(request)
        .addOnSuccessListener {
            // ✅ 설치 완료 - 즉시 사용 가능 (Android O+)
            val resources = resources  // ← 통합된 Resources!

            // Host 리소스
            val hostColor = resources.getColor(R.color.sample_red, null)

            // Feature 리소스
            val featureColorId = resources.getIdentifier(
                "feature_color", "color", packageName
            )
            val featureColor = resources.getColor(featureColorId, null)

            // ✅ 모두 같은 resources 객체에서 접근!
        }
}
```

---

## 하이브리드 방식도 가능

### 현재 프로젝트에 SplitCompat 개념 적용

```kotlin
// PluginLoader.kt 개선
class PluginLoader(private val context: Context) {

    fun loadPluginFromAssets(): Result<PluginInterface> {
        // 1. APK 복사
        val apkFile = copyApkFromAssets()

        // 2. Resources 통합 (SplitCompat 방식)
        integrateResources(apkFile.path)

        // 3. DexClassLoader
        val loader = DexClassLoader(...)

        return Result.success(plugin)
    }

    private fun integrateResources(apkPath: String) {
        // Host의 AssetManager에 Plugin APK 추가
        val assetManager = context.resources.assets
        val addAssetPath = AssetManager::class.java
            .getDeclaredMethod("addAssetPath", String::class.java)
        addAssetPath.invoke(assetManager, apkPath)

        // ✅ 이제 context.resources로 모든 리소스 접근 가능!
    }
}
```

**효과:**
```kotlin
// Plugin에서
val resources = hostContext.resources  // ← 통합된 Resources!

// Host 리소스 접근
val hostColor = resources.getIdentifier("sample_red", "color", packageName)
val color = resources.getColor(hostColor, null)

// Plugin 리소스 접근
val pluginColor = resources.getColor(R.color.plugin_coral, null)

// ✅ shared 모듈 불필요!
// ✅ 리소스 맵 불필요!
// ✅ 하나의 Resources 객체로 모든 접근!
```

---

## 결론

### 질문에 대한 답변

**"SplitCompat 쓰면 shared 모듈 없이 리소스 접근 자유로워지나?"**

**✅ 네, 완전히 자유롭습니다!**

**이유:**
1. SplitCompat.install()이 모든 split APK를 기존 Resources에 병합
2. 하나의 통합된 Resources 객체 사용
3. Host와 Plugin이 같은 Resources 인스턴스 공유
4. 패키지 ID 충돌 자동 관리
5. getIdentifier() 또는 직접 R 클래스 참조로 접근

**하지만:**
- 시스템 설치 필요 (/data/app/)
- Play Store 또는 PackageInstaller 필요
- 재시작 필요할 수 있음 (Android O 이하)

**현재 프로젝트:**
- filesDir 기반 (시스템 설치 없음)
- 재시작 완전 불필요
- 하지만 리소스 접근은 복잡함

**선택:**
- **학습/프로토타입**: 현재 방식 (유연성)
- **프로덕션**: SplitCompat (편의성)
- **하이브리드**: 현재 방식 + Resources 통합 코드
