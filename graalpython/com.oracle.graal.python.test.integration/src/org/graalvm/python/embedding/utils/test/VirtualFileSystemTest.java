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

package org.graalvm.python.embedding.utils.test;

import static com.oracle.graal.python.test.integration.Utils.IS_WINDOWS;
import static org.graalvm.python.embedding.utils.VirtualFileSystem.HostIO.NONE;
import static org.graalvm.python.embedding.utils.VirtualFileSystem.HostIO.READ;
import static org.graalvm.python.embedding.utils.VirtualFileSystem.HostIO.READ_WRITE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.oracle.graal.python.test.integration.Utils;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Context.Builder;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.IOAccess;
import org.graalvm.python.embedding.utils.GraalPyResources;
import org.graalvm.python.embedding.utils.VirtualFileSystem;
import org.junit.Test;

public class VirtualFileSystemTest {

    static final String VFS_UNIX_MOUNT_POINT = "/test_mount_point";
    static final String VFS_WIN_MOUNT_POINT = "X:\\test_win_mount_point";
    static final String VFS_MOUNT_POINT = IS_WINDOWS ? VFS_WIN_MOUNT_POINT : VFS_UNIX_MOUNT_POINT;

    static final String PYTHON = "python";

    private final FileSystem rwHostIOVFS = VirtualFileSystem.newBuilder().//
                    allowHostIO(READ_WRITE).//
                    unixMountPoint(VFS_MOUNT_POINT).//
                    windowsMountPoint(VFS_WIN_MOUNT_POINT).//
                    extractFilter(p -> p.getFileName().toString().equals("extractme")).//
                    resourceLoadingClass(VirtualFileSystemTest.class).build();
    private final FileSystem rHostIOVFS = VirtualFileSystem.newBuilder().//
                    allowHostIO(READ).//
                    unixMountPoint(VFS_MOUNT_POINT).//
                    windowsMountPoint(VFS_WIN_MOUNT_POINT).//
                    extractFilter(p -> p.getFileName().toString().equals("extractme")).//
                    resourceLoadingClass(VirtualFileSystemTest.class).build();
    private final FileSystem noHostIOVFS = VirtualFileSystem.newBuilder().//
                    allowHostIO(NONE).//
                    unixMountPoint(VFS_MOUNT_POINT).//
                    windowsMountPoint(VFS_WIN_MOUNT_POINT).//
                    extractFilter(p -> p.getFileName().toString().equals("extractme")).//
                    resourceLoadingClass(VirtualFileSystemTest.class).build();

    public VirtualFileSystemTest() {
        Logger logger = Logger.getLogger(VirtualFileSystem.class.getName());
        for (Handler handler : logger.getHandlers()) {
            handler.setLevel(Level.FINE);
        }
        logger.setLevel(Level.FINE);
    }

    @Test
    public void defaultValues() throws Exception {
        VirtualFileSystem fs = VirtualFileSystem.create();
        VirtualFileSystem fs2 = VirtualFileSystem.create();

        assertEquals(fs.getMountPoint(), fs2.getMountPoint());

        assertEquals(IS_WINDOWS ? "X:\\graalpy_vfs" : "/graalpy_vfs", fs.getMountPoint());
    }

    @Test
    public void mountPoints() throws Exception {
        VirtualFileSystem fs = VirtualFileSystem.newBuilder().//
                        unixMountPoint(VFS_MOUNT_POINT).//
                        windowsMountPoint(VFS_WIN_MOUNT_POINT).build();

        assertEquals(VFS_MOUNT_POINT, fs.getMountPoint());
    }

    @Test
    public void toRealPath() throws Exception {
        // from VFS
        for (FileSystem fs : new FileSystem[]{rwHostIOVFS, rHostIOVFS, noHostIOVFS}) {
            // check regular resource dir
            assertEquals(Path.of(VFS_MOUNT_POINT + File.separator + "dir1"), fs.toRealPath(Path.of(VFS_MOUNT_POINT + File.separator + "dir1")));
            // check regular resource file
            assertEquals(Path.of(VFS_MOUNT_POINT + File.separator + "SomeFile"), fs.toRealPath(Path.of(VFS_MOUNT_POINT + File.separator + "SomeFile")));
            // check to be extracted file
            checkExtractedFile(fs.toRealPath(Path.of(VFS_MOUNT_POINT + File.separator + "extractme")), new String[]{"text1", "text2"});
            assertEquals(Path.of(VFS_MOUNT_POINT + File.separator + "does-not-exist/extractme"), fs.toRealPath(Path.of(VFS_MOUNT_POINT + File.separator + "does-not-exist/extractme")));
        }

        // from real FS
        final Path realFSPath = Files.createTempDirectory("graalpy.vfs.test").resolve("extractme");
        realFSPath.toFile().createNewFile();
        for (FileSystem fs : new FileSystem[]{rwHostIOVFS, rHostIOVFS}) {
            assertTrue(Files.isSameFile(realFSPath, fs.toRealPath(realFSPath)));
        }
        checkException(SecurityException.class, () -> noHostIOVFS.toRealPath(realFSPath), "expected error for no host io fs");
    }

