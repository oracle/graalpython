/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins;

import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import java.util.Locale;

import org.graalvm.nativeimage.ImageInfo;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.strings.TruffleString;

public enum PythonOS {
    PLATFORM_LINUX("linux", "Linux"),
    PLATFORM_DARWIN("darwin", "Darwin"),
    PLATFORM_WIN32("win32", "Windows"),
    PLATFORM_ANY(null, null);

    public static final String SUPPORTED_PLATFORMS = "linux/amd64, linux/aarch64, macos/amd64, macos/aarch64, and windows/amd64";

    private final TruffleString name;
    private final TruffleString uname;

    PythonOS(String name, String uname) {
        this.name = toTruffleStringUncached(name);
        this.uname = toTruffleStringUncached(uname);
    }

    public TruffleString getName() {
        return name;
    }

    public TruffleString getUname() {
        return uname;
    }

    private static final PythonOS current;

    static {
        String property = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH);
        if (property.contains("linux")) {
            current = PLATFORM_LINUX;
        } else if (property.contains("mac") || property.contains("darwin")) {
            current = PLATFORM_DARWIN;
        } else if (property.contains("windows")) {
            current = PLATFORM_WIN32;
        } else {
            current = PLATFORM_ANY;
        }
    }

    public static PythonOS getPythonOS() {
        if (current == PLATFORM_ANY) {
            if (ImageInfo.inImageBuildtimeCode()) {
                throw new RuntimeException("Native images with GraalPy are only supported on " + SUPPORTED_PLATFORMS + ".");
            }
            String emulated = PythonLanguage.get(null).getEngineOption(PythonOptions.UnsupportedPlatformEmulates);
            if (!emulated.isEmpty()) {
                switch (emulated) {
                    case "linux":
                        return PLATFORM_LINUX;
                    case "macos":
                        return PLATFORM_DARWIN;
                    case "windows":
                        return PLATFORM_WIN32;
                    default:
                        throw new UnsupportedPlatform("UnsupportedPlatformEmulates must be exactly one of \"linux\", \"macos\", or \"windows\"");
                }
            } else {
                throw new UnsupportedPlatform("This platform is not currently supported. " +
                                "Currently supported platforms are " + SUPPORTED_PLATFORMS + ". " +
                                "If you are running on one of these platforms and are receiving this error, that indicates a bug in this build of GraalPy. " +
                                "If you are running on a different platform and accept that any functionality that interacts with the system may be " +
                                "incorrect and Python native extensions will not work, you can specify the system property \"UnsupportedPlatformEmulates\" " +
                                "with a value of either \"linux\", \"macos\", or \"windows\" to continue further and have GraalPy behave as if it were running on " +
                                "the OS specified. Loading native libraries will not work and must be disabled using the context options. " +
                                "See https://www.graalvm.org/python/docs/ for details on GraalPy modules with both native and Java backends, " +
                                "and https://www.graalvm.org/truffle/javadoc/org/graalvm/polyglot/Context.Builder.html to learn about disallowing native access.");
            }
        }
        return current;
    }

    public static void throwIfUnsupported(String msg) {
        if (isUnsupported()) {
            throw new UnsupportedPlatform(msg +
                            "\nThis point was reached as earlier platform checks were overridden using the system property UnsupportedPlatformEmulates");
        }
    }

    public static boolean isUnsupported() {
        return current == PLATFORM_ANY;
    }

    public static final class UnsupportedPlatform extends AbstractTruffleException {
        public UnsupportedPlatform(String msg) {
            super(msg);
        }

        private static final long serialVersionUID = 1L;
    }
}
