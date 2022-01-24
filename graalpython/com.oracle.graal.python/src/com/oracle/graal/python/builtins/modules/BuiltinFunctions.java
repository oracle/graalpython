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
import static com.oracle.graal.python.builtins.objects.PNone.NO_VALUE;
import static com.oracle.graal.python.builtins.objects.PNotImplemented.NOT_IMPLEMENTED;
import static com.oracle.graal.python.nodes.BuiltinNames.ABS;
import static com.oracle.graal.python.nodes.BuiltinNames.ALL;
import static com.oracle.graal.python.nodes.BuiltinNames.ANY;
import static com.oracle.graal.python.nodes.BuiltinNames.ASCII;
import static com.oracle.graal.python.nodes.BuiltinNames.BIN;
import static com.oracle.graal.python.nodes.BuiltinNames.BREAKPOINT;
import static com.oracle.graal.python.nodes.BuiltinNames.BREAKPOINTHOOK;
import static com.oracle.graal.python.nodes.BuiltinNames.CALLABLE;
import static com.oracle.graal.python.nodes.BuiltinNames.CHR;
import static com.oracle.graal.python.nodes.BuiltinNames.COMPILE;
import static com.oracle.graal.python.nodes.BuiltinNames.DELATTR;
import static com.oracle.graal.python.nodes.BuiltinNames.DIR;
import static com.oracle.graal.python.nodes.BuiltinNames.DIVMOD;
import static com.oracle.graal.python.nodes.BuiltinNames.EVAL;
import static com.oracle.graal.python.nodes.BuiltinNames.EXEC;
import static com.oracle.graal.python.nodes.BuiltinNames.FORMAT;
import static com.oracle.graal.python.nodes.BuiltinNames.GETATTR;
import static com.oracle.graal.python.nodes.BuiltinNames.HASH;
import static com.oracle.graal.python.nodes.BuiltinNames.HEX;
import static com.oracle.graal.python.nodes.BuiltinNames.ID;
import static com.oracle.graal.python.nodes.BuiltinNames.ISINSTANCE;
import static com.oracle.graal.python.nodes.BuiltinNames.ISSUBCLASS;
import static com.oracle.graal.python.nodes.BuiltinNames.ITER;
import static com.oracle.graal.python.nodes.BuiltinNames.LEN;
import static com.oracle.graal.python.nodes.BuiltinNames.MAX;
import static com.oracle.graal.python.nodes.BuiltinNames.MIN;
import static com.oracle.graal.python.nodes.BuiltinNames.NEXT;
import static com.oracle.graal.python.nodes.BuiltinNames.OCT;
import static com.oracle.graal.python.nodes.BuiltinNames.ORD;
import static com.oracle.graal.python.nodes.BuiltinNames.POW;
import static com.oracle.graal.python.nodes.BuiltinNames.PRINT;
import static com.oracle.graal.python.nodes.BuiltinNames.REPR;
import static com.oracle.graal.python.nodes.BuiltinNames.ROUND;
import static com.oracle.graal.python.nodes.BuiltinNames.SETATTR;
import static com.oracle.graal.python.nodes.BuiltinNames.SORTED;
import static com.oracle.graal.python.nodes.BuiltinNames.SUM;
import static com.oracle.graal.python.nodes.BuiltinNames.__BUILTINS__;
import static com.oracle.graal.python.nodes.BuiltinNames.__DEBUG__;
import static com.oracle.graal.python.nodes.BuiltinNames.__GRAALPYTHON__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__FORMAT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEXT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ROUND__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.Arrays;
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
import com.oracle.graal.python.lib.PyCallableCheckNode;
import com.oracle.graal.python.lib.PyEvalGetGlobals;
import com.oracle.graal.python.lib.PyMappingCheckNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyNumberIndexNode;
import com.oracle.graal.python.lib.PyObjectAsciiNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectReprAsObjectNode;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.lib.PyObjectStrAsJavaStringNode;
import com.oracle.graal.python.lib.PyObjectStrAsObjectNode;
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
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.LookupCallableSlotInMRONode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.SetAttributeNode;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.builtins.ListNodes.ConstructListNode;
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
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
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
import com.oracle.truffle.api.Assumption;
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
import com.oracle.truffle.api.utilities.TriState;

@CoreFunctions(defineModule = BuiltinNames.BUILTINS, isEager = true)
public final class BuiltinFunctions extends PythonBuiltins {

