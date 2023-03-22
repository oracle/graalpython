package com.oracle.graal.python.nodes;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.Node;

/**
 * Simple implementation of {@link IndirectCallNode} that can be used as a standalone
 * {@code @Cached} node if the user node cannot inherit from other {@link IndirectCallNode}
 * implementations.
 */
public final class IndirectCallData extends Node implements IndirectCallNode {
    private static final IndirectCallData UNCACHED = new IndirectCallData();

    public static IndirectCallData create() {
        return new IndirectCallData();
    }

    public static IndirectCallData getUncached() {
        return UNCACHED;
    }

    @CompilationFinal private Assumption nativeCodeDoesntNeedExceptionState;
    @CompilationFinal private Assumption nativeCodeDoesntNeedMyFrame;

    @Override
    public Assumption needNotPassFrameAssumption() {
        if (nativeCodeDoesntNeedMyFrame == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            nativeCodeDoesntNeedMyFrame = Truffle.getRuntime().createAssumption();
        }
        return nativeCodeDoesntNeedMyFrame;
    }

    @Override
    public Assumption needNotPassExceptionAssumption() {
        if (nativeCodeDoesntNeedExceptionState == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            nativeCodeDoesntNeedExceptionState = Truffle.getRuntime().createAssumption();
        }
        return nativeCodeDoesntNeedExceptionState;
    }
}
