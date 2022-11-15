/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.annotations.ArgumentClinic.PrimitiveType;
import com.oracle.graal.python.processor.ConverterFactory.Param;

public class ArgumentClinicModel {

    /**
     * Mirrors the data of the {@code Builtin} annotation, which cannot be in the "annotations"
     * project because of its dependence on other GraalPython runtime classes.
     */
    public static final class BuiltinAnnotation {
        public final String name;
        public final String[] argumentNames;
        /** Set to {@code -1} if unknown. */
        public final int minNumOfPositionalArgs;

        public BuiltinAnnotation(String name, String[] argumentNames, int minNumOfPositionalArgs) {
            this.name = name;
            this.argumentNames = argumentNames;
            this.minNumOfPositionalArgs = minNumOfPositionalArgs;
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
        public final Set<String> imports;

        private ArgumentClinicData(ArgumentClinic annotation, int index, Set<PrimitiveType> acceptedPrimitiveTypes, String castNodeFactory, Set<String> imports) {
            this.annotation = annotation;
            this.index = index;
            this.acceptedPrimitiveTypes = acceptedPrimitiveTypes;
            this.castNodeFactory = castNodeFactory;
            this.imports = imports;
        }

        private static ConverterFactory[] getFactories(ArgumentClinic annotation, TypeElement type, ConverterFactory[] annotationFactories) throws ProcessingError {
            if (annotationFactories == null && annotation.args().length != 0) {
                throw new ProcessingError(type, "No conversionClass specified but arguments were provided");
            }
            if (annotationFactories != null) {
                return annotationFactories;
            }
            if (annotation.conversion() == ClinicConversion.None && annotation.defaultValue().isEmpty()) {
                throw new ProcessingError(type, "ArgumentClinic annotation must declare either builtin conversion or custom conversion.");
            }
            return ConverterFactory.getBuiltin(annotation);
        }

        public static ArgumentClinicData create(ArgumentClinic annotation, TypeElement type, BuiltinAnnotation builtinAnnotation, int index, ConverterFactory[] annotationFactories)
                        throws ProcessingError {
            if (annotation == null) {
                return new ArgumentClinicData(null, index, new HashSet<>(Arrays.asList(PrimitiveType.values())), null, Collections.emptySet());
            }
            if (!annotation.useDefaultForNone() && !annotation.defaultValue().isEmpty() &&
                            builtinAnnotation.minNumOfPositionalArgs != -1 && index < builtinAnnotation.minNumOfPositionalArgs) {
                throw new ProcessingError(type, "Argument clinic for argument '%s': defaultValue will have no effect, because " +
                                "useDefaultForNone is false and the argument is required positional argument.",
                                annotation.name());
            }

            ConverterFactory[] factories = getFactories(annotation, type, annotationFactories);
            ArrayList<ConverterFactory> applicableFactories = new ArrayList<>(Arrays.asList(factories));
            // Fixed order helps reproducing potential issues:
            applicableFactories.sort(Comparator.comparing(a -> a.id));

            // Validate extra arguments count
            applicableFactories.removeIf(x -> x.extraParamCount != annotation.args().length);
            if (applicableFactories.size() == 0) {
                throw new ProcessingError(type, "None of the factory methods of conversion %s expects %d extra arguments. Found factories: %s",
                                factories[0].fullClassName, annotation.args().length, Arrays.toString(factories));
            }

            // Validate default value
            if (!annotation.defaultValue().equals("")) {
                applicableFactories.removeIf(x -> !x.hasParameter(Param.DefaultValue));
                if (applicableFactories.size() == 0) {
                    throw new ProcessingError(type, "None of the factory methods of conversion %s takes the provided default value '%s'. Found factories: %s",
                                    factories[0].fullClassName, annotation.defaultValue(), Arrays.toString(factories));
                }
            }

            // Validate the defaultForNone
            if (annotation.useDefaultForNone()) {
                applicableFactories.removeIf(x -> !x.hasParameter(Param.UseDefaultForNone));
                if (applicableFactories.size() == 0) {
                    throw new ProcessingError(type, "None of the factory methods of conversion %s takes the 'UseDefaultForNone' argument. Found factories: %s",
                                    factories[0].fullClassName, Arrays.toString(factories));
                }
            }

            factoryLoop: for (ConverterFactory factory : applicableFactories) {
                if (annotation.args().length != factory.extraParamCount) {
                    continue;
                }
                String[] args = new String[factory.params.length];
                Set<String> imports = new HashSet<>();
                int extraParamIndex = 0;
                for (int i = 0; i < args.length; ++i) {
                    switch (factory.params[i]) {
                        case BuiltinName:
                            args[i] = String.format("\"%s\"", builtinAnnotation.name);
                            break;
                        case ArgumentIndex:
                            args[i] = String.valueOf(index);
                            break;
                        case ArgumentName:
                            args[i] = String.format("\"%s\"", builtinAnnotation.argumentNames[index]);
                            break;
                        case DefaultValue:
                            if (annotation.defaultValue().isEmpty()) {
                                // value for this argument is not available, try with the next
                                // factory method
                                continue factoryLoop;
                            }
                            args[i] = getLiteralOrFieldReference(type, annotation.defaultValue(), imports);
                            break;
                        case UseDefaultForNone:
                            args[i] = String.valueOf(annotation.useDefaultForNone());
                            break;
                        case Extra:
                            args[i] = getLiteralOrFieldReference(type, annotation.args()[extraParamIndex++], null);
                            break;
                        default:
                            throw new IllegalStateException("Unsupported ClinicArgument: " + factory.params[i]);
                    }
                }
                String castNodeFactory = String.format("%s.%s(%s)", factory.className, factory.methodName, String.join(", ", args));
                imports.add(factory.fullClassName);
                if (annotation.defaultValue().startsWith("PNone.")) {
                    imports.add("com.oracle.graal.python.builtins.objects.PNone");
                }

                return new ArgumentClinicData(annotation, index, new HashSet<>(Arrays.asList(factory.acceptedPrimitiveTypes)), castNodeFactory, imports);
            }
            throw new ProcessingError(type, "None of the @ClinicConverterFactory annotated methods in %s is applicable. " +
                            "Common issue is not providing defaultValue: either provide the defaultValue or add @ClinicConverterFactory annotated method that does not require the @DefaultValue parameter to class %s.",
                            factories[0].fullClassName, factories[0].fullClassName);
        }

        private static String getLiteralOrFieldReference(TypeElement type, String defaultValue, Set<String> imports) {
            Stream<? extends Element> enclosedElements = type.getEnclosedElements().stream();
            Element enclosingElement = type.getEnclosingElement();
            if (enclosingElement instanceof TypeElement) {
                enclosedElements = Stream.concat(enclosedElements, enclosingElement.getEnclosedElements().stream());
            }
            enclosedElements = enclosedElements.filter(x -> x.getKind() == ElementKind.FIELD && x.getSimpleName().toString().equals(defaultValue));
            Optional<? extends Element> typeElement = enclosedElements.findFirst();
            String result;
            if (typeElement.isPresent()) {
                TypeElement fieldEnclosingType = (TypeElement) typeElement.get().getEnclosingElement();
                result = fieldEnclosingType.getQualifiedName() + "." + typeElement.get().getSimpleName();
            } else {
                result = defaultValue;
                if (imports != null && defaultValue.startsWith("T_")) {
                    imports.add("static com.oracle.graal.python.nodes.StringLiterals.*");
                }
            }
            return result;
        }
    }
}
