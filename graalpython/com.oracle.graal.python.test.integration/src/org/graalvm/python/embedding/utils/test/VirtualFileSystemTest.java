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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.io.IOAccess;
import org.graalvm.python.embedding.vfs.VirtualFileSystem;
import org.junit.Test;

public class VirtualFileSystemTest {

    private static final String VFS_UNIX_MOUNT_POINT = "/test_mount_point";
    private static final String VFS_WIN_MOUNT_POINT = "X:\\test_win_mount_point";
    private static final String VFS_MOUNT_POINT = IS_WINDOWS ? VFS_WIN_MOUNT_POINT : VFS_UNIX_MOUNT_POINT;

    private static final String PYTHON = "python";

    @Test
    public void defaultValues() throws Exception {
        VirtualFileSystem vfs = VirtualFileSystem.create();
        VirtualFileSystem vfs2 = VirtualFileSystem.newBuilder().build();

        assertEquals(vfs.getMountPoint(), vfs2.getMountPoint());
        assertEquals(vfs.vfsHomePath(), vfs2.vfsHomePath());
        assertEquals(vfs.vfsProjPath(), vfs2.vfsProjPath());
        assertEquals(vfs.vfsVenvPath(), vfs2.vfsVenvPath());

        assertEquals(IS_WINDOWS ? "X:\\graalpy_vfs" : "/graalpy_vfs", vfs.getMountPoint());
        assertEquals(IS_WINDOWS ? "X:\\graalpy_vfs\\home" : "/graalpy_vfs/home", vfs.vfsHomePath());
        assertEquals(IS_WINDOWS ? "X:\\graalpy_vfs\\proj" : "/graalpy_vfs/proj", vfs.vfsProjPath());
        assertEquals(IS_WINDOWS ? "X:\\graalpy_vfs\\venv" : "/graalpy_vfs/venv", vfs.vfsVenvPath());
    }

    @Test
    public void mountPoints() throws Exception {
        VirtualFileSystem vfs = VirtualFileSystem.newBuilder().//
                        unixMountPoint(VFS_MOUNT_POINT).//
                        windowsMountPoint(VFS_WIN_MOUNT_POINT).build();

        assertEquals(VFS_MOUNT_POINT, vfs.getMountPoint());
        assertEquals(IS_WINDOWS ? VFS_WIN_MOUNT_POINT + "\\home" : VFS_UNIX_MOUNT_POINT + "/home", vfs.vfsHomePath());
        assertEquals(IS_WINDOWS ? VFS_WIN_MOUNT_POINT + "\\proj" : VFS_UNIX_MOUNT_POINT + "/proj", vfs.vfsProjPath());
        assertEquals(IS_WINDOWS ? VFS_WIN_MOUNT_POINT + "\\venv" : VFS_UNIX_MOUNT_POINT + "/venv", vfs.vfsVenvPath());
    }

    @Test
    public void toRealPath() throws Exception {
        VirtualFileSystem vfs = VirtualFileSystem.newBuilder().//
                        unixMountPoint(VFS_MOUNT_POINT).//
                        windowsMountPoint(VFS_WIN_MOUNT_POINT).//
                        extractFilter(p -> p.getFileName().toString().equals("file1")).//
                        resourceLoadingClass(VirtualFileSystemTest.class).build();
        // check regular resource dir
        assertEquals("dir1", vfs.toRealPath(Path.of("dir1")).toString());
        assertEquals(VFS_MOUNT_POINT + File.separator + "dir1", vfs.toRealPath(Path.of(VFS_MOUNT_POINT + File.separator + "dir1")).toString());
        // check regular resource file
        assertEquals("SomeFile", vfs.toRealPath(Path.of("SomeFile")).toString());
        assertEquals(VFS_MOUNT_POINT + File.separator + "SomeFile", vfs.toRealPath(Path.of(VFS_MOUNT_POINT + File.separator + "SomeFile")).toString());
        // check to be extracted file
        checkExtractedFile(vfs.toRealPath(Path.of("file1")), new String[]{"text1", "text2"});
        checkExtractedFile(vfs.toRealPath(Path.of(VFS_MOUNT_POINT + File.separator + "file1")), new String[]{"text1", "text2"});
    }

