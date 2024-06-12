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
import java.util.function.Predicate;

/**
 * This class provides utilities related to GraalPy specific resources used in embedding
 * scenarios.</br>
 * <p>
 * <ul>
 * <li>The GraalPy standard library</li>
 * <li>The python virtual environment providing third party python packages</li>
 * <li>Additional python files</li>
 * </ul>
 * </p>
 *
 * Resource files can be distributed either in a jar file or as part of a native image executable or
 * in an external filesystem directory.
 * </ul>
 *
 * <h3>Virtual File System</h3>
 * <p>
 * Resources distributed in one application file (either jar or native image) are accessed in
 * runtime through a virtual {@link FileSystem} and expected to have the root directory
 * <code>/org.graalvm.python.vfs</code>, where then:<br/>
 * </p>
 * <ul>
 * <li><code>/org.graalvm.python.vfs/home</code> - is the directory with the GraalPy standard
 * library</li>
 * <li><code>/org.graalvm.python.vfs/venv</code> - is the directory with a python virtual
 * environment holding third-party packages</li>
 * <li><code>/org.graalvm.python.vfs/src</code> - is the directory with additional user files - e.g.
 * python sources files</li>
 * </ul>
 * </p>
 * <b>Example</b> creating a GraalPy context configured for the usage with a virtual
 * {@link FileSystem}:
 * 
 * <pre>
 * try (Context context = GraalPyResources.createContext()) {
 *     context.eval("python", "print('Hello World')");
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
 * <li>create a GraalPy context preconfigured with a virtual {@link FileSystem}</li>
 * <li>use the context to invoke a python snippet</li>
 * </ul>
 *
 * <h3>External resource directory</h3>
 * <p>
 * Instead of distributing GraalPy embedding resources embedded in one application file, it is also
 * possible to keep them in a external directory with the same sub-structure as in the case of a
 * virtual {@link FileSystem}:
 * </p>
 * <ul>
 * <li><code>{resourcesRootDirectory}/home</code> - is the directory with the GraalPy standard
 * library</li>
 * <li><code>{resourcesRootDirectory}/venv</code> - is the directory with a python virtual
 * environment holding third-party packages</li>
 * <li><code>{resourcesRootDirectory}/src</code> - is the directory with additional user files -
 * e.g. python sources files</li>
 * </ul>
 *
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
 * located in <code>/python/src</code> or in a python package installed in <code>/python/venv</code>
 * (python virtual environment)</li>
 * </ul>
 *
 */
public class GraalPyResources {

    /**
     * Creates a GraalPy context preconfigured with a virtual filesystem and other GraalPy and
     * polyglot Context configuration options optimized for the usage of the Python virtual
     * environment contained in the virtual filesystem.
     */
    public static Context createContext() {
        return contextBuilder().build();
    }

    /**
     * Creates a GraalPy context builder preconfigured with a virtual filesystem and other GraalPy
     * and polyglot Context configuration options optimized for the usage of the Python virtual
     * environment contained in the virtual filesystem.
     */
    public static Context.Builder contextBuilder() {
        VirtualFileSystemImpl vfs = (VirtualFileSystemImpl) createVirtualFileSystem();
        return contextBuilder(vfs);
    }