    @Test
    public void toAbsolutePath() throws Exception {
        // from VFS
        for (FileSystem fs : new FileSystem[]{rwHostIOVFS, rHostIOVFS, noHostIOVFS}) {
            // check regular resource dir
            assertEquals(Path.of(VFS_MOUNT_POINT + File.separator + "dir1"), fs.toAbsolutePath(Path.of(VFS_MOUNT_POINT + File.separator + "dir1")));
            // check regular resource file
            assertEquals(Path.of(VFS_MOUNT_POINT + File.separator + "SomeFile"), fs.toAbsolutePath(Path.of(VFS_MOUNT_POINT + File.separator + "SomeFile")));
            // check to be extracted file
            checkExtractedFile(fs.toAbsolutePath(Path.of(VFS_MOUNT_POINT + File.separator + "extractme")), new String[]{"text1", "text2"});
            checkExtractedFile(fs.toAbsolutePath(Path.of(VFS_MOUNT_POINT + File.separator + "dir1/extractme")), null);
            assertEquals(Path.of(VFS_MOUNT_POINT + File.separator + "does-not-exist/extractme"), fs.toAbsolutePath(Path.of(VFS_MOUNT_POINT + File.separator + "does-not-exist/extractme")));
        }

        // from real FS
        Path realFSPath = Files.createTempDirectory("graalpy.vfs.test").resolve("extractme");
        realFSPath.toFile().createNewFile();
        for (FileSystem fs : new FileSystem[]{rwHostIOVFS, rHostIOVFS}) {
            assertEquals(realFSPath, fs.toAbsolutePath(realFSPath));
        }
        checkException(SecurityException.class, () -> noHostIOVFS.toAbsolutePath(realFSPath), "expected error for no host io fs");
    }

    @Test
    public void parseStringPath() throws Exception {
        parsePath(VirtualFileSystemTest::parseStringPath);
    }

    @Test
    public void parseURIPath() throws Exception {
        parsePath(VirtualFileSystemTest::parseURIPath);

        for (FileSystem fs : new FileSystem[]{rwHostIOVFS, rHostIOVFS, noHostIOVFS}) {
            checkException(UnsupportedOperationException.class, () -> fs.parsePath(URI.create("http://testvfs.org")), "only file uri is supported");
        }
    }

    public void parsePath(BiFunction<FileSystem, String, Path> parsePath) throws Exception {
        // from VFS
        for (FileSystem fs : new FileSystem[]{rwHostIOVFS, rHostIOVFS, noHostIOVFS}) {
            // check regular resource dir
            assertEquals(Path.of(VFS_MOUNT_POINT + File.separator + "dir1"), parsePath.apply(fs, VFS_MOUNT_POINT + File.separator + "dir1"));
            // check regular resource file
            assertEquals(Path.of(VFS_MOUNT_POINT + File.separator + "SomeFile"), parsePath.apply(fs, VFS_MOUNT_POINT + File.separator + "SomeFile"));
            // check to be extracted file
            Path p = parsePath.apply(fs, VFS_MOUNT_POINT + File.separator + "extractme");
            // wasn't extracted => we do not expect the path to exist on real FS
            assertFalse(Files.exists(p));
            assertEquals(Path.of(VFS_MOUNT_POINT + File.separator + "extractme"), p);
            p = parsePath.apply(fs, VFS_MOUNT_POINT + File.separator + "dir1/extractme");
            assertFalse(Files.exists(p));
            assertEquals(Path.of(VFS_MOUNT_POINT + File.separator + "dir1/extractme"), p);
            p = parsePath.apply(fs, VFS_MOUNT_POINT + File.separator + "does-not-exist/extractme");
            assertFalse(Files.exists(p));
            assertEquals(Path.of(VFS_MOUNT_POINT + File.separator + "does-not-exist/extractme"), p);
        }

        // from real FS
        Path realFSPath = Files.createTempDirectory("graalpy.vfs.test").resolve("extractme");
        realFSPath.toFile().createNewFile();
        for (FileSystem fs : new FileSystem[]{rwHostIOVFS, rHostIOVFS, noHostIOVFS}) {
            assertEquals(realFSPath, parsePath.apply(fs, realFSPath.toString()));
        }
    }

