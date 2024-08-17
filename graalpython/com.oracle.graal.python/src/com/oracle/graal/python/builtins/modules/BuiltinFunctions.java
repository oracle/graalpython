/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates.
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
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SyntaxError;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_FLUSH;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_WRITE;
import static com.oracle.graal.python.builtins.objects.PNone.NO_VALUE;
import static com.oracle.graal.python.builtins.objects.PNotImplemented.NOT_IMPLEMENTED;
import static com.oracle.graal.python.compiler.RaisePythonExceptionErrorCallback.raiseSyntaxError;
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
import static com.oracle.graal.python.nodes.PGuards.isNoValue;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DICT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___FORMAT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___MRO_ENTRIES__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___NEXT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ROUND__;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_STRING;
import static com.oracle.graal.python.nodes.StringLiterals.T_FALSE;
import static com.oracle.graal.python.nodes.StringLiterals.T_MINUS;
import static com.oracle.graal.python.nodes.StringLiterals.T_NEWLINE;
import static com.oracle.graal.python.nodes.StringLiterals.T_NONE;
import static com.oracle.graal.python.nodes.StringLiterals.T_SPACE;
import static com.oracle.graal.python.nodes.StringLiterals.T_STRICT;
import static com.oracle.graal.python.nodes.StringLiterals.T_STRING_SOURCE;
import static com.oracle.graal.python.nodes.StringLiterals.T_TRUE;
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
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItem;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorKey;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorNext;
import com.oracle.graal.python.builtins.objects.common.PHashingCollection;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes.GetObjectArrayNode;
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
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TypeBuiltins;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsSameTypeNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsTypeNode;
import com.oracle.graal.python.compiler.Compiler;
import com.oracle.graal.python.compiler.RaisePythonExceptionErrorCallback;
import com.oracle.graal.python.lib.GetNextNode;
import com.oracle.graal.python.lib.PyCallableCheckNode;
import com.oracle.graal.python.lib.PyEvalGetGlobals;
import com.oracle.graal.python.lib.PyEvalGetLocals;
import com.oracle.graal.python.lib.PyMappingCheckNode;
import com.oracle.graal.python.lib.PyNumberAddNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyNumberIndexNode;
import com.oracle.graal.python.lib.PyObjectAsciiNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectDir;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectGetAttrO;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectLookupAttrO;
import com.oracle.graal.python.lib.PyObjectReprAsObjectNode;
import com.oracle.graal.python.lib.PyObjectSetAttrO;
import com.oracle.graal.python.lib.PyObjectSetItem;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.lib.PyObjectStrAsObjectNode;
import com.oracle.graal.python.lib.PyObjectStrAsTruffleStringNode;
import com.oracle.graal.python.lib.PyUnicodeFSDecoderNode;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.StringLiterals;
import com.oracle.graal.python.nodes.argument.ReadArgumentNode;
import com.oracle.graal.python.nodes.attributes.DeleteAttributeNode;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.builtins.ListNodes.ConstructListNode;
import com.oracle.graal.python.nodes.bytecode.GetAIterNode;
import com.oracle.graal.python.nodes.bytecode.PBytecodeRootNode;
import com.oracle.graal.python.nodes.bytecode_dsl.PBytecodeDSLRootNode;
import com.oracle.graal.python.nodes.call.CallDispatchNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.GenericInvokeNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallVarargsMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallTernaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodSlotNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNode;
import com.oracle.graal.python.nodes.expression.BinaryOpNode;
import com.oracle.graal.python.nodes.expression.CoerceToBooleanNode;
import com.oracle.graal.python.nodes.expression.TernaryArithmetic;
import com.oracle.graal.python.nodes.frame.GetFrameLocalsNode;
import com.oracle.graal.python.nodes.frame.ReadCallerFrameNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetOrCreateDictNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaLongExactNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.pegparser.AbstractParser;
import com.oracle.graal.python.pegparser.ErrorCallback;
import com.oracle.graal.python.pegparser.FutureFeature;
import com.oracle.graal.python.pegparser.InputType;
import com.oracle.graal.python.pegparser.Parser;
import com.oracle.graal.python.pegparser.sst.ModTy;
import com.oracle.graal.python.pegparser.tokenizer.SourceRange;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
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
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.bytecode.OperationProxy;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
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
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.profiles.InlinedCountingConditionProfile;
import com.oracle.truffle.api.profiles.InlinedLoopConditionProfile;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.Encoding;
import com.oracle.truffle.api.strings.TruffleStringBuilder;
import com.oracle.truffle.api.utilities.TriState;

@CoreFunctions(defineModule = J_BUILTINS, isEager = true)
public final class BuiltinFunctions extends PythonBuiltins {

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
    @Builtin(name = J_ABS, minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = "x")
    @GenerateNodeFactory
    public abstract static class AbsNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object absInt(int arg) {
            return PInt.abs(arg);
        }

        @Specialization
        static double absDouble(double arg) {
            return Math.abs(arg);
        }

