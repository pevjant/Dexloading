# Shared 모듈을 통한 공통 리소스 공유

## 문제: 리소스 중복

플러그인과 호스트 앱이 동일한 리소스를 사용하는 경우:
- ❌ 각 APK에 중복 포함 → APK 크기 증가
- ❌ 유지보수 어려움 (색상 변경 시 양쪽 수정 필요)
- ❌ 일관성 문제 (버전 불일치 가능)

## 해결: Shared 모듈에 공통 리소스 배치

### 디렉토리 구조

```
shared/
├── src/main/
│   ├── java/com/example/shared/
│   │   └── SharedResources.kt
│   └── res/
│       ├── drawable/
│       │   ├── common_logo.xml
│       │   └── common_icon_check.xml
│       ├── values/
│       │   ├── colors.xml       # 브랜딩 색상
│       │   ├── strings.xml      # 공통 문자열
│       │   └── dimens.xml       # 공통 크기
│       └── layout/
│           └── common_loading.xml
└── build.gradle.kts
```

### shared/build.gradle.kts 설정

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.shared"
    compileSdk = 36

    defaultConfig {
        minSdk = 31
    }

    // 리소스 포함 활성화
    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)  // Resources 사용을 위해 필요
}
```

### shared/res/values/colors.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- 브랜딩 색상 - 앱과 플러그인 공통 사용 -->
    <color name="brand_primary">#FF6200EE</color>
    <color name="brand_primary_dark">#FF3700B3</color>
    <color name="brand_accent">#FF03DAC5</color>

    <!-- 공통 상태 색상 -->
    <color name="status_success">#FF4CAF50</color>
    <color name="status_error">#FFF44336</color>
    <color name="status_warning">#FFFF9800</color>
    <color name="status_info">#FF2196F3</color>

    <!-- 공통 배경 색상 -->
    <color name="background_light">#FFFFFFFF</color>
    <color name="background_dark">#FF121212</color>
</resources>
```

### shared/res/values/strings.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- 공통 에러 메시지 -->
    <string name="error_network">네트워크 연결을 확인해주세요</string>
    <string name="error_unknown">알 수 없는 오류가 발생했습니다</string>

    <!-- 공통 버튼 텍스트 -->
    <string name="btn_confirm">확인</string>
    <string name="btn_cancel">취소</string>
    <string name="btn_retry">다시 시도</string>

    <!-- 공통 로딩 메시지 -->
    <string name="loading">로딩 중...</string>
</resources>
```

### shared/src/main/java/com/example/shared/SharedResources.kt

```kotlin
package com.example.shared

import android.content.Context
import android.content.res.Resources
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat

/**
 * 공통 리소스 접근을 위한 유틸리티 클래스
 */
object SharedResources {

    /**
     * 공통 색상 가져오기
     */
    fun getBrandPrimaryColor(context: Context): Int {
        return ContextCompat.getColor(context, R.color.brand_primary)
    }

    fun getStatusColor(context: Context, status: Status): Int {
        return ContextCompat.getColor(context, when (status) {
            Status.SUCCESS -> R.color.status_success
            Status.ERROR -> R.color.status_error
            Status.WARNING -> R.color.status_warning
            Status.INFO -> R.color.status_info
        })
    }

    /**
     * 공통 Drawable 가져오기
     */
    fun getCommonLogo(context: Context): Drawable? {
        return ContextCompat.getDrawable(context, R.drawable.common_logo)
    }

    /**
     * 상태 타입
     */
    enum class Status {
        SUCCESS, ERROR, WARNING, INFO
    }
}
```

### app/build.gradle.kts - 호스트 앱 설정

```kotlin
dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // shared 모듈 의존성 - 리소스가 APK에 포함됨
    implementation(project(":shared"))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
