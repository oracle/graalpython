package com.oracle.graal.python.builtins.objects.contextvars;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

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
            return getContextVar(self, key, null, raise);
        }
    }

    @Builtin(name = "run", takesVarArgs = true, takesVarKeywordArgs = true, minNumOfPositionalArgs = 2, parameterNames = {"$self", "..."})
    @GenerateNodeFactory
    public abstract static class Run extends PythonBuiltinNode {
        @Specialization
        Object get(VirtualFrame frame, PContext self, Object fun, Object[] args, PKeyword[] keywords, @Cached CallNode call) {
            PythonContext.PythonThreadState threadState = getContext().getThreadState(getLanguage());
            self.enter(threadState);
            try {
                return call.execute(frame, fun, args, keywords);
            } finally {
                self.leave(threadState);
            }
        }
    }

    @Builtin(name = "copy", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class Copy extends PythonUnaryBuiltinNode {
        @Specialization
        Object doCopy(PContext self) {
            PContext ret = factory().createContextVarsContext();
            ret.contextVarValues = self.contextVarValues;
            return ret;
        }
    }

    @Builtin(name = "get", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class GetMethod extends PythonBuiltinNode {
        @Specialization(guards = "isNoValue(def)")
        Object doGet(PContext self, Object key, Object def, @Cached PRaiseNode raise) {
            return doGetDefault(self, key, PNone.NONE, raise);
        }

        @Specialization(guards = "!isNoValue(def)")
        Object doGetDefault(PContext self, Object key, Object def, @Cached PRaiseNode raise) {
            return getContextVar(self, key, def, raise);
        }

    }

    private static Object getContextVar(PContext self, Object key, Object def, @Cached PRaiseNode raise) {
        if (key instanceof PContextVar) {
            PContextVar ctxVar = (PContextVar) key;
            Object value = self.contextVarValues.lookup(key, ctxVar.getHash());
            if (value == null) {
                if (def == null) {
                    throw raise.raise(PythonBuiltinClassType.KeyError, ErrorMessages.S, key);
                } else {
                    return def;
                }
            } else {
                return value;
            }
        } else {
            throw raise.raise(PythonBuiltinClassType.TypeError, ErrorMessages.CONTEXTVAR_KEY_EXPECTED, key);
        }
    }
}
