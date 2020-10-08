/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.runtime;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.NotImplementedError;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.ProcessProperties;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

/**
 * Implementation that emulates as much as possible using the Truffle API.
 */
@ExportLibrary(PosixSupportLibrary.class)
public final class EmulatedPosixSupport {
    @ExportMessage
    @ImportStatic(ImageInfo.class)
    public static class Getpid {
        @Specialization(guards = "inImageRuntimeCode()")
        static long inNativeImage(EmulatedPosixSupport receiver) {
            return ProcessProperties.getProcessID();
        }

        @Specialization(guards = "!inImageRuntimeCode()", rewriteOn = Exception.class)
        static long usingProc(EmulatedPosixSupport receiver,
                        @CachedContext(PythonLanguage.class) ContextReference<PythonContext> ctxRef) throws Exception {
            TruffleFile statFile = ctxRef.get().getPublicTruffleFileRelaxed("/proc/self/stat");
            return Long.parseLong(new String(statFile.readAllBytes()).trim().split(" ")[0]);
        }

        @Specialization(guards = "!inImageRuntimeCode()", replaces = "usingProc")
        static long usingMXBean(EmulatedPosixSupport receiver,
                        @CachedContext(PythonLanguage.class) ContextReference<PythonContext> ctxRef) {
            String info = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
            return Long.parseLong(info.split("@")[0]);
        }
    }

    @ExportMessage
    public long umask(long umask,
                    @Cached PRaiseNode raiseNode) { // TODO get the raise node only if actually
                                                    // needed like getRaiseNode() in
                                                    // PythonBuiltinBaseNode / specialize on the
                                                    // value of umask
        if (umask == 0022) {
            return 0022;
        }
        if (umask == 0) {
            // TODO: change me, this does not really set the umask, workaround needed for pip
            // it returns the previous mask (which in our case is always 0022)
            return 0022;
        } else {
            throw raiseNode.raise(NotImplementedError, "setting the umask to anything other than the default");
        }
    }

    @ExportMessage
    public int open(String pathname, int flags) {
        throw CompilerDirectives.shouldNotReachHere("Not implemented");
    }

    @ExportMessage
    public int close(int fd) {
        throw CompilerDirectives.shouldNotReachHere("Not implemented");
    }

    @ExportMessage
    public long read(int fd, byte[] buf) {
        throw CompilerDirectives.shouldNotReachHere("Not implemented");
    }

}
