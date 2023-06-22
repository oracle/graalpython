package com.oracle.graal.python.lib;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.InlinedGetClassNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

@GenerateInline
@GenerateCached(false)
@GenerateUncached
public abstract class PyExceptionInstanceCheckNode extends Node {
    public abstract boolean execute(Node inliningTarget, Object object);

    public static boolean executeUncached(Object object) {
        return PyExceptionInstanceCheckNodeGen.getUncached().execute(null, object);
    }

    @Specialization
    static boolean doManaged(@SuppressWarnings("unused") PBaseException exception) {
        return true;
    }

    @Fallback
    static boolean doOther(Node inliningTarget, Object object,
                    @Cached InlinedGetClassNode getClassNode,
                    @Cached IsSubtypeNode isSubtypeNode) {
        // May be native or interop
        return isSubtypeNode.execute(getClassNode.execute(inliningTarget, object), PythonBuiltinClassType.PBaseException);
    }
}
