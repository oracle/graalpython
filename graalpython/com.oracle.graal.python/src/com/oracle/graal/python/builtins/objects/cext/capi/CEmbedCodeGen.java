package com.oracle.graal.python.builtins.objects.cext.capi;

import com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor;
import com.oracle.graal.python.builtins.objects.cext.structs.CConstants;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructs;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Direct;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.NotImplemented;

public final class CEmbedCodeGen {

    /**
     * Generates the c-python compatible header file cpy_embed.h
     * to allow embedding GraalPython in other projects that assume
     * the CPython C API.
     */
     static boolean generateCPyEmbedHeader(List<CApiCodeGen.CApiBuiltinDesc> javaBuiltins) throws IOException {
        System.out.println("Generating cpy_embed.h");
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

        return CApiCodeGen.writeGenerated(Path.of("com.oracle.graal.python.c_embed", "include", "graalpython-embed.h"), lines);
    }

    /**
     * Generates the c-python compatible source file cpy_embed.c
     * to allow embedding GraalPython in other projects that assume
     * the CPython C API.
     */
    static boolean generateCPyEmbedSource(List<CApiCodeGen.CApiBuiltinDesc> javaBuiltins) throws IOException {
        System.out.println("Generating graalpython-embed.c");
        List<String> lines = new ArrayList<>();
        for (var entry : javaBuiltins) {
            String name = entry.name;
            CApiCodeGen.CApiBuiltinDesc value = entry;
            if (value.call == NotImplemented) {
                lines.add("#undef " + name);
                String line = "PyAPI_FUNC(" + value.returnType.cSignature + ") " + name + "(";
                for (int i = 0; i < value.arguments.length; i++) {
                    line += (i == 0 ? "" : ", ") + CApiCodeGen.CApiBuiltinDesc.getArgSignatureWithName(value.arguments[i], i);
                }
                line += ") {";
                lines.add(line);
                System.out.println("Generating " + name + " with call type " + value.call);
                if (value.call == Direct) { // DISABLED
                    line = "    " + (value.returnType == ArgDescriptor.Void ? "" : "return ") + "Graal" + name + "(";
                    for (int i = 0; i < value.arguments.length; i++) {
                        line += (i == 0 ? "" : ", ");
                        line += argName(i);
                    }
                    line += ");";
                } else {
                    line = "    FUNC_NOT_IMPLEMENTED";
                }
                //line = "    FUNC_NOT_IMPLEMENTED";
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

        return CApiCodeGen.writeGenerated(Path.of("com.oracle.graal.python.c_embed", "src", "graalpython-embed.c"), lines);
    }

    private static String argName(int i) {
        return "" + (char) ('a' + i);
    }

    public static void main(String[] args) throws IOException {
        List<CApiCodeGen.CApiBuiltinDesc> builtins =  CApiFunction.getJavaBuiltinDefinitions();
        List<CApiCodeGen.CApiBuiltinDesc> javaBuiltins = new ArrayList<>();
        for (CApiCodeGen.CApiBuiltinDesc builtin : builtins) {
            if (builtin.id != -1) {
                javaBuiltins.add(builtin);
            }
        }
        generateCPyEmbedHeader(javaBuiltins);
        generateCPyEmbedSource(javaBuiltins);
    }
}
