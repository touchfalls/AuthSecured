plugins {
    java
}

dependencies {
    implementation(project(":core"))

    implementation("com.zaxxer:HikariCP:7.1.0")
    implementation("org.postgresql:postgresql:42.7.14")
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")
    implementation("org.flywaydb:flyway-core:13.12.1")
    implementation("org.flywaydb:flyway-database-postgresql:13.12.1")
    implementation("redis.clients:jedis:5.2.0")
    // slf4j-api is not imported directly here; HikariCP and Jedis already pull it in transitively.

    testImplementation("org.junit.jupiter:junit-jupiter-api:6.1.3")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:6.1.3")
    testImplementation("org.mockito:mockito-core:5.24.0")
}
