/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.python.embedding.utils;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.ProcessProperties;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.IOAccess;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * This class provides utilities related to Python resources used in GraalPy embedding scenarios.
 * <p>
 * Resource files can be embedded and distributed in an <b>application file</b> or made available
 * from an <b>external directory</b>.
 * </p>
 *
 * <p>
 * If the resource files are part of a <b>jar file</b> or a <b>native image</b> executable, then at
 * runtime they will be accessed as standard Java resources through GraalPy
 * {@link VirtualFileSystem}. This will be transparent to Python code running in GraalPy so that it
 * can use standard Python IO to access those files. Note that in order to make this work, it is
 * necessary for those embedded resources to have their <b>root directory</b> set to
 * <code>/org.graalvm.python.vfs</code> which in python code will be mapped to the virtual
 * filesystem mount point, by default <code>/graalpy_vfs</code>. Refer to
 * {@link VirtualFileSystem.Builder} documentation for more details.
 * </p>
 * <p>
 * <b>Example</b> creating a GraalPy context configured for the usage with a
 * {@link VirtualFileSystem}:
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
 * <li><code>${resources_root_directory}/home</code> - is reserved for the GraalPy Standard Library.
 * GraalPy context will be configured to use this standard library as if set in PYTHONHOME
 * environment variable.</li>
 * <li><code>${resources_root_directory}/venv</code> - is reserved for a python virtual environment
 * holding third-party packages. The context will be configured as if it were executed from this
 * virtual environment. Notably packages installed in this virtual environment will be automatically
 * available for importing.</li>
 * <li><code>${resources_root_directory}/src</code> - is reserved for python application files -
 * e.g. python sources. GraalPy context will be configured to see those files as if set in
 * PYTHONPATH environment variable.</li>
 * </ul>
 * where <code>${resources_root_directory}</code> is either an external directory or the virtual
 * filesystem resource root <code>/org.graalvm.python.vfs</code>.
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
 * @see VirtualFileSystem
 * @see VirtualFileSystem.Builder
 */
// TODO: link to user guide
public class GraalPyResources {

    /**
     * Creates a GraalPy context preconfigured with a {@link VirtualFileSystem} and other GraalPy
     * and polyglot Context configuration options optimized for the usage of the Python virtual
     * environment contained in the virtual filesystem.
     * <p>
     * Following resource paths are preconfigured:
     * <ul>
     * <li><code>/org.graalvm.python.vfs/home</code> - is set as the GraalPy Standard Library
     * location</li>
     * <li><code>/org.graalvm.python.vfs/venv</code> - is set as the python virtual environment
     * location</li>
     * <li><code>/org.graalvm.python.vfs/src</code> - is set as the python sources location</li>
     * </ul>
     * </p>
     */
    public static Context createContext() {
        return contextBuilder().build();
    }

    /**
     * Creates a GraalPy context builder preconfigured with a {@link VirtualFileSystem} and other
     * GraalPy and polyglot Context configuration options optimized for the usage of the Python
     * virtual environment contained in the virtual filesystem.
     * <p>
     * Following resource paths are preconfigured:
     * <ul>
     * <li><code>/org.graalvm.python.vfs/home</code> - is set as the GraalPy Standard Library
     * location</li>
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
     *
     */
    // TODO add link to python options doc
    public static Context.Builder contextBuilder() {
        VirtualFileSystem vfs = VirtualFileSystem.create();
        return contextBuilder(vfs);
    }

