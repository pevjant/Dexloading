# 한눈에 보는 비교

## DexClassLoader (현재 프로젝트)

```kotlin
// 파일 복사
val apkFile = File(filesDir, "plugin.apk")
assets.open("plugin.apk").copyTo(apkFile.outputStream())

// 클래스 로드만
val loader = DexClassLoader(apkFile.path, optimizedDir, null, classLoader)
val clazz = loader.loadClass("com.example.Plugin")

// Resources 수동 생성 (번거로움)
val assetManager = AssetManager::class.java.newInstance()
assetManager.addAssetPath(apkFile.path)
val pluginResources = Resources(assetManager, displayMetrics, config)
```

**특징:**
- ❌ 시스템에 설치 안 됨
- ✅ 앱 재시작 불필요
- ⚠️ 리소스 수동 관리
- ✅ 언제든 로드/언로드

---

## Split APK (Play Core)

```kotlin
// Play Store에서 자동 다운로드 + 설치
val manager = SplitInstallManagerFactory.create(context)
val request = SplitInstallRequest.newBuilder()
    .addModule("camera")
    .build()

manager.startInstall(request)
    .addOnSuccessListener {
        // Android O+: 즉시 사용 가능
        // Android N-: 앱 재시작 후 사용
    }

// 설치 후 자동으로 사용 가능 (수동 작업 없음)
val res = getResources()
val cameraLayout = R.layout.camera_view  // ← split APK의 레이아웃
```

**특징:**
- ✅ 시스템에 설치됨 (PackageManager)
- ⚠️ Android O 이하에서 재시작 필요
- ✅ 리소스 자동 통합
- ❌ 언로드 불가능 (설치되면 제거만 가능)

---

## GloballyDynamic

```kotlin
// 자체 서버에서 다운로드 + 설치
val config = GloballyDynamicConfigurationProvider.getConfiguration(this)
val request = SplitInstallRequest.newBuilder()
    .addModule("camera")
    .build()

config.splitInstallManager.startInstall(request)
    .addOnSuccessListener {
        // Split APK와 동일
    }
```

**특징:**
- ✅ Split APK와 동일한 메커니즘
- ✅ 자체 서버 사용 (Play Store 불필요)
- ⚠️ PackageInstaller 권한 필요
- ⚠️ 사용자 설치 승인 필요

---

## 가장 큰 차이: 설치 vs 로드

### DexClassLoader = 로드 (Load)
```
앱 메모리
┌──────────────┐
│  App Process │
│  ┌────────┐  │
│  │ Plugin │  │ ← 메모리에만 존재
│  └────────┘  │
└──────────────┘

시스템 모름 ❌
재시작 불필요 ✅
```

### Split APK = 설치 (Install)
```
시스템 (/data/app/)
┌──────────────────┐
│ base.apk         │ ← 디스크에 설치
│ split_camera.apk │ ← 디스크에 설치
└──────────────────┘
         ↓
앱이 시작될 때 자동 로드

시스템이 관리 ✅
재시작 필요 ⚠️ (Android 버전별)
```

---

## 실전 비유

### DexClassLoader
```
USB 메모리를 꽂아서 파일 읽기
- USB 빼면 사라짐
- 재부팅 불필요
- 파일만 읽을 수 있음 (리소스 제한)
```

### Split APK
```
프로그램 설치
- 제어판에 등록됨
- 설치 후 재시작 필요할 수 있음
- 완전한 기능 (리소스 포함)
```

### GloballyDynamic
```
외부에서 다운받은 프로그램 설치
- Split APK와 동일하지만
- 스토어 대신 자체 서버
```

---

## 플러그인 모듈 APK로 빌드하는 건 공통

### 맞습니다! 모두 APK 형식 사용:

**DexClassLoader:**
```
plugin.apk
├── classes.dex      ← 로드
├── res/             ← 수동 처리
└── assets/          ← 수동 처리
```

**Split APK:**
```
split_feature.apk
├── classes.dex      ← 자동 통합
├── res/             ← 자동 통합
└── AndroidManifest  ← PackageManager가 처리
```

**차이는 "로딩 방식":**
- DexClassLoader: APK 파일을 직접 읽음 (앱이 관리)
- Split APK: 시스템이 설치하고 관리 (PackageManager가 관리)

---

## 왜 현재 프로젝트는 DexClassLoader?

**목표: 앱 종료 없이 기능 추가**

```
DexClassLoader:
✅ 버튼 클릭 → 즉시 로드
✅ 언로드 → 즉시 제거
✅ 다시 로드 → 즉시 사용

Split APK:
❌ 설치 요청 → 대기
⚠️ 앱 재시작 (Android O 이하)
❌ 제거 → 재설치만 가능
```

**프로토타입 학습 목적에는 DexClassLoader가 최적!**
