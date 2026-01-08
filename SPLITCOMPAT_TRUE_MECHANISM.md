# SplitCompat의 진짜 동작 메커니즘

## 중대한 발견: 제가 틀렸습니다!

### 제가 잘못 설명한 것

**❌ 틀린 설명:**
```
GloballyDynamic/SplitCompat은:
- PackageInstaller 사용
- 시스템에 설치 (/data/app/)
- setDontKillApp(true)로 재시작 방지
- Android 8.0+부터 재시작 없음
```

**✅ 사용자의 정확한 지적:**
```
GloballyDynamic/SplitCompat은:
- PackageInstaller 사용 안 함!
- 앱 내부 저장소에 저장 (Internal Storage)
- BaseDexClassLoader의 pathList에 Reflection으로 주입
- setDontKillApp() 필요 없음!
- OS 입장에서는 "앱이 자기 파일을 읽은 것"
```

---

## 두 가지 다른 방식

### A. PackageInstaller 방식 (시스템 설치)

```kotlin
// 시스템의 공식 설치 경로 사용
val sessionParams = PackageInstaller.SessionParams(
    MODE_INHERIT_EXISTING
)
sessionParams.setDontKillApp(true)  // ← Android 14 (API 34)부터!

// 설치 경로: /data/app/com.example.app/split_*.apk
// OS 인식: ✅ 시스템이 관리
// 재시작: setDontKillApp(true) 필요
```

**특징:**
- ✅ OS가 공식적으로 인식
- ✅ PackageManager에 등록
- ⚠️ setDontKillApp(true) 필요 (Android 14+)
- ❌ Android 14 미만: 재시작 필요
- ⚠️ 사용자 설치 권한 필요

---

### B. SplitCompat 방식 (내부 저장소 + Reflection)

```kotlin
// 앱 내부 저장소에 저장
val splitApkPath = File(context.filesDir, "split_feature.apk")
downloadAndSave(splitApkPath)  // HTTP 다운로드

// SplitCompat이 런타임에 ClassLoader 조작
SplitCompat.install(context)  // ← 마법!

// 저장 경로: /data/data/com.example.app/files/split_*.apk
// OS 인식: ❌ 시스템 모름 (앱 내부 파일)
// 재시작: 불필요! (모든 Android 버전)
```

**특징:**
- ✅ 재시작 완전 불필요 (모든 버전)
- ✅ setDontKillApp() 불필요
- ✅ 사용자 권한 불필요
- ❌ OS가 인식 못 함 (앱 데이터)
- ✅ Reflection으로 ClassLoader 조작

