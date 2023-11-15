import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

val minSdk: String by project
val targetSdk: String by project

plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

android {
    compileSdk = (findProperty("android.compileSdk") as String).toInt()
    namespace = "com.zerodea.bluekt"

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")

    defaultConfig {
        minSdk = (findProperty("android.minSdk") as String).toInt()
        targetSdk = (findProperty("android.targetSdk") as String).toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        jvmToolchain(11)
    }
}

val frameworkName = "BlueKt"

kotlin {
    androidTarget {
        publishAllLibraryVariants()
    }
    val xcf = XCFramework(frameworkName)
    ios {
        binaries.framework {
            baseName = frameworkName
            xcf.add(this)
        }
    }
    macosArm64 {
        binaries.framework {
            baseName = frameworkName
            xcf.add(this)
        }
    }
    macosX64 {
        binaries.framework {
            baseName = frameworkName
            xcf.add(this)
        }
    }

    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "1.8"
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }

        val androidMain by getting {
            dependencies {
                api("androidx.appcompat:appcompat:1.6.1")
                api("androidx.core:core-ktx:1.12.0")
            }
        }

        val iosMain by getting {
            dependsOn(commonMain)
        }
        val iosArm64Main by getting
        iosArm64Main.dependsOn(iosMain)
        val iosX64Main by getting
        iosX64Main.dependsOn(iosMain)
        val macosMain by getting {
            dependsOn(commonMain)
        }
        val macosX64Main by getting {
            dependsOn(macosMain)
        }
        val macosArm64Main by getting {
            dependsOn(macosMain)
        }

        val jvmMain by getting {
            dependsOn(commonMain)
            dependencies {
                // TODO this is for windows only. For linux we need to use the GPL library. We will probably do that in
                //  a separate project just for linux so that the GPL library doesn't pollute this codebase
                implementation("net.sf.bluecove:bluecove:2.1.0")
            }
        }
    }
}
