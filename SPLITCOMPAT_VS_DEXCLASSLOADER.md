# SplitCompat vs DexClassLoader - 진실

## 핵심 발견: SplitCompat도 수동 DEX 로딩의 래퍼!

### 제가 착각한 부분

**제 설명:**
```
Split APK = PackageInstaller로 시스템 설치 + 재시작 필요
```

**실제 진실:**
```
SplitCompat = ClassLoader + Resources 조작
            = DexClassLoader처럼 동작하지만 더 정교함
            = 앱 재시작 불필요 (Android O+)
```

---

## SplitCompat의 실제 동작 방식

### 공식 문서에서 확인된 내용:

> **SplitCompat emulates installation of split APKs** to allow immediate access to their code and resources.

**"emulates" = 설치를 흉내낸다!**

### 실제 구현 메커니즘

```kotlin
// SplitCompat.install() 내부 동작 (추론)

fun install(context: Context) {
    // 1. /data/app/에 있는 split APK 파일 찾기
    val splitApks = findInstalledSplitApks(context.packageName)

    // 2. 각 split APK의 DEX와 리소스 로드
    splitApks.forEach { apkPath ->
        // ClassLoader 조작 (DexClassLoader와 유사)
        addDexToClassLoader(context.classLoader, apkPath)

        // Resources 조작 (AssetManager.addAssetPath()와 유사)
        addResourcesToAssetManager(context.resources, apkPath)
    }

    // 3. 현재 Context에 병합
    // ContextWrapper.attachBaseContext()에서 호출되므로
    // 앱/액티비티 초기화 전에 적용됨
}
```

**핵심:**
- ✅ ClassLoader 조작 (DEX 로드)
- ✅ AssetManager.addAssetPath() (리소스 로드)
- ✅ 재시작 불필요
- ⚠️ 단, Split APK가 **이미 설치되어 있어야 함**

---

## 사용자 프로젝트 vs SplitCompat vs GloballyDynamic

### 1. 현재 프로젝트 (수동 DexClassLoader)

```kotlin
// 1. APK 파일 준비
val apkFile = File(filesDir, "plugin.apk")
assets.open("plugin.apk").copyTo(apkFile.outputStream())
apkFile.setReadOnly()  // Android 14+

// 2. ClassLoader 생성
val dexClassLoader = DexClassLoader(
    apkFile.path,
    optimizedDir,
    null,
    context.classLoader
)

// 3. Resources 수동 생성
val assetManager = AssetManager::class.java.newInstance()
assetManager.addAssetPath(apkFile.path)  // reflection
val pluginResources = Resources(assetManager, ...)

// 4. 사용
val clazz = dexClassLoader.loadClass("com.example.Plugin")
val drawable = pluginResources.getDrawable(R.drawable.icon, null)
```

**특징:**
- ❌ 시스템 설치 없음 (filesDir에만 존재)
- ✅ 재시작 불필요
- ⚠️ 수동 작업 많음
- ✅ 완전한 자유도

---

### 2. SplitCompat (Play Core)

```kotlin
// Application.attachBaseContext()
override fun attachBaseContext(base: Context) {
    super.attachBaseContext(base)
    SplitCompat.install(this)  // ← 마법!
}

// Activity.attachBaseContext()
override fun attachBaseContext(base: Context) {
    super.attachBaseContext(base)
    SplitCompat.installActivity(this)  // ← 더 빠른 마법!
}

// 사용 - 아무 추가 작업 없이 바로 사용
val clazz = Class.forName("com.example.camera.CameraFeature")
val drawable = resources.getDrawable(R.drawable.camera_icon, null)
```

**내부 동작 (추론):**
```kotlin
// SplitCompat.install() 내부 (closed-source)
private fun install(context: Context) {
    val packageInfo = context.packageManager.getPackageInfo(...)
    val splitApkPaths = packageInfo.splitNames.map { splitName ->
        // /data/app/com.example.app/split_$splitName.apk
        getSplitApkPath(context.packageName, splitName)
    }

    // ClassLoader 조작 (reflection으로 parent 변경)
    val currentClassLoader = context.classLoader
    splitApkPaths.forEach { apkPath ->
        val splitClassLoader = PathClassLoader(apkPath, currentClassLoader)
        // setParent() 또는 내부 dexElements 배열 조작
    }

    // Resources 조작 (reflection으로 AssetManager 수정)
    val assetManager = context.resources.assets
    splitApkPaths.forEach { apkPath ->
        // AssetManager.addAssetPath(apkPath) - hidden API
        val addAssetPath = AssetManager::class.java
            .getDeclaredMethod("addAssetPath", String::class.java)
        addAssetPath.invoke(assetManager, apkPath)
    }
}
```