    private static Path parseStringPath(FileSystem fs, String p) {
        return fs.parsePath(p);
    }

    private static Path parseURIPath(FileSystem fs, String p) {
        if (IS_WINDOWS) {
            return fs.parsePath(URI.create("file:///" + p.replace('\\', '/')));
        } else {
            return fs.parsePath(URI.create("file://" + p));
        }
    }

    @Test
    public void checkAccess() throws IOException {
        // from VFS
        for (FileSystem fs : new FileSystem[]{rwHostIOVFS, rHostIOVFS, noHostIOVFS}) {
            // check regular resource dir
            fs.checkAccess(Path.of(VFS_MOUNT_POINT + File.separator + "dir1"), Set.of(AccessMode.READ));
            // check regular resource file
            fs.checkAccess(Path.of(VFS_MOUNT_POINT + File.separator + "SomeFile"), Set.of(AccessMode.READ));
            // check to be extracted file
            fs.checkAccess(Path.of(VFS_MOUNT_POINT + File.separator + "extractme"), Set.of(AccessMode.READ));

            checkException(
                            SecurityException.class,
                            () -> {
                                fs.checkAccess(Path.of(VFS_MOUNT_POINT + File.separator + "SomeFile"), Set.of(AccessMode.WRITE));
                                return null;
                            },
                            "write access should not be possible with VFS");
            checkException(
                            NoSuchFileException.class,
                            () -> {
                                fs.checkAccess(Path.of(VFS_MOUNT_POINT + File.separator + "does-not-exits"), Set.of(AccessMode.READ));
                                return null;
                            },
                            "should not be able to access a file which does not exist in VFS");
            checkException(
                            NoSuchFileException.class,
                            () -> {
                                fs.checkAccess(Path.of(VFS_MOUNT_POINT + File.separator + "does-not-exits/extractme"), Set.of(AccessMode.READ));
                                return null;
                            },
                            "should not be able to access a file which does not exist in VFS");
        }

        // from real FS
        Path realFSPath = Files.createTempDirectory("graalpy.vfs.test").resolve("extractme");
        realFSPath.toFile().createNewFile();

        for (FileSystem fs : new FileSystem[]{rwHostIOVFS, rHostIOVFS}) {
            fs.checkAccess(realFSPath, Set.of(AccessMode.READ));
        }
        checkException(SecurityException.class, () -> {
            noHostIOVFS.checkAccess(realFSPath, Set.of(AccessMode.READ));
            return null;
        }, "expected error for no host io fs");
    }

    @Test
    public void createDirectory() throws IOException {
        // from VFS
        for (FileSystem fs : new FileSystem[]{rwHostIOVFS, rHostIOVFS, noHostIOVFS}) {
            checkException(SecurityException.class, () -> {
                fs.createDirectory(Path.of(VFS_MOUNT_POINT + File.separator + "new-dir"));
                return null;
            }, "should not be able to create a directory in VFS");
        }

        // from real FS
        Path realFSPath = Files.createTempDirectory("graalpy.vfs.test").resolve("extractme");
        assertFalse(Files.exists(realFSPath));
        checkException(SecurityException.class, () -> {
            noHostIOVFS.createDirectory(realFSPath);
            return null;
        }, "expected error for no host io fs");
        assertFalse(Files.exists(realFSPath));

        checkException(SecurityException.class, () -> {
            rHostIOVFS.createDirectory(realFSPath);
            return null;
        }, "should not be able to create a directory in a read-only FS");
        assertFalse(Files.exists(realFSPath));

        rwHostIOVFS.createDirectory(realFSPath);
        assertTrue(Files.exists(realFSPath));
    }

    @Test
    public void delete() throws Exception {
        // from VFS
        for (FileSystem fs : new FileSystem[]{rwHostIOVFS, rHostIOVFS, noHostIOVFS}) {
            checkDelete(fs, VFS_MOUNT_POINT + File.separator + "file1");
            checkDelete(fs, VFS_MOUNT_POINT + File.separator + "dir1");
            checkDelete(fs, VFS_MOUNT_POINT + File.separator + "extractme");
        }

        // from real FS
        Path realFSPath = Files.createTempDirectory("graalpy.vfs.test").resolve("extractme");
        Files.createFile(realFSPath);
        assertTrue(Files.exists(realFSPath));

        checkException(SecurityException.class, () -> {
            noHostIOVFS.delete(realFSPath);
            return null;
        }, "expected error for no host io fs");
        assertTrue(Files.exists(realFSPath));

        checkException(SecurityException.class, () -> {
            rHostIOVFS.delete(realFSPath);
            return null;
        }, "should not be able to create a directory in a read-only FS");
        assertTrue(Files.exists(realFSPath));

        rwHostIOVFS.delete(realFSPath);
        assertFalse(Files.exists(realFSPath));
    }

