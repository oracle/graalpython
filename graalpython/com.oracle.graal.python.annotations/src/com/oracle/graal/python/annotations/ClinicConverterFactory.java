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
     * The boxing optimized execute method variants will not attempt to cast the listed
     * primitive types and will just pass them directly to the specializations. This does not
     * apply to primitive values that are already boxed: those are always passed to the
     * convertor.
     */
    ArgumentClinic.PrimitiveType[] shortCircuitPrimitive() default {};

    /**
     * Annotates parameter of the factory method which will receive the default value {@link ArgumentClinic#defaultValue()}.
     */
    @Target(ElementType.PARAMETER)
    @interface DefaultValue {
    }

    /**
     * Annotates parameter of the factory method which will receive the value of {@link ArgumentClinic#useDefaultForNone()}.
     */
    @Target(ElementType.PARAMETER)
    @interface UseDefaultForNone {
    }

    /**
     * Annotates parameter of the factory method which will receive the name of the builtin function.
     */
    @Target(ElementType.PARAMETER)
    @interface BuiltinName {
    }

    /**
     * Annotates parameter of the factory method which will receive the index of the argument of the builtin functions.
     */
    @Target(ElementType.PARAMETER)
    @interface ArgumentIndex {
    }

    /**
     * Annotates parameter of the factory method which will receive the name of the argument of the builtin functions.
     */
    @Target(ElementType.PARAMETER)
    @interface ArgumentName {
    }
}