        @Specialization
        static Object absObject(VirtualFrame frame, Object object,
                        @Bind("this") Node inliningTarget,
                        @Cached("create(T___ABS__)") LookupAndCallUnaryNode callAbs,
                        @Cached PRaiseNode.Lazy raiseNode) {
            Object result = callAbs.executeObject(frame, object);
            if (result == NO_VALUE) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.BAD_OPERAND_FOR, "", "abs()", object);
            }
            return result;
        }
    }

    enum AnyOrAllNodeType {
        ALL,
        ANY
    }

    /**
     * Common class for all() and any() operations, as their logic and behaviors are very similar.
     */
    @GenerateInline
    @GenerateCached(false)
    abstract static class AllOrAnySequenceStorageNode extends PNodeWithContext {
        abstract boolean execute(VirtualFrame frame, Node inliningTarget, SequenceStorage storageObj, AnyOrAllNodeType nodeType);

        @Specialization
        static boolean doBoolSequence(Node inliningTarget, BoolSequenceStorage sequenceStorage, AnyOrAllNodeType nodeType,
                        @Exclusive @Cached InlinedLoopConditionProfile loopConditionProfile,
                        @Exclusive @Cached InlinedCountingConditionProfile earlyExitProfile) {
            boolean[] internalArray = sequenceStorage.getInternalBoolArray();
            int seqLength = sequenceStorage.length();

            for (int i = 0; loopConditionProfile.profile(inliningTarget, i < seqLength); i++) {
                if (nodeType == AnyOrAllNodeType.ALL && earlyExitProfile.profile(inliningTarget, !internalArray[i])) {
                    LoopNode.reportLoopCount(inliningTarget, i);
                    return false;
                } else if (nodeType == AnyOrAllNodeType.ANY && earlyExitProfile.profile(inliningTarget, internalArray[i])) {
                    LoopNode.reportLoopCount(inliningTarget, i);
                    return true;
                }
            }
            LoopNode.reportLoopCount(inliningTarget, seqLength);
            return nodeType == AnyOrAllNodeType.ALL;
        }

        @Specialization
        static boolean doIntSequence(Node inliningTarget, IntSequenceStorage sequenceStorage, AnyOrAllNodeType nodeType,
                        @Exclusive @Cached InlinedLoopConditionProfile loopConditionProfile,
                        @Exclusive @Cached InlinedCountingConditionProfile earlyExitProfile) {
            int[] internalArray = sequenceStorage.getInternalIntArray();
            int seqLength = sequenceStorage.length();

            for (int i = 0; loopConditionProfile.profile(inliningTarget, i < seqLength); i++) {
                if (nodeType == AnyOrAllNodeType.ALL && earlyExitProfile.profile(inliningTarget, internalArray[i] == 0)) {
                    LoopNode.reportLoopCount(inliningTarget, i);
                    return false;
                } else if (nodeType == AnyOrAllNodeType.ANY && earlyExitProfile.profile(inliningTarget, internalArray[i] != 0)) {
                    LoopNode.reportLoopCount(inliningTarget, i);
                    return true;
                }
            }
            LoopNode.reportLoopCount(inliningTarget, seqLength);
            return nodeType == AnyOrAllNodeType.ALL;
        }

        @Specialization
        static boolean doGenericSequence(VirtualFrame frame, Node inliningTarget, SequenceStorage sequenceStorage, AnyOrAllNodeType nodeType,
                        @Exclusive @Cached InlinedLoopConditionProfile loopConditionProfile,
                        @Exclusive @Cached InlinedCountingConditionProfile earlyExitProfile,
                        @Cached PyObjectIsTrueNode isTrueNode,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItem) {
            int seqLength = sequenceStorage.length();

            for (int i = 0; loopConditionProfile.profile(inliningTarget, i < seqLength); i++) {
                if (nodeType == AnyOrAllNodeType.ALL &&
                                earlyExitProfile.profile(inliningTarget, !isTrueNode.execute(frame, inliningTarget, getItem.execute(inliningTarget, sequenceStorage, i)))) {
                    LoopNode.reportLoopCount(inliningTarget, i);
                    return false;
                } else if (nodeType == AnyOrAllNodeType.ANY &&
                                earlyExitProfile.profile(inliningTarget, isTrueNode.execute(frame, inliningTarget, getItem.execute(inliningTarget, sequenceStorage, i)))) {
                    LoopNode.reportLoopCount(inliningTarget, i);
                    return true;
                }
            }
            LoopNode.reportLoopCount(inliningTarget, seqLength);
            return nodeType == AnyOrAllNodeType.ALL;
        }
    }

    /**
     * Common class for all() and any() operations, as their logic and behaviors are very similar.
     */
    @GenerateInline(false) // footprint reduction 68 -> 50
    abstract static class AllOrAnyHashingStorageNode extends PNodeWithContext {
        abstract boolean execute(Frame frame, HashingStorage storageObj, AnyOrAllNodeType nodeType);

        @Specialization
        protected boolean doHashStorage(VirtualFrame frame, HashingStorage hashingStorage, AnyOrAllNodeType nodeType,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectIsTrueNode isTrueNode,
                        @Cached InlinedLoopConditionProfile loopConditionProfile,
                        @Cached HashingStorageGetIterator getIter,
                        @Cached HashingStorageIteratorNext getIterNext,
                        @Cached HashingStorageIteratorKey getIterKey) {
            HashingStorageIterator it = getIter.execute(inliningTarget, hashingStorage);
            while (loopConditionProfile.profile(inliningTarget, getIterNext.execute(inliningTarget, hashingStorage, it))) {
                Object key = getIterKey.execute(inliningTarget, hashingStorage, it);
                if (nodeType == AnyOrAllNodeType.ALL) {
                    if (!isTrueNode.execute(frame, inliningTarget, key)) {
                        return false;
                    }
                } else if (nodeType == AnyOrAllNodeType.ANY && isTrueNode.execute(frame, inliningTarget, key)) {
                    return true;
                }
            }
            return nodeType == AnyOrAllNodeType.ALL;
        }
    }

    @Builtin(name = J_ALL, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class AllNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = "isBuiltinList(object)")
        static boolean doList(VirtualFrame frame, PList object,
                        @Bind("this") Node inliningTarget,
                        @Shared("allOrAnySeqNode") @Cached AllOrAnySequenceStorageNode allOrAnyNode) {
            return allOrAnyNode.execute(frame, inliningTarget, object.getSequenceStorage(), AnyOrAllNodeType.ALL);
        }

        @Specialization(guards = "isBuiltinTuple(object)")
        static boolean doTuple(VirtualFrame frame, PTuple object,
                        @Bind("this") Node inliningTarget,
                        @Shared("allOrAnySeqNode") @Cached AllOrAnySequenceStorageNode allOrAnyNode) {
            return allOrAnyNode.execute(frame, inliningTarget, object.getSequenceStorage(), AnyOrAllNodeType.ALL);
        }

        @Specialization(guards = "isBuiltinHashingCollection(object)")
        static boolean doHashColl(VirtualFrame frame, PHashingCollection object,
                        @Cached AllOrAnyHashingStorageNode allOrAnyNode) {
            return allOrAnyNode.execute(frame, object.getDictStorage(), AnyOrAllNodeType.ALL);
        }

        @Specialization
        static boolean doObject(VirtualFrame frame, Object object,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetIter getIter,
                        @Cached GetNextNode nextNode,
                        @Cached IsBuiltinObjectProfile errorProfile,
                        @Cached PyObjectIsTrueNode isTrueNode) {
            Object iterator = getIter.execute(frame, inliningTarget, object);
            int nbrIter = 0;

            while (true) {
                try {
                    Object next = nextNode.execute(frame, iterator);
                    nbrIter++;
                    if (!isTrueNode.execute(frame, inliningTarget, next)) {
                        return false;
                    }
                } catch (PException e) {
                    e.expectStopIteration(inliningTarget, errorProfile);
                    break;
                } finally {
                    LoopNode.reportLoopCount(inliningTarget, nbrIter);
                }
            }

            return true;
        }
    }

    @Builtin(name = J_ANY, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class AnyNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = "isBuiltinList(object)")
        static boolean doList(VirtualFrame frame, PList object,
                        @Bind("this") Node inliningTarget,
                        @Shared("allOrAnySeqNode") @Cached AllOrAnySequenceStorageNode allOrAnyNode) {
            return allOrAnyNode.execute(frame, inliningTarget, object.getSequenceStorage(), AnyOrAllNodeType.ANY);
        }

        @Specialization(guards = "isBuiltinTuple(object)")
        static boolean doTuple(VirtualFrame frame, PTuple object,
                        @Bind("this") Node inliningTarget,
                        @Shared("allOrAnySeqNode") @Cached AllOrAnySequenceStorageNode allOrAnyNode) {
            return allOrAnyNode.execute(frame, inliningTarget, object.getSequenceStorage(), AnyOrAllNodeType.ANY);
        }

        @Specialization(guards = "isBuiltinHashingCollection(object)")
        static boolean doHashColl(VirtualFrame frame, PHashingCollection object,
                        @Cached AllOrAnyHashingStorageNode allOrAnyNode) {
            return allOrAnyNode.execute(frame, object.getDictStorage(), AnyOrAllNodeType.ANY);
        }

        @Specialization
        static boolean doObject(VirtualFrame frame, Object object,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetIter getIter,
                        @Cached GetNextNode nextNode,
                        @Cached IsBuiltinObjectProfile errorProfile,
                        @Cached PyObjectIsTrueNode isTrueNode) {
            Object iterator = getIter.execute(frame, inliningTarget, object);
            int nbrIter = 0;

            while (true) {
                try {
                    Object next = nextNode.execute(frame, iterator);
                    nbrIter++;
                    if (isTrueNode.execute(frame, inliningTarget, next)) {
                        return true;
                    }
                } catch (PException e) {
                    e.expectStopIteration(inliningTarget, errorProfile);
                    break;
                } finally {
                    LoopNode.reportLoopCount(inliningTarget, nbrIter);
                }
            }

            return false;
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class BinOctHexHelperNode extends Node {

        @FunctionalInterface
        interface LongToString {
            String convert(long value);
        }

        abstract TruffleString execute(VirtualFrame frame, Node inliningTarget, Object o, TruffleString prefix, int radix, LongToString longToString);

        @TruffleBoundary
        private static TruffleString buildString(boolean isNegative, TruffleString prefix, TruffleString number) {
            TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING, tsbCapacity(3) + number.byteLength(TS_ENCODING));
            if (isNegative) {
                sb.appendStringUncached(T_MINUS);
            }
            sb.appendStringUncached(prefix);
            sb.appendStringUncached(number);
            return sb.toStringUncached();
        }

        @TruffleBoundary
        private static BigInteger longMaxPlusOne() {
            return BigInteger.valueOf(Long.MIN_VALUE).abs();
        }

        @TruffleBoundary
        private static String bigToString(int radix, BigInteger x) {
            return x.toString(radix);
        }

        @Specialization
        static TruffleString doL(Node inliningTarget, long x, TruffleString prefix, int radix, LongToString longToString,
                        @Exclusive @Cached InlinedConditionProfile isMinLong,
                        @Shared @Cached(inline = false) TruffleString.FromJavaStringNode fromJavaStringNode) {
            if (isMinLong.profile(inliningTarget, x == Long.MIN_VALUE)) {
                return buildString(true, prefix, fromJavaStringNode.execute(bigToString(radix, longMaxPlusOne()), TS_ENCODING));
            }
            return buildString(x < 0, prefix, fromJavaStringNode.execute(longToString.convert(Math.abs(x)), TS_ENCODING));
        }

        @Specialization
        @SuppressWarnings("unused")
        static TruffleString doD(double x, TruffleString prefix, int radix, LongToString longToString,
                        @Cached(inline = false) PRaiseNode raise) {
            throw raise.raiseIntegerInterpretationError(x);
        }

        @Specialization
        static TruffleString doPI(PInt x, TruffleString prefix, int radix, @SuppressWarnings("unused") LongToString longToString,
                        @Shared @Cached(inline = false) TruffleString.FromJavaStringNode fromJavaStringNode) {
            BigInteger value = x.getValue();
            return buildString(value.signum() < 0, prefix, fromJavaStringNode.execute(bigToString(radix, PInt.abs(value)), TS_ENCODING));
        }

        @Specialization(replaces = {"doL", "doD", "doPI"})
        static TruffleString doO(VirtualFrame frame, Node inliningTarget, Object x, TruffleString prefix, int radix, LongToString longToString,
                        @Exclusive @Cached InlinedConditionProfile isMinLong,
                        @Cached PyNumberIndexNode indexNode,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached InlinedBranchProfile isInt,
                        @Cached InlinedBranchProfile isLong,
                        @Cached InlinedBranchProfile isPInt,
                        @Shared @Cached(inline = false) TruffleString.FromJavaStringNode fromJavaStringNode) {
            Object index = indexNode.execute(frame, inliningTarget, x);
            if (index instanceof Boolean || index instanceof Integer) {
                isInt.enter(inliningTarget);
                return doL(inliningTarget, asSizeNode.executeExact(frame, inliningTarget, index), prefix, radix, longToString, isMinLong, fromJavaStringNode);
            } else if (index instanceof Long) {
                isLong.enter(inliningTarget);
                return doL(inliningTarget, (long) index, prefix, radix, longToString, isMinLong, fromJavaStringNode);
            } else if (index instanceof PInt) {
                isPInt.enter(inliningTarget);
                return doPI((PInt) index, prefix, radix, longToString, fromJavaStringNode);
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw PRaiseNode.raiseUncached(inliningTarget, PythonBuiltinClassType.NotImplementedError, toTruffleStringUncached("bin/oct/hex with native integer subclasses"));
            }
        }
    }

    // bin(object)
    @Builtin(name = J_BIN, minNumOfPositionalArgs = 1)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class BinNode extends PythonUnaryBuiltinNode {
        static final TruffleString T_BIN_PREFIX = tsLiteral("0b");

        @Specialization
        static TruffleString doIt(VirtualFrame frame, Object x,
                        @Bind("this") Node inliningTarget,
                        @Cached BinOctHexHelperNode helperNode) {
            return helperNode.execute(frame, inliningTarget, x, T_BIN_PREFIX, 2, BinNode::longToString);
        }

        @TruffleBoundary
        private static String longToString(long x) {
            return Long.toBinaryString(x);
        }
    }

    // oct(object)
    @Builtin(name = J_OCT, minNumOfPositionalArgs = 1)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class OctNode extends PythonUnaryBuiltinNode {
        static final TruffleString T_OCT_PREFIX = tsLiteral("0o");

        @Specialization
        static TruffleString doIt(VirtualFrame frame, Object x,
                        @Bind("this") Node inliningTarget,
                        @Cached BinOctHexHelperNode helperNode) {
            return helperNode.execute(frame, inliningTarget, x, T_OCT_PREFIX, 8, OctNode::longToString);
        }

        @TruffleBoundary
        private static String longToString(long x) {
            return Long.toOctalString(x);
        }
    }

    // hex(object)
    @Builtin(name = J_HEX, minNumOfPositionalArgs = 1)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class HexNode extends PythonUnaryBuiltinNode {
        static final TruffleString T_HEX_PREFIX = tsLiteral("0x");

        @Specialization
        static TruffleString doIt(VirtualFrame frame, Object x,
                        @Bind("this") Node inliningTarget,
                        @Cached BinOctHexHelperNode helperNode) {
            return helperNode.execute(frame, inliningTarget, x, T_HEX_PREFIX, 16, HexNode::longToString);
        }

        @TruffleBoundary
        private static String longToString(long x) {
            return Long.toHexString(x);
        }
    }

    // callable(object)
    @Builtin(name = J_CALLABLE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class CallableNode extends PythonBuiltinNode {

        @Specialization(guards = "isCallable(callable)")
        static boolean doCallable(@SuppressWarnings("unused") Object callable) {
            return true;
        }

        @Specialization
        static boolean doGeneric(Object object,
                        @Bind("this") Node inliningTarget,
                        @Cached PyCallableCheckNode callableCheck) {
            return callableCheck.execute(inliningTarget, object);
        }
    }

    // chr(i)
    @Builtin(name = J_CHR, minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"i"})
    @GenerateNodeFactory
    @ArgumentClinic(name = "i", conversion = ArgumentClinic.ClinicConversion.Int)
    public abstract static class ChrNode extends PythonUnaryClinicBuiltinNode {
        @Specialization
        static TruffleString charFromInt(int arg,
                        @Bind("this") Node inliningTarget,
                        @Cached TruffleString.FromCodePointNode fromCodePointNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (arg >= 0 && arg <= Character.MAX_CODE_POINT) {
                return fromCodePointNode.execute(arg, TS_ENCODING, true);
            } else {
                throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.ARG_NOT_IN_RANGE, "chr()", "0x110000");
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
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectHashNode hashNode) {
            return hashNode.execute(frame, inliningTarget, object);
        }
    }

    // dir([object])
    @Builtin(name = J_DIR, minNumOfPositionalArgs = 0, maxNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1)
    @GenerateNodeFactory
    public abstract static class DirNode extends PythonBuiltinNode {

        // logic like in 'Objects/object.c: _dir_locals'
        @Specialization(guards = "isNoValue(object)")
        Object locals(VirtualFrame frame, @SuppressWarnings("unused") Object object,
                        @Bind("this") Node inliningTarget,
                        @Cached PyEvalGetLocals getLocals,
                        @Cached("create(T_KEYS)") LookupAndCallUnaryNode callKeysNode,
                        @Cached ListBuiltins.ListSortNode sortNode,
                        @Cached ListNodes.ConstructListNode constructListNode) {
            Object localsDict = getLocals.execute(frame, inliningTarget);
            Object keysObj = callKeysNode.executeObject(frame, localsDict);
            PList list = constructListNode.execute(frame, keysObj);
            sortNode.execute(frame, list);
            return list;
        }

        @Specialization(guards = "!isNoValue(object)")
        static Object dir(VirtualFrame frame, Object object,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectDir dir) {
            return dir.execute(frame, inliningTarget, object);
        }
    }

    // divmod(a, b)
    @Builtin(name = J_DIVMOD, minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    @ImportStatic(BinaryArithmetic.class)
    public abstract static class DivModNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "b != 0")
        static PTuple doLong(long a, long b,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createTuple(new Object[]{Math.floorDiv(a, b), Math.floorMod(a, b)});
        }

        @Specialization(replaces = "doLong")
        static PTuple doLongZero(long a, long b,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PythonObjectFactory factory,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            if (b == 0) {
                throw raiseNode.get(inliningTarget).raise(PythonErrorType.ZeroDivisionError, ErrorMessages.INTEGER_DIVISION_BY_ZERO);
            }
            return factory.createTuple(new Object[]{Math.floorDiv(a, b), Math.floorMod(a, b)});
        }

        @Specialization
        static PTuple doDouble(double a, double b,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PythonObjectFactory factory,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            if (b == 0) {
                throw raiseNode.get(inliningTarget).raise(PythonErrorType.ZeroDivisionError, ErrorMessages.DIVISION_BY_ZERO);
            }
            double q = Math.floor(a / b);
            return factory.createTuple(new Object[]{q, FloatBuiltins.ModNode.mod(a, b)});
        }

        @Specialization
        static Object doObject(VirtualFrame frame, Object a, Object b,
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
        @Child private GetOrCreateDictNode getOrCreateDictNode;

        final void assertNoFreeVars(Node inliningTarget, PCode code, PRaiseNode.Lazy raiseNode) {
            Object[] freeVars = code.getFreeVars();
            if (freeVars.length > 0) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.CODE_OBJ_NO_FREE_VARIABLES, getMode());
            }
        }

        protected TruffleString getMode() {
            return T_EVAL;
        }

        static boolean isMapping(Node inliningTarget, PyMappingCheckNode mappingCheckNode, Object object) {
            return mappingCheckNode.execute(inliningTarget, object);
        }

        static boolean isAnyNone(Object object) {
            return object instanceof PNone;
        }

        final PCode createAndCheckCode(VirtualFrame frame, Node inliningTarget, Object source, PRaiseNode.Lazy raiseNode) {
            PCode code = getCompileNode().compile(frame, source, T_STRING_SOURCE, getMode(), -1, -1);
            assertNoFreeVars(inliningTarget, code, raiseNode);
            return code;
        }

        private static void inheritGlobals(PFrame callerFrame, Object[] args) {
            PArguments.setGlobals(args, callerFrame.getGlobals());
        }

        private static void inheritLocals(Node inliningTarget, PFrame callerFrame, Object[] args, GetFrameLocalsNode getFrameLocalsNode) {
            Object callerLocals = getFrameLocalsNode.execute(inliningTarget, callerFrame);
            setCustomLocals(args, callerLocals);
        }

        private static void setCustomLocals(Object[] args, Object locals) {
            PArguments.setSpecialArgument(args, locals);
        }

        private void setBuiltinsInGlobals(VirtualFrame frame, Node inliningTarget, PDict globals, HashingCollectionNodes.SetItemNode setBuiltins, PythonModule builtins) {
            if (builtins != null) {
                PDict builtinsDict = getOrCreateDictNode(builtins);
                setBuiltins.execute(frame, inliningTarget, globals, T___BUILTINS__, builtinsDict);
            } else {
                // This happens during context initialization
                return;
            }
        }

        private void setCustomGlobals(VirtualFrame frame, Node inliningTarget, PDict globals, HashingCollectionNodes.SetItemNode setBuiltins, Object[] args) {
            PythonModule builtins = getContext().getBuiltins();
            setBuiltinsInGlobals(frame, inliningTarget, globals, setBuiltins, builtins);
            PArguments.setGlobals(args, globals);
        }

        @Specialization
        @SuppressWarnings("truffle-static-method")
        Object execInheritGlobalsInheritLocals(VirtualFrame frame, Object source, @SuppressWarnings("unused") PNone globals, @SuppressWarnings("unused") PNone locals,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached ReadCallerFrameNode readCallerFrameNode,
                        @Exclusive @Cached CodeNodes.GetCodeCallTargetNode getCt,
                        @Cached GetFrameLocalsNode getFrameLocalsNode,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            PCode code = createAndCheckCode(frame, inliningTarget, source, raiseNode);
            PFrame callerFrame = readCallerFrameNode.executeWith(frame, 0);
            Object[] args = PArguments.create();
            inheritGlobals(callerFrame, args);
            inheritLocals(inliningTarget, callerFrame, args, getFrameLocalsNode);

            return invokeNode.execute(frame, getCt.execute(inliningTarget, code), args);
        }

        @Specialization
        Object execCustomGlobalsGlobalLocals(VirtualFrame frame, Object source, PDict globals, @SuppressWarnings("unused") PNone locals,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached HashingCollectionNodes.SetItemNode setBuiltins,
                        @Shared("getCt") @Cached CodeNodes.GetCodeCallTargetNode getCt,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            PCode code = createAndCheckCode(frame, inliningTarget, source, raiseNode);
            Object[] args = PArguments.create();
            setCustomGlobals(frame, inliningTarget, globals, setBuiltins, args);
            setCustomLocals(args, globals);
            RootCallTarget rootCallTarget = getCt.execute(inliningTarget, code);
            if (rootCallTarget == null) {
                throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.CANNOT_CREATE_CALL_TARGET, code);
            }

            return invokeNode.execute(frame, rootCallTarget, args);
        }

        @Specialization(guards = {"isMapping(inliningTarget, mappingCheckNode, locals)"})
        @SuppressWarnings("truffle-static-method")
        Object execInheritGlobalsCustomLocals(VirtualFrame frame, Object source, @SuppressWarnings("unused") PNone globals, Object locals,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached PyMappingCheckNode mappingCheckNode,
                        @Exclusive @Cached ReadCallerFrameNode readCallerFrameNode,
                        @Shared("getCt") @Cached CodeNodes.GetCodeCallTargetNode getCt,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            PCode code = createAndCheckCode(frame, inliningTarget, source, raiseNode);
            PFrame callerFrame = readCallerFrameNode.executeWith(frame, 0);
            Object[] args = PArguments.create();
            inheritGlobals(callerFrame, args);
            setCustomLocals(args, locals);

            return invokeNode.execute(frame, getCt.execute(inliningTarget, code), args);
        }

        @Specialization(guards = {"isMapping(inliningTarget, mappingCheckNode, locals)"})
        @SuppressWarnings("truffle-static-method")
        Object execCustomGlobalsCustomLocals(VirtualFrame frame, Object source, PDict globals, Object locals,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached PyMappingCheckNode mappingCheckNode,
                        @Shared @Cached HashingCollectionNodes.SetItemNode setBuiltins,
                        @Shared("getCt") @Cached CodeNodes.GetCodeCallTargetNode getCt,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            PCode code = createAndCheckCode(frame, inliningTarget, source, raiseNode);
            Object[] args = PArguments.create();
            setCustomGlobals(frame, inliningTarget, globals, setBuiltins, args);
            setCustomLocals(args, locals);

            return invokeNode.execute(frame, getCt.execute(inliningTarget, code), args);
        }

        @Specialization(guards = {"!isAnyNone(globals)", "!isDict(globals)"})
        @SuppressWarnings({"unused", "truffle-static-method"})
        PNone badGlobals(Object source, Object globals, Object locals,
                        @Shared("raise") @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.GLOBALS_MUST_BE_DICT, getMode(), globals);
        }

        @Specialization(guards = {"isAnyNone(globals) || isDict(globals)", "!isAnyNone(locals)", "!isMapping(inliningTarget, mappingCheckNode, locals)"})
        @SuppressWarnings({"unused", "truffle-static-method"})
        PNone badLocals(Object source, PDict globals, Object locals,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PyMappingCheckNode mappingCheckNode,
                        @Shared("raise") @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.LOCALS_MUST_BE_MAPPING, getMode(), locals);
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
            return getOrCreateDictNode.executeCached(object);
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
    @Builtin(name = J_COMPILE, minNumOfPositionalArgs = 3, parameterNames = {"source", "filename", "mode", "flags", "dont_inherit", "optimize"}, keywordOnlyNames = {"_feature_version"})
    @ArgumentClinic(name = "mode", conversion = ArgumentClinic.ClinicConversion.TString)
    @ArgumentClinic(name = "flags", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "0")
    @ArgumentClinic(name = "dont_inherit", conversion = ArgumentClinic.ClinicConversion.IntToBoolean, defaultValue = "false")
    @ArgumentClinic(name = "optimize", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1")
    @ArgumentClinic(name = "_feature_version", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1")
    @GenerateNodeFactory
    public abstract static class CompileNode extends PythonClinicBuiltinNode {

        // code.h
        private static final int CO_NESTED = 0x0010;
        private static final int CO_FUTURE_DIVISION = 0x20000;
        private static final int CO_FUTURE_ABSOLUTE_IMPORT = 0x40000;
        private static final int CO_FUTURE_WITH_STATEMENT = 0x80000;
        private static final int CO_FUTURE_PRINT_FUNCTION = 0x100000;
        private static final int CO_FUTURE_UNICODE_LITERALS = 0x200000;

        private static final int CO_FUTURE_BARRY_AS_BDFL = FutureFeature.BARRY_AS_BDFL.flagValue;
        private static final int CO_FUTURE_GENERATOR_STOP = 0x800000;
        private static final int CO_FUTURE_ANNOTATIONS = FutureFeature.ANNOTATIONS.flagValue;

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

        public final PCode compile(VirtualFrame frame, Object source, TruffleString filename, TruffleString mode, int optimize, int featureVersion) {
            return (PCode) executeInternal(frame, source, filename, mode, 0, false, optimize, featureVersion);
        }

        protected abstract Object executeInternal(VirtualFrame frame, Object source, TruffleString filename, TruffleString mode, int flags, boolean dontInherit, int optimize,
                        int featureVersion);

        @Specialization
        Object doCompile(VirtualFrame frame, TruffleString expression, TruffleString filename, TruffleString mode, int flags, boolean dontInherit, int optimize,
                        int featureVersion,
                        @Shared @Cached ReadCallerFrameNode readCallerFrame,
                        @Shared @Cached PythonObjectFactory factory) {
            if (!dontInherit) {
                PFrame fr = readCallerFrame.executeWith(frame, 0);
                PCode code = factory.createCode(fr.getTarget());
                flags |= code.getFlags() & PyCF_MASK;
            }
            return compile(expression, filename, mode, flags, dontInherit, optimize, featureVersion, factory);
        }

        @TruffleBoundary
        Object compile(TruffleString expression, TruffleString filename, TruffleString mode, int flags, @SuppressWarnings("unused") boolean dontInherit, int optimize, int featureVersion,
                        PythonObjectFactory factory) {
            checkFlags(flags);
            checkOptimize(optimize, optimize);
            checkSource(expression);

            TruffleString code = expression;
            PythonContext context = getContext();
            InputType type = getParserInputType(mode, flags);
            if (type == InputType.FUNCTION_TYPE) {
                if ((flags & PyCF_ONLY_AST) == 0) {
                    throw PRaiseNode.raiseUncached(this, ValueError, ErrorMessages.COMPILE_MODE_FUNC_TYPE_REQUIED_FLAG_ONLY_AST);
                }
            }
            if (lstrip && !code.isEmpty()) {
                int c = code.codePointAtIndexUncached(0, TS_ENCODING);
                if (c == ' ' || c == '\t') {
                    code = code.substringUncached(1, code.codePointLengthUncached(TS_ENCODING) - 1, TS_ENCODING, true);
                }
            }
            if ((flags & PyCF_ONLY_AST) != 0) {
                Source source = PythonLanguage.newSource(context, code, filename, mayBeFromFile, PythonLanguage.MIME_TYPE);
                RaisePythonExceptionErrorCallback errorCb = new RaisePythonExceptionErrorCallback(source, PythonOptions.isPExceptionWithJavaStacktrace(getLanguage()));

                EnumSet<AbstractParser.Flags> compilerFlags = EnumSet.noneOf(AbstractParser.Flags.class);
                if ((flags & PyCF_TYPE_COMMENTS) != 0) {
                    compilerFlags.add(AbstractParser.Flags.TYPE_COMMENTS);
                }
                if (featureVersion < 0) {
                    featureVersion = PythonLanguage.MINOR;
                }
                if (featureVersion < 7) {
                    compilerFlags.add(AbstractParser.Flags.ASYNC_HACKS);
                }
                Parser parser = Compiler.createParser(code.toJavaStringUncached(), errorCb, type, compilerFlags, featureVersion);
                ModTy mod = (ModTy) parser.parse();
                errorCb.triggerDeprecationWarnings();
                return AstModuleBuiltins.sst2Obj(getContext(), mod);
            }
            CallTarget ct;
            TruffleString finalCode = code;
            Supplier<CallTarget> createCode = () -> {
                if (type == InputType.FILE) {
                    Source source = PythonLanguage.newSource(context, finalCode, filename, mayBeFromFile, PythonLanguage.getCompileMimeType(optimize, flags));
                    return context.getEnv().parsePublic(source);
                } else if (type == InputType.EVAL) {
                    Source source = PythonLanguage.newSource(context, finalCode, filename, mayBeFromFile, PythonLanguage.getEvalMimeType(optimize, flags));
                    return context.getEnv().parsePublic(source);
                } else {
                    Source source = PythonLanguage.newSource(context, finalCode, filename, mayBeFromFile, PythonLanguage.MIME_TYPE);
                    return context.getLanguage().parse(context, source, InputType.SINGLE, false, optimize, false, null, FutureFeature.fromFlags(flags));
                }
            };
            if (getContext().isCoreInitialized()) {
                ct = createCode.get();
            } else {
                ct = getContext().getLanguage().cacheCode(filename, createCode);
            }
            return wrapRootCallTarget((RootCallTarget) ct, factory);
        }

        @Specialization(limit = "3")
        @SuppressWarnings("truffle-static-method")
        Object generic(VirtualFrame frame, Object wSource, Object wFilename, TruffleString mode, int flags, @SuppressWarnings("unused") boolean dontInherit, int optimize, int featureVersion,
                        @CachedLibrary(limit = "3") PythonBufferAcquireLibrary acquireLib,
                        @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Bind("this") Node inliningTarget,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @Cached CodecsModuleBuiltins.HandleDecodingErrorNode handleDecodingErrorNode,
                        @Cached PyObjectStrAsTruffleStringNode asStrNode,
                        @CachedLibrary("wSource") InteropLibrary interopLib,
                        @Cached PyUnicodeFSDecoderNode asPath,
                        @Cached WarnNode warnNode,
                        @Cached TruffleString.FromByteArrayNode fromByteArrayNode,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode,
                        @Shared @Cached ReadCallerFrameNode readCallerFrame,
                        @Shared @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (wSource instanceof PCode) {
                return wSource;
            }
            TruffleString filename;
            // TODO use PyUnicode_FSDecode
            if (acquireLib.hasBuffer(wFilename)) {
                Object filenameBuffer = acquireLib.acquireReadonly(wFilename, frame, indirectCallData);
                try {
                    TruffleString utf8 = fromByteArrayNode.execute(bufferLib.getCopiedByteArray(filenameBuffer), Encoding.UTF_8, false);
                    filename = switchEncodingNode.execute(utf8, TS_ENCODING);
                    if (!(wFilename instanceof PBytes)) {
                        warnNode.warnFormat(frame, null, DeprecationWarning, 1, ErrorMessages.PATH_SHOULD_BE_STR_BYTES_PATHLIKE_NOT_P, wFilename);
                    }
                } finally {
                    bufferLib.release(filenameBuffer, frame, indirectCallData);
                }
            } else {
                filename = asPath.execute(frame, wFilename);
            }

            if (!dontInherit) {
                PFrame fr = readCallerFrame.executeWith(frame, 0);
                PCode code = factory.createCode(fr.getTarget());
                flags |= code.getFlags() & PyCF_MASK;
            }

            if (AstModuleBuiltins.isAst(getContext(), wSource)) {
                ModTy mod = AstModuleBuiltins.obj2sst(getContext(), wSource, getParserInputType(mode, flags));
                Source source = PythonUtils.createFakeSource(filename);
                RootCallTarget rootCallTarget = getLanguage().compileModule(getContext(), mod, source, false, optimize, null, null, flags);
                return wrapRootCallTarget(rootCallTarget, factory);
            }
            TruffleString source = sourceAsString(frame, inliningTarget, wSource, filename, interopLib, acquireLib, bufferLib, handleDecodingErrorNode, asStrNode, switchEncodingNode, factory,
                            raiseNode, indirectCallData);
            checkSource(source);
            return compile(source, filename, mode, flags, dontInherit, optimize, featureVersion, factory);
        }

        private static PCode wrapRootCallTarget(RootCallTarget rootCallTarget, PythonObjectFactory factory) {
            RootNode rootNode = rootCallTarget.getRootNode();
            if (PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER) {
                if (rootNode instanceof PBytecodeDSLRootNode bytecodeDSLRootNode) {
                    bytecodeDSLRootNode.triggerDeferredDeprecationWarnings();
                }
            } else if (rootNode instanceof PBytecodeRootNode bytecodeRootNode) {
                bytecodeRootNode.triggerDeferredDeprecationWarnings();
            }
            return factory.createCode(rootCallTarget);
        }

        @TruffleBoundary
        private void checkSource(TruffleString source) throws PException {
            if (source.indexOfCodePointUncached(0, 0, source.codePointLengthUncached(TS_ENCODING), TS_ENCODING) > -1) {
                throw PConstructAndRaiseNode.getUncached().executeWithArgsOnly(null, SyntaxError, new Object[]{ErrorMessages.SRC_CODE_CANNOT_CONTAIN_NULL_BYTES});
            }
        }

        @TruffleBoundary
        private void checkOptimize(int optimize, Object kwOptimize) throws PException {
            if (optimize < -1 || optimize > 2) {
                throw PRaiseNode.raiseUncached(this, TypeError, ErrorMessages.INVALID_OPTIMIZE_VALUE, kwOptimize);
            }
        }

        @TruffleBoundary
        private void checkFlags(int flags) {
            if ((flags & ~(PyCF_MASK | PyCF_MASK_OBSOLETE | PyCF_COMPILE_MASK)) != 0) {
                throw PRaiseNode.raiseUncached(this, ValueError, null, NO_VALUE, ErrorMessages.UNRECOGNIZED_FLAGS, PythonUtils.EMPTY_OBJECT_ARRAY);
            }
        }

        @TruffleBoundary
        private InputType getParserInputType(TruffleString mode, int flags) {
            if (mode.equalsUncached(T_EXEC, TS_ENCODING)) {
                return InputType.FILE;
            } else if (mode.equalsUncached(T_EVAL, TS_ENCODING)) {
                return InputType.EVAL;
            } else if (mode.equalsUncached(StringLiterals.T_SINGLE, TS_ENCODING)) {
                return InputType.SINGLE;
            } else if (mode.equalsUncached(StringLiterals.T_FUNC_TYPE, TS_ENCODING)) {
                return InputType.FUNCTION_TYPE;
            } else {
                if ((flags & PyCF_ONLY_AST) != 0) {
                    throw PRaiseNode.raiseUncached(this, ValueError, ErrorMessages.COMPILE_MODE_MUST_BE_AST_ONLY);
                } else {
                    throw PRaiseNode.raiseUncached(this, ValueError, ErrorMessages.COMPILE_MODE_MUST_BE);
                }
            }
        }

        // modeled after _Py_SourceAsString
        TruffleString sourceAsString(VirtualFrame frame, Node inliningTarget, Object source, TruffleString filename, InteropLibrary interopLib, PythonBufferAcquireLibrary acquireLib,
                        PythonBufferAccessLibrary bufferLib, CodecsModuleBuiltins.HandleDecodingErrorNode handleDecodingErrorNode, PyObjectStrAsTruffleStringNode asStrNode,
                        TruffleString.SwitchEncodingNode switchEncodingNode, PythonObjectFactory factory, PRaiseNode.Lazy raiseNode, IndirectCallData indirectCallData) {
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
                    buffer = acquireLib.acquireReadonly(source, frame, indirectCallData);
                } catch (PException e) {
                    throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.ARG_D_MUST_BE_S, "compile()", 1, "string, bytes or AST object");
                }
                try {
                    byte[] bytes = bufferLib.getInternalOrCopiedByteArray(source);
                    int bytesLen = bufferLib.getBufferLength(source);
                    Charset charset = PythonFileDetector.findEncodingStrict(bytes, bytesLen);
                    TruffleString pythonEncodingNameFromJavaName = CharsetMapping.getPythonEncodingNameFromJavaName(charset.name());
                    CodecsModuleBuiltins.TruffleDecoder decoder = new CodecsModuleBuiltins.TruffleDecoder(pythonEncodingNameFromJavaName, charset, bytes, bytesLen, CodingErrorAction.REPORT);
                    if (!decoder.decodingStep(true)) {
                        try {
                            handleDecodingErrorNode.execute(frame, decoder, T_STRICT, factory.createBytes(bytes, bytesLen));
                            throw CompilerDirectives.shouldNotReachHere();
                        } catch (PException e) {
                            throw raiseInvalidSyntax(filename, "(unicode error) %s", asStrNode.execute(frame, inliningTarget, e.getEscapedException()));
                        }
                    }
                    return decoder.getString();
                } catch (PythonFileDetector.InvalidEncodingException e) {
                    throw raiseInvalidSyntax(filename, "(unicode error) %s", e.getEncodingName());
                } finally {
                    bufferLib.release(buffer, frame, indirectCallData);
                }
            }
        }

        @TruffleBoundary
        private RuntimeException raiseInvalidSyntax(TruffleString filename, String format, Object... args) {
            PythonContext context = getContext();
            // Create non-empty source to avoid overwriting the message with "unexpected EOF"
            Source source = PythonLanguage.newSource(context, T_SPACE, filename, mayBeFromFile, null);
            SourceRange sourceRange = new SourceRange(1, 0, 1, 0);
            TruffleString message = toTruffleStringUncached(String.format(format, args));
            throw raiseSyntaxError(ErrorCallback.ErrorType.Syntax, sourceRange, message, source, PythonOptions.isPExceptionWithJavaStacktrace(context.getLanguage()));
        }

        public static CompileNode create(boolean mapFilenameToUri) {
            return BuiltinFunctionsFactory.CompileNodeFactory.create(mapFilenameToUri, false, new ReadArgumentNode[]{});
        }

        public static CompileNode create(boolean mapFilenameToUri, boolean lstrip) {
            return BuiltinFunctionsFactory.CompileNodeFactory.create(mapFilenameToUri, lstrip, new ReadArgumentNode[]{});
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BuiltinFunctionsClinicProviders.CompileNodeClinicProviderGen.INSTANCE;
        }
    }

    // delattr(object, name)
    @Builtin(name = J_DELATTR, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class DelAttrNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object delattr(VirtualFrame frame, Object object, Object name,
                        @Bind("this") Node inliningTarget,
                        @Cached DeleteAttributeNode delNode) {
            delNode.execute(frame, inliningTarget, object, name);
            return PNone.NONE;
        }
    }

    // getattr(object, name[, default])
    @Builtin(name = J_GETATTR, minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class GetAttrNode extends PythonTernaryBuiltinNode {

        @Specialization(guards = "isNoValue(defaultValue)")
        static Object getAttrNoDefault(VirtualFrame frame, Object primary, Object name, @SuppressWarnings("unused") Object defaultValue,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetAttrO getAttr) {
            return getAttr.execute(frame, inliningTarget, primary, name);
        }

        @Specialization(guards = "!isNoValue(defaultValue)")
        static Object getAttrWithDefault(VirtualFrame frame, Object primary, Object name, Object defaultValue,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedConditionProfile noValueProfile,
                        @Cached PyObjectLookupAttrO lookupAttr) {
            Object result = lookupAttr.execute(frame, inliningTarget, primary, name);
            if (noValueProfile.profile(inliningTarget, result == NO_VALUE)) {
                return defaultValue;
            } else {
                return result;
            }
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

        protected final byte depth;

        protected RecursiveBinaryCheckBaseNode(byte depth) {
            this.depth = depth;
        }

        public abstract boolean executeWith(VirtualFrame frame, Object instance, Object cls);

        @NeverDefault
        protected final RecursiveBinaryCheckBaseNode createRecursive() {
            return createRecursive((byte) (depth + 1));
        }

        @NeverDefault
        protected final RecursiveBinaryCheckBaseNode createNonRecursive() {
            return createRecursive(NON_RECURSIVE);
        }

        protected RecursiveBinaryCheckBaseNode createRecursive(@SuppressWarnings("unused") byte newDepth) {
            throw new AbstractMethodError(); // Cannot be really abstract b/c Truffle DSL...
        }

        @Idempotent
        protected int getMaxExplodeLoop() {
            return MAX_EXPLODE_LOOP >> depth;
        }

        @Specialization(guards = {"depth < getNodeRecursionLimit()", "getLength(clsTuple) == cachedLen", "cachedLen < getMaxExplodeLoop()"}, //
                        limit = "getVariableArgumentInlineCacheLimit()")
        @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL_UNTIL_RETURN)
        static boolean doTupleConstantLen(VirtualFrame frame, Object instance, PTuple clsTuple,
                        @Bind("this") Node inliningTarget,
                        @Cached("getLength(clsTuple)") int cachedLen,
                        @Shared @Cached GetObjectArrayNode getObjectArrayNode,
                        @Shared @Cached("createRecursive()") RecursiveBinaryCheckBaseNode recursiveNode) {
            Object[] array = getObjectArrayNode.execute(inliningTarget, clsTuple);
            for (int i = 0; i < cachedLen; i++) {
                Object cls = array[i];
                if (recursiveNode.executeWith(frame, instance, cls)) {
                    return true;
                }
            }
            return false;
        }

        @Specialization(guards = "depth < getNodeRecursionLimit()", replaces = "doTupleConstantLen")
        static boolean doRecursiveWithNode(VirtualFrame frame, Object instance, PTuple clsTuple,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached GetObjectArrayNode getObjectArrayNode,
                        @Shared @Cached("createRecursive()") RecursiveBinaryCheckBaseNode recursiveNode) {
            for (Object cls : getObjectArrayNode.execute(inliningTarget, clsTuple)) {
                if (recursiveNode.executeWith(frame, instance, cls)) {
                    return true;
                }
            }
            return false;
        }

        @Specialization(guards = {"depth != NON_RECURSIVE", "depth >= getNodeRecursionLimit()"})
        static boolean doRecursiveWithLoop(VirtualFrame frame, Object instance, PTuple clsTuple,
                        @Bind("this") Node inliningTarget,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @Shared @Cached GetObjectArrayNode getObjectArrayNode,
                        @Cached("createNonRecursive()") RecursiveBinaryCheckBaseNode node) {
            PythonLanguage language = PythonLanguage.get(inliningTarget);
            PythonContext context = PythonContext.get(inliningTarget);
            Object state = IndirectCallContext.enter(frame, language, context, indirectCallData);
            try {
                // Note: we need actual recursion to trigger the stack overflow error like CPython
                // Note: we need fresh RecursiveBinaryCheckBaseNode and cannot use "this", because
                // other children of this executed by other specializations may assume they'll
                // always get a non-null frame
                return callRecursiveWithNodeTruffleBoundary(inliningTarget, instance, clsTuple, getObjectArrayNode, node);
            } finally {
                IndirectCallContext.exit(frame, language, context, state);
            }
        }

        @Specialization(guards = "depth == NON_RECURSIVE")
        boolean doRecursiveWithLoopReuseThis(VirtualFrame frame, Object instance, PTuple clsTuple,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached GetObjectArrayNode getObjectArrayNode) {
            // This should be only called by doRecursiveWithLoop, now we have to reuse this to stop
            // recursive node creation. It is OK, because now all specializations should always get
            // null frame
            assert frame == null;
            return callRecursiveWithNodeTruffleBoundary(inliningTarget, instance, clsTuple, getObjectArrayNode, this);
        }

        @TruffleBoundary
        private static boolean callRecursiveWithNodeTruffleBoundary(Node inliningTarget, Object instance, PTuple clsTuple, GetObjectArrayNode getObjectArrayNode, RecursiveBinaryCheckBaseNode node) {
            return doRecursiveWithNode(null, instance, clsTuple, inliningTarget, getObjectArrayNode, node);
        }

        protected static int getLength(PTuple t) {
            return t.getSequenceStorage().length();
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

        private static TriState isInstanceCheckInternal(VirtualFrame frame, Node inliningTarget, Object instance, Object cls, LookupAndCallBinaryNode instanceCheckNode,
                        CoerceToBooleanNode castToBooleanNode) {
            Object instanceCheckResult = instanceCheckNode.executeObject(frame, cls, instance);
            if (instanceCheckResult == NOT_IMPLEMENTED) {
                return TriState.UNDEFINED;
            }
            return TriState.valueOf(castToBooleanNode.executeBoolean(frame, inliningTarget, instanceCheckResult));
        }

        @Specialization(guards = "isPythonClass(cls)")
        static boolean isInstance(VirtualFrame frame, Object instance, Object cls,
                        @Bind("this") Node inliningTarget,
                        @Shared("instanceCheck") @Cached("create(InstanceCheck)") LookupAndCallBinaryNode instanceCheckNode,
                        @Exclusive @Cached CoerceToBooleanNode.YesNode castToBooleanNode,
                        @Cached GetClassNode getClassNode,
                        @Cached IsSameTypeNode isSameTypeNode,
                        @Cached IsSubtypeNode isSubtypeNode) {
            Object instanceClass = getClassNode.execute(inliningTarget, instance);
            return isSameTypeNode.execute(inliningTarget, instanceClass, cls) || isSubtypeNode.execute(frame, instanceClass, cls)//
                            || isInstanceCheckInternal(frame, inliningTarget, instance, cls, instanceCheckNode, castToBooleanNode) == TriState.TRUE;
        }

        @Specialization(guards = {"!isPTuple(cls)", "!isPythonClass(cls)"})
        static boolean isInstance(VirtualFrame frame, Object instance, Object cls,
                        @Bind("this") Node inliningTarget,
                        @Shared("instanceCheck") @Cached("create(InstanceCheck)") LookupAndCallBinaryNode instanceCheckNode,
                        @Exclusive @Cached CoerceToBooleanNode.YesNode castToBooleanNode,
                        @Cached TypeBuiltins.InstanceCheckNode typeInstanceCheckNode) {
            TriState check = isInstanceCheckInternal(frame, inliningTarget, instance, cls, instanceCheckNode, castToBooleanNode);
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
        public abstract boolean executeBoolean(VirtualFrame frame, Object derived, Object cls);

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
                        @Bind("this") Node inliningTarget,
                        @Cached("create(Subclasscheck)") LookupAndCallBinaryNode subclassCheckNode,
                        @Cached CoerceToBooleanNode.YesNode castToBooleanNode,
                        @Cached IsSubtypeNode isSubtypeNode) {
            Object instanceCheckResult = subclassCheckNode.executeObject(frame, cls, derived);
            if (instanceCheckResult != NOT_IMPLEMENTED) {
                return castToBooleanNode.executeBoolean(frame, inliningTarget, instanceCheckResult);
            }
            return isSubtypeNode.execute(frame, derived, cls);
        }

        @NeverDefault
        public static IsSubClassNode create() {
            return BuiltinFunctionsFactory.IsSubClassNodeFactory.create();
        }
    }

    // iter(object[, sentinel])
    @Builtin(name = J_ITER, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @ReportPolymorphism
    public abstract static class IterNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(sentinel)")
        static Object iter(VirtualFrame frame, Object object, @SuppressWarnings("unused") PNone sentinel,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetIter getIter) {
            return getIter.execute(frame, inliningTarget, object);
        }

        @Specialization(guards = {"callableCheck.execute(this, callable)", "!isNoValue(sentinel)"}, limit = "1")
        static Object iter(Object callable, Object sentinel,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached PyCallableCheckNode callableCheck,
                        @Cached PythonObjectFactory factory) {
            return factory.createSentinelIterator(callable, sentinel);
        }

        @Fallback
        @SuppressWarnings("unused")
        static Object iterNotCallable(Object callable, Object sentinel,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.ITER_V_MUST_BE_CALLABLE);
        }
    }

    // len(s)
    @Builtin(name = J_LEN, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class LenNode extends PythonUnaryBuiltinNode {
        @Specialization
        public int len(VirtualFrame frame, Object obj,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectSizeNode sizeNode) {
            return sizeNode.execute(frame, inliningTarget, obj);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class MinMaxNode extends Node {

        abstract Object execute(VirtualFrame frame, Node inliningTarget, Object arg1, Object[] args, Object keywordArgIn, Object defaultVal, String name, BinaryComparisonNode comparisonNode);

        @Specialization(guards = "args.length == 0")
        static Object minmaxSequenceWithKey(VirtualFrame frame, Node inliningTarget, Object arg1, @SuppressWarnings("unused") Object[] args, Object keywordArgIn, Object defaultVal, String name,
                        BinaryComparisonNode compare,
                        @Exclusive @Cached PyObjectGetIter getIter,
                        @Cached(inline = false) GetNextNode nextNode,
                        @Exclusive @Cached CoerceToBooleanNode.YesNode castToBooleanNode,
                        @Exclusive @Cached CallNode.Lazy keyCall,
                        @Exclusive @Cached InlinedBranchProfile seenNonBoolean,
                        @Exclusive @Cached InlinedConditionProfile keywordArgIsNone,
                        @Exclusive @Cached IsBuiltinObjectProfile errorProfile1,
                        @Exclusive @Cached IsBuiltinObjectProfile errorProfile2,
                        @Exclusive @Cached InlinedConditionProfile hasDefaultProfile,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            boolean kwArgsAreNone = keywordArgIsNone.profile(inliningTarget, PGuards.isPNone(keywordArgIn));
            Object keywordArg = kwArgsAreNone ? null : keywordArgIn;

            Object iterator = getIter.execute(frame, inliningTarget, arg1);
            Object currentValue;
            try {
                currentValue = nextNode.execute(frame, iterator);
            } catch (PException e) {
                e.expectStopIteration(inliningTarget, errorProfile1);
                if (hasDefaultProfile.profile(inliningTarget, isNoValue(defaultVal))) {
                    throw raiseNode.get(inliningTarget).raise(PythonErrorType.ValueError, ErrorMessages.ARG_IS_EMPTY_SEQ, name);
                } else {
                    return defaultVal;
                }
            }
            Object currentKey = applyKeyFunction(frame, inliningTarget, keywordArg, keyCall, currentValue);
            int loopCount = 0;
            try {
                while (true) {
                    Object nextValue;
                    try {
                        nextValue = nextNode.execute(frame, iterator);
                    } catch (PException e) {
                        e.expectStopIteration(inliningTarget, errorProfile2);
                        break;
                    }
                    Object nextKey = applyKeyFunction(frame, inliningTarget, keywordArg, keyCall, nextValue);
                    boolean isTrue;
                    if (!seenNonBoolean.wasEntered(inliningTarget)) {
                        try {
                            isTrue = compare.executeBool(frame, nextKey, currentKey);
                        } catch (UnexpectedResultException e) {
                            seenNonBoolean.enter(inliningTarget);
                            isTrue = castToBooleanNode.executeBoolean(frame, inliningTarget, e.getResult());
                        }
                    } else {
                        isTrue = castToBooleanNode.executeBoolean(frame, inliningTarget, compare.executeObject(frame, nextKey, currentKey));
                    }
                    if (isTrue) {
                        currentKey = nextKey;
                        currentValue = nextValue;
                    }
                    loopCount++;
                }
            } finally {
                LoopNode.reportLoopCount(inliningTarget, loopCount < 0 ? Integer.MAX_VALUE : loopCount);
            }
            return currentValue;
        }

        @Specialization(guards = {"args.length != 0"})
        static Object minmaxBinaryWithKey(VirtualFrame frame, Node inliningTarget, Object arg1, Object[] args, Object keywordArgIn, Object defaultVal, String name, BinaryComparisonNode compare,
                        @Exclusive @Cached CallNode.Lazy keyCall,
                        @Exclusive @Cached CoerceToBooleanNode.YesNode castToBooleanNode,
                        @Exclusive @Cached InlinedBranchProfile seenNonBoolean,
                        @Exclusive @Cached InlinedConditionProfile keywordArgIsNone,
                        @Exclusive @Cached InlinedConditionProfile moreThanTwo,
                        @Exclusive @Cached InlinedLoopConditionProfile loopProfile,
                        @Exclusive @Cached InlinedConditionProfile hasDefaultProfile,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {

            boolean kwArgsAreNone = keywordArgIsNone.profile(inliningTarget, PGuards.isPNone(keywordArgIn));
            Object keywordArg = kwArgsAreNone ? null : keywordArgIn;

            if (!hasDefaultProfile.profile(inliningTarget, isNoValue(defaultVal))) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.CANNOT_SPECIFY_DEFAULT_FOR_S, name);
            }
            Object currentValue = arg1;
            Object currentKey = applyKeyFunction(frame, inliningTarget, keywordArg, keyCall, currentValue);
            Object nextValue = args[0];
            Object nextKey = applyKeyFunction(frame, inliningTarget, keywordArg, keyCall, nextValue);
            boolean isTrue;
            if (!seenNonBoolean.wasEntered(inliningTarget)) {
                try {
                    isTrue = compare.executeBool(frame, nextKey, currentKey);
                } catch (UnexpectedResultException e) {
                    seenNonBoolean.enter(inliningTarget);
                    isTrue = castToBooleanNode.executeBoolean(frame, inliningTarget, e.getResult());
                }
            } else {
                isTrue = castToBooleanNode.executeBoolean(frame, inliningTarget, compare.executeObject(frame, nextKey, currentKey));
            }
            if (isTrue) {
                currentKey = nextKey;
                currentValue = nextValue;
            }
            if (moreThanTwo.profile(inliningTarget, args.length > 1)) {
                loopProfile.profileCounted(inliningTarget, args.length);
                LoopNode.reportLoopCount(inliningTarget, args.length);
                for (int i = 0; loopProfile.inject(inliningTarget, i < args.length); i++) {
                    nextValue = args[i];
                    nextKey = applyKeyFunction(frame, inliningTarget, keywordArg, keyCall, nextValue);
                    if (!seenNonBoolean.wasEntered(inliningTarget)) {
                        try {
                            isTrue = compare.executeBool(frame, nextKey, currentKey);
                        } catch (UnexpectedResultException e) {
                            seenNonBoolean.enter(inliningTarget);
                            isTrue = castToBooleanNode.executeBoolean(frame, inliningTarget, e.getResult());
                        }
                    } else {
                        isTrue = castToBooleanNode.executeBoolean(frame, inliningTarget, compare.executeObject(frame, nextKey, currentKey));
                    }
                    if (isTrue) {
                        currentKey = nextKey;
                        currentValue = nextValue;
                    }
                }
            }
            return currentValue;
        }

        private static Object applyKeyFunction(VirtualFrame frame, Node inliningTarget, Object keywordArg, CallNode.Lazy keyCall, Object currentValue) {
            return keywordArg == null ? currentValue : keyCall.get(inliningTarget).execute(frame, keywordArg, new Object[]{currentValue}, PKeyword.EMPTY_KEYWORDS);
        }
    }

    // max(iterable, *[, key])
    // max(arg1, arg2, *args[, key])
    @Builtin(name = J_MAX, minNumOfPositionalArgs = 1, takesVarArgs = true, keywordOnlyNames = {"key", "default"}, doc = "max(iterable, *[, default=obj, key=func]) -> value\n" +
                    "max(arg1, arg2, *args, *[, key=func]) -> value\n\n" + "With a single iterable argument, return its biggest item. The\n" +
                    "default keyword-only argument specifies an object to return if\n" + "the provided iterable is empty.\n" + "With two or more arguments, return the largest argument.")
    @GenerateNodeFactory
    public abstract static class MaxNode extends PythonBuiltinNode {

        @Specialization
        static Object max(VirtualFrame frame, Object arg1, Object[] args, Object keywordArgIn, Object defaultVal,
                        @Bind("this") Node inliningTarget,
                        @Cached MinMaxNode minMaxNode,
                        @Cached BinaryComparisonNode.GtNode gtNode) {
            return minMaxNode.execute(frame, inliningTarget, arg1, args, keywordArgIn, defaultVal, "max", gtNode);
        }
    }

    // min(iterable, *[, key])
    // min(arg1, arg2, *args[, key])
    @Builtin(name = J_MIN, minNumOfPositionalArgs = 1, takesVarArgs = true, keywordOnlyNames = {"key", "default"})
    @GenerateNodeFactory
    public abstract static class MinNode extends PythonBuiltinNode {
        @Specialization
        static Object min(VirtualFrame frame, Object arg1, Object[] args, Object keywordArgIn, Object defaultVal,
                        @Bind("this") Node inliningTarget,
                        @Cached MinMaxNode minMaxNode,
                        @Cached BinaryComparisonNode.LtNode ltNode) {
            return minMaxNode.execute(frame, inliningTarget, arg1, args, keywordArgIn, defaultVal, "min", ltNode);
        }
    }

    // next(iterator[, default])
    @SuppressWarnings("unused")
    @Builtin(name = J_NEXT, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class NextNode extends PythonBinaryBuiltinNode {
        @Specialization
        public Object next(VirtualFrame frame, Object iterator, Object defaultObject,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedConditionProfile defaultIsNoValue,
                        @Cached("createNextCall()") LookupAndCallUnaryNode callNode,
                        @Cached IsBuiltinObjectProfile errorProfile) {
            if (defaultIsNoValue.profile(inliningTarget, isNoValue(defaultObject))) {
                return callNode.executeObject(frame, iterator);
            } else {
                try {
                    return callNode.executeObject(frame, iterator);
                } catch (PException e) {
                    e.expectStopIteration(inliningTarget, errorProfile);
                    return defaultObject;
                }
            }
        }

        @NeverDefault
        protected LookupAndCallUnaryNode createNextCall() {
            return LookupAndCallUnaryNode.create(T___NEXT__, () -> new LookupAndCallUnaryNode.NoAttributeHandler() {
                @Child PRaiseNode raiseNode = PRaiseNode.create();

                @Override
                public Object execute(Object iterator) {
                    throw raiseNode.raise(TypeError, ErrorMessages.OBJ_ISNT_ITERATOR, iterator);
                }
            });
        }
    }

    // ord(c)
    @Builtin(name = J_ORD, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @ImportStatic(PGuards.class)
    public abstract static class OrdNode extends PythonBuiltinNode {

        @Specialization(guards = "isString(chrObj)")
        static int ord(Object chrObj,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringNode castToStringNode,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.CodePointAtIndexNode codePointAtIndexNode,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            TruffleString chr;
            try {
                chr = castToStringNode.execute(inliningTarget, chrObj);
            } catch (CannotCastException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
            int len = codePointLengthNode.execute(chr, TS_ENCODING);
            if (len != 1) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.EXPECTED_CHARACTER_BUT_STRING_FOUND, "ord()", len);
            }
            return codePointAtIndexNode.execute(chr, 0, TS_ENCODING);
        }

        @Specialization
        static long ord(PBytesLike chr,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToJavaLongExactNode castNode,
                        @Cached SequenceStorageNodes.GetItemNode getItemNode,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            int len = chr.getSequenceStorage().length();
            if (len != 1) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.EXPECTED_CHARACTER_BUT_STRING_FOUND, "ord()", len);
            }
            return castNode.execute(inliningTarget, getItemNode.execute(chr.getSequenceStorage(), 0));
        }

        @Specialization(guards = {"!isString(obj)", "!isBytes(obj)"})
        static Object ord(@SuppressWarnings("unused") Object obj,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.S_EXPECTED_STRING_OF_LEN_BUT_P, "ord()", "1", "obj");
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
                        @Bind("this") Node inliningTarget,
                        @Shared("getWriteMethod") @Cached PyObjectGetAttr getWriteMethod,
                        @Shared("callWrite") @Cached CallNode callWrite,
                        @Shared("callFlush") @Cached PyObjectCallMethodObjArgs callFlush,
                        @Shared("strNode") @Cached PyObjectStrAsObjectNode strNode) {
            Object stdout = getStdout();
            return printAllGiven(frame, values, T_SPACE, T_NEWLINE, stdout, false, inliningTarget, getWriteMethod, callWrite, callFlush, strNode);
        }

        @Specialization(guards = {"!isNone(file)", "!isNoValue(file)"})
        static PNone printAllGiven(VirtualFrame frame, Object[] values, TruffleString sep, TruffleString end, Object file, boolean flush,
                        @Bind("this") Node inliningTarget,
                        @Shared("getWriteMethod") @Cached PyObjectGetAttr getWriteMethod,
                        @Shared("callWrite") @Cached CallNode callWrite,
                        @Shared("callFlush") @Cached PyObjectCallMethodObjArgs callFlush,
                        @Shared("strNode") @Cached PyObjectStrAsObjectNode strNode) {
            int lastValue = values.length - 1;
            // Note: the separate lookup is necessary due to different __getattr__ treatment than
            // method lookup
            Object writeMethod = getWriteMethod.execute(frame, inliningTarget, file, T_WRITE);
            for (int i = 0; i < lastValue; i++) {
                callWrite.execute(frame, writeMethod, strNode.execute(frame, inliningTarget, values[i]));
                callWrite.execute(frame, writeMethod, sep);
            }
            if (lastValue >= 0) {
                callWrite.execute(frame, writeMethod, strNode.execute(frame, inliningTarget, values[lastValue]));
            }
            callWrite.execute(frame, writeMethod, end);
            if (flush) {
                callFlush.execute(frame, inliningTarget, file, T_FLUSH);
            }
            return PNone.NONE;
        }

        @Specialization(replaces = {"printAllGiven", "printNoKeywords"})
        @SuppressWarnings("truffle-static-method")
        PNone printGeneric(VirtualFrame frame, Object[] values, Object sepIn, Object endIn, Object fileIn, Object flushIn,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringNode castSep,
                        @Cached CastToTruffleStringNode castEnd,
                        @Cached CoerceToBooleanNode.YesNode castFlush,
                        @Exclusive @Cached PyObjectGetAttr getWriteMethod,
                        @Exclusive @Cached CallNode callWrite,
                        @Exclusive @Cached PyObjectCallMethodObjArgs callFlush,
                        @Exclusive @Cached PyObjectStrAsObjectNode strNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            TruffleString sep;
            try {
                sep = sepIn instanceof PNone ? T_SPACE : castSep.execute(inliningTarget, sepIn);
            } catch (CannotCastException e) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.SEP_MUST_BE_NONE_OR_STRING, sepIn);
            }

            TruffleString end;
            try {
                end = endIn instanceof PNone ? T_NEWLINE : castEnd.execute(inliningTarget, endIn);
            } catch (CannotCastException e) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.S_MUST_BE_NONE_OR_STRING, "end", sepIn);
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
                flush = castFlush.executeBoolean(frame, inliningTarget, flushIn);
            }
            return printAllGiven(frame, values, sep, end, file, flush, inliningTarget, getWriteMethod, callWrite, callFlush, strNode);
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
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw PRaiseNode.raiseUncached(this, RuntimeError, ErrorMessages.LOST_SYSSTDOUT);
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
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectReprAsObjectNode reprNode) {
            return reprNode.execute(frame, inliningTarget, obj);
        }
    }

    // format(object, [format_spec])
    @Builtin(name = J_FORMAT, minNumOfPositionalArgs = 1, parameterNames = {"object", "format_spec"})
    @GenerateNodeFactory
    @OperationProxy.Proxyable
    @ImportStatic(PGuards.class)
    public abstract static class FormatNode extends PythonBinaryBuiltinNode {

        @Specialization
        public static Object format(VirtualFrame frame, Object obj, Object formatSpec,
                        @Bind("this") Node inliningTarget,
                        @Cached("create(Format)") LookupAndCallBinaryNode callFormat,
                        @Cached InlinedConditionProfile formatIsNoValueProfile,
                        @Cached PRaiseNode.Lazy raiseNode) {
            Object format = formatIsNoValueProfile.profile(inliningTarget, isNoValue(formatSpec)) ? T_EMPTY_STRING : formatSpec;
            Object res = callFormat.executeObject(frame, obj, format);
            if (res == NO_VALUE) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.TYPE_DOESNT_DEFINE_FORMAT, obj);
            }
            if (!PGuards.isString(res)) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.S_MUST_RETURN_S_NOT_P, T___FORMAT__, "str", res);
            }
            return res;
        }

        @NeverDefault
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
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectAsciiNode asciiNode) {
            return asciiNode.execute(frame, inliningTarget, obj);
        }
    }

    // round(number[, ndigits])
    @Builtin(name = J_ROUND, minNumOfPositionalArgs = 1, parameterNames = {"number", "ndigits"})
    @GenerateNodeFactory
    public abstract static class RoundNode extends PythonBuiltinNode {
        @Specialization
        static Object round(VirtualFrame frame, Object x, @SuppressWarnings("unused") PNone n,
                        @Bind("this") Node inliningTarget,
                        @Cached("create(Round)") LookupAndCallUnaryNode callRound,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            Object result = callRound.executeObject(frame, x);
            if (result == PNone.NO_VALUE) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.TYPE_DOESNT_DEFINE_METHOD, x, T___ROUND__);
            }
            return result;
        }

        @Specialization(guards = "!isPNone(n)")
        static Object round(VirtualFrame frame, Object x, Object n,
                        @Bind("this") Node inliningTarget,
                        @Cached("create(Round)") LookupAndCallBinaryNode callRound,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            Object result = callRound.executeObject(frame, x, n);
            if (result == NOT_IMPLEMENTED) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.TYPE_DOESNT_DEFINE_METHOD, x, T___ROUND__);
            }
            return result;
        }
    }

    // setattr(object, name, value)
    @Builtin(name = J_SETATTR, minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class SetAttrNode extends PythonTernaryBuiltinNode {
        @Specialization
        static Object setAttr(VirtualFrame frame, Object object, Object key, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectSetAttrO setAttrNode) {
            setAttrNode.execute(frame, inliningTarget, object, key, value);
            return PNone.NONE;
        }
    }

    // hasattr(object, name)
    @Builtin(name = J_HASATTR, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class HasAttrNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean hasAttr(VirtualFrame frame, Object object, Object key,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectLookupAttrO pyObjectLookupAttr) {
            return pyObjectLookupAttr.execute(frame, inliningTarget, object, key) != PNone.NO_VALUE;
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
    }

    @Builtin(name = J_BREAKPOINT, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class BreakPointNode extends PythonBuiltinNode {
        @Child private ReadAttributeFromObjectNode getBreakpointhookNode;
        @Child private CallNode callNode;

        @Specialization
        Object doIt(VirtualFrame frame, Object[] args, PKeyword[] kwargs,
                        @Bind("this") Node inliningTarget,
                        @Cached HashingStorageGetItem getItem,
                        @Cached PRaiseNode.Lazy raiseNode) {
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
                Object sysModule = getItem.execute(inliningTarget, sysModules.getDictStorage(), T_SYS);
                Object breakpointhook = getBreakpointhookNode.execute(sysModule, T_BREAKPOINTHOOK);
                if (breakpointhook == PNone.NO_VALUE) {
                    throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.RuntimeError, ErrorMessages.LOST_SYSBREAKPOINTHOOK);
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

    @Builtin(name = J_POW, minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 0, parameterNames = {"base", "exp", "mod"})
    @GenerateNodeFactory
    public abstract static class PowNode extends PythonTernaryBuiltinNode {
        @NeverDefault
        static BinaryOpNode binaryPow() {
            return BinaryArithmetic.Pow.create();
        }

        @NeverDefault
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

        @Specialization(guards = "isNoValue(start)", rewriteOn = UnexpectedResultException.class)
        int sumIntNone(VirtualFrame frame, Object arg1, @SuppressWarnings("unused") PNone start,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PyNumberAddNode addNode,
                        @Shared @Cached IsBuiltinObjectProfile errorProfile,
                        @Shared("getIter") @Cached PyObjectGetIter getIter) throws UnexpectedResultException {
            return sumIntInternal(frame, inliningTarget, arg1, 0, addNode, getIter, errorProfile);
        }

        @Specialization(rewriteOn = UnexpectedResultException.class)
        int sumIntInt(VirtualFrame frame, Object arg1, int start,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PyNumberAddNode addNode,
                        @Shared @Cached IsBuiltinObjectProfile errorProfile,
                        @Shared("getIter") @Cached PyObjectGetIter getIter) throws UnexpectedResultException {
            return sumIntInternal(frame, inliningTarget, arg1, start, addNode, getIter, errorProfile);
        }

        private int sumIntInternal(VirtualFrame frame, Node inliningTarget, Object arg1, int start, PyNumberAddNode add, PyObjectGetIter getIter,
                        IsBuiltinObjectProfile errorProfile) throws UnexpectedResultException {
            Object iterator = getIter.execute(frame, inliningTarget, arg1);
            int value = start;
            while (true) {
                int nextValue;
                try {
                    nextValue = PGuards.expectInteger(next.executeObject(frame, iterator));
                } catch (PException e) {
                    e.expectStopIteration(inliningTarget, errorProfile);
                    return value;
                } catch (UnexpectedResultException e) {
                    Object newValue = add.execute(frame, inliningTarget, value, e.getResult());
                    throw new UnexpectedResultException(iterateGeneric(frame, inliningTarget, iterator, newValue, add, errorProfile));
                }
                try {
                    value = add.executeInt(frame, inliningTarget, value, nextValue);
                } catch (UnexpectedResultException e) {
                    throw new UnexpectedResultException(iterateGeneric(frame, inliningTarget, iterator, e.getResult(), add, errorProfile));
                }
            }
        }

        @Specialization(rewriteOn = UnexpectedResultException.class)
        double sumDoubleDouble(VirtualFrame frame, Object arg1, double start,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PyNumberAddNode addNode,
                        @Shared @Cached IsBuiltinObjectProfile errorProfile,
                        // dummy inline profile, so it can be @Shared, to optimize generated code:
                        @SuppressWarnings("unused") @Shared @Cached InlinedConditionProfile hasStart,
                        @Shared("getIter") @Cached PyObjectGetIter getIter,
                        // dummy raiseNode, so it can be @Shared, to optimize generated code:
                        @SuppressWarnings("unused") @Shared @Cached PRaiseNode.Lazy raiseNode) throws UnexpectedResultException {
            return sumDoubleInternal(frame, inliningTarget, arg1, start, addNode, getIter, errorProfile);
        }

        private double sumDoubleInternal(VirtualFrame frame, Node inliningTarget, Object arg1, double start, PyNumberAddNode add, PyObjectGetIter getIter,
                        IsBuiltinObjectProfile errorProfile) throws UnexpectedResultException {
            Object iterator = getIter.execute(frame, inliningTarget, arg1);
            double value = start;
            while (true) {
                double nextValue;
                try {
                    nextValue = PGuards.expectDouble(next.executeObject(frame, iterator));
                } catch (PException e) {
                    e.expectStopIteration(inliningTarget, errorProfile);
                    return value;
                } catch (UnexpectedResultException e) {
                    Object newValue = add.execute(frame, inliningTarget, value, e.getResult());
                    throw new UnexpectedResultException(iterateGeneric(frame, inliningTarget, iterator, newValue, add, errorProfile));
                }
                try {
                    value = add.executeDouble(frame, inliningTarget, value, nextValue);
                } catch (UnexpectedResultException e) {
                    throw new UnexpectedResultException(iterateGeneric(frame, inliningTarget, iterator, e.getResult(), add, errorProfile));
                }
            }
        }

        @Specialization(replaces = {"sumIntNone", "sumIntInt", "sumDoubleDouble"})
        Object sum(VirtualFrame frame, Object arg1, Object start,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PyNumberAddNode addNode,
                        @Shared @Cached IsBuiltinObjectProfile errorProfile,
                        @Shared("getIter") @Cached PyObjectGetIter getIter,
                        @Shared @Cached InlinedConditionProfile hasStart,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            if (PGuards.isString(start)) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.CANT_SUM_STRINGS);
            } else if (start instanceof PBytes) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.CANT_SUM_BYTES);
            } else if (start instanceof PByteArray) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.CANT_SUM_BYTEARRAY);
            }
            Object iterator = getIter.execute(frame, inliningTarget, arg1);
            return iterateGeneric(frame, inliningTarget, iterator, hasStart.profile(inliningTarget, start != NO_VALUE) ? start : 0, addNode, errorProfile);
        }

        private Object iterateGeneric(VirtualFrame frame, Node inliningTarget, Object iterator, Object start, PyNumberAddNode add, IsBuiltinObjectProfile errorProfile) {
            Object value = start;
            while (true) {
                Object nextValue;
                try {
                    nextValue = next.executeObject(frame, iterator);
                } catch (PException e) {
                    e.expectStopIteration(inliningTarget, errorProfile);
                    return value;
                }
                value = add.execute(frame, inliningTarget, value, nextValue);
            }
        }
    }

    @Builtin(name = "globals", needsFrame = true, alwaysNeedsCallerFrame = true)
    @GenerateNodeFactory
    abstract static class GlobalsNode extends PythonBuiltinNode {
        private final ConditionProfile condProfile = ConditionProfile.create();

        @Specialization
        public Object globals(VirtualFrame frame,
                        @Bind("this") Node inliningTarget,
                        @Cached PyEvalGetGlobals getGlobals,
                        @Cached GetOrCreateDictNode getDict) {
            Object globals = getGlobals.execute(frame, inliningTarget);
            if (condProfile.profile(globals instanceof PythonModule)) {
                return getDict.execute(inliningTarget, globals);
            } else {
                return globals;
            }
        }
    }

    @Builtin(name = "locals", needsFrame = true, alwaysNeedsCallerFrame = true)
    @GenerateNodeFactory
    abstract static class LocalsNode extends PythonBuiltinNode {

        @Specialization
        Object locals(VirtualFrame frame,
                        @Bind("this") Node inliningTarget,
                        @Cached PyEvalGetLocals getLocals) {
            return getLocals.execute(frame, inliningTarget);
        }
    }

    @Builtin(name = "vars", maxNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class VarsNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = "isNoValue(none)")
        static Object vars(VirtualFrame frame, @SuppressWarnings("unused") PNone none,
                        @Bind("this") Node inliningTarget,
                        @Cached PyEvalGetLocals getLocals) {
            return getLocals.execute(frame, inliningTarget);
        }

        @Specialization(guards = "!isNoValue(obj)")
        static Object vars(VirtualFrame frame, Object obj,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectLookupAttr lookupAttr,
                        @Cached PRaiseNode.Lazy raiseNode) {
            Object dict = lookupAttr.execute(frame, inliningTarget, obj, T___DICT__);
            if (dict == NO_VALUE) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.VARS_ARGUMENT_MUST_HAVE_DICT);
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
    @SuppressWarnings("truffle-inlining")       // footprint reduction 72 -> 53
    abstract static class UpdateBasesNode extends Node {

        abstract PTuple execute(PTuple bases, Object[] arguments, int nargs);

        @Specialization
        static PTuple update(PTuple bases, Object[] arguments, int nargs,
                        @Bind("this") Node inliningTarget,
                        @Cached PythonObjectFactory factory,
                        @Cached PyObjectLookupAttr getMroEntries,
                        @Cached CallUnaryMethodNode callMroEntries,
                        @Cached PRaiseNode.Lazy raiseNode) {
            CompilerAsserts.neverPartOfCompilation();
            ArrayList<Object> newBases = null;
            for (int i = 0; i < nargs; i++) {
                Object base = arguments[i];
                if (IsTypeNode.executeUncached(base)) {
                    if (newBases != null) {
                        // If we already have made a replacement, then we append every normal base,
                        // otherwise just skip it.
                        newBases.add(base);
                    }
                    continue;
                }

                Object meth = getMroEntries.execute(null, inliningTarget, base, T___MRO_ENTRIES__);
                if (isNoValue(meth)) {
                    if (newBases != null) {
                        newBases.add(base);
                    }
                    continue;
                }
                Object newBase = callMroEntries.executeObject(null, meth, bases);
                if (!PGuards.isPTuple(newBase)) {
                    throw raiseNode.get(inliningTarget).raise(PythonErrorType.TypeError, ErrorMessages.MRO_ENTRIES_MUST_RETURN_TUPLE);
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
                    newBases.add(SequenceStorageNodes.GetItemScalarNode.executeUncached(storage, j));
                }
            }
            if (newBases == null) {
                return bases;
            }
            return factory.createTuple(newBases.toArray());
        }
    }

    @SuppressWarnings("truffle-inlining")       // footprint reduction 36 -> 19
    abstract static class CalculateMetaclassNode extends Node {

        abstract Object execute(Object metatype, PTuple bases);

        /* Determine the most derived metatype. */
        @Specialization
        static Object calculate(Object metatype, PTuple bases,
                        @Bind("this") Node inliningTarget,
                        @Cached GetClassNode getClass,
                        @Cached IsSubtypeNode isSubType,
                        @Cached IsSubtypeNode isSubTypeReverse,
                        @Cached PRaiseNode.Lazy raiseNode) {
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
                Object tmp = SequenceStorageNodes.GetItemScalarNode.executeUncached(storage, i);
                Object tmpType = getClass.execute(inliningTarget, tmp);
                if (isSubType.execute(winner, tmpType)) {
                    // nothing to do
                } else if (isSubTypeReverse.execute(tmpType, winner)) {
                    winner = tmpType;
                } else {
                    throw raiseNode.get(inliningTarget).raise(PythonErrorType.TypeError, ErrorMessages.METACLASS_CONFLICT);
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
            Object buildFunction = PyObjectLookupAttr.executeUncached(module, T_BUILD_JAVA_CLASS);
            return CallNode.executeUncached(buildFunction, namespace, name, base);
        }

        @InliningCutoff
        private static Object buildJavaClass(VirtualFrame frame, PythonLanguage language, PFunction function, Object[] arguments, PythonObjectFactory factory, CallDispatchNode callBody,
                        TruffleString name) {
            PDict ns = factory.createDict(new DynamicObjectStorage(language));
            Object[] args = PArguments.create(0);
            PArguments.setSpecialArgument(args, ns);
            callBody.executeCall(frame, function, args);
            return buildJavaClass(ns, name, arguments[1]);
        }

        @Specialization
        protected Object doItNonFunction(VirtualFrame frame, Object function, Object[] arguments, PKeyword[] keywords,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringNode castToTruffleStringNode,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @Cached PythonObjectFactory factory,
                        @Cached CalculateMetaclassNode calculateMetaClass,
                        @Cached("create(T___PREPARE__)") GetAttributeNode getPrepare,
                        @Cached PyMappingCheckNode pyMappingCheckNode,
                        @Cached CallVarargsMethodNode callPrep,
                        @Cached CallVarargsMethodNode callType,
                        @Cached CallDispatchNode callBody,
                        @Cached UpdateBasesNode update,
                        @Cached PyObjectSetItem setOrigBases,
                        @Cached GetClassNode getClass,
                        @Cached IsBuiltinObjectProfile noAttributeProfile) {

            if (arguments.length < 1) {
                throw raise(PythonErrorType.TypeError, ErrorMessages.BUILD_CLS_NOT_ENOUGH_ARGS);
            }

            if (!PGuards.isFunction(function)) {
                throw raise(PythonErrorType.TypeError, ErrorMessages.BUILD_CLS_FUNC_MUST_BE_FUNC);
            }
            TruffleString name;
            try {
                name = castToTruffleStringNode.execute(inliningTarget, arguments[0]);
            } catch (CannotCastException e) {
                throw raise(PythonErrorType.TypeError, ErrorMessages.BUILD_CLS_NAME_NOT_STRING);
            }

            Object[] basesArray = Arrays.copyOfRange(arguments, 1, arguments.length);
            PTuple origBases = factory.createTuple(basesArray);

            PythonContext ctx = PythonContext.get(calculateMetaClass);
            Env env = ctx.getEnv();
            PythonLanguage language = PythonLanguage.get(inliningTarget);
            if (arguments.length == 2 && env.isHostObject(arguments[1]) && env.asHostObject(arguments[1]) instanceof Class<?>) {
                // we want to subclass a Java class
                return buildJavaClass(frame, language, (PFunction) function, arguments, factory, callBody, name);
            }

            class InitializeBuildClass {
                boolean isClass;
                Object meta;
                PKeyword[] mkw;
                PTuple bases;

                @TruffleBoundary
                InitializeBuildClass(PythonContext ctx) {

                    bases = update.execute(origBases, basesArray, basesArray.length);

                    mkw = keywords;
                    for (int i = 0; i < keywords.length; i++) {
                        if (T_METACLASS.equalsUncached(keywords[i].getName(), TS_ENCODING)) {
                            meta = keywords[i].getValue();
                            mkw = PKeyword.create(keywords.length - 1);

                            PythonUtils.arraycopy(keywords, 0, mkw, 0, i);
                            PythonUtils.arraycopy(keywords, i + 1, mkw, i, mkw.length - i);

                            // metaclass is explicitly given, check if it's indeed a class
                            isClass = IsTypeNode.executeUncached(meta);
                            break;
                        }
                    }
                    if (meta == null) {
                        // if there are no bases, use type:
                        if (bases.getSequenceStorage().length() == 0) {
                            meta = ctx.lookupType(PythonBuiltinClassType.PythonClass);
                        } else {
                            // else get the type of the first base
                            meta = getClass.execute(inliningTarget, SequenceStorageNodes.GetItemScalarNode.executeUncached(bases.getSequenceStorage(), 0));
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
            Object savedState = IndirectCallContext.enter(frame, indirectCallData);
            InitializeBuildClass init;
            try {
                init = new InitializeBuildClass(ctx);
            } finally {
                IndirectCallContext.exit(frame, indirectCallData, savedState);
            }

            Object ns;
            try {
                Object prep = getPrepare.executeObject(frame, init.meta);
                ns = callPrep.execute(frame, prep, new Object[]{name, init.bases}, init.mkw);
            } catch (PException p) {
                p.expectAttributeError(inliningTarget, noAttributeProfile);
                ns = factory.createDict(new DynamicObjectStorage(language));
            }
            if (!pyMappingCheckNode.execute(inliningTarget, ns)) {
                throw raiseNoMapping(init.isClass, init.meta, ns);
            }
            Object[] bodyArguments = PArguments.create(0);
            PArguments.setSpecialArgument(bodyArguments, ns);
            callBody.executeCall(frame, (PFunction) function, bodyArguments);
            if (init.bases != origBases) {
                setOrigBases.execute(frame, inliningTarget, ns, SpecialAttributeNames.T___ORIG_BASES__, origBases);
            }
            Object cls = callType.execute(frame, init.meta, new Object[]{name, init.bases, ns}, init.mkw);

            /*
             * We could check here and throw "__class__ not set defining..." errors.
             */

            return cls;
        }

        @InliningCutoff
        private PException raiseNoMapping(boolean isClass, Object meta, Object ns) {
            if (isClass) {
                throw raise(PythonErrorType.TypeError, ErrorMessages.N_PREPARE_MUST_RETURN_MAPPING, meta, ns);
            } else {
                throw raise(PythonErrorType.TypeError, ErrorMessages.MTCLS_PREPARE_MUST_RETURN_MAPPING, ns);
            }
        }
    }

    @Builtin(name = "anext", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ANext extends PythonUnaryBuiltinNode {
        @Specialization
        public Object doGeneric(VirtualFrame frame, Object asyncIter,
                        @Bind("this") Node inliningTarget,
                        @Cached(parameters = "ANext") LookupSpecialMethodSlotNode getANext,
                        @Cached GetClassNode getAsyncIterType,
                        @Cached PRaiseNode.Lazy raiseNoANext,
                        @Cached CallUnaryMethodNode callANext,
                        @Cached TypeNodes.GetNameNode getName) {
            // TODO: two argument anext
            Object type = getAsyncIterType.execute(inliningTarget, asyncIter);
            Object getter = getANext.execute(frame, type, asyncIter);
            if (getter == NO_VALUE) {
                throw raiseNoANext.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.OBJECT_NOT_ASYNCGEN, getName.execute(inliningTarget, type));
            }
            return callANext.executeObject(frame, getter, asyncIter);
        }
    }

    @Builtin(name = "aiter", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class AIter extends PythonUnaryBuiltinNode {
        @Specialization
        public Object doGeneric(VirtualFrame frame, Object arg,
                        @Cached(neverDefault = true) GetAIterNode aiter) {
            return aiter.execute(frame, arg);
        }
    }
}