```

### plugin/build.gradle.kts - 플러그인 설정

```kotlin
dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)

    // shared 모듈을 compileOnly로 - 빌드 시만 참조, APK에는 미포함
    // 런타임에는 호스트 앱의 shared 리소스 사용
    compileOnly(project(":shared"))
}
```

## 사용 예제

### 호스트 앱에서 사용 (app 모듈)

```kotlin
import com.example.shared.R as SharedR
import com.example.shared.SharedResources

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 방법 1: 직접 리소스 ID 사용
        val brandColor = getColor(SharedR.color.brand_primary)
        val errorMsg = getString(SharedR.string.error_network)

        // 방법 2: SharedResources 유틸리티 사용
        val primaryColor = SharedResources.getBrandPrimaryColor(this)
        val successColor = SharedResources.getStatusColor(this, SharedResources.Status.SUCCESS)
    }
}
```

### 플러그인에서 사용 (plugin 모듈)

```kotlin
import com.example.shared.R as SharedR
import com.example.shared.SharedResources

class PluginImpl : PluginInterface {

    override fun createView(hostContext: Context): View {
        val view = LinearLayout(hostContext).apply {
            // shared 모듈의 색상 사용
            setBackgroundColor(SharedResources.getBrandPrimaryColor(hostContext))
        }

        val textView = TextView(hostContext).apply {
            // shared 모듈의 문자열 사용
            text = hostContext.getString(SharedR.string.loading)

            // shared 모듈의 색상 사용
            setTextColor(hostContext.getColor(SharedR.color.brand_accent))
        }

        view.addView(textView)
        return view
    }
}
```

## 장점

### 1. 리소스 중복 제거
- 공통 리소스는 호스트 앱 APK에만 포함
- 플러그인 APK 크기 감소
- 전체 배포 크기 최소화

### 2. 일관성 보장
- 브랜딩 색상, 로고 등이 항상 동일
- 호스트 앱 업데이트 시 플러그인도 자동 반영

### 3. 유지보수 용이
- 색상/문자열 변경 시 한 곳만 수정
- 버전 관리 간소화

### 4. 타입 안전성
- 컴파일 타임에 리소스 존재 여부 확인
- IDE 자동완성 지원

## 주의사항

### 1. ClassLoader 이슈
플러그인에서 shared 모듈의 클래스 참조 시:
```kotlin
// ❌ 안 됨 - 플러그인 APK에 클래스가 없음
val utils = SharedUtils()

// ✅ 됨 - 호스트 ClassLoader를 통해 로드
val sharedClass = Class.forName("com.example.shared.SharedResources",
                                true,
                                hostContext.classLoader)
```

### 2. 리소스 ID 충돌 방지
shared 모듈은 기본 패키지 ID(0x7f) 사용:
- ✅ 호스트 앱(0x7f)과 자연스럽게 병합
- ✅ 플러그인(0x71)과 충돌하지 않음

### 3. ProGuard/R8 설정
플러그인에서 사용하는 shared 클래스는 난독화 제외:

**app/proguard-rules.pro:**
```proguard
# Shared module classes used by plugins
-keep class com.example.shared.** { *; }
-keep interface com.example.shared.** { *; }
```

## 권장 사용 사례

### Shared 모듈에 포함할 리소스:
- ✅ 브랜딩 색상, 로고
- ✅ 공통 아이콘 (체크, 경고 등)
- ✅ 공통 에러 메시지
- ✅ 공통 레이아웃 (로딩, 에러 화면)
- ✅ 공통 스타일, 테마

### 각 모듈에 포함할 리소스:
- ✅ 호스트 앱 전용 화면/기능
- ✅ 플러그인 고유 기능
- ✅ 버전별로 다를 수 있는 리소스

## 실제 적용 시나리오

```
공통 리소스:
- 브랜드 색상: #6200EE
- 회사 로고
- "확인", "취소" 버튼 텍스트

호스트 앱 (v1.0):
- MainActivity 레이아웃
- 기본 기능 화면

플러그인 A (v1.0):
- 새 기능 A 화면
- 공통 브랜드 색상 사용 ← shared
- 공통 로고 사용 ← shared

플러그인 B (v1.0):
- 새 기능 B 화면
- 공통 브랜드 색상 사용 ← shared
- 공통 버튼 텍스트 사용 ← shared

===== 브랜드 색상 변경 =====

호스트 앱 (v1.1):
- shared 색상 업데이트: #6200EE → #FF0000

결과:
- ✅ 플러그인 A/B 재빌드 불필요
- ✅ 앱 재시작 시 모든 화면이 새 색상 적용
```
