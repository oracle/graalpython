package com.oracle.graal.python.builtins.objects.generator;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;

public class PCoroutineWrapper extends PythonBuiltinObject {
    public final PGenerator coroutine;

    public PCoroutineWrapper(PythonLanguage lang, PGenerator coroutine) {
        super(PythonBuiltinClassType.PCoroutineWrapper, PythonBuiltinClassType.PCoroutineWrapper.getInstanceShape(lang));
        assert coroutine.isCoroutine();
        this.coroutine = coroutine;
    }
}
