/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.arrow.vector;

import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

@GenerateCached(false)
@GenerateInline
public abstract class GetFormatFromVectorNode extends PNodeWithContext {

    public abstract byte execute(Node inliningTarget, Object hostVector);

    static final String INT8_PACKAGE_PATH = "org.apache.arrow.vector.TinyIntVector";
    static final String INT16_PACKAGE_PATH = "org.apache.arrow.vector.SmallIntVector";
    static final String INT32_PACKAGE_PATH = "org.apache.arrow.vector.IntVector";
    static final String INT64_PACKAGE_PATH = "org.apache.arrow.vector.BigIntVector";
    static final String BOOLEAN_PACKAGE_PATH = "org.apache.arrow.vector.BitVector";
    static final String FLOAT2_PACKAGE_PATH = "org.apache.arrow.vector.Float2Vector";
    static final String FLOAT4_PACKAGE_PATH = "org.apache.arrow.vector.Float4Vector";
    static final String FLOAT8_PACKAGE_PATH = "org.apache.arrow.vector.Float8Vector";

    @Specialization(guards = "int32VectorClass.isInstance(hostVector)", limit = "1")
    static byte doInt32(Object hostVector,
                    @Cached(value = "getClass(INT32_PACKAGE_PATH)") Class<?> int32VectorClass) {
        // i = 105
        return 105;
    }

    @Specialization(guards = "int64VectorClass.isInstance(hostVector)", limit = "1")
    static byte doInt64(Object hostVector,
                    @Cached(value = "getClass(INT64_PACKAGE_PATH)") Class<?> int64VectorClass) {
        // l = 108
        return 108;
    }

    @Specialization(guards = "float4VectorClass.isInstance(hostVector)", limit = "1")
    static byte doFloat4(Object hostVector,
                    @Cached(value = "getClass(FLOAT4_PACKAGE_PATH)") Class<?> float4VectorClass) {
        // f = 102
        return 102;
    }

    @Specialization(guards = "float8VectorClass.isInstance(hostVector)", limit = "1")
    static byte doFloat8(Object hostVector,
                    @Cached(value = "getClass(FLOAT8_PACKAGE_PATH)") Class<?> float8VectorClass) {
        // g = 103
        return 103;
    }

    @Specialization(guards = "int16VectorClass.isInstance(hostVector)", limit = "1")
    static byte doInt16(Object hostVector,
                    @Cached(value = "getClass(INT16_PACKAGE_PATH)") Class<?> int16VectorClass) {
        // s = 115
        return 115;
    }

    @Specialization(guards = "int8VectorClass.isInstance(hostVector)", limit = "1")
    static byte doInt8(Object hostVector,
                    @Cached(value = "getClass(INT8_PACKAGE_PATH)") Class<?> int8VectorClass) {
        // c = 99
        return 99;
    }

    @Specialization(guards = "boolVectorClass.isInstance(hostVector)", limit = "1")
    static byte doBoolean(Object hostVector,
                    @Cached(value = "getClass(BOOLEAN_PACKAGE_PATH)") Class<?> boolVectorClass) {
        // b = 98
        return 98;
    }

    @Specialization(guards = "float2VectorClass.isInstance(hostVector)", limit = "1")
    static byte doFloat2(Object hostVector,
                    @Cached(value = "getClass(FLOAT2_PACKAGE_PATH)") Class<?> float2VectorClass) {
        // e = 101
        return 101;
    }

    @Fallback
    static byte doError(Object obj) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    static Class<?> getClass(String path) {
        try {
            return Class.forName(path);
        } catch (ClassNotFoundException e) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }
}
