/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.nodes.statement.ExceptNode;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.graal.python.nodes.statement.TryExceptNode;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes.ExceptionState;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes.SetCaughtExceptionNode;
import com.oracle.graal.python.runtime.exception.ExceptionHandledException;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.ExplodeLoop;

public class GeneratorTryExceptNode extends TryExceptNode implements GeneratorControlNode {

    @Child private GeneratorAccessNode gen = GeneratorAccessNode.create();

    private final int exceptFlag;
    private final int elseFlag;
    private final int exceptIndex;
    private final int activeExceptionIndex;

    public GeneratorTryExceptNode(StatementNode body, ExceptNode[] exceptNodes, StatementNode orelse, int exceptFlag, int elseFlag, int exceptIndex, int activeExceptionIndex) {
        super(body, exceptNodes, orelse);
        this.exceptFlag = exceptFlag;
        this.elseFlag = elseFlag;
        this.exceptIndex = exceptIndex;
        this.activeExceptionIndex = activeExceptionIndex;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        if (gen.isActive(frame, exceptFlag)) {
            // we already found the right except handler, jump back into it directly
            catchExceptionInGeneratorCached(frame);
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
        } catch (PException exception) {
            gen.setActive(frame, exceptFlag, true);
            catchExceptionInGeneratorFirstTime(frame, exception);
            reset(frame);
            return;
        } catch (ControlFlowException e) {
            throw e;
        } catch (Exception | StackOverflowError | AssertionError e) {
            if (shouldCatchAllExceptions()) {
                gen.setActive(frame, exceptFlag, true);
                catchExceptionInGeneratorFirstTime(frame, wrapJavaException(e));
                reset(frame);
                return;
            } else {
                throw e;
            }
        }

        gen.setActive(frame, elseFlag, true);
        getOrelse().executeVoid(frame);
        reset(frame);
    }

    @ExplodeLoop
    private void catchExceptionInGeneratorFirstTime(VirtualFrame frame, PException exception) {
        boolean wasHandled = false;
        ExceptNode[] exceptNodes = getExceptNodes();
        // we haven't found the matching node, yet, start searching
        for (int i = 0; i < exceptNodes.length; i++) {
            // we want a constant loop iteration count for ExplodeLoop to work,
            // so we always run through all except handlers
            if (!wasHandled) {
                ExceptNode exceptNode = exceptNodes[i];
                gen.setIndex(frame, exceptIndex, i + 1);
                if (exceptNode.matchesException(frame, exception)) {
                    exception.setCatchingFrameReference(frame);
                    exception.markFrameEscaped();
                    tryChainPreexistingException(frame, exception);
                    gen.setActiveException(frame, activeExceptionIndex, exception);
                    runExceptionHandler(frame, exception, exceptNode);
                    wasHandled = true;
                }
            }
        }
        if (!wasHandled) {
            // we tried and haven't found a matching except node
            throw exception;
        }
    }

    @ExplodeLoop
    private void catchExceptionInGeneratorCached(VirtualFrame frame) {
        ExceptNode[] exceptNodes = getExceptNodes();
        CompilerAsserts.compilationConstant(exceptNodes);
        PException exception = gen.getActiveException(frame, activeExceptionIndex);
        final int matchingExceptNodeIndex = gen.getIndex(frame, exceptIndex);
        assert matchingExceptNodeIndex <= exceptNodes.length;
        boolean wasHandled = false;
        for (int i = 0; i < exceptNodes.length; i++) {
            // we want a constant loop iteration count for ExplodeLoop to work,
            // so we always run through all except handlers
            if (i == matchingExceptNodeIndex - 1) {
                runExceptionHandler(frame, exception, exceptNodes[i]);
                wasHandled = true;
            }
        }
        assert wasHandled : "cached exception handler does not handle exception";
    }

    private void runExceptionHandler(VirtualFrame frame, PException exception, ExceptNode exceptNode) {
        ExceptionState savedExceptionState = saveExceptionState(frame);
        SetCaughtExceptionNode.execute(frame, exception);
        try {
            exceptNode.executeExcept(frame, exception);
        } catch (ExceptionHandledException e) {
            return;
        } catch (PException handlerException) {
            tryChainExceptionFromHandler(handlerException, exception);
            throw handlerException;
        } finally {
            restoreExceptionState(frame, savedExceptionState);
        }
    }

    public void reset(VirtualFrame frame) {
        gen.setActive(frame, elseFlag, false);
        gen.setActive(frame, exceptFlag, false);
        gen.setIndex(frame, exceptIndex, 0);
        gen.setActiveException(frame, activeExceptionIndex, null);
    }
}
