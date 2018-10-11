package com.oracle.graal.python.test.advance;

import java.util.ArrayList;
import java.util.List;

import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.truffle.api.dsl.NodeFactory;

@CoreFunctions(defineModule = "CustomModule")
public class CustomModule extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return new ArrayList<>();
    }

    @Override
    public void initialize(PythonCore core) {
        super.initialize(core);
        this.builtinConstants.put("success", "success");
    }
}