**특징:**
- ✅ 시스템 설치됨 (/data/app/)
- ✅ 재시작 불필요 (Android O+)
- ✅ 자동화됨 (ClassLoader + Resources)
- ❌ Play Store 필수

---

### 3. GloballyDynamic

```kotlin
// 1. Split APK 다운로드 + 설치
val request = SplitInstallRequest.newBuilder()
    .addModule("camera")
    .build()

globallyDynamic.splitInstallManager.startInstall(request)
    .addOnSuccessListener {
        // 설치 완료 - PackageManager에 등록됨
    }

// 2. SplitCompat으로 로드 (동일!)
override fun attachBaseContext(base: Context) {
    super.attachBaseContext(base)
    SplitCompat.install(this)
}
```

**프로세스:**
```
1. HTTP로 split APK 다운로드
   ↓
2. PackageInstaller로 시스템 설치
   (/data/app/com.example.app/split_camera.apk)
   ↓
3. ⚠️ 사용자 승인 필요 (설치 권한)
   ↓
4. ⚠️ 앱 재시작 가능성 (Android 버전별)
   ↓
5. SplitCompat.install()로 로드
```

**특징:**
- ✅ 시스템 설치됨
- ⚠️ 재시작 필요 (Android O 이하)
- ✅ SplitCompat과 동일한 메커니즘
- ⚠️ 사용자 승인 필요 (설치 팝업)
- ✅ 자체 서버 가능

---

## 핵심 비교표

| | DexClassLoader (수동) | SplitCompat | GloballyDynamic |
|---|---|---|---|
| **ClassLoader 조작** | ✅ 직접 | ✅ 자동 | ✅ 자동 |
| **Resources 조작** | ✅ 수동 | ✅ 자동 | ✅ 자동 |
| **시스템 설치** | ❌ | ✅ | ✅ |
| **재시작 불필요** | ✅ | ✅ (Android O+) | ⚠️ (버전별) |
| **사용자 승인** | ❌ | ❌ | ⚠️ (설치 권한) |
| **APK 위치** | filesDir | /data/app/ | /data/app/ |
| **Play Store** | ❌ | ✅ | ❌ |

---

## 사용자의 직관이 맞는 부분

### "수동 DEX 로딩을 편하게 wrapping"

**정확히 맞습니다!**

**현재 프로젝트에서 하는 것:**
```kotlin
// 1. DexClassLoader
val loader = DexClassLoader(apkPath, optimizedDir, null, parent)

// 2. AssetManager (reflection)
val assetManager = AssetManager::class.java.newInstance()
assetManager.addAssetPath(apkPath)

// 3. Resources
val resources = Resources(assetManager, displayMetrics, config)
```

**SplitCompat이 하는 것 (추론):**
```kotlin
// 내부적으로 동일한 작업을 자동화
fun install(context: Context) {
    splitApks.forEach { apkPath ->
        // DexClassLoader 또는 PathClassLoader 생성
        addToClassLoader(apkPath)

        // AssetManager.addAssetPath() (reflection)
        addToResources(apkPath)
    }
}
```

**차이점:**
1. **APK 위치:**
   - 수동: filesDir (앱 전용)
   - SplitCompat: /data/app/ (시스템 설치)

2. **호출 시점:**
   - 수동: 언제든 (버튼 클릭 등)
   - SplitCompat: attachBaseContext() (앱/액티비티 초기화 전)

3. **자동화:**
   - 수동: 모든 단계 직접 작성
   - SplitCompat: install() 한 줄

---

## 왜 GloballyDynamic은 재시작이 필요한가?

### 문제: PackageInstaller 사용

