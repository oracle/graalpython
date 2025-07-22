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
package com.oracle.graal.python.builtins.objects.generator;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.AttributeError;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___QUALNAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___QUALNAME__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CLASS_GETITEM__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.str.StringNodes;
import com.oracle.graal.python.builtins.objects.str.StringUtils.SimpleTruffleStringFormatNode;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotIterNext.TpIterNextBuiltin;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.bytecode.BytecodeFrameInfo;
import com.oracle.graal.python.nodes.bytecode_dsl.BytecodeDSLFrameInfo;
import com.oracle.graal.python.nodes.frame.MaterializeFrameNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.bytecode.BytecodeLocation;
import com.oracle.truffle.api.bytecode.ContinuationResult;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PGenerator)
public final class GeneratorBuiltins extends PythonBuiltins {

    private static void checkResumable(Node inliningTarget, PGenerator self, PRaiseNode raiseNode) {
        if (self.isFinished()) {
            throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.StopIteration);
        }
        if (self.isRunning()) {
            throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.GENERATOR_ALREADY_EXECUTING);
        }
    }

    public static final TpSlots SLOTS = GeneratorBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return GeneratorBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___NAME__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class NameNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(noValue)")
        static Object getName(PGenerator self, @SuppressWarnings("unused") PNone noValue) {
            return self.getName();
        }

        @Specialization
        static Object setName(PGenerator self, TruffleString value) {
            self.setName(value);
            return PNone.NONE;
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object setName(PGenerator self, Object value,
                        @Bind Node inliningTarget,
                        @Cached StringNodes.CastToTruffleStringCheckedNode cast) {
            return setName(self, cast.cast(inliningTarget, value, ErrorMessages.MUST_BE_SET_TO_S_OBJ, T___NAME__, "string"));
        }
    }

    @Builtin(name = J___QUALNAME__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class QualnameNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(noValue)")
        static Object getQualname(PGenerator self, @SuppressWarnings("unused") PNone noValue) {
            return self.getQualname();
        }

        @Specialization
        static Object setQualname(PGenerator self, TruffleString value) {
            self.setQualname(value);
            return PNone.NONE;
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object setQualname(PGenerator self, Object value,
                        @Bind Node inliningTarget,
                        @Cached StringNodes.CastToTruffleStringCheckedNode cast) {
            return setQualname(self, cast.cast(inliningTarget, value, ErrorMessages.MUST_BE_SET_TO_S_OBJ, T___QUALNAME__, "string"));
        }
    }

    @Slot(value = SlotKind.tp_iter, isComplex = true)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object iter(PGenerator self) {
            return self;
        }
    }

    @Slot(value = SlotKind.tp_iternext, isComplex = true)
    @GenerateNodeFactory
    public abstract static class NextNode extends TpIterNextBuiltin {
        @Specialization
        static Object next(VirtualFrame frame, PGenerator self,
                        @Bind Node inliningTarget,
                        @Cached CommonGeneratorBuiltins.ResumeGeneratorNode resumeGeneratorNode,
                        @Cached PRaiseNode raiseNode) {
            if (self.isFinished()) {
                throw iteratorExhausted();
            }
            if (self.isRunning()) {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.GENERATOR_ALREADY_EXECUTING);
            }
            return resumeGeneratorNode.execute(frame, inliningTarget, self, null);
        }
    }

    @Builtin(name = "gi_code", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetCodeNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object getCode(PGenerator self,
                        @Bind Node inliningTarget,
                        @Cached InlinedConditionProfile hasCodeProfile) {
            return self.getOrCreateCode(inliningTarget, hasCodeProfile);
        }
    }

    @Builtin(name = "gi_running", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    public abstract static class GetRunningNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(none)")
        static Object getRunning(PGenerator self, @SuppressWarnings("unused") PNone none) {
            return self.isRunning();
        }

        @Specialization(guards = "!isNoValue(obj)")
        static Object setRunning(@SuppressWarnings("unused") PGenerator self, @SuppressWarnings("unused") Object obj,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, AttributeError, ErrorMessages.ATTRIBUTE_S_OF_P_OBJECTS_IS_NOT_WRITABLE, "gi_running", self);
        }
    }

    @Builtin(name = "gi_frame", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetFrameNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object getFrame(PGenerator self) {
            if (self.isFinished()) {
                return PNone.NONE;
            } else {
                if (PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER) {
                    ContinuationResult continuation = self.getContinuation();
                    BytecodeLocation location = continuation.getBytecodeLocation();
                    MaterializedFrame generatorFrame = continuation.getFrame();
                    BytecodeDSLFrameInfo info = (BytecodeDSLFrameInfo) generatorFrame.getFrameDescriptor().getInfo();
                    PFrame frame = MaterializeFrameNode.materializeGeneratorFrame(location.getBytecodeNode(), generatorFrame, PFrame.Reference.EMPTY);
                    int bci = location.getBytecodeIndex();
                    frame.setBci(bci);
                    frame.setLine(info.getRootNode().bciToLine(bci, location.getBytecodeNode()));
                    return frame;
                } else {
                    MaterializedFrame generatorFrame = PArguments.getGeneratorFrame(self.getArguments());
                    BytecodeFrameInfo info = (BytecodeFrameInfo) generatorFrame.getFrameDescriptor().getInfo();
                    PFrame frame = MaterializeFrameNode.materializeGeneratorFrame(info.getRootNode(), generatorFrame, PFrame.Reference.EMPTY);
                    int bci = self.getBci();
                    frame.setBci(bci);
                    frame.setLine(info.getRootNode().bciToLine(bci));
                    return frame;
                }
            }
        }
    }

    @Builtin(name = "gi_yieldfrom", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetYieldFromNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object getYieldFrom(PGenerator self) {
            Object yieldFrom = self.getYieldFrom();
            return yieldFrom != null ? yieldFrom : PNone.NONE;
        }
    }

    @Builtin(name = "gi_suspended", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class GetSuspendedNode extends PythonUnaryBuiltinNode {
        @Specialization
        static boolean suspended(PGenerator self) {
            return self.isStarted() && !self.isRunning() && !self.isFinished();
        }
    }

    @Slot(value = SlotKind.tp_repr, isComplex = true)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        static TruffleString repr(PGenerator self,
                        @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode) {
            return simpleTruffleStringFormatNode.format("<generator object %s at 0x%d>", self.getQualname(), PythonAbstractObject.objectHashCode(self));
        }
    }

    @Builtin(name = J___CLASS_GETITEM__, minNumOfPositionalArgs = 2, isClassmethod = true)
    @GenerateNodeFactory
    public abstract static class ClassGetItemNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object classGetItem(Object cls, Object key,
                        @Bind PythonLanguage language) {
            return PFactory.createGenericAlias(language, cls, key);
        }
    }
}
