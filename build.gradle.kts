import org.gradle.internal.os.OperatingSystem
import java.util.Properties

val appProperties = Properties().apply {
    file("app.properties").inputStream().use { load(it) }
}

val appMain = "app.StarRodMain"
val appVersion = appProperties.getProperty("version")

repositories {
    mavenCentral()
}

plugins {
    id("java")
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("net.nemerosa.versioning") version "2.8.2"
    id("com.jaredsburrows.license") version "0.9.7"
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
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
    runtimeOnly("org.lwjgl:lwjgl::natives-macos")
    runtimeOnly("org.lwjgl:lwjgl::natives-linux")
    
    runtimeOnly("org.lwjgl:lwjgl-opengl::natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-opengl::natives-macos")
    runtimeOnly("org.lwjgl:lwjgl-opengl::natives-linux")
    
    runtimeOnly("org.lwjgl:lwjgl-glfw::natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-glfw::natives-macos")
    runtimeOnly("org.lwjgl:lwjgl-glfw::natives-linux")

    runtimeOnly("org.lwjgl:lwjgl-tinyfd::natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-tinyfd::natives-macos")
    runtimeOnly("org.lwjgl:lwjgl-tinyfd::natives-linux")
    
    runtimeOnly("org.lwjgl:lwjgl-assimp::natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-assimp::natives-macos")
    runtimeOnly("org.lwjgl:lwjgl-assimp::natives-linux")
    
    implementation("org.lwjglx:lwjgl3-awt:0.1.8")
    
    implementation("commons-io:commons-io:2.16.1")
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

tasks {
    shadowJar {
        mergeServiceFiles("META-INF/spring.*")
        exclude("META-INF/*.SF")
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.RSA")
        exclude("META-INF/LICENSE")
        archiveFileName.set("StarRod.jar")
        
        manifest {
            attributes["Main-Class"] = "$appMain"
            attributes["App-Version"] = "$appVersion"
            attributes["Build-Branch"] = versioning.info.branchId
            attributes["Build-Commit"] = versioning.info.commit
            if (versioning.info.tag != null)
                attributes["Build-Tag"] = versioning.info.tag
        }
    }

    register("createReleaseZip", Zip::class) {
        group = "release"
        description = "Create zip file for Star Rod release"

        from(shadowJar.get().outputs.files)

        from(file("database")) {
            into("database")
        }
        
        from(file(layout.buildDirectory.dir("reports"))) {
            into("database")
        }
        
        from(file("exec")) {
            into("")
        }
        
        val versionTag = versioning.info.tag
        val commitHash = versioning.info.build

        archiveFileName.set(
            if (versionTag != null && versionTag.startsWith("v")) {
                "StarRod-$appVersion.zip"
            } else {
                "StarRod-$appVersion-$commitHash.zip"
            }
        )

        destinationDirectory.set(layout.buildDirectory.dir("release"))
    }

    named("createReleaseZip") {
        dependsOn("clean")
        dependsOn("shadowJar")
        dependsOn("licenseReport")
    }
}
