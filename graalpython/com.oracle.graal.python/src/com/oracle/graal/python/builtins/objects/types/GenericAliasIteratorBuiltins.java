package com.oracle.graal.python.builtins.objects.types;

import static com.oracle.graal.python.nodes.BuiltinNames.T_ITER;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEXT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PGenericAliasIterator)
public class GenericAliasIteratorBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return GenericAliasIteratorBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___NEXT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class NextNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object next(PGenericAliasIterator self,
                        @Cached PRaiseNode raiseNode,
                        @Cached PythonObjectFactory factory) {
            if (self.isExhausted()) {
                throw raiseNode.raise(PythonBuiltinClassType.StopIteration);
            }
            PGenericAlias alias = self.getObj();
            PGenericAlias starredAlias = factory.createGenericAlias(alias.getOrigin(), alias.getArgs(), true);
            self.markExhausted();
            return starredAlias;
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object reduce(VirtualFrame frame, PGenericAliasIterator self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetAttr getAttr,
                        @Cached PythonObjectFactory factory) {
            PythonModule builtins = PythonContext.get(inliningTarget).getBuiltins();
            Object iter = getAttr.execute(frame, inliningTarget, builtins, T_ITER);
            PTuple args;
            if (!self.isExhausted()) {
                args = factory.createTuple(new Object[]{self.getObj()});
            } else {
                args = factory.createEmptyTuple();
            }
            return factory.createTuple(new Object[]{iter, args});
        }
    }
}
