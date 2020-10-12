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
package com.oracle.graal.python.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Repeatable(value = ArgumentsClinic.class)
public @interface ArgumentClinic {
    /**
     * Name of the argument. It must match one of the argument names specified in the
     * {@code Builtin} annotation.
     */
    String name();

    /**
     * Specifies a predefined conversion routine to use. Other fields of this annotation specify
     * configuration for the conversion routine. Note that not all routines support all the
     * configuration options.
     * 
     * Conversion routines are implemented in {@code ConverterFactory}. It creates Java code
     * snippets that instantiate the actual cast nodes, which should implement
     * {@code ArgumentCastNode}.
     */
    ClinicConversion conversion() default ClinicConversion.None;

    /**
     * The string should contain valid Java constant value expression, for example, {@code true}, or
     * {@code \"some string\"}. You may have to update the annotation processor to include import of
     * necessary packages or use fully qualified names.
     */
    String defaultValue() default "";

    /**
     * Whether to use the default value also for {@code PNone.NONE} values. Otherwise, it is used
     * only for {@code PNone.NO_VALUE}.
     */
    boolean useDefaultForNone() default false;

    /**
     * Specifies the name of the conversion node class, which must include a static factory method
     * annotated with {@link ClinicConverterFactory}.
     */
    Class<?> conversionClass() default void.class;

    /**
     * Specifies arguments to the factory method. String literals must be explicitly quoted:
     * {@code args = "\"abc\""}
     */
    String[] args() default {};

    enum PrimitiveType {
        Boolean,
        Int,
        Long,
        Double,
    }

    enum ClinicConversion {
        /**
         * No builtin converter will be used.
         */
        None,
        /**
         * Corresponds to CPython's {@code bool} converter. Supports {@link #defaultValue()}.
         * {@code PNone.NONE} is, for now, always converted to {@code false}.
         */
        Boolean,
        /**
         * GraalPython specific converter that narrows any String representation to Java String.
         * Supports {@link #defaultValue()}, and {@link #useDefaultForNone()}.
         */
        String,
        /**
         * Corresponds to CPython's {@code int} converter. Supports {@link #defaultValue()}, and
         * {@link #useDefaultForNone()}.
         */
        Int,
        /**
         * Corresponds to CPython's {@code Py_ssize_t} converter. Supports {@link #defaultValue()},
         * and {@link #useDefaultForNone()}.
         */
        Index,
        /**
         * Corresponds to CPython's {@code slice_index} converter. Supports {@link #defaultValue()},
         * and {@link #useDefaultForNone()}.
         */
        SliceIndex,
        /**
         * Corresponds to CPython's {@code int(accept={str})} converter. Supports
         * {@link #defaultValue()}, and {@link #useDefaultForNone()}.
         */
        CodePoint,
        /**
         * Corresponds to CPython's {@code Py_buffer} converter.
         */
        Buffer,
    }
}
