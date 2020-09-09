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
package com.oracle.graal.python.processor;

import static java.lang.String.format;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import javax.lang.model.element.TypeElement;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.annotations.ArgumentClinic.PrimitiveType;

public class ArgumentClinicModel {
    static final class BuiltinConvertor {
        public static final String CLINIC_PACKAGE = "com.oracle.graal.python.nodes.function.builtins.clinic";

        public static String getCodeSnippet(ArgumentClinic annotation, BuiltinAnnotation builtin) {
            switch (annotation.conversion()) {
                case Boolean:
                    return format("JavaBooleanConvertorNodeGen.create(%s)", annotation.defaultValue());
                case String:
                    String defaultVal = annotation.defaultValue();
                    if (defaultVal.isEmpty()) {
                        return format("JavaStringConvertorNodeGen.create(\"%s\")", builtin.name);
                    } else {
                        return format("JavaStringConvertorWithDefaultValueNodeGen.create(\"%s\", %s, %s)", builtin.name, defaultVal, annotation.useDefaultForNone());
                    }
                case Int:
                    return format("JavaIntConversionNodeGen.create(%s, %s)", annotation.defaultValue(), annotation.useDefaultForNone());
                case CodePoint:
                    return format("CodePointConversionNodeGen.create(\"%s\", %s, %s)", builtin.name, annotation.defaultValue(), annotation.useDefaultForNone());
                case Index:
                    return format("IndexConversionNodeGen.create(%s, %s)", annotation.defaultValue(), annotation.useDefaultForNone());
                case None:
                    return format("DefaultValueNode.create(%s, %s)", annotation.defaultValue(), annotation.useDefaultForNone());
                default:
                    throw new IllegalArgumentException(annotation.conversion().toString());
            }
        }

        public static void addImports(ArgumentClinic annotation, Set<String> imports) {
            // We may add imports for some other prominent constants
            // Another possibility is to introduce something like ArgumentClinicImportStatic
            // annotation, or piggy-back on Truffle DSL's ImportStatic annotation, but then we can
            // also use fully qualified names in such rare cases
            if (annotation.defaultValue().startsWith("PNone.")) {
                imports.add("com.oracle.graal.python.builtins.objects.PNone");
            }
            if (annotation.conversion() != ClinicConversion.None || (annotation.customConversion().isEmpty() && !annotation.defaultValue().isEmpty())) {
                imports.add(CLINIC_PACKAGE + '.' + getConvertorImport(annotation));
            }
        }

        private static String getConvertorImport(ArgumentClinic annotation) {
            switch (annotation.conversion()) {
                case Boolean:
                    return "JavaBooleanConvertorNodeGen";
                case String:
                    return annotation.defaultValue().isEmpty() ? "JavaStringConvertorNodeGen" : "JavaStringConvertorWithDefaultValueNodeGen";
                case Int:
                    return "JavaIntConversionNodeGen";
                case Index:
                    return "IndexConversionNodeGen";
                case CodePoint:
                    return "CodePointConversionNodeGen";
                case None:
                    return "DefaultValueNode";
                default:
                    throw new IllegalArgumentException(annotation.conversion().toString());
            }
        }

        public static PrimitiveType[] getAcceptedPrimitiveTypes(ArgumentClinic annotation) {
            switch (annotation.conversion()) {
                case Boolean:
                    return new PrimitiveType[]{PrimitiveType.Boolean};
                case String:
                case CodePoint:
                    return new PrimitiveType[0];
                case Int:
                case Index:
                    return new PrimitiveType[]{PrimitiveType.Int};
                case None:
                    return new PrimitiveType[]{PrimitiveType.Boolean, PrimitiveType.Int, PrimitiveType.Long, PrimitiveType.Double};
                default:
                    throw new IllegalArgumentException(annotation.conversion().toString());
            }
        }
    }

    /**
     * Mirrors the data of the {@code Builtin} annotation, which cannot be in the "annotations"
     * project because of its dependence on other GraalPython runtime classes.
     */
    static final class BuiltinAnnotation {
        public final String name;
        public final String[] argumentNames;

        public BuiltinAnnotation(String name, String[] argumentNames) {
            this.name = name;
            this.argumentNames = argumentNames;
        }
    }

    static final class BuiltinClinicData {
        public final TypeElement type;
        public final BuiltinAnnotation builtinAnnotation;
        public final List<ArgumentClinicData> arguments;

        public BuiltinClinicData(TypeElement type, BuiltinAnnotation builtinAnnotation, List<ArgumentClinicData> arguments) {
            this.type = type;
            this.builtinAnnotation = builtinAnnotation;
            this.arguments = arguments;
        }

        public boolean containsAllArguments(int[] argIndices) {
            return argIndices.length == arguments.size();
        }

        public int[] getIndicesForPrimitiveTypeAccepts(PrimitiveType primitiveType) {
            return getIndicesOf(x -> x.acceptedPrimitiveTypes.contains(primitiveType));
        }

        public int[] getIndicesForHasCastNode() {
            return getIndicesOf(x -> x.castNodeFactory != null);
        }

        public ArgumentClinicData[] getArgumentsWithCastNodeFactory() {
            return arguments.stream().filter(x -> x.castNodeFactory != null).toArray(ArgumentClinicData[]::new);
        }

        private int[] getIndicesOf(Predicate<ArgumentClinicData> predicate) {
            return arguments.stream().filter(predicate).mapToInt(x -> x.index).toArray();
        }
    }

    static final class ArgumentClinicData {
        public final ArgumentClinic annotation;
        public final int index;
        public final Set<PrimitiveType> acceptedPrimitiveTypes;
        public final String castNodeFactory;

        public ArgumentClinicData(ArgumentClinic annotation, int index, Set<PrimitiveType> acceptedPrimitiveTypes, String castNodeFactory) {
            this.annotation = annotation;
            this.index = index;
            this.acceptedPrimitiveTypes = acceptedPrimitiveTypes;
            this.castNodeFactory = castNodeFactory;
        }

        public boolean isClinicArgument() {
            return annotation != null;
        }

        public static ArgumentClinicData create(ArgumentClinic annotation, TypeElement type, BuiltinAnnotation builtinAnnotation, int index) throws ProcessingError {
            if (annotation == null) {
                return new ArgumentClinicData(null, index, new HashSet<>(Arrays.asList(PrimitiveType.values())), null);
            }

            PrimitiveType[] acceptedPrimitives = new PrimitiveType[0];
            String castNodeFactory;
            if (annotation.customConversion().isEmpty()) {
                if (annotation.conversion() == ClinicConversion.None && annotation.defaultValue().isEmpty()) {
                    throw new ProcessingError(type, "ArgumentClinic annotation must declare either builtin conversion or custom conversion.");
                }
                castNodeFactory = BuiltinConvertor.getCodeSnippet(annotation, builtinAnnotation);
                acceptedPrimitives = BuiltinConvertor.getAcceptedPrimitiveTypes(annotation);
            } else {
                castNodeFactory = type.getQualifiedName().toString() + '.' + annotation.customConversion() + "()";
            }
            if (annotation.shortCircuitPrimitive().length > 0) {
                acceptedPrimitives = annotation.shortCircuitPrimitive();
            }
            return new ArgumentClinicData(annotation, index, new HashSet<>(Arrays.asList(acceptedPrimitives)), castNodeFactory);
        }
    }
}
