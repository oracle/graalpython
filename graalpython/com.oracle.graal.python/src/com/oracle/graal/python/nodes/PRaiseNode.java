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
package com.oracle.graal.python.nodes;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.CachedLanguage;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.EncapsulatingNodeReference;
import com.oracle.truffle.api.nodes.Node;

@ImportStatic(PGuards.class)
@GenerateUncached
@ReportPolymorphism
public abstract class PRaiseNode extends Node {

    public abstract PException execute(Object type, Object cause, Object format, Object[] arguments);

    public final PException raise(PythonBuiltinClassType type, String format, Object... arguments) {
        throw execute(type, PNone.NO_VALUE, format, arguments);
    }

    public final PException raise(PythonBuiltinClassType type, Object... arguments) {
        throw execute(type, PNone.NO_VALUE, PNone.NO_VALUE, arguments);
    }

    public final PException raise(PythonBuiltinClassType type, Exception e) {
        throw execute(type, PNone.NO_VALUE, getMessage(e), new Object[0]);
    }

    /**
     * Raise an error saying that the {@code result} cannot fit into an index-sized integer. Use the
     * specified {@code type} as exception class.
     */
    public final PException raiseNumberTooLarge(Object type, Object result) {
        return execute(type, PNone.NO_VALUE, "cannot fit '%p' into an index-sized integer", new Object[]{result});
    }

    public final PException raiseHasNoLength(Object result) {
        return raise(PythonBuiltinClassType.TypeError, ErrorMessages.OBJ_HAS_NO_LEN, result);
    }

    public final PException raiseIntegerInterpretationError(Object result) {
        return raise(PythonBuiltinClassType.TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, result);
    }

    public final PException raise(Object exceptionType) {
        throw execute(exceptionType, PNone.NO_VALUE, PNone.NO_VALUE, new Object[0]);
    }

    public final PException raise(PythonBuiltinClassType type, PBaseException cause, String format, Object... arguments) {
        throw execute(type, cause, format, arguments);
    }

    public final PException raiseExceptionObject(PBaseException exc, PythonLanguage language) {
        throw raise(this, exc, PythonOptions.isPExceptionWithJavaStacktrace(language));
    }

    public static PException raise(Node raisingNode, PBaseException exc, boolean withJavaStacktrace) {
        exc.ensureReified();
        if (raisingNode.isAdoptable()) {
            throw PException.fromObject(exc, raisingNode, withJavaStacktrace);
        } else {
            throw PException.fromObject(exc, EncapsulatingNodeReference.getCurrent().get(), withJavaStacktrace);
        }
    }

    @Specialization(guards = {"isNoValue(cause)", "isNoValue(format)", "arguments.length == 0", "exceptionType == cachedType"}, limit = "8")
    PException doPythonBuiltinTypeCached(@SuppressWarnings("unused") PythonBuiltinClassType exceptionType, @SuppressWarnings("unused") PNone cause, @SuppressWarnings("unused") PNone format,
                    @SuppressWarnings("unused") Object[] arguments,
                    @Cached("exceptionType") PythonBuiltinClassType cachedType,
                    @Cached PythonObjectFactory factory,
                    @CachedLanguage PythonLanguage language) {
        throw raiseExceptionObject(factory.createBaseException(cachedType), language);
    }

    @Specialization(guards = {"isNoValue(cause)", "isNoValue(format)", "arguments.length == 0"}, replaces = "doPythonBuiltinTypeCached")
    PException doPythonBuiltinType(PythonBuiltinClassType exceptionType, @SuppressWarnings("unused") PNone cause, @SuppressWarnings("unused") PNone format,
                    @SuppressWarnings("unused") Object[] arguments,
                    @Shared("factory") @Cached PythonObjectFactory factory,
                    @Shared("language") @CachedLanguage PythonLanguage language) {
        throw raiseExceptionObject(factory.createBaseException(exceptionType), language);
    }

    protected static Assumption singleContextAssumption() {
        return PythonLanguage.getCurrent().singleContextAssumption;
    }

    @Specialization(guards = {"isNoValue(cause)", "isNoValue(format)", "arguments.length == 0", "exceptionType == cachedType"}, limit = "3", assumptions = "singleContextAssumption()")
    PException doPythonBuiltinClassCached(@SuppressWarnings("unused") PythonBuiltinClass exceptionType, @SuppressWarnings("unused") PNone cause, @SuppressWarnings("unused") PNone format,
                    @SuppressWarnings("unused") Object[] arguments,
                    @Cached("exceptionType") PythonBuiltinClass cachedType,
                    @Cached PythonObjectFactory factory,
                    @CachedLanguage PythonLanguage language) {
        throw raiseExceptionObject(factory.createBaseException(cachedType), language);
    }

