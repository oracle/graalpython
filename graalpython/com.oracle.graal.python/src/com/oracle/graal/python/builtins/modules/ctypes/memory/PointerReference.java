package com.oracle.graal.python.builtins.modules.ctypes.memory;

import com.oracle.graal.python.runtime.AsyncHandler;

/**
 * Weak reference object that will deallocate possible native memory pointed to by a {@link Pointer}
 * when given referent gets garbage collected. It is not necessary to keep a reference to this
 * object after creating it, it stores a reference to itself into
 * {@link AsyncHandler.SharedFinalizer} automatically.
 */
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
