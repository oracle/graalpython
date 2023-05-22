package com.oracle.graal.python.builtins.modules.ctypes.memory;

import com.oracle.graal.python.runtime.AsyncHandler;

public class PointerReference extends AsyncHandler.SharedFinalizer.FinalizableReference {

    public PointerReference(Object referent, Pointer pointer, AsyncHandler.SharedFinalizer sharedFinalizer) {
        super(referent, pointer, sharedFinalizer);
    }

    @Override
    public AsyncHandler.AsyncAction release() {
        // This node currently doesn't need a call target
        return (context) -> PointerNodes.FreeNode.getUncached().execute(null, (Pointer) getReference());
    }
}