    private static void checkDelete(FileSystem fs, String path) {
        checkException(SecurityException.class, () -> {
            fs.delete(Path.of(path));
            return null;
        }, "should not be able to delete in VFS");
    }

    @Test
    public void newByteChannel() throws IOException {
        // from VFS
        for (FileSystem fs : new FileSystem[]{rwHostIOVFS, rHostIOVFS, noHostIOVFS}) {
            Path path = Path.of(VFS_MOUNT_POINT + File.separator + "file1");
            for (StandardOpenOption o : StandardOpenOption.values()) {
                if (o == StandardOpenOption.READ) {
                    SeekableByteChannel bch = fs.newByteChannel(path, Set.of(o));
                    ByteBuffer buffer = ByteBuffer.allocate(1024);
                    bch.read(buffer);
                    String s = new String(buffer.array());
                    String[] ss = s.split(System.lineSeparator());
                    assertTrue(ss.length >= 2);
                    assertEquals("text1", ss[0]);
                    assertEquals("text2", ss[1]);

                    checkException(IOException.class, () -> bch.write(buffer), "should not be able to write to VFS");
                    checkException(IOException.class, () -> bch.truncate(0), "should not be able to write to VFS");
                } else {
                    checkCanOnlyRead(fs, path, o);
                }
            }
            checkCanOnlyRead(fs, path, StandardOpenOption.READ, StandardOpenOption.WRITE);
        }

        // from real FS
        Path realFSPath = Files.createTempDirectory("graalpy.vfs.test").resolve("extractme");
        Files.createFile(realFSPath);
        checkException(SecurityException.class, () -> rHostIOVFS.newByteChannel(realFSPath, Set.of(StandardOpenOption.WRITE)), "cant write into a read-only host FS");
        rwHostIOVFS.newByteChannel(realFSPath, Set.of(StandardOpenOption.WRITE)).write(ByteBuffer.wrap("text".getBytes()));
        assertTrue(Files.exists(realFSPath));

        for (FileSystem fs : new FileSystem[]{rwHostIOVFS, rHostIOVFS}) {
            SeekableByteChannel bch = fs.newByteChannel(realFSPath, Set.of(StandardOpenOption.READ));
            ByteBuffer buffer = ByteBuffer.allocate(4);
            bch.read(buffer);
            String s = new String(buffer.array());
            String[] ss = s.split(System.lineSeparator());
            assertTrue(ss.length >= 1);
            assertEquals("text", ss[0]);
        }
    }

    private static void checkCanOnlyRead(FileSystem fs, Path path, StandardOpenOption... options) {
        checkException(SecurityException.class, () -> fs.newByteChannel(path, Set.of(options)), "should only be able to read from VFS");
    }

    @Test
    public void newDirectoryStream() throws Exception {
        // from VFS
        for (FileSystem fs : new FileSystem[]{rwHostIOVFS, rHostIOVFS, noHostIOVFS}) {
            DirectoryStream<Path> ds = fs.newDirectoryStream(Path.of(VFS_MOUNT_POINT + File.separator + "dir1"), (p) -> true);
            Set<String> s = new HashSet<>();
            Iterator<Path> it = ds.iterator();
            while (it.hasNext()) {
                Path p = it.next();
                s.add(p.toString());
            }
            assertEquals(2, s.size());
            assertTrue(s.contains(VFS_MOUNT_POINT + File.separator + "dir1" + File.separator + "extractme"));
            assertTrue(s.contains(VFS_MOUNT_POINT + File.separator + "dir1" + File.separator + "file2"));

            ds = fs.newDirectoryStream(Path.of(VFS_MOUNT_POINT + File.separator + "dir1"), (p) -> false);
            assertFalse(ds.iterator().hasNext());

            checkException(NotDirectoryException.class, () -> fs.newDirectoryStream(Path.of(VFS_MOUNT_POINT + File.separator + "file1"), (p) -> true), "");
            checkException(NoSuchFileException.class, () -> fs.newDirectoryStream(Path.of(VFS_MOUNT_POINT + File.separator + "does-not-exist"), (p) -> true), "");
        }

        // from real FS
        Path realFSPath = Files.createTempDirectory("graalpy.vfs.test").resolve("extractme");
        Files.createFile(realFSPath);
        for (FileSystem fs : new FileSystem[]{rHostIOVFS, rwHostIOVFS}) {
            DirectoryStream<Path> ds = fs.newDirectoryStream(realFSPath.getParent(), (p) -> true);
            Iterator<Path> it = ds.iterator();
            assertEquals(realFSPath, it.next());
            assertFalse(it.hasNext());
            ds = fs.newDirectoryStream(realFSPath.getParent(), (p) -> false);
            it = ds.iterator();
            assertFalse(it.hasNext());
        }
        checkException(SecurityException.class, () -> noHostIOVFS.newDirectoryStream(realFSPath, null), "expected error for no host io fs");
    }

