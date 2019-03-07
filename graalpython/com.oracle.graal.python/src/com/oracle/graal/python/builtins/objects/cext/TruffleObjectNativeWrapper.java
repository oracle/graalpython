package com.oracle.graal.python.builtins.objects.cext;

import com.oracle.truffle.api.interop.TruffleObject;

public class TruffleObjectNativeWrapper extends PythonNativeWrapper {

    public TruffleObjectNativeWrapper(TruffleObject foreignObject) {
        super(foreignObject);
    }

    public static TruffleObjectNativeWrapper wrap(TruffleObject foreignObject) {
        assert !(foreignObject instanceof PythonNativeWrapper) : "attempting to wrap a native wrapper";
        return new TruffleObjectNativeWrapper(foreignObject);
    }
}