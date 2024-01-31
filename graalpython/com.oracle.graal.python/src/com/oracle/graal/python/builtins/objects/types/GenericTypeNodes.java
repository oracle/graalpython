/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.ellipsis.PEllipsis;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
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
import com.oracle.graal.python.nodes.attributes.LookupCallableSlotInMRONode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;

public abstract class GenericTypeNodes {

    public static final String J___TYPING_SUBST__ = "__typing_subst__";
    public static final TruffleString T___TYPING_SUBST__ = tsLiteral(J___TYPING_SUBST__);

    public static final String J___TYPING_UNPACKED_TUPLE_ARGS__ = "__typing_unpacked_tuple_args__";
    public static final TruffleString T___TYPING_UNPACKED_TUPLE_ARGS__ = tsLiteral(J___TYPING_UNPACKED_TUPLE_ARGS__);

    public static final String J___TYPING_IS_UNPACKED_TYPEVARTUPLE__ = "__typing_is_unpacked_typevartuple__";
    public static final TruffleString T___TYPING_IS_UNPACKED_TYPEVARTUPLE__ = tsLiteral(J___TYPING_IS_UNPACKED_TYPEVARTUPLE__);

    public static final String J___TYPING_PREPARE_SUBST__ = "__typing_prepare_subst__";
    public static final TruffleString T___TYPING_PREPARE_SUBST__ = tsLiteral(J___TYPING_PREPARE_SUBST__);

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
                if (PyUnicodeCheckNode.executeUncached(module) && PyObjectRichCompareBool.EqNode.compareUncached(module, BuiltinNames.T_BUILTINS)) {
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
        SequenceStorage storage = args.getSequenceStorage();
        int nargs = storage.length();
        List<Object> parameters = new ArrayList<>(nargs);
        for (int iarg = 0; iarg < nargs; iarg++) {
            Object t = storage.getItemNormalized(iarg);
            // We don't want __parameters__ descriptor of a bare Python class
            if (TypeNodes.IsTypeNode.executeUncached(t)) {
                continue;
            }
            Object subst = PyObjectLookupAttr.executeUncached(t, T___TYPING_SUBST__);

            if (subst != PNone.NO_VALUE) {
                listAdd(parameters, t);
            } else {
                Object subparams = lookup.execute(null, null, t, T___PARAMETERS__);
                if (subparams instanceof PTuple) {
                    SequenceStorage storage2 = ((PTuple) subparams).getSequenceStorage();
                    for (int j = 0; j < storage2.length(); j++) {
                        listAdd(parameters, storage2.getItemNormalized(j));
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
            if (storage.getItemNormalized(i) == obj) {
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
            list.add(storage.getItemNormalized(i));
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
    private static Object[] unpackArgs(Object item) {
        List<Object> newargs = new ArrayList<>();
        if (item instanceof PTuple tuple) {
            SequenceStorage storage = tuple.getSequenceStorage();
            for (int i = 0; i < storage.length(); i++) {
                unpackArgsInner(newargs, storage.getItemNormalized(i));
            }
        } else {
            unpackArgsInner(newargs, item);
        }
        return newargs.toArray();
    }

    private static void unpackArgsInner(List<Object> newargs, Object item) {
        if (!TypeNodes.IsTypeNode.executeUncached(item)) {
            Object subargs = unpackedTupleArgs(item);
            if (subargs instanceof PTuple tuple) {
                SequenceStorage storage = tuple.getSequenceStorage();
                if (!(storage.length() > 0 && storage.getItemNormalized(storage.length() - 1) == PEllipsis.INSTANCE)) {
                    for (int i = 0; i < storage.length(); i++) {
                        newargs.add(storage.getItemNormalized(i));
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
        SequenceStorage paramsStorage = parameters.getSequenceStorage();
        int nparams = paramsStorage.length();
        if (nparams == 0) {
            throw PRaiseNode.raiseUncached(node, TypeError, ErrorMessages.S_IS_NOT_A_GENERIC_CLASS, PyObjectReprAsTruffleStringNode.executeUncached(self));
        }
        Object[] argitems = unpackArgs(item);
        for (int i = 0; i < nparams; i++) {
            Object param = paramsStorage.getItemNormalized(i);
            Object prepare = PyObjectLookupAttr.executeUncached(param, T___TYPING_PREPARE_SUBST__);
            if (!(prepare instanceof PNone)) {
                Object itemarg = item instanceof PTuple ? item : PythonObjectFactory.getUncached().createTuple(new Object[]{item});
                item = CallNode.getUncached().execute(prepare, self, itemarg);
            }
        }
        if (argitems.length != nparams) {
            throw PRaiseNode.raiseUncached(node, TypeError, ErrorMessages.TOO_S_ARGUMENTS_FOR_S_ACTUAL_D_EXPECTED_D,
                            argitems.length > nparams ? "many" : "few", PyObjectReprAsTruffleStringNode.executeUncached(self),
                            argitems.length, nparams);
        }
        SequenceStorage argsStorage = args.getSequenceStorage();
        List<Object> newargs = new ArrayList<>(argsStorage.length());
        for (int iarg = 0; iarg < argsStorage.length(); iarg++) {
            Object arg = argsStorage.getItemNormalized(iarg);
            if (TypeNodes.IsTypeNode.executeUncached(arg)) {
                newargs.add(arg);
                continue;
            }
            boolean unpack = isUnpackedTypeVarTuple(arg);
            Object subst = PyObjectLookupAttr.executeUncached(arg, T___TYPING_SUBST__);
            if (subst != PNone.NO_VALUE) {
                int iparam = tupleIndex(parameters, arg);
                assert iparam >= 0;
                arg = CallNode.getUncached().execute(subst, argitems[iparam]);
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
                Object arg = subparamsStorage.getItemNormalized(i);
                int foundIndex = tupleIndex(parameters, arg);
                if (foundIndex >= 0) {
                    Object param = arg;
                    arg = argitems[foundIndex];
                    // TypeVarTuple
                    if (arg instanceof PTuple tuple1 &&
                                    LookupCallableSlotInMRONode.getUncached(SpecialMethodSlot.Iter).execute(GetClassNode.executeUncached(param)) != PNone.NO_VALUE) {
                        listExtend(subargs, tuple1);
                    }
                }
                subargs.add(arg);
            }
            PTuple subargsTuple = PythonObjectFactory.getUncached().createTuple(subargs.toArray());
            obj = PyObjectGetItem.executeUncached(obj, subargsTuple);
        }
        return obj;
    }

    @SuppressWarnings("truffle-inlining")       // footprint reduction 36 -> 20
    public abstract static class UnionTypeOrNode extends PNodeWithContext {
        public abstract Object execute(Object self, Object other);

        @Specialization(guards = {"isUnionable(inliningTarget, typeCheck, self)", "isUnionable(inliningTarget, typeCheck, other)"}, limit = "1")
        static Object union(Object self, Object other,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached PyObjectTypeCheck typeCheck,
                        @Cached PythonObjectFactory factory) {
            Object[] args = dedupAndFlattenArgs(new Object[]{self, other});
            if (args.length == 1) {
                return args[0];
            }
            assert args.length > 1;
            return factory.createUnionType(args);
        }

        @Fallback
        @SuppressWarnings("unused")
        static Object notImplemented(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        protected static boolean isUnionable(Node inliningTarget, PyObjectTypeCheck typeCheck, Object obj) {
            return obj == PNone.NONE || typeCheck.execute(inliningTarget, obj, PythonBuiltinClassType.PythonClass) || typeCheck.execute(inliningTarget, obj, PythonBuiltinClassType.PGenericAlias) ||
                            typeCheck.execute(inliningTarget, obj, PythonBuiltinClassType.PUnionType);
        }
    }

    @TruffleBoundary
    private static Object[] dedupAndFlattenArgs(Object[] args) {
        args = flattenArgs(args);
        PyObjectRichCompareBool.EqNode eq = PyObjectRichCompareBool.EqNode.getUncached();
        Object[] newArgs = new Object[args.length];
        int addedItems = 0;
        for (int i = 0; i < args.length; i++) {
            boolean isDuplicate = false;
            Object iElement = args[i];
            for (int j = 0; j < addedItems; j++) {
                Object jElement = newArgs[j];
                boolean isGA = iElement instanceof PGenericAlias && jElement instanceof PGenericAlias;
                // RichCompare to also deduplicate GenericAlias types (slower)
                isDuplicate = isGA ? eq.compare(null, null, iElement, jElement) : IsNode.getUncached().execute(iElement, jElement);
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
                    flattenedArgs[pos++] = storage.getItemNormalized(j);
                }
            } else {
                flattenedArgs[pos++] = args[i] == PNone.NONE ? PythonBuiltinClassType.PNone : args[i];
            }
        }
        assert pos == totalArgs;
        return flattenedArgs;
    }
}
