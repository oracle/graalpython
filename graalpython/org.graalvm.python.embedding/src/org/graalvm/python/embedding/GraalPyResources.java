/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.graalvm.python.embedding;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.ProcessProperties;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.IOAccess;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * This class provides utilities related to Python resources used in GraalPy embedding scenarios
 * which can be of the following kind:
 * <ul>
 * <li>Python application files</li>
 * <li>Third-party Python packages</li>
 * </ul>
 *
 * <p>
 * Resource files can be embedded and distributed in an <b>application file</b> or made available
 * from an <b>external directory</b>.
 * </p>
 *
 * <h3>Virtual Filesystem</h3>
 * <p>
 * If the resource files are part of an <b>application file</b> (jar file or a native image
 * executable), then at runtime they will be accessed as standard Java resources through GraalPy
 * {@link VirtualFileSystem}. This will be transparent to Python code running in GraalPy so that it
 * can use standard Python IO to access those files.
 * </p>
 *
 * <p>
 * In order to make this work, it is necessary for those embedded resources to have a common
 * <b>resource root directory</b>. The default value is <code>/org.graalvm.python.vfs</code>,
 * however the recommended convention is to use {@code GRAALPY-VFS/{groupId}/{artifactId}}. This
 * root directory will then be in python code mapped to the virtual filesystem <b>mount point</b>,
 * by default <code>/graalpy_vfs</code>. Refer to
 * {@link VirtualFileSystem.Builder#resourceDirectory(String)} documentation for more details.
 * </p>
 *
 * <h3>External Directory</h3>
 * <p>
 * As an alternative to Java resources with the Virtual Filesystem, it is also possible to configure
 * the GraalPy context to use an external directory, which is not embedded as a Java resource into
 * the resulting application. Python code will access the files directly from the real filesystem.
 * </p>
 *
 * <h3>Conventions</h3>
 * <p>
 * The factory methods in GraalPyResources rely on the following conventions:
 * <ul>
 * <li>${resources_root}/src: used for Python application files. This directory will be configured
 * as the default search path for Python module files (equivalent to PYTHONPATH environment
 * variable).</li>
 * <li>${resources_root}/venv: used for the Python virtual environment holding installed third-party
 * Python packages. The Context will be configured as if it is executed from this virtual
 * environment. Notably packages installed in this virtual environment will be automatically
 * available for importing.</li>
 * </ul>
 * where ${resources_root} is either the resource root <code>/org.graalvm.python.vfs</code> or an
 * external directory.
 * </p>
 *
 * <p>
 * <b>Example</b> creating a GraalPy context configured for the usage with a
 * {@link VirtualFileSystem}:
 * </p>
 *
 * <pre>
 * VirtualFileSystem.Builder builder = VirtualFileSystem.newBuilder();
 * builder.unixMountPoint("/python-resources");
 * VirtualFileSystem vfs = builder.build();
 * try (Context context = GraalPyResources.contextBuilder(vfs).build()) {
 *     context.eval("python", "for line in open('/python-resources/data.txt').readlines(): print(line)");
 * } catch (PolyglotException e) {
 *     if (e.isExit()) {
 *         System.exit(e.getExitStatus());
 *     } else {
 *         throw e;
 *     }
 * }
 * </pre>
 *
 * In this example we:
 * <ul>
 * <li>create a {@link VirtualFileSystem} configured to have the root
 * <code>/python-resources</code></li>
 * <li>create a GraalPy context preconfigured with that {@link VirtualFileSystem}</li>
 * <li>use the context to invoke a python snippet reading a resource file</li>
 * </ul>
 * </p>
 * <p>
 * <b>GraalPy context</b> instances created by factory methods in this class are preconfigured with
 * some particular resource paths:
 * <li><code>${resources_root}/venv</code> - is reserved for a python virtual environment holding
 * third-party packages. The context will be configured as if it were executed from this virtual
 * environment. Notably packages installed in this virtual environment will be automatically
 * available for importing.</li>
 * <li><code>${resources_root}/src</code> - is reserved for python application files - e.g. python
 * sources. GraalPy context will be configured to see those files as if set in PYTHONPATH
 * environment variable.</li>
 * </ul>
 * where <code>${resources_root}</code> is either an external directory or the virtual filesystem
 * resource root <code>/org.graalvm.python.vfs</code>.
 * </p>
 * <p>
 * <b>Example</b> creating a GraalPy context configured for the usage with an external resource
 * directory:
 *
 * <pre>
 * try (Context context = GraalPyResources.contextBuilder(Path.of("python-resources")).build()) {
 *     context.eval("python", "import mymodule; mymodule.print_hello_world()");
 * } catch (PolyglotException e) {
 *     if (e.isExit()) {
 *         System.exit(e.getExitStatus());
 *     } else {
 *         throw e;
 *     }
 * }
 * </pre>
 *
 * In this example we:
 * <ul>
 * <li>create a GraalPy context which is preconfigured with GraalPy resources in an external
 * resource directory</li>
 * <li>use the context to import the python module <code>mymodule</code>, which should be either
 * located in <code>python-resources/src</code> or in a python package installed in
 * <code>python-resources/venv</code> (python virtual environment)</li>
 * </ul>
 * </p>
 *
 * For <b>more examples</b> on how to use this class refer to
 * <a href="https://github.com/graalvm/graal-languages-demos/tree/main/graalpy">GraalPy Demos and
 * Guides</a>.
 *
 * @see VirtualFileSystem
 * @see VirtualFileSystem.Builder
 *
 * @since 24.2.0
 */
public final class GraalPyResources {

    private GraalPyResources() {
    }

    /**
     * Creates a GraalPy context preconfigured with a {@link VirtualFileSystem} and other GraalPy
     * and polyglot Context configuration options optimized for the usage of the
     * <a href="https://docs.python.org/3/library/venv.html">Python virtual environment</a>
     * contained in the virtual filesystem.
     * <p>
     * Following resource paths are preconfigured:
     * <ul>
     * <li><code>/org.graalvm.python.vfs/venv</code> - is set as the python virtual environment
     * location</li>
     * <li><code>/org.graalvm.python.vfs/src</code> - is set as the python sources location</li>
     * </ul>
     * <p>
     * When the virtual filesystem is located in other than the default resource directory,
     * {@code org.graalvm.python.vfs}, i.e., using Maven or Gradle option {@code resourceDirectory},
     * use {@link #contextBuilder(VirtualFileSystem)} and
     * {@link VirtualFileSystem.Builder#resourceDirectory(String)} when building the
     * {@link VirtualFileSystem}.
     *
     * @return a new {@link Context} instance
     * @since 24.2.0
     */
    public static Context createContext() {
        return contextBuilder().build();
    }

    /**
     * Creates a GraalPy context builder preconfigured with a {@link VirtualFileSystem} and other
     * GraalPy and polyglot Context configuration options optimized for the usage of the
     * <a href="https://docs.python.org/3/library/venv.html">Python virtual environment</a>
     * contained in the virtual filesystem.
     * <p>
     * Following resource paths are preconfigured:
     * <ul>
     * <li><code>/org.graalvm.python.vfs/venv</code> - is set as the python virtual environment
     * location</li>
     * <li><code>/org.graalvm.python.vfs/src</code> - is set as the python sources location</li>
     * </ul>
     * </p>
     * <b>Example</b> creating a GraalPy context and overriding the verbose option.
     * 
     * <pre>
     * Context.Builder builder = GraalPyResources.contextBuilder().option("python.VerboseFlag", "true");
     * try (Context context = builder.build()) {
     *     context.eval("python", "print('hello world')");
     * } catch (PolyglotException e) {
     *     if (e.isExit()) {
     *         System.exit(e.getExitStatus());
     *     } else {
     *         throw e;
     *     }
     * }
     * </pre>
     * <p>
     * When the virtual filesystem is located in other than the default resource directory,
     * {@code org.graalvm.python.vfs}, i.e., using Maven or Gradle option {@code resourceDirectory},
     * use {@link #contextBuilder(VirtualFileSystem)} and
     * {@link VirtualFileSystem.Builder#resourceDirectory(String)} when building the
     * {@link VirtualFileSystem}.
     *
     * @see <a href=
     *      "https://github.com/oracle/graalpython/blob/master/graalpython/com.oracle.graal.python/src/com/oracle/graal/python/runtime/PythonOptions.java">PythonOptions</a>
     * @return a new {@link org.graalvm.polyglot.Context.Builder} instance
     * @since 24.2.0
     */
    public static Context.Builder contextBuilder() {
        VirtualFileSystem vfs = VirtualFileSystem.create();
        return contextBuilder(vfs);
    }

    /**
     * Creates a GraalPy context builder preconfigured with the given {@link VirtualFileSystem} and
     * other GraalPy and polygot Context configuration options optimized for the usage of the
     * <a href="https://docs.python.org/3/library/venv.html">Python virtual environment</a>
     * contained in the virtual filesystem.
     * <p>
     * Following resource paths are preconfigured:
     * <ul>
     * <li><code>/org.graalvm.python.vfs/venv</code> - is set as the python virtual environment
     * location</li>
     * <li><code>/org.graalvm.python.vfs/src</code> - is set as the python sources location</li>
     * </ul>
     * </p>
     * <p>
     * <b>Example</b> creating a GraalPy context configured for the usage with a virtual
     * {@link FileSystem}:
     * 
     * <pre>
     * VirtualFileSystem.Builder vfsBuilder = VirtualFileSystem.newBuilder();
     * vfsBuilder.unixMountPoint("/python-resources");
     * VirtualFileSystem vfs = vfsBuilder.build();
     * Context.Builder ctxBuilder = GraalPyResources.contextBuilder(vfs);
     * try (Context context = ctxBuilder.build()) {
     *     context.eval("python", "for line in open('/python-resources/data.txt').readlines(): print(line)");
     * } catch (PolyglotException e) {
     *     if (e.isExit()) {
     *         System.exit(e.getExitStatus());
     *     } else {
     *         throw e;
     *     }
     * }
     * </pre>
     *
     * In this example we:
     * <ul>
     * <li>create a {@link VirtualFileSystem} configured to have the root
     * <code>/python-resources</code></li>
     * <li>create a GraalPy context preconfigured with that {@link VirtualFileSystem}</li>
     * <li>use the context to invoke a python snippet reading a resource file</li>
     * </ul>
     * </p>
     *
     * @param vfs the {@link VirtualFileSystem} to be used with the created {@link Context}
     * @return a new {@link org.graalvm.polyglot.Context.Builder} instance
     * @see VirtualFileSystem
     * @see VirtualFileSystem.Builder
     *
     * @since 24.2.0
     */
    public static Context.Builder contextBuilder(VirtualFileSystem vfs) {
        return createContextBuilder().
        // allow access to the virtual and the host filesystem, as well as sockets
                        allowIO(IOAccess.newBuilder().allowHostSocketAccess(true).fileSystem(vfs.delegatingFileSystem).build()).
                        // The sys.executable path, a virtual path that is used by the interpreter
                        // to discover packages
                        option("python.Executable", vfs.impl.vfsVenvPath() + (VirtualFileSystemImpl.isWindows() ? "\\Scripts\\python.exe" : "/bin/python")).
                        // Set python path to point to sources stored in
                        // src/main/resources/org.graalvm.python.vfs/src
                        option("python.PythonPath", vfs.impl.vfsSrcPath()).
                        // pass the path to be executed
                        option("python.InputFilePath", vfs.impl.vfsSrcPath());
    }

    /**
     * Creates a GraalPy context preconfigured with GraalPy and polyglot Context configuration
     * options for use with resources located in an external directory in real filesystem.
     * <p>
     * Following resource paths are preconfigured:
     * <ul>
     * <li><code>${externalResourcesDirectory}/venv</code> - is set as the python virtual
     * environment location</li>
     * <li><code>${externalResourcesDirectory}/src</code> - is set as the python sources
     * location</li>
     * </ul>
     * </p>
     * <p>
     * <b>Example</b>
     *
     * <pre>
     * Context.Builder builder = GraalPyResources.contextBuilder(Path.of("python-resources"));
     * try (Context context = builder.build()) {
     *     context.eval("python", "import mymodule; mymodule.print_hello_world()");
     * } catch (PolyglotException e) {
     *     if (e.isExit()) {
     *         System.exit(e.getExitStatus());
     *     } else {
     *         throw e;
     *     }
     * }
     * </pre>
     *
     * In this example we:
     * <ul>
     * <li>create a GraalPy context which is preconfigured with GraalPy resources in an external
     * resource directory</li>
     * <li>use the context to import the python module <code>mymodule</code>, which should be either
     * located in <code>python-resources/src</code> or in a python package installed in
     * <code>/python/venv</code> (python virtual environment)</li>
     * <li>note that in this scenario, the Python context has access to the extracted resources as
     * well as the rest of the real filesystem</li>
     * </ul>
     * </p>
     *
     * <p>
     * External resources directory is often used for better compatibility with Python native
     * extensions that may bypass the Python abstractions and access the filesystem directly from
     * native code. Setting the {@code PosixModuleBackend} option to "native" increases the
     * compatibility further, but in such case even Python code bypasses the Truffle abstractions
     * and accesses native POSIX APIs directly. Usage:
     *
     * <pre>
     * GraalPyResources.contextBuilder(Path.of("python-resources")).option("python.PosixModuleBackend", "native")
     * </pre>
     * </p>
     *
     * <p/>
     * When Maven or Gradle GraalPy plugin is used to build the virtual environment, it also has to
     * be configured to generate the virtual environment into the same directory using the
     * {@code <externalDirectory>} tag in Maven or the {@code externalDirectory} field in Gradle.
     * <p/>
     *
     * @param externalResourcesDirectory the root directory with GraalPy specific embedding
     *            resources
     * @return a new {@link org.graalvm.polyglot.Context.Builder} instance
     * @since 24.2.0
     */
    public static Context.Builder contextBuilder(Path externalResourcesDirectory) {
        String execPath;
        if (VirtualFileSystemImpl.isWindows()) {
            execPath = externalResourcesDirectory.resolve(VirtualFileSystemImpl.VFS_VENV).resolve("Scripts").resolve("python.exe").toAbsolutePath().toString();
        } else {
            execPath = externalResourcesDirectory.resolve(VirtualFileSystemImpl.VFS_VENV).resolve("bin").resolve("python").toAbsolutePath().toString();
        }

        String srcPath = externalResourcesDirectory.resolve(VirtualFileSystemImpl.VFS_SRC).toAbsolutePath().toString();
        return createContextBuilder().
        // allow all IO access
                        allowIO(IOAccess.ALL).
                        // The sys.executable path, a virtual path that is used by the interpreter
                        // to discover packages
                        option("python.Executable", execPath).
                        // Set python path to point to sources stored in
                        // src/main/resources/org.graalvm.python.vfs/src
                        option("python.PythonPath", srcPath).
                        // pass the path to be executed
                        option("python.InputFilePath", srcPath);
    }

    private static Context.Builder createContextBuilder() {
        return Context.newBuilder().
        // set true to allow experimental options
                        allowExperimentalOptions(false).
                        // setting false will deny all privileges unless configured below
                        allowAllAccess(false).
                        // allows python to access the java language
                        allowHostAccess(HostAccess.ALL).
                        // allow creating python threads
                        allowCreateThread(true).
                        // allow running Python native extensions
                        allowNativeAccess(true).
                        // allow exporting Python values to polyglot bindings and accessing Java
                        // from Python
                        allowPolyglotAccess(PolyglotAccess.ALL).
                        // choose the backend for the POSIX module
                        option("python.PosixModuleBackend", "java").
                        // equivalent to the Python -B flag
                        option("python.DontWriteBytecodeFlag", "true").
                        // Force to automatically import site.py module, to make Python packages
                        // available
                        option("python.ForceImportSite", "true").
                        // causes the interpreter to always assume hash-based pycs are valid
                        option("python.CheckHashPycsMode", "never");
    }

    /**
     * Determines a native executable path if running in {@link ImageInfo#inImageRuntimeCode()}.
     * <p>
     * <b>Example </b> creating a GraalPy context preconfigured with an external resource directory
     * located next to a native image executable.
     *
     * <pre>
     * Path resourcesDir = GraalPyResources.getNativeExecutablePath().getParent().resolve("python-resources");
     * try (Context context = GraalPyResources.contextBuilder(resourcesDir).build()) {
     *     context.eval("python", "print('hello world')");
     * }
     * </pre>
     * </p>
     *
     * @return the native executable path if it could be retrieved, otherwise <code>null</code>.
     * @see #contextBuilder(Path)
     *
     * @since 24.2.0
     */
    public static Path getNativeExecutablePath() {
        if (ImageInfo.inImageRuntimeCode()) {
            String pn = null;
            if (ProcessProperties.getArgumentVectorBlockSize() > 0) {
                pn = ProcessProperties.getArgumentVectorProgramName();
            } else {
                pn = ProcessProperties.getExecutableName();
            }
            if (pn != null) {
                return Paths.get(pn).toAbsolutePath();
            }
        }
        return null;
    }

    /**
     * Extract Python resources which are distributed as part of a <b>jar file</b> or a <b>native
     * image</b> executable into a directory. This can be useful to manage and ship resources with
     * the Maven workflow, but use them (cached) from the real filesystem for better compatibility.
     * <p>
     * The structure of the created resource directory will stay the same like the embedded Python
     * resources structure:
     * <ul>
     * <li><code>${externalResourcesDirectory}/venv</code> - the python virtual environment
     * location</li>
     * <li><code>${externalResourcesDirectory}/src</code> - the python sources location</li>
     * </ul>
     * </p>
     * </p>
     * <p>
     * <b>Example</b>
     *
     * <pre>
     * Path resourcesDir = Path.of(System.getProperty("user.home"), ".cache", "my.java.python.app.resources");
     * VirtualFileSystem vfs = VirtualFileSystem.newBuilder().build();
     * GraalPyResources.extractVirtualFileSystemResources(vfs, resourcesDir);
     * try (Context context = GraalPyResources.contextBuilder(resourcesDir).build()) {
     *     context.eval("python", "print('hello world')");
     * }
     * </pre>
     * </p>
     *
     * @param vfs the {@link VirtualFileSystem} from which resources are to be extracted
     * @param externalResourcesDirectory the target directory to extract the resources to
     * @throws IOException if resources isn't a directory
     * @see #contextBuilder(Path)
     * @see VirtualFileSystem.Builder#resourceLoadingClass(Class)
     *
     * @since 24.2.0
     */
    public static void extractVirtualFileSystemResources(VirtualFileSystem vfs, Path externalResourcesDirectory) throws IOException {
        if (Files.exists(externalResourcesDirectory) && !Files.isDirectory(externalResourcesDirectory)) {
            throw new IOException(String.format("%s has to be a directory", externalResourcesDirectory.toString()));
        }
        vfs.impl.extractResources(externalResourcesDirectory);
    }
}
