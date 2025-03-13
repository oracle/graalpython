Here is how the various env files relate to each other:
* `jvm`
  * `jvm-ce`: + GraalVM Community Compiler
    * `jvm-ce-libgraal`: + libgraal
    * `native-ce`: + libpythonvm + `Truffle Macro`
  * `jvm-ee`: + Oracle GraalVM Compiler + `Truffle enterprise` + license + `LLVM Runtime Native Enterprise`
    * `jvm-ee-libgraal`: + libgraal
    * `native-ee`: + libpythonvm + `Truffle Macro Enterprise` + Native Image G1
      * `native-ee-aux`: + `AuxiliaryEngineCache`, - Native Image G1 (currently incompatible)

They all build the GraalPy standalone and nothing else to optimize build time.
