package com.oracle.graal.python.builtins.modules;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

@CoreFunctions(defineModule = "unicodedata")
public class UnicodeDataModuleBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinNode>> getNodeFactories() {
        return UnicodeDataModuleBuiltinsFactory.getFactories();
    }

    // unicodedata.normalize(form, unistr)
    @Builtin(name = "normalize", fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class NormalizeNode extends PythonBuiltinNode {
        @Specialization
        public String normalize(String form, String unistr) {
            return unistr;
        }
    }
}
