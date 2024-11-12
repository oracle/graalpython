/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.Path;
import java.util.function.Predicate;

public final class VirtualFileSystem {

    final VirtualFileSystemImpl impl;

    public static enum HostIO {
        NONE,
        READ,
        READ_WRITE,
    }

    /**
     * Builder class to create {@link VirtualFileSystem} instances.
     */
    public static final class Builder {
        private static final Predicate<Path> DEFAULT_EXTRACT_FILTER = (p) -> {
            var s = p.toString();
            return s.endsWith(".so") || s.endsWith(".dylib") || s.endsWith(".pyd") || s.endsWith(".dll") || s.endsWith(".ttf");
        };

        private static final String DEFAULT_WINDOWS_MOUNT_POINT = "X:\\graalpy_vfs";
        private static final String DEFAULT_UNIX_MOUNT_POINT = "/graalpy_vfs";
        private Path mountPoint;
        private Predicate<Path> extractFilter = DEFAULT_EXTRACT_FILTER;
        private HostIO allowHostIO = HostIO.READ_WRITE;
        private boolean caseInsensitive = VirtualFileSystemImpl.isWindows();

        private Class<?> resourceLoadingClass;

        private Builder() {
        }

        /**
         * Sets the file system to be case-insensitive. Defaults to true on Windows and false
         * elsewhere.
         */
        public Builder caseInsensitive(boolean value) {
            caseInsensitive = value;
            return this;
        }

        /**
         * Determines if and how much host IO is allowed outside of the virtual filesystem.
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
         */
        public Builder windowsMountPoint(String s) {
            if (VirtualFileSystemImpl.isWindows()) {
                mountPoint = getMountPointAsPath(s);
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
         */
        public Builder unixMountPoint(String s) {
            if (!VirtualFileSystemImpl.isWindows()) {
                mountPoint = getMountPointAsPath(s);
            }
            return this;
        }

        /**
         * By default, virtual filesystem resources are loaded by delegating to
         * <code>VirtualFileSystem.class.getResource(name)</code>. Use
         * <code>resourceLoadingClass</code> to determine where to locate resources in cases when
         * for example <code>VirtualFileSystem</code> is on module path and the jar containing the
         * resources is on class path.
         */
        public Builder resourceLoadingClass(Class<?> c) {
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
        public Builder extractFilter(Predicate<Path> filter) {
            if (extractFilter == null) {
                extractFilter = null;
            } else {
                extractFilter = (p) -> filter.test(p) || DEFAULT_EXTRACT_FILTER.test(p);
            }
            return this;
        }

        public VirtualFileSystem build() {
            if (mountPoint == null) {
                mountPoint = VirtualFileSystemImpl.isWindows() ? Path.of(DEFAULT_WINDOWS_MOUNT_POINT) : Path.of(DEFAULT_UNIX_MOUNT_POINT);
            }
            return new VirtualFileSystem(extractFilter, mountPoint, allowHostIO, resourceLoadingClass, caseInsensitive);
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
                    boolean caseInsensitive) {

        this.impl = new VirtualFileSystemImpl(extractFilter, mountPoint, allowHostIO, resourceLoadingClass, caseInsensitive);
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static VirtualFileSystem create() {
        return newBuilder().build();
    }

    /**
     * The mount point for the virtual filesystem.
     *
     * @see VirtualFileSystem.Builder#windowsMountPoint(String)
     * @see VirtualFileSystem.Builder#unixMountPoint(String)
     */
    public String getMountPoint() {
        return this.impl.mountPoint.toString();
    }

}