    @Test
    public void toAbsolutePath() throws Exception {
        VirtualFileSystem vfs = VirtualFileSystem.newBuilder().//
                        unixMountPoint(VFS_MOUNT_POINT).//
                        windowsMountPoint(VFS_WIN_MOUNT_POINT).//
                        extractFilter(p -> p.getFileName().toString().equals("file1") || p.getFileName().toString().equals("file2")).//
                        resourceLoadingClass(VirtualFileSystemTest.class).build();
        // check regular resource dir
        assertEquals(VFS_MOUNT_POINT + File.separator + "dir1", vfs.toAbsolutePath(Path.of("dir1")).toString());
        assertEquals(VFS_MOUNT_POINT + File.separator + "dir1", vfs.toAbsolutePath(Path.of(VFS_MOUNT_POINT + File.separator + "dir1")).toString());
        // check regular resource file
        assertEquals(VFS_MOUNT_POINT + File.separator + "SomeFile", vfs.toAbsolutePath(Path.of("SomeFile")).toString());
        assertEquals(VFS_MOUNT_POINT + File.separator + "SomeFile", vfs.toAbsolutePath(Path.of(VFS_MOUNT_POINT + File.separator + "SomeFile")).toString());
        // check to be extracted file
        checkExtractedFile(vfs.toAbsolutePath(Path.of("file1")), new String[]{"text1", "text2"});
        checkExtractedFile(vfs.toAbsolutePath(Path.of(VFS_MOUNT_POINT + File.separator + "file1")), new String[]{"text1", "text2"});
        checkExtractedFile(vfs.toAbsolutePath(Path.of("dir1/file2")), null);
        checkExtractedFile(vfs.toAbsolutePath(Path.of(VFS_MOUNT_POINT + File.separator + "dir1/file2")), null);
    }

