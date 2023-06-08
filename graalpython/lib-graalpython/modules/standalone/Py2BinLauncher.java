/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.DirectoryStream;
import java.nio.file.LinkOption;
import java.nio.file.NotDirectoryException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.io.FileSystem;

/**
 * A simple launcher for Python. The launcher sets the filesystem up to read the Python core,
 * standard library, a Python virtual environment, and the module to launch from an embedded
 * resource. Any other file system accesses are passed through to the underlying filesystem. The
 * options are set such that most access is allowed and the interpreter works mostly as if run via
 * the launcher. To support the virtual filesystem, however, the POSIX and C API backends are set
 * up to use Java instead of native execution.
 *
 * This class can serve as a skeleton for more elaborate embeddings, an example of a virtual
 * filesystem, as well as showing how to embed Python code into a single native image binary.
 */
public class Py2BinLauncher {
    private static final String RESOURCE_ZIP = "home.zip";
    private static final String VFS_PREFIX = "/Py2BinLauncher";
    private static final String HOME_PREFIX = VFS_PREFIX + "/home";
    private static final String VENV_PREFIX = VFS_PREFIX + "/venv";
    private static final String PROJ_PREFIX = VFS_PREFIX + "/proj";

    public static void main(String[] args) throws IOException {
        var builder = Context.newBuilder()
            .allowExperimentalOptions(true)
            .allowAllAccess(true)
            .allowIO(true)
            .fileSystem(new JarFileSystem())
            .option("python.PosixModuleBackend", "java")
            .option("python.NativeModules", "")
            .option("python.DontWriteBytecodeFlag", "true")
            .option("python.VerboseFlag", System.getenv("PYTHONVERBOSE") != null ? "true" : "false")
            .option("log.python.level", System.getenv("PYTHONVERBOSE") != null ? "FINE" : "SEVERE")
            .option("python.WarnOptions", System.getenv("PYTHONWARNINGS") == null ? "" : System.getenv("PYTHONWARNINGS"))
            .option("python.AlwaysRunExcepthook", "true")
            .option("python.ForceImportSite", "true")
            .option("python.RunViaLauncher", "true")
            .option("python.Executable", VENV_PREFIX + "/bin/python")
            .option("python.InputFilePath", PROJ_PREFIX)
            .option("python.PythonHome", HOME_PREFIX)
            .option("python.CheckHashPycsMode", "never");
        if(ImageInfo.inImageRuntimeCode()) {
            builder.option("engine.WarnInterpreterOnly", "false");
        }
        try (var context = builder.build()) {
            try {
                var src = Source.newBuilder("python", "__graalpython__.run_path()", "<internal>").internal(true).build();
                context.eval(src);
            } catch (PolyglotException e) {
                if (e.isExit()) {
                    System.exit(e.getExitStatus());
                } else {
                    throw e;
                }
            }
        }
    }

    static final class JarFileSystem implements FileSystem {
        static final record Entry(ZipEntry zipInfo, byte[] data) {};

        private static final TreeMap<String, Entry> VFS_ENTRIES = new TreeMap<>();
        static {
            try (var zis = new ZipInputStream(JarFileSystem.class.getResourceAsStream(RESOURCE_ZIP))) {
                try {
                    ZipEntry entry;
                    while ((entry = zis.getNextEntry()) != null) {
                        String name = entry.getName();
                        if (!name.startsWith("/")) {
                            name = "/" + name;
                        }
                        if (name.endsWith("/")) {
                            // a directory entry, add it also by its basename
                            VFS_ENTRIES.put(name, new Entry(entry, null));
                            VFS_ENTRIES.put(name.substring(0, name.length() - 1), new Entry(entry, null));
                        } else {
                            byte[] data = new byte[(int) entry.getSize()];
                            int position = 0;
                            int numRead = 0;
                            while ((numRead = zis.read(data, position, data.length - position)) > 0) {
                                position += numRead;
                            }
                            VFS_ENTRIES.put(name, new Entry(entry, data));
                        }
                    }
                } catch (IOException e) {
                    // fall through
                }
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }

        private final FileSystem delegate = FileSystem.newDefaultFileSystem();

        private Entry file(Path path) throws IOException {
            return VFS_ENTRIES.get(toRealPath(toAbsolutePath(path)).toString());
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
            if (path.normalize().startsWith(VFS_PREFIX)) {
                if (modes.contains(AccessMode.WRITE)) {
                    throw new IOException("read-only filesystem");
                }
                if (file(path) == null) {
                    throw new IOException("no such file or directory");
                }
            } else {
                delegate.checkAccess(path, modes, linkOptions);
            }
        }

        @Override
        public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
            if (dir.normalize().startsWith(VFS_PREFIX)) {
                throw new SecurityException("read-only filesystem");
            } else {
                delegate.createDirectory(dir, attrs);
            }
        }

