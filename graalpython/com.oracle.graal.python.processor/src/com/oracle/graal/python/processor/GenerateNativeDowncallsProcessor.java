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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic.Kind;

import com.oracle.graal.python.annotations.DowncallSignature;
import com.oracle.graal.python.annotations.GenerateNativeDowncalls;
import com.oracle.graal.python.annotations.NativeSimpleType;

public class GenerateNativeDowncallsProcessor extends AbstractProcessor {
    private record NativeDowncallDesc(String name, String symbolName, String returnType, List<String> argumentTypes, List<String> argumentNames) {
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(GenerateNativeDowncalls.class.getName(), DowncallSignature.class.getName());
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
        validateDowncallSignatures(roundEnv);
        for (Element element : roundEnv.getElementsAnnotatedWith(GenerateNativeDowncalls.class)) {
            if (element.getKind() != ElementKind.ENUM) {
                throw error(element, "Can only annotate enums with @GenerateNativeDowncalls");
            }
            generateInvoker((TypeElement) element);
        }
    }

    private static void validateDowncallSignatures(RoundEnvironment roundEnv) throws ProcessingError {
        for (Element element : roundEnv.getElementsAnnotatedWith(DowncallSignature.class)) {
            if (element.getKind() != ElementKind.ENUM_CONSTANT) {
                throw error(element, "@DowncallSignature can only annotate enum constants");
            }
            Element enclosingElement = element.getEnclosingElement();
            if (enclosingElement == null || enclosingElement.getKind() != ElementKind.ENUM) {
                throw error(element, "@DowncallSignature can only annotate enum constants");
            }
            if (enclosingElement.getAnnotation(GenerateNativeDowncalls.class) == null) {
                throw error(element, "Enum constants annotated with @DowncallSignature must be enclosed in an enum annotated with @GenerateNativeDowncalls");
            }
        }
    }

    private void generateInvoker(TypeElement enumElement) throws IOException, ProcessingError {
        GenerateNativeDowncalls annotation = enumElement.getAnnotation(GenerateNativeDowncalls.class);
        List<NativeDowncallDesc> downcalls = collectDowncalls(enumElement);
        if (downcalls.isEmpty()) {
            throw error(enumElement, "Annotated enum does not declare any downcalls");
        }

        String packageName = processingEnv.getElementUtils().getPackageOf(enumElement).getQualifiedName().toString();
        String enumQualifiedName = enumElement.getQualifiedName().toString();
        String enumTypeRef = enumQualifiedName.startsWith(packageName + ".") ? enumQualifiedName.substring(packageName.length() + 1) : enumQualifiedName;
        String className = annotation.generatedClassName();

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
        lines.add("final class " + className + " {");
        lines.add("    private final PythonContext context;");
        lines.add("    private final AtomicLongArray cachedFunctions = new AtomicLongArray(" + enumTypeRef + ".values().length);");
        lines.add("    private volatile NativeLibrary nativeLibrary;");
        lines.add("");

        for (NativeDowncallDesc downcall : downcalls) {
            NativeDowncallMethodHandleGenerator.emitMethodHandleField(lines, methodHandleName(downcall.name), downcall.returnType, downcall.argumentTypes);
        }

        lines.add("");
        lines.add("    " + className + "(PythonContext context) {");
        lines.add("        this.context = context;");
        lines.add("    }");

        for (NativeDowncallDesc downcall : downcalls) {
            emitDowncallMethod(lines, enumTypeRef, downcall);
        }

        lines.add("");
        lines.add("    @TruffleBoundary");
        lines.add("    private long lookup(" + enumTypeRef + " function, String symbolName) {");
        lines.add("        long symbol = cachedFunctions.get(function.ordinal());");
        lines.add("        if (symbol == 0) {");
        lines.add("            symbol = loadFunction(function, symbolName);");
        lines.add("            cachedFunctions.compareAndSet(function.ordinal(), 0, symbol);");
        lines.add("            symbol = cachedFunctions.get(function.ordinal());");
        lines.add("        }");
        lines.add("        return symbol;");
        lines.add("    }");
        lines.add("");
        lines.add("    @TruffleBoundary");
        lines.add("    private long loadFunction(" + enumTypeRef + " function, String symbolName) {");
        lines.add("        return ensureLibrary().lookupSymbol(symbolName);");
        lines.add("    }");
        lines.add("");
        lines.add("    @TruffleBoundary");
        lines.add("    private NativeLibrary ensureLibrary() {");
        lines.add("        NativeLibrary library = nativeLibrary;");
        lines.add("        if (library == null) {");
        lines.add("            library = " + enumTypeRef + ".loadNativeLibrary(context);");
        lines.add("            nativeLibrary = library;");
        lines.add("        }");
        lines.add("        return library;");
        lines.add("    }");
        lines.add("}");

        var file = processingEnv.getFiler().createSourceFile(packageName + "." + className, enumElement);
        try (var writer = file.openWriter()) {
            writer.append(String.join(System.lineSeparator(), lines));
        }
    }

