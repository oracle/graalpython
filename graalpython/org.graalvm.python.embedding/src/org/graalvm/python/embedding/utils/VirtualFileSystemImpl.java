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

import org.graalvm.polyglot.io.FileSystem;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.Charset;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystemException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.NotLinkException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static org.graalvm.python.embedding.utils.VirtualFileSystem.HostIO.NONE;

final class VirtualFileSystemImpl implements FileSystem, AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(VirtualFileSystem.class.getName());

    static {
        LOGGER.setUseParentHandlers(false);
        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setFormatter(new SimpleFormatter() {
            @Override
            public synchronized String format(LogRecord lr) {
                if (lr.getThrown() != null) {
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    lr.getThrown().printStackTrace(pw);
                    pw.close();
                    return String.format("%s: %s\n%s", lr.getLevel().getName(), lr.getMessage(), sw.toString());
                }
                return String.format("%s: %s\n", lr.getLevel().getName(), lr.getMessage());
            }
        });
        LOGGER.addHandler(consoleHandler);
    }

    /*
     * Root of the virtual filesystem in the resources.
     */
    private static final String VFS_ROOT = "/org.graalvm.python.vfs";

    static final String VFS_HOME = "home";
    static final String VFS_VENV = "venv";
    static final String VFS_SRC = "src";

    /*
     * Index of all files and directories available in the resources at runtime. - paths are
     * absolute - directory paths end with a '/' - uses '/' separator regardless of platform. Used
     * to determine directory entries, if an entry is a file or a directory, etc.
     */
    private static final String FILES_LIST_PATH = VFS_ROOT + "/fileslist.txt";
    private static final String VENV_PREFIX = VFS_ROOT + "/" + VFS_VENV;
    private static final String HOME_PREFIX = VFS_ROOT + "/" + VFS_HOME;
    private static final String PROJ_PREFIX = VFS_ROOT + "/proj";
    private static final String SRC_PREFIX = VFS_ROOT + "/" + VFS_SRC;
    private final VirtualFileSystem.HostIO allowHostIO;

    /*
     * Maps platform-specific paths to entries.
     */
    private Map<String, BaseEntry> vfsEntries;

    /**
     * Class used to read resources with getResource(name). By default VirtualFileSystem.class.
     */
    private Class<?> resourceLoadingClass;

    static final String PLATFORM_SEPARATOR = Paths.get("").getFileSystem().getSeparator();
    private static final char RESOURCE_SEPARATOR_CHAR = '/';
    private static final String RESOURCE_SEPARATOR = String.valueOf(RESOURCE_SEPARATOR_CHAR);

    private abstract class BaseEntry {
        final String platformPath;

        private BaseEntry(String platformPath) {
            this.platformPath = platformPath;
        }

        String getPlatformPath() {
            return platformPath;
        }

        String getResourcePath() {
            return platformPathToResourcePath(platformPath);
        }
    }

    private final class FileEntry extends BaseEntry {
        private byte[] data;

        public FileEntry(String path) {
            super(path);
        }

        private byte[] getData() throws IOException {
            if (data == null) {
                data = readResource(getResourcePath());
            }
            return data;
        }
    }

    private final class DirEntry extends BaseEntry {
        List<BaseEntry> entries = new ArrayList<>();

        DirEntry(String platformPath) {
            super(platformPath);
        }
    }

    /*
     * Determines where the virtual filesystem lives in the real filesystem, e.g. if set to
     * "X:\graalpy_vfs", then a resource with path /org.graalvm.python.vfs/xyz/abc is visible as
     * "X:\graalpy_vfs\xyz\abc". This needs to be an absolute path with platform-specific separators
     * without any trailing separator. If that file or directory actually exists, it will not be
     * accessible.
     */
    final Path mountPoint;
    private final String mountPointLowerCase;

    /**
     * The temporary directory where to extract files/directories to.
     */
    private final Path extractDir;

    private boolean extractOnStartup = "true".equals(System.getProperty("org.graalvm.python.vfs.extractOnStartup")) || "true".equals(System.getProperty("graalpy.vfs.extractOnStartup"));

    /**
     * A filter to determine if a path should be extracted (see {@link #shouldExtract(Path)}).
     */
    private final Predicate<Path> extractFilter;
    private final boolean caseInsensitive;

    private static final class DeleteTempDir extends Thread {
        private final Path extractDir;

        DeleteTempDir(Path extractDir) {
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
    VirtualFileSystemImpl(Predicate<Path> extractFilter,
                    Path mountPoint,
                    VirtualFileSystem.HostIO allowHostIO,
                    Class<?> resourceLoadingClass,
                    boolean caseInsensitive) {
        if (resourceLoadingClass != null) {
            this.resourceLoadingClass = resourceLoadingClass;
        } else {
            this.resourceLoadingClass = VirtualFileSystem.class;
        }

        this.caseInsensitive = caseInsensitive;
        this.mountPoint = mountPoint;

        this.mountPointLowerCase = mountPoint.toString().toLowerCase(Locale.ROOT);

        fine("VirtualFilesystem %s, allowHostIO: %s, resourceLoadingClass: %s, caseInsensitive: %s, extractOnStartup: %s%s",
                        mountPoint, allowHostIO.toString(), this.resourceLoadingClass.getName(), caseInsensitive, extractOnStartup, extractFilter != null ? "" : ", extractFilter: null");

        this.extractFilter = extractFilter;
        if (extractFilter != null) {
            try {
                this.extractDir = Files.createTempDirectory("org.graalvm.python.vfsx");
                this.deleteTempDir = new DeleteTempDir(this.extractDir);
                Runtime.getRuntime().addShutdownHook(deleteTempDir);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        } else {
            this.extractDir = null;
            this.deleteTempDir = null;
        }
        this.allowHostIO = allowHostIO;
    }

    /**
     * Used to access files which were extracted, see e.g. checkAccess(). Since VFS is read-only, we
     * also limit the access to extracted files as read-only.
     */
    private FileSystem extractedFilesFS = FileSystem.newReadOnlyFileSystem(FileSystem.newDefaultFileSystem());

    static FileSystem createDelegatingFileSystem(VirtualFileSystemImpl vfs) {
        FileSystem d = switch (vfs.allowHostIO) {
            case NONE -> FileSystem.newDenyIOFileSystem();
            case READ -> FileSystem.newReadOnlyFileSystem(FileSystem.newDefaultFileSystem());
            case READ_WRITE -> FileSystem.newDefaultFileSystem();
        };
        FileSystem delegatingFS = FileSystem.newCompositeFileSystem(d, new Selector(vfs) {
            @Override
            public boolean test(Path path) {
                Objects.requireNonNull(path);
                return vfs.pathIsInVfs(toAbsoluteNormalizedPath(path));
            }
        });
        if (vfs.allowHostIO == NONE) {
            delegatingFS.setCurrentWorkingDirectory(vfs.mountPoint.resolve("src"));
        }
        return delegatingFS;
    }

    @Override
    public void close() {
        if (deleteTempDir != null) {
            deleteTempDir.removeExtractDir();
        }
    }

    static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows");
    }

    String vfsHomePath() {
        return resourcePathToPlatformPath(HOME_PREFIX);
    }

    String vfsSrcPath() {
        return resourcePathToPlatformPath(SRC_PREFIX);
    }

    String vfsVenvPath() {
        return resourcePathToPlatformPath(VENV_PREFIX);
    }

    /**
     * Converts the given path starting with the internal resource root to the path as seen by
     * Python IO. For example if no other mount point was set then the path
     * "/org.graalvm.python.vfs/src/hello.py" will be converted to the default mount point
     * "/graalpy_vfs/src/hello.py" .
     */
    private String resourcePathToPlatformPath(String resourcePath) {
        if (!(resourcePath.length() > VFS_ROOT.length() && resourcePath.startsWith(VFS_ROOT))) {
            String msg = "Resource path is expected to start with '" + VFS_ROOT + "' but was '" + resourcePath + "'.\n" +
                            "Please also ensure that your virtual file system resources root directory is '" + VFS_ROOT + "'";
            throw new IllegalArgumentException(msg);
        }
        var path = resourcePath.substring(VFS_ROOT.length() + 1);
        if (!PLATFORM_SEPARATOR.equals(RESOURCE_SEPARATOR)) {
            path = path.replace(RESOURCE_SEPARATOR, PLATFORM_SEPARATOR);
        }
        String absolute = mountPoint.resolve(path).toString();
        if (resourcePath.endsWith(RESOURCE_SEPARATOR) && !absolute.endsWith(PLATFORM_SEPARATOR)) {
            absolute += PLATFORM_SEPARATOR;
        }
        return absolute;
    }

    private String platformPathToResourcePath(String inputPath) {
        String mountPointString = mountPoint.toString();
        String path = inputPath;
        assert path.startsWith(mountPointString) : String.format("path `%s` expected to start with `%s`", path, mountPointString);
        if (path.startsWith(mountPointString)) {
            path = path.substring(mountPointString.length());
        }
        if (!PLATFORM_SEPARATOR.equals(RESOURCE_SEPARATOR)) {
            path = path.replace(PLATFORM_SEPARATOR, RESOURCE_SEPARATOR);
        }
        if (path.endsWith(RESOURCE_SEPARATOR)) {
            path = path.substring(0, path.length() - RESOURCE_SEPARATOR.length());
        }
        path = VFS_ROOT + path;
        return path;
    }

    private String toCaseComparable(String file) {
        return caseInsensitive ? file.toLowerCase(Locale.ROOT) : file;
    }

    private boolean projWarning = false;

    private void initEntries() throws IOException {
        vfsEntries = new HashMap<>();
        try (InputStream stream = this.resourceLoadingClass.getResourceAsStream(FILES_LIST_PATH)) {
            if (stream == null) {
                warn("VFS.initEntries: could not read resource %s", FILES_LIST_PATH);
                return;
            }
            BufferedReader br = new BufferedReader(new InputStreamReader(stream));
            String line;
            finest("VFS entries:");
            while ((line = br.readLine()) != null) {

                if (!projWarning && line.startsWith(PROJ_PREFIX)) {
                    projWarning = true;
                    LOGGER.warning("");
                    LOGGER.warning(String.format("%s source root was deprecated, use %s instead.", PROJ_PREFIX, SRC_PREFIX));
                    LOGGER.warning("");
                }

                String platformPath = resourcePathToPlatformPath(line);
                int i = mountPoint.toString().length();
                DirEntry parent = null;
                do {
                    String dir = platformPath.substring(0, i);
                    String dirKey = toCaseComparable(dir);
                    DirEntry dirEntry = (DirEntry) vfsEntries.get(dirKey);
                    if (dirEntry == null) {
                        dirEntry = new DirEntry(dir);
                        vfsEntries.put(dirKey, dirEntry);
                        finest("  %s", dirEntry.getResourcePath());
                        if (parent != null) {
                            parent.entries.add(dirEntry);
                        }
                    }
                    parent = dirEntry;
                    i++;
                } while ((i = platformPath.indexOf(PLATFORM_SEPARATOR, i)) != -1);

                assert parent != null;
                if (!platformPath.endsWith(PLATFORM_SEPARATOR)) {
                    FileEntry fileEntry = new FileEntry(platformPath);
                    vfsEntries.put(toCaseComparable(platformPath), fileEntry);
                    finest("  %s", fileEntry.getResourcePath());
                    parent.entries.add(fileEntry);
                    if (extractOnStartup) {
                        Path p = Paths.get(fileEntry.getPlatformPath());
                        if (shouldExtract(p)) {
                            getExtractedPath(p);
                        }
                    }
                }
            }
        }
    }

    byte[] readResource(String path) throws IOException {
        try (InputStream stream = this.resourceLoadingClass.getResourceAsStream(path)) {
            if (stream == null) {
                warn("VFS.initEntries: could not read resource %s", path);
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

    private BaseEntry getEntry(Path inputPath) throws IOException {
        if (vfsEntries == null) {
            initEntries();
            if (vfsEntries == null || vfsEntries.isEmpty()) {
                warn("VFS.getEntry: no entries after init");
            }
        }
        Path path = toAbsoluteNormalizedPath(inputPath);
        return vfsEntries.get(toCaseComparable(path.toString()));
    }

    /**
     * Determines if the given path belongs to the VFS. The path should be already normalized
     */
    private boolean pathIsInVfs(Path path) {
        assert isNormalized(path);
        return toCaseComparable(path.toString()).startsWith(mountPointLowerCase);
    }

    private static boolean isNormalized(Path path) {
        for (Path name : path) {
            String strName = name.toString();
            if (".".equals(strName) || "..".equals(strName)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Uses {@link #extractFilter} to determine if the given platform path should be extracted.
     */
    private boolean shouldExtract(Path path) {
        boolean ret = extractFilter != null && extractFilter.test(path);
        finest("VFS.shouldExtract '%s' %s", path, ret);
        return ret;
    }

    private static boolean followLinks(LinkOption... linkOptions) {
        if (linkOptions != null) {
            for (Object o : linkOptions) {
                if (Objects.requireNonNull(o) == NOFOLLOW_LINKS) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Extracts a file or directory from the resource to the temporary directory and returns the
     * path to the extracted file. Nonexistent parent directories will also be created
     * (recursively). If the extracted file or directory already exists, nothing will be done.
     */
    private Path getExtractedPath(Path path) {
        assert shouldExtract(path);
        return extractPath(path, true);
    }

    private Path extractPath(Path path, boolean extractLibsDir) {
        assert extractDir != null;
        try {
            /*
             * Remove the mountPoint(X) (e.g. "graalpy_vfs(x)") prefix if given. Method 'file' is
             * able to handle relative paths and we need it to compute the extract path.
             */
            BaseEntry entry = getEntry(path);
            if (entry == null) {
                return null;
            }
            Path relPath = mountPoint.relativize(Paths.get(entry.getPlatformPath()));

            // create target path
            Path xPath = extractDir.resolve(relPath);
            if (!Files.exists(xPath)) {
                if (entry instanceof FileEntry fileEntry) {
                    // first create parent dirs
                    Path parent = xPath.getParent();
                    assert parent == null || !Files.exists(parent) || Files.isDirectory(parent);
                    if (parent == null) {
                        throw new NullPointerException("Parent is null during extracting path.");
                    }
                    Files.createDirectories(parent);

                    // write data extracted file
                    Files.write(xPath, fileEntry.getData());
                    finest("extracted '%s' -> '%s'", path, xPath);

                    if (extractLibsDir) {
                        Path pkgDir = getPythonPackageDir(path);
                        if (pkgDir != null) {
                            Path libsDir = Paths.get(pkgDir + ".libs");
                            extract(libsDir);
                        }
                    }

                } else if (entry instanceof DirEntry) {
                    Files.createDirectories(xPath);
                } else {
                    return path;
                }
            }

            return xPath;
        } catch (IOException e) {
            throw new RuntimeException(String.format("Error while extracting virtual filesystem path '%s' to the disk", path), e);
        }
    }

    private static Path getPythonPackageDir(Path path) {
        Path prev = null;
        Path p = path;
        while ((p = p.getParent()) != null) {
            Path fileName = p.getFileName();
            if (fileName != null && "site-packages".equals(fileName.toString())) {
                return prev;
            }
            prev = p;
        }
        return null;
    }

    private void extract(Path path) throws IOException {
        BaseEntry entry = getEntry(path);
        if (entry instanceof FileEntry) {
            extractPath(path, false);
        } else if (entry != null) {
            if (((DirEntry) entry).entries != null) {
                for (BaseEntry be : ((DirEntry) entry).entries) {
                    extract(Path.of(be.getPlatformPath()));
                }
            }
        }
    }

    void extractResources(Path resourcesDirectory) throws IOException {
        fine("VFS.extractResources '%s'", resourcesDirectory);
        InputStream stream = this.resourceLoadingClass.getResourceAsStream(FILES_LIST_PATH);
        if (stream == null) {
            warn("VFS.initEntries: could not read resource %s", FILES_LIST_PATH);
            return;
        }
        BufferedReader br = new BufferedReader(new InputStreamReader(stream));
        String resourcePath;
        while ((resourcePath = br.readLine()) != null) {
            Path destFile = resourcesDirectory.resolve(Path.of(resourcePath.substring(VFS_ROOT.length() + 1)));
            if (destFile == null) {
                continue;
            }
            if (resourcePath.endsWith(RESOURCE_SEPARATOR)) {
                Files.createDirectories(destFile);
            } else {
                Path parent = destFile.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                finest("VFS.extractResources '%s' -> '%s'", resourcePath, destFile);
                Files.write(destFile, readResource(resourcePath));
            }
        }
    }

    private FileSystemProvider defaultFileSystemProvider;

    private synchronized FileSystemProvider getDefaultFileSystem() {
        if (defaultFileSystemProvider == null) {
            // c&p from c.o.t.polyglot.FileSystems.DeniedIOFileSystem
            for (FileSystemProvider provider : FileSystemProvider.installedProviders()) {
                if ("file".equals(provider.getScheme())) {
                    defaultFileSystemProvider = provider;
                }
            }
        }
        return defaultFileSystemProvider;
    }

    @Override
    public Path parsePath(URI uri) {
        Objects.requireNonNull(uri);

        // similar as in c.o.t.polyglot.FileSystems.DeniedIOFileSystem
        if (uri.getScheme().equals("file")) {
            Path path = getDefaultFileSystem().getPath(uri);
            finest("VFS.parsePath '%s' -> '%s'", uri, path);
            return path;
        } else {
            String msg = "Unsupported URI scheme '%s'";
            finer(msg, uri.getScheme());
            throw new UnsupportedOperationException(String.format(msg, uri.getScheme()));
        }
    }

    @Override
    public Path parsePath(String path) {
        Objects.requireNonNull(path);

        // same as in c.o.t.polyglot.FileSystems.DeniedIOFileSystem
        Path p = Paths.get(path);
        finer("VFS.parsePath '%s' -> '%s'", path, p);
        return p;
    }

    @Override
    public void checkAccess(Path p, Set<? extends AccessMode> modes, LinkOption... linkOptions) throws IOException {
        Objects.requireNonNull(p);
        Objects.requireNonNull(modes);

        Path path = toAbsoluteNormalizedPath(p);

        boolean extractable = shouldExtract(path);
        if (extractable && followLinks(linkOptions)) {
            Path extractedPath = getExtractedPath(path);
            if (extractedPath != null) {
                extractedFilesFS.checkAccess(extractedPath, modes, linkOptions);
                return;
            } else {
                finer("VFS.checkAccess could not extract path '%s'", p);
                throw new NoSuchFileException(String.format("no such file or directory: '%s'", path));
            }
        }

        if (modes.contains(AccessMode.WRITE)) {
            throw securityException("VFS.checkAccess", String.format("read-only filesystem, write access not supported '%s'", path));
        }
        if (modes.contains(AccessMode.EXECUTE)) {
            throw securityException("VFS.checkAccess", String.format("execute access not supported for  '%s'", p));
        }

        if (getEntry(path) == null) {
            String msg = String.format("no such file or directory: '%s'", path);
            finer("VFS.checkAccess %s", msg);
            throw new NoSuchFileException(msg);
        }
        finer("VFS.checkAccess %s OK", path);
    }

    @Override
    public void createDirectory(Path d, FileAttribute<?>... attrs) throws IOException {
        throw securityException("VFS.createDirectory", String.format("read-only filesystem, create directory not supported '%s'", d));
    }

    @Override
    public void delete(Path p) throws IOException {
        throw securityException("VFS.delete", String.format("read-only filesystem, delete not supported: '%s'", p));
    }

    @Override
    public SeekableByteChannel newByteChannel(Path p, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        Objects.requireNonNull(p);
        Objects.requireNonNull(options);
        // some instances of Set throw NPE on .contains(null)
        for (OpenOption o : options) {
            Objects.requireNonNull(o);
        }
        Objects.requireNonNull(attrs);

        Path path = toAbsoluteNormalizedPath(p);

        boolean extractable = shouldExtract(path);
        if (extractable) {
            if (!options.contains(NOFOLLOW_LINKS)) {
                Path extractedPath = getExtractedPath(path);
                if (extractedPath != null) {
                    return extractedFilesFS.newByteChannel(extractedPath, options);
                } else {
                    finer("VFS.newByteChannel could not extract path '%s'", p);
                    throw new FileNotFoundException(String.format("no such file or directory: '%s'", p));
                }
            } else {
                String msg = String.format("can't create byte channel for '%s' (NOFOLLOW_LINKS specified)", p);
                finer("VFS.newByteChannel '%s'", msg);
                throw new IOException(msg);
            }
        }

        checkNoWriteOption(options, p);

        BaseEntry entry = getEntry(path);
        if (entry == null) {
            String msg = String.format("No such file or directory: '%s'", path);
            finer("VFS.newByteChannel '%s'", path);
            throw new FileNotFoundException(msg);
        }
        if (!(entry instanceof FileEntry fileEntry)) {
            finer("VFS.newByteChannel Is a directory '%s'", path);
            // this constructor is used since we rely on the error message to convert to the
            // appropriate python error
            throw new FileSystemException(path.toString(), null, "Is a directory");
        }
        return new SeekableByteChannel() {
            long position = 0;

            final byte[] bytes = fileEntry.getData();

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
                finer("VFS.newByteChannel '%s'", String.format("read-only filesystem: '%s'", path));
                throw new NonWritableChannelException();
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
                finer("VFS.newByteChannel '%s'", String.format("read-only filesystem: '%s'", path));
                throw new NonWritableChannelException();
            }

            @Override
            public boolean isOpen() {
                return true;
            }

            @Override
            public void close() throws IOException {
            }
        };
    }

    private static final Set<? extends OpenOption> READ_OPTIONS = Set.of(
                    StandardOpenOption.READ,
                    StandardOpenOption.DSYNC,
                    StandardOpenOption.SPARSE,
                    StandardOpenOption.SYNC,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    LinkOption.NOFOLLOW_LINKS);

    /**
     * copied from FileSystems.ReadOnlyFileSystem
     */
    private static void checkNoWriteOption(Set<? extends OpenOption> options, Path path) {
        Set<OpenOption> writeOptions = new HashSet<>(options);
        boolean read = writeOptions.contains(StandardOpenOption.READ);
        writeOptions.removeAll(READ_OPTIONS);
        // The APPEND option is ignored in case of read but without explicit READ option it
        // implies write. Remove the APPEND option only when options contain READ.
        if (read) {
            writeOptions.remove(StandardOpenOption.APPEND);
        }
        boolean write = !writeOptions.isEmpty();
        if (write) {
            throw securityException("VFS.newByteChannel", String.format("read-only filesystem, can't create byte channel for write access: '%s'", path));
        }
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path d, DirectoryStream.Filter<? super Path> filter) throws IOException {
        Objects.requireNonNull(d);
        Path dir = toAbsoluteNormalizedPath(d);
        Objects.requireNonNull(filter);
        BaseEntry entry = getEntry(dir);
        if (entry instanceof FileEntry) {
            finer("VFS.newDirectoryStream not a directory %s", dir);
            throw new NotDirectoryException(dir.toString());
        } else if (entry instanceof DirEntry dirEntry) {
            return new DirectoryStream<>() {
                @Override
                public void close() throws IOException {
                    // nothing to do
                }

                @Override
                public Iterator<Path> iterator() {
                    finest("VFS.newDirectoryStream %s entries:", dir);
                    return dirEntry.entries.stream().filter(e -> {
                        boolean accept = false;
                        try {
                            accept = filter.accept(Path.of(e.platformPath));
                            finest("VFS.newDirectoryStream entry %s accept: %s", e.platformPath, accept);
                        } catch (IOException ex) {
                            ex.printStackTrace();
                            return false;
                        }
                        return accept;
                    }).map(e -> Path.of(e.getPlatformPath())).iterator();
                }
            };
        } else {
            throw new NoSuchFileException(dir.toString());
        }
    }

    private static Path toAbsoluteNormalizedPath(Path path) {
        // see doc of FileSystem.newCompositeFileSystem()
        // - until CWD is not set, toAbsolutePath maybe be called
        // - if an explicit CWD was set then toAbsolutePath is never called
        if (path.isAbsolute()) {
            // it is guaranteed that once an incoming path from truffle is absolute
            // then it is also already normalized
            return path;
        }
        // guaranteed that relative paths are sent only if cwd was not set yet,
        // and in such case it is safe to fallback on jdk cwd
        return path.toAbsolutePath().normalize();
    }

    @Override
    public Path toAbsolutePath(Path path) {
        // see doc of FileSystem.newCompositeFileSystem()
        // - until CWD is not set, toAbsolutePath maybe be called
        // - if an explicit CWD was set then toAbsolutePath is never called
        Objects.requireNonNull(path);
        if (path.isAbsolute()) {
            return path;
        } else {
            // guaranteed that relative paths are sent only if cwd was not set yet,
            // and in such case it is safe to fallback on jdk cwd
            return path.toAbsolutePath();
        }
    }

    @Override
    public Path toRealPath(Path p, LinkOption... linkOptions) throws IOException {
        Objects.requireNonNull(p);

        Path path = toAbsoluteNormalizedPath(p);
        Path result = path;
        if (shouldExtract(path) && followLinks(linkOptions)) {
            result = getExtractedPath(path);
            if (result == null) {
                warn("no VFS entry for '%s'", p);
                throw new NoSuchFileException(String.format("no such file or directory: '%s'", p));
            }
        }
        finer("VFS.toRealPath '%s' -> '%s'", path, result);
        return result;
    }

    @Override
    public Map<String, Object> readAttributes(Path p, String attributes, LinkOption... options) throws IOException {
        Objects.requireNonNull(p);

        Path path = toAbsoluteNormalizedPath(p);

        boolean extractable = shouldExtract(path);
        if (extractable && followLinks(options)) {
            Path extractedPath = getExtractedPath(path);
            if (extractedPath != null) {
                return extractedFilesFS.readAttributes(extractedPath, attributes, options);
            } else {
                finer("VFS.readAttributes could not extract path '%s'", p);
                throw new NoSuchFileException(String.format("no such file or directory: '%s'", path));
            }
        }

        BaseEntry entry = getEntry(path);
        if (entry == null) {
            String msg = String.format("no such file or directory: '%s'", path);
            finer("VFS.readAttributes %s", msg);
            throw new NoSuchFileException(msg);
        }
        HashMap<String, Object> attrs = new HashMap<>();
        if (attributes.startsWith("unix:") || attributes.startsWith("posix:")) {
            finer("VFS.readAttributes unsupported attributes '%s' %s", path, attributes);
            throw new UnsupportedOperationException();
        }

        attrs.put("creationTime", FileTime.fromMillis(0));
        attrs.put("lastModifiedTime", FileTime.fromMillis(0));
        attrs.put("lastAccessTime", FileTime.fromMillis(0));
        attrs.put("isRegularFile", !extractable && entry instanceof FileEntry);
        attrs.put("isDirectory", entry instanceof DirEntry);
        attrs.put("isSymbolicLink", extractable);
        attrs.put("isOther", false);
        attrs.put("size", (long) (entry instanceof FileEntry fileEntry ? fileEntry.getData().length : 0));
        attrs.put("mode", 0555);
        attrs.put("dev", 0L);
        attrs.put("nlink", 1);
        attrs.put("uid", 0);
        attrs.put("gid", 0);
        attrs.put("ctime", FileTime.fromMillis(0));
        finer("VFS.readAttributes '%s' %s", path, attrs);
        return attrs;
    }

    @Override
    public void setCurrentWorkingDirectory(Path d) {
        // CompositeFileSystem never dispatches to us
        // see doc of FileSystem.newCompositeFileSystem()
        throw new RuntimeException("should not reach here");
    }

    @Override
    public void copy(Path s, Path t, CopyOption... options) {
        // CompositeFileSystem never dispatches to us
        // CompositeFileSystem.copy() -> FileSystem.copy() -> IOHelper.copy()
        throw new RuntimeException("should not reach here");
    }

    @Override
    public void move(Path s, Path t, CopyOption... options) {
        // CompositeFileSystem never dispatches to us
        // CompositeFileSystem.move() -> FileSystem.move() -> IOHelper.move()
        throw new RuntimeException("should not reach here");
    }

    @Override
    public Charset getEncoding(Path p) {
        Objects.requireNonNull(p);
        return null;
    }

    @Override
    public void createSymbolicLink(Path l, Path t, FileAttribute<?>... attrs) throws IOException {
        Objects.requireNonNull(l);
        Objects.requireNonNull(t);
        throw securityException("VFS.createSymbolicLink", String.format("read-only filesystem, can't create symbolic link from '%s' to '%s'", l, t));
    }

    @Override
    public void createLink(Path l, Path e) throws IOException {
        Objects.requireNonNull(l);
        Objects.requireNonNull(e);
        throw securityException("VFS.createLink", String.format("read-only filesystem, can't create link '%s' to '%s'", l, e));
    }

    @Override
    public Path readSymbolicLink(Path link) throws IOException {
        Objects.requireNonNull(link);

        Path path = toAbsoluteNormalizedPath(link);
        if (shouldExtract(path)) {
            Path result = getExtractedPath(path);
            if (result != null) {
                finer("VFS.readSymbolicLink '%s' '%s'", link, result);
                return result;
            }
            finer("VFS.readSymbolicLink could not extract path '%s'", link);
            throw new NoSuchFileException(String.format("no such file or directory: '%s'", path));
        }
        if (getEntry(path) == null) {
            finer("VFS.readSymbolicLink no entry for path '%s'", link);
            throw new NoSuchFileException(String.format("no such file or directory: '%s'", path));
        }
        throw new NotLinkException(link.toString());
    }

    @Override
    public void setAttribute(Path p, String attribute, Object value, LinkOption... options) throws IOException {
        Objects.requireNonNull(p);
        throw securityException("VFS.setAttribute", String.format("read-only filesystem, can't set attribute '%s' for '%s", attribute, p));
    }

    @Override
    public String getMimeType(Path p) {
        Objects.requireNonNull(p);
        return null;
    }

    @Override
    public Path getTempDirectory() {
        throw new RuntimeException("should not reach here");
    }

    private static void warn(String msgFormat, Object... args) {
        if (LOGGER.isLoggable(Level.WARNING)) {
            LOGGER.log(Level.WARNING, String.format(msgFormat, args));
        }
    }

    private static void fine(String msgFormat, Object... args) {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, String.format(msgFormat, args));
        }
    }

    private static void finer(String msgFormat, Object... args) {
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.log(Level.FINER, String.format(msgFormat, args));
        }
    }

    private static void finest(String msgFormat, Object... args) {
        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.log(Level.FINEST, String.format(msgFormat, args));
        }
    }

    private static SecurityException securityException(String from, String msg) {
        finer("%s %s", from, msg);
        throw new SecurityException(msg);
    }

}
