package com.oracle.graal.python.builtins.objects.cext.capi;

import com.oracle.graal.python.builtins.objects.PythonAbstractObject.PExecuteNode;
import com.oracle.graal.python.nodes.util.CastToJavaIntLossyNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

public class PyGILStateNodes {
    /**
     * A simple executable interop object that acquires the GIL if not acquired already. This cannot
     * be implemented as a Python built-in function in {@code PythonCextBuiltins} because if the
     * built-in function would be called via interop (this would go through
     * {@link com.oracle.graal.python.builtins.objects.PythonAbstractObject#execute(Object[], PExecuteNode, GilNode)}
     * ), it tries to acquire/release the GIL for execution. This would again release the GIL when
     * returning.
     */
    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings("static-method")
    public static final class PyGILStateEnsure implements TruffleObject {
        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        int execute(@SuppressWarnings("unused") Object[] arguments,
                        @Cached GilNode gil) {
            // TODO allow acquiring from foreign thread
            boolean acquired = gil.acquire();
            return acquired ? 1 : 0;
        }
    }

    /**
     * A simple executable interop object that releases GIL only if acquired in the corresponding
     * {@code PyGILState_Ensure} call. This cannot be implemented as a Python built-in function in
     * {@code PythonCextBuiltins} because if the built-in function would be called via interop (this
     * would go through
     * {@link com.oracle.graal.python.builtins.objects.PythonAbstractObject#execute(Object[], PExecuteNode, GilNode)}
     * ), it tries to acquire/release the GIL for execution. This would again release the GIL when
     * returning.
     */
    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings("static-method")
    public static final class PyGILStateRelease implements TruffleObject {
        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached CastToJavaIntLossyNode cast,
                        @Cached GilNode gil) {
            gil.release(cast.execute(arguments[0]) == 1);
            return 0;
        }
    }
}
