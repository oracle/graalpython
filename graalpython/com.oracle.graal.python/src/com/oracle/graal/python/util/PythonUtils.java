/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PString;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___NEW__;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_STRING;
import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;

import java.io.IOException;
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
import org.graalvm.nativeimage.VMRuntime;
import org.graalvm.polyglot.io.ByteSequence;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.ellipsis.PEllipsis;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.pegparser.scope.ScopeEnvironment;
import com.oracle.graal.python.pegparser.sst.ConstantValue;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.object.PythonObjectSlowPathFactory;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.dsl.GeneratedBy;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.memory.ByteArraySupport;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.Encoding;
import com.oracle.truffle.api.strings.TruffleStringBuilder;
import com.oracle.truffle.api.strings.TruffleStringBuilderUTF32;

import sun.misc.Unsafe;

public final class PythonUtils {

    public static final ByteArraySupport ARRAY_ACCESSOR_LE = ByteArraySupport.littleEndian();
    public static final ByteArraySupport ARRAY_ACCESSOR_BE = ByteArraySupport.bigEndian();
    public static final ByteArraySupport ARRAY_ACCESSOR = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN ? ARRAY_ACCESSOR_LE : ARRAY_ACCESSOR_BE;
    public static final ByteArraySupport ARRAY_ACCESSOR_SWAPPED = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN ? ARRAY_ACCESSOR_BE : ARRAY_ACCESSOR_LE;

    public static final ConditionProfile[] DISABLED = new ConditionProfile[]{ConditionProfile.getUncached()};

    public static ByteArraySupport byteArraySupport(ByteOrder order) {
        return order == ByteOrder.LITTLE_ENDIAN ? ARRAY_ACCESSOR_LE : ARRAY_ACCESSOR_BE;
    }

    private PythonUtils() {
        // no instances
    }

    /**
     * Encoding of all {@link TruffleString} instances.
     */
    public static final TruffleString.Encoding TS_ENCODING = TruffleString.Encoding.UTF_32;

    public static TruffleStringBuilderUTF32 createStringBuilder() {
        return TruffleStringBuilder.createUTF32();
    }

