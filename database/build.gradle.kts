plugins {
    java
}

dependencies {
    implementation(project(":core"))

    implementation("com.zaxxer:HikariCP:6.3.3")
    implementation("org.postgresql:postgresql:42.7.13")
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")
    implementation("org.flywaydb:flyway-core:13.10.3")
    implementation("org.flywaydb:flyway-database-postgresql:13.10.3")
    implementation("redis.clients:jedis:5.2.0")
    // slf4j-api is not imported directly here; HikariCP and Jedis already pull it in transitively.

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testImplementation("org.mockito:mockito-core:5.11.0")
}
