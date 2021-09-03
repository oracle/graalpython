package com.oracle.graal.python.nodes.object;

import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_PY_OBJECT_GENERIC_GET_DICT;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.object.DynamicObjectLibrary;

@GenerateUncached
public abstract class GetDictIfExistsNode extends PNodeWithContext {
    public abstract PDict execute(Object object);

    public abstract PDict execute(PythonObject object);

    // FIXME thread local

    @Specialization
    static PDict doPythonObject(PythonObject object,
                    @CachedLibrary(limit = "4") DynamicObjectLibrary dylib) {
        return (PDict) dylib.getOrDefault(object, PythonObject.DICT, null);
    }

    @Specialization
    PDict doNativeObject(PythonAbstractNativeObject object,
                    @Cached CExtNodes.ToSulongNode toSulong,
                    @Cached CExtNodes.ToJavaNode toJava,
                    @Cached CExtNodes.PCallCapiFunction callGetDictNode) {
        Object javaDict = toJava.execute(callGetDictNode.call(FUN_PY_OBJECT_GENERIC_GET_DICT, toSulong.execute(object)));
        if (javaDict instanceof PDict) {
            return (PDict) javaDict;
        } else if (javaDict == PNone.NO_VALUE) {
            return null;
        } else {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw PRaiseNode.raiseUncached(this, PythonBuiltinClassType.TypeError, ErrorMessages.DICT_MUST_BE_SET_TO_DICT, javaDict);
        }
    }

    @Fallback
    static PDict doOther(@SuppressWarnings("unused") Object object) {
        return null;
    }

    public static GetDictIfExistsNode create() {
        return GetDictIfExistsNodeGen.create();
    }

    public static GetDictIfExistsNode getUncached() {
        return GetDictIfExistsNodeGen.getUncached();
    }
}
