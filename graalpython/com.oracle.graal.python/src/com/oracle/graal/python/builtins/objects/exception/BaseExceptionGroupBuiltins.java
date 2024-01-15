package com.oracle.graal.python.builtins.objects.exception;

import static com.oracle.graal.python.nodes.BuiltinNames.T_BUILTINS;
import static com.oracle.graal.python.nodes.BuiltinNames.T_EXCEPTION_GROUP;
import static com.oracle.graal.python.nodes.BuiltinNames.T_TYPE;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___MODULE__;

import java.util.ArrayList;
import java.util.List;

import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.common.EconomicMapStorage;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.runtime.object.PythonObjectSlowPathFactory;
import com.oracle.truffle.api.dsl.NodeFactory;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PBaseExceptionGroup)
public class BaseExceptionGroupBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        // TODO
        return new ArrayList<>();
    }

    @Override
    public void postInitialize(Python3Core core) {
        createExceptionGroupType(core);
    }

    private static void createExceptionGroupType(Python3Core core) {
        PythonModule builtins = core.getBuiltins();
        Object typeBuiltin = builtins.getAttribute(T_TYPE);
        PythonObjectSlowPathFactory factory = core.factory();
        PTuple bases = factory.createTuple(new Object[]{PythonBuiltinClassType.PBaseExceptionGroup, PythonBuiltinClassType.Exception});
        EconomicMapStorage dictStorage = EconomicMapStorage.create(1);
        dictStorage.putUncachedWithJavaEq(T___MODULE__, T_BUILTINS);
        PDict dict = factory.createDict(dictStorage);
        Object exceptionGroupType = CallNode.getUncached().execute(typeBuiltin, T_EXCEPTION_GROUP, bases, dict);
        builtins.setAttribute(T_EXCEPTION_GROUP, exceptionGroupType);
    }
}
