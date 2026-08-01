#!/bin/bash
set -e

JAVAC="/Library/Java/JavaVirtualMachines/jdk-26.jdk/Contents/Home/bin/javac"
JAVA="/Library/Java/JavaVirtualMachines/jdk-26.jdk/Contents/Home/bin/java"
JAR="/Library/Java/JavaVirtualMachines/jdk-26.jdk/Contents/Home/bin/jar"

echo "=== Cleaning build directories ==="
rm -rf build/classes build/test-classes build/libs build/tmp
mkdir -p build/classes build/test-classes build/libs build/tmp/shaded

echo "=== Compiling Main Sources (Java 21 target) ==="
find src/main/java -name "*.java" > build/main_sources.txt
$JAVAC -encoding UTF-8 --release 21 -cp "lib/*" -d build/classes @build/main_sources.txt

if [ -d "src/main/resources" ]; then
    cp -r src/main/resources/* build/classes/ 2>/dev/null || true
fi

echo "=== Compiling Test Sources ==="
find src/test/java -name "*.java" > build/test_sources.txt
$JAVAC -encoding UTF-8 --release 21 -cp "build/classes:lib/*" -d build/test-classes @build/test_sources.txt

echo "=== Running JUnit 5 Unit Tests ==="
$JAVA -XX:+EnableDynamicAgentLoading -javaagent:lib/byte-buddy-agent-1.14.12.jar --add-opens java.base/java.lang=ALL-UNNAMED -cp "build/classes:build/test-classes:lib/*" \
    org.junit.platform.console.ConsoleLauncher \
    --scan-classpath \
    --details=tree

echo "=== Building Shaded Plugin Jar ==="
# Copy compiled plugin classes to shaded directory
cp -r build/classes/* build/tmp/shaded/

# Unpack implementation dependencies for shading (excluding paper-api and test libs)
ROOT_DIR=$(pwd)
for lib in lib/*.jar; do
    basename_lib=$(basename "$lib")
    case "$basename_lib" in
        paper-api*|fabric-loader*|junit*|mockito*|byte-buddy*|objenesis*)
            echo "Skipping $basename_lib from shadow jar..."
            ;;
        *)
            echo "Shading $basename_lib..."
            (cd build/tmp/shaded && $JAR xf "$ROOT_DIR/$lib")
            ;;
    esac
done

# Remove META-INF manifest/signature files from unpacked dependencies to avoid invalid signature
rm -rf build/tmp/shaded/META-INF/*.SF build/tmp/shaded/META-INF/*.DSA build/tmp/shaded/META-INF/*.RSA 2>/dev/null || true

# Package into final jar
JAR_NAME="AuthSecured-1.0.2.jar"
(cd build/tmp/shaded && $JAR cf "../../libs/$JAR_NAME" .)

echo "=== Build and Test Completed Successfully ==="
ls -lh "build/libs/$JAR_NAME"
