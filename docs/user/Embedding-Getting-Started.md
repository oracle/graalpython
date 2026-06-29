# Embedding Python in Java

This guide shows you how to embed Python code directly in Java applications using GraalPy.
You can use GraalPy with any JDK (GraalVM JDK, Oracle JDK, or OpenJDK).

GraalPy provides dedicated Maven and Gradle plugins that handle all the complexity for you.
If you are using other build systems (Ant, Make, CMake), manual configuration is required.

## Maven Quick Start

The fastest way to get started is with GraalPy's Maven archetype, which generates a complete starter project for you.

1. Generate a new project using the GraalPy Maven archetype:

   ```bash
   mvn archetype:generate \
     -DarchetypeGroupId=org.graalvm.python \
     -DarchetypeArtifactId=graalpy-archetype-polyglot-app \
     -DarchetypeVersion=25.1.3
   ```

   This generates the following project structure:

   ```bash
   └── polyglot-app
       ├── pom.xml
       ├── src
       │   └── main
       │       ├── java
       │       │   └── com
       │       │       └── example
       │       │           └── Main.java
       │       └── resources
       └── target/
   ```

2. Build a native executable using the [GraalVM `native-image` tool](https://www.graalvm.org/latest/reference-manual/native-image/) that was added for you automatically:

    ```bash
    mvn -Pnative package
    ```

3. Once completed, run the executable:

    ```bash
    ./target/polyglot_app
    ```

    You should see "hello java" printed to the console.

The generated project includes everything you need: the [GraalVM Polyglot API](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/package-summary.html) for Python execution, Python virtual environment management, and examples showing how to integrate Python packages.
The generated _pom.xml_ and Java code are well-documented with explanations of all features.

For advanced plugin configuration, deployment options, and dependency management, see the [Embedding Build Tools](Embedding-Build-Tools.md) guide.

### Cross-Platform Distribution

For creating cross-platform JARs with native Python packages, see the [Virtual Filesystem deployment section](Embedding-Build-Tools.md#virtual-filesystem) in the Build Tools guide.

## Gradle Quick Start

If you prefer Gradle, here is how to set up a new project with GraalPy embedding:

1. Create a new Java application with Gradle:

    ```bash
    gradle init --type java-application \
                --project-name interop  \
                --package interop \
                --no-split-project
    ```

    This generates the following project structure:

    ```bash
    └── app
        ├── build.gradle
        └── src
            └── main
                ├── java
                │   └── interop
                │       └── App.java
                └── resources
    ```

2. Add GraalPy dependencies to your _app/build.gradle_ file.
    - Include the GraalPy support and the [GraalVM Polyglot API](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/package-summary.html) in the `dependencies` section:

        ```gradle
        implementation("org.graalvm.polyglot:polyglot:25.1.3")
        implementation("org.graalvm.python:python-embedding:25.1.3")
        ```

3. Replace the _App.java_ content with this simple Python embedding example:

    ```java
    package interop;

    import org.graalvm.polyglot.*;
    import org.graalvm.python.embedding.GraalPyResources;

    class App {
        public static void main(String[] args) {
            try (var context = GraalPyResources.createContext()) {
                System.out.println(context.eval("python", "'Hello Python!'").asString());
            }
        }
    }
    ```

4. Run the application with Gradle:

    ```bash
    ./gradlew run
    ```

    The application prints "Hello Python!" to the console.

    > **Note**: GraalPy's performance depends on the JDK you are using. For optimal performance, see the [Runtime Optimization Support](https://www.graalvm.org/latest/reference-manual/embed-languages/#runtime-optimization-support) guide.

## Adding Python Dependencies

To use third-party Python packages like NumPy or Requests in your embedded application:

Configure the GraalPy Maven or Gradle plugin with the packages your application needs.
The plugin installs packages into a managed virtual environment, and `GraalPyResources` configures the Python context to import from it.

### Maven

Add the Python embedding dependency and GraalPy Maven plugin configuration to your _pom.xml_ file:

```xml
<dependencies>
    <dependency>
        <groupId>org.graalvm.python</groupId>
        <artifactId>python-embedding</artifactId>
        <version>25.1.3</version>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.graalvm.python</groupId>
            <artifactId>graalpy-maven-plugin</artifactId>
            <version>25.1.3</version>
            <executions>
                <execution>
                    <configuration>
                        <packages>
                            <package>termcolor==2.2</package>
                        </packages>
                    </configuration>
                    <goals>
                        <goal>process-graalpy-resources</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

### Gradle

Add the GraalPy Gradle plugin and configure dependencies in _app/build.gradle_:

```gradle
plugins {
    id "java"
    id "application"
    id "org.graalvm.python" version "25.1.3"
}

dependencies {
    implementation("org.graalvm.python:python-embedding:25.1.3")
}

graalPy {
    packages = ["termcolor==2.2"]
}
```

Then use the Python package from Java:

```java
package interop;

import org.graalvm.polyglot.*;
import org.graalvm.python.embedding.GraalPyResources;

class App {
    public static void main(String[] args) {
        try (Context context = GraalPyResources.contextBuilder().build()) {
            String src = """
            from termcolor import colored
            colored_text = colored("hello java", "red", attrs=["reverse", "blink"])
            print(colored_text)
            """;
            context.eval("python", src);
        }
    }
}
```

For complete plugin configuration options, deployment strategies, and dependency management, see [Embedding Build Tools](Embedding-Build-Tools.md).

### Supporting Legacy Private Keys

BouncyCastle is optional for GraalPy embeddings. Add the following dependency only if the application must load legacy version 0 or 1 RSA, DSA, or EC private keys:

```xml
<dependency>
    <groupId>org.graalvm.python</groupId>
    <artifactId>python-bouncycastle-support</artifactId>
    <version>25.1.3</version>
</dependency>
```

For Gradle, use `implementation("org.graalvm.python:python-bouncycastle-support:25.1.3")`.

### Configuring Individual Sources

Embeddings can set options on an individual `Source` without changing the entire context. Set `python.Optimize` to `0`, `1`, or `2` to select the optimization level for a source. Set `python.NewGlobals=true` to execute a source with a fresh globals dictionary instead of the main module globals.

## Other Build Systems (Ant, CMake, Makefile)

If you are using build systems like Ant, Makefiles, or CMake that do not directly support Maven dependencies, you can still use GraalPy.
Projects like [Apache Ivy&trade;](https://ant.apache.org/ivy/history/master/tutorial/start.html) can resolve Maven dependencies for these systems, you might prefer a simpler approach.
GraalPy provides a tool to download the required JAR files directly.

### Manual Setup

1. Set up your project structure with a directory for dependencies:

    ```bash
    ├── lib/           # JAR dependencies
    │   └── *.jar
    └── src/           # Your Java source files
        └── *.java
    ```

2. Download GraalPy dependencies using the bundled tool:

   First, [install GraalPy](Standalone-Getting-Started.md#installation) and ensure `graalpy` is in your PATH.

   Then run the appropriate command for your system:

    **On Linux/macOS:**

    ```bash
    export GRAALPY_HOME=$(graalpy -c 'print(__graalpython__.home)')
    export GRAALPY_VERSION=$(graalpy -c 'import __graalpython__; print(__graalpython__.version)')
    "${GRAALPY_HOME}/libexec/graalpy-polyglot-get" -a python -o lib -v "${GRAALPY_VERSION}"
    ```

    **On Windows (PowerShell):**

    ```powershell
    $GRAALPY_HOME = graalpy -c "print(__graalpython__.home)"
    $GRAALPY_VERSION = graalpy -c "import __graalpython__; print(__graalpython__.version)"
    & "$GRAALPY_HOME/libexec/graalpy-polyglot-get" -a python -o lib -v "$GRAALPY_VERSION"
    ```

    This downloads all required GraalPy JARs into your _lib_ directory.

3. Write your embedding code:

    ```java
    import org.graalvm.polyglot.*;

    public class Hello {
        public static void main(String[] args) {
            try (var context = Context.newBuilder()
                    .option("engine.WarnInterpreterOnly", "false")
                    .build()) {
                System.out.println(context.eval("python", "'Hello Python!'").asString());
            }
        }
    }
    ```

   Make sure your build system includes all JARs from the _lib_ directory in the classpath.
