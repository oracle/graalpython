package com.oracle.graal.python.builtins.objects.contextvars;

import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEXT__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

@CoreFunctions(extendClasses = PythonBuiltinClassType.ContextIterator)
public class ContextIteratorBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ContextIteratorBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___ITER__, declaresExplicitSelf = true, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class Iter extends PythonUnaryBuiltinNode {
        @Specialization
        static Object iter(PContextIterator self) {
            return self;
        }
    }

    @Builtin(name = J___NEXT__, declaresExplicitSelf = true, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class Next extends PythonUnaryBuiltinNode {
        @Specialization
        Object next(PContextIterator self) {
            Object next = self.next(factory());
            if (next == null) {
                throw raiseStopIteration();
            } else {
                return next;
            }
        }
    }
}
