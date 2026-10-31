import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties
import org.jetbrains.kotlin.cli.common.toBooleanLenient

plugins {
	id("com.android.application")
	kotlin("android")
	alias(libs.plugins.kotlin.ksp)
	alias(libs.plugins.kotlin.serialization)
	alias(libs.plugins.aboutlibraries)
}

android {
	namespace = "org.jellyfin.androidtv"
	compileSdk = gradleLocalProperties(rootDir).getProperty("COMPILE_SDK_NR", "34").toInt()
	ndkVersion = "26.1.10909125"

	defaultConfig {
		minSdk = gradleLocalProperties(rootDir).getProperty("MIN_SDK_NR", "23").toInt()
		targetSdk = gradleLocalProperties(rootDir).getProperty("TARGET_SDK_NR", "32").toInt()

		// Release version
		applicationId = "org.jellyfin.androidtv.zidoo"
		versionName = project.getVersionName()
		versionCode = getVersionCode(versionName!!)
		setProperty("archivesBaseName", "jellyfin-androidtv-v$versionName")

		ndk.abiFilters.addAll(setOf("armeabi-v7a"))
	}

	sourceSets["main"].java.srcDirs("src/main/kotlin")
	sourceSets["test"].java.srcDirs("src/test/kotlin")

	buildFeatures {
		viewBinding = gradleLocalProperties(rootDir).getProperty("BUILD_VIEW_BINDING", "true").toBooleanLenient()
		dataBinding = gradleLocalProperties(rootDir).getProperty("BUILD_DATA_BINDING", "false").toBooleanLenient()
		mlModelBinding = gradleLocalProperties(rootDir).getProperty("BUILD_MODEL_BINDING", "false").toBooleanLenient()
	}

	compileOptions {
		isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.valueOf(gradleLocalProperties(rootDir).getProperty("JAVA_VERSION", "VERSION_17"))
        targetCompatibility = JavaVersion.valueOf(gradleLocalProperties(rootDir).getProperty("JAVA_VERSION", "VERSION_17"))
    }

	kotlinOptions {
		jvmTarget = gradleLocalProperties(rootDir).getProperty("KOTLIN_JVM_TARGET", "17")
	}

	kotlin {
		jvmToolchain {
			(this as JavaToolchainSpec).languageVersion.set(JavaLanguageVersion.of(gradleLocalProperties(rootDir).getProperty("JAVA_VERSION_NR", "17")))
		}
	}

//	bundle {
//		abi {
//			enableSplit = true
//		}
//	}

	signingConfigs {
		create("release") {
			storeFile = gradleLocalProperties(rootDir).getProperty("STOREFILE")?.let { file(it) }
			keyAlias = gradleLocalProperties(rootDir).getProperty("KEYALIAS")
			keyPassword = gradleLocalProperties(rootDir).getProperty("KEYPASSWORD")
			storePassword = gradleLocalProperties(rootDir).getProperty("STOREPASSWORD")
		}
	}

	buildTypes {
		getByName("release") {
			matchingFallbacks += listOf()
			signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
		}

		val release by getting {
			isMinifyEnabled = false
			isShrinkResources = false
//			isJniDebuggable = false
			// Set package names used in various XML files
			resValue("string", "app_id", defaultConfig.applicationId!!)
			resValue("string", "app_search_suggest_authority", "${defaultConfig.applicationId}.content")
			resValue("string", "app_search_suggest_intent_data", "content://${defaultConfig.applicationId}.content/intent")

			// Set flavored application name
			resValue("string", "app_name", "Jellyfin Zidoo")

			buildConfigField("boolean", "DEVELOPMENT", "false")
		}

		val debug by getting {
			// Use different application id to run release and debug at the same time
			applicationIdSuffix = ".debug"

			// Set package names used in various XML files
			resValue("string", "app_id", defaultConfig.applicationId + applicationIdSuffix)
			resValue("string", "app_search_suggest_authority", "${defaultConfig.applicationId + applicationIdSuffix}.content")
			resValue("string", "app_search_suggest_intent_data", "content://${defaultConfig.applicationId + applicationIdSuffix}.content/intent")

			// Set flavored application name
			resValue("string", "app_name", "Jellyfin Zidoo Debug")

			buildConfigField("boolean", "DEVELOPMENT", (defaultConfig.versionCode!! < 100).toString())
		}
	}

	lint {
		lintConfig = file("$rootDir/android-lint.xml")
		abortOnError = false
		sarifReport = true
		checkDependencies = true
	}

	testOptions.unitTests.all {
		it.useJUnitPlatform()
	}
}

aboutLibraries {
	// Remove the "generated" timestamp to allow for reproducible builds
	excludeFields = arrayOf("generated")
}

val versionTxt by tasks.registering {
	val path = buildDir.resolve("version.txt")

	doLast {
		val versionString = "v${android.defaultConfig.versionName}=${android.defaultConfig.versionCode}"
		logger.info("Writing [$versionString] to $path")
		path.writeText("$versionString\n")
	}
}

dependencies {
	// Jellyfin
	implementation(projects.playback)
	implementation(projects.preference)
	implementation(libs.jellyfin.apiclient)
	implementation(libs.jellyfin.sdk) {
		// Change version if desired
		val sdkVersion = findProperty("sdk.version")?.toString()
		when (sdkVersion) {
			"local" -> version { strictly("latest-SNAPSHOT") }
			"snapshot" -> version { strictly("master-SNAPSHOT") }
			"unstable-snapshot" -> version { strictly("openapi-unstable-SNAPSHOT") }
		}
	}

	// Kotlin
	implementation(libs.kotlinx.coroutines)
	implementation(libs.kotlinx.serialization.json)

	// Android(x)
	implementation(libs.androidx.core)
	implementation(libs.androidx.activity)
	implementation(libs.androidx.fragment)
	implementation(libs.androidx.leanback.core)
	implementation(libs.androidx.leanback.preference)
	implementation(libs.androidx.preference)
	implementation(libs.androidx.appcompat)
	implementation(libs.androidx.tvprovider)
	implementation(libs.androidx.constraintlayout)
	implementation(libs.androidx.recyclerview)
	implementation(libs.androidx.work.runtime)
	implementation(libs.bundles.androidx.lifecycle)
	implementation(libs.androidx.window)
	implementation(libs.androidx.cardview)
	implementation(libs.androidx.startup)
	implementation(libs.androidx.compose.ui)

	// Dependency Injection
	implementation(libs.bundles.koin)

	// GSON
	implementation(libs.gson)

	// Media players
	implementation(libs.exoplayer)
	implementation(libs.jellyfin.exoplayer.ffmpegextension)
	implementation(libs.libvlc)

	// Apfloat
	implementation(libs.apfloat)

	// kotlin-extensions
	implementation(libs.carleslc)

	// Markdown
	implementation(libs.bundles.markwon)

	// Image utility
	implementation(libs.blurhash)
	implementation(libs.glide.core)
	ksp(libs.glide.ksp)
	implementation(libs.kenburnsview)

	// Crash Reporting
	implementation(libs.bundles.acra)

	// Licenses
	implementation(libs.aboutlibraries)

	// Logging
	implementation(libs.timber)
	implementation(libs.slf4j.timber)

	// Debugging
	if (getProperty("leakcanary.enable")?.toBoolean() == true)
		debugImplementation(libs.leakcanary)

	// Compatibility (desugaring)
	coreLibraryDesugaring(libs.android.desugar)

	// Testing
	testImplementation(libs.kotest.runner.junit5)
	testImplementation(libs.kotest.assertions)
	testImplementation(libs.mockk)
}
