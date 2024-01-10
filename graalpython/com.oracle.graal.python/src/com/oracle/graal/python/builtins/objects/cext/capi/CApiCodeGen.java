/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.capi;

import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.CImpl;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Direct;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Ignored;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.NotImplemented;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.VARARGS;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinRegistry;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor;
import com.oracle.graal.python.builtins.objects.cext.structs.CConstants;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructs;
import com.oracle.graal.python.builtins.objects.type.MethodsFlags;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;

/**
 * This class generates the contents of the {@link PythonCextBuiltinRegistry} class and the code
 * needed on the native side to define all function symbols and to forward calls to the Java/Sulong
 * side. The codegen process also checks whether there are undefined or extraneous C API functions.
 */
public final class CApiCodeGen {

    private static final String START_CAPI_BUILTINS = "{{start CAPI_BUILTINS}}";
    private static final String END_CAPI_BUILTINS = "{{end CAPI_BUILTINS}}";

    private static final String START_CAPI_CONSTANTS = "{{start CAPI_CONSTANTS}}";
    private static final String END_CAPI_CONSTANTS = "{{end CAPI_CONSTANTS}}";

    public static final class CApiBuiltinDesc {
        public final String name;
        public final boolean inlined;
        public final ArgDescriptor[] arguments;
        public final ArgDescriptor returnType;
        public final CApiCallPath call;
        public final String factory;
        public int id;

        public CApiBuiltinDesc(String name, boolean inlined, ArgDescriptor returnType, ArgDescriptor[] arguments, CApiCallPath call, String factory) {
            this.name = name;
            this.inlined = inlined;
            this.returnType = returnType;
            this.arguments = arguments;
            this.call = call;
            this.factory = factory;
        }

        public static String getArgSignatureWithName(ArgDescriptor arg, int i) {
            if (arg == VARARGS) {
                return arg.cSignature;
            }
            String sig = arg.getCSignature();
            if (sig.contains("(*)")) {
                // function type
                return sig.replace("(*)", "(*" + argName(i) + ")");
            } else if (sig.endsWith("[]")) {
                return sig.substring(0, sig.length() - 2) + argName(i) + "[]";
            } else {
                return arg.getCSignature() + " " + argName(i);
            }
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
        throw new RuntimeException("not found: " + path);
    }

