/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.function.builtins.clinic;

import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;

public abstract class ArgumentClinicProvider {
    public static final ArgumentClinicProvider NOOP = new NoopArgumentClinic();

    /**
     * The variants of the 'accept' method serve as a fast-path check, without allocating any extra
     * node, the result should be a compilation constant provided that argIndex is a compilation
     * constant.
     */
    public boolean acceptsBoolean(@SuppressWarnings("unused") int argIndex) {
        return false;
    }

    public boolean acceptsInt(@SuppressWarnings("unused") int argIndex) {
        return false;
    }

    public boolean acceptsLong(@SuppressWarnings("unused") int argIndex) {
        return false;
    }

    public boolean acceptsDouble(@SuppressWarnings("unused") int argIndex) {
        return false;
    }

    /**
     * Creates a cast node for given argument index. Should be called only if
     * {@link #hasCastNode(int)} returned {@code true} for given index.
     */
    public ArgumentCastNode createCastNode(@SuppressWarnings("unused") int argIndex, @SuppressWarnings("unused") PythonBuiltinBaseNode builtin) {
        throw new IllegalStateException("createCastNode should not be called unless hasCastNode returns true");
    }

    /**
     * Fast-path check if given argument has a cast node associated with it. The result should be a
     * compilation constant given that the argument is constant.
     */
    public boolean hasCastNode(@SuppressWarnings("unused") int argIndex) {
        return false;
    }

    private static final class NoopArgumentClinic extends ArgumentClinicProvider {
        @Override
        public boolean acceptsBoolean(@SuppressWarnings("unused") int argIndex) {
            return true;
        }

        @Override
        public boolean acceptsInt(@SuppressWarnings("unused") int argIndex) {
            return true;
        }

        @Override
        public boolean acceptsLong(@SuppressWarnings("unused") int argIndex) {
            return true;
        }

        @Override
        public boolean acceptsDouble(@SuppressWarnings("unused") int argIndex) {
            return true;
        }
    }
}
