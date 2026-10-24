plugins {
    `java-library`
}

dependencies {
    api(project(":core"))
    implementation(project(":database"))
    implementation("redis.clients:jedis:8.0.2")
    testImplementation("org.junit.jupiter:junit-jupiter-api:6.1.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.1.3")
    testImplementation("org.mockito:mockito-core:5.11.0")
}
