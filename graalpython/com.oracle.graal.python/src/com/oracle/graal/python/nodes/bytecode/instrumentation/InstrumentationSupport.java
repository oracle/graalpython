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

import com.oracle.graal.python.compiler.BytecodeCodeUnit;
import com.oracle.graal.python.compiler.OpCodes;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.bytecode.PBytecodeRootNode;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.nodes.Node;

/**
 * The bytecode interpreter has no AST nodes therefore Truffle AST instrumentation doesn't directly
 * work. We work around that by lazily creating a fake non-executable AST with a list of statement
 * nodes. We keep track of line changes in the bytecode loop and notify the probe nodes manually. We
 * also insert bytecode helper nodes into the statement nodes so that asking the location of a call
 * node obtained from a frame works.
 *
 * @see InstrumentationRoot
 */
public final class InstrumentationSupport extends Node {
    final BytecodeCodeUnit code;
    @Children InstrumentedBytecodeStatement[] statements;
    /*
     * When instrumentation is active, this array is used instead of PBytecodeRootNode#adoptedNodes
     * to hold helper nodes. The actual helper nodes are adopted by the statement nodes in
     * statements array, so this must not be annotated as @Children. When materializing, we cannot
     * reuse existing nodes from adoptedNodes due to possible race conditions.
     */
    @CompilationFinal(dimensions = 1) public Node[] bciToHelperNode;

    final int startLine;

    public InstrumentationSupport(PBytecodeRootNode rootNode) {
        assert rootNode.getSource() != null && rootNode.getSource().hasCharacters();
        code = rootNode.getCodeUnit();
        /*
         * TODO GR-40896 this search for min/max line shouldn't be necessary, but the parser doesn't
         * provide the correct location for f-strings (may be outside of the range of the function's
         * location).
         */
        int minLine = code.startLine;
        int maxLine = code.endLine;
        for (int bci = 0; bci < code.code.length; bci++) {
            minLine = Math.min(minLine, code.bciToLine(bci));
            maxLine = Math.max(maxLine, code.bciToLine(bci));
        }
        startLine = minLine;
        statements = new InstrumentedBytecodeStatement[maxLine - minLine + 1];
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
                InstrumentedBytecodeStatement statement = getStatement(line);
                if (statement == null) {
                    statement = InstrumentedBytecodeStatement.create();
                    try {
                        statement.setSourceSection(rootNode.getSource().createSection(line));
                    } catch (IllegalArgumentException e) {
                        // happens if source file is not available
                        statement.setSourceSection(rootNode.getSource().createUnavailableSection());
                    }
                    setStatement(line, statement);
                }
                statement.coversBci(bci, op.length());
                if (setBreakpoint) {
                    statement.setContainsBreakpoint();
                }
            }
        });
    }

    private InstrumentedBytecodeStatement getStatement(int line) {
        return statements[getStatementIndex(line)];
    }

    private void setStatement(int line, InstrumentedBytecodeStatement statement) {
        statements[getStatementIndex(line)] = statement;
    }

    private int getStatementIndex(int line) {
        return line - startLine;
    }

    private InstrumentableNode.WrapperNode getWrapperAtLine(int line) {
        if (line >= 0) {
            InstrumentableNode node = getStatement(line);
            if (node instanceof InstrumentableNode.WrapperNode) {
                return (InstrumentableNode.WrapperNode) node;
            }
        }
        return null;
    }

    public void notifyStatement(VirtualFrame frame, int prevLine, int nextLine) {
        if (nextLine != prevLine) {
            if (prevLine >= 0) {
                notifyStatementExit(frame, prevLine);
            }
            notifyStatementEnter(frame, nextLine);
        }
    }

    public void notifyStatementEnter(VirtualFrame frame, int line) {
        CompilerAsserts.partialEvaluationConstant(line);
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

    public void notifyStatementExit(VirtualFrame frame, int line) {
        CompilerAsserts.partialEvaluationConstant(line);
        InstrumentableNode.WrapperNode wrapper = getWrapperAtLine(line);
        if (wrapper != null) {
            try {
                wrapper.getProbeNode().onReturnValue(frame, null);
            } catch (Throwable t) {
                handleException(frame, wrapper, t, true);
                throw t;
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
        CompilerAsserts.partialEvaluationConstant(line);
        InstrumentableNode.WrapperNode wrapper = getWrapperAtLine(line);
        if (wrapper != null) {
            handleException(frame, wrapper, exception, false);
        }
    }

    public void insertHelperNode(Node node, int bci) {
        int line = code.bciToLine(bci);
        getStatement(line).insertHelperNode(node, bci);
        bciToHelperNode[bci] = node;
    }
}
