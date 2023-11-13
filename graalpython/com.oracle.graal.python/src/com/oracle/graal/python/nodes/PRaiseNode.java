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
package com.oracle.graal.python.nodes;

import static com.oracle.graal.python.nodes.ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC;
import static com.oracle.graal.python.nodes.truffle.TruffleStringMigrationHelpers.assertNoJavaString;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.nodes.PRaiseNodeGen.LazyNodeGen;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.EncapsulatingNodeReference;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@ImportStatic(PGuards.class)
@GenerateUncached
@SuppressWarnings("truffle-inlining")       // footprint reduction 32 -> 13
public abstract class PRaiseNode extends Node {

    public final PException execute(Node raisingNode, PythonBuiltinClassType type, Object cause, Object format, Object[] arguments) {
        return execute(raisingNode, type, null, cause, format, arguments);
    }

    public abstract PException execute(Node raisingNode, PythonBuiltinClassType type, Object[] data, Object cause, Object format, Object[] arguments);

    public final PException raise(PythonBuiltinClassType type) {
        throw execute(this, type, null, PNone.NO_VALUE, PNone.NO_VALUE, PythonUtils.EMPTY_OBJECT_ARRAY);
    }

    public final PException raise(PythonBuiltinClassType type, TruffleString message) {
        throw execute(this, type, null, PNone.NO_VALUE, message, PythonUtils.EMPTY_OBJECT_ARRAY);
    }

    public final PException raise(PythonBuiltinClassType type, TruffleString format, Object... arguments) {
        throw execute(this, type, null, PNone.NO_VALUE, format, arguments);
    }

    public final PException raise(PythonBuiltinClassType type, Object[] arguments) {
        throw execute(this, type, null, PNone.NO_VALUE, PNone.NO_VALUE, arguments);
    }

    public final PException raiseWithData(PythonBuiltinClassType type, Object[] data, Object... arguments) {
        throw execute(this, type, data, PNone.NO_VALUE, PNone.NO_VALUE, arguments);
    }

    public final PException raise(PythonBuiltinClassType type, Exception e) {
        throw execute(this, type, null, PNone.NO_VALUE, getMessage(e), PythonUtils.EMPTY_OBJECT_ARRAY);
    }

    public final PException raiseWithCause(PythonBuiltinClassType type, Object cause, TruffleString format, Object... arguments) {
        throw execute(this, type, null, cause, format, arguments);
    }

    public final PException raiseWithCause(PythonBuiltinClassType errorType, PException e, TruffleString message, Object... arguments) {
        return raiseWithCause(errorType, e.getEscapedException(), message, arguments);
    }

    public static PException raiseUncached(Node raisingNode, PythonBuiltinClassType exceptionType) {
        throw PRaiseNodeGen.getUncached().execute(raisingNode, exceptionType, null, PNone.NO_VALUE, PNone.NO_VALUE, PythonUtils.EMPTY_OBJECT_ARRAY);
    }

    public static PException raiseUncached(Node raisingNode, PythonBuiltinClassType exceptionType, TruffleString message) {
        throw PRaiseNodeGen.getUncached().execute(raisingNode, exceptionType, null, PNone.NO_VALUE, assertNoJavaString(message), PythonUtils.EMPTY_OBJECT_ARRAY);
    }

    public static PException raiseUncached(Node raisingNode, PythonBuiltinClassType type, TruffleString format, Object... arguments) {
        throw PRaiseNodeGen.getUncached().execute(raisingNode, type, null, PNone.NO_VALUE, format, arguments);
    }

    public static PException raiseUncached(Node raisingNode, PythonBuiltinClassType type, Exception e) {
        throw PRaiseNodeGen.getUncached().execute(raisingNode, type, null, PNone.NO_VALUE, getMessage(e), PythonUtils.EMPTY_OBJECT_ARRAY);
    }

    /**
     * Raise an error saying that the {@code result} cannot fit into an index-sized integer. Use the
     * specified {@code type} as exception class.
     */
    public final PException raiseNumberTooLarge(PythonBuiltinClassType type, Object result) {
        return execute(this, type, null, PNone.NO_VALUE, ErrorMessages.CANNOT_FIT_P_INTO_INDEXSIZED_INT, new Object[]{result});
    }

    public final PException raiseOverflow() {
        return raiseNumberTooLarge(OverflowError, 0);
    }

    public final PException raiseSystemExit(Object code) {
        return raiseWithData(PythonBuiltinClassType.SystemExit, new Object[]{code}, code);
    }

    public final PException raiseStopIteration() {
        return raise(PythonBuiltinClassType.StopIteration);
    }

    public final PException raiseStopIteration(Object value) {
        final Object retVal = value != null ? value : PNone.NONE;
        final Object[] args = {retVal};
        return raiseWithData(PythonBuiltinClassType.StopIteration, args, retVal);
    }

    public final PException raiseIntegerInterpretationError(Object result) {
        return raise(PythonBuiltinClassType.TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, result);
    }

    public final PException raiseBadInternalCall() {
        return raise(PythonBuiltinClassType.SystemError, BAD_ARG_TO_INTERNAL_FUNC);
    }

    public final PException raiseMemoryError() {
        return raise(PythonBuiltinClassType.MemoryError);
    }

