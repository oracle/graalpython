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
package com.oracle.graal.python.resources;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.InternalResource;

/**
 * This code needs to be kept in sync somehow with the suite.py code. In particular, the layouts
 * produced by the unpacking logic in {@link #unpackFiles} should produce the same layout as the
 * GRAALPYTHON_GRAALVM_SUPPORT distribution in the suite.py.
 */
@InternalResource.Id(value = "python-home", componentId = "python", optional = true)
public final class PythonResource implements InternalResource {
    private static final int PYTHON_MAJOR;
    private static final int PYTHON_MINOR;
    private static final int GRAALVM_MAJOR;
    private static final int GRAALVM_MINOR;

    /**
     * The version generated at build time is stored in an ASCII-compatible way. Add build time, we
     * added the ordinal value of some base character (in this case {@code '!'}) to ensure that we
     * have a printable character.
     */
    private static final int VERSION_BASE = '!';

    static {
        // See static ctor of PythonLanguage
        try (InputStream is = PythonResource.class.getResourceAsStream("/graalpy_versions")) {
            PYTHON_MAJOR = is.read() - VERSION_BASE;
            PYTHON_MINOR = is.read() - VERSION_BASE;
            is.read(); // skip python micro version
            GRAALVM_MAJOR = is.read() - VERSION_BASE;
            GRAALVM_MINOR = is.read() - VERSION_BASE;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static final Path BASE_PATH = Path.of("META-INF", "resources");
    private static final String LIBGRAALPY = "libgraalpy";
    private static final String LIBPYTHON = "libpython";
    private static final String INCLUDE = "include";
    private static final String LIBGRAALPY_FILES = LIBGRAALPY + ".files";
    private static final String LIBPYTHON_FILES = LIBPYTHON + ".files";
    private static final String INCLUDE_FILES = "include.files";
    private static final String NI_FILES = "ni.files";
    private static final String NATIVE_FILES = "native.files";
    private static final Path LIBGRAALPY_SHA256 = Path.of(LIBGRAALPY + ".sha256");
    private static final Path LIBPYTHON_SHA256 = Path.of(LIBPYTHON + ".sha256");
    private static final Path INCLUDE_SHA256 = Path.of("include.sha256");
    private static final Path NI_SHA256 = Path.of("ni.sha256");
    private static final Path NATIVE_SHA256 = Path.of("native.sha256");

    @Override
    public void unpackFiles(Env env, Path targetDirectory) throws IOException {
        OS os = env.getOS();
        Path osArch = Path.of(os.toString()).resolve(env.getCPUArchitecture().toString());
        ResourcesFilter filter = new ResourcesFilter();
        if (os.equals(OS.WINDOWS)) {
            env.unpackResourceFiles(BASE_PATH.resolve(LIBPYTHON_FILES), targetDirectory.resolve("Lib"), BASE_PATH.resolve(LIBPYTHON), filter);
            env.unpackResourceFiles(BASE_PATH.resolve(LIBGRAALPY_FILES), targetDirectory.resolve("lib-graalpython"), BASE_PATH.resolve(LIBGRAALPY), filter);
            env.unpackResourceFiles(BASE_PATH.resolve(INCLUDE_FILES), targetDirectory.resolve("Include"), BASE_PATH.resolve(INCLUDE), filter);
        } else {
            String pythonMajMin = "python" + PYTHON_MAJOR + "." + PYTHON_MINOR;
            env.unpackResourceFiles(BASE_PATH.resolve(LIBPYTHON_FILES), targetDirectory.resolve("lib").resolve(pythonMajMin), BASE_PATH.resolve(LIBPYTHON), filter);
            env.unpackResourceFiles(BASE_PATH.resolve(LIBGRAALPY_FILES), targetDirectory.resolve("lib").resolve("graalpy" + GRAALVM_MAJOR + "." + GRAALVM_MINOR), BASE_PATH.resolve(LIBGRAALPY),
                            filter);
            env.unpackResourceFiles(BASE_PATH.resolve(INCLUDE_FILES), targetDirectory.resolve("include").resolve(pythonMajMin), BASE_PATH.resolve(INCLUDE), filter);
        }
        // ni files are in the same place on all platforms
        env.unpackResourceFiles(BASE_PATH.resolve(NI_FILES), targetDirectory, BASE_PATH, filter);
        // native files already have the correct structure
        env.unpackResourceFiles(BASE_PATH.resolve(osArch).resolve(NATIVE_FILES), targetDirectory, BASE_PATH.resolve(osArch), filter);

        if (filter.log) {
            System.out.println("unpacked python resources:");
            System.out.println("include pattern: '" + filter.include + "'");
            System.out.println("exclude pattern: '" + filter.exclude + "'");
            listFiles("included files:", filter.included);
            listFiles("excluded files:", filter.excluded);
        }
    }

    private static void listFiles(String header, List<String> l) {
        if (!l.isEmpty()) {
            Collections.sort(l);
            System.out.println(header);
            for (String s : l) {
                System.out.println("\t" + s);
            }
        }
    }

    private static class ResourcesFilter implements Predicate<Path> {

        private final boolean log;
        private final Pattern includePattern;
        private final Pattern excludePattern;
        private final List<String> excluded;
        private final String exclude;
        private final String include;
        private final ArrayList<String> included;

        private ResourcesFilter() {
            include = getProperty("org.graalvm.python.resources.include");
            exclude = getProperty("org.graalvm.python.resources.exclude");
            log = Boolean.parseBoolean(getProperty("org.graalvm.python.resources.log"));
            includePattern = include != null ? Pattern.compile(include) : null;
            excludePattern = exclude != null ? Pattern.compile(exclude) : null;
            included = log ? new ArrayList<>() : null;
            excluded = log ? new ArrayList<>() : null;
        }

        @Override
        public boolean test(Path path) {
            String absolutePath = path.toAbsolutePath().toString();
            if (File.separator.equals("\\")) {
                absolutePath = absolutePath.replaceAll("\\\\", "/");
            }
            if ((includePattern != null && !includePattern.matcher(absolutePath).matches()) ||
                            (excludePattern != null && excludePattern.matcher(absolutePath).matches())) {
                if (log) {
                    excluded.add(absolutePath);
                }
                return false;
            } else {
                if (log) {
                    included.add(absolutePath);
                }
                return true;
            }
        }

        private static String getProperty(String prop) {
            String s = System.getProperty(prop);
            if (s != null) {
                if (s.isEmpty()) {
                    s = null;
                } else if (s.startsWith("\"") && s.endsWith("\"")) {
                    // native-gradle-plugin sends system properties wrapped in "
                    s = s.substring(1, s.length() - 1);
                }
            }
            return s;
        }
    }

    @Override
    public String versionHash(Env env) {
        StringBuilder sb = new StringBuilder();
        for (var s : List.of(LIBGRAALPY_SHA256, LIBPYTHON_SHA256, NI_SHA256, INCLUDE_SHA256, Path.of(env.getOS().toString()).resolve(env.getCPUArchitecture().toString()).resolve(NATIVE_SHA256))) {
            try {
                sb.append(env.readResourceLines(BASE_PATH.resolve(s)).get(0).substring(0, 8));
            } catch (IOException | IndexOutOfBoundsException | InvalidPathException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
        return sb.toString();
    }
}