    @Test
    public void libsExtract() throws Exception {
        VirtualFileSystem vfs = VirtualFileSystem.newBuilder().//
                        unixMountPoint(VFS_MOUNT_POINT).//
                        windowsMountPoint(VFS_WIN_MOUNT_POINT).//
                        extractFilter(p -> p.getFileName().toString().endsWith(".tso")).//
                        resourceLoadingClass(VirtualFileSystemTest.class).build();
        Path p = vfs.toAbsolutePath(Path.of("site-packages/testpkg/file.tso"));
        checkExtractedFile(p, null);
        Path extractedRoot = p.getParent().getParent().getParent();
        checkExtractedFile(extractedRoot.resolve("site-packages/testpkg.libs/file1.tso"), null);
        checkExtractedFile(extractedRoot.resolve("site-packages/testpkg.libs/file2.tso"), null);
        checkExtractedFile(extractedRoot.resolve("site-packages/testpkg.libs/dir/file1.tso"), null);
        checkExtractedFile(extractedRoot.resolve("site-packages/testpkg.libs/dir/file2.tso"), null);
        checkExtractedFile(extractedRoot.resolve("site-packages/testpkg.libs/dir/nofilterfile"), null);
        checkExtractedFile(extractedRoot.resolve("site-packages/testpkg.libs/dir/dir/file1.tso"), null);
        checkExtractedFile(extractedRoot.resolve("site-packages/testpkg.libs/dir/dir/file2.tso"), null);

        p = vfs.toAbsolutePath(Path.of("site-packages/testpkg-nolibs/file.tso"));
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

    @Test
    public void fsOperations() {

        // os.path.exists

        eval("import os; assert os.path.exists('/test_mount_point/file1')");
        eval("import os; assert os.path.exists('/test_mount_point/dir1')");
        eval("import os; assert os.path.exists('/test_mount_point/dir1/')");
        eval("import os; assert os.path.exists('/test_mount_point/emptydir')");
        eval("import os; assert os.path.exists('/test_mount_point/emptydir/')");
        eval("import os; assert os.path.exists('/test_mount_point/dir1/file2')");
        eval("import os; assert not os.path.exists('/test_mount_point/doesnotexist')");
        eval("import os; assert not os.path.exists('/test_mount_point/doesnotexist/')");

        // pathlib.exists

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

        eval("import os; assert not os.path.isdir('/test_mount_point/file1')");
        eval("import os; assert os.path.isdir('/test_mount_point/dir1')");
        eval("import os; assert os.path.isdir('/test_mount_point/dir1/')");

        // pathlib.is_file|is_dir

        eval("from pathlib import Path; assert Path('/test_mount_point/file1').is_file()");
        eval("from pathlib import Path; assert not Path('/test_mount_point/dir1').is_file()");
        eval("from pathlib import Path; assert not Path('/test_mount_point/dir1/').is_file()");

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
                        assert len(f) == 6, 'expected 6 files, got ' + str(len(f))

                        assert 'dir1' in f, 'does not contain "dir1"'
                        assert 'emptydir' in f, 'does not contain "emptydir"'
                        assert 'file1' in f, 'does not contain "file1"'
                        assert 'fileslist.txt' in f, 'does not contain "fileslist.txt"'

                        f = listdir('/test_mount_point/dir1')
                        if len(f) != 2:
                            print('files in /test_mount_point/dir1:')
                            for ff in f:
                                print(ff)
                            assert False, 'expected 2 got ' + str(len(f))
                        assert 'dir2' in f, 'does not contain "dir2"'
                        assert 'file2' in f, 'does not contain "file2"'
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

                        assert len(roots) == 10, 'expected 10 roots, got ' + str(len(roots))
                        assert len(files) == 13, 'expected 13 files, got ' + str(len(files))
                        assert len(dirs) == 9, 'expected 9 dirs, got ' + str(len(dirs))
                        """);

        // read file

        eval("""
                        with open("/test_mount_point/file1", "r") as f:
                            l = f.readlines()
                            assert len(l) == 2, 'expect 2 lines, got ' + len(l)
                            assert l[0] == "text1\\n", 'expected "text1", got ' + l[0]
                            assert l[1] == "text2\\n", 'expected "text2", got ' + l[1]

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
        String src;
        if (IS_WINDOWS) {
            src = s.replace(VFS_UNIX_MOUNT_POINT, "X:\\\\test_win_mount_point");
        } else {
            src = s;
        }
        getContext(builderFunction).eval(PYTHON, src);
    }

    private Context cachedContext;

    public Context getContext(Function<VirtualFileSystem.Builder, VirtualFileSystem.Builder> builderFunction) {
        if (builderFunction == null && cachedContext != null) {
            return cachedContext;
        }
        VirtualFileSystem.Builder builder = VirtualFileSystem.newBuilder().//
                        unixMountPoint(VFS_MOUNT_POINT).//
                        windowsMountPoint(VFS_WIN_MOUNT_POINT).//
                        resourceLoadingClass(VirtualFileSystemTest.class);
        if (builderFunction != null) {
            builder = builderFunction.apply(builder);
        }
        VirtualFileSystem vfs = builder.build();
        Context context = Context.newBuilder().//
                        allowExperimentalOptions(false).//
                        allowAllAccess(false).//
                        allowHostAccess(HostAccess.ALL).//
                        allowIO(IOAccess.newBuilder().//
                                        allowHostSocketAccess(true).//
                                        fileSystem(vfs).//
                                        build()).//
                        allowCreateThread(true).//
                        allowNativeAccess(true).//
                        allowPolyglotAccess(PolyglotAccess.ALL).//
                        option("python.PosixModuleBackend", "java").//
                        option("python.DontWriteBytecodeFlag", "true").//
                        option("python.VerboseFlag", System.getenv("PYTHONVERBOSE") != null ? "true" : "false").//
                        option("log.python.level", System.getenv("PYTHONVERBOSE") != null ? "FINE" : "SEVERE").//
                        option("python.WarnOptions", System.getenv("PYTHONWARNINGS") == null ? "" : System.getenv("PYTHONWARNINGS")).//
                        option("python.AlwaysRunExcepthook", "true").//
                        option("python.ForceImportSite", "true").//
                        option("engine.WarnInterpreterOnly", "false").build();
        if (builderFunction == null) {
            cachedContext = context;
        }
        return context;
    }
}
