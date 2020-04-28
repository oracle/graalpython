/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.test.module;

import static com.oracle.graal.python.test.PythonTests.assertPrints;
import static com.oracle.graal.python.test.PythonTests.assertLastLineError;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static com.oracle.graal.python.test.PythonTests.assertLastLineErrorContains;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class PosixTest {
    private Path tmpfile;

    @Before
    public void setup() throws IOException {
        tmpfile = Files.createTempFile("graalpython", "test");
    }

    @After
    public void teardown() throws IOException {
        Files.deleteIfExists(tmpfile);
    }

    String open(String arg2) {
        return "import posix\n" +
                        "fd = posix.open('" + tmpfile.toString() + "', " + arg2 + ")\n";
    }

    @Test
    public void stat() {
        String source = "import posix\n" +
                        "result = posix.stat('/')\n" +
                        "def isdir(stat_result):\n" +
                        "    return (stat_result.st_mode & 0xf000) == 0x4000\n" +
                        "print(isdir(result))\n";
        assertPrints("True\n", source);
    }

    @Test
    public void open() {
        assertPrints("True\n", open("0") + "print(fd > 2)");
    }

    @Test
    public void openFail() {
        // TODO this should be checked for FileNotFoundError, but now randomly fails
        // because sometimes is OSError
        assertLastLineErrorContains("No such file or directory",
                        "import posix; print(posix.open('prettysurethisisnthere', 0) > 2)");
    }

    @Test
    public void fstatFile() {
        assertPrints("True\n", "" +
                        open("0") +
                        "print((posix.fstat(fd).st_mode & 0xf000) == 0x8000)\n");
    }

    @Test
    public void fstatDir() throws IOException {
        Path tmp = Files.createTempDirectory("graalpython");
        assertPrints("True\n", "" +
                        "import posix\n" +
                        "fd = posix.open('" +
                        tmp.toString() +
                        "', 0)\n" +
                        "print((posix.fstat(fd).st_mode & 0xf000) == 0x4000)\n");
    }

    @Test
    public void openCreat() throws IOException {
        try {
            Files.delete(tmpfile);
            assertTrue(Files.notExists(tmpfile));
            assertPrints("", "" +
                            "import posix\n" +
                            "posix.open('" +
                            tmpfile.toString() +
                            "', posix.O_CREAT)");
            assertTrue(Files.exists(tmpfile));
        } finally {
            Files.deleteIfExists(tmpfile);
        }
    }

    @Test
    public void openTrunc() throws IOException {
        Files.write(tmpfile, "hello".getBytes());
        assertPrints("", open("posix.O_TRUNC"));
        assertTrue(Files.readAllBytes(tmpfile).length == 0);
    }

    @Test
    public void read() throws IOException {
        Files.write(tmpfile, "hello".getBytes());
        assertPrints("b'hello'\n", open("0") +
                        "print(posix.read(fd, 5))");
    }

    @Test
    public void lseek() throws IOException {
        Files.write(tmpfile, "hello".getBytes());
        assertPrints("b'llo'\n", open("0") +
                        "posix.lseek(fd, 2, posix.SEEK_SET)\n" +
                        "print(posix.read(fd, 3))");
    }

    @Test
    public void write() throws IOException {
        assertPrints("", open("posix.O_RDWR") +
                        "posix.write(fd, 'hello')");
        assertTrue(new String(Files.readAllBytes(tmpfile)).equals("hello"));
    }

    @Test
    public void close() throws IOException {
        assertLastLineErrorContains("OSError",
                        open("posix.O_RDWR") +
                                        "posix.write(fd, 'hello')\n" +
                                        "posix.close(fd)\n" +
                                        "posix.write(fd, 'world')");
        assertTrue(new String(Files.readAllBytes(tmpfile)).equals("hello"));
    }

    @Test
    public void stdout() {
        assertPrints("hello\n", "import sys; sys.stdout.write('hello\\n')");
    }

    @Test
    public void stderr() {
        assertLastLineError("error\n", "import sys; sys.stderr.write('error\\n')");
    }

    @Test
    public void printToStdout() {
        assertPrints("1-2...", "import sys; print('1', '2', sep='-', file=sys.stdout, end='...', flush=True)");
    }

    @Test
    public void printToStderr() {
        assertLastLineError("1-2...", "import sys; print('1', '2', sep='-', file=sys.stderr, end='...', flush=True)");
    }

    @Test
    public void printToFile() throws IOException {
        assertPrints("", open("posix.O_CREAT") +
                        "import _io\n" +
                        "f = _io.FileIO(fd, mode='w')\n" +
                        "print('hello', file=f)");
        assertEquals("hello\n", new String(Files.readAllBytes(tmpfile)));
    }

    @Test
    public void readlink() throws IOException {
        Path realPath = tmpfile.toRealPath();
        Path symlinkPath = realPath.getParent().resolve(tmpfile.getFileName() + "__symlink");
        try {
            Path symlink = Files.createSymbolicLink(symlinkPath, Paths.get(tmpfile.toUri()));
            assertPrints(realPath.toString() + "\n", "import posix\n" +
                            "print(posix.readlink('" + symlink.toString() + "'))\n");
        } finally {
            Files.deleteIfExists(symlinkPath);
        }
    }

    @Test
    public void sysExcInfo0() {
        assertPrints("42\n", "import sys\n" +
                        "sys.exc_info = lambda: [42, 24, 4224]\n" +
                        "try:\n" +
                        "  raise ValueError\n" +
                        "except:\n" +
                        "  print(sys.exc_info()[0])\n");
    }

}
