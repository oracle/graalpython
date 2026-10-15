/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.WrapperBaseRoot;
import com.oracle.graal.python.builtins.objects.cext.capi.MethodDescriptorWrapper;
import com.oracle.graal.python.builtins.modules.cext.PythonCextTypeBuiltins;
import com.oracle.graal.python.builtins.objects.frame.FrameBuiltinsFactory.GetLocalsNodeFactory;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.runtime.CallerFlags;
import com.oracle.graal.python.nodes.argument.CreateArgumentsNode;
import com.oracle.graal.python.nodes.argument.ReadArgumentNode;
import com.oracle.graal.python.nodes.argument.ReadIndexedArgumentNode;
import com.oracle.graal.python.nodes.argument.ReadVarArgsNode;
import com.oracle.graal.python.nodes.argument.ReadVarKeywordsNode;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinFrameArgsNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.util.PythonUtils.PrototypeNodeFactory;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.strings.TruffleString;

/** Independent inventory and runtime checks for native wrappers using ordinary builtin roots. */
public class ExternalFunctionWrapperTest {
    private Context context;
    private static final TruffleString NAME = tsLiteral("wrapper_test");

    // Positional names and literal vararg indices are the pre-conversion Python signatures.
    private record Spec(String body, String enums, String parameters, int vararg, boolean kwargs, boolean hidden, boolean owner, boolean closure, boolean method) {
    }

    private static Spec method(String body, String convention, String parameters, int vararg, boolean kwargs) {
        return new Spec(body, convention, parameters, vararg, kwargs, true, true, false, true);
    }

    private static Spec descriptor(String body, String wrappers, String parameters, int vararg, boolean kwargs, boolean hidden, boolean owner, boolean closure) {
        return new Spec(body, wrappers, parameters, vararg, kwargs, hidden, owner, closure, false);
    }

    private static final List<Spec> SPECS = List.of(
                    method("MethNoargsRoot", "NOARGS", "self", -1, false),
                    method("MethORoot", "O", "self,arg", -1, false),
                    method("MethVarargsRoot", "VARARGS", "self", 1, false),
                    method("MethKeywordsRoot", "KEYWORDS", "self", 1, true),
                    method("MethFastcallRoot", "FASTCALL", "self", 1, false),
                    method("MethFastcallWithKeywordsRoot", "FASTCALL_WITH_KEYWORDS", "self", 1, true),
                    method("MethMethodRoot", "METHOD", "self,cls", 1, true),
                    descriptor("MethUnaryFunc", "UNARYFUNC", "self", -1, false, false, true, false),
                    descriptor("MethNewRoot", "NEW", "self", 1, true, true, true, false),
                    descriptor("MethCallRoot", "CALL", "self", 1, true, true, true, false),
                    descriptor("MethInitRoot", "INIT", "self", 1, true, true, true, false),
                    descriptor("MethInquiryRoot", "INQUIRYPRED", "self", -1, false, false, true, false),
                    descriptor("IndexArgFuncRootNode", "INDEXARGFUNC", "self,i", -1, false, false, true, false),
                    descriptor("GetAttrFuncRootNode", "GETATTR", "self,key", -1, false, false, true, false),
                    descriptor("SetAttrFuncRootNode", "SETATTR", "self,key,value", -1, false, false, true, false),
                    descriptor("SetAttrOFuncRootNode", "SETATTRO", "self,name,value", -1, false, false, true, false),
                    descriptor("RichCmpFuncRootNode", "RICHCMP", "self,other,op", -1, false, false, true, false),
                    descriptor("GetItemRootNode", "SQ_ITEM", "self,i", -1, false, false, true, false),
                    descriptor("SetItemRootNode", "SQ_SETITEM", "self,i,value", -1, false, false, true, false),
                    descriptor("SqDelItemRootNode", "SQ_DELITEM", "self,key", -1, false, false, true, false),
                    descriptor("DescrGetRootNode", "DESCR_GET", "self,obj,type", -1, false, false, true, false),
                    descriptor("DescrDeleteRootNode", "DESCR_DELETE", "self,obj", -1, false, false, true, false),
                    descriptor("DelAttrRootNode", "DELATTRO", "self,obj", -1, false, false, true, false),
                    descriptor("MpDelItemRootNode", "MP_DELITEM", "self,key", -1, false, false, true, false),
                    descriptor("MethTernaryFuncRoot", "TERNARYFUNC,TERNARYFUNC_R", "self,other,third", -1, false, false, false, false),
                    descriptor("MethRichcmpOpRootNode", "GT,GE,LE,LT,EQ,NE", "self,other", -1, false, false, true, false),
                    descriptor("IterNextFuncRootNode", "ITERNEXT", "self", -1, false, true, true, false),
                    descriptor("GetterRoot", "GETTER", "self", -1, false, false, true, true),
                    descriptor("SetterRoot", "SETTER", "self,value", -1, false, false, true, true),
                    descriptor("MethLenfuncRoot", "LENFUNC,HASHFUNC", "self", -1, false, false, true, false),
                    descriptor("MethObjObjProcRoot", "OBJOBJPROC", "self,value", -1, false, false, true, false),
                    descriptor("MethObjObjArgProcRoot", "OBJOBJARGPROC", "self,key,value", -1, false, false, true, false),
                    descriptor("MethBinaryRoot", "BINARYFUNC,BINARYFUNC_L,BINARYFUNC_R", "self,other", -1, false, false, true, false),
                    descriptor("MethDescrSetRoot", "DESCR_SET", "self,instance,value", -1, false, false, true, false));

