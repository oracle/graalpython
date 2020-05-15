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
package com.oracle.graal.python.nodes.util;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import com.oracle.graal.python.builtins.modules.MathGuards;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CastToJavaIntNodeGen.CastToJavaIntExactNodeGen;
import com.oracle.graal.python.nodes.util.CastToJavaIntNodeGen.CastToJavaIntLossyNodeGen;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;

@TypeSystemReference(PythonArithmeticTypes.class)
@ImportStatic(MathGuards.class)
public abstract class CastToJavaIntNode extends PNodeWithContext {

    public abstract int execute(int x);

    public abstract int execute(long x);

    public abstract int execute(Object x);

    protected int toIntInternal(@SuppressWarnings("unused") long x) {
        CompilerAsserts.neverPartOfCompilation();
        throw new IllegalStateException("should not be reached");
    }

    protected int toIntInternal(@SuppressWarnings("unused") PInt x) {
        CompilerAsserts.neverPartOfCompilation();
        throw new IllegalStateException("should not be reached");
    }

    public static CastToJavaIntNode create() {
        return CastToJavaIntExactNodeGen.create();
    }

    public static CastToJavaIntNode createLossy() {
        return CastToJavaIntLossyNodeGen.create();
    }

    public static CastToJavaIntNode getUncached() {
        return CastToJavaIntExactNodeGen.getUncached();
    }

    public static CastToJavaIntNode getLossyUncached() {
        return CastToJavaIntLossyNodeGen.getUncached();
    }

    @Specialization
    public int toInt(int x) {
        return x;
    }

    @Specialization
    public int toInt(long x) {
        return toIntInternal(x);
    }

    @Specialization
    public int toInt(PInt x) {
        return toIntInternal(x);
    }

    @Fallback
    static int doUnsupported(@SuppressWarnings("unused") Object x) {
        throw CannotCastException.INSTANCE;
    }

    @GenerateUncached
    abstract static class CastToJavaIntLossyNode extends CastToJavaIntNode {
        @Override
        protected int toIntInternal(long x) {
            return (int) x;
        }

        @Override
        protected int toIntInternal(PInt x) {
            return x.intValue();
        }
    }

    @GenerateUncached
    abstract static class CastToJavaIntExactNode extends CastToJavaIntNode {
        @Override
        protected int toIntInternal(long x) {
            try {
                return PInt.intValueExact(x);
            } catch (ArithmeticException e) {
                CompilerDirectives.transferToInterpreter();
                throw PRaiseNode.getUncached().raise(TypeError, "value too large to find into index-sized integer");
            }
        }

        @Override
        protected int toIntInternal(PInt x) {
            try {
                return x.intValueExact();
            } catch (ArithmeticException e) {
                CompilerDirectives.transferToInterpreter();
                throw PRaiseNode.getUncached().raise(TypeError, "%s cannot be interpreted as int (type %p)", x, x);
            }
        }
    }
}
