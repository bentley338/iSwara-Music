import java.util.Properties
import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}
val secrets = Properties().apply { rootProject.file("secrets.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) } }
val publicSupabaseKey = System.getenv("SUPABASE_ANON_KEY") ?: secrets.getProperty("SUPABASE_ANON_KEY", "")
if (publicSupabaseKey.isNotBlank()) {
    val isPublicKey = runCatching {
        val parts = publicSupabaseKey.split('.')
        check(parts.size == 3)
        val payload = String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)
        val claims = groovy.json.JsonSlurper().parseText(payload) as Map<*, *>
        claims["role"] == "anon"
    }.getOrDefault(false)
    check(isPublicKey) { "SUPABASE_ANON_KEY must be a public anon JWT, never a service-role/secret key. Leave it empty for an unconfigured local build." }
}
fun secret(name: String) = (System.getenv(name) ?: secrets.getProperty(name, "")).replace("\\", "\\\\").replace("\"", "\\\"")
android {
    namespace = "id.iswara.music"
    compileSdk = 36
    defaultConfig {
        applicationId = "id.iswara.music"
        minSdk = 26
        targetSdk = 36
        versionCode = 12
        versionName = "0.9.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SUPABASE_URL", "\"${secret("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${secret("SUPABASE_ANON_KEY")}\"")
    }
    signingConfigs {
        create("release") {
            val path = System.getenv("ISWARA_KEYSTORE") ?: secrets.getProperty("ISWARA_KEYSTORE")
            if (path != null) {
                storeFile = file(path)
                storePassword = System.getenv("ISWARA_STORE_PASSWORD") ?: secrets.getProperty("ISWARA_STORE_PASSWORD")
                keyAlias = System.getenv("ISWARA_KEY_ALIAS") ?: secrets.getProperty("ISWARA_KEY_ALIAS", "iswara")
                keyPassword = System.getenv("ISWARA_KEY_PASSWORD") ?: secrets.getProperty("ISWARA_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        debug {
            buildConfigField("boolean", "ADMIN_TOOLS_ENABLED", "true")
        }
        release {
            buildConfigField("boolean", "ADMIN_TOOLS_ENABLED", "true")
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    packaging {
        resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1", "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
        resources.pickFirsts += "META-INF/CONTRIBUTORS.md"
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
        unitTests.all { it.systemProperty("java.library.path", "."); it.testLogging { events("passed", "skipped", "failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL } }
    }
    lint { abortOnError = true }
}
androidComponents {
    beforeVariants(selector().withBuildType("release")) {
        it.hostTests[com.android.build.api.variant.HostTestBuilder.UNIT_TEST_TYPE]?.enable = true
    }
}
kotlin { jvmToolchain(17) }
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(21) }
    jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.base/java.io=ALL-UNNAMED")
    // Android process singletons (DataStore, dispatchers, native graphics) must not leak between test classes.
    forkEvery = 1
}
dependencyLocking { lockAllConfigurations() }
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    implementation("com.atilika.kuromoji:kuromoji-ipadic:0.9.0")
    implementation("com.ibm.icu:icu4j:76.1")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.24.3")
    testRuntimeOnly("com.microsoft.onnxruntime:onnxruntime:1.24.3")
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.compose.ui:ui:1.11.2")
    implementation("androidx.compose.foundation:foundation:1.11.2")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.compose.ui:ui-tooling-preview:1.11.2")
    debugImplementation("androidx.compose.ui:ui-tooling:1.11.2")
    implementation("androidx.media3:media3-exoplayer:1.10.1")
    implementation("androidx.media3:media3-session:1.10.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.10.1")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.work:work-runtime-ktx:2.11.1")
    implementation("com.google.dagger:hilt-android:2.59.2")
    ksp("com.google.dagger:hilt-compiler:2.59.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt.coil3:coil-compose:3.4.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")
    implementation("com.github.TeamNewPipe:nanojson:e9d656ddb49a412a5a0a5d5ef20ca7ef09549996")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("org.json:json:20240303")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.compose.ui:ui-test-junit4:1.11.2")
    testImplementation("androidx.test:core-ktx:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.11.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.11.2")
}
