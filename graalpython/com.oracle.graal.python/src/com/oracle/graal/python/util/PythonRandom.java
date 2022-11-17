/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.util;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import com.oracle.graal.python.builtins.PythonOS;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public final class PythonRandom {
    @CompilationFinal private static PythonRandom INSTANCE;
    private final Object secureRandom;

    private PythonRandom(Object secureRandom) {
        this.secureRandom = secureRandom;
        assert PythonOptions.JAVA_SECURE_RANDOM == null ? (secureRandom instanceof SecureRandom) : (secureRandom instanceof ByteChannel);
    }

    @TruffleBoundary
    public int nextInt() {
        if (PythonOptions.JAVA_SECURE_RANDOM == null) {
            return ((SecureRandom) secureRandom).nextInt();
        } else {
            ByteChannel randomSource = (ByteChannel) secureRandom;
            ByteBuffer buf = ByteBuffer.allocate(4);
            try {
                randomSource.read(buf);
            } catch (IOException e) {
                throw CompilerDirectives.shouldNotReachHere("Class provided via python.java.random is not expected to ever throw on #read", e);
            }
            buf.rewind();
            return buf.getInt();
        }
    }

    @TruffleBoundary
    public static PythonRandom getInstance() {
        if (INSTANCE == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            if (PythonOptions.JAVA_SECURE_RANDOM == null) {
                SecureRandom secureRandom;
                try {
                    secureRandom = SecureRandom.getInstance("NativePRNGNonBlocking");
                } catch (NoSuchAlgorithmException e) {
                    if (PythonOS.getPythonOS() == PythonOS.PLATFORM_WIN32) {
                        try {
                            secureRandom = SecureRandom.getInstanceStrong();
                        } catch (NoSuchAlgorithmException e2) {
                            throw new RuntimeException("Unable to obtain entropy source for random number generation (NativePRNGNonBlocking)", e2);
                        }
                    } else {
                        throw new RuntimeException("Unable to obtain entropy source for random number generation (NativePRNGNonBlocking)", e);
                    }
                }
                INSTANCE = new PythonRandom(secureRandom);
            } else {
                try {
                    INSTANCE = new PythonRandom(Class.forName(PythonOptions.JAVA_SECURE_RANDOM).getConstructor().newInstance());
                } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException
                                | ClassNotFoundException e) {
                    throw CompilerDirectives.shouldNotReachHere("Class provided via python.java.random=" + PythonOptions.JAVA_SECURE_RANDOM + " must be instantiable with a default constructor", e);
                }
            }
        }
        return INSTANCE;
    }

    @TruffleBoundary
    public void nextBytes(byte[] bytes) {
        if (PythonOptions.JAVA_SECURE_RANDOM == null) {
            ((SecureRandom) secureRandom).nextBytes(bytes);
        } else {
            ByteChannel randomSource = (ByteChannel) secureRandom;
            ByteBuffer buf = ByteBuffer.wrap(bytes);
            try {
                randomSource.read(buf);
            } catch (IOException e) {
                throw CompilerDirectives.shouldNotReachHere("Class provided via python.java.random is not expected to ever throw on #read", e);
            }
        }
    }
}
