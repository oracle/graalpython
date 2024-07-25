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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

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
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.AbstractAnnotationValueVisitor14;
import javax.lang.model.util.ElementFilter;
import javax.tools.StandardLocation;

import com.oracle.graal.python.annotations.CApiConstants;
import com.oracle.graal.python.annotations.CApiFields;
import com.oracle.graal.python.annotations.CApiStructs;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

public class CApiBuiltinsProcessor extends AbstractProcessor {
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

    @Override
    public synchronized void init(ProcessingEnvironment pe) {
        super.init(pe);
        try {
            this.trees = Trees.instance(pe);
        } catch (Throwable t) {
            // ECJ does not support this, so we skip the some processing of C API builtins
            pe.getMessager().printWarning("The compiler does not support source tree parsing during annotation processing. Regeneration of Python C API builtins will be incorrect.");
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

    private boolean isVoid(VariableElement obj) {
        return getFieldInitializer(obj).contains("ArgBehavior.Void");
    }

    private static String name(VariableElement obj) {
        return obj.getSimpleName().toString();
    }

    private static final class CApiBuiltinDesc {
        public final Element origin;
        public final String name;
        public final VariableElement[] arguments;
        public final VariableElement returnType;
        public final boolean acquireGil;
        public final String call;
        public final String factory;
        public int id;

        public CApiBuiltinDesc(Element origin, String name, VariableElement returnType, VariableElement[] arguments, boolean acquireGil, String call, String factory) {
            this.origin = origin;
            this.name = name;
            this.returnType = returnType;
            this.arguments = arguments;
            this.acquireGil = acquireGil;
            this.call = call;
            this.factory = factory;
        }
    }

    private static String argName(int i) {
        return "" + (char) ('a' + i);
    }

    private static final String CAPI_BUILTIN = "com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin";
    private static final String CAPI_BUILTINS = "com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltins";

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(CAPI_BUILTIN, CAPI_BUILTINS, CApiFields.class.getName(), CApiConstants.class.getName(), CApiStructs.class.getName());
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
                    String call = name(findValue(builtin, "call", VariableElement.class));
                    // boolean inlined = findValue(builtin, "inlined", Boolean.class);
                    VariableElement[] args = findValues(builtin, "args", VariableElement.class).toArray(new VariableElement[0]);
                    if (((TypeElement) element).getQualifiedName().toString().equals("com.oracle.graal.python.builtins.objects.cext.capi.CApiFunction.Dummy")) {
                        additionalBuiltins.add(new CApiBuiltinDesc(element, builtinName, ret, args, acquireGil, call, null));
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
                        verifyNodeClass(((TypeElement) element), builtin);
                        javaBuiltins.add(new CApiBuiltinDesc(element, name, ret, args, acquireGil, call, genName));
                    }
                }
            }
        }
        javaBuiltins.sort((a, b) -> a.name.compareTo(b.name));
        for (int i = 0; i < javaBuiltins.size(); i++) {
            javaBuiltins.get(i).id = i;
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
                    line = "    " + (isVoid(value.returnType) ? "" : "return ") + "Graal" + name + "(";
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

        lines.add("PyAPI_FUNC(int64_t*) PyTruffle_constants() {");
        lines.add("    static int64_t constants[] = {");
        for (var constant : constants) {
            lines.add("        (int64_t) " + constant + ",");
        }
        lines.add("        0xdead1111 // marker value");
        lines.add("    };");
        lines.add("    return constants;");
        lines.add("}");
        lines.add("PyAPI_FUNC(Py_ssize_t*) PyTruffle_struct_offsets() {");
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
        lines.add("PyAPI_FUNC(Py_ssize_t*) PyTruffle_struct_sizes() {");
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

    private void updateResource(String name, List<CApiBuiltinDesc> javaBuiltins, List<String> lines) throws IOException {
        var origins = javaBuiltins.stream().map((jb) -> jb.origin).toArray(Element[]::new);
        String oldContents = "";
        String newContents = String.join(System.lineSeparator(), lines);
        try {
            oldContents = processingEnv.getFiler().getResource(StandardLocation.NATIVE_HEADER_OUTPUT, "", name).getCharContent(true).toString();
        } catch (IOException e) {
            // pass to regenerate
        }
        if (!oldContents.equals(newContents)) {
            var file = processingEnv.getFiler().createResource(StandardLocation.NATIVE_HEADER_OUTPUT, "", name, origins);
            try (var w = file.openWriter()) {
                w.append(newContents);
            }
        } else {
            processingEnv.getMessager().printNote("Python %s is up to date".formatted(name));
        }
    }

    /**
     * Generates the builtin specification in capi.h, which includes only the builtins implemented
     * in Java code. Additionally, it generates helpers for all "Py_get_" and "Py_set_" builtins.
     *
     * @param methodFlags
     */
    private void generateCApiHeader(List<CApiBuiltinDesc> javaBuiltins, Map<String, Long> methodFlags) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("#define CAPI_BUILTINS \\");
        int id = 0;
        for (var entry : javaBuiltins) {
            assert (id++) == entry.id;
            String line = "    BUILTIN(" + entry.name + ", " + getCSignature(entry.returnType);
            for (var arg : entry.arguments) {
                line += ", " + getCSignature(arg);
            }
            line += ") \\";
            lines.add(line);
        }
        lines.add("");

        for (var entry : javaBuiltins) {
            String name = entry.name;
            if (!name.endsWith("_dummy")) {
                if (name.startsWith("Py_get_")) {
                    assert entry.arguments.length == 1 : name;
                    String type = name(entry.arguments[0]).replace("Wrapper", "");
                    StringBuilder macro = new StringBuilder();
                    assert name.charAt(7 + type.length()) == '_' : name;
                    String field = name.substring(7 + type.length() + 1); // after "_"
                    macro.append("#define " + name.substring(7) + "(OBJ) ( points_to_py_handle_space(OBJ) ? Graal" + name + "((" + type + "*) (OBJ)) : ((" + type + "*) (OBJ))->" + field + " )");
                    lines.add(macro.toString());
                } else if (name.startsWith("Py_set_")) {
                    assert entry.arguments.length == 2 : name;
                    String type = name(entry.arguments[0]).replace("Wrapper", "");
                    StringBuilder macro = new StringBuilder();
                    assert name.charAt(7 + type.length()) == '_' : name;
                    String field = name.substring(7 + type.length() + 1); // after "_"
                    macro.append("#define set_" + name.substring(7) + "(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) Graal" + name + "((" + type + "*) (OBJ), (VALUE)); else  ((" + type +
                                    "*) (OBJ))->" + field + " = (VALUE); }");
                    lines.add(macro.toString());
                }
            }
        }

        /*
         * Adding constants for methods flags checks in {@link
         * NativeCAPISymbol.FUN_GET_METHODS_FLAGS}
         */
        lines.add("");
        methodFlags.entrySet().stream().sorted((a, b) -> a.getValue().compareTo(b.getValue())).forEach(e -> lines.add("#define " + e.getKey() + " " + e.getValue()));
        updateResource("capi.gen.h", javaBuiltins, lines);
    }

    /**
     * Generates the contents of the PythonCextBuiltinRegistry class: the list of builtins, the
     * CApiBuiltinNode factory function, and the slot query function.
     */
    private void generateBuiltinRegistry(List<CApiBuiltinDesc> javaBuiltins) throws IOException {
        ArrayList<String> lines = new ArrayList<>();

        lines.add("// @formatter:off");
        lines.add("// Checkstyle: stop");
        lines.add("// Generated by annotation processor: " + getClass().getName());
        lines.add("package %s".formatted("com.oracle.graal.python.builtins.modules.cext;"));
        lines.add("");
        lines.add("import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltinExecutable;");
        lines.add("import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltinNode;");
        lines.add("import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath;");
        lines.add("import com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor;");
        lines.add("");
        lines.add("public abstract class PythonCextBuiltinRegistry {");
        lines.add("");
        lines.add("    private PythonCextBuiltinRegistry() {");
        lines.add("        // no instances");
        lines.add("    }");

        for (var builtin : javaBuiltins) {
            String argString = Arrays.stream(builtin.arguments).map(b -> "ArgDescriptor." + b).collect(Collectors.joining(", "));
            lines.add("    public static final CApiBuiltinExecutable " + builtin.name + " = new CApiBuiltinExecutable(\"" + builtin.name + "\", CApiCallPath." + builtin.call + ", ArgDescriptor." +
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
            lines.add("                return " + builtin.factory + ".create();");
        }

        lines.add("        }");
        lines.add("        return null;");
        lines.add("    }");
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
            } else if (builtin.call.equals("Ignored")) {
                lines.add("        if (hasMember) messages.add(\"unexpected C impl: " + builtin.name + "\");");
            } else {
                lines.add("        messages.add(hasMember ? \"unexpected C impl: " + builtin.name + "\" : \"missing implementation: " + builtin.name + "\");");
            }
        }
        lines.add("");

        var origins = allBuiltins.stream().map((jb) -> jb.origin).toArray(Element[]::new);
        var file = processingEnv.getFiler().createSourceFile("com.oracle.graal.python.builtins.modules.cext.PythonCApiAssertions", origins);
        try (var w = file.openWriter()) {
            w.append("""
                            // @formatter:off
                            // Checkstyle: stop
                            package %s;

                            import java.util.TreeSet;
                            import com.oracle.truffle.api.CompilerDirectives;
                            import com.oracle.truffle.api.interop.InteropLibrary;
                            import com.oracle.truffle.api.interop.UnknownIdentifierException;
                            import com.oracle.truffle.api.interop.UnsupportedMessageException;

                            public abstract class PythonCApiAssertions {

                                private PythonCApiAssertions() {
                                    // no instances
                                }

                                public static boolean reallyHasMember(Object capiLibrary, String name) {
                                    try {
                                        InteropLibrary.getUncached().readMember(capiLibrary, name);
                                    } catch (UnsupportedMessageException e) {
                                        throw CompilerDirectives.shouldNotReachHere(e);
                                    } catch (UnknownIdentifierException e) {
                                        return false;
                                    }
                                    return true;
                                }

                                /**
                                 * Checks whether the "not implemented" state of builtins matches whether they exist in the capi
                                 * library: CApiCallPath#NotImplemented and CApiCallPath#Ignored builtins cannot have an
                                 * implementation, and all others need to be present.
                                 */
                                public static boolean assertBuiltins(Object capiLibrary) {
                                    boolean hasMember = false;
                                    TreeSet<String> messages = new TreeSet<>();
                                    %s
                                    messages.forEach(System.err::println);
                                    return messages.isEmpty();
                                }
                            }
                            """.formatted("com.oracle.graal.python.builtins.modules.cext", String.join(System.lineSeparator(), lines)));
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
    private static final String[] ADDITIONAL = new String[]{"PyCMethod_GetClass", "PyDescrObject_GetName", "PyDescrObject_GetType", "PyInterpreterState_GetIDFromThreadState",
                    "PyMethodDescrObject_GetMethod", "PyObject_GetDoc", "PyObject_SetDoc", "PySlice_Start", "PySlice_Step", "PySlice_Stop", "_PyFrame_SetLineNumber",
                    "_PyCFunction_GetModule", "_PyCFunction_GetMethodDef", "PyCode_GetName",
                    "_PyCFunction_SetModule", "_PyCFunction_SetMethodDef",
                    "PyCode_GetFileName", "_PyArray_Resize", "_PyArray_Data",
                    "_PyErr_Occurred", "_PyNamespace_New", "_Py_GetErrorHandler",
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

        names.removeIf(n -> n.startsWith("Py_get_"));
        names.removeIf(n -> n.startsWith("Py_set_"));
        names.removeIf(n -> n.startsWith("PyTruffle"));
        names.removeIf(n -> n.startsWith("_PyTruffle"));
        names.removeAll(Arrays.asList(ADDITIONAL));
        if (!names.isEmpty()) {
            processingEnv.getMessager().printError("extra builtins (defined in GraalPy, but not in CPython - some of these are necessary for internal modules like 'math'):");
            processingEnv.getMessager().printError("    " + names.stream().collect(Collectors.joining(", ")));
        }
    }

    @Override
    @SuppressWarnings({"try", "unused"})
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment re) {
        if (re.processingOver()) {
            return false;
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
        Collections.sort(allBuiltins, (a, b) -> a.name.compareTo(b.name));

        List<String> constants = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        List<String> structs = new ArrayList<>();
        Map<String, Long> methodFlags = new HashMap<>();
        for (var el : re.getElementsAnnotatedWith(CApiConstants.class)) {
            if (el.getKind() == ElementKind.ENUM) {
                for (var enumBit : el.getEnclosedElements()) {
                    if (enumBit.getKind() == ElementKind.ENUM_CONSTANT) {
                        constants.add(enumBit.getSimpleName().toString());
                    }
                }
            } else if (el.getKind() == ElementKind.CLASS) {
                for (VariableElement field : ElementFilter.fieldsIn(el.getEnclosedElements())) {
                    Object constantValue = field.getConstantValue();
                    if (constantValue instanceof Long longValue) {
                        methodFlags.put(field.getSimpleName().toString(), longValue);
                    }
                }
            } else {
                processingEnv.getMessager().printError(CApiConstants.class.getSimpleName() + " is only applicable for enums.", el);
            }
        }
        for (var el : re.getElementsAnnotatedWith(CApiFields.class)) {
            if (el.getKind() != ElementKind.ENUM) {
                processingEnv.getMessager().printError(CApiConstants.class.getSimpleName() + " is only applicable for enums.", el);
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
                processingEnv.getMessager().printError(CApiConstants.class.getSimpleName() + " is only applicable for enums.", el);
            } else {
                for (var enumBit : el.getEnclosedElements()) {
                    if (enumBit.getKind() == ElementKind.ENUM_CONSTANT) {
                        structs.add(enumBit.getSimpleName().toString());
                    }
                }
            }
        }

        if (allBuiltins.isEmpty()) {
            return true;
        }
        try {
            if (trees != null) {
                // needs jdk.compiler
                generateCApiSource(allBuiltins, constants, fields, structs);
                generateCApiHeader(javaBuiltins, methodFlags);
            }
            generateBuiltinRegistry(javaBuiltins);
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