    public final PException raiseExceptionObject(Object exc) {
        throw raiseExceptionObject(this, exc, PythonOptions.isPExceptionWithJavaStacktrace(PythonLanguage.get(this)));
    }

    public static PException raiseExceptionObject(Node raisingNode, Object exc) {
        throw raiseExceptionObject(raisingNode, exc, PythonOptions.isPExceptionWithJavaStacktrace(PythonLanguage.get(raisingNode)));
    }

    public static PException raiseExceptionObject(Node raisingNode, Object exc, boolean withJavaStacktrace) {
        if (raisingNode != null && raisingNode.isAdoptable()) {
            throw PException.fromObject(exc, raisingNode, withJavaStacktrace);
        } else {
            throw PException.fromObject(exc, EncapsulatingNodeReference.getCurrent().get(), withJavaStacktrace);
        }
    }

    @Specialization(guards = {"isNoValue(cause)", "isNoValue(format)", "arguments.length == 0", "exceptionType == cachedType"}, limit = "8")
    static PException doPythonBuiltinTypeCached(Node raisingNode, @SuppressWarnings("unused") PythonBuiltinClassType exceptionType, Object[] data, @SuppressWarnings("unused") PNone cause,
                    @SuppressWarnings("unused") PNone format,
                    @SuppressWarnings("unused") Object[] arguments,
                    @Cached("exceptionType") PythonBuiltinClassType cachedType,
                    @Shared("factory") @Cached PythonObjectFactory factory) {
        throw raiseExceptionObject(raisingNode, factory.createBaseException(cachedType, data));
    }

    @Specialization(guards = {"isNoValue(cause)", "isNoValue(format)", "arguments.length == 0"}, replaces = "doPythonBuiltinTypeCached")
    static PException doPythonBuiltinType(Node raisingNode, PythonBuiltinClassType exceptionType, Object[] data, @SuppressWarnings("unused") PNone cause,
                    @SuppressWarnings("unused") PNone format,
                    @SuppressWarnings("unused") Object[] arguments,
                    @Shared("factory") @Cached PythonObjectFactory factory) {
        throw raiseExceptionObject(raisingNode, factory.createBaseException(exceptionType, data));
    }

    @Specialization(guards = {"isNoValue(cause)", "isNoValue(format)", "arguments.length > 0"})
    static PException doBuiltinType(Node raisingNode, PythonBuiltinClassType type, Object[] data, @SuppressWarnings("unused") PNone cause, @SuppressWarnings("unused") PNone format,
                    Object[] arguments,
                    @Shared("factory") @Cached PythonObjectFactory factory,
                    @Shared("js2ts") @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
        ensureNoJavaStrings(arguments, fromJavaStringNode);
        throw raiseExceptionObject(raisingNode, factory.createBaseException(type, data, factory.createTuple(arguments)));
    }

    @Specialization(guards = {"isNoValue(cause)"})
    static PException doBuiltinType(Node raisingNode, PythonBuiltinClassType type, Object[] data, @SuppressWarnings("unused") PNone cause, TruffleString format, Object[] arguments,
                    @Shared("factory") @Cached PythonObjectFactory factory,
                    @Shared("js2ts") @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
        assert format != null;
        ensureNoJavaStrings(arguments, fromJavaStringNode);
        throw raiseExceptionObject(raisingNode, factory.createBaseException(type, data, format, arguments));
    }

    @Specialization(guards = {"!isNoValue(cause)"})
    static PException doBuiltinTypeWithCause(Node raisingNode, PythonBuiltinClassType type, Object[] data, PBaseException cause, TruffleString format, Object[] arguments,
                    @Shared("factory") @Cached PythonObjectFactory factory,
                    @Shared("js2ts") @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
        assert format != null;
        ensureNoJavaStrings(arguments, fromJavaStringNode);
        PBaseException baseException = factory.createBaseException(type, data, format, arguments);
        baseException.setContext(cause);
        baseException.setCause(cause);
        throw raiseExceptionObject(raisingNode, baseException);
    }

    @TruffleBoundary
    private static TruffleString getMessage(Exception e) {
        String msg = e.getMessage();
        return toTruffleStringUncached(msg != null ? msg : e.getClass().getSimpleName());
    }

    @NeverDefault
    public static PRaiseNode create() {
        return PRaiseNodeGen.create();
    }

    public static PRaiseNode getUncached() {
        return PRaiseNodeGen.getUncached();
    }

    private static void ensureNoJavaStrings(Object[] arguments, TruffleString.FromJavaStringNode fromJavaStringNode) {
        for (int i = 0; i < arguments.length; i++) {
            if (arguments[i] instanceof String) {
                arguments[i] = fromJavaStringNode.execute((String) arguments[i], TS_ENCODING);
            }
        }
    }

    @GenerateInline
    @GenerateUncached
    @GenerateCached(false)
    public abstract static class Lazy extends Node {
        public static Lazy getUncached() {
            return LazyNodeGen.getUncached();
        }

        public final PRaiseNode get(Node inliningTarget) {
            return execute(inliningTarget);
        }

        abstract PRaiseNode execute(Node inliningTarget);

        @Specialization
        static PRaiseNode doIt(@Cached(inline = false) PRaiseNode node) {
            return node;
        }
    }
}
