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
package com.oracle.graal.python.util;

import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DOC__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___NEW__;
import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;

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

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.ellipsis.PEllipsis;
import com.oracle.graal.python.pegparser.sst.ConstantValue;
import org.graalvm.nativeimage.ImageInfo;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.GetSetDescriptor;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.object.PythonObjectSlowPathFactory;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.memory.ByteArraySupport;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.Encoding;

import sun.misc.Unsafe;

public final class PythonUtils {

    public static final PCell[] NO_CLOSURE = new PCell[0];
    public static final ByteArraySupport arrayAccessor;
    public static final ConditionProfile[] DISABLED = new ConditionProfile[]{ConditionProfile.getUncached()};

    static {
        if (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN) {
            arrayAccessor = ByteArraySupport.bigEndian();
        } else {
            arrayAccessor = ByteArraySupport.littleEndian();
        }
    }

    private PythonUtils() {
        // no instances
    }

    /**
     * Encoding of all {@link TruffleString} instances.
     */
    public static final TruffleString.Encoding TS_ENCODING = TruffleString.Encoding.UTF_32;

    public static final String[] EMPTY_STRING_ARRAY = new String[0];
    public static final TruffleString[] EMPTY_TRUFFLESTRING_ARRAY = new TruffleString[0];
    public static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];
    public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
    public static final int[] EMPTY_INT_ARRAY = new int[0];
    public static final double[] EMPTY_DOUBLE_ARRAY = new double[0];
    public static final char[] EMPTY_CHAR_ARRAY = new char[0];

    /**
     * Returns an estimate for the initial capacity of a
     * {@link com.oracle.truffle.api.strings.TruffleStringBuilder}.
     *
     * @param cpCount the initial capacity in code points
     * @return the estimate in bytes
     */
    public static int tsbCapacity(int cpCount) {
        assert TS_ENCODING == Encoding.UTF_32;
        return 4 * cpCount;
    }

    /**
     * Uncached conversion of {@link String} to {@link TruffleString}. The intended use of this
     * method is in static initializers where the argument is a string literal (or a static final
     * constant).
     */
    @TruffleBoundary
    public static TruffleString tsLiteral(String s) {
        assert s != null;
        return TruffleString.fromJavaStringUncached(s, TS_ENCODING);
    }

    /**
     * Uncached conversion of {@link String} to {@link TruffleString}. The intended use of this
     * method is in slow-path where the argument is a variable as a shortcut for
     * {@link TruffleString#fromJavaStringUncached(String, Encoding)}.
     */
    @TruffleBoundary
    public static TruffleString toTruffleStringUncached(String s) {
        return s == null ? null : TruffleString.fromJavaStringUncached(s, TS_ENCODING);
    }

    /**
     * Creates an array of {@link TruffleString}s using uncached conversion. The intended use of
     * this method is in static initializers where the arguments are string literals as a shortcut
     * for {@code TruffleString[] X = {tsLiteral("a"), tsLiteral("b")};}
     */
    @TruffleBoundary
    public static TruffleString[] tsArray(String... s) {
        TruffleString[] result = new TruffleString[s.length];
        for (int i = 0; i < result.length; ++i) {
            result[i] = tsLiteral(s[i]);
        }
        return result;
    }

    /**
     * Creates an array of {@link TruffleString}s using uncached conversion. The intended use of
     * this method is in slow-path where the argument is a variable.
     */
    @TruffleBoundary
    public static TruffleString[] toTruffleStringArrayUncached(String[] s) {
        if (s == null) {
            return null;
        }
        if (s.length == 0) {
            return EMPTY_TRUFFLESTRING_ARRAY;
        }
        TruffleString[] result = new TruffleString[s.length];
        for (int i = 0; i < result.length; ++i) {
            result[i] = toTruffleStringUncached(s[i]);
        }
        return result;
    }

    /**
     * Creates an array of {@link Object}'s. The intended use of this method is in slow-path in
     * calls to methods like {@link PythonObjectFactory#createTuple(Object[])}.
     */
    public static Object[] convertToObjectArray(TruffleString[] src) {
        if (src == null) {
            return null;
        }
        if (src.length == 0) {
            return EMPTY_OBJECT_ARRAY;
        }
        Object[] result = new Object[src.length];
        for (int i = 0; i < src.length; ++i) {
            result[i] = src[i];
        }
        return result;
    }

    @TruffleBoundary
    public static TruffleString getMessage(Exception ex) {
        return toTruffleStringUncached(ex.getMessage());
    }

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

    public static int negateExact(int a) throws OverflowException {
        if (a == Integer.MIN_VALUE) {
            throw OverflowException.INSTANCE;
        }

        return -a;
    }

    public static long negateExact(long a) throws OverflowException {
        if (a == Long.MIN_VALUE) {
            throw OverflowException.INSTANCE;
        }

        return -a;
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
    public static String formatJString(String fmt, Object... args) {
        return String.format(fmt, args);
    }

    @TruffleBoundary
    public static TruffleString getPythonArch() {
        String arch = System.getProperty("os.arch", "");
        if (arch.equals("amd64")) {
            // be compatible with CPython's designation
            arch = "x86_64";
        }
        return toTruffleStringUncached(arch);
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
    public static void createMember(PythonObjectSlowPathFactory factory, PythonLanguage language, Object klass, Class<?> nodeClass, TruffleString name, TruffleString doc, int idx,
                    Function<PythonLanguage, RootNode> rootNodeSupplier) {
        RootCallTarget callTarget = language.createCachedCallTarget(rootNodeSupplier, nodeClass, idx);
        PBuiltinFunction getter = factory.createGetSetBuiltinFunction(name, klass, 0, callTarget);
        GetSetDescriptor callable = factory.createGetSetDescriptor(getter, null, name, klass, false);
        callable.setAttribute(T___DOC__, doc);
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
        TruffleString name = toTruffleStringUncached(builtin.name());
        PBuiltinFunction function = factory.createBuiltinFunction(name, type, numDefaults, flags, callTarget);
        if (klass != null) {
            WriteAttributeToObjectNode.getUncached(true).execute(klass, name, function);
        }
        return function;
    }

    @TruffleBoundary
    public static void createConstructor(PythonObjectSlowPathFactory factory, PythonLanguage language, Object klass, Class<?> nodeClass, Supplier<PythonBuiltinBaseNode> nodeSupplier,
                    Object... callTargetCacheKeys) {
        Builtin builtin = nodeClass.getAnnotation(Builtin.class);
        assert J___NEW__.equals(builtin.name());
        assert IsSubtypeNode.getUncached().execute(klass, PythonBuiltinClassType.PTuple);
        RootCallTarget callTarget = language.createCachedCallTarget(l -> {
            NodeFactory<PythonBuiltinBaseNode> nodeFactory = new BuiltinFunctionRootNode.StandaloneBuiltinFactory<>(nodeSupplier.get());
            return new BuiltinFunctionRootNode(l, builtin, nodeFactory, false, PythonBuiltinClassType.PTuple);
        }, nodeClass, createCalltargetKeys(callTargetCacheKeys, nodeClass));
        int flags = PBuiltinFunction.getFlags(builtin, callTarget);
        PBuiltinFunction function = factory.createBuiltinFunction(toTruffleStringUncached(builtin.name()), PythonBuiltinClassType.PTuple, 1, flags, callTarget);
        PBuiltinMethod method = factory.createBuiltinMethod(PythonBuiltinClassType.PTuple, function);
        WriteAttributeToObjectNode.getUncached(true).execute(klass, T___NEW__, method);
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

    public static void copyFrameSlot(Frame frameToSync, MaterializedFrame target, int slot) {
        if (frameToSync.isObject(slot)) {
            target.setObject(slot, frameToSync.getObject(slot));
        } else if (frameToSync.isInt(slot)) {
            target.setInt(slot, frameToSync.getInt(slot));
        } else if (frameToSync.isLong(slot)) {
            target.setLong(slot, frameToSync.getLong(slot));
        } else if (frameToSync.isBoolean(slot)) {
            target.setBoolean(slot, frameToSync.getBoolean(slot));
        } else if (frameToSync.isDouble(slot)) {
            target.setDouble(slot, frameToSync.getDouble(slot));
        } else if (frameToSync.isFloat(slot)) {
            target.setFloat(slot, frameToSync.getFloat(slot));
        } else if (frameToSync.isByte(slot)) {
            target.setByte(slot, frameToSync.getByte(slot));
        }
    }

    public static TruffleString[] objectArrayToTruffleStringArray(Object[] array, CastToTruffleStringNode cast) {
        if (array.length == 0) {
            return EMPTY_TRUFFLESTRING_ARRAY;
        }
        TruffleString[] result = new TruffleString[array.length];
        for (int i = 0; i < array.length; i++) {
            result[i] = cast.execute(array[i]);
        }
        return result;
    }

    @TruffleBoundary
    public static Source createFakeSource() {
        return Source.newBuilder(PythonLanguage.ID, "", "").build();
    }

    public static final class NodeCounterWithLimit implements NodeVisitor {
        private int count;
        private final int limit;

        public NodeCounterWithLimit(int counterStart, int limit) {
            this.count = counterStart;
            this.limit = limit;
        }

        public NodeCounterWithLimit(int limit) {
            this.limit = limit;
        }

        public boolean visit(Node node) {
            return ++count < limit;
        }

        public int getCount() {
            return count;
        }

        public boolean isOverLimit() {
            return count >= limit;
        }
    }

    public static Object pythonObjectFromConstantValue(ConstantValue v, PythonObjectFactory factory) {
        switch (v.kind) {
            case BOOLEAN:
                return v.getBoolean();
            case LONG: {
                long l = v.getLong();
                if (l == (int) l) {
                    return (int) l;
                }
                return l;
            }
            case DOUBLE:
                return v.getDouble();
            case COMPLEX: {
                double[] c = v.getComplex();
                return factory.createComplex(c[0], c[1]);
            }
            case NONE:
                return PNone.NONE;
            case ELLIPSIS:
                return PEllipsis.INSTANCE;
            case BIGINTEGER:
                return factory.createInt(v.getBigInteger());
            case BYTES:
                return factory.createBytes(v.getBytes());
            case RAW:
                return v.getRaw(TruffleString.class);
            case TUPLE:
            case FROZENSET:
                // These cases cannot happen:
                // - when called from Sst2ObjVisitor, the SST comes from the parser which never
                // emits tuples or frozensets
                // - when called from the compiler of pattern matching, the SST has been checked by
                // Validator#validatePatternMatchValue() which rejects tuples and frozensets
            default:
                throw shouldNotReachHere();
        }
    }
}
