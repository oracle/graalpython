/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.graalvm.polyglot.io.FileSystem;

/**
 * The GraalPy Virtual Filesystem accesses embedded resource files as standard Java resources and
 * makes them available to Python code running in GraalPy.
 *
 * @see GraalPyResources for more information on Python resources in GraalPy embedding and how to
 *      use the {@link VirtualFileSystem} together with a GraalPy context.
 *
 * @since 24.2.0
 */
public final class VirtualFileSystem implements AutoCloseable {

    final VirtualFileSystemImpl impl;
    final FileSystem delegatingFileSystem;

    /**
     * Determines if and how much host IO is allowed outside the {@link VirtualFileSystem}.
     *
     * @since 24.2.0
     */
    public static enum HostIO {
        /**
         * No host IO allowed.
         *
         * @since 24.2.0
         */
        NONE,
        /**
         * Only read access allowed.
         *
         * @since 24.2.0
         */
        READ,
        /**
         * Read and write access is allowed.
         *
         * @since 24.2.0
         */
        READ_WRITE,
    }

    /**
     * Builder class to create {@link VirtualFileSystem} instances.
     *
     * @since 24.2.0
     */
    public static final class Builder {
        private static final Pattern DEFAULT_EXTRACT_REGEX = Pattern.compile(".*(\\.(so|dylib|pyd|dll|ttf)$|\\.so\\..*)");
        private static final Predicate<Path> DEFAULT_EXTRACT_FILTER = (p) -> DEFAULT_EXTRACT_REGEX.matcher(p.toString()).matches();

        private static final String DEFAULT_WINDOWS_MOUNT_POINT = "X:\\graalpy_vfs";
        private static final String DEFAULT_UNIX_MOUNT_POINT = "/graalpy_vfs";
        private Path mountPoint;
        private Predicate<Path> extractFilter = DEFAULT_EXTRACT_FILTER;
        private HostIO allowHostIO = HostIO.READ_WRITE;
        private boolean caseInsensitive = VirtualFileSystemImpl.isWindows();

        private Class<?> resourceLoadingClass;
        private String resourceDirectory;

        private Builder() {
        }

        /**
         * Sets the root directory of the virtual filesystem within Java resources. The default
         * value is {@code "org.graalvm.python.vfs"}. This Java resources directory will be
         * accessible as {@link #unixMountPoint(String)} or {@link #windowsMountPoint(String)} from
         * Python code. The recommended convention is to use
         * {@code GRAALPY-VFS/{groupId}/{artifactId}}.
         * <p/>
         * User scripts, data files, and other resources that should be accessible in Python should
         * be put into this resource directory, e.g.,
         * {@code src/main/resources/org.graalvm.python.vfs/src} where:
         * <ul>
         * <li>assuming the usual layout of a Maven or Gradle project then the
         * {@code src/main/resources/org.graalvm.python.vfs} prefix is the default value of the
         * {@code resourceDirectory} option</li>
         * <li>and the following {@code src} directory is the folder used by {@link GraalPyResources
         * convention} for Python application files and is configured as the default search path for
         * Python module files.</i>
         * </ul>
         * <p/>
         * When Maven or Gradle GraalPy plugin is used to build the virtual environment, it should
         * be configured to generate the virtual environment into the same directory using the
         * {@code <resourceDirectory>} tag in Maven or the {@code resourceDirectory} field in
         * Gradle.
         * <p/>
         * Note regarding Java module system: resources in named modules are subject to the
         * encapsulation rules. This is also the case of the default virtual filesystem location.
         * When a resources directory is not a valid Java package name, such as the recommended
         * "GRAALPY-VFS", the resources are not subject to the encapsulation rules and do not
         * require additional module system configuration.
         * <p/>
         * The value must be relative resources path, i.e., not starting with `/`, and must use '/'
         * as path separator regardless of the host OS.
         *
         * @since 24.2.0
         */
        public Builder resourceDirectory(String directory) {
            if (directory.startsWith("/")) {
                throw new IllegalArgumentException("Use relative resources path, i.e., not starting with '/'.");
            }
            this.resourceDirectory = directory;
            return this;
        }

        /**
         * Sets the file system to be case-insensitive. Defaults to true on Windows and false
         * elsewhere.
         *
         * @since 24.2.0
         */
        public Builder caseInsensitive(boolean value) {
            caseInsensitive = value;
            return this;
        }

