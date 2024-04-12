/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.lib;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OverflowError;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.nodes.util.CastToJavaIntLossyNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

/**
 * Equivalent of CPython's {@code PyNumber_AsSize} function. Converts the argument into integer (our
 * equivalent of {@code Py_ssize_t}) using its {@code __index__} method. Raises a {@code TypeError}
 * if the argument is not convertible to integer. Overflow behavior depends on the execute method
 * used:
 * <ul>
 * <li>{@link #executeLossy(Frame, Node, Object)} clamps the value to maximal/minimal integer
 * (equivalent of passing {@code NULL} in CPython).
 * <li>{@link #executeExact(Frame, Node, Object)} raises {@code OverflowError} on overflow.
 * <li>{@link #executeExact(Frame, Node, Object, PythonBuiltinClassType)} raises the supplied
 * exception type on overflow.
 * </ul>
 */
@GenerateUncached
@GenerateCached
@GenerateInline(inlineByDefault = true)
public abstract class PyNumberAsSizeNode extends PNodeWithContext {
    public final int executeLossy(Frame frame, Node inliningTarget, Object object) {
        return execute(frame, inliningTarget, object, PNone.NO_VALUE);
    }

    /**
     * Attention: only to be used with a cached variant of this node. Prefer the inline variant (the
     * default when used as @Cached).
     */
    public final int executeExactCached(Frame frame, Object object) {
        return execute(frame, this, object, OverflowError);
    }

    public final int executeExact(Frame frame, Node inliningTarget, Object object) {
        return execute(frame, inliningTarget, object, OverflowError);
    }

    public static int executeExactUncached(Object object) {
        return getUncached().executeExact(null, null, object);
    }

    /**
     * Attention: only to be used with a cached variant of this node. Prefer the inline variant (the
     * default when used as @Cached).
     */
    public final int executeExact(Frame frame, Object object, PythonBuiltinClassType errorClass) {
        return execute(frame, null, object, errorClass);
    }

    public final int executeExact(Frame frame, Node inliningTarget, Object object, PythonBuiltinClassType errorClass) {
        return execute(frame, inliningTarget, object, errorClass);
    }

    protected abstract int execute(Frame frame, Node inliningTarget, Object object, Object errorClass);

    @Specialization
    static int doInt(int object, @SuppressWarnings("unused") Object errorClass) {
        return object;
    }

    @Specialization
    public static int doLongExact(Node inliningTarget, long object, PythonBuiltinClassType errorClass,
                    @Cached PRaiseNode.Lazy raiseNode) {
        int converted = (int) object;
        if (object == converted) {
            return converted;
        }
        throw raiseNode.get(inliningTarget).raise(errorClass, ErrorMessages.CANNOT_FIT_P_INTO_INDEXSIZED_INT, object);
    }

    @Specialization
    static int doLongLossy(long object, @SuppressWarnings("unused") PNone errorClass) {
        int converted = (int) object;
        if (object == converted) {
            return converted;
        }
        return object > 0 ? Integer.MAX_VALUE : Integer.MIN_VALUE;
    }

    @Fallback
    @InliningCutoff
    static int doObject(Frame frame, Object object, Object errorClass,
                    @Cached(inline = false) PyNumberAsSizeObjectNode node) {
        return node.execute(frame, object, errorClass);
    }

    @GenerateInline(false) // used lazily
    @GenerateUncached
    abstract static class PyNumberAsSizeObjectNode extends Node {
        protected abstract int execute(Frame frame, Object object, Object errorClass);

        @Specialization
        static int doObjectExact(VirtualFrame frame, Object object, PythonBuiltinClassType errorClass,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached PyNumberIndexNode indexNode,
                        @Cached PRaiseNode.Lazy raiseNode,
                        @Cached CastToJavaIntExactNode cast) {
            Object index = indexNode.execute(frame, inliningTarget, object);
            try {
                return cast.execute(inliningTarget, index);
            } catch (PException pe) {
                throw raiseNode.get(inliningTarget).raise(errorClass, ErrorMessages.CANNOT_FIT_P_INTO_INDEXSIZED_INT, object);
            } catch (CannotCastException cannotCastException) {
                throw CompilerDirectives.shouldNotReachHere("PyNumberIndexNode didn't return a python integer");
            }
        }

        @Specialization
        static int doObjectLossy(VirtualFrame frame, Object object, @SuppressWarnings("unused") PNone errorClass,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached PyNumberIndexNode indexNode,
                        @Cached CastToJavaIntLossyNode cast) {
            Object index = indexNode.execute(frame, inliningTarget, object);
            try {
                return cast.execute(inliningTarget, index);
            } catch (CannotCastException cannotCastException) {
                throw CompilerDirectives.shouldNotReachHere("PyNumberIndexNode didn't return a python integer");
            }
        }
    }

    @NeverDefault
    public static PyNumberAsSizeNode create() {
        return PyNumberAsSizeNodeGen.create();
    }

    public static PyNumberAsSizeNode getUncached() {
        return PyNumberAsSizeNodeGen.getUncached();
    }
}
