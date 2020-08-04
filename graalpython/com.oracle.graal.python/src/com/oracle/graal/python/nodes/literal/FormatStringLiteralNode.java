/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;

import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;

public class FormatStringLiteralNode extends LiteralNode {
    private static final String EMPTY_STRING = "";

    private final StringPart[] parts;

    /**
     * The size of this array is total number of actual String literals and expressions inside the
     * f-string. Slots that do not represent actual String literal are left {@code null}, for each
     * such slot there is an expression in {@link #expressions}.
     */
    @CompilationFinal(dimensions = 1) private final String[] literals;
    @Children ExpressionNode[] expressions;
    @Children CastToJavaStringNode[] castToJavaStringNodes;

    public FormatStringLiteralNode(StringPart[] parts, ExpressionNode[] exprs, String[] literals) {
        this.parts = parts;
        this.expressions = exprs;
        this.literals = literals;
    }

    public static final class StringPart {
        /**
         * Marks, whether the value is formatted string
         */
        public final boolean isFormatString;
        public final String text;

        public StringPart(String text, boolean isFormatString) {
            this.text = text;
            this.isFormatString = isFormatString;
        }

        public boolean isFormatString() {
            return isFormatString;
        }

        public String getText() {
            return text;
        }
    }

    public StringPart[] getParts() {
        return parts;
    }

    @Override
    @ExplodeLoop
    public Object execute(VirtualFrame frame) {
        if (parts.length == 0) {
            return EMPTY_STRING;
        }

        // Get all the Strings and calculate the resulting String size
        String[] values = Arrays.copyOf(literals, literals.length);
        int exprIndex = 0;
        int length = 0;
        ensureCastNodes();
        for (int i = 0; i < literals.length; i++) {
            if (values[i] == null) {
                values[i] = castToJavaStringNodes[exprIndex].execute(expressions[exprIndex].execute(frame));
                exprIndex++;
            }
            length += values[i].length();
        }

        // Create the result
        char[] result = new char[length];
        int nextIndex = 0;
        for (String value : values) {
            PythonUtils.getChars(value, 0, value.length(), result, nextIndex);
            nextIndex += value.length();
        }
        return new String(result);
    }

    private void ensureCastNodes() {
        if (castToJavaStringNodes == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            CastToJavaStringNode[] nodes = new CastToJavaStringNode[expressions.length];
            for (int i = 0; i < nodes.length; i++) {
                nodes[i] = CastToJavaStringNode.create();
            }
            castToJavaStringNodes = insert(nodes);
        }
    }
}
