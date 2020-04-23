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
package com.oracle.graal.python.test.interop;

import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.instrumentation.NodeObjectDescriptor;
import com.oracle.graal.python.test.PythonTests;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.LibraryFactory;
import com.oracle.truffle.api.nodes.Node;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.Source;
import org.junit.Test;

@SuppressWarnings("all")
public class ReadAndWriteVariableTagsTests extends PythonTests {

    static final InteropLibrary INTEROP = LibraryFactory.resolve(InteropLibrary.class).getUncached();

    private static Context newContext(Engine engine) {
        return Context.newBuilder().allowExperimentalOptions(true).allowAllAccess(true).engine(engine).build();
    }

    @Test
    public void testInModule() throws Exception {
        String code = "a = 0\n" +
                        "b = a\n" +
                        "c = a + a + b";
        HashMap<String, Set<InstrumentableNode>> nodes = checkReadVariableTagAndWriteVariableTag(code);
        // checkWriteNodeTag(nodes, "a", 0, 5);
        checkReadNodeTag(nodes, "a", 10, 11);
        checkReadNodeTag(nodes, "a", 16, 17);
        checkReadNodeTag(nodes, "a", 20, 21);
        checkReadNodeTag(nodes, "b", 24, 25);
    }

    @Test
    public void testFunction01() throws Exception {
        String code = "def fn(arg):\n" +
                        "  arg = arg + 1\n" +
                        "  return arg\n" +
                        "fn(1)";
        HashMap<String, Set<InstrumentableNode>> nodes = checkReadVariableTagAndWriteVariableTag(code);
        checkReadNodeTag(nodes, "arg", 21, 24);
        checkReadNodeTag(nodes, "arg", 38, 41);
        checkReadNodeTag(nodes, "fn", 42, 44);
    }

    @Test
    public void testFunction02() throws Exception {
        String code = "c = 2\n" + "def foo(a, b): \n" +
                        "  return a + b + c\n" +
                        "foo(1, 2)";
        HashMap<String, Set<InstrumentableNode>> nodes = checkReadVariableTagAndWriteVariableTag(code);
        checkReadNodeTag(nodes, "a", 31, 32);
        checkReadNodeTag(nodes, "b", 35, 36);
        checkReadNodeTag(nodes, "c", 39, 40);
        checkReadNodeTag(nodes, "foo", 41, 44);
    }

    @Test
    public void testGenerator01() throws Exception {
        String code = "g = (x*x for x in range(10))\n" +
                        "for i in g:\n" +
                        "  i";
        HashMap<String, Set<InstrumentableNode>> nodes = checkReadVariableTagAndWriteVariableTag(code);
        checkReadNodeTag(nodes, "g", 38, 39);
        checkReadNodeTag(nodes, "x", 7, 8);
        checkReadNodeTag(nodes, "x", 5, 6);
        checkReadNodeTag(nodes, "range", 18, 23);
        checkReadNodeTag(nodes, "i", 43, 44);
    }

    @Test
    public void testGenerator02() throws Exception {
        String code = "def fn(arg):\n" +
                        "  g = (x*x for x in range(arg))\n" +
                        "  return g\n" +
                        "for i in fn(10):\n" +
                        "  i";
        HashMap<String, Set<InstrumentableNode>> nodes = checkReadVariableTagAndWriteVariableTag(code);
        checkReadNodeTag(nodes, "arg", 39, 42);
        checkReadNodeTag(nodes, "g", 54, 55);
        checkReadNodeTag(nodes, "fn", 65, 67);
        checkReadNodeTag(nodes, "x", 22, 23);
        checkReadNodeTag(nodes, "x", 20, 21);
        checkReadNodeTag(nodes, "range", 33, 38);
        checkReadNodeTag(nodes, "i", 75, 76);
    }

    @Test
    public void testGenerator03() throws Exception {
        String code = "def fn(item1, item2, item3):\n" +
                        "  yield item1\n" +
                        "  yield item2\n" +
                        "  yield item3\n" +
                        "g = fn(1, 2, 3)\n" +
                        "for i in g:\n" +
                        "  i\n";
        HashMap<String, Set<InstrumentableNode>> nodes = checkReadVariableTagAndWriteVariableTag(code);
        checkReadNodeTag(nodes, "item1", 37, 42);
        checkReadNodeTag(nodes, "item2", 51, 56);
        checkReadNodeTag(nodes, "item3", 65, 70);
        checkReadNodeTag(nodes, "g", 96, 97);
        checkReadNodeTag(nodes, "fn", 75, 77);
        checkReadNodeTag(nodes, "i", 101, 102);
    }

    @Test
    public void testListComp01() throws Exception {
        String code = "y = 1\n" +
                        "[x**y for x in range(20) if x*y % 3]\n";
        HashMap<String, Set<InstrumentableNode>> nodes = checkReadVariableTagAndWriteVariableTag(code);
        checkReadNodeTag(nodes, "x", 7, 8);
        checkReadNodeTag(nodes, "x", 34, 35);
        checkReadNodeTag(nodes, "range", 21, 26);
        checkReadNodeTag(nodes, "y", 10, 11);
        checkReadNodeTag(nodes, "y", 36, 37);
    }

