/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
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
package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.DeprecationWarning;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.RuntimeError;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_FLUSH;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_WRITE;
import static com.oracle.graal.python.builtins.objects.PNone.NO_VALUE;
import static com.oracle.graal.python.builtins.objects.PNotImplemented.NOT_IMPLEMENTED;
import static com.oracle.graal.python.nodes.BuiltinNames.J_ABS;
import static com.oracle.graal.python.nodes.BuiltinNames.J_ALL;
import static com.oracle.graal.python.nodes.BuiltinNames.J_ANY;
import static com.oracle.graal.python.nodes.BuiltinNames.J_ASCII;
import static com.oracle.graal.python.nodes.BuiltinNames.J_BIN;
import static com.oracle.graal.python.nodes.BuiltinNames.J_BREAKPOINT;
import static com.oracle.graal.python.nodes.BuiltinNames.J_BUILTINS;
import static com.oracle.graal.python.nodes.BuiltinNames.J_CALLABLE;
import static com.oracle.graal.python.nodes.BuiltinNames.J_CHR;
import static com.oracle.graal.python.nodes.BuiltinNames.J_COMPILE;
import static com.oracle.graal.python.nodes.BuiltinNames.J_DELATTR;
import static com.oracle.graal.python.nodes.BuiltinNames.J_DIR;
import static com.oracle.graal.python.nodes.BuiltinNames.J_DIVMOD;
import static com.oracle.graal.python.nodes.BuiltinNames.J_EVAL;
import static com.oracle.graal.python.nodes.BuiltinNames.J_EXEC;
import static com.oracle.graal.python.nodes.BuiltinNames.J_FORMAT;
import static com.oracle.graal.python.nodes.BuiltinNames.J_GETATTR;
import static com.oracle.graal.python.nodes.BuiltinNames.J_HASATTR;
import static com.oracle.graal.python.nodes.BuiltinNames.J_HASH;
import static com.oracle.graal.python.nodes.BuiltinNames.J_HEX;
import static com.oracle.graal.python.nodes.BuiltinNames.J_ID;
import static com.oracle.graal.python.nodes.BuiltinNames.J_ISINSTANCE;
import static com.oracle.graal.python.nodes.BuiltinNames.J_ISSUBCLASS;
import static com.oracle.graal.python.nodes.BuiltinNames.J_ITER;
import static com.oracle.graal.python.nodes.BuiltinNames.J_LEN;
import static com.oracle.graal.python.nodes.BuiltinNames.J_MAX;
import static com.oracle.graal.python.nodes.BuiltinNames.J_MIN;
import static com.oracle.graal.python.nodes.BuiltinNames.J_NEXT;
import static com.oracle.graal.python.nodes.BuiltinNames.J_OCT;
import static com.oracle.graal.python.nodes.BuiltinNames.J_ORD;
import static com.oracle.graal.python.nodes.BuiltinNames.J_POW;
import static com.oracle.graal.python.nodes.BuiltinNames.J_PRINT;
import static com.oracle.graal.python.nodes.BuiltinNames.J_REPR;
import static com.oracle.graal.python.nodes.BuiltinNames.J_ROUND;
import static com.oracle.graal.python.nodes.BuiltinNames.J_SETATTR;
import static com.oracle.graal.python.nodes.BuiltinNames.J_SORTED;
import static com.oracle.graal.python.nodes.BuiltinNames.J_SUM;
import static com.oracle.graal.python.nodes.BuiltinNames.J___BUILD_CLASS__;
import static com.oracle.graal.python.nodes.BuiltinNames.T_BREAKPOINTHOOK;
import static com.oracle.graal.python.nodes.BuiltinNames.T_EVAL;
import static com.oracle.graal.python.nodes.BuiltinNames.T_EXEC;
import static com.oracle.graal.python.nodes.BuiltinNames.T_STDOUT;
import static com.oracle.graal.python.nodes.BuiltinNames.T_SYS;
import static com.oracle.graal.python.nodes.BuiltinNames.T___BUILTINS__;
import static com.oracle.graal.python.nodes.BuiltinNames.T___DEBUG__;
import static com.oracle.graal.python.nodes.BuiltinNames.T___GRAALPYTHON__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DICT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___FORMAT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___MRO_ENTRIES__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___NEXT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ROUND__;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_STRING;
import static com.oracle.graal.python.nodes.StringLiterals.T_MINUS;
import static com.oracle.graal.python.nodes.StringLiterals.T_NEWLINE;
import static com.oracle.graal.python.nodes.StringLiterals.T_SPACE;
import static com.oracle.graal.python.nodes.StringLiterals.T_STRICT;
import static com.oracle.graal.python.nodes.StringLiterals.T_STRING_SOURCE;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;
import static com.oracle.graal.python.util.PythonUtils.tsbCapacity;

import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import com.oracle.graal.python.PythonFileDetector;
import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinFunctionsFactory.GetAttrNodeFactory;
import com.oracle.graal.python.builtins.modules.BuiltinFunctionsFactory.GlobalsNodeFactory;
import com.oracle.graal.python.builtins.modules.BuiltinFunctionsFactory.HexNodeFactory;
import com.oracle.graal.python.builtins.modules.BuiltinFunctionsFactory.OctNodeFactory;
import com.oracle.graal.python.builtins.modules.WarningsModuleBuiltins.WarnNode;
import com.oracle.graal.python.builtins.modules.ast.AstModuleBuiltins;
import com.oracle.graal.python.builtins.modules.io.IOModuleBuiltins;
import com.oracle.graal.python.builtins.modules.io.IONodes;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.code.CodeNodes;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.PHashingCollection;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes.GetObjectArrayNode;
import com.oracle.graal.python.builtins.objects.common.SequenceNodesFactory.GetObjectArrayNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.floats.FloatBuiltins;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.ListBuiltins;
import com.oracle.graal.python.builtins.objects.list.ListBuiltins.ListSortNode;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.ObjectNodes;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TypeBuiltins;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsTypeNode;
import com.oracle.graal.python.compiler.Compiler;
import com.oracle.graal.python.compiler.RaisePythonExceptionErrorCallback;
import com.oracle.graal.python.lib.PyCallableCheckNode;
import com.oracle.graal.python.lib.PyEvalGetGlobals;
import com.oracle.graal.python.lib.PyMappingCheckNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyNumberIndexNode;
import com.oracle.graal.python.lib.PyObjectAsciiNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectDir;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectGetMethod;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectReprAsObjectNode;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.lib.PyObjectStrAsObjectNode;
import com.oracle.graal.python.lib.PyObjectStrAsTruffleStringNode;
import com.oracle.graal.python.lib.PyUnicodeFSDecoderNode;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.GraalPythonTranslationErrorNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PNodeWithRaise;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.argument.ReadArgumentNode;
import com.oracle.graal.python.nodes.attributes.DeleteAttributeNode;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode.GetAnyAttributeNode;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode.GetFixedAttributeNode;
import com.oracle.graal.python.nodes.attributes.LookupCallableSlotInMRONode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.SetAttributeNode;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.builtins.ListNodes.ConstructListNode;
import com.oracle.graal.python.nodes.bytecode.PBytecodeRootNode;
import com.oracle.graal.python.nodes.call.CallDispatchNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.GenericInvokeNode;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallVarargsMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallTernaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.AddNode;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNode;
import com.oracle.graal.python.nodes.expression.BinaryOpNode;
import com.oracle.graal.python.nodes.expression.CoerceToBooleanNode;
import com.oracle.graal.python.nodes.expression.TernaryArithmetic;
import com.oracle.graal.python.nodes.frame.MaterializeFrameNode;
import com.oracle.graal.python.nodes.frame.ReadCallerFrameNode;
import com.oracle.graal.python.nodes.frame.ReadLocalsNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetOrCreateDictNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.subscript.SetItemNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.nodes.util.CastToJavaLongExactNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.pegparser.AbstractParser;
import com.oracle.graal.python.pegparser.InputType;
import com.oracle.graal.python.pegparser.Parser;
import com.oracle.graal.python.pegparser.sst.ModTy;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.PythonParser.ParserMode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.storage.BoolSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.IntSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.CharsetMapping;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.graal.python.util.Supplier;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.LoopConditionProfile;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.Encoding;
import com.oracle.truffle.api.strings.TruffleStringBuilder;
import com.oracle.truffle.api.utilities.TriState;

@CoreFunctions(defineModule = J_BUILTINS, isEager = true)
public final class BuiltinFunctions extends PythonBuiltins {

    private static final TruffleString T_NONE = PythonUtils.tsLiteral("None");
    private static final TruffleString T_FALSE = PythonUtils.tsLiteral("False");
    private static final TruffleString T_TRUE = PythonUtils.tsLiteral("True");