    private static List<NativeDowncallDesc> collectDowncalls(TypeElement enumElement) throws ProcessingError {
        List<NativeDowncallDesc> result = new ArrayList<>();
        for (Element enclosedElement : enumElement.getEnclosedElements()) {
            if (enclosedElement.getKind() == ElementKind.ENUM_CONSTANT) {
                result.add(extractDowncall((VariableElement) enclosedElement));
            }
        }
        return result;
    }

    private static NativeDowncallDesc extractDowncall(VariableElement enumConstant) throws ProcessingError {
        DowncallSignature annotation = enumConstant.getAnnotation(DowncallSignature.class);
        if (annotation == null) {
            throw error(enumConstant, "Enum constant in @GenerateNativeDowncalls enum must be annotated with @DowncallSignature");
        }

        NativeSimpleType[] argTypes = annotation.argTypes();
        List<String> argumentTypes = new ArrayList<>(argTypes.length);
        for (NativeSimpleType argType : argTypes) {
            argumentTypes.add(nativeSimpleTypeToJavaType(argType));
        }

        List<String> argumentNames = extractArgumentNames(enumConstant, argTypes.length, annotation.argNames());
        String symbolName = annotation.symbol().isBlank() ? enumConstant.getSimpleName().toString() : annotation.symbol();
        return new NativeDowncallDesc(
                        enumConstant.getSimpleName().toString(),
                        symbolName,
                        nativeSimpleTypeToJavaType(annotation.returns()),
                        argumentTypes,
                        argumentNames);
    }

    private static List<String> extractArgumentNames(VariableElement enumConstant, int argCount, String[] argNames) throws ProcessingError {
        if (argNames.length == 0) {
            if (argCount > 26) {
                throw error(enumConstant, "Generated downcall stubs support at most 26 synthetic argument names (a-z); specify argNames explicitly for %d parameters", argCount);
            }
            List<String> generatedNames = new ArrayList<>(argCount);
            for (int i = 0; i < argCount; i++) {
                generatedNames.add(NativeDowncallMethodHandleGenerator.argName(i));
            }
            return generatedNames;
        }
        if (argNames.length != argCount) {
            throw error(enumConstant, "@DowncallSignature argNames length must match argTypes length (%d != %d)", argNames.length, argCount);
        }
        HashSet<String> seenNames = new HashSet<>();
        List<String> result = new ArrayList<>(argCount);
        for (String argName : argNames) {
            if (argName.isBlank()) {
                throw error(enumConstant, "@DowncallSignature argNames must not contain blank names");
            }
            if (!SourceVersion.isIdentifier(argName) || SourceVersion.isKeyword(argName)) {
                throw error(enumConstant, "@DowncallSignature argName is not a valid Java identifier: %s", argName);
            }
            if (!seenNames.add(argName)) {
                throw error(enumConstant, "@DowncallSignature argNames must be unique: %s", argName);
            }
            result.add(argName);
        }
        return result;
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

    private static void emitDowncallMethod(List<String> lines, String enumQualifiedName, NativeDowncallDesc downcall) {
        lines.add("");
        lines.add("    @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)");
        lines.add("    " + downcall.returnType + " " + downcall.name + "(" + typedArgs(downcall.argumentTypes, downcall.argumentNames) + ") {");
        lines.add("        long functionPointer = lookup(" + enumQualifiedName + "." + downcall.name + ", " + stringLiteral(downcall.symbolName) + ");");
        lines.add("        try {");
        if ("void".equals(downcall.returnType)) {
            lines.add("            " + methodHandleName(downcall.name) + ".invokeExact(" + invokeArgs(downcall.argumentNames) + ");");
        } else {
            lines.add("            return (" + downcall.returnType + ") " + methodHandleName(downcall.name) + ".invokeExact(" + invokeArgs(downcall.argumentNames) + ");");
        }
        lines.add("        } catch (Throwable t) {");
        lines.add("            throw CompilerDirectives.shouldNotReachHere(t);");
        lines.add("        }");
        lines.add("    }");
    }

    private static String methodHandleName(String downcallName) {
        return NativeDowncallMethodHandleGenerator.methodHandleVarName(downcallName.toUpperCase());
    }

    private static String typedArgs(List<String> argTypes, List<String> argNames) {
        List<String> args = new ArrayList<>(argTypes.size());
        for (int i = 0; i < argTypes.size(); i++) {
            args.add(argTypes.get(i) + " " + argNames.get(i));
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
