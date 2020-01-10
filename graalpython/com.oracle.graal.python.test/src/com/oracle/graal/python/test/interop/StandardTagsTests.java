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

import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.function.InnerRootNode;
import com.oracle.graal.python.test.PythonTests;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.LibraryFactory;
import com.oracle.truffle.api.nodes.Node;
import java.util.HashMap;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.Source;
import org.junit.Assert;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import org.junit.Test;

public class StandardTagsTests extends PythonTests {

    static final InteropLibrary INTEROP = LibraryFactory.resolve(InteropLibrary.class).getUncached();

    private static Context newContext(Engine engine) {
        return Context.newBuilder().allowExperimentalOptions(true).allowAllAccess(true).engine(engine).build();
    }

    @Test
    public void testRootBodyTagModule() throws Exception {
        String code = "import time\n" + "time.gmtime()\n";
        checkRootTagAndRootBodyTag(code);
    }

    @Test
    public void testRootBodyTagGenerator01() throws Exception {
        String code = "def test():\n" + "  yield 22\n" + "for i in test():\n" + "  i\n";
        checkRootTagAndRootBodyTag(code);
    }

    @Test
    public void testRootBodyTagGenerator02() throws Exception {
        String code = "def test():\n" + "  yield 22\n" + "  yield 42\n" + "  yield 62\n" + "for i in test():\n" + "  i\n";
        checkRootTagAndRootBodyTag(code);
    }

    @Test
    public void testRootBodyTagGenerator03() throws Exception {
        String code = "def fn(a, b):\n" + "  yield a\n" + "  yield b\n" + "for i in fn(3,4):\n" + "  i\n";
        checkRootTagAndRootBodyTag(code);
    }

    @Test
    public void testRootBodyTagFunction01() throws Exception {
        String code = "def test():\n" + "  return 22\n" + "test()";
        checkRootTagAndRootBodyTag(code);
    }

    @Test
    public void testRootBodyTagFunction02() throws Exception {
        String code = "def test(a,b):\n" + "  return a + b\n" + "test(1, 2)";
        HashMap<InstrumentableNode, InstrumentableNode> rootsMap = checkRootTagAndRootBodyTag(code);
        InnerRootNode inner = findInnerRootNodeWithEndOffset(rootsMap, 30);
        checkBodyPosition(rootsMap, inner, 17, 29);
    }

    @Test
    public void testRootBodyTagFunction03() throws Exception {
        String code = "def test(a,b):\n" + "  '''This is a simple doc'''\n" + "  return a + b\n" + "test(1, 2)";
        HashMap<InstrumentableNode, InstrumentableNode> rootsMap = checkRootTagAndRootBodyTag(code);
        InnerRootNode inner = findInnerRootNodeWithEndOffset(rootsMap, 59);
        checkBodyPosition(rootsMap, inner, 46, 58);
    }

    @Test
    public void testRootBodyTagFunction04() throws Exception {
        String code = "def test():\n" + "  '''Function without body'''\n" + "test()";
        HashMap<InstrumentableNode, InstrumentableNode> rootsMap = checkRootTagAndRootBodyTag(code, true);
        InnerRootNode inner = findInnerRootNodeWithEndOffset(rootsMap, 42);
        Assert.assertEquals(null, rootsMap.get(inner));
    }

    @Test
    public void testRootBodyTagLambda01() throws Exception {
        String code = "x = lambda a, b, c : a + b + c\n" + "x(5, 6, 2)";
        checkRootTagAndRootBodyTag(code);
    }

    @Test
    public void testRootBodyTagClass01() throws Exception {
        String code = "class MyClass:\n" + "  x = 5\n" + "m = MyClass()\n";
        HashMap<InstrumentableNode, InstrumentableNode> rootsMap = checkRootTagAndRootBodyTag(code);
        InnerRootNode inner = findInnerRootNodeWithEndOffset(rootsMap, 23);
        checkBodyPosition(rootsMap, inner, 17, 22);
    }

    @Test
    public void testRootBodyTagClass02() throws Exception {
        String code = "class MyClass:\n" + "  '''This is a simple test class'''\n" + "  x = 5\n" + "m = MyClass()\n";
        HashMap<InstrumentableNode, InstrumentableNode> rootsMap = checkRootTagAndRootBodyTag(code);
        InnerRootNode inner = findInnerRootNodeWithEndOffset(rootsMap, 59);
        checkBodyPosition(rootsMap, inner, 53, 58);

    }

