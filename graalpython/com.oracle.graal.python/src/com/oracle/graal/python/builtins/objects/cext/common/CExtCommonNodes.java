/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.nodes.StringLiterals.T_STRICT;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.SystemError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.UnicodeEncodeError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.io.PrintWriter;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.BytesBuiltins;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeVoidPtr;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiGuards;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.FromCharPointerNode;
import com.oracle.graal.python.builtins.objects.cext.capi.PrimitiveNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers.CByteArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodesFactory.GetIndexNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodesFactory.ReadUnicodeArrayNodeGen;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.common.IndexNodes.NormalizeIndexNode;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.lib.PyFloatAsDoubleNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodSlotNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
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
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

public abstract class CExtCommonNodes {
    @TruffleBoundary
    public static void fatalError(Node location, PythonContext context, TruffleString prefix, TruffleString msg, int status) {
        fatalError(location, context, prefix != null ? prefix.toJavaStringUncached() : null, msg.toJavaStringUncached(), status);
    }

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
            PosixSupportLibrary posixLib = PosixSupportLibrary.getUncached();
            Object posixSupport = context.getPosixSupport();
            posixLib.abort(posixSupport);
            // abort does not return
        }
        throw new PythonExitException(location, status);
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class ImportCExtSymbolNode extends PNodeWithContext {

        public abstract Object execute(Node inliningTarget, CExtContext nativeContext, NativeCExtSymbol symbol);

        @Specialization(guards = {"isSingleContext()", "cachedSymbol == symbol"}, limit = "1")
        static Object doSymbolCached(@SuppressWarnings("unused") Node inliningTarget, @SuppressWarnings("unused") CExtContext nativeContext, @SuppressWarnings("unused") NativeCExtSymbol symbol,
                        @Cached("symbol") @SuppressWarnings("unused") NativeCExtSymbol cachedSymbol,
                        @Cached("importCAPISymbolUncached(inliningTarget, nativeContext, symbol)") Object llvmSymbol) {
            return llvmSymbol;
        }

        // n.b. if 'singleContextAssumption' is valid, we may also cache the native context
        @Specialization(guards = {"isSingleContext()", "nativeContext == cachedNativeContext"}, limit = "1", //
                        replaces = "doSymbolCached")
        static Object doWithSymbolCacheSingleContext(Node inliningTarget, @SuppressWarnings("unused") CExtContext nativeContext, NativeCExtSymbol symbol,
                        @Cached("nativeContext") CExtContext cachedNativeContext,
                        @Cached("nativeContext.getSymbolCache()") DynamicObject cachedSymbolCache,
                        @CachedLibrary("cachedSymbolCache") DynamicObjectLibrary dynamicObjectLib) {
            return doWithSymbolCache(inliningTarget, cachedNativeContext, symbol, cachedSymbolCache, dynamicObjectLib);
        }

        @Specialization(replaces = {"doSymbolCached", "doWithSymbolCacheSingleContext"}, limit = "1")
        static Object doWithSymbolCache(Node inliningTarget, CExtContext nativeContext, NativeCExtSymbol symbol,
                        @Bind("nativeContext.getSymbolCache()") DynamicObject symbolCache,
                        @CachedLibrary("symbolCache") DynamicObjectLibrary dynamicObjectLib) {
            Object nativeSymbol = dynamicObjectLib.getOrDefault(symbolCache, symbol, PNone.NO_VALUE);
            if (nativeSymbol == PNone.NO_VALUE) {
                nativeSymbol = importCAPISymbolUncached(inliningTarget, nativeContext, symbol, symbolCache, dynamicObjectLib);
            }
            return nativeSymbol;
        }

        protected static Object importCAPISymbolUncached(Node location, CExtContext nativeContext, NativeCExtSymbol symbol) {
            CompilerAsserts.neverPartOfCompilation();
            return importCAPISymbolUncached(location, nativeContext, symbol, nativeContext.getSymbolCache(), DynamicObjectLibrary.getUncached());
        }

        @TruffleBoundary
        protected static Object importCAPISymbolUncached(Node location, CExtContext nativeContext, NativeCExtSymbol symbol, DynamicObject symbolCache, DynamicObjectLibrary dynamicObjectLib) {
            Object llvmLibrary = nativeContext.getLLVMLibrary();
            String name = symbol.getName();
            try {
                Object nativeSymbol = InteropLibrary.getUncached().readMember(llvmLibrary, name);
                nativeSymbol = NativeCExtSymbol.ensureExecutable(nativeSymbol, symbol);
                dynamicObjectLib.put(symbolCache, symbol, nativeSymbol);
                return nativeSymbol;
            } catch (UnknownIdentifierException e) {
                throw PRaiseNode.raiseUncached(location, PythonBuiltinClassType.SystemError, ErrorMessages.INVALID_CAPI_FUNC, symbol.getTsName());
            } catch (UnsupportedMessageException e) {
                throw PRaiseNode.raiseUncached(location, PythonBuiltinClassType.SystemError, ErrorMessages.CORRUPTED_CAPI_LIB_OBJ, llvmLibrary);
            }
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class EnsureTruffleStringNode extends Node {
        public abstract Object execute(Node inliningTarget, Object obj);

        @Specialization
        static TruffleString doString(String s,
                        @Cached(inline = false) TruffleString.FromJavaStringNode fromJavaStringNode) {
            return fromJavaStringNode.execute(s, TS_ENCODING);
        }

        @Fallback
        static Object doObj(Object o) {
            return o;
        }
    }

    @GenerateInline(false) // footprint reduction 40 -> 22
    @GenerateUncached
    public abstract static class EncodeNativeStringNode extends PNodeWithContext {

        public abstract byte[] execute(Charset charset, Object unicodeObject, TruffleString errors);

        @Specialization
        static byte[] doGeneric(Charset charset, Object unicodeObject, TruffleString errors,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringNode castToTruffleStringNode,
                        @Cached TruffleString.EqualNode eqNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            TruffleString str;
            try {
                str = castToTruffleStringNode.execute(inliningTarget, unicodeObject);
            } catch (CannotCastException e) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.S_MUST_BE_S_NOT_P, "argument", "string", unicodeObject);
            }
            try {
                CodingErrorAction action = BytesBuiltins.toCodingErrorAction(inliningTarget, errors, raiseNode, eqNode);
                return BytesBuiltins.doEncode(charset, str, action);
            } catch (CharacterCodingException e) {
                throw raiseNode.get(inliningTarget).raise(UnicodeEncodeError, ErrorMessages.M, e);
            }
        }
    }

    public abstract static class Charsets {
        private static final int NATIVE_ORDER = 0;
        private static Charset UTF32;
        private static Charset UTF32LE;
        private static Charset UTF32BE;

        private static final TruffleString T_UTF_32 = tsLiteral("UTF-32");
        private static final TruffleString T_UTF_32LE = tsLiteral("UTF-32LE");
        private static final TruffleString T_UTF_32BE = tsLiteral("UTF-32BE");

        @TruffleBoundary
        public static Charset getUTF32Charset(int byteorder) {
            String utf32Name = getUTF32Name(byteorder).toJavaStringUncached();
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

        public static TruffleString getUTF32Name(int byteorder) {
            TruffleString csName;
            if (byteorder == 0) {
                csName = T_UTF_32;
            } else if (byteorder < 0) {
                csName = T_UTF_32LE;
            } else {
                csName = T_UTF_32BE;
            }
            return csName;
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    @ImportStatic(CApiGuards.class)
    public abstract static class ReadUnicodeArrayNode extends PNodeWithContext {

        public abstract int[] execute(Node inliningTarget, Object array, int length, int elementSize);

        public static int[] executeUncached(Object array, int length, int elementSize) {
            return ReadUnicodeArrayNodeGen.getUncached().execute(null, array, length, elementSize);
        }

        @Specialization(guards = "elementSize == 1")
        static int[] read1(Node inliningTarget, Object array, int length, @SuppressWarnings("unused") int elementSize,
                        @Shared @Cached InlinedConditionProfile calcLength,
                        @Cached(inline = false) CStructAccess.ReadByteNode read) {
            int len = length;
            if (calcLength.profile(inliningTarget, len == -1)) {
                do {
                    len++;
                } while (read.readArrayElement(array, len) != 0);
            }
            int[] result = new int[len];
            for (int i = 0; i < len; i++) {
                result[i] = read.readArrayElement(array, i) & 0xFF;
            }
            return result;
        }

        @Specialization(guards = "elementSize == 2")
        static int[] read2(Node inliningTarget, Object array, int length, @SuppressWarnings("unused") int elementSize,
                        @Shared @Cached InlinedConditionProfile calcLength,
                        @Cached(inline = false) CStructAccess.ReadI16Node read) {
            int len = length;
            if (calcLength.profile(inliningTarget, len == -1)) {
                do {
                    len++;
                } while (read.readArrayElement(array, len) != 0);
            }
            int[] result = new int[len];
            for (int i = 0; i < len; i++) {
                result[i] = read.readArrayElement(array, i) & 0xFFFF;
            }
            return result;
        }

        @Specialization(guards = "elementSize == 4")
        static int[] read4(Node inliningTarget, Object array, int length, @SuppressWarnings("unused") int elementSize,
                        @Shared @Cached InlinedConditionProfile calcLength,
                        @Cached(inline = false) CStructAccess.ReadI32Node read) {
            int len = length;
            if (calcLength.profile(inliningTarget, len == -1)) {
                do {
                    len++;
                } while (read.readArrayElement(array, len) != 0);
            }
            int[] result = new int[len];
            for (int i = 0; i < len; i++) {
                result[i] = read.readArrayElement(array, i);
            }
            return result;
        }
    }

    @GenerateInline(inlineByDefault = true)
    @GenerateCached
    @GenerateUncached
    @ImportStatic({PGuards.class, CApiGuards.class})
    public abstract static class ConvertPIntToPrimitiveNode extends Node {

        public abstract Object execute(Node inliningTarget, Object o, int signed, int targetTypeSize, boolean exact);

        public final Object execute(Node inliningTarget, Object o, int signed, int targetTypeSize) {
            return execute(inliningTarget, o, signed, targetTypeSize, true);
        }

        public final long executeLongCached(Object o, int signed, int targetTypeSize, boolean exact) throws UnexpectedResultException {
            return PGuards.expectLong(execute(this, o, signed, targetTypeSize, exact));
        }

        public final int executeIntCached(Object o, int signed, int targetTypeSize, boolean exact) throws UnexpectedResultException {
            return PGuards.expectInteger(execute(this, o, signed, targetTypeSize, exact));
        }

        public final long executeLongCached(Object o, int signed, int targetTypeSize) throws UnexpectedResultException {
            return PGuards.expectLong(execute(this, o, signed, targetTypeSize, true));
        }

        public final int executeIntCached(Object o, int signed, int targetTypeSize) throws UnexpectedResultException {
            return PGuards.expectInteger(execute(this, o, signed, targetTypeSize, true));
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
                        @Shared @Cached(inline = false) AsNativePrimitiveNode asNativePrimitiveNode) {
            return asNativePrimitiveNode.execute(nativeWrapper.getLong(), signed, targetTypeSize, exact);
        }

        @Specialization
        static Object doInt(int value, int signed, int targetTypeSize, boolean exact,
                        @Shared @Cached(inline = false) AsNativePrimitiveNode asNativePrimitiveNode) {
            return asNativePrimitiveNode.execute(value, signed, targetTypeSize, exact);
        }

        @Specialization
        static Object doLong(long value, int signed, int targetTypeSize, boolean exact,
                        @Shared @Cached(inline = false) AsNativePrimitiveNode asNativePrimitiveNode) {
            return asNativePrimitiveNode.execute(value, signed, targetTypeSize, exact);
        }

        @Specialization(guards = {"!isPrimitiveNativeWrapper(obj)"}, replaces = {"doInt", "doLong"})
        static Object doOther(Object obj, int signed, int targetTypeSize, boolean exact,
                        @Shared @Cached(inline = false) AsNativePrimitiveNode asNativePrimitiveNode) {
            return asNativePrimitiveNode.execute(obj, signed, targetTypeSize, exact);
        }

        static boolean fitsInInt32(PrimitiveNativeWrapper nativeWrapper) {
            return nativeWrapper.isBool() || nativeWrapper.isInt();
        }

        static boolean fitsInInt64(PrimitiveNativeWrapper nativeWrapper) {
            return nativeWrapper.isIntLike() || nativeWrapper.isBool();
        }

        static boolean fitsInUInt32(PrimitiveNativeWrapper nativeWrapper) {
            return (nativeWrapper.isBool() || nativeWrapper.isInt()) && nativeWrapper.getInt() >= 0;
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
    @GenerateInline(false) // footprint reduction 28 -> 10, inherits non-inlineable execute()
    @GenerateUncached
    @ImportStatic({SpecialMethodNames.class, CApiGuards.class})
    public abstract static class AsNativeDoubleNode extends CExtToNativeNode {
        public abstract double executeDouble(Object arg);

        @Specialization(guards = "!isNativeWrapper(value)")
        static double runGeneric(Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached PyFloatAsDoubleNode asDoubleNode) {
            // IMPORTANT: this should implement the behavior like 'PyFloat_AsDouble'. So, if it
            // is a float object, use the value and do *NOT* call '__float__'.
            return asDoubleNode.execute(null, inliningTarget, value);
        }

        @Specialization(guards = "!object.isDouble()")
        static double doLongNativeWrapper(PrimitiveNativeWrapper object) {
            return object.getLong();
        }

        @Specialization(guards = "object.isDouble()")
        static double doDoubleNativeWrapper(PrimitiveNativeWrapper object) {
            return object.getDouble();
        }
    }

    public abstract static class CheckFunctionResultNode extends PNodeWithContext {
        public static void checkFunctionResult(Node inliningTarget, TruffleString name, boolean indicatesError, boolean strict, PythonLanguage language, PythonContext context,
                        InlinedConditionProfile errOccurredProfile, TruffleString nullButNoErrorMessage, TruffleString resultWithErrorMessage) {
            PythonThreadState threadState = context.getThreadState(language);
            checkFunctionResult(inliningTarget, threadState, name, indicatesError, strict, errOccurredProfile, nullButNoErrorMessage, resultWithErrorMessage);
        }

        /**
         * Check the result of a C extension function.
         *
         * @param inliningTarget The processing node (also needed for the source location if a
         *            {@code SystemError} is raised).
         * @param threadState The Python thread state.
         * @param name The name of the function (used for the error message).
         * @param indicatesError {@code true} if the function results indicates an error (e.g.
         *            {@code NULL} if the return type is a pointer or {@code -1} if the return type
         *            is an int).
         * @param strict If {@code true}, a {@code SystemError} will be raised if the result value
         *            indicates an error but no exception was set. Setting this to {@code false}
         *            mostly makes sense for primitive return values with semantics
         *            {@code if (res != -1 && PyErr_Occurred()}.
         * @param errOccurredProfile Profiles if a Python exception occurred and is set in the
         *            context.
         * @param nullButNoErrorMessage Error message used if the value indicates an error and is
         *            not primitive but no error was set.
         * @param resultWithErrorMessage Error message used if an error was set but the value does
         *            not indicate and error.
         */
        public static void checkFunctionResult(Node inliningTarget, PythonThreadState threadState, TruffleString name, boolean indicatesError, boolean strict,
                        InlinedConditionProfile errOccurredProfile, TruffleString nullButNoErrorMessage, TruffleString resultWithErrorMessage) {
            PException currentException = threadState.getCurrentException();
            boolean errOccurred = errOccurredProfile.profile(inliningTarget, currentException != null);
            if (indicatesError) {
                // consume exception
                threadState.setCurrentException(null);
                if (errOccurred) {
                    assert currentException != null;
                    throw currentException.getExceptionForReraise(false);
                } else if (strict) {
                    throw raiseNullButNoError(inliningTarget, name, nullButNoErrorMessage);
                }
            } else if (errOccurred) {
                assert currentException != null;
                // consume exception
                threadState.setCurrentException(null);
                throw raiseResultWithError(inliningTarget, name, currentException, resultWithErrorMessage);
            }
        }

        @TruffleBoundary
        private static PException raiseNullButNoError(Node node, TruffleString name, TruffleString nullButNoErrorMessage) {
            throw PRaiseNode.raiseUncached(node, SystemError, nullButNoErrorMessage, name);
        }

        @TruffleBoundary
        private static PException raiseResultWithError(Node node, TruffleString name, PException currentException, TruffleString resultWithErrorMessage) {
            PBaseException sysExc = PythonObjectFactory.getUncached().createBaseException(SystemError, resultWithErrorMessage, new Object[]{name});
            sysExc.setCause(currentException.getEscapedException());
            throw PRaiseNode.raiseExceptionObject(node, sysExc, PythonOptions.isPExceptionWithJavaStacktrace(PythonLanguage.get(null)));
        }

        public abstract Object execute(PythonContext context, TruffleString name, Object result);
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class GetByteArrayNode extends Node {

        public abstract byte[] execute(Node inliningTarget, Object obj, long n) throws InteropException, OverflowException;

        @Specialization
        static byte[] doCArrayWrapper(CByteArrayWrapper obj, long n) {
            return subRangeIfNeeded(obj.getByteArray(), n);
        }

        @Specialization
        static byte[] doForeign(Object obj, long n,
                        @Cached(inline = false) CStructAccess.ReadByteNode readNode) {
            return readNode.readByteArray(obj, (int) n);
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
    @ImportStatic({PGuards.class, SpecialMethodSlot.class})
    @GenerateInline(false) // footprint reduction 32 -> 15, triggers GR-44020
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
            if (Integer.toUnsignedLong((int) obj) == obj) {
                return (int) obj;
            } else {
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
            return doLongToUInt32PosExact(obj, signed, targetTypeSize, exact, raiseNode);
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
                        @Bind("this") Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached(parameters = "Index") LookupSpecialMethodSlotNode lookupIndex,
                        @Cached CallUnaryMethodNode call,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {

            Object type = getClassNode.execute(inliningTarget, obj);
            Object indexDescr = lookupIndex.execute(null, type, obj);

            Object result;
            if (indexDescr != PNone.NO_VALUE) {
                result = call.executeObject(null, indexDescr, obj);
            } else {
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, ErrorMessages.INTEGER_REQUIRED_GOT, obj);
            }

            /*
             * The easiest would be to recursively use this node and ensure that this generic case
             * isn't taken but we cannot guarantee that because the uncached version will always try
             * the generic case first. Hence, the 'toInt32' and 'toInt64' handle all cases in
             * if-else style. This won't be as bad as it looks in source code because arguments
             * 'signed', 'targetTypeSize', and 'exact' are usually constants.
             */
            if (targetTypeSize == 4) {
                return toInt32(result, signed, exact, raiseNode);
            } else if (targetTypeSize == 8) {
                return toInt64(result, signed, exact, raiseNode);
            }
            throw raiseNode.raise(SystemError, ErrorMessages.UNSUPPORTED_TARGET_SIZE, targetTypeSize);
        }

        @Specialization(guards = {"targetTypeSize != 4", "targetTypeSize != 8"})
        @SuppressWarnings("unused")
        static int doUnsupportedTargetSize(Object obj, int signed, int targetTypeSize, boolean exact,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(SystemError, ErrorMessages.UNSUPPORTED_TARGET_SIZE, targetTypeSize);
        }

        private static PException raiseNegativeValue(PRaiseNode raiseNativeNode) {
            throw raiseNativeNode.raise(OverflowError, ErrorMessages.CANNOT_CONVERT_NEGATIVE_VALUE_TO_UNSIGNED_INT);
        }

        /**
         * Slow-path conversion of an object to a signed or unsigned 32-bit value.
         */
        private static int toInt32(Object object, int signed, boolean exact,
                        PRaiseNode raiseNode) {
            if (object instanceof Integer) {
                int ival = (int) object;
                if (signed != 0) {
                    return ival;
                }
                return doIntToUInt32(ival, signed, 4, exact, raiseNode);
            } else if (object instanceof Long) {
                long lval = (long) object;
                if (exact) {
                    if (signed != 0) {
                        return doLongToInt32Exact(lval, 1, 4, true, raiseNode);
                    }
                    return doLongToUInt32Exact(lval, signed, 4, true, raiseNode);
                }
                return doLongToInt32Lossy(lval, 0, 4, false);
            } else if (object instanceof PInt) {
                PInt pval = (PInt) object;
                if (exact) {
                    return doPIntTo32Bit(pval, signed, 4, true, raiseNode);
                }
                return doPIntToInt32Lossy(pval, signed, 4, false);
            } else if (object instanceof PythonNativeVoidPtr) {
                // that's just not possible
                throw raiseNode.raise(PythonErrorType.OverflowError, ErrorMessages.PYTHON_INT_TOO_LARGE_TO_CONV_TO_C_TYPE, 4);
            }
            throw raiseNode.raise(PythonErrorType.TypeError, ErrorMessages.INDEX_RETURNED_NON_INT, object);
        }

        /**
         * Slow-path conversion of an object to a signed or unsigned 64-bit value.
         */
        private static Object toInt64(Object object, int signed, boolean exact,
                        PRaiseNode raiseNode) {
            if (object instanceof Integer) {
                Integer ival = (Integer) object;
                if (signed != 0) {
                    return ival.longValue();
                }
                return doIntToUInt64(ival, signed, 8, exact, raiseNode);
            } else if (object instanceof Long) {
                long lval = (long) object;
                if (signed != 0) {
                    return doLongToInt64(lval, 1, 8, exact);
                }
                return doLongToUInt64(lval, signed, 8, exact, raiseNode);
            } else if (object instanceof PInt) {
                PInt pval = (PInt) object;
                if (exact) {
                    return doPIntTo64Bit(pval, signed, 8, true, raiseNode);
                }
                return doPIntToInt64Lossy(pval, signed, 8, false);
            } else if (object instanceof PythonNativeVoidPtr) {
                return doVoidPtrToI64((PythonNativeVoidPtr) object, signed, 8, exact);
            }
            throw raiseNode.raise(PythonErrorType.TypeError, ErrorMessages.INDEX_RETURNED_NON_INT, object);
        }
    }

    /**
     * This node converts a {@link String} object to a {@link TruffleString} or it converts a
     * {@code NULL} pointer to {@link PNone#NONE}. This is a very special use case and certainly
     * only good for reading a member of type
     * {@link com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef#HPY_MEMBER_STRING} or
     * {@link com.oracle.graal.python.builtins.objects.cext.capi.CApiMemberAccessNodes#T_STRING}.
     */
    @GenerateInline(false) // footprint reduction 32 -> 13, inherits non-inlineable execute()
    @GenerateUncached
    public abstract static class StringAsPythonStringNode extends CExtToJavaNode {

        @Specialization
        static TruffleString doJavaString(String value,
                        @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
            // TODO review with GR-37896
            return fromJavaStringNode.execute(value, TS_ENCODING);
        }

        @Specialization
        static TruffleString doTruffleString(TruffleString value) {
            return value;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "interopLib.isNull(value)", limit = "3")
        static Object doGeneric(Object value,
                        @CachedLibrary("value") InteropLibrary interopLib) {
            return PNone.NONE;
        }

        @Specialization
        static TruffleString doNative(Object value,
                        @Cached FromCharPointerNode fromPtr) {
            return fromPtr.execute(value);
        }
    }

    /**
     * This node converts a C Boolean value to Python Boolean.
     */
    @GenerateInline(false) // footprint reduction 24 -> 5, inherits non-inlineable execute()
    @GenerateUncached
    public abstract static class NativePrimitiveAsPythonBooleanNode extends CExtToJavaNode {

        @Specialization
        static Boolean doBoolean(Boolean b) {
            return b;
        }

        @Specialization
        static Object doByte(byte b) {
            return b != 0;
        }

        @Specialization
        static Object doShort(short i) {
            return i != 0;
        }

        @Specialization
        static Object doLong(long l) {
            // If the integer is out of byte range, we just to a lossy cast since that's the same
            // semantics as we should just read a single byte.
            return l != 0;
        }

        @Specialization(replaces = {"doBoolean", "doByte", "doShort", "doLong"}, limit = "1")
        static Object doGeneric(Object n,
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
    @GenerateInline(false) // footprint reduction 36 -> 17
    @GenerateUncached
    public abstract static class NativePrimitiveAsPythonCharNode extends CExtToJavaNode {

        @Specialization
        static TruffleString doByte(byte b,
                        @Shared("fromInt") @Cached TruffleString.FromIntArrayUTF32Node fromIntArrayNode,
                        @Shared("switchEnc") @Cached TruffleString.SwitchEncodingNode switchEncodingNode) {
            // fromIntArrayNode return utf32, thich is at this point the same as TS_ENCODING,
            // but might change in the future
            return switchEncodingNode.execute(fromIntArrayNode.execute(new int[]{b}), TS_ENCODING);
        }

        @Specialization
        static TruffleString doShort(short i,
                        @Shared("fromInt") @Cached TruffleString.FromIntArrayUTF32Node fromIntArrayNode,
                        @Shared("switchEnc") @Cached TruffleString.SwitchEncodingNode switchEncodingNode) {
            // fromIntArrayNode return utf32, thich is at this point the same as TS_ENCODING,
            // but might change in the future
            return switchEncodingNode.execute(fromIntArrayNode.execute(new int[]{i}, 0, 1), TS_ENCODING);
        }

        @Specialization
        static TruffleString doLong(long l,
                        @Cached TruffleString.FromLongNode fromLongNode) {
            return fromLongNode.execute(l, TS_ENCODING, true);
        }

        @Specialization(replaces = {"doByte", "doShort", "doLong"}, limit = "1")
        static Object doGeneric(Object n,
                        @CachedLibrary("n") InteropLibrary lib,
                        @Shared("fromInt") @Cached TruffleString.FromIntArrayUTF32Node fromIntArrayNode,
                        @Shared("switchEnc") @Cached TruffleString.SwitchEncodingNode switchEncodingNode) {
            if (lib.fitsInShort(n)) {
                try {
                    // fromIntArrayNode return utf32, thich is at this point the same as
                    // TS_ENCODING,
                    // but might change in the future
                    return switchEncodingNode.execute(fromIntArrayNode.execute(new int[]{lib.asShort(n)}, 0, 1), TS_ENCODING);
                } catch (UnsupportedMessageException e) {
                    // fall through
                }
            }
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @GenerateInline(false) // footprint reduction 20 -> 1, inherits non-inlineable execute()
    @GenerateUncached
    public abstract static class NativeUnsignedByteNode extends CExtToJavaNode {

        @Specialization
        static int doUnsignedIntPositive(int n) {
            return n & 0xff;
        }
    }

    @GenerateInline(false) // footprint reduction 20 -> 1, inherits non-inlineable execute()
    @GenerateUncached
    public abstract static class NativeUnsignedShortNode extends CExtToJavaNode {

        @Specialization
        static int doUnsignedIntPositive(int n) {
            return n & 0xffff;
        }
    }

    /**
     * This node converts a native primitive value to an appropriate Python value considering the
     * native value as unsigned. For example, a negative {@code int} value will be converted to a
     * positive {@code long} value.
     */
    @GenerateInline(false) // footprint reduction 24 -> 5, inherits non-inlineable execute()

    @GenerateUncached
    public abstract static class NativeUnsignedPrimitiveAsPythonObjectNode extends CExtToJavaNode {

        @Specialization(guards = "n >= 0")
        static int doUnsignedIntPositive(int n) {
            return n;
        }

        @Specialization(replaces = "doUnsignedIntPositive")
        static long doUnsignedInt(int n) {
            if (n < 0) {
                return n & 0xffffffffL;
            }
            return n;
        }

        @Specialization(guards = "n >= 0")
        static long doUnsignedLongPositive(long n) {
            return n;
        }

        @Specialization(guards = "n < 0")
        static Object doUnsignedLongNegative(long n,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            return factory.createInt(PInt.longToUnsignedBigInteger(n));
        }

        @Specialization(replaces = {"doUnsignedIntPositive", "doUnsignedInt", "doUnsignedLongPositive", "doUnsignedLongNegative"})
        static Object doGeneric(Object n,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            if (n instanceof Integer) {
                int i = (int) n;
                if (i >= 0) {
                    return i;
                } else {
                    return doUnsignedInt(i);
                }
            } else if (n instanceof Long) {
                long l = (long) n;
                if (l >= 0) {
                    return l;
                } else {
                    return doUnsignedLongNegative(l, factory);
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
    @GenerateInline(false) // footprint reduction 28 -> 9, inherits non-inlineable execute()
    @GenerateUncached
    public abstract static class AsNativeCharNode extends CExtToNativeNode {

        public abstract byte executeByte(Object value);

        @Specialization
        static byte doGeneric(Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached EncodeNativeStringNode encodeNativeStringNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            byte[] encoded = encodeNativeStringNode.execute(StandardCharsets.UTF_8, value, T_STRICT);
            if (encoded.length != 1) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.BAD_ARG_TYPE_FOR_BUILTIN_OP);
            }
            return encoded[0];
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
        Object doGeneric(Object value,
                        @Cached AsNativePrimitiveNode asNativePrimitiveNode) {
            return asNativePrimitiveNode.execute(value, signed, targetTypeSize, true);
        }
    }

    /**
     * Implements semantics of function {@code typeobject.c: getindex}.
     */
    @GenerateInline(false) // footprint reduction 60 -> 44
    public abstract static class GetIndexNode extends Node {
        public abstract int execute(Object self, Object indexObj);

        @Specialization
        static int doIt(Object self, Object indexObj,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedBranchProfile indexLt0Branch,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached PyObjectSizeNode sizeNode,
                        @Cached NormalizeIndexNode normalizeIndexNode) {
            int index = asSizeNode.executeExact(null, inliningTarget, indexObj);
            if (index < 0) {
                indexLt0Branch.enter(inliningTarget);
                return normalizeIndexNode.execute(index, sizeNode.execute(null, inliningTarget, self));
            }
            return index;
        }

        public static GetIndexNode create() {
            return GetIndexNodeGen.create();
        }
    }
}
