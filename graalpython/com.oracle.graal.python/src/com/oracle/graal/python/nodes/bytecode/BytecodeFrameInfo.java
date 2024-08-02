package com.oracle.graal.python.nodes.bytecode;

import com.oracle.graal.python.compiler.CodeUnit;
import com.oracle.graal.python.compiler.OpCodesConstants;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.Frame;

public class BytecodeFrameInfo implements FrameInfo {
    @CompilationFinal PBytecodeRootNode rootNode;

    void setRootNode(PBytecodeRootNode rootNode) {
        this.rootNode = rootNode;
    }

    @Override
    public PBytecodeRootNode getRootNode() {
        return rootNode;
    }

    public int getBci(Frame frame) {
        if (frame.isInt(rootNode.bcioffset)) {
            return frame.getInt(rootNode.bcioffset);
        } else {
            return -1;
        }
    }

    public int getLineForBci(int bci) {
        return rootNode.bciToLine(bci);
    }

    public int getLine(Frame frame) {
        return getLineForBci(getBci(frame));
    }

    @Override
    public int getFirstLineNumber() {
        return rootNode.getFirstLineno();
    }

    @Override
    public Object getYieldFrom(Frame generatorFrame, int bci, int stackTop) {
        /* Match the `yield from` bytecode pattern and get the object from stack */
        if (bci > 3 && bci < rootNode.bytecode.length && rootNode.bytecode[bci - 3] == OpCodesConstants.SEND && rootNode.bytecode[bci - 1] == OpCodesConstants.YIELD_VALUE &&
                        rootNode.bytecode[bci] == OpCodesConstants.RESUME_YIELD) {
            return generatorFrame.getObject(stackTop);
        }
        return null;
    }

    @Override
    public CodeUnit getCodeUnit() {
        return rootNode.getCodeUnit();
    }

    @Override
    public boolean includeInTraceback() {
        return rootNode.frameIsVisibleToPython();
    }
}