    private HashMap<InstrumentableNode, InstrumentableNode> checkRootTagAndRootBodyTag(String code) throws Exception {
        return checkRootTagAndRootBodyTag(code, false);
    }

    private HashMap<InstrumentableNode, InstrumentableNode> checkRootTagAndRootBodyTag(String code, boolean possibleEmptyRootTag) throws Exception {
        Engine engine = Engine.newBuilder().build();
        Context newContext = newContext(engine);
        newContext.initialize("python");
        Instrument envInstr = newContext.getEngine().getInstruments().get("TestPythonInstrument");

        Source source = Source.newBuilder("python", code, "testing").build();
        TruffleInstrument.Env env = envInstr.lookup(TestEnvProvider.class).env;
        SourceSectionFilter filter = SourceSectionFilter.newBuilder().lineIn(1, source.getLineCount()).tagIs(StandardTags.RootTag.class, StandardTags.RootBodyTag.class).build();
        HashMap<InstrumentableNode, InstrumentableNode> rootsMap = new HashMap<>();
        env.getInstrumenter().attachExecutionEventListener(filter, new RootTagAndRoodBodyTagListener(rootsMap));

        newContext.eval(source);

        if (!possibleEmptyRootTag) {
            for (InstrumentableNode rootBodyTag : rootsMap.values()) {
                assertTrue("There is a RootTag node without RootBodyTag node", rootBodyTag != null);
            }
        }
        return rootsMap;
    }

    private InnerRootNode findInnerRootNodeWithEndOffset(HashMap<InstrumentableNode, InstrumentableNode> rootsMap, int end) {
        for (InstrumentableNode node : rootsMap.keySet()) {
            InnerRootNode inner = (InnerRootNode) node;
            if (inner.getSourceSection().getCharEndIndex() == end) {
                return inner;
            }
        }
        assertTrue("No InnerRootNode found with end offset " + end, false);
        return null;
    }

    private void checkBodyPosition(HashMap<InstrumentableNode, InstrumentableNode> rootsMap, InnerRootNode inner, int start, int end) {
        InstrumentableNode body = rootsMap.get(inner);
        assertTrue(body != null);
        PNode pbody = (PNode) body;
        Assert.assertEquals(start, pbody.getSourceSection().getCharIndex());
        Assert.assertEquals(end, pbody.getSourceSection().getCharEndIndex());
    }

    @TruffleInstrument.Registration(id = "TestPythonInstrument", services = TestEnvProvider.class)
    public static class TestPythonInstrument extends TruffleInstrument {

        @Override
        protected void onCreate(final TruffleInstrument.Env env) {
            env.registerService(new TestEnvProvider(env));
        }
    }

    private static class TestEnvProvider {

        TruffleInstrument.Env env;

        TestEnvProvider(TruffleInstrument.Env env) {
            this.env = env;
        }
    }

    private static class RootTagAndRoodBodyTagListener implements ExecutionEventListener {

        // map of the RootTag -> RootBodyTag
        private final HashMap<InstrumentableNode, InstrumentableNode> result;

        public RootTagAndRoodBodyTagListener(HashMap<InstrumentableNode, InstrumentableNode> resultMap) {
            this.result = resultMap;
        }

        @Override
        public void onEnter(EventContext context, VirtualFrame frame) {
            Node node = context.getInstrumentedNode();
            InstrumentableNode inode = (InstrumentableNode) node;

            if (inode.hasTag(StandardTags.RootTag.class)) {
                result.put(inode, null);
            }
            if (inode.hasTag(StandardTags.RootBodyTag.class)) {
                Node parent = node;
                while (parent != null) {
                    if (parent instanceof InstrumentableNode) {
                        InstrumentableNode iparent = (InstrumentableNode) parent;
                        if (iparent.hasTag(StandardTags.RootTag.class)) {
                            assertTrue("RootBodyTag find sooner then RootTag", result.containsKey(iparent));
                            assertTrue("Found RootTag that already has RootBodyTag", result.get(iparent) == null);
                            result.put(iparent, inode);
                            break;
                        }
                        if (iparent != inode) {
                            assertFalse("RootBodyTag is innered in other RootBodyTag", iparent.hasTag(StandardTags.RootBodyTag.class));
                        }
                    }
                    parent = parent.getParent();
                }
                assertFalse("Found RootBodyTag without RootTag", parent == null);
            }
        }

        @Override
        public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
        }

        @Override
        public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
        }
    }
}
