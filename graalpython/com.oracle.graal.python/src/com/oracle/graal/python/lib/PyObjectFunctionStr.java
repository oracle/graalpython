package com.oracle.graal.python.lib;

import static com.oracle.graal.python.nodes.SpecialAttributeNames.__MODULE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__QUALNAME__;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * Obtains a string representation of a function for error reporting. Equivalent of CPython's
 * {@code _PyObject_FunctionStr}.
 */
@GenerateUncached
public abstract class PyObjectFunctionStr extends PNodeWithContext {
    public abstract String execute(Frame frame, Object function);

    @Specialization
    String str(VirtualFrame frame, Object function,
                    @Cached PyObjectLookupAttr lookupQualname,
                    @Cached PyObjectLookupAttr lookupModule,
                    @Cached PyObjectStrAsJavaStringNode asStr) {
        Object qualname = lookupQualname.execute(frame, function, __QUALNAME__);
        if (qualname == PNone.NO_VALUE) {
            return asStr.execute(function);
        }
        Object module = lookupModule.execute(frame, function, __MODULE__);
        if (!(module instanceof PNone) && !(module instanceof String && !"builtins".equals(module))) {
            return PString.cat(asStr.execute(frame, module), ".", asStr.execute(frame, qualname), "()");
        }
        return PString.cat(asStr.execute(frame, qualname), "()");
    }

    public static PyObjectFunctionStr create() {
        return PyObjectFunctionStrNodeGen.create();
    }
}
