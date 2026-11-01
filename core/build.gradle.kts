plugins {
    java
}

dependencies {
    implementation("org.bouncycastle:bcprov-jdk18on:1.86")
    implementation("org.yaml:snakeyaml:2.7")

    testImplementation("org.junit.jupiter:junit-jupiter-api:6.1.3")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:6.1.3")
    testImplementation("org.mockito:mockito-core:5.24.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.24.0")
}
