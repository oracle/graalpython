/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.AbstractAnnotationValueVisitor14;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;
import javax.tools.StandardLocation;

import com.oracle.graal.python.annotations.CApiConstants;
import com.oracle.graal.python.annotations.CApiExternalFunctionSignatures;
import com.oracle.graal.python.annotations.CApiFields;
import com.oracle.graal.python.annotations.CApiStructs;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

public class CApiBuiltinsProcessor extends AbstractProcessor {
    private static final String TRUFFLE_VIRTUAL_FRAME = "com.oracle.truffle.api.frame.VirtualFrame";
    private static final String TRUFFLE_NODE = "com.oracle.truffle.api.nodes.Node";
    private static final String NFI_BOUND_FUNCTION = "com.oracle.graal.python.nfi2.NfiBoundFunction";
    private static final String PYTHON_CONTEXT = "com.oracle.graal.python.runtime.PythonContext";
    private static final String TARGET_PACKAGE = "com.oracle.graal.python.builtins.modules.cext";

    private static class ArgDescriptorsTreeScanner extends TreePathScanner<Object, Trees> {
        private Map<String, String> initializerMap;

        @Override
        public Object visitVariable(VariableTree variableTree, Trees trees) {
            var initializer = variableTree.getInitializer();
            if (initializer != null) {
                initializerMap.put(variableTree.getName().toString(), initializer.toString());
            }
            return super.visitVariable(variableTree, trees);
        }
    }

    private Map<String, String> signatureToArgDescriptor = new HashMap<>();
    private Map<String, String> argDescriptorToInitializer = new HashMap<>();

    private Map<String, String> externalFunctionSignatureToInitializer = new HashMap<>();

    private Trees trees;

    private String getFieldInitializer(VariableElement theField) {
        if (trees == null) {
            return "";
        }
        if (argDescriptorToInitializer.isEmpty()) {
            // lazily initialize all arg descriptors in a single scan
            var codeScanner = new ArgDescriptorsTreeScanner();
            var tp = trees.getPath(theField.getEnclosingElement());
            codeScanner.initializerMap = argDescriptorToInitializer;
            codeScanner.scan(tp, this.trees);
            for (var e : argDescriptorToInitializer.entrySet()) {
                var signature = getCSignature(e.getValue());
                signatureToArgDescriptor.putIfAbsent(signature, e.getKey());
            }
        }
        return argDescriptorToInitializer.get(name(theField));
    }

    private String getExternalFunctionSignatureInitializer(VariableElement theField) {
        if (trees == null) {
            return "";
        }
        if (externalFunctionSignatureToInitializer.isEmpty()) {
            // lazily initialize all external function signatures in a single scan
            var codeScanner = new ArgDescriptorsTreeScanner();
            var tp = trees.getPath(theField.getEnclosingElement());
            codeScanner.initializerMap = externalFunctionSignatureToInitializer;
            codeScanner.scan(tp, this.trees);
        }
        return externalFunctionSignatureToInitializer.get(name(theField));

    }

    @Override
    public synchronized void init(ProcessingEnvironment pe) {
        super.init(pe);
        try {
            this.trees = Trees.instance(pe);
        } catch (Throwable t) {
            // ECJ does not support this, so we skip the processing of C API builtins
            pe.getMessager().printMessage(Kind.NOTE, "The compiler does not support source tree parsing during annotation processing. Regeneration of Python C API builtins will be incorrect.");
            this.trees = null;
        }
    }

    private String getCSignature(VariableElement obj) {
        return getCSignature(getFieldInitializer(obj));
    }

    private static String getCSignature(String initializer) {
        // assumes that the C signature is the first literal string in the initializer
        var parts = initializer.split("\"");
        if (parts.length < 2) {
            return "";
        } else {
            return initializer.split("\"")[1];
        }
    }

    private static boolean isVarArgs(VariableElement obj) {
        return name(obj).equals("VARARGS");
    }

    private boolean isValidReturnType(VariableElement obj) {
        /*
         * We don't want to allow "bare" PyObject and force ourselves to decide between
         * PyObjectTransfer and PyObjectBorrow
         */
        var initializer = getFieldInitializer(obj);
        // assumes that the 'transfer' flag is the only bool flag and defaults to false
        return !initializer.matches("ArgBehavior\\.PyObject[,)]") || initializer.contains("true");
    }

    private static String name(Element obj) {
        return obj.getSimpleName().toString();
    }

    private static final class CApiBuiltinDesc {
        public final Element origin;
        public final String name;
        public final VariableElement[] arguments;
        public final VariableElement returnType;
        public final boolean acquireGil;
        public final boolean canRaise;
        public final String call;
        public final String factory;
        public int id;

        public CApiBuiltinDesc(Element origin, String name, VariableElement returnType, VariableElement[] arguments, boolean acquireGil, boolean canRaise, String call, String factory) {
            this.origin = origin;
            this.name = name;
            this.returnType = returnType;
            this.arguments = arguments;
            this.acquireGil = acquireGil;
            this.canRaise = canRaise;
            this.call = call;
            this.factory = factory;
        }
    }

    private String capiTypeToLogicalType(VariableElement element) {
        var init = getFieldInitializer(element);
        if (init.contains("Void")) {
            return "void";
        } else if (init.contains("Float64")) {
            return "double";
        } else if (init.contains("Float32")) {
            return "float";
        } else if (init.contains("Int32")) {
            return "int";
        } else if (init.contains("Char16")) {
            return "short";
        } else if (init.contains("Char8")) {
            return "byte";
        } else if (init.contains("PyObject") || init.contains("Pointer")) {
            return "pointer";
        } else {
            return "long";
        }
    }

    private boolean isVoid(VariableElement element) {
        return capiTypeToLogicalType(element).equals("void");
    }

    private String capiTypeToErrorValue(VariableElement element) {
        if (capiTypeToLogicalType(element).equals("pointer")) {
            return "0";
        } else {
            return "-1";
        }
    }

    private String capiTypeToJavaPrimitiveType(VariableElement element) {
        var type = capiTypeToLogicalType(element);
        return type.equals("pointer") ? "long" : type;
    }

    private String capiTypeToForeignPrimitiveType(VariableElement element) {
        String type = capiTypeToJavaPrimitiveType(element);
        return type.equals("void") ? "void" : ("j" + type);
    }

    private static String argName(int i) {
        return "" + (char) ('a' + i);
    }

    private static final String CAPI_BUILTIN = "com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin";
    private static final String CAPI_BUILTINS = "com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltins";
    private static final String CAPI_WRAPPER_DESCRIPTOR = "com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.CApiWrapperDescriptor";
    private static final String INVOKE_EXTERNAL_FUNCTION = "com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.InvokeExternalFunction";

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(CAPI_BUILTIN, CAPI_BUILTINS, CApiFields.class.getName(), CApiConstants.class.getName(), CApiStructs.class.getName(), CApiExternalFunctionSignatures.class.getName());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    private static AnnotationMirror findAnnotationMirror(Element el, String annotationQualifiedName) {
        for (AnnotationMirror annot : el.getAnnotationMirrors()) {
            String name = ((TypeElement) annot.getAnnotationType().asElement()).getQualifiedName().toString();
            if (name.equals(annotationQualifiedName)) {
                return annot;
            }
        }
        return null;
    }

    private static final class AnnotationValueExtractVisitor<T> extends AbstractAnnotationValueVisitor14<List<T>, Class<T>> {
        public List<T> visitBoolean(boolean b, Class<T> p) {
            if (p == Boolean.class) {
                return List.of(p.cast(b));
            } else {
                return null;
            }
        }

        public List<T> visitByte(byte b, Class<T> p) {
            if (p == Byte.class) {
                return List.of(p.cast(b));
            } else {
                return null;
            }
        }

        public List<T> visitChar(char c, Class<T> p) {
            if (p == Character.class) {
                return List.of(p.cast(c));
            } else {
                return null;
            }
        }

        public List<T> visitDouble(double d, Class<T> p) {
            if (p == Double.class) {
                return List.of(p.cast(d));
            } else {
                return null;
            }
        }

        public List<T> visitFloat(float f, Class<T> p) {
            if (p == Float.class) {
                return List.of(p.cast(f));
            } else {
                return null;
            }
        }

        public List<T> visitInt(int i, Class<T> p) {
            if (p == Integer.class) {
                return List.of(p.cast(i));
            } else {
                return null;
            }
        }

        public List<T> visitLong(long i, Class<T> p) {
            if (p == Long.class) {
                return List.of(p.cast(i));
            } else {
                return null;
            }
        }

        public List<T> visitShort(short s, Class<T> p) {
            if (p == Short.class) {
                return List.of(p.cast(s));
            } else {
                return null;
            }
        }

        public List<T> visitString(String s, Class<T> p) {
            if (p == String.class) {
                return List.of(p.cast(s));
            } else {
                return null;
            }
        }

        public List<T> visitType(TypeMirror t, Class<T> p) {
            if (p == TypeMirror.class) {
                return List.of(p.cast(t));
            } else {
                return null;
            }
        }

        public List<T> visitEnumConstant(VariableElement c, Class<T> p) {
            if (p == VariableElement.class) {
                return List.of(p.cast(c));
            } else {
                return null;
            }
        }

        public List<T> visitAnnotation(AnnotationMirror a, Class<T> p) {
            if (p == AnnotationMirror.class) {
                return List.of(p.cast(a));
            } else {
                return null;
            }
        }

        public List<T> visitArray(List<? extends AnnotationValue> vals, Class<T> p) {
            List<T> results = new ArrayList<>();
            for (var val : vals) {
                results.addAll(val.accept(this, p));
            }
            return results;
        }
    }

    private <T> List<T> findValues(AnnotationMirror am, String key, Class<T> type) {
        for (var es : processingEnv.getElementUtils().getElementValuesWithDefaults(am).entrySet()) {
            if (es.getKey().getSimpleName().toString().equals(key)) {
                var v = new AnnotationValueExtractVisitor<T>();
                return es.getValue().accept(v, type);
            }
        }
        return null;
    }

    private <T> T findValue(AnnotationMirror am, String key, Class<T> type) {
        var values = findValues(am, key, type);
        if (values != null && values.size() > 0) {
            return values.get(0);
        } else {
            return null;
        }
    }

