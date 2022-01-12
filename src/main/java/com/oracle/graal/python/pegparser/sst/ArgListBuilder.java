/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.python.pegparser.sst;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

//import com.oracle.graal.python.nodes.PNode;
//import com.oracle.graal.python.nodes.argument.keywords.ConcatKeywordsNodeGen;
//import com.oracle.graal.python.nodes.argument.positional.AppendPositionalStarargsNode;
//import com.oracle.graal.python.nodes.argument.positional.ConcatPositionalStarargsNode;
//import com.oracle.graal.python.nodes.expression.ExpressionNode;
//import com.oracle.graal.python.nodes.literal.KeywordLiteralNode;
//import com.oracle.graal.python.nodes.literal.ListLiteralNode;
//import com.oracle.graal.python.util.PythonUtils;

public final class ArgListBuilder {

    private static final SSTNode[] EMPTY_SSTN = new SSTNode[0];

    private List<SSTNode> args;
    private List<SSTNode> nameArgNodes;
    private List<String> nameArgNames;
    private List<SSTNode> kwArg;
    private SSTNode nakedForComp;
    private BitSet isStarArgBitset;
    private int firstStarArgIndex = -1;

    public ArgListBuilder() {
        // default
    }

    public ArgListBuilder(int argCount, int namedArgCount, int kwArgCount) {
        this.args = new ArrayList<>(argCount);
        this.nameArgNodes = new ArrayList<>(namedArgCount);
        this.nameArgNames = new ArrayList<>(namedArgCount);
        this.kwArg = new ArrayList<>(kwArgCount);
        this.isStarArgBitset = new BitSet(argCount);
    }

    public void addArg(SSTNode value) {
        if (args == null) {
            args = new ArrayList<>();
        }
        args.add(value);
    }

//    public ExpressionNode[] getArgs(SSTreeVisitor<PNode> visitor) {
//        ExpressionNode[] result;
//        if (args == null || args.isEmpty()) {
//            result = ExpressionNode.EMPTY_ARRAY;
//        } else {
//            int len = firstStarArgIndex < 0 ? args.size() : firstStarArgIndex;
//            result = new ExpressionNode[len];
//            for (int i = 0; i < len; i++) {
//                result[i] = (ExpressionNode) args.get(i).accept(visitor);
//            }
//        }
//        return result;
//    }

    public SSTNode[] getArgs() {
        return args == null ? EMPTY_SSTN : args.toArray(new SSTNode[args.size()]);
    }

    protected SSTNode[] getNameArgNodes() {
        return nameArgNodes == null ? EMPTY_SSTN : nameArgNodes.toArray(new SSTNode[nameArgNodes.size()]);
    }

//    protected String[] getNameArgNames() {
//        return nameArgNames == null ? PythonUtils.EMPTY_STRING_ARRAY : nameArgNames.toArray(new String[nameArgNames.size()]);
//    }

    protected int getFirstStarArgIndex() {
        return firstStarArgIndex;
    }

    protected void setFirstStarArgIndex(int starArgIndex) {
        this.firstStarArgIndex = starArgIndex;
    }

    protected boolean isStarArgAt(int i) {
        return isStarArgBitset != null && isStarArgBitset.get(i);
    }

    public SSTNode[] getKwArg() {
        return kwArg == null ? EMPTY_SSTN : kwArg.toArray(new SSTNode[kwArg.size()]);
    }

    public void addNamedArg(String name, SSTNode value) {
        if (nameArgNodes == null) {
            nameArgNodes = new ArrayList<>();
            nameArgNames = new ArrayList<>();
        }
        nameArgNodes.add(value);
        nameArgNames.add(name);
    }

//    public ExpressionNode[] getNameArgs(SSTreeVisitor<PNode> visitor) {
//        ExpressionNode[] result;
//        if (nameArgNodes == null || nameArgNodes.isEmpty()) {
//            result = ExpressionNode.EMPTY_ARRAY;
//        } else {
//            int len = nameArgNodes.size();
//            result = new ExpressionNode[len];
//            for (int i = 0; i < len; i++) {
//                result[i] = new KeywordLiteralNode((ExpressionNode) nameArgNodes.get(i).accept(visitor), nameArgNames.get(i));
//            }
//        }
//        return result;
//    }

    public boolean hasNameArg() {
        return !(nameArgNodes == null || nameArgNodes.isEmpty());
    }

    public boolean hasNameArg(String name) {
        return nameArgNames != null && nameArgNames.contains(name);
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

//    public ExpressionNode getKwArgs(SSTreeVisitor<PNode> visitor) {
//        ExpressionNode result = null;
//        if (kwArg != null && !kwArg.isEmpty()) {
//            int len = kwArg.size();
//            if (len == 1) {
//                result = (ExpressionNode) kwArg.get(0).accept(visitor);
//            } else {
//                ExpressionNode[] expressions = new ExpressionNode[len];
//                for (int i = 0; i < len; i++) {
//                    expressions[i] = (ExpressionNode) kwArg.get(i).accept(visitor);
//                }
//                result = ConcatKeywordsNodeGen.create(expressions);
//            }
//        }
//        return result;
//    }

    public void addStarArg(SSTNode value) {
        addArg(value);
        if (isStarArgBitset == null) {
            isStarArgBitset = new BitSet(args.size());
        }
        if (firstStarArgIndex < 0) {
            firstStarArgIndex = args.size() - 1;
        }
        isStarArgBitset.set(args.size() - 1);
    }

//    public ExpressionNode getStarArgs(SSTreeVisitor<PNode> visitor) {
//        ExpressionNode result = null;
//        if (firstStarArgIndex >= 0) {
//            int starArgCount = args.size() - firstStarArgIndex;
//            if (starArgCount > 1) {
//                result = ListLiteralNode.create(ExpressionNode.EMPTY_ARRAY);
//                for (int i = firstStarArgIndex; i < args.size(); i++) {
//                    ExpressionNode value = (ExpressionNode) args.get(i).accept(visitor);
//                    if (isStarArgAt(i)) {
//                        result = ConcatPositionalStarargsNode.create(result, value);
//                    } else {
//                        result = AppendPositionalStarargsNode.create(result, value);
//                    }
//                }
//            } else {
//                result = (ExpressionNode) args.get(firstStarArgIndex).accept(visitor);
//            }
//        }
//        return result;
//    }

    public boolean hasNakedForComp() {
        return nakedForComp != null;
    }

    public SSTNode getNakedForComp() {
        return nakedForComp;
    }

    public void addNakedForComp(SSTNode node) {
        setNakedForComp(node);
        addArg(node);
    }

    public void setNakedForComp(SSTNode nakedForComp) {
        this.nakedForComp = nakedForComp;
    }
}
