/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.bytecode.instrumentation;

import com.oracle.graal.python.compiler.CodeUnit;
import com.oracle.graal.python.compiler.OpCodes;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.bytecode.PBytecodeRootNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.nodes.Node;

public final class InstrumentationSupport extends Node {
    final CodeUnit code;
    @Children InstrumentedBytecodeStatement[] lineToNode;
    @CompilationFinal(dimensions = 1) public Node[] bciToHelperNode;

    public InstrumentationSupport(PBytecodeRootNode rootNode) {
        assert rootNode.getSource() != null && rootNode.getSource().hasCharacters();
        code = rootNode.getCodeUnit();
        lineToNode = new InstrumentedBytecodeStatement[code.endLine + 1];
        bciToHelperNode = new Node[code.code.length];
        boolean[] loadedBreakpoint = new boolean[1];
        code.iterateBytecode((bci, op, oparg, followingArgs) -> {
            boolean setBreakpoint = false;
            if ((op == OpCodes.LOAD_NAME || op == OpCodes.LOAD_GLOBAL) && BuiltinNames.T_BREAKPOINT.equals(code.names[oparg])) {
                loadedBreakpoint[0] = true;
            } else {
                if (op == OpCodes.CALL_FUNCTION && loadedBreakpoint[0]) {
                    setBreakpoint = true;
                }
                loadedBreakpoint[0] = false;
            }
            int line = code.bciToLine(bci);
            if (line >= 0) {
                if (lineToNode[line] == null) {
                    InstrumentedBytecodeStatement statement = InstrumentedBytecodeStatement.create();
                    statement.setSourceSection(rootNode.getSource().createSection(line));
                    lineToNode[line] = statement;
                }
                lineToNode[line].coversBci(bci, op.length());
                if (setBreakpoint) {
                    lineToNode[line].setContainsBreakpoint();
                }
            }
        });
    }

    private InstrumentableNode.WrapperNode getWrapperAtLine(int line) {
        if (line >= 0 && line < lineToNode.length) {
            InstrumentableNode node = lineToNode[line];
            if (node instanceof InstrumentableNode.WrapperNode) {
                return (InstrumentableNode.WrapperNode) node;
            }
        }
        return null;
    }

    public void notifyStatement(VirtualFrame frame, int prevLine, int line) {
        if (line != prevLine) {
            if (prevLine >= 0) {
                InstrumentableNode.WrapperNode wrapper = getWrapperAtLine(prevLine);
                if (wrapper != null) {
                    try {
                        wrapper.getProbeNode().onReturnValue(frame, null);
                    } catch (Throwable t) {
                        handleException(frame, wrapper, t, true);
                        throw t;
                    }
                }
            }
            InstrumentableNode.WrapperNode wrapper = getWrapperAtLine(line);
            if (wrapper != null) {
                try {
                    wrapper.getProbeNode().onEnter(frame);
                } catch (Throwable t) {
                    handleException(frame, wrapper, t, false);
                    throw t;
                }
            }
        }
    }

    private static void handleException(VirtualFrame frame, InstrumentableNode.WrapperNode wrapper, Throwable t, boolean isReturnCalled) {
        Object result = wrapper.getProbeNode().onReturnExceptionalOrUnwind(frame, t, isReturnCalled);
        if (result == ProbeNode.UNWIND_ACTION_REENTER) {
            throw CompilerDirectives.shouldNotReachHere("Unwind not supported on statement level");
        } else if (result != null) {
            throw CompilerDirectives.shouldNotReachHere("Cannot replace a return of a statement");
        }
    }

    public void notifyException(VirtualFrame frame, int line, Throwable exception) {
        InstrumentableNode.WrapperNode wrapper = getWrapperAtLine(line);
        if (wrapper != null) {
            wrapper.getProbeNode().onReturnExceptionalOrUnwind(frame, exception, false);
        }
    }

    public void insertHelperNode(Node node, int bci) {
        int line = code.bciToLine(bci);
        lineToNode[line].insertHelperNode(node, bci);
        bciToHelperNode[bci] = node;
    }
}
