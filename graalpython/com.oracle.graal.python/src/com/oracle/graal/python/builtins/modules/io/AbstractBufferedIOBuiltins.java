/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.BlockingIOError;
import static com.oracle.graal.python.builtins.objects.exception.OsErrorBuiltins.OS_ERROR_ATTR_FACTORY;
import static com.oracle.graal.python.nodes.ErrorMessages.BUF_SIZE_POS;
import static com.oracle.graal.python.nodes.ErrorMessages.IO_STREAM_DETACHED;
import static com.oracle.graal.python.nodes.ErrorMessages.IO_UNINIT;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.io.BufferedIONodes.RawTellIgnoreErrorNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.builtins.objects.exception.OsErrorBuiltins;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetClassNode.GetPythonObjectClassNode;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

abstract class AbstractBufferedIOBuiltins extends PythonBuiltins {

    public static final int DEFAULT_BUFFER_SIZE = IOModuleBuiltins.DEFAULT_BUFFER_SIZE;

    @GenerateInline
    @GenerateCached(false)
    public abstract static class BufferedInitNode extends PNodeWithContext {

        public abstract void execute(VirtualFrame frame, Node inliningTarget, PBuffered self, int bufferSize, PythonObjectFactory factory);

        @Specialization(guards = "bufferSize > 0")
        static void bufferedInit(VirtualFrame frame, Node inliningTarget, PBuffered self, int bufferSize, PythonObjectFactory factory,
                        @Cached RawTellIgnoreErrorNode rawTellNode) {
            init(self, bufferSize, factory);
            rawTellNode.execute(frame, inliningTarget, self);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "bufferSize <= 0")
        static void bufferSizeError(PBuffered self, int bufferSize, PythonObjectFactory factory,
                        @Cached(inline = false) PRaiseNode raiseNode) {
            throw raiseNode.raise(ValueError, BUF_SIZE_POS);
        }

        private static void init(PBuffered self, int bufferSize, PythonObjectFactory factory) {
            self.initBuffer(bufferSize);
            self.setLock(factory.createLock());
            self.setOwner(0);
            int n;
            for (n = bufferSize - 1; (n & 1) != 0; n >>= 1) {
            }
            int mask = n == 0 ? bufferSize - 1 : 0;
            self.setBufferMask(mask);
        }

        public static void internalInit(PBuffered self, int bufferSize, PythonObjectFactory factory,
                        Object posixSupport,
                        PosixSupportLibrary posixLib) {
            init(self, bufferSize, factory);
            try {
                FileIOBuiltins.TellNode.internalTell(self.getFileIORaw(), posixSupport, posixLib);
            } catch (PosixException e) {
                // ignore.. it's ok if it's not seekable
            }
        }
    }

    protected static boolean isFileIO(PBuffered self, Object raw, PythonBuiltinClassType type,
                    Node inliningTarget, GetPythonObjectClassNode getSelfClass, GetClassNode getRawClass) {
        return raw instanceof PFileIO &&
                        getSelfClass.execute(inliningTarget, self) == type &&
                        getRawClass.execute(inliningTarget, raw) == PythonBuiltinClassType.PFileIO;
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class InitBufferSizeNode extends Node {
        public abstract int execute(VirtualFrame frame, Node inliningTarget, Object bufferSize);

        @Specialization
        static int doInit(@SuppressWarnings("unused") PNone bufferSize) {
            return DEFAULT_BUFFER_SIZE;
        }

        @Specialization
        static int doInt(int bufferSize) {
            return bufferSize;
        }

        @Fallback
        static int doOther(VirtualFrame frame, Node inliningTarget, Object bufferSizeObj,
                        @Cached PyNumberAsSizeNode asSizeNode) {
            return asSizeNode.executeExact(frame, inliningTarget, bufferSizeObj, ValueError);
        }
    }

    abstract static class PythonBinaryWithInitErrorClinicBuiltinNode extends PythonBinaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            throw CompilerDirectives.shouldNotReachHere("abstract");
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!self.isOK()")
        static Object initError(PBuffered self, Object o,
                        @Cached PRaiseNode raiseNode) {
            if (self.isDetached()) {
                throw raiseNode.raise(ValueError, IO_STREAM_DETACHED);
            } else {
                throw raiseNode.raise(ValueError, IO_UNINIT);
            }
        }
    }

    abstract static class PythonBinaryWithInitErrorBuiltinNode extends PythonBinaryBuiltinNode {

        @SuppressWarnings("unused")
        @Specialization(guards = "!self.isOK()")
        static Object initError(PBuffered self, Object buffer,
                        @Cached PRaiseNode raiseNode) {
            if (self.isDetached()) {
                throw raiseNode.raise(ValueError, IO_STREAM_DETACHED);
            } else {
                throw raiseNode.raise(ValueError, IO_UNINIT);
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

    @GenerateInline(false)
    public abstract static class RaiseBlockingIOError extends Node {
        protected abstract PException execute(Node node, Object errno, TruffleString message, int written);

        public final PException raise(Object errno, TruffleString message, int written) {
            return execute(this, errno, message, written);
        }

        public final PException raiseEWOULDBLOCK(TruffleString message, int written) {
            return raise(OSErrorEnum.EWOULDBLOCK.getNumber(), message, written);
        }

        public final PException raiseEAGAIN(TruffleString message, int written) {
            return raise(OSErrorEnum.EAGAIN.getNumber(), message, written);
        }

        @Specialization
        static PException raise(Node node, Object errno, TruffleString message, int written,
                        @Cached PythonObjectFactory factory) {
            Object[] args = new Object[]{
                            errno,
                            message,
                            written
            };
            PBaseException exception = factory.createBaseException(BlockingIOError, factory.createTuple(args));
            final Object[] attrs = OS_ERROR_ATTR_FACTORY.create();
            attrs[OsErrorBuiltins.IDX_ERRNO] = errno;
            attrs[OsErrorBuiltins.IDX_STRERROR] = message;
            attrs[OsErrorBuiltins.IDX_WRITTEN] = written;
            exception.setExceptionAttributes(attrs);
            return PRaiseNode.raiseExceptionObject(node, exception);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class LazyRaiseBlockingIOError extends Node {
        public final RaiseBlockingIOError get(Node inliningTarget) {
            return execute(inliningTarget);
        }

        protected abstract RaiseBlockingIOError execute(Node inliningTarget);

        @Specialization
        static RaiseBlockingIOError doIt(@Cached(inline = false) RaiseBlockingIOError node) {
            return node;
        }
    }
}
