/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.runtime.exception.PythonErrorType.LookupError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.SystemError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.UnicodeDecodeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.UnicodeEncodeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.UnicodeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.bytes.BytesBuiltins;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaLongNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
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
import com.oracle.truffle.api.profiles.BranchProfile;

public abstract class CExtCommonNodes {

    @GenerateUncached
    public abstract static class ImportCExtSymbolNode extends PNodeWithContext {

        public abstract Object execute(CExtContext nativeContext, String name);

        // n.b. if 'singleContextAssumption' is valid, we may also cache the native context
        @Specialization(guards = {"nativeContext == cachedNativeContext", "cachedName == name"}, //
                        limit = "1", //
                        assumptions = "singleContextAssumption()")
        @SuppressWarnings("unused")
        static Object doReceiverCachedIdentity(CExtContext nativeContext, String name,
                        @Cached("nativeContext") CExtContext cachedNativeContext,
                        @Cached("name") String cachedName,
                        @Cached("importCAPISymbolUncached(nativeContext, name)") Object sym) {
            return sym;
        }

        // n.b. if 'singleContextAssumption' is valid, we may also cache the native context
        @Specialization(guards = {"nativeContext == cachedNativeContext", "cachedName.equals(name)"}, //
                        limit = "1", //
                        assumptions = "singleContextAssumption()", //
                        replaces = "doReceiverCachedIdentity")
        @SuppressWarnings("unused")
        static Object doReceiverCached(CExtContext nativeContext, String name,
                        @Cached("nativeContext") CExtContext cachedNativeContext,
                        @Cached("name") String cachedName,
                        @Cached("importCAPISymbolUncached(nativeContext, name)") Object sym) {
            return sym;
        }

        @Specialization(replaces = {"doReceiverCachedIdentity", "doReceiverCached"}, //
                        limit = "1")
        static Object doWithContext(CExtContext nativeContext, String name,
                        @CachedLibrary("nativeContext.getLLVMLibrary()") InteropLibrary interopLib,
                        @Cached PRaiseNode raiseNode) {
            return importCAPISymbol(raiseNode, interopLib, nativeContext.getLLVMLibrary(), name);
        }

        protected static Object importCAPISymbolUncached(CExtContext nativeContext, String name) {
            Object capiLibrary = nativeContext.getLLVMLibrary();
            return importCAPISymbol(PRaiseNode.getUncached(), InteropLibrary.getFactory().getUncached(capiLibrary), capiLibrary, name);
        }

        private static Object importCAPISymbol(PRaiseNode raiseNode, InteropLibrary library, Object capiLibrary, String name) {
            try {
                return library.readMember(capiLibrary, name);
            } catch (UnknownIdentifierException e) {
                throw raiseNode.raise(PythonBuiltinClassType.SystemError, "invalid C API function: %s", name);
            } catch (UnsupportedMessageException e) {
                throw raiseNode.raise(PythonBuiltinClassType.SystemError, "corrupted C API library object: %s", capiLibrary);
            }
        }
    }

    @GenerateUncached
    public abstract static class PCallCExtFunction extends PNodeWithContext {

        public final Object call(CExtContext nativeContext, String name, Object... args) {
            return execute(nativeContext, name, args);
        }

        public abstract Object execute(CExtContext nativeContext, String name, Object[] args);

        @Specialization
        static Object doIt(CExtContext nativeContext, String name, Object[] args,
                        @CachedLibrary(limit = "1") InteropLibrary interopLibrary,
                        @Cached ImportCExtSymbolNode importCExtSymbolNode,
                        @Cached BranchProfile profile,
                        @Cached PRaiseNode raiseNode) {
            try {
                return interopLibrary.execute(importCExtSymbolNode.execute(nativeContext, name), args);
            } catch (UnsupportedTypeException | ArityException e) {
                profile.enter();
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, e);
            } catch (UnsupportedMessageException e) {
                profile.enter();
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, "C API symbol %s is not callable", name);
            }
        }
    }

    @GenerateUncached
    public abstract static class EncodeNativeStringNode extends PNodeWithContext {

        public abstract PBytes execute(Charset charset, Object string, String errors);

        @Specialization
        static PBytes doJavaString(Charset charset, String s, String errors,
                        @Shared("factory") @Cached PythonObjectFactory factory,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            try {
                CodingErrorAction action = BytesBuiltins.toCodingErrorAction(errors, raiseNode);
                return factory.createBytes(doEncode(charset, s, action));
            } catch (CharacterCodingException e) {
                throw raiseNode.raise(UnicodeEncodeError, "%m", e);
            }
        }

        @Specialization(replaces = "doJavaString")
        static PBytes doGeneric(Charset charset, Object stringObj, String errors,
                        @Cached CastToJavaStringNode castToJavaStringNode,
                        @Shared("factory") @Cached PythonObjectFactory factory,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            return doJavaString(charset, castToJavaStringNode.execute(stringObj), errors, factory, raiseNode);
        }

        @TruffleBoundary
        private static byte[] doEncode(Charset charset, String string, CodingErrorAction action) throws CharacterCodingException {
            CharsetEncoder encoder = charset.newEncoder();
            encoder.onMalformedInput(action).onUnmappableCharacter(action);
            CharBuffer buf = CharBuffer.allocate(string.length());
            buf.put(string);
            buf.flip();
            ByteBuffer encoded = encoder.encode(buf);
            byte[] barr = new byte[encoded.remaining()];
            encoded.get(barr);
            return barr;
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
                        throw raiseNode.raise(SystemError, "provided object is not an array", cachedElementSize);
                    }
                    long arraySize = lib.getArraySize(arr);
                    bytes = readWithSize(lib, elemLib, arr, PInt.intValueExact(arraySize), (int) cachedElementSize);
                    bytes.flip();
                } else {
                    throw raiseNode.raise(ValueError, "unsupported 'wchar_t' size; was: %d", cachedElementSize);
                }
                return decode(bytes);
            } catch (ArithmeticException e) {
                throw raiseNode.raise(ValueError, "array size too large");
            } catch (CharacterCodingException e) {
                throw raiseNode.raise(UnicodeError, "%m", e);
            } catch (IllegalArgumentException e) {
                throw raiseNode.raise(LookupError, "%m", e);
            } catch (InteropException e) {
                throw raiseNode.raise(TypeError, "%m", e);
            } catch (IllegalElementTypeException e) {
                throw raiseNode.raise(UnicodeDecodeError, "Invalid input element type '%p'", e.elem);
            }
        }

        @Specialization(limit = "getVariableArgumentInlineCacheLimit()")
        static String doBytes(Object arr, Object elementSizeObj,
                        @Cached CastToJavaLongNode castToJavaLongNode,
                        @CachedLibrary("arr") InteropLibrary lib,
                        @CachedLibrary(limit = "1") InteropLibrary elemLib,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            try {
                long es = castToJavaLongNode.execute(elementSizeObj);
                return doBytes(arr, es, es, lib, elemLib, raiseNode);
            } catch (CannotCastException e) {
                throw raiseNode.raise(ValueError, "invalid parameters");
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
                    CompilerDirectives.transferToInterpreter();
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
}
