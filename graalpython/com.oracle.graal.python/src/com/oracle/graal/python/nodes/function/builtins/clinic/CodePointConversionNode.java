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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;

import com.oracle.graal.python.annotations.ClinicConverterFactory;
import com.oracle.graal.python.annotations.ClinicConverterFactory.BuiltinName;
import com.oracle.graal.python.annotations.ClinicConverterFactory.DefaultValue;
import com.oracle.graal.python.annotations.ClinicConverterFactory.UseDefaultForNone;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentCastNode.ArgumentCastNodeWithRaise;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;

public abstract class CodePointConversionNode extends ArgumentCastNodeWithRaise {
    private final String builtinName;
    private final int defaultValue;
    protected final boolean useDefaultForNone;

    public CodePointConversionNode(String builtinName, int defaultValue, boolean useDefaultForNone) {
        this.builtinName = builtinName;
        this.defaultValue = defaultValue;
        this.useDefaultForNone = useDefaultForNone;
    }

    @Specialization(guards = {"!useDefaultForNone", "isNoValue(none)"})
    int doNoValue(@SuppressWarnings("unused") PNone none) {
        return defaultValue;
    }

    @Specialization(guards = "useDefaultForNone")
    int doNoValueAndNone(@SuppressWarnings("unused") PNone none) {
        return defaultValue;
    }

    @Specialization
    int doString(String value) {
        if (PString.codePointCount(value, 0, value.length()) == 1) {
            return PString.codePointAt(value, 0);
        } else {
            throw raise(TypeError, ErrorMessages.S_BRACKETS_ARG_MUST_BE_S_NOT_P, builtinName, "unicode character", value);
        }
    }

    @Specialization(guards = {"!isHandledPNone(useDefaultForNone, value)"}, replaces = "doString")
    int doOthers(Object value,
                    @Cached CastToJavaStringNode castToJavaStringNode) {
        try {
            return doString(castToJavaStringNode.execute(value));
        } catch (CannotCastException ex) {
            throw raise(TypeError, ErrorMessages.S_BRACKETS_ARG_MUST_BE_S_NOT_P, builtinName, "unicode character", value);
        }
    }

    @ClinicConverterFactory
    public static CodePointConversionNode create(@BuiltinName String builtinName, @DefaultValue int defaultValue, @UseDefaultForNone boolean useDefaultForNone) {
        return CodePointConversionNodeGen.create(builtinName, defaultValue, useDefaultForNone);
    }
}
