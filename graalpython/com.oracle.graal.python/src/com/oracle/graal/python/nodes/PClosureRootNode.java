/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */
package com.oracle.graal.python.nodes;

import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;

public abstract class PClosureRootNode extends PRootNode {
    @CompilerDirectives.CompilationFinal(dimensions = 1) protected final FrameSlot[] freeVarSlots;

    protected PClosureRootNode(TruffleLanguage<?> language, FrameDescriptor frameDescriptor, FrameSlot[] freeVarSlots) {
        super(language, frameDescriptor);
        this.freeVarSlots = freeVarSlots;
    }

    protected void addClosureCellsToLocals(Frame frame) {
        PCell[] closure = PArguments.getClosure(frame);
        if (closure != null) {
            assert freeVarSlots != null : "closure root node: the free var slots cannot be null when the closure is not null";
            assert closure.length == freeVarSlots.length : "closure root node: the closure must have the same length as the free var slots array";
            for (int i = 0; i < closure.length; i++) {
                frame.setObject(freeVarSlots[i], closure[i]);
            }
        }
    }
}
