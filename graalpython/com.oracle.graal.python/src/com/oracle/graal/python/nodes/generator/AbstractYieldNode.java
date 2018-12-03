package com.oracle.graal.python.nodes.generator;

import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.profiles.BranchProfile;

public abstract class AbstractYieldNode extends ExpressionNode {
    @CompilationFinal protected int flagSlot;

    protected final BranchProfile gotException = BranchProfile.create();
    protected final BranchProfile gotValue = BranchProfile.create();
    protected final BranchProfile gotNothing = BranchProfile.create();

    public void setFlagSlot(int slot) {
        this.flagSlot = slot;
    }

    public AbstractYieldNode() {
        super();
    }

}