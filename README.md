# GraalPy, the GraalVM Implementation of Python

[![](https://img.shields.io/badge/maven-org.graalvm.polyglot/python-orange)](https://central.sonatype.com/artifact/org.graalvm.polyglot/python)
[![](https://img.shields.io/badge/pyenv-graalpy-blue)](#start-replacing-cpython-with-graalpy) 
</a> [![Join Slack][badge-slack]][slack] [![GraalVM on Twitter][badge-twitter]][twitter] [![License](https://img.shields.io/badge/license-UPL-green)](#license)

GraalPy is a high-performance implementation of the [Python](https://www.python.org/) language for the JVM built on [GraalVM](https://www.graalvm.org/).
GraalPy has first-class support for embedding in Java and can turn Python applications into fast, standalone binaries.

## Why GraalPy?

**Low-overhead integration with Java and other languages**

* Use [Python in Java](docs/user/Interoperability.md) applications on GraalVM JDK, Oracle JDK, or OpenJDK
* Use JVM tools like [Maven](docs/user/README.md), JFR, or [GraalVM Native Image](docs/user/Native-Images-with-Python.md)
* Manage Python libraries' system access thanks to GraalPy's [Java-based emulation of Python OS APIs](docs/user/Embedding-Permissions.md)

**Compatible with the Python ecosystem**

* Install [packages](docs/user/Python-Runtime.md#installing-packages) like *NumPy*, *PyTorch*, or *Tensorflow*; run [Hugging Face](https://huggingface.co/) models like *Stable Diffusion* or *GPT*
* See if the packages you need work with our [Python Compatibility Checker](https://www.graalvm.org/python/compatibility/)
* Use almost any standard Python feature, the CPython tests run on every commit and pass ~85%
![](docs/user/assets/mcd.svg)<sup>
We run the tests of the [top PyPI packages](https://hugovk.github.io/top-pypi-packages/) on GraalPy every day.
For more than 96% of the top PyPI packages, there is at least one recent version that installs successfully and we are currently passing over 50% of all tests those top packages.
</sup>

**Runs Python code faster**

* Pure Python code is often faster than on CPython after JIT compilation
* C extension performance is near CPython, but varies depending on the specific interactions of native and Python code
* GraalPy is ~4x faster than CPython on the official [Python Performance Benchmark Suite](https://pyperformance.readthedocs.io/)
![](docs/user/assets/performance.svg)<sup>
Benchmarks run via `pip install pyperformance && pyperformance run` on each of CPython and GraalPy.
Harness and benchmarks were adapted by hand for Jython due to missing Python 3 support.
Each interpreter was installed via <tt>[pyenv](https://github.com/pyenv/pyenv)</tt>.
Geomean speedup was calculated against CPython on the intersection of benchmarks that run on all interpreters.
</sup>

## Getting Started

<details>
<summary><strong><a name="start-embedding-graalpy-in-java"></a>Embedding GraalPy in Java</strong></summary>

GraalPy is [available on Maven Central](https://central.sonatype.com/artifact/org.graalvm.polyglot/python) for inclusion in Java projects.
Refer to our [embedding documentation](https://www.graalvm.org/latest/reference-manual/embed-languages/) for more details.

* Maven
  ```xml
  <dependency>
      <groupId>org.graalvm.polyglot</groupId>
      <artifactId>polyglot</artifactId>
      <version>23.1.2</version>
  </dependency>
  <dependency>
      <groupId>org.graalvm.polyglot</groupId>
      <artifactId>python</artifactId>
      <version>23.1.2</version>
      <type>pom</type>
  </dependency>
  ```

* Gradle
  ```kotlin
  implementation("org.graalvm.polyglot:polyglot:23.1.2")
  implementation("org.graalvm.polyglot:python:23.1.2")
  ```

</details>

<details>
<summary><strong><a name="start-replacing-cpython-with-graalpy"></a>Replacing CPython with GraalPy</strong></summary>

GraalPy should in many cases work as a drop-in replacement for CPython.
You can use `pip` to install packages as usual.
Packages with C code usually do not provide binaries for GraalPy, so they will be automatically compiled during installation.
This means that build tools have to be available and installation will take longer.
We provide [Github actions](scripts/wheelbuilder) to help you build binary packages with the correct dependencies.
Thanks to our integration with GraalVM Native Image, we can deploy Python applications as [standalone binary](docs/user/Python-Standalone-Applications.md), all dependencies included.

* Linux

  The easiest way to install GraalPy on Linux is to use [Pyenv](https://github.com/pyenv/pyenv) (the Python version manager).
  To install version 23.1.2 using Pyenv, run the following commands:
  ```bash
  pyenv install graalpy-23.1.2
  ```
  ```bash
  pyenv shell graalpy-23.1.2
  ```
  
  Alternatively, you can download a compressed GraalPy installation file from [GitHub releases](https://github.com/oracle/graalpython/releases).
  
  1. Find the download that matches the pattern _graalpy-XX.Y.Z-linux-amd64.tar.gz_ or _graalpy-XX.Y.Z-linux-aarch64.tar.gz_ (depending on your platform) and download.
  2. Uncompress the file and update your `PATH` environment variable to include the _graalpy-XX.Y.Z-linux-amd64/bin_ (or _graalpy-XX.Y.Z-linux-aarch64/bin_) directory.

* macOS

  The easiest way to install GraalPy on macOS is to use [Pyenv](https://github.com/pyenv/pyenv) (the Python version manager).
  To install version 23.1.2 using Pyenv, run the following commands:
  ```bash
  pyenv install graalpy-23.1.2
  ```
  ```bash
  pyenv shell graalpy-23.1.2
  ```
  Alternatively, you can download a compressed GraalPy installation file from [GitHub releases](https://github.com/oracle/graalpython/releases).
  
  1. Find the download that matches the pattern _graalpy-XX.Y.Z-macos-amd64.tar.gz_ or _graalpy-XX.Y.Z-macos-aarch64.tar.gz_ (depending on your platform) and download. 
  2. Remove the quarantine attribute.
      ```bash
      sudo xattr -r -d com.apple.quarantine /path/to/graalpy
      ```
      For example:
      ```bash
      sudo xattr -r -d com.apple.quarantine ~/.pyenv/versions/graalpy-23.1.2
      ```
  3. Uncompress the file and update your `PATH` environment variable to include to the _graalpy-XX.Y.Z-macos-amd64/bin_ (or _graalpy-XX.Y.Z-macos-aarch64/bin_) directory.

* Windows

  The Windows support of GraalPy is still experimental, so not all features and packages may be available.
  
  1. Find and download a compressed GraalPy installation file from [GitHub releases](https://github.com/oracle/graalpython/releases) that matches the pattern _graalpy-XX.Y.Z-windows-amd64.tar.gz_.
  2. Uncompress the file and update your `PATH` variable to include to the _graalpy-XX.Y.Z-windows-amd64/bin_ directory.
  
</details>
<details>
<summary><strong><a name="start-using-graalpy-in-github-actions"></a>Using GraalPy in Github Actions</strong></summary>

The _setup-python_ action supports GraalPy:

```yaml
    - name: Setup GraalPy
      uses: actions/setup-python@v5
      with:
        python-version: graalpy # or graalpy23.1 to pin a version
```

</details>
<details>
<summary><strong><a name="start-migrating-jython-scripts-to-graalpy"></a>Migrating Jython Scripts to GraalPy</strong></summary>

Most existing Jython code that uses Java integration will be based on a stable Jython release&mdash;however, these are only available in Python 2.x versions.
To migrate your code from Python 2 to Python 3, follow [the official guide from the Python community](https://docs.python.org/3/howto/pyporting.html).
GraalPy provides a [special mode](docs/user/Python-on-JVM.md) to facilitate migration.
To run Jython scripts, you need to use a GraalPy distribution running on the JVM so you can access Java classes from Python scripts.

* Linux
  
  1. Find and download a compressed GraalPy installation file from [GitHub releases](https://github.com/oracle/graalpython/releases) that matches the pattern _graalpy-jvm-XX.Y.Z-linux-amd64.tar.gz_ or _graalpy-jvm-XX.Y.Z-linux-aarch64.tar.gz_ (depending on your platform) and download.
  2. Uncompress the file and update your `PATH` environment variable to include the _graalpy-jvm-XX.Y.Z-linux-amd64/bin_ (or _graalpy-jvm-XX.Y.Z-linux-aarch64/bin_) directory.
  3. Run your scripts with `graalpy --python.EmulateJython`.

* macOS

  1. Find and download a compressed GraalPy installation file from [GitHub releases](https://github.com/oracle/graalpython/releases) that matches the pattern  _graalpy-jvm-XX.Y.Z-macos-amd64.tar.gz_ or _graalpy-jvm-XX.Y.Z-macos-aarch64.tar.gz_ (depending on your platform) and download.
  2. Remove the quarantine attribute.
      ```bash
      sudo xattr -r -d com.apple.quarantine /path/to/graalpy
      ```
      For example:
      ```bash
      sudo xattr -r -d com.apple.quarantine ~/.pyenv/versions/graalpy-23.1.2
      ```
  3. Uncompress the file and update your `PATH` environment variable to include to the _graalpy-jvm-XX.Y.Z-macos-amd64/bin_ (or _graalpy-jvm-XX.Y.Z-macos-aarch64/bin_) directory.
  4. Run your scripts with `graalpy --python.EmulateJython`.

* Windows

  1. Find and download a compressed GraalPy installation file from [GitHub releases](https://github.com/oracle/graalpython/releases) that matches the pattern _graalpy-jvm-XX.Y.Z-windows-amd64.tar.gz_.
  2. Uncompress the file and update your `PATH` variable to include to the _graalpy-jvm-XX.Y.Z-windows-amd64/bin_ directory.
  3. Run your scripts with `graalpy --python.EmulateJython`.

</details>

### Examples
![](docs/showcase.png)<sup>
[Java AWT app with Python graph library](https://github.com/timfel/graalpy-jbang) using [JBang](https://www.jbang.dev/)  |  [Standalone binary](https://github.com/timfel/racing-all-afternoon) of a Python game by [Joey Navarro](https://github.com/josephnavarro/racing-all-afternoon) with all dependencies included.
</sup>

## Documentation

[GraalPy Quick Reference Sheet](https://www.graalvm.org/uploads/quick-references/GraalPy_v1/quick-reference-graalpy-v1(eu_a4).pdf) should help you get started.
More GraalPy-specific user documentation is available in [docs/user](docs/user).
General documentation about [polyglot programming](https://www.graalvm.org/latest/reference-manual/polyglot-programming/) and [language embedding](https://www.graalvm.org/latest/reference-manual/embed-languages/) is available on the GraalVM website.

## Community

The best way to get in touch with us is to join the `#graalpy` channel on [GraalVM Slack][slack] or [tweet us][twitter].

## Contributing

This project welcomes contributions from the community. Before submitting a pull request, please [review our contribution guide](./CONTRIBUTING.md).

If you're thinking about contributing something to this repository, you will need to sign the [Oracle Contributor Agreement](https://www.graalvm.org/community/contributors/) for us to able to merge your work.
Also take a look at the [code of conduct](https://www.graalvm.org/community/conduct/) for contributors.

## Security

Consult the [security guide](./SECURITY.md) for our responsible security vulnerability disclosure process.

## License

This GraalVM implementation of Python is Copyright (c) 2017, 2024 Oracle and/or its affiliates and is made available to you under the terms the Universal Permissive License v 1.0 as shown at [https://oss.oracle.com/licenses/upl/](https://oss.oracle.com/licenses/upl/).
This implementation is in part derived from and contains additional code from 3rd parties, the copyrights and licensing of which is detailed in the [LICENSE](./LICENSE.txt) and [THIRD_PARTY_LICENSE](THIRD_PARTY_LICENSE.txt) files.

[badge-slack]: https://img.shields.io/badge/Slack-join-active?logo=slack
[badge-twitter]: https://img.shields.io/badge/Twitter-@graalvm-active?logo=twitter
[slack]: https://www.graalvm.org/slack-invitation/
[twitter]: https://twitter.com/graalvm
