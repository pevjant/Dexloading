# PackageInstaller로 앱 재시작 없이 Split APK 설치하기

## 사용자 질문의 핵심

**"base APK 제외한 split APK만 설치되게 안 돼?"**

**답: 됩니다! 그것도 앱 재시작 없이!**

---

## 핵심 발견

### 1. MODE_INHERIT_EXISTING

```kotlin
val sessionParams = PackageInstaller.SessionParams(
    PackageInstaller.SessionParams.MODE_INHERIT_EXISTING  // ← 핵심!
)
```

**의미:**
> Mode for an install session that should **inherit any existing APKs** for the target app, unless they have been explicitly overridden (based on split name) by the session.

**해석:**
- ✅ 기존에 설치된 base APK는 그대로 유지
- ✅ 새로운 split APK만 추가
- ✅ base APK 재설치 불필요!

---

### 2. setDontKillApp(true) - 재시작 방지!

```kotlin
val sessionParams = PackageInstaller.SessionParams(
    PackageInstaller.SessionParams.MODE_INHERIT_EXISTING
)

sessionParams.setDontKillApp(true)  // ← 앱 재시작 방지!
```

**의미:**
> Requests that the system **not kill any of the package's running processes** as part of a MODE_INHERIT_EXISTING session in which splits being added.

**기본 동작:**
- ❌ 기본값: 설치 완료 시 앱 프로세스 종료 (재시작)
- ✅ setDontKillApp(true): 앱 프로세스 유지 (재시작 없음)

---

## AAB vs APK

### AAB (Android App Bundle)

**역할: 배포 포맷 (설치 불가)**

```
my-app.aab
├── base/
│   ├── dex/
│   ├── res/
│   └── manifest/
├── feature_camera/
│   ├── dex/
│   ├── res/
│   └── manifest/
└── feature_ar/
    ├── dex/
    ├── res/
    └── manifest/
```

**특징:**
- ❌ 디바이스에 직접 설치 불가
- ✅ Play Store/서버가 split APK로 변환
- ✅ 사용자는 필요한 것만 다운로드

