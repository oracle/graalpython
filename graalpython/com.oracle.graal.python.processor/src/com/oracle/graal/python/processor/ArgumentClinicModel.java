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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import javax.lang.model.element.TypeElement;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.annotations.ArgumentClinic.PrimitiveType;

public class ArgumentClinicModel {

    /**
     * Mirrors the data of the {@code Builtin} annotation, which cannot be in the "annotations"
     * project because of its dependence on other GraalPython runtime classes.
     */
    public static final class BuiltinAnnotation {
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
        public final Set<String> imports;

        private ArgumentClinicData(ArgumentClinic annotation, int index, Set<PrimitiveType> acceptedPrimitiveTypes, String castNodeFactory, Set<String> imports) {
            this.annotation = annotation;
            this.index = index;
            this.acceptedPrimitiveTypes = acceptedPrimitiveTypes;
            this.castNodeFactory = castNodeFactory;
            this.imports = imports;
        }

        private static ConverterFactory getFactory(ArgumentClinic annotation, TypeElement type, ConverterFactory factory) throws ProcessingError {
            if (factory == null && annotation.args().length != 0) {
                throw new ProcessingError(type, "No conversionClass specified but arguments were provided");
            }
            if (factory != null) {
                return factory;
            }
            if (annotation.conversion() == ClinicConversion.None && annotation.defaultValue().isEmpty()) {
                throw new ProcessingError(type, "ArgumentClinic annotation must declare either builtin conversion or custom conversion.");
            }
            return ConverterFactory.getBuiltin(annotation);
        }

        public static ArgumentClinicData create(ArgumentClinic annotation, TypeElement type, BuiltinAnnotation builtinAnnotation, int index, ConverterFactory annotationFactory)
                        throws ProcessingError {
            if (annotation == null) {
                return new ArgumentClinicData(null, index, new HashSet<>(Arrays.asList(PrimitiveType.values())), null, Collections.emptySet());
            }
            ConverterFactory factory = getFactory(annotation, type, annotationFactory);
            if (annotation.args().length != factory.extraParamCount) {
                throw new ProcessingError(type, "Conversion %s.%s expects %d arguments", factory.fullClassName, factory.methodName, factory.extraParamCount);
            }

            String[] args = new String[factory.params.length];
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
                        args[i] = annotation.defaultValue();
                        break;
                    case UseDefaultForNone:
                        args[i] = String.valueOf(annotation.useDefaultForNone());
                        break;
                    case Extra:
                        args[i] = annotation.args()[extraParamIndex++];
                        break;
                    default:
                        throw new IllegalStateException("Unsupported ClinicArgument: " + factory.params[i]);
                }
            }
            String castNodeFactory = String.format("%s.%s(%s)", factory.className, factory.methodName, String.join(", ", args));
            Set<String> imports = new HashSet<>();
            imports.add(factory.fullClassName);
            if (annotation.defaultValue().startsWith("PNone.")) {
                imports.add("com.oracle.graal.python.builtins.objects.PNone");
            }

            return new ArgumentClinicData(annotation, index, new HashSet<>(Arrays.asList(factory.acceptedPrimitiveTypes)), castNodeFactory, imports);
        }
    }
}