    private void addCApiBuiltins(RoundEnvironment re, List<CApiBuiltinDesc> javaBuiltins, List<CApiBuiltinDesc> additionalBuiltins) {
        for (var root : re.getRootElements()) {
            for (var element : root.getEnclosedElements()) {
                List<AnnotationMirror> annotations;
                var annot = findAnnotationMirror(element, CAPI_BUILTIN);
                if (annot == null) {
                    annot = findAnnotationMirror(element, CAPI_BUILTINS);
                    if (annot != null) {
                        annotations = findValues(annot, "value", AnnotationMirror.class);
                    } else {
                        continue;
                    }
                } else {
                    annotations = List.of(annot);
                }
                for (var builtin : annotations) {
                    var name = element.getSimpleName().toString();
                    var builtinName = findValue(builtin, "name", String.class);
                    if (!builtinName.isEmpty()) {
                        name = builtinName;
                    }
                    var ret = findValue(builtin, "ret", VariableElement.class);
                    boolean acquireGil = findValue(builtin, "acquireGil", Boolean.class);
                    boolean canRaise = findValue(builtin, "canRaise", Boolean.class);
                    if (acquireGil && !canRaise) {
                        processingEnv.getMessager().printError(String.format("Invalid @CApiBuiltin %s: if acquireGil is true, canRaise must be true as well (a safepoint action may run)", name, ret));
                        continue;
                    }
                    String call = name(findValue(builtin, "call", VariableElement.class));
                    // boolean inlined = findValue(builtin, "inlined", Boolean.class);
                    VariableElement[] args = findValues(builtin, "args", VariableElement.class).toArray(new VariableElement[0]);
                    if (element instanceof TypeElement te && te.getQualifiedName().toString().equals("com.oracle.graal.python.builtins.objects.cext.capi.CApiFunction.Dummy")) {
                        additionalBuiltins.add(new CApiBuiltinDesc(element, builtinName, ret, args, acquireGil, canRaise, call, null));
                    } else {
                        if (!isValidReturnType(ret)) {
                            processingEnv.getMessager().printError(
                                            String.format("Invalid @CApiBuiltin %s: %s is not an allowed return type, use PyObjectTransfer or PyObjectBorrow variants", name, ret));
                            continue;
                        }
                        boolean usesGenerateNodeFactory = false;
                        for (var am : element.getAnnotationMirrors()) {
                            if (((TypeElement) am.getAnnotationType().asElement()).getQualifiedName().toString().equals("com.oracle.truffle.api.dsl.GenerateNodeFactory")) {
                                usesGenerateNodeFactory = true;
                                break;
                            }
                        }
                        var container = element.getEnclosingElement();
                        String genName;
                        if (container instanceof TypeElement cte) {
                            genName = cte.getQualifiedName() + "Factory." + element.getSimpleName();
                        } else {
                            genName = ((TypeElement) element).getQualifiedName().toString();
                        }
                        if (usesGenerateNodeFactory) {
                            genName += "Factory";
                        } else {
                            genName += "NodeGen";
                        }
                        if (element instanceof TypeElement te) {
                            verifyNodeClass(te, builtin);
                        } else {
                            verifyStaticMethod((ExecutableElement) element, builtin);
                        }
                        javaBuiltins.add(new CApiBuiltinDesc(element, name, ret, args, acquireGil, canRaise, call, genName));
                    }
                }
            }
        }
        javaBuiltins.sort((a, b) -> a.name.compareTo(b.name));
        for (int i = 0; i < javaBuiltins.size(); i++) {
            javaBuiltins.get(i).id = i;
        }
    }

    private void verifyStaticMethod(ExecutableElement e, AnnotationMirror annotation) {
        if (!e.getModifiers().contains(Modifier.STATIC)) {
            processingEnv.getMessager().printError("CApiBuiltins must be nodes or static methods", e);
        } else if (e.getParameters().size() != findValues(annotation, "args", VariableElement.class).size()) {
            processingEnv.getMessager().printError("Arity mismatch between declared arguments and static method", e);
        }
    }

    private void verifyNodeClass(TypeElement te, AnnotationMirror annotation) {
        var tm = te.asType();
        while (tm instanceof DeclaredType dt) {
            for (var e : dt.asElement().getEnclosedElements()) {
                if (e.getKind() == ElementKind.METHOD) {
                    if (e.getModifiers().contains(Modifier.ABSTRACT) && e.getSimpleName().toString().equals("execute")) {
                        if (((ExecutableElement) e).getParameters().size() != findValues(annotation, "args", VariableElement.class).size()) {
                            processingEnv.getMessager().printError("Arity mismatch between declared arguments and builtin superclass", te);
                        }
                        return;
                    }
                }
            }
            tm = ((TypeElement) dt.asElement()).getSuperclass();
        }
        processingEnv.getMessager().printError("Couldn't find execute method for C builtin", te);
    }

    private static Optional<CApiBuiltinDesc> findBuiltin(List<CApiBuiltinDesc> builtins, String name) {
        return builtins.stream().filter(n -> n.name.equals(name)).findFirst();
    }

    /**
     * Check whether the two given types are similar, based on the C signature (and ignoring a
     * "struct" keyword).
     */
    private boolean isSimilarType(String t1, String t2) {
        return t1.equals(t2) || t1.equals("struct " + t2) || ("struct " + t1).equals(t2) ||
                        t1.equals(resolveTypeAlias(t2)) || resolveTypeAlias(t1).equals(t2);
    }

    private void compareFunction(String name, VariableElement ret1, VariableElement ret2, VariableElement[] args1, VariableElement[] args2) {
        compareFunction(name, ret1, getCSignature(ret2), args1, Arrays.stream(args2).map(this::getCSignature).toArray(String[]::new));
    }

    private void compareFunction(String name, VariableElement ret1, String ret2, VariableElement[] args1, String[] args2) {
        if (trees == null) {
            return; // This isn't correct without parsing
        }
        if (!isSimilarType(getCSignature(ret1), ret2)) {
            processingEnv.getMessager().printError("duplicate entry for " + name + ", different return " + ret1 + " vs. " + ret2);
        }
        if (args1.length != args2.length) {
            processingEnv.getMessager().printError("duplicate entry for " + name + ", different arg lengths " + args1.length + " vs. " + args2.length);
        } else {
            for (int i = 0; i < args1.length; i++) {
                if (!isSimilarType(getCSignature(args1[i]), args2[i])) {
                    processingEnv.getMessager().printError("duplicate entry for " + name + ", different arg " + i + ": " + args1[i] + " vs. " + args2[i]);
                }
            }
        }
    }

    private String getArgSignatureWithName(VariableElement arg, int i) {
        String sig = getCSignature(arg);
        if (isVarArgs(arg)) {
            return sig;
        } else if (sig.contains("(*)")) {
            // function type
            return sig.replace("(*)", "(*" + argName(i) + ")");
        } else if (sig.endsWith("[]")) {
            return sig.substring(0, sig.length() - 2) + argName(i) + "[]";
        } else {
            return sig + " " + argName(i);
        }
    }

    /**
     * Generates the functions for capi.c that forward CApiCallPath#Direct builtins to their
     * associated Java implementations.
     *
     * @throws IOException
     */
    private void generateCApiSource(List<CApiBuiltinDesc> javaBuiltins, List<String> constants, List<String> fields, List<String> structs) throws IOException {
        ArrayList<String> lines = new ArrayList<>();
        for (var entry : javaBuiltins) {
            String name = entry.name;
            CApiBuiltinDesc value = entry;
            if (value.call.equals("Direct") || value.call.equals("NotImplemented")) {
                lines.add("#undef " + name);
                String line = "PyAPI_FUNC(" + getCSignature(value.returnType) + ") " + name + "(";
                for (int i = 0; i < value.arguments.length; i++) {
                    line += (i == 0 ? "" : ", ") + getArgSignatureWithName(value.arguments[i], i);
                }
                line += ") {";
                lines.add(line);
                if (value.call.equals("Direct")) {
                    line = "    " + (isVoid(value.returnType) ? "" : "return ") + "GraalPyPrivate_Upcall_" + name + "(";
                    for (int i = 0; i < value.arguments.length; i++) {
                        line += (i == 0 ? "" : ", ");
                        line += argName(i);
                    }
                    line += ");";
                } else {
                    line = "    FUNC_NOT_IMPLEMENTED";
                }
                lines.add(line);
                lines.add("}");
            }
        }

        lines.add("PyAPI_FUNC(int64_t*) GraalPyPrivate_Constants() {");
        lines.add("    static int64_t constants[] = {");
        for (var constant : constants) {
            lines.add("        (int64_t) " + constant + ",");
        }
        lines.add("        0xdead1111 // marker value");
        lines.add("    };");
        lines.add("    return constants;");
        lines.add("}");
        lines.add("PyAPI_FUNC(Py_ssize_t*) GraalPyPrivate_StructOffsets() {");
        lines.add("    static Py_ssize_t offsets[] = {");
        for (var field : fields) {
            int delim = field.indexOf("__");
            assert delim != -1;
            String struct = field.substring(0, delim);
            String name = field.substring(delim + 2);
            name = name.replace("__", "."); // to allow inlined structs
            lines.add("        offsetof(" + struct + ", " + name + "),");
        }
        lines.add("        0xdead2222 // marker value");
        lines.add("    };");
        lines.add("    return offsets;");
        lines.add("}");
        lines.add("PyAPI_FUNC(Py_ssize_t*) GraalPyPrivate_StructSizes() {");
        lines.add("    static Py_ssize_t sizes[] = {");
        for (var struct : structs) {
            lines.add("        sizeof(" + struct.replace("__", " ") + "),");
        }
        lines.add("        0xdead3333 // marker value");
        lines.add("    };");
        lines.add("    return sizes;");
        lines.add("}");

        updateResource("capi.gen.c.h", javaBuiltins, lines);
    }

    private void updateResource(String name, List<CApiBuiltinDesc> javaBuiltins, List<String> lines, StandardLocation loc) throws IOException {
        var origins = javaBuiltins.stream().map((jb) -> jb.origin).toArray(Element[]::new);
        String oldContents = "";
        String newContents = String.join(System.lineSeparator(), lines);
        try {
            oldContents = processingEnv.getFiler().getResource(loc, "", name).getCharContent(true).toString();
        } catch (IOException e) {
            // pass to regenerate
        }
        if (!oldContents.equals(newContents)) {
            var file = processingEnv.getFiler().createResource(loc, "", name, origins);
            try (var w = file.openWriter()) {
                w.append(newContents);
            }
        } else {
            processingEnv.getMessager().printNote("Python %s is up to date".formatted(name));
        }
    }

