/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */
package com.oracle.graal.python.builtins.objects.cpyobject;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

@CoreFunctions(extendClasses = PBytes.class)
public class PyObjectByteBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinNode>> getNodeFactories() {
        return PyObjectByteBuiltinsFactory.getFactories();
    }

    @Builtin(name = "ob_sval", fixedNumOfArguments = 1, isGetter = true)
    @GenerateNodeFactory
    static abstract class ObSval extends PythonUnaryBuiltinNode {

        @Specialization
        Object run(PBytes object) {
            return object.getInternalByteArray();
        }
    }
}
