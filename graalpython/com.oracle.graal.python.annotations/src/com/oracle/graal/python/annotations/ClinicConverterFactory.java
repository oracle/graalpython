package com.oracle.graal.python.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * Annotates the factory method (which must be static) in the class specified by
 * {@link ArgumentClinic#conversionClass()}.
 */
@Target(ElementType.METHOD)
public @interface ClinicConverterFactory {

    /**
     * Specifies which arguments will be provided by the clinic. These are passed to the factory
     * method before the argument supplied in {@link ArgumentClinic#args()}.
     */
    ClinicArgument[] clinicArgs() default {};

    /**
     * The boxing optimized execute method variants will not attempt to cast the listed
     * primitive types and will just pass them directly to the specializations. This does not
     * apply to primitive values that are already boxed: those are always passed to the
     * convertor.
     */
    ArgumentClinic.PrimitiveType[] shortCircuitPrimitive() default {};

    enum ClinicArgument {
        /**
         * The default value {@link ArgumentClinic#defaultValue()}.
         */
        DefaultValue,
        /**
         * The flag {@link ArgumentClinic#useDefaultForNone()}.
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
