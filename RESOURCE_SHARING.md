# 플러그인에서 호스트 앱 리소스 접근 방법

## 문제 상황
- 플러그인(0x71)과 호스트(0x7f)의 리소스 ID가 분리되어 있음
- 플러그인 빌드 타임에는 호스트의 R 클래스를 참조할 수 없음
- 하드코딩된 ID(0x7f060001)는 유지보수 불가능

## 해결 방법

### 방법 1: getIdentifier() - 리소스 이름으로 조회 ✅ 추천
**장점:**
- 타입 안전하지는 않지만 실용적
- 리소스 이름만 알면 접근 가능
- 추가 의존성 불필요

**구현:**
```kotlin
// 플러그인에서 호스트 리소스 접근
val hostResources = hostContext.resources
val hostPackageName = hostContext.packageName

// Drawable 접근
val drawableId = hostResources.getIdentifier("ic_launcher", "drawable", hostPackageName)
val drawable = hostResources.getDrawable(drawableId, null)

// Color 접근
val colorId = hostResources.getIdentifier("sample_red", "color", hostPackageName)
val color = hostResources.getColor(colorId, null)

// Layout 접근
val layoutId = hostResources.getIdentifier("activity_main", "layout", hostPackageName)

// String 접근
val stringId = hostResources.getIdentifier("app_name", "string", hostPackageName)
val appName = hostResources.getString(stringId)
```

**단점:**
- 런타임 에러 가능 (리소스가 없으면 0 반환)
- 컴파일 타임 체크 불가능
- 문자열 하드코딩 필요

---

### 방법 2: Reflection으로 R 클래스 로드
**장점:**
- R.id.xxx 형태로 접근 가능
- IDE 자동완성 없지만 구조적

**구현:**
```kotlin
// 호스트 앱의 R 클래스 로드
val pluginClassLoader = this.javaClass.classLoader  // 플러그인의 parent는 호스트 ClassLoader
val hostRClass = pluginClassLoader?.loadClass("com.example.dexloadingtest.R")

// R.drawable 클래스 접근
val rDrawableClass = pluginClassLoader?.loadClass("com.example.dexloadingtest.R\$drawable")
val iconField = rDrawableClass?.getField("ic_launcher")
val iconId = iconField?.getInt(null)

// R.color 클래스 접근
val rColorClass = pluginClassLoader?.loadClass("com.example.dexloadingtest.R\$color")
val colorField = rColorClass?.getField("sample_red")
val colorId = colorField?.getInt(null)

// 실제 리소스 조회
val drawable = hostContext.resources.getDrawable(iconId!!, null)
val color = hostContext.resources.getColor(colorId!!, null)
```

**단점:**
- Reflection 오버헤드
- 패키지명 하드코딩 필요
- ProGuard/R8 난독화 시 문제 발생 가능

---

### 방법 3: 리소스 맵 전달 (인터페이스 확장) ✅ 추천
**장점:**
- 타입 안전
- 명시적이고 유지보수 용이
- 필요한 리소스만 노출

**구현:**

#### shared/PluginInterface.kt 수정:
```kotlin
data class HostResources(
    val colors: Map<String, Int> = emptyMap(),
    val drawables: Map<String, Int> = emptyMap(),
    val strings: Map<String, Int> = emptyMap(),
    val layouts: Map<String, Int> = emptyMap()
)

interface PluginInterface {
    fun initialize(
        hostContext: Context,
        pluginResources: Resources,
        pluginAssets: AssetManager,
        hostResources: HostResources  // 추가
    )
}
```

#### app/MainActivity.kt 사용 예:
```kotlin
val hostResources = HostResources(
    colors = mapOf(
        "primary" to R.color.sample_red,
        "accent" to R.color.sample_blue
    ),
    drawables = mapOf(
        "logo" to R.drawable.ic_launcher
    ),
    strings = mapOf(
        "app_name" to R.string.app_name
    )
)

plugin.initialize(context, pluginResources, pluginAssets, hostResources)
```

#### plugin/PluginImpl.kt 사용:
```kotlin
private var hostResourceMap: HostResources? = null

override fun initialize(..., hostResources: HostResources) {
    this.hostResourceMap = hostResources
}

fun useHostColor() {
    val colorId = hostResourceMap?.colors?.get("primary")
    if (colorId != null) {
        val color = hostContext.resources.getColor(colorId, null)
    }
}
```

---

### 방법 4: 공유 모듈에 공통 리소스 배치 ✅ 중복 제거에 최적
**장점:**
- 리소스 중복 완전 제거
- 빌드 타임 체크 가능
- 양방향 접근 가능

**구조:**
```
shared/
├── src/main/res/
│   ├── drawable/
│   │   └── common_icon.xml
│   ├── values/
│   │   ├── colors.xml  (공통 색상)
│   │   └── strings.xml (공통 문자열)
└── build.gradle.kts
```

**shared/build.gradle.kts:**
```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.shared"
    compileSdk = 36

    // 리소스를 포함하도록 설정
    buildFeatures {
        resValues = true
    }
}
```

**app/build.gradle.kts:**
```kotlin
dependencies {
    implementation(project(":shared"))  // shared의 리소스 포함
}
```

**plugin/build.gradle.kts:**
```kotlin
dependencies {
    compileOnly(project(":shared"))  // 빌드만, APK에는 미포함
}
```

**사용:**
```kotlin
// app과 plugin 모두에서 동일하게 사용
val commonColor = resources.getColor(com.example.shared.R.color.common_primary, null)
```

---

## 권장 방식

### 시나리오별 추천:

1. **공통 리소스가 많은 경우**
   → **방법 4 (shared 모듈)**
   - 예: 브랜딩 색상, 로고, 공통 아이콘

2. **호스트 리소스 선택적 접근**
   → **방법 3 (리소스 맵 전달)**
   - 예: 플러그인이 호스트의 특정 기능/리소스만 사용

3. **동적 리소스 접근 (리소스 이름만 알 때)**
   → **방법 1 (getIdentifier)**
   - 예: 설정 파일에서 리소스 이름을 읽어서 동적 로드

4. **레거시 호환 (패키지명이 고정된 경우)**
   → **방법 2 (Reflection)**
   - 예: 기존 앱의 리소스 구조 변경 불가

---

## 성능 비교

| 방법 | 빌드타임 체크 | 런타임 성능 | 유지보수성 | 타입 안전성 |
|------|--------------|------------|-----------|------------|
| getIdentifier | ❌ | ⚠️ 중간 | ⚠️ | ❌ |
| Reflection | ❌ | ❌ 느림 | ❌ | ❌ |
| 리소스 맵 | ✅ | ✅ 빠름 | ✅ | ✅ |
| shared 모듈 | ✅ | ✅ 빠름 | ✅ | ✅ |

---

## 실제 구현 예제

다음 파일들에서 각 방법의 실제 구현을 확인할 수 있습니다:

1. `examples/ResourceAccessExample.kt` - getIdentifier 예제
2. `examples/ReflectionAccessExample.kt` - Reflection 예제
3. `shared/PluginInterface.kt` - 리소스 맵 인터페이스
4. `shared/res/` - 공유 리소스 예제