    @Override
    protected List<com.oracle.truffle.api.dsl.NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return BuiltinFunctionsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        addBuiltinConstant(T___GRAALPYTHON__, core.lookupBuiltinModule(T___GRAALPYTHON__));
        addBuiltinConstant(T_NONE, PNone.NONE);
        addBuiltinConstant(T_FALSE, false);
        addBuiltinConstant(T_TRUE, true);
        super.initialize(core);
    }

    @Override
    public void postInitialize(Python3Core core) {
        super.postInitialize(core);
        PythonModule builtinsModule = core.lookupBuiltinModule(BuiltinNames.T_BUILTINS);
        builtinsModule.setAttribute(T___DEBUG__, !core.getContext().getOption(PythonOptions.PythonOptimizeFlag));
    }

    // abs(x)
    @Builtin(name = J_ABS, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class AbsNode extends PythonUnaryBuiltinNode {
        @Specialization
        public int absBoolean(boolean arg) {
            return arg ? 1 : 0;
        }

        @Specialization
        public double absDouble(double arg) {
            return Math.abs(arg);
        }

        @Specialization
        public Object absObject(VirtualFrame frame, Object object,
                        @Cached("create(T___ABS__)") LookupAndCallUnaryNode callAbs) {
            Object result = callAbs.executeObject(frame, object);
            if (result == NO_VALUE) {
                throw raise(TypeError, ErrorMessages.BAD_OPERAND_FOR, "", "abs()", object);
            }
            return result;
        }
    }

    /**
     * Common class for all() and any() operations, as their logic and behaviors are very similar.
     */
    abstract static class AllOrAnyNode extends PNodeWithContext {
        enum NodeType {
            ALL,
            ANY
        }

        @Child private PyObjectIsTrueNode isTrueNode = PyObjectIsTrueNode.create();

        private final LoopConditionProfile loopConditionProfile = LoopConditionProfile.create();

        abstract boolean execute(Frame frame, Object storageObj, NodeType nodeType);

        @Specialization
        boolean doBoolSequence(VirtualFrame frame,
                        BoolSequenceStorage sequenceStorage,
                        NodeType nodeType) {
            boolean[] internalArray = sequenceStorage.getInternalBoolArray();
            int seqLength = sequenceStorage.length();

            loopConditionProfile.profileCounted(seqLength);
            for (int i = 0; loopConditionProfile.inject(i < seqLength); i++) {
                if (nodeType == NodeType.ALL && !isTrueNode.execute(frame, internalArray[i])) {
                    return false;
                } else if (nodeType == NodeType.ANY && isTrueNode.execute(frame, internalArray[i])) {
                    return true;
                }
            }

            return nodeType == NodeType.ALL;
        }

        @Specialization
        boolean doIntSequence(VirtualFrame frame,
                        IntSequenceStorage sequenceStorage,
                        NodeType nodeType) {
            int[] internalArray = sequenceStorage.getInternalIntArray();
            int seqLength = sequenceStorage.length();

            loopConditionProfile.profileCounted(seqLength);
            for (int i = 0; loopConditionProfile.inject(i < seqLength); i++) {
                if (nodeType == NodeType.ALL && !isTrueNode.execute(frame, internalArray[i])) {
                    return false;
                } else if (nodeType == NodeType.ANY && isTrueNode.execute(frame, internalArray[i])) {
                    return true;
                }
            }

            return nodeType == NodeType.ALL;
        }

        @Specialization
        boolean doGenericSequence(VirtualFrame frame,
                        SequenceStorage sequenceStorage,
                        NodeType nodeType,
                        @Cached SequenceStorageNodes.LenNode lenNode) {
            Object[] internalArray = sequenceStorage.getInternalArray();
            int seqLength = lenNode.execute(sequenceStorage);

            loopConditionProfile.profileCounted(seqLength);
            for (int i = 0; loopConditionProfile.inject(i < seqLength); i++) {
                if (nodeType == NodeType.ALL && !isTrueNode.execute(frame, internalArray[i])) {
                    return false;
                } else if (nodeType == NodeType.ANY && isTrueNode.execute(frame, internalArray[i])) {
                    return true;
                }
            }

            return nodeType == NodeType.ALL;
        }

        @Specialization(limit = "3")
        protected boolean doHashStorage(VirtualFrame frame,
                        HashingStorage hashingStorage,
                        NodeType nodeType,
                        @CachedLibrary("hashingStorage") HashingStorageLibrary hlib) {
            HashingStorageLibrary.HashingStorageIterator<Object> keysIter = hlib.keys(hashingStorage).iterator();
            int seqLength = hlib.length(hashingStorage);

            loopConditionProfile.profileCounted(seqLength);
            for (int i = 0; loopConditionProfile.inject(i < seqLength); i++) {
                Object key = keysIter.next();
                if (nodeType == NodeType.ALL) {
                    if (!isTrueNode.execute(frame, key)) {
                        return false;
                    }
                } else if (nodeType == NodeType.ANY && isTrueNode.execute(frame, key)) {
                    return true;
                }
            }

            return nodeType == NodeType.ALL;
        }
    }

    @Builtin(name = J_ALL, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class AllNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = "cannotBeOverridden(object, getClassNode)", limit = "1")
        static boolean doList(VirtualFrame frame,
                        PList object,
                        @SuppressWarnings("unused") @Shared("getClassNode") @Cached GetClassNode getClassNode,
                        @Shared("allOrAnyNode") @Cached AllOrAnyNode allOrAnyNode) {
            return allOrAnyNode.execute(frame, object.getSequenceStorage(), AllOrAnyNode.NodeType.ALL);
        }

        @Specialization(guards = "cannotBeOverridden(object, getClassNode)", limit = "1")
        static boolean doTuple(VirtualFrame frame,
                        PTuple object,
                        @SuppressWarnings("unused") @Shared("getClassNode") @Cached GetClassNode getClassNode,
                        @Shared("allOrAnyNode") @Cached AllOrAnyNode allOrAnyNode) {
            return allOrAnyNode.execute(frame, object.getSequenceStorage(), AllOrAnyNode.NodeType.ALL);
        }

        @Specialization(guards = "cannotBeOverridden(object, getClassNode)", limit = "1")
        static boolean doHashColl(VirtualFrame frame,
                        PHashingCollection object,
                        @SuppressWarnings("unused") @Shared("getClassNode") @Cached GetClassNode getClassNode,
                        @Shared("allOrAnyNode") @Cached AllOrAnyNode allOrAnyNode) {
            return allOrAnyNode.execute(frame, object.getDictStorage(), AllOrAnyNode.NodeType.ALL);
        }

        @Specialization
        boolean doObject(VirtualFrame frame,
                        Object object,
                        @Cached PyObjectGetIter getIter,
                        @Cached GetNextNode nextNode,
                        @Cached IsBuiltinClassProfile errorProfile,
                        @Cached PyObjectIsTrueNode isTrueNode) {
            Object iterator = getIter.execute(frame, object);
            int nbrIter = 0;

            while (true) {
                try {
                    Object next = nextNode.execute(frame, iterator);
                    nbrIter++;
                    if (!isTrueNode.execute(frame, next)) {
                        return false;
                    }
                } catch (PException e) {
                    e.expectStopIteration(errorProfile);
                    break;
                } finally {
                    LoopNode.reportLoopCount(this, nbrIter);
                }
            }

            return true;
        }
    }

    @Builtin(name = J_ANY, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class AnyNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = "cannotBeOverridden(object, getClassNode)", limit = "1")
        static boolean doList(VirtualFrame frame,
                        PList object,
                        @SuppressWarnings("unused") @Shared("getClassNode") @Cached GetClassNode getClassNode,
                        @Shared("allOrAnyNode") @Cached AllOrAnyNode allOrAnyNode) {
            return allOrAnyNode.execute(frame, object.getSequenceStorage(), AllOrAnyNode.NodeType.ANY);
        }

        @Specialization(guards = "cannotBeOverridden(object, getClassNode)", limit = "1")
        static boolean doTuple(VirtualFrame frame,
                        PTuple object,
                        @SuppressWarnings("unused") @Shared("getClassNode") @Cached GetClassNode getClassNode,
                        @Shared("allOrAnyNode") @Cached AllOrAnyNode allOrAnyNode) {
            return allOrAnyNode.execute(frame, object.getSequenceStorage(), AllOrAnyNode.NodeType.ANY);
        }

        @Specialization(guards = "cannotBeOverridden(object, getClassNode)", limit = "1")
        static boolean doHashColl(VirtualFrame frame,
                        PHashingCollection object,
                        @SuppressWarnings("unused") @Shared("getClassNode") @Cached GetClassNode getClassNode,
                        @Shared("allOrAnyNode") @Cached AllOrAnyNode allOrAnyNode) {
            return allOrAnyNode.execute(frame, object.getDictStorage(), AllOrAnyNode.NodeType.ANY);
        }

        @Specialization
        boolean doObject(VirtualFrame frame,
                        Object object,
                        @Cached PyObjectGetIter getIter,
                        @Cached GetNextNode nextNode,
                        @Cached IsBuiltinClassProfile errorProfile,
                        @Cached PyObjectIsTrueNode isTrueNode) {
            Object iterator = getIter.execute(frame, object);
            int nbrIter = 0;

            while (true) {
                try {
                    Object next = nextNode.execute(frame, iterator);
                    nbrIter++;
                    if (isTrueNode.execute(frame, next)) {
                        return true;
                    }
                } catch (PException e) {
                    e.expectStopIteration(errorProfile);
                    break;
                } finally {
                    LoopNode.reportLoopCount(this, nbrIter);
                }
            }

            return false;
        }
    }

    // bin(object)
    @Builtin(name = J_BIN, minNumOfPositionalArgs = 1)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class BinNode extends PythonUnaryBuiltinNode {
        static final TruffleString T_BIN_PREFIX = tsLiteral("0b");
        static final TruffleString T_HEX_PREFIX = tsLiteral("0x");
        static final TruffleString T_OCT_PREFIX = tsLiteral("0o");

        @TruffleBoundary
        protected TruffleString buildString(boolean isNegative, TruffleString number, TruffleStringBuilder.AppendStringNode appendStringNode, TruffleStringBuilder.ToStringNode toStringNode) {
            TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING, tsbCapacity(3) + number.byteLength(TS_ENCODING));
            if (isNegative) {
                appendStringNode.execute(sb, T_MINUS);
            }
            appendStringNode.execute(sb, prefix());
            appendStringNode.execute(sb, number);
            return toStringNode.execute(sb);
        }

        protected TruffleString prefix() {
            return T_BIN_PREFIX;
        }

        @TruffleBoundary
        protected String longToString(long x) {
            return Long.toBinaryString(x);
        }

        @TruffleBoundary
        protected String bigToString(BigInteger x) {
            return x.toString(2);
        }

        @TruffleBoundary
        protected BigInteger bigAbs(BigInteger x) {
            return x.abs();
        }

        @Specialization
        TruffleString doL(long x,
                        @Cached ConditionProfile isMinLong,
                        @Cached TruffleString.FromJavaStringNode fromJavaStringNode,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            if (isMinLong.profile(x == Long.MIN_VALUE)) {
                return buildString(true, fromJavaStringNode.execute(bigToString(bigAbs(PInt.longToBigInteger(x))), TS_ENCODING), appendStringNode, toStringNode);
            }
            return buildString(x < 0, fromJavaStringNode.execute(longToString(Math.abs(x)), TS_ENCODING), appendStringNode, toStringNode);
        }

        @Specialization
        TruffleString doD(double x,
                        @Cached PRaiseNode raise) {
            throw raise.raiseIntegerInterpretationError(x);
        }

        @Specialization
        TruffleString doPI(PInt x,
                        @Cached TruffleString.FromJavaStringNode fromJavaStringNode,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            BigInteger value = x.getValue();
            return buildString(value.signum() < 0, fromJavaStringNode.execute(bigToString(PInt.abs(value)), TS_ENCODING), appendStringNode, toStringNode);
        }

        @Specialization(replaces = {"doL", "doD", "doPI"})
        TruffleString doO(VirtualFrame frame, Object x,
                        @Cached ConditionProfile isMinLong,
                        @Cached PyNumberIndexNode indexNode,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached BranchProfile isInt,
                        @Cached BranchProfile isLong,
                        @Cached BranchProfile isPInt,
                        @Cached TruffleString.FromJavaStringNode fromJavaStringNode,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            Object index = indexNode.execute(frame, x);
            if (index instanceof Boolean || index instanceof Integer) {
                isInt.enter();
                return doL(asSizeNode.executeExact(frame, index), isMinLong, fromJavaStringNode, appendStringNode, toStringNode);
            } else if (index instanceof Long) {
                isLong.enter();
                return doL((long) index, isMinLong, fromJavaStringNode, appendStringNode, toStringNode);
            } else if (index instanceof PInt) {
                isPInt.enter();
                return doPI((PInt) index, fromJavaStringNode, appendStringNode, toStringNode);
            } else {
                CompilerDirectives.transferToInterpreter();
                throw raise(PythonBuiltinClassType.NotImplementedError, toTruffleStringUncached("bin/oct/hex with native integer subclasses"));
            }
        }
    }

    // oct(object)
    @Builtin(name = J_OCT, minNumOfPositionalArgs = 1)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class OctNode extends BinNode {
        @Override
        @TruffleBoundary
        protected String bigToString(BigInteger x) {
            return x.toString(8);
        }

        @Override
        @TruffleBoundary
        protected String longToString(long x) {
            return Long.toOctalString(x);
        }

        @Override
        protected TruffleString prefix() {
            return T_OCT_PREFIX;
        }

        public static OctNode create() {
            return OctNodeFactory.create();
        }
    }

    // hex(object)
    @Builtin(name = J_HEX, minNumOfPositionalArgs = 1)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class HexNode extends BinNode {
        @Override
        @TruffleBoundary
        protected String bigToString(BigInteger x) {
            return x.toString(16);
        }

        @Override
        @TruffleBoundary
        protected String longToString(long x) {
            return Long.toHexString(x);
        }

        @Override
        protected TruffleString prefix() {
            return T_HEX_PREFIX;
        }

        public static HexNode create() {
            return HexNodeFactory.create();
        }
    }

    // callable(object)
    @Builtin(name = J_CALLABLE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class CallableNode extends PythonBuiltinNode {

        @Specialization(guards = "isCallable(callable)")
        boolean doCallable(@SuppressWarnings("unused") Object callable) {
            return true;
        }

        @Specialization
        boolean doGeneric(Object object,
                        @Cached PyCallableCheckNode callableCheck) {
            /*
             * Added temporarily to skip translation/execution errors in unit testing
             */

            if (GraalPythonTranslationErrorNode.T_MESSAGE.equals(object)) {
                return true;
            }

            return callableCheck.execute(object);
        }
    }

    // chr(i)
    @Builtin(name = J_CHR, minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"i"})
    @GenerateNodeFactory
    @ArgumentClinic(name = "i", conversion = ArgumentClinic.ClinicConversion.Int)
    public abstract static class ChrNode extends PythonUnaryClinicBuiltinNode {
        @Specialization
        public TruffleString charFromInt(int arg,
                        @Cached TruffleString.FromCodePointNode fromCodePointNode) {
            if (arg >= 0 && arg <= 1114111) {
                return fromCodePointNode.execute(arg, TS_ENCODING, true);
            } else {
                throw raise(ValueError, ErrorMessages.ARG_NOT_IN_RANGE, "chr()", "0x110000");
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BuiltinFunctionsClinicProviders.ChrNodeClinicProviderGen.INSTANCE;
        }
    }

    // hash(object)
    @Builtin(name = J_HASH, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class HashNode extends PythonUnaryBuiltinNode {
        @Specialization
        long hash(VirtualFrame frame, Object object,
                        @Cached PyObjectHashNode hashNode) {
            return hashNode.execute(frame, object);
        }
    }

    // dir([object])
    @Builtin(name = J_DIR, minNumOfPositionalArgs = 0, maxNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class DirNode extends PythonBuiltinNode {

        // logic like in 'Objects/object.c: _dir_locals'
        @Specialization(guards = "isNoValue(object)")
        Object locals(VirtualFrame frame, @SuppressWarnings("unused") Object object,
                        @Cached ReadLocalsNode readLocalsNode,
                        @Cached ReadCallerFrameNode readCallerFrameNode,
                        @Cached MaterializeFrameNode materializeNode,
                        @Cached ConditionProfile inGenerator,
                        @Cached("create(T_KEYS)") LookupAndCallUnaryNode callKeysNode,
                        @Cached ListBuiltins.ListSortNode sortNode,
                        @Cached ListNodes.ConstructListNode constructListNode) {

            Object localsDict = LocalsNode.getLocalsDict(frame, this, readLocalsNode, readCallerFrameNode, materializeNode, inGenerator);
            Object keysObj = callKeysNode.executeObject(frame, localsDict);
            PList list = constructListNode.execute(frame, keysObj);
            sortNode.execute(frame, list);
            return list;
        }

        @Specialization(guards = "!isNoValue(object)")
        Object dir(VirtualFrame frame, Object object,
                        @Cached PyObjectDir dir) {
            return dir.execute(frame, object);
        }
    }

    // divmod(a, b)
    @Builtin(name = J_DIVMOD, minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    @ImportStatic(BinaryArithmetic.class)
    public abstract static class DivModNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "b != 0")
        public PTuple doLong(long a, long b) {
            return factory().createTuple(new Object[]{Math.floorDiv(a, b), Math.floorMod(a, b)});
        }

        @Specialization(replaces = "doLong")
        public PTuple doLongZero(long a, long b) {
            if (b == 0) {
                throw raise(PythonErrorType.ZeroDivisionError, ErrorMessages.INTEGER_DIVISION_BY_ZERO);
            }
            return factory().createTuple(new Object[]{Math.floorDiv(a, b), Math.floorMod(a, b)});
        }

        @Specialization
        public PTuple doDouble(double a, double b) {
            if (b == 0) {
                throw raise(PythonErrorType.ZeroDivisionError, ErrorMessages.DIVISION_BY_ZERO);
            }
            double q = Math.floor(a / b);
            return factory().createTuple(new Object[]{q, FloatBuiltins.ModNode.op(a, b)});
        }

        @Specialization
        public Object doObject(VirtualFrame frame, Object a, Object b,
                        @Cached("DivMod.create()") BinaryOpNode callDivmod) {
            return callDivmod.executeObject(frame, a, b);
        }
    }

    // eval(expression, globals=None, locals=None)
    @Builtin(name = J_EVAL, minNumOfPositionalArgs = 1, parameterNames = {"expression", "globals", "locals"})
    @GenerateNodeFactory
    public abstract static class EvalNode extends PythonBuiltinNode {
        @Child protected CompileNode compileNode;
        @Child private GenericInvokeNode invokeNode = GenericInvokeNode.create();
        @Child private PyMappingCheckNode mappingCheckNode;
        @Child private GetOrCreateDictNode getOrCreateDictNode;

        protected void assertNoFreeVars(PCode code) {
            Object[] freeVars = code.getFreeVars();
            if (freeVars.length > 0) {
                throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.CODE_OBJ_NO_FREE_VARIABLES, getMode());
            }
        }

        protected TruffleString getMode() {
            return T_EVAL;
        }

        protected boolean isMapping(Object object) {
            if (mappingCheckNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                mappingCheckNode = insert(PyMappingCheckNode.create());
            }
            return mappingCheckNode.execute(object);
        }

        protected boolean isAnyNone(Object object) {
            return object instanceof PNone;
        }

        protected PCode createAndCheckCode(VirtualFrame frame, Object source) {
            PCode code = getCompileNode().compile(frame, source, T_STRING_SOURCE, getMode(), -1, -1);
            assertNoFreeVars(code);
            return code;
        }

        private static void inheritGlobals(PFrame callerFrame, Object[] args) {
            PArguments.setGlobals(args, callerFrame.getGlobals());
        }

        private static void inheritLocals(VirtualFrame frame, PFrame callerFrame, Object[] args, ReadLocalsNode getLocalsNode) {
            Object callerLocals = getLocalsNode.execute(frame, callerFrame);
            setCustomLocals(args, callerLocals);
        }

        private static void setCustomLocals(Object[] args, Object locals) {
            PArguments.setSpecialArgument(args, locals);
            PArguments.setCustomLocals(args, locals);
        }

        private void setBuiltinsInGlobals(VirtualFrame frame, PDict globals, HashingCollectionNodes.SetItemNode setBuiltins, PythonModule builtins) {
            if (builtins != null) {
                PDict builtinsDict = getOrCreateDictNode(builtins);
                setBuiltins.execute(frame, globals, T___BUILTINS__, builtinsDict);
            } else {
                // This happens during context initialization
                return;
            }
        }

        private void setCustomGlobals(VirtualFrame frame, PDict globals, HashingCollectionNodes.SetItemNode setBuiltins, Object[] args) {
            PythonModule builtins = getContext().getBuiltins();
            setBuiltinsInGlobals(frame, globals, setBuiltins, builtins);
            PArguments.setGlobals(args, globals);
        }

        @Specialization
        Object execInheritGlobalsInheritLocals(VirtualFrame frame, Object source, @SuppressWarnings("unused") PNone globals, @SuppressWarnings("unused") PNone locals,
                        @Cached ReadCallerFrameNode readCallerFrameNode,
                        @Cached ReadLocalsNode getLocalsNode,
                        @Shared("getCt") @Cached CodeNodes.GetCodeCallTargetNode getCt) {
            PCode code = createAndCheckCode(frame, source);
            PFrame callerFrame = readCallerFrameNode.executeWith(frame, 0);
            Object[] args = PArguments.create();
            inheritGlobals(callerFrame, args);
            inheritLocals(frame, callerFrame, args, getLocalsNode);

            return invokeNode.execute(frame, getCt.execute(code), args);
        }

        @Specialization
        Object execCustomGlobalsGlobalLocals(VirtualFrame frame, Object source, PDict globals, @SuppressWarnings("unused") PNone locals,
                        @Cached HashingCollectionNodes.SetItemNode setBuiltins,
                        @Shared("getCt") @Cached CodeNodes.GetCodeCallTargetNode getCt) {
            PCode code = createAndCheckCode(frame, source);
            Object[] args = PArguments.create();
            setCustomGlobals(frame, globals, setBuiltins, args);
            setCustomLocals(args, globals);
            RootCallTarget rootCallTarget = getCt.execute(code);
            if (rootCallTarget == null) {
                throw raise(ValueError, ErrorMessages.CANNOT_CREATE_CALL_TARGET, code);
            }

            return invokeNode.execute(frame, rootCallTarget, args);
        }

        @Specialization(guards = {"isMapping(locals)"})
        Object execInheritGlobalsCustomLocals(VirtualFrame frame, Object source, @SuppressWarnings("unused") PNone globals, Object locals,
                        @Cached ReadCallerFrameNode readCallerFrameNode,
                        @Shared("getCt") @Cached CodeNodes.GetCodeCallTargetNode getCt) {
            PCode code = createAndCheckCode(frame, source);
            PFrame callerFrame = readCallerFrameNode.executeWith(frame, 0);
            Object[] args = PArguments.create();
            inheritGlobals(callerFrame, args);
            setCustomLocals(args, locals);

            return invokeNode.execute(frame, getCt.execute(code), args);
        }

        @Specialization(guards = {"isMapping(locals)"})
        Object execCustomGlobalsCustomLocals(VirtualFrame frame, Object source, PDict globals, Object locals,
                        @Cached HashingCollectionNodes.SetItemNode setBuiltins,
                        @Shared("getCt") @Cached CodeNodes.GetCodeCallTargetNode getCt) {
            PCode code = createAndCheckCode(frame, source);
            Object[] args = PArguments.create();
            setCustomGlobals(frame, globals, setBuiltins, args);
            setCustomLocals(args, locals);

            return invokeNode.execute(frame, getCt.execute(code), args);
        }

        @Specialization(guards = {"!isAnyNone(globals)", "!isDict(globals)"})
        PNone badGlobals(@SuppressWarnings("unused") Object source, Object globals, @SuppressWarnings("unused") Object locals) {
            throw raise(TypeError, ErrorMessages.GLOBALS_MUST_BE_DICT, getMode(), globals);
        }

        @Specialization(guards = {"isAnyNone(globals) || isDict(globals)", "!isAnyNone(locals)", "!isMapping(locals)"})
        PNone badLocals(@SuppressWarnings("unused") Object source, @SuppressWarnings("unused") PDict globals, Object locals) {
            throw raise(TypeError, ErrorMessages.LOCALS_MUST_BE_MAPPING, getMode(), locals);
        }

        private CompileNode getCompileNode() {
            if (compileNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                compileNode = insert(CompileNode.create(false, shouldStripLeadingWhitespace()));
            }
            return compileNode;
        }

        private PDict getOrCreateDictNode(PythonObject object) {
            if (getOrCreateDictNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getOrCreateDictNode = insert(GetOrCreateDictNode.create());
            }
            return getOrCreateDictNode.execute(object);
        }

        protected boolean shouldStripLeadingWhitespace() {
            return true;
        }
    }

    @Builtin(name = J_EXEC, minNumOfPositionalArgs = 1, parameterNames = {"source", "globals", "locals"})
    @GenerateNodeFactory
    abstract static class ExecNode extends EvalNode {
        protected abstract Object executeInternal(VirtualFrame frame);

        @Override
        protected TruffleString getMode() {
            return T_EXEC;
        }

        @Override
        public final Object execute(VirtualFrame frame) {
            executeInternal(frame);
            return PNone.NONE;
        }

        @Override
        protected boolean shouldStripLeadingWhitespace() {
            return false;
        }
    }

    // compile(source, filename, mode, flags=0, dont_inherit=False, optimize=-1)
    @Builtin(name = J_COMPILE, minNumOfPositionalArgs = 3, parameterNames = {"source", "filename", "mode", "flags", "dont_inherit", "optimize", "_feature_version"})
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class CompileNode extends PythonBuiltinNode {

        // code.h
        private static final int CO_NESTED = 0x0010;
        private static final int CO_FUTURE_DIVISION = 0x20000;
        private static final int CO_FUTURE_ABSOLUTE_IMPORT = 0x40000;
        private static final int CO_FUTURE_WITH_STATEMENT = 0x80000;
        private static final int CO_FUTURE_PRINT_FUNCTION = 0x100000;
        private static final int CO_FUTURE_UNICODE_LITERALS = 0x200000;

        private static final int CO_FUTURE_BARRY_AS_BDFL = 0x400000;
        private static final int CO_FUTURE_GENERATOR_STOP = 0x800000;
        private static final int CO_FUTURE_ANNOTATIONS = 0x1000000;

        // compile.h
        private static final int PyCF_MASK = CO_FUTURE_DIVISION | CO_FUTURE_ABSOLUTE_IMPORT | CO_FUTURE_WITH_STATEMENT | CO_FUTURE_PRINT_FUNCTION | CO_FUTURE_UNICODE_LITERALS |
                        CO_FUTURE_BARRY_AS_BDFL | CO_FUTURE_GENERATOR_STOP | CO_FUTURE_ANNOTATIONS;
        private static final int PyCF_MASK_OBSOLETE = CO_NESTED;

        private static final int PyCF_DONT_IMPLY_DEDENT = 0x0200;
        public static final int PyCF_ONLY_AST = 0x0400;
        public static final int PyCF_TYPE_COMMENTS = 0x1000;
        public static final int PyCF_ALLOW_TOP_LEVEL_AWAIT = 0x2000;
        private static final int PyCF_ALLOW_INCOMPLETE_INPUT = 0x4000;
        private static final int PyCF_COMPILE_MASK = PyCF_ONLY_AST | PyCF_ALLOW_TOP_LEVEL_AWAIT | PyCF_TYPE_COMMENTS | PyCF_DONT_IMPLY_DEDENT | PyCF_ALLOW_INCOMPLETE_INPUT;

        private static final TruffleString T_SINGLE = tsLiteral("single");
        private static final TruffleString T_FUNC_EVAL = tsLiteral("func_eval");

        /**
         * Decides whether this node should attempt to map the filename to a URI for the benefit of
         * Truffle tooling
         */
        private final boolean mayBeFromFile;
        private final boolean lstrip;

        public CompileNode(boolean mayBeFromFile, boolean lstrip) {
            this.mayBeFromFile = mayBeFromFile;
            this.lstrip = lstrip;
        }

        public CompileNode() {
            this.mayBeFromFile = true;
            this.lstrip = false;
        }

        public final PCode compile(VirtualFrame frame, Object source, TruffleString filename, TruffleString mode, Object kwOptimize, int featureVersion) {
            return (PCode) executeInternal(frame, source, filename, mode, 0, false, kwOptimize, featureVersion);
        }

        protected abstract Object executeInternal(VirtualFrame frame, Object source, TruffleString filename, TruffleString mode, Object kwFlags, Object kwDontInherit, Object kwOptimize,
                        Object featureVersion);

        @SuppressWarnings("unused")
        @Specialization
        @TruffleBoundary
        Object compile(TruffleString expression, TruffleString filename, TruffleString mode, int kwFlags, Object kwDontInherit, int kwOptimize, int featureVersion) {
            checkFlags(kwFlags);
            checkOptimize(kwOptimize, kwOptimize);
            checkSource(expression);

            TruffleString code = expression;
            PythonContext context = getContext();
            ParserMode pm;
            if (mode.equalsUncached(T_EXEC, TS_ENCODING)) {
                pm = ParserMode.File;
                // CPython adds a newline and we need to do the same in order to produce
                // SyntaxError with the same offset when the line is incomplete.
                // The new parser does this on its own - we must not add a newline here since
                // that would lead to incorrect line number for the ENDMARKER token.
                if (!context.getOption(PythonOptions.EnableBytecodeInterpreter)) {
                    if (code.isEmpty() || code.codePointAtIndexUncached(code.codePointLengthUncached(TS_ENCODING) - 1, TS_ENCODING) != '\n') {
                        code = code.concatUncached(T_NEWLINE, TS_ENCODING, true);
                    }
                }
            } else if (mode.equalsUncached(T_EVAL, TS_ENCODING)) {
                pm = ParserMode.Eval;
            } else if (mode.equalsUncached(T_SINGLE, TS_ENCODING)) {
                pm = ParserMode.Statement;
            } else if (mode.equalsUncached(T_FUNC_EVAL, TS_ENCODING)) {
                if ((kwFlags & PyCF_ONLY_AST) == 0) {
                    throw raise(ValueError, ErrorMessages.COMPILE_MODE_FUNC_TYPE_REQUIED_FLAG_ONLY_AST);
                }
                pm = ParserMode.FuncType;
            } else {
                if ((kwFlags & PyCF_ONLY_AST) != 0) {
                    throw raise(ValueError, ErrorMessages.COMPILE_MODE_MUST_BE_AST_ONLY);
                } else {
                    throw raise(ValueError, ErrorMessages.COMPILE_MODE_MUST_BE);
                }
            }
            if (lstrip && !code.isEmpty()) {
                int c = code.codePointAtIndexUncached(0, TS_ENCODING);
                if (c == ' ' || c == '\t') {
                    code = code.substringUncached(1, code.codePointLengthUncached(TS_ENCODING) - 1, TS_ENCODING, true);
                }
            }
            if ((kwFlags & PyCF_ONLY_AST) != 0) {
                InputType type;
                switch (pm) {
                    case File:
                        type = InputType.FILE;
                        break;
                    case Eval:
                        type = InputType.EVAL;
                        break;
                    case Statement:
                        type = InputType.SINGLE;
                        break;
                    case FuncType:
                        type = InputType.FUNCTION_TYPE;
                        break;
                    default:
                        throw CompilerDirectives.shouldNotReachHere();
                }
                Source source = PythonLanguage.newSource(context, code, filename, mayBeFromFile, PythonLanguage.MIME_TYPE);
                RaisePythonExceptionErrorCallback errorCb = new RaisePythonExceptionErrorCallback(source, PythonOptions.isPExceptionWithJavaStacktrace(getLanguage()));

                EnumSet<AbstractParser.Flags> flags = EnumSet.noneOf(AbstractParser.Flags.class);
                if ((kwFlags & PyCF_TYPE_COMMENTS) != 0) {
                    flags.add(AbstractParser.Flags.TYPE_COMMENTS);
                }
                Parser parser = Compiler.createParser(code.toJavaStringUncached(), errorCb, type, flags, featureVersion >= 0 ? featureVersion : PythonLanguage.MINOR);
                ModTy mod = (ModTy) parser.parse();
                errorCb.triggerDeprecationWarnings();
                return AstModuleBuiltins.sst2Obj(getContext(), mod);
            }
            CallTarget ct;
            TruffleString finalCode = code;
            Supplier<CallTarget> createCode = () -> {
                if (pm == ParserMode.File) {
                    Source source = PythonLanguage.newSource(context, finalCode, filename, mayBeFromFile, PythonLanguage.getCompileMimeType(kwOptimize));
                    return context.getEnv().parsePublic(source);
                } else if (pm == ParserMode.Eval) {
                    Source source = PythonLanguage.newSource(context, finalCode, filename, mayBeFromFile, PythonLanguage.getEvalMimeType(kwOptimize));
                    return context.getEnv().parsePublic(source);
                } else {
                    Source source = PythonLanguage.newSource(context, finalCode, filename, mayBeFromFile, PythonLanguage.MIME_TYPE);
                    if (context.getOption(PythonOptions.EnableBytecodeInterpreter)) {
                        return context.getLanguage().parseForBytecodeInterpreter(context, source, InputType.SINGLE, false, kwOptimize, false, null);
                    }
                    return PythonUtils.getOrCreateCallTarget((RootNode) getCore().getParser().parse(pm, kwOptimize, getCore(), source, null, null));
                }
            };
            if (getCore().isCoreInitialized()) {
                ct = createCode.get();
            } else {
                ct = getCore().getLanguage().cacheCode(filename, createCode);
            }
            return wrapRootCallTarget((RootCallTarget) ct);
        }

        @Specialization(limit = "3")
        Object generic(VirtualFrame frame, Object wSource, Object wFilename, Object wMode, Object kwFlags, Object kwDontInherit, Object kwOptimize, Object kwFeatureVersion,
                        @CachedLibrary(limit = "3") PythonBufferAcquireLibrary acquireLib,
                        @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Cached CastToTruffleStringNode castStr,
                        @Cached CastToJavaIntExactNode castInt,
                        @Cached CodecsModuleBuiltins.HandleDecodingErrorNode handleDecodingErrorNode,
                        @Cached PyObjectStrAsTruffleStringNode asStrNode,
                        @CachedLibrary("wSource") InteropLibrary interopLib,
                        @Cached PyUnicodeFSDecoderNode asPath,
                        @Cached WarnNode warnNode,
                        @Cached TruffleString.FromByteArrayNode fromByteArrayNode,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode) {
            if (wSource instanceof PCode) {
                return wSource;
            }
            TruffleString filename;
            // TODO use PyUnicode_FSDecode
            if (acquireLib.hasBuffer(wFilename)) {
                Object filenameBuffer = acquireLib.acquireReadonly(wFilename, frame, this);
                try {
                    TruffleString utf8 = fromByteArrayNode.execute(bufferLib.getCopiedByteArray(filenameBuffer), Encoding.UTF_8, false);
                    filename = switchEncodingNode.execute(utf8, TS_ENCODING);
                    if (!(wFilename instanceof PBytes)) {
                        warnNode.warnFormat(frame, null, DeprecationWarning, 1, ErrorMessages.PATH_SHOULD_BE_STR_BYTES_PATHLIKE_NOT_P, wFilename);
                    }
                } finally {
                    bufferLib.release(filenameBuffer, frame, this);
                }
            } else {
                filename = asPath.execute(frame, wFilename);
            }
            TruffleString mode;
            try {
                mode = castStr.execute(wMode);
            } catch (CannotCastException e) {
                throw raise(TypeError, ErrorMessages.ARG_S_MUST_BE_S_NOT_P, "compile()", "mode", "str", wMode);
            }
            int flags = 0;
            if (kwFlags != PNone.NO_VALUE) {
                try {
                    flags = castInt.execute(kwFlags);
                } catch (CannotCastException e) {
                    throw raise(TypeError, ErrorMessages.INTEGER_REQUIRED_GOT, kwFlags);
                }
                checkFlags(flags);
            }
            int optimize = 0;
            if (kwOptimize != PNone.NO_VALUE) {
                try {
                    optimize = castInt.execute(kwOptimize);
                } catch (CannotCastException e) {
                    throw raise(TypeError, ErrorMessages.INTEGER_REQUIRED_GOT, kwFlags);
                }
                checkOptimize(optimize, kwOptimize);
            }
            int featureVersion = -1;
            if (kwFeatureVersion != PNone.NO_VALUE) {
                try {
                    featureVersion = castInt.execute(kwFeatureVersion);
                } catch (CannotCastException e) {
                    throw raise(TypeError, ErrorMessages.INTEGER_REQUIRED_GOT, kwFlags);
                }
            }
            if (AstModuleBuiltins.isAst(getContext(), wSource)) {
                ModTy mod = AstModuleBuiltins.obj2sst(getContext(), wSource);
                Source source = PythonUtils.createFakeSource();
                RootCallTarget rootCallTarget = getLanguage().compileForBytecodeInterpreter(getContext(), mod, source, false, optimize, null, null);
                return wrapRootCallTarget(rootCallTarget);
            }
            TruffleString source = sourceAsString(frame, wSource, filename, interopLib, acquireLib, bufferLib, handleDecodingErrorNode, asStrNode, switchEncodingNode);
            checkSource(source);
            return compile(source, filename, mode, flags, kwDontInherit, optimize, featureVersion);
        }

        private PCode wrapRootCallTarget(RootCallTarget rootCallTarget) {
            RootNode rootNode = rootCallTarget.getRootNode();
            if (rootNode instanceof PBytecodeRootNode) {
                ((PBytecodeRootNode) rootNode).triggerDeferredDeprecationWarnings();
            } else if (rootNode instanceof PRootNode) {
                ((PRootNode) rootNode).triggerDeprecationWarnings();
            }
            return factory().createCode(rootCallTarget);
        }

        private void checkSource(TruffleString source) throws PException {
            if (source.indexOfCodePointUncached(0, 0, source.codePointLengthUncached(TS_ENCODING), TS_ENCODING) > -1) {
                throw raise(ValueError, ErrorMessages.SRC_CODE_CANNOT_CONTAIN_NULL_BYTES);
            }
        }

        private void checkOptimize(int optimize, Object kwOptimize) throws PException {
            if (optimize < -1 || optimize > 2) {
                throw raise(TypeError, ErrorMessages.INVALID_OPTIMIZE_VALUE, kwOptimize);
            }
        }

        private void checkFlags(int flags) {
            if ((flags & ~(PyCF_MASK | PyCF_MASK_OBSOLETE | PyCF_COMPILE_MASK)) != 0) {
                throw raise(ValueError, ErrorMessages.UNRECOGNIZED_FLAGS);
            }
        }

        // modeled after _Py_SourceAsString
        TruffleString sourceAsString(VirtualFrame frame, Object source, TruffleString filename, InteropLibrary interopLib, PythonBufferAcquireLibrary acquireLib, PythonBufferAccessLibrary bufferLib,
                        CodecsModuleBuiltins.HandleDecodingErrorNode handleDecodingErrorNode, PyObjectStrAsTruffleStringNode asStrNode, TruffleString.SwitchEncodingNode switchEncodingNode) {
            if (interopLib.isString(source)) {
                try {
                    return switchEncodingNode.execute(interopLib.asTruffleString(source), TS_ENCODING);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            } else {
                // cpython checks for bytes and bytearray separately, but we deal with it as
                // buffers, since that's fast for us anyway
                Object buffer;
                try {
                    buffer = acquireLib.acquireReadonly(source, frame, this);
                } catch (PException e) {
                    throw raise(TypeError, ErrorMessages.ARG_D_MUST_BE_S, "compile()", 1, "string, bytes or AST object");
                }
                try {
                    byte[] bytes = bufferLib.getInternalOrCopiedByteArray(source);
                    int bytesLen = bufferLib.getBufferLength(source);
                    Charset charset = PythonFileDetector.findEncodingStrict(bytes, bytesLen);
                    TruffleString pythonEncodingNameFromJavaName = CharsetMapping.getPythonEncodingNameFromJavaName(charset.name());
                    CodecsModuleBuiltins.TruffleDecoder decoder = new CodecsModuleBuiltins.TruffleDecoder(pythonEncodingNameFromJavaName, charset, bytes, bytesLen, CodingErrorAction.REPORT);
                    if (!decoder.decodingStep(true)) {
                        try {
                            handleDecodingErrorNode.execute(decoder, T_STRICT, source);
                            throw CompilerDirectives.shouldNotReachHere();
                        } catch (PException e) {
                            throw raiseInvalidSyntax(filename, ErrorMessages.UNICODE_ERROR, asStrNode.execute(frame, e.getEscapedException()));
                        }
                    }
                    return decoder.getString();
                } catch (PythonFileDetector.InvalidEncodingException e) {
                    throw raiseInvalidSyntax(filename, ErrorMessages.ENCODING_PROBLEM, e.getEncodingName());
                } finally {
                    bufferLib.release(buffer, frame, this);
                }
            }
        }

        @TruffleBoundary
        private RuntimeException raiseInvalidSyntax(TruffleString filename, TruffleString format, Object... args) {
            PythonContext context = getContext();
            // Create non-empty source to avoid overwriting the message with "unexpected EOF"
            Source source = PythonLanguage.newSource(context, T_SPACE, filename, mayBeFromFile, null);
            throw getCore().raiseInvalidSyntax(source, source.createUnavailableSection(), format, args);
        }

        public static CompileNode create(boolean mapFilenameToUri) {
            return BuiltinFunctionsFactory.CompileNodeFactory.create(mapFilenameToUri, false, new ReadArgumentNode[]{});
        }

        public static CompileNode create(boolean mapFilenameToUri, boolean lstrip) {
            return BuiltinFunctionsFactory.CompileNodeFactory.create(mapFilenameToUri, lstrip, new ReadArgumentNode[]{});
        }
    }

    // delattr(object, name)
    @Builtin(name = J_DELATTR, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class DelAttrNode extends PythonBinaryBuiltinNode {
        @Child private DeleteAttributeNode delNode = DeleteAttributeNode.create();

        @Specialization
        Object delattr(VirtualFrame frame, Object object, Object name) {
            delNode.execute(frame, object, name);
            return PNone.NONE;
        }
    }

    // getattr(object, name[, default])
    @Builtin(name = J_GETATTR, minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class GetAttrNode extends PythonTernaryBuiltinNode {
        public static GetAttrNode create() {
            return GetAttrNodeFactory.create();
        }

        public abstract Object executeWithArgs(VirtualFrame frame, Object primary, TruffleString name, Object defaultValue);

        @SuppressWarnings("unused")
        @Specialization(limit = "getAttributeAccessInlineCacheMaxDepth()", guards = {"stringEquals(cachedName, name, equalNode, stringProfile)", "isNoValue(defaultValue)"})
        public Object getAttrDefault(VirtualFrame frame, Object primary, TruffleString name, PNone defaultValue,
                        @Cached ConditionProfile stringProfile,
                        @Cached TruffleString.EqualNode equalNode,
                        @Cached("name") TruffleString cachedName,
                        @Cached("create(name)") GetFixedAttributeNode getAttributeNode) {
            return getAttributeNode.executeObject(frame, primary);
        }

        @SuppressWarnings("unused")
        @Specialization(limit = "getAttributeAccessInlineCacheMaxDepth()", guards = {"stringEquals(cachedName, name, equalNode, stringProfile)", "!isNoValue(defaultValue)"})
        Object getAttr(VirtualFrame frame, Object primary, TruffleString name, Object defaultValue,
                        @Cached ConditionProfile stringProfile,
                        @Cached TruffleString.EqualNode equalNode,
                        @Cached("name") TruffleString cachedName,
                        @Cached("create(name)") GetFixedAttributeNode getAttributeNode,
                        @Cached IsBuiltinClassProfile errorProfile) {
            try {
                return getAttributeNode.executeObject(frame, primary);
            } catch (PException e) {
                e.expectAttributeError(errorProfile);
                return defaultValue;
            }
        }

        @Specialization(replaces = {"getAttr", "getAttrDefault"}, guards = "isNoValue(defaultValue)")
        Object getAttrFromObject(VirtualFrame frame, Object primary, TruffleString name, @SuppressWarnings("unused") PNone defaultValue,
                        @Cached GetAnyAttributeNode getAttributeNode) {
            return getAttributeNode.executeObject(frame, primary, name);
        }

        @Specialization(replaces = {"getAttr", "getAttrDefault"}, guards = "!isNoValue(defaultValue)")
        Object getAttrFromObject(VirtualFrame frame, Object primary, TruffleString name, Object defaultValue,
                        @Cached GetAnyAttributeNode getAttributeNode,
                        @Cached IsBuiltinClassProfile errorProfile) {
            try {
                return getAttributeNode.executeObject(frame, primary, name);
            } catch (PException e) {
                e.expectAttributeError(errorProfile);
                return defaultValue;
            }
        }

        @Specialization
        Object getAttr2(VirtualFrame frame, Object object, PString name, Object defaultValue) {
            return executeWithArgs(frame, object, name.getValueUncached(), defaultValue);
        }

        @Specialization(guards = "!isString(name)")
        @SuppressWarnings("unused")
        Object getAttrGeneric(Object primary, Object name, Object defaultValue) {
            throw raise(TypeError, ErrorMessages.GETATTR_ATTRIBUTE_NAME_MUST_BE_STRING);
        }
    }

    // id(object)
    @Builtin(name = J_ID, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IdNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object doObject(Object value,
                        @Cached ObjectNodes.GetIdNode getIdNode) {
            return getIdNode.execute(value);
        }
    }

    /**
     * Base class for {@code isinstance} and {@code issubclass} that implements the recursive
     * iteration of tuples passed as the second argument. The inheriting classes need to just
     * provide the base for the recursion.
     */
    public abstract static class RecursiveBinaryCheckBaseNode extends PythonBinaryBuiltinNode {
        static final int MAX_EXPLODE_LOOP = 16; // is also shifted to the left by recursion depth
        static final byte NON_RECURSIVE = Byte.MAX_VALUE;

        @Child private SequenceStorageNodes.LenNode lenNode;
        @Child private GetObjectArrayNode getObjectArrayNode;
        protected final byte depth;

        protected RecursiveBinaryCheckBaseNode(byte depth) {
            this.depth = depth;
        }

        public abstract boolean executeWith(VirtualFrame frame, Object instance, Object cls);

        protected final RecursiveBinaryCheckBaseNode createRecursive() {
            return createRecursive((byte) (depth + 1));
        }

        protected final RecursiveBinaryCheckBaseNode createNonRecursive() {
            return createRecursive(NON_RECURSIVE);
        }

        protected RecursiveBinaryCheckBaseNode createRecursive(@SuppressWarnings("unused") byte newDepth) {
            throw new AbstractMethodError(); // Cannot be really abstract b/c Truffle DSL...
        }

        protected int getMaxExplodeLoop() {
            return MAX_EXPLODE_LOOP >> depth;
        }

        @Specialization(guards = {"depth < getNodeRecursionLimit()", "getLength(clsTuple) == cachedLen", "cachedLen < getMaxExplodeLoop()"}, //
                        limit = "getVariableArgumentInlineCacheLimit()")
        @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL_UNTIL_RETURN)
        final boolean doTupleConstantLen(VirtualFrame frame, Object instance, PTuple clsTuple,
                        @Cached("getLength(clsTuple)") int cachedLen,
                        @Cached("createRecursive()") RecursiveBinaryCheckBaseNode recursiveNode) {
            Object[] array = getArray(clsTuple);
            for (int i = 0; i < cachedLen; i++) {
                Object cls = array[i];
                if (recursiveNode.executeWith(frame, instance, cls)) {
                    return true;
                }
            }
            return false;
        }

        @Specialization(guards = "depth < getNodeRecursionLimit()", replaces = "doTupleConstantLen")
        final boolean doRecursiveWithNode(VirtualFrame frame, Object instance, PTuple clsTuple,
                        @Cached("createRecursive()") RecursiveBinaryCheckBaseNode recursiveNode) {
            for (Object cls : getArray(clsTuple)) {
                if (recursiveNode.executeWith(frame, instance, cls)) {
                    return true;
                }
            }
            return false;
        }

        @Specialization(guards = {"depth != NON_RECURSIVE", "depth >= getNodeRecursionLimit()"})
        final boolean doRecursiveWithLoop(VirtualFrame frame, Object instance, PTuple clsTuple,
                        @Cached("createNonRecursive()") RecursiveBinaryCheckBaseNode node) {
            PythonLanguage language = PythonLanguage.get(this);
            Object state = IndirectCallContext.enter(frame, language, getContext(), this);
            try {
                // Note: we need actual recursion to trigger the stack overflow error like CPython
                // Note: we need fresh RecursiveBinaryCheckBaseNode and cannot use "this", because
                // other children of this executed by other specializations may assume they'll
                // always get a non-null frame
                return callRecursiveWithNodeTruffleBoundary(instance, clsTuple, node);
            } finally {
                IndirectCallContext.exit(frame, PythonLanguage.get(this), getContext(), state);
            }
        }

        @Specialization(guards = "depth == NON_RECURSIVE")
        final boolean doRecursiveWithLoopReuseThis(VirtualFrame frame, Object instance, PTuple clsTuple) {
            // This should be only called by doRecursiveWithLoop, now we have to reuse this to stop
            // recursive node creation. It is OK, because now all specializations should always get
            // null frame
            assert frame == null;
            return callRecursiveWithNodeTruffleBoundary(instance, clsTuple, this);
        }

        @TruffleBoundary
        private boolean callRecursiveWithNodeTruffleBoundary(Object instance, PTuple clsTuple, RecursiveBinaryCheckBaseNode node) {
            return doRecursiveWithNode(null, instance, clsTuple, node);
        }

        protected final int getLength(PTuple t) {
            if (lenNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lenNode = insert(SequenceStorageNodes.LenNode.create());
            }
            return lenNode.execute(t.getSequenceStorage());
        }

        private Object[] getArray(PTuple tuple) {
            if (getObjectArrayNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getObjectArrayNode = insert(GetObjectArrayNodeGen.create());
            }
            return getObjectArrayNode.execute(tuple);
        }
    }

    // isinstance(object, classinfo)
    @Builtin(name = J_ISINSTANCE, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class IsInstanceNode extends RecursiveBinaryCheckBaseNode {

        protected IsInstanceNode(byte depth) {
            super(depth);
        }

        protected IsInstanceNode() {
            this((byte) 0);
        }

        @Override
        public IsInstanceNode createRecursive(byte newDepth) {
            return BuiltinFunctionsFactory.IsInstanceNodeFactory.create(newDepth);
        }

        private static TriState isInstanceCheckInternal(VirtualFrame frame, Object instance, Object cls, LookupAndCallBinaryNode instanceCheckNode, CoerceToBooleanNode castToBooleanNode) {
            Object instanceCheckResult = instanceCheckNode.executeObject(frame, cls, instance);
            if (instanceCheckResult == NOT_IMPLEMENTED) {
                return TriState.UNDEFINED;
            }
            return TriState.valueOf(castToBooleanNode.executeBoolean(frame, instanceCheckResult));
        }

        @Specialization(guards = "isPythonClass(cls)")
        static boolean isInstance(VirtualFrame frame, Object instance, Object cls,
                        @Shared("instanceCheck") @Cached("create(InstanceCheck)") LookupAndCallBinaryNode instanceCheckNode,
                        @Shared("boolCast") @Cached("createIfTrueNode()") CoerceToBooleanNode castToBooleanNode,
                        @Cached GetClassNode getClassNode,
                        @Cached TypeNodes.IsSameTypeNode isSameTypeNode,
                        @Cached IsSubtypeNode isSubtypeNode) {
            Object instanceClass = getClassNode.execute(instance);
            return isSameTypeNode.execute(instanceClass, cls) || isSubtypeNode.execute(frame, instanceClass, cls)//
                            || isInstanceCheckInternal(frame, instance, cls, instanceCheckNode, castToBooleanNode) == TriState.TRUE;
        }

        @Specialization(guards = {"!isPTuple(cls)", "!isPythonClass(cls)"})
        static boolean isInstance(VirtualFrame frame, Object instance, Object cls,
                        @Shared("instanceCheck") @Cached("create(InstanceCheck)") LookupAndCallBinaryNode instanceCheckNode,
                        @Shared("boolCast") @Cached("createIfTrueNode()") CoerceToBooleanNode castToBooleanNode,
                        @Cached TypeBuiltins.InstanceCheckNode typeInstanceCheckNode) {
            TriState check = isInstanceCheckInternal(frame, instance, cls, instanceCheckNode, castToBooleanNode);
            if (check == TriState.UNDEFINED) {
                return typeInstanceCheckNode.executeWith(frame, cls, instance);
            }
            return check == TriState.TRUE;
        }
    }

    // issubclass(class, classinfo)
    @Builtin(name = J_ISSUBCLASS, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class IsSubClassNode extends RecursiveBinaryCheckBaseNode {

        protected IsSubClassNode(byte depth) {
            super(depth);
        }

        protected IsSubClassNode() {
            this((byte) 0);
        }

        @Override
        public IsSubClassNode createRecursive(byte newDepth) {
            return BuiltinFunctionsFactory.IsSubClassNodeFactory.create(newDepth);
        }

        @Specialization(guards = "!isPTuple(cls)")
        static boolean isSubclass(VirtualFrame frame, Object derived, Object cls,
                        @Cached("create(Subclasscheck)") LookupAndCallBinaryNode subclassCheckNode,
                        @Cached("createIfTrueNode()") CoerceToBooleanNode castToBooleanNode,
                        @Cached IsSubtypeNode isSubtypeNode) {
            Object instanceCheckResult = subclassCheckNode.executeObject(frame, cls, derived);
            if (instanceCheckResult != NOT_IMPLEMENTED) {
                return castToBooleanNode.executeBoolean(frame, instanceCheckResult);
            }
            return isSubtypeNode.execute(frame, derived, cls);
        }
    }

    // iter(object[, sentinel])
    @Builtin(name = J_ITER, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @ReportPolymorphism
    public abstract static class IterNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(sentinel)")
        static Object iter(VirtualFrame frame, Object object, @SuppressWarnings("unused") PNone sentinel,
                        @Cached PyObjectGetIter getIter) {
            return getIter.execute(frame, object);
        }

        @Specialization(guards = {"callableCheck.execute(callable)", "!isNoValue(sentinel)"}, limit = "1")
        Object iter(Object callable, Object sentinel,
                        @SuppressWarnings("unused") @Cached PyCallableCheckNode callableCheck) {
            return factory().createSentinelIterator(callable, sentinel);
        }

        @Fallback
        @SuppressWarnings("unused")
        Object iterNotCallable(Object callable, Object sentinel) {
            throw raise(TypeError, ErrorMessages.ITER_V_MUST_BE_CALLABLE);
        }
    }

    // len(s)
    @Builtin(name = J_LEN, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class LenNode extends PythonUnaryBuiltinNode {
        @Specialization
        public int len(VirtualFrame frame, Object obj,
                        @Cached PyObjectSizeNode sizeNode) {
            return sizeNode.execute(frame, obj);
        }
    }

    public abstract static class MinMaxNode extends PythonBuiltinNode {

        @CompilationFinal private boolean seenNonBoolean = false;

        protected final BinaryComparisonNode createComparison() {
            if (this instanceof MaxNode) {
                return BinaryComparisonNode.GtNode.create();
            } else {
                return BinaryComparisonNode.LtNode.create();
            }
        }

        @Specialization(guards = "args.length == 0")
        Object maxSequence(VirtualFrame frame, Object arg1, Object[] args, @SuppressWarnings("unused") PNone key, Object defaultVal,
                        @Cached PyObjectGetIter getIter,
                        @Cached GetNextNode nextNode,
                        @Cached("createComparison()") BinaryComparisonNode compare,
                        @Cached("createIfTrueNode()") CoerceToBooleanNode castToBooleanNode,
                        @Cached IsBuiltinClassProfile errorProfile1,
                        @Cached IsBuiltinClassProfile errorProfile2,
                        @Cached ConditionProfile hasDefaultProfile) {
            return minmaxSequenceWithKey(frame, arg1, args, null, defaultVal, getIter, nextNode, compare, castToBooleanNode, null, errorProfile1, errorProfile2, hasDefaultProfile);
        }

        @Specialization(guards = {"args.length == 0", "!isPNone(keywordArg)"})
        Object minmaxSequenceWithKey(VirtualFrame frame, Object arg1, @SuppressWarnings("unused") Object[] args, Object keywordArg, Object defaultVal,
                        @Cached PyObjectGetIter getIter,
                        @Cached GetNextNode nextNode,
                        @Cached("createComparison()") BinaryComparisonNode compare,
                        @Cached("createIfTrueNode()") CoerceToBooleanNode castToBooleanNode,
                        @Cached CallNode keyCall,
                        @Cached IsBuiltinClassProfile errorProfile1,
                        @Cached IsBuiltinClassProfile errorProfile2,
                        @Cached ConditionProfile hasDefaultProfile) {
            Object iterator = getIter.execute(frame, arg1);
            Object currentValue;
            try {
                currentValue = nextNode.execute(frame, iterator);
            } catch (PException e) {
                e.expectStopIteration(errorProfile1);
                if (hasDefaultProfile.profile(PGuards.isNoValue(defaultVal))) {
                    throw raise(PythonErrorType.ValueError, ErrorMessages.ARG_IS_EMPTY_SEQ, getName());
                } else {
                    currentValue = defaultVal;
                }
            }
            Object currentKey = applyKeyFunction(frame, keywordArg, keyCall, currentValue);
            while (true) {
                Object nextValue;
                try {
                    nextValue = nextNode.execute(frame, iterator);
                } catch (PException e) {
                    e.expectStopIteration(errorProfile2);
                    break;
                }
                Object nextKey = applyKeyFunction(frame, keywordArg, keyCall, nextValue);
                boolean isTrue;
                if (!seenNonBoolean) {
                    try {
                        isTrue = compare.executeBool(frame, nextKey, currentKey);
                    } catch (UnexpectedResultException e) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        seenNonBoolean = true;
                        isTrue = castToBooleanNode.executeBoolean(frame, e.getResult());
                    }
                } else {
                    isTrue = castToBooleanNode.executeBoolean(frame, compare.executeObject(frame, nextKey, currentKey));
                }
                if (isTrue) {
                    currentKey = nextKey;
                    currentValue = nextValue;
                }
            }
            return currentValue;
        }

        private String getName() {
            return this instanceof MaxNode ? "max" : "min";
        }

        @Specialization(guards = "args.length != 0")
        Object minmaxBinary(VirtualFrame frame, Object arg1, Object[] args, @SuppressWarnings("unused") PNone keywordArg, Object defaultVal,
                        @Cached("createComparison()") BinaryComparisonNode compare,
                        @Cached ConditionProfile moreThanTwo,
                        @Shared("castToBooleanNode") @Cached("createIfTrueNode()") CoerceToBooleanNode castToBooleanNode,
                        @Shared("hasDefaultProfile") @Cached ConditionProfile hasDefaultProfile) {
            return minmaxBinaryWithKey(frame, arg1, args, null, defaultVal, compare, null, moreThanTwo, castToBooleanNode, hasDefaultProfile);
        }

        @Specialization(guards = {"args.length != 0", "!isPNone(keywordArg)"})
        Object minmaxBinaryWithKey(VirtualFrame frame, Object arg1, Object[] args, Object keywordArg, Object defaultVal,
                        @Cached("createComparison()") BinaryComparisonNode compare,
                        @Cached CallNode keyCall,
                        @Cached ConditionProfile moreThanTwo,
                        @Shared("castToBooleanNode") @Cached("createIfTrueNode()") CoerceToBooleanNode castToBooleanNode,
                        @Shared("hasDefaultProfile") @Cached ConditionProfile hasDefaultProfile) {

            if (!hasDefaultProfile.profile(PGuards.isNoValue(defaultVal))) {
                throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.CANNOT_SPECIFY_DEFAULT_FOR_S, getName());
            }
            Object currentValue = arg1;
            Object currentKey = applyKeyFunction(frame, keywordArg, keyCall, currentValue);
            Object nextValue = args[0];
            Object nextKey = applyKeyFunction(frame, keywordArg, keyCall, nextValue);
            boolean isTrue;
            try {
                isTrue = compare.executeBool(frame, nextKey, currentKey);
            } catch (UnexpectedResultException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                seenNonBoolean = true;
                isTrue = castToBooleanNode.executeBoolean(frame, e.getResult());
            }
            if (isTrue) {
                currentKey = nextKey;
                currentValue = nextValue;
            }
            if (moreThanTwo.profile(args.length > 1)) {
                for (int i = 0; i < args.length; i++) {
                    nextValue = args[i];
                    nextKey = applyKeyFunction(frame, keywordArg, keyCall, nextValue);
                    if (!seenNonBoolean) {
                        try {
                            isTrue = compare.executeBool(frame, nextKey, currentKey);
                        } catch (UnexpectedResultException e) {
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            seenNonBoolean = true;
                            isTrue = castToBooleanNode.executeBoolean(frame, e.getResult());
                        }
                    } else {
                        isTrue = castToBooleanNode.executeBoolean(frame, compare.executeObject(frame, nextKey, currentKey));
                    }
                    if (isTrue) {
                        currentKey = nextKey;
                        currentValue = nextValue;
                    }
                }
            }
            return currentValue;
        }

        private static Object applyKeyFunction(VirtualFrame frame, Object keywordArg, CallNode keyCall, Object currentValue) {
            return keyCall == null ? currentValue : keyCall.execute(frame, keywordArg, new Object[]{currentValue}, PKeyword.EMPTY_KEYWORDS);
        }
    }

    // max(iterable, *[, key])
    // max(arg1, arg2, *args[, key])
    @Builtin(name = J_MAX, minNumOfPositionalArgs = 1, takesVarArgs = true, keywordOnlyNames = {"key", "default"}, doc = "max(iterable, *[, default=obj, key=func]) -> value\n" +
                    "max(arg1, arg2, *args, *[, key=func]) -> value\n\n" + "With a single iterable argument, return its biggest item. The\n" +
                    "default keyword-only argument specifies an object to return if\n" + "the provided iterable is empty.\n" + "With two or more arguments, return the largest argument.")
    @GenerateNodeFactory
    public abstract static class MaxNode extends MinMaxNode {

    }

    // min(iterable, *[, key])
    // min(arg1, arg2, *args[, key])
    @Builtin(name = J_MIN, minNumOfPositionalArgs = 1, takesVarArgs = true, keywordOnlyNames = {"key", "default"})
    @GenerateNodeFactory
    public abstract static class MinNode extends MinMaxNode {

    }

    // next(iterator[, default])
    @SuppressWarnings("unused")
    @Builtin(name = J_NEXT, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class NextNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(defaultObject)")
        public Object next(VirtualFrame frame, Object iterator, PNone defaultObject,
                        @Cached("createNextCall()") LookupAndCallUnaryNode callNode) {
            return callNode.executeObject(frame, iterator);
        }

        @Specialization(guards = "!isNoValue(defaultObject)")
        public Object next(VirtualFrame frame, Object iterator, Object defaultObject,
                        @Cached("createNextCall()") LookupAndCallUnaryNode callNode,
                        @Cached IsBuiltinClassProfile errorProfile) {
            try {
                return callNode.executeObject(frame, iterator);
            } catch (PException e) {
                e.expectStopIteration(errorProfile);
                return defaultObject;
            }
        }

        protected LookupAndCallUnaryNode createNextCall() {
            return LookupAndCallUnaryNode.create(T___NEXT__, () -> new LookupAndCallUnaryNode.NoAttributeHandler() {
                @Override
                public Object execute(Object iterator) {
                    throw raise(TypeError, ErrorMessages.OBJ_ISNT_ITERATOR, iterator);
                }
            });
        }
    }

    // ord(c)
    @Builtin(name = J_ORD, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @ImportStatic(PGuards.class)
    public abstract static class OrdNode extends PythonBuiltinNode {

        @Specialization
        @TruffleBoundary
        public int ord(TruffleString chr,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.CodePointAtIndexNode codePointAtIndexNode) {
            int len = codePointLengthNode.execute(chr, TS_ENCODING);
            if (len != 1) {
                throw raise(TypeError, ErrorMessages.EXPECTED_CHARACTER_BUT_STRING_FOUND, "ord()", len);
            }
            return codePointAtIndexNode.execute(chr, 0, TS_ENCODING);
        }

        @Specialization
        public int ord(PString pchr,
                        @Cached CastToTruffleStringNode castToStringNode,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.CodePointAtIndexNode codePointAtIndexNode) {
            TruffleString chr;
            try {
                chr = castToStringNode.execute(pchr);
            } catch (CannotCastException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
            return ord(chr, codePointLengthNode, codePointAtIndexNode);
        }

        @Specialization
        public long ord(PBytesLike chr,
                        @Cached CastToJavaLongExactNode castNode,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached SequenceStorageNodes.GetItemNode getItemNode) {
            int len = lenNode.execute(chr.getSequenceStorage());
            if (len != 1) {
                throw raise(TypeError, ErrorMessages.EXPECTED_CHARACTER_BUT_STRING_FOUND, "ord()", len);
            }
            return castNode.execute(getItemNode.execute(chr.getSequenceStorage(), 0));
        }

        @Specialization(guards = {"!isString(obj)", "!isBytes(obj)"})
        public Object ord(@SuppressWarnings("unused") Object obj) {
            throw raise(TypeError, ErrorMessages.S_EXPECTED_STRING_OF_LEN_BUT_P, "ord()", "1", "obj");
        }
    }

    // print(*objects, sep=' ', end='\n', file=sys.stdout, flush=False)
    @Builtin(name = J_PRINT, takesVarArgs = true, keywordOnlyNames = {"sep", "end", "file", "flush"}, doc = "\n" +
                    "print(value, ..., sep=' ', end='\\n', file=sys.stdout, flush=False)\n" +
                    "\n" +
                    "Prints the values to a stream, or to sys.stdout by default.\n" +
                    "Optional keyword arguments:\n" +
                    "file:  a file-like object (stream); defaults to the current sys.stdout.\n" +
                    "sep:   string inserted between values, default a space.\n" +
                    "end:   string appended after the last value, default a newline.\n" +
                    "flush: whether to forcibly flush the stream.")
    @GenerateNodeFactory
    public abstract static class PrintNode extends PythonBuiltinNode {
        @Child private ReadAttributeFromObjectNode readStdout;
        @CompilationFinal private PythonModule cachedSys;

        @Specialization
        @SuppressWarnings("unused")
        PNone printNoKeywords(VirtualFrame frame, Object[] values, PNone sep, PNone end, PNone file, PNone flush,
                        @Shared("getWriteMethod") @Cached PyObjectGetAttr getWriteMethod,
                        @Shared("callWrite") @Cached CallNode callWrite,
                        @Shared("callFlush") @Cached PyObjectCallMethodObjArgs callFlush,
                        @Shared("strNode") @Cached PyObjectStrAsObjectNode strNode) {
            Object stdout = getStdout();
            return printAllGiven(frame, values, T_SPACE, T_NEWLINE, stdout, false, getWriteMethod, callWrite, callFlush, strNode);
        }

        @Specialization(guards = {"!isNone(file)", "!isNoValue(file)"})
        static PNone printAllGiven(VirtualFrame frame, Object[] values, TruffleString sep, TruffleString end, Object file, boolean flush,
                        @Shared("getWriteMethod") @Cached PyObjectGetAttr getWriteMethod,
                        @Shared("callWrite") @Cached CallNode callWrite,
                        @Shared("callFlush") @Cached PyObjectCallMethodObjArgs callFlush,
                        @Shared("strNode") @Cached PyObjectStrAsObjectNode strNode) {
            int lastValue = values.length - 1;
            // Note: the separate lookup is necessary due to different __getattr__ treatment than
            // method lookup
            Object writeMethod = getWriteMethod.execute(frame, file, T_WRITE);
            for (int i = 0; i < lastValue; i++) {
                callWrite.execute(frame, writeMethod, strNode.execute(frame, values[i]));
                callWrite.execute(frame, writeMethod, sep);
            }
            if (lastValue >= 0) {
                callWrite.execute(frame, writeMethod, strNode.execute(frame, values[lastValue]));
            }
            callWrite.execute(frame, writeMethod, end);
            if (flush) {
                callFlush.execute(frame, file, T_FLUSH);
            }
            return PNone.NONE;
        }

        @Specialization(replaces = {"printAllGiven", "printNoKeywords"})
        PNone printGeneric(VirtualFrame frame, Object[] values, Object sepIn, Object endIn, Object fileIn, Object flushIn,
                        @Cached CastToTruffleStringNode castSep,
                        @Cached CastToTruffleStringNode castEnd,
                        @Cached("createIfTrueNode()") CoerceToBooleanNode castFlush,
                        @Cached PRaiseNode raiseNode,
                        @Shared("getWriteMethod") @Cached PyObjectGetAttr getWriteMethod,
                        @Shared("callWrite") @Cached CallNode callWrite,
                        @Shared("callFlush") @Cached PyObjectCallMethodObjArgs callFlush,
                        @Shared("strNode") @Cached PyObjectStrAsObjectNode strNode) {
            TruffleString sep;
            try {
                sep = sepIn instanceof PNone ? T_SPACE : castSep.execute(sepIn);
            } catch (CannotCastException e) {
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, ErrorMessages.SEP_MUST_BE_NONE_OR_STRING, sepIn);
            }

            TruffleString end;
            try {
                end = endIn instanceof PNone ? T_NEWLINE : castEnd.execute(endIn);
            } catch (CannotCastException e) {
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, ErrorMessages.S_MUST_BE_NONE_OR_STRING, "end", sepIn);
            }

            Object file;
            if (fileIn instanceof PNone) {
                file = getStdout();
            } else {
                file = fileIn;
            }
            boolean flush;
            if (flushIn instanceof PNone) {
                flush = false;
            } else {
                flush = castFlush.executeBoolean(frame, flushIn);
            }
            return printAllGiven(frame, values, sep, end, file, flush, getWriteMethod, callWrite, callFlush, strNode);
        }

        private Object getStdout() {
            PythonModule sys;
            if (getLanguage().isSingleContext()) {
                if (cachedSys == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    cachedSys = getContext().lookupBuiltinModule(T_SYS);
                }
                sys = cachedSys;
            } else {
                if (cachedSys != null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    cachedSys = null;
                }
                sys = getContext().lookupBuiltinModule(T_SYS);
            }
            if (readStdout == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readStdout = insert(ReadAttributeFromObjectNode.create());
            }
            Object stdout = readStdout.execute(sys, T_STDOUT);
            if (stdout instanceof PNone) {
                throw raise(RuntimeError, ErrorMessages.LOST_SYSSTDOUT);
            }
            return stdout;
        }
    }

    // repr(object)
    @Builtin(name = J_REPR, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReprNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object repr(VirtualFrame frame, Object obj,
                        @Cached PyObjectReprAsObjectNode reprNode) {
            return reprNode.execute(frame, obj);
        }
    }

    // format(object, [format_spec])
    @Builtin(name = J_FORMAT, minNumOfPositionalArgs = 1, parameterNames = {"object", "format_spec"})
    @GenerateNodeFactory
    @ImportStatic(PGuards.class)
    public abstract static class FormatNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "isNoValue(formatSpec)")
        Object format(VirtualFrame frame, Object obj, @SuppressWarnings("unused") PNone formatSpec,
                        @Shared("callFormat") @Cached("create(Format)") LookupAndCallBinaryNode callFormat) {
            return format(frame, obj, T_EMPTY_STRING, callFormat);
        }

        @Specialization(guards = "!isNoValue(formatSpec)")
        Object format(VirtualFrame frame, Object obj, Object formatSpec,
                        @Shared("callFormat") @Cached("create(Format)") LookupAndCallBinaryNode callFormat) {
            Object res = callFormat.executeObject(frame, obj, formatSpec);
            if (res == NO_VALUE) {
                throw raise(TypeError, ErrorMessages.TYPE_DOESNT_DEFINE_FORMAT, obj);
            }
            if (!PGuards.isString(res)) {
                throw raise(TypeError, ErrorMessages.S_MUST_RETURN_S_NOT_P, T___FORMAT__, "str", res);
            }
            return res;
        }

        public static FormatNode create() {
            return BuiltinFunctionsFactory.FormatNodeFactory.create();
        }
    }

    // ascii(object)
    @Builtin(name = J_ASCII, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class AsciiNode extends PythonUnaryBuiltinNode {

        @Specialization
        public static TruffleString ascii(VirtualFrame frame, Object obj,
                        @Cached PyObjectAsciiNode asciiNode) {
            return asciiNode.execute(frame, obj);
        }
    }

    // round(number[, ndigits])
    @Builtin(name = J_ROUND, minNumOfPositionalArgs = 1, parameterNames = {"number", "ndigits"})
    @GenerateNodeFactory
    public abstract static class RoundNode extends PythonBuiltinNode {
        @Specialization
        Object round(VirtualFrame frame, Object x, @SuppressWarnings("unused") PNone n,
                        @Cached("create(Round)") LookupAndCallUnaryNode callRound) {
            Object result = callRound.executeObject(frame, x);
            if (result == PNone.NO_VALUE) {
                throw raise(TypeError, ErrorMessages.TYPE_DOESNT_DEFINE_METHOD, x, T___ROUND__);
            }
            return result;
        }

        @Specialization(guards = "!isPNone(n)")
        Object round(VirtualFrame frame, Object x, Object n,
                        @Cached("create(Round)") LookupAndCallBinaryNode callRound) {
            Object result = callRound.executeObject(frame, x, n);
            if (result == NOT_IMPLEMENTED) {
                throw raise(TypeError, ErrorMessages.TYPE_DOESNT_DEFINE_METHOD, x, T___ROUND__);
            }
            return result;
        }
    }

    // setattr(object, name, value)
    @Builtin(name = J_SETATTR, minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class SetAttrNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object setAttr(VirtualFrame frame, Object object, Object key, Object value,
                        @Cached SetAttributeNode.Dynamic setAttrNode) {
            setAttrNode.execute(frame, object, key, value);
            return PNone.NONE;
        }
    }

    // hasattr(object, name)
    @Builtin(name = J_HASATTR, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class HasAttrNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean hasAttr(VirtualFrame frame, Object object, Object key,
                        @Cached PyObjectLookupAttr pyObjectLookupAttr) {
            return pyObjectLookupAttr.execute(frame, object, key) != PNone.NO_VALUE;
        }
    }

    // sorted(iterable, key, reverse)
    @Builtin(name = J_SORTED, minNumOfPositionalArgs = 1, parameterNames = {"$self"}, keywordOnlyNames = {"key", "reverse"})
    @ArgumentClinic(name = "reverse", conversion = ArgumentClinic.ClinicConversion.IntToBoolean, defaultValue = "false")
    @GenerateNodeFactory
    public abstract static class SortedNode extends PythonClinicBuiltinNode {

        public abstract Object executeInternal(VirtualFrame frame, Object iterable, Object keyfunc, boolean reverse);

        @Specialization
        Object sorted(VirtualFrame frame, Object iterable, Object keyfunc, boolean reverse,
                        @Cached ConstructListNode constructListNode,
                        @Cached ListSortNode sortNode) {
            PList list = constructListNode.execute(frame, iterable);
            sortNode.execute(frame, list, keyfunc, reverse);
            return list;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BuiltinFunctionsClinicProviders.SortedNodeClinicProviderGen.INSTANCE;
        }

        public static SortedNode create() {
            return BuiltinFunctionsFactory.SortedNodeFactory.create(null);
        }

    }

    @Builtin(name = J_BREAKPOINT, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class BreakPointNode extends PythonBuiltinNode {
        @Child private ReadAttributeFromObjectNode getBreakpointhookNode;
        @Child private CallNode callNode;

        @Specialization
        public Object doIt(VirtualFrame frame, Object[] args, PKeyword[] kwargs,
                        @CachedLibrary("getContext().getSysModules().getDictStorage()") HashingStorageLibrary hlib) {
            if (getDebuggerSessionCount() > 0) {
                // we already have a Truffle debugger attached, it'll stop here
                return PNone.NONE;
            } else if (getContext().isInitialized()) {
                if (callNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    getBreakpointhookNode = insert(ReadAttributeFromObjectNode.create());
                    callNode = insert(CallNode.create());
                }
                PDict sysModules = getContext().getSysModules();
                Object sysModule = hlib.getItem(sysModules.getDictStorage(), T_SYS);
                Object breakpointhook = getBreakpointhookNode.execute(sysModule, T_BREAKPOINTHOOK);
                if (breakpointhook == PNone.NO_VALUE) {
                    throw raise(PythonBuiltinClassType.RuntimeError, ErrorMessages.LOST_SYSBREAKPOINTHOOK);
                }
                return callNode.execute(frame, breakpointhook, args, kwargs);
            } else {
                return PNone.NONE;
            }
        }

        @TruffleBoundary
        private int getDebuggerSessionCount() {
            return Debugger.find(getContext().getEnv()).getSessionCount();
        }
    }

    @Builtin(name = J_POW, minNumOfPositionalArgs = 2, parameterNames = {"base", "exp", "mod"})
    @GenerateNodeFactory
    public abstract static class PowNode extends PythonTernaryBuiltinNode {
        static BinaryOpNode binaryPow() {
            return BinaryArithmetic.Pow.create();
        }

        static LookupAndCallTernaryNode ternaryPow() {
            return TernaryArithmetic.Pow.create();
        }

        @Specialization
        Object binary(VirtualFrame frame, Object x, Object y, @SuppressWarnings("unused") PNone z,
                        @Cached("binaryPow()") BinaryOpNode powNode) {
            return powNode.executeObject(frame, x, y);
        }

        @Specialization(guards = "!isPNone(z)")
        Object ternary(VirtualFrame frame, Object x, Object y, Object z,
                        @Cached("ternaryPow()") LookupAndCallTernaryNode powNode) {
            return powNode.execute(frame, x, y, z);
        }
    }

    // sum(iterable[, start])
    @Builtin(name = J_SUM, minNumOfPositionalArgs = 1, parameterNames = {"iterable", "start"})
    @GenerateNodeFactory
    public abstract static class SumFunctionNode extends PythonBuiltinNode {

        @Child private LookupAndCallUnaryNode next = LookupAndCallUnaryNode.create(SpecialMethodSlot.Next);
        @Child private AddNode add = AddNode.create();

        @Child private IsBuiltinClassProfile errorProfile1 = IsBuiltinClassProfile.create();
        @Child private IsBuiltinClassProfile errorProfile2 = IsBuiltinClassProfile.create();
        @Child private IsBuiltinClassProfile errorProfile3 = IsBuiltinClassProfile.create();

        @Specialization(rewriteOn = UnexpectedResultException.class)
        int sumIntNone(VirtualFrame frame, Object arg1, @SuppressWarnings("unused") PNone start,
                        @Shared("getIter") @Cached PyObjectGetIter getIter) throws UnexpectedResultException {
            return sumIntInternal(frame, arg1, 0, getIter);
        }

        @Specialization(rewriteOn = UnexpectedResultException.class)
        int sumIntInt(VirtualFrame frame, Object arg1, int start,
                        @Shared("getIter") @Cached PyObjectGetIter getIter) throws UnexpectedResultException {
            return sumIntInternal(frame, arg1, start, getIter);
        }

        private int sumIntInternal(VirtualFrame frame, Object arg1, int start, PyObjectGetIter getIter) throws UnexpectedResultException {
            Object iterator = getIter.execute(frame, arg1);
            int value = start;
            while (true) {
                int nextValue;
                try {
                    nextValue = PGuards.expectInteger(next.executeObject(frame, iterator));
                } catch (PException e) {
                    e.expectStopIteration(errorProfile1);
                    return value;
                } catch (UnexpectedResultException e) {
                    Object newValue = add.executeObject(frame, value, e.getResult());
                    throw new UnexpectedResultException(iterateGeneric(frame, iterator, newValue, errorProfile2));
                }
                try {
                    value = add.executeInt(frame, value, nextValue);
                } catch (UnexpectedResultException e) {
                    throw new UnexpectedResultException(iterateGeneric(frame, iterator, e.getResult(), errorProfile3));
                }
            }
        }

        @Specialization(rewriteOn = UnexpectedResultException.class)
        double sumDoubleDouble(VirtualFrame frame, Object arg1, double start,
                        @Shared("getIter") @Cached PyObjectGetIter getIter) throws UnexpectedResultException {
            return sumDoubleInternal(frame, arg1, start, getIter);
        }

        private double sumDoubleInternal(VirtualFrame frame, Object arg1, double start, PyObjectGetIter getIter) throws UnexpectedResultException {
            Object iterator = getIter.execute(frame, arg1);
            double value = start;
            while (true) {
                double nextValue;
                try {
                    nextValue = PGuards.expectDouble(next.executeObject(frame, iterator));
                } catch (PException e) {
                    e.expectStopIteration(errorProfile1);
                    return value;
                } catch (UnexpectedResultException e) {
                    Object newValue = add.executeObject(frame, value, e.getResult());
                    throw new UnexpectedResultException(iterateGeneric(frame, iterator, newValue, errorProfile2));
                }
                try {
                    value = add.executeDouble(frame, value, nextValue);
                } catch (UnexpectedResultException e) {
                    throw new UnexpectedResultException(iterateGeneric(frame, iterator, e.getResult(), errorProfile3));
                }
            }
        }

        @Specialization(replaces = {"sumIntNone", "sumIntInt", "sumDoubleDouble"})
        Object sum(VirtualFrame frame, Object arg1, Object start,
                        @Shared("getIter") @Cached PyObjectGetIter getIter,
                        @Cached ConditionProfile hasStart) {
            if (PGuards.isString(start)) {
                throw raise(TypeError, ErrorMessages.CANT_SUM_STRINGS);
            } else if (start instanceof PBytes) {
                throw raise(TypeError, ErrorMessages.CANT_SUM_BYTES);
            } else if (start instanceof PByteArray) {
                throw raise(TypeError, ErrorMessages.CANT_SUM_BYTEARRAY);
            }
            Object iterator = getIter.execute(frame, arg1);
            return iterateGeneric(frame, iterator, hasStart.profile(start != NO_VALUE) ? start : 0, errorProfile1);
        }

        private Object iterateGeneric(VirtualFrame frame, Object iterator, Object start, IsBuiltinClassProfile errorProfile) {
            Object value = start;
            while (true) {
                Object nextValue;
                try {
                    nextValue = next.executeObject(frame, iterator);
                } catch (PException e) {
                    e.expectStopIteration(errorProfile);
                    return value;
                }
                value = add.executeObject(frame, value, nextValue);
            }
        }
    }

    @Builtin(name = "globals")
    @GenerateNodeFactory
    public abstract static class GlobalsNode extends PythonBuiltinNode {
        private final ConditionProfile condProfile = ConditionProfile.createBinaryProfile();

        @Specialization
        public Object globals(VirtualFrame frame,
                        @Cached PyEvalGetGlobals getGlobals,
                        @Cached GetOrCreateDictNode getDict) {
            Object globals = getGlobals.execute(frame);
            if (condProfile.profile(globals instanceof PythonModule)) {
                return getDict.execute(globals);
            } else {
                return globals;
            }
        }

        public static GlobalsNode create() {
            return GlobalsNodeFactory.create(null);
        }
    }

    @Builtin(name = "locals", needsFrame = true, alwaysNeedsCallerFrame = true)
    @GenerateNodeFactory
    abstract static class LocalsNode extends PythonBuiltinNode {

        @Specialization
        Object locals(VirtualFrame frame,
                        @Cached ReadLocalsNode readLocalsNode,
                        @Cached ReadCallerFrameNode readCallerFrameNode,
                        @Cached MaterializeFrameNode materializeNode,
                        @Cached ConditionProfile inGenerator) {
            return getLocalsDict(frame, this, readLocalsNode, readCallerFrameNode, materializeNode, inGenerator);
        }

        static Object getLocalsDict(VirtualFrame frame, Node n, ReadLocalsNode readLocalsNode, ReadCallerFrameNode readCallerFrameNode, MaterializeFrameNode materializeNode,
                        ConditionProfile inGenerator) {
            PFrame callerFrame = readCallerFrameNode.executeWith(frame, 0);
            Frame generatorFrame = PArguments.getGeneratorFrame(callerFrame.getArguments());
            if (inGenerator.profile(generatorFrame == null)) {
                return readLocalsNode.execute(frame, callerFrame);
            } else {
                return readLocalsNode.execute(frame, materializeNode.execute(frame, n, false, false, generatorFrame));
            }
        }

        public static LocalsNode create() {
            return BuiltinFunctionsFactory.LocalsNodeFactory.create(null);
        }
    }

    @Builtin(name = "vars", maxNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class VarsNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = "isNoValue(none)")
        Object vars(VirtualFrame frame, @SuppressWarnings("unused") PNone none,
                        @Cached LocalsNode localsNode) {
            return localsNode.execute(frame);
        }

        @Specialization(guards = "!isNoValue(obj)")
        Object vars(VirtualFrame frame, Object obj,
                        @Cached PyObjectLookupAttr lookupAttr) {
            Object dict = lookupAttr.execute(frame, obj, T___DICT__);
            if (dict == NO_VALUE) {
                throw raise(TypeError, ErrorMessages.VARS_ARGUMENT_MUST_HAVE_DICT);
            }
            return dict;
        }
    }

    @Builtin(name = "open", minNumOfPositionalArgs = 1, parameterNames = {"file", "mode", "buffering", "encoding", "errors", "newline", "closefd", "opener"})
    @ArgumentClinic(name = "mode", conversionClass = IONodes.CreateIOModeNode.class, args = "true")
    @ArgumentClinic(name = "buffering", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1", useDefaultForNone = true)
    @ArgumentClinic(name = "encoding", conversion = ArgumentClinic.ClinicConversion.TString, defaultValue = "PNone.NONE", useDefaultForNone = true)
    @ArgumentClinic(name = "errors", conversion = ArgumentClinic.ClinicConversion.TString, defaultValue = "PNone.NONE", useDefaultForNone = true)
    @ArgumentClinic(name = "newline", conversion = ArgumentClinic.ClinicConversion.TString, defaultValue = "PNone.NONE", useDefaultForNone = true)
    @ArgumentClinic(name = "closefd", conversion = ArgumentClinic.ClinicConversion.Boolean, defaultValue = "true", useDefaultForNone = true)
    @ImportStatic(IONodes.IOMode.class)
    @GenerateNodeFactory
    public abstract static class OpenNode extends IOModuleBuiltins.IOOpenNode {
        /*
         * XXX: (mq) CPython defines `builtins.open` by importing `OpenWrapper` from the `io` module
         * see ('Python/pylifecycle.c:init_set_builtins_open'). `io.OpenWrapper` is set to
         * `_io.open`. However, the process seems redundant and expensive in our case. So, here, we
         * skip this and define `open` in `io` and `builtins` modules at once.
         */

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BuiltinFunctionsClinicProviders.OpenNodeClinicProviderGen.INSTANCE;
        }
    }

    @ImportStatic(SpecialMethodNames.class)
    abstract static class UpdateBasesNode extends PNodeWithRaise {

        abstract PTuple execute(PTuple bases, Object[] arguments, int nargs);

        @Specialization
        PTuple update(PTuple bases, Object[] arguments, int nargs,
                        @Cached PythonObjectFactory factory,
                        @Cached PyObjectGetMethod getMroEntries,
                        @Cached CallBinaryMethodNode callMroEntries) {
            CompilerAsserts.neverPartOfCompilation();
            ArrayList<Object> newBases = null;
            for (int i = 0; i < nargs; i++) {
                Object base = arguments[i];
                if (IsTypeNode.getUncached().execute(base)) {
                    if (newBases != null) {
                        // If we already have made a replacement, then we append every normal base,
                        // otherwise just skip it.
                        newBases.add(base);
                    }
                    continue;
                }

                Object meth = getMroEntries.execute(null, base, T___MRO_ENTRIES__);
                if (PGuards.isNoValue(meth)) {
                    if (newBases != null) {
                        newBases.add(base);
                    }
                    continue;
                }
                Object newBase = callMroEntries.executeObject(null, meth, base, bases);
                if (newBase == null) {
                    // error
                    return null;
                }
                if (!PGuards.isPTuple(newBase)) {
                    throw raise(PythonErrorType.TypeError, ErrorMessages.MRO_ENTRIES_MUST_RETURN_TUPLE);
                }
                PTuple newBaseTuple = (PTuple) newBase;
                if (newBases == null) {
                    // If this is a first successful replacement, create new_bases list and copy
                    // previously encountered bases.
                    newBases = new ArrayList<>();
                    for (int j = 0; j < i; j++) {
                        newBases.add(arguments[j]);
                    }
                }
                SequenceStorage storage = newBaseTuple.getSequenceStorage();
                for (int j = 0; j < storage.length(); j++) {
                    newBases.add(storage.getItemNormalized(j));
                }
            }
            if (newBases == null) {
                return bases;
            }
            return factory.createTuple(newBases.toArray());
        }
    }

    abstract static class CalculateMetaclassNode extends PNodeWithRaise {

        abstract Object execute(Object metatype, PTuple bases);

        /* Determine the most derived metatype. */
        @Specialization
        Object calculate(Object metatype, PTuple bases,
                        @Cached GetClassNode getClass,
                        @Cached IsSubtypeNode isSubType,
                        @Cached IsSubtypeNode isSubTypeReverse) {
            CompilerAsserts.neverPartOfCompilation();
            /*
             * Determine the proper metatype to deal with this, and check for metatype conflicts
             * while we're at it. Note that if some other metatype wins to contract, it's possible
             * that its instances are not types.
             */

            SequenceStorage storage = bases.getSequenceStorage();
            int nbases = storage.length();
            Object winner = metatype;
            for (int i = 0; i < nbases; i++) {
                Object tmp = storage.getItemNormalized(i);
                Object tmpType = getClass.execute(tmp);
                if (isSubType.execute(winner, tmpType)) {
                    // nothing to do
                } else if (isSubTypeReverse.execute(tmpType, winner)) {
                    winner = tmpType;
                } else {
                    throw raise(PythonErrorType.TypeError, ErrorMessages.METACLASS_CONFLICT);
                }
            }
            return winner;
        }
    }

    @Builtin(name = J___BUILD_CLASS__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    @ImportStatic(SpecialMethodSlot.class)
    public abstract static class BuildClassNode extends PythonVarargsBuiltinNode {
        private static final TruffleString T_METACLASS = tsLiteral("metaclass");
        public static final TruffleString T_BUILD_JAVA_CLASS = tsLiteral("build_java_class");

        @TruffleBoundary
        private static Object buildJavaClass(Object namespace, TruffleString name, Object base) {
            // uncached PythonContext get, since this code path is slow in any case
            Object module = PythonContext.get(null).lookupBuiltinModule(T___GRAALPYTHON__);
            Object buildFunction = PyObjectLookupAttr.getUncached().execute(null, module, T_BUILD_JAVA_CLASS);
            return CallNode.getUncached().execute(buildFunction, namespace, name, base);
        }

        @Specialization
        protected Object doItNonFunction(VirtualFrame frame, Object function, Object[] arguments, PKeyword[] keywords,
                        @Cached PythonObjectFactory factory,
                        @Cached CalculateMetaclassNode calculateMetaClass,
                        @Cached("create(T___PREPARE__)") GetAttributeNode getPrepare,
                        @Cached(parameters = "GetItem") LookupCallableSlotInMRONode getGetItem,
                        @Cached GetClassNode getGetItemClass,
                        @Cached CallVarargsMethodNode callPrep,
                        @Cached CallVarargsMethodNode callType,
                        @Cached CallDispatchNode callBody,
                        @Cached UpdateBasesNode update,
                        @Cached SetItemNode setOrigBases,
                        @Cached GetClassNode getClass,
                        @Cached IsBuiltinClassProfile noAttributeProfile) {

            if (arguments.length < 1) {
                throw raise(PythonErrorType.TypeError, ErrorMessages.BUILD_CLS_NOT_ENOUGH_ARGS);
            }

            if (!PGuards.isFunction(function)) {
                throw raise(PythonErrorType.TypeError, ErrorMessages.BUILD_CLS_FUNC_MUST_BE_FUNC);
            }
            TruffleString name;
            try {
                name = CastToTruffleStringNode.getUncached().execute(arguments[0]);
            } catch (CannotCastException e) {
                throw raise(PythonErrorType.TypeError, ErrorMessages.BUILD_CLS_NAME_NOT_STRING);
            }

            Object[] basesArray = Arrays.copyOfRange(arguments, 1, arguments.length);
            PTuple origBases = factory.createTuple(basesArray);

            Env env = PythonContext.get(calculateMetaClass).getEnv();
            if (arguments.length == 2 && env.isHostObject(arguments[1]) && env.asHostObject(arguments[1]) instanceof Class<?>) {
                // we want to subclass a Java class
                PDict ns = PythonObjectFactory.getUncached().createDict(new DynamicObjectStorage(PythonLanguage.get(null)));
                Object[] args = PArguments.create(0);
                PArguments.setCustomLocals(args, ns);
                PArguments.setSpecialArgument(args, ns);
                callBody.executeCall(frame, (PFunction) function, args);
                return buildJavaClass(ns, name, arguments[1]);
            }

            class InitializeBuildClass {
                boolean isClass;
                Object meta;
                PKeyword[] mkw;
                PTuple bases;

                @TruffleBoundary
                InitializeBuildClass() {

                    bases = update.execute(origBases, basesArray, basesArray.length);

                    mkw = keywords;
                    for (int i = 0; i < keywords.length; i++) {
                        if (T_METACLASS.equalsUncached(keywords[i].getName(), TS_ENCODING)) {
                            meta = keywords[i].getValue();
                            mkw = PKeyword.create(keywords.length - 1);

                            PythonUtils.arraycopy(keywords, 0, mkw, 0, i);
                            PythonUtils.arraycopy(keywords, i + 1, mkw, i, mkw.length - i);

                            // metaclass is explicitly given, check if it's indeed a class
                            isClass = IsTypeNode.getUncached().execute(meta);
                            break;
                        }
                    }
                    if (meta == null) {
                        // if there are no bases, use type:
                        if (bases.getSequenceStorage().length() == 0) {
                            meta = PythonContext.get(update).lookupType(PythonBuiltinClassType.PythonClass);
                        } else {
                            // else get the type of the first base
                            meta = getClass.execute(bases.getSequenceStorage().getItemNormalized(0));
                        }
                        isClass = true;  // meta is really a class
                    }
                    if (isClass) {
                        // meta is really a class, so check for a more derived metaclass, or
                        // possible
                        // metaclass conflicts:
                        meta = calculateMetaClass.execute(meta, bases);
                    }
                    // else: meta is not a class, so we cannot do the metaclass calculation, so we
                    // will use the explicitly given object as it is
                }
            }
            InitializeBuildClass init = new InitializeBuildClass();

            Object ns;
            try {
                Object prep = getPrepare.executeObject(frame, init.meta);
                ns = callPrep.execute(frame, prep, new Object[]{name, init.bases}, init.mkw);
            } catch (PException p) {
                p.expectAttributeError(noAttributeProfile);
                ns = factory().createDict(new DynamicObjectStorage(PythonLanguage.get(this)));
            }
            if (PGuards.isNoValue(getGetItem.execute(getGetItemClass.execute(ns)))) {
                if (init.isClass) {
                    throw raise(PythonErrorType.TypeError, ErrorMessages.N_PREPARE_MUST_RETURN_MAPPING, init.meta, ns);
                } else {
                    throw raise(PythonErrorType.TypeError, ErrorMessages.MTCLS_PREPARE_MUST_RETURN_MAPPING, ns);
                }
            }
            Object[] bodyArguments = PArguments.create(0);
            PArguments.setCustomLocals(bodyArguments, ns);
            PArguments.setSpecialArgument(bodyArguments, ns);
            callBody.executeCall(frame, (PFunction) function, bodyArguments);
            if (init.bases != origBases) {
                setOrigBases.executeWith(frame, ns, SpecialAttributeNames.T___ORIG_BASES__, origBases);
            }
            Object cls = callType.execute(frame, init.meta, new Object[]{name, init.bases, ns}, init.mkw);

            /*
             * We could check here and throw "__class__ not set defining..." errors.
             */

            return cls;
        }
    }
}
