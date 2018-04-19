/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
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
package com.oracle.graal.python.nodes.generator;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.control.LoopNode;
import com.oracle.graal.python.nodes.frame.WriteNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.profiles.ConditionProfile;

public abstract class GeneratorForNode extends LoopNode implements GeneratorControlNode {

    @Child protected PNode body;
    @Child protected WriteNode target;
    @Child protected PNode getIterator;
    @Child protected GetNextNode getNext = GetNextNode.create();

    protected final ConditionProfile errorProfile = ConditionProfile.createBinaryProfile();

    protected final int iteratorSlot;
    private int count;

    public GeneratorForNode(WriteNode target, PNode getIterator, PNode body, int iteratorSlot) {
        this.body = body;
        this.target = target;
        this.getIterator = getIterator;
        this.iteratorSlot = iteratorSlot;
    }

    public static GeneratorForNode create(WriteNode target, PNode getIterator, PNode body, int iteratorSlot) {
        return new UninitializedGeneratorForNode(target, getIterator, body, iteratorSlot);
    }

    @Override
    public PNode getBody() {
        return body;
    }

    public final int getIteratorSlot() {
        return iteratorSlot;
    }

    protected final Object doReturn(VirtualFrame frame) {
        if (CompilerDirectives.inInterpreter()) {
            reportLoopCount(count);
            count = 0;
        }

        reset(frame);
        return PNone.NONE;
    }

    public void reset(VirtualFrame frame) {
        setIterator(frame, iteratorSlot, null);
    }

    protected final void incrementCounter() {
        if (CompilerDirectives.inInterpreter()) {
            count++;
        }
    }

    @Override
    public Object execute(VirtualFrame frame) {

        if (executeIterator(frame)) {
            return doReturn(frame);
        }

        while (true) {
            body.executeVoid(frame);
            Object iterator = getIterator(frame, iteratorSlot);
            Object value;
            try {
                value = getNext.execute(iterator);
            } catch (PException e) {
                e.expectStopIteration(getCore(), errorProfile);
                break;
            }
            target.doWrite(frame, value);
            incrementCounter();
        }

        return doReturn(frame);
    }

    /**
     * Returns {@code true} if the iterator stopped (instead of throwing an exception).
     */
    protected abstract boolean executeIterator(VirtualFrame frame);

    @NodeInfo(cost = NodeCost.POLYMORPHIC)
    public static final class GenericGeneratorForNode extends GeneratorForNode {

        public GenericGeneratorForNode(WriteNode target, PNode getIterator, PNode body, int iteratorSlot) {
            super(target, getIterator, body, iteratorSlot);
        }

        @Override
        protected boolean executeIterator(VirtualFrame frame) {
            if (getIterator(frame, iteratorSlot) != null) {
                return false;
            }

            setIterator(frame, iteratorSlot, this.getIterator.execute(frame));
            Object iterator = getIterator(frame, iteratorSlot);

            Object value;
            try {
                value = getNext.execute(iterator);
            } catch (PException e) {
                e.expectStopIteration(getCore(), errorProfile);
                return true;
            }
            target.doWrite(frame, value);
            incrementCounter();
            return false;
        }
    }

    @NodeInfo(cost = NodeCost.UNINITIALIZED)
    public static final class UninitializedGeneratorForNode extends GeneratorForNode {

        public UninitializedGeneratorForNode(WriteNode target, PNode getIterator, PNode body, int iteratorSlot) {
            super(target, getIterator, body, iteratorSlot);
        }

        @Override
        protected boolean executeIterator(VirtualFrame frame) {
            CompilerDirectives.transferToInterpreterAndInvalidate();

            if (getIterator(frame, iteratorSlot) != null) {
                return false;
            }

            Object iterator = getIterator.execute(frame);

            replace(new GenericGeneratorForNode(target, getIterator, body, this.getIteratorSlot()));

            setIterator(frame, iteratorSlot, iterator);

            iterator = getIterator(frame, iteratorSlot);
            Object value;
            try {
                value = getNext.execute(iterator);
            } catch (PException e) {
                e.expectStopIteration(getCore(), errorProfile);
                return true;
            }
            target.doWrite(frame, value);
            incrementCounter();
            return false;
        }
    }

}