    public static final String[] EMPTY_STRING_ARRAY = new String[0];
    public static final TruffleString[] EMPTY_TRUFFLESTRING_ARRAY = new TruffleString[0];
    public static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];
    public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
    public static final int[] EMPTY_INT_ARRAY = new int[0];
    public static final long[] EMPTY_LONG_ARRAY = new long[0];
    public static final double[] EMPTY_DOUBLE_ARRAY = new double[0];
    public static final char[] EMPTY_CHAR_ARRAY = new char[0];
    public static final ByteSequence EMPTY_BYTE_SEQUENCE = ByteSequence.create(EMPTY_BYTE_ARRAY);

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
        return TruffleString.fromConstant(s, TS_ENCODING);
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

    public static PString toPString(TruffleString name) {
        return new PString(PString, PString.getInstanceShape(PythonLanguage.get(null)), name);
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

    // parser.c:_Py_Mangle
    @TruffleBoundary
    public static TruffleString mangleName(TruffleString className, TruffleString name) {
        return toTruffleStringUncached(ScopeEnvironment.mangle(className.toJavaStringUncached(), name.toJavaStringUncached()));
    }

    @TruffleBoundary
    public static TruffleString getMessage(Exception ex) {
        return toTruffleStringUncached(ex.getMessage());
    }

    /**
     * Execute Arrays.fill and puts all exceptions on slow path.
     */
    public static void fill(byte[] array, int from, int to, byte value) {
        try {
            Arrays.fill(array, from, to, value);
        } catch (Throwable t) {
            // this is really unexpected and we want to break exception edges in compiled code
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw t;
        }
    }

    /**
     * Execute Arrays.fill and puts all exceptions on slow path.
     */
    public static void fill(Object[] array, int from, int to, Object value) {
        try {
            Arrays.fill(array, from, to, value);
        } catch (Throwable t) {
            // this is really unexpected and we want to break exception edges in compiled code
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw t;
        }
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

    public static int toIntError(long x) {
        int r = (int) x;
        if (r != x) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw PRaiseNode.raiseUncached(null, SystemError, ErrorMessages.INTERNAL_INT_OVERFLOW);
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

    /**
     * Fast check whether {@code number % (2^twoExponent) == 0}.
     */
    public static boolean isDivisible(int number, int twoExponent) {
        assert number >= 0;
        return ((0xffffffff << twoExponent) & number) == number;
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
        if (ImageInfo.inImageCode()) {
            try {
                VMRuntime.dumpHeap(path, true);
            } catch (UnsupportedOperationException | IOException e) {
                System.err.println("Heap dump creation failed." + e.getMessage());
                e.printStackTrace();
            }
        } else if (SERVER != null) {
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
        return new ArrayList<>();
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
    public static PBuiltinFunction createMethod(PythonLanguage language, Object klass, NodeFactory<? extends PythonBuiltinBaseNode> nodeFactory, Object type, int numDefaults) {
        Class<? extends PythonBuiltinBaseNode> nodeClass = nodeFactory.getNodeClass();
        Builtin builtin = nodeClass.getAnnotation(Builtin.class);
        RootCallTarget callTarget = language.createCachedCallTarget(l -> new BuiltinFunctionRootNode(l, builtin, nodeFactory, true), nodeClass);
        return createMethod(PythonObjectFactory.getUncached(), klass, builtin, callTarget, type, numDefaults);
    }

    @TruffleBoundary
    public static PBuiltinFunction createMethod(PythonObjectFactory factory, Object klass, Builtin builtin, RootCallTarget callTarget, Object type,
                    int numDefaults) {
        assert callTarget.getRootNode() instanceof BuiltinFunctionRootNode r && r.getBuiltin() == builtin;
        int flags = PBuiltinFunction.getFlags(builtin, callTarget);
        TruffleString name = toTruffleStringUncached(builtin.name());
        PBuiltinFunction function = factory.createBuiltinFunction(name, type, numDefaults, flags, callTarget);
        if (klass != null) {
            WriteAttributeToObjectNode.getUncached(true).execute(klass, name, function);
        }
        return function;
    }

    @TruffleBoundary
    public static void createConstructor(PythonObjectSlowPathFactory factory, Object klass, Builtin builtin, RootCallTarget callTarget) {
        assert J___NEW__.equals(builtin.name());
        assert IsSubtypeNode.getUncached().execute(klass, PythonBuiltinClassType.PTuple);
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
                throw new UnsupportedOperationException("Cannot initialize Unsafe for the native backends", e);
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

    public static TruffleString[] objectArrayToTruffleStringArray(Node inliningTarget, Object[] array, CastToTruffleStringNode cast) {
        if (array.length == 0) {
            return EMPTY_TRUFFLESTRING_ARRAY;
        }
        TruffleString[] result = new TruffleString[array.length];
        for (int i = 0; i < array.length; i++) {
            result[i] = cast.execute(inliningTarget, array[i]);
        }
        return result;
    }

    public static Source createFakeSource() {
        return createFakeSource(T_EMPTY_STRING);
    }

    @TruffleBoundary
    public static Source createFakeSource(TruffleString name) {
        if (PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER) {
            // The DSL interpreter requires character-based sources.
            return Source.newBuilder(PythonLanguage.ID, "", name.toJavaStringUncached()).content(Source.CONTENT_NONE).build();
        } else {
            return Source.newBuilder(PythonLanguage.ID, EMPTY_BYTE_SEQUENCE, name.toJavaStringUncached()).build();
        }
    }

    public static Object[] prependArgument(Object primary, Object[] arguments) {
        Object[] result = new Object[arguments.length + 1];
        result[0] = primary;
        arraycopy(arguments, 0, result, 1, arguments.length);
        return result;
    }

    public static boolean isAscii(TruffleString string, TruffleString.GetCodeRangeNode getCodeRangeNode) {
        return getCodeRangeNode.execute(string, TS_ENCODING) == TruffleString.CodeRange.ASCII;
    }

    public static byte[] getAsciiBytes(TruffleString string, TruffleString.CopyToByteArrayNode copyToByteArrayNode, TruffleString.SwitchEncodingNode switchEncodingNode) {
        assert string.getCodeRangeUncached(TS_ENCODING) == TruffleString.CodeRange.ASCII;
        TruffleString ascii = switchEncodingNode.execute(string, Encoding.US_ASCII);
        byte[] data = new byte[ascii.byteLength(Encoding.US_ASCII)];
        copyToByteArrayNode.execute(ascii, 0, data, 0, data.length, Encoding.US_ASCII);
        return data;
    }

    public static ConditionProfile[] createConditionProfiles(int n) {
        ConditionProfile[] profiles = new ConditionProfile[n];
        for (int i = 0; i < profiles.length; i++) {
            profiles[i] = ConditionProfile.create();
        }
        return profiles;
    }

    public static Object builtinClassToType(Object cls) {
        if (cls instanceof PythonBuiltinClass builtinClass) {
            return builtinClass.getType();
        }
        return cls;
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

    public static long crc32(int initialValue, byte[] bytes, int offset, int length) {
        int crc = ~initialValue;
        for (int i = 0; i < length; ++i) {
            crc = CRC_32_TAB[(crc ^ bytes[offset + i]) & 0xff] ^ (crc >>> 8);
        }
        return ~crc & 0xFFFFFFFFL;
    }

    private static final int[] CRC_32_TAB = {
                    0x00000000, 0x77073096, 0xee0e612c, 0x990951ba, 0x076dc419, 0x706af48f, 0xe963a535, 0x9e6495a3,
                    0x0edb8832, 0x79dcb8a4, 0xe0d5e91e, 0x97d2d988, 0x09b64c2b, 0x7eb17cbd, 0xe7b82d07, 0x90bf1d91,
                    0x1db71064, 0x6ab020f2, 0xf3b97148, 0x84be41de, 0x1adad47d, 0x6ddde4eb, 0xf4d4b551, 0x83d385c7,
                    0x136c9856, 0x646ba8c0, 0xfd62f97a, 0x8a65c9ec, 0x14015c4f, 0x63066cd9, 0xfa0f3d63, 0x8d080df5,
                    0x3b6e20c8, 0x4c69105e, 0xd56041e4, 0xa2677172, 0x3c03e4d1, 0x4b04d447, 0xd20d85fd, 0xa50ab56b,
                    0x35b5a8fa, 0x42b2986c, 0xdbbbc9d6, 0xacbcf940, 0x32d86ce3, 0x45df5c75, 0xdcd60dcf, 0xabd13d59,
                    0x26d930ac, 0x51de003a, 0xc8d75180, 0xbfd06116, 0x21b4f4b5, 0x56b3c423, 0xcfba9599, 0xb8bda50f,
                    0x2802b89e, 0x5f058808, 0xc60cd9b2, 0xb10be924, 0x2f6f7c87, 0x58684c11, 0xc1611dab, 0xb6662d3d,
                    0x76dc4190, 0x01db7106, 0x98d220bc, 0xefd5102a, 0x71b18589, 0x06b6b51f, 0x9fbfe4a5, 0xe8b8d433,
                    0x7807c9a2, 0x0f00f934, 0x9609a88e, 0xe10e9818, 0x7f6a0dbb, 0x086d3d2d, 0x91646c97, 0xe6635c01,
                    0x6b6b51f4, 0x1c6c6162, 0x856530d8, 0xf262004e, 0x6c0695ed, 0x1b01a57b, 0x8208f4c1, 0xf50fc457,
                    0x65b0d9c6, 0x12b7e950, 0x8bbeb8ea, 0xfcb9887c, 0x62dd1ddf, 0x15da2d49, 0x8cd37cf3, 0xfbd44c65,
                    0x4db26158, 0x3ab551ce, 0xa3bc0074, 0xd4bb30e2, 0x4adfa541, 0x3dd895d7, 0xa4d1c46d, 0xd3d6f4fb,
                    0x4369e96a, 0x346ed9fc, 0xad678846, 0xda60b8d0, 0x44042d73, 0x33031de5, 0xaa0a4c5f, 0xdd0d7cc9,
                    0x5005713c, 0x270241aa, 0xbe0b1010, 0xc90c2086, 0x5768b525, 0x206f85b3, 0xb966d409, 0xce61e49f,
                    0x5edef90e, 0x29d9c998, 0xb0d09822, 0xc7d7a8b4, 0x59b33d17, 0x2eb40d81, 0xb7bd5c3b, 0xc0ba6cad,
                    0xedb88320, 0x9abfb3b6, 0x03b6e20c, 0x74b1d29a, 0xead54739, 0x9dd277af, 0x04db2615, 0x73dc1683,
                    0xe3630b12, 0x94643b84, 0x0d6d6a3e, 0x7a6a5aa8, 0xe40ecf0b, 0x9309ff9d, 0x0a00ae27, 0x7d079eb1,
                    0xf00f9344, 0x8708a3d2, 0x1e01f268, 0x6906c2fe, 0xf762575d, 0x806567cb, 0x196c3671, 0x6e6b06e7,
                    0xfed41b76, 0x89d32be0, 0x10da7a5a, 0x67dd4acc, 0xf9b9df6f, 0x8ebeeff9, 0x17b7be43, 0x60b08ed5,
                    0xd6d6a3e8, 0xa1d1937e, 0x38d8c2c4, 0x4fdff252, 0xd1bb67f1, 0xa6bc5767, 0x3fb506dd, 0x48b2364b,
                    0xd80d2bda, 0xaf0a1b4c, 0x36034af6, 0x41047a60, 0xdf60efc3, 0xa867df55, 0x316e8eef, 0x4669be79,
                    0xcb61b38c, 0xbc66831a, 0x256fd2a0, 0x5268e236, 0xcc0c7795, 0xbb0b4703, 0x220216b9, 0x5505262f,
                    0xc5ba3bbe, 0xb2bd0b28, 0x2bb45a92, 0x5cb36a04, 0xc2d7ffa7, 0xb5d0cf31, 0x2cd99e8b, 0x5bdeae1d,
                    0x9b64c2b0, 0xec63f226, 0x756aa39c, 0x026d930a, 0x9c0906a9, 0xeb0e363f, 0x72076785, 0x05005713,
                    0x95bf4a82, 0xe2b87a14, 0x7bb12bae, 0x0cb61b38, 0x92d28e9b, 0xe5d5be0d, 0x7cdcefb7, 0x0bdbdf21,
                    0x86d3d2d4, 0xf1d4e242, 0x68ddb3f8, 0x1fda836e, 0x81be16cd, 0xf6b9265b, 0x6fb077e1, 0x18b74777,
                    0x88085ae6, 0xff0f6a70, 0x66063bca, 0x11010b5c, 0x8f659eff, 0xf862ae69, 0x616bffd3, 0x166ccf45,
                    0xa00ae278, 0xd70dd2ee, 0x4e048354, 0x3903b3c2, 0xa7672661, 0xd06016f7, 0x4969474d, 0x3e6e77db,
                    0xaed16a4a, 0xd9d65adc, 0x40df0b66, 0x37d83bf0, 0xa9bcae53, 0xdebb9ec5, 0x47b2cf7f, 0x30b5ffe9,
                    0xbdbdf21c, 0xcabac28a, 0x53b39330, 0x24b4a3a6, 0xbad03605, 0xcdd70693, 0x54de5729, 0x23d967bf,
                    0xb3667a2e, 0xc4614ab8, 0x5d681b02, 0x2a6f2b94, 0xb40bbe37, 0xc30c8ea1, 0x5a05df1b, 0x2d02ef8d
    };

    public static long crcHqx(int initialValue, byte[] bytes, int offset, int length) {
        int crc = initialValue & 0xffff;
        for (int i = offset; i < length; i++) {
            crc = ((crc << 8) & 0xff00) ^ CRC_HQX_TAB[(crc >>> 8) ^ (bytes[i] & 0xff)];
        }
        return crc;
    }

    static final int[] CRC_HQX_TAB = new int[]{
                    0x0000, 0x1021, 0x2042, 0x3063, 0x4084, 0x50a5, 0x60c6, 0x70e7,
                    0x8108, 0x9129, 0xa14a, 0xb16b, 0xc18c, 0xd1ad, 0xe1ce, 0xf1ef,
                    0x1231, 0x0210, 0x3273, 0x2252, 0x52b5, 0x4294, 0x72f7, 0x62d6,
                    0x9339, 0x8318, 0xb37b, 0xa35a, 0xd3bd, 0xc39c, 0xf3ff, 0xe3de,
                    0x2462, 0x3443, 0x0420, 0x1401, 0x64e6, 0x74c7, 0x44a4, 0x5485,
                    0xa56a, 0xb54b, 0x8528, 0x9509, 0xe5ee, 0xf5cf, 0xc5ac, 0xd58d,
                    0x3653, 0x2672, 0x1611, 0x0630, 0x76d7, 0x66f6, 0x5695, 0x46b4,
                    0xb75b, 0xa77a, 0x9719, 0x8738, 0xf7df, 0xe7fe, 0xd79d, 0xc7bc,
                    0x48c4, 0x58e5, 0x6886, 0x78a7, 0x0840, 0x1861, 0x2802, 0x3823,
                    0xc9cc, 0xd9ed, 0xe98e, 0xf9af, 0x8948, 0x9969, 0xa90a, 0xb92b,
                    0x5af5, 0x4ad4, 0x7ab7, 0x6a96, 0x1a71, 0x0a50, 0x3a33, 0x2a12,
                    0xdbfd, 0xcbdc, 0xfbbf, 0xeb9e, 0x9b79, 0x8b58, 0xbb3b, 0xab1a,
                    0x6ca6, 0x7c87, 0x4ce4, 0x5cc5, 0x2c22, 0x3c03, 0x0c60, 0x1c41,
                    0xedae, 0xfd8f, 0xcdec, 0xddcd, 0xad2a, 0xbd0b, 0x8d68, 0x9d49,
                    0x7e97, 0x6eb6, 0x5ed5, 0x4ef4, 0x3e13, 0x2e32, 0x1e51, 0x0e70,
                    0xff9f, 0xefbe, 0xdfdd, 0xcffc, 0xbf1b, 0xaf3a, 0x9f59, 0x8f78,
                    0x9188, 0x81a9, 0xb1ca, 0xa1eb, 0xd10c, 0xc12d, 0xf14e, 0xe16f,
                    0x1080, 0x00a1, 0x30c2, 0x20e3, 0x5004, 0x4025, 0x7046, 0x6067,
                    0x83b9, 0x9398, 0xa3fb, 0xb3da, 0xc33d, 0xd31c, 0xe37f, 0xf35e,
                    0x02b1, 0x1290, 0x22f3, 0x32d2, 0x4235, 0x5214, 0x6277, 0x7256,
                    0xb5ea, 0xa5cb, 0x95a8, 0x8589, 0xf56e, 0xe54f, 0xd52c, 0xc50d,
                    0x34e2, 0x24c3, 0x14a0, 0x0481, 0x7466, 0x6447, 0x5424, 0x4405,
                    0xa7db, 0xb7fa, 0x8799, 0x97b8, 0xe75f, 0xf77e, 0xc71d, 0xd73c,
                    0x26d3, 0x36f2, 0x0691, 0x16b0, 0x6657, 0x7676, 0x4615, 0x5634,
                    0xd94c, 0xc96d, 0xf90e, 0xe92f, 0x99c8, 0x89e9, 0xb98a, 0xa9ab,
                    0x5844, 0x4865, 0x7806, 0x6827, 0x18c0, 0x08e1, 0x3882, 0x28a3,
                    0xcb7d, 0xdb5c, 0xeb3f, 0xfb1e, 0x8bf9, 0x9bd8, 0xabbb, 0xbb9a,
                    0x4a75, 0x5a54, 0x6a37, 0x7a16, 0x0af1, 0x1ad0, 0x2ab3, 0x3a92,
                    0xfd2e, 0xed0f, 0xdd6c, 0xcd4d, 0xbdaa, 0xad8b, 0x9de8, 0x8dc9,
                    0x7c26, 0x6c07, 0x5c64, 0x4c45, 0x3ca2, 0x2c83, 0x1ce0, 0x0cc1,
                    0xef1f, 0xff3e, 0xcf5d, 0xdf7c, 0xaf9b, 0xbfba, 0x8fd9, 0x9ff8,
                    0x6e17, 0x7e36, 0x4e55, 0x5e74, 0x2e93, 0x3eb2, 0x0ed1, 0x1ef0,
    };

    /**
     * Use as a documentation and safety guard in specializations that are meant to be activated
     * only in the uncached case. Those Specializations exist only to allow to generate an uncached
     * variant of the node, but should never be activated in a regular cached case.
     *
     * Note that such specializations can take VirtualFrame and should forward it to other uncached
     * (and cached) nodes. The uncached nodes may be executed during the uncached execution of the
     * bytecode root node where it is allowed to pass the VirtualFrame around without materializing
     * it.
     */
    public static void assertUncached() {
        if (!TruffleOptions.AOT) {
            // We cannot assert that it's never part of compilation, because then it would fail
            // during native-image build since it cannot prove that this Specialization is never
            // activated at runtime and includes this in runtime compiled methods.
            CompilerAsserts.neverPartOfCompilation();
        }
        CompilerDirectives.transferToInterpreter();
    }

    public static boolean isBitSet(int state, int position) {
        return ((state >>> position) & 0x1) != 0;
    }

    public static String formatPointer(Object pointer) {
        CompilerAsserts.neverPartOfCompilation();
        InteropLibrary lib = InteropLibrary.getUncached(pointer);
        if (lib.isPointer(pointer)) {
            try {
                return String.format("%s#0x%016x", pointer.getClass().getSimpleName(), lib.asPointer(pointer));
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
        return String.valueOf(pointer);
    }

    public static long coerceToLong(Object allocated, InteropLibrary lib) {
        if (allocated instanceof Long) {
            return (long) allocated;
        } else {
            if (!lib.isPointer(allocated)) {
                lib.toNative(allocated);
            }
            try {
                return lib.asPointer(allocated);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    public static InteropLibrary getUncachedInterop(InteropLibrary existing, Object obj) {
        // TODO: have a simple LRU cache of "uncached" pointer InteropLibrary in context?
        // "accepts" should be fast and saves us the concurrent hash map lookup in getUncached
        return existing != null && existing.accepts(obj) ? existing : InteropLibrary.getUncached(obj);
    }

    /**
     * Node factory that creates new instances of given node by cloning a prototype node instance
     * passed in the constructor. We use cloning as opposed to a producer lambda, because this
     * avoids the need for two arguments (node class and the producer factory).
     */
    public static final class PrototypeNodeFactory<T extends Node> implements NodeFactory<T> {
        private final T node;

        public PrototypeNodeFactory(T node) {
            this.node = node;
        }

        @Override
        public T createNode(Object... arguments) {
            return NodeUtil.cloneNode(node);
        }

        @Override
        public Class<T> getNodeClass() {
            return determineNodeClass(node);
        }

        @SuppressWarnings("unchecked")
        private static <T> Class<T> determineNodeClass(T node) {
            CompilerAsserts.neverPartOfCompilation();
            Class<T> nodeClass = (Class<T>) node.getClass();
            GeneratedBy genBy = nodeClass.getAnnotation(GeneratedBy.class);
            if (genBy != null) {
                nodeClass = (Class<T>) genBy.value();
                assert nodeClass.isAssignableFrom(node.getClass());
            }
            return nodeClass;
        }

        @Override
        public List<List<Class<?>>> getNodeSignatures() {
            throw new IllegalAccessError();
        }

        @Override
        public List<Class<? extends Node>> getExecutionSignature() {
            throw new IllegalAccessError();
        }
    }
}
