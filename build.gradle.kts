import org.gradle.internal.os.OperatingSystem
import java.util.Properties

val appProperties = Properties().apply {
    file("app.properties").inputStream().use { load(it) }
}

val bootMain = "boot.StarRodBootstrap"
val appMain = "app.StarRodMain"
val appVersion = appProperties.getProperty("version")

repositories {
    mavenCentral()
}

plugins {
    id("java")
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("net.nemerosa.versioning") version "3.1.0"
    id("com.cmgapps.licenses") version "4.8.0"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation(platform("org.lwjgl:lwjgl-bom:3.3.3"))
    
    implementation("org.lwjgl", "lwjgl")
    implementation("org.lwjgl", "lwjgl-opengl")
    implementation("org.lwjgl", "lwjgl-jawt")
    implementation("org.lwjgl", "lwjgl-glfw")
    implementation("org.lwjgl", "lwjgl-tinyfd")
    implementation("org.lwjgl", "lwjgl-assimp")
            
    runtimeOnly("org.lwjgl:lwjgl::natives-windows")
    runtimeOnly("org.lwjgl:lwjgl::natives-windows-arm64")
    runtimeOnly("org.lwjgl:lwjgl::natives-macos")
    runtimeOnly("org.lwjgl:lwjgl::natives-macos-arm64")
    runtimeOnly("org.lwjgl:lwjgl::natives-linux")
    runtimeOnly("org.lwjgl:lwjgl::natives-linux-arm64")
    
    runtimeOnly("org.lwjgl:lwjgl-opengl::natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-opengl::natives-windows-arm64")
    runtimeOnly("org.lwjgl:lwjgl-opengl::natives-macos")
    runtimeOnly("org.lwjgl:lwjgl-opengl::natives-macos-arm64")
    runtimeOnly("org.lwjgl:lwjgl-opengl::natives-linux")
    runtimeOnly("org.lwjgl:lwjgl-opengl::natives-linux-arm64")
    
    runtimeOnly("org.lwjgl:lwjgl-glfw::natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-glfw::natives-windows-arm64")
    runtimeOnly("org.lwjgl:lwjgl-glfw::natives-macos")
    runtimeOnly("org.lwjgl:lwjgl-glfw::natives-macos-arm64")
    runtimeOnly("org.lwjgl:lwjgl-glfw::natives-linux")
    runtimeOnly("org.lwjgl:lwjgl-glfw::natives-linux-arm64")

    runtimeOnly("org.lwjgl:lwjgl-tinyfd::natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-tinyfd::natives-windows-arm64")
    runtimeOnly("org.lwjgl:lwjgl-tinyfd::natives-macos")
    runtimeOnly("org.lwjgl:lwjgl-tinyfd::natives-macos-arm64")
    runtimeOnly("org.lwjgl:lwjgl-tinyfd::natives-linux")
    runtimeOnly("org.lwjgl:lwjgl-tinyfd::natives-linux-arm64")
    
    runtimeOnly("org.lwjgl:lwjgl-assimp::natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-assimp::natives-windows-arm64")
    runtimeOnly("org.lwjgl:lwjgl-assimp::natives-macos")
    runtimeOnly("org.lwjgl:lwjgl-assimp::natives-macos-arm64")
    runtimeOnly("org.lwjgl:lwjgl-assimp::natives-linux")
    runtimeOnly("org.lwjgl:lwjgl-assimp::natives-linux-arm64")
    
    implementation("org.lwjglx:lwjgl3-awt:0.1.8")
    
    implementation("commons-io:commons-io:2.16.1")
    implementation("org.apache.commons:commons-text:1.12.0")
    implementation("org.apache.commons:commons-lang3:3.14.0")
    
    implementation("com.miglayout:miglayout-core:11.3")
    implementation("com.miglayout:miglayout-swing:11.3")
    
    implementation("com.alexandriasoftware.swing:jsplitbutton:1.3.1")
    implementation("com.alexdupre:pngj:2.1.2.1")
    
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.yaml:snakeyaml:2.2")
        
    implementation("com.formdev:flatlaf:3.4.1")
    implementation("com.formdev:flatlaf-intellij-themes:3.4.1")
    
    implementation(files("lib/org.eclipse.cdt.core-5.11.0.jar"))
    implementation(files("lib/org.eclipse.equinox.common-3.6.0.jar"))
    
    implementation("org.ahocorasick:ahocorasick:0.6.3")
}


val bootBuildDir = layout.buildDirectory.dir("bootstrap")
val appBuildDir = layout.buildDirectory.dir("app")
val licenseBuildDir = layout.buildDirectory.dir("reports/licenses/licenseReport")
val releaseBuildDir = layout.buildDirectory.dir("release")

tasks {
    val compileBoot = register<JavaCompile>("compileBoot") {
        source = fileTree("src/bootstrap/java")
        destinationDirectory.set(bootBuildDir.get().asFile)
        classpath = files() // use empty classpath since there are no dependencies
        options.release.set(8)
    }
    
    val jarBoot = register<Jar>("jarBoot") {
        dependsOn(compileBoot)
        archiveBaseName.set("boot")
        manifest {
            attributes["Main-Class"] = bootMain
        }
        from(bootBuildDir)
    }
    
    val compileApp = register<JavaCompile>("compileApp") {
        source = fileTree("src/main/java")
        destinationDirectory.set(appBuildDir.get().asFile)
        classpath = sourceSets.main.get().compileClasspath // use the default classpath
        options.release.set(17)
        options.compilerArgs.add("-Xlint:deprecation")
    }
    
    val jarApp = register<Jar>("jarApp") {
        dependsOn(compileApp)
        archiveBaseName.set("main-app")
        manifest {
            attributes["Main-Class"] = appMain
        }
        from(appBuildDir)
    }

    shadowJar {
        dependsOn(jarBoot, jarApp)
        
        from(bootBuildDir, appBuildDir)
        
        mergeServiceFiles("META-INF/spring.*")
        exclude("META-INF/*.SF")
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.RSA")
        exclude("META-INF/LICENSE")
        archiveFileName.set("StarRod.jar")
        
        manifest {
            attributes["Main-Class"] = bootMain
            attributes["App-Version"] = appVersion
            attributes["Build-Branch"] = versioning.info.branchId
            attributes["Build-Commit"] = versioning.info.commit
            if (versioning.info.tag != null)
                attributes["Build-Tag"] = versioning.info.tag
        }
    }

    register<Zip>("createReleaseZip") {
        dependsOn(clean, licenseReport, shadowJar)
        
        group = "release"
        description = "Create zip file for Star Rod release"

        from(shadowJar.get().outputs.files)

        from(file("database")) {
            into("database")
        }
        
        from(file(licenseBuildDir)) {
            into("database/licenses")
        }
        
        from(file("exec")) {
            into("")
        }
        
        archiveFileName.set("StarRod.zip")

        destinationDirectory.set(releaseBuildDir)
    }
}