    private void updateResource(String name, List<CApiBuiltinDesc> javaBuiltins, List<String> lines) throws IOException {
        updateResource(name, javaBuiltins, lines, StandardLocation.NATIVE_HEADER_OUTPUT);
    }

    /**
     * Generates the builtin specification in capi.h, which includes only the builtins implemented
     * in Java code. Additionally, it generates helpers for all "GraalPyPrivate_Get_" and
     * "GraalPyPrivate_Set_" builtins.
     */
    private void generateCApiHeader(List<CApiBuiltinDesc> javaBuiltins) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("#define CAPI_BUILTINS \\");
        int id = 0;
        for (var entry : javaBuiltins) {
            assert (id++) == entry.id;
            String prefix = entry.call.equals("Direct") ? "PUBLIC" : "PRIVATE";
            String line = "    " + prefix + "_BUILTIN(" + entry.name + ", " + getCSignature(entry.returnType);
            for (var arg : entry.arguments) {
                line += ", " + getCSignature(arg);
            }
            line += ") \\";
            lines.add(line);
        }
        lines.add("");

        for (var entry : javaBuiltins) {
            String name = entry.name;
            if (entry.origin.getEnclosingElement().getSimpleName().toString().equals("PythonCextSlotBuiltins")) {
                String getPrefix = "GraalPyPrivate_Get_";
                String setPrefix = "GraalPyPrivate_Set_";
                if (name.startsWith(getPrefix)) {
                    assert entry.arguments.length == 1 : name;
                    String type = name(entry.arguments[0]).replace("Wrapper", "");
                    StringBuilder macro = new StringBuilder();
                    assert name.charAt(getPrefix.length() + type.length()) == '_' : name;
                    String unprefixed = name.substring(getPrefix.length());
                    String field = unprefixed.substring(type.length() + 1);
                    macro.append("#define GraalPyPrivate_GET_" + unprefixed +
                                    "(OBJ) ( points_to_py_handle_space(OBJ) ? " + name + "((" + type + "*) (OBJ)) : ((" + type + "*) (OBJ))->" + field + ")");
                    lines.add(macro.toString());
                } else if (name.startsWith(setPrefix)) {
                    assert entry.arguments.length == 2 : name;
                    String type = name(entry.arguments[0]).replace("Wrapper", "");
                    StringBuilder macro = new StringBuilder();
                    assert name.charAt(setPrefix.length() + type.length()) == '_' : name;
                    String unprefixed = name.substring(setPrefix.length());
                    String field = unprefixed.substring(type.length() + 1);
                    macro.append("#define GraalPyPrivate_SET_" + unprefixed +
                                    "(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) " + name + "((" + type + "*) (OBJ), (VALUE)); else  ((" + type + "*) (OBJ))->" + field + " = (VALUE); }");
                    lines.add(macro.toString());
                }
            }
        }

