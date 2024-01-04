package com.oracle.graal.python.builtins.modules.pickle;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;

import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.object.Shape;

@ExportLibrary(PythonBufferAcquireLibrary.class)
public final class PPickleBuffer extends PythonBuiltinObject {
    // Buffer object implementing PythonBufferAccessLibrary
    private Object view;

    public PPickleBuffer(Object cls, Shape instanceShape, Object view) {
        super(cls, instanceShape);
        PythonBufferAccessLibrary.assertIsBuffer(view);
        this.view = view;
    }

    public Object getView() {
        return view;
    }

    public void release() {
        this.view = null;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasBuffer() {
        return true;
    }

    @ExportMessage
    Object acquire(int flags,
                    @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                    @CachedLibrary(limit = "3") PythonBufferAcquireLibrary acquireLib,
                    @Cached PRaiseNode raise) {
        Object owner = null;
        if (view != null) {
            owner = bufferLib.getOwner(view);
        }
        if (owner == null) {
            throw raise.raise(ValueError, ErrorMessages.OP_FORBIDDEN_ON_OBJECT, "PickleBuffer");
        }
        return acquireLib.acquire(owner, flags);
    }
}
