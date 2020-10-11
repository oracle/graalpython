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
import com.oracle.graal.python.annotations.ClinicConverterFactory;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConverterFactory {
    public static final String CLINIC_PACKAGE = "com.oracle.graal.python.nodes.function.builtins.clinic";

    public enum Param {
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
        /**
         * Extra argument provided in {@link ArgumentClinic#args()}.
         */
        Extra,
    }

    private static final Map<TypeElement, ConverterFactory> cache = new HashMap<>();

    private static ConverterFactory BuiltinBoolean;
    private static ConverterFactory BuiltinString;
    private static ConverterFactory BuiltinStringWithDefaultValue;
    private static ConverterFactory BuiltinInt;
    private static ConverterFactory BuiltinCodePoint;
    private static ConverterFactory BuiltinBuffer;
    private static ConverterFactory BuiltinIndex;
    private static ConverterFactory BuiltinSliceIndex;
    private static ConverterFactory BuiltinNone;

    public final String fullClassName;
    public final String className;
    public final String methodName;
    public final int extraParamCount;
    public final Param[] params;
    public final PrimitiveType[] acceptedPrimitiveTypes;

    private ConverterFactory(String fullClassName, String className, String methodName, int extraParamCount, Param[] params, PrimitiveType[] acceptedPrimitiveTypes) {
        this.fullClassName = fullClassName;
        this.className = className;
        this.methodName = methodName;
        this.extraParamCount = extraParamCount;
        this.params = params;
        this.acceptedPrimitiveTypes = acceptedPrimitiveTypes;
    }

    public static ConverterFactory getBuiltin(ArgumentClinic annotation) {
        switch (annotation.conversion()) {
            case Boolean:
                return BuiltinBoolean;
            case String:
                return annotation.defaultValue().isEmpty() ? BuiltinString : BuiltinStringWithDefaultValue;
            case Int:
                return BuiltinInt;
            case CodePoint:
                return BuiltinCodePoint;
            case Buffer:
                return BuiltinBuffer;
            case Index:
                return BuiltinIndex;
            case SliceIndex:
                return BuiltinSliceIndex;
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
            ClinicConverterFactory annot = e.getAnnotation(ClinicConverterFactory.class);
            if (annot != null) {
                if (!e.getModifiers().contains(Modifier.STATIC) || e.getKind() != ElementKind.METHOD) {
                    throw new ProcessingError(conversionClass, "ClinicConverterFactory annotation is applicable only to static methods.");
                }
                if (factory != null) {
                    throw new ProcessingError(conversionClass, "Multiple ClinicConverterFactory annotations in a single class.");
                }
                String fullClassName = conversionClass.toString();
                String className = conversionClass.getSimpleName().toString();
                String methodName = e.getSimpleName().toString();
                List<? extends VariableElement> params = ((ExecutableElement) e).getParameters();
                Param[] args = new Param[params.size()];
                int extraParamCount = 0;
                for (int i = 0; i < args.length; ++i) {
                    VariableElement param = params.get(i);
                    if (param.getAnnotation(ClinicConverterFactory.ArgumentIndex.class) != null) {
                        args[i] = Param.ArgumentIndex;
                    } else if (param.getAnnotation(ClinicConverterFactory.ArgumentName.class) != null) {
                        args[i] = Param.ArgumentName;
                    } else if (param.getAnnotation(ClinicConverterFactory.BuiltinName.class) != null) {
                        args[i] = Param.BuiltinName;
                    } else if (param.getAnnotation(ClinicConverterFactory.DefaultValue.class) != null) {
                        args[i] = Param.DefaultValue;
                    } else if (param.getAnnotation(ClinicConverterFactory.UseDefaultForNone.class) != null) {
                        args[i] = Param.UseDefaultForNone;
                    } else {
                        args[i] = Param.Extra;
                        extraParamCount++;
                    }
                }
                factory = new ConverterFactory(fullClassName, className, methodName, extraParamCount, args, annot.shortCircuitPrimitive());
            }
        }
        if (factory == null) {
            throw new ProcessingError(conversionClass, "No ClinicConverterFactory annotation found.");
        }
        cache.put(conversionClass, factory);
        return factory;
    }

    public static ConverterFactory forCustomConversion(TypeElement type, String methodName) {
        String fullClassName = type.getQualifiedName().toString();
        String className = type.getSimpleName().toString();
        return new ConverterFactory(fullClassName, className, methodName, 0, new Param[0], new PrimitiveType[0]);
    }

    private static ConverterFactory forBuiltin(Elements elementUtils, String className) throws ProcessingError {
        TypeElement type = elementUtils.getTypeElement(CLINIC_PACKAGE + "." + className);
        if (type == null) {
            throw new ProcessingError(null, "Unable to find built-in argument clinic conversion node " + CLINIC_PACKAGE + "." + className);
        }
        return getForClass(type);
    }

    public static void initBuiltins(Elements elementUtils) throws ProcessingError {
        BuiltinBoolean = forBuiltin(elementUtils, "JavaBooleanConverterNode");
        BuiltinString = forBuiltin(elementUtils, "JavaStringConverterNode");
        BuiltinStringWithDefaultValue = forBuiltin(elementUtils, "JavaStringConverterWithDefaultValueNode");
        BuiltinInt = forBuiltin(elementUtils, "JavaIntConversionNode");
        BuiltinCodePoint = forBuiltin(elementUtils, "CodePointConversionNode");
        BuiltinBuffer = forBuiltin(elementUtils, "BufferConversionNode");
        BuiltinIndex = forBuiltin(elementUtils, "IndexConversionNode");
        BuiltinSliceIndex = forBuiltin(elementUtils, "SliceIndexConversionNode");
        BuiltinNone = forBuiltin(elementUtils, "DefaultValueNode");
    }
}
