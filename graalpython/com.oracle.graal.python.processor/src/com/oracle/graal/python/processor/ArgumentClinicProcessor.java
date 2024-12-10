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

import static com.oracle.graal.python.processor.ConverterFactory.CLINIC_PACKAGE;

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
import com.oracle.graal.python.annotations.ClinicBuiltinBaseClass;
import com.oracle.graal.python.annotations.ClinicConverterFactory;
import com.oracle.graal.python.processor.ArgumentClinicModel.ArgumentClinicData;
import com.oracle.graal.python.processor.ArgumentClinicModel.BuiltinAnnotation;
import com.oracle.graal.python.processor.ArgumentClinicModel.BuiltinClinicData;
import com.oracle.graal.python.processor.CodeWriter.Block;

public class ArgumentClinicProcessor extends AbstractProcessor {
    private static final boolean LOGGING = false;
    private static final String BuiltinAnnotationClass = "com.oracle.graal.python.builtins.Builtin";
    private static final String BuiltinsAnnotationClass = "com.oracle.graal.python.builtins.Builtins";
    private static final String BUILTINS_BASE_CLASSES_PACKAGE = "com.oracle.graal.python.nodes.function.builtins";

    private Element[] clinicBuiltinBaseClasses;

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(ArgumentClinic.class.getName(), ArgumentsClinic.class.getName(), ClinicConverterFactory.class.getName());
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
            ConverterFactory.initBuiltins(processingEnv.getElementUtils());
            clinicBuiltinBaseClasses = getClinicBuiltinBases(roundEnv);
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
        imports.add(CLINIC_PACKAGE + ".ArgumentClinicProvider");
        for (BuiltinClinicData builtin : enclosingType.getValue()) {
            for (ArgumentClinicData arg : builtin.arguments) {
                imports.addAll(arg.imports);
                if (arg.castNodeFactory != null) {
                    imports.add("com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode");
                    imports.add(CLINIC_PACKAGE + ".ArgumentCastNode");
                }
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
            try (Block i2 = w.newIndent()) {
                StringBuilder superConstructor = new StringBuilder("super(");

                for (PrimitiveType primitiveType : PrimitiveType.values()) {
                    int bits = 0;
                    for (int index : builtin.getIndicesForPrimitiveTypeAccepts(primitiveType)) {
                        bits |= 1 << index;
                    }
                    superConstructor.append("0x").append(Integer.toHexString(bits)).append(", ");
                }
                int bits = 0;
                for (int index : builtin.getIndicesForHasCastNode()) {
                    bits |= 1 << index;
                }
                superConstructor.append("0x").append(Integer.toHexString(bits)).append(");");
                w.writeLn(superConstructor.toString());
            }
            w.writeLn("}");

            ArgumentClinicData[] argsWithCastNodeFactory = builtin.getArgumentsWithCastNodeFactory();
            if (argsWithCastNodeFactory.length > 0) {
                w.writeLn();
                w.writeLn("@Override");
                w.writeLn("public ArgumentCastNode createCastNode(int argIndex, PythonBuiltinBaseNode builtin) {");
                try (Block i2 = w.newIndent()) {
                    if (argsWithCastNodeFactory.length == 1) {
                        // use an "if" when there's only one option
                        w.writeLn("if (argIndex == %d) {", argsWithCastNodeFactory[0].index);
                        try (Block i3 = w.newIndent()) {
                            w.writeLn("return %s;", argsWithCastNodeFactory[0].castNodeFactory);
                        }
                        w.writeLn("}");
                    } else {
                        w.writeLn("switch (argIndex) {");
                        try (Block i3 = w.newIndent()) {
                            for (ArgumentClinicData arg : argsWithCastNodeFactory) {
                                w.writeLn("case %d: return %s;", arg.index, arg.castNodeFactory);
                            }
                        }
                        w.writeLn("}");
                    }
                    w.writeLn("return super.createCastNode(argIndex, builtin);");
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

            checkClinicBuiltinBaseClass(clinicBuiltinBaseClasses, type);
            BuiltinClinicData builtinClinicData = getBuiltinClinicData(type, getBuiltinAnnotation(type));
            TypeElement enclosingType = (TypeElement) type.getEnclosingElement();
            enclosingTypes.computeIfAbsent(enclosingType, k -> new HashSet<>()).add(builtinClinicData);
        }
        return enclosingTypes;
    }

    private Element[] getClinicBuiltinBases(RoundEnvironment roundEnv) throws ProcessingError {
        // To better support incremental compilation we do not ask the roundEnv, but scan known
        // package that should contain all clinic builtin base classes. We also report error on any
        // @ClinicBuiltinBaseClass annotated class that is not in that package
        Set<? extends Element> clinicBuiltinsBasesInEnv = roundEnv.getElementsAnnotatedWith(ClinicBuiltinBaseClass.class);
        for (Element x : clinicBuiltinsBasesInEnv) {
            if (!((TypeElement) x).getQualifiedName().toString().startsWith(BUILTINS_BASE_CLASSES_PACKAGE)) {
                throw new ProcessingError(x, "All @ClinicBuiltinBaseClass classes should be in package %s.", BUILTINS_BASE_CLASSES_PACKAGE);
            }
        }
        return processingEnv.getElementUtils().getPackageElement(BUILTINS_BASE_CLASSES_PACKAGE).getEnclosedElements().stream().filter(
                        x -> x.getAnnotation(ClinicBuiltinBaseClass.class) != null).toArray(Element[]::new);
    }

    private BuiltinClinicData getBuiltinClinicData(TypeElement type, BuiltinAnnotation builtinAnnotation) throws ProcessingError {
        ArrayList<ArgumentClinic> rawArgAnnotations;
        ArgumentsClinic argsClinicAnnotation = type.getAnnotation(ArgumentsClinic.class);
        if (argsClinicAnnotation == null) {
            rawArgAnnotations = new ArrayList<>(1);
            rawArgAnnotations.add(type.getAnnotation(ArgumentClinic.class));
        } else {
            rawArgAnnotations = new ArrayList<>(Arrays.asList(argsClinicAnnotation.value()));
        }

        Map<String, ConverterFactory[]> converterFactories = getConverterFactories(type);
        String[] argNames = builtinAnnotation.argumentNames;
        List<ArgumentClinicData> arguments = new ArrayList<>(argNames.length);
        for (int i = 0; i < argNames.length; i++) {
            String name = argNames[i];
            ArgumentClinic clinicAnnotation = rawArgAnnotations.stream().filter(x -> x.name().equals(name)).findFirst().orElse(null);
            if (clinicAnnotation != null) {
                rawArgAnnotations.remove(clinicAnnotation);
            }
            arguments.add(ArgumentClinicData.create(clinicAnnotation, type, builtinAnnotation, i, converterFactories.get(name)));
        }

        if (rawArgAnnotations.size() != 0) {
            throw new ProcessingError(type, "The builtin is annotated with argument clinic for arguments that the builtin does no take: %s.",
                            rawArgAnnotations.stream().map(ArgumentClinic::name).collect(Collectors.joining(", ")));
        }

        return new BuiltinClinicData(type, builtinAnnotation, arguments);
    }

    private Map<String, ConverterFactory[]> getConverterFactories(TypeElement type) throws ProcessingError {
        List<AnnotationMirror> rawArgMirrors;
        AnnotationMirror argsClinicMirror = findAnnotationMirror(type, ArgumentsClinic.class.getCanonicalName());
        if (argsClinicMirror != null) {
            rawArgMirrors = ((List<?>) getAnnotationValue(argsClinicMirror, "value").getValue()).stream().map(av -> (AnnotationMirror) ((AnnotationValue) av).getValue()).collect(Collectors.toList());
        } else {
            rawArgMirrors = Collections.singletonList(findAnnotationMirror(type, ArgumentClinic.class.getCanonicalName()));
        }

        Map<String, ConverterFactory[]> converterFactories = new HashMap<>();
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

    private void checkClinicBuiltinBaseClass(Element[] clinicBuiltinBases, TypeElement type) throws ProcessingError {
        boolean hasCorrectBaseClass = false;
        for (Element baseClass : clinicBuiltinBases) {
            if (processingEnv.getTypeUtils().isSubtype(type.asType(), baseClass.asType())) {
                hasCorrectBaseClass = true;
            }
        }
        if (!hasCorrectBaseClass) {
            throw error(type, "Argument clinic annotated node must inherit from @ClinicBuiltinBaseClass annotated base class, e.g., PythonBinaryClinicBuiltinNode.");
        }
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
        int minNumOfPositionalArgs = -1;
        boolean takesVarArgs = false;
        AnnotationMirror annot = findAnnotationMirror(type, BuiltinAnnotationClass);
        if (annot == null) {
            annot = findAnnotationMirror(type, BuiltinsAnnotationClass);
            if (annot != null) {
                // Multiple builtin annotations, we will use the first. The
                // number of arguments must be compatible, otherwise the node
                // will not work anyway. The user is in charge of keeping the
                // argument names consistent.
                for (Entry<? extends ExecutableElement, ? extends AnnotationValue> item : annot.getElementValues().entrySet()) {
                    if (item.getKey().getSimpleName().toString().equals("value")) {
                        try {
                            Object value = ((List<AnnotationValue>) item.getValue().getValue()).get(0);
                            if (value instanceof AnnotationValue) {
                                value = ((AnnotationValue) value).getValue();
                            }
                            annot = (AnnotationMirror) value;
                        } catch (ClassCastException e) {
                            throw new RuntimeException("class " + type, e);
                        }
                        break;
                    }
                }
            }
        }
        if (annot != null) {
            for (Entry<? extends ExecutableElement, ? extends AnnotationValue> item : annot.getElementValues().entrySet()) {
                if (item.getKey().getSimpleName().toString().equals("parameterNames")) {
                    parameterNames = ((List<AnnotationValue>) item.getValue().getValue()).stream().map(AnnotationValue::getValue);
                } else if (item.getKey().getSimpleName().toString().equals("keywordOnlyNames")) {
                    keywordOnlyNames = ((List<AnnotationValue>) item.getValue().getValue()).stream().map(AnnotationValue::getValue);
                } else if (item.getKey().getSimpleName().toString().equals("name")) {
                    builtinName = (String) item.getValue().getValue();
                } else if (item.getKey().getSimpleName().toString().equals("minNumOfPositionalArgs")) {
                    minNumOfPositionalArgs = (int) item.getValue().getValue();
                } else if (item.getKey().getSimpleName().toString().equals("takesVarArgs")) {
                    takesVarArgs = (boolean) item.getValue().getValue();
                }
            }
        }
        if ((parameterNames == null && keywordOnlyNames == null) || builtinName == null) {
            throw error(type, "In order to use Argument Clinic, the Builtin annotation must contain 'name' and 'parameterNames' and/or 'keywordOnlyNames' fields.");
        }
        if (parameterNames == null) {
            parameterNames = Stream.of();
        }
        if (takesVarArgs) {
            parameterNames = Stream.concat(parameterNames, Stream.of("*args"));
        }
        if (keywordOnlyNames != null) {
            parameterNames = Stream.concat(parameterNames, keywordOnlyNames);
        }
        return new BuiltinAnnotation(builtinName, parameterNames.toArray(String[]::new), minNumOfPositionalArgs);
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