    private void printNodes(HashMap<String, Set<InstrumentableNode>> nodesMap) {
        for (String name : nodesMap.keySet()) {
            for (InstrumentableNode node : nodesMap.get(name)) {
                StringBuilder sb = new StringBuilder();
                sb.append("check");
                if (node.hasTag(StandardTags.ReadVariableTag.class)) {
                    sb.append("ReadNodeTag(nodes, \"");
                } else {
                    sb.append("WriteNodeTag(nodes, \"");
                }
                sb.append(name);
                sb.append("\", ").append(((PNode) node).getSourceSection().getCharIndex());
                sb.append(", ").append(((PNode) node).getSourceSection().getCharEndIndex());
                sb.append(");");
                System.out.println(sb.toString());
            }
        }
    }

    private void checkWriteNodeTag(HashMap<String, Set<InstrumentableNode>> nodesMap, String name, int startOffset, int endOffset) {
        Set<InstrumentableNode> nodes = nodesMap.get(name);
        assertTrue("Any node with name '" + name + "' was found.", nodes != null);
        for (InstrumentableNode node : nodes) {
            if (node.hasTag(StandardTags.WriteVariableTag.class) && ((PNode) node).getSourceSection().getCharIndex() == startOffset &&
                            ((PNode) node).getSourceSection().getCharEndIndex() == endOffset) {
                return;
            }
        }
        assertTrue("Any write node with name '" + name + "' and source section[" + startOffset + ", " + endOffset + "] was found.", false);
    }

    private void checkReadNodeTag(HashMap<String, Set<InstrumentableNode>> nodesMap, String name, int startOffset, int endOffset) {
        Set<InstrumentableNode> nodes = nodesMap.get(name);
        assertTrue("Any node with name '" + name + "' was found.", nodes != null);
        for (InstrumentableNode node : nodes) {
            if (node.hasTag(StandardTags.ReadVariableTag.class) && ((PNode) node).getSourceSection().getCharIndex() == startOffset &&
                            ((PNode) node).getSourceSection().getCharEndIndex() == endOffset) {
                return;
            }
        }
        assertTrue("Any read node with name '" + name + "' and source section[" + startOffset + ", " + endOffset + "] was found.", false);
    }

    private HashMap<String, Set<InstrumentableNode>> checkReadVariableTagAndWriteVariableTag(String code) throws Exception {
        Engine engine = Engine.newBuilder().build();
        Context newContext = newContext(engine);
        newContext.initialize("python");
        Instrument envInstr = newContext.getEngine().getInstruments().get("TestPythonInstrumentRW");

        Source source = Source.newBuilder("python", code, "testing").build();
        TruffleInstrument.Env env = envInstr.lookup(TestEnvProvider.class).env;
        SourceSectionFilter filter = SourceSectionFilter.newBuilder().lineIn(1, source.getLineCount()).tagIs(StandardTags.ReadVariableTag.class, StandardTags.WriteVariableTag.class).build();
        HashMap<String, Set<InstrumentableNode>> nodesMap = new HashMap<>();
        env.getInstrumenter().attachExecutionEventListener(filter, new ReadVariableTagAndWriteVariableTagListener(nodesMap));

        newContext.eval(source);
        return nodesMap;
    }

    private static class TestEnvProvider {

        TruffleInstrument.Env env;

        TestEnvProvider(TruffleInstrument.Env env) {
            this.env = env;
        }
    }

    @TruffleInstrument.Registration(id = "TestPythonInstrumentRW", services = TestEnvProvider.class)
    public static class TestPythonInstrument extends TruffleInstrument {

        @Override
        protected void onCreate(final TruffleInstrument.Env env) {
            env.registerService(new TestEnvProvider(env));
        }
    }

    private static class ReadVariableTagAndWriteVariableTagListener implements ExecutionEventListener {

        // map of the variable name -> list of nodes
        private final HashMap<String, Set<InstrumentableNode>> result;

        public ReadVariableTagAndWriteVariableTagListener(HashMap<String, Set<InstrumentableNode>> resultMap) {
            this.result = resultMap;
        }

        @Override
        public void onEnter(EventContext context, VirtualFrame frame) {
            Node node = context.getInstrumentedNode();
            InstrumentableNode inode = (InstrumentableNode) node;

            NodeObjectDescriptor descr = (NodeObjectDescriptor) inode.getNodeObject();
            String name = null;
            try {
                if (inode.hasTag(StandardTags.WriteVariableTag.class)) {
                    name = (String) INTEROP.readMember(descr, StandardTags.WriteVariableTag.NAME);
                } else if (inode.hasTag(StandardTags.ReadVariableTag.class)) {
                    name = (String) INTEROP.readMember(descr, StandardTags.ReadVariableTag.NAME);
                }
            } catch (UnsupportedMessageException | UnknownIdentifierException ex) {

            }
            assertTrue("Attribute name was not found", name != null);
            Set<InstrumentableNode> nodes = result.get(name);
            if (nodes == null) {
                nodes = new HashSet<>();
                result.put(name, nodes);
            }
            nodes.add(inode);
        }

        @Override
        public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
        }

        @Override
        public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
        }
    }
}