**출처:** [About Android App Bundles](https://developer.android.com/guide/app-bundle)

---

### Split APK (실제 설치 포맷)

**역할: 설치 가능한 APK 파일들**

```
설치된 앱 구조:
/data/app/com.example.app/
├── base.apk              ← 필수 (최초 설치)
├── split_config.arm64_v8a.apk
├── split_config.en.apk
├── split_feature_camera.apk  ← 나중에 추가 가능!
└── split_feature_ar.apk      ← 나중에 추가 가능!
```

**특징:**
- ✅ 디바이스에 직접 설치 가능
- ✅ base.apk는 필수 (최초 설치 시)
- ✅ split APK는 나중에 추가 가능 (MODE_INHERIT_EXISTING)

**출처:** [How Android Handles Feature Modules](https://medium.com/@shashankkumar45556/how-android-handles-the-installation-of-feature-modules-b4544b4b4a97)

---

## 실제 구현 방법

### 방법 1: 재시작 없이 Split APK 설치

```kotlin
fun installSplitWithoutRestart(context: Context, splitApkPath: String) {
    val packageInstaller = context.packageManager.packageInstaller

    // 1. SessionParams 생성
    val sessionParams = PackageInstaller.SessionParams(
        PackageInstaller.SessionParams.MODE_INHERIT_EXISTING  // base 유지
    )

    // 2. 앱 재시작 방지 (핵심!)
    sessionParams.setDontKillApp(true)  // ← Android 8.0+

    // 3. Session 생성
    val sessionId = packageInstaller.createSession(sessionParams)
    val session = packageInstaller.openSession(sessionId)

    // 4. Split APK 쓰기
    val splitApkFile = File(splitApkPath)
    session.openWrite("split_feature", 0, -1).use { output ->
        splitApkFile.inputStream().use { input ->
            input.copyTo(output)
        }
    }

    // 5. 설치 커밋
    val intent = Intent(context, InstallResultReceiver::class.java)
    val pendingIntent = PendingIntent.getBroadcast(
        context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT
    )

    session.commit(pendingIntent.intentSender)
    session.close()

    // ✅ 앱 재시작 없이 split APK 설치 완료!
}
```

**결과:**
```
1. 앱 실행 중
   ↓
2. Split APK 다운로드
   ↓
3. PackageInstaller 설치
   - MODE_INHERIT_EXISTING (base 재설치 X)
   - setDontKillApp(true) (재시작 X)
   ↓
4. ✅ 앱 계속 실행 중 (재시작 없음!)
   ↓
5. SplitCompat.install() 호출
   ↓
6. ✅ 새 기능 즉시 사용 가능!
```

---

### 방법 2: 재시작 허용 (안전)

```kotlin
fun installSplitWithRestart(context: Context, splitApkPath: String) {
    val sessionParams = PackageInstaller.SessionParams(
        PackageInstaller.SessionParams.MODE_INHERIT_EXISTING
    )

    // setDontKillApp() 호출 안 함 - 재시작 허용

    // ... 동일한 설치 과정 ...

    // ⚠️ 설치 완료 후 앱 재시작됨
}
```

**결과:**
```
1. 앱 실행 중
   ↓
2. Split APK 설치
   ↓
3. ⚠️ 앱 프로세스 종료 (재시작)
   ↓
4. 앱 재시작
   ↓
5. SplitCompat.install() 자동 호출
   ↓
6. ✅ 새 기능 사용 가능
```

---

## GloballyDynamic의 실제 동작

### 추측: setDontKillApp() 사용 여부에 따라 다름

```kotlin
// GloballyDynamic 내부 (추정)
fun installSplit(apkBytes: ByteArray, dontKillApp: Boolean = false) {
    val sessionParams = PackageInstaller.SessionParams(
        MODE_INHERIT_EXISTING
    )

    if (dontKillApp) {
        sessionParams.setDontKillApp(true)  // ← 옵션에 따라
    }

    // ... 설치 진행 ...
}
```

**시나리오:**

| 설정 | base APK | 재시작 | Android 요구사항 |
|------|----------|--------|-----------------|
| MODE_INHERIT_EXISTING + setDontKillApp(true) | ✅ 유지 | ❌ 없음 | Android 8.0+ |
| MODE_INHERIT_EXISTING + setDontKillApp(false) | ✅ 유지 | ⚠️ 있음 | Android 5.0+ |
| MODE_FULL_INSTALL | ❌ 재설치 | ⚠️ 있음 | 모든 버전 |

---

## Android 버전별 차이

### Android 8.0 (API 26) 이상

```kotlin
// ✅ setDontKillApp() 지원
sessionParams.setDontKillApp(true)

// 결과:
// - base APK 유지
// - 앱 재시작 없음
// - split APK 즉시 사용 가능
```

### Android 7.x (API 24-25) 이하

```kotlin
// ❌ setDontKillApp() 없음

// 결과:
// - base APK 유지
// - 앱 재시작 필요
// - 재시작 후 split APK 사용 가능
```

**출처:** [PackageInstaller.SessionParams](https://learn.microsoft.com/en-us/dotnet/api/android.content.pm.packageinstaller.sessionparams?view=net-android-35.0)

---

## 비교표

| 방식 | base APK | Split 추가 | 재시작 | Android 요구 |
|------|----------|-----------|--------|-------------|
| **DexClassLoader** | ❌ 불필요 | ✅ filesDir | ❌ 없음 | Android 5.0+ |
| **SplitCompat (Play)** | ✅ 시스템 설치 | ✅ Play Store | ⚠️ O 이하 | Android 5.0+ |
| **PackageInstaller (재시작 방지)** | ✅ 시스템 설치 | ✅ MODE_INHERIT | ❌ 없음 | Android 8.0+ |
| **PackageInstaller (재시작 허용)** | ✅ 시스템 설치 | ✅ MODE_INHERIT | ⚠️ 있음 | Android 5.0+ |

---

## 프로덕션 레벨 권장 방식

### 시나리오 1: 최신 디바이스 타겟 (Android 8.0+)

```kotlin
// ✅ 최고의 사용자 경험
val sessionParams = PackageInstaller.SessionParams(
    MODE_INHERIT_EXISTING
)
sessionParams.setDontKillApp(true)

// 장점:
// - base APK 재설치 없음
// - 앱 재시작 없음
// - 시스템 관리 (안정성)
// - 리소스 통합 (SplitCompat)

// 단점:
// - Android 8.0+ 필수
// - 사용자 설치 권한 필요
```

---

### 시나리오 2: 광범위한 디바이스 지원 (Android 5.0+)

```kotlin
// 버전별 분기
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    // Android 8.0+: 재시작 없이 설치
    sessionParams.setDontKillApp(true)
} else {
    // Android 7.x 이하: 재시작 허용
    // (자동 재시작됨)
}

// 장점:
// - 넓은 호환성
// - Android 8.0+에서는 재시작 없음

// 단점:
// - Android 7.x 이하 사용자는 재시작 경험
```

---

### 시나리오 3: 최대 유연성 (DexClassLoader)

```kotlin
// 현재 프로젝트 방식
// - filesDir 기반
// - 재시작 완전 불필요
// - 모든 Android 버전 지원

// 장점:
// - 재시작 완전 없음 (모든 버전)
// - 시스템 설치 불필요
// - 사용자 승인 불필요

// 단점:
// - 리소스 접근 복잡 (해결 가능)
// - 수동 관리 필요
```

---

## 하이브리드 최적 방식

### DexClassLoader + Resources 통합

```kotlin
class OptimizedPluginLoader(private val context: Context) {

    fun loadPlugin(apkPath: String): Result<Plugin> {
        // 1. Host Resources에 Plugin 리소스 통합
        integrateResources(apkPath)

        // 2. DexClassLoader로 클래스 로드
        val loader = DexClassLoader(apkPath, optimizedDir, null, parent)

        // 3. 플러그인 초기화
        val plugin = loader.loadClass("Plugin").newInstance()

        return Result.success(plugin)
    }

    private fun integrateResources(apkPath: String) {
        // SplitCompat 방식으로 리소스 통합
        val assetManager = context.resources.assets
        assetManager.addAssetPath(apkPath)

        // ✅ 이제 context.resources로 모든 접근 가능!
    }
}
```

**장점:**
- ✅ 재시작 완전 불필요
- ✅ 리소스 통합 (SplitCompat 방식)
- ✅ 시스템 설치 불필요
- ✅ 사용자 승인 불필요
- ✅ 모든 Android 버전 지원

**단점:**
- ⚠️ 시스템 관리 없음 (직접 관리)

---

## 결론

### 사용자 질문에 대한 답변

**"base APK까지 재설치해서 재시작되는 거 아님?"**

**✅ 아닙니다!**

**이유:**
1. **MODE_INHERIT_EXISTING**: base APK 유지, split만 추가
2. **setDontKillApp(true)**: 앱 재시작 방지
3. **Android 8.0+**: 재시작 없이 설치 가능

**GloballyDynamic이 재시작되는 이유:**
- ⚠️ setDontKillApp(true)를 사용하지 않거나
- ⚠️ Android 7.x 이하 디바이스거나
- ⚠️ 설정에 따라 안전하게 재시작 허용

**프로덕션 레벨 권장:**

| 목표 | 추천 방식 |
|------|----------|
| 최신 디바이스 (8.0+) | PackageInstaller + setDontKillApp |
| 광범위 호환성 | 버전별 분기 |
| 최대 유연성 | DexClassLoader + Resources 통합 |

---

## 참고 자료

### PackageInstaller & MODE_INHERIT_EXISTING
- [How Android Handles Feature Modules](https://medium.com/@shashankkumar45556/how-android-handles-the-installation-of-feature-modules-b4544b4b4a97)
- [PackageInstaller Documentation](https://learn.microsoft.com/en-us/dotnet/api/android.content.pm.packageinstaller?view=net-android-35.0)
- [Applying PackageInstaller](https://commonsware.com/Q/pages/chap-pkg-001)

### AAB vs APK
- [AAB File Guide | LambdaTest](https://www.lambdatest.com/blog/aab-file/)
- [About Android App Bundles](https://developer.android.com/guide/app-bundle)
- [AAB vs APK Developer's Guide](https://medium.com/droidstack/apk-vs-aab-a-developers-guide-to-packaging-and-distribution-1bdacca1f172)

### Split APK Installation
- [Manually installing split APK files](https://raccoon.onyxbits.de/blog/install-split-apk-adb/)
- [Split APK Installation Guide | XDA](https://xdaforums.com/t/guide-split-apk-installation.3934631/)
