package com.oracle.graal.python.builtins.objects.cext;

import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.truffle.api.interop.TruffleObject;

public class PythonNativeVoidPtr extends PythonAbstractObject {
    public final TruffleObject object;

    public PythonNativeVoidPtr(TruffleObject object) {
        this.object = object;
    }

    public int compareTo(Object o) {
        return 0;
    }

}
