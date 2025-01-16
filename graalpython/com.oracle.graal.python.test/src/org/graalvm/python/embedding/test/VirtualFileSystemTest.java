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

package org.graalvm.python.embedding.test;

import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.python.embedding.VirtualFileSystem;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.NotLinkException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static com.oracle.graal.python.test.integration.Utils.IS_WINDOWS;
import static org.graalvm.python.embedding.VirtualFileSystem.HostIO.NONE;
import static org.graalvm.python.embedding.VirtualFileSystem.HostIO.READ;
import static org.graalvm.python.embedding.VirtualFileSystem.HostIO.READ_WRITE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class VirtualFileSystemTest {

    private static String MOUNT_POINT_NAME = "test_mount_point";
    static final String VFS_UNIX_MOUNT_POINT = "/test_mount_point";
    static final String VFS_WIN_MOUNT_POINT = "X:\\test_mount_point";
    static final String VFS_MOUNT_POINT = IS_WINDOWS ? VFS_WIN_MOUNT_POINT : VFS_UNIX_MOUNT_POINT;

    private static final Path VFS_ROOT_PATH = Path.of(VFS_MOUNT_POINT);

    private FileSystem rwHostIOVFS;
    private FileSystem rHostIOVFS;
    private FileSystem noHostIOVFS;

    private FileSystem getDelegatingFS(VirtualFileSystem vfs) throws NoSuchFieldException, IllegalAccessException {
        Field f = vfs.getClass().getDeclaredField("impl");
        f.setAccessible(true);
        toBeClosed.add((AutoCloseable) f.get(vfs));
        f = vfs.getClass().getDeclaredField("delegatingFileSystem");
        f.setAccessible(true);
        return (FileSystem) f.get(vfs);
    }

    public VirtualFileSystemTest() {
        Logger logger = Logger.getLogger(VirtualFileSystem.class.getName());
        for (Handler handler : logger.getHandlers()) {
            handler.setLevel(Level.FINE);
        }
        logger.setLevel(Level.FINE);
    }

    private List<AutoCloseable> toBeClosed = new ArrayList<>();

    @Before
    public void initFS() throws Exception {
        rwHostIOVFS = getDelegatingFS(VirtualFileSystem.newBuilder().//
                        allowHostIO(READ_WRITE).//
                        unixMountPoint(VFS_UNIX_MOUNT_POINT).//
                        windowsMountPoint(VFS_WIN_MOUNT_POINT).//
                        extractFilter(p -> p.getFileName().toString().endsWith("extractme")).//
                        resourceLoadingClass(VirtualFileSystemTest.class).build());
        rHostIOVFS = getDelegatingFS(VirtualFileSystem.newBuilder().//
                        allowHostIO(READ).//
                        unixMountPoint(VFS_UNIX_MOUNT_POINT).//
                        windowsMountPoint(VFS_WIN_MOUNT_POINT).//
                        extractFilter(p -> p.getFileName().toString().endsWith("extractme")).//
                        resourceLoadingClass(VirtualFileSystemTest.class).build());
        noHostIOVFS = getDelegatingFS(VirtualFileSystem.newBuilder().//
                        allowHostIO(NONE).//
                        unixMountPoint(VFS_UNIX_MOUNT_POINT).//
                        windowsMountPoint(VFS_WIN_MOUNT_POINT).//
                        extractFilter(p -> p.getFileName().toString().endsWith("extractme")).//
                        resourceLoadingClass(VirtualFileSystemTest.class).build());
    }

    @After
    public void close() throws Exception {
        Iterator<AutoCloseable> it = toBeClosed.iterator();
        while (it.hasNext()) {
            it.next().close();
            it.remove();
        }
    }

    @Test
    public void toRealPath() throws Exception {
        // from VFS
        for (FileSystem fs : new FileSystem[]{rwHostIOVFS, rHostIOVFS, noHostIOVFS}) {
            checkException(NullPointerException.class, () -> fs.toRealPath(null));
            toRealPathVFS(fs, VFS_MOUNT_POINT);
            withCWD(fs, VFS_ROOT_PATH, (fst) -> toRealPathVFS(fst, ""));
        }

        // from real FS
        final Path realFSDir = Files.createTempDirectory("graalpy.vfs.test");
        final Path realFSPath = realFSDir.resolve("extractme");
        realFSPath.toFile().createNewFile();
        for (FileSystem fs : new FileSystem[]{rwHostIOVFS, rHostIOVFS}) {
            assertTrue(Files.isSameFile(realFSPath, fs.toRealPath(realFSPath)));
            withCWD(fs, realFSDir, (fst) -> assertTrue(Files.isSameFile(realFSPath, fst.toRealPath(Path.of("..", realFSPath.getParent().getFileName().toString(), "extractme")))));
            withCWD(fs, VFS_ROOT_PATH, (fst) -> assertTrue(Files.isSameFile(realFSPath, fst.toRealPath(Path.of("..", realFSPath.toString())))));
        }
        checkException(SecurityException.class, () -> noHostIOVFS.toRealPath(realFSPath), "expected error for no host io fs");
    }

    private static void toRealPathVFS(FileSystem fs, String pathPrefix) throws IOException {
        assertEquals(Path.of(VFS_MOUNT_POINT, "dir1"), fs.toRealPath(Path.of(pathPrefix, "dir1")));
        assertEquals(Path.of(VFS_MOUNT_POINT, "SomeFile"), fs.toRealPath(Path.of(pathPrefix, "SomeFile")));
        assertEquals(Path.of(VFS_MOUNT_POINT, "does-not-exist"), fs.toRealPath(Path.of(pathPrefix, "does-not-exist")));
        assertEquals(Path.of(VFS_MOUNT_POINT, "extractme"), fs.toRealPath(Path.of(pathPrefix, "extractme"), LinkOption.NOFOLLOW_LINKS));
        checkExtractedFile(fs.toRealPath(Path.of(pathPrefix, "extractme")), new String[]{"text1", "text2"});
        checkException(NoSuchFileException.class, () -> fs.toRealPath(Path.of(pathPrefix, "does-not-exist", "extractme")));
    }

    @Test
    public void toAbsolutePath() throws Exception {
        // VFS
        for (FileSystem fs : new FileSystem[]{rwHostIOVFS, rHostIOVFS, noHostIOVFS}) {
            checkException(NullPointerException.class, () -> fs.toAbsolutePath(null));

            assertEquals(Path.of(VFS_MOUNT_POINT, "dir1"), fs.toAbsolutePath(Path.of(VFS_MOUNT_POINT, "dir1")));
            assertEquals(Path.of(VFS_MOUNT_POINT, "SomeFile"), fs.toAbsolutePath(Path.of(VFS_MOUNT_POINT, "SomeFile")));

            if (fs == noHostIOVFS) {
                // cwd is by default set to VFS_ROOT/src
                assertEquals(Path.of(VFS_MOUNT_POINT, "src", "dir1"), fs.toAbsolutePath(Path.of("dir1")));
                assertEquals(Path.of(VFS_MOUNT_POINT, "src", "SomeFile"), fs.toAbsolutePath(Path.of("SomeFile")));
                assertEquals(Path.of(VFS_MOUNT_POINT, "src", "does-not-exist", "extractme"), fs.toAbsolutePath(Path.of("does-not-exist", "extractme")));
            } else {
                // without cwd set, the real FS absolute path is returned given by jdk cwd
                assertEquals(Path.of("dir1").toAbsolutePath(), fs.toAbsolutePath(Path.of("dir1")));
                assertEquals(Path.of("SomeFile").toAbsolutePath(), fs.toAbsolutePath(Path.of("SomeFile")));
                assertEquals(Path.of("does-not-exist", "extractme").toAbsolutePath(), fs.toAbsolutePath(Path.of("does-not-exist", "extractme")));
            }

            withCWD(fs, VFS_ROOT_PATH, (fst) -> {
                assertEquals(Path.of(VFS_MOUNT_POINT, "extractme"), fst.toAbsolutePath(Path.of("extractme")));
                assertEquals(Path.of(VFS_MOUNT_POINT, "does-not-exist", "extractme"), fst.toAbsolutePath(Path.of("does-not-exist", "extractme")));
            });
        }

        // real FS
        Path realFSDir = Files.createTempDirectory("graalpy.vfs.test");
        Path realFSDirDir = realFSDir.resolve("dir");
        Files.createDirectories(realFSDirDir);
        Path realFSPath = realFSDir.resolve("extractme");
        realFSPath.toFile().createNewFile();
        for (FileSystem fs : new FileSystem[]{rwHostIOVFS, rHostIOVFS}) {
            // assertEquals(Path.of("extractme").toAbsolutePath(),
            // fs.toAbsolutePath(Path.of("extractme")));
            Path p = fs.toAbsolutePath(Path.of("extractme"));
            assertTrue(p.isAbsolute());
            assertEquals(Path.of("extractme").toAbsolutePath().normalize(), p.normalize());

            // absolute path starting with VFS, pointing to real FS
            // /VFS_ROOT/../real/fs/path/
            p = Path.of(VFS_MOUNT_POINT, "..", realFSPath.toString());
            assertEquals(p, fs.toAbsolutePath(p));

            // absolute path starting with real FS, pointing to VFS
            // /real/fs/path/../../../VFS_MOUNT_POINT
            // XXX return same abs path ???
            p = Path.of(fromPathToFSRoot(realFSDir).toString(), MOUNT_POINT_NAME);
            assertEquals(p, fs.toAbsolutePath(p));
            // /real/fs/path/../../../VFS_MOUNT_POINT/../VFS_MOUNT_POINT
            p = Path.of(fromPathToFSRoot(realFSDir).toString(), MOUNT_POINT_NAME, "..", MOUNT_POINT_NAME);
            assertEquals(p, fs.toAbsolutePath(p));

            // no CWD set, so relative path starting in real FS, pointing to VFS
            // ../../../VFS_ROOT
            Path cwd = Path.of(".").toAbsolutePath();
            p = fs.toAbsolutePath(Path.of(dotdot(cwd.getNameCount()).toString(), MOUNT_POINT_NAME));
            assertTrue(p.isAbsolute());
            assertEquals(VFS_ROOT_PATH, p.normalize());

            // ../../../VFS_ROOT/../real/fs/path
            p = fs.toAbsolutePath(Path.of(dotdot(cwd.getNameCount()).toString(), MOUNT_POINT_NAME, "..", realFSPath.toString()));
            assertTrue(p.isAbsolute());
            assertEquals(realFSPath, p.normalize());

            // CWD is VFS_ROOT, relative path pointing to real FS
            // ../real/fs/path
            withCWD(fs, VFS_ROOT_PATH, (fst) -> {
                Path pp = fst.toAbsolutePath(Path.of("..", realFSPath.toString()));
                assertTrue(pp.isAbsolute());
                assertEquals(realFSPath, pp.normalize());
            });

            // CWD is VFS_ROOT, relative path pointing through real FS back to VFS
            // ../some/path/../../VFS_ROOT_PATH
            withCWD(fs, VFS_ROOT_PATH, (fst) -> {
                Path pp = fst.toAbsolutePath(Path.of("..", "some", "path", "..", "..", MOUNT_POINT_NAME));
                assertTrue(pp.isAbsolute());
                assertEquals(VFS_ROOT_PATH, pp.normalize());
            });

            // CWD is real FS, relative path pointing to VFS
            // real/fs/path/../../
            withCWD(fs, realFSPath.getParent(), (fst) -> {
                Path pp = fst.toAbsolutePath(Path.of("dir", dotdot(realFSDirDir.getNameCount()), MOUNT_POINT_NAME));
                assertTrue(pp.isAbsolute());
                assertEquals(VFS_ROOT_PATH, pp.normalize());
            });

            // CWD is real FS, relative path pointing through VFS to real FS
            // real/fs/path/../../../VFS
            withCWD(fs, realFSPath.getParent(),
                            (fst) -> {
                                Path pp = fst.toAbsolutePath(Path.of("dir", dotdot(realFSDirDir.getNameCount()), MOUNT_POINT_NAME, "..", realFSPath.toString()));
                                assertTrue(pp.isAbsolute());
                                assertEquals(realFSPath, pp.normalize());
                            });

            assertEquals(Path.of("extractme").toAbsolutePath(), fs.toAbsolutePath(Path.of("extractme")));

            withCWD(fs, realFSPath.getParent(), (fst) -> assertEquals(realFSPath, fst.toAbsolutePath(realFSPath.getFileName())));
        }

        // noHostIOVFS sets default CDW to /VFS_MOUNT_POINT/src
        assertEquals(realFSPath, noHostIOVFS.toAbsolutePath(realFSPath));
        assertEquals(Path.of(VFS_MOUNT_POINT, "src", "extractme"), noHostIOVFS.toAbsolutePath(Path.of("extractme")));

        // absolute path starting with real FS, pointing to VFS
        // /real/fs/path/../../../VFS_ROOT
        Path p = Path.of(realFSDir.toString(), dotdot(realFSDir.getNameCount()), MOUNT_POINT_NAME);
        assertEquals(p, noHostIOVFS.toAbsolutePath(p));

        // no CWD set, relative path starting in real FS, pointing to VFS
        // ../../../VFS_ROOT
        Path defaultCWD = Path.of(".").toAbsolutePath();
        p = Path.of(dotdot(defaultCWD.getNameCount()), MOUNT_POINT_NAME);
        assertEquals(Path.of(VFS_MOUNT_POINT, "src", p.toString()), noHostIOVFS.toAbsolutePath(p));
    }

    @Test
    public void parseStringPath() throws Exception {
        parsePath(VirtualFileSystemTest::parseStringPath);
    }

    @Test
    public void parseURIPath() throws Exception {
        parsePath(VirtualFileSystemTest::parseURIPath);

        for (FileSystem fs : new FileSystem[]{rwHostIOVFS, rHostIOVFS, noHostIOVFS}) {
            checkException(NullPointerException.class, () -> fs.parsePath((URI) null));
            checkException(NullPointerException.class, () -> fs.parsePath((String) null));
            checkException(UnsupportedOperationException.class, () -> fs.parsePath(URI.create("http://testvfs.org")), "only file uri is supported");
        }
    }

    public void parsePath(BiFunction<FileSystem, String, Path> parsePath) throws Exception {
        // from VFS
        for (FileSystem fs : new FileSystem[]{rwHostIOVFS, rHostIOVFS, noHostIOVFS}) {
            // check regular resource dir
            assertEquals(VFS_ROOT_PATH.resolve("dir1"), parsePath.apply(fs, VFS_MOUNT_POINT + File.separator + "dir1"));
            // check regular resource file
            assertEquals(VFS_ROOT_PATH.resolve("SomeFile"), parsePath.apply(fs, VFS_MOUNT_POINT + File.separator + "SomeFile"));
            // check to be extracted file
            Path p = parsePath.apply(fs, VFS_MOUNT_POINT + File.separator + "extractme");
            // wasn't extracted => we do not expect the path to exist on real FS
            assertFalse(Files.exists(p));
            assertEquals(VFS_ROOT_PATH.resolve("extractme"), p);
            p = parsePath.apply(fs, VFS_MOUNT_POINT + File.separator + "dir1" + File.separator + "extractme");
            assertFalse(Files.exists(p));
            assertEquals(VFS_ROOT_PATH.resolve("dir1" + File.separator + "extractme"), p);
            p = parsePath.apply(fs, VFS_MOUNT_POINT + File.separator + "does-not-exist" + File.separator + "extractme");
            assertFalse(Files.exists(p));
            assertEquals(VFS_ROOT_PATH.resolve("does-not-exist" + File.separator + "extractme"), p);
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
    public void checkAccess() throws Exception {
        // from VFS
        for (FileSystem fs : new FileSystem[]{rwHostIOVFS, rHostIOVFS, noHostIOVFS}) {
            checkException(NullPointerException.class, () -> fs.checkAccess(null, null));
            checkException(NullPointerException.class, () -> fs.checkAccess(VFS_ROOT_PATH.resolve("dir1"), null));

            checkAccessVFS(fs, VFS_MOUNT_POINT);
            withCWD(fs, VFS_ROOT_PATH, (fst) -> checkAccessVFS(fs, ""));
        }

        // from real FS
        Path realFSPath = Files.createTempDirectory("graalpy.vfs.test").resolve("extractme");
        realFSPath.toFile().createNewFile();

        for (FileSystem fs : new FileSystem[]{rwHostIOVFS, rHostIOVFS}) {
            fs.checkAccess(realFSPath, Set.of(AccessMode.READ));
            withCWD(fs, realFSPath.getParent(), (fst) -> fst.checkAccess(realFSPath.getFileName(), Set.of(AccessMode.READ)));
            withCWD(fs, VFS_ROOT_PATH, (fst) -> fst.checkAccess(Path.of("..", realFSPath.toString()), Set.of(AccessMode.READ)));
        }
        checkException(SecurityException.class, () -> noHostIOVFS.checkAccess(realFSPath, Set.of(AccessMode.READ)), "expected error for no host io fs");
    }

    private static void checkAccessVFS(FileSystem fs, String pathPrefix) throws IOException {
        // check regular resource dir
        fs.checkAccess(Path.of(pathPrefix, "dir1"), Set.of(AccessMode.READ));
        // check regular resource file
        fs.checkAccess(Path.of(pathPrefix, "SomeFile"), Set.of(AccessMode.READ));

        // check to be extracted file
        fs.checkAccess(Path.of(pathPrefix, "extractme"), Set.of(AccessMode.READ), LinkOption.NOFOLLOW_LINKS);
        checkException(SecurityException.class, () -> fs.checkAccess(Path.of(pathPrefix, "extractme"), Set.of(AccessMode.WRITE), LinkOption.NOFOLLOW_LINKS));
        fs.checkAccess(Path.of(pathPrefix, "extractme"), Set.of(AccessMode.READ));
        // even though extracted -> FS is read-only and we are limiting the access to read-only also
        // for extracted files
        checkException(IOException.class, () -> fs.checkAccess(Path.of(pathPrefix, "extractme"), Set.of(AccessMode.WRITE)));

        checkException(NoSuchFileException.class, () -> fs.checkAccess(Path.of(pathPrefix, "does-not-exits", "extractme"), Set.of(AccessMode.READ), LinkOption.NOFOLLOW_LINKS));
        checkException(NoSuchFileException.class, () -> fs.checkAccess(Path.of(pathPrefix, "does-not-exits", "extractme"), Set.of(AccessMode.READ)));

        checkException(SecurityException.class, () -> fs.checkAccess(Path.of(pathPrefix, "SomeFile"), Set.of(AccessMode.WRITE)), "write access should not be possible with VFS");
        checkException(SecurityException.class, () -> fs.checkAccess(Path.of(pathPrefix, "does-not-exist"), Set.of(AccessMode.WRITE)), "execute access should not be possible with VFS");
        checkException(SecurityException.class, () -> fs.checkAccess(Path.of(pathPrefix, "SomeFile"), Set.of(AccessMode.EXECUTE)), "execute access should not be possible with VFS");
        checkException(SecurityException.class, () -> fs.checkAccess(Path.of(pathPrefix, "does-not-exist"), Set.of(AccessMode.EXECUTE)), "execute access should not be possible with VFS");

        checkException(NoSuchFileException.class, () -> fs.checkAccess(Path.of(pathPrefix, "does-not-exits"), Set.of(AccessMode.READ)),
                        "should not be able to access a file which does not exist in VFS");
        checkException(NoSuchFileException.class, () -> fs.checkAccess(Path.of(pathPrefix, "does-not-exits", "extractme"), Set.of(AccessMode.READ)),
                        "should not be able to access a file which does not exist in VFS");
    }

    @Test
    public void createDirectory() throws Exception {
        // from VFS
        for (FileSystem fs : new FileSystem[]{rwHostIOVFS, rHostIOVFS, noHostIOVFS}) {
            Path path = VFS_ROOT_PATH.resolve("new-dir");
            checkException(NullPointerException.class, () -> fs.createDirectory(null));
            checkException(SecurityException.class, () -> fs.createDirectory(path));

            withCWD(fs, VFS_ROOT_PATH, (fst) -> checkException(SecurityException.class, () -> fst.createDirectory(Path.of("new-dir")), "should not be able to create a directory in VFS"));
        }

        // from real FS
        Path newDir = Files.createTempDirectory("graalpy.vfs.test").resolve("newdir");
        assertFalse(Files.exists(newDir));
        checkException(SecurityException.class, () -> noHostIOVFS.createDirectory(newDir), "expected error for no host io fs");
        assertFalse(Files.exists(newDir));

        checkException(SecurityException.class, () -> rHostIOVFS.createDirectory(newDir), "should not be able to create a directory in a read-only FS");
        assertFalse(Files.exists(newDir));

        rwHostIOVFS.createDirectory(newDir);
        assertTrue(Files.exists(newDir));
        withCWD(rwHostIOVFS, newDir.getParent(), (fs) -> {
            Path newDir2 = newDir.getParent().resolve("newdir2");
            assertFalse(Files.exists(newDir2));
            fs.createDirectory(newDir2.getFileName());
            assertTrue(Files.exists(newDir2));
        });
        withCWD(rwHostIOVFS, VFS_ROOT_PATH, (fs) -> {
            Path newDir3 = newDir.getParent().resolve("newdir3");
            assertFalse(Files.exists(newDir3));
            fs.createDirectory(Path.of("..", newDir3.toString()));
            assertTrue(Files.exists(newDir3));
        });
    }

    @Test
    public void delete() throws Exception {
        // VFS
        for (FileSystem fs : new FileSystem[]{rwHostIOVFS, rHostIOVFS, noHostIOVFS}) {
            checkException(NullPointerException.class, () -> fs.delete(null));

            deleteVFS(fs, VFS_MOUNT_POINT);
            withCWD(fs, VFS_ROOT_PATH, (fst) -> deleteVFS(fs, ""));
        }

        // real FS
        Path realFSPath = Files.createTempDirectory("graalpy.vfs.test").resolve("extractme");
        Files.createFile(realFSPath);
        assertTrue(Files.exists(realFSPath));

        checkException(SecurityException.class, () -> noHostIOVFS.delete(realFSPath), "expected error for no host io fs");
        assertTrue(Files.exists(realFSPath));

        checkException(SecurityException.class, () -> rHostIOVFS.delete(realFSPath), "should not be able to delete in a read-only FS");
        assertTrue(Files.exists(realFSPath));

        // Files.createFile(realFSPath);
        assertTrue(Files.exists(realFSPath));
        rwHostIOVFS.delete(realFSPath);
        assertFalse(Files.exists(realFSPath));

        Files.createFile(realFSPath);
        assertTrue(Files.exists(realFSPath));
        withCWD(rwHostIOVFS, realFSPath.getParent(), (fs) -> rwHostIOVFS.delete(realFSPath.getFileName()));
        assertFalse(Files.exists(realFSPath));

        Files.createFile(realFSPath);
        assertTrue(Files.exists(realFSPath));
        withCWD(rwHostIOVFS, VFS_ROOT_PATH, (fs) -> rwHostIOVFS.delete(Path.of("..", realFSPath.toString())));
        assertFalse(Files.exists(realFSPath));
    }

    private static void deleteVFS(FileSystem fs, String pathPrefix) {
        checkException(SecurityException.class, () -> fs.delete(Path.of(pathPrefix, "file1")), "should not be able to delete in VFS");
        checkException(SecurityException.class, () -> fs.delete(Path.of(pathPrefix, "dir1")), "should not be able to delete in VFS");
        checkException(SecurityException.class, () -> fs.delete(Path.of(pathPrefix, "extractme")), "should not be able to delete in VFS");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void newByteChannel() throws Exception {
        // from VFS
        for (FileSystem fs : new FileSystem[]{rwHostIOVFS, rHostIOVFS, noHostIOVFS}) {

            checkException(NullPointerException.class, () -> fs.newByteChannel(null, (Set<? extends OpenOption>) null, (FileAttribute<?>) null));
            checkException(NullPointerException.class, () -> fs.newByteChannel(null, null));
            checkException(NullPointerException.class, () -> fs.newByteChannel(VFS_ROOT_PATH.resolve("file1"), null));
            checkException(NullPointerException.class, () -> fs.newByteChannel(VFS_ROOT_PATH.resolve("file1"), Set.of(StandardOpenOption.READ), (FileAttribute<?>[]) null));

            newByteChannelVFS(fs, VFS_MOUNT_POINT);
            withCWD(fs, VFS_ROOT_PATH, (fst) -> newByteChannelVFS(fst, ""));

            checkException(NullPointerException.class, () -> fs.newByteChannel(Path.of(VFS_MOUNT_POINT, "does-not-exist"), null));
            withCWD(fs, VFS_ROOT_PATH, (fst) -> checkException(NullPointerException.class, () -> fst.newByteChannel(Path.of("does-not-exist"), null)));
            checkException(NullPointerException.class, () -> fs.newByteChannel(Path.of(VFS_MOUNT_POINT, "does-not-exist", "extractme"), null));
            withCWD(fs, VFS_ROOT_PATH, (fst) -> checkException(NullPointerException.class, () -> fst.newByteChannel(Path.of("does-not-exist", "extractme"), null)));
        }

        // from real FS
        Path realFSPath = Files.createTempDirectory("graalpy.vfs.test").resolve("extractme");
        Files.createFile(realFSPath);
        checkException(SecurityException.class, () -> rHostIOVFS.newByteChannel(realFSPath, Set.of(StandardOpenOption.WRITE)), "cant write into a read-only host FS");
        rwHostIOVFS.newByteChannel(realFSPath, Set.of(StandardOpenOption.WRITE)).write(ByteBuffer.wrap("text".getBytes()));
        assertTrue(Files.exists(realFSPath));

        for (FileSystem fs : new FileSystem[]{rwHostIOVFS, rHostIOVFS}) {
            newByteChannelRealFS(fs, realFSPath, "text");
            withCWD(fs, realFSPath.getParent(), (fst) -> newByteChannelRealFS(fs, realFSPath.getFileName(), "text"));
            withCWD(fs, VFS_ROOT_PATH, (fst) -> newByteChannelRealFS(fs, Path.of("..", realFSPath.toString()), "text"));
        }
    }

    private static void newByteChannelVFS(FileSystem fs, String pathPrefix) throws IOException {
        Path file1 = Path.of(pathPrefix, "file1");
        Path extractable = Path.of(pathPrefix, "extractme");
        for (StandardOpenOption o : new StandardOpenOption[]{StandardOpenOption.WRITE, StandardOpenOption.READ}) {
            if (o == StandardOpenOption.READ) {
                newByteChannelVFS(fs, file1, Set.of(o));
                newByteChannelVFS(fs, file1, Set.of(o, LinkOption.NOFOLLOW_LINKS));
                newByteChannelVFS(fs, extractable, Set.of(o));
                checkException(IOException.class, () -> fs.newByteChannel(extractable, Set.of(o, LinkOption.NOFOLLOW_LINKS)));
            } else {
                checkCanOnlyRead(fs, file1, o);
                checkCanOnlyRead(fs, extractable, o);
            }
        }
        checkCanOnlyRead(fs, file1, StandardOpenOption.READ, StandardOpenOption.WRITE);
        checkCanOnlyRead(fs, extractable, StandardOpenOption.READ, StandardOpenOption.WRITE);
    }

    private static void newByteChannelVFS(FileSystem fs, Path path, Set<OpenOption> options) throws IOException {
        SeekableByteChannel bch = fs.newByteChannel(path, options);
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        bch.read(buffer);
        String s = new String(buffer.array());
        String[] ss = s.split(System.lineSeparator());
        assertTrue(ss.length >= 2);
        assertEquals("text1", ss[0]);
        assertEquals("text2", ss[1]);
        checkException(NonWritableChannelException.class, () -> bch.write(buffer), "should not be able to write to VFS");
        checkException(NonWritableChannelException.class, () -> bch.truncate(0), "should not be able to write to VFS");
    }

    private static void newByteChannelRealFS(FileSystem fs, Path path, String expectedText) throws IOException {
        SeekableByteChannel bch = fs.newByteChannel(path, Set.of(StandardOpenOption.READ));
        ByteBuffer buffer = ByteBuffer.allocate(expectedText.length());
        bch.read(buffer);
        String s = new String(buffer.array());
        String[] ss = s.split(System.lineSeparator());
        assertTrue(ss.length >= 1);
        assertEquals(expectedText, ss[0]);
    }

    private static void checkCanOnlyRead(FileSystem fs, Path path, StandardOpenOption... options) {
        checkException(SecurityException.class, () -> fs.newByteChannel(path, Set.of(options)), "should only be able to read from VFS");
    }

    @Test
    public void newDirectoryStream() throws Exception {
        // from VFS
        for (FileSystem fs : new FileSystem[]{rwHostIOVFS, rHostIOVFS, noHostIOVFS}) {
            checkException(NullPointerException.class, () -> fs.newDirectoryStream(null, null));
            checkException(NullPointerException.class, () -> fs.newDirectoryStream(VFS_ROOT_PATH.resolve("dir1"), null));
            checkException(NotDirectoryException.class, () -> fs.newDirectoryStream(VFS_ROOT_PATH.resolve("file1"), (p) -> true));

            newDirectoryStreamVFS(fs, VFS_MOUNT_POINT);
            withCWD(fs, VFS_ROOT_PATH, (fst) -> newDirectoryStreamVFS(fst, ""));
        }

        // from real FS
        Path realFSDir = Files.createTempDirectory("graalpy.vfs.test");
        Path realFSFile = realFSDir.resolve("extractme");
        Files.createFile(realFSFile);
        for (FileSystem fs : new FileSystem[]{rHostIOVFS, rwHostIOVFS}) {
            checkException(NotDirectoryException.class, () -> fs.newDirectoryStream(realFSFile, (p) -> true));
            newDirectoryStreamRealFS(fs, realFSDir, realFSFile);
            withCWD(fs, realFSDir.getParent(), (fst) -> newDirectoryStreamRealFS(fs, realFSDir.getFileName(), realFSFile));
            withCWD(fs, VFS_ROOT_PATH, (fst) -> newDirectoryStreamRealFS(fs, Path.of("..", realFSDir.toString()), realFSFile));
            // from real fs to VFS
            withCWD(fs, realFSDir, (fst) -> newDirectoryStreamVFS(fs, Path.of(dotdot(realFSDir.getNameCount()), VFS_MOUNT_POINT).toString()));
            // from VFS to real FS
            withCWD(fs, VFS_ROOT_PATH, (fst) -> newDirectoryStreamRealFS(fs, Path.of("..", realFSDir.toString()), realFSFile));
        }
        checkException(SecurityException.class, () -> noHostIOVFS.newDirectoryStream(realFSDir, null), "expected error for no host io fs");
    }

    private static void newDirectoryStreamVFS(FileSystem fs, String pathPrefix) throws Exception {
        DirectoryStream<Path> ds = fs.newDirectoryStream(Path.of(pathPrefix, "dir1"), (p) -> true);
        Set<String> s = new HashSet<>();
        Iterator<Path> it = ds.iterator();
        while (it.hasNext()) {
            Path p = it.next();
            s.add(p.toString());
        }
        assertEquals(2, s.size());
        String prefix = pathPrefix.isEmpty() ? "" : pathPrefix + File.separator;
        assertTrue(s.contains(prefix + "dir1" + File.separator + "extractme"));
        assertTrue(s.contains(prefix + "dir1" + File.separator + "file2"));

        ds = fs.newDirectoryStream(Path.of(pathPrefix, "dir1"), (p) -> false);
        assertFalse(ds.iterator().hasNext());

        checkException(NotDirectoryException.class, () -> fs.newDirectoryStream(Path.of(pathPrefix, "file1"), (p) -> true), "");
        checkException(NoSuchFileException.class, () -> fs.newDirectoryStream(Path.of(pathPrefix, "does-not-exist"), (p) -> true), "");
    }

    private static void newDirectoryStreamRealFS(FileSystem fs, Path dir, Path file) throws Exception {
        DirectoryStream<Path> ds = fs.newDirectoryStream(dir, (p) -> true);
        Iterator<Path> it = ds.iterator();
        Path pp = it.next();
        assertEquals(dir.resolve(file.getFileName()), pp);
        assertFalse(it.hasNext());
        ds = fs.newDirectoryStream(dir, (p) -> false);
        it = ds.iterator();
        assertFalse(it.hasNext());
    }

    @Test
    public void readAttributes() throws Exception {
        // from VFS
        for (FileSystem fs : new FileSystem[]{rwHostIOVFS, rHostIOVFS, noHostIOVFS}) {
            checkException(NullPointerException.class, () -> fs.readAttributes(null, "creationTime"));
            readAttributesVFS(fs, VFS_MOUNT_POINT);
            withCWD(fs, VFS_ROOT_PATH, (fst) -> readAttributesVFS(fst, ""));
        }

        // from real FS
        Path realFSPath = Files.createTempDirectory("graalpy.vfs.test").resolve("extractme");
        Files.createFile(realFSPath);
        for (FileSystem fs : new FileSystem[]{rHostIOVFS, rwHostIOVFS}) {
            assertTrue(((FileTime) fs.readAttributes(realFSPath, "creationTime").get("creationTime")).toMillis() > 0);
            withCWD(fs, realFSPath.getParent(), (fst) -> assertTrue(((FileTime) fs.readAttributes(realFSPath.getFileName(), "creationTime").get("creationTime")).toMillis() > 0));
            withCWD(fs, VFS_ROOT_PATH, (fst) -> assertTrue(((FileTime) fs.readAttributes(Path.of("..", realFSPath.toString()), "creationTime").get("creationTime")).toMillis() > 0));

        }
        checkException(SecurityException.class, () -> noHostIOVFS.readAttributes(realFSPath, "creationTime"), "expected error for no host io fs");
    }

    private static void readAttributesVFS(FileSystem fs, String pathPrefix) throws IOException {
        Map<String, Object> attrs = fs.readAttributes(Path.of(pathPrefix, "dir1"), "creationTime");
        assertEquals(FileTime.fromMillis(0), attrs.get("creationTime"));

        attrs = fs.readAttributes(Path.of(pathPrefix, "extractme"), "creationTime,isSymbolicLink,isRegularFile", LinkOption.NOFOLLOW_LINKS);
        assertEquals(FileTime.fromMillis(0), attrs.get("creationTime"));
        assertTrue((Boolean) attrs.get("isSymbolicLink"));
        assertFalse((Boolean) attrs.get("isRegularFile")); //

        attrs = fs.readAttributes(Path.of(pathPrefix, "extractme"), "creationTime,isSymbolicLink,isRegularFile");
        assertNotEquals(FileTime.fromMillis(0), attrs.get("creationTime"));
        assertFalse((Boolean) attrs.get("isSymbolicLink"));
        assertTrue((Boolean) attrs.get("isRegularFile"));

        checkException(NoSuchFileException.class, () -> fs.readAttributes(Path.of(pathPrefix, "does-not-exist", "extractme"), "creationTime", LinkOption.NOFOLLOW_LINKS));
        checkException(NoSuchFileException.class, () -> fs.readAttributes(Path.of(pathPrefix, "does-not-exist", "extractme"), "creationTime"));

        checkException(NoSuchFileException.class, () -> fs.readAttributes(Path.of(pathPrefix, "does-not-exist"), "creationTime"), "");
        checkException(UnsupportedOperationException.class, () -> fs.readAttributes(Path.of(pathPrefix, "file1"), "unix:creationTime"), "");
    }

    @Test
    public void libsExtract() throws Exception {
        try (VirtualFileSystem vfs = VirtualFileSystem.newBuilder().//
                        unixMountPoint(VFS_MOUNT_POINT).//
                        windowsMountPoint(VFS_WIN_MOUNT_POINT).//
                        extractFilter(p -> p.getFileName().toString().endsWith(".tso")).//
                        resourceLoadingClass(VirtualFileSystemTest.class).build()) {
            FileSystem fs = getDelegatingFS(vfs);
            Path p = fs.toRealPath(VFS_ROOT_PATH.resolve("site-packages/testpkg/file.tso"));
            checkExtractedFile(p, null);
            Path extractedRoot = p.getParent().getParent().getParent();

            checkExtractedFile(extractedRoot.resolve("site-packages/testpkg.libs/file1.tso"), null);
            checkExtractedFile(extractedRoot.resolve("site-packages/testpkg.libs/file2.tso"), null);
            checkExtractedFile(extractedRoot.resolve("site-packages/testpkg.libs/dir/file1.tso"), null);
            checkExtractedFile(extractedRoot.resolve("site-packages/testpkg.libs/dir/file2.tso"), null);
            checkExtractedFile(extractedRoot.resolve("site-packages/testpkg.libs/dir/nofilterfile"), null);
            checkExtractedFile(extractedRoot.resolve("site-packages/testpkg.libs/dir/dir/file1.tso"), null);
            checkExtractedFile(extractedRoot.resolve("site-packages/testpkg.libs/dir/dir/file2.tso"), null);

            p = fs.toRealPath(VFS_ROOT_PATH.resolve("site-packages/testpkg-nolibs/file.tso"));
            checkExtractedFile(p, null);
        }
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

    @Test
    public void noExtractFilter() throws Exception {
        try (VirtualFileSystem vfs = VirtualFileSystem.newBuilder().//
                        unixMountPoint(VFS_MOUNT_POINT).//
                        windowsMountPoint(VFS_WIN_MOUNT_POINT).//
                        extractFilter(null).//
                        resourceLoadingClass(VirtualFileSystemTest.class).build()) {
            FileSystem fs = getDelegatingFS(vfs);
            assertEquals(23, checkNotExtracted(fs, VFS_ROOT_PATH));
        }
    }

    /**
     * Check that all listed files have paths from VFS and do not get extracted if touched by
     * toRealPath.
     *
     * @return amount of all listed files
     */
    private static int checkNotExtracted(FileSystem fs, Path dir) throws IOException {
        DirectoryStream<Path> ds = fs.newDirectoryStream(dir, (p) -> true);
        Iterator<Path> it = ds.iterator();
        int c = 0;
        while (it.hasNext()) {
            c++;
            Path p = it.next();
            assertTrue(p.toString().startsWith(VFS_MOUNT_POINT));
            assertTrue(fs.toRealPath(p).startsWith(VFS_MOUNT_POINT));
            fs.readAttributes(p, "isDirectory");
            if (Boolean.TRUE.equals((fs.readAttributes(p, "isDirectory").get("isDirectory")))) {
                c += checkNotExtracted(fs, p);
            }
        }
        return c;
    }

    private interface ExceptionCall {
        void call() throws Exception;
    }

    private static void checkException(Class<?> exType, ExceptionCall c) {
        checkException(exType, c, null);
    }

    private static void checkException(Class<?> exType, ExceptionCall c, String msg) {
        boolean gotEx = false;
        try {
            c.call();
        } catch (Exception e) {
            if (!exType.isAssignableFrom(e.getClass())) {
                e.printStackTrace();
                assertEquals(exType, e.getClass());
            }
            gotEx = true;
        }
        assertTrue(msg != null ? msg : "expected " + exType.getName(), gotEx);
    }

    @Test
    public void currentWorkingDirectory() throws Exception {
        Path realFSDir = Files.createTempDirectory("graalpy.vfs.test");
        Path realFSFile = realFSDir.resolve("extractme");
        Files.createFile(realFSFile);

        for (FileSystem fs : new FileSystem[]{rwHostIOVFS, rHostIOVFS, noHostIOVFS}) {
            checkException(NullPointerException.class, () -> fs.setCurrentWorkingDirectory(null), "expected NPE");
            checkException(IllegalArgumentException.class, () -> fs.setCurrentWorkingDirectory(Path.of("dir")));

            checkException(IllegalArgumentException.class, () -> fs.setCurrentWorkingDirectory(VFS_ROOT_PATH.resolve("file1")));

            Object oldCwd = getCwd(fs);
            try {
                Path nonExistingDir = VFS_ROOT_PATH.resolve("does-not-exist");
                fs.setCurrentWorkingDirectory(nonExistingDir);
                assertEquals(VFS_ROOT_PATH.resolve("does-not-exist").resolve("dir"), fs.toAbsolutePath(Path.of("dir")));

                Path vfsDir = VFS_ROOT_PATH.resolve("dir1");
                fs.setCurrentWorkingDirectory(vfsDir);
                assertEquals(vfsDir, fs.toAbsolutePath(Path.of("dir")).getParent());

                if (fs == noHostIOVFS) {
                    checkException(SecurityException.class, () -> fs.setCurrentWorkingDirectory(realFSFile));
                } else {
                    checkException(IllegalArgumentException.class, () -> fs.setCurrentWorkingDirectory(realFSFile));

                    nonExistingDir = realFSDir.resolve("does-not-exist");
                    fs.setCurrentWorkingDirectory(nonExistingDir);
                    assertEquals(nonExistingDir, fs.toAbsolutePath(Path.of("dir")).getParent());

                    fs.setCurrentWorkingDirectory(realFSDir);
                    assertEquals(realFSDir, fs.toAbsolutePath(Path.of("dir")).getParent());
                }
            } finally {
                resetCWD(fs, oldCwd);
            }
        }
    }

    @Test
    public void getTempDirectory() {
        assertTrue(Files.exists(rwHostIOVFS.getTempDirectory()));
        checkException(SecurityException.class, () -> rHostIOVFS.getTempDirectory());
        checkException(SecurityException.class, () -> noHostIOVFS.getTempDirectory());
    }

    @Test
    public void getMimeType() throws Exception {
        Path realFSPath = Files.createTempDirectory("graalpy.vfs.test").resolve("extractme");
        Files.createFile(realFSPath);
        for (FileSystem fs : new FileSystem[]{rwHostIOVFS, rHostIOVFS, noHostIOVFS}) {
            checkException(NullPointerException.class, () -> fs.getMimeType(null));
            Assert.assertNull(fs.getMimeType(VFS_ROOT_PATH));
            fs.getMimeType(realFSPath);
            // whatever the return value, just check it does not fail
            withCWD(fs, VFS_ROOT_PATH, (fst) -> fst.getMimeType(Path.of("..", realFSPath.toString())));
        }
    }

    @Test
    public void getEncoding() throws Exception {
        Path realFSPath = Files.createTempDirectory("graalpy.vfs.test").resolve("extractme");
        Files.createFile(realFSPath);
        for (FileSystem fs : new FileSystem[]{rwHostIOVFS, rHostIOVFS, noHostIOVFS}) {
            checkException(NullPointerException.class, () -> fs.getEncoding(null));
            Assert.assertNull(fs.getEncoding(VFS_ROOT_PATH));
            fs.getEncoding(realFSPath);
            // whatever the return value, just check it does not fail
            withCWD(fs, VFS_ROOT_PATH, (fst) -> fst.getEncoding(Path.of("..", realFSPath.toString())));
        }
    }

    @Test
    public void setAttribute() throws Exception {
        for (FileSystem fs : new FileSystem[]{rwHostIOVFS, rHostIOVFS, noHostIOVFS}) {
            checkException(NullPointerException.class, () -> fs.setAttribute(null, null, null));
            checkException(SecurityException.class, () -> fs.setAttribute(VFS_ROOT_PATH, null, null));
        }
        Path realFSPath = Files.createTempDirectory("graalpy.vfs.test").resolve("extractme");
        Files.createFile(realFSPath);
        checkException(SecurityException.class, () -> rHostIOVFS.setAttribute(realFSPath, "creationTime", FileTime.fromMillis(42)));
        checkException(SecurityException.class, () -> noHostIOVFS.setAttribute(realFSPath, "creationTime", FileTime.fromMillis(42)));

        // just check it does not fail for real FS paths
        rwHostIOVFS.setAttribute(realFSPath, "creationTime", FileTime.fromMillis(42));
        withCWD(rwHostIOVFS, VFS_ROOT_PATH, (fst) -> fst.setAttribute(Path.of("..", realFSPath.toString()), "creationTime", FileTime.fromMillis(43)));
    }

    @Test
    public void isSameFile() throws Exception {
        for (FileSystem fs : new FileSystem[]{rwHostIOVFS, rHostIOVFS, noHostIOVFS}) {
            assertFalse(fs.isSameFile(Path.of(VFS_MOUNT_POINT, "src"), Path.of(VFS_MOUNT_POINT, "file1")));
            withCWD(fs, VFS_ROOT_PATH, (fst) -> assertTrue(fst.isSameFile(Path.of("src"), Path.of("src", "..", "src"))));
        }
        Path realFSDir = Files.createTempDirectory("graalpy.vfs.test");
        Path realFSFile1 = realFSDir.resolve("file1");
        Files.createFile(realFSFile1);
        Path realFSFile2 = realFSDir.resolve("file2");
        Files.createFile(realFSFile2);
        for (FileSystem fs : new FileSystem[]{rwHostIOVFS, rHostIOVFS, noHostIOVFS}) {
            assertFalse(fs.isSameFile(realFSDir, VFS_ROOT_PATH));
            assertFalse(fs.isSameFile(VFS_ROOT_PATH, realFSDir));
            if (fs == noHostIOVFS) {
                withCWD(fs, VFS_ROOT_PATH, (fst) -> checkException(SecurityException.class, () -> fst.isSameFile(realFSDir, Path.of("..", realFSDir.toString()))));
            } else {
                withCWD(fs, VFS_ROOT_PATH, (fst) -> assertTrue(fst.isSameFile(realFSDir, Path.of("..", realFSDir.toString()))));
                withCWD(fs, realFSDir, (fst) -> assertTrue(fs.isSameFile(realFSFile1.getFileName(), realFSFile1.getFileName())));
                withCWD(fs, realFSDir, (fst) -> assertFalse(fs.isSameFile(realFSFile1.getFileName(), realFSFile2.getFileName())));
            }
        }
    }

    @Test
    public void createLink() throws Exception {
        Path realFSDir = Files.createTempDirectory("graalpy.vfs.test");
        Path realFSFile = realFSDir.resolve("extractme");
        Files.createFile(realFSFile);
        for (FileSystem fs : new FileSystem[]{rwHostIOVFS, rHostIOVFS, noHostIOVFS}) {
            checkException(NullPointerException.class, () -> fs.createLink(null, null));
            checkException(NullPointerException.class, () -> fs.createLink(VFS_ROOT_PATH, null));

            // IOException: Cross file system linking is not supported.
            checkException(IOException.class, () -> fs.createLink(VFS_ROOT_PATH.resolve("link1"), realFSFile));
            checkException(IOException.class, () -> fs.createLink(realFSDir.resolve("link"), VFS_ROOT_PATH.resolve("file1")));

            checkException(SecurityException.class, () -> fs.createLink(VFS_ROOT_PATH, VFS_ROOT_PATH.resolve("link")));
        }
        Path link = realFSDir.resolve("link1");
        assertFalse(Files.exists(link));
        rwHostIOVFS.createLink(link, realFSFile);
        assertTrue(Files.exists(link));
        Path link2 = realFSDir.resolve("link2");
        assertFalse(Files.exists(link2));
        withCWD(rwHostIOVFS, VFS_ROOT_PATH, (fs) -> rwHostIOVFS.createLink(Path.of("..", link2.toString()), Path.of("..", realFSFile.toString())));
        assertTrue(Files.exists(link2));

        checkException(SecurityException.class, () -> rHostIOVFS.createLink(realFSDir.resolve("link3"), realFSFile));
        checkException(SecurityException.class, () -> noHostIOVFS.createLink(realFSDir.resolve("link4"), realFSFile));
    }

    @Test
    public void createAndReadSymbolicLink() throws Exception {
        Path realFSDir = Files.createTempDirectory("graalpy.vfs.test");
        Path realFSLinkTarget = realFSDir.resolve("linkTarget");
        Files.createFile(realFSLinkTarget);
        for (FileSystem fs : new FileSystem[]{rwHostIOVFS, rHostIOVFS, noHostIOVFS}) {
            checkException(NullPointerException.class, () -> fs.createSymbolicLink(null, null));
            checkException(NullPointerException.class, () -> fs.readSymbolicLink(null));

            // IOException: Cross file system linking is not supported.
            checkException(IOException.class, () -> fs.createSymbolicLink(realFSDir.resolve("symlink2"), VFS_ROOT_PATH));
            checkException(IOException.class, () -> fs.createSymbolicLink(VFS_ROOT_PATH.resolve("link1"), realFSLinkTarget));

            checkException(SecurityException.class, () -> fs.createSymbolicLink(VFS_ROOT_PATH, VFS_ROOT_PATH.resolve("link")));
        }
        checkException(SecurityException.class, () -> rHostIOVFS.createSymbolicLink(realFSDir.resolve("link2"), realFSLinkTarget));
        checkException(SecurityException.class, () -> noHostIOVFS.createSymbolicLink(realFSDir.resolve("link3"), realFSLinkTarget));

        Path symlink = realFSDir.resolve("symlink1");
        assertFalse(Files.exists(symlink));
        rwHostIOVFS.createSymbolicLink(symlink, realFSLinkTarget);
        checkSymlink(realFSDir, realFSLinkTarget, symlink);

        Files.delete(symlink);
        assertFalse(Files.exists(symlink));
        withCWD(rwHostIOVFS, VFS_ROOT_PATH, (fst) -> fst.createSymbolicLink(Path.of("..", symlink.toString()), realFSLinkTarget));
        checkSymlink(realFSDir, realFSLinkTarget, symlink);
    }

    private void checkSymlink(Path dir, Path target, Path symlink) throws Exception {
        assertTrue(Files.exists(symlink));
        checkException(SecurityException.class, () -> noHostIOVFS.readSymbolicLink(symlink));
        for (FileSystem fs : new FileSystem[]{rwHostIOVFS, rHostIOVFS}) {
            assertEquals(target, fs.readSymbolicLink(symlink));
            withCWD(fs, VFS_ROOT_PATH, (fst) -> assertEquals(target, fst.readSymbolicLink(Path.of("..", symlink.toString()))));
            withCWD(fs, dir, (fst) -> assertEquals(target, fst.readSymbolicLink(Path.of(dotdot(dir.getNameCount()), "..", VFS_MOUNT_POINT, "..", symlink.toString()))));
        }
    }

    @Test
    public void readSymbolicLink() throws Exception {
        for (FileSystem fs : new FileSystem[]{rwHostIOVFS, rHostIOVFS, noHostIOVFS}) {
            readSymbolicLink(fs, VFS_MOUNT_POINT);
            withCWD(fs, VFS_ROOT_PATH, (fst) -> readSymbolicLink(fst, ""));
        }
    }

    private static void readSymbolicLink(FileSystem fs, String vfsPrefix) throws IOException {
        checkException(NotLinkException.class, () -> fs.readSymbolicLink(Path.of(vfsPrefix, "file1")));
        checkException(NoSuchFileException.class, () -> fs.readSymbolicLink(Path.of(vfsPrefix, "does-not-exist")));
        checkExtractedFile(fs.readSymbolicLink(Path.of(vfsPrefix, "extractme")), new String[]{"text1", "text2"});
    }

    @Test
    public void move() throws Exception {
        Path realFSDir = Files.createTempDirectory("graalpy.vfs.test");
        Path realFSSource = realFSDir.resolve("movesource");
        Files.createFile(realFSSource);
        Path realFSTarget = realFSDir.resolve("movetarget");
        for (FileSystem fs : new FileSystem[]{rwHostIOVFS, rHostIOVFS, noHostIOVFS}) {
            checkException(NullPointerException.class, () -> fs.move(null, null));
            checkException(NullPointerException.class, () -> fs.move(VFS_ROOT_PATH, null));

            checkException(SecurityException.class, () -> fs.move(VFS_ROOT_PATH.resolve("file1"), realFSTarget));
            Files.deleteIfExists(realFSTarget); // cleanup, move is nonatomic

            if (fs == noHostIOVFS) {
                checkException(SecurityException.class, () -> fs.move(realFSSource, VFS_ROOT_PATH));
            } else {
                checkException(IOException.class, () -> fs.move(realFSSource, VFS_ROOT_PATH));
            }

            checkException(SecurityException.class, () -> fs.move(realFSSource, VFS_ROOT_PATH, StandardCopyOption.REPLACE_EXISTING));
            checkException(SecurityException.class, () -> fs.move(realFSSource, VFS_ROOT_PATH.resolve("movetarget")));

            checkException(SecurityException.class, () -> fs.move(VFS_ROOT_PATH.resolve("file1"), VFS_ROOT_PATH.resolve("file2")));
        }

        rwHostIOVFS.newByteChannel(realFSSource, Set.of(StandardOpenOption.WRITE)).write(ByteBuffer.wrap("moved text".getBytes()));
        assertTrue(Files.exists(realFSSource));
        assertFalse(Files.exists(realFSTarget));
        rwHostIOVFS.move(realFSSource, realFSTarget);
        assertFalse(Files.exists(realFSSource));
        assertTrue(Files.exists(realFSTarget));
        newByteChannelRealFS(rwHostIOVFS, realFSTarget, "moved text");

        Path realFSSource2 = realFSTarget;
        Path realFSTarget2 = realFSSource;
        assertTrue(Files.exists(realFSSource2));
        assertFalse(Files.exists(realFSTarget2));

        withCWD(rwHostIOVFS, VFS_ROOT_PATH, (fs) -> fs.move(Path.of("..", realFSSource2.toString()), Path.of("..", realFSTarget2.toString())));
        assertFalse(Files.exists(realFSSource2));
        assertTrue(Files.exists(realFSTarget2));
        newByteChannelRealFS(rwHostIOVFS, realFSSource, "moved text");

        checkException(IOException.class, () -> rHostIOVFS.move(realFSSource2, realFSTarget2));
        checkException(SecurityException.class, () -> noHostIOVFS.move(realFSSource2, realFSTarget2));
    }

    @Test
    public void copy() throws Exception {
        Path realFSDir = Files.createTempDirectory("graalpy.vfs.test");
        Path realFSSource = realFSDir.resolve("copysource");
        Path realFSTarget = realFSDir.resolve("target");
        Files.createFile(realFSSource);
        assertTrue(Files.exists(realFSSource));
        for (FileSystem fs : new FileSystem[]{rwHostIOVFS, rHostIOVFS, noHostIOVFS}) {
            checkException(NullPointerException.class, () -> fs.copy(null, null));
            checkException(NullPointerException.class, () -> fs.copy(VFS_ROOT_PATH, null));
            checkException(SecurityException.class, () -> fs.copy(VFS_ROOT_PATH.resolve("file1"), VFS_ROOT_PATH.resolve("file2")));
            checkException(SecurityException.class, () -> fs.copy(realFSSource, VFS_ROOT_PATH.resolve("file2")));
            checkException(SecurityException.class, () -> fs.copy(VFS_ROOT_PATH.resolve("file1"), VFS_ROOT_PATH.resolve("file2")));
        }

        Files.delete(realFSSource);
        checkException(NoSuchFileException.class, () -> rHostIOVFS.copy(realFSSource, realFSTarget));
        checkException(SecurityException.class, () -> noHostIOVFS.copy(realFSSource, realFSTarget));

        Files.createFile(realFSSource);
        rwHostIOVFS.newByteChannel(realFSSource, Set.of(StandardOpenOption.WRITE)).write(ByteBuffer.wrap("copied text".getBytes()));
        assertTrue(Files.exists(realFSSource));

        rwHostIOVFS.copy(realFSSource, realFSTarget);
        assertTrue(Files.exists(realFSSource));
        assertTrue(Files.exists(realFSTarget));
        newByteChannelRealFS(rwHostIOVFS, realFSTarget, "copied text");

        Files.delete(realFSTarget);
        assertFalse(Files.exists(realFSTarget));
        withCWD(rwHostIOVFS, VFS_ROOT_PATH, (fs) -> fs.copy(Path.of("..", realFSSource.toString()), Path.of("..", realFSTarget.toString())));
        assertTrue(Files.exists(realFSTarget));
        newByteChannelRealFS(rwHostIOVFS, realFSTarget, "copied text");

        Files.delete(realFSTarget);
        assertFalse(Files.exists(realFSTarget));
        rwHostIOVFS.copy(VFS_ROOT_PATH.resolve("file1"), realFSTarget);
        assertTrue(Files.exists(realFSTarget));
        newByteChannelRealFS(rwHostIOVFS, realFSTarget, "text1");

        Files.delete(realFSTarget);
        assertFalse(Files.exists(realFSTarget));
        withCWD(rwHostIOVFS, VFS_ROOT_PATH, (fs) -> fs.copy(Path.of("file1"), realFSTarget));
        assertTrue(Files.exists(realFSTarget));
        newByteChannelRealFS(rwHostIOVFS, realFSTarget, "text1");

        Files.delete(realFSTarget);
        assertFalse(Files.exists(realFSTarget));
        withCWD(rwHostIOVFS, realFSDir, (fs) -> fs.copy(Path.of(dotdot(realFSDir.getNameCount()), VFS_MOUNT_POINT, "file1"), realFSTarget));
        assertTrue(Files.exists(realFSTarget));
        newByteChannelRealFS(rwHostIOVFS, realFSTarget, "text1");

        Files.delete(realFSTarget);
        assertFalse(Files.exists(realFSTarget));
        // NoSuchFileException: no such file or directory: '/test_mount_point/does-no-exist'
        checkException(NoSuchFileException.class, () -> rwHostIOVFS.copy(VFS_ROOT_PATH.resolve("does-no-exist"), realFSTarget));
        assertFalse(Files.exists(realFSTarget));

        // read only

        // SecurityException: Operation is not allowed for: realFSPath
        checkException(SecurityException.class, () -> rHostIOVFS.copy(VFS_ROOT_PATH.resolve("file1"), realFSTarget));
        assertFalse(Files.exists(realFSTarget));

        // no host IO

        withCWD(rwHostIOVFS, VFS_ROOT_PATH, (fs) -> fs.copy(Path.of("file1"), Path.of("..", realFSTarget.toString())));
        assertTrue(Files.exists(realFSTarget));
        newByteChannelRealFS(rwHostIOVFS, realFSTarget, "text1");

    }

    @Test
    public void testImpl() throws Exception {
        Set<String> ignored = Set.of(
                        "isSameFile",
                        "getSeparator",
                        "getPathSeparator");
        Set<String> implementedMethods = new HashSet<>();
        Class<?> vfsClass;
        try (VirtualFileSystem vfs = VirtualFileSystem.create()) {
            Field fs = vfs.getClass().getDeclaredField("impl");
            fs.setAccessible(true);
            vfsClass = fs.get(vfs).getClass();

            for (Method m : vfsClass.getDeclaredMethods()) {
                if (Modifier.isPublic(m.getModifiers()) && !Modifier.isStatic(m.getModifiers())) {
                    implementedMethods.add(m.getName());
                }
            }
        }

        List<String> notImplemented = new ArrayList<>();
        for (Method m : FileSystem.class.getDeclaredMethods()) {
            if (Modifier.isPublic(m.getModifiers()) && !Modifier.isStatic(m.getModifiers())) {
                if (ignored.contains(m.getName())) {
                    continue;
                }
                if (!implementedMethods.contains(m.getName())) {
                    notImplemented.add(m.getName());
                }
            }
        }

        if (!notImplemented.isEmpty()) {
            fail(vfsClass.getName() + " is missing implemention for " + notImplemented);
        }
    }

    private static String fromPathToFSRoot(Path p) {
        String ret = Path.of(p.toString(), dotdot(p.getNameCount())).toString();
        assert ret.contains("..");
        return ret;
    }

    private static String dotdot(int n) {
        String ret = Path.of(".", Stream.generate(() -> "..").limit(n).toArray(String[]::new)).toString();
        if (ret.startsWith(".")) {
            ret = ret.substring(2, ret.length());
        }
        return ret;
    }

    private interface FSCall {
        void call(FileSystem fs) throws Exception;
    }

    private static void withCWD(FileSystem fs, Path cwd, FSCall c) throws Exception {
        Object prevCwd = getCwd(fs);
        fs.setCurrentWorkingDirectory(cwd);
        try {
            c.call(fs);
        } finally {
            resetCWD(fs, prevCwd);
        }
    }

    private static Object getCwd(FileSystem fs) throws IllegalAccessException, NoSuchFieldException {
        // need to know if CompositeFileSystem.currentWorkingDirectory is set to null,
        // because initial CWD is null and in such case CompositeFileSystem:
        // - falls back on jdk CWD
        // - and also behaves differently as when CWD is set to a non 'null' value
        Field f = fs.getClass().getDeclaredField("currentWorkingDirectory");
        f.setAccessible(true);
        return f.get(fs);
    }

    private static void resetCWD(FileSystem fs, Object oldCwd) throws NoSuchFieldException, IllegalAccessException {
        // calling fs.setCurrentWorkingDirectory(null) is not possible, so use reflection instead
        // see also getCwd()
        Field f = fs.getClass().getDeclaredField("currentWorkingDirectory");
        f.setAccessible(true);
        f.set(fs, oldCwd);
    }

}