    /**
     * Creates a GraalPy context builder preconfigured with the given {@link VirtualFileSystem} and
     * other GraalPy and polygot Context configuration options optimized for the usage of the Python
     * virtual environment contained in the virtual filesystem.
     * <p>
     * Following resource paths are preconfigured:
     * <ul>
     * <li><code>/org.graalvm.python.vfs/home</code> - is set as the GraalPy Standard Library
     * location</li>
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
     * @see VirtualFileSystem
     * @see VirtualFileSystem.Builder
     * 
     */
    public static Context.Builder contextBuilder(VirtualFileSystem vfs) {
        return createContextBuilder().
        // allow access to the virtual and the host filesystem, as well as sockets
                        allowIO(IOAccess.newBuilder().allowHostSocketAccess(true).fileSystem(vfs).build()).
                        // The sys.executable path, a virtual path that is used by the interpreter
                        // to discover packages
                        option("python.Executable", vfs.vfsVenvPath() + (VirtualFileSystem.isWindows() ? "\\Scripts\\python.exe" : "/bin/python")).
                        // Set the python home to be read from the embedded resources
                        option("python.PythonHome", vfs.vfsHomePath()).
                        // Set python path to point to sources stored in
                        // src/main/resources/org.graalvm.python.vfs/src
                        option("python.PythonPath", vfs.vfsSrcPath() + File.pathSeparator + vfs.vfsProjPath()).
                        // pass the path to be executed
                        option("python.InputFilePath", vfs.vfsSrcPath());
    }

    /**
     * Creates a GraalPy context preconfigured with GraalPy and polyglot Context configuration
     * options for use with resources located in a real filesystem.
     * <p>
     * Following resource paths are preconfigured:
     * <ul>
     * <li><code>${resourcesDirectory}/home</code> - is set as the GraalPy Standard Library
     * location</li>
     * <li><code>${resourcesDirectory}/venv</code> - is set as the python virtual environment
     * location</li>
     * <li><code>${resourcesDirectory}/src</code> - is set as the python sources location</li>
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
     * </ul>
     * </p>
     * 
     * @param resourcesDirectory the root directory with GraalPy specific embedding resources
     */
    public static Context.Builder contextBuilder(Path resourcesDirectory) {
        String execPath = resourcesDirectory.resolve(VirtualFileSystem.VFS_VENV + "/bin/python").toAbsolutePath().toString();
        String homePath = resourcesDirectory.resolve(VirtualFileSystem.VFS_HOME).toAbsolutePath().toString();
        String srcPath = resourcesDirectory.resolve(VirtualFileSystem.VFS_SRC).toAbsolutePath().toString();
        return createContextBuilder().
        // allow all IO access
                        allowIO(IOAccess.ALL).
                        // The sys.executable path, a virtual path that is used by the interpreter
                        // to discover packages
                        option("python.Executable", execPath).
                        // Set the python home to be read from the embedded resources
                        option("python.PythonHome", homePath).
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
     * <b>Example </b> creating a GraalPy context precofigured with an external resource directory
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
     * <li><code>${resourcesDirectory}/home</code> - the GraalPy Standard Library location</li>
     * <li><code>${resourcesDirectory}/venv</code> - the python virtual environment location</li>
     * <li><code>${resourcesDirectory}/src</code> - the python sources location</li>
     * </ul>
     * </p>
     * </p>
     * <p>
     * <b>Example</b>
     * 
     * <pre>
     * Path resourcesDir = Path.of(System.getProperty("user.home"), ".cache", "my.java.python.app.resources");
     * FileSystem fs = GraalPyResources.createVirtualFileSystem();
     * GraalPyResources.extractVirtualFileSystemResources(fs, resourcesDir);
     * try (Context context = GraalPyResources.contextBuilder(resourcesDir).build()) {
     *     context.eval("python", "print('hello world')");
     * }
     * </pre>
     * </p>
     *
     * @see #contextBuilder(Path)
     * @see VirtualFileSystem.Builder#resourceLoadingClass(Class)
     */
    public static void extractVirtualFileSystemResources(VirtualFileSystem vfs, Path resourcesDirectory) throws IOException {
        if (Files.exists(resourcesDirectory) && !Files.isDirectory(resourcesDirectory)) {
            throw new IOException(String.format("%s has to be a directory", resourcesDirectory.toString()));
        }
        vfs.extractResources(resourcesDirectory);
    }
}