```kotlin
// GloballyDynamic의 설치 과정
fun installSplitApk(apkBytes: ByteArray) {
    val packageInstaller = context.packageManager.packageInstaller
    val sessionParams = PackageInstaller.SessionParams(
        PackageInstaller.SessionParams.MODE_INHERIT_EXISTING
    )

    val sessionId = packageInstaller.createSession(sessionParams)
    val session = packageInstaller.openSession(sessionId)

    // APK 쓰기
    session.openWrite("split_camera", 0, -1).use { output ->
        output.write(apkBytes)
    }

    // 설치 커밋 - 여기서 시스템이 개입!
    session.commit(pendingIntent)

    // ⚠️ 시스템이 패키지를 업데이트 중...
    // ⚠️ 앱 재시작 필요 (Android O 이하)
}
```

**재시작 이유:**
- PackageManager가 패키지 정보 업데이트
- ClassLoader 캐시 무효화
- Android O 이하: 완전 재시작 필요

**Android O+ 개선:**
```kotlin
// Android O+에서는 isolated split 지원
// 설치 완료 즉시 SplitCompat.install() 가능
// 재시작 불필요
```

---

## 결론: 사용자 이해가 정확합니다!

### 정리

**1. SplitCompat ≈ DexClassLoader + AssetManager 자동화**
```
SplitCompat.install() =
    DexClassLoader (자동) +
    AssetManager.addAssetPath() (자동) +
    ClassLoader 조작 (자동) +
    Resources 병합 (자동)
```

**2. GloballyDynamic = PackageInstaller + SplitCompat**
```
GloballyDynamic =
    HTTP 다운로드 +
    PackageInstaller (시스템 설치) +
    SplitCompat.install()
```

**3. 현재 프로젝트 = 순수 수동 구현**
```
현재 프로젝트 =
    Assets에서 복사 +
    DexClassLoader (수동) +
    AssetManager (수동, reflection) +
    Resources (수동)
```

---

## 실전 코드 비교

### 현재 프로젝트 방식
```kotlin
// PluginLoader.kt:101-129
private fun createPluginResources(apkPath: String) {
    // AssetManager 생성 (reflection)
    val assetManager = AssetManager::class.java.newInstance()
    val addAssetPathMethod = AssetManager::class.java
        .getDeclaredMethod("addAssetPath", String::class.java)
    addAssetPathMethod.invoke(assetManager, apkPath)

    // Resources 생성
    pluginResources = Resources(
        assetManager,
        hostResources.displayMetrics,
        hostResources.configuration
    )
}
```

### SplitCompat 방식 (추론)
```kotlin
// SplitCompat 내부 (closed-source, 추론)
private fun addSplitResources(context: Context, apkPath: String) {
    // 기존 AssetManager에 추가
    val assetManager = context.resources.assets
    val addAssetPath = AssetManager::class.java
        .getDeclaredMethod("addAssetPath", String::class.java)
    addAssetPath.invoke(assetManager, apkPath)

    // ⚠️ 기존 Resources 객체를 재사용!
    // 새 Resources 객체 생성하지 않음
}
```

**차이점:**
- 현재 프로젝트: 새 Resources 객체 생성
- SplitCompat: 기존 Resources에 병합

---

## 참고 자료

### SplitCompat 동작 방식
- [SplitCompat | Android Developers](https://developer.android.com/reference/com/google/android/play/core/splitcompat/SplitCompat)
- [Chromium Docs: Isolated Splits](https://chromium.googlesource.com/chromium/src.git/+/HEAD/docs/android_isolated_splits.md)
- [How Android Handles Feature Modules](https://medium.com/@shashankkumar45556/how-android-handles-the-installation-of-feature-modules-b4544b4b4a97)

### GloballyDynamic 구현
- [GitHub: GloballyDynamic](https://github.com/jeppeman/GloballyDynamic)
- [PackageInstaller | Android Developers](https://developer.android.com/reference/android/content/pm/PackageInstaller)
- [In Depth: Android Package Manager](https://dzone.com/articles/depth-android-package-manager)

### DexClassLoader
- [3 ways for Dynamic Code Loading](https://erev0s.com/blog/3-ways-for-dynamic-code-loading-in-android/)
