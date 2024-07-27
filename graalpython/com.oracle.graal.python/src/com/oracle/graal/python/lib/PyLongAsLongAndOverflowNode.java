/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.objects.cext.PythonNativeVoidPtr;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

/**
 * Equivalent of CPython's {@code PyLong_AsLongAndOverflow}. Converts an object into a Java long
 * using it's {@code __index__} or (deprecated) {@code __int__} method. In CPython the overflow is
 * communicated through an output variable, we communicate it using a checked exception
 * {@link com.oracle.graal.python.util.OverflowException}.
 */
@GenerateUncached
@GenerateInline(inlineByDefault = true)
@GenerateCached(false)
@ImportStatic(SpecialMethodSlot.class)
public abstract class PyLongAsLongAndOverflowNode extends PNodeWithContext {

    public abstract long execute(Frame frame, Node inliningTarget, Object object) throws OverflowException;

    // Attention: keep these Specializations in synch with BuiltinPyLongAsLongAndOverflowNode!
    // DSL does not allow reusing BuiltinPyLongAsLongAndOverflowNode here

    @Specialization
    static long doInt(int object) {
        return object;
    }

    @Specialization
    static long doLong(long object) {
        return object;
    }

    @Specialization
    static long doPInt(PInt object) throws OverflowException {
        return object.longValueExact();
    }

    @Specialization
    static long doBoolean(boolean x) {
        return x ? 1 : 0;
    }

    @Specialization
    static long doNativePointer(PythonNativeVoidPtr object) {
        return object.getNativePointer();
    }

    // TODO When we implement casting native longs, this should cast them instead of calling their
    // __index__
    @Fallback
    @InliningCutoff
    static long doObject(VirtualFrame frame, Node inliningTarget, Object object,
                    @Cached PyNumberIndexNode indexNode,
                    @Cached BuiltinPyLongAsLongAndOverflowNode recursive) throws OverflowException {
        Object result = indexNode.execute(frame, inliningTarget, object);
        // PyNumberIndexNode guarantees that the result is a builtin integer
        assert PyLongCheckExactNode.executeUncached(result);
        return recursive.execute(frame, inliningTarget, result);
    }

    /**
     * Internal variant of {@link PyLongAsLongAndOverflowNode} that accepts only builtin integers.
     * Truffle DSL does not allow us to (easily) reuse this code in
     * {@link PyLongAsLongAndOverflowNode}.
     */
    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    abstract static class BuiltinPyLongAsLongAndOverflowNode extends PNodeWithContext {
        public abstract long execute(Frame frame, Node inliningTarget, Object object) throws OverflowException;

        @Specialization
        static long doInt(int object) {
            return object;
        }

        @Specialization
        static long doLong(long object) {
            return object;
        }

        @Specialization
        static long doPInt(PInt object) throws OverflowException {
            return object.longValueExact();
        }

        @Specialization
        static long doBoolean(boolean x) {
            return x ? 1 : 0;
        }

        @Specialization
        static long doNativePointer(PythonNativeVoidPtr object) {
            return object.getNativePointer();
        }
    }
}
