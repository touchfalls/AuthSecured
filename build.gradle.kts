plugins {
    java
    id("com.gradleup.shadow") version "8.3.0"
}

group = "com.example.authsecured"
version = property("pluginVersion") as String

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://oss.sonatype.org/content/groups/public/")
}

val javaVersion = (property("javaVersion") as String).toInt()

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(javaVersion))
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:${property("paperApiVersion")}")
    testImplementation("io.papermc.paper:paper-api:${property("paperApiVersion")}")
    compileOnly(fileTree("lib") { include("*.jar") })
    testImplementation(fileTree("lib") { include("*.jar") })

    // Security & Password Hashing
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")

    // Database & Connection Pooling
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.postgresql:postgresql:42.7.3")
    implementation("org.xerial:sqlite-jdbc:3.45.2.0")

    // Flyway Database Migrations
    implementation("org.flywaydb:flyway-core:10.10.0")
    implementation("org.flywaydb:flyway-database-postgresql:10.10.0")

    // Redis Client
    implementation("redis.clients:jedis:5.1.2")

    // YAML Parser
    implementation("org.yaml:snakeyaml:2.2")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.11.0")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(javaVersion)
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveClassifier.set("")
    relocate("org.bouncycastle", "com.example.authsecured.libs.bouncycastle")
    relocate("com.zaxxer.hikari", "com.example.authsecured.libs.hikari")
    relocate("org.postgresql", "com.example.authsecured.libs.postgresql")
    relocate("org.sqlite", "com.example.authsecured.libs.sqlite")
    relocate("org.flywaydb", "com.example.authsecured.libs.flyway")
    relocate("redis.clients.jedis", "com.example.authsecured.libs.jedis")
    relocate("org.yaml.snakeyaml", "com.example.authsecured.libs.snakeyaml")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
