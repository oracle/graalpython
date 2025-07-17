/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.exception.AttributeErrorBuiltins;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.lib.PyExceptionInstanceCheckNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.EncapsulatingNodeReference;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.strings.TruffleString;

@GenerateInline
@GenerateCached(false)
@GenerateUncached
public abstract class PRaiseNode extends Node {

    protected abstract void executeEnterProfile(Node inliningTarget);

    @Specialization
    static void doProfile(Node inliningTarget,
                    @Cached InlinedBranchProfile profile) {
        profile.enter(inliningTarget);
    }

    public final PException raise(Node inliningTarget, PythonBuiltinClassType type) {
        executeEnterProfile(inliningTarget);
        throw raiseStatic(inliningTarget, type);
    }

    public static PException raiseStatic(Node node, PythonBuiltinClassType type) {
        PythonLanguage language = PythonLanguage.get(node);
        PBaseException pythonException = PFactory.createBaseException(language, type);
        throw raiseExceptionObject(node, pythonException, language);
    }

    public final PException raise(Node inliningTarget, PythonBuiltinClassType type, TruffleString message) {
        executeEnterProfile(inliningTarget);
        throw raiseStatic(inliningTarget, type, message);
    }

    public static PException raiseStatic(Node node, PythonBuiltinClassType type, TruffleString message) {
        PythonLanguage language = PythonLanguage.get(node);
        PBaseException pythonException = PFactory.createBaseException(language, type, message);
        throw raiseExceptionObject(node, pythonException, language);
    }

    public final PException raise(Node inliningTarget, PythonBuiltinClassType type, TruffleString format, Object... formatArgs) {
        executeEnterProfile(inliningTarget);
        throw raiseStatic(inliningTarget, type, format, formatArgs);
    }

    public static PException raiseStatic(Node node, PythonBuiltinClassType type, TruffleString message, Object... formatArgs) {
        PythonLanguage language = PythonLanguage.get(node);
        PBaseException pythonException = PFactory.createBaseException(language, type, message, formatArgs);
        throw raiseExceptionObject(node, pythonException, language);
    }

    public final PException raise(Node inliningTarget, PythonBuiltinClassType type, Object[] arguments) {
        executeEnterProfile(inliningTarget);
        throw raiseStatic(inliningTarget, type, arguments);
    }

    public static PException raiseStatic(Node node, PythonBuiltinClassType type, Object[] arguments) {
        PythonLanguage language = PythonLanguage.get(node);
        PBaseException pythonException = PFactory.createBaseException(language, type, PFactory.createTuple(language, arguments));
        throw raiseExceptionObject(node, pythonException, language);
    }

    public final PException raiseWithData(Node inliningTarget, PythonBuiltinClassType type, Object[] data) {
        executeEnterProfile(inliningTarget);
        throw raiseWithDataStatic(inliningTarget, type, data);
    }

    public static PException raiseWithDataStatic(Node node, PythonBuiltinClassType type, Object[] data) {
        PythonLanguage language = PythonLanguage.get(node);
        PBaseException pythonException = PFactory.createBaseException(language, type, data, null);
        throw raiseExceptionObject(node, pythonException, language);
    }

    public final PException raiseAttributeError(Node inliningTarget, Object obj, Object key) {
        executeEnterProfile(inliningTarget);
        throw raiseAttributeErrorStatic(inliningTarget, obj, key);
    }

    public static PException raiseAttributeErrorStatic(Node inliningTarget, Object obj, Object key) {
        throw raiseWithDataStatic(inliningTarget, PythonBuiltinClassType.AttributeError, AttributeErrorBuiltins.dataForObjKey(obj, key), ErrorMessages.OBJ_P_HAS_NO_ATTR_S, obj, key);
    }

    public final PException raiseWithData(Node inliningTarget, PythonBuiltinClassType type, Object[] data, TruffleString format, Object... formatArgs) {
        executeEnterProfile(inliningTarget);
        throw raiseWithDataStatic(inliningTarget, type, data, format, formatArgs);
    }

    public static PException raiseWithDataStatic(Node node, PythonBuiltinClassType type, Object[] data, TruffleString format, Object... formatArgs) {
        PythonLanguage language = PythonLanguage.get(node);
        PBaseException pythonException = PFactory.createBaseException(language, type, data, format, formatArgs);
        throw raiseExceptionObject(node, pythonException, language);
    }

    public final PException raiseWithData(Node inliningTarget, PythonBuiltinClassType type, Object[] data, Object... arguments) {
        executeEnterProfile(inliningTarget);
        throw raiseWithDataStatic(inliningTarget, type, data, arguments);
    }

