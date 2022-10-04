package com.oracle.graal.python.builtins.objects.types;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.BuiltinNames.T_TYPE_VAR;
import static com.oracle.graal.python.nodes.BuiltinNames.T_TYPING;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___ARGS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___ORIGIN__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___MODULE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___PARAMETERS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___OR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REPR__;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.ArrayList;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.ellipsis.PEllipsis;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectReprAsTruffleStringNode;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.lib.PyUnicodeCheckNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.StringLiterals;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PGenericAlias)
public class GenericAliasBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return GenericAliasBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___ORIGIN__, numOfPositionalOnlyArgs = 1, isGetter = true)
    @GenerateNodeFactory
    static abstract class OriginNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object origin(PGenericAlias self) {
            return self.getOrigin();
        }
    }

    @Builtin(name = J___ARGS__, numOfPositionalOnlyArgs = 1, isGetter = true)
    @GenerateNodeFactory
    static abstract class ArgsNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object args(PGenericAlias self) {
            return self.getArgs();
        }
    }

    @Builtin(name = J___OR__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class OrNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object union(Object self, Object other,
                        @Cached GenericTypeNodes.UnionTypeOrNode orNode) {
            return orNode.execute(self, other);
        }
    }

    @Builtin(name = J___REPR__, numOfPositionalOnlyArgs = 1)
    @GenerateNodeFactory
    static abstract class ReprNode extends PythonUnaryBuiltinNode {
        private static final TruffleString SEPARATOR = tsLiteral(", ");

        @Specialization
        @TruffleBoundary
        Object repr(PGenericAlias self) {
            TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING);
            reprItem(sb, self.getOrigin());
            sb.appendCodePointUncached('[');
            SequenceStorage argsStorage = self.getArgs().getSequenceStorage();
            for (int i = 0; i < argsStorage.length(); i++) {
                if (i > 0) {
                    sb.appendStringUncached(SEPARATOR);
                }
                reprItem(sb, argsStorage.getItemNormalized(i));
            }
            if (argsStorage.length() == 0) {
                // for something like tuple[()] we should print a "()"
                sb.appendCodePointUncached('(');
                sb.appendCodePointUncached(')');
            }
            sb.appendCodePointUncached(']');
            return sb.toStringUncached();
        }

        // Equivalent of ga_repr_item in CPython
        private static void reprItem(TruffleStringBuilder sb, Object obj) {
            if (obj == PEllipsis.INSTANCE) {
                sb.appendStringUncached(StringLiterals.T_ELLIPSIS);
                return;
            }
            GenericTypeNodes.reprItem(sb, obj);
        }
    }

    @Builtin(name = J___GETITEM__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GetItemNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object getitem(PGenericAlias self, Object item) {
            if (self.getParameters() == null) {
                self.setParameters(factory().createTuple(makeParameters(self.getArgs())));
            }
            Object[] newargs = subsParameters(this, self, self.getArgs(), self.getParameters(), item);
            PTuple newargsTuple = factory().createTuple(newargs);
            return factory().createGenericAlias(self.getOrigin(), newargsTuple);
        }
    }

    // Equivalent of _Py_make_parameters
    @TruffleBoundary
    private static Object[] makeParameters(PTuple args) {
        PyObjectLookupAttr lookup = PyObjectLookupAttr.getUncached();
        SequenceStorage storage = args.getSequenceStorage();
        int nargs = storage.length();
        List<Object> parameters = new ArrayList<>(nargs);
        for (int iarg = 0; iarg < nargs; iarg++) {
            Object t = storage.getItemNormalized(iarg);
            if (isTypeVar(t)) {
                addUnique(parameters, t);
            }
            Object subparams = lookup.execute(null, t, T___PARAMETERS__);
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
    private static boolean isTypeVar(Object obj) {
        // isinstance(obj, TypeVar) without importing typing.py.
        Object type = GetClassNode.getUncached().execute(obj);
        TruffleString typeName = TypeNodes.GetNameNode.getUncached().execute(type);
        if (T_TYPE_VAR.equalsUncached(typeName, TS_ENCODING)) {
            Object module = PyObjectLookupAttr.getUncached().execute(null, type, T___MODULE__);
            return PyUnicodeCheckNode.getUncached().execute(module) && PyObjectRichCompareBool.EqNode.getUncached().execute(null, module, T_TYPING);
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
    private static Object[] subsParameters(Node node, PGenericAlias self, PTuple args, PTuple parameters, Object item) {
        PyObjectReprAsTruffleStringNode repr = PyObjectReprAsTruffleStringNode.getUncached();
        SequenceStorage paramsStorage = parameters.getSequenceStorage();
        int nparams = paramsStorage.length();
        if (nparams == 0) {
            throw PRaiseNode.raiseUncached(node, TypeError, ErrorMessages.THERE_ARE_NO_TYPE_VARIABLES_LEFT_IN_S, repr.execute(null, self));
        }
        Object[] argitems = item instanceof PTuple ? ((PTuple) item).getSequenceStorage().getCopyOfInternalArray() : new Object[]{item};
        if (argitems.length != nparams) {
            throw PRaiseNode.raiseUncached(node, TypeError, ErrorMessages.TOO_S_ARGUMENTS_FOR_S, argitems.length > nparams ? "many" : "few", repr.execute(null, self));
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
        PyObjectLookupAttr lookup = PyObjectLookupAttr.getUncached();
        Object subparams = lookup.execute(null, obj, T___PARAMETERS__);
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
                            break;
                        }
                        subargs[i] = arg;
                    }
                }
                PTuple subargsTuple = PythonObjectFactory.getUncached().createTuple(subargs);
                obj = PyObjectGetItem.getUncached().execute(null, obj, subargsTuple);
            }
        }
        return obj;
    }
}