    @Test
    public void readAttributes() throws IOException {
        // from VFS
        for (FileSystem fs : new FileSystem[]{rwHostIOVFS, rHostIOVFS, noHostIOVFS}) {
            Map<String, Object> attrs = fs.readAttributes(Path.of(VFS_MOUNT_POINT + File.separator + "dir1"), "creationTime");
            assertEquals(FileTime.fromMillis(0), attrs.get("creationTime"));

            checkException(NoSuchFileException.class, () -> fs.readAttributes(Path.of(VFS_MOUNT_POINT + File.separator + "does-not-exist"), "creationTime"), "");
            checkException(UnsupportedOperationException.class, () -> fs.readAttributes(Path.of(VFS_MOUNT_POINT + File.separator + "file1"), "unix:creationTime"), "");
        }

        // from real FS
        Path realFSPath = Files.createTempDirectory("graalpy.vfs.test").resolve("extractme");
        Files.createFile(realFSPath);
        for (FileSystem fs : new FileSystem[]{rHostIOVFS, rwHostIOVFS}) {
            Map<String, Object> attrs = fs.readAttributes(realFSPath, "creationTime");
            assertTrue(((FileTime) attrs.get("creationTime")).toMillis() > 0);
        }
        checkException(SecurityException.class, () -> noHostIOVFS.readAttributes(realFSPath, "creationTime"), "expected error for no host io fs");
    }

    @Test
    public void libsExtract() throws Exception {
        FileSystem fs = VirtualFileSystem.newBuilder().//
                        unixMountPoint(VFS_MOUNT_POINT).//
                        windowsMountPoint(VFS_WIN_MOUNT_POINT).//
                        extractFilter(p -> p.getFileName().toString().endsWith(".tso")).//
                        resourceLoadingClass(VirtualFileSystemTest.class).build();
        Path p = fs.toAbsolutePath(Path.of(VFS_MOUNT_POINT + File.separator + "site-packages/testpkg/file.tso"));
        checkExtractedFile(p, null);
        Path extractedRoot = p.getParent().getParent().getParent();

        checkExtractedFile(extractedRoot.resolve("site-packages/testpkg.libs/file1.tso"), null);
        checkExtractedFile(extractedRoot.resolve("site-packages/testpkg.libs/file2.tso"), null);
        checkExtractedFile(extractedRoot.resolve("site-packages/testpkg.libs/dir/file1.tso"), null);
        checkExtractedFile(extractedRoot.resolve("site-packages/testpkg.libs/dir/file2.tso"), null);
        checkExtractedFile(extractedRoot.resolve("site-packages/testpkg.libs/dir/nofilterfile"), null);
        checkExtractedFile(extractedRoot.resolve("site-packages/testpkg.libs/dir/dir/file1.tso"), null);
        checkExtractedFile(extractedRoot.resolve("site-packages/testpkg.libs/dir/dir/file2.tso"), null);

        p = fs.toAbsolutePath(Path.of(VFS_MOUNT_POINT + File.separator + "site-packages/testpkg-nolibs/file.tso"));
        checkExtractedFile(p, null);
    }

    private static void checkExtractedFile(Path extractedFile, String[] expectedContens) throws IOException {
        assertTrue(Files.exists(extractedFile));
        List<String> lines = Files.readAllLines(extractedFile);
        if (expectedContens != null) {
            assertEquals("expected " + expectedContens.length + " lines in extracted file '" + extractedFile + "'", expectedContens.length, lines.size());
            for (String line : expectedContens) {
                assertTrue("expected line '" + line + "' in file '" + extractedFile + "' with contents:\n" + expectedContens, lines.contains(line));
            }
        } else {
            assertEquals("extracted file '" + extractedFile + "' expected to be empty, but had " + lines.size() + " lines", 0, lines.size());
        }
    }

    private static void checkException(Class<?> exType, Callable<Object> c) {
        checkException(exType, c, null);
    }

    private static void checkException(Class<?> exType, Callable<Object> c, String msg) {
        boolean gotEx = false;
        try {
            c.call();
        } catch (Exception e) {
            assert e.getClass() == exType;
            gotEx = true;
        }
        assertTrue(msg != null ? msg : "expected " + exType.getName(), gotEx);
    }

