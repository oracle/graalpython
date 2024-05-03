---
layout: docs-experimental
toc_group: python
link_title: Python Reference
permalink: /reference-manual/python/
redirect_from:
  - /docs/reference-manual/python/
  - /reference-manual/python/FAQ/
---

Go [here](Python-Runtime.md) to get started with GraalPy as CPython replacement.

# Getting Started with GraalPy on the JVM

You can use GraalPy with GraalVM JDK, Oracle JDK, or OpenJDK.
You can easily add GraalPy to your Java application using Maven or Gradle build tools as shown below.
Other build systems (Ant, Make, CMake, ...) can also be used with a bit more manual work.

## Maven

GraalPy can generate a Maven project that embeds Python packages into a Java application using [Maven artefacts](https://mvnrepository.com/artifact/org.graalvm.python).


1. Since version 24.0, the GraalPy project publishes a Maven archetype to generate a starter project:
   ```bash
   mvn archetype:generate \
     -DarchetypeGroupId=org.graalvm.python \
     -DarchetypeArtifactId=graalpy-archetype-polyglot-app \
     -DarchetypeVersion=24.0.0
   ```

2. Build a native executable using the [GraalVM Native Image](https://www.graalvm.org/latest/reference-manual/native-image/) plugin that was added for you automatically:
    ```bash
    mvn -Pnative package
    ```

3. Once completed, run the executable:
    ```
    ./target/polyglot_app
    ```
    The application prints "hello java" to the console.

The project uses the [GraalVM SDK Polyglot API](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/package-summary.html) with additional features to manage Python virtual environments and integrate Python package dependencies with a Maven workflow.
The Java code and the _pom.xml_ file are heavily documented and the generated code describes available features.
(If you do not wish to use Maven, the archetype Java code also provides guidance to create a custom embedding.)

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

1. Create a Java application with Gradle using the command below and follow the prompts (select a build script language, select a test framework, and so on):
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
    - Include the GraalPy support and the [GraalVM SDK Polyglot API](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/package-summary.html) in the `dependencies` section:

        ```
        implementation("org.graalvm.polyglot:polyglot:24.0.0")
        implementation("org.graalvm.polyglot:python:24.0.0")
        ```

    - We recommend you use the Java modules build. Add the appropriate plugin to the `plugins` section:
        ```
        id("org.javamodularity.moduleplugin") version "1.8.12"
        ```

    - To run the application as a module rather than from the classpath, edit the `application` section to look like this:
        ```
        application {
            mainClass.set("interop.App")
            mainModule.set("interop")
        }
        ```

3. Create a new file named _app/src/main/java/module-info.java_ with the following contents:
    ```java
    module interop {
        requires org.graalvm.polyglot;
    }
    ```

4. Finally, replace the code in the file named _App.java_ as follows for a small Python embedding:
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

5. Run the application with Gradle:
    ```bash
    ./gradlew run
    ```
    The application prints "Hello Python!" to the console.

> Note: The performance of the GraalPy runtime depends on the JDK in which you embed it. For more information, see [Runtime Optimization Support](https://www.graalvm.org/latest/reference-manual/embed-languages/#runtime-optimization-support).

## Ant, CMake, Makefile or Other Build Systems Without Direct Support for Maven Dependencies

Some (often older) projects may be using Ant, Makefiles, CMake, or other build systems that do not directly support Maven dependencies.
Projects such as [Apache Ivy&trade;](https://ant.apache.org/ivy/history/master/tutorial/start.html) enable such build systems to resolve Maven dependencies, but developers may have reasons not to use them.
GraalPy comes with a tool to obtain the required JAR files from Maven.

1. Assuming there is some directory where third-party dependencies are stored for the project and that the build system is set up to put any JAR files there on the classpath, the project directory tree might look similar to this:

    ```
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
    "${GRAALPY_HOME}/libexec/graalpy-polyglot-get" -a python -o lib -v "24.0.0"
    ```

    In PowerShell:
    ```
    $GRAALPY_HOME = graalpy -c "print(__graalpython__.home)"
    & "$GRAALPY_HOME/libexec/graalpy-polyglot-get" -a python -o lib -v "24.0.0"
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

#### Related Documentation

- [Modern Python on the JVM](Python-on-JVM.md)
- [Embedding Graal languages in Java](https://www.graalvm.org/latest/reference-manual/embed-languages/)
