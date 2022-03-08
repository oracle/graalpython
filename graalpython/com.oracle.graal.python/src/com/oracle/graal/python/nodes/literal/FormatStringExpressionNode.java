/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.modules.BuiltinFunctions;
import com.oracle.graal.python.builtins.modules.BuiltinFunctionsFactory;
import com.oracle.graal.python.lib.PyObjectAsciiNode;
import com.oracle.graal.python.lib.PyObjectReprAsTruffleStringNode;
import com.oracle.graal.python.lib.PyObjectStrAsTruffleStringNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.parser.sst.StringLiteralSSTNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;

public class FormatStringExpressionNode extends LiteralNode {

    /**
     * This is the expression itself
     */
    @Child private ExpressionNode expression;
    /**
     * In current implementation can be the specifier defined via FormatStringLiteralNode. It can be
     * null, if there is now specifier.
     */
    @Child private ExpressionNode specifier;

    private final StringLiteralSSTNode.FormatStringConversionType conversionType;

    @Child private BuiltinFunctions.FormatNode formatNode;
    @Child private PyObjectStrAsTruffleStringNode strNode;
    @Child private PyObjectReprAsTruffleStringNode reprNode;
    @Child private PyObjectAsciiNode asciiNode;

    public FormatStringExpressionNode(ExpressionNode expression, ExpressionNode specifier, StringLiteralSSTNode.FormatStringConversionType conversionType) {
        this.expression = expression;
        this.specifier = specifier;
        this.conversionType = conversionType;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object result = expression.execute(frame);
        if (null != conversionType) {
            switch (conversionType) {
                case STR_CONVERTION:
                    result = getStrNode().execute(frame, result);
                    break;
                case REPR_CONVERSION:
                    result = getReprNode().execute(frame, result);
                    break;
                case ASCII_CONVERSION:
                    result = getAsciiNode().execute(frame, result);
                    break;
                default:
                    break;
            }
        }
        Object specifierObject = specifier == null ? T_EMPTY_STRING : specifier.execute(frame);
        result = getFormatNode().execute(frame, result, specifierObject);
        return result;
    }

    private PyObjectAsciiNode getAsciiNode() {
        if (asciiNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            asciiNode = insert(PyObjectAsciiNode.create());
        }
        return asciiNode;
    }

    private BuiltinFunctions.FormatNode getFormatNode() {
        if (formatNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            formatNode = insert(BuiltinFunctionsFactory.FormatNodeFactory.create());
        }
        return formatNode;
    }

    private PyObjectStrAsTruffleStringNode getStrNode() {
        if (strNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            strNode = insert(PyObjectStrAsTruffleStringNode.create());
        }
        return strNode;
    }

    private PyObjectReprAsTruffleStringNode getReprNode() {
        if (reprNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            reprNode = insert(PyObjectReprAsTruffleStringNode.create());
        }
        return reprNode;
    }

}
