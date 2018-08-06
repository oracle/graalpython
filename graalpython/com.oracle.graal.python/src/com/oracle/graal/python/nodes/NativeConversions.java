package com.oracle.graal.python.nodes;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.FromNativeSubclassNode;
import com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols;

public final class NativeConversions {
    public static FromNativeSubclassNode<Double> nativeFloat() {
        return FromNativeSubclassNode.create(PythonBuiltinClassType.PFloat, NativeCAPISymbols.FUN_PY_FLOAT_AS_DOUBLE);
    }
}
