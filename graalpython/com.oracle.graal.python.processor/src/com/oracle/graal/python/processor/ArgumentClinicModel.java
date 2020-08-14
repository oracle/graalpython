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
        public final String factory;
        public final PrimitiveType[] acceptedPrimitiveTypes;

        private BuiltinConvertor(String factory, PrimitiveType... acceptedPrimitiveTypes) {
            this.factory = factory;
            this.acceptedPrimitiveTypes = acceptedPrimitiveTypes;
        }

        public String getCastNodeFactoryExpression(ArgumentClinic config, String builtinName) {
            if (factory == null) {
                return null;
            }
            return factory.replace("$isRequired$", java.lang.Boolean.toString(config.defaultValue().isEmpty())).//
                            replace("$builtinName$", '"' + builtinName + '"').//
                            replace("$defaultValue$", config.defaultValue());
        }

        public static BuiltinConvertor fromConvertorEnum(ClinicConversion conversion) {
            switch (conversion) {
                case None:
                    return new BuiltinConvertor(null, PrimitiveType.values());
                case Boolean:
                    return new BuiltinConvertor("JavaBooleanConvertorNodeGen.create($defaultValue$)", PrimitiveType.Boolean);
                case String:
                    return new BuiltinConvertor("new JavaStringConvertorNode($builtinName$)");
                case Int:
                    return new BuiltinConvertor("JavaIntConversionNodeGen.create($defaultValue$)", PrimitiveType.Int);
                default:
                    throw new IllegalArgumentException();
            }
        }
    }

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

        public static ArgumentClinicData create(ArgumentClinic annotation, TypeElement type, BuiltinAnnotation builtinAnnotation, int index) {
            if (annotation == null) {
                return new ArgumentClinicData(null, index, new HashSet<>(Arrays.asList(PrimitiveType.values())), null);
            }

            PrimitiveType[] acceptedPrimitives = new PrimitiveType[0];
            String castNodeFactory;
            if (annotation.customConversion().isEmpty()) {
                BuiltinConvertor convertor = BuiltinConvertor.fromConvertorEnum(annotation.conversion());
                castNodeFactory = convertor.getCastNodeFactoryExpression(annotation, builtinAnnotation.name);
                acceptedPrimitives = convertor.acceptedPrimitiveTypes;
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