    /**
     * Updates the given file by replacing the lines between the start/end markers with the given
     * lines.
     *
     * @return true if the file was modified, false if there were no changes
     */
    private static boolean writeGenerated(Path path, List<String> contents) throws IOException {
        Path capi = CApiCodeGen.resolvePath(path);
        List<String> lines = Files.readAllLines(capi);
        int start = -1;
        int end = -1;
        String prefix = "";
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains(START_CAPI_BUILTINS)) {
                assert start == -1;
                start = i + 1;
                prefix = lines.get(i).substring(0, lines.get(i).indexOf(START_CAPI_BUILTINS));
            } else if (lines.get(i).contains(END_CAPI_BUILTINS)) {
                assert end == -1;
                end = i;
            }
        }
        assert start != -1 && end != -1;
        List<String> result = new ArrayList<>();
        result.addAll(lines.subList(0, start));
        result.add(prefix + "GENERATED CODE - see " + CApiCodeGen.class.getSimpleName());
        result.add(prefix + "This can be re-generated using the 'mx python-capi-forwards' command or");
        result.add(prefix + "by executing the main class " + CApiCodeGen.class.getSimpleName());
        result.add("");
        result.addAll(contents);
        result.addAll(lines.subList(end, lines.size()));
        if (result.equals(lines)) {
            System.out.println("no changes for CAPI_BUILTINS in " + capi);
            return false;
        } else {
            assert result.stream().noneMatch(l -> l.contains("\n")) : "comparison fails with embedded newlines";
            Files.write(capi, result);
            System.out.println("replacing CAPI_BUILTINS in " + capi);
            return true;
        }
    }

    /**
     * Check whether the two given types are similar, based on the C signature (and ignoring a
     * "struct" keyword).
     */
    private static boolean isSimilarType(ArgDescriptor t1, ArgDescriptor t2) {
        return t1.cSignature.equals(t2.cSignature) || t1.cSignature.equals("struct " + t2.cSignature) || ("struct " + t1.cSignature).equals(t2.cSignature);
    }

    private static void compareFunction(String name, ArgDescriptor ret1, ArgDescriptor ret2, ArgDescriptor[] args1, ArgDescriptor[] args2) {
        if (!isSimilarType(ret1, ret2)) {
            System.out.println("duplicate entry for " + name + ", different return " + ret1 + " vs. " + ret2);
        }
        if (args1.length != args2.length) {
            System.out.println("duplicate entry for " + name + ", different arg lengths " + args1.length + " vs. " + args2.length);
        } else {
            for (int i = 0; i < args1.length; i++) {
                if (!isSimilarType(args1[i], args2[i])) {
                    System.out.println("duplicate entry for " + name + ", different arg " + i + ": " + args1[i] + " vs. " + args2[i]);
                }
            }
        }
    }

    private static String argName(int i) {
        return "" + (char) ('a' + i);
    }

    private static Optional<CApiBuiltinDesc> findBuiltin(List<CApiBuiltinDesc> builtins, String name) {
        return builtins.stream().filter(n -> n.name.equals(name)).findFirst();
    }

    /**
     * Generates the functions in capi.c that forward {@link CApiCallPath#Direct} builtins to their
     * associated Java implementations.
     */
    private static boolean generateCApiSource(List<CApiBuiltinDesc> javaBuiltins) throws IOException {
        ArrayList<String> lines = new ArrayList<>();
        for (var entry : javaBuiltins) {
            String name = entry.name;
            CApiBuiltinDesc value = entry;
            if (value.call == Direct || value.call == NotImplemented) {
                lines.add("#undef " + name);
                String line = "PyAPI_FUNC(" + value.returnType.cSignature + ") " + name + "(";
                for (int i = 0; i < value.arguments.length; i++) {
                    line += (i == 0 ? "" : ", ") + CApiBuiltinDesc.getArgSignatureWithName(value.arguments[i], i);
                }
                line += ") {";
                lines.add(line);
                if (value.call == Direct) {
                    line = "    " + (value.returnType == ArgDescriptor.Void ? "" : "return ") + "Graal" + name + "(";
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
        for (CConstants constant : CConstants.VALUES) {
            lines.add("        (int64_t) " + constant.name() + ",");
        }
        lines.add("        0xdead1111 // marker value");
        lines.add("    };");
        lines.add("    return constants;");
        lines.add("}");
        lines.add("PyAPI_FUNC(Py_ssize_t*) PyTruffle_struct_offsets() {");
        lines.add("    static Py_ssize_t offsets[] = {");
        for (CFields field : CFields.VALUES) {
            int delim = field.name().indexOf("__");
            assert delim != -1;
            String struct = field.name().substring(0, delim);
            String name = field.name().substring(delim + 2);
            name = name.replace("__", "."); // to allow inlined structs
            lines.add("        offsetof(" + struct + ", " + name + "),");
        }
        lines.add("        0xdead2222 // marker value");
        lines.add("    };");
        lines.add("    return offsets;");
        lines.add("}");
        lines.add("PyAPI_FUNC(Py_ssize_t*) PyTruffle_struct_sizes() {");
        lines.add("    static Py_ssize_t sizes[] = {");
        for (CStructs struct : CStructs.VALUES) {
            lines.add("        sizeof(" + struct.name().replace("__", " ") + "),");
        }
        lines.add("        0xdead3333 // marker value");
        lines.add("    };");
        lines.add("    return sizes;");
        lines.add("}");

        return writeGenerated(Path.of("com.oracle.graal.python.cext", "src", "capi.c"), lines);
    }

    /**
     * Generates the builtin specification in capi.h, which includes only the builtins implemented
     * in Java code. Additionally, it generates helpers for all "Py_get_" and "Py_set_" builtins.
     */
    private static boolean generateCApiHeader(List<CApiBuiltinDesc> javaBuiltins) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("#define CAPI_BUILTINS \\");
        int id = 0;
        for (var entry : javaBuiltins) {
            assert (id++) == entry.id;
            String line = "    BUILTIN(" + entry.name + ", " + entry.returnType.cSignature;
            for (var arg : entry.arguments) {
                line += ", " + arg.cSignature;
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
                    String type = entry.arguments[0].name().replace("Wrapper", "");
                    StringBuilder macro = new StringBuilder();
                    assert name.charAt(7 + type.length()) == '_' : name;
                    String field = name.substring(7 + type.length() + 1); // after "_"
                    macro.append("#define " + name.substring(7) + "(OBJ) ( points_to_py_handle_space(OBJ) ? Graal" + name + "((" + type + "*) (OBJ)) : ((" + type + "*) (OBJ))->" + field + " )");
                    lines.add(macro.toString());
                } else if (name.startsWith("Py_set_")) {
                    assert entry.arguments.length == 2 : name;
                    String type = entry.arguments[0].name().replace("Wrapper", "");
                    StringBuilder macro = new StringBuilder();
                    assert name.charAt(7 + type.length()) == '_' : name;
                    String field = name.substring(7 + type.length() + 1); // after "_"
                    macro.append("#define set_" + name.substring(7) + "(OBJ, VALUE) { if (points_to_py_handle_space(OBJ)) Graal" + name + "((" + type + "*) (OBJ), (VALUE)); else  ((" + type +
                                    "*) (OBJ))->" + field + " = (VALUE); }");
                    lines.add(macro.toString());
                }
            }
        }

        /**
         * Adding constants for methods flags checks in
         * {@link NativeCAPISymbol.FUN_GET_METHODS_FLAGS}
         */
        lines.add("");
        lines.addAll(MethodsFlags.CAPI_METHODS_FLAGS_DEFINES);

        return writeGenerated(Path.of("com.oracle.graal.python.cext", "src", "capi.h"), lines);
    }

    /**
     * Generates the contents of the {@link PythonCextBuiltinRegistry} class: the list of builtins,
     * the {@link CApiBuiltinNode} factory function, and the slot query function.
     */
    private static boolean generateBuiltinRegistry(List<CApiBuiltinDesc> javaBuiltins) throws IOException {
        ArrayList<String> lines = new ArrayList<>();

        lines.add("    // @formatter:off");
        lines.add("    // Checkstyle: stop");
        for (var builtin : javaBuiltins) {
            String argString = Arrays.stream(builtin.arguments).map(b -> "ArgDescriptor." + b).collect(Collectors.joining(", "));
            lines.add("    public static final CApiBuiltinExecutable " + builtin.name + " = new CApiBuiltinExecutable(\"" + builtin.name + "\", CApiCallPath." + builtin.call + ", ArgDescriptor." +
                            builtin.returnType + ", new ArgDescriptor[]{" + argString + "}, " + builtin.id + ");");
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
        lines.add("");
        lines.add("    // @formatter:on");

        return writeGenerated(Path.of("com.oracle.graal.python", "src", "com", "oracle", "graal", "python", "builtins", "modules", "cext", "PythonCextBuiltinRegistry.java"), lines);
    }

    private static void checkUnimplementedAPI(Path path, List<CApiBuiltinDesc> additionalBuiltins) {
        List<String> lines;
        try {
            lines = Files.readAllLines(path);
        } catch (IOException e) {
            System.out.println("    Error while reading " + path + ": " + e.getMessage());
            return;
        }
        boolean msg = false;
        for (CApiBuiltinDesc builtin : additionalBuiltins) {
            if (builtin.call == NotImplemented) {
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    int offset = line.indexOf(builtin.name);
                    // avoid recognizing, e.g. "_PyObject_CallMethodId" in
                    // "_PyObject_CallMethodIdNoArgs"
                    if (offset > 0 && Character.isUnicodeIdentifierPart(line.charAt(offset - 1))) {
                        continue;
                    }
                    if (offset + builtin.name.length() < line.length() && Character.isUnicodeIdentifierPart(line.charAt(offset + builtin.name.length()))) {
                        continue;
                    }
                    if (line.contains(builtin.name)) {
                        if (!msg) {
                            msg = true;
                            System.out.println("Checking " + path);
                        }
                        System.out.println("    " + builtin.name + " used in " + path + " line " + (i + 1) + ": " + line);
                    }
                }
            }
        }
    }

    /**
     * Entry point for the "mx python-capi-forwards" command.
     */
    public static void main(String[] args) throws IOException {
        List<CApiBuiltinDesc> javaBuiltins = CApiFunction.getJavaBuiltinDefinitions();
        List<CApiBuiltinDesc> additionalBuiltins = CApiFunction.getOtherBuiltinDefinitions();

        /*
         * Calling with arguments "check <path>" will recursively check all files in the path for
         * unimplemented C API functions.
         */
        if (args.length == 2 && "check".equals(args[0])) {
            System.out.println("Checking usages of unimplemented API:");
            String path = args[1];
            try (Stream<Path> stream = Files.walk(Paths.get(path))) {
                stream.filter(Files::isRegularFile).forEach(p -> checkUnimplementedAPI(p, additionalBuiltins));
            }
            return;
        }

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

        boolean changed = false;
        changed |= generateCApiSource(allBuiltins);
        changed |= generateCApiHeader(javaBuiltins);
        changed |= generateBuiltinRegistry(javaBuiltins);
        changed |= checkImports(allBuiltins);
        if (changed) {
            System.exit(-1);
        }
    }

    /**
     * Checks whether the "not implemented" state of builtins matches whether they exist in the capi
     * library: {@link CApiCallPath#NotImplemented} and {@link CApiCallPath#Ignored} builtins cannot
     * have an implementation, and all others need to be present.
     */
    public static boolean assertBuiltins(Object capiLibrary) {
        List<CApiBuiltinDesc> builtins = new ArrayList<>();
        builtins.addAll(CApiFunction.getOtherBuiltinDefinitions());
        builtins.addAll(CApiFunction.getJavaBuiltinDefinitions());

        TreeSet<String> messages = new TreeSet<>();
        for (CApiBuiltinDesc function : builtins) {
            boolean hasMember = InteropLibrary.getUncached().isMemberReadable(capiLibrary, function.name);
            if (hasMember) {
                try {
                    InteropLibrary.getUncached().readMember(capiLibrary, function.name);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                } catch (UnknownIdentifierException e) {
                    // NFI lied to us!
                    hasMember = false;
                }
            }
            if (hasMember) {
                if (function.call == CImpl || function.call == CApiCallPath.Direct || function.call == NotImplemented) {
                    // ok
                } else {
                    messages.add("unexpected C impl: " + function.name);
                }
            } else {
                if (function.call == Ignored) {
                    // ok
                } else {
                    messages.add("missing implementation: " + function.name);
                }
            }
        }

        messages.forEach(System.out::println);
        return messages.isEmpty();
    }

    /**
     * These are functions that are introduced by GraalPy, mostly auxiliary functions that we added
     * to avoid direct fields accesses:
     */
    private static final String[] ADDITIONAL = new String[]{"PyCMethod_GetClass", "PyDescrObject_GetName", "PyDescrObject_GetType", "PyInterpreterState_GetIDFromThreadState",
                    "PyMethodDescrObject_GetMethod", "PyObject_GetDoc", "PyObject_SetDoc", "PySlice_Start", "PySlice_Step", "PySlice_Stop", "_PyFrame_SetLineNumber", "_PyMemoryView_GetBuffer",
                    "_PySequence_Fast_ITEMS", "_PySequence_ITEM", "_PyUnicodeObject_DATA", "_PyUnicode_KIND", "_PyCFunction_GetModule", "_PyCFunction_GetMethodDef", "PyCode_GetName",
                    "PyCode_GetFileName", "_PyList_SET_ITEM", "_PyArray_Resize", "_PyArray_Data",
                    "_PyErr_Occurred", "_PyNamespace_New", "_Py_GetErrorHandler"};

    /**
     * Check the list of implemented and unimplemented builtins against the list of CPython exported
     * symbols, to determine if builtins are missing. If a builtin is missing, this function
     * suggests the appropriate {@link CApiBuiltin} specification.
     */
    private static boolean checkImports(List<CApiBuiltinDesc> builtins) throws IOException {
        boolean result = false;
        List<String> lines = Files.readAllLines(resolvePath(Path.of("com.oracle.graal.python.cext", "CAPIFunctions.txt")));

        TreeSet<String> newBuiltins = new TreeSet<>();
        TreeSet<String> names = new TreeSet<>();
        builtins.forEach(n -> names.add(n.name));

        for (String line : lines) {
            String[] s = line.split(";");
            String name = s[0].trim();
            names.remove(name);
            ArgDescriptor ret = ArgDescriptor.resolve(s[1].trim());
            String[] argSplit = s[2].isBlank() || "void".equals(s[2]) ? new String[0] : s[2].trim().split("\\|");
            ArgDescriptor[] args = Arrays.stream(argSplit).map(ArgDescriptor::resolve).toArray(ArgDescriptor[]::new);

            Optional<CApiBuiltinDesc> existing = findBuiltin(builtins, name);
            if (existing.isPresent()) {
                compareFunction(name, existing.get().returnType, ret, existing.get().arguments, args);
            } else {
                String argString = Arrays.stream(args).map(t -> String.valueOf(t)).collect(Collectors.joining(", "));
                newBuiltins.add("    @CApiBuiltin(name = \"" + name + "\", ret = " + ret + ", args = {" + argString + "}, call = NotImplemented)");
            }
        }
        if (!newBuiltins.isEmpty()) {
            System.out.println("missing builtins (defined in CPython, but not in GraalPy):");
            newBuiltins.stream().forEach(System.out::println);
            result = true;
        }

        names.removeIf(n -> n.startsWith("Py_get_"));
        names.removeIf(n -> n.startsWith("Py_set_"));
        names.removeIf(n -> n.startsWith("PyTruffle"));
        names.removeIf(n -> n.startsWith("_PyTruffle"));
        names.removeAll(Arrays.asList(ADDITIONAL));
        if (!names.isEmpty()) {
            System.out.println("extra builtins (defined in GraalPy, but not in CPython - some of these are necessary for internal modules like 'math'):");
            System.out.println("    " + names.stream().collect(Collectors.joining(", ")));
            result = true;
        }
        return result;
    }
}