**출처:** [SplitCompat API Reference](https://developer.android.com/reference/com/google/android/play/core/splitcompat/SplitCompat)

---

## SplitCompat의 실제 동작 (Emulation)

### 공식 문서에서 확인된 내용

> "SplitCompat **emulates installation** of split APKs to allow immediate access to their code and resources."

> "Modules are considered installed **even when they are emulated by SplitCompat** because they are accessible to the app."

**핵심: "emulates" = 실제 설치가 아님!**

**출처:** [SplitCompat Installation Issue](https://github.com/android/app-bundle-samples/issues/56)

---

## 내부 구현 추정

### 1. Split APK 다운로드 및 저장

```kotlin
// GloballyDynamic/Play Core 내부 (추정)
fun downloadAndSaveSplit(splitName: String, url: String): File {
    // 앱 내부 저장소에 저장
    val splitFile = File(context.filesDir, "splits/$splitName.apk")

    // HTTP 다운로드
    downloadFromServer(url, splitFile)

    // Android 14+ 보안: setReadOnly
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        splitFile.setReadOnly()
    }

    return splitFile
    // ❌ PackageInstaller 호출 안 함!
}
```

**저장 경로:**
```
/data/data/com.example.app/
├── files/
│   └── splits/
│       ├── feature_camera.apk    ← 앱 내부 파일!
│       ├── feature_ar.apk
│       └── config_xxhdpi.apk
```

---

### 2. ClassLoader에 런타임 주입 (Reflection)

```kotlin
// SplitCompat.install() 내부 (추정)
fun install(context: Context) {
    // 1. 저장된 split APK 파일 찾기
    val splitFiles = File(context.filesDir, "splits").listFiles()

    splitFiles?.forEach { apkFile ->
        // 2. AssetManager에 추가 (리소스 접근)
        injectResources(context, apkFile.path)

        // 3. ClassLoader에 주입 (코드 접근)
        injectClassLoader(context, apkFile.path)
    }
}

@Suppress("DiscouragedPrivateApi")
private fun injectClassLoader(context: Context, apkPath: String) {
    try {
        // BaseDexClassLoader 가져오기
        val classLoader = context.classLoader as BaseDexClassLoader

        // pathList 필드 접근 (Reflection)
        val pathListField = BaseDexClassLoader::class.java
            .getDeclaredField("pathList")
        pathListField.isAccessible = true
        val pathList = pathListField.get(classLoader)

        // dexElements 필드 접근
        val dexElementsField = pathList.javaClass
            .getDeclaredField("dexElements")
        dexElementsField.isAccessible = true
        val dexElements = dexElementsField.get(pathList) as Array<*>

        // 새로운 DexPathList.Element 생성
        val newElement = createDexElement(apkPath)

        // dexElements 배열에 추가
        val newDexElements = Array(dexElements.size + 1) { i ->
            if (i < dexElements.size) dexElements[i] else newElement
        }

        dexElementsField.set(pathList, newDexElements)

        Log.d("SplitCompat", "✅ Injected split APK into ClassLoader: $apkPath")

        // ✅ 이제 해당 APK의 클래스 로드 가능!

    } catch (e: Exception) {
        Log.e("SplitCompat", "Failed to inject ClassLoader", e)
    }
}

private fun injectResources(context: Context, apkPath: String) {
    // AssetManager에 추가 (우리가 이미 알고 있는 방식)
    val assetManager = context.resources.assets
    val addAssetPath = AssetManager::class.java
        .getDeclaredMethod("addAssetPath", String::class.java)
    addAssetPath.invoke(assetManager, apkPath)

    // ✅ 이제 해당 APK의 리소스 접근 가능!
}
```

---

## 왜 OS가 재시작하지 않는가?

### 시스템 관점

```
PackageInstaller 방식 (A):
1. PackageInstaller.commit() 호출
   ↓
2. PackageManagerService가 인식
   ↓
3. "새 패키지 설치됨" 이벤트
   ↓
4. 기본 동작: 앱 프로세스 종료
   ↓
5. setDontKillApp(true) 있으면 예외 처리 (Android 14+)

SplitCompat 방식 (B):
1. 파일 다운로드 → filesDir 저장
   ↓
2. Reflection으로 ClassLoader 조작
   ↓
3. OS 입장: "앱이 자기 파일을 읽음"
   ↓
4. 아무 이벤트 없음!
   ↓
5. 재시작 없음 (모든 Android 버전)
```

---

## setDontKillApp() 지원 버전

### 중대한 정정

**❌ 제가 틀린 정보:**
- Android 8.0 (API 26)부터 지원

**✅ 실제:**
- **Android 14 (API 34)부터 지원!**

**출처:** [PackageInstaller.SessionParams.SetDontKillApp](https://learn.microsoft.com/en-us/dotnet/api/android.content.pm.packageinstaller.sessionparams.setdontkillapp?view=net-android-34.0)

```kotlin
// ❌ Android 13 이하: 컴파일 에러!
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {  // API 33
    sessionParams.setDontKillApp(true)  // ← 메서드 없음!
}

// ✅ Android 14+만 가능
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {  // API 34
    sessionParams.setDontKillApp(true)  // ← OK!
}
```

---

## GloballyDynamic의 실제 동작

### 추정: 두 가지 모드 지원

```kotlin
// GloballyDynamic 설정 (추정)
class GloballyDynamicConfig {
    enum class InstallMode {
        SPLIT_COMPAT,      // 내부 저장소 + Reflection (기본)
        PACKAGE_INSTALLER  // 시스템 설치 (옵션)
    }

    var installMode: InstallMode = InstallMode.SPLIT_COMPAT
}

// 실제 사용
fun installSplit(splitName: String) {
    when (config.installMode) {
        SPLIT_COMPAT -> {
            // 방식 B: 내부 저장소 + SplitCompat
            // ✅ 재시작 없음 (모든 버전)
            // ❌ OS 인식 없음
        }
        PACKAGE_INSTALLER -> {
            // 방식 A: PackageInstaller
            // ⚠️ Android 14+에서만 재시작 없음
            // ✅ OS 인식
        }
    }
}
```

---

## 비교표 (정정)

| 항목 | DexClassLoader (현재) | SplitCompat (GloballyDynamic) | PackageInstaller |
|------|---------------------|---------------------------|-----------------|
| **저장 위치** | filesDir | filesDir | /data/app/ |
| **OS 인식** | ❌ | ❌ | ✅ |
| **ClassLoader 조작** | ✅ 직접 | ✅ Reflection | ❌ |
| **재시작 (모든 버전)** | ❌ 없음 | ❌ 없음 | ⚠️ 있음 |
| **재시작 (Android 14+)** | ❌ 없음 | ❌ 없음 | ✅ 없음 (setDontKillApp) |
| **사용자 권한** | ❌ | ❌ | ✅ 필요 |
| **리소스 통합** | ⚠️ 수동 | ✅ 자동 | ✅ 자동 |

---

## 현재 프로젝트는 SplitCompat과 동일!

### 비교

**현재 프로젝트:**
```kotlin
// 1. 내부 저장소에 저장
val apkFile = File(context.filesDir, "plugin.apk")
assets.open("plugin.apk").copyTo(apkFile.outputStream())

// 2. DexClassLoader
val loader = DexClassLoader(apkPath, optimizedDir, null, parent)

// 3. Resources 수동 생성
val assetManager = AssetManager::class.java.newInstance()
assetManager.addAssetPath(apkPath)
```

**SplitCompat:**
```kotlin
// 1. 내부 저장소에 저장
val splitFile = File(context.filesDir, "splits/feature.apk")
download(splitFile)

// 2. SplitCompat이 자동으로 ClassLoader 조작
SplitCompat.install(context)

// 3. Resources 자동 통합
```

**차이점:**
- 현재: 수동으로 DexClassLoader 생성
- SplitCompat: Reflection으로 기존 ClassLoader에 주입

**본질적으로 동일한 방식!**

---

## 결론

### 사용자의 지적이 100% 정확했습니다!

**핵심 정리:**

1. **SplitCompat은 PackageInstaller 사용 안 함**
   - 앱 내부 저장소에 저장
   - Reflection으로 ClassLoader 조작
   - OS는 인식 못 함

2. **setDontKillApp() 불필요**
   - SplitCompat 방식에서는 필요 없음
   - PackageInstaller 방식에서만 필요

3. **setDontKillApp()은 Android 14부터**
   - ❌ Android 8.0 (제가 틀렸음)
   - ✅ Android 14 (API 34)

4. **현재 프로젝트 = SplitCompat 방식**
   - 본질적으로 동일한 메커니즘
   - 차이: 수동 vs 자동화

5. **GloballyDynamic**
   - SplitCompat 방식 사용 (기본)
   - PackageInstaller는 옵션일 가능성

---

## 프로덕션 레벨 권장 (수정)

| 목표 | 추천 방식 | 재시작 | Android 요구 |
|------|----------|--------|-------------|
| **최대 호환성** | DexClassLoader / SplitCompat | ❌ | 5.0+ |
| **자동화 + 호환성** | GloballyDynamic (SplitCompat 모드) | ❌ | 5.0+ |
| **OS 관리 (최신)** | PackageInstaller + setDontKillApp | ❌ | 14+ |
| **OS 관리 (구버전)** | PackageInstaller | ⚠️ | 5.0+ |

---

## 참고 자료

### SplitCompat Emulation
- [SplitCompat API Reference](https://developer.android.com/reference/com/google/android/play/core/splitcompat/SplitCompat)
- [SplitCompat Installation Issue](https://github.com/android/app-bundle-samples/issues/56)
- [Dynamic Feature Module Integration](https://medium.com/swlh/dynamic-feature-module-integration-android-a315194a4801)

### setDontKillApp Version
- [PackageInstaller.SessionParams.SetDontKillApp](https://learn.microsoft.com/en-us/dotnet/api/android.content.pm.packageinstaller.sessionparams.setdontkillapp?view=net-android-34.0)
- [Android API Levels](https://apilevels.com/)

### BaseDexClassLoader & Reflection
- [Custom Class Loading in Dalvik](https://android-developers.googleblog.com/2011/07/custom-class-loading-in-dalvik.html)
- [BaseDexClassLoader API Reference](https://developer.android.com/reference/dalvik/system/BaseDexClassLoader)
