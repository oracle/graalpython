# Embedding Build Tools

> **Note**: The GraalPy build tools are being developed [GraalPy Extensions repository](https://github.com/oracle/graalpy-extensions) on GitHub.

The GraalPy **Maven** and **Gradle** plugins simplify embedding Python in Java applications by automatically managing Python resources during your build process:

- **Your Python code**: Application files, modules, and scripts that are part of your project
- **Third-party packages**: Python libraries (like NumPy, requests) automatically installed in the build according to your plugin configuration.

These plugins handle the complexity of packaging Python code with your Java application, ensuring all dependencies are available at runtime but you need to configure your Java application to access them at runtime.
The [GraalPyResources](https://github.com/oracle/graalpy-extensions/blob/main/org.graalvm.python.embedding/src/main/java/org/graalvm/python/embedding/GraalPyResources.java) API provides factory methods that create a preconfigured GraalPy Context.

> The preconfigured GraalPy Context is a GraalVM Context that has been automatically set up with the right settings to access your Python resources without you having to manually configure all the details.

## Deployment

You can choose between two deployment approaches:

- **Virtual Filesystem**: Resources are embedded within your JAR or executable
- **External Directory**: Resources are stored in a separate directory

### Virtual Filesystem

With the Virtual Filesystem approach, your Python resources are embedded directly inside your JAR file or Native Image executable as standard Java resources. This creates a self-contained application with everything bundled together.

This approach involves the following steps:

- Python files are packaged as Java resources in dedicated resource directories (like `src/main/resources`)
- Multiple resource directories are merged during the build process by Maven or Gradle
- You can configure the Java resource path (default: `org.graalvm.python.vfs`) that gets mapped to a Virtual Filesystem mount point in Python (default: `/graalpy_vfs`)
- GraalPy's Virtual Filesystem transparently maps these resources to Python file paths  
- Your Python code can use normal file operations (`open()`, `import`, etc.) without knowing the files are embedded

For example, a file at `src/main/resources/org.graalvm.python.vfs/src/mymodule.py` becomes accessible to Python as `/graalpy_vfs/src/mymodule.py`.

You can customize the resource path (default: `org.graalvm.python.vfs`) and mount point (default: `/graalpy_vfs`) to avoid conflicts with other libraries.

To use the Virtual Filesystem in your Java application, use the factory methods in the [GraalPyResources](https://github.com/oracle/graalpy-extensions/blob/main/org.graalvm.python.embedding/src/main/java/org/graalvm/python/embedding/GraalPyResources.java) API:

- `GraalPyResources.createContext()` - Creates a ready-to-use context with default Virtual Filesystem configuration
- `GraalPyResources.contextBuilder()` - Returns a context builder for additional customization before creating the context  
- `GraalPyResources.contextBuilder(VirtualFileSystem)` - Returns a context builder with a custom Virtual Filesystem configuration

#### Java Resource Path

When building reusable libraries, use a unique Java resource path to prevent conflicts with other Virtual Filesystem users. This ensures your library's Python resources don't interfere with other libraries on the classpath.

The recommended path is:
```bash
GRAALPY-VFS/${project.groupId}/${project.artifactId}
```

This path must be configured identically in both your build plugin and runtime code using the `VirtualFileSystem$Builder#resourceDirectory` API.

> **Java Module System compatibility:** The "GRAALPY-VFS" prefix bypasses module encapsulation rules since it's not a valid Java package name, eliminating the need for additional module system configuration that would otherwise be required for accessing resources in named modules.

#### Extracting files from Virtual Filesystem

Some files need to exist on the real filesystem rather than staying embedded as Java resources. 

This is required for Python C extensions (`.so`, `.dylib`, `.pyd`, `.dll`) and font files (`.ttf`) that must be accessed by the operating system loader outside the Truffle sandbox.
GraalPy automatically extracts these file types to a temporary directory when first accessed, then delegates to the real files for subsequent operations.

Use the `VirtualFileSystem$Builder#extractFilter` API to modify which files get extracted automatically. For full control, extract all resources to a user-defined directory before creating your GraalPy context:

- `GraalPyResources.extractVirtualFileSystemResources(VirtualFileSystem vfs, Path externalResourcesDirectory)` - Extract resources to a specified directory
- `GraalPyResources.contextBuilder(Path externalResourcesDirectory)` - Create a context builder using the extracted resources directory

For more information, see [GraalPyResources](https://github.com/oracle/graalpy-extensions/blob/main/org.graalvm.python.embedding/src/main/java/org/graalvm/python/embedding/GraalPyResources.java).

### External Directory

With the External Directory approach, your Python resources are stored in a separate directory on the filesystem rather than being embedded as Java resources. This creates a deployment where Python files exist as regular files that you must distribute alongside your application.

This approach involves the following steps:

- Python files remain as regular filesystem files (not embedded as Java resources)
- You are responsible for deploying and managing the external directory
- Python code accesses files directly from the real filesystem
- Smaller JAR/executable size since Python resources aren't embedded

To use an external directory, create your GraalPy context with:
- `GraalPyResources.createContextBuilder(Path)` - Creates a context builder pointing to your external directory path

## Directory Structure

The [GraalPyResources](https://github.com/oracle/graalpy-extensions/blob/main/org.graalvm.python.embedding/src/main/java/org/graalvm/python/embedding/GraalPyResources.java) factory methods rely on this directory structure, which includes a standard [Python virtual environment](https://docs.python.org/3.11/tutorial/venv.html) in the `venv` subdirectory:

| Directory       | Purpose                      | Management         | Python Path |
| --------------- | ---------------------------- | ------------------ |------------ |
| `${root}/src/`  | Your Python application code | **You manage**     | Default search path (equivalent to `PYTHONPATH`) |
| `${root}/venv/` | Third-party Python packages  | **Plugin manages** | Context configured as if executed from this virtual environment |

The `${root}` placeholder refers to different locations depending on your deployment approach:

- **Virtual Filesystem**: `/graalpy_vfs` (Python) / `${project_resources_directory}/org.graalvm.python.vfs` (Java)
- **External Directory**: Filesystem path like `python-resources/`

The GraalPy Context is automatically configured to run within this virtual environment, making all installed packages available for import.

> **Important**: Plugin completely manages `venv/` - any manual changes will be overridden during builds.

## Python Dependency Management for Reproducible Builds

Python packages typically specify dependencies as version ranges (e.g., `B>=2.0.0`) rather than fixed versions.
This means today's build might install `B==2.0.0`, but tomorrow's clean build could pull the newly released `B==2.0.1`, potentially introducing breaking changes or GraalPy incompatibilities.

### Locking Dependencies

**We highly recommend locking all Python dependencies** when packages change. Run a Maven goal or Gradle task to generate _graalpy.lock_, which captures exact versions of all dependencies (those specified explicitly in the _pom.xml_ or _build.gradle_ files and all their transitive dependencies).

Commit the _graalpy.lock_ file to version control (e.g., git). Once this file exists, Maven or Gradle builds will install the exact same package versions captured in the _graalpy.lock_ file.

If you modify dependencies in _pom.xml_ or _build.gradle_ and they no longer match what's in _graalpy.lock_, the build will fail and the user will be asked to explicitly regenerate the _graalpy.lock_ file.

We recommend specifying dependencies without version numbers in the _pom.xml_ or _build.gradle_ file. GraalPy automatically installs compatible versions for well-known packages.

Once installed, lock these versions to ensure reproducible builds.

See the "Locking Python Packages" sections below for specific Maven and Gradle commands.

For information on the specific Maven or Gradle lock packages actions, see the [Locking Python Packages](#locking-python-packages) section.

## GraalPy Maven Plugin

The GraalPy Maven Plugin automates Python resource management in Maven-based Java projects. It downloads Python packages, creates virtual environments, and configures deployment for both Virtual Filesystem (embedded) and External Directory approaches.

### Maven Plugin Configuration

Configure the plugin in your _pom.xml_ file with these elements:

| Element             | Description  |
| ------------------- | ------------ |
| `packages`          | Python dependencies using pip syntax (e.g., `requests>=2.25.0`) - optional |
| `requirementsFile`  | Path to pip-compatible _requirements.txt_ file - optional, mutually exclusive with `packages` |
| `resourceDirectory` | Custom path for [Virtual Filesystem](#virtual-filesystem) deployment (must match Java runtime configuration) |
| `externalDirectory` | Path for [External Directory](#external-directory) deployment (mutually exclusive with `resourceDirectory`) |

Add the plugin configuration to your _pom.xml_ file:

```xml
<plugin>
    <groupId>org.graalvm.python</groupId>
    <artifactId>graalpy-maven-plugin</artifactId>
    <configuration>
        <!-- Python packages (pip-style syntax) -->
        <packages>
            <package>termcolor==2.2</package>
        </packages>
        
        <!-- Choose ONE deployment approach: -->
        <!-- Virtual Filesystem (embedded) -->
        <resourceDirectory>GRAALPY-VFS/${project.groupId}/${project.artifactId}</resourceDirectory>
        
        <!-- OR External Directory (separate files) -->
        <externalDirectory>${basedir}/python-resources</externalDirectory>
    </configuration>
</plugin>
```

#### Using `requirements.txt`

The `requirementsFile` element declares a path to a pip-compatible _requirements.txt_ file.
When configured, the plugin forwards this file directly to pip using `pip install -r`,
allowing full use of pip's native dependency format.

```xml
<configuration>
    <requirementsFile>requirements.txt</requirementsFile>
    ...
</configuration>
```

> **Important:** You must configure either `packages` or `requirementsFile`, but not both.
> 
> When `requirementsFile` is used:
> - the GraalPy lock file is **not created and not used**
> - the `lock-packages` goal is **disabled**
> - dependency locking must be handled externally by pip (for example using `pip freeze`)
> 
> Mixing `packages` and `requirementsFile` in the same configuration is not supported.

#### Excluding Build-Only Packages

You can remove build-only packages from final JAR using `maven-jar-plugin`:

```xml
  <plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-jar-plugin</artifactId>
    <version>3.4.2</version>
    <configuration>
        <excludes>
            <exclude>**/site-packages/pip*/**</exclude>
            <exclude>**/site-packages/setuptools*/**</exclude>
        </excludes>
    </configuration>
  </plugin>
```

### Locking Python Packages

Generate a lock file to ensure reproducible builds:

```bash
$ mvn org.graalvm.python:graalpy-maven-plugin:lock-packages
```

> **Note:** This action overwrites any existing _graalpy.lock_ file.

To customize the lock file path, configure _graalPyLockFile_ :

```xml
<configuration>
    <graalPyLockFile>${basedir}/graalpy.lock</graalPyLockFile>
</configuration>
```

> **Note:** This only changes the path (defaults to _${basedir}/graalpy.lock_). To generate the lock file, run the `lock-packages` goal.

For more information of this feature, please see the
[Python Dependency Management for Reproducible Builds](#python-dependency-management-for-reproducible-builds) section.

## GraalPy Gradle Plugin

The GraalPy Gradle Plugin automates Python resource management in Gradle-based Java projects. It downloads Python packages, creates virtual environments, and configures deployment for both Virtual Filesystem (embedded) and External Directory approaches.

### Gradle Plugin Configuration

Configure the plugin in your _build.gradle_ file with these elements:

| Element             | Description  |
| ------------------- | ------------ |
| `packages`          | Python dependencies using pip syntax (e.g., `requests>=2.25.0`) |
| `resourceDirectory` | Custom path for [Virtual Filesystem](#virtual-filesystem) deployment (must match Java runtime configuration) |
| `externalDirectory` | Path for [External Directory](#external-directory) deployment (mutually exclusive with `resourceDirectory`) |

Add the plugin configuration to your _build.gradle_ file:

```groovy
plugins {
    id 'org.graalvm.python' version '25.0.2'
}

graalPy {
    // Python packages (pip-style syntax)
    packages = ["termcolor==2.2"]
    
    // Choose ONE deployment approach:
    // Virtual Filesystem (embedded)
    resourceDirectory = "GRAALPY-VFS/my.group.id/artifact.id"
    
    // OR External Directory (separate files)
    externalDirectory = file("$rootDir/python-resources")
}
```

The plugin automatically injects these dependencies of the same version as the plugin version:

- `org.graalvm.python:python`
- `org.graalvm.python:python-embedding`

### Locking Python Packages

Generate a lock file to ensure reproducible builds:

```bash
gradle graalPyLockPackages
```

> **Note:** This overwrites any existing _graalpy.lock_ file.

To customize the lock file path, configure _graalPyLockFile_:

```groovy
  graalPy {
    graalPyLockFile = file("$rootDir/graalpy.lock")
    ...
  }
```

> **Note:** This only changes the path (defaults to _$rootDir/graalpy.lock_). To generate the lock file, run the `graalPyLockPackages` task.

For more information of this feature, please see the
[Python Dependency Management for Reproducible Builds](#python-dependency-management-for-reproducible-builds) section.

## Related Documentation

- [Embedding Graal languages in Java](https://www.graalvm.org/reference-manual/embed-languages/)
- [Permissions for Python Embeddings](Embedding-Permissions.md)
- [GraalPy extensions on GitHub](https://github.com/oracle/graalpy-extensions)
