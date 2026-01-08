# Android 동적 로딩 방식 비교

## 질문: Split APK vs DexClassLoader vs GloballyDynamic의 차이는?

---

## 1️⃣ DexClassLoader (현재 프로젝트)

### 동작 방식
```kotlin
// APK 파일에서 클래스만 로드
val dexClassLoader = DexClassLoader(
    apkPath,           // APK 파일 경로
    optimizedDir,      // DEX 최적화 디렉토리
    null,              // Native library path
    parent             // Parent ClassLoader
)

// 클래스 로드
val clazz = dexClassLoader.loadClass("com.example.Plugin")
val instance = clazz.newInstance()
```

### 특징
- ✅ **완전한 자유도**: 어디서든 APK/DEX 파일 로드 가능
- ✅ **런타임 로드**: 앱 실행 중 언제든지 로드/언로드
- ✅ **외부 서버**: HTTP로 다운로드 후 로드 가능
- ⚠️ **리소스 제한적**: Resources, AssetManager 수동 생성 필요
- ⚠️ **보안 위험**: 코드 검증 없음 (멀웨어 악용)
- ❌ **Android 10+ 제한**: 보안 정책으로 제약 증가

### 프로세스
```
1. APK 다운로드 (assets, 서버 등)
2. filesDir에 복사 (Android 14+: setReadOnly 필수)
3. DexClassLoader로 클래스 로드
4. 수동으로 Resources, AssetManager 생성
5. 클래스 인스턴스화

❌ 프로세스 종료 없음
❌ 시스템에 설치 안 됨
✅ 앱 내부에서만 로드
```

---

## 2️⃣ Split APK + Dynamic Feature Module (Google Play)

### 동작 방식
```kotlin
// Play Core API 사용
val manager = SplitInstallManagerFactory.create(context)

val request = SplitInstallRequest.newBuilder()
    .addModule("feature_camera")
    .build()

manager.startInstall(request)
    .addOnSuccessListener { sessionId ->
        // 설치 진행
    }
```

### 특징
- ✅ **완벽한 리소스 지원**: Layout, Drawable, String 모두 사용 가능
- ✅ **시스템 통합**: PackageManager에 등록됨
- ✅ **보안**: Google Play 검증 완료
- ✅ **자동 업데이트**: Play Store를 통한 업데이트
- ❌ **Play Store 종속**: Google Play 필수
- ❌ **빌드 제약**: Android App Bundle 형식만 가능

### Split APK 구조
```
base.apk              (기본 앱)
├── classes.dex
├── res/
└── AndroidManifest.xml

split_feature_camera.apk  (기능 모듈)
├── classes.dex
├── res/              ← 독립적인 리소스!
└── AndroidManifest.xml

split_config.arm64_v8a.apk  (아키텍처별)
└── lib/arm64-v8a/
```

### 프로세스
```
1. Play Store에서 base.apk + split APK 다운로드
2. PackageManager가 시스템에 설치
3. ⚠️ 프로세스 종료 (재시작 필요)
4. 재시작 후 SplitCompat.install() 호출
5. Split APK가 base와 "논리적으로" 병합

✅ 시스템에 설치됨
✅ 물리적으로는 별도 파일 (base + split)
✅ 논리적으로는 하나의 앱
⚠️ 설치 시 프로세스 재시작 필요 (Android O 이하)
✅ Android O+에서는 즉시 사용 가능
```

### Base APK에 합쳐지는가?
```
❌ 물리적 병합 안 됨:
/data/app/com.example.app/
├── base.apk                    # 별도 파일
├── split_feature_camera.apk    # 별도 파일
└── split_config.arm64.apk      # 별도 파일

✅ 논리적 병합:
- PackageManager가 모든 split을 하나의 앱으로 관리
- Resources.getIdentifier()로 모든 리소스 접근 가능
- ClassLoader가 모든 DEX 파일 통합
```

---

## 3️⃣ GloballyDynamic (Play Core 오픈소스 대안)

### 동작 방식
```kotlin
// Self-hosted server 사용
val globallyDynamic = GloballyDynamicConfigurationProvider
    .getConfiguration(this)

val request = SplitInstallRequest.newBuilder()
    .addModule("feature_camera")
    .build()

globallyDynamic.splitInstallManager.startInstall(request)
```

### 특징
- ✅ **Split APK 방식**: Dynamic Feature Module과 동일한 구조
- ✅ **Self-hosted**: 자체 서버에서 배포 가능
- ✅ **Play Store 불필요**: Amazon, Samsung, Firebase 등 지원
- ✅ **완벽한 리소스 지원**: Split APK와 동일
- ⚠️ **PackageInstaller 사용**: 시스템 설치 필요
- ⚠️ **사용자 승인 필요**: 설치 권한 요청

### 프로세스
```
1. GloballyDynamic 서버에 split APK 업로드
2. 클라이언트가 HTTP로 split APK 다운로드
3. PackageInstaller로 시스템 설치 요청
4. 사용자 승인 (설치 팝업)
5. ⚠️ 프로세스 재시작
6. Split APK가 시스템에 설치됨

✅ 시스템에 설치됨
✅ Split APK 방식과 동일
⚠️ 사용자 설치 승인 필요
```

---

## 📊 비교표

