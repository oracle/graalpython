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
package com.oracle.graal.python.builtins.objects.exception;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.BuiltinNames.T___NOTES__;
import static com.oracle.graal.python.nodes.ErrorMessages.P_TAKES_NO_KEYWORD_ARGS;
import static com.oracle.graal.python.nodes.ErrorMessages.STATE_IS_NOT_A_DICT;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___CAUSE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___CONTEXT__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___DICT__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___SUPPRESS_CONTEXT__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___TRACEBACK__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___NAME__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SETSTATE__;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_PARENS;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_STRING;
import static com.oracle.graal.python.nodes.StringLiterals.T_LPAREN;
import static com.oracle.graal.python.nodes.StringLiterals.T_RPAREN;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageForEach;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageForEachCallback;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItemWithHash;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorKey;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorKeyHash;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.PyExceptionInstanceCheckNode;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectReprAsTruffleStringNode;
import com.oracle.graal.python.lib.PyObjectSetAttr;
import com.oracle.graal.python.lib.PyObjectSetAttrO;
import com.oracle.graal.python.lib.PyObjectStrAsObjectNode;
import com.oracle.graal.python.lib.PyUnicodeCheckNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.expression.CastToListExpressionNode.CastToListNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetOrCreateDictNode;
import com.oracle.graal.python.nodes.object.SetDictNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaBooleanNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleStringBuilder;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PBaseException)
public final class BaseExceptionBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = BaseExceptionBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return BaseExceptionBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = "BaseException", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class BaseExceptionNode extends PythonVarargsBuiltinNode {

        @Specialization(guards = "!needsNativeAllocationNode.execute(inliningTarget, cls)", limit = "1")
        static Object doManaged(Object cls, @SuppressWarnings("unused") Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @Bind Node inliningTarget,
                        @SuppressWarnings("unused") @Cached.Exclusive @Cached TypeNodes.NeedsNativeAllocationNode needsNativeAllocationNode,
                        @Bind PythonLanguage language,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape,
                        @Cached InlinedConditionProfile argsProfile) {
            PTuple argsTuple;
            if (argsProfile.profile(inliningTarget, args.length == 0)) {
                argsTuple = null;
            } else {
                argsTuple = PFactory.createTuple(language, args);
            }
            return PFactory.createBaseException(language, cls, getInstanceShape.execute(cls), null, argsTuple);
        }

