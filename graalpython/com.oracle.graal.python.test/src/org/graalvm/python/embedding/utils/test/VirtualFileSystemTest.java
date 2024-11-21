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

import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.python.embedding.utils.VirtualFileSystem;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
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

import static com.oracle.graal.python.test.integration.Utils.IS_WINDOWS;
import static org.graalvm.python.embedding.utils.VirtualFileSystem.HostIO.NONE;
import static org.graalvm.python.embedding.utils.VirtualFileSystem.HostIO.READ;
import static org.graalvm.python.embedding.utils.VirtualFileSystem.HostIO.READ_WRITE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class VirtualFileSystemTest {

    static final String VFS_UNIX_MOUNT_POINT = "/test_mount_point";
    static final String VFS_WIN_MOUNT_POINT = "X:\\test_win_mount_point";
    static final String VFS_MOUNT_POINT = IS_WINDOWS ? VFS_WIN_MOUNT_POINT : VFS_UNIX_MOUNT_POINT;

    static final String VFS_ROOT = VFS_MOUNT_POINT + File.separator;
    static final String VFS_SRC = VFS_ROOT + "src" + File.separator;
    private static final Path VFS_ROOT_PATH = Path.of(VFS_ROOT);

    private final FileSystem rwHostIOVFS;
    private final FileSystem rHostIOVFS;
    private final FileSystem noHostIOVFS;

    private static FileSystem getVFSImpl(VirtualFileSystem vfs) throws NoSuchFieldException, IllegalAccessException {
        Field impl = vfs.getClass().getDeclaredField("impl");
        impl.setAccessible(true);
        return (FileSystem) impl.get(vfs);
    }

    public VirtualFileSystemTest() throws NoSuchFieldException, IllegalAccessException {
        Logger logger = Logger.getLogger(VirtualFileSystem.class.getName());
        for (Handler handler : logger.getHandlers()) {
            handler.setLevel(Level.FINE);
        }
        logger.setLevel(Level.FINE);

        rwHostIOVFS = getVFSImpl(VirtualFileSystem.newBuilder().//
                        allowHostIO(READ_WRITE).//
                        unixMountPoint(VFS_UNIX_MOUNT_POINT).//
                        windowsMountPoint(VFS_WIN_MOUNT_POINT).//
                        extractFilter(p -> p.getFileName().toString().equals("extractme")).//
                        resourceLoadingClass(VirtualFileSystemTest.class).build());
        rHostIOVFS = getVFSImpl(VirtualFileSystem.newBuilder().//
                        allowHostIO(READ).//
                        unixMountPoint(VFS_UNIX_MOUNT_POINT).//
                        windowsMountPoint(VFS_WIN_MOUNT_POINT).//
                        extractFilter(p -> p.getFileName().toString().equals("extractme")).//
                        resourceLoadingClass(VirtualFileSystemTest.class).build());
        noHostIOVFS = getVFSImpl(VirtualFileSystem.newBuilder().//
                        allowHostIO(NONE).//
                        unixMountPoint(VFS_UNIX_MOUNT_POINT).//
                        windowsMountPoint(VFS_WIN_MOUNT_POINT).//
                        extractFilter(p -> p.getFileName().toString().equals("extractme")).//
                        resourceLoadingClass(VirtualFileSystemTest.class).build());

    }

    @Test
    public void toRealPath() throws Exception {
        // from VFS
        for (FileSystem fs : new FileSystem[]{rwHostIOVFS, rHostIOVFS, noHostIOVFS}) {
            checkException(NullPointerException.class, () -> fs.toRealPath(null));
            toRealPathVFS(fs, VFS_ROOT);
            withCWD(fs, VFS_ROOT_PATH, (fst) -> toRealPathVFS(fst, ""));
        }

        // from real FS
        final Path realFSPath = Files.createTempDirectory("graalpy.vfs.test").resolve("extractme");
        realFSPath.toFile().createNewFile();
        for (FileSystem fs : new FileSystem[]{rwHostIOVFS, rHostIOVFS}) {
            assertTrue(Files.isSameFile(realFSPath, fs.toRealPath(Path.of(realFSPath.getParent().toString() + "/../" + realFSPath.getParent().getFileName().toString() + "/extractme"))));
            withCWD(fs, realFSPath.getParent(), (fst) -> assertTrue(Files.isSameFile(realFSPath, fst.toRealPath(Path.of("../" + realFSPath.getParent().getFileName().toString() + "/extractme")))));

            withCWD(fs, VFS_ROOT_PATH, (fst) -> assertTrue(Files.isSameFile(realFSPath, fst.toRealPath(Path.of("../" + realFSPath.toString())))));
        }
        checkException(SecurityException.class, () -> noHostIOVFS.toRealPath(realFSPath), "expected error for no host io fs");
    }

    private void toRealPathVFS(FileSystem fs, String pathPrefix) throws IOException {
        // check regular resource dir
        assertEquals(Path.of(VFS_ROOT + "dir1"), fs.toRealPath(Path.of(pathPrefix + "dir1/../dir1")));
        // check regular resource file
        assertEquals(Path.of(VFS_ROOT + "SomeFile"), fs.toRealPath(Path.of(pathPrefix + "../" + VFS_MOUNT_POINT + File.separator + "SomeFile")));
        // check to be extracted file
        checkExtractedFile(fs.toRealPath(Path.of(pathPrefix + "../" + VFS_MOUNT_POINT + File.separator + "extractme")), new String[]{"text1", "text2"});
        assertEquals(Path.of(VFS_ROOT + "does-not-exist/extractme"), fs.toRealPath(Path.of(pathPrefix + "does-not-exist/../does-not-exist/extractme")));
    }

    @Test
    public void toAbsolutePath() throws Exception {
        // from VFS
        for (FileSystem fs : new FileSystem[]{rwHostIOVFS, rHostIOVFS, noHostIOVFS}) {
            checkException(NullPointerException.class, () -> fs.toAbsolutePath(null));

            assertEquals(Path.of(VFS_ROOT + "dir1"), fs.toAbsolutePath(Path.of(VFS_ROOT + "dir1")));
            assertEquals(Path.of(VFS_ROOT + "SomeFile"), fs.toAbsolutePath(Path.of(VFS_ROOT + "SomeFile")));

            if (fs == noHostIOVFS) {
                // cwd is by default set to VFS_ROOT/src
                assertEquals(Path.of(VFS_SRC + "dir1"), fs.toAbsolutePath(Path.of("dir1")));
                assertEquals(Path.of(VFS_SRC + "SomeFile").toAbsolutePath(), fs.toAbsolutePath(Path.of("SomeFile")));
                // file does not exist, so not extracted
                assertEquals(Path.of(VFS_SRC + "does-not-exist/extractme"), fs.toAbsolutePath(Path.of("does-not-exist/extractme")));
            } else {
                // without cwd set, the real FS absolute path is returned
                assertEquals(Path.of("dir1").toAbsolutePath(), fs.toAbsolutePath(Path.of("dir1")));
                assertEquals(Path.of("SomeFile").toAbsolutePath(), fs.toAbsolutePath(Path.of("SomeFile")));
                assertEquals(Path.of("does-not-exist/extractme").toAbsolutePath(), fs.toAbsolutePath(Path.of("does-not-exist/extractme")));
            }

            withCWD(fs, VFS_ROOT_PATH, (fst) -> {
                // cwd set, file is recognised as from VFS and extracted
                checkExtractedFile(fst.toAbsolutePath(Path.of("extractme")), new String[]{"text1", "text2"});
                // file does not exist, so not extracted
                assertEquals(Path.of(VFS_ROOT + "does-not-exist/extractme"), fst.toAbsolutePath(Path.of("does-not-exist/extractme")));
            });
        }

        // from real FS
        Path realFSPath = Files.createTempDirectory("graalpy.vfs.test").resolve("extractme");
        realFSPath.toFile().createNewFile();
        for (FileSystem fs : new FileSystem[]{rwHostIOVFS, rHostIOVFS}) {
            assertEquals(Path.of("extractme").toAbsolutePath(), fs.toAbsolutePath(Path.of("extractme")));
            withCWD(fs, realFSPath.getParent(), (fst) -> assertEquals(realFSPath, fst.toAbsolutePath(realFSPath.getFileName())));
        }
        checkException(SecurityException.class, () -> noHostIOVFS.toAbsolutePath(realFSPath));
        assertEquals(Path.of(VFS_SRC + "extractme"), noHostIOVFS.toAbsolutePath(realFSPath.getFileName()));
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
            assertEquals(VFS_ROOT_PATH.resolve("dir1"), parsePath.apply(fs, VFS_ROOT + "dir1"));
            // check regular resource file
            assertEquals(VFS_ROOT_PATH.resolve("SomeFile"), parsePath.apply(fs, VFS_ROOT + "SomeFile"));
            // check to be extracted file
            Path p = parsePath.apply(fs, VFS_ROOT + "extractme");
            // wasn't extracted => we do not expect the path to exist on real FS
            assertFalse(Files.exists(p));
            assertEquals(VFS_ROOT_PATH.resolve("extractme"), p);
            p = parsePath.apply(fs, VFS_ROOT + "dir1/extractme");
            assertFalse(Files.exists(p));
            assertEquals(VFS_ROOT_PATH.resolve("dir1/extractme"), p);
            p = parsePath.apply(fs, VFS_ROOT + "does-not-exist/extractme");
            assertFalse(Files.exists(p));
            assertEquals(VFS_ROOT_PATH.resolve("does-not-exist/extractme"), p);
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

            checkAccessVFS(fs, VFS_ROOT);
            withCWD(fs, VFS_ROOT_PATH, (fst) -> checkAccessVFS(fs, ""));
        }

        // from real FS
        Path realFSPath = Files.createTempDirectory("graalpy.vfs.test").resolve("extractme");
        realFSPath.toFile().createNewFile();

        for (FileSystem fs : new FileSystem[]{rwHostIOVFS, rHostIOVFS}) {
            fs.checkAccess(realFSPath, Set.of(AccessMode.READ));
            withCWD(fs, realFSPath.getParent(), (fst) -> fst.checkAccess(realFSPath.getFileName(), Set.of(AccessMode.READ)));
            withCWD(fs, VFS_ROOT_PATH, (fst) -> fst.checkAccess(Path.of("../" + realFSPath.toString()), Set.of(AccessMode.READ)));
        }
        checkException(SecurityException.class, () -> noHostIOVFS.checkAccess(realFSPath, Set.of(AccessMode.READ)), "expected error for no host io fs");
    }

    private void checkAccessVFS(FileSystem fs, String pathPrefix) throws IOException {
        // check regular resource dir
        fs.checkAccess(Path.of(pathPrefix + "dir1"), Set.of(AccessMode.READ));
        // check regular resource file
        fs.checkAccess(Path.of(pathPrefix + "SomeFile"), Set.of(AccessMode.READ));
        // check to be extracted file
        fs.checkAccess(Path.of(pathPrefix + "extractme"), Set.of(AccessMode.READ));

        checkException(SecurityException.class, () -> fs.checkAccess(Path.of(pathPrefix + "SomeFile"), Set.of(AccessMode.WRITE)), "write access should not be possible with VFS");
        checkException(NoSuchFileException.class, () -> fs.checkAccess(Path.of(pathPrefix + "does-not-exits"), Set.of(AccessMode.READ)),
                        "should not be able to access a file which does not exist in VFS");
        checkException(NoSuchFileException.class, () -> fs.checkAccess(Path.of(pathPrefix + "does-not-exits/extractme"), Set.of(AccessMode.READ)),
                        "should not be able to access a file which does not exist in VFS");
    }

    @Test
    public void createDirectory() throws Exception {
        // from VFS
        for (FileSystem fs : new FileSystem[]{rwHostIOVFS, rHostIOVFS, noHostIOVFS}) {
            Path path = VFS_ROOT_PATH.resolve("new-dir");
            checkException(NullPointerException.class, () -> fs.createDirectory(null));
            checkException(NullPointerException.class, () -> fs.createDirectory(path, (FileAttribute<?>[]) null));
            checkException(SecurityException.class, () -> fs.createDirectory(path), "should not be able to create a directory in VFS");

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
            fs.createDirectory(newDir2.getFileName());
            assertTrue(Files.exists(newDir2));
        });
        withCWD(rwHostIOVFS, VFS_ROOT_PATH, (fs) -> {
            Path newDir3 = newDir.getParent().resolve("newdir3");
            fs.createDirectory(Path.of("../" + newDir3.toString()));
            assertTrue(Files.exists(newDir3));
        });
    }

    @Test
    public void delete() throws Exception {
        // from VFS
        for (FileSystem fs : new FileSystem[]{rwHostIOVFS, rHostIOVFS, noHostIOVFS}) {
            checkException(NullPointerException.class, () -> fs.delete(null));

            deleteVFS(fs, VFS_ROOT);
            withCWD(fs, VFS_ROOT_PATH, (fst) -> deleteVFS(fs, ""));
        }

        // from real FS
        Path realFSPath = Files.createTempDirectory("graalpy.vfs.test").resolve("extractme");
        Files.createFile(realFSPath);
        assertTrue(Files.exists(realFSPath));

        checkException(SecurityException.class, () -> noHostIOVFS.delete(realFSPath), "expected error for no host io fs");
        assertTrue(Files.exists(realFSPath));

        checkException(SecurityException.class, () -> rHostIOVFS.delete(realFSPath), "should not be able to create a directory in a read-only FS");
        assertTrue(Files.exists(realFSPath));

        rwHostIOVFS.delete(realFSPath);
        assertFalse(Files.exists(realFSPath));

        Files.createFile(realFSPath);
        assertTrue(Files.exists(realFSPath));
        withCWD(rwHostIOVFS, realFSPath.getParent(), (fs) -> {
            rwHostIOVFS.delete(realFSPath.getFileName());
            assertFalse(Files.exists(realFSPath));
        });
        Files.createFile(realFSPath);
        assertTrue(Files.exists(realFSPath));
        withCWD(rwHostIOVFS, VFS_ROOT_PATH, (fs) -> {
            rwHostIOVFS.delete(Path.of("../" + realFSPath.toString()));
            assertFalse(Files.exists(realFSPath));
        });
    }

    private void deleteVFS(FileSystem fs, String pathPrefix) {
        checkDelete(fs, pathPrefix + "file1");
        checkDelete(fs, pathPrefix + "dir1");
        checkDelete(fs, pathPrefix + "extractme");
    }

    private static void checkDelete(FileSystem fs, String path) {
        checkException(SecurityException.class, () -> fs.delete(Path.of(path)), "should not be able to delete in VFS");
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

            newByteChannelVFS(fs, VFS_ROOT);
            withCWD(fs, VFS_ROOT_PATH, (fst) -> newByteChannelVFS(fst, ""));
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
            withCWD(fs, VFS_ROOT_PATH, (fst) -> newByteChannelRealFS(fs, Path.of("../" + realFSPath.toString()), "text"));
        }
    }

    private void newByteChannelVFS(FileSystem fs, String pathPrefix) throws IOException {
        Path path = Path.of(pathPrefix + "file1");
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

    private void newByteChannelRealFS(FileSystem fs, Path path, String expectedText) throws IOException {
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

            newDirectoryStreamVFS(fs, VFS_ROOT);
            withCWD(fs, VFS_ROOT_PATH, (fst) -> newDirectoryStreamVFS(fst, ""));
        }

        // from real FS
        Path realFSPath = Files.createTempDirectory("graalpy.vfs.test").resolve("extractme");
        Files.createFile(realFSPath);
        for (FileSystem fs : new FileSystem[]{rHostIOVFS, rwHostIOVFS}) {
            newDirectoryStreamRealFS(fs, realFSPath.getParent(), realFSPath);
            withCWD(fs, realFSPath.getParent().getParent(), (fst) -> newDirectoryStreamRealFS(fs, realFSPath.getParent().getFileName(), realFSPath));
            withCWD(fs, VFS_ROOT_PATH, (fst) -> newDirectoryStreamRealFS(fs, Path.of("../" + realFSPath.getParent().toString()), realFSPath));
        }
        checkException(SecurityException.class, () -> noHostIOVFS.newDirectoryStream(realFSPath, null), "expected error for no host io fs");
    }

    public void newDirectoryStreamVFS(FileSystem fs, String pathPrefix) throws Exception {
        DirectoryStream<Path> ds = fs.newDirectoryStream(Path.of(pathPrefix + "dir1"), (p) -> true);
        Set<String> s = new HashSet<>();
        Iterator<Path> it = ds.iterator();
        while (it.hasNext()) {
            Path p = it.next();
            s.add(p.toString());
        }
        assertEquals(2, s.size());
        assertTrue(s.contains(VFS_ROOT + "dir1" + File.separator + "extractme"));
        assertTrue(s.contains(VFS_ROOT + "dir1" + File.separator + "file2"));

        ds = fs.newDirectoryStream(Path.of(pathPrefix + "dir1"), (p) -> false);
        assertFalse(ds.iterator().hasNext());

        checkException(NotDirectoryException.class, () -> fs.newDirectoryStream(Path.of(pathPrefix + "file1"), (p) -> true), "");
        checkException(NoSuchFileException.class, () -> fs.newDirectoryStream(Path.of(pathPrefix + "does-not-exist"), (p) -> true), "");
    }

    public void newDirectoryStreamRealFS(FileSystem fs, Path dir, Path file) throws Exception {
        DirectoryStream<Path> ds = fs.newDirectoryStream(dir, (p) -> true);
        Iterator<Path> it = ds.iterator();
        assertEquals(file, it.next());
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
            readAttributesVFS(fs, VFS_ROOT);
            withCWD(fs, VFS_ROOT_PATH, (fst) -> readAttributesVFS(fst, ""));
        }

        // from real FS
        Path realFSPath = Files.createTempDirectory("graalpy.vfs.test").resolve("extractme");
        Files.createFile(realFSPath);
        for (FileSystem fs : new FileSystem[]{rHostIOVFS, rwHostIOVFS}) {
            assertTrue(((FileTime) fs.readAttributes(realFSPath, "creationTime").get("creationTime")).toMillis() > 0);
            withCWD(fs, realFSPath.getParent(), (fst) -> assertTrue(((FileTime) fs.readAttributes(realFSPath.getFileName(), "creationTime").get("creationTime")).toMillis() > 0));
            withCWD(fs, VFS_ROOT_PATH, (fst) -> assertTrue(((FileTime) fs.readAttributes(Path.of("../" + realFSPath.toString()), "creationTime").get("creationTime")).toMillis() > 0));

        }
        checkException(SecurityException.class, () -> noHostIOVFS.readAttributes(realFSPath, "creationTime"), "expected error for no host io fs");
    }

    public void readAttributesVFS(FileSystem fs, String pathPrefix) throws IOException {
        Map<String, Object> attrs = fs.readAttributes(Path.of(pathPrefix + "dir1"), "creationTime");
        assertEquals(FileTime.fromMillis(0), attrs.get("creationTime"));

        checkException(NoSuchFileException.class, () -> fs.readAttributes(Path.of(pathPrefix + "does-not-exist"), "creationTime"), "");
        checkException(UnsupportedOperationException.class, () -> fs.readAttributes(Path.of(pathPrefix + "file1"), "unix:creationTime"), "");
    }

    @Test
    public void libsExtract() throws Exception {
        FileSystem fs = getVFSImpl(VirtualFileSystem.newBuilder().//
                        unixMountPoint(VFS_MOUNT_POINT).//
                        windowsMountPoint(VFS_WIN_MOUNT_POINT).//
                        extractFilter(p -> p.getFileName().toString().endsWith(".tso")).//
                        resourceLoadingClass(VirtualFileSystemTest.class).build());
        Path p = fs.toAbsolutePath(VFS_ROOT_PATH.resolve("site-packages/testpkg/file.tso"));
        checkExtractedFile(p, null);
        Path extractedRoot = p.getParent().getParent().getParent();

        checkExtractedFile(extractedRoot.resolve("site-packages/testpkg.libs/file1.tso"), null);
        checkExtractedFile(extractedRoot.resolve("site-packages/testpkg.libs/file2.tso"), null);
        checkExtractedFile(extractedRoot.resolve("site-packages/testpkg.libs/dir/file1.tso"), null);
        checkExtractedFile(extractedRoot.resolve("site-packages/testpkg.libs/dir/file2.tso"), null);
        checkExtractedFile(extractedRoot.resolve("site-packages/testpkg.libs/dir/nofilterfile"), null);
        checkExtractedFile(extractedRoot.resolve("site-packages/testpkg.libs/dir/dir/file1.tso"), null);
        checkExtractedFile(extractedRoot.resolve("site-packages/testpkg.libs/dir/dir/file2.tso"), null);

        p = fs.toAbsolutePath(VFS_ROOT_PATH.resolve("site-packages/testpkg-nolibs/file.tso"));
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
            assertEquals(exType, e.getClass());
            gotEx = true;
        }
        assertTrue(msg != null ? msg : "expected " + exType.getName(), gotEx);
    }

    @Test
    public void setCurrentWorkingDirectory() throws Exception {

        // XXX test with not normalized paths

        Path realFSDir = Files.createTempDirectory("graalpy.vfs.test");
        Path realFSFile = realFSDir.resolve("extractme");
        Files.createFile(realFSFile);

        for (FileSystem fs : new FileSystem[]{rwHostIOVFS, rHostIOVFS, noHostIOVFS}) {
            checkException(NullPointerException.class, () -> fs.setCurrentWorkingDirectory(null), "expected NPE");
            checkException(IllegalArgumentException.class, () -> fs.setCurrentWorkingDirectory(Path.of("dir")));

            checkException(IllegalArgumentException.class, () -> fs.setCurrentWorkingDirectory(VFS_ROOT_PATH.resolve("file1")));

            try {
                // support non existing working dir
                Path nonExistingDir = VFS_ROOT_PATH.resolve("does-not-exist");
                fs.setCurrentWorkingDirectory(nonExistingDir);
                assertEquals(nonExistingDir, fs.toAbsolutePath(Path.of("dir")).getParent());

                Path vfsDir = VFS_ROOT_PATH.resolve("dir1");
                fs.setCurrentWorkingDirectory(vfsDir);
                assertEquals(vfsDir, fs.toAbsolutePath(Path.of("dir")).getParent());
            } finally {
                resetCWD(fs);
            }

            if (fs == noHostIOVFS) {
                checkException(SecurityException.class, () -> fs.setCurrentWorkingDirectory(realFSFile));
                checkException(SecurityException.class, () -> fs.setCurrentWorkingDirectory(realFSDir));
            } else {
                checkException(IllegalArgumentException.class, () -> fs.setCurrentWorkingDirectory(realFSFile));
                try {
                    // support non existing working dir
                    Path nonExistingDir = realFSDir.resolve("does-not-exist");
                    fs.setCurrentWorkingDirectory(nonExistingDir);
                    assertEquals(nonExistingDir, fs.toAbsolutePath(Path.of("dir")).getParent());

                    fs.setCurrentWorkingDirectory(realFSDir);
                    assertEquals(realFSDir, fs.toAbsolutePath(Path.of("dir")).getParent());
                } finally {
                    resetCWD(fs);
                }
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
            withCWD(fs, VFS_ROOT_PATH, (fst) -> fst.getMimeType(Path.of("../" + realFSPath.toString())));
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
            withCWD(fs, VFS_ROOT_PATH, (fst) -> fst.getEncoding(Path.of("../" + realFSPath.toString())));
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

        rwHostIOVFS.setAttribute(realFSPath, "creationTime", FileTime.fromMillis(42));
        withCWD(rwHostIOVFS, VFS_ROOT_PATH, (fst) -> fst.setAttribute(Path.of("../" + realFSPath.toString()), "creationTime", FileTime.fromMillis(43)));
    }

    @Test
    public void isSameFile() throws Exception {
        for (FileSystem fs : new FileSystem[]{rwHostIOVFS, rHostIOVFS, noHostIOVFS}) {
            assertTrue(fs.isSameFile(Path.of(VFS_SRC), Path.of(VFS_SRC + "/../src")));
        }
        Path realFSPath = Files.createTempDirectory("graalpy.vfs.test").resolve("extractme");
        Files.createFile(realFSPath);
        for (FileSystem fs : new FileSystem[]{rwHostIOVFS, rHostIOVFS}) {
            withCWD(fs, VFS_ROOT_PATH, (fst) -> assertTrue(fst.isSameFile(realFSPath, Path.of("../" + realFSPath.toString()))));
        }
        checkException(SecurityException.class, () -> noHostIOVFS.isSameFile(realFSPath, Path.of(realFSPath.getParent().toString() + "/../" + realFSPath.getFileName())));
    }

    @Test
    public void createLink() throws Exception {
        Path realFSPath = Files.createTempDirectory("graalpy.vfs.test").resolve("extractme");
        Files.createFile(realFSPath);
        for (FileSystem fs : new FileSystem[]{rwHostIOVFS, rHostIOVFS, noHostIOVFS}) {
            checkException(NullPointerException.class, () -> fs.createLink(null, null));
            checkException(NullPointerException.class, () -> fs.createLink(VFS_ROOT_PATH, null));
            checkException(SecurityException.class, () -> fs.createLink(VFS_ROOT_PATH.resolve("/link1"), realFSPath));
            checkException(SecurityException.class, () -> fs.createLink(realFSPath.getParent().resolve("link2"), VFS_ROOT_PATH));
            checkException(SecurityException.class, () -> fs.createLink(VFS_ROOT_PATH, VFS_ROOT_PATH.resolve("link")));
        }
        Path link = realFSPath.getParent().resolve("link1");
        assertFalse(Files.exists(link));
        rwHostIOVFS.createLink(link, realFSPath);
        assertTrue(Files.exists(link));
        Path link2 = realFSPath.getParent().resolve("link2");
        assertFalse(Files.exists(link2));
        withCWD(rwHostIOVFS, VFS_ROOT_PATH, (fs) -> rwHostIOVFS.createLink(Path.of("../" + link2.toString()), Path.of("../" + realFSPath.toString())));
        assertTrue(Files.exists(link2));

        checkException(SecurityException.class, () -> rHostIOVFS.createLink(realFSPath.getParent().resolve("link3"), realFSPath));
        checkException(SecurityException.class, () -> noHostIOVFS.createLink(realFSPath.getParent().resolve("link4"), realFSPath));
    }

    @Test
    public void createAndReadSymbolicLink() throws Exception {
        Path realFSPath = Files.createTempDirectory("graalpy.vfs.test").resolve("extractme");
        Files.createFile(realFSPath);
        for (FileSystem fs : new FileSystem[]{rwHostIOVFS, rHostIOVFS, noHostIOVFS}) {
            checkException(NullPointerException.class, () -> fs.createSymbolicLink(null, null));
            checkException(NullPointerException.class, () -> fs.readSymbolicLink(null));

            checkException(SecurityException.class, () -> fs.createSymbolicLink(VFS_ROOT_PATH.resolve("/link1"), realFSPath));
            checkException(SecurityException.class, () -> fs.readSymbolicLink(VFS_ROOT_PATH.resolve("/link1")));
            checkException(SecurityException.class, () -> fs.createSymbolicLink(realFSPath.getParent().resolve("link2"), VFS_ROOT_PATH));
            checkException(SecurityException.class, () -> fs.createSymbolicLink(VFS_ROOT_PATH, VFS_ROOT_PATH.resolve("link")));
        }

        Path link = realFSPath.getParent().resolve("link1");
        assertFalse(Files.exists(link));
        rwHostIOVFS.createSymbolicLink(link, realFSPath);
        assertTrue(Files.exists(link));
        assertEquals(realFSPath, rwHostIOVFS.readSymbolicLink(link));
        assertEquals(realFSPath, rHostIOVFS.readSymbolicLink(link));

        Path link2 = realFSPath.getParent().resolve("link2");
        assertFalse(Files.exists(link2));
        withCWD(rwHostIOVFS, VFS_ROOT_PATH, (fs) -> rwHostIOVFS.createSymbolicLink(Path.of("../" + link2.toString()), Path.of("../" + realFSPath.toString())));
        assertTrue(Files.exists(link2));
        withCWD(rwHostIOVFS, VFS_ROOT_PATH, (fs) -> assertEquals(realFSPath, fs.readSymbolicLink(Path.of("../" + link2.toString()))));
        withCWD(rHostIOVFS, VFS_ROOT_PATH, (fs) -> assertEquals(realFSPath, fs.readSymbolicLink(Path.of("../" + link2.toString()))));

        checkException(SecurityException.class, () -> rHostIOVFS.createSymbolicLink(realFSPath.getParent().resolve("link2"), realFSPath));
        checkException(SecurityException.class, () -> noHostIOVFS.createSymbolicLink(realFSPath.getParent().resolve("link3"), realFSPath));
        checkException(SecurityException.class, () -> noHostIOVFS.readSymbolicLink(link));
    }

    @Test
    public void move() throws Exception {
        Path realFSPath = Files.createTempDirectory("graalpy.vfs.test").resolve("extractme");
        for (FileSystem fs : new FileSystem[]{rwHostIOVFS, rHostIOVFS, noHostIOVFS}) {
            checkException(NullPointerException.class, () -> fs.move(null, null));
            checkException(NullPointerException.class, () -> fs.move(VFS_ROOT_PATH, null));
            checkException(SecurityException.class, () -> fs.move(VFS_ROOT_PATH.resolve("file1"), realFSPath));
            checkException(SecurityException.class, () -> fs.move(realFSPath, VFS_ROOT_PATH));
            checkException(SecurityException.class, () -> fs.move(VFS_ROOT_PATH.resolve("file1"), VFS_ROOT_PATH.resolve("file2")));
        }

        Files.createFile(realFSPath);
        rwHostIOVFS.newByteChannel(realFSPath, Set.of(StandardOpenOption.WRITE)).write(ByteBuffer.wrap("moved text".getBytes()));
        assertTrue(Files.exists(realFSPath));

        Path realFSPath2 = realFSPath.getParent().resolve("file");
        assertFalse(Files.exists(realFSPath2));
        rwHostIOVFS.move(realFSPath, realFSPath2);
        assertFalse(Files.exists(realFSPath));
        assertTrue(Files.exists(realFSPath2));
        newByteChannelRealFS(rwHostIOVFS, realFSPath2, "moved text");
        withCWD(rwHostIOVFS, VFS_ROOT_PATH, (fs) -> fs.move(Path.of("../" + realFSPath2.toString()), Path.of("../" + realFSPath)));
        assertTrue(Files.exists(realFSPath));
        assertFalse(Files.exists(realFSPath2));
        newByteChannelRealFS(rwHostIOVFS, realFSPath, "moved text");

        checkException(SecurityException.class, () -> rHostIOVFS.move(realFSPath2, realFSPath));
        checkException(SecurityException.class, () -> noHostIOVFS.move(realFSPath2, realFSPath));
    }

    @Test
    public void copy() throws Exception {
        Path realFSPath = Files.createTempDirectory("graalpy.vfs.test").resolve("extractme");
        for (FileSystem fs : new FileSystem[]{rwHostIOVFS, rHostIOVFS, noHostIOVFS}) {
            checkException(NullPointerException.class, () -> fs.copy(null, null));
            checkException(NullPointerException.class, () -> fs.copy(VFS_ROOT_PATH, null));
            checkException(SecurityException.class, () -> fs.copy(VFS_ROOT_PATH.resolve("file1"), VFS_ROOT_PATH.resolve("file2")));
            checkException(SecurityException.class, () -> fs.copy(realFSPath, VFS_ROOT_PATH.resolve("file2")));
        }

        checkException(SecurityException.class, () -> noHostIOVFS.copy(realFSPath, realFSPath.getParent().resolve("file")));
        checkException(SecurityException.class, () -> rHostIOVFS.copy(realFSPath, realFSPath.getParent().resolve("file")));

        Files.createFile(realFSPath);
        rwHostIOVFS.newByteChannel(realFSPath, Set.of(StandardOpenOption.WRITE)).write(ByteBuffer.wrap("copied text".getBytes()));
        assertTrue(Files.exists(realFSPath));

        Path realFSPath2 = realFSPath.getParent().resolve("file");
        assertFalse(Files.exists(realFSPath2));
        rwHostIOVFS.copy(realFSPath, realFSPath2);
        assertTrue(Files.exists(realFSPath));
        assertTrue(Files.exists(realFSPath2));
        newByteChannelRealFS(rwHostIOVFS, realFSPath2, "copied text");

        Path realFSPath3 = realFSPath.getParent().resolve("file3");
        assertFalse(Files.exists(realFSPath3));
        withCWD(rwHostIOVFS, VFS_ROOT_PATH, (fs) -> fs.copy(Path.of("../" + realFSPath.toString()), Path.of("../" + realFSPath3.toString())));
        assertTrue(Files.exists(realFSPath));
        assertTrue(Files.exists(realFSPath3));
        newByteChannelRealFS(rwHostIOVFS, realFSPath3, "copied text");

        Path realFSPath4 = realFSPath.getParent().resolve("fromvfs");
        assertFalse(Files.exists(realFSPath4));
        rwHostIOVFS.copy(VFS_ROOT_PATH.resolve("file1"), realFSPath4);
        assertTrue(Files.exists(realFSPath4));
        newByteChannelRealFS(rwHostIOVFS, realFSPath4, "text1");

        Path realFSPath5 = realFSPath.getParent().resolve("fromvfs2");
        assertFalse(Files.exists(realFSPath5));
        withCWD(rwHostIOVFS, VFS_ROOT_PATH, (fs) -> fs.copy(Path.of("file1"), Path.of("../" + realFSPath5.toString())));
        assertTrue(Files.exists(realFSPath5));
        newByteChannelRealFS(rwHostIOVFS, realFSPath5, "text1");
    }

    @Test
    public void testImpl() throws NoSuchFieldException, IllegalAccessException {
        Set<String> ignored = Set.of(
                        "allowInternalResourceAccess",
                        "allowLanguageHomeAccess",
                        "newReadOnlyFileSystem",
                        "newFileSystem",
                        "newDefaultFileSystem",
                        "isSameFile",
                        "getSeparator",
                        "getPathSeparator");
        Set<String> implementedMethods = new HashSet<>();
        Class<?> vfsClass = getVFSImpl(VirtualFileSystem.create()).getClass();
        for (Method m : vfsClass.getDeclaredMethods()) {
            if ((m.getModifiers() & Modifier.PUBLIC) != 0) {
                implementedMethods.add(m.getName());
            }
        }

        List<String> notImplemented = new ArrayList<>();
        for (Method m : FileSystem.class.getDeclaredMethods()) {
            if ((m.getModifiers() & Modifier.PUBLIC) != 0) {
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

    private interface FSCall {
        void call(FileSystem fs) throws Exception;
    }

    private void withCWD(FileSystem fs, Path cwd, FSCall c) throws Exception {
        fs.setCurrentWorkingDirectory(cwd);
        try {
            c.call(fs);
        } finally {
            resetCWD(fs);
        }
    }

    private void resetCWD(FileSystem fs) throws NoSuchFieldException, IllegalAccessException {
        Field f = fs.getClass().getDeclaredField("cwd");
        f.setAccessible(true);
        if (fs == noHostIOVFS) {
            f.set(fs, Path.of(VFS_MOUNT_POINT + File.separator + "src"));
        } else {
            f.set(fs, null);
        }
    }

}
