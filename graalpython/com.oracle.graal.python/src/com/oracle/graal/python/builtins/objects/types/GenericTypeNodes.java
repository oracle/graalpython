/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.types;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___ARGS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___MODULE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___ORIGIN__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___PARAMETERS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___QUALNAME__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___TYPING_PREPARE_SUBST__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___TYPING_SUBST__;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetInternalObjectArrayNode;
import com.oracle.graal.python.builtins.objects.ellipsis.PEllipsis;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetObjectSlotsNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectReprAsTruffleStringNode;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.lib.PyObjectStrAsTruffleStringNode;
import com.oracle.graal.python.lib.PyObjectTypeCheck;
import com.oracle.graal.python.lib.PyUnicodeCheckNode;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.object.IsNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;

public abstract class GenericTypeNodes {

    public static final String J___TYPING_UNPACKED_TUPLE_ARGS__ = "__typing_unpacked_tuple_args__";
    public static final TruffleString T___TYPING_UNPACKED_TUPLE_ARGS__ = tsLiteral(J___TYPING_UNPACKED_TUPLE_ARGS__);

    public static final String J___TYPING_IS_UNPACKED_TYPEVARTUPLE__ = "__typing_is_unpacked_typevartuple__";
    public static final TruffleString T___TYPING_IS_UNPACKED_TYPEVARTUPLE__ = tsLiteral(J___TYPING_IS_UNPACKED_TYPEVARTUPLE__);

    @TruffleBoundary
    private static Object getItemUncached(SequenceStorage storage, int i) {
        return SequenceStorageNodes.GetItemScalarNode.executeUncached(storage, i);
    }

    static void reprItem(TruffleStringBuilder sb, Object obj) {
        PyObjectLookupAttr lookup = PyObjectLookupAttr.getUncached();
        PyObjectStrAsTruffleStringNode str = PyObjectStrAsTruffleStringNode.getUncached();
        Object origin = lookup.execute(null, null, obj, T___ORIGIN__);
        if (origin != PNone.NO_VALUE) {
            Object args = lookup.execute(null, null, obj, T___ARGS__);
            if (args != PNone.NO_VALUE) {
                // It looks like a GenericAlias
                sb.appendStringUncached(PyObjectReprAsTruffleStringNode.executeUncached(obj));
                return;
            }
        }
        Object qualname = lookup.execute(null, null, obj, T___QUALNAME__);
        if (qualname != PNone.NO_VALUE) {
            Object module = lookup.execute(null, null, obj, T___MODULE__);
            if (!(module instanceof PNone)) {
                // Looks like a class
                if (PyUnicodeCheckNode.executeUncached(module) && PyObjectRichCompareBool.executeEqUncached(module, BuiltinNames.T_BUILTINS)) {
                    // builtins don't need a module name
                    sb.appendStringUncached(str.execute(null, null, qualname));
                    return;
                } else {
                    sb.appendStringUncached(str.execute(null, null, module));
                    sb.appendCodePointUncached('.');
                    sb.appendStringUncached(str.execute(null, null, qualname));
                    return;
                }
            }
        }
        sb.appendStringUncached(PyObjectReprAsTruffleStringNode.executeUncached(obj));
    }

    // Equivalent of _Py_make_parameters
    @TruffleBoundary
    static Object[] makeParameters(PTuple args) {
        PyObjectLookupAttr lookup = PyObjectLookupAttr.getUncached();
        SequenceStorage argsStorage = args.getSequenceStorage();
        int nargs = argsStorage.length();
        List<Object> parameters = new ArrayList<>(nargs);
        for (int iarg = 0; iarg < nargs; iarg++) {
            Object t = getItemUncached(argsStorage, iarg);
            // We don't want __parameters__ descriptor of a bare Python class
            if (TypeNodes.IsTypeNode.executeUncached(t)) {
                continue;
            }
            Object subst = PyObjectLookupAttr.executeUncached(t, T___TYPING_SUBST__);

            if (subst != PNone.NO_VALUE) {
                listAdd(parameters, t);
            } else {
                Object subparams = lookup.execute(null, null, t, T___PARAMETERS__);
                if (subparams instanceof PTuple subparamsTuple) {
                    SequenceStorage subparamsStorage = subparamsTuple.getSequenceStorage();
                    for (int j = 0; j < subparamsStorage.length(); j++) {
                        listAdd(parameters, getItemUncached(subparamsStorage, j));
                    }
                }
            }
        }
        return parameters.toArray();
    }

    // Equivalent of tuple_add, but we use list
    @TruffleBoundary
    private static void listAdd(List<Object> list, Object obj) {
        if (listIndex(list, obj) < 0) {
            list.add(obj);
        }
    }

