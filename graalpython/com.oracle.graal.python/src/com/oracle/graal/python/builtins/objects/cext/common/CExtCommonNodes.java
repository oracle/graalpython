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
package com.oracle.graal.python.builtins.objects.cext.common;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OverflowError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.LookupError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.SystemError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.UnicodeDecodeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.UnicodeEncodeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.UnicodeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.CodecsModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.BytesBuiltins;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeVoidPtr;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiGuards;
import com.oracle.graal.python.builtins.objects.cext.capi.CArrayWrappers.CByteArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.DynamicObjectNativeWrapper.PrimitiveNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PySequenceArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapperLibrary;
import com.oracle.graal.python.builtins.objects.common.IndexNodes.NormalizeIndexNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.lib.PyFloatAsDoubleNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode.LookupAndCallUnaryDynamicNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaBooleanNode;
import com.oracle.graal.python.nodes.util.CastToJavaLongLossyNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.exception.PythonExitException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;

public abstract class CExtCommonNodes {
    private static final int SIGABRT_EXIT_CODE = 134;

    @TruffleBoundary
    public static void fatalError(Node location, PythonContext context, String prefix, String msg, int status) {
        PrintWriter stderr = new PrintWriter(context.getStandardErr());
        stderr.print("Fatal Python error: ");
        if (prefix != null) {
            stderr.print(prefix);
            stderr.print(": ");
        }
        if (msg != null) {
            stderr.print(msg);
        } else {
            stderr.print("<message not set>");
        }
        stderr.println();
        stderr.flush();

        if (status < 0) {
            // In CPython, this will use 'abort()' which sets a special exit code.
            throw new PythonExitException(location, SIGABRT_EXIT_CODE);
        }
        throw new PythonExitException(location, status);
    }

    @GenerateUncached
    public abstract static class ImportCExtSymbolNode extends PNodeWithContext {

        public abstract Object execute(CExtContext nativeContext, NativeCExtSymbol symbol);

        @Specialization(guards = "cachedSymbol == symbol", limit = "1", assumptions = "singleContextAssumption()")
        static Object doSymbolCached(@SuppressWarnings("unused") CExtContext nativeContext, @SuppressWarnings("unused") NativeCExtSymbol symbol,
                        @Cached("symbol") @SuppressWarnings("unused") NativeCExtSymbol cachedSymbol,
                        @Cached("importCAPISymbolUncached(nativeContext, symbol)") Object llvmSymbol) {
            return llvmSymbol;
        }

        // n.b. if 'singleContextAssumption' is valid, we may also cache the native context
        @Specialization(guards = "nativeContext == cachedNativeContext", limit = "1", //
                        assumptions = "singleContextAssumption()", //
                        replaces = "doSymbolCached")
        Object doWithSymbolCacheSingleContext(@SuppressWarnings("unused") CExtContext nativeContext, NativeCExtSymbol symbol,
                        @Cached("nativeContext") CExtContext cachedNativeContext,
                        @Cached("nativeContext.getSymbolCache()") DynamicObject cachedSymbolCache,
                        @CachedLibrary("cachedSymbolCache") DynamicObjectLibrary dynamicObjectLib) {
            return doWithSymbolCache(cachedNativeContext, symbol, cachedSymbolCache, dynamicObjectLib);
        }

        @Specialization(replaces = {"doSymbolCached", "doWithSymbolCacheSingleContext"}, limit = "1")
        Object doWithSymbolCache(CExtContext nativeContext, NativeCExtSymbol symbol,
                        @Bind("nativeContext.getSymbolCache()") DynamicObject symbolCache,
                        @CachedLibrary("symbolCache") DynamicObjectLibrary dynamicObjectLib) {
            Object nativeSymbol = dynamicObjectLib.getOrDefault(symbolCache, symbol, PNone.NO_VALUE);
            if (nativeSymbol == PNone.NO_VALUE) {
                nativeSymbol = importCAPISymbolUncached(nativeContext, symbol, symbolCache, dynamicObjectLib);
            }
            return nativeSymbol;
        }

        protected Object importCAPISymbolUncached(CExtContext nativeContext, NativeCExtSymbol symbol) {
            CompilerAsserts.neverPartOfCompilation();
            return importCAPISymbolUncached(nativeContext, symbol, nativeContext.getSymbolCache(), DynamicObjectLibrary.getUncached());
        }

        @TruffleBoundary
        protected Object importCAPISymbolUncached(CExtContext nativeContext, NativeCExtSymbol symbol, DynamicObject symbolCache, DynamicObjectLibrary dynamicObjectLib) {
            Object llvmLibrary = nativeContext.getLLVMLibrary();
            String name = symbol.getName();
            try {
                Object nativeSymbol = InteropLibrary.getUncached().readMember(llvmLibrary, name);
                dynamicObjectLib.put(symbolCache, symbol, nativeSymbol);
                return nativeSymbol;
            } catch (UnknownIdentifierException e) {
                throw PRaiseNode.raiseUncached(this, PythonBuiltinClassType.SystemError, ErrorMessages.INVALID_CAPI_FUNC, name);
            } catch (UnsupportedMessageException e) {
                throw PRaiseNode.raiseUncached(this, PythonBuiltinClassType.SystemError, ErrorMessages.CORRUPTED_CAPI_LIB_OBJ, llvmLibrary);
            }
        }
    }

