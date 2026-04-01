# Using GraalPy as a Standalone Python Runtime

GraalPy can be used as a standalone Python runtime, providing a drop-in replacement for CPython.
This guide covers installation, basic usage, and deployment options for standalone GraalPy applications.

> **Choosing a Distribution**: For detailed information about GraalPy distributions and runtime options, see [Python Runtime](Python-Runtime.md#choosing-a-graalpy-distribution).

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

   - AMD64: `graalpy-XX.Y.Z-linux-amd64.tar.gz`
   - ARM64: `graalpy-XX.Y.Z-linux-aarch64.tar.gz`

2. Extract and add it to your `PATH` environment variable:

   ```bash
   tar -xzf graalpy-25.0.2-linux-amd64.tar.gz
   export PATH="$PWD/graalpy-25.0.2-linux-amd64/bin:$PATH"
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
   tar -xzf graalpy-25.0.2-macos-aarch64.tar.gz
   export PATH="$PWD/graalpy-25.0.2-macos-aarch64/bin:$PATH"
   ```

### Windows

> **Warning**: The Windows distribution has more limitations than Linux or macOS. Not all features and packages may be available.

1. Download the binary from [GitHub releases](https://github.com/oracle/graalpython/releases).

2. Extract and add it to your `PATH` environment variable:

   ```powershell
   # Extract the file and update your PATH environment variable
   # to include the graalpy-XX.Y.Z-windows-amd64/bin directory
   tar -xzf graalpy-25.0.2-windows-amd64.tar.gz
   $env:PATH = "$PWD\graalpy-25.0.2-windows-amd64\bin;$env:PATH"
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