    @Test
    public void fsOperations() {

        // os.path.exists
        eval("import os; assert os.path.exists('/test_mount_point')");
        eval("import os; assert os.path.exists('/test_mount_point/file1')");
        eval("import os; assert os.path.exists('/test_mount_point/dir1')");
        eval("import os; assert os.path.exists('/test_mount_point/dir1/')");
        eval("import os; assert os.path.exists('/test_mount_point/emptydir')");
        eval("import os; assert os.path.exists('/test_mount_point/emptydir/')");
        eval("import os; assert os.path.exists('/test_mount_point/dir1/file2')");
        eval("import os; assert not os.path.exists('/test_mount_point/doesnotexist')");
        eval("import os; assert not os.path.exists('/test_mount_point/doesnotexist/')");

        // pathlib.exists
        eval("from pathlib import Path; assert Path('/test_mount_point').exists()");
        eval("from pathlib import Path; assert Path('/test_mount_point/file1').exists()");
        eval("from pathlib import Path; assert Path('/test_mount_point/dir1').exists()");
        eval("from pathlib import Path; assert Path('/test_mount_point/dir1/').exists()");
        eval("from pathlib import Path; assert Path('/test_mount_point/emptydir').exists()");
        eval("from pathlib import Path; assert Path('/test_mount_point/emptydir/').exists()");
        eval("from pathlib import Path; assert not Path('/test_mount_point/doesnotexist').exists()");
        eval("from pathlib import Path; assert not Path('/test_mount_point/doesnotexist/').exists()");

        // path.isfile|isdir

        eval("import os; assert os.path.isfile('/test_mount_point/file1')");
        eval("import os; assert not os.path.isfile('/test_mount_point/dir1')");
        eval("import os; assert not os.path.isfile('/test_mount_point/dir1/')");

        eval("import os; assert not os.path.isfile('/test_mount_point')");
        eval("import os; assert os.path.isdir('/test_mount_point')");
        eval("import os; assert not os.path.isdir('/test_mount_point/file1')");
        eval("import os; assert os.path.isdir('/test_mount_point/dir1')");
        eval("import os; assert os.path.isdir('/test_mount_point/dir1/')");

        // pathlib.is_file|is_dir

        eval("from pathlib import Path; assert not Path('/test_mount_point').is_file()");
        eval("from pathlib import Path; assert Path('/test_mount_point/file1').is_file()");
        eval("from pathlib import Path; assert not Path('/test_mount_point/dir1').is_file()");
        eval("from pathlib import Path; assert not Path('/test_mount_point/dir1/').is_file()");

        eval("from pathlib import Path; assert Path('/test_mount_point').is_dir()");
        eval("from pathlib import Path; assert not Path('/test_mount_point/file1').is_dir()");
        eval("from pathlib import Path; assert Path('/test_mount_point/dir1').is_dir()");
        eval("from pathlib import Path; assert Path('/test_mount_point/dir1/').is_dir()");

        // delete os.remove|rmdir

        eval("""
                        import os
                        try:
                            os.remove('/test_mount_point/doesnotexist')
                        except OSError:
                            pass
                        """);
        eval("""
                        import os
                        try:
                            os.remove('/test_mount_point/file1')
                        except OSError:
                            pass
                        """);
        eval("""
                        import os
                        try:
                            os.rmdir('/test_mount_point/file1')
                        except OSError:
                            pass
                        """);
        eval("""
                        import os
                        try:
                            os.remove('/test_mount_point/dir1')
                        except OSError:
                            pass
                        """);
        eval("""
                        import os
                        try:
                            os.rmdir('/test_mount_point/dir1')
                        except OSError:
                            pass
                        """);
        eval("""
                        import os
                        try:
                            os.rmdir('/test_mount_point/emptydir')
                        except OSError:
                            pass
                        """);

        // delete pathlib.unlink|rmdir

        eval("""
                        from pathlib import Path
                        try:
                            Path('/test_mount_point/doesnotexist').unlink()
                        except OSError:
                            pass
                        """);
        eval("""
                        from pathlib import Path
                        try:
                            Path('/test_mount_point/file').unlink()
                        except OSError:
                            pass
                        """);
        eval("""
                        from pathlib import Path
                        try:
                            Path('/test_mount_point/file1').rmdir()
                        except OSError:
                            pass
                        """);
        eval("""
                        from pathlib import Path
                        try:
                            Path('/test_mount_point/dir1').unlink()
                        except OSError:
                            pass
                        """);
        eval("""
                        from pathlib import Path
                        try:
                            Path('/test_mount_point/dir1').rmdir()
                        except OSError:
                            pass
                        """);
        eval("""
                        from pathlib import Path
                        try:
                            Path('/test_mount_point/emptydir').rmdir()
                        except OSError:
                            pass
                        """);

        // delete shutil.rmtree

        eval("""
                        import shutil
                        try:
                            shutil.rmtree('/test_mount_point/dir1')
                        except OSError:
                            pass
                        """);
        eval("""
                        import shutil
                        try:
                            shutil.rmtree('/test_mount_point/emptydir')
                        except OSError:
                            pass
                        """);

        // os.listdir

        eval("""
                        from os import listdir
                        try:
                            f = listdir('/test_mount_point/doesnotexist')
                        except FileNotFoundError:
                            pass
                        except Error:
                            assert False, 'expected FileNotFoundError'

                        f = listdir('/test_mount_point/emptydir')
                        assert len(f) == 0, 'expected no files'

                        f = listdir('/test_mount_point/')
                        assert len(f) == 7, 'expected 7 files, got ' + str(len(f))

                        assert 'dir1' in f, 'does not contain "dir1"'
                        assert 'emptydir' in f, 'does not contain "emptydir"'
                        assert 'file1' in f, 'does not contain "file1"'
                        assert 'fileslist.txt' in f, 'does not contain "fileslist.txt"'

                        f = listdir('/test_mount_point/dir1')
                        if len(f) != 2:
                            print('files in /test_mount_point/dir1:')
                            for ff in f:
                                print(ff)
                            assert False, 'expected 3 got ' + str(len(f))
                        assert 'file2' in f, 'does not contain "file2"'
                        assert 'extractme' in f, 'does not contain "extractme"'
                        """);

        // os.walk

        eval("""
                        from os import walk
                        i = 0
                        for r, d, f in walk('/test_mount_point/doesnotexist'):
                            i = i + 1
                        assert i == 0

                        for r, d, f in walk('/test_mount_point/emptydir'):
                            i = i + 1
                            assert r == '/test_mount_point/emptydir', 'expected /test_mount_point/emptydir, got' + r
                            assert len(d) == 0, 'expected no dirs in emptydir'
                            assert len(f) == 0, 'expected no files in emptydir'
                        assert i == 1

                        roots = set()
                        dirs = set()
                        files = set()
                        for r, d, f in walk('/test_mount_point/'):
                            roots.add(r)
                            for ff in f:
                                files.add(r + "/" + ff)
                            for dd in d:
                                dirs.add(r + "/" + dd)

                        assert len(roots) == 9, 'expected 10 roots, got ' + str(len(roots))
                        assert len(files) == 15, 'expected 15 files, got ' + str(len(files))
                        assert len(dirs) == 8, 'expected 8 dirs, got ' + str(len(dirs))
                        """);

        // read file

        eval("""
                        with open("/test_mount_point/file1", "r") as f:
                            l = f.readlines()
                            assert len(l) == 2, 'expect 2 lines, got ' + len(l)
                            assert l[0].startswith("text1"), f'expected "text1", got "{l[0]}"'
                            assert l[1].startswith("text2"), f'expected "text2", got "{l[1]}"'

                        with open("/test_mount_point/dir1/file2", "r") as f:
                            l = f.readlines()
                            assert len(l) == 0, 'expect 0 lines from empty file, got ' + str(len(l))
                        """);

        // write file
        eval("""
                        try:
                            f = open("/test_mount_point/file1", "w")
                        except OSError:
                            pass
                        """);
    }

