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
import static com.oracle.graal.python.nodes.BuiltinNames.T_TYPE_VAR;
import static com.oracle.graal.python.nodes.BuiltinNames.T_TYPING;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___ARGS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___MODULE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___ORIGIN__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___PARAMETERS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___QUALNAME__;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.PyObjectGetItem;
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
            if (isTypeVar(t)) {
                addUnique(parameters, t);
            }
            Object subparams = lookup.execute(null, null, t, T___PARAMETERS__);
            if (subparams instanceof PTuple) {
                SequenceStorage storage2 = ((PTuple) subparams).getSequenceStorage();
                for (int j = 0; j < storage2.length(); j++) {
                    addUnique(parameters, storage2.getItemNormalized(j));
                }
            }
        }
        return parameters.toArray();
    }

    // Equivalent of is_typevar
    @TruffleBoundary
    static boolean isTypeVar(Object obj) {
        // isinstance(obj, TypeVar) without importing typing.py.
        Object type = GetClassNode.executeUncached(obj);
        TruffleString typeName = TypeNodes.GetNameNode.executeUncached(type);
        if (T_TYPE_VAR.equalsUncached(typeName, TS_ENCODING)) {
            Object module = PyObjectLookupAttr.executeUncached(type, T___MODULE__);
            return PyUnicodeCheckNode.executeUncached(module) && PyObjectRichCompareBool.EqNode.compareUncached(module, T_TYPING);
        }
        return false;
    }

    // Rough equivalent of tuple_add
    @TruffleBoundary
    private static void addUnique(List<Object> list, Object obj) {
        if (indexOf(list, obj) < 0) {
            list.add(obj);
        }
    }

    @TruffleBoundary
    private static int indexOf(List<Object> list, Object obj) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == obj) {
                return i;
            }
        }
        return -1;
    }

    // Equivalent of _Py_subs_parameters
    @TruffleBoundary
    static Object[] subsParameters(Node node, Object self, PTuple args, PTuple parameters, Object item) {
        SequenceStorage paramsStorage = parameters.getSequenceStorage();
        int nparams = paramsStorage.length();
        if (nparams == 0) {
            throw PRaiseNode.raiseUncached(node, TypeError, ErrorMessages.THERE_ARE_NO_TYPE_VARIABLES_LEFT_IN_S, PyObjectReprAsTruffleStringNode.executeUncached(self));
        }
        Object[] argitems = item instanceof PTuple ? ((PTuple) item).getSequenceStorage().getCopyOfInternalArray() : new Object[]{item};
        if (argitems.length != nparams) {
            throw PRaiseNode.raiseUncached(node, TypeError, ErrorMessages.TOO_S_ARGUMENTS_FOR_S, argitems.length > nparams ? "many" : "few", PyObjectReprAsTruffleStringNode.executeUncached(self));
        }
        SequenceStorage argsStorage = args.getSequenceStorage();
        Object[] newargs = new Object[argsStorage.length()];
        for (int iarg = 0; iarg < argsStorage.length(); iarg++) {
            Object arg = argsStorage.getItemNormalized(iarg);
            if (isTypeVar(arg)) {
                for (int iparam = 0; iparam < nparams; iparam++) {
                    if (paramsStorage.getItemNormalized(iparam) == arg) {
                        arg = argitems[iparam];
                        break;
                    }
                }
            } else {
                arg = subsTvars(arg, paramsStorage, argitems);
            }
            newargs[iarg] = arg;
        }
        return newargs;
    }

    private static Object subsTvars(Object obj, SequenceStorage parameters, Object[] argitems) {
        Object subparams = PyObjectLookupAttr.executeUncached(obj, T___PARAMETERS__);
        if (subparams instanceof PTuple) {
            SequenceStorage subparamsStorage = ((PTuple) subparams).getSequenceStorage();
            int nparams = parameters.length();
            int nsubargs = subparamsStorage.length();
            if (nsubargs > 0) {
                Object[] subargs = new Object[nsubargs];
                for (int i = 0; i < nsubargs; i++) {
                    Object arg = subparamsStorage.getItemNormalized(i);
                    for (int iparam = 0; iparam < nparams; iparam++) {
                        if (parameters.getItemNormalized(iparam) == arg) {
                            arg = argitems[iparam];
                            subargs[i] = arg;
                            break;
                        }
                    }
                }
                PTuple subargsTuple = PythonObjectFactory.getUncached().createTuple(subargs);
                obj = PyObjectGetItem.executeUncached(obj, subargsTuple);
            }
        }
        return obj;
    }

    @SuppressWarnings("truffle-inlining")       // footprint reduction 36 -> 20
    public abstract static class UnionTypeOrNode extends PNodeWithContext {
        public abstract Object execute(Object self, Object other);

        @Specialization(guards = {"isUnionable(inliningTarget, typeCheck, self)", "isUnionable(inliningTarget, typeCheck, other)"}, limit = "1")
        static Object union(Object self, Object other,
                        @Bind("this") Node inliningTarget,
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
