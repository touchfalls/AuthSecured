import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.file.DuplicatesStrategy

plugins {
    id("com.gradleup.shadow")
}

val minecraftVersion = providers.gradleProperty("minecraft_version").get()
apply(plugin = if (minecraftVersion.startsWith("26.")) {
    "net.fabricmc.fabric-loom"
} else {
    "net.fabricmc.fabric-loom-remap"
})
val fabricApiVersions = mapOf(
    "1.21" to "0.102.0+1.21",
    "1.21.1" to "0.116.15+1.21.1",
    "1.21.2" to "0.106.1+1.21.2",
    "1.21.3" to "0.114.1+1.21.3",
    "1.21.4" to "0.119.4+1.21.4",
    "1.21.5" to "0.128.2+1.21.5",
    "1.21.6" to "0.128.2+1.21.6",
    "1.21.7" to "0.129.0+1.21.7",
    "1.21.8" to "0.136.1+1.21.8",
    "1.21.9" to "0.134.1+1.21.9",
    "1.21.10" to "0.138.4+1.21.10",
    "1.21.11" to "0.141.6+1.21.11",
    "26.1" to "0.145.1+26.1",
    "26.1.1" to "0.145.4+26.1.1",
    "26.1.2" to "0.155.2+26.1.2",
    "26.2" to "0.157.0+26.2",
    "26.3" to "0.164.0+26.3"
)
val fabricApiVersion = fabricApiVersions[minecraftVersion]
    ?: error("Unsupported minecraft_version '$minecraftVersion'. See SUPPORTED_VERSIONS.md.")
val javaRelease = if (minecraftVersion.startsWith("26.")) 25 else 21
val shade by configurations.creating {
    // org.sqlite can't be relocated (JNI), and on Knot our copy wins over every other mod's
    // (Ledger broke with 3.53.x: bogus "ALTER TABLE ... MODIFY COLUMN"). Use the 3.49.1.0 EasyAuth ships (verified with Ledger).
    resolutionStrategy.force("org.xerial:sqlite-jdbc:3.54.1.0")
}

dependencies {
    add("minecraft", "com.mojang:minecraft:$minecraftVersion")
    if (!minecraftVersion.startsWith("26.")) {
        add("mappings", project.extensions.getByType<net.fabricmc.loom.api.LoomGradleExtensionAPI>()
                .officialMojangMappings())
    }
    if (minecraftVersion.startsWith("26.")) {
        implementation("net.fabricmc:fabric-loader:${property("fabric_loader_version")}")
        implementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
    } else {
        add("modImplementation", "net.fabricmc:fabric-loader:${property("fabric_loader_version")}")
        add("modImplementation", "net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
    }

    implementation(project(":core"))
    implementation(project(":database"))
    implementation(project(":platform-common"))

    shade(project(":core"))
    shade(project(":database"))
    shade(project(":platform-common"))

    testImplementation("org.junit.jupiter:junit-jupiter-api:6.1.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.1.3")
    // Runs the tests inside Fabric Loader, so MixinAuditTest can apply the mixins to the real game classes.
    testImplementation("net.fabricmc:fabric-loader-junit:${property("fabric_loader_version")}")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(if (javaRelease == 25) 26 else 21))
}

sourceSets.main {
    java.srcDir(if (minecraftVersion == "1.21" || minecraftVersion == "1.21.1") {
        "src/legacy/java"
    } else {
        "src/modern/java"
    })
    java.srcDir(if (minecraftVersion in setOf("1.21", "1.21.1", "1.21.2", "1.21.3", "1.21.4")) {
        "src/click-legacy/java"
    } else {
        "src/click-modern/java"
    })
    java.srcDir(if (minecraftVersion.startsWith("1.21") && minecraftVersion != "1.21.11") {
        "src/permission-legacy/java"
    } else {
        "src/permission-modern/java"
    })
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(javaRelease)
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft_version", minecraftVersion)
    inputs.property("java_version", javaRelease)
    filesMatching(listOf("fabric.mod.json", "authsecured.mixins.json")) {
        expand(
            "version" to project.version,
            "minecraft_version" to minecraftVersion,
            "java_version" to javaRelease,
            "java_compatibility" to "JAVA_$javaRelease"
        )
    }
}

