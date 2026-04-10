/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.readPtrField;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.writePtrField;
import static com.oracle.graal.python.nfi2.NativeMemory.readByteArrayElement;
import static com.oracle.graal.python.nfi2.NativeMemory.readByteArrayElements;
import static com.oracle.graal.python.nfi2.NativeMemory.readIntArrayElement;
import static com.oracle.graal.python.nfi2.NativeMemory.readShortArrayElement;
import static com.oracle.graal.python.nodes.ErrorMessages.RETURNED_NULL_WO_SETTING_EXCEPTION;
import static com.oracle.graal.python.nodes.ErrorMessages.RETURNED_RESULT_WITH_EXCEPTION_SET;
import static com.oracle.graal.python.nodes.StringLiterals.T_IGNORE;
import static com.oracle.graal.python.nodes.StringLiterals.T_REPLACE;
import static com.oracle.graal.python.nodes.StringLiterals.T_STRICT;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.SystemError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.UnicodeEncodeError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.nio.charset.Charset;
import java.util.logging.Level;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.bytes.BytesCommonBuiltins;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.FromCharPointerNode;
import com.oracle.graal.python.builtins.objects.cext.capi.PThreadState;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNewRefNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodesFactory.GetIndexNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodesFactory.ReadUnicodeArrayNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodesFactory.TransformPExceptionToNativeCachedNodeGen;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetObjectSlotsNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotLen.CallSlotLenNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyNumberIndexNode;
import com.oracle.graal.python.nfi2.NativeMemory;
import com.oracle.graal.python.nfi2.NfiBoundFunction;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.GetThreadStateNode;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

