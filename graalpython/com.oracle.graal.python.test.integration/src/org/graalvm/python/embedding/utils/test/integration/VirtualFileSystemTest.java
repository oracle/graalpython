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

package org.graalvm.python.embedding.utils.test.integration;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Context.Builder;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.graalvm.python.embedding.utils.GraalPyResources;
import org.graalvm.python.embedding.utils.VirtualFileSystem;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.oracle.graal.python.test.integration.Utils.IS_WINDOWS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class VirtualFileSystemTest {

    static final String VFS_UNIX_MOUNT_POINT = "/test_mount_point";
    static final String VFS_WIN_MOUNT_POINT = "X:\\test_win_mount_point";
    static final String VFS_MOUNT_POINT = IS_WINDOWS ? VFS_WIN_MOUNT_POINT : VFS_UNIX_MOUNT_POINT;

    static final String PYTHON = "python";

    public VirtualFileSystemTest() {
        Logger logger = Logger.getLogger(VirtualFileSystem.class.getName());
        for (Handler handler : logger.getHandlers()) {
            handler.setLevel(Level.FINE);
        }
        logger.setLevel(Level.FINE);
    }

    @Test
    public void defaultValues() {
        VirtualFileSystem fs = VirtualFileSystem.create();
        VirtualFileSystem fs2 = VirtualFileSystem.create();

        assertEquals(fs.getMountPoint(), fs2.getMountPoint());

        assertEquals(IS_WINDOWS ? "X:\\graalpy_vfs" : "/graalpy_vfs", fs.getMountPoint());
    }

    @Test
    public void mountPoints() {
        VirtualFileSystem fs = VirtualFileSystem.newBuilder().//
                        unixMountPoint(VFS_UNIX_MOUNT_POINT).//
                        windowsMountPoint(VFS_WIN_MOUNT_POINT).build();

        assertEquals(VFS_MOUNT_POINT, fs.getMountPoint());

        String multiPathUnixMountPoint = "/test/mount/point";
        String multiPathWinMountPoint = "X:\\test\\win\\mount\\point";
        VirtualFileSystem vfs = VirtualFileSystem.newBuilder().//
                        unixMountPoint(multiPathUnixMountPoint).//
                        windowsMountPoint(multiPathWinMountPoint).//
                        resourceLoadingClass(VirtualFileSystemTest.class).build();
        Context ctx = addTestOptions(GraalPyResources.contextBuilder(vfs)).build();
        ctx.eval(PYTHON, "from os import listdir; listdir('" + (IS_WINDOWS ? multiPathWinMountPoint.replace("\\", "\\\\") : multiPathUnixMountPoint) + "')");
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
            assertEquals(e.getClass(), exType);
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
            checkException(IllegalArgumentException.class, () -> VirtualFileSystem.newBuilder().windowsMountPoint("test"));
            checkException(IllegalArgumentException.class, () -> VirtualFileSystem.newBuilder().windowsMountPoint("test\\"));
            checkException(IllegalArgumentException.class, () -> VirtualFileSystem.newBuilder().windowsMountPoint("\\test\\"));
            checkException(IllegalArgumentException.class, () -> VirtualFileSystem.newBuilder().windowsMountPoint("\\test"));
            checkException(IllegalArgumentException.class, () -> VirtualFileSystem.newBuilder().windowsMountPoint("X:\\test\\"));
            checkException(InvalidPathException.class, () -> VirtualFileSystem.newBuilder().windowsMountPoint("X:\\test|test"));
        } else {
            checkException(IllegalArgumentException.class, () -> VirtualFileSystem.newBuilder().unixMountPoint("test"));
            checkException(IllegalArgumentException.class, () -> VirtualFileSystem.newBuilder().unixMountPoint("test/"));
            checkException(IllegalArgumentException.class, () -> VirtualFileSystem.newBuilder().unixMountPoint("/test/"));
            checkException(IllegalArgumentException.class, () -> VirtualFileSystem.newBuilder().unixMountPoint("X:\\test"));
            checkException(InvalidPathException.class, () -> VirtualFileSystem.newBuilder().unixMountPoint("/test/\0"));
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void pythonPathsTest() throws IOException {
        Context ctx = GraalPyResources.createContext();
        String getPathsSource = "import sys; [__graalpython__.get_python_home_paths(), sys.path, sys.executable]";

        Value paths = ctx.eval("python", getPathsSource);
        String defaultMountPoint = VirtualFileSystem.newBuilder().build().getMountPoint();
        assertEquals(IS_WINDOWS ? "X:\\graalpy_vfs" : "/graalpy_vfs", defaultMountPoint);
        checkPaths(paths.as(List.class), defaultMountPoint);

        ctx = GraalPyResources.contextBuilder().build();
        paths = ctx.eval("python", getPathsSource);
        checkPaths(paths.as(List.class), defaultMountPoint);

        VirtualFileSystem vfs = VirtualFileSystem.newBuilder().//
                        unixMountPoint(VFS_UNIX_MOUNT_POINT).//
                        windowsMountPoint(VFS_WIN_MOUNT_POINT).build();
        assertEquals(VFS_MOUNT_POINT, vfs.getMountPoint());
        ctx = GraalPyResources.contextBuilder(vfs).build();
        paths = ctx.eval("python", getPathsSource);
        checkPaths(paths.as(List.class), vfs.getMountPoint(), true);

        Path resourcesDir = Files.createTempDirectory("python-resources");
        ctx = GraalPyResources.contextBuilder(resourcesDir).build();
        paths = ctx.eval("python", getPathsSource);
        checkPaths(paths.as(List.class), resourcesDir.toString());
    }

    private static void checkPaths(List<Object> l, String pathPrefix) {
        checkPaths(l, pathPrefix, false);
    }

    @SuppressWarnings("unchecked")
    private static void checkPaths(List<Object> l, String pathPrefix, boolean checkHome) {
        // python.PythonHome
        // TODO how to check python.PythonHome?
        // /org.graalvm.python.vfs/home has to be in fileslist.txt
        // - if it is in fileslist.txt, than it also has to contain the whole stdlib,
        // or other tests will fail when trying to load python
        // - the pythonHome value is implicitly covered in maven and gradle plugin tests
        // if (checkHome) {
        // assertTrue(((String) l.get(0)).contains(pathPrefix + File.separator + "home" +
        // File.separator +
        // "lib-graalpython"));
        // assertTrue(((String) l.get(0)).contains(pathPrefix + File.separator + "home" +
        // File.separator +
        // "lib-python" + File.separator + "3"));
        // }

        // option python.PythonPath
        assertTrue(((List<Object>) l.get(1)).contains(pathPrefix + File.separator + "src"));
        // option python.Executable
        assertEquals(l.get(2), pathPrefix + (IS_WINDOWS ? "\\venv\\Scripts\\python.exe" : "/venv/bin/python"));
    }

    private static Builder addTestOptions(Builder builder) {
        return builder.option("engine.WarnInterpreterOnly", "false");
    }

}
