package com.oracle.graal.python.nodes.classes;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.NameError;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;

@NodeInfo(shortName = "load_build_class")
public final class LoadBuildClassNode extends ExpressionNode {
    @Child ReadAttributeFromObjectNode read = ReadAttributeFromObjectNode.create();

    @Override
    public Object execute(VirtualFrame frame) {
        PythonModule builtins = getContext().getCore().getBuiltins();
        Object result = read.execute(builtins, BuiltinNames.__BUILD_CLASS__);
        if (result == PNone.NO_VALUE) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw PRaiseNode.raiseUncached(this, NameError, "__build_class__ not found");
        }
        return result;
    }
}
