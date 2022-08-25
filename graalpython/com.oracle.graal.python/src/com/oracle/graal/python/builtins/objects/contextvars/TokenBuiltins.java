package com.oracle.graal.python.builtins.objects.contextvars;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

@CoreFunctions(extendClasses = PythonBuiltinClassType.ContextVarsToken)
public class TokenBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return TokenBuiltinsFactory.getFactories();
    }

    @Builtin(name = "var", isGetter = true, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class VarAttr extends PythonBuiltinNode {
        @Specialization
        public Object doVar(PToken self) {
            return self.getVar();
        }
    }

    @Builtin(name = "old_value", isGetter = true, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class OldValueAttr extends PythonBuiltinNode {
        @Specialization
        public Object doOld(PToken self) {
            return self.getOldValue();
        }
    }
}
