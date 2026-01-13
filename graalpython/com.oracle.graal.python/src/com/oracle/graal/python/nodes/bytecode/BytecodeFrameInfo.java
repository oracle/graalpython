/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.bytecode;

import com.oracle.graal.python.compiler.CodeUnit;
import com.oracle.graal.python.compiler.OpCodesConstants;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.Frame;

public class BytecodeFrameInfo implements FrameInfo {
    @CompilationFinal PBytecodeRootNode rootNode;

    void setRootNode(PBytecodeRootNode rootNode) {
        this.rootNode = rootNode;
    }

    @Override
    public PBytecodeRootNode getRootNode() {
        return rootNode;
    }

    public int getBci(Frame frame) {
        if (frame.isInt(rootNode.bcioffset)) {
            return frame.getInt(rootNode.bcioffset);
        } else {
            return -1;
        }
    }

    public int getLineForBci(int bci) {
        return rootNode.bciToLine(bci);
    }

    public int getLine(Frame frame) {
        return getLineForBci(getBci(frame));
    }

    @Override
    public int getFirstLineNumber() {
        return rootNode.getFirstLineno();
    }

    public Object getYieldFrom(Frame generatorFrame, int bci, int stackTop) {
        /* Match the `yield from` bytecode pattern and get the object from stack */
        if (bci > 3 && bci < rootNode.bytecode.length && rootNode.bytecode[bci - 3] == OpCodesConstants.SEND && rootNode.bytecode[bci - 1] == OpCodesConstants.YIELD_VALUE &&
                        rootNode.bytecode[bci] == OpCodesConstants.RESUME_YIELD) {
            return generatorFrame.getObject(stackTop);
        }
        return null;
    }

    @Override
    public CodeUnit getCodeUnit() {
        return rootNode.getCodeUnit();
    }

    @Override
    public boolean includeInTraceback() {
        return rootNode.frameIsVisibleToPython();
    }
}
