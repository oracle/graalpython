/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.nodes.generator;

import com.oracle.graal.python.builtins.objects.exception.ExceptionInfo;
import com.oracle.graal.python.nodes.statement.ExceptNode;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.graal.python.nodes.statement.TryExceptNode;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes.ExceptionState;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes.RestoreExceptionStateNode;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes.SaveExceptionStateNode;
import com.oracle.graal.python.runtime.exception.ExceptionHandledException;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.ExplodeLoop;

public class GeneratorTryExceptNode extends TryExceptNode implements GeneratorControlNode {

    @Child private GeneratorAccessNode gen = GeneratorAccessNode.create();
    @Child private SaveExceptionStateNode saveExceptionStateNode = SaveExceptionStateNode.create();
    @Child private RestoreExceptionStateNode restoreExceptionState = RestoreExceptionStateNode.create();

    private final int exceptFlag;
    private final int elseFlag;
    private final int exceptIndex;

    public GeneratorTryExceptNode(StatementNode body, ExceptNode[] exceptNodes, StatementNode orelse, int exceptFlag, int elseFlag, int exceptIndex) {
        super(body, exceptNodes, orelse);
        this.exceptFlag = exceptFlag;
        this.elseFlag = elseFlag;
        this.exceptIndex = exceptIndex;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        ExceptionState exceptionState = saveExceptionStateNode.execute(frame);

        if (gen.isActive(frame, exceptFlag)) {
            catchExceptionInGenerator(frame, gen.getActiveException(frame).exc.exception.getException(), exceptionState);
            reset(frame);
            return;
        }

        if (gen.isActive(frame, elseFlag)) {
            getOrelse().executeVoid(frame);
            reset(frame);
            return;
        }

        try {
            getBody().executeVoid(frame);
        } catch (PException ex) {
            gen.setActive(frame, exceptFlag, true);
            ExceptionInfo exceptionInfo = new ExceptionInfo(ex.getExceptionObject(), ex.getExceptionObject().getTraceback());
            gen.setActiveException(frame, new ExceptionState(exceptionInfo, ExceptionState.SOURCE_GENERATOR));
            catchExceptionInGenerator(frame, ex, exceptionState);
            reset(frame);
            return;
        }

        gen.setActive(frame, elseFlag, true);
        getOrelse().executeVoid(frame);
        reset(frame);
        return;
    }

    @ExplodeLoop
    private void catchExceptionInGenerator(VirtualFrame frame, PException exception, ExceptionState exceptionState) {
        ExceptNode[] exceptNodes = getExceptNodes();
        final int matchingExceptNodeIndex = gen.getIndex(frame, exceptIndex);
        boolean wasHandled = false;
        if (matchingExceptNodeIndex == 0) {
            // we haven't found the matching node, yet, start searching
            for (int i = 0; i < exceptNodes.length; i++) {
                // we want a constant loop iteration count for ExplodeLoop to work,
                // so we always run through all except handlers
                if (!wasHandled) {
                    ExceptNode exceptNode = exceptNodes[i];
                    gen.setIndex(frame, exceptIndex, i + 1);
                    if (exceptNode.matchesException(frame, exception)) {
                        runExceptionHandler(frame, exception, exceptNode, exceptionState);
                        wasHandled = true;
                    }
                }
            }
        } else if (matchingExceptNodeIndex <= exceptNodes.length) {
            // we already found the right except handler, jump back into it directly
            wasHandled = catchExceptionInGeneratorCached(frame, exceptNodes, exception, exceptionState, matchingExceptNodeIndex);
        }
        reset(frame);
        if (!wasHandled) {
            // we tried and haven't found a matching except node
            throw exception;
        }
        restoreExceptionState.execute(frame, exceptionState);
    }

    @ExplodeLoop
    private boolean catchExceptionInGeneratorCached(VirtualFrame frame, ExceptNode[] exceptNodes, PException exception, ExceptionState exceptionState, int matchingExceptNodeIndex) {
        CompilerAsserts.compilationConstant(exceptNodes);
        assert matchingExceptNodeIndex <= exceptNodes.length;
        boolean wasHandled = false;
        for (int i = 0; i < exceptNodes.length; i++) {
            // we want a constant loop iteration count for ExplodeLoop to work,
            // so we always run through all except handlers
            if (i == matchingExceptNodeIndex - 1) {
                runExceptionHandler(frame, exception, exceptNodes[i], exceptionState);
                wasHandled = true;
            }
        }
        assert wasHandled : "cached exception handler does not handle exception";
        return wasHandled;
    }

    private void runExceptionHandler(VirtualFrame frame, PException exception, ExceptNode exceptNode, ExceptionState exceptionState) {
        try {
            exceptNode.executeExcept(frame, exception);
        } catch (ExceptionHandledException e) {
            return;
        } catch (ControlFlowException e) {
            // restore previous exception state, this won't happen if the except block raises an
            // exception
            restoreExceptionState.execute(frame, exceptionState);
            throw e;
        }
    }

    public void reset(VirtualFrame frame) {
        gen.setActive(frame, elseFlag, false);
        gen.setActive(frame, exceptFlag, false);
        gen.setIndex(frame, exceptIndex, 0);
        gen.setActiveException(frame, null);
    }
}
