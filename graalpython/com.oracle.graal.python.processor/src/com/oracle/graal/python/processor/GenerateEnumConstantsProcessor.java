/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileObject;

import com.oracle.graal.python.annotations.GenerateEnumConstants;
import com.oracle.graal.python.processor.CodeWriter.Block;

public class GenerateEnumConstantsProcessor extends AbstractProcessor {

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Collections.singleton(GenerateEnumConstants.class.getName());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    @SuppressWarnings({"try", "unused"})
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment re) {
        if (re.processingOver()) {
            return true;
        }
        Set<? extends Element> annotatedElements = re.getElementsAnnotatedWith(GenerateEnumConstants.class);
        for (Element el : annotatedElements) {
            GenerateEnumConstants annotation = el.getAnnotation(GenerateEnumConstants.class);
            boolean useByte = annotation.type() == GenerateEnumConstants.Type.BYTE;
            String elementType = useByte ? "byte" : "int";
            if (el.getKind() == ElementKind.ENUM) {
                try {
                    Element enc = el.getEnclosingElement();
                    int enclosingTypes = 0;
                    OUTER: while (enc != null) {
                        switch (enc.getKind()) {
                            case CLASS:
                            case ENUM:
                            case INTERFACE:
                                enclosingTypes++;
                                break;
                            default:
                                break OUTER;
                        }
                        enc.getEnclosingElement();
                    }
                    String qualName = ((TypeElement) el).getQualifiedName() + "Constants";
                    String pkgName = qualName.substring(0, qualName.lastIndexOf('.'));
                    String className = qualName.substring(qualName.lastIndexOf('.') + 1);

                    while (enclosingTypes-- > 0) {
                        className = pkgName.substring(qualName.lastIndexOf('.') + 1) + className;
                        pkgName = pkgName.substring(0, pkgName.lastIndexOf('.'));
                        qualName = pkgName + "." + className;
                    }

                    JavaFileObject file = processingEnv.getFiler().createSourceFile(qualName);
                    try (CodeWriter w = new CodeWriter(file.openWriter())) {
                        w.writeLn("// CheckStyle: start generated");
                        w.writeLn("// Auto generated by GenerateEnumConstantsProcessor at %s", LocalDateTime.now());
                        w.writeLn("package %s;", pkgName);
                        w.writeLn();
                        w.writeLn("public final class %s {", className);
                        int i = 0;
                        for (Element enumBit : el.getEnclosedElements()) {
                            if (enumBit.getKind() == ElementKind.ENUM_CONSTANT) {
                                String enumName = ((VariableElement) enumBit).getSimpleName().toString();
                                try (Block b = w.newIndent()) {
                                    int value = i++;
                                    if (useByte) {
                                        if (value <= 0xFF) {
                                            value = (byte) value;
                                        } else {
                                            throw new IllegalArgumentException("Enum constant doesn't fit into byte");
                                        }
                                    }
                                    w.writeLn("public static final %s %s = %d;", elementType, enumName, value);
                                }
                            }
                        }
                        w.writeLn("}");
                    }
                } catch (IOException e) {
                    processingEnv.getMessager().printMessage(Kind.ERROR, e.getMessage(), el);
                }
            } else {
                processingEnv.getMessager().printMessage(Kind.ERROR, "Can only annotate enums with @GenerateEnumConstants", el);
            }
        }
        return true;
    }
}
