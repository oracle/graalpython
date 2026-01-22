# Getting Started with GraalPy on the JVM

You can use GraalPy with GraalVM JDK, Oracle JDK, or OpenJDK.
To add GraalPy to your Java application, use Maven or Gradle as shown below.
For other build systems (like Ant, Make, CMake, etc.), manual configuration may be required.

### Platform support

GraalPy is mostly written in Java and Python, but the Python package ecosystem is rich in native packages that need platform specific support via native libraries that expose platform-specific APIs.
Our main operating system is Oracle Linux, the CPU architectures we focus on are AMD64 and ARM, and the main JDK we test is Oracle GraalVM.
Windows and macOS with GraalVM JDK are less well tested, and outside of those combinations we target only basic test coverage.
See [below](Test-Tiers.md) for a detailed breakdown.

## Maven

GraalPy can generate a Maven project that embeds Python packages into a Java application using [Maven artefacts](https://central.sonatype.com/namespace/org.graalvm.python).

1. GraalPy project publishes a Maven archetype to generate a starter project:
   ```bash
   mvn archetype:generate \
     -DarchetypeGroupId=org.graalvm.python \
     -DarchetypeArtifactId=graalpy-archetype-polyglot-app \
     -DarchetypeVersion=25.0.2
   ```

2. Build a native executable using the [GraalVM Native Image "tool"](https://www.graalvm.org/latest/reference-manual/native-image/) plugin that was added for you automatically:
    ```bash
    mvn -Pnative package
    ```

3. Once completed, run the executable:
    ```bash
    ./target/polyglot_app
    ```
    The application prints "hello java" to the console.

The project uses the [GraalVM Polyglot API](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/package-summary.html) with additional features to manage Python virtual environments and integrate Python package dependencies with a Maven workflow.
The Java code and the _pom.xml_ file are heavily documented and the generated code describes available features.

See also [Embedding Build Tools](Embedding-Build-Tools.md#graalpy-maven-plugin) for more information about the GraalPy Maven Plugin.

### Creating Cross-platform JARs with Native Python Packages

The generated project uses the GraalPy Maven plugin, which makes it easy to add Python dependencies.
However, Python packages may have native components that are specific to the build system.
In order to distribute the resulting application for other systems, follow these steps:

1. Build the project on each deployment platform.
   Rename JAR files so they each have a platform-specific name and move them to a temporary directory on the same machine.

2. Unzip each of the JAR files (substituting the correct names for the JAR files).
   A special file, _vfs/fileslist.txt_ needs to be concatenated from each JAR file.
   Finally, create a new _combined.jar_ from the combination of all files and with the concatenated _fileslist.txt_.
    ```bash
    unzip linux.jar -d combined
    mv combined/vfs/fileslist.txt fileslist-linux.txt
    unzip windows.jar -d combined
    mv combined/vfs/fileslist.txt fileslist-windows.txt
    cat fileslist-linux.txt fileslist-windows.txt > combined/vfs/fileslist.txt
    cd combined
    zip -r ../combined.jar *
    ```

## Gradle

1. Create a Java application with Gradle using the command below and follow the prompts (select the Groovy build script language, select a test framework, and so on):
    ```bash
    gradle init --type java-application \
                --project-name interop  \
                --package interop \
                --no-split-project
    ```

    The project is generated in the current working directory with the following structure:
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

2. Open your project configuration file, _app/build.gradle_, and modify it as follows.
    - Include the GraalPy support and the [GraalVM Polyglot API](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/package-summary.html) in the `dependencies` section:
        ```bash
        implementation("org.graalvm.polyglot:polyglot:25.0.2")
        implementation("org.graalvm.polyglot:python:25.0.2")
        ```

3. Finally, replace the code in the file named _App.java_ as follows for a small Python embedding:
    ```java
    package interop;

    import org.graalvm.polyglot.*;

    class App {
        public static void main(String[] args) {
            try (var context = Context.create()) {
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

    > Note: The performance of the GraalPy runtime depends on the JDK in which you embed it. For more information, see [Runtime Optimization Support](https://www.graalvm.org/latest/reference-manual/embed-languages/#runtime-optimization-support).

5. Optionally, you can also use a third-party Python package.

   5.1. In _app/build.gradle_:
   - add the graalpy-gradle-plugin to the `plugins` section:
   ```bash
   id "org.graalvm.python" version "25.0.2"
   ```

   - configure the GraalPy Gradle plugin:
   ```bash
   graalPy {
      packages = ["termcolor==2.2"]
   }
   ```

   5.2. In _settings.gradle_, add the following `pluginManagement` configuration.
   ```bash
   pluginManagement {
      repositories {
         gradlePluginPortal()
      }
   }
   ```

   5.3. Update the file named _App.java_ as follows:
      ```java
      package interop;

      import org.graalvm.polyglot.*;
      import org.graalvm.python.embedding.GraalPyResources;

      class App {
      ...
      public static void main(String[] args) {
          try (Context context = GraalPyResources.createContext()) {
              String src = """
              from termcolor import colored
              colored_text = colored("hello java", "red", attrs=["reverse", "blink"])
              print(colored_text)
              """;
              context.eval("python", src);
          }
      }
      ```

See also [Embedding Build Tools](Embedding-Build-Tools.md) for more information about the GraalPy Gradle Plugin.

## Ant, CMake, Makefile or Other Build Systems Without Direct Support for Maven Dependencies

Some (often older) projects may be using Ant, Makefiles, CMake, or other build systems that do not directly support Maven dependencies.
Projects such as [Apache Ivy&trade;](https://ant.apache.org/ivy/history/master/tutorial/start.html) enable such build systems to resolve Maven dependencies, but developers may have reasons not to use them.
GraalPy comes with a tool to obtain the required JAR files from Maven.

1. Assuming there is some directory where third-party dependencies are stored for the project and that the build system is set up to put any JAR files there on the classpath, the project directory tree might look similar to this:

    ```bash
    ├───lib
    │   └─── ... *.jar dependencies are here
    └───src
        └─── ... *.java files and resources are here
    ```

2. [Install GraalPy](Python-Runtime.md#installing-graalpy) for your system and ensure you have `graalpy` on your `PATH`.
   Open a command-line interface and enter your project directory.
   Then, as appropriate for your system, run one of the following commands:

    In a POSIX shell:
    ```bash
    export GRAALPY_HOME=$(graalpy -c 'print(__graalpython__.home)')
    "${GRAALPY_HOME}/libexec/graalpy-polyglot-get" -a python -o lib -v "25.0.2"
    ```

    In PowerShell:
    ```bash
    $GRAALPY_HOME = graalpy -c "print(__graalpython__.home)"
    & "$GRAALPY_HOME/libexec/graalpy-polyglot-get" -a python -o lib -v "25.0.2"
    ```

    These commands download all GraalPy dependencies into the _lib_ directory.

3. Provided that your build system is set up to pick up the JAR files from _lib_, the GraalPy embedding code below should work if put in an appropriate place in the project to run as the main class.

    ```java
    import org.graalvm.polyglot.*;

    public class Hello {
        public static void main(String[] args) {
            try (var context = Context.newBuilder().option("engine.WarnInterpreterOnly", "false").build()) {
                System.out.println(context.eval("python", "'Hello Python!'").asString());
            }
        }
    }
    ```

### Related Documentation

- [Modern Python on the JVM](Python-on-JVM.md)
- [Embedding Graal languages in Java](https://www.graalvm.org/latest/reference-manual/embed-languages/)
