/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates.
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
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEXT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REPR__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.util.List;

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
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.bytecode.BytecodeFrameInfo;
import com.oracle.graal.python.nodes.bytecode_dsl.BytecodeDSLFrameInfo;
import com.oracle.graal.python.nodes.bytecode_dsl.PBytecodeDSLRootNode;
import com.oracle.graal.python.nodes.frame.MaterializeFrameNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.bytecode.BytecodeNode;
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

    private static void checkResumable(Node inliningTarget, PGenerator self, PRaiseNode.Lazy raiseNode) {
        if (self.isFinished()) {
            throw raiseNode.get(inliningTarget).raiseStopIteration();
        }
        if (self.isRunning()) {
            throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.GENERATOR_ALREADY_EXECUTING);
        }
    }

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
                        @Bind("this") Node inliningTarget,
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
                        @Bind("this") Node inliningTarget,
                        @Cached StringNodes.CastToTruffleStringCheckedNode cast) {
            return setQualname(self, cast.cast(inliningTarget, value, ErrorMessages.MUST_BE_SET_TO_S_OBJ, T___QUALNAME__, "string"));
        }
    }

    @Builtin(name = J___ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object iter(PGenerator self) {
            return self;
        }
    }

    @Builtin(name = J___NEXT__, minNumOfPositionalArgs = 1, doc = "Implement next(self).")
    @GenerateNodeFactory
    public abstract static class NextNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object next(VirtualFrame frame, PGenerator self,
                        @Bind("this") Node inliningTarget,
                        @Cached CommonGeneratorBuiltins.ResumeGeneratorNode resumeGeneratorNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            checkResumable(inliningTarget, self, raiseNode);
            return resumeGeneratorNode.execute(frame, inliningTarget, self, null);
        }
    }

    @Builtin(name = "gi_code", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetCodeNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object getCode(PGenerator self,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedConditionProfile hasCodeProfile,
                        @Cached PythonObjectFactory.Lazy factory) {
            return self.getOrCreateCode(inliningTarget, hasCodeProfile, factory);
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
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(AttributeError, ErrorMessages.ATTRIBUTE_S_OF_P_OBJECTS_IS_NOT_WRITABLE, "gi_running", self);
        }
    }

    @Builtin(name = "gi_frame", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetFrameNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object getFrame(PGenerator self,
                        @Cached PythonObjectFactory factory) {
            if (self.isFinished()) {
                return PNone.NONE;
            } else {
                MaterializedFrame generatorFrame = PArguments.getGeneratorFrame(self.getArguments());
                if (PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER) {
                    BytecodeDSLFrameInfo info = (BytecodeDSLFrameInfo) generatorFrame.getFrameDescriptor().getInfo();
                    PBytecodeDSLRootNode rootNode = info.getRootNode();
                    ContinuationResult continuation = self.getContinuation();
                    BytecodeNode bytecodeNode = continuation.getBytecodeNode();
                    PFrame frame = MaterializeFrameNode.materializeGeneratorFrame(bytecodeNode, generatorFrame, PFrame.Reference.EMPTY, factory);
                    int bci = continuation.getBci();
                    frame.setBci(bci);
                    frame.setLine(rootNode.bciToLine(bci, bytecodeNode));
                    return frame;
                } else {
                    BytecodeFrameInfo info = (BytecodeFrameInfo) generatorFrame.getFrameDescriptor().getInfo();
                    PFrame frame = MaterializeFrameNode.materializeGeneratorFrame(info.getRootNode(), generatorFrame, PFrame.Reference.EMPTY, factory);
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

    @Builtin(name = J___REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        static TruffleString repr(PGenerator self,
                        @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode) {
            return simpleTruffleStringFormatNode.format("<generator object %s at %d>", self.getName(), PythonAbstractObject.objectHashCode(self));
        }
    }

    @Builtin(name = J___CLASS_GETITEM__, minNumOfPositionalArgs = 2, isClassmethod = true)
    @GenerateNodeFactory
    public abstract static class ClassGetItemNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object classGetItem(Object cls, Object key,
                        @Cached PythonObjectFactory factory) {
            return factory.createGenericAlias(cls, key);
        }
    }
}
