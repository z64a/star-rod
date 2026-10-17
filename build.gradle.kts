import com.diffplug.gradle.spotless.SpotlessExtension
import java.util.Locale
import java.util.Properties

val appProperties = Properties().apply {
    file("app.properties").inputStream().use { load(it) }
}

val appMain = "app.StarRodMain"
val appVersion = requireNotNull(appProperties.getProperty("version")) {
    "app.properties must define a version"
}
val targetJavaVersion = 17

repositories {
    mavenCentral()
}

plugins {
    id("java")
    id("com.diffplug.spotless") version "8.8.0"
    id("com.gradleup.shadow") version "8.3.11"
    id("net.nemerosa.versioning") version "3.1.0"
    id("com.cmgapps.licenses") version "4.8.0"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(targetJavaVersion))
    }
}

configure<SpotlessExtension> {
    isEnforceCheck = false

    java {
        target("src/**/*.java")
        targetExclude("src/main/java/analysis/**/*.java")
        importOrder("\\#", "java", "javax", "org", "com", "")
        removeUnusedImports()
        eclipse("4.38").configFile(".settings/org.eclipse.jdt.core.prefs")
    }
}

tasks.register("formatJava") {
    group = "formatting"
    description = "Format and organize all Java sources under src using the project Eclipse settings"
    dependsOn("spotlessJavaApply")
}

tasks.compileJava {
    options.release.set(targetJavaVersion)
    options.compilerArgs.add("-Xlint:deprecation")
}

val operatingSystem = System.getProperty("os.name")
val osName = when {
    operatingSystem.startsWith("Windows", ignoreCase = true) -> "windows"
    operatingSystem.startsWith("Mac", ignoreCase = true) ||
        operatingSystem.startsWith("Darwin", ignoreCase = true) -> "macos"
    operatingSystem.startsWith("Linux", ignoreCase = true) -> "linux"
    else -> throw GradleException("Releases are not supported on $operatingSystem")
}
val isWindows = osName == "windows"

val architecture = System.getProperty("os.arch").lowercase(Locale.ROOT)
val archName = when (architecture) {
    "x86_64", "amd64" -> "x64"
    "aarch64", "arm64" -> "arm64"
    else -> throw GradleException("Releases are not supported on $architecture")
}

val lwjglNatives = "natives-$osName${if (archName == "arm64") "-arm64" else ""}"
val platformName = "$osName-$archName"

dependencies {
    implementation(platform("org.lwjgl:lwjgl-bom:3.3.3"))
    
    implementation("org.lwjgl:lwjgl")
    implementation("org.lwjgl:lwjgl-opengl")
    implementation("org.lwjgl:lwjgl-jawt")
    implementation("org.lwjgl:lwjgl-glfw")
    implementation("org.lwjgl:lwjgl-tinyfd")
    implementation("org.lwjgl:lwjgl-assimp")
            
    runtimeOnly("org.lwjgl:lwjgl::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-opengl::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-glfw::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-tinyfd::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-assimp::$lwjglNatives")
    
    implementation("org.lwjglx:lwjgl3-awt:0.2.2") {
        exclude(group = "org.lwjgl") // https://github.com/LWJGLX/lwjgl3-awt/issues/74
    }
    
    implementation("commons-io:commons-io:2.16.1")
    implementation("org.apache.commons:commons-text:1.12.0")
    implementation("org.apache.commons:commons-lang3:3.14.0")
    
    implementation("com.miglayout:miglayout-core:11.3")
    implementation("com.miglayout:miglayout-swing:11.3")
    
    implementation("com.alexandriasoftware.swing:jsplitbutton:1.3.1")
    implementation("com.alexdupre:pngj:2.1.2.1")
    
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.yaml:snakeyaml:2.2")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
        
    implementation("com.formdev:flatlaf:3.4.1")
    implementation("com.formdev:flatlaf-intellij-themes:3.4.1")
    implementation("com.formdev:flatlaf-extras:3.4.1")
    
    implementation(files("lib/org.eclipse.cdt.core-5.11.0.jar"))
    implementation(files("lib/org.eclipse.equinox.common-3.6.0.jar"))
    
    implementation("org.ahocorasick:ahocorasick:0.6.3")
}

tasks.test {
    useJUnitPlatform()
}


val licenseBuildDir = layout.buildDirectory.dir("reports/licenses/licenseReport")
val releaseBuildDir = layout.buildDirectory.dir("release")
val runtimeBuildDir = layout.buildDirectory.dir("runtime/$platformName")
val javaCompiler = javaToolchains.compilerFor(java.toolchain)

val cleanRuntime by tasks.registering(Delete::class) {
    delete(runtimeBuildDir)
}

val createRuntime by tasks.registering(Exec::class) {
    dependsOn(cleanRuntime)

    val jlink = javaCompiler.map {
        it.metadata.installationPath.file("bin/${if (isWindows) "jlink.exe" else "jlink"}")
    }

    args(
        "--add-modules",
        // java.desktop includes Swing/AWT, image I/O, sound, XML and preferences.
        "java.desktop,java.logging,java.management,java.naming,java.scripting,java.sql,jdk.crypto.ec,jdk.unsupported",
        "--strip-debug",
        "--no-header-files",
        "--no-man-pages",
        "--compress=2",
        "--output",
        runtimeBuildDir.get().asFile.absolutePath
    )

    doFirst {
        executable(jlink.get().asFile)
    }
}

tasks {
    shadowJar {
        mergeServiceFiles()
        exclude("META-INF/*.SF")
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.RSA")
        exclude("META-INF/LICENSE")
        exclude("META-INF/LICENSE.txt")
        exclude("META-INF/NOTICE")
        exclude("META-INF/NOTICE.txt")
        exclude("LICENSE.txt")
        exclude("NOTICE.txt")
        archiveFileName.set("StarRod.jar")
        
        manifest {
            attributes["Main-Class"] = appMain
            attributes["App-Version"] = appVersion
            attributes["Build-Branch"] = versioning.info.branchId
            // a build without git history, such as a Nix build, passes -PbuildCommit instead
            attributes["Build-Commit"] = (findProperty("buildCommit") as String?) ?: versioning.info.commit
            if (versioning.info.tag != null)
                attributes["Build-Tag"] = versioning.info.tag
        }
    }

    register<Zip>("createReleaseZip") {
        dependsOn(licenseReport, shadowJar, createRuntime)
        
        group = "release"
        description = "Create a Star Rod release for $platformName"

        from(shadowJar)

        from(runtimeBuildDir) {
            into("runtime")
        }
        
        from(file(licenseBuildDir)) {
            into("licenses")
        }

        from(file("LICENSE"))
        
        from(file("exec")) {
            include(if (isWindows) "StarRod.bat" else "StarRod")
        }

        if (!isWindows) {
            eachFile {
                if (name == "StarRod" || path.startsWith("runtime/bin/") ||
                    path == "runtime/lib/jspawnhelper" || path == "runtime/lib/jexec")
                    permissions { unix("755") }
            }
        }
        
        val versionTag = versioning.info.tag?.takeIf { it.startsWith("v") }
        val commitHash = versioning.info.build

        if (versionTag != null) {
            require(versionTag == "v$appVersion") {
                "Release tag $versionTag does not match app version $appVersion"
            }
        }

        archiveFileName.set(
            if (versionTag != null) {
                "StarRod-$appVersion-$platformName.zip"
            } else {
                "StarRod-$appVersion-$commitHash-$platformName.zip"
            }
        )

        destinationDirectory.set(releaseBuildDir)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}
