/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.argument.keywords;

import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;

public class ExecuteKeywordArgumentsNode extends Node {

    @Children private final ExpressionNode[] arguments;
    @Child private CompactKeywordsNode compactNode;

    ExecuteKeywordArgumentsNode(ExpressionNode[] arguments) {
        this.arguments = arguments;
    }

    public static ExecuteKeywordArgumentsNode create(ExpressionNode[] arguments) {
        return new ExecuteKeywordArgumentsNode(arguments);
    }

    @ExplodeLoop
    public PKeyword[] execute(VirtualFrame frame) {
        int length = arguments.length;
        CompilerAsserts.partialEvaluationConstant(length);
        PKeyword[] keywords = new PKeyword[length];
        int reshape = 0;
        for (int i = 0; i < length; i++) {
            Object o = arguments[i].execute(frame);
            if (o instanceof PKeyword) {
                keywords[i] = (PKeyword) o;
            } else {
                reshape++;
            }
        }

        if (reshape > 0) {
            return getCompactNode().execute(keywords, reshape);
        } else {
            return keywords;
        }
    }

    private CompactKeywordsNode getCompactNode() {
        if (compactNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            compactNode = insert(CompactKeywordsNodeGen.create());
        }
        return compactNode;
    }

}
