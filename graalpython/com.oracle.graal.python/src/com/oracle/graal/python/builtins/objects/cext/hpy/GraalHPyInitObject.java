package com.oracle.graal.python.builtins.objects.cext.hpy;

import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

/**
 * A simple interop-capable object that is used to initialize the HPy C API.
 */
@ExportLibrary(InteropLibrary.class)
public class GraalHPyInitObject implements TruffleObject {

    public static final String SET_HPY_NATIVE_TYPE = "setHPyNativeType";
    private final GraalHPyContext hpyContext;

    public GraalHPyInitObject(GraalHPyContext hpyContext) {
        this.hpyContext = hpyContext;
    }

    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return new PythonAbstractObject.Keys(new String[] { SET_HPY_NATIVE_TYPE });
    }

    @ExportMessage
    boolean isMemberInvocable(String key) {
        return SET_HPY_NATIVE_TYPE.equals(key);
    }

    @ExportMessage
    Object invokeMember(String key, Object[] arguments) throws UnsupportedMessageException {
        if (SET_HPY_NATIVE_TYPE.equals(key)) {
            hpyContext.setHPyNativeType(arguments[0]);
            return 0;
        }
        throw UnsupportedMessageException.create();
    }

}
