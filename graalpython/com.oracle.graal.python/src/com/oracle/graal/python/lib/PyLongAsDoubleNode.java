/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.NotImplementedError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;

import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

/**
 * Equivalent of CPython's {@code PyLong_AsDouble}. Converts an object into a Java double. Raises
 * {@code OverflowError} on overflow.
 */
@GenerateUncached
@GenerateCached(false)
@GenerateInline
public abstract class PyLongAsDoubleNode extends PNodeWithContext {
    public static double executeUncached(Object object) {
        return PyLongAsDoubleNodeGen.getUncached().execute(null, object);
    }

    public abstract double execute(Node inliningTarget, Object object);

    @Specialization
    static double doBoolean(boolean self) {
        return self ? 1.0 : 0.0;
    }

    @Specialization
    static double doInt(int self) {
        return self;
    }

    @Specialization
    static double doLong(long self) {
        return self;
    }

    @Specialization
    static double doPInt(Node inliningTarget, PInt self) {
        return self.doubleValueWithOverflow(inliningTarget);
    }

    @Specialization(guards = "check.execute(inliningTarget, self)", limit = "1")
    @InliningCutoff
    static double doNative(Node inliningTarget, @SuppressWarnings("unused") PythonAbstractNativeObject self,
                    @SuppressWarnings("unused") @Cached PyLongCheckNode check) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw PRaiseNode.raiseUncached(inliningTarget, NotImplementedError, ErrorMessages.CASTING_A_NATIVE_INT_OBJECT_IS_NOT_IMPLEMENTED_YET);
    }

    @Fallback
    @InliningCutoff
    @SuppressWarnings("unused")
    static double fallback(Node inliningTarget, Object object,
                    @Cached(inline = false) PRaiseNode raiseNode) {
        throw raiseNode.raise(TypeError, ErrorMessages.INTEGER_REQUIRED);
    }
}
