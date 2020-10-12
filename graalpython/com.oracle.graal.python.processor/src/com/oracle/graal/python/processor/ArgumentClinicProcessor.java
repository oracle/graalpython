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

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileObject;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.PrimitiveType;
import com.oracle.graal.python.annotations.ArgumentsClinic;
import com.oracle.graal.python.annotations.ClinicConverterFactory;
import com.oracle.graal.python.processor.ArgumentClinicModel.ArgumentClinicData;
import com.oracle.graal.python.processor.ArgumentClinicModel.BuiltinAnnotation;
import com.oracle.graal.python.processor.ArgumentClinicModel.BuiltinClinicData;
import com.oracle.graal.python.processor.CodeWriter.Block;

import static com.oracle.graal.python.processor.ConverterFactory.CLINIC_PACKAGE;

public class ArgumentClinicProcessor extends AbstractProcessor {
    private static final boolean LOGGING = false;
    private static final String BuiltinAnnotationClass = "com.oracle.graal.python.builtins.Builtin";

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        HashSet<String> vals = new HashSet<>();
        vals.add(ArgumentClinic.class.getName());
        vals.add(ArgumentsClinic.class.getName());
        vals.add(ClinicConverterFactory.class.getName());
        return vals;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }
        try {
            ConverterFactory.initBuiltins(processingEnv.getElementUtils());
            doProcess(roundEnv);
        } catch (IOException ex) {
            ex.printStackTrace();
        } catch (ProcessingError ex) {
            processingEnv.getMessager().printMessage(Kind.ERROR, ex.getMessage(), ex.getElement());
        }
        return true;
    }

    private void doProcess(RoundEnvironment roundEnv) throws IOException, ProcessingError {
        log("Running the ArgumentClinicProcessor");
        writeCode(collectEnclosingTypes(roundEnv));
    }

    @SuppressWarnings("try")
    private void writeCode(HashMap<TypeElement, Set<BuiltinClinicData>> enclosingTypes) throws IOException {
        for (Entry<TypeElement, Set<BuiltinClinicData>> enclosingType : enclosingTypes.entrySet()) {
            String pkgName = getPackage(enclosingType.getKey());
            String className = enclosingType.getKey().getSimpleName() + "ClinicProviders";
            String sourceFile = pkgName + "." + className;
            log("Generating file '%s'", sourceFile);

            JavaFileObject file = processingEnv.getFiler().createSourceFile(sourceFile);
            try (CodeWriter w = new CodeWriter(file.openWriter())) {
                w.writeLn("// CheckStyle: start generated");
                w.writeLn("// Auto generated by ArgumentClinicProcessor at %s", LocalDateTime.now());
                w.writeLn("package %s;", pkgName);
                w.writeLn();
                writeImports(w, enclosingType);
                w.writeLn();
                w.writeLn("public class %s {", className);
                for (BuiltinClinicData builtin : enclosingType.getValue()) {
                    try (Block i = w.newIndent()) {
                        writeClinicNode(w, builtin);
                    }
                }
                w.writeLn("}");
            }
        }
    }

    private void writeImports(CodeWriter w, Entry<TypeElement, Set<BuiltinClinicData>> enclosingType) throws IOException {
        log("Writing imports...");
        TreeSet<String> imports = new TreeSet<>();
        imports.add("com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode");
        imports.add(CLINIC_PACKAGE + ".ArgumentClinicProvider");
        imports.add(CLINIC_PACKAGE + ".ArgumentCastNode");
        for (BuiltinClinicData builtin : enclosingType.getValue()) {
            for (ArgumentClinicData arg : builtin.arguments) {
                imports.addAll(arg.imports);
            }
        }
        for (String pkg : imports) {
            w.writeLn("import %s;", pkg);
        }
    }

    @SuppressWarnings("try")
    private void writeClinicNode(CodeWriter w, BuiltinClinicData builtin) throws IOException {
        TypeElement type = builtin.type;
        String clinicClassName = type.getSimpleName() + "ClinicProviderGen";
        log("Writing clinic node %s", clinicClassName);
        w.writeLn("public static final class %s extends ArgumentClinicProvider {", clinicClassName);
        try (Block i1 = w.newIndent()) {

            // Static field holding the singleton and private ctor:
            w.writeLn("public static final %s INSTANCE = new %s();", clinicClassName, clinicClassName);
            w.writeLn();
            w.writeLn("private %s() {", clinicClassName);
            w.writeLn("}");

            for (PrimitiveType primitiveType : PrimitiveType.values()) {
                int[] argIndices = builtin.getIndicesForPrimitiveTypeAccepts(primitiveType);
                if (argIndices.length == 0) {
                    continue; // the default impl returns false
                }
                w.writeLn();
                w.writeLn("@Override");
                w.writeLn("public boolean accepts%s(int argIndex) {", primitiveType.toString());
                try (Block i2 = w.newIndent()) {
                    if (builtin.containsAllArguments(argIndices)) {
                        w.writeLn("return true;");
                    } else {
                        w.startLn().write("return ").writeEach(argIndices, " || ", "argIndex == %d").endLn(";");
                    }
                }
                w.writeLn("}");
            }

            int[] hasCastNodeArgsIndices = builtin.getIndicesForHasCastNode();
            if (hasCastNodeArgsIndices.length > 0) {
                w.writeLn();
                w.writeLn("@Override");
                w.writeLn("public boolean hasCastNode(int argIndex) {");
                try (Block i2 = w.newIndent()) {
                    if (builtin.containsAllArguments(hasCastNodeArgsIndices)) {
                        w.writeLn("return true;");
                    } else {
                        w.startLn().write("return ").writeEach(hasCastNodeArgsIndices, " || ", "argIndex == %d").endLn(";");
                    }
                }
                w.writeLn("}");
            }

            ArgumentClinicData[] argsWithCastNodeFactory = builtin.getArgumentsWithCastNodeFactory();
            if (argsWithCastNodeFactory.length > 0) {
                w.writeLn();
                w.writeLn("@Override");
                w.writeLn("public ArgumentCastNode createCastNode(int argIndex, PythonBuiltinBaseNode builtin) {");
                try (Block i2 = w.newIndent()) {
                    w.writeLn("switch (argIndex) {");
                    try (Block i3 = w.newIndent()) {
                        for (ArgumentClinicData arg : argsWithCastNodeFactory) {
                            w.writeLn("case %d: return %s;", arg.index, arg.castNodeFactory);
                        }
                        w.writeLn("default: throw new IllegalStateException(\"Unexpected argument index: \" + Integer.toString(argIndex));");
                    }
                    w.writeLn("}");
                }
                w.writeLn("}");
            }
        }
        w.writeLn("}");
    }

    private HashMap<TypeElement, Set<BuiltinClinicData>> collectEnclosingTypes(RoundEnvironment roundEnv) throws ProcessingError {
        HashMap<TypeElement, Set<BuiltinClinicData>> enclosingTypes = new HashMap<>();
        HashSet<Element> elements = new HashSet<>(roundEnv.getElementsAnnotatedWith(ArgumentsClinic.class));
        elements.addAll(roundEnv.getElementsAnnotatedWith(ArgumentClinic.class));
        for (Element e : elements) {
            log("Checking type '%s'", e);
            if (e.getKind() != ElementKind.CLASS) {
                throw error(e, "ArgumentClinic annotation is applicable only to classes.");
            }
            TypeElement type = (TypeElement) e;
            if (type.getEnclosingElement() == null) {
                throw error(e, "ArgumentClinicProcessor supports only inner classes at moment.");
            }
            BuiltinClinicData builtinClinicData = getBuiltinClinicData(type, getBuiltinAnnotation(type));
            TypeElement enclosingType = (TypeElement) type.getEnclosingElement();
            enclosingTypes.computeIfAbsent(enclosingType, k -> new HashSet<>()).add(builtinClinicData);
        }
        return enclosingTypes;
    }

    private BuiltinClinicData getBuiltinClinicData(TypeElement type, BuiltinAnnotation builtinAnnotation) throws ProcessingError {
        ArgumentClinic[] rawArgAnnotations;
        ArgumentsClinic argsClinicAnnotation = type.getAnnotation(ArgumentsClinic.class);
        if (argsClinicAnnotation == null) {
            rawArgAnnotations = new ArgumentClinic[]{type.getAnnotation(ArgumentClinic.class)};
        } else {
            rawArgAnnotations = argsClinicAnnotation.value();
        }

        Map<String, ConverterFactory> converterFactories = getConverterFactories(type);
        String[] argNames = builtinAnnotation.argumentNames;
        List<ArgumentClinicData> arguments = new ArrayList<>(argNames.length);
        for (int i = 0; i < argNames.length; i++) {
            String name = argNames[i];
            ArgumentClinic clinicAnnotation = Arrays.stream(rawArgAnnotations).filter(x -> x.name().equals(name)).findFirst().orElse(null);
            arguments.add(ArgumentClinicData.create(clinicAnnotation, type, builtinAnnotation, i, converterFactories.get(name)));
        }

        return new BuiltinClinicData(type, builtinAnnotation, arguments);
    }

    private Map<String, ConverterFactory> getConverterFactories(TypeElement type) throws ProcessingError {
        List<AnnotationMirror> rawArgMirrors;
        AnnotationMirror argsClinicMirror = findAnnotationMirror(type, ArgumentsClinic.class.getCanonicalName());
        if (argsClinicMirror != null) {
            rawArgMirrors = ((List<?>) getAnnotationValue(argsClinicMirror, "value").getValue()).stream().map(av -> (AnnotationMirror) ((AnnotationValue) av).getValue()).collect(Collectors.toList());
        } else {
            rawArgMirrors = Collections.singletonList(findAnnotationMirror(type, ArgumentClinic.class.getCanonicalName()));
        }

        Map<String, ConverterFactory> converterFactories = new HashMap<>();
        for (AnnotationMirror m : rawArgMirrors) {
            String name = (String) getAnnotationValue(m, "name").getValue();
            AnnotationValue v = findAnnotationValue(m, "conversionClass");
            if (v != null) {
                TypeElement conversionClass = (TypeElement) processingEnv.getTypeUtils().asElement((TypeMirror) v.getValue());
                converterFactories.put(name, ConverterFactory.getForClass(conversionClass));
            }
        }
        return converterFactories;
    }

    private static AnnotationMirror findAnnotationMirror(TypeElement type, String annotationQualifiedName) {
        for (AnnotationMirror annot : type.getAnnotationMirrors()) {
            String name = ((TypeElement) annot.getAnnotationType().asElement()).getQualifiedName().toString();
            if (name.equals(annotationQualifiedName)) {
                return annot;
            }
        }
        return null;
    }

    private static AnnotationValue findAnnotationValue(AnnotationMirror annotationMirror, String key) {
        for (Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : annotationMirror.getElementValues().entrySet()) {
            if (entry.getKey().getSimpleName().toString().equals(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static AnnotationValue getAnnotationValue(AnnotationMirror annotationMirror, String key) {
        AnnotationValue v = findAnnotationValue(annotationMirror, key);
        if (v == null) {
            throw new IllegalStateException("Annotation value `" + key + "` not found");
        }
        return v;
    }

    @SuppressWarnings("unchecked")
    private static BuiltinAnnotation getBuiltinAnnotation(TypeElement type) throws ProcessingError {
        String builtinName = null;
        Stream<?> parameterNames = null;
        Stream<?> keywordOnlyNames = null;
        AnnotationMirror annot = findAnnotationMirror(type, BuiltinAnnotationClass);
        if (annot != null) {
            for (Entry<? extends ExecutableElement, ? extends AnnotationValue> item : annot.getElementValues().entrySet()) {
                if (item.getKey().getSimpleName().toString().equals("parameterNames")) {
                    parameterNames = ((List<AnnotationValue>) item.getValue().getValue()).stream().map(AnnotationValue::getValue);
                } else if (item.getKey().getSimpleName().toString().equals("keywordOnlyNames")) {
                    keywordOnlyNames = ((List<AnnotationValue>) item.getValue().getValue()).stream().map(AnnotationValue::getValue);
                } else if (item.getKey().getSimpleName().toString().equals("name")) {
                    builtinName = (String) item.getValue().getValue();
                }
            }
        }
        if (parameterNames == null || builtinName == null) {
            throw error(type, "In order to use Argument Clinic, the Builtin annotation must contain 'name' and 'parameterNames' fields.");
        }
        if (keywordOnlyNames != null) {
            parameterNames = Stream.concat(parameterNames, keywordOnlyNames);
        }
        return new BuiltinAnnotation(builtinName, parameterNames.toArray(String[]::new));
    }

    private static ProcessingError error(Element element, String fmt, Object... args) throws ProcessingError {
        throw new ProcessingError(element, fmt, args);
    }

    private void log(String fmt, Object... args) {
        if (LOGGING) {
            String msg = "ArgumentClinicProcessor: " + String.format(fmt, args);
            processingEnv.getMessager().printMessage(Kind.NOTE, msg);
        }
    }

    private static String getPackage(TypeElement type) {
        return getPackage(type.getQualifiedName().toString());
    }

    private static String getPackage(String qname) {
        int idx = qname.lastIndexOf('.');
        assert idx > 0 : qname;
        return qname.substring(0, idx);
    }
}
