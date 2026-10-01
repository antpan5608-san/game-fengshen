plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
val releaseCode = providers.gradleProperty("fengshenVersionCode").orNull?.toInt() ?: 21
val releaseName = providers.gradleProperty("fengshenVersionName").orNull ?: "0.8.1-town-01"
require(releaseCode > 0 && releaseName.matches(Regex("[A-Za-z0-9._-]{1,80}")))
val releaseKey = System.getenv("FENGSHEN_KEYSTORE_PATH")
val requestingRelease = gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) }
if (requestingRelease) {
    require(!releaseKey.isNullOrBlank() && file(releaseKey).isFile) { "Existing signing keystore is required for release" }
    listOf("FENGSHEN_KEYSTORE_PASSWORD", "FENGSHEN_KEY_ALIAS", "FENGSHEN_KEY_PASSWORD").forEach {
        require(!System.getenv(it).isNullOrBlank()) { "Missing release signing secret: $it" }
    }
}
android {
    namespace = "org.fengshen.dev"
    compileSdk = 35
    useLibrary("android.test.runner")
    useLibrary("android.test.base")
    defaultConfig {
        applicationId = "org.fengshen.dev"
        minSdk = 30
        targetSdk = 35
        versionCode = releaseCode
        versionName = releaseName
        buildConfigField("String","BUILD_ID","\"${if (requestingRelease) "ci-release" else "town-01"}-v$releaseCode\"")
        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    signingConfigs {
        if (!releaseKey.isNullOrBlank()) create("existingApp") {
            storeFile = file(releaseKey)
            storePassword = System.getenv("FENGSHEN_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("FENGSHEN_KEY_ALIAS")
            keyPassword = System.getenv("FENGSHEN_KEY_PASSWORD")
        }
    }
    buildTypes {
        getByName("debug") { isDebuggable = true }
        getByName("release") {
            isDebuggable = false
            isMinifyEnabled = false
            if (!releaseKey.isNullOrBlank()) signingConfig = signingConfigs.getByName("existingApp")
        }
    }
    buildFeatures { buildConfig = true }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation("androidx.work:work-runtime:2.9.1")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
}
