/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.exception;

import static com.oracle.graal.python.nodes.BuiltinNames.T_BUILTINS;
import static com.oracle.graal.python.nodes.BuiltinNames.T_EXCEPTION_GROUP;
import static com.oracle.graal.python.nodes.BuiltinNames.T_TYPE;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___MODULE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CLASS_GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___STR__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.ArrayList;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.EconomicMapStorage;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.PyErrExceptionMatchesNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.lib.PyTupleCheckExactNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.object.PythonObjectSlowPathFactory;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PBaseExceptionGroup)
public class BaseExceptionGroupBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return BaseExceptionGroupBuiltinsFactory.getFactories();
    }

    @Override
    public void postInitialize(Python3Core core) {
        createExceptionGroupType(core);
    }

    private static void createExceptionGroupType(Python3Core core) {
        PythonModule builtins = core.getBuiltins();
        Object typeBuiltin = builtins.getAttribute(T_TYPE);
        PythonObjectSlowPathFactory factory = core.factory();
        PTuple bases = factory.createTuple(new Object[]{PythonBuiltinClassType.PBaseExceptionGroup, PythonBuiltinClassType.Exception});
        EconomicMapStorage dictStorage = EconomicMapStorage.create(1);
        dictStorage.putUncachedWithJavaEq(T___MODULE__, T_BUILTINS);
        PDict dict = factory.createDict(dictStorage);
        Object exceptionGroupType = CallNode.getUncached().execute(typeBuiltin, T_EXCEPTION_GROUP, bases, dict);
        builtins.setAttribute(T_EXCEPTION_GROUP, exceptionGroupType);
    }

    @Builtin(name = J___STR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class StrNode extends PythonUnaryBuiltinNode {

        private static final TruffleString T1 = tsLiteral(" (");
        private static final TruffleString T2 = tsLiteral(" sub-exception");

        @Specialization
        static TruffleString str(PBaseExceptionGroup self,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached TruffleStringBuilder.AppendCodePointNode appendCodePointNode,
                        @Cached TruffleStringBuilder.AppendIntNumberNode appendIntNumberNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            TruffleStringBuilder builder = TruffleStringBuilder.create(TS_ENCODING);
            appendStringNode.execute(builder, self.getMessage());
            appendStringNode.execute(builder, T1);
            appendIntNumberNode.execute(builder, self.getExceptions().length);
            appendStringNode.execute(builder, T2);
            if (self.getExceptions().length > 1) {
                appendCodePointNode.execute(builder, 's');
            }
            appendCodePointNode.execute(builder, ')');
            return toStringNode.execute(builder);
        }
    }

    @Builtin(name = "message", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class MessageNode extends PythonUnaryBuiltinNode {
        @Specialization
        static TruffleString message(PBaseExceptionGroup self) {
            return self.getMessage();
        }
    }

    @Builtin(name = "exceptions", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class ExceptionsNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object exceptions(PBaseExceptionGroup self,
                        @Cached PythonObjectFactory factory) {
            return factory.createTuple(self.getExceptions());
        }
    }

    @Builtin(name = "derive", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class DeriveNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object derive(VirtualFrame frame, PBaseExceptionGroup self, Object exceptions,
                        @Cached CallNode callNode) {
            return callNode.execute(frame, PythonBuiltinClassType.PBaseExceptionGroup, self.getMessage(), exceptions);
        }
    }

    private static final TruffleString T_DERIVE = tsLiteral("derive");

    @TruffleBoundary
    private static PBaseExceptionGroup subset(Node inliningTarget, PBaseExceptionGroup orig, Object[] exceptions) {
        if (exceptions.length == 0) {
            return null;
        }
        PythonObjectSlowPathFactory factory = PythonContext.get(inliningTarget).factory();
        Object egObj = PyObjectCallMethodObjArgs.executeUncached(orig, T_DERIVE, factory.createTuple(exceptions));
        if (!(egObj instanceof PBaseExceptionGroup eg)) {
            throw PRaiseNode.raiseUncached(inliningTarget, TypeError, ErrorMessages.DERIVE_MUST_RETURN_AN_INSTANCE_OF_BASE_EXCEPTION_GROUP);
        }
        Object tb = ExceptionNodes.GetTracebackNode.executeUncached(orig);
        if (tb instanceof PTraceback) {
            ExceptionNodes.SetTracebackNode.executeUncached(eg, tb);
        }
        Object context = ExceptionNodes.GetContextNode.executeUncached(orig);
        ExceptionNodes.SetContextNode.executeUncached(eg, context);
        Object cause = ExceptionNodes.GetCauseNode.executeUncached(orig);
        ExceptionNodes.SetCauseNode.executeUncached(eg, cause);
        // TODO copy notes
        return eg;
    }

    private enum MatcherType {
        BY_TYPE,
        BY_PREDICATE,
        INSTANCE_IDS
    }

    @TruffleBoundary
    private static MatcherType getMatcherType(Node inliningTarget, Object value) {
        // CPython really does PyFunction_Check(value). One would expect a callable check...
        if (value instanceof PFunction) {
            return MatcherType.BY_PREDICATE;
        }
        if (isExceptionTypeUncached(value)) {
            return MatcherType.BY_TYPE;
        }
        if (value instanceof PTuple tuple && PyTupleCheckExactNode.executeUncached(tuple)) {
            SequenceStorage storage = tuple.getSequenceStorage();
            for (int i = 0; i < storage.length(); i++) {
                Object elem = storage.getItemNormalized(i);
                if (!isExceptionTypeUncached(elem)) {
                    throw PRaiseNode.raiseUncached(inliningTarget, TypeError, ErrorMessages.EXPECTED_A_FUNCTION_EXCEPTION_TYPE_OR_TUPLE_OF_EXCEPTION_TYPES);
                }
            }
            return MatcherType.BY_TYPE;
        }
        throw PRaiseNode.raiseUncached(inliningTarget, TypeError, ErrorMessages.EXPECTED_A_FUNCTION_EXCEPTION_TYPE_OR_TUPLE_OF_EXCEPTION_TYPES);
    }

    private static boolean isExceptionTypeUncached(Object value) {
        return TypeNodes.IsTypeNode.executeUncached(value) && BuiltinClassProfiles.IsBuiltinClassProfile.profileClassSlowPath(value, PythonBuiltinClassType.PBaseException);
    }

    @TruffleBoundary
    private static boolean splitCheckMatch(Object exception, MatcherType matcherType, Object matcherValue) {
        return switch (matcherType) {
            case BY_TYPE -> PyErrExceptionMatchesNode.executeUncached(exception, matcherValue);
            case BY_PREDICATE ->
                PyObjectIsTrueNode.executeUncached(CallNode.getUncached().execute(matcherValue, exception));
            // TODO INSTANCE_IDS
            default -> true;
        };
    }

    private record SplitResult(Object match, Object rest) {
        static final SplitResult EMPTY = new SplitResult(null, null);
    };

    @TruffleBoundary
    private static SplitResult splitRecursive(Node inliningTarget, Object exception, MatcherType matcherType, Object matcherValue, boolean constructRest) {
        boolean isMatch = splitCheckMatch(exception, matcherType, matcherValue);
        if (isMatch) {
            /* Full match */
            return new SplitResult(exception, null);
        }
        if (!(exception instanceof PBaseExceptionGroup eg)) {
            /* Leaf exception and no match */
            if (constructRest) {
                return new SplitResult(null, exception);
            } else {
                return SplitResult.EMPTY;
            }
        }
        List<Object> matches = new ArrayList<>();
        List<Object> rest = null;
        if (constructRest) {
            rest = new ArrayList<>();
        }
        /* Partial match */
        for (Object e : eg.getExceptions()) {
            SplitResult result = splitRecursive(inliningTarget, e, matcherType, matcherValue, constructRest);
            if (result.match != null) {
                matches.add(result.match);
            }
            if (constructRest && result.rest != null) {
                rest.add(result.rest);
            }
        }

        PBaseExceptionGroup matchGroup = subset(inliningTarget, eg, matches.toArray());
        PBaseExceptionGroup restGroup = null;
        if (constructRest) {
            restGroup = subset(inliningTarget, eg, rest.toArray());
        }
        return new SplitResult(matchGroup, restGroup);
    }

    @TruffleBoundary
    private static SplitResult doSplit(PBaseExceptionGroup self, Object matcherValue, Node inliningTarget, boolean constructRest) {
        MatcherType matcherType = getMatcherType(inliningTarget, matcherValue);
        return splitRecursive(inliningTarget, self, matcherType, matcherValue, constructRest);
    }

    @Builtin(name = "split", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class SplitNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object split(VirtualFrame frame, PBaseExceptionGroup self, Object matcherValue,
                        @Bind("this") Node inliningTarget,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @Cached PythonObjectFactory factory) {
            PythonLanguage language = PythonLanguage.get(inliningTarget);
            PythonContext context = PythonContext.get(inliningTarget);
            Object state = IndirectCallContext.enter(frame, language, context, indirectCallData);
            /*
             * TODO this could benefit from PE, but the recusions and list building make that
             * annoying to implement.
             */
            SplitResult result;
            try {
                result = doSplit(self, matcherValue, inliningTarget, true);
            } finally {
                IndirectCallContext.exit(frame, language, context, state);
            }
            Object match = result.match != null ? result.match : PNone.NONE;
            Object rest = result.rest != null ? result.rest : PNone.NONE;
            return factory.createTuple(new Object[]{match, rest});
        }
    }

    @Builtin(name = "subgroup", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class SubgroupNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object subgroup(VirtualFrame frame, PBaseExceptionGroup self, Object matcherValue,
                        @Bind("this") Node inliningTarget,
                        @Cached("createFor(this)") IndirectCallData indirectCallData) {
            PythonLanguage language = PythonLanguage.get(inliningTarget);
            PythonContext context = PythonContext.get(inliningTarget);
            Object state = IndirectCallContext.enter(frame, language, context, indirectCallData);
            /*
             * TODO this could benefit from PE, but the recusions and list building make that
             * annoying to implement.
             */
            SplitResult result;
            try {
                result = doSplit(self, matcherValue, inliningTarget, false);
            } finally {
                IndirectCallContext.exit(frame, language, context, state);
            }
            return result.match != null ? result.match : PNone.NONE;
        }
    }

    @Builtin(name = J___CLASS_GETITEM__, minNumOfPositionalArgs = 2, isClassmethod = true)
    @GenerateNodeFactory
    abstract static class ClassGetItemNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object classGetItem(Object cls, Object key,
                        @Cached PythonObjectFactory factory) {
            return factory.createGenericAlias(cls, key);
        }
    }
}