    @Before
    public void enter() {
        context = Context.newBuilder("python").allowAllAccess(true).allowExperimentalOptions(true).option("engine.WarnInterpreterOnly", "false").option("engine.SplittingAllowForcedSplits",
                        "true").build();
        context.initialize("python");
        context.enter();
    }

    @After
    public void leave() {
        context.leave();
        context.close();
    }

    private static Object invoke(Class<?> owner, Object receiver, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = owner.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(receiver, args);
    }

    private static NodeFactory<?> factory(Spec spec, String convention, boolean isStatic) throws Exception {
        if (spec.method) {
            return (NodeFactory<?>) invoke(MethodDescriptorWrapper.class, null, "createFactory",
                            new Class<?>[]{MethodDescriptorWrapper.class, TruffleString.class, boolean.class}, MethodDescriptorWrapper.valueOf(convention), NAME, isStatic);
        }
        return (NodeFactory<?>) invoke(PExternalFunctionWrapper.class, null, "createFactory", new Class<?>[]{PExternalFunctionWrapper.class, TruffleString.class},
                        PExternalFunctionWrapper.valueOf(convention), NAME);
    }

    private static BuiltinFunctionRootNode root(NodeFactory<?> factory) throws Exception {
        return (BuiltinFunctionRootNode) invoke(factory.getClass(), factory, "createRoot", new Class<?>[]{PythonLanguage.class}, PythonLanguage.get(null));
    }

    private static List<String> names(TruffleString[] names) {
        return Arrays.stream(names).map(TruffleString::toJavaStringUncached).toList();
    }

