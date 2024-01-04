package com.oracle.graal.python.builtins.modules.pickle;

import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;

import java.util.LinkedHashMap;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.pickle.MemoTable.MemoIterator;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PicklerMemoProxy)
public class PicklerMemoProxyBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PicklerMemoProxyBuiltinsFactory.getFactories();
    }

    @Builtin(name = "clear", minNumOfPositionalArgs = 1, parameterNames = {"$self"})
    @GenerateNodeFactory
    public abstract static class PicklerMemoProxyClearNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object clear(PPicklerMemoProxy self) {
            final MemoTable memoTable = self.getPickler().getMemo();
            memoTable.clear();
            return PNone.NONE;
        }
    }

    @TruffleBoundary
    public static PDict picklerMemoCopyImpl(PythonContext context, MemoTable memoTable) {
        PythonObjectFactory factory = context.factory();
        LinkedHashMap<Object, Object> copy = new LinkedHashMap<>();
        MemoIterator iterator = memoTable.iterator();
        while (iterator.advance()) {
            copy.put(System.identityHashCode(iterator.key()),
                            factory.createTuple(new Object[]{iterator.value(), iterator.key()}));

        }
        return factory.createDictFromMapGeneric(copy);
    }

    @Builtin(name = "copy", minNumOfPositionalArgs = 1, parameterNames = {"$self"})
    @GenerateNodeFactory
    public abstract static class PicklerMemoProxyCopyNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object copy(PPicklerMemoProxy self) {
            final MemoTable memoTable = self.getPickler().getMemo();
            return picklerMemoCopyImpl(getContext(), memoTable);
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1, parameterNames = {"$self"})
    @GenerateNodeFactory
    public abstract static class PicklerMemoProxyReduceNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object reduce(PPicklerMemoProxy self,
                        @Cached PythonObjectFactory factory) {
            final MemoTable memoTable = self.getPickler().getMemo();
            final PDict dictMemoCopy = picklerMemoCopyImpl(getContext(), memoTable);
            final PTuple dictArgs = factory.createTuple(new Object[]{dictMemoCopy});
            return factory.createTuple(new Object[]{PythonBuiltinClassType.PDict, dictArgs});
        }
    }
}
