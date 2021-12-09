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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PBufferedRandom;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PBufferedReader;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PBufferedWriter;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.RuntimeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.SEEK_CUR;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.SEEK_END;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.SEEK_SET;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.rawOffset;
import static com.oracle.graal.python.builtins.modules.io.IONodes.CLOSE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.CLOSED;
import static com.oracle.graal.python.builtins.modules.io.IONodes.DETACH;
import static com.oracle.graal.python.builtins.modules.io.IONodes.FILENO;
import static com.oracle.graal.python.builtins.modules.io.IONodes.FLUSH;
import static com.oracle.graal.python.builtins.modules.io.IONodes.ISATTY;
import static com.oracle.graal.python.builtins.modules.io.IONodes.MODE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.NAME;
import static com.oracle.graal.python.builtins.modules.io.IONodes.RAW;
import static com.oracle.graal.python.builtins.modules.io.IONodes.SEEK;
import static com.oracle.graal.python.builtins.modules.io.IONodes.SEEKABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.TELL;
import static com.oracle.graal.python.builtins.modules.io.IONodes.TRUNCATE;
import static com.oracle.graal.python.builtins.modules.io.IONodes._DEALLOC_WARN;
import static com.oracle.graal.python.builtins.modules.io.IONodes._FINALIZING;
import static com.oracle.graal.python.nodes.ErrorMessages.IO_STREAM_DETACHED;
import static com.oracle.graal.python.nodes.ErrorMessages.IO_UNINIT;
import static com.oracle.graal.python.nodes.ErrorMessages.UNSUPPORTED_WHENCE;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.nodes.statement.ExceptionHandlingStatementNode.chainExceptions;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.IOUnsupportedOperation;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.modules.io.BufferedIONodes.CheckIsClosedNode;
import com.oracle.graal.python.builtins.modules.io.BufferedIONodes.EnterBufferedNode;
import com.oracle.graal.python.builtins.modules.io.BufferedIONodes.FlushAndRewindUnlockedNode;
import com.oracle.graal.python.builtins.modules.io.BufferedIONodes.RawTellNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectReprAsJavaStringNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = {PBufferedReader, PBufferedWriter, PBufferedRandom})
public final class BufferedIOMixinBuiltins extends AbstractBufferedIOBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return BufferedIOMixinBuiltinsFactory.getFactories();
    }

    @Builtin(name = CLOSE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CloseNode extends PythonUnaryWithInitErrorBuiltinNode {

        private static Object close(VirtualFrame frame, PBuffered self,
                        EnterBufferedNode lock,
                        PyObjectCallMethodObjArgs callMethodClose) {
            try {
                lock.enter(self);
                Object res = callMethodClose.execute(frame, self.getRaw(), CLOSE);
                if (self.getBuffer() != null) {
                    self.setBuffer(null);
                }
                return res;
            } finally {
                EnterBufferedNode.leave(self);
            }
        }

        @Specialization(guards = "self.isOK()")
        static Object doit(VirtualFrame frame, PBuffered self,
                        @Cached BufferedIONodes.IsClosedNode isClosedNode,
                        @Cached PyObjectCallMethodObjArgs callMethodFlush,
                        @Cached PyObjectCallMethodObjArgs callMethodClose,
                        @Cached PyObjectCallMethodObjArgs callMethodDeallocWarn,
                        @Cached EnterBufferedNode lock,
                        @Cached ConditionProfile profile) {
            try {
                lock.enter(self);
                if (profile.profile(isClosedNode.execute(frame, self))) {
                    return PNone.NONE;
                }
                if (self.isFinalizing()) {
                    if (self.getRaw() != null) {
                        callMethodDeallocWarn.execute(frame, self.getRaw(), _DEALLOC_WARN, self);
                    }
                }
            } finally {
                EnterBufferedNode.leave(self);
            }
            /* flush() will most probably re-take the lock, so drop it first */
            try {
                callMethodFlush.execute(frame, self, FLUSH);
            } catch (PException e) {
                try {
                    close(frame, self, lock, callMethodClose);
                } catch (PException ee) {
                    chainExceptions(ee.getEscapedException(), e);
                    throw ee.getExceptionForReraise();
                }
                throw e;
            }
            return close(frame, self, lock, callMethodClose);
        }
    }

    @Builtin(name = DETACH, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class DetachNode extends PythonUnaryWithInitErrorBuiltinNode {
        @Specialization(guards = "self.isOK()")
        static Object doit(VirtualFrame frame, PBuffered self,
                        @Cached PyObjectCallMethodObjArgs callMethodFlush) {
            callMethodFlush.execute(frame, self, FLUSH);
            Object raw = self.getRaw();
            self.clearRaw();
            self.setDetached(true);
            self.setOK(false);
            return raw;
        }
    }

    @Builtin(name = SEEKABLE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class SeekableNode extends PythonUnaryWithInitErrorBuiltinNode {
        @Specialization(guards = "self.isOK()")
        static Object doit(VirtualFrame frame, PBuffered self,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            return callMethod.execute(frame, self.getRaw(), SEEKABLE);
        }
    }

    @Builtin(name = FILENO, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class FileNoNode extends PythonUnaryWithInitErrorBuiltinNode {
        @Specialization(guards = "self.isOK()")
        static Object doit(VirtualFrame frame, PBuffered self,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            return callMethod.execute(frame, self.getRaw(), FILENO);
        }
    }

    @Builtin(name = ISATTY, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsAttyNode extends PythonUnaryWithInitErrorBuiltinNode {
        @Specialization(guards = "self.isOK()")
        static Object doit(VirtualFrame frame, PBuffered self,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            return callMethod.execute(frame, self.getRaw(), ISATTY);
        }
    }

    @Builtin(name = _DEALLOC_WARN, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class DeallocWarnNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = {"self.isOK()", "self.getRaw() != null"})
        static Object doit(VirtualFrame frame, PBuffered self, Object source,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            callMethod.execute(frame, self.getRaw(), _DEALLOC_WARN, source);
            return PNone.NONE;
        }

        @Fallback
        static Object none(@SuppressWarnings("unused") Object self, @SuppressWarnings("unused") Object source) {
            return PNone.NONE;
        }
    }

    @Builtin(name = SEEK, minNumOfPositionalArgs = 2, parameterNames = {"$self", "$offset", "whence"})
    @ArgumentClinic(name = "whence", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "BufferedIOUtil.SEEK_SET", useDefaultForNone = true)
    @GenerateNodeFactory
    @ImportStatic(IONodes.class)
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class SeekNode extends PythonTernaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BufferedIOMixinBuiltinsClinicProviders.SeekNodeClinicProviderGen.INSTANCE;
        }

        protected static boolean isSupportedWhence(int whence) {
            return whence == SEEK_SET || whence == SEEK_CUR || whence == SEEK_END;
        }

        @Specialization(guards = {"self.isOK()", "isSupportedWhence(whence)"})
        static long doit(VirtualFrame frame, PBuffered self, Object off, int whence,
                        @Cached("create(SEEK)") CheckIsClosedNode checkIsClosedNode,
                        @Cached BufferedIONodes.CheckIsSeekabledNode checkIsSeekabledNode,
                        @Cached BufferedIONodes.AsOffNumberNode asOffNumberNode,
                        @Cached BufferedIONodes.SeekNode seekNode) {
            checkIsClosedNode.execute(frame, self);
            checkIsSeekabledNode.execute(frame, self);
            long pos = asOffNumberNode.execute(frame, off, TypeError);
            return seekNode.execute(frame, self, pos, whence);
        }

        @Specialization(guards = {"self.isOK()", "!isSupportedWhence(whence)"})
        Object whenceError(@SuppressWarnings("unused") PBuffered self, @SuppressWarnings("unused") int off, int whence) {
            throw raise(ValueError, UNSUPPORTED_WHENCE, whence);
        }

        @Specialization(guards = "!self.isOK()")
        Object initError(PBuffered self, @SuppressWarnings("unused") int off, @SuppressWarnings("unused") int whence) {
            if (self.isDetached()) {
                throw raise(ValueError, IO_STREAM_DETACHED);
            } else {
                throw raise(ValueError, IO_UNINIT);
            }
        }
    }

    @Builtin(name = TELL, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class TellNode extends PythonUnaryWithInitErrorBuiltinNode {
        @Specialization(guards = "self.isOK()")
        static long doit(VirtualFrame frame, PBuffered self,
                        @Cached RawTellNode rawTellNode) {
            long pos = rawTellNode.execute(frame, self);
            pos -= rawOffset(self);
            /* TODO: sanity check (pos >= 0) */
            return pos;
        }
    }

    @Builtin(name = TRUNCATE, minNumOfPositionalArgs = 1, parameterNames = {"$self", "pos"})
    @ArgumentClinic(name = "pos", defaultValue = "PNone.NONE", useDefaultForNone = true)
    @ImportStatic(IONodes.class)
    @GenerateNodeFactory
    abstract static class TruncateNode extends PythonBinaryWithInitErrorClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BufferedIOMixinBuiltinsClinicProviders.TruncateNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = {"self.isOK()", "self.isWritable()"})
        static Object doit(VirtualFrame frame, PBuffered self, Object pos,
                        @Cached EnterBufferedNode lock,
                        @Cached("create(TRUNCATE)") CheckIsClosedNode checkIsClosedNode,
                        @Cached RawTellNode rawTellNode,
                        @Cached FlushAndRewindUnlockedNode flushAndRewindUnlockedNode,
                        @Cached PyObjectCallMethodObjArgs callMethodTruncate) {
            checkIsClosedNode.execute(frame, self);
            try {
                lock.enter(self);
                flushAndRewindUnlockedNode.execute(frame, self);
                Object res = callMethodTruncate.execute(frame, self.getRaw(), TRUNCATE, pos);
                /* Reset cached position */
                rawTellNode.execute(frame, self);
                return res;
            } finally {
                EnterBufferedNode.leave(self);
            }
        }

        @Specialization(guards = {"self.isOK()", "!self.isWritable()"})
        Object notWritable(@SuppressWarnings("unused") PBuffered self, @SuppressWarnings("unused") Object pos) {
            throw raise(IOUnsupportedOperation, TRUNCATE);
        }
    }

    @Builtin(name = RAW, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class RawNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doit(PBuffered self) {
            return self.getRaw();
        }
    }

    @Builtin(name = _FINALIZING, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class FinalizingNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doit(PBuffered self) {
            return self.isFinalizing();
        }
    }

    @Builtin(name = CLOSED, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class ClosedNode extends PythonUnaryWithInitErrorBuiltinNode {
        @Specialization(guards = "self.isOK()")
        static Object doit(VirtualFrame frame, PBuffered self,
                        @Cached BufferedIONodes.IsClosedNode isClosedNode) {
            return isClosedNode.execute(frame, self);
        }
    }

    @Builtin(name = NAME, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class NameNode extends PythonUnaryWithInitErrorBuiltinNode {
        @Specialization(guards = "self.isOK()")
        static Object doit(VirtualFrame frame, PBuffered self,
                        @Cached PyObjectGetAttr getAttr) {
            return getAttr.execute(frame, self.getRaw(), NAME);
        }
    }

    @Builtin(name = MODE, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class ModeNode extends PythonUnaryWithInitErrorBuiltinNode {
        @Specialization(guards = "self.isOK()")
        static Object doit(VirtualFrame frame, PBuffered self,
                        @Cached PyObjectGetAttr getAttr) {
            return getAttr.execute(frame, self.getRaw(), MODE);
        }
    }

    @Builtin(name = __REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object repr(VirtualFrame frame, PBuffered self,
                        @Cached PyObjectLookupAttr lookup,
                        @Cached TypeNodes.GetNameNode getNameNode,
                        @Cached GetClassNode getClassNode,
                        @Cached IsBuiltinClassProfile isValueError,
                        @Cached PyObjectReprAsJavaStringNode repr) {
            String typeName = getNameNode.execute(getClassNode.execute(self));
            Object nameobj = PNone.NO_VALUE;
            try {
                nameobj = lookup.execute(frame, self, NAME);
            } catch (PException e) {
                e.expect(ValueError, isValueError);
                // ignore
            }
            if (nameobj instanceof PNone) {
                return PythonUtils.format("<%s>", typeName);
            } else {
                if (!getContext().reprEnter(self)) {
                    throw raise(RuntimeError, "reentrant call inside %s.__repr__", typeName);
                } else {
                    try {
                        String name = repr.execute(frame, nameobj);
                        return PythonUtils.format("<%s name=%s>", typeName, name);
                    } finally {
                        getContext().reprLeave(self);
                    }
                }
            }
        }
    }
}
