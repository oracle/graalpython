/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates.
 * Copyright (c) 2014, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.builtins.objects.frame;

import static com.oracle.graal.python.builtins.objects.PythonAbstractObject.objectHashCodeAsHexString;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.DescriptorDeleteMarker;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltins;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltins.DictNode;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.str.StringUtils.SimpleTruffleStringFormatNode;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.lib.PyLongAsLongAndOverflowNode;
import com.oracle.graal.python.lib.PyLongCheckExactNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.frame.GetFrameLocalsNode;
import com.oracle.graal.python.nodes.frame.MaterializeFrameNode;
import com.oracle.graal.python.nodes.frame.ReadFrameNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaBooleanNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PFrame)
public final class FrameBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = FrameBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return FrameBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_repr, isComplex = true)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        TruffleString repr(VirtualFrame frame, PFrame self,
                        @Cached GetCodeNode getCodeNode,
                        @Bind Node inliningTarget,
                        @Cached InlinedConditionProfile profile,
                        @Cached MaterializeFrameNode materializeFrameNode,
                        @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode) {
            PCode code = getCodeNode.executeObject(frame, self);
            LinenoNode.syncLocationIfNeeded(frame, self, inliningTarget, profile, materializeFrameNode);
            int lineno = self.getLine();
            return simpleTruffleStringFormatNode.format("<frame at 0x%s, file '%s', line %d, code %s>",
                            objectHashCodeAsHexString(self), code.getFilename(), lineno, code.getName());
        }
    }

    @Builtin(name = "f_globals", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetGlobalsNode extends PythonBuiltinNode {
        @Child private DictNode getDictNode;

        public abstract Object execute(VirtualFrame frame, PFrame self);

        @Specialization
        Object get(VirtualFrame curFrame, PFrame self) {
            PythonObject globals = self.getGlobals();
            if (globals instanceof PythonModule) {
                if (getDictNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    getDictNode = insert(ObjectBuiltins.DictNode.create());
                }
                return getDictNode.execute(curFrame, globals, PNone.NO_VALUE);
            } else {
                return globals != null ? globals : PFactory.createDict(PythonLanguage.get(this));
            }
        }

        @NeverDefault
        public static GetGlobalsNode create() {
            return FrameBuiltinsFactory.GetGlobalsNodeFactory.create(null);
        }
    }

    @Builtin(name = "f_builtins", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetBuiltinsNode extends PythonBuiltinNode {
        public abstract Object execute(VirtualFrame frame, PFrame self);

        @Child private DictNode dictNode = ObjectBuiltins.DictNode.create();

        @Specialization
        Object get(VirtualFrame frame, @SuppressWarnings("unused") PFrame self) {
            // TODO: builtins can be set per frame
            return dictNode.execute(frame, getContext().getBuiltins(), PNone.NO_VALUE);
        }

        @NeverDefault
        public static GetBuiltinsNode create() {
            return FrameBuiltinsFactory.GetBuiltinsNodeFactory.create(null);
        }
    }

    @Builtin(name = "f_lineno", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, allowsDelete = true)
    @GenerateNodeFactory
    public abstract static class LinenoNode extends PythonBinaryBuiltinNode {

        @Specialization
        Object delete(VirtualFrame frame, PFrame self, DescriptorDeleteMarker ignored,
                        @Bind Node inliningTarget,
                        @Cached @Cached.Exclusive PRaiseNode raise) {
            raise.raise(inliningTarget, PythonBuiltinClassType.AttributeError, ErrorMessages.CANNOT_DELETE);
            return PNone.NONE;
        }

        @Specialization(guards = "isNoValue(newLineno)")
        int get(VirtualFrame frame, PFrame self, Object newLineno,
                        @Bind Node inliningTarget,
                        @Cached @Cached.Exclusive InlinedConditionProfile profile,
                        @Cached @Cached.Exclusive MaterializeFrameNode frameNode) {
            syncLocationIfNeeded(frame, self, inliningTarget, profile, frameNode);
            return self.getLine();
        }

        static void syncLocationIfNeeded(VirtualFrame frame, PFrame self, Node inliningTarget, InlinedConditionProfile isCurrentFrameProfile, MaterializeFrameNode materializeNode) {
            // Special case because this builtin can be called without going through an invoke node:
            // we need to sync the location of the frame if and only if 'self' represents the
            // current frame. If 'self' represents another frame on the stack, the location is
            // already set
            if (isCurrentFrameProfile.profile(inliningTarget, frame != null && PArguments.getCurrentFrameInfo(frame) == self.getRef())) {
                PFrame pyFrame = materializeNode.executeOnStack(false, false, frame);
                assert pyFrame == self;
            }
        }

        @SuppressWarnings("truffle-static-method") // this is used for location here
        @Specialization(guards = {"!isNoValue(newLineno)", "!isDeleteMarker(newLineno)"})
        PNone set(VirtualFrame frame, PFrame self, Object newLineno,
                        @Bind Node inliningTarget,
                        @Cached @Cached.Exclusive InlinedConditionProfile isCurrentFrameProfile,
                        @Cached @Cached.Exclusive MaterializeFrameNode materializeNode,
                        @Cached @Cached.Exclusive PRaiseNode raise,
                        @Cached PyLongCheckExactNode isLong,
                        @Cached PyLongAsLongAndOverflowNode toLong) {
            syncLocationIfNeeded(frame, self, inliningTarget, isCurrentFrameProfile, materializeNode);
            if (self.isTraceArgument()) {
                if (isLong.execute(inliningTarget, newLineno)) {
                    try {
                        long lineno = toLong.execute(frame, inliningTarget, newLineno);
                        if (lineno <= Integer.MAX_VALUE && lineno >= Integer.MIN_VALUE) {
                            self.setJumpDestLine((int) lineno);
                        } else {
                            throw raise.raise(inliningTarget, PythonBuiltinClassType.ValueError, ErrorMessages.LINENO_OUT_OF_RANGE);
                        }
                    } catch (OverflowException e) {
                        throw raise.raise(inliningTarget, PythonBuiltinClassType.ValueError, ErrorMessages.LINENO_OUT_OF_RANGE);
                    }
                } else {
                    throw raise.raise(inliningTarget, PythonBuiltinClassType.ValueError, ErrorMessages.LINENO_MUST_BE_AN_INTEGER);
                }
            } else {
                PythonContext context = getContext();
                throw raise.raise(inliningTarget, PythonBuiltinClassType.ValueError, ErrorMessages.CANT_JUMP_FROM_S_EVENT,
                                context.getThreadState(context.getLanguage(inliningTarget)).getTracingWhat().pythonName);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "f_lasti", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetLastiNode extends PythonUnaryBuiltinNode {
        @Specialization
        int get(PFrame self) {
            return self.getLasti();
        }
    }

    @Builtin(name = "f_trace", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, allowsDelete = true)
    @GenerateNodeFactory
    public abstract static class GetTraceNode extends PythonBuiltinNode {
        @Specialization(guards = "isNoValue(v)")
        static Object doGet(PFrame self, @SuppressWarnings("unused") PNone v) {
            Object traceFun = self.getLocalTraceFun();
            return traceFun == null ? PNone.NONE : traceFun;
        }

        @Specialization(guards = {"!isNoValue(v)", "!isDeleteMarker(v)"})
        static Object doSet(PFrame self, Object v) {
            self.setLocalTraceFun(v == PNone.NONE ? null : v);
            return PNone.NONE;
        }

        @Specialization(guards = {"!isNoValue(v)", "isDeleteMarker(v)"})
        static Object doDel(PFrame self, @SuppressWarnings("unused") Object v) {
            self.setLocalTraceFun(null);
            return PNone.NONE;
        }
    }

    @Builtin(name = "f_trace_lines", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isSetter = true, isGetter = true)
    @GenerateNodeFactory
    public abstract static class TraceLinesNode extends PythonBuiltinNode {
        @Specialization(guards = "isNoValue(v)")
        static boolean doGet(PFrame self, @SuppressWarnings("unused") PNone v) {
            return self.getTraceLine();
        }

        @Specialization(guards = "!isNoValue(v)")
        static Object doSet(PFrame self, Object v, @Bind Node inliningTarget,
                        @Cached PRaiseNode raise,
                        @Cached CastToJavaBooleanNode cast) {
            try {
                self.setTraceLine(cast.execute(inliningTarget, v));
            } catch (CannotCastException e) {
                throw raise.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.ATTRIBUTE_VALUE_MUST_BE_BOOL);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "f_code", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetCodeNode extends PythonBuiltinNode {
        public abstract PCode executeObject(VirtualFrame frame, PFrame self);

        @Specialization
        static PCode get(PFrame self,
                        @Bind PythonLanguage language) {
            RootCallTarget ct = self.getTarget();
            assert ct != null;
            return PFactory.createCode(language, ct);
        }

        @NeverDefault
        public static GetCodeNode create() {
            return FrameBuiltinsFactory.GetCodeNodeFactory.create(null);
        }
    }

    @Builtin(name = "f_locals", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetLocalsNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object getUpdating(VirtualFrame frame, PFrame self,
                        @Bind Node inliningTarget,
                        @Cached GetFrameLocalsNode getFrameLocalsNode) {
            Object locals = getFrameLocalsNode.execute(frame, inliningTarget, self);
            self.setLocalsAccessed(true);
            return locals;
        }
    }

    @Builtin(name = "f_back", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetBackrefNode extends PythonBuiltinNode {
        public abstract Object execute(VirtualFrame frame, PFrame self);

        @Specialization
        Object getBackref(VirtualFrame frame, PFrame self,
                        @Cached ReadFrameNode readCallerFrame) {
            PFrame backref = readCallerFrame.getFrameForReference(frame, self.getRef(), 1, false);
            if (backref != null) {
                backref.getRef().markAsEscaped();
                return backref;
            } else {
                return PNone.NONE;
            }
        }

        @NeverDefault
        public static GetBackrefNode create() {
            return FrameBuiltinsFactory.GetBackrefNodeFactory.create(null);
        }
    }

    @Builtin(name = "clear", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class FrameClearNode extends PythonBuiltinNode {
        @Specialization
        Object clear(@SuppressWarnings("unused") PFrame self) {
            // TODO: implement me
            // see: https://github.com/python/cpython/blob/master/Objects/frameobject.c#L503
            return PNone.NONE;
        }
    }
}
