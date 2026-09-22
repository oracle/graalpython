Here is how the various env files relate to each other:
* `jvm`
  * `jvm-ce`: + GraalVM Community Compiler
    * `jvm-ce-libgraal`: + libgraal
    * `native-ce`: + libpythonvm + `Truffle Macro`
  * `jvm-ee`: + Oracle GraalVM Compiler + `Truffle enterprise` + license + `LLVM Runtime Native Enterprise`
    * `jvm-ee-libgraal`: + libgraal
    * `native-ee`: + libpythonvm + `Truffle Macro Enterprise` + Native Image G1
      * `native-ee-aux`: + `AuxiliaryEngineCache`, - Native Image G1 (currently incompatible)

The configurations above build the GraalPy standalone and nothing else to optimize build time.

`jmh-native-ce` and `jmh-native-ee` instead build `GRAALPYTHON_BENCH` and a Native Image toolchain
with the Truffle macro and tracing agent. They are used by the `python-jmh` suite to build and run
the embedding benchmarks as native executables, without building a GraalPy native standalone.
See [Java embedding benchmarks](../docs/contributor/CONTRIBUTING.md#java-embedding-benchmarks-jmh)
for build and comparison commands.
