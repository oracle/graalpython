/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.nodes.BuiltinNames.T___NOTES__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___MODULE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CLASS_GETITEM__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.ArrayList;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.EconomicMapStorage;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.PyErrExceptionMatchesNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectSetAttr;
import com.oracle.graal.python.lib.PySequenceCheckNode;
import com.oracle.graal.python.lib.PyTupleCheckExactNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.builtins.TupleNodes;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.ExecutionContext.BoundaryCallContext;
import com.oracle.graal.python.runtime.IndirectCallData.BoundaryCallData;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedLoopConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;
import com.oracle.truffle.api.strings.TruffleStringBuilderUTF32;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PBaseExceptionGroup)
public class BaseExceptionGroupBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = BaseExceptionGroupBuiltinsSlotsGen.SLOTS;

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
        PythonLanguage language = core.getLanguage();
        Object typeBuiltin = builtins.getAttribute(T_TYPE);
        PTuple bases = PFactory.createTuple(language, new Object[]{PythonBuiltinClassType.PBaseExceptionGroup, PythonBuiltinClassType.Exception});
        EconomicMapStorage dictStorage = EconomicMapStorage.create(1);
        dictStorage.putUncached(T___MODULE__, T_BUILTINS);
        PDict dict = PFactory.createDict(language, dictStorage);
        Object exceptionGroupType = CallNode.executeUncached(typeBuiltin, T_EXCEPTION_GROUP, bases, dict);
        builtins.setAttribute(T_EXCEPTION_GROUP, exceptionGroupType);
    }

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = "BaseExceptionGroup.__new__", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class BaseExceptionGroupNode extends PythonTernaryBuiltinNode {
        @Override
        public abstract PBaseExceptionGroup execute(VirtualFrame frame, Object cls, Object messageObj, Object exceptionsObj);

        @Specialization
        static PBaseExceptionGroup doManaged(VirtualFrame frame, Object cls, Object messageObj, Object exceptionsObj,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape,
                        @Cached CastToTruffleStringNode castToStringNode,
                        @Cached PySequenceCheckNode sequenceCheckNode,
                        @Cached TupleNodes.ConstructTupleNode toTupleNode,
                        @Cached SequenceStorageNodes.ToArrayNode toArrayNode,
                        @Cached PyObjectGetAttr getAttr,
                        @Cached InlinedLoopConditionProfile loopConditionProfile,
                        @Cached GetClassNode getClassNode,
                        @Cached BuiltinClassProfiles.IsBuiltinClassProfile exceptionProfile,
                        @Cached BuiltinClassProfiles.IsBuiltinClassProfile baseExceptionProfile,
                        @Cached TypeNodes.IsSameTypeNode isSameTypeNode,
                        @Cached PRaiseNode raiseNode) {
            TruffleString message;
            try {
                message = castToStringNode.execute(inliningTarget, messageObj);
            } catch (CannotCastException ex) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.ARG_D_MUST_BE_S_NOT_P, "BaseExceptionGroup", 1, "str", messageObj);
            }
            if (!sequenceCheckNode.execute(inliningTarget, exceptionsObj)) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.SECOND_ARGUMENT_EXCEPTIONS_MUST_BE_A_SEQUENCE);
            }
            PTuple exceptionsTuple = toTupleNode.execute(frame, exceptionsObj);
            Object[] exceptions = toArrayNode.execute(inliningTarget, exceptionsTuple.getSequenceStorage());
            if (exceptions.length == 0) {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.SECOND_ARGUMENT_EXCEPTIONS_MUST_BE_A_NON_EMPTY_SEQUENCE);
            }
            PythonContext context = PythonContext.get(inliningTarget);
            Object exceptionGroupType = getAttr.execute(inliningTarget, context.getBuiltins(), T_EXCEPTION_GROUP);
            boolean nestedBaseExceptions = false;
            loopConditionProfile.profileCounted(inliningTarget, exceptions.length);
            for (int i = 0; loopConditionProfile.inject(inliningTarget, i < exceptions.length); i++) {
                Object exceptionType = getClassNode.execute(inliningTarget, exceptions[i]);
                if (exceptionProfile.profileClass(inliningTarget, exceptionType, PythonBuiltinClassType.Exception)) {
                    continue;
                }
                if (baseExceptionProfile.profileClass(inliningTarget, exceptionType, PythonBuiltinClassType.PBaseException)) {
                    nestedBaseExceptions = true;
                } else {
                    throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.ITEM_D_OF_SECOND_ARGUMENT_EXCEPTIONS_IS_NOT_AN_EXCEPTION, i);
                }
            }
            if (isSameTypeNode.execute(inliningTarget, cls, PythonBuiltinClassType.PBaseExceptionGroup)) {
                if (!nestedBaseExceptions) {
                    /*
                     * All nested exceptions are Exception subclasses, wrap them in an
                     * ExceptionGroup
                     */
                    cls = exceptionGroupType;
                }
            } else if (isSameTypeNode.execute(inliningTarget, cls, exceptionGroupType)) {
                if (nestedBaseExceptions) {
                    throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.CANNOT_NEST_BASE_EXCEPTIONS_IN_AN_EXCEPTION_GROUP);
                }
            } else {
                /* user-defined subclass */
                if (nestedBaseExceptions && exceptionProfile.profileClass(inliningTarget, cls, PythonBuiltinClassType.Exception)) {
                    throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.CANNOT_NEST_BASE_EXCEPTIONS_IN_N, cls);
                }
            }
            return PFactory.createBaseExceptionGroup(language, cls, getInstanceShape.execute(cls), message, exceptions, new Object[]{messageObj, exceptionsObj});
        }
    }

    @Slot(value = SlotKind.tp_str, isComplex = true)
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
            TruffleStringBuilderUTF32 builder = TruffleStringBuilder.createUTF32();
            appendStringNode.execute(builder, self.getMessage());
            appendStringNode.execute(builder, T1);
            appendIntNumberNode.execute(builder, self.getExceptions().length);
            appendStringNode.execute(builder, T2);
            if (self.getExceptions().length > 1) {
                appendCodePointNode.execute(builder, 's');
            }
            appendCodePointNode.execute(builder, ')');
            // TODO: GR-70916 recursive printing of exception groups + indentation
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
                        @Bind PythonLanguage language) {
            return PFactory.createTuple(language, self.getExceptions());
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
        Object egObj = PyObjectCallMethodObjArgs.executeUncached(orig, T_DERIVE, PFactory.createTuple(PythonLanguage.get(null), exceptions));
        if (!(egObj instanceof PBaseExceptionGroup eg)) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.DERIVE_MUST_RETURN_AN_INSTANCE_OF_BASE_EXCEPTION_GROUP);
        }
        Object tb = ExceptionNodes.GetTracebackNode.executeUncached(orig);
        if (tb instanceof PTraceback) {
            ExceptionNodes.SetTracebackNode.executeUncached(eg, tb);
        }
        Object context = ExceptionNodes.GetContextNode.executeUncached(orig);
        ExceptionNodes.SetContextNode.executeUncached(eg, context);
        Object cause = ExceptionNodes.GetCauseNode.executeUncached(orig);
        ExceptionNodes.SetCauseNode.executeUncached(eg, cause);
        Object notes = PyObjectLookupAttr.executeUncached(orig, T___NOTES__);
        if (notes != PNone.NO_VALUE) {
            if (PySequenceCheckNode.executeUncached(notes)) {
                /* Make a copy so the parts have independent notes lists. */
                PList notesCopy = ListNodes.ConstructListNode.getUncached().execute(null, notes);
                PyObjectSetAttr.executeUncached(eg, T___NOTES__, notesCopy);
            }
        }
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
                Object elem = SequenceStorageNodes.GetItemScalarNode.executeUncached(storage, i);
                if (!isExceptionTypeUncached(elem)) {
                    throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.EXPECTED_A_FUNCTION_EXCEPTION_TYPE_OR_TUPLE_OF_EXCEPTION_TYPES);
                }
            }
            return MatcherType.BY_TYPE;
        }
        throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.EXPECTED_A_FUNCTION_EXCEPTION_TYPE_OR_TUPLE_OF_EXCEPTION_TYPES);
    }

    private static boolean isExceptionTypeUncached(Object value) {
        return TypeNodes.IsTypeNode.executeUncached(value) && BuiltinClassProfiles.IsBuiltinClassProfile.profileClassSlowPath(value, PythonBuiltinClassType.PBaseException);
    }

    @TruffleBoundary
    private static boolean splitCheckMatch(Object exception, MatcherType matcherType, Object matcherValue) {
        return switch (matcherType) {
            case BY_TYPE -> PyErrExceptionMatchesNode.executeUncached(exception, matcherValue);
            case BY_PREDICATE ->
                PyObjectIsTrueNode.executeUncached(CallNode.executeUncached(matcherValue, exception));
            // TODO INSTANCE_IDS
            default -> true;
        };
    }

    private record SplitResult(Object match, Object rest) {
        static final SplitResult EMPTY = new SplitResult(null, null);
    }

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
                        @Bind Node inliningTarget,
                        @Cached("createFor($node)") BoundaryCallData boundaryCallData) {
            PythonContext context = PythonContext.get(inliningTarget);
            PythonLanguage language = context.getLanguage(inliningTarget);
            Object state = BoundaryCallContext.enter(frame, language, context, boundaryCallData);
            /*
             * TODO this could benefit from PE, but the recusions and list building make that
             * annoying to implement.
             */
            SplitResult result;
            try {
                result = doSplit(self, matcherValue, inliningTarget, true);
            } finally {
                BoundaryCallContext.exit(frame, language, context, state);
            }
            Object match = result.match != null ? result.match : PNone.NONE;
            Object rest = result.rest != null ? result.rest : PNone.NONE;
            return PFactory.createTuple(language, new Object[]{match, rest});
        }
    }

    @Builtin(name = "subgroup", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class SubgroupNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object subgroup(VirtualFrame frame, PBaseExceptionGroup self, Object matcherValue,
                        @Bind Node inliningTarget,
                        @Cached("createFor($node)") BoundaryCallData boundaryCallData) {
            PythonContext context = PythonContext.get(inliningTarget);
            PythonLanguage language = context.getLanguage(inliningTarget);
            Object state = BoundaryCallContext.enter(frame, language, context, boundaryCallData);
            /*
             * TODO this could benefit from PE, but the recusions and list building make that
             * annoying to implement.
             */
            SplitResult result;
            try {
                result = doSplit(self, matcherValue, inliningTarget, false);
            } finally {
                BoundaryCallContext.exit(frame, language, context, state);
            }
            return result.match != null ? result.match : PNone.NONE;
        }
    }

    @Builtin(name = J___CLASS_GETITEM__, minNumOfPositionalArgs = 2, isClassmethod = true)
    @GenerateNodeFactory
    abstract static class ClassGetItemNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object classGetItem(Object cls, Object key,
                        @Bind PythonLanguage language) {
            return PFactory.createGenericAlias(language, cls, key);
        }
    }
}