    @Override
    protected List<com.oracle.truffle.api.dsl.NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return BuiltinFunctionsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        builtinConstants.put(__GRAALPYTHON__, core.lookupBuiltinModule(__GRAALPYTHON__));
        super.initialize(core);
    }

    @Override
    public void postInitialize(Python3Core core) {
        super.postInitialize(core);
        PythonModule builtinsModule = core.lookupBuiltinModule(BuiltinNames.BUILTINS);
        builtinsModule.setAttribute(__DEBUG__, !core.getContext().getOption(PythonOptions.PythonOptimizeFlag));
    }

    // abs(x)
    @Builtin(name = ABS, minNumOfPositionalArgs = 1)
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
                        @Cached("create(__ABS__)") LookupAndCallUnaryNode callAbs) {
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

    @Builtin(name = ALL, minNumOfPositionalArgs = 1)
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

    @Builtin(name = ANY, minNumOfPositionalArgs = 1)
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
    @Builtin(name = BIN, minNumOfPositionalArgs = 1)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class BinNode extends PythonUnaryBuiltinNode {
        @TruffleBoundary
        protected String buildString(boolean isNegative, String number) {
            StringBuilder sb = new StringBuilder();
            if (isNegative) {
                sb.append('-');
            }
            sb.append(prefix());
            sb.append(number);
            return sb.toString();
        }

        protected String prefix() {
            return "0b";
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
        String doL(long x,
                        @Cached ConditionProfile isMinLong) {
            if (isMinLong.profile(x == Long.MIN_VALUE)) {
                return buildString(true, bigToString(bigAbs(PInt.longToBigInteger(x))));
            }
            return buildString(x < 0, longToString(Math.abs(x)));
        }

        @Specialization
        String doD(double x,
                        @Cached PRaiseNode raise) {
            throw raise.raiseIntegerInterpretationError(x);
        }

        @Specialization
        @TruffleBoundary
        String doPI(PInt x) {
            BigInteger value = x.getValue();
            return buildString(value.compareTo(BigInteger.ZERO) < 0, bigToString(value.abs()));
        }

        @Specialization(replaces = {"doL", "doD", "doPI"})
        String doO(VirtualFrame frame, Object x,
                        @Cached ConditionProfile isMinLong,
                        @Cached PyNumberIndexNode indexNode,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached BranchProfile isInt,
                        @Cached BranchProfile isLong,
                        @Cached BranchProfile isPInt) {
            Object index = indexNode.execute(frame, x);
            if (index instanceof Boolean || index instanceof Integer) {
                isInt.enter();
                return doL(asSizeNode.executeExact(frame, index), isMinLong);
            } else if (index instanceof Long) {
                isLong.enter();
                return doL((long) index, isMinLong);
            } else if (index instanceof PInt) {
                isPInt.enter();
                return doPI((PInt) index);
            } else {
                CompilerDirectives.transferToInterpreter();
                throw raise(PythonBuiltinClassType.NotImplementedError, "bin/oct/hex with native integer subclasses");
            }
        }
    }

    // oct(object)
    @Builtin(name = OCT, minNumOfPositionalArgs = 1)
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
        protected String prefix() {
            return "0o";
        }

        public static OctNode create() {
            return OctNodeFactory.create();
        }
    }

    // hex(object)
    @Builtin(name = HEX, minNumOfPositionalArgs = 1)
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
        protected String prefix() {
            return "0x";
        }

        public static HexNode create() {
            return HexNodeFactory.create();
        }
    }

    // callable(object)
    @Builtin(name = CALLABLE, minNumOfPositionalArgs = 1)
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

            if (GraalPythonTranslationErrorNode.MESSAGE.equals(object)) {
                return true;
            }

            return callableCheck.execute(object);
        }
    }

    // chr(i)
    @Builtin(name = CHR, minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"i"})
    @GenerateNodeFactory
    @ArgumentClinic(name = "i", conversion = ArgumentClinic.ClinicConversion.Int)
    public abstract static class ChrNode extends PythonUnaryClinicBuiltinNode {
        @Specialization
        public String charFromInt(int arg) {
            if (arg >= 0 && arg <= 1114111) {
                return doChr(arg);
            } else {
                throw raise(ValueError, ErrorMessages.ARG_NOT_IN_RANGE, "chr()", "0x110000");
            }
        }

        @TruffleBoundary
        private static String doChr(int arg) {
            return new String(Character.toChars(arg));
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BuiltinFunctionsClinicProviders.ChrNodeClinicProviderGen.INSTANCE;
        }
    }

    // hash(object)
    @Builtin(name = HASH, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class HashNode extends PythonUnaryBuiltinNode {
        @Specialization
        long hash(VirtualFrame frame, Object object,
                        @Cached PyObjectHashNode hashNode) {
            return hashNode.execute(frame, object);
        }
    }

    // dir([object])
    @Builtin(name = DIR, minNumOfPositionalArgs = 0, maxNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class DirNode extends PythonBuiltinNode {

        // logic like in 'Objects/object.c: _dir_locals'
        @Specialization(guards = "isNoValue(object)")
        Object locals(VirtualFrame frame, @SuppressWarnings("unused") Object object,
                        @Cached ReadLocalsNode readLocalsNode,
                        @Cached ReadCallerFrameNode readCallerFrameNode,
                        @Cached MaterializeFrameNode materializeNode,
                        @Cached ConditionProfile inGenerator,
                        @Cached("create(KEYS)") LookupAndCallUnaryNode callKeysNode,
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
                        @Cached ListBuiltins.ListSortNode sortNode,
                        @Cached ListNodes.ConstructListNode constructListNode,
                        @Cached("create(__DIR__)") LookupAndCallUnaryNode callDir) {
            Object result = callDir.executeObject(frame, object);
            if (result == NO_VALUE) {
                throw raise(TypeError, "object does not provide __dir__");
            }
            PList list = constructListNode.execute(frame, result);
            sortNode.execute(frame, list);
            return list;
        }
    }

    // divmod(a, b)
    @Builtin(name = DIVMOD, minNumOfPositionalArgs = 2)
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
    @Builtin(name = EVAL, minNumOfPositionalArgs = 1, parameterNames = {"expression", "globals", "locals"})
    @GenerateNodeFactory
    public abstract static class EvalNode extends PythonBuiltinNode {
        protected static final String funcname = "eval";
        private final BranchProfile hasFreeVarsBranch = BranchProfile.create();
        @Child protected CompileNode compileNode;
        @Child private GenericInvokeNode invokeNode = GenericInvokeNode.create();
        @Child private PyMappingCheckNode mappingCheckNode;
        @Child private GetOrCreateDictNode getOrCreateDictNode;

        protected void assertNoFreeVars(PCode code) {
            Object[] freeVars = code.getFreeVars();
            if (freeVars.length > 0) {
                hasFreeVarsBranch.enter();
                throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.CODE_OBJ_NO_FREE_VARIABLES, getMode());
            }
        }

        protected String getMode() {
            return "eval";
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
            PCode code = getCompileNode().execute(frame, source, "<string>", getMode(), 0, false, -1);
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
                setBuiltins.execute(frame, globals, __BUILTINS__, builtinsDict);
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
            // here, we don't need to set any locals, since the {Write,Read,Delete}NameNodes will
            // fall back (like their CPython counterparts) to writing to the globals. We only need
            // to ensure that the `locals()` call still gives us the globals dict
            PArguments.setCustomLocals(args, globals);
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
            throw raise(TypeError, ErrorMessages.GLOBALS_MUST_BE_DICT, funcname, globals);
        }

        @Specialization(guards = {"isAnyNone(globals) || isDict(globals)", "!isAnyNone(locals)", "!isMapping(locals)"})
        PNone badLocals(@SuppressWarnings("unused") Object source, @SuppressWarnings("unused") PDict globals, Object locals) {
            throw raise(TypeError, ErrorMessages.LOCALS_MUST_BE_MAPPING, funcname, locals);
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

    @Builtin(name = EXEC, minNumOfPositionalArgs = 1, parameterNames = {"source", "globals", "locals"})
    @GenerateNodeFactory
    abstract static class ExecNode extends EvalNode {
        protected abstract Object executeInternal(VirtualFrame frame);

        @Override
        protected String getMode() {
            return "exec";
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
    @Builtin(name = COMPILE, minNumOfPositionalArgs = 3, parameterNames = {"source", "filename", "mode", "flags", "dont_inherit", "optimize", "_feature_version"})
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
        private static final int PyCF_ONLY_AST = 0x0400;
        private static final int PyCF_TYPE_COMMENTS = 0x1000;

        /**
         * Decides wether this node should attempt to map the filename to a URI for the benefit of
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

        public abstract PCode execute(VirtualFrame frame, Object source, String filename, String mode, Object kwFlags, Object kwDontInherit, Object kwOptimize);

        @SuppressWarnings("unused")
        @Specialization
        @TruffleBoundary
        PCode compile(String expression, String filename, String mode, int kwFlags, Object kwDontInherit, int kwOptimize) {
            checkFlags(kwFlags);
            checkOptimize(kwOptimize, kwOptimize);
            checkSource(expression);

            String code = expression;
            PythonContext context = getContext();
            ParserMode pm;
            if (mode.equals("exec")) {
                pm = ParserMode.File;
                // CPython adds a newline and we need to do the same in order to produce
                // SyntaxError with the same offset when the line is incomplete
                if (!code.endsWith("\n")) {
                    code += '\n';
                }
            } else if (mode.equals("eval")) {
                pm = ParserMode.Eval;
            } else if (mode.equals("single")) {
                pm = ParserMode.Statement;
            } else {
                throw raise(ValueError, ErrorMessages.COMPILE_MUST_BE);
            }
            if (lstrip) {
                code = code.replaceFirst("^[ \t]", "");
            }
            CallTarget ct;
            String finalCode = code;
            Supplier<CallTarget> createCode = () -> {
                if (pm == ParserMode.File) {
                    Source source = PythonLanguage.newSource(context, finalCode, filename, mayBeFromFile, PythonLanguage.getCompileMimeType(kwOptimize));
                    return getContext().getEnv().parsePublic(source);
                } else if (pm == ParserMode.Eval) {
                    Source source = PythonLanguage.newSource(context, finalCode, filename, mayBeFromFile, PythonLanguage.getEvalMimeType(kwOptimize));
                    return getContext().getEnv().parsePublic(source);
                } else {
                    Source source = PythonLanguage.newSource(context, finalCode, filename, mayBeFromFile, PythonLanguage.MIME_TYPE);
                    return PythonUtils.getOrCreateCallTarget((RootNode) getCore().getParser().parse(pm, kwOptimize, getCore(), source, null, null));
                }
            };
            if (getCore().isCoreInitialized()) {
                ct = createCode.get();
            } else {
                ct = getCore().getLanguage().cacheCode(filename, createCode);
            }
            RootCallTarget rootCallTarget = (RootCallTarget) ct;
            if (rootCallTarget.getRootNode() instanceof PRootNode) {
                ((PRootNode) rootCallTarget.getRootNode()).triggerDeprecationWarnings();
            }
            return factory().createCode(rootCallTarget);
        }

        @Specialization(limit = "3")
        PCode generic(VirtualFrame frame, Object wSource, Object wFilename, Object wMode, Object kwFlags, Object kwDontInherit, Object kwOptimize,
                        @CachedLibrary(limit = "3") PythonBufferAcquireLibrary acquireLib,
                        @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Cached CastToJavaStringNode castStr,
                        @Cached CastToJavaIntExactNode castInt,
                        @Cached CodecsModuleBuiltins.HandleDecodingErrorNode handleDecodingErrorNode,
                        @Cached PyObjectStrAsJavaStringNode asStrNode,
                        @CachedLibrary("wSource") InteropLibrary interopLib,
                        @Cached PyUnicodeFSDecoderNode asPath,
                        @Cached WarnNode warnNode) {
            if (wSource instanceof PCode) {
                return (PCode) wSource;
            }
            String filename;
            // TODO use PyUnicode_FSDecode
            if (acquireLib.hasBuffer(wFilename)) {
                Object filenameBuffer = acquireLib.acquireReadonly(wFilename, frame, this);
                try {
                    filename = PythonUtils.newString(bufferLib.getCopiedByteArray(filenameBuffer));
                    if (!(wFilename instanceof PBytes)) {
                        warnNode.warnFormat(frame, null, DeprecationWarning, 1, ErrorMessages.PATH_SHOULD_BE_STR_BYTES_PATHLIKE_NOT_P, wFilename);
                    }
                } finally {
                    bufferLib.release(filenameBuffer, frame, this);
                }
            } else {
                filename = asPath.execute(frame, wFilename);
            }
            String mode;
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
            String source = sourceAsString(frame, wSource, filename, interopLib, acquireLib, bufferLib, handleDecodingErrorNode, asStrNode);
            checkSource(source);
            return compile(source, filename, mode, flags, kwDontInherit, optimize);
        }

        private void checkSource(String source) throws PException {
            if (source.indexOf(0) > -1) {
                throw raise(ValueError, ErrorMessages.SRC_CODE_CANNOT_CONTAIN_NULL_BYTES);
            }
        }

        private void checkOptimize(int optimize, Object kwOptimize) throws PException {
            if (optimize < -1 || optimize > 2) {
                throw raise(TypeError, ErrorMessages.INVALID_OPTIMIZE_VALUE, kwOptimize);
            }
        }

        private void checkFlags(int flags) {
            if ((flags & ~(PyCF_MASK | PyCF_MASK_OBSOLETE | PyCF_DONT_IMPLY_DEDENT | PyCF_ONLY_AST | PyCF_TYPE_COMMENTS)) > 0) {
                throw raise(ValueError, ErrorMessages.UNRECOGNIZED_FLAGS);
            }
        }

        // modeled after _Py_SourceAsString
        String sourceAsString(VirtualFrame frame, Object source, String filename, InteropLibrary interopLib, PythonBufferAcquireLibrary acquireLib, PythonBufferAccessLibrary bufferLib,
                        CodecsModuleBuiltins.HandleDecodingErrorNode handleDecodingErrorNode, PyObjectStrAsJavaStringNode asStrNode) {
            if (interopLib.isString(source)) {
                try {
                    return interopLib.asString(source);
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
                    String pythonEncodingNameFromJavaName = CharsetMapping.getPythonEncodingNameFromJavaName(charset.name());
                    CodecsModuleBuiltins.TruffleDecoder decoder = new CodecsModuleBuiltins.TruffleDecoder(pythonEncodingNameFromJavaName, charset, bytes, bytesLen, CodingErrorAction.REPORT);
                    if (!decoder.decodingStep(true)) {
                        try {
                            handleDecodingErrorNode.execute(decoder, "strict", source);
                            throw CompilerDirectives.shouldNotReachHere();
                        } catch (PException e) {
                            throw raiseInvalidSyntax(filename, "(unicode error) %s", asStrNode.execute(frame, e.getEscapedException()));
                        }
                    }
                    return decoder.getString();
                } catch (PythonFileDetector.InvalidEncodingException e) {
                    throw raiseInvalidSyntax(filename, "encoding problem: %s", e.getEncodingName());
                } finally {
                    bufferLib.release(buffer, frame, this);
                }
            }
        }

        @TruffleBoundary
        private RuntimeException raiseInvalidSyntax(String filename, String format, Object... args) {
            PythonContext context = getContext();
            // Create non-empty source to avoid overwriting the message with "unexpected EOF"
            Source source = PythonLanguage.newSource(context, " ", filename, mayBeFromFile, null);
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
    @Builtin(name = DELATTR, minNumOfPositionalArgs = 2)
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
    @Builtin(name = GETATTR, minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class GetAttrNode extends PythonTernaryBuiltinNode {
        public static GetAttrNode create() {
            return GetAttrNodeFactory.create();
        }

        public abstract Object executeWithArgs(VirtualFrame frame, Object primary, String name, Object defaultValue);

        @SuppressWarnings("unused")
        @Specialization(limit = "getAttributeAccessInlineCacheMaxDepth()", guards = {"stringEquals(cachedName, name, stringProfile)", "isNoValue(defaultValue)"})
        public Object getAttrDefault(VirtualFrame frame, Object primary, String name, PNone defaultValue,
                        @Cached ConditionProfile stringProfile,
                        @Cached("name") String cachedName,
                        @Cached("create(name)") GetFixedAttributeNode getAttributeNode) {
            return getAttributeNode.executeObject(frame, primary);
        }

        @SuppressWarnings("unused")
        @Specialization(limit = "getAttributeAccessInlineCacheMaxDepth()", guards = {"stringEquals(cachedName, name, stringProfile)", "!isNoValue(defaultValue)"})
        Object getAttr(VirtualFrame frame, Object primary, String name, Object defaultValue,
                        @Cached ConditionProfile stringProfile,
                        @Cached("name") String cachedName,
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
        Object getAttrFromObject(VirtualFrame frame, Object primary, String name, @SuppressWarnings("unused") PNone defaultValue,
                        @Cached GetAnyAttributeNode getAttributeNode) {
            return getAttributeNode.executeObject(frame, primary, name);
        }

        @Specialization(replaces = {"getAttr", "getAttrDefault"}, guards = "!isNoValue(defaultValue)")
        Object getAttrFromObject(VirtualFrame frame, Object primary, String name, Object defaultValue,
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
            return executeWithArgs(frame, object, name.getValue(), defaultValue);
        }

        @Specialization(guards = "!isString(name)")
        @SuppressWarnings("unused")
        Object getAttrGeneric(Object primary, Object name, Object defaultValue) {
            throw raise(TypeError, ErrorMessages.GETATTR_ATTRIBUTE_NAME_MUST_BE_STRING);
        }
    }

    // id(object)
    @Builtin(name = ID, minNumOfPositionalArgs = 1)
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
    @Builtin(name = ISINSTANCE, minNumOfPositionalArgs = 2)
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
    @Builtin(name = ISSUBCLASS, minNumOfPositionalArgs = 2)
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
    @Builtin(name = ITER, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
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
    @Builtin(name = LEN, minNumOfPositionalArgs = 1)
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
                throw raise(PythonBuiltinClassType.TypeError, "Cannot specify a default for %s with multiple positional arguments", getName());
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
    @Builtin(name = MAX, minNumOfPositionalArgs = 1, takesVarArgs = true, keywordOnlyNames = {"key", "default"}, doc = "max(iterable, *[, default=obj, key=func]) -> value\n" +
                    "max(arg1, arg2, *args, *[, key=func]) -> value\n\n" + "With a single iterable argument, return its biggest item. The\n" +
                    "default keyword-only argument specifies an object to return if\n" + "the provided iterable is empty.\n" + "With two or more arguments, return the largest argument.")
    @GenerateNodeFactory
    public abstract static class MaxNode extends MinMaxNode {

    }

    // min(iterable, *[, key])
    // min(arg1, arg2, *args[, key])
    @Builtin(name = MIN, minNumOfPositionalArgs = 1, takesVarArgs = true, keywordOnlyNames = {"key", "default"})
    @GenerateNodeFactory
    public abstract static class MinNode extends MinMaxNode {

    }

    // next(iterator[, default])
    @SuppressWarnings("unused")
    @Builtin(name = NEXT, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
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
            return LookupAndCallUnaryNode.create(__NEXT__, () -> new LookupAndCallUnaryNode.NoAttributeHandler() {
                @Override
                public Object execute(Object iterator) {
                    throw raise(TypeError, ErrorMessages.OBJ_ISNT_ITERATOR, iterator);
                }
            });
        }
    }

    // ord(c)
    @Builtin(name = ORD, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @ImportStatic(PGuards.class)
    public abstract static class OrdNode extends PythonBuiltinNode {

        @Specialization
        @TruffleBoundary
        public int ord(String chr) {
            if (chr.codePointCount(0, chr.length()) != 1) {
                throw raise(TypeError, ErrorMessages.EXPECTED_CHARACTER_BUT_STRING_FOUND, "ord()", chr.length());
            }
            return chr.codePointAt(0);
        }

        @Specialization
        public int ord(PString pchr,
                        @Cached CastToJavaStringNode castToJavaStringNode) {
            String chr;
            try {
                chr = castToJavaStringNode.execute(pchr);
            } catch (CannotCastException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
            return ord(chr);
        }

        @Specialization
        public long ord(VirtualFrame frame, PBytesLike chr,
                        @Cached CastToJavaLongExactNode castNode,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached SequenceStorageNodes.GetItemNode getItemNode) {
            int len = lenNode.execute(chr.getSequenceStorage());
            if (len != 1) {
                throw raise(TypeError, ErrorMessages.EXPECTED_CHARACTER_BUT_STRING_FOUND, "ord()", len);
            }
            return castNode.execute(getItemNode.execute(frame, chr.getSequenceStorage(), 0));
        }

        @Specialization(guards = {"!isString(obj)", "!isBytes(obj)"})
        public Object ord(@SuppressWarnings("unused") Object obj) {
            throw raise(TypeError, ErrorMessages.S_EXPECTED_STRING_OF_LEN_BUT_P, "ord()", "1", "obj");
        }
    }

    // print(*objects, sep=' ', end='\n', file=sys.stdout, flush=False)
    @Builtin(name = PRINT, takesVarArgs = true, keywordOnlyNames = {"sep", "end", "file", "flush"}, doc = "\n" +
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
        private static final String DEFAULT_END = "\n";
        private static final String DEFAULT_SEPARATOR = " ";
        @Child private ReadAttributeFromObjectNode readStdout;
        @CompilationFinal private Assumption singleContextAssumption;
        @CompilationFinal private PythonModule cachedSys;

        @Specialization
        @SuppressWarnings("unused")
        PNone printNoKeywords(VirtualFrame frame, Object[] values, PNone sep, PNone end, PNone file, PNone flush,
                        @Shared("getWriteMethod") @Cached PyObjectGetAttr getWriteMethod,
                        @Shared("callWrite") @Cached CallNode callWrite,
                        @Shared("callFlush") @Cached PyObjectCallMethodObjArgs callFlush,
                        @Shared("strNode") @Cached PyObjectStrAsObjectNode strNode) {
            Object stdout = getStdout();
            return printAllGiven(frame, values, DEFAULT_SEPARATOR, DEFAULT_END, stdout, false, getWriteMethod, callWrite, callFlush, strNode);
        }

        @Specialization(guards = {"!isNone(file)", "!isNoValue(file)"})
        static PNone printAllGiven(VirtualFrame frame, Object[] values, String sep, String end, Object file, boolean flush,
                        @Shared("getWriteMethod") @Cached PyObjectGetAttr getWriteMethod,
                        @Shared("callWrite") @Cached CallNode callWrite,
                        @Shared("callFlush") @Cached PyObjectCallMethodObjArgs callFlush,
                        @Shared("strNode") @Cached PyObjectStrAsObjectNode strNode) {
            int lastValue = values.length - 1;
            // Note: the separate lookup is necessary due to different __getattr__ treatment than
            // method lookup
            Object writeMethod = getWriteMethod.execute(frame, file, "write");
            for (int i = 0; i < lastValue; i++) {
                callWrite.execute(frame, writeMethod, strNode.execute(frame, values[i]));
                callWrite.execute(frame, writeMethod, sep);
            }
            if (lastValue >= 0) {
                callWrite.execute(frame, writeMethod, strNode.execute(frame, values[lastValue]));
            }
            callWrite.execute(frame, writeMethod, end);
            if (flush) {
                callFlush.execute(frame, file, "flush");
            }
            return PNone.NONE;
        }

        @Specialization(replaces = {"printAllGiven", "printNoKeywords"})
        PNone printGeneric(VirtualFrame frame, Object[] values, Object sepIn, Object endIn, Object fileIn, Object flushIn,
                        @Cached CastToJavaStringNode castSep,
                        @Cached CastToJavaStringNode castEnd,
                        @Cached("createIfTrueNode()") CoerceToBooleanNode castFlush,
                        @Cached PRaiseNode raiseNode,
                        @Shared("getWriteMethod") @Cached PyObjectGetAttr getWriteMethod,
                        @Shared("callWrite") @Cached CallNode callWrite,
                        @Shared("callFlush") @Cached PyObjectCallMethodObjArgs callFlush,
                        @Shared("strNode") @Cached PyObjectStrAsObjectNode strNode) {
            String sep;
            try {
                sep = sepIn instanceof PNone ? DEFAULT_SEPARATOR : castSep.execute(sepIn);
            } catch (CannotCastException e) {
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, ErrorMessages.SEP_MUST_BE_NONE_OR_STRING, sepIn);
            }

            String end;
            try {
                end = endIn instanceof PNone ? DEFAULT_END : castEnd.execute(endIn);
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
            if (singleContextAssumption == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                singleContextAssumption = singleContextAssumption();
            }
            PythonModule sys;
            if (singleContextAssumption.isValid()) {
                if (cachedSys == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    cachedSys = getContext().lookupBuiltinModule("sys");
                }
                sys = cachedSys;
            } else {
                if (cachedSys != null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    cachedSys = null;
                }
                sys = getContext().lookupBuiltinModule("sys");
            }
            if (readStdout == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readStdout = insert(ReadAttributeFromObjectNode.create());
            }
            Object stdout = readStdout.execute(sys, "stdout");
            if (stdout instanceof PNone) {
                throw raise(RuntimeError, ErrorMessages.LOST_SYSSTDOUT);
            }
            return stdout;
        }
    }

    // repr(object)
    @Builtin(name = REPR, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReprNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object repr(VirtualFrame frame, Object obj,
                        @Cached PyObjectReprAsObjectNode reprNode) {
            return reprNode.execute(frame, obj);
        }
    }

    // format(object, [format_spec])
    @Builtin(name = FORMAT, minNumOfPositionalArgs = 1, parameterNames = {"object", "format_spec"})
    @GenerateNodeFactory
    @ImportStatic(PGuards.class)
    public abstract static class FormatNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "isNoValue(formatSpec)")
        Object format(VirtualFrame frame, Object obj, @SuppressWarnings("unused") PNone formatSpec,
                        @Shared("callFormat") @Cached("create(Format)") LookupAndCallBinaryNode callFormat) {
            return format(frame, obj, "", callFormat);
        }

        @Specialization(guards = "!isNoValue(formatSpec)")
        Object format(VirtualFrame frame, Object obj, Object formatSpec,
                        @Shared("callFormat") @Cached("create(Format)") LookupAndCallBinaryNode callFormat) {
            Object res = callFormat.executeObject(frame, obj, formatSpec);
            if (res == NO_VALUE) {
                throw raise(TypeError, ErrorMessages.TYPE_DOESNT_DEFINE_FORMAT, obj);
            }
            if (!PGuards.isString(res)) {
                throw raise(TypeError, ErrorMessages.S_MUST_RETURN_S_NOT_P, __FORMAT__, "str", res);
            }
            return res;
        }
    }

    // ascii(object)
    @Builtin(name = ASCII, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class AsciiNode extends PythonUnaryBuiltinNode {

        @Specialization
        public static String ascii(VirtualFrame frame, Object obj,
                        @Cached PyObjectAsciiNode asciiNode) {
            return asciiNode.execute(frame, obj);
        }
    }

    // round(number[, ndigits])
    @Builtin(name = ROUND, minNumOfPositionalArgs = 1, parameterNames = {"number", "ndigits"})
    @GenerateNodeFactory
    public abstract static class RoundNode extends PythonBuiltinNode {
        @Specialization
        Object round(VirtualFrame frame, Object x, @SuppressWarnings("unused") PNone n,
                        @Cached("create(Round)") LookupAndCallUnaryNode callRound) {
            Object result = callRound.executeObject(frame, x);
            if (result == PNone.NO_VALUE) {
                throw raise(TypeError, ErrorMessages.TYPE_DOESNT_DEFINE_METHOD, x, __ROUND__);
            }
            return result;
        }

        @Specialization(guards = "!isPNone(n)")
        Object round(VirtualFrame frame, Object x, Object n,
                        @Cached("create(Round)") LookupAndCallBinaryNode callRound) {
            Object result = callRound.executeObject(frame, x, n);
            if (result == NOT_IMPLEMENTED) {
                throw raise(TypeError, ErrorMessages.TYPE_DOESNT_DEFINE_METHOD, x, __ROUND__);
            }
            return result;
        }
    }

    // setattr(object, name, value)
    @Builtin(name = SETATTR, minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class SetAttrNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object setAttr(VirtualFrame frame, Object object, Object key, Object value,
                        @Cached SetAttributeNode.Dynamic setAttrNode) {
            setAttrNode.execute(frame, object, key, value);
            return PNone.NONE;
        }
    }

    // sorted(iterable, key, reverse)
    @Builtin(name = SORTED, minNumOfPositionalArgs = 1, parameterNames = {"$self"}, keywordOnlyNames = {"key", "reverse"})
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

    @Builtin(name = BREAKPOINT, takesVarArgs = true, takesVarKeywordArgs = true)
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
                Object sysModule = hlib.getItem(sysModules.getDictStorage(), "sys");
                Object breakpointhook = getBreakpointhookNode.execute(sysModule, BREAKPOINTHOOK);
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

    @Builtin(name = POW, minNumOfPositionalArgs = 2, parameterNames = {"base", "exp", "mod"})
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
    @Builtin(name = SUM, minNumOfPositionalArgs = 1, parameterNames = {"iterable", "start"})
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
                        @Cached ConditionProfile hasStart,
                        @Cached BranchProfile stringStart,
                        @Cached BranchProfile bytesStart,
                        @Cached BranchProfile byteArrayStart) {
            if (PGuards.isString(start)) {
                stringStart.enter();
                throw raise(TypeError, ErrorMessages.CANT_SUM_STRINGS);
            } else if (start instanceof PBytes) {
                bytesStart.enter();
                throw raise(TypeError, ErrorMessages.CANT_SUM_BYTES);
            } else if (start instanceof PByteArray) {
                byteArrayStart.enter();
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
    }

    @Builtin(name = "open", minNumOfPositionalArgs = 1, parameterNames = {"file", "mode", "buffering", "encoding", "errors", "newline", "closefd", "opener"})
    @ArgumentClinic(name = "mode", conversionClass = IONodes.CreateIOModeNode.class, args = "true")
    @ArgumentClinic(name = "buffering", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1", useDefaultForNone = true)
    @ArgumentClinic(name = "encoding", conversion = ArgumentClinic.ClinicConversion.String, defaultValue = "PNone.NONE", useDefaultForNone = true)
    @ArgumentClinic(name = "errors", conversion = ArgumentClinic.ClinicConversion.String, defaultValue = "PNone.NONE", useDefaultForNone = true)
    @ArgumentClinic(name = "newline", conversion = ArgumentClinic.ClinicConversion.String, defaultValue = "PNone.NONE", useDefaultForNone = true)
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
                        @Cached GetClassNode getMroClass,
                        @Cached(parameters = "__MRO_ENTRIES__") LookupAttributeInMRONode getMroEntries,
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

                Object meth = getMroEntries.execute(getMroClass.execute(base));
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
                    throw raise(PythonErrorType.TypeError, "__mro_entries__ must return a tuple");
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

    @Builtin(name = BuiltinNames.__BUILD_CLASS__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    @ImportStatic(SpecialMethodSlot.class)
    public abstract static class BuildClassNode extends PythonVarargsBuiltinNode {
        @TruffleBoundary
        private static Object buildJavaClass(Object namespace, String name, Object base) {
            // uncached PythonContext get, since this code path is slow in any case
            Object module = PythonContext.get(null).lookupBuiltinModule(BuiltinNames.__GRAALPYTHON__);
            Object buildFunction = PyObjectLookupAttr.getUncached().execute(null, module, "build_java_class");
            return CallNode.getUncached().execute(buildFunction, namespace, name, base);
        }

        @Specialization
        protected Object doItNonFunction(VirtualFrame frame, Object function, Object[] arguments, PKeyword[] keywords,
                        @Cached PythonObjectFactory factory,
                        @Cached CalculateMetaclassNode calculateMetaClass,
                        @Cached("create(__PREPARE__)") GetAttributeNode getPrepare,
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
                throw raise(PythonErrorType.TypeError, "__build_class__: not enough arguments");
            }

            if (!PGuards.isFunction(function)) {
                throw raise(PythonErrorType.TypeError, "__build_class__: func must be a function");
            }
            String name;
            try {
                name = CastToJavaStringNode.getUncached().execute(arguments[0]);
            } catch (CannotCastException e) {
                throw raise(PythonErrorType.TypeError, "__build_class__: name is not a string");
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
                        if ("metaclass".equals(keywords[i].getName())) {
                            meta = keywords[i].getValue();
                            mkw = new PKeyword[keywords.length - 1];

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
                    throw raise(PythonErrorType.TypeError, "%N.__prepare__() must return a mapping, not %p", init.meta, ns);
                } else {
                    throw raise(PythonErrorType.TypeError, "<metaclass>.__prepare__() must return a mapping, not %p", ns);
                }
            }
            Object[] bodyArguments = PArguments.create(0);
            PArguments.setCustomLocals(bodyArguments, ns);
            PArguments.setSpecialArgument(bodyArguments, ns);
            callBody.executeCall(frame, (PFunction) function, bodyArguments);
            if (init.bases != origBases) {
                setOrigBases.executeWith(frame, ns, SpecialAttributeNames.__ORIG_BASES__, origBases);
            }
            Object cls = callType.execute(frame, init.meta, new Object[]{name, init.bases, ns}, init.mkw);

            /*
             * We could check here and throw "__class__ not set defining..." errors.
             */

            return cls;
        }
    }
}
