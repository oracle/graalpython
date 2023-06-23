/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates.
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
import static com.oracle.graal.python.nodes.ErrorMessages.P_TAKES_NO_KEYWORD_ARGS;
import static com.oracle.graal.python.nodes.ErrorMessages.STATE_IS_NOT_A_DICT;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___CAUSE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___CONTEXT__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___DICT__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___SUPPRESS_CONTEXT__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___TRACEBACK__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___NAME__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SETSTATE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___STR__;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_PARENS;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_STRING;
import static com.oracle.graal.python.nodes.StringLiterals.T_LPAREN;
import static com.oracle.graal.python.nodes.StringLiterals.T_RPAREN;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.util.IllegalFormatException;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
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
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltins.GetItemNode;
import com.oracle.graal.python.lib.PyExceptionInstanceCheckNode;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectReprAsTruffleStringNode;
import com.oracle.graal.python.lib.PyObjectSetAttr;
import com.oracle.graal.python.lib.PyObjectStrAsObjectNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.argument.ReadArgumentNode;
import com.oracle.graal.python.nodes.expression.CastToListExpressionNode.CastToListNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.object.GetOrCreateDictNode;
import com.oracle.graal.python.nodes.object.InlinedGetClassNode;
import com.oracle.graal.python.nodes.object.SetDictNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaBooleanNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.nodes.util.SplitArgsNode;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.formatting.ErrorMessageFormatter;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PBaseException)
public class BaseExceptionBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return BaseExceptionBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___INIT__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class BaseExceptionInitNode extends PythonVarargsBuiltinNode {
        @Child private SplitArgsNode splitArgsNode;

        public final Object execute(PBaseException self, Object[] args) {
            return execute(null, self, args, PKeyword.EMPTY_KEYWORDS);
        }

