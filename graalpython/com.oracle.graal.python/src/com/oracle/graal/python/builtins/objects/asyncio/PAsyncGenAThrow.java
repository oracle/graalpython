package com.oracle.graal.python.builtins.objects.asyncio;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;

public class PAsyncGenAThrow extends PythonBuiltinObject {
    public PAsyncGenAThrow(PythonLanguage language) {
        super(PythonBuiltinClassType.PAsyncGenAThrow, PythonBuiltinClassType.PAsyncGenAThrow.getInstanceShape(language));
    }
}