    // Equivalent of tuple_index, but we use list
    @TruffleBoundary
    private static int listIndex(List<Object> list, Object obj) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == obj) {
                return i;
            }
        }
        return -1;
    }

    @TruffleBoundary
    private static int tupleIndex(PTuple tuple, Object obj) {
        SequenceStorage storage = tuple.getSequenceStorage();
        for (int i = 0; i < storage.length(); i++) {
            if (getItemUncached(storage, i) == obj) {
                return i;
            }
        }
        return -1;
    }

    // Equivalent of tuple_extend, but we use list
    @TruffleBoundary
    private static void listExtend(List<Object> list, PTuple tuple) {
        SequenceStorage storage = tuple.getSequenceStorage();
        for (int i = 0; i < storage.length(); i++) {
            list.add(SequenceStorageNodes.GetItemScalarNode.executeUncached(storage, i));
        }
    }

    @TruffleBoundary
    private static boolean isUnpackedTypeVarTuple(Object arg) {
        if (TypeNodes.IsTypeNode.executeUncached(arg)) {
            return false;
        }
        Object result = PyObjectLookupAttr.executeUncached(arg, T___TYPING_IS_UNPACKED_TYPEVARTUPLE__);
        return PyObjectIsTrueNode.executeUncached(result);
    }

    @TruffleBoundary
    private static Object unpackedTupleArgs(Object item) {
        // Fast path
        if (item instanceof PGenericAlias alias && alias.isStarred() &&
                        TypeNodes.IsSameTypeNode.executeUncached(alias.getOrigin(), PythonBuiltinClassType.PTuple)) {
            return alias.getArgs();
        }
        Object result = PyObjectLookupAttr.executeUncached(item, T___TYPING_UNPACKED_TUPLE_ARGS__);
        if (result instanceof PNone) {
            return null;
        }
        return result;
    }

    @TruffleBoundary
    private static PTuple unpackArgs(Object item) {
        List<Object> newargs = new ArrayList<>();
        if (item instanceof PTuple tuple) {
            SequenceStorage storage = tuple.getSequenceStorage();
            for (int i = 0; i < storage.length(); i++) {
                unpackArgsInner(newargs, getItemUncached(storage, i));
            }
        } else {
            unpackArgsInner(newargs, item);
        }
        return PFactory.createTuple(PythonLanguage.get(null), newargs.toArray());
    }

    private static void unpackArgsInner(List<Object> newargs, Object item) {
        if (!TypeNodes.IsTypeNode.executeUncached(item)) {
            Object subargs = unpackedTupleArgs(item);
            if (subargs instanceof PTuple tuple) {
                SequenceStorage storage = tuple.getSequenceStorage();
                if (!(storage.length() > 0 && getItemUncached(storage, storage.length() - 1) == PEllipsis.INSTANCE)) {
                    for (int i = 0; i < storage.length(); i++) {
                        newargs.add(getItemUncached(storage, i));
                    }
                    return;
                }
            }
        }
        newargs.add(item);
    }

    // Equivalent of _Py_subs_parameters
    @TruffleBoundary
    static Object[] subsParameters(Node node, Object self, PTuple args, PTuple parameters, Object item) {
        PythonLanguage language = PythonLanguage.get(null);
        SequenceStorage paramsStorage = parameters.getSequenceStorage();
        int nparams = paramsStorage.length();
        if (nparams == 0) {
            throw PRaiseNode.raiseStatic(node, TypeError, ErrorMessages.S_IS_NOT_A_GENERIC_CLASS, PyObjectReprAsTruffleStringNode.executeUncached(self));
        }
        item = unpackArgs(item);
        for (int i = 0; i < nparams; i++) {
            Object param = getItemUncached(paramsStorage, i);
            Object prepare = PyObjectLookupAttr.executeUncached(param, T___TYPING_PREPARE_SUBST__);
            if (!(prepare instanceof PNone)) {
                Object itemarg = item instanceof PTuple ? item : PFactory.createTuple(language, new Object[]{item});
                item = CallNode.executeUncached(prepare, self, itemarg);
            }
        }

        int nitems;
        Object[] argitems;
        if (item instanceof PTuple t) {
            argitems = GetInternalObjectArrayNode.executeUncached(t.getSequenceStorage());
            nitems = t.getSequenceStorage().length();
        } else {
            argitems = new Object[]{item};
            nitems = 1;
        }
        if (nitems != nparams) {
            throw PRaiseNode.raiseStatic(node, TypeError, ErrorMessages.TOO_S_ARGUMENTS_FOR_S_ACTUAL_D_EXPECTED_D, nitems > nparams ? "many" : "few",
                            PyObjectReprAsTruffleStringNode.executeUncached(self), nitems, nparams);
        }
        SequenceStorage argsStorage = args.getSequenceStorage();
        List<Object> newargs = new ArrayList<>(argsStorage.length());
        for (int iarg = 0; iarg < argsStorage.length(); iarg++) {
            Object arg = getItemUncached(argsStorage, iarg);
            if (TypeNodes.IsTypeNode.executeUncached(arg)) {
                newargs.add(arg);
                continue;
            }
            boolean unpack = isUnpackedTypeVarTuple(arg);
            Object subst = PyObjectLookupAttr.executeUncached(arg, T___TYPING_SUBST__);
            if (subst != PNone.NO_VALUE) {
                int iparam = tupleIndex(parameters, arg);
                assert iparam >= 0;
                arg = CallNode.executeUncached(subst, argitems[iparam]);
            } else {
                arg = subsTvars(arg, parameters, argitems);
            }
            if (unpack && arg instanceof PTuple tuple /* CPython doesn't check the cast?! */) {
                listExtend(newargs, tuple);
            } else {
                newargs.add(arg);
            }
        }
        return newargs.toArray();
    }

    @TruffleBoundary
    private static Object subsTvars(Object obj, PTuple parameters, Object[] argitems) {
        Object subparams = PyObjectLookupAttr.executeUncached(obj, T___PARAMETERS__);
        if (subparams instanceof PTuple tuple && tuple.getSequenceStorage().length() > 0) {
            SequenceStorage subparamsStorage = tuple.getSequenceStorage();
            List<Object> subargs = new ArrayList<>(subparamsStorage.length());
            for (int i = 0; i < subparamsStorage.length(); i++) {
                Object arg = getItemUncached(subparamsStorage, i);
                int foundIndex = tupleIndex(parameters, arg);
                if (foundIndex >= 0) {
                    Object param = getItemUncached(parameters.getSequenceStorage(), foundIndex);
                    arg = argitems[foundIndex];
                    // TypeVarTuple
                    if (arg instanceof PTuple tuple1) {
                        if (GetObjectSlotsNode.executeUncached(param).tp_iter() != null) {
                            listExtend(subargs, tuple1);
                            continue;
                        }
                    }
                }
                subargs.add(arg);
            }
            PTuple subargsTuple = PFactory.createTuple(PythonLanguage.get(null), subargs.toArray());
            obj = PyObjectGetItem.executeUncached(obj, subargsTuple);
        }
        return obj;
    }

    @GenerateInline(false)       // footprint reduction 36 -> 20
    public abstract static class UnionTypeOrNode extends PNodeWithContext {
        public abstract Object execute(Object self, Object other);

        @Specialization(guards = {"isUnionable(inliningTarget, typeCheck, self)", "isUnionable(inliningTarget, typeCheck, other)"}, limit = "1")
        static Object union(Object self, Object other,
                        @SuppressWarnings("unused") @Bind Node inliningTarget,
                        @SuppressWarnings("unused") @Cached PyObjectTypeCheck typeCheck,
                        @Bind PythonLanguage language) {
            Object[] args = dedupAndFlattenArgs(new Object[]{self, other});
            if (args.length == 1) {
                return args[0];
            }
            assert args.length > 1;
            return PFactory.createUnionType(language, args);
        }

        @Fallback
        @SuppressWarnings("unused")
        static Object notImplemented(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        protected static boolean isUnionable(Node inliningTarget, PyObjectTypeCheck typeCheck, Object obj) {
            return obj == PNone.NONE || typeCheck.execute(inliningTarget, obj, PythonBuiltinClassType.PythonClass) || typeCheck.execute(inliningTarget, obj, PythonBuiltinClassType.PGenericAlias) ||
                            typeCheck.execute(inliningTarget, obj, PythonBuiltinClassType.PUnionType) || typeCheck.execute(inliningTarget, obj, PythonBuiltinClassType.PTypeAliasType);
        }
    }

    @TruffleBoundary
    private static Object[] dedupAndFlattenArgs(Object[] args) {
        args = flattenArgs(args);
        PyObjectRichCompareBool eq = PyObjectRichCompareBool.getUncached();
        Object[] newArgs = new Object[args.length];
        int addedItems = 0;
        for (int i = 0; i < args.length; i++) {
            boolean isDuplicate = false;
            Object iElement = args[i];
            for (int j = 0; j < addedItems; j++) {
                Object jElement = newArgs[j];
                boolean isGA = iElement instanceof PGenericAlias && jElement instanceof PGenericAlias;
                // RichCompare to also deduplicate GenericAlias types (slower)
                isDuplicate = isGA ? eq.executeEq(null, null, iElement, jElement) : IsNode.getUncached().execute(iElement, jElement);
                if (isDuplicate) {
                    break;
                }
            }
            if (!isDuplicate) {
                newArgs[addedItems++] = iElement;
            }
        }
        if (addedItems != args.length) {
            newArgs = Arrays.copyOf(newArgs, addedItems);
        }
        return newArgs;
    }

    @TruffleBoundary
    private static Object[] flattenArgs(Object[] args) {
        int totalArgs = 0;
        // Get number of total args once it's flattened.
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof PUnionType) {
                totalArgs += ((PUnionType) args[i]).getArgs().getSequenceStorage().length();
            } else {
                totalArgs++;
            }
        }
        Object[] flattenedArgs = new Object[totalArgs];
        int pos = 0;
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof PUnionType) {
                SequenceStorage storage = ((PUnionType) args[i]).getArgs().getSequenceStorage();
                for (int j = 0; j < storage.length(); j++) {
                    flattenedArgs[pos++] = getItemUncached(storage, j);
                }
            } else {
                flattenedArgs[pos++] = args[i] == PNone.NONE ? PythonBuiltinClassType.PNone : args[i];
            }
        }
        assert pos == totalArgs;
        return flattenedArgs;
    }
}
