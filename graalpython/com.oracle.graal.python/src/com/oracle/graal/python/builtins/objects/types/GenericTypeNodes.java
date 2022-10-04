package com.oracle.graal.python.builtins.objects.types;

import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___ARGS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___MODULE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___ORIGIN__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___QUALNAME__;

import java.util.Arrays;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectReprAsTruffleStringNode;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.lib.PyObjectStrAsTruffleStringNode;
import com.oracle.graal.python.lib.PyObjectTypeCheck;
import com.oracle.graal.python.lib.PyUnicodeCheckNode;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.object.IsNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.strings.TruffleStringBuilder;

public abstract class GenericTypeNodes {
    static void reprItem(TruffleStringBuilder sb, Object obj) {
        PyObjectLookupAttr lookup = PyObjectLookupAttr.getUncached();
        PyObjectReprAsTruffleStringNode repr = PyObjectReprAsTruffleStringNode.getUncached();
        PyObjectStrAsTruffleStringNode str = PyObjectStrAsTruffleStringNode.getUncached();
        PyUnicodeCheckNode unicodeCheck = PyUnicodeCheckNode.getUncached();
        PyObjectRichCompareBool.EqNode eq = PyObjectRichCompareBool.EqNode.getUncached();
        Object origin = lookup.execute(null, obj, T___ORIGIN__);
        if (origin != PNone.NO_VALUE) {
            Object args = lookup.execute(null, obj, T___ARGS__);
            if (args != PNone.NO_VALUE) {
                // It looks like a GenericAlias
                sb.appendStringUncached(repr.execute(null, obj));
                return;
            }
        }
        Object qualname = lookup.execute(null, obj, T___QUALNAME__);
        if (qualname != PNone.NO_VALUE) {
            Object module = lookup.execute(null, obj, T___MODULE__);
            if (!(module instanceof PNone)) {
                // Looks like a class
                if (unicodeCheck.execute(module) && eq.execute(null, module, BuiltinNames.T_BUILTINS)) {
                    // builtins don't need a module name
                    sb.appendStringUncached(str.execute(null, qualname));
                    return;
                } else {
                    sb.appendStringUncached(str.execute(null, module));
                    sb.appendCodePointUncached('.');
                    sb.appendStringUncached(str.execute(null, qualname));
                    return;
                }
            }
        }
        sb.appendStringUncached(repr.execute(null, obj));
    }

    public static abstract class UnionTypeOrNode extends PNodeWithContext {
        public abstract Object execute(Object self, Object other);

        @Specialization(guards = {"isUnionable(typeCheck, self)", "isUnionable(typeCheck, other)"}, limit = "1")
        static Object union(Object self, Object other,
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

        protected static boolean isUnionable(PyObjectTypeCheck typeCheck, Object obj) {
            return typeCheck.execute(obj, PythonBuiltinClassType.PythonClass) || typeCheck.execute(obj, PythonBuiltinClassType.PGenericAlias) ||
                            typeCheck.execute(obj, PythonBuiltinClassType.PUnionType);
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
                isDuplicate = isGA ? eq.execute(null, iElement, jElement) : IsNode.getUncached().execute(iElement, jElement);
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