tasks.named<ShadowJar>("shadowJar") {
    configurations = listOf(shade)
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    archiveBaseName.set("authsecured-fabric")
    archiveVersion.set("${project.version}+mc$minecraftVersion")
    archiveClassifier.set(if (minecraftVersion.startsWith("26.")) "" else "dev-shadow")
    mergeServiceFiles()
    minimize {
        // JDBC drivers, Flyway providers and serializers use reflection or ServiceLoader.
        exclude(dependency("org.xerial:sqlite-jdbc:.*"))
        exclude(dependency("org.postgresql:postgresql:.*"))
        exclude(dependency("org.flywaydb:.*:.*"))
        exclude(dependency("com.zaxxer:HikariCP:.*"))
        exclude(dependency("redis.clients:jedis:.*"))
        exclude(dependency("org.yaml:snakeyaml:.*"))
    }
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    exclude("META-INF/LICENSE*", "META-INF/NOTICE*")
    exclude("org/slf4j/**")
    exclude("org/checkerframework/**") // Compile-time annotations only.

    relocate("org.bouncycastle", "io.github.touchfalls.authsecured.libs.bouncycastle")
    relocate("com.zaxxer.hikari", "io.github.touchfalls.authsecured.libs.hikari")
    relocate("org.postgresql", "io.github.touchfalls.authsecured.libs.postgresql")
    relocate("org.flywaydb", "io.github.touchfalls.authsecured.libs.flyway")
    relocate("redis.clients.jedis", "io.github.touchfalls.authsecured.libs.jedis")
    relocate("org.apache.commons.pool2", "io.github.touchfalls.authsecured.libs.commons.pool2")
    relocate("org.yaml.snakeyaml", "io.github.touchfalls.authsecured.libs.snakeyaml")
    relocate("com.google.gson", "io.github.touchfalls.authsecured.libs.gson")
    relocate("org.json", "io.github.touchfalls.authsecured.libs.json")
    relocate("com.fasterxml.jackson", "io.github.touchfalls.authsecured.libs.jackson")
    // sqlite-jdbc must keep org.sqlite: its JNI bridge and native resource lookup
    // use the original package and break when only bytecode names are relocated.
}

tasks.jar {
    archiveClassifier.set("dev")
}

if (minecraftVersion.startsWith("26.")) {
    tasks.build {
        dependsOn(tasks.shadowJar)
    }
} else {
    tasks.named<net.fabricmc.loom.task.RemapJarTask>("remapJar") {
        dependsOn(tasks.shadowJar)
        inputFile.set(tasks.shadowJar.flatMap { it.archiveFile })
        archiveBaseName.set("authsecured-fabric")
        archiveVersion.set("${project.version}+mc$minecraftVersion")
        archiveClassifier.set("")
    }
}

tasks.test {
    useJUnitPlatform()
    systemProperty("authsecured.minecraftVersion", minecraftVersion)
    systemProperty("fabric.side", "server")
}

val verifySQLiteDriverInShadowJar by tasks.registering {
    dependsOn(tasks.shadowJar)
    doLast {
        val archive = tasks.shadowJar.get().archiveFile.get().asFile
        val nativeDb = zipTree(archive).matching { include("org/sqlite/core/NativeDB.class") }
        check(!nativeDb.isEmpty) { "SQLite NativeDB is missing from ${archive.name}" }
        check(!zipTree(archive).matching {
            include("io/github/touchfalls/authsecured/fabric/AuthSecuredFabricMod.class")
        }.isEmpty) { "Fabric entrypoint is missing from ${archive.name}" }
        check(zipTree(archive).matching {
            include("com/google/gson/**", "org/json/**", "com/fasterxml/jackson/**")
        }.isEmpty) {
            "Unrelocated JSON libraries remain in ${archive.name}"
        }
    }
}

tasks.build {
    dependsOn(verifySQLiteDriverInShadowJar)
}
