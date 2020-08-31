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

import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.graal.python.nodes.statement.TryFinallyNode;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes.ExceptionState;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes.SetCaughtExceptionNode;
import com.oracle.graal.python.parser.GeneratorInfo;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.YieldException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.profiles.BranchProfile;

public class GeneratorTryFinallyNode extends TryFinallyNode implements GeneratorControlNode {
    @Child private GeneratorAccessNode gen = GeneratorAccessNode.create();
    private final BranchProfile hasPExceptionProfile = BranchProfile.create();
    private final BranchProfile hasControlFlowExceptionProfile = BranchProfile.create();

    private final int finallyFlag;
    private final int activeExceptionIndex;

    public GeneratorTryFinallyNode(StatementNode body, StatementNode finalbody, GeneratorInfo.Mutable generatorInfo) {
        super(body, finalbody);
        this.finallyFlag = generatorInfo.nextActiveFlagIndex();
        this.activeExceptionIndex = generatorInfo.nextExceptionSlotIndex();
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        ExceptionState savedExceptionState = null;
        PException activePException = null;
        ControlFlowException activeControlFlowException = null;
        if (gen.isActive(frame, finallyFlag)) {
            Object activeException = gen.getActiveException(frame, activeExceptionIndex);
            if (activeException instanceof PException) {
                hasPExceptionProfile.enter();
                activePException = (PException) activeException;
            } else if (activeException instanceof ControlFlowException) {
                hasControlFlowExceptionProfile.enter();
                activeControlFlowException = (ControlFlowException) activeException;
            }
        } else {
            try {
                getBody().executeVoid(frame);
            } catch (PException e) {
                // any thrown Python exception is visible in the finally block
                hasPExceptionProfile.enter();
                activePException = e;
                e.setCatchingFrameReference(frame, this);
                e.markFrameEscaped();
                tryChainPreexistingException(frame, e);
                gen.setActiveException(frame, activeExceptionIndex, e);
            } catch (YieldException e) {
                throw e;
            } catch (ControlFlowException e) {
                hasControlFlowExceptionProfile.enter();
                // We need to save the exception to be rethrown at the end. Return may carry a value
                activeControlFlowException = e;
                gen.setActiveException(frame, activeExceptionIndex, e);
            } catch (Throwable e) {
                PException pe = wrapJavaExceptionIfApplicable(e);
                if (pe == null) {
                    throw e;
                }
                hasPExceptionProfile.enter();
                activePException = pe;
                pe.setCatchingFrameReference(frame, this);
                pe.markFrameEscaped();
                tryChainPreexistingException(frame, pe);
                gen.setActiveException(frame, activeExceptionIndex, pe);
            }
            gen.setActive(frame, finallyFlag, true);
        }
        if (activePException != null) {
            savedExceptionState = saveExceptionState(frame);
            SetCaughtExceptionNode.execute(frame, activePException);
        }
        try {
            executeFinalBody(frame);
        } catch (PException handlerException) {
            if (activePException != null) {
                tryChainExceptionFromHandler(handlerException, activePException);
            }
            throw handlerException;
        } catch (Throwable e) {
            PException handlerException = wrapJavaExceptionIfApplicable(e);
            if (handlerException == null) {
                throw e;
            }
            if (activePException != null) {
                tryChainExceptionFromHandler(handlerException, activePException);
            }
            throw handlerException.getExceptionForReraise();
        } finally {
            if (activePException != null) {
                restoreExceptionState(frame, savedExceptionState);
            }
        }
        reset(frame);
        if (activePException != null) {
            throw activePException.getExceptionForReraise();
        } else if (activeControlFlowException != null) {
            throw activeControlFlowException;
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
