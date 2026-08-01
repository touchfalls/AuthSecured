plugins {
    java
    id("com.gradleup.shadow") version "8.3.0"
}

dependencies {
    implementation(project(":core"))
    implementation(project(":database"))

    compileOnly("net.fabricmc:fabric-loader:0.15.11")
    compileOnly(fileTree("../lib") { include("*.jar") })
}

tasks.shadowJar {
    archiveBaseName.set("authsecured-fabric")
    archiveClassifier.set("")
    archiveVersion.set("1.0.3")

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
