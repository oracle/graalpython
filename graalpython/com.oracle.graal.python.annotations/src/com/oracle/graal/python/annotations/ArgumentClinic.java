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
     * Conversion routines are implemented in {@code ArgumentClinicModel#BuiltinConvertor}. It
     * creates Java code snippets that instantiate the actual cast nodes, which should implement
     * {@code ArgumentCastNode}.
     */
    ClinicConversion conversion() default ClinicConversion.None;

    /**
     * Overrides the {@link #conversion()} value. Name of a method of the builtin class that should
     * be used as a factory to create the conversion node. When this value is set, then other
     * conversion options are ignored.
     */
    String customConversion() default "";

    /**
     * The boxing optimized execute method variants will not attempt to cast the listed primitive
     * types and will just pass them directly to the specializations. This does not apply to
     * primitive values that are already boxed: those are always passed to the convertor.
     * 
     * It is not necessary to set this when using a builtin conversion. Built-in convertors provide
     * their own list of short circuit types, which is applied if this field is set to its default
     * value.
     */
    PrimitiveType[] shortCircuitPrimitive() default {};

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
     * annotated with {@link ConversionFactory}. Must not be used with {@link #customConversion()}.
     */
    Class<?> conversionClass() default void.class;

    /**
     * Specifies arguments to the factory method, applicable only with {@link #conversionClass()}.
     * String literals must be explicitly quoted: {@code args = "\"abc\""}
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
         * No builtin convertor will be used.
         */
        None,
        /**
         * Corresponds to CPython's {@code bool} convertor. Supports {@link #defaultValue()}.
         * {@code PNone.NONE} is, for now, always converted to {@code false}.
         */
        Boolean,
        /**
         * GraalPython specific convertor that narrows any String representation to Java String.
         * Supports {@link #defaultValue()}, and {@link #useDefaultForNone()}.
         */
        String,
        /**
         * Corresponds to CPython's {@code int} convertor. Supports {@link #defaultValue()}, and
         * {@link #useDefaultForNone()}.
         */
        Int,
        /**
         * Corresponds to CPython's {@code Py_ssize_t} convertor. Supports {@link #defaultValue()},
         * and {@link #useDefaultForNone()}.
         */
        Index,
        /**
         * Corresponds to CPython's {@code int(accept={str})} convertor. Supports
         * {@link #defaultValue()}, and {@link #useDefaultForNone()}.
         */
        CodePoint,
        /**
         * Corresponds to CPython's {@code Py_buffer} convertor.
         */
        Buffer,
    }

    /**
     * Annotates the factory method (which must be static) in the class specified by
     * {@link #conversionClass()}.
     */
    @Target(ElementType.METHOD)
    @interface ConversionFactory {

        /**
         * Specifies which arguments will be provided by the clinic. These are passed to the factory
         * method before the argument supplied in {@link #args()}.
         */
        ClinicArgument[] clinicArgs() default {};

        enum ClinicArgument {
            /**
             * The default value {@link #defaultValue()}.
             */
            DefaultValue,
            /**
             * The flag {@link #useDefaultForNone()}.
             */
            UseDefaultForNone,
            /**
             * The name of the builtin function.
             */
            BuiltinName,
            /**
             * The index of the argument.
             */
            ArgumentIndex,
            /**
             * The name of the argument.
             */
            ArgumentName,
        }
    }
}
