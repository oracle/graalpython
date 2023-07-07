package com.oracle.graal.python.lib;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

@GenerateInline
@GenerateCached(false)
@GenerateUncached
public abstract class PyFloatCheckNode extends Node {
    public abstract boolean execute(Node inliningTarget, Object obj);

    public static boolean executeUncached(Object obj) {
        return PyFloatCheckNodeGen.getUncached().execute(null, obj);
    }

    @Specialization
    static boolean doDouble(@SuppressWarnings("unused") Double obj) {
        return true;
    }

    @Specialization
    static boolean doPFloat(@SuppressWarnings("unused") PFloat obj) {
        return true;
    }

    @Specialization
    static boolean doNative(PythonAbstractNativeObject obj,
                    @Cached(inline = false) PyObjectTypeCheck check) {
        return check.execute(obj, PythonBuiltinClassType.PFloat);
    }

    @Fallback
    static boolean doOther(@SuppressWarnings("unused") Object obj) {
        return false;
    }
}
