package com.oracle.graal.python.builtins.modules.cext;

import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Ignored;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PY_BUFFER_PTR;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObject;

import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBinaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiTernaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.ctypes.CDataObject;
import com.oracle.graal.python.builtins.modules.ctypes.CtypesNodes;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;

public final class PythonCextCDataBuiltins {
    @CApiBuiltin(ret = Int, args = {PyObject, PY_BUFFER_PTR, Int}, call = Ignored)
    abstract static class PyTruffleCData_NewGetBuffer extends CApiTernaryBuiltinNode {
        @Specialization
        static int getBuffer(CDataObject self, Object view, int flags,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "1") InteropLibrary lib,
                        @Cached CtypesNodes.CDataGetBufferNode getBufferNode) {
            if (lib.isNull(view)) {
                return 0;
            }
            getBufferNode.execute(inliningTarget, self, view, flags);
            return 0;
        }
    }

    @CApiBuiltin(ret = ArgDescriptor.Void, args = {PyObject, PY_BUFFER_PTR}, call = Ignored)
    abstract static class PyTruffleCData_ReleaseBuffer extends CApiBinaryBuiltinNode {
        @Specialization
        static Object releaseBuffer(@SuppressWarnings("unused") CDataObject self, Object view,
                        @Bind("this") Node inliningTarget,
                        @Cached CtypesNodes.CDataReleaseBufferNode releaseBufferNode) {
            releaseBufferNode.execute(inliningTarget, view);
            return PNone.NO_VALUE;
        }
    }
}
