/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;

import com.oracle.graal.python.annotations.DowncallSignature;
import com.oracle.graal.python.annotations.NativeSimpleType;

public class GenerateNativeDowncallsProcessor extends AbstractProcessor {
    private record NativeDowncallDesc(String name, String symbolName, NativeSimpleType returnType, List<NativeSimpleType> argumentTypes, List<String> argumentNames) {
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(DowncallSignature.class.getName());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return true;
        }
        try {
            doProcess(roundEnv);
        } catch (IOException ex) {
            ex.printStackTrace();
        } catch (ProcessingError ex) {
            processingEnv.getMessager().printMessage(Kind.ERROR, ex.getMessage(), ex.getElement());
        }
        return true;
    }

    private void doProcess(RoundEnvironment roundEnv) throws IOException, ProcessingError {
        Set<TypeElement> invokerElements = new LinkedHashSet<>();
        for (Element element : roundEnv.getElementsAnnotatedWith(DowncallSignature.class)) {
            invokerElements.add(validateDowncallSignature(element));
        }
        for (TypeElement invokerElement : invokerElements) {
            generateInvoker(invokerElement);
        }
    }

    private static TypeElement validateDowncallSignature(Element element) throws ProcessingError {
        if (element.getKind() != ElementKind.METHOD) {
            throw error(element, "@DowncallSignature can only annotate methods");
        }
        Element enclosingElement = element.getEnclosingElement();
        if (enclosingElement == null || enclosingElement.getKind() != ElementKind.CLASS) {
            throw error(element, "@DowncallSignature can only annotate methods in classes");
        }
        if (!enclosingElement.getModifiers().contains(Modifier.ABSTRACT)) {
            throw error(enclosingElement, "@DowncallSignature methods must be enclosed in an abstract class");
        }
        if (!element.getModifiers().contains(Modifier.ABSTRACT)) {
            throw error(element, "@DowncallSignature methods must be abstract");
        }
        return (TypeElement) enclosingElement;
    }

    private void generateInvoker(TypeElement invokerElement) throws IOException, ProcessingError {
        List<NativeDowncallDesc> downcalls = collectDowncalls(invokerElement);
        if (downcalls.isEmpty()) {
            throw error(invokerElement, "Annotated class does not declare any downcalls");
        }

        String packageName = processingEnv.getElementUtils().getPackageOf(invokerElement).getQualifiedName().toString();
        String invokerQualifiedName = invokerElement.getQualifiedName().toString();
        String invokerTypeRef = invokerQualifiedName.startsWith(packageName + ".") ? invokerQualifiedName.substring(packageName.length() + 1) : invokerQualifiedName;
        String className = invokerElement.getSimpleName() + "Gen";

        ArrayList<String> lines = new ArrayList<>();
        lines.add("// @formatter:off");
        lines.add("// Checkstyle: stop");
        lines.add("// Generated by annotation processor: " + getClass().getName());
        lines.add("package " + packageName + ";");
        lines.add("");
        lines.add("import java.lang.invoke.MethodHandle;");
        lines.add("import java.lang.invoke.MethodType;");
        lines.add("import java.util.concurrent.atomic.AtomicLongArray;");
        lines.add("");
        lines.add("import com.oracle.graal.python.runtime.nativeaccess.NativeAccessSupport;");
        lines.add("import com.oracle.graal.python.runtime.nativeaccess.NativeLibrary;");
        lines.add("import com.oracle.truffle.api.CompilerDirectives;");
        lines.add("import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;");
        lines.add("");
        lines.add("final class " + className + " extends " + invokerTypeRef + " {");
        lines.add("    private final PythonContext context;");
        lines.add("    private final AtomicLongArray cachedFunctions = new AtomicLongArray(" + downcalls.size() + ");");
        lines.add("    private volatile NativeLibrary nativeLibrary;");
        lines.add("");

        for (NativeDowncallDesc downcall : downcalls) {
            NativeDowncallMethodHandleGenerator.emitMethodHandleField(lines, methodHandleName(downcall.name), downcall.returnType, downcall.argumentTypes);
        }

        lines.add("");
        lines.add("    " + className + "(PythonContext context) {");
        lines.add("        this.context = context;");
        lines.add("    }");

        for (int i = 0; i < downcalls.size(); i++) {
            emitDowncallMethod(lines, downcalls.get(i), i);
        }

        lines.add("");
        lines.add("    @TruffleBoundary");
        lines.add("    private long lookup(int functionIndex, String symbolName) {");
        lines.add("        long symbol = cachedFunctions.get(functionIndex);");
        lines.add("        if (symbol == 0) {");
        lines.add("            symbol = loadFunction(symbolName);");
        lines.add("            cachedFunctions.compareAndSet(functionIndex, 0, symbol);");
        lines.add("            symbol = cachedFunctions.get(functionIndex);");
        lines.add("        }");
        lines.add("        return symbol;");
        lines.add("    }");
        lines.add("");
        lines.add("    @TruffleBoundary");
        lines.add("    private long loadFunction(String symbolName) {");
        lines.add("        return ensureLibrary().lookupSymbol(symbolName);");
        lines.add("    }");
        lines.add("");
        lines.add("    @TruffleBoundary");
        lines.add("    private NativeLibrary ensureLibrary() {");
        lines.add("        NativeLibrary library = nativeLibrary;");
        lines.add("        if (library == null) {");
        lines.add("            library = " + invokerTypeRef + ".loadNativeLibrary(context);");
        lines.add("            nativeLibrary = library;");
        lines.add("        }");
        lines.add("        return library;");
        lines.add("    }");
        lines.add("}");

        var file = processingEnv.getFiler().createSourceFile(packageName + "." + className, invokerElement);
        try (var writer = file.openWriter()) {
            writer.append(String.join(System.lineSeparator(), lines));
        }
    }

    private static List<NativeDowncallDesc> collectDowncalls(TypeElement invokerElement) throws ProcessingError {
        List<NativeDowncallDesc> result = new ArrayList<>();
        Set<String> methodNames = new java.util.HashSet<>();
        for (Element enclosedElement : invokerElement.getEnclosedElements()) {
            if (enclosedElement.getKind() == ElementKind.METHOD && enclosedElement.getAnnotation(DowncallSignature.class) != null) {
                NativeDowncallDesc downcall = extractDowncall((ExecutableElement) enclosedElement);
                if (!methodNames.add(downcall.name)) {
                    throw error(enclosedElement, "Duplicate downcall method name: %s", downcall.name);
                }
                result.add(downcall);
            }
        }
        return result;
    }

    private static NativeDowncallDesc extractDowncall(ExecutableElement method) throws ProcessingError {
        DowncallSignature annotation = method.getAnnotation(DowncallSignature.class);
        if (annotation == null) {
            throw error(method, "Downcall method must be annotated with @DowncallSignature");
        }

        NativeSimpleType[] argTypes = annotation.argumentTypes();
        if (argTypes.length != method.getParameters().size()) {
            throw error(method, "@DowncallSignature argumentTypes length must match method parameter count (%d != %d)", argTypes.length, method.getParameters().size());
        }
        validateJavaType(method, method.getReturnType(), annotation.returnType());
        for (int i = 0; i < argTypes.length; i++) {
            validateJavaType(method.getParameters().get(i), method.getParameters().get(i).asType(), argTypes[i]);
        }

        List<NativeSimpleType> argumentTypes = List.of(argTypes);
        List<String> argumentNames = extractArgumentNames(method);
        String symbolName = method.getSimpleName().toString();
        return new NativeDowncallDesc(
                        symbolName,
                        symbolName,
                        annotation.returnType(),
                        argumentTypes,
                        argumentNames);
    }

    private static List<String> extractArgumentNames(ExecutableElement method) throws ProcessingError {
        List<String> result = new ArrayList<>(method.getParameters().size());
        for (VariableElement parameter : method.getParameters()) {
            String argName = parameter.getSimpleName().toString();
            if (argName.isBlank()) {
                throw error(parameter, "Downcall parameter name must not be blank");
            }
            if (!SourceVersion.isIdentifier(argName) || SourceVersion.isKeyword(argName)) {
                throw error(parameter, "Downcall parameter name is not a valid Java identifier: %s", argName);
            }
            result.add(argName);
        }
        return result;
    }

    private static void validateJavaType(Element element, TypeMirror actualType, NativeSimpleType nativeType) throws ProcessingError {
        TypeKind expected = switch (nativeType) {
            case VOID -> TypeKind.VOID;
            case SINT8 -> TypeKind.BYTE;
            case SINT16 -> TypeKind.SHORT;
            case SINT32 -> TypeKind.INT;
            case SINT64, POINTER -> TypeKind.LONG;
            case FLOAT -> TypeKind.FLOAT;
            case DOUBLE -> TypeKind.DOUBLE;
        };
        if (actualType.getKind() != expected) {
            throw error(element, "Java type %s does not match native type %s", actualType, nativeType);
        }
    }

    private static ProcessingError error(Element element, String fmt, Object... args) throws ProcessingError {
        throw new ProcessingError(element, fmt, args);
    }

    private static String nativeSimpleTypeToJavaType(NativeSimpleType type) {
        return switch (type) {
            case VOID -> "void";
            case SINT8 -> "byte";
            case SINT16 -> "short";
            case SINT32 -> "int";
            case SINT64, POINTER -> "long";
            case FLOAT -> "float";
            case DOUBLE -> "double";
        };
    }

    private static void emitDowncallMethod(List<String> lines, NativeDowncallDesc downcall, int functionIndex) {
        lines.add("");
        lines.add("    @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)");
        lines.add("    @Override");
        lines.add("    " + nativeSimpleTypeToJavaType(downcall.returnType) + " " + downcall.name + "(" + typedArgs(downcall.argumentTypes, downcall.argumentNames) + ") {");
        lines.add("        long functionPointer = lookup(" + functionIndex + ", " + stringLiteral(downcall.symbolName) + ");");
        lines.add("        try {");
        if (NativeSimpleType.VOID == downcall.returnType) {
            lines.add("            " + methodHandleName(downcall.name) + ".invokeExact(" + invokeArgs(downcall.argumentNames) + ");");
        } else {
            lines.add("            return (" + nativeSimpleTypeToJavaType(downcall.returnType) + ") " + methodHandleName(downcall.name) + ".invokeExact(" + invokeArgs(downcall.argumentNames) + ");");
        }
        lines.add("        } catch (Throwable t) {");
        lines.add("            throw CompilerDirectives.shouldNotReachHere(t);");
        lines.add("        }");
        lines.add("    }");
    }

    private static String methodHandleName(String downcallName) {
        return NativeDowncallMethodHandleGenerator.methodHandleVarName(downcallName.toUpperCase());
    }

    private static String typedArgs(List<NativeSimpleType> argTypes, List<String> argNames) {
        List<String> args = new ArrayList<>(argTypes.size());
        for (int i = 0; i < argTypes.size(); i++) {
            args.add(nativeSimpleTypeToJavaType(argTypes.get(i)) + " " + argNames.get(i));
        }
        return String.join(", ", args);
    }

    private static String invokeArgs(List<String> argNames) {
        String args = String.join(", ", argNames);
        return args.isEmpty() ? "functionPointer" : "functionPointer, " + args;
    }

    private static String stringLiteral(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
