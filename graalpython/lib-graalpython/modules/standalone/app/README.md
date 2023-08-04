## Java and Python Integration Example for GraalVM

This simple project is meant as a jumping off point for a polyglot Python Java application on GraalVM.

### Getting Started

1. Download [GraalVM](https://www.graalvm.org/downloads/) and set your `JAVA_HOME` to point to it.

2. Build and run a standalone jar:
```
mvn package -Pjar
java -jar target/polyglot_app-1.0-SNAPSHOT.jar
```

3. Build and run a standalone native executable:
```
mvn package -Pnative
target/polyglot_app
``
