import java.util.Base64 // Configuration Cache Invalidation Trigger

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

android {
  namespace = "com.example"
  compileSdk = 35

  defaultConfig {
    applicationId = "com.aistudio.csdplayer.mtknyr"
    minSdk = 21
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Automatically decode debug.keystore.base64 to debug.keystore if running locally
  val debugKeystore = file("${rootDir}/debug.keystore")
  val debugKeystoreBase64 = file("${rootDir}/debug.keystore.base64")
  if (!debugKeystore.exists() && debugKeystoreBase64.exists()) {
    try {
      val cleanedBase64 = debugKeystoreBase64.readText().replace("\\s".toRegex(), "")
      val decoded = Base64.getDecoder().decode(cleanedBase64)
      debugKeystore.writeBytes(decoded)
    } catch (e: Exception) {
      project.logger.warn("Could not auto-decode debug.keystore.base64: ${e.message}")
    }
  }

  // Ensure the custom logo configuration is processed prior to resource compilation
  val drawableDir = file("${projectDir}/src/main/res/drawable")
  val customLogoPng = file("${drawableDir}/custom_app_logo.png")
  val customLogoJpg = file("${drawableDir}/custom_app_logo.jpg")
  val customLogoJpeg = file("${drawableDir}/custom_app_logo.jpeg")
  
  val hasCustomLogo = customLogoPng.exists() || customLogoJpg.exists() || customLogoJpeg.exists()
  val customLogoResourceName = when {
    customLogoPng.exists() -> "custom_app_logo"
    customLogoJpg.exists() -> "custom_app_logo"
    customLogoJpeg.exists() -> "custom_app_logo"
    else -> null
  }

  val launcherForegroundXml = file("${drawableDir}/ic_launcher_foreground.xml")
  val tvBannerXml = file("${drawableDir}/ic_tv_banner.xml")

  if (hasCustomLogo && customLogoResourceName != null) {
    // Overwrite with custom logo layer-list representation
    val customForeground = """
        <?xml version="1.0" encoding="utf-8"?>
        <layer-list xmlns:android="http://schemas.android.com/apk/res/android">
            <item
                android:width="66dp"
                android:height="66dp"
                android:drawable="@drawable/$customLogoResourceName"
                android:gravity="center" />
        </layer-list>
    """.trimIndent()
    if (launcherForegroundXml.exists() && launcherForegroundXml.readText().trim() != customForeground.trim()) {
      launcherForegroundXml.writeText(customForeground)
      println("BUILD HOOK: Overwrote ic_launcher_foreground.xml to use $customLogoResourceName")
    }

    val customTvBanner = """
        <?xml version="1.0" encoding="utf-8"?>
        <layer-list xmlns:android="http://schemas.android.com/apk/res/android">
            <!-- Deep premium dark layout background -->
            <item>
                <shape android:shape="rectangle">
                    <solid android:color="#0A0A0A" />
                </shape>
            </item>
        
            <!-- Warm dynamic dark-red background flow -->
            <item>
                <shape android:shape="rectangle">
                    <gradient
                        android:angle="315"
                        android:startColor="#2A0505"
                        android:endColor="#0A0A0A" />
                </shape>
            </item>
        
            <!-- Left visual red accent indicator -->
            <item android:right="314dp">
                <shape android:shape="rectangle">
                    <solid android:color="#E50914" />
                </shape>
            </item>
        
            <!-- Core App Icon Rounded Box centered in banner -->
            <item android:gravity="center" android:width="80dp" android:height="80dp">
                <shape android:shape="rectangle">
                    <corners android:radius="16dp" />
                    <solid android:color="#1A1A1A" />
                </shape>
            </item>
        
            <!-- Dynamic User Uploaded App Logo in the TV Banner -->
            <item
                android:width="64dp"
                android:height="64dp"
                android:drawable="@drawable/$customLogoResourceName"
                android:gravity="center" />
        </layer-list>
    """.trimIndent()
    if (tvBannerXml.exists() && tvBannerXml.readText().trim() != customTvBanner.trim()) {
      tvBannerXml.writeText(customTvBanner)
      println("BUILD HOOK: Overwrote ic_tv_banner.xml to use $customLogoResourceName")
    }
  } else {
    // Restore defaults if modified/custom logo doesn't exist
    val defaultForeground = """
        <?xml version="1.0" encoding="utf-8"?>
        <vector xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:aapt="http://schemas.android.com/aapt"
            android:width="108dp"
            android:height="108dp"
            android:viewportWidth="108"
            android:viewportHeight="108">
        
            <!-- Glowing Cyber Outer Shield (Hexagonal Bezel) -->
            <path
                android:pathData="M54,20 L84,37 L84,71 L54,88 L24,71 L24,37 Z"
                android:strokeWidth="2.5"
                android:strokeLineJoin="round"
                android:fillColor="#CD050505">
                <aapt:attr name="android:strokeColor">
                    <gradient
                        android:startX="24"
                        android:startY="37"
                        android:endX="84"
                        android:endY="71"
                        android:type="linear">
                        <item android:offset="0.0" android:color="#00E5FF" />
                        <item android:offset="0.5" android:color="#FFB300" />
                        <item android:offset="1.0" android:color="#E50914" />
                    </gradient>
                </aapt:attr>
            </path>
        
            <!-- Chrome Inner Accent Bezel -->
            <path
                android:pathData="M54,24 L80,39 L80,69 L54,84 L28,69 L28,39 Z"
                android:strokeWidth="1.5"
                android:strokeLineJoin="round">
                <aapt:attr name="android:strokeColor">
                    <gradient
                        android:startX="28"
                        android:startY="39"
                        android:endX="80"
                        android:endY="69"
                        android:type="linear">
                        <item android:offset="0.0" android:color="#FFFFFF" />
                        <item android:offset="0.5" android:color="#555555" />
                        <item android:offset="1.0" android:color="#FFFFFF" />
                    </gradient>
                </aapt:attr>
            </path>
        
            <!-- Masterful "CSD" Lettering Monogram (Glowing 3D Cyber-Chrome Outlines) -->
            
            <!-- 'C' Character Outline -->
            <path
                android:pathData="M43,45 C38,45 34,48 34,54 C34,60 38,63 43,63"
                android:strokeWidth="4"
                android:strokeLineCap="round"
                android:strokeLineJoin="round">
                <aapt:attr name="android:strokeColor">
                    <gradient
                        android:startX="34"
                        android:startY="45"
                        android:endX="43"
                        android:endY="63"
                        android:type="linear">
                        <item android:offset="0.0" android:color="#00E5FF" />
                        <item android:offset="1.0" android:color="#FFFFFF" />
                    </gradient>
                </aapt:attr>
            </path>
        
            <!-- 'S' Character Outline -->
            <path
                android:pathData="M50,46 C47,46 46,48 47,51 C48,54 59,52 60,56 C61,60 57,62 53,62"
                android:strokeWidth="4"
                android:strokeLineCap="round"
                android:strokeLineJoin="round">
                <aapt:attr name="android:strokeColor">
                    <gradient
                        android:startX="46"
                        android:startY="46"
                        android:endX="60"
                        android:endY="62"
                        android:type="linear">
                        <item android:offset="0.0" android:color="#FFFFFF" />
                        <item android:offset="0.7" android:color="#FFD54F" />
                        <item android:offset="1.0" android:color="#00E5FF" />
                    </gradient>
                </aapt:attr>
            </path>
        
            <!-- 'D' Character Outline -->
            <path
                android:pathData="M67,45 L71,45 C75,45 78,48 78,54 C78,60 75,63 71,63 L67,63 Z"
                android:strokeWidth="4"
                android:strokeLineCap="round"
                android:strokeLineJoin="round">
                <aapt:attr name="android:strokeColor">
                    <gradient
                        android:startX="67"
                        android:startY="45"
                        android:endX="78"
                        android:endY="63"
                        android:type="linear">
                        <item android:offset="0.0" android:color="#FFFFFF" />
                        <item android:offset="1.0" android:color="#E50914" />
                    </gradient>
                </aapt:attr>
            </path>
        
            <!-- Minimalist play triangle underneath the logo -->
            <path
                android:pathData="M50,70 L60,70 L55,75 Z"
                android:fillColor="#E50914" />
        
        </vector>
    """.trimIndent()
    if (launcherForegroundXml.exists() && launcherForegroundXml.readText().trim() != defaultForeground.trim()) {
      launcherForegroundXml.writeText(defaultForeground)
      println("BUILD HOOK: Reverted ic_launcher_foreground.xml to default design vector")
    }

    val defaultTvBanner = """
        <?xml version="1.0" encoding="utf-8"?>
        <layer-list xmlns:android="http://schemas.android.com/apk/res/android">
            <!-- Deep premium dark layout background -->
            <item>
                <shape android:shape="rectangle">
                    <solid android:color="#0A0A0A" />
                </shape>
            </item>
        
            <!-- Warm dynamic dark-red background flow -->
            <item>
                <shape android:shape="rectangle">
                    <gradient
                        android:angle="315"
                        android:startColor="#2A0505"
                        android:endColor="#0A0A0A" />
                </shape>
            </item>
        
            <!-- Left visual red accent indicator -->
            <item android:right="314dp">
                <shape android:shape="rectangle">
                    <solid android:color="#E50914" />
                </shape>
            </item>
        
            <!-- Core App Icon Rounded Box centered in banner -->
            <item android:gravity="center" android:width="72dp" android:height="72dp">
                <shape android:shape="rectangle">
                    <corners android:radius="16dp" />
                    <solid android:color="#E50914" />
                </shape>
            </item>
        
            <!-- Secondary Overlay for high-end feel -->
            <item android:gravity="center" android:width="44dp" android:height="44dp">
                <shape android:shape="rectangle">
                    <corners android:radius="8dp" />
                    <solid android:color="#FFFFFF" />
                </shape>
            </item>
        </layer-list>
    """.trimIndent()
    if (tvBannerXml.exists() && tvBannerXml.readText().trim() != defaultTvBanner.trim()) {
      tvBannerXml.writeText(defaultTvBanner)
      println("BUILD HOOK: Reverted ic_tv_banner.xml to default design layout")
    }
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.ui)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}