public abstract class CExtCommonNodes {

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class EnsureTruffleStringNode extends Node {
        public abstract Object execute(Node inliningTarget, Object obj);

        @Specialization
        static TruffleString doString(String s,
                        @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
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

        public abstract TruffleString execute(TruffleString.Encoding encoding, Object unicodeObject, TruffleString errors);

        @Specialization
        static TruffleString doGeneric(TruffleString.Encoding encoding, Object unicodeObject, TruffleString errors,
                        @Bind Node inliningTarget,
                        @Cached CastToTruffleStringNode castToTruffleStringNode,
                        @Cached TruffleString.EqualNode eqNode,
                        @Cached TruffleString.IsValidNode isValidNode,
                        @Cached TruffleString.GetCodeRangeNode getCodeRangeNode,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode,
                        @Cached InlinedConditionProfile strictProfile,
                        @Cached InlinedConditionProfile ignoreProfile,
                        @Cached InlinedConditionProfile replaceProfile,
                        @Cached PRaiseNode raiseNode) {
            assert encoding == TruffleString.Encoding.US_ASCII ||
                            encoding == TruffleString.Encoding.ISO_8859_1 ||
                            encoding == TruffleString.Encoding.UTF_8 ||
                            encoding == TruffleString.Encoding.UTF_16LE ||
                            encoding == TruffleString.Encoding.UTF_16BE ||
                            encoding == TruffleString.Encoding.UTF_32LE ||
                            encoding == TruffleString.Encoding.UTF_32BE : encoding;
            TruffleString str;
            try {
                str = castToTruffleStringNode.execute(inliningTarget, unicodeObject);
            } catch (CannotCastException e) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.S_MUST_BE_S_NOT_P, "argument", "a string", unicodeObject);
            }
            // deliberate individual branches to ensure the error handlers are partial
            // evaluation constant
            if (ignoreProfile.profile(inliningTarget, eqNode.execute(T_IGNORE, errors, TS_ENCODING))) {
                return switchEncodingNode.execute(str, encoding, BytesCommonBuiltins.TS_TRANSCODE_ERROR_HANDLER_IGNORE);
            } else {
                if (strictProfile.profile(inliningTarget, eqNode.execute(T_STRICT, errors, TS_ENCODING))) {
                    if (!isValidNode.execute(str, TS_ENCODING)) {
                        // any invalid string will trigger an exception when strict mode is used, so
                        // we don't even need to try
                        throw raiseNode.raise(inliningTarget, UnicodeEncodeError, ErrorMessages.M);
                    }
                    if (encoding == TruffleString.Encoding.ISO_8859_1 || encoding == TruffleString.Encoding.US_ASCII) {
                        // if the target encoding is ASCII or LATIN-1, transcoding will still fail
                        // if the source string is in any UTF-* encoding and contains characters
                        // outside the target encoding's value range
                        TruffleString.CodeRange codeRange = getCodeRangeNode.execute(str, TS_ENCODING);
                        if (!codeRange.isSubsetOf(encoding == TruffleString.Encoding.ISO_8859_1 ? TruffleString.CodeRange.LATIN_1 : TruffleString.CodeRange.ASCII)) {
                            throw raiseNode.raise(inliningTarget, UnicodeEncodeError, ErrorMessages.M);
                        }
                    }
                } else if (replaceProfile.profile(inliningTarget, !eqNode.execute(T_REPLACE, errors, TS_ENCODING))) {
                    throw raiseNode.raise(inliningTarget, PythonErrorType.LookupError, ErrorMessages.UNKNOWN_ERROR_HANDLER, errors);
                }
                return switchEncodingNode.execute(str, encoding);
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
    public abstract static class ReadUnicodeArrayNode extends PNodeWithContext {

        public abstract int[] execute(Node inliningTarget, long array, int length, int elementSize);

        public static int[] executeUncached(long array, int length, int elementSize) {
            return ReadUnicodeArrayNodeGen.getUncached().execute(null, array, length, elementSize);
        }

        @Specialization(guards = "elementSize == 1")
        static int[] read1(Node inliningTarget, long array, int length, @SuppressWarnings("unused") int elementSize,
                        @Shared @Cached InlinedConditionProfile calcLength) {
            int len = length;
            if (calcLength.profile(inliningTarget, len == -1)) {
                do {
                    len++;
                } while (readByteArrayElement(array, len) != 0);
            }
            int[] result = new int[len];
            for (int i = 0; i < len; i++) {
                result[i] = readByteArrayElement(array, i) & 0xFF;
            }
            return result;
        }

        @Specialization(guards = "elementSize == 2")
        static int[] read2(Node inliningTarget, long array, int length, @SuppressWarnings("unused") int elementSize,
                        @Shared @Cached InlinedConditionProfile calcLength) {
            int len = length;
            if (calcLength.profile(inliningTarget, len == -1)) {
                do {
                    len++;
                } while (readShortArrayElement(array, len) != 0);
            }
            int[] result = new int[len];
            for (int i = 0; i < len; i++) {
                result[i] = readShortArrayElement(array, i) & 0xFFFF;
            }
            return result;
        }

        @Specialization(guards = "elementSize == 4")
        static int[] read4(Node inliningTarget, long array, int length, @SuppressWarnings("unused") int elementSize,
                        @Shared @Cached InlinedConditionProfile calcLength) {
            int len = length;
            if (calcLength.profile(inliningTarget, len == -1)) {
                do {
                    len++;
                } while (readIntArrayElement(array, len) != 0);
            }
            int[] result = new int[len];
            for (int i = 0; i < len; i++) {
                result[i] = readIntArrayElement(array, i);
            }
            return result;
        }
    }

    @GenerateInline(inlineByDefault = true)
    @GenerateCached
    @GenerateUncached
    @ImportStatic(PGuards.class)
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

        @Specialization(replaces = {"doInt", "doLong"})
        static Object doOther(Object obj, int signed, int targetTypeSize, boolean exact,
                        @Shared @Cached(inline = false) AsNativePrimitiveNode asNativePrimitiveNode) {
            return asNativePrimitiveNode.execute(obj, signed, targetTypeSize, exact);
        }
    }

    /**
     * Use this node to transform an exception to native if a Python exception was thrown during an
     * upcall and before returning to native code. This node will reify the exception appropriately
     * and register the exception as the current exception.
     */
    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class TransformExceptionToNativeNode extends Node {

        public abstract void execute(Node inliningTarget, Object pythonException);

        @TruffleBoundary
        public static void executeUncached(Object pythonException) {
            CExtCommonNodesFactory.TransformExceptionToNativeNodeGen.getUncached().execute(null, pythonException);
        }

        @Specialization
        static void setCurrentException(Node inliningTarget, Object pythonException,
                        @Cached GetThreadStateNode getThreadStateNode,
                        @Cached CExtNodes.XDecRefPointerNode decRefPointerNode,
                        @Cached(inline = false) PythonToNativeNewRefNode pythonToNativeNode) {
            /*
             * Run the ToNative conversion early so that the reference poll won't interrupt between
             * the read and write.
             */
            long currentException = pythonToNativeNode.executeLong(pythonException);
            long nativeThreadState = PThreadState.getOrCreateNativeThreadState(getThreadStateNode.execute(inliningTarget));
            long oldException = readPtrField(nativeThreadState, CFields.PyThreadState__current_exception);
            writePtrField(nativeThreadState, CFields.PyThreadState__current_exception, currentException);
            decRefPointerNode.execute(inliningTarget, oldException);
        }
    }

    /**
     * This node acts as a branch profile.
     */
    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class TransformPExceptionToNativeNode extends Node {
        public abstract void execute(Node inliningTarget, PException e);

        @TruffleBoundary(allowInlining = true)
        public static void executeUncached(PException ex) {
            CExtCommonNodesFactory.TransformPExceptionToNativeNodeGen.getUncached().execute(null, ex);
        }

        @Specialization
        static void setCurrentException(Node inliningTarget, PException ex,
                        @Cached TransformExceptionToNativeNode transformNode) {
            transformNode.execute(inliningTarget, ex.getEscapedException());
        }
    }

    /**
     * This node acts as a branch profile.
     */
    @GenerateInline(false)
    @GenerateCached
    public abstract static class TransformPExceptionToNativeCachedNode extends Node {
        public static TransformPExceptionToNativeCachedNode create() {
            return TransformPExceptionToNativeCachedNodeGen.create();
        }

        public abstract void execute(PException e);

        @Specialization
        static void setCurrentException(PException ex,
                        @Bind Node inliningTarget,
                        @Cached TransformExceptionToNativeNode transformNode) {
            transformNode.execute(inliningTarget, ex.getEscapedException());
        }
    }

    /**
     * Equivalent of native {@code _PyErr_GetRaisedException}. Returns {@link PNone#NO_VALUE} if
     * there is no exception.
     */
    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class ReadAndClearNativeException extends Node {
        public abstract Object execute(Node inliningTarget, PythonThreadState threadState);

        public static Object executeUncached(PythonThreadState threadState) {
            return CExtCommonNodesFactory.ReadAndClearNativeExceptionNodeGen.getUncached().execute(null, threadState);
        }

        @Specialization
        static Object getException(PythonThreadState threadState,
                        @Cached CApiTransitions.NativeToPythonTransferNode nativeToPythonNode) {
            long nativeThreadState = threadState.getNativePointer();
            if (nativeThreadState != PythonAbstractObject.UNINITIALIZED) {
                assert nativeThreadState != PythonAbstractObject.NATIVE_POINTER_FREED;
                Object exception = nativeToPythonNode.execute(readPtrField(nativeThreadState, CFields.PyThreadState__current_exception));
                writePtrField(nativeThreadState, CFields.PyThreadState__current_exception, 0L);
                return exception;
            }
            return PNone.NO_VALUE;
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class TransformExceptionFromNativeNode extends Node {

        @TruffleBoundary
        public static void executeUncached(PythonThreadState threadState, TruffleString name, boolean indicatesError, boolean strict) {
            TransformExceptionFromNativeNode.getUncached().execute(null, threadState, name, indicatesError, strict);
        }

        /**
         * Checks the current exception state with respect to flag {@code indicatesError} (and
         * {@code strict}).
         *
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
         * @param nullButNoErrorMessage Error message used if the value indicates an error and is
         *            not primitive but no error was set.
         * @param resultWithErrorMessage Error message used if an error was set but the value does
         *            not indicate and error.
         */
        public abstract void execute(Node inliningTarget, PythonThreadState threadState, TruffleString name, boolean indicatesError, boolean strict,
                        TruffleString nullButNoErrorMessage, TruffleString resultWithErrorMessage);

        public final void execute(Node inliningTarget, PythonThreadState threadState, TruffleString name, boolean indicatesError, boolean strict) {
            execute(inliningTarget, threadState, name, indicatesError, strict, RETURNED_NULL_WO_SETTING_EXCEPTION, RETURNED_RESULT_WITH_EXCEPTION_SET);
        }

        public final void reraise(Node inliningTarget, PythonThreadState threadState) {
            execute(inliningTarget, threadState, null, true, true, null, null);
        }

        @Specialization
        static void doGeneric(Node inliningTarget, PythonThreadState threadState, TruffleString name, boolean indicatesError, boolean strict,
                        TruffleString nullButNoErrorMessage, TruffleString resultWithErrorMessage,
                        @Cached InlinedConditionProfile errOccurredProfile,
                        @Cached InlinedConditionProfile indicatesErrorProfile,
                        @Cached ReadAndClearNativeException readAndClearNativeException) {
            Object currentException = readAndClearNativeException.execute(inliningTarget, threadState);
            boolean errOccurred = errOccurredProfile.profile(inliningTarget, currentException != PNone.NO_VALUE);
            boolean indicatesErrorProfiled = indicatesErrorProfile.profile(inliningTarget, indicatesError);
            if (indicatesErrorProfiled || errOccurred) {
                checkFunctionResultSlowpath(inliningTarget, name, indicatesErrorProfiled, strict,
                                nullButNoErrorMessage, resultWithErrorMessage, errOccurred, currentException);
            }
        }

        @InliningCutoff
        private static void checkFunctionResultSlowpath(Node inliningTarget, TruffleString name, boolean indicatesError, boolean strict,
                        TruffleString nullButNoErrorMessage, TruffleString resultWithErrorMessage, boolean errOccurred, Object currentException) {
            if (indicatesError) {
                if (errOccurred) {
                    assert currentException != PNone.NO_VALUE;
                    throw PException.fromObjectFixUncachedLocation(currentException, inliningTarget, false);
                } else if (strict) {
                    assert currentException == PNone.NO_VALUE;
                    throw raiseNullButNoError(inliningTarget, name, nullButNoErrorMessage);
                }
            } else if (errOccurred) {
                assert currentException != null;
                throw raiseResultWithError(inliningTarget, name, currentException, resultWithErrorMessage);
            }
        }

        @TruffleBoundary
        private static PException raiseNullButNoError(Node node, TruffleString name, TruffleString nullButNoErrorMessage) {
            throw PRaiseNode.raiseStatic(node, SystemError, nullButNoErrorMessage, name);
        }

        @TruffleBoundary
        private static PException raiseResultWithError(Node node, TruffleString name, Object currentException, TruffleString resultWithErrorMessage) {
            PythonLanguage language = PythonLanguage.get(null);
            PBaseException sysExc = PFactory.createBaseException(language, SystemError, resultWithErrorMessage, new Object[]{name});
            sysExc.setCause(currentException);
            throw PRaiseNode.raiseExceptionObjectStatic(node, sysExc, PythonOptions.isPExceptionWithJavaStacktrace(language));
        }

        public static TransformExceptionFromNativeNode getUncached() {
            return CExtCommonNodesFactory.TransformExceptionFromNativeNodeGen.getUncached();
        }
    }

    public static byte[] getByteArray(long ptr, long n) throws OverflowException {
        return readByteArrayElements(ptr, 0, PInt.intValueExact(n));
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
                        @Bind Node inliningTarget,
                        @Shared("raiseNativeNode") @Cached PRaiseNode raiseNativeNode) {
            if (exact && value < 0) {
                throw raiseNegativeValue(inliningTarget, raiseNativeNode);
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
                        @Bind Node inliningTarget,
                        @Shared("raiseNativeNode") @Cached PRaiseNode raiseNativeNode) {
            if (exact && value < 0) {
                throw raiseNegativeValue(inliningTarget, raiseNativeNode);
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
                        @Bind Node inliningTarget,
                        @Shared("raiseNativeNode") @Cached PRaiseNode raiseNativeNode) {
            if (exact && value < 0) {
                throw raiseNegativeValue(inliningTarget, raiseNativeNode);
            }
            return value;
        }

        @Specialization(guards = {"exact", "targetTypeSize == 4", "signed != 0"})
        @SuppressWarnings("unused")
        static int doLongToInt32Exact(long obj, int signed, int targetTypeSize, boolean exact,
                        @Bind Node inliningTarget,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            try {
                return PInt.intValueExact(obj);
            } catch (OverflowException e) {
                throw raiseNode.raise(inliningTarget, PythonErrorType.OverflowError, ErrorMessages.PYTHON_INT_TOO_LARGE_TO_CONV_TO_C_TYPE, targetTypeSize);
            }
        }

        @Specialization(guards = {"exact", "targetTypeSize == 4", "signed == 0", "obj >= 0"})
        @SuppressWarnings("unused")
        static int doLongToUInt32PosExact(long obj, int signed, int targetTypeSize, boolean exact,
                        @Bind Node inliningTarget,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            if (Integer.toUnsignedLong((int) obj) == obj) {
                return (int) obj;
            } else {
                throw raiseNode.raise(inliningTarget, PythonErrorType.OverflowError, ErrorMessages.PYTHON_INT_TOO_LARGE_TO_CONV_TO_C_TYPE, targetTypeSize);
            }
        }

        @Specialization(guards = {"exact", "targetTypeSize == 4", "signed == 0"}, replaces = "doLongToUInt32PosExact")
        @SuppressWarnings("unused")
        static int doLongToUInt32Exact(long obj, int signed, int targetTypeSize, boolean exact,
                        @Bind Node inliningTarget,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            if (obj < 0) {
                throw raiseNegativeValue(inliningTarget, raiseNode);
            }
            return doLongToUInt32PosExact(obj, signed, targetTypeSize, exact, inliningTarget, raiseNode);
        }

        @Specialization(guards = {"!exact", "targetTypeSize == 4"})
        @SuppressWarnings("unused")
        static int doLongToInt32Lossy(long obj, int signed, int targetTypeSize, boolean exact) {
            return (int) obj;
        }

        @Specialization(guards = {"exact", "targetTypeSize == 4"})
        @SuppressWarnings("unused")
        @TruffleBoundary
        static int doPIntTo32Bit(PInt obj, int signed, int targetTypeSize, boolean exact,
                        @Bind Node inliningTarget,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            try {
                if (signed != 0) {
                    return obj.intValueExact();
                } else if (obj.bitLength() <= 32) {
                    if (obj.isNegative()) {
                        throw raiseNegativeValue(inliningTarget, raiseNode);
                    }
                    return obj.intValue();
                }
            } catch (OverflowException e) {
                // fall through
            }
            throw raiseNode.raise(inliningTarget, PythonErrorType.OverflowError, ErrorMessages.PYTHON_INT_TOO_LARGE_TO_CONV_TO_C_TYPE, targetTypeSize);
        }

        @Specialization(guards = {"exact", "targetTypeSize == 8"})
        @SuppressWarnings("unused")
        @TruffleBoundary
        static long doPIntTo64Bit(PInt obj, int signed, int targetTypeSize, boolean exact,
                        @Bind Node inliningTarget,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            try {
                if (signed != 0) {
                    return obj.longValueExact();
                } else if (obj.bitLength() <= 64) {
                    if (obj.isNegative()) {
                        throw raiseNegativeValue(inliningTarget, raiseNode);
                    }
                    return obj.longValue();
                }
            } catch (OverflowException e) {
                // fall through
            }
            throw raiseNode.raise(inliningTarget, PythonErrorType.OverflowError, ErrorMessages.PYTHON_INT_TOO_LARGE_TO_CONV_TO_C_TYPE, targetTypeSize);
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
                                        "doPIntTo32Bit", "doPIntTo64Bit", "doPIntToInt32Lossy", "doPIntToInt64Lossy"})
        static Object doGeneric(Object obj, int signed, int targetTypeSize, boolean exact,
                        @Bind Node inliningTarget,
                        @Cached PyNumberIndexNode indexNode,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            Object result = indexNode.execute(null, inliningTarget, obj);
            /*
             * The easiest would be to recursively use this node and ensure that this generic case
             * isn't taken but we cannot guarantee that because the uncached version will always try
             * the generic case first. Hence, the 'toInt32' and 'toInt64' handle all cases in
             * if-else style. This won't be as bad as it looks in source code because arguments
             * 'signed', 'targetTypeSize', and 'exact' are usually constants.
             */
            if (targetTypeSize == 4) {
                return toInt32(inliningTarget, result, signed, exact, raiseNode);
            } else if (targetTypeSize == 8) {
                return toInt64(inliningTarget, result, signed, exact, raiseNode);
            }
            throw raiseNode.raise(inliningTarget, SystemError, ErrorMessages.UNSUPPORTED_TARGET_SIZE, targetTypeSize);
        }

        @Specialization(guards = {"targetTypeSize != 4", "targetTypeSize != 8"})
        @SuppressWarnings("unused")
        static int doUnsupportedTargetSize(Object obj, int signed, int targetTypeSize, boolean exact,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, SystemError, ErrorMessages.UNSUPPORTED_TARGET_SIZE, targetTypeSize);
        }

        private static PException raiseNegativeValue(Node inliningTarget, PRaiseNode raiseNativeNode) {
            throw raiseNativeNode.raise(inliningTarget, OverflowError, ErrorMessages.CANNOT_CONVERT_NEGATIVE_VALUE_TO_UNSIGNED_INT);
        }

        /**
         * Slow-path conversion of an object to a signed or unsigned 32-bit value.
         */
        private static int toInt32(Node inliningTarget, Object object, int signed, boolean exact,
                        PRaiseNode raiseNode) {
            if (object instanceof Integer) {
                int ival = (int) object;
                if (signed != 0) {
                    return ival;
                }
                return doIntToUInt32(ival, signed, 4, exact, inliningTarget, raiseNode);
            } else if (object instanceof Long) {
                long lval = (long) object;
                if (exact) {
                    if (signed != 0) {
                        return doLongToInt32Exact(lval, 1, 4, true, inliningTarget, raiseNode);
                    }
                    return doLongToUInt32Exact(lval, signed, 4, true, inliningTarget, raiseNode);
                }
                return doLongToInt32Lossy(lval, 0, 4, false);
            } else if (object instanceof PInt) {
                PInt pval = (PInt) object;
                if (exact) {
                    return doPIntTo32Bit(pval, signed, 4, true, inliningTarget, raiseNode);
                }
                return doPIntToInt32Lossy(pval, signed, 4, false);
            }
            throw raiseNode.raise(inliningTarget, PythonErrorType.TypeError, ErrorMessages.INDEX_RETURNED_NON_INT, object);
        }

        /**
         * Slow-path conversion of an object to a signed or unsigned 64-bit value.
         */
        private static Object toInt64(Node inliningTarget, Object object, int signed, boolean exact,
                        PRaiseNode raiseNode) {
            if (object instanceof Integer) {
                Integer ival = (Integer) object;
                if (signed != 0) {
                    return ival.longValue();
                }
                return doIntToUInt64(ival, signed, 8, exact, inliningTarget, raiseNode);
            } else if (object instanceof Long) {
                long lval = (long) object;
                if (signed != 0) {
                    return doLongToInt64(lval, 1, 8, exact);
                }
                return doLongToUInt64(lval, signed, 8, exact, inliningTarget, raiseNode);
            } else if (object instanceof PInt) {
                PInt pval = (PInt) object;
                if (exact) {
                    return doPIntTo64Bit(pval, signed, 8, true, inliningTarget, raiseNode);
                }
                return doPIntToInt64Lossy(pval, signed, 8, false);
            }
            throw raiseNode.raise(inliningTarget, PythonErrorType.TypeError, ErrorMessages.INDEX_RETURNED_NON_INT, object);
        }
    }

    /**
     * This node converts a {@link String} object to a {@link TruffleString} or it converts a
     * {@code NULL} pointer to {@link PNone#NONE}. This is a very special use case and certainly
     * only good for reading a member of type
     * {@link com.oracle.graal.python.builtins.objects.cext.capi.CApiMemberAccessNodes#T_STRING}.
     */
    @GenerateInline(false) // footprint reduction 32 -> 13, inherits non-inlineable execute()
    @GenerateUncached
    @ImportStatic(NativeMemory.class)
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
        @Specialization(guards = "value == NULLPTR")
        static Object doGeneric(long value) {
            return PNone.NONE;
        }

        @Specialization
        static TruffleString doNative(long value,
                        @Bind Node inliningTarget,
                        @Cached FromCharPointerNode fromPtr) {
            return fromPtr.execute(inliningTarget, value);
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
                        @Bind PythonLanguage language) {
            return PFactory.createInt(language, PInt.longToUnsignedBigInteger(n));
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
                        @Bind Node inliningTarget,
                        @Cached EncodeNativeStringNode encodeNativeStringNode,
                        @Cached TruffleString.ReadByteNode readByteNode,
                        @Cached PRaiseNode raiseNode) {
            TruffleString encoded = encodeNativeStringNode.execute(TruffleString.Encoding.UTF_8, value, T_STRICT);
            if (encoded.byteLength(TruffleString.Encoding.UTF_8) != 1) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.BAD_ARG_TYPE_FOR_BUILTIN_OP);
            }
            return (byte) readByteNode.execute(encoded, 0, TruffleString.Encoding.UTF_8);
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
                        @Bind Node inliningTarget,
                        @Cached InlinedBranchProfile indexLt0Branch,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached CallSlotLenNode callLenNode,
                        @Cached GetObjectSlotsNode getObjectSlotsNode) {
            int index = asSizeNode.executeExact(null, inliningTarget, indexObj);
            if (index < 0) {
                indexLt0Branch.enter(inliningTarget);
                TpSlots slots = getObjectSlotsNode.execute(inliningTarget, self);
                if (slots.sq_length() != null) {
                    int len = callLenNode.execute(null, inliningTarget, slots.sq_length(), self);
                    index += len;
                }
            }
            return index;
        }

        public static GetIndexNode create() {
            return GetIndexNodeGen.create();
        }
    }

    private static final TruffleLogger LOGGER = CApiContext.getLogger(CExtContext.class);

    /**
     * Binds a native pointer with a signature to an object that can be directly
     * {@link NfiBoundFunction#invoke(Object...) invoked}.
     *
     * <p>
     * <b>NOTE:</b> This method will fail if {@link PythonContext#isNativeAccessAllowed() native
     * access} is not allowed
     * </p>
     */
    @TruffleBoundary
    public static NfiBoundFunction bindFunctionPointer(long pointer, NativeCExtSymbol descriptor) {
        PythonContext pythonContext = PythonContext.get(null);
        if (!pythonContext.isNativeAccessAllowed()) {
            LOGGER.severe(PythonUtils.formatJString("Attempting to bind %s to an NFI signature but native access is not allowed", pointer));
        }
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.finer(PythonUtils.formatJString("Binding %s to native callable %s", pointer, descriptor.getName()));
        }
        return descriptor.bind(pythonContext.ensureNfiContext(), pointer);
    }
}
