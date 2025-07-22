/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.annotations.ArgumentClinic.PrimitiveType;
import com.oracle.graal.python.annotations.ClinicConverterFactory;
import com.oracle.graal.python.annotations.ClinicConverterFactory.DefaultValue;
import com.oracle.graal.python.annotations.ClinicConverterFactory.UseDefaultForNone;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.lib.PyLongAsIntNode;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

/**
 * Implements the {@code bool(accept=int)} argument clinic conversion.
 */
public abstract class JavaIntToBooleanConverterNode extends ArgumentCastNode {
    protected final boolean defaultValue;
    protected final boolean useDefaultForNone;

    protected JavaIntToBooleanConverterNode(boolean defaultValue, boolean useDefaultForNone) {
        this.defaultValue = defaultValue;
        this.useDefaultForNone = useDefaultForNone;
    }

    @Specialization(guards = {"!useDefaultForNone", "isNoValue(none)"})
    boolean doNoValue(@SuppressWarnings("unused") PNone none) {
        return defaultValue;
    }

    @Specialization(guards = {"!useDefaultForNone", "isNone(none)"})
    static boolean doNone(@SuppressWarnings("unused") PNone none) {
        return false;
    }

    @Specialization(guards = "useDefaultForNone")
    boolean doNoValueAndNone(@SuppressWarnings("unused") PNone none) {
        return defaultValue;
    }

    @Specialization
    static boolean doBoolean(boolean b) {
        return b;
    }

    @Specialization
    static boolean doInt(int i) {
        return i != 0;
    }

    @Specialization
    static boolean doLong(long i) {
        return i != 0;
    }

    @Specialization(guards = "!isNoValue(value)")
    static Object doOthers(VirtualFrame frame, Object value,
                    @Bind Node inliningTarget,
                    @Cached PyLongAsIntNode asIntNode) {
        return asIntNode.execute(frame, inliningTarget, value) != 0;
    }

    @ClinicConverterFactory(shortCircuitPrimitive = PrimitiveType.Boolean)
    @NeverDefault
    public static JavaIntToBooleanConverterNode create(@UseDefaultForNone boolean useDefaultForNone, @DefaultValue boolean defaultValue) {
        if (!defaultValue) {
            // If default value is false, it's the same as useDefaultForNone, which needs to do
            // fewer checks
            return JavaIntToBooleanConverterNodeGen.create(false, true);
        } else {
            return JavaIntToBooleanConverterNodeGen.create(defaultValue, useDefaultForNone);
        }
    }

    @ClinicConverterFactory(shortCircuitPrimitive = PrimitiveType.Boolean)
    @NeverDefault
    public static JavaIntToBooleanConverterNode create(@UseDefaultForNone boolean useDefaultForNone) {
        assert !useDefaultForNone : "defaultValue must be provided if useDefaultForNone is true";
        return JavaIntToBooleanConverterNode.create(false, false);
    }
}