    public static PException raiseWithDataStatic(Node node, PythonBuiltinClassType type, Object[] data, Object[] arguments) {
        PythonLanguage language = PythonLanguage.get(node);
        PBaseException pythonException = PFactory.createBaseException(language, type, data, PFactory.createTuple(language, arguments));
        throw raiseExceptionObject(node, pythonException, language);
    }

    public final PException raise(Node inliningTarget, PythonBuiltinClassType type, Exception e) {
        executeEnterProfile(inliningTarget);
        throw raiseStatic(inliningTarget, type, e);
    }

    public static PException raiseStatic(Node node, PythonBuiltinClassType type, Exception e) {
        PythonLanguage language = PythonLanguage.get(node);
        PBaseException pythonException = PFactory.createBaseException(language, type, ErrorMessages.M, new Object[]{e});
        throw raiseExceptionObject(node, pythonException, language);
    }

    private static void setCause(PBaseException pythonException, PException cause) {
        // _PyErr_FormatFromCause sets both cause and context
        Object causePythonException = cause.getEscapedException();
        pythonException.setCause(causePythonException);
        pythonException.setContext(causePythonException);
    }

    public final PException raiseWithCause(Node inliningTarget, PythonBuiltinClassType type, PException cause, TruffleString format) {
        executeEnterProfile(inliningTarget);
        throw raiseWithCauseStatic(inliningTarget, type, cause, format);
    }

    public static PException raiseWithCauseStatic(Node node, PythonBuiltinClassType type, PException cause, TruffleString format) {
        PythonLanguage language = PythonLanguage.get(node);
        PBaseException pythonException = PFactory.createBaseException(language, type, format);
        setCause(pythonException, cause);
        throw raiseExceptionObject(node, pythonException, language);
    }

    public final PException raiseWithCause(Node inliningTarget, PythonBuiltinClassType type, PException cause, TruffleString format, Object... arguments) {
        assert PyExceptionInstanceCheckNode.executeUncached(cause);
        executeEnterProfile(inliningTarget);
        throw raiseWithCauseStatic(inliningTarget, type, cause, format, arguments);
    }

    public static PException raiseWithCauseStatic(Node node, PythonBuiltinClassType type, PException cause, TruffleString format, Object... formatArgs) {
        assert PyExceptionInstanceCheckNode.executeUncached(cause);
        PythonLanguage language = PythonLanguage.get(node);
        PBaseException pythonException = PFactory.createBaseException(language, type, format, formatArgs);
        setCause(pythonException, cause);
        throw raiseExceptionObject(node, pythonException, language);
    }

    public final PException raiseOverflow(Node inliningTarget) {
        throw raise(inliningTarget, OverflowError, ErrorMessages.CANNOT_FIT_P_INTO_INDEXSIZED_INT, 0);
    }

    public static PException raiseSystemExitStatic(Node inliningTarget, Object code) {
        throw raiseWithDataStatic(inliningTarget, PythonBuiltinClassType.SystemExit, new Object[]{code}, new Object[]{code});
    }

    public final PException raiseStopIteration(Node inliningTarget, Object value) {
        final Object retVal = value != null ? value : PNone.NONE;
        final Object[] args = {retVal};
        throw raiseWithData(inliningTarget, PythonBuiltinClassType.StopIteration, args, retVal);
    }

    public final PException raiseStopAsyncIteration(Node inliningTarget, Object value) {
        final Object retVal = value != null ? value : PNone.NONE;
        final Object[] args = {retVal};
        throw raiseWithData(inliningTarget, PythonBuiltinClassType.StopAsyncIteration, args, retVal);
    }

    public final PException raiseBadInternalCall(Node inliningTarget) {
        throw raise(inliningTarget, PythonBuiltinClassType.SystemError, BAD_ARG_TO_INTERNAL_FUNC);
    }

    public static PException raiseExceptionObject(Node raisingNode, Object exc) {
        throw raiseExceptionObject(raisingNode, exc, PythonOptions.isPExceptionWithJavaStacktrace(PythonLanguage.get(raisingNode)));
    }

    public static PException raiseExceptionObject(Node raisingNode, Object exc, PythonLanguage language) {
        throw raiseExceptionObject(raisingNode, exc, PythonOptions.isPExceptionWithJavaStacktrace(language));
    }

    public static PException raiseExceptionObject(Node raisingNode, Object exc, boolean withJavaStacktrace) {
        if (raisingNode != null && raisingNode.isAdoptable()) {
            throw PException.fromObject(exc, raisingNode, withJavaStacktrace);
        } else {
            throw PException.fromObject(exc, EncapsulatingNodeReference.getCurrent().get(), withJavaStacktrace);
        }
    }

    public static PRaiseNode getUncached() {
        return PRaiseNodeGen.getUncached();
    }
}
