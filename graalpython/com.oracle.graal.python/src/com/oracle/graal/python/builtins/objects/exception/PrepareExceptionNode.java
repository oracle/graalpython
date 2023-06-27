/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.exception;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsTypeNode;
import com.oracle.graal.python.lib.PyExceptionInstanceCheckNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

/**
 * Creates an exception out of type and value, similarly to CPython's
 * {@code PyErr_NormalizeException}. Returns the normalized exception.
 */
@ImportStatic(PGuards.class)
public abstract class PrepareExceptionNode extends Node {
    public abstract Object execute(VirtualFrame frame, Object type, Object value);

    @Specialization
    static Object doException(PBaseException exc, @SuppressWarnings("unused") PNone value) {
        return exc;
    }

    @Specialization(guards = "check.execute(inliningTarget, exc)", limit = "1")
    static Object doException(PythonAbstractNativeObject exc, @SuppressWarnings("unused") PNone value,
                    @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                    @SuppressWarnings("unused") @Shared @Cached PyExceptionInstanceCheckNode check) {
        return exc;
    }

    @Specialization(guards = {"check.execute(inliningTarget, exc)", "!isPNone(value)"}, limit = "1")
    static Object doException(@SuppressWarnings("unused") PBaseException exc, @SuppressWarnings("unused") Object value,
                    @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                    @SuppressWarnings("unused") @Shared @Cached PyExceptionInstanceCheckNode check,
                    @Shared @Cached PRaiseNode raiseNode) {
        throw raiseNode.raise(TypeError, ErrorMessages.INSTANCE_EX_MAY_NOT_HAVE_SEP_VALUE);
    }

    @Specialization(guards = {"isTypeNode.execute(type)", "!isPNone(value)", "!isPTuple(value)"}, limit = "1")
    static Object doExceptionOrCreate(VirtualFrame frame, Object type, Object value,
                    @Bind("this") Node inliningTarget,
                    @SuppressWarnings("unused") @Shared("isType") @Cached IsTypeNode isTypeNode,
                    @Shared @Cached PyExceptionInstanceCheckNode check,
                    @Cached BuiltinFunctions.IsInstanceNode isInstanceNode,
                    @Cached InlinedConditionProfile isInstanceProfile,
                    @Shared @Cached IsSubtypeNode isSubtypeNode,
                    @Shared @Cached PRaiseNode raiseNode,
                    @Shared("callCtor") @Cached CallNode callConstructor) {
        checkExceptionClass(type, isSubtypeNode, raiseNode);
        if (isInstanceProfile.profile(inliningTarget, isInstanceNode.executeWith(frame, value, type))) {
            return value;
        } else {
            Object instance = callConstructor.execute(frame, type, value);
            if (check.execute(inliningTarget, instance)) {
                return instance;
            } else {
                return handleInstanceNotAnException(type, instance);
            }
        }
    }

    @Specialization(guards = "isTypeNode.execute(type)", limit = "1")
    static Object doCreate(VirtualFrame frame, Object type, @SuppressWarnings("unused") PNone value,
                    @Bind("this") Node inliningTarget,
                    @SuppressWarnings("unused") @Shared("isType") @Cached IsTypeNode isTypeNode,
                    @Shared @Cached PyExceptionInstanceCheckNode check,
                    @Shared @Cached IsSubtypeNode isSubtypeNode,
                    @Shared @Cached PRaiseNode raiseNode,
                    @Shared("callCtor") @Cached CallNode callConstructor) {
        checkExceptionClass(type, isSubtypeNode, raiseNode);
        Object instance = callConstructor.execute(frame, type);
        if (check.execute(inliningTarget, instance)) {
            return instance;
        } else {
            return handleInstanceNotAnException(type, instance);
        }
    }

    @Specialization(guards = "isTypeNode.execute(type)", limit = "1")
    static Object doCreateTuple(VirtualFrame frame, Object type, PTuple value,
                    @Bind("this") Node inliningTarget,
                    @SuppressWarnings("unused") @Shared("isType") @Cached IsTypeNode isTypeNode,
                    @Shared @Cached PyExceptionInstanceCheckNode check,
                    @Cached SequenceNodes.GetObjectArrayNode getObjectArrayNode,
                    @Shared @Cached IsSubtypeNode isSubtypeNode,
                    @Shared @Cached PRaiseNode raiseNode,
                    @Shared("callCtor") @Cached CallNode callConstructor) {
        checkExceptionClass(type, isSubtypeNode, raiseNode);
        Object[] args = getObjectArrayNode.execute(inliningTarget, value);
        Object instance = callConstructor.execute(frame, type, args);
        if (check.execute(inliningTarget, instance)) {
            return instance;
        } else {
            return handleInstanceNotAnException(type, instance);
        }
    }

    @Fallback
    static Object doError(Object type, @SuppressWarnings("unused") Object value,
                    @Shared @Cached PRaiseNode raiseNode) {
        throw raiseNode.raise(TypeError, ErrorMessages.EXCEPTIONS_MUST_BE_CLASSES_OR_INSTANCES_DERIVING_FROM_BASE_EX, type);
    }

    private static PBaseException handleInstanceNotAnException(Object type, Object instance) {
        /*
         * Instead of throwing the exception here, we replace the created exception with it. This is
         * done to match CPython's behavior of `generator.throw`
         */
        return PythonObjectFactory.getUncached().createBaseException(TypeError, ErrorMessages.CALLING_N_SHOULD_HAVE_RETURNED_AN_INSTANCE_OF_BASE_EXCEPTION_NOT_P, new Object[]{type, instance});
    }

    private static void checkExceptionClass(Object type, IsSubtypeNode isSubtypeNode, PRaiseNode raiseNode) {
        if (!isSubtypeNode.execute(type, PythonBuiltinClassType.PBaseException)) {
            throw raiseNode.raise(TypeError, ErrorMessages.EXCEPTIONS_MUST_BE_CLASSES_OR_INSTANCES_DERIVING_FROM_BASE_EX, type);
        }
    }
}