        @Override
        public void delete(Path path) throws IOException {
            if (path.normalize().startsWith(VFS_PREFIX)) {
                throw new SecurityException("read-only filesystem");
            } else {
                delegate.delete(path);
            }
        }

        @Override
        public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
            if (!path.normalize().startsWith(VFS_PREFIX)) {
                return delegate.newByteChannel(path, options, attrs);
            }
            if (options.isEmpty() || (options.size() == 1 && options.contains(StandardOpenOption.READ))) {
                final Entry e = file(path);
                if (e == null) {
                    throw new IOException("no such file");
                }
                if (e.zipInfo.getName().endsWith("/")) {
                    throw new IOException("is a directory");
                }
                return new SeekableByteChannel() {
                    int position = 0;

                    @Override
                    public int read(ByteBuffer dst) throws IOException {
                        if (position > e.data.length) {
                            return -1;
                        } else if (position == e.data.length) {
                            return 0;
                        } else {
                            int length = Math.min(e.data.length - position, dst.remaining());
                            dst.put(e.data, position, length);
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
                        newPosition = Math.max(0, newPosition);
                        return this;
                    }

                    @Override
                    public long size() throws IOException {
                        return e.data.length;
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
            if (!dir.normalize().startsWith(VFS_PREFIX)) {
                return delegate.newDirectoryStream(dir, filter);
            }
            Entry e = file(dir);
            if (e == null) {
                throw new IOException("no such file or directory");
            }
            if (!e.zipInfo.getName().endsWith("/")) {
                // a file, not a directory
                throw new NotDirectoryException(dir.toString());
            }
            String dirName = toRealPath(toAbsolutePath(dir)).toString();
            return new DirectoryStream<>() {
                @Override
                public void close() throws IOException {
                    // nothing to do
                }

                @Override
                public Iterator<Path> iterator() {
                    return new Iterator<>() {
                        final Iterator<Entry> entries = VFS_ENTRIES.values().iterator();
                        final Path dirPath = Paths.get(dirName);
                        String next;

                        private void findNext() {
                            if (next == null) {
                                while (entries.hasNext()) {
                                    Entry nextEntry = entries.next();
                                    String eName = nextEntry.zipInfo.getName();
                                    if (!eName.startsWith("/")) {
                                        eName = "/" + eName;
                                    }
                                    Path entryPath = Paths.get(eName);
                                    Path parentPath = entryPath.getParent();
                                    if (dirPath.equals(parentPath)) {
                                        next = eName;
                                        return;
                                    }
                                }
                            }
                        }

                        @Override
                        public boolean hasNext() {
                            findNext();
                            return next != null;
                        }

                        @Override
                        public Path next() {
                            String nextEntry = next;
                            next = null;
                            return Paths.get(nextEntry);
                        }
                    };
                }
            };
        }

        @Override
        public Path toAbsolutePath(Path path) {
            if (path.startsWith("/")) {
                return path;
            } else {
                return Paths.get("/", path.toString());
            }
        }

        @Override
        public Path toRealPath(Path path, LinkOption... linkOptions) throws IOException {
            return path.normalize();
        }

        @Override
        public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
            if (!path.normalize().startsWith(VFS_PREFIX)) {
                return delegate.readAttributes(path, attributes, options);
            }
            Entry e = file(path);
            if (e == null) {
                throw new IOException("no such file");
            }
            HashMap<String, Object> attrs = new HashMap<>();
            if (attributes.startsWith("unix:") || attributes.startsWith("posix:") ) {
                throw new UnsupportedOperationException();
            }
            attrs.put("creationTime", FileTime.fromMillis(0));
            attrs.put("lastModifiedTime", FileTime.fromMillis(0));
            attrs.put("lastAccessTime", FileTime.fromMillis(0));
            attrs.put("isRegularFile", !e.zipInfo.getName().endsWith("/"));
            attrs.put("isDirectory", e.zipInfo.getName().endsWith("/"));
            attrs.put("isSymbolicLink", false);
            attrs.put("isOther", false);
            attrs.put("size", Math.max(0, e.zipInfo.getSize()));
            attrs.put("fileKey", e.zipInfo.getCrc());
            attrs.put("mode", 555);
            attrs.put("ino", e.zipInfo.getCrc());
            attrs.put("dev", 0L);
            attrs.put("nlink", 1);
            attrs.put("uid", 0);
            attrs.put("gid", 0);
            attrs.put("ctime", FileTime.fromMillis(0));
            return attrs;
        }
    }

}
