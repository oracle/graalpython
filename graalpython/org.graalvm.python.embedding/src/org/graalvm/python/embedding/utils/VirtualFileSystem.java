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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystemException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;

import org.graalvm.polyglot.io.FileSystem;

public final class VirtualFileSystem implements FileSystem, AutoCloseable {
    public static enum HostIO {
        NONE,
        READ,
        READ_WRITE,
    }

    public static final class Builder {
        private static final Predicate<Path> DEFAULT_EXTRACT_FILTER = (p) -> {
            var s = p.toString();
            return s.endsWith(".so") || s.endsWith(".dylib") || s.endsWith(".pyd") || s.endsWith(".dll");
        };

        private String vfsPrefix = "/vfs";
        private String filesListPath = vfsPrefix + "/fileslist.txt";
        private String windowsMountPoint = "X:\\graalpy_vfs";
        private String unixMountPoint = "/graalpy_vfs";
        private Predicate<Path> extractFilter = DEFAULT_EXTRACT_FILTER;
        private HostIO allowHostIO = HostIO.READ_WRITE;

        private Class<?> resourceLoadingClass;

        private Builder() {
        }

        /**
         * The path in the Java resources to the virtual filesystem.
         */
        public Builder vfsPrefix(String s) {
            vfsPrefix = s;
            filesListPath = vfsPrefix + "/fileslist.txt";
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
         * The resource path to a file that lists all files and directories under the
         * {@link #vfsPrefix}.
         */
        public Builder filesListPath(String s) {
            filesListPath = s;
            return this;
        }

        /**
         * The mount point for the virtual filesystem on Windows. This mount point shadows any real
         * filesystem, so should be chosen to avoid clashes with the users machine.
         */
        public Builder windowsMountPoint(String s) {
            windowsMountPoint = s;
            return this;
        }

        /**
         * The mount point for the virtual filesystem on Unices. This mount point shadows any real
         * filesystem, so should be chosen to avoid clashes with the users machine.
         */
        public Builder unixMountPoint(String s) {
            unixMountPoint = s;
            return this;
        }

        /**
         * By default virtual filesystem resources are loaded by delegating to
         * VirtualFileSystem.class.getResource(name). Use resourceLoadingClass to determine where to
         * locate resources in cases when for example VirtualFileSystem is on module path and the
         * jar containing the resources is on class path.
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
            return new VirtualFileSystem(extractFilter, vfsPrefix, filesListPath, windowsMountPoint, unixMountPoint, allowHostIO, resourceLoadingClass);
        }
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static VirtualFileSystem create() {
        return newBuilder().build();
    }

    /*
     * Root of the virtual filesystem in the resources.
     */
    private final String vfsPrefix;

    /*
     * Index of all files and directories available in the resources at runtime. - paths are
     * absolute - directory paths end with a '/' - uses '/' separator regardless of platform. Used
     * to determine directory entries, if an entry is a file or a directory, etc.
     */
    private final String filesListPath;

    /*
     * Maps platform-specific paths to entries.
     */
    private final TreeMap<String, Entry> vfsEntries = new TreeMap<>();

    /*
     * These use '/' as the separator and start with VFS_PREFIX, no trailing slashes.
     */
    private static Set<String> filesList;

    /**
     * Class used to read resources with getResource(name). By default VirtualFileSystem.class.
     */
    private Class<?> resourceLoadingClass;

    private static Set<String> dirsList;
    private static Map<String, String> lowercaseToResourceMap;

    private final FileSystem delegate;

    private static final String PLATFORM_SEPARATOR = Paths.get("").getFileSystem().getSeparator();
    private static final char RESOURCE_SEPARATOR_CHAR = '/';
    private static final String RESOURCE_SEPARATOR = String.valueOf(RESOURCE_SEPARATOR_CHAR);

    /*
     * For files, `data` is a byte[], for directories it is a Path[] which contains
     * platform-specific paths.
     */
    private static final record Entry(boolean isFile, Object data) {
    }

    /*
     * Determines where the virtual filesystem lives in the real filesystem, e.g. if set to
     * "X:\graalpy_vfs", then a resource with path /vfs/xyz/abc is visible as
     * "X:\graalpy_vfs\xyz\abc". This needs to be an absolute path with platform-specific separators
     * without any trailing separator. If that file or directory actually exists, it will not be
     * accessible.
     */
    private final Path mountPoint;

    /**
     * The temporary directory where to extract files/directories to.
     */
    private final Path extractDir;

    /**
     * A filter to determine if a path should be extracted (see {@link #shouldExtract(Path)}).
     */
    private final Predicate<Path> extractFilter;
    private static final boolean caseInsensitive = isWindows();

    private static final class DeleteTempDir extends Thread {
        private final Path extractDir;

        public DeleteTempDir(Path extractDir) {
            this.extractDir = extractDir;
        }

        private static final SimpleFileVisitor<Path> deleteVisitor = new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        };

        @Override
        public void run() {
            removeExtractDir();
        }

        private void removeExtractDir() {
            if (extractDir != null && Files.exists(extractDir)) {
                try {
                    Files.walkFileTree(extractDir, deleteVisitor);
                } catch (IOException e) {
                    System.err.format("Could not delete temp directory '%s': %s", extractDir, e);
                }
            }
        }
    }

    private final DeleteTempDir deleteTempDir;

    /**
     * If an extract filter is given, the virtual file system will lazily extract files and
     * directories matching the filter to a temporary directory. This happens if the
     * {@link #toAbsolutePath(Path) absolute path} is computed. This argument may be {@code null}
     * causing that no extraction will happen.
     */
    private VirtualFileSystem(Predicate<Path> extractFilter,
                    String resourcesPrefix,
                    String fileListResource,
                    String windowsMountPoint,
                    String unixMountPoint,
                    HostIO allowHostIO,
                    Class<?> resourceLoadingClass) {
        if (resourceLoadingClass != null) {
            this.resourceLoadingClass = resourceLoadingClass;
        } else {
            this.resourceLoadingClass = VirtualFileSystem.class;
        }
        this.vfsPrefix = resourcesPrefix;
        this.filesListPath = fileListResource;
        String mp = System.getenv("GRAALPY_VFS_MOUNT_POINT");
        if (mp == null) {
            mp = isWindows() ? windowsMountPoint : unixMountPoint;
        }
        this.mountPoint = Path.of(mp);
        if (mp.endsWith(PLATFORM_SEPARATOR) || !mountPoint.isAbsolute()) {
            throw new IllegalArgumentException("GRAALPY_VFS_MOUNT_POINT must be set to an absolute path without a trailing separator");
        }
        this.extractFilter = extractFilter;
        if (extractFilter != null) {
            try {
                this.extractDir = Files.createTempDirectory("vfsx");
                this.deleteTempDir = new DeleteTempDir(this.extractDir);
                Runtime.getRuntime().addShutdownHook(deleteTempDir);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        } else {
            this.extractDir = null;
            this.deleteTempDir = null;
        }
        delegate = switch (allowHostIO) {
            case NONE -> null;
            case READ -> FileSystem.newReadOnlyFileSystem(FileSystem.newDefaultFileSystem());
            case READ_WRITE -> FileSystem.newDefaultFileSystem();
        };
    }

    @Override
    public void close() {
        if (deleteTempDir != null) {
            deleteTempDir.removeExtractDir();
        }
    }

    public static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows");
    }

    public String resourcePathToPlatformPath(String inputPath) {
        assert inputPath.startsWith(vfsPrefix);
        var path = inputPath.substring(vfsPrefix.length() + 1);
        if (!PLATFORM_SEPARATOR.equals(RESOURCE_SEPARATOR)) {
            path = path.replace(RESOURCE_SEPARATOR, PLATFORM_SEPARATOR);
        }
        return mountPoint.resolve(path).toString();
    }

    private String platformPathToResourcePath(String inputPath) throws IOException {
        String mountPointString = this.mountPoint.toString();
        String path = inputPath;
        assert path.startsWith(mountPointString);
        if (path.startsWith(mountPointString)) {
            path = path.substring(mountPointString.length());
        }
        if (!PLATFORM_SEPARATOR.equals(RESOURCE_SEPARATOR)) {
            path = path.replace(PLATFORM_SEPARATOR, RESOURCE_SEPARATOR);
        }
        if (path.endsWith(RESOURCE_SEPARATOR)) {
            path = path.substring(0, path.length() - RESOURCE_SEPARATOR.length());
        }
        path = vfsPrefix + path;
        if (caseInsensitive) {
            path = getLowercaseToResourceMap().get(path);
        }
        return path;
    }

    private Set<String> getFilesList() throws IOException {
        if (filesList == null) {
            initFilesAndDirsList();
        }
        return filesList;
    }

    private Set<String> getDirsList() throws IOException {
        if (dirsList == null) {
            initFilesAndDirsList();
        }
        return dirsList;
    }

    private Map<String, String> getLowercaseToResourceMap() throws IOException {
        assert caseInsensitive;
        if (lowercaseToResourceMap == null) {
            initFilesAndDirsList();
        }
        return lowercaseToResourceMap;
    }

    private void initFilesAndDirsList() throws IOException {
        filesList = new HashSet<>();
        dirsList = new HashSet<>();
        if (caseInsensitive) {
            lowercaseToResourceMap = new HashMap<>();
        }

        try (InputStream stream = this.resourceLoadingClass.getResourceAsStream(filesListPath)) {
            if (stream == null) {
                return;
            }
            BufferedReader br = new BufferedReader(new InputStreamReader(stream));
            String line;
            while ((line = br.readLine()) != null) {
                if (line.endsWith(RESOURCE_SEPARATOR)) {
                    line = line.substring(0, line.length() - 1);
                    dirsList.add(line);
                } else {
                    filesList.add(line);
                }
                if (caseInsensitive) {
                    lowercaseToResourceMap.put(line.toLowerCase(Locale.ROOT), line);
                }
            }
        }
    }

    private Entry readDirEntry(String parentDir) throws IOException {
        List<String> l = new ArrayList<>();

        // find all files in parent dir
        for (String file : getFilesList()) {
            if (isParent(parentDir, file)) {
                l.add(file);
            }
        }

        // find all dirs in parent dir
        for (String file : getDirsList()) {
            if (isParent(parentDir, file)) {
                l.add(file);
            }
        }

        Path[] paths = new Path[l.size()];
        for (int i = 0; i < paths.length; i++) {
            paths[i] = Paths.get(resourcePathToPlatformPath(l.get(i)));
        }
        return new Entry(false, paths);
    }

    private static boolean isParent(String parentDir, String file) {
        return file.length() > parentDir.length() && file.startsWith(parentDir) &&
                        file.indexOf(RESOURCE_SEPARATOR_CHAR, parentDir.length() + 1) < 0;
    }

    Entry readFileEntry(String file) throws IOException {
        return new Entry(true, readResource(file));
    }

    byte[] readResource(String path) throws IOException {
        try (InputStream stream = this.resourceLoadingClass.getResourceAsStream(path)) {
            if (stream == null) {
                return null;
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int n;
            byte[] data = new byte[4096];

            while ((n = stream.readNBytes(data, 0, data.length)) != 0) {
                buffer.write(data, 0, n);
            }
            buffer.flush();
            return buffer.toByteArray();
        }
    }

    private Path toAbsolutePathInternal(Path path) {
        if (path.startsWith(mountPoint)) {
            return path;
        }
        return mountPoint.resolve(path);
    }

    private Entry file(Path inputPath) throws IOException {
        Path path = toAbsolutePathInternal(inputPath).normalize();
        String pathString = path.toString();
        String entryKey = caseInsensitive ? pathString.toLowerCase(Locale.ROOT) : pathString;
        Entry e = vfsEntries.get(entryKey);
        if (e == null) {
            pathString = platformPathToResourcePath(pathString);
            URL uri = pathString == null ? null : this.resourceLoadingClass.getResource(pathString);
            if (uri != null) {
                if (getDirsList().contains(pathString)) {
                    e = readDirEntry(pathString);
                } else {
                    e = readFileEntry(pathString);
                }
                vfsEntries.put(entryKey, e);
            } else {
                if (getDirsList().contains(pathString)) {
                    e = readDirEntry(pathString);
                }
            }
        }
        return e;
    }

    public String getPrefix() {
        return this.vfsPrefix;
    }

    public String getFileListPath() {
        return this.filesListPath;
    }

    /**
     * Uses {@link #extractFilter} to determine if the given platform path should be extracted.
     */
    private boolean shouldExtract(Path path) {
        return extractFilter != null && extractFilter.test(path);
    }

    /**
     * Extracts a file or directory from the resource to the temporary directory and returns the
     * path to the extracted file. Inexisting parent directories will also be created (recursively).
     * If the extracted file or directory already exists, nothing will be done.
     */
    private Path getExtractedPath(Path path) {
        assert extractDir != null;
        assert shouldExtract(path);
        try {
            /*
             * Remove the mountPoint(X) (e.g. "graalpy_vfs(x)") prefix if given. Method 'file' is
             * able to handle relative paths and we need it to compute the extract path.
             */
            Path relPath;
            if (path.startsWith(mountPoint)) {
                relPath = mountPoint.relativize(path);
            } else {
                relPath = path;
            }

            // create target path
            Path xPath = extractDir.resolve(relPath);
            if (!Files.exists(xPath)) {
                Entry e = file(relPath);
                if (e == null) {
                    return path;
                }
                if (e.isFile()) {
                    // first create parent dirs
                    Path parent = xPath.getParent();
                    assert parent == null || Files.isDirectory(parent);
                    if (parent == null) {
                        throw new NullPointerException("Parent is null during extracting path.");
                    }
                    Files.createDirectories(parent);

                    // write data extracted file
                    Files.write(xPath, (byte[]) e.data());
                } else {
                    Files.createDirectories(xPath);
                }
            }

            return xPath;
        } catch (IOException e) {
            throw new RuntimeException(String.format("Error while extracting virtual filesystem path '%s' to the disk", path), e);
        }
    }

    @Override
    public Path parsePath(URI uri) {
        if (uri.getScheme().equals("file")) {
            return Paths.get(uri);
        } else {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }

    @Override
    public Path parsePath(String path) {
        return Paths.get(path);
    }

    @Override
    public void checkAccess(Path path, Set<? extends AccessMode> modes, LinkOption... linkOptions) throws IOException {
        if (path.normalize().startsWith(mountPoint)) {
            if (modes.contains(AccessMode.WRITE)) {
                throw new SecurityException("read-only filesystem");
            }
            if (file(path) == null) {
                throw new NoSuchFileException("no such file or directory");
            }
        } else if (delegate != null) {
            delegate.checkAccess(path, modes, linkOptions);
        } else {
            throw new SecurityException("read-only filesystem");
        }
    }

    @Override
    public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
        if (delegate == null || dir.normalize().startsWith(mountPoint)) {
            throw new SecurityException("read-only filesystem");
        } else {
            delegate.createDirectory(dir, attrs);
        }
    }

    @Override
    public void delete(Path path) throws IOException {
        if (delegate == null || path.normalize().startsWith(mountPoint)) {
            throw new SecurityException("read-only filesystem");
        } else {
            delegate.delete(path);
        }
    }

    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        if (delegate != null && !path.normalize().startsWith(mountPoint)) {
            return delegate.newByteChannel(path, options, attrs);
        }

        if (options.isEmpty() || (options.size() == 1 && options.contains(StandardOpenOption.READ))) {
            final Entry e = file(path);
            if (e == null) {
                throw new FileNotFoundException("No such file or directory");
            }
            if (!e.isFile) {
                // this constructor is used since we rely on the error message to convert to the
                // appropriate python error
                throw new FileSystemException(path.toString(), null, "Is a directory");
            }
            return new SeekableByteChannel() {
                long position = 0;

                byte[] bytes = (byte[]) e.data;

                @Override
                public int read(ByteBuffer dst) throws IOException {
                    if (position > bytes.length) {
                        return -1;
                    } else if (position == bytes.length) {
                        return 0;
                    } else {
                        int length = Math.min(bytes.length - (int) position, dst.remaining());
                        dst.put(bytes, (int) position, length);
                        position += length;
                        if (dst.hasRemaining()) {
                            position++;
                        }
                        return length;
                    }
                }

                @Override
                public int write(ByteBuffer src) throws IOException {
                    throw new IOException("read-only");
                }

                @Override
                public long position() throws IOException {
                    return position;
                }

                @Override
                public SeekableByteChannel position(long newPosition) throws IOException {
                    position = Math.max(0, newPosition);
                    return this;
                }

                @Override
                public long size() throws IOException {
                    return bytes.length;
                }

                @Override
                public SeekableByteChannel truncate(long size) throws IOException {
                    throw new IOException("read-only");
                }

                @Override
                public boolean isOpen() {
                    return true;
                }

                @Override
                public void close() throws IOException {
                }
            };
        } else {
            throw new SecurityException("read-only filesystem");
        }
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
        if (delegate != null && !dir.normalize().startsWith(mountPoint)) {
            return delegate.newDirectoryStream(dir, filter);
        }
        Entry e = file(dir);
        if (e == null) {
            throw new NoSuchFileException(dir.toString());
        }
        if (e.isFile) {
            throw new NotDirectoryException(dir.toString());
        }
        return new DirectoryStream<>() {
            @Override
            public void close() throws IOException {
                // nothing to do
            }

            @Override
            public Iterator<Path> iterator() {
                return Arrays.asList((Path[]) e.data).iterator();
            }
        };
    }

    @Override
    public Path toAbsolutePath(Path path) {
        Path result;
        if (shouldExtract(path)) {
            result = getExtractedPath(path);
        } else {
            result = path;
        }
        return toAbsolutePathInternal(result);
    }

    @Override
    public Path toRealPath(Path path, LinkOption... linkOptions) throws IOException {
        Path result;
        if (shouldExtract(path)) {
            result = getExtractedPath(path);
        } else {
            result = path;
        }
        return result.normalize();
    }

    @Override
    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
        if (delegate != null && !path.normalize().startsWith(mountPoint)) {
            return delegate.readAttributes(path, attributes, options);
        }
        Entry e = file(path);
        if (e == null) {
            throw new NoSuchFileException("no such file or directory");
        }
        HashMap<String, Object> attrs = new HashMap<>();
        if (attributes.startsWith("unix:") || attributes.startsWith("posix:")) {
            throw new UnsupportedOperationException();
        }

        attrs.put("creationTime", FileTime.fromMillis(0));
        attrs.put("lastModifiedTime", FileTime.fromMillis(0));
        attrs.put("lastAccessTime", FileTime.fromMillis(0));
        attrs.put("isRegularFile", e.isFile);
        attrs.put("isDirectory", !e.isFile);
        attrs.put("isSymbolicLink", false);
        attrs.put("isOther", false);
        attrs.put("size", (long) (e.isFile ? ((byte[]) e.data).length : 0));
        attrs.put("mode", 0555);
        attrs.put("dev", 0L);
        attrs.put("nlink", 1);
        attrs.put("uid", 0);
        attrs.put("gid", 0);
        attrs.put("ctime", FileTime.fromMillis(0));
        return attrs;
    }
}
