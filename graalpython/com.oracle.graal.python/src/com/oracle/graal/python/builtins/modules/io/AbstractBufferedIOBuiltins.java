/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.io;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PBufferedWriter;
import static com.oracle.graal.python.nodes.ErrorMessages.BUF_SIZE_POS;
import static com.oracle.graal.python.nodes.ErrorMessages.IO_STREAM_DETACHED;
import static com.oracle.graal.python.nodes.ErrorMessages.IO_UNINIT;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

abstract class AbstractBufferedIOBuiltins extends PythonBuiltins {

    protected static final int DEFAULT_BUFFER_SIZE = IOModuleBuiltins.DEFAULT_BUFFER_SIZE;

    protected static final String DETACH = "detach";
    protected static final String FLUSH = "flush";
    protected static final String CLOSE = "close";
    protected static final String SEEKABLE = "seekable";
    protected static final String READABLE = "readable";
    protected static final String WRITABLE = "writable";
    protected static final String FILENO = "fileno";
    protected static final String ISATTY = "isatty";
    protected static final String _DEALLOC_WARN = "_dealloc_warn";

    protected static final String READ = "read";
    protected static final String PEEK = "peek";
    protected static final String READ1 = "read1";
    protected static final String READINTO = "readinto";
    protected static final String READINTO1 = "readinto1";
    protected static final String READLINE = "readline";
    protected static final String READLINES = "readlines";
    protected static final String WRITELINES = "writelines";
    protected static final String WRITE = "write";
    protected static final String SEEK = "seek";
    protected static final String TELL = "tell";
    protected static final String TRUNCATE = "truncate";

    protected static final String RAW = "raw";
    protected static final String _FINALIZING = "_finalizing";

    protected static final String CLOSED = "closed";
    protected static final String NAME = "name";
    protected static final String MODE = "mode";

    public abstract static class BaseInitNode extends PythonTernaryClinicBuiltinNode {

        @Child BufferedIONodes.RawTellNode rawTellNode = BufferedIONodesFactory.RawTellNodeGen.create(true);

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            throw CompilerDirectives.shouldNotReachHere("abstract method");
        }

        protected boolean isFileIO(PBuffered self, Object raw,
                        PythonObjectLibrary libSelf,
                        PythonObjectLibrary libRaw) {
            return raw instanceof PFileIO &&
                            libSelf.getLazyPythonClass(self) == PBufferedWriter &&
                            libRaw.getLazyPythonClass(raw) == PythonBuiltinClassType.PFileIO;
        }

        protected void bufferedInit(VirtualFrame frame, PBuffered self, int bufferSize) {
            self.initBuffer(bufferSize);
            self.setLock(factory().createRLock());
            self.setOwner(0);
            int n;
            for (n = bufferSize - 1; (n & 1) != 0; n >>= 1) {
            }
            int mask = n == 0 ? bufferSize - 1 : 0;
            self.setBufferMask(mask);
            rawTellNode.execute(frame, self);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "bufferSize <= 0")
        public PNone bufferSizeError(VirtualFrame frame, PBuffered self, Object raw, int bufferSize) {
            throw raise(ValueError, BUF_SIZE_POS);
        }
    }

    abstract static class PythonBinaryWithInitErrorClinicBuiltinNode extends PythonBinaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            throw CompilerDirectives.shouldNotReachHere("abstract");
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!self.isOK()")
        Object initError(VirtualFrame frame, PBuffered self, Object o) {
            if (self.isDetached()) {
                throw raise(ValueError, IO_STREAM_DETACHED);
            } else {
                throw raise(ValueError, IO_UNINIT);
            }
        }
    }

    abstract static class PythonBinaryWithInitErrorBuiltinNode extends PythonBinaryBuiltinNode {

        @SuppressWarnings("unused")
        @Specialization(guards = "!self.isOK()")
        Object initError(VirtualFrame frame, PBuffered self, Object buffer) {
            if (self.isDetached()) {
                throw raise(ValueError, IO_STREAM_DETACHED);
            } else {
                throw raise(ValueError, IO_UNINIT);
            }
        }
    }

    abstract static class PythonUnaryWithInitErrorBuiltinNode extends PythonUnaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization(guards = "!self.isOK()")
        Object initError(PBuffered self) {
            if (self.isDetached()) {
                throw raise(ValueError, IO_STREAM_DETACHED);
            } else {
                throw raise(ValueError, IO_UNINIT);
            }
        }
    }

}