        @Specialization(guards = "needsNativeAllocationNode.execute(inliningTarget, cls)", limit = "1")
        static Object doNativeSubtype(Object cls, Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @SuppressWarnings("unused") @Bind Node inliningTarget,
                        @SuppressWarnings("unused") @Cached.Exclusive @Cached TypeNodes.NeedsNativeAllocationNode needsNativeAllocationNode,
                        @Bind PythonLanguage language,
                        @Cached CExtNodes.PCallCapiFunction callCapiFunction,
                        @Cached CApiTransitions.PythonToNativeNode toNativeNode,
                        @Cached CApiTransitions.NativeToPythonTransferNode toPythonNode,
                        @Cached ExternalFunctionNodes.DefaultCheckFunctionResultNode checkFunctionResultNode) {
            Object argsTuple = args.length > 0 ? PFactory.createTuple(language, args) : PFactory.createEmptyTuple(language);
            Object nativeResult = callCapiFunction.call(NativeCAPISymbol.FUN_EXCEPTION_SUBTYPE_NEW, toNativeNode.execute(cls), toNativeNode.execute(argsTuple));
            return toPythonNode.execute(checkFunctionResultNode.execute(PythonContext.get(inliningTarget), NativeCAPISymbol.FUN_EXCEPTION_SUBTYPE_NEW.getTsName(), nativeResult));
        }
    }

    @Slot(value = SlotKind.tp_init, isComplex = true)
    @SlotSignature(minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class BaseExceptionInitNode extends PythonVarargsBuiltinNode {

        public final Object execute(Object self, Object[] args) {
            return execute(null, self, args, PKeyword.EMPTY_KEYWORDS);
        }

        @Specialization(guards = {"args.length == 0", "keywords.length == 0"})
        static Object doNoArguments(PBaseException self, @SuppressWarnings("unused") Object[] args, @SuppressWarnings("unused") PKeyword[] keywords) {
            self.setArgs(null);
            return PNone.NONE;
        }

        @Specialization(guards = {"args.length != 0", "keywords.length == 0"})
        Object doWithArguments(PBaseException self, Object[] args, @SuppressWarnings("unused") PKeyword[] keywords,
                        @Bind PythonLanguage language) {
            self.setArgs(PFactory.createTuple(language, args));
            return PNone.NONE;
        }

        @Specialization(replaces = {"doNoArguments", "doWithArguments"})
        static Object doGeneric(PBaseException self, Object[] args, PKeyword[] keywords,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            if (keywords.length != 0) {
                throw raiseNode.raise(inliningTarget, TypeError, P_TAKES_NO_KEYWORD_ARGS, self);
            }
            if (args.length == 0) {
                self.setArgs(null);
            } else {
                self.setArgs(PFactory.createTuple(language, args));
            }
            return PNone.NONE;
        }

        @Specialization
        static Object doNative(PythonAbstractNativeObject self, Object[] args, PKeyword[] keywords,
                        @Bind Node inliningTarget,
                        @Cached ExceptionNodes.SetArgsNode setArgsNode,
                        @Bind PythonLanguage language,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            if (keywords.length != 0) {
                throw raiseNode.raise(inliningTarget, TypeError, P_TAKES_NO_KEYWORD_ARGS, self);
            }
            if (args.length == 0) {
                setArgsNode.execute(inliningTarget, self, PFactory.createEmptyTuple(language));
            } else {
                setArgsNode.execute(inliningTarget, self, PFactory.createTuple(language, args));
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "args", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    public abstract static class ArgsNode extends PythonBuiltinNode {

        @Specialization(guards = "isNoValue(none)")
        static Object args(Object self, @SuppressWarnings("unused") PNone none,
                        @Bind Node inliningTarget,
                        @Cached ExceptionNodes.GetArgsNode getArgsNode) {
            return getArgsNode.execute(inliningTarget, self);
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object args(VirtualFrame frame, Object self, Object value,
                        @Bind Node inliningTarget,
                        @Cached CastToListNode castToList,
                        @Cached SequenceStorageNodes.CopyInternalArrayNode copy,
                        @Cached ExceptionNodes.SetArgsNode setArgsNode,
                        @Bind PythonLanguage language) {
            PList list = castToList.execute(frame, value);
            setArgsNode.execute(inliningTarget, self, PFactory.createTuple(language, copy.execute(inliningTarget, list.getSequenceStorage())));
            return PNone.NONE;
        }
    }

    @Builtin(name = J___CAUSE__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    public abstract static class CauseNode extends PythonBuiltinNode {
        @Specialization(guards = "isNoValue(value)")
        public static Object getCause(Object self, @SuppressWarnings("unused") PNone value,
                        @Bind Node inliningTarget,
                        @Cached ExceptionNodes.GetCauseNode getCauseNode) {
            return getCauseNode.execute(inliningTarget, self);
        }

        @Specialization(guards = {"!isNoValue(value)", "check.execute(inliningTarget, value)"})
        public static Object setCause(Object self, Object value,
                        @Bind Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached PyExceptionInstanceCheckNode check,
                        @Shared @Cached ExceptionNodes.SetCauseNode setCauseNode) {
            setCauseNode.execute(inliningTarget, self, value);
            return PNone.NONE;
        }

        @Specialization(guards = "isNone(value)")
        public static Object setCause(Object self, @SuppressWarnings("unused") PNone value,
                        @Bind Node inliningTarget,
                        @Shared @Cached ExceptionNodes.SetCauseNode setCauseNode) {
            setCauseNode.execute(inliningTarget, self, PNone.NONE);
            return PNone.NONE;
        }

        @Specialization(guards = {"!isNoValue(value)", "!check.execute(inliningTarget, value)"})
        public static Object cause(@SuppressWarnings("unused") Object self, @SuppressWarnings("unused") Object value,
                        @SuppressWarnings("unused") @Shared @Cached PyExceptionInstanceCheckNode check,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.EXCEPTION_CAUSE_MUST_BE_NONE_OR_DERIVE_FROM_BASE_EX);
        }
    }

    @Builtin(name = J___CONTEXT__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    public abstract static class ContextNode extends PythonBuiltinNode {
        @Specialization(guards = "isNoValue(value)")
        public static Object getContext(Object self, @SuppressWarnings("unused") PNone value,
                        @Bind Node inliningTarget,
                        @Cached ExceptionNodes.GetContextNode getContextNode) {
            return getContextNode.execute(inliningTarget, self);
        }

        @Specialization(guards = {"!isNoValue(value)", "check.execute(inliningTarget, value)"})
        public static Object setContext(Object self, Object value,
                        @Bind Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached PyExceptionInstanceCheckNode check,
                        @Shared @Cached ExceptionNodes.SetContextNode setContextNode) {
            setContextNode.execute(inliningTarget, self, value);
            return PNone.NONE;
        }

        @Specialization(guards = "isNone(value)")
        public static Object setContext(Object self, @SuppressWarnings("unused") PNone value,
                        @Bind Node inliningTarget,
                        @Shared @Cached ExceptionNodes.SetContextNode setContextNode) {
            setContextNode.execute(inliningTarget, self, PNone.NONE);
            return PNone.NONE;
        }

        @Specialization(guards = {"!isNoValue(value)", "!check.execute(inliningTarget, value)"})
        public static Object context(@SuppressWarnings("unused") Object self, @SuppressWarnings("unused") Object value,
                        @SuppressWarnings("unused") @Shared @Cached PyExceptionInstanceCheckNode check,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.EXCEPTION_CONTEXT_MUST_BE_NONE_OR_DERIVE_FROM_BASE_EX);
        }
    }

    @Builtin(name = J___SUPPRESS_CONTEXT__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    public abstract static class SuppressContextNode extends PythonBuiltinNode {
        @Specialization(guards = "isNoValue(value)")
        static Object getSuppressContext(Object self, @SuppressWarnings("unused") PNone value,
                        @Bind Node inliningTarget,
                        @Cached ExceptionNodes.GetSuppressContextNode getSuppressContextNode) {
            return getSuppressContextNode.execute(inliningTarget, self);
        }

        @Specialization(guards = "!isNoValue(valueObj)")
        static Object setSuppressContext(Object self, Object valueObj,
                        @Bind Node inliningTarget,
                        @Cached ExceptionNodes.SetSuppressContextNode setSuppressContextNode,
                        @Cached CastToJavaBooleanNode castToJavaBooleanNode,
                        @Cached PRaiseNode raiseNode) {
            boolean value;
            try {
                value = castToJavaBooleanNode.execute(inliningTarget, valueObj);
            } catch (CannotCastException e) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.ATTR_VALUE_MUST_BE_BOOL);
            }
            setSuppressContextNode.execute(inliningTarget, self, value);
            return PNone.NONE;
        }
    }

    @Builtin(name = J___TRACEBACK__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    public abstract static class TracebackNode extends PythonBuiltinNode {

        @Specialization(guards = "isNoValue(tb)")
        static Object getTraceback(Object self, @SuppressWarnings("unused") Object tb,
                        @Bind Node inliningTarget,
                        @Cached ExceptionNodes.GetTracebackNode getTracebackNode) {
            return getTracebackNode.execute(inliningTarget, self);
        }

        @Specialization(guards = "!isNoValue(tb)")
        static Object setTraceback(Object self, @SuppressWarnings("unused") PNone tb,
                        @Bind Node inliningTarget,
                        @Shared @Cached ExceptionNodes.SetTracebackNode setTracebackNode) {
            setTracebackNode.execute(inliningTarget, self, PNone.NONE);
            return PNone.NONE;
        }

        @Specialization
        static Object setTraceback(Object self, PTraceback tb,
                        @Bind Node inliningTarget,
                        @Shared @Cached ExceptionNodes.SetTracebackNode setTracebackNode) {
            setTracebackNode.execute(inliningTarget, self, tb);
            return PNone.NONE;
        }

        @Fallback
        static Object setTraceback(@SuppressWarnings("unused") Object self, @SuppressWarnings("unused") Object tb,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, PythonErrorType.TypeError, ErrorMessages.MUST_BE_S_OR_S, "__traceback__", "a traceback", "None");
        }
    }

    @Builtin(name = "with_traceback", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class WithTracebackNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object doClearTraceback(Object self, @SuppressWarnings("unused") PNone tb,
                        @Bind Node inliningTarget,
                        @Shared @Cached ExceptionNodes.SetTracebackNode setTracebackNode) {
            setTracebackNode.execute(inliningTarget, self, PNone.NONE);
            return self;
        }

        @Specialization
        static Object doSetTraceback(Object self, PTraceback tb,
                        @Bind Node inliningTarget,
                        @Shared @Cached ExceptionNodes.SetTracebackNode setTracebackNode) {
            setTracebackNode.execute(inliningTarget, self, tb);
            return self;
        }
    }

    @Builtin(name = J___DICT__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class DictNode extends PythonBinaryBuiltinNode {
        @Specialization
        static PNone dict(Object self, PDict mapping,
                        @Bind Node inliningTarget,
                        @Cached SetDictNode setDict) {
            setDict.execute(inliningTarget, self, mapping);
            return PNone.NONE;
        }

        @Specialization(guards = "isNoValue(mapping)")
        static Object dict(Object self, @SuppressWarnings("unused") PNone mapping,
                        @Bind Node inliningTarget,
                        @Cached GetOrCreateDictNode getDict) {
            return getDict.execute(inliningTarget, self);
        }

        @Specialization(guards = {"!isNoValue(mapping)", "!isDict(mapping)"})
        static PNone dict(@SuppressWarnings("unused") Object self, Object mapping,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.DICT_MUST_BE_SET_TO_DICT, mapping);
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object reduce(VirtualFrame frame, Object self,
                        @Bind Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached ExceptionNodes.GetArgsNode argsNode,
                        @Cached DictNode dictNode,
                        @Bind PythonLanguage language) {
            Object clazz = getClassNode.execute(inliningTarget, self);
            PTuple args = argsNode.execute(inliningTarget, self);
            Object dict = dictNode.execute(frame, self, PNone.NO_VALUE);
            return PFactory.createTuple(language, new Object[]{clazz, args, dict});
        }
    }

    @Slot(value = SlotKind.tp_repr, isComplex = true)
    @GenerateNodeFactory
    public abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object repr(VirtualFrame frame, Object self,
                        @Bind Node inliningTarget,
                        @Cached InlinedBranchProfile noArgsProfile,
                        @Cached InlinedBranchProfile oneArgProfile,
                        @Cached InlinedBranchProfile moreArgsProfile,
                        @Cached ExceptionNodes.GetArgsNode getArgsNode,
                        @Cached GetClassNode getClassNode,
                        @Cached PyObjectGetAttr getAttrNode,
                        @Cached CastToTruffleStringNode castStringNode,
                        @Cached PyObjectReprAsTruffleStringNode reprNode,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItemScalarNode,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            Object type = getClassNode.execute(inliningTarget, self);
            TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING);
            appendStringNode.execute(sb, castStringNode.execute(inliningTarget, getAttrNode.execute(frame, inliningTarget, type, T___NAME__)));
            PTuple args = getArgsNode.execute(inliningTarget, self);
            SequenceStorage argsStorage = args.getSequenceStorage();
            if (argsStorage.length() == 1) {
                oneArgProfile.enter(inliningTarget);
                appendStringNode.execute(sb, T_LPAREN);
                appendStringNode.execute(sb, reprNode.execute(frame, inliningTarget, getItemScalarNode.execute(inliningTarget, argsStorage, 0)));
                appendStringNode.execute(sb, T_RPAREN);
            } else if (argsStorage.length() > 1) {
                moreArgsProfile.enter(inliningTarget);
                appendStringNode.execute(sb, reprNode.execute(frame, inliningTarget, args));
            } else {
                noArgsProfile.enter(inliningTarget);
                appendStringNode.execute(sb, T_EMPTY_PARENS);
            }
            return toStringNode.execute(sb);
        }
    }

    @Slot(value = SlotKind.tp_str, isComplex = true)
    @GenerateNodeFactory
    public abstract static class StrNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object str(VirtualFrame frame, Object self,
                        @Bind Node inliningTarget,
                        @Cached InlinedBranchProfile noArgsProfile,
                        @Cached InlinedBranchProfile oneArgProfile,
                        @Cached InlinedBranchProfile moreArgsProfile,
                        @Cached ExceptionNodes.GetArgsNode getArgsNode,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItemScalarNode,
                        @Cached PyObjectStrAsObjectNode strNode) {
            PTuple args = getArgsNode.execute(inliningTarget, self);
            SequenceStorage argsStorage = args.getSequenceStorage();
            if (argsStorage.length() == 1) {
                oneArgProfile.enter(inliningTarget);
                return strNode.execute(frame, inliningTarget, getItemScalarNode.execute(inliningTarget, argsStorage, 0));
            } else if (argsStorage.length() > 1) {
                moreArgsProfile.enter(inliningTarget);
                return strNode.execute(frame, inliningTarget, args);
            } else {
                noArgsProfile.enter(inliningTarget);
                return T_EMPTY_STRING;
            }
        }
    }

    @Builtin(name = J___SETSTATE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class BaseExceptionSetStateNode extends PythonBinaryBuiltinNode {
        @ValueType
        static final class ExcState {
            private final HashingStorage dictStorage;
            private final Object exception;

            ExcState(HashingStorage dictStorage, Object exception) {
                this.dictStorage = dictStorage;
                this.exception = exception;
            }
        }

        @ImportStatic(PGuards.class)
        @GenerateInline
        @GenerateCached(false)
        abstract static class ForEachKW extends HashingStorageForEachCallback<ExcState> {
            @Override
            public abstract ExcState execute(Frame frame, Node node, HashingStorage storage, HashingStorageIterator it, ExcState state);

            @Specialization
            public static ExcState doIt(Frame frame, @SuppressWarnings("unused") Node node, HashingStorage storage, HashingStorageIterator it, ExcState state,
                            @Bind Node inliningTarget,
                            @Cached PyObjectSetAttrO setAttr,
                            @Cached HashingStorageIteratorKey itKey,
                            @Cached HashingStorageIteratorKeyHash itKeyHash,
                            @Cached HashingStorageGetItemWithHash getItem) {
                Object key = itKey.execute(inliningTarget, storage, it);
                Object value = getItem.execute(frame, inliningTarget, state.dictStorage, key, itKeyHash.execute(frame, inliningTarget, storage, it));
                setAttr.execute(frame, inliningTarget, state.exception, key, value);
                return state;
            }
        }

        @Specialization
        static Object setDict(VirtualFrame frame, Object self, PDict state,
                        @Bind Node inliningTarget,
                        @Cached ForEachKW forEachKW,
                        @Cached HashingStorageForEach forEachNode) {
            final HashingStorage dictStorage = state.getDictStorage();
            forEachNode.execute(frame, inliningTarget, dictStorage, forEachKW, new ExcState(dictStorage, self));
            return PNone.NONE;
        }

        @Specialization(guards = "!isDict(state)")
        static Object generic(@SuppressWarnings("unused") Object self, Object state,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            if (state != PNone.NONE) {
                throw raiseNode.raise(inliningTarget, TypeError, STATE_IS_NOT_A_DICT);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "add_note", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class AddNoteNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object addNote(VirtualFrame frame, Object self, Object note,
                        @Bind Node inliningTarget,
                        @Cached PyUnicodeCheckNode unicodeCheckNode,
                        @Cached PyObjectLookupAttr lookupAttr,
                        @Cached PyObjectSetAttr setAttr,
                        @Cached ListNodes.AppendNode appendNode,
                        @Bind PythonLanguage language,
                        @Cached PRaiseNode raiseNode) {
            if (!unicodeCheckNode.execute(inliningTarget, note)) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.NOTE_MUST_BE_A_STR_NOT_P, note);
            }
            Object notes = lookupAttr.execute(frame, inliningTarget, self, T___NOTES__);
            if (notes == PNone.NO_VALUE) {
                notes = PFactory.createList(language);
                setAttr.execute(frame, inliningTarget, self, T___NOTES__, notes);
            }
            if (notes instanceof PList notesList) {
                appendNode.execute(notesList, note);
            } else {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.CANNOT_ADD_NOTE_NOTES_IS_NOT_A_LIST);
            }
            return PNone.NONE;
        }
    }
}
