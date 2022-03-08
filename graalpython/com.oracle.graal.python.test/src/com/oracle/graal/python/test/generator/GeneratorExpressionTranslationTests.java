/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.test.generator;

import static com.oracle.graal.python.test.PythonTests.assertPrints;
import static com.oracle.graal.python.test.PythonTests.getParseResult;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.oracle.graal.python.nodes.function.FunctionDefinitionNode;
import com.oracle.graal.python.nodes.function.GeneratorExpressionNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;

public class GeneratorExpressionTranslationTests {
    private static RootNode getFunctionRoot(Node root, String functionName) {
        NodeUtil.findAllNodeInstances(root, FunctionDefinitionNode.class);
        for (FunctionDefinitionNode node : NodeUtil.findAllNodeInstances(root, FunctionDefinitionNode.class)) {
            if (node.getFunctionName().toJavaStringUncached().equals(functionName)) {
                return node.getFunctionRoot();
            }
        }
        return null;
    }

    @Test
    public void generatorExpressionAsIterator() {

        String source = "def foo():\n" + //
                        "    n = 5\n" + //
                        "    for i in (x for x in range(n)):\n" + //
                        "        item = i\n" +
                        "        print(item)";
        RootNode parsed = getParseResult(source);
        RootNode root = getFunctionRoot(parsed, "foo");
        int genexp = NodeUtil.findAllNodeInstances(root, GeneratorExpressionNode.class).size();
        assertTrue(genexp == 1);
    }

    @Test
    public void generatorExpressionAsArgumentToConstructor() {

        String source = "def foo():\n" + //
                        "    n = 5\n" + //
                        "    return list(x for x in range(n))\n";

        RootNode parsed = getParseResult(source);
        RootNode root = getFunctionRoot(parsed, "foo");
        int genexp = NodeUtil.findAllNodeInstances(root, GeneratorExpressionNode.class).size();
        assertTrue(genexp != 0);
    }

    @Test
    public void assignedToLocalVar() {

        String source = "def foo():\n" + //
                        "    n = 5\n" + //
                        "    ll = (x for x in range(n))\n" + //
                        "    return list(ll)\n";

        RootNode parsed = getParseResult(source);
        RootNode root = getFunctionRoot(parsed, "foo");
        int genexp = NodeUtil.findAllNodeInstances(root, GeneratorExpressionNode.class).size();
        assertTrue(genexp != 0);
    }

    @Test
    public void escapeByReturn() {

        String source = "def foo():\n" + //
                        "    n = 5\n" + //
                        "    ll = (x for x in range(n))\n" + //
                        "    return ll\n";

        RootNode parsed = getParseResult(source);
        RootNode root = getFunctionRoot(parsed, "foo");
        int genexp = NodeUtil.findAllNodeInstances(root, GeneratorExpressionNode.class).size();
        assertTrue(genexp != 0);
    }

    @Test
    public void escapeByStore() {

        String source = "LIST = []\n" + //
                        "def foo():\n" + //
                        "    n = 5\n" + //
                        "    ll = (x for x in range(n))\n" + //
                        "    LIST[0] = ll\n";

        RootNode parsed = getParseResult(source);
        RootNode root = getFunctionRoot(parsed, "foo");
        int genexp = NodeUtil.findAllNodeInstances(root, GeneratorExpressionNode.class).size();
        assertTrue(genexp != 0);
    }

    @Test
    public void escapeByCall() {
        String source = "LIST = []\n" + //
                        "def foo():\n" + //
                        "    n = 5\n" + //
                        "    ll = (x for x in range(n))\n" + //
                        "    LIST.append(ll)\n";

        RootNode parsed = getParseResult(source);
        RootNode root = getFunctionRoot(parsed, "foo");
        int genexp = NodeUtil.findAllNodeInstances(root, GeneratorExpressionNode.class).size();
        assertTrue(genexp != 0);
    }

    @Test
    public void yieldLambda() {
        String source = "print(type((lambda: (yield))()).__name__)\n";
        assertPrints("generator\n", source);
    }
}