    @Test
    public void fsOperationsCaseInsensitive() {
        eval("""
                        import os
                        assert os.path.exists('/test_mount_point/SomeFile')
                        assert os.path.exists('/test_mount_point/someFile')
                        assert os.path.exists('/test_mount_point/somefile')
                        assert not os.path.exists('/test_mount_point/somefile1')
                        """, b -> b.caseInsensitive(true));
    }

    private void eval(String s) {
        eval(s, null);
    }

    private void eval(String s, Function<VirtualFileSystem.Builder, VirtualFileSystem.Builder> builderFunction) {
        String src = patchMountPoint(s);

        getContext(builderFunction).eval(PYTHON, src);
    }

    private static String patchMountPoint(String src) {
        if (IS_WINDOWS) {
            return src.replace(VFS_UNIX_MOUNT_POINT, "X:\\\\test_win_mount_point");
        }
        return src;
    }

    private Context cachedContext;

    public Context getContext(Function<VirtualFileSystem.Builder, VirtualFileSystem.Builder> builderFunction) {
        if (builderFunction == null && cachedContext != null) {
            return cachedContext;
        }
        VirtualFileSystem.Builder builder = VirtualFileSystem.newBuilder().//
                        unixMountPoint(VFS_MOUNT_POINT).//
                        windowsMountPoint(VFS_WIN_MOUNT_POINT).//
                        extractFilter(p -> p.getFileName().toString().endsWith(".tso")).//
                        resourceLoadingClass(VirtualFileSystemTest.class);
        if (builderFunction != null) {
            builder = builderFunction.apply(builder);
        }
        VirtualFileSystem fs = builder.build();
        Context context = addTestOptions(GraalPyResources.contextBuilder(fs)).build();
        if (builderFunction == null) {
            cachedContext = context;
        }
        return context;
    }