        @Override
        public Object varArgExecute(VirtualFrame frame, Object self, Object[] arguments, PKeyword[] keywords) throws VarargsBuiltinDirectInvocationNotSupported {
            if (arguments.length == 0) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw VarargsBuiltinDirectInvocationNotSupported.INSTANCE;
            }
            if (splitArgsNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                splitArgsNode = insert(SplitArgsNode.create());
            }
            Object[] argsWithoutSelf = splitArgsNode.execute(arguments);
            return execute(frame, arguments[0], argsWithoutSelf, keywords);
        }

        @Specialization(guards = {"args.length == 0", "keywords.length == 0"})
        static Object doNoArguments(PBaseException self, @SuppressWarnings("unused") Object[] args, @SuppressWarnings("unused") PKeyword[] keywords) {
            self.setArgs(null);
            return PNone.NONE;
        }

        @Specialization(guards = {"args.length != 0", "keywords.length == 0"})
        Object doWithArguments(PBaseException self, Object[] args, @SuppressWarnings("unused") PKeyword[] keywords) {
            self.setArgs(factory().createTuple(args));
            return PNone.NONE;
        }

        @Specialization(replaces = {"doNoArguments", "doWithArguments"})
        Object doGeneric(PBaseException self, Object[] args, PKeyword[] keywords) {
            if (keywords.length != 0) {
                throw raise(PythonBuiltinClassType.TypeError, P_TAKES_NO_KEYWORD_ARGS, self);
            }
            if (args.length == 0) {
                self.setArgs(null);
            } else {
                self.setArgs(factory().createTuple(args));
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "args", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    public abstract static class ArgsNode extends PythonBuiltinNode {

        private final ErrorMessageFormatter formatter = new ErrorMessageFormatter();

        @TruffleBoundary
        private String getFormattedMessage(TruffleString format, Object... args) {
            String jFormat = format.toJavaStringUncached();
            try {
                // pre-format for custom error message formatter
                if (ErrorMessageFormatter.containsCustomSpecifier(jFormat)) {
                    return formatter.format(jFormat, args);
                }
                return String.format(jFormat, args);
            } catch (IllegalFormatException e) {
                // According to PyUnicode_FromFormat, invalid format specifiers are just ignored.
                return jFormat;
            }
        }

        @Specialization(guards = "isNoValue(none)")
        public Object args(PBaseException self, @SuppressWarnings("unused") PNone none,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedConditionProfile nullArgsProfile,
                        @Cached InlinedConditionProfile hasMessageFormat,
                        @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
            PTuple args = self.getArgs();
            if (nullArgsProfile.profile(inliningTarget, args == null)) {
                if (hasMessageFormat.profile(inliningTarget, !self.hasMessageFormat())) {
                    args = factory().createEmptyTuple();
                } else {
                    // lazily format the exception message:
                    args = factory().createTuple(new Object[]{fromJavaStringNode.execute(getFormattedMessage(self.getMessageFormat(), self.getMessageArgs()), TS_ENCODING)});
                }
                self.setArgs(args);
            }
            return args;
        }

        @Specialization(guards = "!isNoValue(value)")
        public Object args(VirtualFrame frame, PBaseException self, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToListNode castToList,
                        @Cached SequenceStorageNodes.CopyInternalArrayNode copy) {
            PList list = castToList.execute(frame, value);
            self.setArgs(factory().createTuple(copy.execute(inliningTarget, list.getSequenceStorage())));
            return PNone.NONE;
        }

        public abstract Object executeObject(VirtualFrame frame, Object excObj, Object value);

        public final Object executeGet(VirtualFrame frame, Object excObj) {
            return executeObject(frame, excObj, PNone.NO_VALUE);
        }

        @NeverDefault
        public static ArgsNode create() {
            return BaseExceptionBuiltinsFactory.ArgsNodeFactory.create(new ReadArgumentNode[]{});
        }
    }

    @Builtin(name = J___CAUSE__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    public abstract static class CauseNode extends PythonBuiltinNode {
        @Specialization(guards = "isNoValue(value)")
        public static Object getCause(Object self, @SuppressWarnings("unused") PNone value,
                        @Bind("this") Node inliningTarget,
                        @Cached ExceptionNodes.GetCauseNode getCauseNode) {
            return getCauseNode.execute(inliningTarget, self);
        }

        @Specialization(guards = {"!isNoValue(value)", "check.execute(inliningTarget, value)"})
        public static Object setCause(Object self, Object value,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached PyExceptionInstanceCheckNode check,
                        @Shared @Cached ExceptionNodes.SetCauseNode setCauseNode) {
            setCauseNode.execute(inliningTarget, self, value);
            return PNone.NONE;
        }

        @Specialization(guards = "isNone(value)")
        public static Object setCause(Object self, @SuppressWarnings("unused") PNone value,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached ExceptionNodes.SetCauseNode setCauseNode) {
            setCauseNode.execute(inliningTarget, self, PNone.NONE);
            return PNone.NONE;
        }

        @Specialization(guards = {"!isNoValue(value)", "!check.execute(inliningTarget, value)"})
        public static Object cause(@SuppressWarnings("unused") Object self, @SuppressWarnings("unused") Object value,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached PyExceptionInstanceCheckNode check,
                        @Cached PRaiseNode raise) {
            throw raise.raise(TypeError, ErrorMessages.EXCEPTION_CAUSE_MUST_BE_NONE_OR_DERIVE_FROM_BASE_EX);
        }
    }

    @Builtin(name = J___CONTEXT__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    public abstract static class ContextNode extends PythonBuiltinNode {
        @Specialization(guards = "isNoValue(value)")
        public static Object getContext(Object self, @SuppressWarnings("unused") PNone value,
                        @Bind("this") Node inliningTarget,
                        @Cached ExceptionNodes.GetContextNode getContextNode) {
            return getContextNode.execute(inliningTarget, self);
        }

        @Specialization(guards = {"!isNoValue(value)", "check.execute(inliningTarget, value)"})
        public static Object setContext(Object self, Object value,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached PyExceptionInstanceCheckNode check,
                        @Shared @Cached ExceptionNodes.SetContextNode setContextNode) {
            setContextNode.execute(inliningTarget, self, value);
            return PNone.NONE;
        }

        @Specialization(guards = "isNone(value)")
        public static Object setContext(Object self, @SuppressWarnings("unused") PNone value,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached ExceptionNodes.SetContextNode setContextNode) {
            setContextNode.execute(inliningTarget, self, PNone.NONE);
            return PNone.NONE;
        }

        @Specialization(guards = {"!isNoValue(value)", "!check.execute(inliningTarget, value)"})
        public static Object context(@SuppressWarnings("unused") Object self, @SuppressWarnings("unused") Object value,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached PyExceptionInstanceCheckNode check,
                        @Cached PRaiseNode raise) {
            throw raise.raise(TypeError, ErrorMessages.EXCEPTION_CONTEXT_MUST_BE_NONE_OR_DERIVE_FROM_BASE_EX);
        }
    }

    @Builtin(name = J___SUPPRESS_CONTEXT__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    public abstract static class SuppressContextNode extends PythonBuiltinNode {
        @Specialization(guards = "isNoValue(value)")
        public static Object getSuppressContext(Object self, @SuppressWarnings("unused") PNone value,
                        @Bind("this") Node inliningTarget,
                        @Cached ExceptionNodes.GetSuppressContextNode getSuppressContextNode) {
            return getSuppressContextNode.execute(inliningTarget, self);
        }

        @Specialization
        public static Object setSuppressContext(Object self, boolean value,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached ExceptionNodes.SetSuppressContextNode setSuppressContextNode) {
            setSuppressContextNode.execute(inliningTarget, self, value);
            return PNone.NONE;
        }

        @Specialization(guards = "!isBoolean(valueObj)")
        public Object setSuppressContext(Object self, Object valueObj,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached ExceptionNodes.SetSuppressContextNode setSuppressContextNode,
                        @Cached CastToJavaBooleanNode castToJavaBooleanNode) {
            boolean value;
            try {
                value = castToJavaBooleanNode.execute(valueObj);
            } catch (CannotCastException e) {
                throw raise(TypeError, ErrorMessages.ATTR_VALUE_MUST_BE_BOOL);
            }
            setSuppressContextNode.execute(inliningTarget, self, value);
            return PNone.NONE;
        }
    }

    @Builtin(name = J___TRACEBACK__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    public abstract static class TracebackNode extends PythonBuiltinNode {

        @Specialization(guards = "isNoValue(tb)")
        public static Object getTraceback(Object self, @SuppressWarnings("unused") Object tb,
                        @Bind("this") Node inliningTarget,
                        @Cached ExceptionNodes.GetTracebackNode getTracebackNode) {
            return getTracebackNode.execute(inliningTarget, self);
        }

        @Specialization(guards = "!isNoValue(tb)")
        public static Object setTraceback(Object self, @SuppressWarnings("unused") PNone tb,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached ExceptionNodes.SetTracebackNode setTracebackNode) {
            setTracebackNode.execute(inliningTarget, self, PNone.NONE);
            return PNone.NONE;
        }

        @Specialization
        public static Object setTraceback(Object self, PTraceback tb,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached ExceptionNodes.SetTracebackNode setTracebackNode) {
            setTracebackNode.execute(inliningTarget, self, tb);
            return PNone.NONE;
        }

        @Fallback
        public Object setTraceback(@SuppressWarnings("unused") Object self, @SuppressWarnings("unused") Object tb) {
            throw raise(PythonErrorType.TypeError, ErrorMessages.MUST_BE_S_OR_S, "__traceback__", "a traceback", "None");
        }
    }

    @Builtin(name = "with_traceback", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class WithTracebackNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object doClearTraceback(Object self, @SuppressWarnings("unused") PNone tb,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached ExceptionNodes.SetTracebackNode setTracebackNode) {
            setTracebackNode.execute(inliningTarget, self, PNone.NONE);
            return self;
        }

        @Specialization
        static Object doSetTraceback(Object self, PTraceback tb,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached ExceptionNodes.SetTracebackNode setTracebackNode) {
            setTracebackNode.execute(inliningTarget, self, tb);
            return self;
        }
    }

    @Builtin(name = J___DICT__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class DictNode extends PythonBinaryBuiltinNode {
        @Specialization
        static PNone dict(PBaseException self, PDict mapping,
                        @Cached SetDictNode setDict) {
            setDict.execute(self, mapping);
            return PNone.NONE;
        }

        @Specialization(guards = "isNoValue(mapping)")
        Object dict(PBaseException self, @SuppressWarnings("unused") PNone mapping,
                        @Cached GetOrCreateDictNode getDict) {
            return getDict.execute(self);
        }

        @Specialization(guards = {"!isNoValue(mapping)", "!isDict(mapping)"})
        PNone dict(@SuppressWarnings("unused") PBaseException self, Object mapping) {
            throw raise(TypeError, ErrorMessages.DICT_MUST_BE_SET_TO_DICT, mapping);
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object reduce(VirtualFrame frame, PBaseException self,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedGetClassNode getClassNode,
                        @Cached ArgsNode argsNode,
                        @Cached DictNode dictNode) {
            Object clazz = getClassNode.execute(inliningTarget, self);
            Object args = argsNode.executeObject(frame, self, PNone.NO_VALUE);
            Object dict = dictNode.execute(frame, self, PNone.NO_VALUE);
            return factory().createTuple(new Object[]{clazz, args, dict});
        }
    }

    @Builtin(name = J___REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "argsLen(frame, self, argsNode) == 0", limit = "1")
        Object reprNoArgs(@SuppressWarnings("unused") VirtualFrame frame, PBaseException self,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared("argsNode") @Cached ArgsNode argsNode,
                        @Shared("getClass") @Cached InlinedGetClassNode getClassNode,
                        @Shared("getAttr") @Cached PyObjectGetAttr getAttrNode,
                        @Shared("castStr") @Cached CastToTruffleStringNode castStringNode,
                        @Shared("appendStr") @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Shared("toStr") @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            Object type = getClassNode.execute(inliningTarget, self);
            TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING);
            appendStringNode.execute(sb, castStringNode.execute(getAttrNode.execute(frame, type, T___NAME__)));
            appendStringNode.execute(sb, T_EMPTY_PARENS);
            return toStringNode.execute(sb);
        }

        @Specialization(guards = "argsLen(frame, self, argsNode) == 1", limit = "1")
        Object reprArgs1(VirtualFrame frame, PBaseException self,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared("argsNode") @Cached ArgsNode argsNode,
                        @Shared("getClass") @Cached InlinedGetClassNode getClassNode,
                        @Shared("getAttr") @Cached PyObjectGetAttr getAttrNode,
                        @Cached GetItemNode getItemNode,
                        @Cached PyObjectReprAsTruffleStringNode reprNode,
                        @Shared("castStr") @Cached CastToTruffleStringNode castStringNode,
                        @Shared("appendStr") @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Shared("toStr") @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            Object type = getClassNode.execute(inliningTarget, self);
            TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING);
            appendStringNode.execute(sb, castStringNode.execute(getAttrNode.execute(frame, type, T___NAME__)));
            appendStringNode.execute(sb, T_LPAREN);
            appendStringNode.execute(sb, reprNode.execute(frame, getItemNode.execute(frame, self.getArgs(), 0)));
            appendStringNode.execute(sb, T_RPAREN);
            return toStringNode.execute(sb);
        }

        @Specialization(guards = "argsLen(frame, self, argsNode) > 1", limit = "1")
        Object reprArgs(VirtualFrame frame, PBaseException self,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared("argsNode") @Cached ArgsNode argsNode,
                        @Shared("getClass") @Cached InlinedGetClassNode getClassNode,
                        @Shared("getAttr") @Cached PyObjectGetAttr getAttrNode,
                        @Cached com.oracle.graal.python.builtins.objects.tuple.TupleBuiltins.ReprNode reprNode,
                        @Shared("castStr") @Cached CastToTruffleStringNode castStringNode,
                        @Shared("appendStr") @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Shared("toStr") @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            Object type = getClassNode.execute(inliningTarget, self);
            TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING);
            appendStringNode.execute(sb, castStringNode.execute(getAttrNode.execute(frame, type, T___NAME__)));
            appendStringNode.execute(sb, reprNode.execute(frame, self.getArgs()));
            return toStringNode.execute(sb);
        }

        protected int argsLen(VirtualFrame frame, PBaseException self, ArgsNode argsNode) {
            return ((PTuple) argsNode.executeObject(frame, self, PNone.NO_VALUE)).getSequenceStorage().length();
        }
    }

    @Builtin(name = J___STR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class StrNode extends PythonUnaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization(guards = "argsLen(frame, self, argsNode) == 0", limit = "1")
        Object strNoArgs(VirtualFrame frame, PBaseException self,
                        @Shared("argsNode") @Cached ArgsNode argsNode) {
            return T_EMPTY_STRING;
        }

        @Specialization(guards = "argsLen(frame, self, argsNode) == 1", limit = "1")
        Object strArgs1(VirtualFrame frame, PBaseException self,
                        @SuppressWarnings("unused") @Shared("argsNode") @Cached ArgsNode argsNode,
                        @Cached GetItemNode getItemNode,
                        @Cached PyObjectStrAsObjectNode strNode) {
            return strNode.execute(frame, getItemNode.execute(frame, self.getArgs(), 0));
        }

        @Specialization(guards = {"argsLen(frame, self, argsNode) > 1"}, limit = "1")
        Object strArgs(VirtualFrame frame, PBaseException self,
                        @SuppressWarnings("unused") @Shared("argsNode") @Cached ArgsNode argsNode,
                        @Cached PyObjectStrAsObjectNode strNode) {
            return strNode.execute(frame, self.getArgs());
        }

        protected int argsLen(VirtualFrame frame, PBaseException self, ArgsNode argsNode) {
            return ((PTuple) argsNode.executeObject(frame, self, PNone.NO_VALUE)).getSequenceStorage().length();
        }
    }

    @Builtin(name = J___SETSTATE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class BaseExceptionSetStateNode extends PythonBinaryBuiltinNode {
        @CompilerDirectives.ValueType
        static final class ExcState {
            private final HashingStorage dictStorage;
            private final PBaseException exception;

            ExcState(HashingStorage dictStorage, PBaseException exception) {
                this.dictStorage = dictStorage;
                this.exception = exception;
            }
        }

        @ImportStatic(PGuards.class)
        abstract static class ForEachKW extends HashingStorageForEachCallback<ExcState> {
            @Override
            public abstract ExcState execute(Frame frame, Node inliningTarget, HashingStorage storage, HashingStorageIterator it, ExcState state);

            @Specialization
            public static ExcState doIt(Frame frame, @SuppressWarnings("unused") Node inliningTarget, HashingStorage storage, HashingStorageIterator it, ExcState state,
                            @Cached PyObjectSetAttr setAttr,
                            @Cached HashingStorageIteratorKey itKey,
                            @Cached HashingStorageIteratorKeyHash itKeyHash,
                            @Cached HashingStorageGetItemWithHash getItem) {
                Object key = itKey.execute(storage, it);
                Object value = getItem.execute(frame, state.dictStorage, key, itKeyHash.execute(storage, it));
                setAttr.execute(frame, state.exception, key, value);
                return state;
            }
        }

        @Specialization
        Object setDict(VirtualFrame frame, PBaseException self, PDict state,
                        @Cached ForEachKW forEachKW,
                        @Cached HashingStorageForEach forEachNode) {
            final HashingStorage dictStorage = state.getDictStorage();
            forEachNode.execute(frame, dictStorage, forEachKW, new ExcState(dictStorage, self));
            return PNone.NONE;
        }

        @Specialization(guards = "!isDict(state)")
        Object generic(@SuppressWarnings("unused") PBaseException self, Object state) {
            if (state != PNone.NONE) {
                throw raise(TypeError, STATE_IS_NOT_A_DICT);
            }
            return PNone.NONE;
        }
    }
}
