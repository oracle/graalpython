package com.oracle.graal.python.nodes.bytecode_dsl;

import com.oracle.graal.python.compiler.CodeUnit;
import com.oracle.graal.python.nodes.bytecode.FrameInfo;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.Frame;

public class BytecodeDSLFrameInfo implements FrameInfo {
    @CompilationFinal PBytecodeDSLRootNode rootNode;

    /**
     * The root node cannot be created without a frame descriptor, which cannot be created without
     * the frame info, so we have to set the root node after the frame info is constructed.
     */
    void setRootNode(PBytecodeDSLRootNode rootNode) {
        this.rootNode = rootNode;
    }

    @Override
    public PBytecodeDSLRootNode getRootNode() {
        return rootNode;
    }

    @Override
    public int getFirstLineNumber() {
        return rootNode.getFirstLineno();
    }

    @Override
    public Object getYieldFrom(Frame generatorFrame, int bci, int stackTop) {
        // TODO implement
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public CodeUnit getCodeUnit() {
        return rootNode.getCodeUnit();
    }

    @Override
    public boolean includeInTraceback() {
        return !rootNode.isInternal();
    }
}
