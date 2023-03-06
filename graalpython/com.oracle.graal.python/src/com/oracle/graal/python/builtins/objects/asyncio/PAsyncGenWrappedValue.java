package com.oracle.graal.python.builtins.objects.asyncio;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.truffle.api.object.Shape;

public class PAsyncGenWrappedValue extends PythonBuiltinObject {
    // TODO: consider whether a freelist makes sense
    private final Object wrapped;

    public PAsyncGenWrappedValue(PythonLanguage lang, Object wrapped) {
        super(PythonBuiltinClassType.PAsyncGenAWrappedValue, PythonBuiltinClassType.PAsyncGenAWrappedValue.getInstanceShape(lang));
        this.wrapped = wrapped;
    }

    public Object getWrapped() {
        return wrapped;
    }
}
