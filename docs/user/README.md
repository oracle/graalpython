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

## Gradle

1. Create a Java application with Gradle using the provided command and follow the prompts (select a build script language, select a test framework, etc.):
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
        implementation("org.graalvm.polyglot:polyglot:23.1.2")
        implementation("org.graalvm.polyglot:python:23.1.2")
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

#### Related Documentation

- [Modern Python on the JVM](Python-on-JVM.md)
- [Embedding Graal languages in Java](https://www.graalvm.org/latest/reference-manual/embed-languages/)
