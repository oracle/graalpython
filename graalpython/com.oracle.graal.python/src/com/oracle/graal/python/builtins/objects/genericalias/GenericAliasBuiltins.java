package com.oracle.graal.python.builtins.objects.genericalias;

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

@CoreFunctions(extendClasses = PythonBuiltinClassType.PGenericAlias)
public class GenericAliasBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return GenericAliasBuiltinsFactory.getFactories();
    }

    @Builtin(name = "__origin__", numOfPositionalOnlyArgs = 1, isGetter = true)
    @GenerateNodeFactory
    static abstract class OriginNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object origin(PGenericAlias self) {
            return self.getOrigin();
        }
    }

    @Builtin(name = "__args__", numOfPositionalOnlyArgs = 1, isGetter = true)
    @GenerateNodeFactory
    static abstract class ArgsNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object args(PGenericAlias self) {
            return self.getArgs();
        }
    }
}
