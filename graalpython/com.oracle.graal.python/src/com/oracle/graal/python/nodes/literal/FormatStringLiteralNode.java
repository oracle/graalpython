/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.literal;

import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_STRING;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;

public class FormatStringLiteralNode extends LiteralNode {

    /**
     * The parts array can basically contain only StringLiteralNodes and
     * FormatStringExpressionNodes.
     */
    @Children ExpressionNode[] parts;
    @Children CastToTruffleStringNode[] castToStringNodes;
    @Child TruffleStringBuilder.AppendStringNode appendStringNode;
    @Child TruffleStringBuilder.ToStringNode toStringNode;

    public FormatStringLiteralNode(ExpressionNode[] parts) {
        this.parts = parts;
    }

    public ExpressionNode[] getParts() {
        return parts;
    }

    @Override
    @ExplodeLoop
    public TruffleString execute(VirtualFrame frame) {
        if (parts.length == 0) {
            return T_EMPTY_STRING;
        }

        // Get all the Strings and calculate the resulting String size
        TruffleString[] values = new TruffleString[parts.length];
        int length = 0;
        ensureNodes();
        for (int i = 0; i < parts.length; i++) {
            Object stringPart = parts[i].execute(frame);
            try {
                values[i] = castToStringNodes[i].execute(stringPart);
            } catch (CannotCastException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
            length += values[i].byteLength(TS_ENCODING);
        }

        // Create the result
        TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING, length);
        for (TruffleString value : values) {
            appendStringNode.execute(sb, value);
        }
        return toStringNode.execute(sb);
    }

    private void ensureNodes() {
        if (castToStringNodes == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            CastToTruffleStringNode[] nodes = new CastToTruffleStringNode[parts.length];
            for (int i = 0; i < nodes.length; i++) {
                nodes[i] = CastToTruffleStringNode.create();
            }
            castToStringNodes = insert(nodes);
        }
        if (appendStringNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            appendStringNode = insert(TruffleStringBuilder.AppendStringNode.create());
        }
        if (toStringNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            toStringNode = insert(TruffleStringBuilder.ToStringNode.create());
        }
    }
}
