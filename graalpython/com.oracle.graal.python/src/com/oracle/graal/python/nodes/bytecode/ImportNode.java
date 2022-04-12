package com.oracle.graal.python.nodes.bytecode;

import com.oracle.graal.python.nodes.statement.AbstractImportNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;

public class ImportNode extends AbstractImportNode {
    @Override
    public final void executeVoid(VirtualFrame frame) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    public final Object execute(VirtualFrame frame, String name, Object globals, String[] fromList, int level) {
        return importModule(frame, name, globals, fromList, level);
    }
}
