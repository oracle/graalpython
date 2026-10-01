/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PFileIO;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PRawIOBase;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PWindowsConsoleIO;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_CLOSE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_CLOSED;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_CLOSEFD;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_FILENO;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_MODE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_READABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_WRITABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_CLOSE;
import static com.oracle.graal.python.nodes.ErrorMessages.IO_CLOSED;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.List;

import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.PosixModuleBuiltins;
import com.oracle.graal.python.builtins.modules.WarningsModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.lib.PyErrChainExceptions;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = {PFileIO, PWindowsConsoleIO})
public final class CommonFileIOBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return CommonFileIOBuiltinsFactory.getFactories();
    }

    @Builtin(name = J_CLOSE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CloseNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "!self.isCloseFD()")
        static Object simple(VirtualFrame frame, PFileIO self,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached PyObjectCallMethodObjArgs callClose) {
            try {
                callClose.execute(frame, inliningTarget, PythonContext.get(inliningTarget).lookupType(PRawIOBase), T_CLOSE, self);
            } catch (PException e) {
                self.setClosed();
                throw e;
            }
            self.setClosed();
            return PNone.NONE;
        }

        @Specialization(guards = {"self.isCloseFD()", "!self.isFinalizing()"})
        static Object common(VirtualFrame frame, PFileIO self,
                        @Bind Node inliningTarget,
                        @Shared("c") @Cached PosixModuleBuiltins.CloseNode posixClose,
                        @Shared("l") @Cached PyObjectCallMethodObjArgs callSuperClose,
                        @Shared @Cached PyErrChainExceptions chainExceptions) {
            try {
                callSuperClose.execute(frame, inliningTarget, PythonContext.get(inliningTarget).lookupType(PRawIOBase), T_CLOSE, self);
            } catch (PException e) {
                try {
                    FileIOBuiltins.internalClose(frame, self, posixClose);
                } catch (PException ee) {
                    throw chainExceptions.execute(inliningTarget, ee, e);
                }
                throw e;
            }
            FileIOBuiltins.internalClose(frame, self, posixClose);
            return PNone.NONE;
        }

        @Specialization(guards = {"self.isCloseFD()", "self.isFinalizing()"})
        static Object slow(VirtualFrame frame, PFileIO self,
                        @Bind Node inliningTarget,
                        @Shared("c") @Cached PosixModuleBuiltins.CloseNode posixClose,
                        @Cached WarningsModuleBuiltins.WarnNode warnNode,
                        @Shared("l") @Cached PyObjectCallMethodObjArgs callSuperClose,
                        @Shared @Cached PyErrChainExceptions chainExceptions) {
            PException rawIOException = null;
            PythonContext context = PythonContext.get(inliningTarget);
            try {
                callSuperClose.execute(frame, inliningTarget, context.lookupType(PRawIOBase), T_CLOSE, self);
            } catch (PException e) {
                rawIOException = e;
            }
            FileIOBuiltins.deallocWarn(frame, self, warnNode);
            try {
                FileIOBuiltins.internalClose(frame, self, posixClose);
            } catch (PException ee) {
                if (rawIOException != null) {
                    throw chainExceptions.execute(inliningTarget, ee, rawIOException);
                } else {
                    throw ee;
                }
            }
            if (rawIOException != null) {
                throw rawIOException;
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = J_READABLE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReadableNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "!self.isClosed()")
        static Object readable(PFileIO self) {
            return self.isReadable();
        }

        @Specialization(guards = "self.isClosed()")
        static Object closedError(@SuppressWarnings("unused") PFileIO self,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, ValueError, IO_CLOSED);
        }
    }

    @Builtin(name = J_WRITABLE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class WritableNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "!self.isClosed()")
        static Object writable(PFileIO self) {
            return self.isWritable();
        }

        @Specialization(guards = "self.isClosed()")
        static Object closedError(@SuppressWarnings("unused") PFileIO self,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, ValueError, IO_CLOSED);
        }
    }

    @Builtin(name = J_FILENO, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class FilenoNode extends PythonBuiltinNode {
        @Specialization(guards = "!self.isClosed()")
        static Object fileno(PFileIO self) {
            return self.getFD();
        }

        @Specialization(guards = "self.isClosed()")
        static Object closedError(@SuppressWarnings("unused") PFileIO self,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, ValueError, IO_CLOSED);
        }
    }

    @Builtin(name = J_CLOSED, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class ClosedNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doit(PFileIO self) {
            return self.getFD() < 0;
        }
    }

    @Builtin(name = J_CLOSEFD, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class CloseFDNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doit(PFileIO self) {
            return self.isCloseFD();
        }
    }

    @Builtin(name = J_MODE, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class ModeNode extends PythonUnaryBuiltinNode {
        private static final TruffleString T_XB = tsLiteral("xb");
        private static final TruffleString T_XBP = tsLiteral("xb+");
        private static final TruffleString T_AB = tsLiteral("ab");
        private static final TruffleString T_ABP = tsLiteral("ab+");
        private static final TruffleString T_RB = tsLiteral("rb");
        private static final TruffleString T_RBP = tsLiteral("rb+");
        private static final TruffleString T_WB = tsLiteral("wb");

        @Specialization
        static TruffleString mode(PFileIO self) {
            if (self.isCreated()) {
                return self.isReadable() ? T_XBP : T_XB;
            }
            if (self.isAppending()) {
                return self.isReadable() ? T_ABP : T_AB;
            } else if (self.isReadable()) {
                return self.isWritable() ? T_RBP : T_RB;
            }
            return T_WB;
        }
    }
}
