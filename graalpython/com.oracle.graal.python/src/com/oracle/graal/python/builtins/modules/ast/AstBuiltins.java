package com.oracle.graal.python.builtins.modules.ast;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__DIR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INIT__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

@CoreFunctions(extendClasses = PythonBuiltinClassType.AST)
public class AstBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return AstBuiltinsFactory.getFactories();
    }

    @Builtin(name = __INIT__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    abstract static class InitNode extends PythonBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        static Object init(Object self) {
            return PNone.NONE;
        }
    }

    @Builtin(name = __DIR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class DirNode extends PythonUnaryBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        Object dir(Object self) {
            return factory().createList();
        }
    }
}
