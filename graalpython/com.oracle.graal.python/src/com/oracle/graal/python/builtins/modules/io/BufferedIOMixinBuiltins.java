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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PBufferedRandom;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PBufferedReader;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PBufferedWriter;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.RuntimeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.SEEK_CUR;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.SEEK_END;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.SEEK_SET;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.rawOffset;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_CLOSE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_CLOSED;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_DETACH;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_FILENO;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_ISATTY;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_MODE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_NAME;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_RAW;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_SEEK;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_SEEKABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_TELL;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_TRUNCATE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J__DEALLOC_WARN;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J__FINALIZING;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_CLOSE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_FILENO;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_FLUSH;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_ISATTY;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_MODE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_NAME;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_SEEKABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_TRUNCATE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T__DEALLOC_WARN;
import static com.oracle.graal.python.nodes.ErrorMessages.IO_STREAM_DETACHED;
import static com.oracle.graal.python.nodes.ErrorMessages.IO_UNINIT;
import static com.oracle.graal.python.nodes.ErrorMessages.REENTRANT_CALL_INSIDE_S_REPR;
import static com.oracle.graal.python.nodes.ErrorMessages.UNSUPPORTED_WHENCE;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REPR__;
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
import com.oracle.graal.python.builtins.objects.str.StringUtils.SimpleTruffleStringFormatNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.PyErrChainExceptions;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectReprAsTruffleStringNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = {PBufferedReader, PBufferedWriter, PBufferedRandom})
public final class BufferedIOMixinBuiltins extends AbstractBufferedIOBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return BufferedIOMixinBuiltinsFactory.getFactories();
    }

    @Builtin(name = J_CLOSE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CloseNode extends PythonUnaryWithInitErrorBuiltinNode {

        private static Object close(VirtualFrame frame, Node inliningTarget, PBuffered self,
                        EnterBufferedNode lock,
                        PyObjectCallMethodObjArgs callMethodClose) {
            try {
                lock.enter(inliningTarget, self);
                Object res = callMethodClose.execute(frame, inliningTarget, self.getRaw(), T_CLOSE);
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
                        @Bind("this") Node inliningTarget,
                        @Cached BufferedIONodes.IsClosedNode isClosedNode,
                        @Cached PyObjectCallMethodObjArgs callMethodFlush,
                        @Cached PyObjectCallMethodObjArgs callMethodClose,
                        @Cached PyObjectCallMethodObjArgs callMethodDeallocWarn,
                        @Cached EnterBufferedNode lock,
                        @Cached InlinedConditionProfile profile,
                        @Cached PyErrChainExceptions chainExceptions) {
            try {
                lock.enter(inliningTarget, self);
                if (profile.profile(inliningTarget, isClosedNode.execute(frame, inliningTarget, self))) {
                    return PNone.NONE;
                }
                if (self.isFinalizing()) {
                    if (self.getRaw() != null) {
                        callMethodDeallocWarn.execute(frame, inliningTarget, self.getRaw(), T__DEALLOC_WARN, self);
                    }
                }
            } finally {
                EnterBufferedNode.leave(self);
            }
            /* flush() will most probably re-take the lock, so drop it first */
            try {
                callMethodFlush.execute(frame, inliningTarget, self, T_FLUSH);
            } catch (PException e) {
                try {
                    close(frame, inliningTarget, self, lock, callMethodClose);
                } catch (PException ee) {
                    throw chainExceptions.execute(inliningTarget, ee, e);
                }
                throw e;
            }
            return close(frame, inliningTarget, self, lock, callMethodClose);
        }
    }

    @Builtin(name = J_DETACH, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class DetachNode extends PythonUnaryWithInitErrorBuiltinNode {
        @Specialization(guards = "self.isOK()")
        static Object doit(VirtualFrame frame, PBuffered self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethodFlush) {
            callMethodFlush.execute(frame, inliningTarget, self, T_FLUSH);
            Object raw = self.getRaw();
            self.clearRaw();
            self.setDetached(true);
            self.setOK(false);
            return raw;
        }
    }

    @Builtin(name = J_SEEKABLE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class SeekableNode extends PythonUnaryWithInitErrorBuiltinNode {
        @Specialization(guards = "self.isOK()")
        static Object doit(VirtualFrame frame, PBuffered self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            return callMethod.execute(frame, inliningTarget, self.getRaw(), T_SEEKABLE);
        }
    }

    @Builtin(name = J_FILENO, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class FileNoNode extends PythonUnaryWithInitErrorBuiltinNode {
        @Specialization(guards = "self.isOK()")
        static Object doit(VirtualFrame frame, PBuffered self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            return callMethod.execute(frame, inliningTarget, self.getRaw(), T_FILENO);
        }
    }

    @Builtin(name = J_ISATTY, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsAttyNode extends PythonUnaryWithInitErrorBuiltinNode {
        @Specialization(guards = "self.isOK()")
        static Object doit(VirtualFrame frame, PBuffered self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            return callMethod.execute(frame, inliningTarget, self.getRaw(), T_ISATTY);
        }
    }

    @Builtin(name = J__DEALLOC_WARN, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class DeallocWarnNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = {"self.isOK()", "self.getRaw() != null"})
        static Object doit(VirtualFrame frame, PBuffered self, Object source,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            callMethod.execute(frame, inliningTarget, self.getRaw(), T__DEALLOC_WARN, source);
            return PNone.NONE;
        }

        @Fallback
        static Object none(@SuppressWarnings("unused") Object self, @SuppressWarnings("unused") Object source) {
            return PNone.NONE;
        }
    }

    @Builtin(name = J_SEEK, minNumOfPositionalArgs = 2, parameterNames = {"$self", "$offset", "whence"})
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
                        @Bind("this") Node inliningTarget,
                        @Cached("create(T_SEEK)") CheckIsClosedNode checkIsClosedNode,
                        @Cached BufferedIONodes.CheckIsSeekabledNode checkIsSeekabledNode,
                        @Cached BufferedIONodes.AsOffNumberNode asOffNumberNode,
                        @Cached(inline = true) BufferedIONodes.SeekNode seekNode) {
            checkIsClosedNode.execute(frame, self);
            checkIsSeekabledNode.execute(frame, self);
            long pos = asOffNumberNode.execute(frame, inliningTarget, off, TypeError);
            return seekNode.execute(frame, inliningTarget, self, pos, whence);
        }

        @Specialization(guards = {"self.isOK()", "!isSupportedWhence(whence)"})
        static Object whenceError(@SuppressWarnings("unused") PBuffered self, @SuppressWarnings("unused") int off, int whence,
                        @Shared @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(ValueError, UNSUPPORTED_WHENCE, whence);
        }

        @Specialization(guards = "!self.isOK()")
        static Object initError(PBuffered self, @SuppressWarnings("unused") int off, @SuppressWarnings("unused") int whence,
                        @Shared @Cached PRaiseNode raiseNode) {
            if (self.isDetached()) {
                throw raiseNode.raise(ValueError, IO_STREAM_DETACHED);
            } else {
                throw raiseNode.raise(ValueError, IO_UNINIT);
            }
        }
    }

    @Builtin(name = J_TELL, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class TellNode extends PythonUnaryWithInitErrorBuiltinNode {
        @Specialization(guards = "self.isOK()")
        static long doit(VirtualFrame frame, PBuffered self,
                        @Bind("this") Node inliningTarget,
                        @Cached RawTellNode rawTellNode) {
            long pos = rawTellNode.execute(frame, inliningTarget, self);
            pos -= rawOffset(self);
            /* TODO: sanity check (pos >= 0) */
            return pos;
        }
    }

    @Builtin(name = J_TRUNCATE, minNumOfPositionalArgs = 1, parameterNames = {"$self", "pos"})
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
                        @Bind("this") Node inliningTarget,
                        @Cached EnterBufferedNode lock,
                        @Cached("create(T_TRUNCATE)") CheckIsClosedNode checkIsClosedNode,
                        @Cached RawTellNode rawTellNode,
                        @Cached FlushAndRewindUnlockedNode flushAndRewindUnlockedNode,
                        @Cached PyObjectCallMethodObjArgs callMethodTruncate) {
            checkIsClosedNode.execute(frame, self);
            try {
                lock.enter(inliningTarget, self);
                flushAndRewindUnlockedNode.execute(frame, inliningTarget, self);
                Object res = callMethodTruncate.execute(frame, inliningTarget, self.getRaw(), T_TRUNCATE, pos);
                /* Reset cached position */
                rawTellNode.execute(frame, inliningTarget, self);
                return res;
            } finally {
                EnterBufferedNode.leave(self);
            }
        }

        @Specialization(guards = {"self.isOK()", "!self.isWritable()"})
        static Object notWritable(@SuppressWarnings("unused") PBuffered self, @SuppressWarnings("unused") Object pos,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(IOUnsupportedOperation, T_TRUNCATE);
        }
    }

    @Builtin(name = J_RAW, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class RawNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doit(PBuffered self) {
            return self.getRaw();
        }
    }

    @Builtin(name = J__FINALIZING, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class FinalizingNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doit(PBuffered self) {
            return self.isFinalizing();
        }
    }

    @Builtin(name = J_CLOSED, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class ClosedNode extends PythonUnaryWithInitErrorBuiltinNode {
        @Specialization(guards = "self.isOK()")
        static Object doit(VirtualFrame frame, PBuffered self,
                        @Bind("this") Node inliningTarget,
                        @Cached BufferedIONodes.IsClosedNode isClosedNode) {
            return isClosedNode.execute(frame, inliningTarget, self);
        }
    }

    @Builtin(name = J_NAME, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class NameNode extends PythonUnaryWithInitErrorBuiltinNode {
        @Specialization(guards = "self.isOK()")
        static Object doit(VirtualFrame frame, PBuffered self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetAttr getAttr) {
            return getAttr.execute(frame, inliningTarget, self.getRaw(), T_NAME);
        }
    }

    @Builtin(name = J_MODE, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class ModeNode extends PythonUnaryWithInitErrorBuiltinNode {
        @Specialization(guards = "self.isOK()")
        static Object doit(VirtualFrame frame, PBuffered self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetAttr getAttr) {
            return getAttr.execute(frame, inliningTarget, self.getRaw(), T_MODE);
        }
    }

    @Builtin(name = J___REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        TruffleString repr(VirtualFrame frame, PBuffered self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectLookupAttr lookup,
                        @Cached TypeNodes.GetNameNode getNameNode,
                        @Cached GetClassNode getClassNode,
                        @Cached IsBuiltinObjectProfile isValueError,
                        @Cached PyObjectReprAsTruffleStringNode repr,
                        @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode) {
            TruffleString typeName = getNameNode.execute(inliningTarget, getClassNode.execute(inliningTarget, self));
            Object nameobj = PNone.NO_VALUE;
            try {
                nameobj = lookup.execute(frame, inliningTarget, self, T_NAME);
            } catch (PException e) {
                e.expect(inliningTarget, ValueError, isValueError);
                // ignore
            }
            if (nameobj instanceof PNone) {
                return simpleTruffleStringFormatNode.format("<%s>", typeName);
            } else {
                if (!getContext().reprEnter(self)) {
                    throw raise(RuntimeError, REENTRANT_CALL_INSIDE_S_REPR, typeName);
                } else {
                    try {
                        TruffleString name = repr.execute(frame, inliningTarget, nameobj);
                        return simpleTruffleStringFormatNode.format("<%s name=%s>", typeName, name);
                    } finally {
                        getContext().reprLeave(self);
                    }
                }
            }
        }
    }
}
