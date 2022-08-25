package com.oracle.graal.python.builtins.objects.contextvars;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

import java.util.List;

@CoreFunctions(extendClasses = PythonBuiltinClassType.ContextVarsContext)
public class ContextBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ContextBuiltinsFactory.getFactories();
    }

    @Builtin(name = "__getitem__", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class GetContextVar extends PythonBinaryBuiltinNode {
        @Specialization
        Object get(PContext self, Object key, @Cached PRaiseNode raise) {
            if (key instanceof PContextVar) {
                PContextVar ctxVar = (PContextVar) key;
                Object value = self.contextVarValues.lookup(key, ctxVar.getHash());
                if (value == null) {
                    throw raise.raise(PythonBuiltinClassType.KeyError, ErrorMessages.S, key);
                } else {
                    return value;
                }
            } else {
                throw raise.raise(PythonBuiltinClassType.TypeError, ErrorMessages.CONTEXTVAR_KEY_EXPECTED, key);
            }
        }
    }
}
