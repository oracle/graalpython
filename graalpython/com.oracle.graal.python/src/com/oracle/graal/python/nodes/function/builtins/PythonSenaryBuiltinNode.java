package com.oracle.graal.python.nodes.function.builtins;

import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class PythonSenaryBuiltinNode extends PythonBuiltinBaseNode {

    public abstract Object execute(VirtualFrame frame, Object arg, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6);
}