    @Test
    public void vfsBuilderTest() {
        Context context = addTestOptions(GraalPyResources.contextBuilder()).allowAllAccess(true).allowHostAccess(HostAccess.ALL).build();
        context.eval(PYTHON, "import java; java.type('java.lang.String')");

        context = addTestOptions(GraalPyResources.contextBuilder()).allowAllAccess(false).allowHostAccess(HostAccess.NONE).build();
        context.eval(PYTHON, """
                        import java
                        try:
                            java.type('java.lang.String');
                        except NotImplementedError:
                            pass
                        else:
                            assert False, 'expected NotImplementedError'
                        """);

        VirtualFileSystem fs = VirtualFileSystem.newBuilder().//
                        unixMountPoint(VFS_MOUNT_POINT).//
                        windowsMountPoint(VFS_WIN_MOUNT_POINT).//
                        resourceLoadingClass(VirtualFileSystemTest.class).build();
        context = addTestOptions(GraalPyResources.contextBuilder(fs)).build();
        context.eval(PYTHON, patchMountPoint("from os import listdir; listdir('/test_mount_point')"));

        context = GraalPyResources.createContext();
        context.eval(PYTHON, "from os import listdir; listdir('.')");

        context = addTestOptions(GraalPyResources.contextBuilder()).allowIO(IOAccess.NONE).build();
        boolean gotPE = false;
        try {
            context.eval(PYTHON, "from os import listdir; listdir('.')");
        } catch (PolyglotException pe) {
            gotPE = true;
        }
        assert gotPE : "expected PolyglotException";
    }

    @Test
    public void externalResourcesBuilderTest() throws IOException {
        VirtualFileSystem fs = VirtualFileSystem.newBuilder().resourceLoadingClass(VirtualFileSystemTest.class).build();
        Path resourcesDir = Files.createTempDirectory("vfs-test-resources");

        // extract VFS
        GraalPyResources.extractVirtualFileSystemResources(fs, resourcesDir);

        // check extracted contents
        InputStream stream = VirtualFileSystemTest.class.getResourceAsStream("/org.graalvm.python.vfs/fileslist.txt");
        BufferedReader br = new BufferedReader(new InputStreamReader(stream));
        String line;
        while ((line = br.readLine()) != null) {
            line = line.substring("/org.graalvm.python.vfs/".length());
            if (line.length() == 0) {
                continue;
            }
            Path extractedFile = resourcesDir.resolve(line);
            assert Files.exists(extractedFile);
            if (line.endsWith("/")) {
                assert Files.isDirectory(extractedFile);
            }
        }
        checkExtractedFile(resourcesDir.resolve(Path.of("file1")), new String[]{"text1", "text2"});

        // create context with extracted resource dir and check if we can see the extracted file
        try (Context context = addTestOptions(GraalPyResources.contextBuilder(resourcesDir)).build()) {
            context.eval("python", "import os; assert os.path.exists('" + resourcesDir.resolve("file1").toString().replace("\\", "\\\\") + "')");
        }
    }

    @Test
    public void vfsMountPointTest() {
        if (IS_WINDOWS) {
            checkException(IllegalArgumentException.class, () -> VirtualFileSystem.newBuilder().windowsMountPoint("test").build());
            checkException(IllegalArgumentException.class, () -> VirtualFileSystem.newBuilder().windowsMountPoint("test\\").build());
            checkException(IllegalArgumentException.class, () -> VirtualFileSystem.newBuilder().windowsMountPoint("\\test\\").build());
            checkException(IllegalArgumentException.class, () -> VirtualFileSystem.newBuilder().windowsMountPoint("\\test").build());
            checkException(InvalidPathException.class, () -> VirtualFileSystem.newBuilder().windowsMountPoint("X:\\test|test").build());
        } else {
            checkException(IllegalArgumentException.class, () -> VirtualFileSystem.newBuilder().unixMountPoint("test").build());
            checkException(IllegalArgumentException.class, () -> VirtualFileSystem.newBuilder().unixMountPoint("test/").build());
            checkException(IllegalArgumentException.class, () -> VirtualFileSystem.newBuilder().unixMountPoint("/test/").build());
            checkException(InvalidPathException.class, () -> VirtualFileSystem.newBuilder().unixMountPoint("/test/\0").build());
        }
    }

    private static Builder addTestOptions(Builder builder) {
        return builder.option("engine.WarnInterpreterOnly", "false");
    }

}
