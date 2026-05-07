# Using GraalPy as a Standalone Python Runtime

GraalPy can be used as a standalone Python runtime, providing a drop-in replacement for CPython.
This guide covers choosing a distribution, installation, package management, basic usage, and deployment options for standalone GraalPy applications.

## Choosing a GraalPy Distribution

GraalPy is available in multiple distributions:

### Distribution Options

- **GraalPy built on Oracle GraalVM** provides the best experience with additional optimizations, significantly faster performance, and better memory efficiency. It is licensed under the [GraalVM Free Terms and Conditions (GFTC)](https://www.oracle.com/downloads/licenses/graal-free-license.html), which permits use by any user including commercial and production use. Redistribution is permitted as long as it is not for a fee.
- **GraalPy Community** is built on top of GraalVM Community Edition and is fully open source.

### Runtime Options

Two language runtime options are available for both distributions:

- **Native** (recommended for standalone use)
  - GraalPy is compiled ahead-of-time to a native executable
  - You do not need a JVM to run GraalPy and it is compact in size
  - Faster startup time
  - Faster time to reach peak performance
- **JVM**
  - You can easily exploit Java interoperability
  - Peak performance may be higher than the native option
  - Slower startup time

### Distribution Identification

The GraalPy native runtimes are identified using the pattern _graalpy(-community)&lt;python-version&gt;-&lt;graal-version&gt;-&lt;os&gt;-&lt;arch&gt;_:

| Distribution  | Native                                                          | JVM                                                 |
|---------------|-----------------------------------------------------------------|-----------------------------------------------------|
| **Oracle**    | `graalpy<python-version>-<graal-version>-<os>-<arch>`           | `graalpy-jvm-<graal-version>-<os>-<arch>`           |
| **Community** | `graalpy-community<python-version>-<graal-version>-<os>-<arch>` | `graalpy-community-jvm-<graal-version>-<os>-<arch>` |

### Runtime Comparison

| Runtime | Native (default) | JVM |
|:-------|:-----------------|:----|
| Time to start | faster | slower |
| Time to reach peak performance | faster | slower |
| Peak performance (also considering GC) | good | best |
| Java interoperability | needs configuration | works |

## GraalPy Capabilities

GraalPy provides a Python 3.12 compliant runtime.
A primary goal is to support PyTorch, SciPy, and their constituent libraries, as well as to work with other data science and machine learning libraries from the rich Python ecosystem.

GraalPy provides the following capabilities:

- CPython-compatible distribution for testing Python code on GraalPy.
- A [single native binary packaging mode](Python-Standalone-Applications.md) for Python applications.
- Access to GraalVM language ecosystems and tools.

## Installation

> **Note**: There may be a delay between GraalPy release and its availability on Pyenv.

### Linux (Recommended Platform)

The easiest way to install GraalPy on Linux is to use [Pyenv](https://github.com/pyenv/pyenv) (the Python version manager):

```bash
# Update pyenv to include latest GraalPy versions (if needed)
pyenv update

# Install GraalPy 25.0.2
pyenv install graalpy-25.0.2

# Use GraalPy for the current shell session
pyenv shell graalpy-25.0.2
```

#### Manual Installation (Linux)

1. Download the appropriate binary from [GitHub releases](https://github.com/oracle/graalpython/releases):

   - AMD64: `graalpy3.12-25.1.0-linux-amd64.tar.gz`
   - ARM64: `graalpy3.12-25.1.0-linux-aarch64.tar.gz`

2. Extract and add it to your `PATH` environment variable:

   ```bash
   tar -xzf graalpy3.12-25.1.0-linux-amd64.tar.gz
   export PATH="$PWD/graalpy3.12-25.1.0-linux-amd64/bin:$PATH"
   ```

### macOS

Using Pyenv (recommended):

```bash
# Install GraalPy 25.0.2
pyenv install graalpy-25.0.2

# Use GraalPy for the current shell session
pyenv shell graalpy-25.0.2
```

#### Manual Installation (macOS)

1. Download the binary from [GitHub releases](https://github.com/oracle/graalpython/releases).

2. Remove quarantine attribute:

   ```bash
   sudo xattr -r -d com.apple.quarantine /path/to/graalpy
   # For example:
   sudo xattr -r -d com.apple.quarantine ~/.pyenv/versions/graalpy-25.0.2
   ```

3. Extract and add it to your `PATH` environment variable:

   ```bash
   tar -xzf graalpy3.12-25.1.0-macos-aarch64.tar.gz
   export PATH="$PWD/graalpy3.12-25.1.0-macos-aarch64/bin:$PATH"
   ```

### Windows

> **Warning**: The Windows distribution has more limitations than Linux or macOS. Not all features and packages may be available.

1. Download the binary from [GitHub releases](https://github.com/oracle/graalpython/releases).

2. Extract and add it to your `PATH` environment variable:

   ```powershell
   # Extract the file and update your PATH environment variable
   # to include the graalpy3.12-25.1.0-windows-amd64/bin directory
   tar -xzf graalpy3.12-25.1.0-windows-amd64.zip
   $env:PATH = "$PWD\graalpy3.12-25.1.0-windows-amd64\bin;$env:PATH"
   ```

#### Known Windows Limitations

- JLine treats Windows as a dumb terminal (no autocomplete, limited REPL editing)
- Interactive `help()` in REPL doesn't work
- Virtual environment issues:
  - `graalpy.cmd` and `graalpy.exe` are broken inside `venv`
  - `pip.exe` cannot be used directly
  - Use `myvenv/Scripts/python.exe -m pip --no-cache-dir install <pkg>`
  - Only pure Python binary wheels supported
- PowerShell works better than CMD

## Using Virtual Environments

The recommended way to use GraalPy is with [venv](https://docs.python.org/3/library/venv.html) virtual environments:

### Creating a Virtual Environment

```bash
# Create a virtual environment
graalpy -m venv ~/.virtualenvs/graalpy-25.0.2

# Activate the environment
source ~/.virtualenvs/graalpy-25.0.2/bin/activate
```

### Installing Packages

Once in a virtual environment, you can use `pip` to install packages:

```bash
# Install a package
pip install requests

# Install with requirements file
pip install -r requirements.txt
```

> **Note**: GraalPy's `pip` implementation may choose different package versions to ensure better compatibility.

To deactivate the virtual environment:

```bash
# Return to your normal shell environment
deactivate
```

## Running Python Code

Once installed, you can use GraalPy like any other Python interpreter:

```bash
# Interactive REPL
graalpy

# Run a Python script
graalpy myscript.py

# Run a module
graalpy -m mymodule

# Execute inline code
graalpy -c "print('Hello from GraalPy!')"
```

## Platform Support

**Linux is the recommended platform** for GraalPy.
The main testing focus is:

- **Operating System**: Oracle Linux
- **CPU Architectures**: AMD64 and ARM64
- **Primary JDK**: Oracle GraalVM

Windows and macOS with GraalVM JDK have less comprehensive testing.
Some GraalPy features may not work optimally on these platforms.

See [Test Tiers](Test-Tiers.md) for a detailed breakdown of platform support.