    @Specialization(guards = {"isNoValue(cause)", "isNoValue(format)", "arguments.length == 0", "exceptionType.getType() == cachedType"}, limit = "3")
    PException doPythonBuiltinClassCachedMulti(@SuppressWarnings("unused") PythonBuiltinClass exceptionType, @SuppressWarnings("unused") PNone cause, @SuppressWarnings("unused") PNone format,
                    @SuppressWarnings("unused") Object[] arguments,
                    @Cached("exceptionType.getType()") PythonBuiltinClassType cachedType,
                    @Cached PythonObjectFactory factory,
                    @CachedLanguage PythonLanguage language) {
        throw raiseExceptionObject(factory.createBaseException(cachedType), language);
    }

    @Specialization(guards = {"isNoValue(cause)", "isNoValue(format)", "arguments.length == 0"}, replaces = {"doPythonBuiltinClassCached", "doPythonBuiltinClassCachedMulti"})
    PException doPythonBuiltinClass(PythonBuiltinClass exceptionType, @SuppressWarnings("unused") PNone cause, @SuppressWarnings("unused") PNone format, @SuppressWarnings("unused") Object[] arguments,
                    @Shared("factory") @Cached PythonObjectFactory factory,
                    @Shared("language") @CachedLanguage PythonLanguage language) {
        throw raiseExceptionObject(factory.createBaseException(exceptionType), language);
    }

    @Specialization(guards = {"isNoValue(cause)"})
    PException doBuiltinClass(PythonBuiltinClass exceptionType, @SuppressWarnings("unused") PNone cause, String format, Object[] arguments,
                    @Shared("factory") @Cached PythonObjectFactory factory,
                    @Shared("language") @CachedLanguage PythonLanguage language) {
        assert format != null;
        throw doBuiltinType(exceptionType.getType(), cause, format, arguments, factory, language);
    }

    @Specialization(guards = {"isNoValue(cause)", "isNoValue(format)", "arguments.length == 0"})
    PException doPythonManagedClass(PythonManagedClass exceptionType, @SuppressWarnings("unused") PNone cause, @SuppressWarnings("unused") PNone format, @SuppressWarnings("unused") Object[] arguments,
                    @Shared("factory") @Cached PythonObjectFactory factory,
                    @Shared("language") @CachedLanguage PythonLanguage language) {
        throw raiseExceptionObject(factory.createBaseException(exceptionType), language);
    }

    @Specialization(guards = {"isNoValue(cause)", "isNoValue(format)", "arguments.length > 0"})
    PException doBuiltinType(PythonBuiltinClassType type, @SuppressWarnings("unused") PNone cause, @SuppressWarnings("unused") PNone format, Object[] arguments,
                    @Shared("factory") @Cached PythonObjectFactory factory,
                    @Shared("language") @CachedLanguage PythonLanguage language) {
        throw raiseExceptionObject(factory.createBaseException(type, factory.createTuple(arguments)), language);
    }

    @Specialization(guards = {"isNoValue(cause)"})
    PException doBuiltinType(PythonBuiltinClassType type, @SuppressWarnings("unused") PNone cause, String format, Object[] arguments,
                    @Shared("factory") @Cached PythonObjectFactory factory,
                    @Shared("language") @CachedLanguage PythonLanguage language) {
        assert format != null;
        throw raiseExceptionObject(factory.createBaseException(type, format, arguments), language);
    }

    @Specialization(guards = {"!isNoValue(cause)"})
    PException doBuiltinTypeWithCause(PythonBuiltinClassType type, PBaseException cause, String format, Object[] arguments,
                    @Shared("factory") @Cached PythonObjectFactory factory,
                    @Shared("language") @CachedLanguage PythonLanguage language) {
        assert format != null;
        PBaseException baseException = factory.createBaseException(type, format, arguments);
        baseException.setContext(cause);
        baseException.setCause(cause);
        throw raiseExceptionObject(baseException, language);
    }

    @TruffleBoundary
    private static String getMessage(Exception e) {
        String msg = e.getMessage();
        return msg != null ? msg : e.getClass().getSimpleName();
    }

    public static PRaiseNode create() {
        return PRaiseNodeGen.create();
    }

    public static PRaiseNode getUncached() {
        return PRaiseNodeGen.getUncached();
    }
}