    private static List<Field> readerFields(WrapperBaseRoot body) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> owner = body.getClass(); owner != null; owner = owner.getSuperclass()) {
            for (Field field : owner.getDeclaredFields()) {
                if (ReadArgumentNode.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    fields.add(field);
                }
            }
        }
        return fields;
    }

    private static void initializeReaders(WrapperBaseRoot body, Signature signature) throws Exception {
        // Initialize the production helper groups without making a native call. The C API suite
        // separately exercises the guards on first and repeated real wrapper invocations.
        for (Class<?> owner = body.getClass(); owner != null; owner = owner.getSuperclass()) {
            for (Method method : owner.getDeclaredMethods()) {
                if ((method.getName().equals("createNodes") || method.getName().equals("ensureReadArgNode")) && method.getParameterCount() == 0) {
                    method.setAccessible(true);
                    method.invoke(body);
                }
            }
        }
        for (Field field : readerFields(body)) {
            if (field.getName().equals("readClosureNode")) {
                field.set(body, invoke(ExternalFunctionNodes.GetterRoot.class, null, "createReadClosureNode", new Class<?>[]{Signature.class}, signature));
            }
        }
    }

    private static List<ReadArgumentNode> checkReaders(WrapperBaseRoot body, Spec spec, boolean isStatic, VirtualFrame frame, Object[] positional,
                    Object callable, Object closure, Object[] extras, PKeyword[] keywords) throws Exception {
        List<ReadArgumentNode> readers = new ArrayList<>();
        Map<Node, Integer> counts = new IdentityHashMap<>();
        countChildren(body, counts);
        int indexed = 0;
        int varargs = 0;
        int kwargs = 0;
        for (Field field : readerFields(body)) {
            ReadArgumentNode reader = (ReadArgumentNode) field.get(body);
            if (field.getName().equals("readSelfNode") && isStatic) {
                assertSame(null, reader);
                continue;
            }
            assertNotNull(field.getName(), reader);
            assertSame(body, reader.getParent());
            assertEquals(Integer.valueOf(1), counts.get(reader));
            readers.add(reader);
            Object value = reader.execute(frame);
            if (reader instanceof ReadIndexedArgumentNode argument) {
                int expectedIndex = switch (field.getName()) {
                    case "readSelfNode" -> 0;
                    case "readCallableNode" -> positional.length;
                    case "readClosureNode" -> positional.length + 1;
                    case "readArg2Node", "readOpNode", "readType" -> 2;
                    case "readValueNode" -> positional.length - 1;
                    case "readArg1Node", "readArgNode", "readClsNode", "readINode", "readInstanceNode", "readKeyNode", "readNameNode", "readObj", "readOtherNode" -> 1;
                    default -> throw new AssertionError(field.getName());
                };
                assertEquals(field.getName(), expectedIndex, argument.getIndex());
                Object expected = expectedIndex < positional.length ? positional[expectedIndex] : expectedIndex == positional.length ? callable : closure;
                assertSame(field.getName(), expected, value);
                indexed++;
            } else if (reader instanceof ReadVarArgsNode) {
                assertArrayEquals(extras, (Object[]) value);
                varargs++;
            } else if (reader instanceof ReadVarKeywordsNode) {
                assertArrayEquals(keywords, (PKeyword[]) value);
                kwargs++;
            } else {
                fail("unexpected production reader: " + reader.getClass());
            }
        }
        assertEquals(positional.length + (spec.closure ? 2 : 1) - (isStatic ? 1 : 0), indexed);
        assertEquals(spec.vararg == -1 ? 0 : 1, varargs);
        assertEquals(spec.kwargs ? 1 : 0, kwargs);
        assertSame(isStatic ? com.oracle.graal.python.builtins.objects.PNone.NO_VALUE : positional[0],
                        invoke(WrapperBaseRoot.class, body, "readSelf", new Class<?>[]{VirtualFrame.class}, frame));
        return readers;
    }

    @Test
    public void builtinBodyDispatch() {
        Object token = new Object();
        Object[] arguments = PArguments.create(1);
        PArguments.setArgument(arguments, 0, token);
        Signature signature = new Signature(false, -1, new TruffleString[]{tsLiteral("self")}, null);
        Builtin metadata = OrdinaryNode.class.getAnnotation(Builtin.class);
        Builtin frameMetadata = (Builtin) Proxy.newProxyInstance(Builtin.class.getClassLoader(), new Class<?>[]{Builtin.class}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "parameterNames", "keywordOnlyNames", "minNumOfPositionalArgs", "maxNumOfPositionalArgs", "takesVarArgs", "takesVarKeywordArgs" -> throw new AssertionError(
                                "frame-only builtin queried reader metadata: " + method.getName());
            }
            return method.invoke(metadata, args);
        });
        BuiltinFunctionRootNode frameRoot = new BuiltinFunctionRootNode(PythonLanguage.get(null), signature, frameMetadata,
                        new ZeroArgumentFactory<>(FrameRecordingNode.class, FrameRecordingNode::new), true, null);
        BuiltinFunctionRootNode ordinaryRoot = new BuiltinFunctionRootNode(PythonLanguage.get(null), signature, metadata, new RecordingFactory(), true, null);
        BuiltinFunctionRootNode unaryRoot = new BuiltinFunctionRootNode(PythonLanguage.get(null), signature, metadata,
                        new ZeroArgumentFactory<>(UnaryRecordingNode.class, UnaryRecordingNode::new), true, null);
        for (int i = 0; i < 2; i++) {
            assertSame(token, frameRoot.getCallTarget().call(arguments));
            assertArrayEquals(new Object[]{token}, (Object[]) ordinaryRoot.getCallTarget().call(arguments));
            assertSame(token, unaryRoot.getCallTarget().call(arguments));
        }
    }

    @Test
    public void readerLayouts() throws Exception {
        assertEquals(34, SPECS.size());
        for (Spec spec : SPECS) {
            for (boolean isStatic : spec.method && !spec.enums.equals("METHOD") ? new boolean[]{false, true} : new boolean[]{false}) {
                BuiltinFunctionRootNode production = root(factory(spec, spec.enums.split(",")[0], isStatic));
                Signature signature = production.getSignature();
                Builtin builtin = production.getBuiltin();
                List<String> parameters = List.of(spec.parameters.split(","));
                List<String> hidden = spec.closure ? List.of("$callable", "$closure") : List.of("$callable");
                assertEquals(spec.body, parameters, names(signature.getParameterIds()));
                assertEquals(hidden, names(signature.getKeywordNames()));
                assertEquals(0, builtin.parameterNames().length);
                assertEquals(0, builtin.keywordOnlyNames().length);
                Field vararg = Signature.class.getDeclaredField("varArgIndex");
                vararg.setAccessible(true);
                assertEquals(spec.vararg, vararg.getInt(signature));
                assertEquals(-1, signature.getPositionalOnlyArgIndex());
                assertEquals(spec.kwargs, signature.takesVarKeywordArgs());
                assertEquals(spec.owner, signature.checkEnclosingType());
                assertEquals(spec.hidden, signature.isHidden());
                assertEquals("", signature.getRaiseErrorName().toJavaStringUncached());
                assertEquals(0, signature.getVisibleKeywordNames().length);
                assertEquals(-1, builtin.maxNumOfPositionalArgs());
                assertEquals(0, builtin.minNumOfPositionalArgs());
                assertTrue(production.declaresExplicitSelf());
                assertTrue(builtin.declaresExplicitSelf());
                assertTrue(builtin.needsFrame());
                assertFalse(builtin.autoRegister());

                for (int size : new int[]{0, 2}) {
                    Object[] positional = new Object[parameters.size()];
                    Arrays.setAll(positional, i -> new Object());
                    Object[] extras = spec.vararg == -1 ? new Object[0] : new Object[size];
                    Arrays.setAll(extras, i -> new Object());
                    Object callable = new Object();
                    Object closure = Long.valueOf(0x12345678L);
                    PKeyword[] defaults = spec.closure ? new PKeyword[]{new PKeyword(tsLiteral("$callable"), callable), new PKeyword(tsLiteral("$closure"), closure)}
                                    : new PKeyword[]{new PKeyword(tsLiteral("$callable"), callable)};
                    PKeyword[] keywords = spec.kwargs && size > 0 ? new PKeyword[]{new PKeyword(tsLiteral("user_a"), new Object()), new PKeyword(tsLiteral("user_b"), new Object())}
                                    : PKeyword.EMPTY_KEYWORDS;
                    // Bind self and defining class through the same path as bound native methods.
                    Object cls = spec.enums.equals("METHOD") ? positional[1] : null;
                    int upfront = cls == null ? 1 : 2;
                    Object[] user = new Object[positional.length - upfront + extras.length];
                    System.arraycopy(positional, upfront, user, 0, positional.length - upfront);
                    System.arraycopy(extras, 0, user, positional.length - upfront, extras.length);
                    Object[] bound = CreateArgumentsNode.executeUncached(NAME, user, keywords, signature, positional[0], cls, new Object[0], defaults, true);
                    WrapperBaseRoot body = (WrapperBaseRoot) production.getFactory().createNode();
                    initializeReaders(body, signature);
                    new AdoptionRoot(body).getCallTarget();
                    VirtualFrame frame = Truffle.getRuntime().createVirtualFrame(bound, FrameDescriptor.newBuilder().build());
                    List<ReadArgumentNode> firstReaders = null;
                    for (int invocation = 0; invocation < 2; invocation++) {
                        // Exercise the production callable-reader initialization and validation.
                        // An invalid pointer stops execution before any native conversion/downcall.
                        boolean rejected = false;
                        try {
                            body.execute(frame);
                        } catch (AssertionError expected) {
                            assertEquals("com.oracle.truffle.api.CompilerDirectives$ShouldNotReachHere", expected.getClass().getName());
                            rejected = true;
                        }
                        assertTrue("invalid native pointer must be rejected", rejected);
                        List<ReadArgumentNode> readers = checkReaders(body, spec, isStatic, frame, positional, callable, closure, extras, keywords);
                        if (firstReaders == null) {
                            firstReaders = readers;
                        } else {
                            assertEquals(firstReaders, readers);
                        }
                    }
                }
            }
        }
    }

    @Test
    public void factoryClassesAndChildren() throws Exception {
        int methods = 0;
        int descriptors = 0;
        for (Spec spec : SPECS) {
            for (String convention : spec.enums.split(",")) {
                NodeFactory<?> factory = factory(spec, convention, false);
                assertEquals(ExternalFunctionNodes.class.getName() + "$" + spec.body, factory.getNodeClass().getName());
                assertEquals(List.of(List.of()), factory.getNodeSignatures());
                assertEquals(List.of(), factory.getExecutionSignature());
                WrapperBaseRoot body = (WrapperBaseRoot) factory.createNode();
                WrapperBaseRoot other = (WrapperBaseRoot) factory.createNode();
                assertNotSame(body, other);
                assertTrue(PythonBuiltinFrameArgsNode.class.isAssignableFrom(body.getClass()));
                assertFalse(PythonBuiltinNode.class.isAssignableFrom(body.getClass()));
                assertTrue(factory.getNodeClass().isInstance(body));
                assertEquals(0, factory.getNodeClass().getAnnotationsByType(Builtin.class).length);
                initializeReaders(body, root(factory).getSignature());
                initializeReaders(other, root(factory).getSignature());
                new AdoptionRoot(body).getCallTarget();
                new AdoptionRoot(other).getCallTarget();
                Map<Node, Integer> counts = new IdentityHashMap<>();
                countChildren(body, counts);
                Map<Node, Integer> otherCounts = new IdentityHashMap<>();
                countChildren(other, otherCounts);
                for (Field field : readerFields(body)) {
                    ReadArgumentNode reader = (ReadArgumentNode) field.get(body);
                    if (reader != null) {
                        assertEquals(Integer.valueOf(1), counts.get(reader));
                        assertSame(body, reader.getParent());
                        assertFalse(otherCounts.containsKey(reader));
                    }
                }
                Field self = WrapperBaseRoot.class.getDeclaredField("readSelfNode");
                self.setAccessible(true);
                assertNotNull(self.get(body));
                if (spec.method && !convention.equals("METHOD")) {
                    Node staticBody = (Node) factory(spec, convention, true).createNode();
                    assertSame(null, self.get(staticBody));
                }
                if (spec.method) {
                    methods++;
                } else {
                    descriptors++;
                }
            }
        }
        assertEquals(7, methods);
        assertEquals(36, descriptors);
        try {
            factory(SPECS.get(7), "TRAVERSEPROC", false);
            fail("TRAVERSEPROC must remain unsupported");
        } catch (InvocationTargetException expected) {
            assertNotNull(expected.getCause());
        }
    }

    private static void countChildren(Node node, Map<Node, Integer> counts) {
        for (Node child : node.getChildren()) {
            counts.merge(child, 1, Integer::sum);
            countChildren(child, counts);
        }
    }

    @Test
    public void framePoliciesAndClones() throws Exception {
        for (Spec spec : SPECS) {
            for (String convention : spec.enums.split(",")) {
                BuiltinFunctionRootNode root = root(factory(spec, convention, false));
                assertPolicies(root, true, true);
                assertWrapperRootIdentity(root, NAME);
                DirectCallNode call = DirectCallNode.create(root.getCallTarget());
                new AdoptionRoot(call).getCallTarget();
                call.cloneCallTarget();
                RootCallTarget clonedTarget = (RootCallTarget) call.getCurrentCallTarget();
                assertNotSame("An optimized runtime with forced splitting is required", root.getCallTarget(), clonedTarget);
                BuiltinFunctionRootNode clone = (BuiltinFunctionRootNode) clonedTarget.getRootNode();
                assertSame(root.getFactory(), clone.getFactory());
                assertSame(root.getSignature(), clone.getSignature());
                assertSame(root.getBuiltin(), clone.getBuiltin());
                assertPolicies(clone, true, true);
                assertWrapperRootIdentity(clone, NAME);
            }
        }
        Builtin builtin = OrdinaryNode.class.getAnnotation(Builtin.class);
        assertPolicies(new BuiltinFunctionRootNode(PythonLanguage.get(null), builtin, new PrototypeNodeFactory<>(new OrdinaryNode()), true), false, false);
        NodeFactory<?> locals = GetLocalsNodeFactory.getInstance();
        Builtin localsBuiltin = locals.getNodeClass().getAnnotationsByType(Builtin.class)[0];
        assertPolicies(new BuiltinFunctionRootNode(PythonLanguage.get(null), localsBuiltin, GetLocalsNodeFactory.getInstance(), true), false, true);
    }

    private static void assertPolicies(BuiltinFunctionRootNode root, boolean capture, boolean locals) {
        assertEquals(capture, root.isCaptureFramesForTrace(false));
        assertEquals(capture, root.isCaptureFramesForTrace(true));
        root.updateCallerFlags(CallerFlags.NEEDS_MATERIALIZED_LOCALS);
        assertEquals(locals, (root.getCallerFlags() & CallerFlags.NEEDS_MATERIALIZED_LOCALS) != 0);
        assertTrue(root.isInternal());
        assertTrue(root.isPythonInternal());
        assertTrue(root.setsUpCalleeContext());
    }

    private static void assertWrapperRootIdentity(BuiltinFunctionRootNode root, TruffleString name) {
        assertEquals(name.toJavaStringUncached(), root.getName());
        assertEquals(name.toJavaStringUncached(), root.getFunctionName());
        // The name and flags match the former wrapper root. The diagnostic string now uses
        // BuiltinFunctionRootNode's format rather than the former "<METH root name>" format.
        assertEquals("<builtin function ExternalFunctionNodes." + name.toJavaStringUncached() + " at " + Integer.toHexString(root.hashCode()) + ">", root.toString());
    }

    @Test
    public void cacheKeys() throws Exception {
        PythonLanguage language = PythonLanguage.get(null);
        for (Spec spec : SPECS) {
            for (String convention : spec.enums.split(",")) {
                BuiltinFunctionRootNode first = cached(spec, convention, NAME, false);
                BuiltinFunctionRootNode second = cached(spec, convention, NAME, false);
                assertSame(first, second);
                assertSame(first, first.getCallTarget().getRootNode());
                assertSame(first.getCallTarget(), second.getCallTarget());
                BuiltinFunctionRootNode renamed = cached(spec, convention, tsLiteral("another_name"), false);
                assertNotSame(first, renamed);
                assertWrapperRootIdentity(renamed, tsLiteral("another_name"));
                if (spec.method && !convention.equals("METHOD")) {
                    BuiltinFunctionRootNode staticRoot = cached(spec, convention, NAME, true);
                    assertNotSame(first, staticRoot);
                    assertWrapperRootIdentity(staticRoot, NAME);
                    assertTrue(staticRoot.isInternal());
                    assertTrue(staticRoot.isPythonInternal());
                    assertTrue(staticRoot.setsUpCalleeContext());
                }
            }
        }
        BuiltinFunctionRootNode reference = cached(SPECS.get(0), "NOARGS", NAME, false);
        assertSame(reference, language.createCachedExternalFunWrapperRootNode(l -> {
            throw new AssertionError("cache miss");
        },
                        reference.getFactory().getNodeClass(), MethodDescriptorWrapper.NOARGS, NAME, true, false));
        List<BuiltinFunctionRootNode> variants = new ArrayList<>();
        variants.add(language.createCachedExternalFunWrapperRootNode(l -> freshRoot(), ExternalFunctionNodes.MethORoot.class, MethodDescriptorWrapper.NOARGS, NAME, true, false));
        variants.add(language.createCachedExternalFunWrapperRootNode(l -> freshRoot(), reference.getFactory().getNodeClass(), MethodDescriptorWrapper.O, NAME, true, false));
        variants.add(language.createCachedExternalFunWrapperRootNode(l -> freshRoot(), reference.getFactory().getNodeClass(), MethodDescriptorWrapper.NOARGS, NAME, false, false));
        variants.add(language.createCachedExternalFunWrapperRootNode(l -> freshRoot(), reference.getFactory().getNodeClass(), MethodDescriptorWrapper.NOARGS, NAME, true, true));
        for (BuiltinFunctionRootNode variant : variants) {
            assertNotSame(reference, variant);
            assertNotSame(reference.getCallTarget(), variant.getCallTarget());
        }
        for (String kind : new String[]{"getter", "setter"}) {
            BuiltinFunctionRootNode getset = (BuiltinFunctionRootNode) invoke(PythonCextTypeBuiltins.class, null, kind + "RootNode",
                            new Class<?>[]{TruffleString.class, PythonLanguage.class}, NAME, language);
            BuiltinFunctionRootNode descriptor = PExternalFunctionWrapper.getOrCreateRootNode(PExternalFunctionWrapper.valueOf(kind.toUpperCase(java.util.Locale.ROOT)), language, NAME);
            assertSame(descriptor, getset);
            assertSame(descriptor.getCallTarget(), getset.getCallTarget());
        }
    }

    private static BuiltinFunctionRootNode freshRoot() {
        return new BuiltinFunctionRootNode(PythonLanguage.get(null), OrdinaryNode.class.getAnnotation(Builtin.class), new PrototypeNodeFactory<>(new OrdinaryNode()), true);
    }

    private static BuiltinFunctionRootNode cached(Spec spec, String convention, TruffleString name, boolean isStatic) throws Exception {
        if (!spec.method) {
            return PExternalFunctionWrapper.getOrCreateRootNode(PExternalFunctionWrapper.valueOf(convention), PythonLanguage.get(null), name);
        }
        return (BuiltinFunctionRootNode) invoke(MethodDescriptorWrapper.class, null, "getOrCreateRootNode",
                        new Class<?>[]{PythonLanguage.class, MethodDescriptorWrapper.class, TruffleString.class, boolean.class},
                        PythonLanguage.get(null), MethodDescriptorWrapper.valueOf(convention), name, isStatic);
    }

    private static final class ZeroArgumentFactory<T extends PythonBuiltinBaseNode> implements NodeFactory<T> {
        private final Class<T> nodeClass;
        private final Supplier<T> constructor;

        ZeroArgumentFactory(Class<T> nodeClass, Supplier<T> constructor) {
            this.nodeClass = nodeClass;
            this.constructor = constructor;
        }

        @Override
        public T createNode(Object... arguments) {
            assertEquals(0, arguments.length);
            return constructor.get();
        }

        @Override
        public Class<T> getNodeClass() {
            return nodeClass;
        }

        @Override
        public List<List<Class<?>>> getNodeSignatures() {
            return List.of(List.of());
        }

        @Override
        public List<Class<? extends Node>> getExecutionSignature() {
            return List.of();
        }
    }

    private static final class FrameRecordingNode extends PythonBuiltinFrameArgsNode {
        @Child private ReadIndexedArgumentNode reader = ReadIndexedArgumentNode.create(0);

        @Override
        public Object execute(VirtualFrame frame) {
            return reader.execute(frame);
        }
    }

    private static final class UnaryRecordingNode extends PythonUnaryBuiltinNode {
        @Override
        public Object execute(VirtualFrame frame, Object argument) {
            return argument;
        }
    }

    private static final class RecordingFactory implements NodeFactory<RecordingNode> {
        @Override
        public RecordingNode createNode(Object... arguments) {
            assertEquals(1, arguments.length);
            return new RecordingNode((ReadArgumentNode[]) arguments[0]);
        }

        @Override
        public Class<RecordingNode> getNodeClass() {
            return RecordingNode.class;
        }

        @Override
        public List<List<Class<?>>> getNodeSignatures() {
            return List.of(List.of(ReadArgumentNode[].class));
        }

        @Override
        public List<Class<? extends Node>> getExecutionSignature() {
            return List.of(ReadArgumentNode.class);
        }
    }

    private static final class RecordingNode extends PythonBuiltinNode {
        @Children private final ReadArgumentNode[] readers;

        RecordingNode(ReadArgumentNode[] readers) {
            this.readers = readers;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object[] values = new Object[readers.length];
            for (int i = 0; i < readers.length; i++) {
                values[i] = readers[i].execute(frame);
            }
            return values;
        }
    }

    private static final class AdoptionRoot extends RootNode {
        @Child private Node child;

        AdoptionRoot(Node child) {
            super(PythonLanguage.get(null));
            this.child = child;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return child;
        }
    }

    @Builtin(name = "ordinary_wrapper_control", parameterNames = {"self"}, autoRegister = false)
    private static final class OrdinaryNode extends PythonBuiltinNode {
        @Override
        public Object execute(VirtualFrame frame) {
            return 42;
        }
    }
}
