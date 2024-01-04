package com.oracle.graal.python.builtins.modules.pickle;

import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItem;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

@CoreFunctions(extendClasses = PythonBuiltinClassType.UnpicklerMemoProxy)
public class UnpicklerMemoProxyBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return UnpicklerMemoProxyBuiltinsFactory.getFactories();
    }

    @Builtin(name = "clear", minNumOfPositionalArgs = 1, parameterNames = {"$self"})
    @GenerateNodeFactory
    public abstract static class UnpicklerMemoProxyClearNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object clear(PUnpicklerMemoProxy self) {
            self.getUnpickler().clearMemo();
            return PNone.NONE;
        }
    }

    @Builtin(name = "copy", minNumOfPositionalArgs = 1, parameterNames = {"$self"})
    @GenerateNodeFactory
    public abstract static class UnpicklerMemoProxyCopyNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object copy(PUnpicklerMemoProxy self,
                        @Bind("this") Node inliningTarget,
                        @Cached HashingStorageSetItem setItem,
                        @Cached PythonObjectFactory factory) {
            return factory.createDict(self.getUnpickler().copyMemoToHashingStorage(inliningTarget, setItem));
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1, parameterNames = {"$self"})
    @GenerateNodeFactory
    public abstract static class UnpicklerMemoProxyReduceNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object reduce(PUnpicklerMemoProxy self,
                        @Bind("this") Node inliningTarget,
                        @Cached HashingStorageSetItem setItem,
                        @Cached PythonObjectFactory factory) {
            final PDict dictMemoCopy = factory.createDict(self.getUnpickler().copyMemoToHashingStorage(inliningTarget, setItem));
            final PTuple constructorArgs = factory.createTuple(new Object[]{dictMemoCopy});
            return factory.createTuple(new Object[]{PythonBuiltinClassType.PDict, constructorArgs});
        }
    }
}
