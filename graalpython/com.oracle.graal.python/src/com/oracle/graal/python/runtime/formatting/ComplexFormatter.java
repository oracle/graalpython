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
package com.oracle.graal.python.runtime.formatting;

import com.oracle.graal.python.builtins.objects.complex.PComplex;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.formatting.InternalFormat.Spec;

public class ComplexFormatter extends InternalFormat.Formatter {
    private final FloatFormatter reFormatter;
    private final FloatFormatter imFormatter;

    protected ComplexFormatter(PythonCore core, FormattingBuffer result, Spec spec) {
        super(core, result, spec);
        Spec reSpec;
        Spec imSpec;
        if (hasNoSpecType()) {
            // no type spec: should be like the default __str__
            reSpec = getComponentSpecForNoSpecType(InternalFormat.Spec.NONE);
            imSpec = getComponentSpecForNoSpecType('+');
        } else {
            // Turn off any flags that should apply to the result as a whole and not to the
            // individual
            // components (re/im). Sign of re is determined by the sign flag, sign of im will be
            // always
            // printed ('+' flag)
            reSpec = getComponentSpec(spec, spec.sign);
            imSpec = getComponentSpec(spec, '+');
        }
        reFormatter = new FloatFormatter(core, result, reSpec);
        imFormatter = new FloatFormatter(core, result, imSpec);
    }

    public ComplexFormatter(PythonCore core, Spec spec) {
        this(core, new FormattingBuffer.StringFormattingBuffer(32 + Math.max(0, spec.width)), spec);
    }

    private static Spec getComponentSpec(Spec spec, char sign) {
        return new Spec(
                        '\0', // (fill)
                        '<', // (align)
                        sign, //
                        spec.alternate, //
                        -1, // (width)
                        spec.grouping, //
                        spec.precision, //
                        spec.type);
    }

    private static Spec getComponentSpecForNoSpecType(char sign) {
        // CPython uses "r" type, but also some internal flags that cause that integer values
        // are printed without the decimal part, which is mostly what "g" does
        return new InternalFormat.Spec(' ', '>', sign, false, InternalFormat.Spec.UNSPECIFIED, Spec.NONE, -1, 'g');
    }

    private boolean hasNoSpecType() {
        return spec.getType('\0') == '\0';
    }

    public ComplexFormatter format(PComplex value) {
        setStart();

        // Note: the spec is validated in the __format__ builtin

        boolean closeParen = false;
        if (hasNoSpecType()) {
            // no type spec: should be like the default __str__
            if (value.getReal() == 0 && Math.copySign(1.0, value.getReal()) == 1.0) {
                // we intentionally use reFormatter to avoid unwanted '+' sign
                reFormatter.format(value.getImag());
                result.append('j');
                return this;
            }
            result.append('(');
            closeParen = true;
        }

        reFormatter.format(value.getReal());
        imFormatter.format(value.getImag());
        result.append('j');

        if (closeParen) {
            result.append(')');
        }

        return this;
    }
}
