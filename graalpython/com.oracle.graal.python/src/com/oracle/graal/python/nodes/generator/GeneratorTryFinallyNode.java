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

import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.graal.python.nodes.statement.TryFinallyNode;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes.ExceptionState;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes.SetCaughtExceptionNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;

public class GeneratorTryFinallyNode extends TryFinallyNode implements GeneratorControlNode {
    @Child private GeneratorAccessNode gen = GeneratorAccessNode.create();
    private final BranchProfile exceptionProfile = BranchProfile.create();

    private final int finallyFlag;
    private final int activeExceptionIndex;

    public GeneratorTryFinallyNode(StatementNode body, StatementNode finalbody, int finallyFlag, int activeExceptionIndex) {
        super(body, finalbody);
        this.finallyFlag = finallyFlag;
        this.activeExceptionIndex = activeExceptionIndex;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        ExceptionState savedExceptionState = null;
        PException activeException = null;
        if (gen.isActive(frame, finallyFlag)) {
            activeException = gen.getActiveException(frame, activeExceptionIndex);
        } else {
            try {
                getBody().executeVoid(frame);
            } catch (PException e) {
                // any thrown Python exception is visible in the finally block
                exceptionProfile.enter();
                activeException = e;
                e.reify(frame);
                e.markFrameEscaped();
                tryChainPreexistingException(frame, e);
                gen.setActiveException(frame, activeExceptionIndex, e);
            }
            gen.setActive(frame, finallyFlag, true);
        }
        if (activeException != null) {
            savedExceptionState = saveExceptionState(frame);
            SetCaughtExceptionNode.execute(frame, activeException);
        }
        try {
            executeFinalBody(frame);
        } catch (PException handlerException) {
            if (activeException != null) {
                tryChainExceptionFromHandler(handlerException, activeException);
            }
            throw handlerException;
        } finally {
            if (activeException != null) {
                restoreExceptionState(frame, savedExceptionState);
            }
        }
        reset(frame);
        if (activeException != null) {
            throw activeException.getExceptionForReraise();
        }
    }

    private void executeFinalBody(VirtualFrame frame) {
        StatementNode finalbody = getFinalbody();
        if (finalbody != null) {
            finalbody.executeVoid(frame);
        }
    }

    public void reset(VirtualFrame frame) {
        gen.setActive(frame, finallyFlag, false);
        gen.setActiveException(frame, activeExceptionIndex, null);
    }
}
