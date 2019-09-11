/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.python.parser.sst;

import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.generator.DictConcatNode;
import com.oracle.graal.python.nodes.literal.KeywordLiteralNode;
import java.util.ArrayList;
import java.util.List;

public final class ArgListBuilder {

    private static final SSTNode[] EMPTY_SSTN = new SSTNode[0];
    private static final ExpressionNode[] EMPTY = new ExpressionNode[0];

    private List<SSTNode> args;
    private List<SSTNode> nameArgNodes;
    private List<String> nameArgNames;
    private List<SSTNode> starArg;
    private List<SSTNode> kwArg;

    public void addArg(SSTNode value) {
        if (args == null) {
            args = new ArrayList<>();
        }
        args.add(value);
    }

    public ExpressionNode[] getArgs(SSTreeVisitor<PNode> visitor) {
        ExpressionNode[] result;
        if (args == null || args.isEmpty()) {
            result = EMPTY;
        } else {
            int len = args.size();
            result = new ExpressionNode[len];
            for (int i = 0; i < len; i++) {
                result[i] = (ExpressionNode) args.get(i).accept(visitor);
            }
        }
        return result;
    }

    public SSTNode[] getArgs() {
        return args == null ? EMPTY_SSTN : args.toArray(new SSTNode[args.size()]);
    }

    public void addNamedArg(String name, SSTNode value) {
        if (nameArgNodes == null) {
            nameArgNodes = new ArrayList<>();
            nameArgNames = new ArrayList<>();
        }
        nameArgNodes.add(value);
        nameArgNames.add(name);
    }

    public ExpressionNode[] getNameArgs(SSTreeVisitor<PNode> visitor) {
        ExpressionNode[] result;
        if (nameArgNodes == null || nameArgNodes.isEmpty()) {
            result = EMPTY;
        } else {
            int len = nameArgNodes.size();
            result = new ExpressionNode[len];
            for (int i = 0; i < len; i++) {
                result[i] = new KeywordLiteralNode((ExpressionNode) nameArgNodes.get(i).accept(visitor), nameArgNames.get(i));
            }
        }
        return result;
    }

    public boolean hasNameArg() {
        return !(nameArgNodes == null || nameArgNodes.isEmpty());
    }

    public void addKwArg(SSTNode value) {
        if (kwArg == null) {
            kwArg = new ArrayList<>();
        }
        kwArg.add(value);
    }

    public boolean hasKwArg() {
        return kwArg != null;
    }

    public ExpressionNode getKwArgs(SSTreeVisitor<PNode> visitor) {
        ExpressionNode result = null;
        if (kwArg != null && !kwArg.isEmpty()) {
            int len = kwArg.size();
            if (len == 1) {
                result = (ExpressionNode) kwArg.get(0).accept(visitor);
            } else {
                ExpressionNode[] expressions = new ExpressionNode[len];
                for (int i = 0; i < len; i++) {
                    expressions[i] = (ExpressionNode) kwArg.get(i).accept(visitor);
                }
                result = DictConcatNode.create(expressions);
            }
        }
        return result;
    }

    public void addStarArg(SSTNode value) {
        if (starArg == null) {
            starArg = new ArrayList<>();
        }
        starArg.add(value);
    }

    public ExpressionNode getStarArgs(SSTreeVisitor<PNode> visitor) {
        ExpressionNode result = null;
        if (starArg != null && !starArg.isEmpty()) {
            result = (ExpressionNode) starArg.get(0).accept(visitor);
            for (int i = 1; i < starArg.size(); i++) {
                result = BinaryArithmetic.Add.create(result, (ExpressionNode) starArg.get(i).accept(visitor));
            }
        }
        return result;
    }

}