        updateResource("capi.gen.h", javaBuiltins, lines);
    }

    /**
     * Generates the native image config for the direct upcalls to the builtins.
     */
    private void generateUpcallConfig(List<CApiBuiltinDesc> javaBuiltins) throws IOException {
        ArrayList<String> lines = new ArrayList<>();
        lines.add("{");
        lines.add("  \"foreign\": {");
        lines.add("    \"directUpcalls\": [");

        for (int i = 0; i < javaBuiltins.size(); i++) {
            var builtin = javaBuiltins.get(i);
            String argString = Arrays.stream(builtin.arguments).map(b -> '"' + capiTypeToForeignPrimitiveType(b) + '"').collect(Collectors.joining(", "));
            String classString;
            String methodString;
            if (builtin.origin instanceof TypeElement) {
                classString = "com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinRegistry";
                methodString = "upcall_" + builtin.name;
            } else {
                classString = ((TypeElement) builtin.origin.getEnclosingElement()).getQualifiedName().toString();
                methodString = builtin.origin.getSimpleName().toString();
            }
            lines.add("      {");
            lines.add("        \"class\": \"" + classString + "\",");
            lines.add("        \"method\": \"" + methodString + "\",");
            lines.add("        \"returnType\": \"" + capiTypeToForeignPrimitiveType(builtin.returnType) + "\",");
            lines.add("        \"parameterTypes\": [" + argString + "]");
            if (i < javaBuiltins.size() - 1) {
                lines.add("      },");
            } else {
                lines.add("      }");
            }
        }
        lines.add("    ]");
        lines.add("  }");
        lines.add("}");

        updateResource("META-INF/native-image/com.oracle.graal.python.capi/reachability-metadata.json", javaBuiltins, lines, StandardLocation.CLASS_OUTPUT);
    }

    /**
     * Generates the contents of the PythonCextBuiltinRegistry class: the list of builtins, the
     * CApiBuiltinNode factory function, and the slot query function.
     */
    private void generateBuiltinRegistry(List<CApiBuiltinDesc> javaBuiltins) throws IOException {
        ArrayList<String> lines = new ArrayList<>();
        // language=java
        lines.add("""
                        // @formatter:off
                        // Checkstyle: stop
                        // Generated by annotation processor: CApiBuiltinsProcessor
                        package %s;

                        import java.lang.invoke.MethodHandle;
                        import java.lang.invoke.MethodHandles;
                        import java.lang.invoke.MethodType;

                        import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins;
                        import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltinExecutable;
                        import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltinNode;
                        import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath;
                        import com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor;
                        import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins;
                        import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.TransformPExceptionToNativeNode;
                        import com.oracle.graal.python.runtime.GilNode;
                        import com.oracle.graal.python.runtime.exception.PException;

                        public final class PythonCextBuiltinRegistry {

                            private PythonCextBuiltinRegistry() {
                                // no instances
                            }
                        """.formatted(TARGET_PACKAGE));

        for (var builtin : javaBuiltins) {
            String argString = Arrays.stream(builtin.arguments).map(b -> "ArgDescriptor." + b).collect(Collectors.joining(", "));
            lines.add("    public static final CApiBuiltinExecutable " + builtin.name + " = new CApiBuiltinExecutable(\"" + builtin.name + "\", ArgDescriptor." +
                            builtin.returnType + ", new ArgDescriptor[]{" + argString + "}, " + builtin.acquireGil + ", " + builtin.id + ");");
        }
        lines.add("");
        lines.add("    public static final CApiBuiltinExecutable[] builtins = {");
        for (var builtin : javaBuiltins) {
            lines.add("                    " + builtin.name + ",");
        }
        lines.add("    };");
        lines.add("");

        lines.add("    static CApiBuiltinNode createBuiltinNode(int id) {");
        lines.add("        switch (id) {");

        for (var builtin : javaBuiltins) {
            lines.add("            case " + builtin.id + ":");
            if (builtin.origin instanceof ExecutableElement) {
                lines.add("                throw new RuntimeException(\"Static builtins should never need a node, they are just a static method.\");");
            } else {
                lines.add("                return " + builtin.factory + ".create();");
            }
        }

        lines.add("        }");
        lines.add("        return null;");
        lines.add("    }");

        lines.add("");
        for (var builtin : javaBuiltins) {
            lines.add("    private static final MethodHandle HANDLE_" + builtin.name + ";");
        }
        lines.add("    static {");
        lines.add("        try {");
        for (var builtin : javaBuiltins) {
            String argString = Arrays.stream(builtin.arguments).map(b -> capiTypeToJavaPrimitiveType(b) + ".class").collect(Collectors.joining(", "));
            String classString;
            String methodString;
            if (builtin.origin instanceof TypeElement || builtin.canRaise) {
                classString = "PythonCextBuiltinRegistry";
                methodString = "\"upcall_" + builtin.name + "\"";
            } else {
                classString = ((TypeElement) builtin.origin.getEnclosingElement()).getQualifiedName().toString();
                methodString = '"' + builtin.origin.getSimpleName().toString() + '"';
            }
            lines.add("            HANDLE_" + builtin.name + " = MethodHandles.lookup().findStatic(" + classString + ".class, " + methodString + ", MethodType.methodType(" +
                            capiTypeToJavaPrimitiveType(builtin.returnType) + ".class" + (builtin.arguments.length > 0 ? ", " : "") + argString + "));");
        }
        lines.add("        } catch (NoSuchMethodException | IllegalAccessException e) {");
        lines.add("            throw new RuntimeException(e);");
        lines.add("        }");
        lines.add("    }");

        lines.add("");
        lines.add("    static MethodHandle getMethodHandle(int id) {");
        lines.add("        switch (id) {");
        for (var builtin : javaBuiltins) {
            lines.add("            case " + builtin.id + ":");
            lines.add("                return HANDLE_" + builtin.name + ";");
        }
        lines.add("        }");
        lines.add("        return null;");
        lines.add("    }");

        for (var builtin : javaBuiltins) {
            if (builtin.origin instanceof ExecutableElement && !builtin.canRaise) {
                // will be called directly
                continue;
            }
            lines.add("");
            String argString = IntStream.range(0, builtin.arguments.length).mapToObj(i -> capiTypeToJavaPrimitiveType(builtin.arguments[i]) + " " + argName(i)).collect(Collectors.joining(", "));
            if (!(builtin.origin instanceof TypeElement) && builtin.acquireGil) {
                lines.add("    @SuppressWarnings(\"try\")");
            }
            lines.add("    public static " + capiTypeToJavaPrimitiveType(builtin.returnType) + " upcall_" + builtin.name + "(" + argString + ") {");
            String paramString = IntStream.range(0, builtin.arguments.length).mapToObj(i -> argName(i)).collect(Collectors.joining(", "));
            String retString = capiTypeToJavaPrimitiveType(builtin.returnType);
            if (retString.equals("void")) {
                retString = "";
            } else {
                retString = "return (" + retString + ")";
            }
            if (builtin.origin instanceof TypeElement) {
                lines.add("        " + retString + builtin.name + ".getCallTarget().call(" + paramString + ");");
            } else {
                if (!retString.isEmpty()) {
                    retString = "return ";
                }
                assert builtin.canRaise;
                lines.add("        try {");
                lines.add("            try " + (builtin.acquireGil ? "(GilNode.UncachedAcquire gil = GilNode.uncachedAcquire()) " : "") + "{");
                lines.add("                " + retString + ((TypeElement) builtin.origin.getEnclosingElement()).getQualifiedName().toString() + "." + builtin.origin.getSimpleName().toString() + "(" +
                                paramString + ");");
                lines.add("            } catch (Throwable t) {");
                lines.add("                throw PythonCextBuiltins.checkThrowableBeforeNative(t, \"CApiBuiltin\", \"" + builtin.name + "\");");
                lines.add("            }");
                lines.add("        } catch (PException pe) {");
                lines.add("            TransformPExceptionToNativeNode.executeUncached(pe);");
                if (!retString.isEmpty()) {
                    lines.add("            return " + capiTypeToErrorValue(builtin.returnType) + ";");
                }
                lines.add("        }");
            }
            lines.add("    }");
        }

        lines.add("}");

        var origins = javaBuiltins.stream().map((jb) -> jb.origin).toArray(Element[]::new);
        var file = processingEnv.getFiler().createSourceFile("com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinRegistry", origins);
        try (var w = file.openWriter()) {
            w.append(String.join(System.lineSeparator(), lines));
        }
    }

    private void generateCApiAsserts(List<CApiBuiltinDesc> allBuiltins) throws IOException {
        ArrayList<String> lines = new ArrayList<>();
        lines.add("");
        for (var builtin : allBuiltins) {
            lines.add("        hasMember = reallyHasMember(capiLibrary, \"" + builtin.name + "\");");
            if (builtin.call.equals("CImpl") || builtin.call.equals("Direct") || builtin.call.equals("NotImplemented")) {
                lines.add("        if (!hasMember) messages.add(\"missing implementation: " + builtin.name + "\");");
            }
        }
        lines.add("");

        var origins = allBuiltins.stream().map((jb) -> jb.origin).toArray(Element[]::new);
        var file = processingEnv.getFiler().createSourceFile("com.oracle.graal.python.builtins.modules.cext.PythonCApiAssertions", origins);
        try (var w = file.openWriter()) {
            // language=java
            w.append("""
                            // @formatter:off
                            // Checkstyle: stop
                            package %s;

                            import java.util.TreeSet;
                            import com.oracle.graal.python.nfi2.NfiLibrary;
                            import com.oracle.truffle.api.CompilerDirectives;
                            import com.oracle.truffle.api.interop.InteropLibrary;
                            import com.oracle.truffle.api.interop.UnsupportedMessageException;

                            public abstract class PythonCApiAssertions {

                                private PythonCApiAssertions() {
                                    // no instances
                                }

                                public static boolean reallyHasMember(NfiLibrary capiLibrary, String name) {
                                    return capiLibrary.lookupOptionalSymbol(name) != 0L;
                                }

                                /**
                                 * Checks whether the expected builtins exist in the library.
                                 */
                                public static boolean assertBuiltins(NfiLibrary capiLibrary) {
                                    boolean hasMember = false;
                                    TreeSet<String> messages = new TreeSet<>();
                                    %s
                                    messages.forEach(System.err::println);
                                    return messages.isEmpty();
                                }
                            }
                            """.formatted(TARGET_PACKAGE, String.join(System.lineSeparator(), lines)));
        }
    }

    /**
     * Looks for the given (relative) path, assuming that the current working directory is either
     * the repository root or a project directory.
     */
    private static Path resolvePath(Path path) {
        Path result = Path.of("graalpython").resolve(path);
        if (result.toFile().exists()) {
            return result;
        }
        result = Path.of("..").resolve(path);
        if (result.toFile().exists()) {
            return result;
        }
        return null;
    }

    /**
     * These are functions that are introduced by GraalPy, mostly auxiliary functions that we added
     * to avoid direct fields accesses:
     */
    private static final String[] ADDITIONAL = new String[]{
                    /*
                     * These PySlice builtins are deprecated and scheduled for removal once we no
                     * longer support versions of Cython that use them. Grep all patches before
                     * removing
                     */
                    "PySlice_Start", "PySlice_Step", "PySlice_Stop",
                    "PyObject_GetDoc", "PyObject_SetDoc",
                    // Only in include/internal/pycore_namespace.h, not public
                    "_PyNamespace_New",
                    // Only in include/internal/pycore_fileutils.h, not public
                    "_Py_GetErrorHandler",
                    // Not actually additional, only defined on Windows.
                    // TODO: fix generated CAPIFunctions.txt
                    "PyUnicode_AsMBCSString", "PyUnicode_EncodeCodePage", "PyUnicode_DecodeMBCS",
                    "PyUnicode_DecodeCodePageStateful", "PyUnicode_DecodeMBCSStateful",
    };

    public String resolveArgDescriptor(String sig) {
        switch (sig) {
            case "struct _typeobject*":
                return "PyTypeObject";
            case "struct PyGetSetDef*":
                return "PyGetSetDef";
            case "const struct PyConfig*":
                return "CONST_PYCONFIG_PTR";
            case "struct PyConfig*":
                return "PYCONFIG_PTR";
        }
        var knownDescriptor = signatureToArgDescriptor.get(sig);
        if (knownDescriptor == null) {
            processingEnv.getMessager().printWarning("unknown C signature: " + sig);
            return "'%s'".formatted(sig);
        } else {
            return knownDescriptor;
        }
    }

    public String resolveTypeAlias(String sig) {
        return switch (sig) {
            case "struct _typeobject*" -> "PyTypeObject*";
            case "const struct PyConfig*" -> "const PyConfig*";
            default -> sig;
        };
    }

    /**
     * Check the list of implemented and unimplemented builtins against the list of CPython exported
     * symbols, to determine if builtins are missing. If a builtin is missing, this function
     * suggests the appropriate @CApiBuiltin specification.
     */
    private void checkImports(List<CApiBuiltinDesc> builtins) throws IOException {
        var path = resolvePath(Path.of("com.oracle.graal.python.cext", "CAPIFunctions.txt"));
        if (path == null) {
            return;
        }
        List<String> lines = Files.readAllLines(path);

        TreeSet<String> newBuiltins = new TreeSet<>();
        TreeSet<String> names = new TreeSet<>();
        builtins.forEach(n -> names.add(n.name));

        for (String line : lines) {
            String[] s = line.split(";");
            String name = s[0].trim();
            names.remove(name);
            String retSig = s[1].trim();
            String ret = resolveArgDescriptor(retSig);
            String[] argSplit = s[2].isBlank() || "void".equals(s[2]) ? new String[0] : s[2].trim().split("\\|");
            String[] args = Arrays.stream(argSplit).map(this::resolveArgDescriptor).toArray(String[]::new);

            Optional<CApiBuiltinDesc> existing = findBuiltin(builtins, name);
            if (existing.isPresent()) {
                compareFunction(name, existing.get().returnType, retSig, existing.get().arguments, argSplit);
            } else {
                String argString = Arrays.stream(args).map(t -> String.valueOf(t)).collect(Collectors.joining(", "));
                newBuiltins.add("    @CApiBuiltin(name = \"" + name + "\", ret = " + ret + ", args = {" + argString + "}, call = NotImplemented)");
            }
        }
        if (!newBuiltins.isEmpty()) {
            processingEnv.getMessager().printError("missing builtins (defined in CPython, but not in GraalPy):");
            newBuiltins.stream().forEach(processingEnv.getMessager()::printError);
        }

        names.removeIf(n -> n.startsWith("GraalPy"));
        names.removeAll(Arrays.asList(ADDITIONAL));
        if (!names.isEmpty()) {
            processingEnv.getMessager().printError("extra builtins (defined in GraalPy, but not in CPython - some of these are necessary for internal modules like 'math'):");
            processingEnv.getMessager().printError("    " + names.stream().collect(Collectors.joining(", ")));
        }
    }

    private static final class CApiExternalFunctionSignatureDesc {
        public final VariableElement origin;
        public final String name;

        String returnType;
        String[] argumentTypes;
        public boolean cannotRaise;

        public CApiExternalFunctionSignatureDesc(VariableElement origin, String name) {
            this.origin = origin;
            this.name = name;
        }
    }

    private record InvokeExternalFunctionDesc(ExecutableElement origin, VariableElement signature, TypeMirror returnType, List<TypeMirror> argumentTypes) {
    }

    private static final String NFI2_PACKAGE = "com.oracle.graal.python.nfi2";
    private static final String EXFUNC_INVOKER_PACKAGE = "com.oracle.graal.python.builtins.objects.cext.capi";
    private static final String EXFUNC_INVOKER_CLASS_NAME = "ExternalFunctionInvoker";

    /**
     * Find classes annotated with {@link #CAPI_WRAPPER_DESCRIPTOR} and methods annotated with
     * {@link #INVOKE_EXTERNAL_FUNCTION} and store the extraced information in
     * {@link CApiExternalFunctionWrapperDesc} and {@link InvokeExternalFunctionDesc}, respectively.
     */
    private List<InvokeExternalFunctionDesc> collectExternalFunctionAndWrapperDescs(RoundEnvironment re, List<CApiExternalFunctionWrapperDesc> wrappers) {
        List<InvokeExternalFunctionDesc> wrapperDescs = new ArrayList<>();

        // TODO: remove this as soon as the annotation 'INVOKE_EXTERNAL_FUNCTION' is accessible by
        // this processor
        for (var rootElement : re.getRootElements()) {
            collectExternalFunctionAndWrapperDescs(rootElement, wrapperDescs, wrappers);
        }
        return wrapperDescs;
    }

    private void collectExternalFunctionAndWrapperDescs(Element element, List<InvokeExternalFunctionDesc> wrapperDescs, List<CApiExternalFunctionWrapperDesc> wrappers) {
        AnnotationMirror invokeAnnot = findAnnotationMirror(element, INVOKE_EXTERNAL_FUNCTION);
        if (invokeAnnot != null) {
            assert element.getKind() == ElementKind.METHOD;
            VariableElement signatureElement = findValue(invokeAnnot, "value", VariableElement.class);
            TypeMirror retConversion = findValue(invokeAnnot, "retConversion", TypeMirror.class);
            List<TypeMirror> argConversion = findValues(invokeAnnot, "argConversions", TypeMirror.class);
            wrapperDescs.add(new InvokeExternalFunctionDesc((ExecutableElement) element, signatureElement, retConversion, argConversion));
        }

        AnnotationMirror wrapperAnnot = findAnnotationMirror(element, CAPI_WRAPPER_DESCRIPTOR);
        if (wrapperAnnot != null) {
            List<VariableElement> wrapperNames = findValues(wrapperAnnot, "value", VariableElement.class);
            wrappers.add(new CApiExternalFunctionWrapperDesc(element, wrapperNames));
        }

        for (Element enclosedElement : element.getEnclosedElements()) {
            collectExternalFunctionAndWrapperDescs(enclosedElement, wrapperDescs, wrappers);
        }
    }

    /**
     * Maps an {@code com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgBehavior} to
     * the NFI Java type.
     */
    private String toJavaNfiType(String argDescriptor) {
        // TODO: types should be inferred with: 'ArgDescriptor.behavior.nfi2Type'
        return switch (argDescriptor) {
            case "Void" -> "void";
            case "Int", "InquiryResult", "InitResult", "PrimitiveResult32" -> "int";
            case "Double" -> "double";
            case "Float" -> "float";
            case "Py_hash_t", "Py_ssize_t", "PrimitiveResult64", "INT64_T", "SIZE_T", "UINTPTR_T", "Long", "UNSIGNED_LONG", "UINT64_T",
                            "PyObjectReturn", "PyObject", "PyObjectTransfer", "PyObjectConstArray", "PyTypeObject", "CharPtrAsTruffleString", "IterResult", "Pointer",
                            "CHAR_PTR" ->
                "long";
            default -> {
                processingEnv.getMessager().printError(String.format("Unexpected ArgDescriptor: '%s'", argDescriptor));
                yield null;
            }
        };
    }

    private static String getSimpleName(TypeMirror typeMirror) {
        if (typeMirror.getKind() == TypeKind.DECLARED) {
            return ((DeclaredType) typeMirror).asElement().getSimpleName().toString();
        }
        return typeMirror.toString();
    }

    private TypeMirror toJavaNfiType(TypeMirror typeMirror) {
        if (typeMirror.getKind() == TypeKind.VOID && typeMirror.getKind().isPrimitive()) {
            return typeMirror;
        }
        return processingEnv.getTypeUtils().getPrimitiveType(TypeKind.LONG);
    }

    /**
     * Maps an {@code com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgBehavior} to
     * the NFI type.
     */
    private String toNfiType(String argDescriptor) {
        // TODO: types should be inferred with: 'ArgDescriptor.behavior.nfi2Type'
        return switch (argDescriptor) {
            case "Void" -> "NfiType.VOID";
            case "Int", "InquiryResult", "InitResult", "PrimitiveResult32" -> "NfiType.SINT32";
            case "Py_ssize_t", "PrimitiveResult64" -> "NfiType.SINT64";
            case "PyObjectReturn", "PyObject", "PyObjectTransfer", "Pointer", "PyObjectConstArray", "PyTypeObject", "CharPtrAsTruffleString", "IterResult", "CHAR_PTR" ->
                "NfiType.RAW_POINTER";
            default -> {
                processingEnv.getMessager().printError(String.format("Unexpected ArgDescriptor: '%s'", argDescriptor));
                yield null;
            }
        };
    }

    private static String getNfiMethodHandleVarName(String signatureName) {
        return "NFI_METHOD_HANDLE_" + signatureName;
    }

    private static String toClassLiteral(String javaType) {
        return switch (javaType) {
            case "void" -> "void.class";
            case "int" -> "int.class";
            case "long" -> "long.class";
            case "float" -> "float.class";
            case "double" -> "double.class";
            default -> throw new IllegalArgumentException("Unexpected Java type: " + javaType);
        };
    }

    private boolean isCannotRaise(VariableElement signature) {
        String externalFunctionSignatureInitializer = getExternalFunctionSignatureInitializer(signature);
        int start = externalFunctionSignatureInitializer.indexOf('(');
        int end = externalFunctionSignatureInitializer.lastIndexOf(')');
        String[] initArgs = externalFunctionSignatureInitializer.substring(start + 1, end).split(",");
        return Boolean.parseBoolean(initArgs[0].strip());
    }

    private boolean isWrapperRootInvoke(InvokeExternalFunctionDesc wrapper) {
        TypeMirror wrapperBaseRoot = processingEnv.getElementUtils().getTypeElement(EXFUNC_INVOKER_PACKAGE + ".ExternalFunctionNodes.WrapperBaseRoot").asType();
        return processingEnv.getTypeUtils().isSubtype(wrapper.origin.getEnclosingElement().asType(), wrapperBaseRoot);
    }

    private static String getGeneratedExternalInvokeHelperClassName(Element clazz) {
        return clazz.getSimpleName() + "Gen";
    }

    private void generateExternalFunctionInvoker(List<CApiExternalFunctionSignatureDesc> signatures) throws IOException {
        boolean hasFfmDowncalls = Runtime.version().feature() >= 22;
        ArrayList<String> lines = new ArrayList<>();

        lines.add("// @formatter:off");
        lines.add("// Checkstyle: stop");
        lines.add("// Generated by annotation processor: " + getClass().getName());
        lines.add("package " + EXFUNC_INVOKER_PACKAGE + ";");
        lines.add("");
        if (hasFfmDowncalls) {
            lines.add("import java.lang.foreign.FunctionDescriptor;");
            lines.add("import java.lang.foreign.Linker;");
            lines.add("import java.lang.foreign.MemoryLayout;");
            lines.add("import java.lang.foreign.MemorySegment;");
            lines.add("import java.lang.foreign.ValueLayout;");
            lines.add("import java.lang.invoke.MethodHandle;");
            lines.add("import java.lang.invoke.MethodHandles;");
            lines.add("import java.lang.invoke.MethodType;");
            lines.add("");
        }
        lines.add("import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.EnsurePythonObjectNode;");
        lines.add("import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTiming;");
        lines.add("import com.oracle.graal.python.builtins.objects.function.PArguments;");
        lines.add("import " + NFI2_PACKAGE + ".NfiBoundFunction;");
        lines.add("import " + NFI2_PACKAGE + ".NfiContext;");
        lines.add("import com.oracle.graal.python.runtime.ExecutionContext.BoundaryCallContext;");
        lines.add("import com.oracle.graal.python.runtime.GilNode;");
        lines.add("import com.oracle.graal.python.runtime.IndirectCallData.BoundaryCallData;");
        lines.add("import com.oracle.graal.python.runtime.PythonContext;");
        lines.add("import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;");
        lines.add("import com.oracle.truffle.api.CompilerDirectives;");
        lines.add("import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;");
        if (hasFfmDowncalls) {
            lines.add("import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;");
        }
        lines.add("import " + TRUFFLE_VIRTUAL_FRAME + ";");
        lines.add("");
        lines.add("public final class " + EXFUNC_INVOKER_CLASS_NAME + " {");
        lines.add("");
        lines.add("    private " + EXFUNC_INVOKER_CLASS_NAME + "() {");
        lines.add("        // no instances");
        lines.add("    }");
        lines.add("");
        lines.add("    private static final String NFI_UNAVAILABLE_MESSAGE = \"NFI2 downcalls require JDK 22+\";");
        lines.add("");
        if (hasFfmDowncalls) {
            lines.add("    private static final MethodHandle OF_ADDRESS;");
            lines.add("");
            lines.add("    static {");
            lines.add("        try {");
            lines.add("            OF_ADDRESS = MethodHandles.lookup().findStatic(MemorySegment.class, \"ofAddress\", MethodType.methodType(MemorySegment.class, long.class));");
            lines.add("        } catch (NoSuchMethodException | IllegalAccessException e) {");
            lines.add("            throw new RuntimeException(e);");
            lines.add("        }");
            lines.add("    }");
            lines.add("");
            lines.add("    @SuppressWarnings(\"restricted\")");
            lines.add("    private static MethodHandle createDowncallHandle(MethodType methodType, boolean critical) {");
            lines.add("        FunctionDescriptor functionDescriptor = createFunctionDescriptor(methodType);");
            lines.add("        Linker.Option[] options = critical ? new Linker.Option[] { Linker.Option.critical(false) } : new Linker.Option[0];");
            lines.add("        MethodHandle methodHandle = Linker.nativeLinker().downcallHandle(functionDescriptor);");
            lines.add("        methodHandle = MethodHandles.filterArguments(methodHandle, 0, OF_ADDRESS);");
            lines.add("        return methodHandle.asType(methodType);");
            lines.add("    }");
            lines.add("");
            lines.add("    private static FunctionDescriptor createFunctionDescriptor(MethodType methodType) {");
            lines.add("        Class<?>[] parameterTypes = methodType.parameterArray();");
            lines.add("        MemoryLayout[] argLayouts = new MemoryLayout[parameterTypes.length - 1];");
            lines.add("        for (int i = 1; i < parameterTypes.length; i++) {");
            lines.add("            argLayouts[i - 1] = asLayout(parameterTypes[i]);");
            lines.add("        }");
            lines.add("        Class<?> returnType = methodType.returnType();");
            lines.add("        return returnType == void.class ? FunctionDescriptor.ofVoid(argLayouts) : FunctionDescriptor.of(asLayout(returnType), argLayouts);");
            lines.add("    }");
            lines.add("");
            lines.add("    private static MemoryLayout asLayout(Class<?> type) {");
            lines.add("        if (type == byte.class) {");
            lines.add("            return ValueLayout.JAVA_BYTE;");
            lines.add("        } else if (type == short.class) {");
            lines.add("            return ValueLayout.JAVA_SHORT;");
            lines.add("        } else if (type == int.class) {");
            lines.add("            return ValueLayout.JAVA_INT;");
            lines.add("        } else if (type == long.class) {");
            lines.add("            return ValueLayout.JAVA_LONG;");
            lines.add("        } else if (type == float.class) {");
            lines.add("            return ValueLayout.JAVA_FLOAT;");
            lines.add("        } else if (type == double.class) {");
            lines.add("            return ValueLayout.JAVA_DOUBLE;");
            lines.add("        }");
            lines.add("        throw shouldNotReachHere(\"Unsupported layout carrier: \" + type);");
            lines.add("    }");
            lines.add("");
        }

        // resolve return and argument types and declare downcall method handles
        for (CApiExternalFunctionSignatureDesc sig : signatures) {
            // determine arg types and return type for signature; initializer syntax:
            // 'ExternalFunctionSignature(ArgDescriptor returnValue, ArgDescriptor... arguments)'
            String externalFunctionSignatureInitializer = getExternalFunctionSignatureInitializer(sig.origin);

            int start = externalFunctionSignatureInitializer.indexOf('(');
            int end = externalFunctionSignatureInitializer.lastIndexOf(')');
            String[] initArgs = externalFunctionSignatureInitializer.substring(start + 1, end).split(",");

            assert !sig.cannotRaise;
            sig.cannotRaise = Boolean.valueOf(initArgs[0].strip());
            assert sig.returnType == null;
            sig.returnType = initArgs[1].strip();
            assert sig.argumentTypes == null;
            sig.argumentTypes = Arrays.stream(initArgs).skip(2).map(String::strip).toArray(String[]::new);
        }

        for (CApiExternalFunctionSignatureDesc sig : signatures) {
            assert sig.returnType != null;
            assert sig.argumentTypes != null;
            String returnType = toJavaNfiType(sig.returnType);
            String returnTypeLiteral = toClassLiteral(returnType);
            List<String> argTypes = Arrays.stream(sig.argumentTypes).map(this::toJavaNfiType).toList();

            boolean isVoidReturn = "void".equals(returnType);

            List<String> invokeArgs = new LinkedList<>();
            if (sig.cannotRaise) {
                invokeArgs.add("CApiTiming timing");
                invokeArgs.add("NfiBoundFunction nfiFunction");
            } else {
                invokeArgs.add("VirtualFrame frame");
                invokeArgs.add("CApiTiming timing");
                invokeArgs.add("NfiContext nfiContext");
                invokeArgs.add("BoundaryCallData boundaryCallData");
                invokeArgs.add("PythonThreadState threadState");
                invokeArgs.add("NfiBoundFunction nfiFunction");
            }
            int i = 0;
            for (String argType : argTypes) {
                invokeArgs.add(argType + " " + argName(i++));
            }

            List<String> cArgs = new LinkedList<>();
            i = 0;
            for (String ignored : argTypes) {
                cArgs.add(argName(i++));
            }

            List<String> typedArgs = new LinkedList<>();
            i = 0;
            for (String argType : argTypes) {
                typedArgs.add(argType + " " + argName(i++));
            }

            if (hasFfmDowncalls) {
                List<String> methodTypeArgs = new ArrayList<>();
                methodTypeArgs.add("long.class");
                for (String argType : argTypes) {
                    methodTypeArgs.add(toClassLiteral(argType));
                }
                lines.add("    private static final MethodHandle " + getNfiMethodHandleVarName(sig.name) + " = createDowncallHandle(" +
                                "MethodType.methodType(" + returnTypeLiteral + ", " + String.join(", ", methodTypeArgs) + "), false);");
            }

            lines.add("");
            if (sig.cannotRaise) {
                lines.add("    public static " + returnType + " invoke" + sig.name + "(" + String.join(", ", invokeArgs) + ") {");
                String returnStmt = isVoidReturn ? "" : "return ";
                lines.add("        CApiTiming.enter();");
                lines.add("        try {");
                lines.add("            " + returnStmt + "invoke" + sig.name + "(" +
                                "nfiFunction.getAddress()" +
                                (cArgs.isEmpty() ? "" : ", " + String.join(", ", cArgs)) + ");");
                lines.add("        } catch (Throwable exception) {");
                lines.add("            throw CompilerDirectives.shouldNotReachHere(exception);");
                lines.add("        } finally {");
                lines.add("            CApiTiming.exit(timing);");
                lines.add("        }");
                lines.add("    }");
                lines.add("");
            } else {
                List<String> contextInvokeArgs = new LinkedList<>();
                contextInvokeArgs.add("VirtualFrame frame");
                contextInvokeArgs.add("CApiTiming timing");
                contextInvokeArgs.add("PythonContext context");
                contextInvokeArgs.add("NfiBoundFunction nfiFunction");
                contextInvokeArgs.addAll(typedArgs);

                String returnStmt = isVoidReturn ? "" : "return ";

                List<String> nullFrameInvokeArgs = new LinkedList<>();
                nullFrameInvokeArgs.add("CApiTiming timing");
                nullFrameInvokeArgs.add("PythonContext context");
                nullFrameInvokeArgs.add("NfiBoundFunction nfiFunction");
                nullFrameInvokeArgs.addAll(typedArgs);
                lines.add("");

                lines.add("    public static " + returnType + " invoke" + sig.name + "(" + String.join(", ", invokeArgs) + ") {");
                for (int j = 0; j < argTypes.size(); j++) {
                    if (argTypes.get(j).contains("PyObject")) {
                        lines.add("    assert EnsurePythonObjectNode.doesNotNeedPromotion(" + argName(j) + ");");
                    }
                }
                lines.add("        // If any code requested the caught exception (i.e. used 'sys.exc_info()'), we store;");
                lines.add("        // it to the context since we cannot propagate it through the native frames.");
                lines.add("");
                lines.add("        Object state = BoundaryCallContext.enter(frame, threadState, boundaryCallData);");
                lines.add("        CApiTiming.enter();");
                lines.add("        try {");
                lines.add("            " + returnStmt + "invoke" + sig.name + "(" +
                                "nfiFunction.getAddress()" +
                                (cArgs.isEmpty() ? "" : ", " + String.join(", ", cArgs)) + ");");
                lines.add("        } catch (Throwable exception) {");
                lines.add("            CompilerDirectives.transferToInterpreterAndInvalidate();");
                lines.add("            GilNode.uncachedAcquire();");
                lines.add("            throw CompilerDirectives.shouldNotReachHere(exception);");
                lines.add("        } finally {");
                lines.add("            CApiTiming.exit(timing);");
                lines.add("            if (frame != null && threadState.getCaughtException() != null) {");
                lines.add("                PArguments.setException(frame, threadState.getCaughtException());");
                lines.add("            }");
                lines.add("            BoundaryCallContext.exit(frame, threadState, state);");
                lines.add("        }");
                lines.add("    }");
                lines.add("");
            }

            List<String> rawInvokeArgs = new LinkedList<>();
            rawInvokeArgs.add("long function");
            i = 0;
            for (String argType : argTypes) {
                rawInvokeArgs.add(argType + " " + argName(i++));
            }

            lines.add("    @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)");
            lines.add("    public static " + returnType + " invoke" + sig.name + "(" + String.join(", ", rawInvokeArgs) + ") throws Throwable {");
            if (hasFfmDowncalls) {
                String directArgExpr = cArgs.isEmpty() ? "function" : "function, " + String.join(", ", cArgs);
                if (isVoidReturn) {
                    lines.add("        " + getNfiMethodHandleVarName(sig.name) + ".invokeExact(" + directArgExpr + ");");
                } else {
                    lines.add("        return (" + returnType + ") " + getNfiMethodHandleVarName(sig.name) + ".invokeExact(" + directArgExpr + ");");
                }
            } else {
                lines.add("        throw CompilerDirectives.shouldNotReachHere(NFI_UNAVAILABLE_MESSAGE);");
            }
            lines.add("    }");
        }
        lines.add("}");

        var origins = signatures.stream().map((sig) -> sig.origin).toArray(Element[]::new);
        var file = processingEnv.getFiler().createSourceFile(EXFUNC_INVOKER_PACKAGE + "." + EXFUNC_INVOKER_CLASS_NAME, origins);
        try (var w = file.openWriter()) {
            w.append(String.join(System.lineSeparator(), lines));
        }
    }

    /**
     * @param wrapperNames enum constant names denoting wrapper descriptors
     */
    private record CApiExternalFunctionWrapperDesc(Element origin, List<VariableElement> wrapperNames) {
    }

    private boolean verifyArguments(ExecutableElement origin, String... expectedPrefixArgs) {
        if (origin.getParameters().size() < expectedPrefixArgs.length) {
            processingEnv.getMessager().printError(String.format("Method \"%s\" must at least have parameters %s.", origin, Arrays.toString(expectedPrefixArgs)), origin);
            return false;
        }
        assert origin.getParameters().size() >= expectedPrefixArgs.length;
        Iterator<? extends VariableElement> iterator = origin.getParameters().iterator();
        for (int i = 0; i < expectedPrefixArgs.length; i++) {
            VariableElement formalParameter = iterator.next();
            TypeElement te = (TypeElement) processingEnv.getTypeUtils().asElement(formalParameter.asType());

            if (!expectedPrefixArgs[i].equals(te.getQualifiedName().toString())) {
                processingEnv.getMessager().printError(String.format("Argument %d of method \"%s\" must be of type \"%s\" but was \"%s\"", i, origin, expectedPrefixArgs[i], formalParameter), origin);
                return false;
            }
        }
        return true;
    }

    private static boolean needsConversion(TypeMirror type) {
        return !type.getKind().isPrimitive() && type.getKind() != TypeKind.VOID;
    }

    private static boolean needsReachabilityFence(TypeMirror type) {
        return !type.getKind().isPrimitive() && type.getKind() != TypeKind.NULL;
    }

    /**
     * Lookup a method in {@code enclosingType} that exactly satisfies the specified signature and
     * has the given name prefix.
     */
    private ExecutableElement findMethod(Element location, TypeMirror enclosingType, String prefix, TypeMirror retType, TypeMirror... argTypes) {
        Types typeUtils = processingEnv.getTypeUtils();
        Elements elementUtils = processingEnv.getElementUtils();

        Element el = typeUtils.asElement(enclosingType);
        if (!(el instanceof TypeElement typeElement)) {
            processingEnv.getMessager().printError("Type %s does not have any executable methods.", location);
            // Not a declared type (could be primitive, array, type variable, etc.)
            return null;
        }

        for (Element e : elementUtils.getAllMembers(typeElement)) {
            if (e.getKind() == ElementKind.METHOD && e.getSimpleName().toString().startsWith(prefix)) {
                ExecutableElement method = (ExecutableElement) e;
                // if (retType.equals(method.getReturnType()) &&
                // argType.equals(method.getParameters().get(0).asType()) ) {
                TypeMirror[] parameters = method.getParameters().stream().map(VariableElement::asType).toArray(TypeMirror[]::new);
                if (retType.equals(method.getReturnType()) && Arrays.equals(parameters, argTypes)) {
                    return method;
                }
            }
        }
        processingEnv.getMessager().printError(String.format("Type %s does not have any \"%s %s*(%s)\" method.",
                        enclosingType, retType, prefix, Arrays.stream(argTypes).map(Object::toString).collect(Collectors.joining(", "))), location);
        return null;
    }

    private static final String WRAPPER_DESCRIPTOR_GEN_CLASS_NAME = "WrapperDescriptorRootNodesGen";

    private void generateExternalFunctionHelperNodes(List<InvokeExternalFunctionDesc> externalFunctionDescs) throws IOException {
        final Types typeUtils = processingEnv.getTypeUtils();
        final Elements elementUtils = processingEnv.getElementUtils();
        TypeMirror nodeType = elementUtils.getTypeElement(TRUFFLE_NODE).asType();
        Map<TypeElement, List<InvokeExternalFunctionDesc>> helperInvokeDescsByClass = new LinkedHashMap<>();

        for (InvokeExternalFunctionDesc desc : externalFunctionDescs) {
            if (!isWrapperRootInvoke(desc)) {
                TypeElement clazz = (TypeElement) desc.origin.getEnclosingElement();
                helperInvokeDescsByClass.computeIfAbsent(clazz, k -> new ArrayList<>()).add(desc);
            }
        }

        for (Map.Entry<TypeElement, List<InvokeExternalFunctionDesc>> entry : helperInvokeDescsByClass.entrySet()) {
            TypeElement clazz = entry.getKey();
            if (!typeUtils.isSubtype(clazz.asType(), nodeType)) {
                processingEnv.getMessager().printError(String.format("Type %s must extend %s.", clazz, TRUFFLE_NODE), clazz);
                return;
            }

            String packageName = elementUtils.getPackageOf(clazz).getQualifiedName().toString();
            String genClassName = getGeneratedExternalInvokeHelperClassName(clazz);
            ArrayList<String> lines = new ArrayList<>();

            lines.add("// @formatter:off");
            lines.add("// Checkstyle: stop");
            lines.add("// Generated by annotation processor: " + getClass().getName());
            lines.add("package " + packageName + ";");
            lines.add("");
            lines.add("import " + clazz.getQualifiedName() + ";");
            lines.add("import " + EXFUNC_INVOKER_PACKAGE + "." + EXFUNC_INVOKER_CLASS_NAME + ";");
            lines.add("import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTiming;");
            lines.add("import " + NFI_BOUND_FUNCTION + ";");
            lines.add("import " + PYTHON_CONTEXT + ";");
            lines.add("import com.oracle.graal.python.runtime.IndirectCallData.BoundaryCallData;");
            lines.add("import " + TRUFFLE_VIRTUAL_FRAME + ";");
            lines.add("");
            lines.add("public final class " + genClassName + " extends " + clazz.getSimpleName() + " {");
            lines.add("");
            lines.add("    private static final " + genClassName + " UNCACHED = new " + genClassName + "();");
            lines.add("");
            for (InvokeExternalFunctionDesc helper : entry.getValue()) {
                lines.add("    private static final CApiTiming TIMING_" + helper.origin.getSimpleName() + " = CApiTiming.create(true, \"" + helper.origin.getSimpleName() + "\");");
            }
            lines.add("");
            lines.add("    private " + genClassName + "() {");
            lines.add("    }");
            lines.add("");
            lines.add("    public static " + clazz.getSimpleName() + " create() {");
            lines.add("        return new " + genClassName + "();");
            lines.add("    }");
            lines.add("");
            lines.add("    public static " + clazz.getSimpleName() + " getUncached() {");
            lines.add("        return UNCACHED;");
            lines.add("    }");

            for (InvokeExternalFunctionDesc helper : entry.getValue()) {
                List<? extends VariableElement> formalParameters = helper.origin.getParameters();
                boolean isVoidReturn = helper.origin.getReturnType().getKind() == TypeKind.VOID;
                boolean cannotRaise = isCannotRaise(helper.signature);

                if (!verifyArguments(helper.origin, TRUFFLE_VIRTUAL_FRAME, PYTHON_CONTEXT, NFI_BOUND_FUNCTION)) {
                    return;
                }

                int actualArgConversionClasses = helper.argumentTypes.size();
                int expectedArgConversionClasses = formalParameters.size() - 3;
                if (actualArgConversionClasses != expectedArgConversionClasses) {
                    processingEnv.getMessager().printError(String.format("You need to specify exactly %d argument conversion classes but there were %d.",
                                    expectedArgConversionClasses, actualArgConversionClasses), helper.origin);
                    return;
                }

                List<String> methodInvokeFormalArgs = new ArrayList<>(formalParameters.size());
                List<String> cArgs = new ArrayList<>();
                if (cannotRaise) {
                    cArgs.add("TIMING_" + helper.origin.getSimpleName());
                    cArgs.add(formalParameters.get(2).getSimpleName().toString()); // boundFunction
                } else {
                    cArgs.add(formalParameters.get(0).getSimpleName().toString()); // frame
                    cArgs.add("TIMING_" + helper.origin.getSimpleName());
                    cArgs.add(formalParameters.get(1).getSimpleName().toString()); // context
                    cArgs.add(formalParameters.get(2).getSimpleName().toString()); // boundFunction
                }

                for (VariableElement formalParameter : formalParameters) {
                    TypeMirror type = formalParameter.asType();
                    Element element = typeUtils.asElement(type);
                    String typeString = element != null ? element.getSimpleName().toString() : type.toString();
                    methodInvokeFormalArgs.add(typeString + " " + formalParameter.getSimpleName());
                }
                for (int i = 3; i < formalParameters.size(); i++) {
                    cArgs.add(formalParameters.get(i).getSimpleName().toString());
                }

                lines.add("");
                lines.add("    @Override");
                lines.add("    protected " + getSimpleName(helper.origin.getReturnType()) + " " + helper.origin.getSimpleName() + "(" + String.join(", ", methodInvokeFormalArgs) + ") {");
                if (isVoidReturn) {
                    lines.add("        " + EXFUNC_INVOKER_CLASS_NAME + ".invoke" + helper.signature + "(" + String.join(", ", cArgs) + ");");
                } else {
                    lines.add("        return " + EXFUNC_INVOKER_CLASS_NAME + ".invoke" + helper.signature + "(" + String.join(", ", cArgs) + ");");
                }
                lines.add("    }");
            }

            lines.add("}");

            var origins = entry.getValue().stream().map((desc) -> desc.origin).toArray(Element[]::new);
            var file = processingEnv.getFiler().createSourceFile(packageName + "." + genClassName, origins);
            try (var w = file.openWriter()) {
                w.append(String.join(System.lineSeparator(), lines));
            }
        }
    }

    private void generateExternalFunctionRootNodes(List<InvokeExternalFunctionDesc> externalFunctionDescs, List<CApiExternalFunctionWrapperDesc> wrappers,
                    @SuppressWarnings("unused") Map<String, CApiExternalFunctionSignatureDesc> externalFunctionSignatures) throws IOException {
        final Types typeUtils = processingEnv.getTypeUtils();
        List<InvokeExternalFunctionDesc> wrapperInvokeDescs = externalFunctionDescs.stream().filter(this::isWrapperRootInvoke).toList();
        ArrayList<String> lines = new ArrayList<>();

        lines.add("// @formatter:off");
        lines.add("// Checkstyle: stop");
        lines.add("// Generated by annotation processor: " + getClass().getName());
        lines.add("package " + EXFUNC_INVOKER_PACKAGE + ";");
        lines.add("");

        lines.add("import com.oracle.graal.python.PythonLanguage;");
        lines.add("import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.EnsurePythonObjectNode;");
        for (InvokeExternalFunctionDesc wrapper : wrapperInvokeDescs) {
            Element clazz = wrapper.origin.getEnclosingElement();
            lines.add("import " + EXFUNC_INVOKER_PACKAGE + "." + clazz.getEnclosingElement().getSimpleName() + "." + clazz.getSimpleName() + ";");
        }
        lines.add("import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper;");
        lines.add("import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.WrapperDescriptorRoot;");
        lines.add("import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTiming;");
        lines.add("import " + NFI_BOUND_FUNCTION + ";");
        lines.add("import com.oracle.graal.python.runtime.ExecutionContext.CalleeContext;");
        lines.add("import com.oracle.graal.python.runtime.IndirectCallData.BoundaryCallData;");
        lines.add("import com.oracle.graal.python.runtime.PythonContext;");
        lines.add("import com.oracle.graal.python.runtime.PythonContext.GetThreadStateNode;");
        lines.add("import com.oracle.truffle.api.CompilerDirectives;");
        lines.add("import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;");
        lines.add("import " + TRUFFLE_VIRTUAL_FRAME + ";");
        lines.add("import com.oracle.truffle.api.nodes.Node.Child;");
        lines.add("import com.oracle.truffle.api.strings.TruffleString;");
        lines.add("");
        int importMarker = lines.size();
        lines.add("");
        lines.add("import java.lang.ref.Reference;");
        lines.add("");
        lines.add("public final class " + WRAPPER_DESCRIPTOR_GEN_CLASS_NAME + " {");
        lines.add("");
        lines.add("    private " + WRAPPER_DESCRIPTOR_GEN_CLASS_NAME + "() {");
        lines.add("        // no instances");
        lines.add("    }");
        lines.add("");

        Set<Name> classesToImport = new HashSet<>();

        // declare downcall signature variables and resolve return and argument types
        for (InvokeExternalFunctionDesc wrapper : wrapperInvokeDescs) {
            boolean errorOccurred = false;
            boolean cannotRaise = isCannotRaise(wrapper.signature);

            assert wrapper.returnType != null;
            assert wrapper.argumentTypes != null;
            List<? extends VariableElement> formalParameters = wrapper.origin.getParameters();

            boolean isVoidReturn = wrapper.origin.getReturnType().getKind() == TypeKind.VOID;

            // verify arguments of annotated method
            if (!verifyArguments(wrapper.origin, TRUFFLE_VIRTUAL_FRAME, NFI_BOUND_FUNCTION)) {
                return;
            }

            // check if the right count of argument conversion classes was specified
            int actualArgConversionClasses = wrapper.argumentTypes.size();
            int expectedArgConversionClasses = formalParameters.size() - 2;
            if (actualArgConversionClasses != expectedArgConversionClasses) {
                processingEnv.getMessager().printError(String.format("You need to specify exactly %d argument conversion classes but there were %d.",
                                expectedArgConversionClasses, actualArgConversionClasses), wrapper.origin);
                return;
            }

            // formal arguments of method 'invokeExternalFunction'
            List<String> methodInvokeFormalArgs = new ArrayList<>(formalParameters.size());
            List<VariableElement> needReachabilityFence = new LinkedList<>();
            for (VariableElement formalParameter : formalParameters) {
                TypeMirror type = formalParameter.asType();
                Element element = typeUtils.asElement(type);
                String typeString;
                if (element != null) {
                    typeString = element.getSimpleName().toString();
                } else {
                    typeString = type.toString();
                }
                methodInvokeFormalArgs.add(typeString + " " + formalParameter.getSimpleName());
            }

            // list of arguments passed to 'ExternalFunctionInvoker.invoke*' method
            List<String> cArgs = new LinkedList<>();
            if (cannotRaise) {
                cArgs.add("timing");
                cArgs.add(formalParameters.get(1).getSimpleName().toString()); // boundFunction
            } else {
                cArgs.add(formalParameters.getFirst().getSimpleName().toString()); // frame
                cArgs.add("timing");
                cArgs.add("context.ensureNfiContext()");
                cArgs.add("boundaryCallData"); // boundaryCallData
                cArgs.add("getThreadStateNode.executeCached(context)"); // threadState
                cArgs.add(formalParameters.get(1).getSimpleName().toString()); // boundFunction
            }

            Element clazz = wrapper.origin.getEnclosingElement();
            String genClassName = clazz.getSimpleName() + "Gen";

            lines.add("");
            lines.add("    public static final class " + genClassName + " extends " + clazz.getSimpleName() + " {");
            lines.add("");
            lines.add("        @Child private CalleeContext calleeContext = CalleeContext.create();");
            lines.add("        @Child private BoundaryCallData boundaryCallData;");
            lines.add("        @Child private GetThreadStateNode getThreadStateNode = GetThreadStateNode.create();");
            for (int j = 0; j < wrapper.argumentTypes.size(); j++) {
                TypeMirror argConversionClass = wrapper.argumentTypes.get(j);

                VariableElement formalParameter = formalParameters.get(j + 2);
                Name formalParameterName = formalParameter.getSimpleName();
                String convertNodeName = "convert" + formalParameterName;
                if (needsConversion(argConversionClass)) {
                    TypeElement argConversionClassTypeElement = (TypeElement) typeUtils.asElement(argConversionClass);
                    classesToImport.add(argConversionClassTypeElement.getQualifiedName());
                    Name simpleName = argConversionClassTypeElement.getSimpleName();
                    ExecutableElement createMethod = findMethod(wrapper.origin, argConversionClass, "create", argConversionClass);
                    String createMethodName = createMethod != null ? createMethod.getSimpleName().toString() : "null";
                    lines.add(String.format("        @Child private %s %s = %s.%s();", simpleName, convertNodeName, simpleName, createMethodName));

                    /*
                     * TODO: Reliably determine the expected type for the actual parameter.
                     *
                     * This is about the required type for the actual parameter for the call of
                     * `ExternalFunctionInvoker.invoke*`. The required parameter type should be
                     * inferred from the formal parameter type of the invoke method. Since those
                     * methods are generated in the same go, we cannot rely on them being already
                     * available. However, we know how they are generated in
                     * `generateExternalFunctionInvoker` and we could use that. For this, we will
                     * need table `externalFunctionSignatures`.
                     */
                    TypeMirror expectedActualType = toJavaNfiType(formalParameter.asType());

                    ExecutableElement executeMethod = findMethod(wrapper.origin, argConversionClass, "execute", expectedActualType, formalParameter.asType());
                    String executeMethodName = executeMethod != null ? executeMethod.getSimpleName().toString() : "null";
                    if (executeMethod != null) {
                        /*
                         * Also already generate the expression that invokes the Python-to-native
                         * conversion node.
                         */
                        cArgs.add(String.format("%s.%s(%s)", convertNodeName, executeMethodName, formalParameterName));
                    } else {
                        errorOccurred = true;
                    }
                } else {
                    // If no conversion is required, then just pass the formal parameter.
                    cArgs.add(formalParameterName.toString());
                }

                if (needsReachabilityFence(formalParameter.asType())) {
                    needReachabilityFence.add(formalParameter);
                }
            }
            lines.add("");
            lines.add("        private final CApiTiming timing;");
            lines.add("");
            lines.add("        public " + genClassName + "(PythonLanguage language, TruffleString name, PExternalFunctionWrapper wrapper) {");
            lines.add("            super(language, name, wrapper);");
            lines.add("            this.timing = CApiTiming.create(true, name);");
            lines.add("            this.boundaryCallData = BoundaryCallData.createFor(this);");
            lines.add("        }");
            lines.add("");

            lines.add("        @Override");
            lines.add("        protected " + getSimpleName(wrapper.origin.getReturnType()) + " invokeExternalFunction(" + String.join(", ", methodInvokeFormalArgs) + ") {");
            lines.add("            PythonContext context = PythonContext.get(this);");

            String returnStmt = isVoidReturn ? "" : "return ";
            lines.add("            try {");
            if (errorOccurred) {
                lines.add("                throw new RuntimeException(\"error occurred during generation\");");
            } else {
                lines.add("                " + returnStmt + EXFUNC_INVOKER_CLASS_NAME + "." + "invoke" + wrapper.signature + "(" + String.join(", ", cArgs) + ");");
            }
            lines.add("            } finally {");
            for (VariableElement formalParameter : needReachabilityFence) {
                lines.add(String.format("                Reference.reachabilityFence(%s);", formalParameter.getSimpleName()));
            }
            lines.add("            }");

            lines.add("        }");
            lines.add("    }");
        }

        for (Name classToImport : classesToImport) {
            lines.add(importMarker++, String.format("import %s;", classToImport));
        }

        // generate factory method
        lines.add("    @TruffleBoundary");
        lines.add("    public static WrapperDescriptorRoot create(PythonLanguage language, TruffleString name, PExternalFunctionWrapper wrapper) {");
        lines.add("        return switch (wrapper) {");
        for (CApiExternalFunctionWrapperDesc wrapper : wrappers) {
            String wrapperList = wrapper.wrapperNames.stream().map(CApiBuiltinsProcessor::name).collect(Collectors.joining(", "));
            lines.add("        case " + wrapperList + " -> new " + name(wrapper.origin) + "Gen(language, name, wrapper);");
        }
        lines.add("        default -> throw CompilerDirectives.shouldNotReachHere(\"no root node for wrapper \" + wrapper);");
        lines.add("        };");
        lines.add("    }");

        // closing brace for WRAPPER_DESCRIPTOR_GEN_CLASS_NAME
        lines.add("}");

        var origins = wrapperInvokeDescs.stream().map((desc) -> desc.origin).toArray(Element[]::new);
        var file = processingEnv.getFiler().createSourceFile(EXFUNC_INVOKER_PACKAGE + "." + WRAPPER_DESCRIPTOR_GEN_CLASS_NAME, origins);
        try (var w = file.openWriter()) {
            w.append(String.join(System.lineSeparator(), lines));
        }
    }

    @Override
    @SuppressWarnings({"try", "unused"})
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment re) {
        if (re.processingOver()) {
            return true;
        }

        List<CApiBuiltinDesc> javaBuiltins = new ArrayList<>();
        List<CApiBuiltinDesc> additionalBuiltins = new ArrayList<>();
        addCApiBuiltins(re, javaBuiltins, additionalBuiltins);

        List<CApiBuiltinDesc> allBuiltins = new ArrayList<>();
        allBuiltins.addAll(additionalBuiltins);
        for (var entry : javaBuiltins) {
            Optional<CApiBuiltinDesc> existing1 = findBuiltin(allBuiltins, entry.name);
            if (existing1.isPresent()) {
                compareFunction(entry.name, entry.returnType, existing1.get().returnType, entry.arguments, existing1.get().arguments);
            } else {
                allBuiltins.add(entry);
            }
        }
        allBuiltins.sort((a, b) -> a.name.compareTo(b.name));

        List<String> constants = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        List<String> structs = new ArrayList<>();
        List<CApiExternalFunctionSignatureDesc> externalFunctionSignatures = new ArrayList<>();
        for (var el : re.getElementsAnnotatedWith(CApiConstants.class)) {
            if (el.getKind() == ElementKind.ENUM) {
                for (var enumBit : el.getEnclosedElements()) {
                    if (enumBit.getKind() == ElementKind.ENUM_CONSTANT) {
                        constants.add(enumBit.getSimpleName().toString());
                    }
                }
            } else {
                processingEnv.getMessager().printError(CApiConstants.class.getSimpleName() + " is only applicable for enums.", el);
            }
        }
        for (var el : re.getElementsAnnotatedWith(CApiFields.class)) {
            if (el.getKind() != ElementKind.ENUM) {
                processingEnv.getMessager().printError(CApiFields.class.getSimpleName() + " is only applicable for enums.", el);
            } else {
                for (var enumBit : el.getEnclosedElements()) {
                    if (enumBit.getKind() == ElementKind.ENUM_CONSTANT) {
                        fields.add(enumBit.getSimpleName().toString());
                    }
                }
            }
        }
        for (var el : re.getElementsAnnotatedWith(CApiStructs.class)) {
            if (el.getKind() != ElementKind.ENUM) {
                processingEnv.getMessager().printError(CApiStructs.class.getSimpleName() + " is only applicable for enums.", el);
            } else {
                for (var enumBit : el.getEnclosedElements()) {
                    if (enumBit.getKind() == ElementKind.ENUM_CONSTANT) {
                        structs.add(enumBit.getSimpleName().toString());
                    }
                }
            }
        }
        Map<String, CApiExternalFunctionSignatureDesc> sigs = new HashMap<>();
        for (var el : re.getElementsAnnotatedWith(CApiExternalFunctionSignatures.class)) {
            if (el.getKind() == ElementKind.ENUM) {
                for (var enumBit : el.getEnclosedElements()) {
                    if (enumBit.getKind() == ElementKind.ENUM_CONSTANT) {
                        CApiExternalFunctionSignatureDesc value = new CApiExternalFunctionSignatureDesc((VariableElement) enumBit, enumBit.getSimpleName().toString());
                        sigs.put(value.name, value);
                    }
                }
            } else {
                processingEnv.getMessager().printError(CApiExternalFunctionSignatures.class.getSimpleName() + " is only applicable for enums.", el);
            }
        }
        List<CApiExternalFunctionWrapperDesc> cApiExternalFunctionWrapperDescs = new LinkedList<>();
        List<InvokeExternalFunctionDesc> externalFunctionDescs = collectExternalFunctionAndWrapperDescs(re, cApiExternalFunctionWrapperDescs);

        if (allBuiltins.isEmpty()) {
            return true;
        }
        try {
            if (trees != null) {
                // needs jdk.compiler
                generateCApiSource(allBuiltins, constants, fields, structs);
                generateCApiHeader(javaBuiltins);
                generateExternalFunctionInvoker(new ArrayList<>(sigs.values()));
                generateExternalFunctionHelperNodes(externalFunctionDescs);
                generateExternalFunctionRootNodes(externalFunctionDescs, cApiExternalFunctionWrapperDescs, sigs);
            }
            generateBuiltinRegistry(javaBuiltins);
            generateUpcallConfig(javaBuiltins);
            generateCApiAsserts(allBuiltins);
            if (trees != null) {
                // needs jdk.compiler
                checkImports(allBuiltins);
            }
        } catch (IOException e) {
            processingEnv.getMessager().printError(e.getMessage());
        }
        return true;
    }
}
