#!/bin/bash
set -e
mkdir -p lib

urls=(
    "https://repo1.maven.org/maven2/org/bouncycastle/bcprov-jdk18on/1.78.1/bcprov-jdk18on-1.78.1.jar"
    "https://repo1.maven.org/maven2/com/zaxxer/HikariCP/5.1.0/HikariCP-5.1.0.jar"
    "https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.3/postgresql-42.7.3.jar"
    "https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.45.2.0/sqlite-jdbc-3.45.2.0.jar"
    "https://repo1.maven.org/maven2/org/flywaydb/flyway-core/10.10.0/flyway-core-10.10.0.jar"
    "https://repo1.maven.org/maven2/org/flywaydb/flyway-database-postgresql/10.10.0/flyway-database-postgresql-10.10.0.jar"
    "https://repo1.maven.org/maven2/redis/clients/jedis/5.1.2/jedis-5.1.2.jar"
    "https://repo1.maven.org/maven2/org/apache/commons/commons-pool2/2.12.0/commons-pool2-2.12.0.jar"
    "https://repo1.maven.org/maven2/org/yaml/snakeyaml/2.2/snakeyaml-2.2.jar"
    "https://repo1.maven.org/maven2/org/slf4j/slf4j-api/2.0.12/slf4j-api-2.0.12.jar"
    "https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/1.20.4-R0.1-SNAPSHOT/paper-api-1.20.4-R0.1-20241030.192207-176.jar"
    "https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/1.10.2/junit-platform-console-standalone-1.10.2.jar"
    "https://repo1.maven.org/maven2/org/mockito/mockito-core/5.11.0/mockito-core-5.11.0.jar"
    "https://repo1.maven.org/maven2/org/mockito/mockito-junit-jupiter/5.11.0/mockito-junit-jupiter-5.11.0.jar"
    "https://repo1.maven.org/maven2/net/bytebuddy/byte-buddy/1.14.12/byte-buddy-1.14.12.jar"
    "https://repo1.maven.org/maven2/net/bytebuddy/byte-buddy-agent/1.14.12/byte-buddy-agent-1.14.12.jar"
    "https://repo1.maven.org/maven2/net/md-5/bungeecord-chat/1.20-R0.2/bungeecord-chat-1.20-R0.2.jar"
    "https://repo1.maven.org/maven2/net/kyori/adventure-api/4.16.0/adventure-api-4.16.0.jar"
    "https://repo1.maven.org/maven2/net/kyori/examination-api/1.3.0/examination-api-1.3.0.jar"
    "https://repo1.maven.org/maven2/net/kyori/examination-string/1.3.0/examination-string-1.3.0.jar"
    "https://repo1.maven.org/maven2/com/google/guava/guava/32.1.2-jre/guava-32.1.2-jre.jar"
    "https://repo1.maven.org/maven2/org/jetbrains/annotations/24.1.0/annotations-24.1.0.jar"
    "https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.15.11/fabric-loader-0.15.11.jar"
)

for url in "${urls[@]}"; do
    filename=$(basename "$url")
    if [ ! -f "lib/$filename" ]; then
        echo "Downloading $filename..."
        curl -sL "$url" -o "lib/$filename"
    fi
done

echo "All dependencies downloaded successfully."