        /**
         * Determines if and how much host IO is allowed outside the {@link VirtualFileSystem}.
         *
         * @since 24.2.0
         */
        public Builder allowHostIO(HostIO b) {
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
         *
         * @throws IllegalArgumentException if the provided mount point isn't absolute or ends with
         *             a trailing separator
         * @since 24.2.0
         */
        public Builder windowsMountPoint(String windowsMountPoint) {
            if (VirtualFileSystemImpl.isWindows()) {
                this.mountPoint = getMountPointAsPath(windowsMountPoint);
            }
            return this;
        }

        /**
         * The mount point for the virtual filesystem on Unices. This mount point shadows any real
         * filesystem, so should be chosen to avoid clashes with the users machine, e.g. if set to
         * "/graalpy_vfs", then a resource with path /org.graalvm.python.vfs/xyz/abc is visible as
         * "/graalpy_vfs/xyz/abc". This needs to be an absolute path with platform-specific
         * separators without any trailing separator. If that file or directory actually exists, it
         * will not be accessible.
         *
         * @throws IllegalArgumentException if the provided mount point isn't absolute or ends with
         *             a trailing separator
         * @since 24.2.0
         */
        public Builder unixMountPoint(String unixMountPoint) {
            if (!VirtualFileSystemImpl.isWindows()) {
                this.mountPoint = getMountPointAsPath(unixMountPoint);
            }
            return this;
        }

        /**
         * By default, virtual filesystem resources are loaded by delegating to
         * <code>VirtualFileSystem.class.getResource(name)</code>. Use
         * <code>resourceLoadingClass</code> to determine where to locate resources in cases when
         * for example <code>VirtualFileSystem</code> is on module path and the jar containing the
         * resources is on class path.
         *
         * @since 24.2.0
         */
        public Builder resourceLoadingClass(Class<?> c) {
            resourceLoadingClass = c;
            return this;
        }

        /**
         * This filter applied to files in the virtual filesystem treats them as symlinks to real
         * files in the host filesystem. This is useful, for example, if files in the virtual
         * filesystem need to be accessed outside the Truffle IO virtualization. They will be
         * extracted to the Java temporary directory when first accessed. Matching files belonging
         * to the same wheel are extracted together. The default filter matches any DLLs, dynamic
         * libraries, shared objects, and Python C extension files, because these need to be
         * accessed by the operating system loader. Setting this filter to <code>null</code> denies
         * any extraction. Any other filter is combined with the default filter.
         *
         * @param filter the extraction filter, where the provided path is an absolute path from the
         *            VirtualFileSystem.
         * @since 24.2.0
         */
        public Builder extractFilter(Predicate<Path> filter) {
            if (filter == null) {
                extractFilter = null;
            } else {
                extractFilter = (p) -> filter.test(p) || DEFAULT_EXTRACT_FILTER.test(p);
            }
            return this;
        }

        /**
         * Build a new {@link VirtualFileSystem} instance from the configuration provided in the
         * builder.
         *
         * @since 24.2.0
         */
        public VirtualFileSystem build() {
            if (mountPoint == null) {
                mountPoint = VirtualFileSystemImpl.isWindows() ? Path.of(DEFAULT_WINDOWS_MOUNT_POINT) : Path.of(DEFAULT_UNIX_MOUNT_POINT);
            }
            return new VirtualFileSystem(extractFilter, mountPoint, allowHostIO, resourceLoadingClass, resourceDirectory, caseInsensitive);
        }
    }

    private static Path getMountPointAsPath(String mp) {
        Path mountPoint = Path.of(mp);
        if (mp.endsWith(VirtualFileSystemImpl.PLATFORM_SEPARATOR) || !mountPoint.isAbsolute()) {
            throw new IllegalArgumentException(String.format("Virtual filesystem mount point must be set to an absolute path without a trailing separator: '%s'", mp));
        }
        return mountPoint;
    }

    private VirtualFileSystem(Predicate<Path> extractFilter,
                    Path mountPoint,
                    HostIO allowHostIO,
                    Class<?> resourceLoadingClass,
                    String resourceDirectory,
                    boolean caseInsensitive) {

        this.impl = new VirtualFileSystemImpl(extractFilter, mountPoint, resourceDirectory, allowHostIO, resourceLoadingClass, caseInsensitive);
        this.delegatingFileSystem = VirtualFileSystemImpl.createDelegatingFileSystem(impl);
    }

    /**
     * Creates a builder for constructing a {@link VirtualFileSystem} with a custom configuration.
     *
     * @since 24.2.0
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Creates a {@link VirtualFileSystem}.
     *
     * @since 24.2.0
     */
    public static VirtualFileSystem create() {
        return newBuilder().build();
    }

    /**
     * Returns the mount point for this {@link VirtualFileSystem}.
     *
     * @see VirtualFileSystem.Builder#windowsMountPoint(String)
     * @see VirtualFileSystem.Builder#unixMountPoint(String)
     *
     * @since 24.2.0
     */
    public String getMountPoint() {
        return this.impl.mountPoint.toString();
    }

    /**
     * Closes the VirtualFileSystem and frees up potentially allocated resources.
     *
     * @throws IOException if the resources could not be freed.
     */
    @Override
    public void close() throws IOException {
        impl.close();
    }

}