| 항목 | DexClassLoader | Split APK (Play) | GloballyDynamic |
|------|----------------|------------------|-----------------|
| **리소스 지원** | ⚠️ 수동 생성 | ✅ 완벽 | ✅ 완벽 |
| **시스템 설치** | ❌ 앱 내부만 | ✅ 시스템 등록 | ✅ 시스템 등록 |
| **프로세스 재시작** | ❌ 불필요 | ⚠️ Android O 이하만 | ⚠️ 필요 |
| **사용자 승인** | ❌ 불필요 | ❌ 불필요 | ⚠️ 필요 (설치 권한) |
| **배포 방식** | 🌐 Any (HTTP, assets) | 🏪 Play Store | 🌐 Self-hosted |
| **보안 검증** | ❌ 없음 | ✅ Play 검증 | ⚠️ 서버 책임 |
| **앱 종료 없이 로드** | ✅ 가능 | ⚠️ Android O+ | ❌ 불가능 |
| **APK 크기** | 작음 | 작음 (Split) | 작음 (Split) |
| **난이도** | 🔴 높음 | 🟢 낮음 | 🟡 중간 |

---

## 🔍 핵심 차이: 설치 메커니즘

### DexClassLoader
```
앱 프로세스 메모리
┌─────────────────────┐
│   Base ClassLoader  │
│         ↓           │
│  DexClassLoader     │ ← 런타임에 추가
│    (plugin.apk)     │
└─────────────────────┘

시스템 인식: ❌
PackageManager: ❌
재시작 필요: ❌
```

### Split APK (Play / GloballyDynamic)
```
시스템 PackageManager
┌─────────────────────────────┐
│  com.example.app            │
│  ├── base.apk               │ ← 설치됨
│  ├── split_feature.apk      │ ← 설치됨
│  └── split_config.apk       │ ← 설치됨
└─────────────────────────────┘
         ↓
앱 ClassLoader (통합)

시스템 인식: ✅
PackageManager: ✅
재시작 필요: ⚠️ (Android 버전 의존)
```

---

## 💡 언제 어떤 방식을 사용할까?

### DexClassLoader 사용 시점
- ✅ **Hot-reload 필요**: 앱 재시작 없이 코드 교체
- ✅ **플러그인 시스템**: 외부 개발자가 제작한 플러그인
- ✅ **A/B 테스트**: 동적으로 로직 변경
- ✅ **보안 검토 필요 없음**: 자체 서버, 신뢰할 수 있는 소스
- ❌ **리소스 많으면 불편**: 수동 관리 필요

**예:** IntelliJ IDEA 플러그인 시스템, Minecraft 모드

### Split APK (Play Core) 사용 시점
- ✅ **Play Store 배포**: 공식 스토어 사용
- ✅ **대용량 기능**: 카메라, AR, 게임 모드 등
- ✅ **설치 기반 로드**: 최초 설치는 작게, 필요 시 추가
- ✅ **보안 중요**: Google 검증 필수
- ❌ **Play Store 종속 가능**: 벤더 락인

**예:** Google Maps (내비게이션 모듈), YouTube (VR 모듈)

### GloballyDynamic 사용 시점
- ✅ **Multi-store**: Amazon, Samsung, Huawei 동시 지원
- ✅ **Private 배포**: 기업 내부 앱
- ✅ **자체 서버**: 배포 제어 필요
- ✅ **Split APK 장점**: 리소스 완벽 지원
- ⚠️ **사용자 승인 필요**: 설치 권한

**예:** 엔터프라이즈 앱, B2B 솔루션

---

## 🎯 프로젝트별 추천

### 현재 프로젝트 (DexLoading)
- **목적**: Hot-reload, 앱 재시작 없이 기능 추가
- **추천**: ✅ DexClassLoader (현재 방식 유지)
- **이유**:
  - 프로세스 재시작 불필요
  - 자유로운 로드/언로드
  - 학습/프로토타입 목적

### 만약 프로덕션이라면?
- **Play Store 배포**: Split APK (Play Core)
- **자체 배포**: GloballyDynamic
- **Hot-reload 필수**: DexClassLoader

---

## 📚 참고 자료

### DexClassLoader
- [3 ways for Dynamic Code Loading in Android](https://erev0s.com/blog/3-ways-for-dynamic-code-loading-in-android/)
- [Android Developers: Custom Class Loading in Dalvik](https://android-developers.googleblog.com/2011/07/custom-class-loading-in-dalvik.html)

### Split APK / Dynamic Feature Module
- [Overview of Play Feature Delivery](https://developer.android.com/guide/playcore/feature-delivery)
- [Android App Bundle Format](https://developer.android.com/guide/app-bundle/app-bundle-format)
- [Chromium Docs: Dynamic Feature Modules](https://chromium.googlesource.com/chromium/src/+/main/docs/android_dynamic_feature_modules.md)

### GloballyDynamic
- [GloballyDynamic GitHub](https://github.com/jeppeman/GloballyDynamic)
- [GloballyDynamic: Multi-platform dynamic delivery](https://medium.com/@jesperaamann/globallydynamic-multi-platform-dynamic-delivery-with-a-unified-client-api-4dd6f160a07d)

---

## ⚠️ 보안 주의사항

2024-2025년 Google Play에서 239개 악성 앱이 DexClassLoader를 이용해 동적 코드 로딩으로 악성 행위를 숨긴 사례가 보고되었습니다.

**DexClassLoader 사용 시 필수 보안 조치:**
1. ✅ APK 파일 서명 검증
2. ✅ HTTPS로만 다운로드
3. ✅ SHA-256 해시 검증
4. ✅ 신뢰할 수 있는 소스만
5. ❌ 사용자가 업로드한 파일 로드 금지

출처: [Google Online Security Blog: Android app ecosystem safety 2024](https://security.googleblog.com/2025/01/how-we-kept-google-play-android-app-ecosystem-safe-2024.html)
