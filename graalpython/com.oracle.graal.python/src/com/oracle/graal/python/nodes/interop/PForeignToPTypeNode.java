/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
/**
 *
 */
package com.oracle.graal.python.nodes.interop;

import com.oracle.graal.python.nodes.PGuards;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

@ImportStatic(PGuards.class)
@GenerateUncached
public abstract class PForeignToPTypeNode extends Node {

    public abstract Object executeConvert(Object value);

    protected static boolean isOtherClass(Class<?> clazz) {
        return !(clazz == Byte.class || clazz == Short.class || clazz == Float.class || clazz == Character.class);
    }

    @Specialization(guards = {"value.getClass() == cachedClass", "isOtherClass(cachedClass)"}, limit = "1")
    protected static Object fromObjectCached(Object value,
                    @Cached("value.getClass()") @SuppressWarnings("unused") Class<?> cachedClass) {
        return value;
    }

    @Specialization
    protected static int fromByte(byte value) {
        return value;
    }

    @Specialization
    protected static int fromShort(short value) {
        return value;
    }

    @Specialization
    protected static double fromFloat(float value) {
        return value;
    }

    @Specialization
    protected static String fromChar(char value) {
        return String.valueOf(value);
    }

    @Fallback
    protected static Object fromObjectGeneric(Object value) {
        return value;
    }

    public static PForeignToPTypeNode create() {
        return PForeignToPTypeNodeGen.create();
    }
}
