/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.util;

import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DOC__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEW__;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import org.graalvm.nativeimage.ImageInfo;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.GetSetDescriptor;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.object.PythonObjectSlowPathFactory;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.memory.ByteArraySupport;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;

import sun.misc.Unsafe;

public final class PythonUtils {

    public static final PCell[] NO_CLOSURE = new PCell[0];
    public static final ByteArraySupport arrayAccessor;
    public static final ConditionProfile[] DISABLED = new ConditionProfile[]{ConditionProfile.getUncached()};
    public static final boolean ASSERTIONS_ENABLED;

    static {
        if (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN) {
            arrayAccessor = ByteArraySupport.bigEndian();
        } else {
            arrayAccessor = ByteArraySupport.littleEndian();
        }
        boolean ae = false;
        assert (ae = true) == true;
        ASSERTIONS_ENABLED = ae;
    }

    private PythonUtils() {
        // no instances
    }

    public static final String EMPTY_STRING = "";
    public static final String NEW_LINE = "\n";
    public static final String[] EMPTY_STRING_ARRAY = new String[0];
    public static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];
    public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
    public static final int[] EMPTY_INT_ARRAY = new int[0];
    public static final double[] EMPTY_DOUBLE_ARRAY = new double[0];
    public static final char[] EMPTY_CHAR_ARRAY = new char[0];

    /**
     * Executes System.arraycopy and puts all exceptions on the slow path.
     */
    public static void arraycopy(Object src, int srcPos, Object dest, int destPos, int length) {
        try {
            System.arraycopy(src, srcPos, dest, destPos, length);
        } catch (Throwable t) {
            // this is really unexpected and we want to break exception edges in compiled code
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw t;
        }
    }

    /**
     * Executes {@link Arrays#copyOfRange(Object[], int, int)} and puts all exceptions on the slow
     * path.
     */
    public static <T> T[] arrayCopyOfRange(T[] original, int from, int to) {
        try {
            return Arrays.copyOfRange(original, from, to);
        } catch (Throwable t) {
            // this is really unexpected and we want to break exception edges in compiled code
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw t;
        }
    }

    /**
     * Executes {@link Arrays#copyOfRange(byte[], int, int)} and puts all exceptions on the slow
     * path.
     */
    public static byte[] arrayCopyOfRange(byte[] original, int from, int to) {
        try {
            return Arrays.copyOfRange(original, from, to);
        } catch (Throwable t) {
            // this is really unexpected and we want to break exception edges in compiled code
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw t;
        }
    }

    /**
     * Executes {@link Arrays#copyOf(Object[], int)} and puts all exceptions on the slow path.
     */
    public static <T> T[] arrayCopyOf(T[] original, int newLength) {
        try {
            return Arrays.copyOf(original, newLength);
        } catch (Throwable t) {
            // this is really unexpected and we want to break exception edges in compiled code
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw t;
        }
    }

    /**
     * Executes {@link Arrays#copyOf(char[], int)} and puts all exceptions on the slow path.
     */
    public static char[] arrayCopyOf(char[] original, int newLength) {
        try {
            return Arrays.copyOf(original, newLength);
        } catch (Throwable t) {
            // Break exception edges
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw t;
        }
    }

    /**
     * Executes {@link Arrays#copyOf(boolean[], int)} and puts all exceptions on the slow path.
     */
    public static boolean[] arrayCopyOf(boolean[] original, int newLength) {
        try {
            return Arrays.copyOf(original, newLength);
        } catch (Throwable t) {
            // Break exception edges
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw t;
        }
    }

    /**
     * Executes {@link Arrays#copyOf(byte[], int)} and puts all exceptions on the slow path.
     */
    public static byte[] arrayCopyOf(byte[] original, int newLength) {
        try {
            return Arrays.copyOf(original, newLength);
        } catch (Throwable t) {
            // Break exception edges
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw t;
        }
    }

    /**
     * Executes {@link Arrays#copyOf(int[], int)} and puts all exceptions on the slow path.
     */
    public static int[] arrayCopyOf(int[] original, int newLength) {
        try {
            return Arrays.copyOf(original, newLength);
        } catch (Throwable t) {
            // Break exception edges
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw t;
        }
    }

    /**
     * Executes {@link Arrays#copyOf(double[], int)} and puts all exceptions on the slow path.
     */
    public static double[] arrayCopyOf(double[] original, int newLength) {
        try {
            return Arrays.copyOf(original, newLength);
        } catch (Throwable t) {
            // Break exception edges
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw t;
        }
    }

    /**
     * Executes {@link Arrays#copyOf(long[], int)} and puts all exceptions on the slow path.
     */
    public static long[] arrayCopyOf(long[] original, int newLength) {
        try {
            return Arrays.copyOf(original, newLength);
        } catch (Throwable t) {
            // Break exception edges
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw t;
        }
    }

    /**
     * Executes {@code String.getChars} and puts all exceptions on the slow path.
     */
    @TruffleBoundary
    public static void getChars(String str, int srcBegin, int srcEnd, char[] dst, int dstBegin) {
        str.getChars(srcBegin, srcEnd, dst, dstBegin);
    }

    /*
     * Replacements for JDK's exact math methods that throw the checked singleton {@link
     * OverflowException}. The implementation is taken from JDK.
     */
    public static int addExact(int x, int y) throws OverflowException {
        int r = x + y;
        if (((x ^ r) & (y ^ r)) < 0) {
            throw OverflowException.INSTANCE;
        }
        return r;
    }

    public static long addExact(long x, long y) throws OverflowException {
        long r = x + y;
        if (((x ^ r) & (y ^ r)) < 0) {
            throw OverflowException.INSTANCE;
        }
        return r;
    }

    public static int subtractExact(int x, int y) throws OverflowException {
        int r = x - y;
        if (((x ^ y) & (x ^ r)) < 0) {
            throw OverflowException.INSTANCE;
        }
        return r;
    }

    public static long subtractExact(long x, long y) throws OverflowException {
        long r = x - y;
        if (((x ^ y) & (x ^ r)) < 0) {
            throw OverflowException.INSTANCE;
        }
        return r;
    }

    public static int toIntExact(long x) throws OverflowException {
        int r = (int) x;
        if (r != x) {
            throw OverflowException.INSTANCE;
        }
        return r;
    }

    public static int multiplyExact(int x, int y) throws OverflowException {
        // copy&paste from Math.multiplyExact
        long r = (long) x * (long) y;
        if ((int) r != r) {
            throw OverflowException.INSTANCE;
        }
        return (int) r;
    }

    public static long multiplyExact(long x, long y) throws OverflowException {
        // copy&paste from Math.multiplyExact
        long r = x * y;
        long ax = Math.abs(x);
        long ay = Math.abs(y);
        if (((ax | ay) >>> 31 != 0)) {
            if (((y != 0) && (r / y != x)) || (x == Long.MIN_VALUE && y == -1)) {
                throw OverflowException.INSTANCE;
            }
        }
        return r;
    }

    private static final MBeanServer SERVER;
    private static final String OPERATION_NAME = "gcRun";
    private static final Object[] PARAMS = new Object[]{null};
    private static final String[] SIGNATURE = new String[]{String[].class.getName()};
    private static final ObjectName OBJECT_NAME;

    static {
        if (ImageInfo.inImageCode()) {
            OBJECT_NAME = null;
            SERVER = null;
        } else {
            SERVER = ManagementFactory.getPlatformMBeanServer();
            ObjectName n;
            try {
                n = new ObjectName("com.sun.management:type=DiagnosticCommand");
            } catch (final MalformedObjectNameException e) {
                n = null;
            }
            OBJECT_NAME = n;
        }
    }

    /**
     * {@link System#gc()} does not force a GC, but the DiagnosticCommand "gcRun" does.
     */
    @TruffleBoundary
    public static void forceFullGC() {
        if (OBJECT_NAME != null && SERVER != null) {
            try {
                SERVER.invoke(OBJECT_NAME, OPERATION_NAME, PARAMS, SIGNATURE);
            } catch (InstanceNotFoundException | ReflectionException | MBeanException e) {
                // use fallback
            }
        }
        System.gc();
        Runtime.getRuntime().freeMemory();
    }

    @TruffleBoundary
    public static void dumpHeap(String path) {
        if (SERVER != null) {
            try {
                Class<?> mxBeanClass = Class.forName("com.sun.management.HotSpotDiagnosticMXBean");
                Object mxBean = ManagementFactory.newPlatformMXBeanProxy(SERVER,
                                "com.sun.management:type=HotSpotDiagnostic",
                                mxBeanClass);
                mxBeanClass.getMethod("dumpHeap", String.class, boolean.class).invoke(mxBean, path, true);
            } catch (Throwable e) {
                System.err.println("Cannot dump heap: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Get the existing or create a new {@link CallTarget} for the provided root node.
     */
    @TruffleBoundary
    public static RootCallTarget getOrCreateCallTarget(RootNode rootNode) {
        return rootNode.getCallTarget();
    }

    @TruffleBoundary
    public static String format(String fmt, Object... args) {
        return String.format(fmt, args);
    }

    @TruffleBoundary
    public static String replace(String self, String old, String with) {
        return self.replace(old, with);
    }

    @TruffleBoundary(allowInlining = true)
    public static String newString(byte[] bytes) {
        return new String(bytes);
    }

    @TruffleBoundary(allowInlining = true)
    public static String newString(byte[] bytes, int offset, int length) {
        return new String(bytes, offset, length);
    }

    @TruffleBoundary(allowInlining = true)
    public static String newString(char[] chars) {
        return new String(chars);
    }

    @TruffleBoundary
    public static String newString(int[] codePoints, int offset, int count) {
        return new String(codePoints, offset, count);
    }

    @TruffleBoundary(allowInlining = true)
    public static StringBuilder newStringBuilder() {
        return new StringBuilder();
    }

    @TruffleBoundary(allowInlining = true)
    public static StringBuilder newStringBuilder(String str) {
        return new StringBuilder(str);
    }

    @TruffleBoundary(allowInlining = true)
    public static StringBuilder newStringBuilder(int capacity) {
        return new StringBuilder(capacity);
    }

    @TruffleBoundary(allowInlining = true)
    public static String sbToString(StringBuilder sb) {
        return sb.toString();
    }

    @TruffleBoundary(allowInlining = true)
    public static String substring(StringBuilder sb, int start, int end) {
        return sb.substring(start, end);
    }

    @TruffleBoundary(allowInlining = true)
    public static int indexOf(StringBuilder sb, String str, int start) {
        return sb.indexOf(str, start);
    }

    @TruffleBoundary(allowInlining = true)
    public static String substring(String s, int start, int end) {
        return s.substring(start, end);
    }

    @TruffleBoundary(allowInlining = true)
    public static String substring(String s, int start) {
        return s.substring(start);
    }

    @TruffleBoundary(allowInlining = true)
    public static int indexOf(String s, char chr) {
        return s.indexOf(chr);
    }

    @TruffleBoundary(allowInlining = true)
    public static int indexOf(String s, String sep) {
        return s.indexOf(sep);
    }

    @TruffleBoundary(allowInlining = true)
    public static int lastIndexOf(String s, char chr) {
        return s.lastIndexOf(chr);
    }

    @TruffleBoundary(allowInlining = true)
    public static int lastIndexOf(String s, String sep) {
        return s.lastIndexOf(sep);
    }

    @TruffleBoundary(allowInlining = true)
    public static StringBuilder append(StringBuilder sb, char c) {
        return sb.append(c);
    }

    @TruffleBoundary(allowInlining = true)
    public static StringBuilder append(StringBuilder sb, Object... args) {
        for (Object arg : args) {
            sb.append(arg);
        }
        return sb;
    }

    @TruffleBoundary(allowInlining = true)
    public static StringBuilder append(StringBuilder sb, String s) {
        return sb.append(s);
    }

    @TruffleBoundary(allowInlining = true)
    public static StringBuilder append(StringBuilder sb, int i) {
        return sb.append(i);
    }

    @TruffleBoundary(allowInlining = true)
    public static StringBuilder append(StringBuilder sb, String s, int start, int end) {
        return sb.append(s, start, end);
    }

    @TruffleBoundary(allowInlining = true)
    public static StringBuilder append(StringBuilder sb, StringBuilder s) {
        return sb.append(s);
    }

    @TruffleBoundary(allowInlining = true)
    public static StringBuilder appendCodePoint(StringBuilder sb, int codePoint) {
        return sb.appendCodePoint(codePoint);
    }

    @TruffleBoundary(allowInlining = true)
    public static String toString(CharSequence sequence) {
        return sequence.toString();
    }

    @TruffleBoundary(allowInlining = true)
    public static String trim(String s) {
        return s.trim();
    }

    @TruffleBoundary(allowInlining = true)
    public static String trim(CharSequence sequence) {
        return sequence.toString().trim();
    }

    @TruffleBoundary(allowInlining = true)
    public static String trimLeft(CharSequence sequence) {
        int len = sequence.length();
        int st = 0;

        while ((st < len) && (sequence.charAt(st) <= ' ')) {
            st++;
        }

        final String s = sequence.toString();
        return (st > 0) ? substring(s, st, len) : s;
    }

    @TruffleBoundary(allowInlining = true)
    public static String toLowerCase(String s) {
        return s.toLowerCase();
    }

    @TruffleBoundary(allowInlining = true)
    public static String toUpperCase(String s) {
        return s.toUpperCase();
    }

    @TruffleBoundary(allowInlining = true)
    public static int sbLength(StringBuilder sb) {
        return sb.length();
    }

    @TruffleBoundary
    public static String getPythonArch() {
        String arch = System.getProperty("os.arch", "");
        if (arch.equals("amd64")) {
            // be compatible with CPython's designation
            arch = "x86_64";
        }
        return arch;
    }

    @TruffleBoundary
    public static ByteBuffer allocateByteBuffer(int capacity) {
        return ByteBuffer.allocate(capacity);
    }

    @TruffleBoundary
    public static ByteBuffer wrapByteBuffer(byte[] array) {
        return ByteBuffer.wrap(array);
    }

    @TruffleBoundary
    public static ByteBuffer wrapByteBuffer(byte[] array, int offset, int length) {
        return ByteBuffer.wrap(array, offset, length);
    }

    @TruffleBoundary
    public static byte[] getBufferArray(ByteBuffer buffer) {
        return buffer.array();
    }

    @TruffleBoundary
    public static int getBufferPosition(ByteBuffer buffer) {
        return buffer.position();
    }

    @TruffleBoundary
    public static int getBufferLimit(ByteBuffer buffer) {
        return buffer.limit();
    }

    @TruffleBoundary
    public static int getBufferRemaining(ByteBuffer buffer) {
        return buffer.remaining();
    }

    @TruffleBoundary
    public static void flipBuffer(ByteBuffer buffer) {
        buffer.flip();
    }

    @TruffleBoundary
    public static boolean bufferHasRemaining(ByteBuffer buffer) {
        return buffer.hasRemaining();
    }

    @TruffleBoundary
    public static boolean equals(Object a, Object b) {
        return a.equals(b);
    }

    @TruffleBoundary
    public static <E> ArrayDeque<E> newDeque() {
        return new ArrayDeque<>();
    }

    @TruffleBoundary
    public static <E> void push(ArrayDeque<E> q, E e) {
        q.push(e);
    }

    @TruffleBoundary
    public static <E> E pop(ArrayDeque<E> q) {
        return q.pop();
    }

    @TruffleBoundary
    public static <E> List<E> newList() {
        return new ArrayList<E>();
    }

    @TruffleBoundary
    public static <E> void add(List<E> list, E e) {
        list.add(e);
    }

    @TruffleBoundary
    public static <E> E get(List<E> list, int index) {
        return list.get(index);
    }

    @TruffleBoundary
    public static <E> Object[] toArray(List<E> list) {
        return list.toArray();
    }

    /**
     * Same as {@link Character#isBmpCodePoint(int)}.
     */
    public static boolean isBmpCodePoint(int codePoint) {
        return codePoint >>> 16 == 0;
        // Optimized form of:
        // codePoint >= MIN_VALUE && codePoint <= MAX_VALUE
        // We consistently use logical shift (>>>) to facilitate
        // additional runtime optimizations.
    }

    public static ValueProfile createValueIdentityProfile() {
        CompilerAsserts.neverPartOfCompilation();
        return PythonLanguage.get(null).isSingleContext() ? ValueProfile.createIdentityProfile() : ValueProfile.createClassProfile();
    }

    @TruffleBoundary
    public static void createMember(PythonObjectSlowPathFactory factory, PythonLanguage language, Object klass, Class<?> nodeClass, String name, String doc, int idx,
                    Function<PythonLanguage, RootNode> rootNodeSupplier) {
        RootCallTarget callTarget = language.createCachedCallTarget(rootNodeSupplier, nodeClass, idx);
        PBuiltinFunction getter = factory.createGetSetBuiltinFunction(name, klass, 0, callTarget);
        GetSetDescriptor callable = factory.createGetSetDescriptor(getter, null, name, klass, false);
        callable.setAttribute(__DOC__, doc);
        WriteAttributeToObjectNode.getUncached(true).execute(klass, name, callable);
    }

    @TruffleBoundary
    public static PBuiltinFunction createMethod(PythonLanguage language, Object klass, Class<?> nodeClass, Object type, int numDefaults, Supplier<PythonBuiltinBaseNode> nodeSupplier,
                    Object... callTargetCacheKeys) {
        return createMethod(PythonObjectFactory.getUncached(), language, klass, nodeClass, type, numDefaults, nodeSupplier, callTargetCacheKeys);
    }

    @TruffleBoundary
    public static PBuiltinFunction createMethod(PythonObjectFactory factory, PythonLanguage language, Object klass, Class<?> nodeClass, Object type, int numDefaults,
                    Supplier<PythonBuiltinBaseNode> nodeSupplier,
                    Object... callTargetCacheKeys) {
        Builtin builtin = nodeClass.getAnnotation(Builtin.class);
        RootCallTarget callTarget = language.createCachedCallTarget(l -> {
            NodeFactory<PythonBuiltinBaseNode> nodeFactory = new BuiltinFunctionRootNode.StandaloneBuiltinFactory<>(nodeSupplier.get());
            return new BuiltinFunctionRootNode(l, builtin, nodeFactory, true);
        }, nodeClass, createCalltargetKeys(callTargetCacheKeys, nodeClass));
        int flags = PBuiltinFunction.getFlags(builtin, callTarget);
        PBuiltinFunction function = PythonObjectFactory.getUncached().createBuiltinFunction(builtin.name(), type, numDefaults, flags, callTarget);
        if (klass != null) {
            WriteAttributeToObjectNode.getUncached(true).execute(klass, builtin.name(), function);
        }
        return function;
    }

    @TruffleBoundary
    public static void createConstructor(PythonObjectSlowPathFactory factory, PythonLanguage language, Object klass, Class<?> nodeClass, Supplier<PythonBuiltinBaseNode> nodeSupplier,
                    Object... callTargetCacheKeys) {
        Builtin builtin = nodeClass.getAnnotation(Builtin.class);
        assert __NEW__.equals(builtin.name());
        assert IsSubtypeNode.getUncached().execute(klass, PythonBuiltinClassType.PTuple);
        RootCallTarget callTarget = language.createCachedCallTarget(l -> {
            NodeFactory<PythonBuiltinBaseNode> nodeFactory = new BuiltinFunctionRootNode.StandaloneBuiltinFactory<>(nodeSupplier.get());
            return new BuiltinFunctionRootNode(l, builtin, nodeFactory, true, PythonBuiltinClassType.PTuple);
        }, nodeClass, createCalltargetKeys(callTargetCacheKeys, nodeClass));
        int flags = PBuiltinFunction.getFlags(builtin, callTarget);
        PBuiltinFunction function = factory.createBuiltinFunction(builtin.name(), PythonBuiltinClassType.PTuple, 1, flags, callTarget);
        WriteAttributeToObjectNode.getUncached(true).execute(klass, __NEW__, function);
    }

    private static Object[] createCalltargetKeys(Object[] callTargetCacheKeys, Class<?> nodeClass) {
        Object[] keys = new Object[callTargetCacheKeys.length + 1];
        keys[0] = nodeClass;
        arraycopy(callTargetCacheKeys, 0, keys, 1, callTargetCacheKeys.length);
        return keys;
    }

    public static Unsafe initUnsafe() {
        try {
            return Unsafe.getUnsafe();
        } catch (SecurityException se) {
            try {
                Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
                theUnsafe.setAccessible(true);
                return (Unsafe) theUnsafe.get(Unsafe.class);
            } catch (Exception e) {
                throw new UnsupportedOperationException("Cannot initialize Unsafe for the native POSIX backend", e);
            }
        }
    }
}
