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
package org.graalvm.python.embedding.utils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VFSUtils {
    public static void generateVFSFilesList(Path vfs) throws IOException {
        Path filesList = vfs.resolve("fileslist.txt");
        if (!Files.isDirectory(vfs)) {
            throw new IOException(String.format("'%s' has to exist and be a directory.\n", vfs.toString()));
        }
        var ret = new HashSet<String>();
        String rootPath = makeDirPath(vfs.toAbsolutePath());
        int rootEndIdx = rootPath.lastIndexOf(File.separator, rootPath.lastIndexOf(File.separator) - 1);
        ret.add(rootPath.substring(rootEndIdx));
        try (var s = Files.walk(vfs)) {
            s.forEach(p -> {
                if (Files.isDirectory(p)) {
                    String dirPath = makeDirPath(p.toAbsolutePath());
                    ret.add(dirPath.substring(rootEndIdx));
                } else if (Files.isRegularFile(p)) {
                    ret.add(p.toAbsolutePath().toString().substring(rootEndIdx));
                }
            });
        }
        String[] a = ret.toArray(new String[ret.size()]);
        Arrays.sort(a);
        try (var wr = new FileWriter(filesList.toFile())) {
            for (String f : a) {
                if (f.charAt(0) == '\\') {
                    f = f.replace("\\", "/");
                }
                wr.write(f);
                wr.write("\n");
            }
        }
    }

    private static String makeDirPath(Path p) {
        String ret = p.toString();
        if (!ret.endsWith(File.separator)) {
            ret += File.separator;
        }
        return ret;
    }

    public static void copyGraalPyHome(Set<String> classpath, Path home, Collection<String> pythonHomeIncludes, Collection<String> pythonHomeExcludes, SubprocessLog log)
                    throws IOException, InterruptedException {
        log.log(String.format("Copying std lib to '%s'\n", home));
        // get stdlib and core home
        String stdlibHome = null;
        String coreHome = null;
        String pathsOutputPrefix = "<=outputpaths=>";

        CollectOutputLog outputLog = new CollectOutputLog();
        GraalPyRunner.run(classpath, outputLog, new String[]{"-c", "print('" + pathsOutputPrefix + "', __graalpython__.get_python_home_paths(), sep='')"});
        for (String l : outputLog.output) {
            if (l.startsWith(pathsOutputPrefix)) {
                String[] s = l.substring(pathsOutputPrefix.length()).split(File.pathSeparator);
                stdlibHome = s[0];
                coreHome = s[1];
            }
        }
        assert stdlibHome != null;
        assert coreHome != null;

        // copy core home
        Path target = home.resolve("lib-graalpython");
        if (!Files.exists(target)) {
            Files.createDirectories(target);
        }
        Path source = Paths.get(coreHome);
        Predicate<Path> filter = (f) -> {
            if (Files.isDirectory(f)) {
                if (f.getFileName().toString().equals("__pycache__") || f.getFileName().toString().equals("standalone")) {
                    return true;
                }
            } else {
                if (f.getFileName().endsWith(".py") || f.getFileName().endsWith(".txt") ||
                                f.getFileName().endsWith(".c") || f.getFileName().endsWith(".md") ||
                                f.getFileName().endsWith(".patch") || f.getFileName().endsWith(".toml") ||
                                f.getFileName().endsWith("PKG-INFO")) {
                    return true;
                }
                if (!isIncluded(f.toAbsolutePath().toString(), pythonHomeIncludes)) {
                    return true;
                }
            }
            return isExcluded(f.toAbsolutePath().toString(), pythonHomeExcludes);
        };
        copyFolder(source, source, target, filter);

        // copy stdlib home
        target = home.resolve("lib-python").resolve("3");
        if (!Files.exists(target)) {
            Files.createDirectories(target);
        }
        source = Paths.get(stdlibHome);
        filter = (f) -> {
            if (Files.isDirectory(f)) {
                if (f.getFileName().toString().equals("idlelib") || f.getFileName().toString().equals("ensurepip") ||
                                f.getFileName().toString().equals("tkinter") || f.getFileName().toString().equals("turtledemo") ||
                                f.getFileName().toString().equals("__pycache__")) {
                    return true;
                }
            } else {
                // libpythonvm.* in same folder as stdlib is a windows issue only
                if (f.getFileName().toString().equals("libpythonvm.dll")) {
                    return true;
                }
                if (!isIncluded(f.toAbsolutePath().toString(), pythonHomeIncludes)) {
                    return true;
                }
            }
            return isExcluded(f.toAbsolutePath().toString(), pythonHomeExcludes);
        };
        copyFolder(source, source, target, filter);
    }

    private static boolean isIncluded(String filePath, Collection<String> includes) {
        if (includes == null || includes.isEmpty()) {
            return true;
        }
        return pathMatches(filePath, includes);
    }

    private static boolean isExcluded(String filePath, Collection<String> excludes) {
        if (excludes == null || excludes.isEmpty()) {
            return false;
        }
        return pathMatches(filePath, excludes);
    }

    private static boolean pathMatches(String filePath, Collection<String> includes) {
        String path = filePath;
        if (File.separator.equals("\\")) {
            path = path.replaceAll("\\\\", "/");
        }
        for (String i : includes) {
            Pattern pattern = Pattern.compile(i);
            Matcher matcher = pattern.matcher(path);
            if (matcher.matches()) {
                return true;
            }
        }
        return false;
    }

    private static void copyFolder(Path sourceRoot, Path file, Path targetRoot, Predicate<Path> filter) throws IOException {
        Files.walkFileTree(file, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path f, BasicFileAttributes attrs) throws IOException {
                if (filter.test(f)) {
                    return FileVisitResult.CONTINUE;
                }
                if (Files.isDirectory(f)) {
                    copyFolder(sourceRoot, f, targetRoot, filter);
                } else {
                    Path relFile = sourceRoot.relativize(f);
                    Path targetPath = targetRoot.resolve(relFile.toString());
                    Path parent = targetPath.getParent();
                    if (parent == null) {
                        return FileVisitResult.CONTINUE;
                    }
                    if (!Files.exists(parent)) {
                        Files.createDirectories(parent);
                    }
                    if (Files.exists(targetPath)) {
                        Files.delete(targetPath);
                    }
                    Files.copy(f, targetPath);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static class CollectOutputLog implements SubprocessLog {

        private final List<String> output = new ArrayList<>();

        public void subProcessOut(CharSequence var1) {
            output.add(var1.toString());
        }

        public void subProcessErr(CharSequence var1) {
            System.err.println(var1);
        }

        public void log(CharSequence var1) {

        }
    }
}
