## Java and Python Integration Example for GraalVM

This simple project is meant as a jumping off point for a polyglot Python Java application on GraalVM.

### Getting Started

1. To build and run a standalone jar:

```
mvn package -Pjar
java -jar target/polyglot_app-1.0-SNAPSHOT.jar
```

2. To build and run a standalone native executable you need to use a [GraalVM JDK with Native Image](https://www.graalvm.org/downloads/) and run:

```
mvn package -Pnative
target/polyglot_app
```