    /**
     * Creates a GraalPy context builder preconfigured with the given virtual filesystem and other
     * GraalPy and polygot Context configuration options optimized for the usage of the Python
     * virtual environment contained in the virtual filesystem.
     */
    public static Context.Builder contextBuilder(FileSystem fs) {
        assert fs instanceof VirtualFileSystemImpl;
        VirtualFileSystemImpl vfs = (VirtualFileSystemImpl) fs;
        return createContextBuilder().
        // allow access to the virtual and the host filesystem, as well as sockets
                        allowIO(IOAccess.newBuilder().allowHostSocketAccess(true).fileSystem(vfs).build()).
                        // The sys.executable path, a virtual path that is used by the interpreter
                        // to discover packages
                        option("python.Executable", vfs.vfsVenvPath() + (VirtualFileSystemImpl.isWindows() ? "\\Scripts\\python.exe" : "/bin/python")).
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
     *
     * @param resourcesPath the root directory with GraalPy specific embedding resources
     */
    public static Context.Builder contextBuilder(Path resourcesPath) {
        String execPath = resourcesPath.resolve(VirtualFileSystemImpl.VFS_VENV + "/bin/python").toAbsolutePath().toString();
        String homePath = resourcesPath.resolve(VirtualFileSystemImpl.VFS_HOME).toAbsolutePath().toString();
        String srcPath = resourcesPath.resolve(VirtualFileSystemImpl.VFS_SRC).toAbsolutePath().toString();
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
                        // equivalent to the Python -v flag
                        option("python.VerboseFlag", System.getenv("PYTHONVERBOSE") != null ? "true" : "false").
                        // log level
                        option("log.python.level", System.getenv("PYTHONVERBOSE") != null ? "FINE" : "SEVERE").
                        // equivalent to setting the PYTHONWARNINGS environment variable
                        option("python.WarnOptions", System.getenv("PYTHONWARNINGS") == null ? "" : System.getenv("PYTHONWARNINGS")).
                        // print Python exceptions directly
                        option("python.AlwaysRunExcepthook", "true").
                        // Force to automatically import site.py module, to make Python packages
                        // available
                        option("python.ForceImportSite", "true").
                        // Do not warn if running without JIT. This can be desirable for short
                        // running scripts to reduce memory footprint.
                        option("engine.WarnInterpreterOnly", "false").
                        // causes the interpreter to always assume hash-based pycs are valid
                        option("python.CheckHashPycsMode", "never");
    }

    public static final class VirtualFileSystemBuilder {
        private static final Predicate<Path> DEFAULT_EXTRACT_FILTER = (p) -> {
            var s = p.toString();
            return s.endsWith(".so") || s.endsWith(".dylib") || s.endsWith(".pyd") || s.endsWith(".dll") || s.endsWith(".ttf");
        };

        private String windowsMountPoint = "X:\\graalpy_vfs";
        private String unixMountPoint = "/graalpy_vfs";
        private Predicate<Path> extractFilter = DEFAULT_EXTRACT_FILTER;
        private VirtualFileSystemImpl.HostIO allowHostIO = VirtualFileSystemImpl.HostIO.READ_WRITE;
        private boolean caseInsensitive = VirtualFileSystemImpl.isWindows();

        private Class<?> resourceLoadingClass;

        private VirtualFileSystemBuilder() {
        }

        /**
         * Sets the file system to be case-insensitive. Defaults to true on Windows and false
         * elsewhere.
         */
        public VirtualFileSystemBuilder caseInsensitive(boolean value) {
            caseInsensitive = value;
            return this;
        }

        /**
         * Determines if and how much host IO is allowed outside of the virtual filesystem.
         */
        public VirtualFileSystemBuilder allowHostIO(VirtualFileSystemImpl.HostIO b) {
            allowHostIO = b;
            return this;
        }

        /**
         * The mount point for the virtual filesystem on Windows. This mount point shadows any real
         * filesystem, so should be chosen to avoid clashes with the users machine, e.g. if set to
         * "X:\graalpy_vfs", then a resource with path /org.graalvm.python.vfs/xyz/abc is visible as
         * "X:\graalpy_vfs\xyz\abc". This needs to be an absolute path with platform-specific
         * separators without any trailing separator. If that file or directory actually exists, it
         * will not be accessible.
         */
        public VirtualFileSystemBuilder windowsMountPoint(String s) {
            windowsMountPoint = s;
            return this;
        }

        /**
         * The mount point for the virtual filesystem on Unices. This mount point shadows any real
         * filesystem, so should be chosen to avoid clashes with the users machine, e.g. if set to
         * "/graalpy_vfs", then a resource with path /org.graalvm.python.vfs/xyz/abc is visible as
         * "/graalpy_vfs/xyz/abc". This needs to be an absolute path with platform-specific
         * separators without any trailing separator. If that file or directory actually exists, it
         * will not be accessible.
         */
        public VirtualFileSystemBuilder unixMountPoint(String s) {
            unixMountPoint = s;
            return this;
        }

        /**
         * By default, virtual filesystem resources are loaded by delegating to
         * VirtualFileSystem.class.getResource(name). Use resourceLoadingClass to determine where to
         * locate resources in cases when for example VirtualFileSystem is on module path and the
         * jar containing the resources is on class path.
         */
        public VirtualFileSystemBuilder resourceLoadingClass(Class<?> c) {
            resourceLoadingClass = c;
            return this;
        }

        /**
         * This filter applied to files in the virtual filesystem treats them as symlinks to real
         * files in the host filesystem. This is useful, for example, if files in the virtual
         * filesystem need to be accessed outside the Truffle sandbox. They will be extracted to the
         * Java temporary directory on demand. The default filter matches any DLLs, dynamic
         * libraries, shared objects, and Python C extension files, because these need to be
         * accessed by the operating system loader. Setting this filter to <code>null</code> denies
         * any extraction. Any other filter is combined with the default filter.
         */
        public VirtualFileSystemBuilder extractFilter(Predicate<Path> filter) {
            if (extractFilter == null) {
                extractFilter = null;
            } else {
                extractFilter = (p) -> filter.test(p) || DEFAULT_EXTRACT_FILTER.test(p);
            }
            return this;
        }

        public FileSystem build() {
            return new VirtualFileSystemImpl(extractFilter, windowsMountPoint, unixMountPoint, allowHostIO, resourceLoadingClass, caseInsensitive);
        }
    }

    /**
     * Creates a builder for constructing a virtual {@link FileSystem} usable with graalpy
     * embedding.
     */
    public static VirtualFileSystemBuilder virtualFileSystemBuilder() {
        return new VirtualFileSystemBuilder();
    }

    /**
     * Creates a virtual {@link FileSystem} usable with graalpy embedding.
     */
    public static FileSystem createVirtualFileSystem() {
        return virtualFileSystemBuilder().build();
    }

    /**
     * The mount point for a virtual filesystem.
     *
     * @see VirtualFileSystemBuilder#windowsMountPoint(String)
     * @see VirtualFileSystemBuilder#unixMountPoint(String)
     */
    public static String getMountPoint(FileSystem fs) {
        assert fs instanceof VirtualFileSystemImpl : "only filessytems created with VirtualFileSystemBuilder are accepted";
        return ((VirtualFileSystemImpl) fs).getMountPoint();
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
     * Extract the contents of the given virtual filesystem into a directory. This can be useful to
     * manage and ship resources with the Maven workflow, but use them (cached) from the real
     * filesystem for better compatibility.
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
     * @see #getNativeExecutablePath()
     */
    public static void extractVirtualFileSystemResources(FileSystem fs, Path destDir) throws IOException {
        assert fs instanceof VirtualFileSystemImpl : "can extract resources only from filessytems created with VirtualFileSystemBuilder";
        if (Files.exists(destDir) && !Files.isDirectory(destDir)) {
            throw new IOException(String.format("%s has to be a directory", destDir.toString()));
        }
        ((VirtualFileSystemImpl) fs).extractResources(destDir);
    }
}
