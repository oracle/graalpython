plugins {
    application
    id("org.graalvm.python")
    id("org.graalvm.buildtools.native") version "0.10.2"
}

repositories {
    mavenCentral()
}

application {
    // Define the main class for the application.
    mainClass = "org.example.GraalPy"
}

// XXX how to run with asserts on, aka -ea?
//run {
//    enableAssertions = true
//}
val r = tasks.run.get()
r.enableAssertions = true
r.outputs.upToDateWhen {false}

dependencies {
    implementation("org.graalvm.python:python-community:24.2.0")
}