    @GenerateUncached
    public abstract static class PCallCExtFunction extends PNodeWithContext {

        public final Object call(CExtContext nativeContext, NativeCExtSymbol symbol, Object... args) {
            return execute(nativeContext, symbol, args);
        }

        public abstract Object execute(CExtContext nativeContext, NativeCExtSymbol symbol, Object[] args);

        @Specialization
        static Object doIt(CExtContext nativeContext, NativeCExtSymbol symbol, Object[] args,
                        @CachedLibrary(limit = "1") InteropLibrary interopLibrary,
                        @Cached ImportCExtSymbolNode importCExtSymbolNode,
                        @Cached PRaiseNode raiseNode) {
            try {
                return interopLibrary.execute(importCExtSymbolNode.execute(nativeContext, symbol), args);
            } catch (UnsupportedTypeException | ArityException e) {
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, e);
            } catch (UnsupportedMessageException e) {
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, ErrorMessages.CAPI_SYM_NOT_CALLABLE, symbol.getName());
            }
        }
    }

    @GenerateUncached
    public abstract static class EncodeNativeStringNode extends PNodeWithContext {

        public abstract byte[] execute(Charset charset, Object unicodeObject, String errors);

        @Specialization
        static byte[] doJavaString(Charset charset, String unicodeObject, String errors,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            try {
                CodingErrorAction action = BytesBuiltins.toCodingErrorAction(errors, raiseNode);
                return BytesBuiltins.doEncode(charset, unicodeObject, action);
            } catch (CharacterCodingException e) {
                throw raiseNode.raise(UnicodeEncodeError, "%m", e);
            }
        }

        @Specialization
        static byte[] doGeneric(Charset charset, Object unicodeObject, String errors,
                        @Cached CastToJavaStringNode castToJavaStringNode,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {

            try {
                String s = castToJavaStringNode.execute(unicodeObject);
                return doJavaString(charset, s, errors, raiseNode);
            } catch (CannotCastException e) {
                throw raiseNode.raise(TypeError, ErrorMessages.MUST_BE_S_NOT_P, "argument", "string", unicodeObject);
            }
        }
    }

    public abstract static class Charsets {
        private static final int NATIVE_ORDER = 0;
        private static Charset UTF32;
        private static Charset UTF32LE;
        private static Charset UTF32BE;

        @TruffleBoundary
        public static Charset getUTF32Charset(int byteorder) {
            String utf32Name = getUTF32Name(byteorder);
            if (byteorder == NATIVE_ORDER) {
                if (UTF32 == null) {
                    UTF32 = Charset.forName(utf32Name);
                }
                return UTF32;
            } else if (byteorder < NATIVE_ORDER) {
                if (UTF32LE == null) {
                    UTF32LE = Charset.forName(utf32Name);
                }
                return UTF32LE;
            }
            if (UTF32BE == null) {
                UTF32BE = Charset.forName(utf32Name);
            }
            return UTF32BE;
        }

        public static String getUTF32Name(int byteorder) {
            String csName;
            if (byteorder == 0) {
                csName = "UTF-32";
            } else if (byteorder < 0) {
                csName = "UTF-32LE";
            } else {
                csName = "UTF-32BE";
            }
            return csName;
        }
    }

    @GenerateUncached
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(PythonOptions.class)
    public abstract static class UnicodeFromWcharNode extends PNodeWithContext {

        public abstract String execute(Object arr, Object elementSize);

        @Specialization(guards = "elementSize == cachedElementSize", limit = "getVariableArgumentInlineCacheLimit()")
        static String doBytes(Object arr, @SuppressWarnings("unused") long elementSize,
                        @Cached(value = "elementSize", allowUncached = true) long cachedElementSize,
                        @CachedLibrary("arr") InteropLibrary lib,
                        @CachedLibrary(limit = "1") InteropLibrary elemLib,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            try {
                ByteBuffer bytes;
                if (cachedElementSize == 1L || cachedElementSize == 2L || cachedElementSize == 4L) {
                    if (!lib.hasArrayElements(arr)) {
                        throw raiseNode.raise(SystemError, ErrorMessages.PROVIDED_OBJ_NOT_ARRAY, cachedElementSize);
                    }
                    long arraySize = lib.getArraySize(arr);
                    bytes = readWithSize(lib, elemLib, arr, PInt.intValueExact(arraySize), (int) cachedElementSize);
                    bytes.flip();
                } else {
                    throw raiseNode.raise(ValueError, ErrorMessages.UNSUPPORTED_SIZE_WAS, "wchar_t", cachedElementSize);
                }
                return decode(bytes);
            } catch (OverflowException e) {
                throw raiseNode.raise(ValueError, ErrorMessages.ARRAY_SIZE_TOO_LARGE);
            } catch (CharacterCodingException e) {
                throw raiseNode.raise(UnicodeError, "%m", e);
            } catch (IllegalArgumentException e) {
                throw raiseNode.raise(LookupError, "%m", e);
            } catch (InteropException e) {
                throw raiseNode.raise(TypeError, "%m", e);
            } catch (IllegalElementTypeException e) {
                throw raiseNode.raise(UnicodeDecodeError, ErrorMessages.INVALID_INPUT_ELEM_TYPE, e.elem);
            }
        }

        @Specialization(limit = "getVariableArgumentInlineCacheLimit()")
        static String doBytes(Object arr, Object elementSizeObj,
                        @Cached CastToJavaLongLossyNode castToJavaLongNode,
                        @CachedLibrary("arr") InteropLibrary lib,
                        @CachedLibrary(limit = "1") InteropLibrary elemLib,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            try {
                long es = castToJavaLongNode.execute(elementSizeObj);
                return doBytes(arr, es, es, lib, elemLib, raiseNode);
            } catch (CannotCastException e) {
                throw raiseNode.raise(ValueError, ErrorMessages.INVALID_PARAMS);
            }
        }

        @TruffleBoundary
        private static String decode(ByteBuffer bytes) throws CharacterCodingException {
            return Charsets.getUTF32Charset(0).newDecoder().decode(bytes).toString();
        }

        private static ByteBuffer readWithSize(InteropLibrary arrLib, InteropLibrary elemLib, Object o, int size, int elementSize)
                        throws UnsupportedMessageException, InvalidArrayIndexException, IllegalElementTypeException {
            ByteBuffer buf = allocate(size * Integer.BYTES);
            for (int i = 0; i < size; i += elementSize) {
                putInt(buf, readElement(arrLib, elemLib, o, i, elementSize));
            }
            return buf;
        }

        @ExplodeLoop(kind = LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN)
        private static int readElement(InteropLibrary arrLib, InteropLibrary elemLib, Object arr, int i, int elementSize)
                        throws InvalidArrayIndexException, UnsupportedMessageException, IllegalElementTypeException {
            byte[] barr = new byte[4];
            CompilerAsserts.partialEvaluationConstant(elementSize);
            for (int j = 0; j < elementSize; j++) {
                Object elem = arrLib.readArrayElement(arr, i + j);
                // The array object could be one of our wrappers (e.g. 'PySequenceArrayWrapper').
                // Since the Interop library does not allow to specify how many bytes we want to
                // read when we do readArrayElement, our wrappers always return long. So, we check
                // for 'long' here and cast down to 'byte'.
                if (elemLib.fitsInLong(elem)) {
                    barr[j] = (byte) elemLib.asLong(elem);
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new IllegalElementTypeException(elem);
                }
            }
            return toInt(barr);
        }

        @TruffleBoundary(allowInlining = true)
        private static int toInt(byte[] barr) {
            return ByteBuffer.wrap(barr).order(ByteOrder.LITTLE_ENDIAN).getInt();
        }

        @TruffleBoundary(allowInlining = true)
        private static ByteBuffer allocate(int cap) {
            return ByteBuffer.allocate(cap);
        }

        @TruffleBoundary(allowInlining = true)
        private static void putInt(ByteBuffer buf, int element) {
            buf.putInt(element);
        }

        private static final class IllegalElementTypeException extends Exception {
            private static final long serialVersionUID = 0L;
            private final Object elem;

            IllegalElementTypeException(Object elem) {
                this.elem = elem;
            }
        }
    }

    @GenerateUncached
    @ImportStatic({PGuards.class, CApiGuards.class})
    public abstract static class ConvertPIntToPrimitiveNode extends Node {

        public abstract Object execute(Object o, int signed, int targetTypeSize, boolean exact);

        public final Object execute(Object o, int signed, int targetTypeSize) {
            return execute(o, signed, targetTypeSize, true);
        }

        public final long executeLong(Object o, int signed, int targetTypeSize, boolean exact) throws UnexpectedResultException {
            return PGuards.expectLong(execute(o, signed, targetTypeSize, exact));
        }

        public final int executeInt(Object o, int signed, int targetTypeSize, boolean exact) throws UnexpectedResultException {
            return PGuards.expectInteger(execute(o, signed, targetTypeSize, exact));
        }

        public final long executeLong(Object o, int signed, int targetTypeSize) throws UnexpectedResultException {
            return PGuards.expectLong(execute(o, signed, targetTypeSize, true));
        }

        public final int executeInt(Object o, int signed, int targetTypeSize) throws UnexpectedResultException {
            return PGuards.expectInteger(execute(o, signed, targetTypeSize, true));
        }

        @Specialization(guards = {"targetTypeSize == 4", "signed != 0", "fitsInInt32(nativeWrapper)"})
        @SuppressWarnings("unused")
        static int doWrapperToInt32(PrimitiveNativeWrapper nativeWrapper, int signed, int targetTypeSize, boolean exact) {
            return nativeWrapper.getInt();
        }

        @Specialization(guards = {"targetTypeSize == 4", "signed == 0", "fitsInUInt32(nativeWrapper)"})
        @SuppressWarnings("unused")
        static int doWrapperToUInt32Pos(PrimitiveNativeWrapper nativeWrapper, int signed, int targetTypeSize, boolean exact) {
            return nativeWrapper.getInt();
        }

        @Specialization(guards = {"targetTypeSize == 8", "signed != 0", "fitsInInt64(nativeWrapper)"})
        @SuppressWarnings("unused")
        static long doWrapperToInt64(PrimitiveNativeWrapper nativeWrapper, int signed, int targetTypeSize, boolean exact) {
            return nativeWrapper.getLong();
        }

        @Specialization(guards = {"targetTypeSize == 8", "signed == 0", "fitsInUInt64(nativeWrapper)"})
        @SuppressWarnings("unused")
        static long doWrapperToUInt64Pos(PrimitiveNativeWrapper nativeWrapper, int signed, int targetTypeSize, boolean exact) {
            return nativeWrapper.getLong();
        }

        @Specialization
        @SuppressWarnings("unused")
        static Object doWrapperGeneric(PrimitiveNativeWrapper nativeWrapper, int signed, int targetTypeSize, boolean exact,
                        @Shared("asNativePrimitiveNode") @Cached AsNativePrimitiveNode asNativePrimitiveNode) {
            return asNativePrimitiveNode.execute(nativeWrapper.getLong(), signed, targetTypeSize, exact);
        }

        @Specialization
        static Object doInt(int value, int signed, int targetTypeSize, boolean exact,
                        @Shared("asNativePrimitiveNode") @Cached AsNativePrimitiveNode asNativePrimitiveNode) {
            return asNativePrimitiveNode.execute(value, signed, targetTypeSize, exact);
        }

        @Specialization
        static Object doLong(long value, int signed, int targetTypeSize, boolean exact,
                        @Shared("asNativePrimitiveNode") @Cached AsNativePrimitiveNode asNativePrimitiveNode) {
            return asNativePrimitiveNode.execute(value, signed, targetTypeSize, exact);
        }

        @Specialization(guards = {"!isPrimitiveNativeWrapper(obj)"}, replaces = {"doInt", "doLong"})
        static Object doOther(Object obj, int signed, int targetTypeSize, boolean exact,
                        @Cached AsNativePrimitiveNode asNativePrimitiveNode) {
            return asNativePrimitiveNode.execute(obj, signed, targetTypeSize, exact);
        }

        static boolean fitsInInt32(PrimitiveNativeWrapper nativeWrapper) {
            return nativeWrapper.isBool() || nativeWrapper.isByte() || nativeWrapper.isInt();
        }

        static boolean fitsInInt64(PrimitiveNativeWrapper nativeWrapper) {
            return nativeWrapper.isIntLike() || nativeWrapper.isBool();
        }

        static boolean fitsInUInt32(PrimitiveNativeWrapper nativeWrapper) {
            return (nativeWrapper.isBool() || nativeWrapper.isByte() || nativeWrapper.isInt()) && nativeWrapper.getInt() >= 0;
        }

        static boolean fitsInUInt64(PrimitiveNativeWrapper nativeWrapper) {
            return (nativeWrapper.isIntLike() || nativeWrapper.isBool()) && nativeWrapper.getLong() >= 0;
        }
    }

    /**
     * Converts a Python object to a Java double value (which is compatible to a C double).<br/>
     * This node is, for example, used to implement {@code PyFloat_AsDouble} or similar C API
     * functions and does coercion and may raise a Python exception if coercion fails.<br/>
     * Please note: In most cases, it is sufficient to use {@link PyFloatAsDoubleNode} but you might
     * want to use this node if the argument can be an object of type {@link PrimitiveNativeWrapper}
     * .
     */
    @GenerateUncached
    @ImportStatic({SpecialMethodNames.class, CApiGuards.class})
    public abstract static class AsNativeDoubleNode extends CExtToNativeNode {
        public abstract double executeDouble(CExtContext cExtContext, Object arg);

        public final double executeDouble(Object arg) {
            return executeDouble(CExtContext.LAZY_CONTEXT, arg);
        }

        @Specialization(guards = "!isNativeWrapper(value)")
        static double runGeneric(@SuppressWarnings("unused") CExtContext cExtContext, Object value,
                        @Cached PyFloatAsDoubleNode asDoubleNode) {
            // IMPORTANT: this should implement the behavior like 'PyFloat_AsDouble'. So, if it
            // is a float object, use the value and do *NOT* call '__float__'.
            return asDoubleNode.execute(null, value);
        }

        @Specialization(guards = "!object.isDouble()")
        static double doLongNativeWrapper(@SuppressWarnings("unused") CExtContext cExtContext, PrimitiveNativeWrapper object) {
            return object.getLong();
        }

        @Specialization(guards = "object.isDouble()")
        static double doDoubleNativeWrapper(@SuppressWarnings("unused") CExtContext cExtContext, PrimitiveNativeWrapper object) {
            return object.getDouble();
        }
    }

    public abstract static class CheckFunctionResultNode extends PNodeWithContext {
        public abstract Object execute(PythonContext context, String name, Object result);
    }

    @GenerateUncached
    public abstract static class GetByteArrayNode extends Node {

        public abstract byte[] execute(Object obj, long n) throws InteropException, OverflowException;

        @Specialization(limit = "1")
        static byte[] doCArrayWrapper(CByteArrayWrapper obj, long n,
                        @CachedLibrary("obj") PythonNativeWrapperLibrary lib) {
            return subRangeIfNeeded(obj.getByteArray(lib), n);
        }

        @Specialization(limit = "1")
        static byte[] doSequenceArrayWrapper(PySequenceArrayWrapper obj, long n,
                        @CachedLibrary("obj") PythonNativeWrapperLibrary lib,
                        @Cached SequenceStorageNodes.ToByteArrayNode toByteArrayNode) {
            Object delegate = lib.getDelegate(obj);
            if (delegate instanceof PBytesLike) {
                byte[] bytes = toByteArrayNode.execute(((PBytesLike) delegate).getSequenceStorage());
                return subRangeIfNeeded(bytes, n);
            }
            throw CompilerDirectives.shouldNotReachHere();
        }

        @Specialization(limit = "5")
        static byte[] doForeign(Object obj, long n,
                        @CachedLibrary("obj") InteropLibrary interopLib,
                        @CachedLibrary(limit = "1") InteropLibrary elementLib) throws InteropException, OverflowException {
            long size = n < 0 ? interopLib.getArraySize(obj) : n;
            byte[] bytes = new byte[PInt.intValueExact(size)];
            for (int i = 0; i < bytes.length; i++) {
                Object elem = interopLib.readArrayElement(obj, i);
                if (elementLib.fitsInByte(elem)) {
                    bytes[i] = elementLib.asByte(elem);
                }
            }
            return bytes;
        }

        private static byte[] subRangeIfNeeded(byte[] bytes, long n) {
            if (bytes.length > n && n >= 0) {
                // cast to int is guaranteed because of 'bytes.length > n'
                return PythonUtils.arrayCopyOf(bytes, (int) n);
            } else {
                return bytes;
            }
        }
    }

    /**
     * Converts a Python object (i.e. {@code PyObject*}) to a C integer value ({@code int} or
     * {@code long}).<br/>
     * This node is used to implement {@code PyLong_AsLong} or similar C API functions and does
     * coercion and may raise a Python exception if coercion fails. <br/>
     * Allowed {@code targetTypeSize} values are {@code 4} and {@code 8}. <br/>
     * If {@code exact} is {@code false}, then casting can be lossy without raising an error.
     */
    @GenerateUncached
    @ImportStatic(PGuards.class)
    public abstract static class AsNativePrimitiveNode extends Node {

        public final int toInt32(Object value, boolean exact) {
            return (int) execute(value, 1, 4, exact);
        }

        public final int toUInt32(Object value, boolean exact) {
            return (int) execute(value, 0, 4, exact);
        }

        public final long toInt64(Object value, boolean exact) {
            return (long) execute(value, 1, 8, exact);
        }

        public final long toUInt64(Object value, boolean exact) {
            return (long) execute(value, 0, 8, exact);
        }

        public abstract Object execute(byte value, int signed, int targetTypeSize, boolean exact);

        public abstract Object execute(int value, int signed, int targetTypeSize, boolean exact);

        public abstract Object execute(long value, int signed, int targetTypeSize, boolean exact);

        public abstract Object execute(Object value, int signed, int targetTypeSize, boolean exact);

        @Specialization(guards = {"targetTypeSize == 4", "signed != 0"})
        @SuppressWarnings("unused")
        static int doIntToInt32(int value, int signed, int targetTypeSize, boolean exact) {
            return value;
        }

        @Specialization(guards = {"targetTypeSize == 4", "signed == 0", "value >= 0"})
        @SuppressWarnings("unused")
        static int doIntToUInt32Pos(int value, int signed, int targetTypeSize, boolean exact) {
            return value;
        }

        @Specialization(guards = {"targetTypeSize == 4", "signed == 0"}, replaces = "doIntToUInt32Pos")
        @SuppressWarnings("unused")
        static int doIntToUInt32(int value, int signed, int targetTypeSize, boolean exact,
                        @Shared("raiseNativeNode") @Cached PRaiseNode raiseNativeNode) {
            if (exact && value < 0) {
                throw raiseNegativeValue(raiseNativeNode);
            }
            return value;
        }

        @Specialization(guards = {"targetTypeSize == 8", "signed != 0"})
        @SuppressWarnings("unused")
        static long doIntToInt64(int obj, int signed, int targetTypeSize, boolean exact) {
            return obj;
        }

        @Specialization(guards = {"targetTypeSize == 8", "signed == 0", "value >= 0"})
        @SuppressWarnings("unused")
        static long doIntToUInt64Pos(int value, int signed, int targetTypeSize, boolean exact) {
            return value;
        }

        @Specialization(guards = {"targetTypeSize == 8", "signed == 0"}, replaces = "doIntToUInt64Pos")
        @SuppressWarnings("unused")
        static long doIntToUInt64(int value, int signed, int targetTypeSize, boolean exact,
                        @Shared("raiseNativeNode") @Cached PRaiseNode raiseNativeNode) {
            if (exact && value < 0) {
                throw raiseNegativeValue(raiseNativeNode);
            }
            return value;
        }

        @Specialization(guards = {"targetTypeSize == 8", "signed != 0"})
        @SuppressWarnings("unused")
        static long doLongToInt64(long value, int signed, int targetTypeSize, boolean exact) {
            return value;
        }

        @Specialization(guards = {"targetTypeSize == 8", "signed == 0", "value >= 0"})
        @SuppressWarnings("unused")
        static long doLongToUInt64Pos(long value, int signed, int targetTypeSize, boolean exact) {
            return value;
        }

        @Specialization(guards = {"targetTypeSize == 8", "signed == 0"}, replaces = "doLongToUInt64Pos")
        @SuppressWarnings("unused")
        static long doLongToUInt64(long value, int signed, int targetTypeSize, boolean exact,
                        @Shared("raiseNativeNode") @Cached PRaiseNode raiseNativeNode) {
            if (exact && value < 0) {
                throw raiseNegativeValue(raiseNativeNode);
            }
            return value;
        }

        @Specialization(guards = {"exact", "targetTypeSize == 4", "signed != 0"})
        @SuppressWarnings("unused")
        static int doLongToInt32Exact(long obj, int signed, int targetTypeSize, boolean exact,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            try {
                return PInt.intValueExact(obj);
            } catch (OverflowException e) {
                throw raiseNode.raise(PythonErrorType.OverflowError, ErrorMessages.PYTHON_INT_TOO_LARGE_TO_CONV_TO_C_TYPE, targetTypeSize);
            }
        }

        @Specialization(guards = {"exact", "targetTypeSize == 4", "signed == 0", "obj >= 0"})
        @SuppressWarnings("unused")
        static int doLongToUInt32PosExact(long obj, int signed, int targetTypeSize, boolean exact,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            try {
                return PInt.intValueExact(obj);
            } catch (OverflowException e) {
                throw raiseNode.raise(PythonErrorType.OverflowError, ErrorMessages.PYTHON_INT_TOO_LARGE_TO_CONV_TO_C_TYPE, targetTypeSize);
            }
        }

        @Specialization(guards = {"exact", "targetTypeSize == 4", "signed == 0"}, replaces = "doLongToUInt32PosExact")
        @SuppressWarnings("unused")
        static int doLongToUInt32Exact(long obj, int signed, int targetTypeSize, boolean exact,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            if (obj < 0) {
                throw raiseNegativeValue(raiseNode);
            }
            try {
                return PInt.intValueExact(obj);
            } catch (OverflowException e) {
                throw raiseNode.raise(PythonErrorType.OverflowError, ErrorMessages.PYTHON_INT_TOO_LARGE_TO_CONV_TO_C_TYPE, targetTypeSize);
            }
        }

        @Specialization(guards = {"!exact", "targetTypeSize == 4"})
        @SuppressWarnings("unused")
        static int doLongToInt32Lossy(long obj, int signed, int targetTypeSize, boolean exact) {
            return (int) obj;
        }

        @Specialization(guards = "targetTypeSize == 8")
        @SuppressWarnings("unused")
        static Object doVoidPtrToI64(PythonNativeVoidPtr obj, int signed, int targetTypeSize, boolean exact) {
            return obj;
        }

        @Specialization(guards = {"exact", "targetTypeSize == 4"})
        @SuppressWarnings("unused")
        @TruffleBoundary
        static int doPIntTo32Bit(PInt obj, int signed, int targetTypeSize, boolean exact,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            try {
                if (signed != 0) {
                    return obj.intValueExact();
                } else if (obj.bitLength() <= 32) {
                    if (obj.isNegative()) {
                        throw raiseNegativeValue(raiseNode);
                    }
                    return obj.intValue();
                }
            } catch (OverflowException e) {
                // fall through
            }
            throw raiseNode.raise(PythonErrorType.OverflowError, ErrorMessages.PYTHON_INT_TOO_LARGE_TO_CONV_TO_C_TYPE, targetTypeSize);
        }

        @Specialization(guards = {"exact", "targetTypeSize == 8"})
        @SuppressWarnings("unused")
        @TruffleBoundary
        static long doPIntTo64Bit(PInt obj, int signed, int targetTypeSize, boolean exact,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            try {
                if (signed != 0) {
                    return obj.longValueExact();
                } else if (obj.bitLength() <= 64) {
                    if (obj.isNegative()) {
                        throw raiseNegativeValue(raiseNode);
                    }
                    return obj.longValue();
                }
            } catch (OverflowException e) {
                // fall through
            }
            throw raiseNode.raise(PythonErrorType.OverflowError, ErrorMessages.PYTHON_INT_TOO_LARGE_TO_CONV_TO_C_TYPE, targetTypeSize);
        }

        @Specialization(guards = {"!exact", "targetTypeSize == 4"})
        @SuppressWarnings("unused")
        static int doPIntToInt32Lossy(PInt obj, int signed, int targetTypeSize, boolean exact) {
            return obj.intValue();
        }

        @Specialization(guards = {"!exact", "targetTypeSize == 8"})
        @SuppressWarnings("unused")
        static long doPIntToInt64Lossy(PInt obj, int signed, int targetTypeSize, boolean exact) {
            return obj.longValue();
        }

        @Specialization(guards = {"targetTypeSize == 4 || targetTypeSize == 8"}, //
                        replaces = {"doIntToInt32", "doIntToUInt32Pos", "doIntToUInt32", //
                                        "doIntToInt64", "doIntToUInt64Pos", "doIntToUInt64", //
                                        "doLongToInt64", "doLongToUInt64Pos", "doLongToUInt64", //
                                        "doLongToInt32Exact", "doLongToUInt32PosExact", "doLongToUInt32Exact", "doLongToInt32Lossy", //
                                        "doVoidPtrToI64", //
                                        "doPIntTo32Bit", "doPIntTo64Bit", "doPIntToInt32Lossy", "doPIntToInt64Lossy"})
        static Object doGeneric(Object obj, int signed, int targetTypeSize, boolean exact,
                        @Cached LookupAndCallUnaryDynamicNode callIndexNode,
                        @Cached LookupAndCallUnaryDynamicNode callIntNode,
                        @Cached AsNativePrimitiveNode recursive,
                        @Exclusive @Cached BranchProfile noIntProfile,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {

            Object result = callIndexNode.executeObject(obj, SpecialMethodNames.__INDEX__);
            if (result == PNone.NO_VALUE) {
                result = callIntNode.executeObject(obj, SpecialMethodNames.__INT__);
                if (result == PNone.NO_VALUE) {
                    noIntProfile.enter();
                    throw raiseNode.raise(PythonErrorType.TypeError, ErrorMessages.INTEGER_REQUIRED_GOT, result);
                }
            }
            // n.b. this check is important to avoid endless recursions; it will ensure that
            // 'doGeneric' is not triggered in the recursive node
            if (!(isIntegerType(result))) {
                throw raiseNode.raise(PythonErrorType.TypeError, ErrorMessages.INDEX_RETURNED_NON_INT, result);
            }
            return recursive.execute(result, signed, targetTypeSize, exact);
        }

        @Specialization(guards = {"targetTypeSize != 4", "targetTypeSize != 8"})
        @SuppressWarnings("unused")
        static int doUnsupportedTargetSize(Object obj, int signed, int targetTypeSize, boolean exact,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(SystemError, ErrorMessages.UNSUPPORTED_TARGET_SIZE, targetTypeSize);
        }

        static boolean isIntegerType(Object obj) {
            return PGuards.isInteger(obj) || PGuards.isPInt(obj) || obj instanceof PythonNativeVoidPtr;
        }

        private static PException raiseNegativeValue(PRaiseNode raiseNativeNode) {
            throw raiseNativeNode.raise(OverflowError, ErrorMessages.CANNOT_CONVERT_NEGATIVE_VALUE_TO_UNSIGNED_INT);
        }

    }

    /**
     * This node either passes a {@link String} object through or it converts a {@code NULL} pointer
     * to {@link PNone#NONE}. This is a very special use case and certainly only good for reading a
     * member of type
     * {@link com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef#HPY_MEMBER_STRING} or
     * {@link com.oracle.graal.python.builtins.objects.cext.capi.CApiMemberAccessNodes#T_STRING}.
     */
    @GenerateUncached
    public abstract static class StringAsPythonStringNode extends CExtToJavaNode {

        @Specialization
        static String doString(@SuppressWarnings("unused") CExtContext hpyContext, String value) {
            return value;
        }

        @Specialization(replaces = "doString", limit = "3")
        static Object doGeneric(@SuppressWarnings("unused") CExtContext hpyContext, Object value,
                        @CachedLibrary("value") InteropLibrary interopLib) {
            if (interopLib.isNull(value)) {
                return PNone.NONE;
            }
            assert value instanceof String;
            return value;
        }
    }

    /**
     * This node converts a C Boolean value to Python Boolean.
     */
    @GenerateUncached
    public abstract static class NativePrimitiveAsPythonBooleanNode extends CExtToJavaNode {

        @Specialization
        static Object doByte(@SuppressWarnings("unused") CExtContext hpyContext, byte b) {
            return b != 0;
        }

        @Specialization
        static Object doShort(@SuppressWarnings("unused") CExtContext hpyContext, short i) {
            return i != 0;
        }

        @Specialization
        static Object doLong(@SuppressWarnings("unused") CExtContext hpyContext, long l) {
            // If the integer is out of byte range, we just to a lossy cast since that's the same
            // sematics as we should just read a single byte.
            return l != 0;
        }

        @Specialization(replaces = {"doByte", "doShort", "doLong"}, limit = "1")
        static Object doGeneric(@SuppressWarnings("unused") CExtContext hpyContext, Object n,
                        @CachedLibrary("n") InteropLibrary lib) {
            if (lib.fitsInLong(n)) {
                try {
                    return lib.asLong(n) != 0;
                } catch (UnsupportedMessageException e) {
                    // fall through
                }
            }
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    /**
     * This node converts a native primitive value to an appropriate Python char value (a
     * single-char Python string).
     */
    @GenerateUncached
    public abstract static class NativePrimitiveAsPythonCharNode extends CExtToJavaNode {

        @Specialization
        static Object doByte(@SuppressWarnings("unused") CExtContext hpyContext, byte b) {
            return PythonUtils.newString(new char[]{(char) b});
        }

        @Specialization
        static Object doShort(@SuppressWarnings("unused") CExtContext hpyContext, short i) {
            return createString((char) i);
        }

        @Specialization
        static Object doLong(@SuppressWarnings("unused") CExtContext hpyContext, long l) {
            // If the integer is out of byte range, we just to a lossy cast since that's the same
            // sematics as we should just read a single byte.
            return createString((char) l);
        }

        @Specialization(replaces = {"doByte", "doShort", "doLong"}, limit = "1")
        static Object doGeneric(@SuppressWarnings("unused") CExtContext hpyContext, Object n,
                        @CachedLibrary("n") InteropLibrary lib) {
            if (lib.fitsInShort(n)) {
                try {
                    return createString((char) lib.asShort(n));
                } catch (UnsupportedMessageException e) {
                    // fall through
                }
            }
            throw CompilerDirectives.shouldNotReachHere();
        }

        private static String createString(char c) {
            return PythonUtils.newString(new char[]{c});
        }
    }

    /**
     * This node converts a native primitive value to an appropriate Python value considering the
     * native value as unsigned. For example, a negative {@code int} value will be converted to a
     * positive {@code long} value.
     */
    @GenerateUncached
    public abstract static class NativeUnsignedPrimitiveAsPythonObjectNode extends CExtToJavaNode {

        @Specialization(guards = "n >= 0")
        static int doUnsignedIntPositive(@SuppressWarnings("unused") CExtContext hpyContext, int n) {
            return n;
        }

        @Specialization(replaces = "doUnsignedIntPositive")
        static long doUnsignedInt(@SuppressWarnings("unused") CExtContext hpyContext, int n) {
            if (n < 0) {
                return n & 0xffffffffL;
            }
            return n;
        }

        @Specialization(guards = "n >= 0")
        static long doUnsignedLongPositive(@SuppressWarnings("unused") CExtContext hpyContext, long n) {
            return n;
        }

        @Specialization(guards = "n < 0")
        static Object doUnsignedLongNegative(@SuppressWarnings("unused") CExtContext hpyContext, long n,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            return factory.createInt(PInt.longToUnsignedBigInteger(n));
        }

        @Specialization(replaces = {"doUnsignedIntPositive", "doUnsignedInt", "doUnsignedLongPositive", "doUnsignedLongNegative"})
        static Object doGeneric(CExtContext hpyContext, Object n,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            if (n instanceof Integer) {
                int i = (int) n;
                if (i >= 0) {
                    return i;
                } else {
                    return doUnsignedInt(hpyContext, i);
                }
            } else if (n instanceof Long) {
                long l = (long) n;
                if (l >= 0) {
                    return l;
                } else {
                    return doUnsignedLongNegative(hpyContext, l, factory);
                }
            }
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    /**
     * Converts a Python character (1-element Python string) into a UTF-8 encoded C {@code char}.
     * According to CPython, we need to encode the whole Python string before we access the first
     * byte (see also: {@code structmember.c:PyMember_SetOne} case {@code T_CHAR}).
     */
    @GenerateUncached
    public abstract static class AsNativeCharNode extends CExtToNativeNode {

        @Specialization
        static byte doGeneric(@SuppressWarnings("unused") CExtContext hpyContext, Object value,
                        @Cached EncodeNativeStringNode encodeNativeStringNode,
                        @Cached PRaiseNode raiseNode) {
            byte[] encoded = encodeNativeStringNode.execute(StandardCharsets.UTF_8, value, CodecsModuleBuiltins.STRICT);
            if (encoded.length != 1) {
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, ErrorMessages.BAD_ARG_TYPE_FOR_BUILTIN_OP);
            }
            return encoded[0];
        }
    }

    /**
     * Converts a Python Boolean into a C Boolean {@code char} (see also:
     * {@code structmember.c:PyMember_SetOne} case {@code T_BOOL}).
     */
    @GenerateUncached
    public abstract static class AsNativeBooleanNode extends CExtToNativeNode {

        @Specialization
        static byte doGeneric(@SuppressWarnings("unused") CExtContext hpyContext, Object value,
                        @Cached CastToJavaBooleanNode castToJavaBooleanNode,
                        @Cached PRaiseNode raiseNode) {
            try {
                return (byte) PInt.intValue(castToJavaBooleanNode.execute(value));
            } catch (CannotCastException e) {
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, ErrorMessages.ATTR_VALUE_MUST_BE_BOOL);
            }
        }
    }

    /**
     * Converts a Python object to a C primitive value with a fixed size and sign.
     *
     * @see AsNativePrimitiveNode
     */
    public abstract static class AsFixedNativePrimitiveNode extends CExtToNativeNode {

        private final int targetTypeSize;
        private final int signed;

        protected AsFixedNativePrimitiveNode(int targetTypeSize, boolean signed) {
            this.targetTypeSize = targetTypeSize;
            this.signed = PInt.intValue(signed);
        }

        // Adding specializations for primitives does not make a lot of sense just to avoid
        // un-/boxing in the interpreter since interop will force un-/boxing anyway.
        @Specialization
        Object doGeneric(@SuppressWarnings("unused") CExtContext hpyContext, Object value,
                        @Cached AsNativePrimitiveNode asNativePrimitiveNode) {
            return asNativePrimitiveNode.execute(value, signed, targetTypeSize, true);
        }
    }

    /**
     * Implements semantics of function {@code typeobject.c: getindex}.
     */
    public static final class GetIndexNode extends Node {

        @Child PyNumberAsSizeNode asSizeNode = PyNumberAsSizeNode.create();
        @Child private PythonObjectLibrary selfLib;
        @Child private NormalizeIndexNode normalizeIndexNode;

        public int execute(Object self, Object indexObj) {
            int index = asSizeNode.executeExact(null, indexObj);
            if (index < 0) {
                // 'selfLib' acts as an implicit profile for 'index < 0'
                if (selfLib == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    selfLib = insert(PythonObjectLibrary.getFactory().createDispatched(1));
                }
                if (normalizeIndexNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    normalizeIndexNode = insert(NormalizeIndexNode.create(false));
                }
                return normalizeIndexNode.execute(index, selfLib.length(self));
            }
            return index;
        }

        public static GetIndexNode create() {
            return new GetIndexNode();
        }
    }
}
