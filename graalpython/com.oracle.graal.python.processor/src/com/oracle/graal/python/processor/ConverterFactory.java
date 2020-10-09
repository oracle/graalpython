/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.processor;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.PrimitiveType;
import com.oracle.graal.python.annotations.ArgumentClinic.ConversionFactory.ClinicArgument;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import java.util.HashMap;
import java.util.Map;

public class ConverterFactory {
    public static final String CLINIC_PACKAGE = "com.oracle.graal.python.nodes.function.builtins.clinic";

    private static final Map<TypeElement, ConverterFactory> cache = new HashMap<>();

    public final String fullClassName;
    public final String className;
    public final String methodName;
    public final int paramCount;
    public final ClinicArgument[] clinicArgs;
    public final PrimitiveType[] acceptedPrimitiveTypes;

    private ConverterFactory(String fullClassName, String className, String methodName, int paramCount, ClinicArgument[] clinicArgs, PrimitiveType[] acceptedPrimitiveTypes) {
        this.fullClassName = fullClassName;
        this.className = className;
        this.methodName = methodName;
        this.paramCount = paramCount;
        this.clinicArgs = clinicArgs;
        this.acceptedPrimitiveTypes = acceptedPrimitiveTypes;
    }

    private ConverterFactory(String className, ClinicArgument[] clinicArgs, PrimitiveType[] acceptedPrimitiveTypes) {
        this(CLINIC_PACKAGE + "." + className, className, "create", 0, clinicArgs, acceptedPrimitiveTypes);
    }

    private static final ConverterFactory BuiltinBoolean = new ConverterFactory("JavaBooleanConvertorNodeGen",
                    new ClinicArgument[]{ClinicArgument.DefaultValue},
                    new PrimitiveType[]{PrimitiveType.Boolean});

    private static final ConverterFactory BuiltinString = new ConverterFactory("JavaStringConvertorNodeGen",
                    new ClinicArgument[]{ClinicArgument.BuiltinName},
                    new PrimitiveType[]{});

    private static final ConverterFactory BuiltinStringWithDefault = new ConverterFactory("JavaStringConvertorWithDefaultValueNodeGen",
                    new ClinicArgument[]{ClinicArgument.BuiltinName, ClinicArgument.DefaultValue, ClinicArgument.UseDefaultForNone},
                    new PrimitiveType[]{});

    private static final ConverterFactory BuiltinInt = new ConverterFactory("JavaIntConversionNodeGen",
                    new ClinicArgument[]{ClinicArgument.DefaultValue, ClinicArgument.UseDefaultForNone},
                    new PrimitiveType[]{PrimitiveType.Int});

    private static final ConverterFactory BuiltinCodePoint = new ConverterFactory("CodePointConversionNodeGen",
                    new ClinicArgument[]{ClinicArgument.BuiltinName, ClinicArgument.DefaultValue, ClinicArgument.UseDefaultForNone},
                    new PrimitiveType[]{});

    private static final ConverterFactory BuiltinBuffer = new ConverterFactory("BufferConversionNodeGen",
                    new ClinicArgument[]{},
                    new PrimitiveType[]{});

    private static final ConverterFactory BuiltinIndex = new ConverterFactory("IndexConversionNodeGen",
                    new ClinicArgument[]{ClinicArgument.DefaultValue, ClinicArgument.UseDefaultForNone},
                    new PrimitiveType[]{PrimitiveType.Int});

    private static final ConverterFactory BuiltinNone = new ConverterFactory("DefaultValueNode",
                    new ClinicArgument[]{ClinicArgument.DefaultValue, ClinicArgument.UseDefaultForNone},
                    new PrimitiveType[]{PrimitiveType.Boolean, PrimitiveType.Int, PrimitiveType.Long, PrimitiveType.Double});

    public static ConverterFactory getBuiltin(ArgumentClinic annotation) {
        switch (annotation.conversion()) {
            case Boolean:
                return BuiltinBoolean;
            case String:
                return annotation.defaultValue().isEmpty() ? BuiltinString : BuiltinStringWithDefault;
            case Int:
                return BuiltinInt;
            case CodePoint:
                return BuiltinCodePoint;
            case Buffer:
                return BuiltinBuffer;
            case Index:
                return BuiltinIndex;
            case None:
                assert !annotation.defaultValue().isEmpty();
                return BuiltinNone;
            default:
                throw new IllegalArgumentException(annotation.conversion().toString());
        }
    }

    public static ConverterFactory getForClass(TypeElement conversionClass) throws ProcessingError {
        ConverterFactory factory = cache.get(conversionClass);
        if (factory != null) {
            return factory;
        }
        for (Element e : conversionClass.getEnclosedElements()) {
            ArgumentClinic.ConversionFactory annot = e.getAnnotation(ArgumentClinic.ConversionFactory.class);
            if (annot != null) {
                if (!e.getModifiers().contains(Modifier.STATIC) || e.getKind() != ElementKind.METHOD) {
                    throw new ProcessingError(conversionClass, "ConversionFactory annotation is applicable only to static methods.");
                }
                if (factory != null) {
                    throw new ProcessingError(conversionClass, "Multiple ConversionFactory annotations in a single class.");
                }
                String fullClassName = conversionClass.toString();
                String className = conversionClass.getSimpleName().toString();
                String methodName = e.getSimpleName().toString();
                int paramCount = ((ExecutableElement) e).getParameters().size() - annot.clinicArgs().length;
                factory = new ConverterFactory(fullClassName, className, methodName, paramCount, annot.clinicArgs(), annot.shortCircuitPrimitive());
            }
        }
        if (factory == null) {
            throw new ProcessingError(conversionClass, "No ConversionFactory annotation found.");
        }
        cache.put(conversionClass, factory);
        return factory;
    }

    public static ConverterFactory forCustomConversion(TypeElement type, String methodName) {
        String fullClassName = type.getQualifiedName().toString();
        String className = type.getSimpleName().toString();
        return new ConverterFactory(fullClassName, className, methodName, 0, new ClinicArgument[0], new PrimitiveType[0]);
    }
}
