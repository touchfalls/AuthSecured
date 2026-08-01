#!/bin/bash
set -e

JAVAC="/Library/Java/JavaVirtualMachines/jdk-26.jdk/Contents/Home/bin/javac"
JAVA="/Library/Java/JavaVirtualMachines/jdk-26.jdk/Contents/Home/bin/java"
JAR="/Library/Java/JavaVirtualMachines/jdk-26.jdk/Contents/Home/bin/jar"

echo "=== Cleaning build directories ==="
rm -rf build/classes build/test-classes build/tmp build/paper-classes build/libs 2>/dev/null || true
mkdir -p build/libs build/classes build/test-classes build/tmp/paper

echo "=== Compiling Core Module ==="
find core/src/main/java -name "*.java" > build/core_sources.txt
$JAVAC -encoding UTF-8 --release 21 -cp "lib/*" -d build/classes @build/core_sources.txt
if [ -d "core/src/main/resources" ]; then
    cp -r core/src/main/resources/* build/classes/ 2>/dev/null || true
fi

echo "=== Compiling Database Module ==="
find database/src/main/java -name "*.java" > build/db_sources.txt
$JAVAC -encoding UTF-8 --release 21 -cp "build/classes:lib/*" -d build/classes @build/db_sources.txt
if [ -d "database/src/main/resources" ]; then
    cp -r database/src/main/resources/* build/classes/ 2>/dev/null || true
fi

echo "=== Compiling Platform-Paper Module ==="
find platform-paper/src/main/java -name "*.java" > build/paper_sources.txt
mkdir -p build/paper-classes
$JAVAC -encoding UTF-8 --release 21 -cp "build/classes:lib/*" -d build/paper-classes @build/paper_sources.txt
if [ -d "platform-paper/src/main/resources" ]; then
    cp -r platform-paper/src/main/resources/* build/paper-classes/ 2>/dev/null || true
fi

echo "=== Compiling Test Sources ==="
find core/src/test/java platform-paper/src/test/java -name "*.java" 2>/dev/null > build/test_sources.txt
$JAVAC -encoding UTF-8 --release 21 -cp "build/classes:build/paper-classes:lib/*" -d build/test-classes @build/test_sources.txt

echo "=== Running JUnit 5 Unit Tests ==="
$JAVA -Dnet.bytebuddy.experimental=true -XX:+EnableDynamicAgentLoading -javaagent:lib/byte-buddy-agent-1.14.12.jar --add-opens java.base/java.lang=ALL-UNNAMED -cp "build/classes:build/paper-classes:build/test-classes:lib/*" \
    org.junit.platform.console.ConsoleLauncher \
    --scan-classpath \
    --details=tree

echo "=== Building Shaded Paper Plugin Jar ==="
cp -r build/classes/* build/tmp/paper/
cp -r build/paper-classes/* build/tmp/paper/

ROOT_DIR=$(pwd)
for lib in lib/*.jar; do
    basename_lib=$(basename "$lib")
    case "$basename_lib" in
        paper-api*|junit*|mockito*|byte-buddy*|objenesis*)
            echo "Skipping $basename_lib from Paper shadow jar..."
            ;;
        *)
            (cd build/tmp/paper && $JAR xf "$ROOT_DIR/$lib")
            ;;
    esac
done

rm -rf build/tmp/paper/META-INF/*.SF build/tmp/paper/META-INF/*.DSA build/tmp/paper/META-INF/*.RSA 2>/dev/null || true
PAPER_JAR="authsecured-paper-1.0.3.jar"
(cd build/tmp/paper && $JAR cf "../../libs/$PAPER_JAR" .)

echo "=== Build and Test Completed Successfully ==="
ls -lh build/libs/

