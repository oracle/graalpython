package com.oracle.graal.python.lib;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.list.ListBuiltins;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * Partial equivalent of CPython's {@code PyObject_Dir}. Only supports listing attributes of an
 * object, not local variables like the {@code dir} builtin when called with no arguments.
 */
public abstract class PyObjectDir extends PNodeWithContext {
    public abstract PList execute(VirtualFrame frame, Object object);

    @Specialization
    PList dir(VirtualFrame frame, Object object,
                    @Cached ListBuiltins.ListSortNode sortNode,
                    @Cached ListNodes.ConstructListNode constructListNode,
                    @Cached("create(T___DIR__)") LookupAndCallUnaryNode callDir,
                    @Cached PRaiseNode raiseNode) {
        Object result = callDir.executeObject(frame, object);
        if (result == PNone.NO_VALUE) {
            throw raiseNode.raise(TypeError, ErrorMessages.OBJ_DOES_NOT_PROVIDE_DIR);
        }
        PList list = constructListNode.execute(frame, result);
        sortNode.execute(frame, list);
        return list;
    }
}
