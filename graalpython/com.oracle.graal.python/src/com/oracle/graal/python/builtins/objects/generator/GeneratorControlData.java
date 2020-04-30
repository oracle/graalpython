/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
 * Copyright (c) 2014, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.builtins.objects.generator;

import com.oracle.graal.python.runtime.exception.PException;

public final class GeneratorControlData {

    // See {@link GeneratorReturnTargetNode},
    // {@link GeneratorIfNode},
    // {@link GeneratorWhileNode}.
    private final boolean[] activeFlags;
    private final int[] blockNodeIndices;       // See {@link GeneratorBlockNode}
    private final Object[] forNodeIterators; // See {@link GeneratorForNode}
    private final PException[] activeExceptions;
    private int lastYieldIndex;

    public GeneratorControlData(int numOfActiveFlags, int numOfGeneratorBlockNode, int numOfGeneratorForNode, int numOfTryNode) {
        this.activeFlags = new boolean[numOfActiveFlags];
        this.blockNodeIndices = new int[numOfGeneratorBlockNode];
        this.forNodeIterators = new Object[numOfGeneratorForNode];
        this.activeExceptions = new PException[numOfTryNode];
    }

    public int getLastYieldIndex() {
        return lastYieldIndex;
    }

    public void setLastYieldIndex(int lastYieldIndex) {
        assert lastYieldIndex != 0;
        this.lastYieldIndex = lastYieldIndex;
    }

    public boolean getActive(int slot) {
        return activeFlags[slot];
    }

    public void setActive(int slot, boolean flag) {
        activeFlags[slot] = flag;
    }

    public int getBlockIndexAt(int slot) {
        return blockNodeIndices[slot];
    }

    public void setBlockIndexAt(int slot, int value) {
        blockNodeIndices[slot] = value;
    }

    public Object getIteratorAt(int slot) {
        return forNodeIterators[slot];
    }

    public void setIteratorAt(int slot, Object value) {
        forNodeIterators[slot] = value;
    }

    public PException getActiveException(int slot) {
        return activeExceptions[slot];
    }

    public void setActiveException(int slot, PException activeException) {
        this.activeExceptions[slot] = activeException;
    }
}
