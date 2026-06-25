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

import java.util.List;

import com.oracle.graal.python.annotations.NativeSimpleType;

final class NativeDowncallMethodHandleGenerator {
    private NativeDowncallMethodHandleGenerator() {
    }

    static String argName(int i) {
        if (i >= 26) {
            throw new IllegalArgumentException("Generated downcall stubs support at most 26 synthetic argument names (a-z), got index " + i);
        }
        return "" + (char) ('a' + i);
    }

    static String methodHandleVarName(String signatureName) {
        return "NATIVE_METHOD_HANDLE_" + signatureName;
    }

    private static String toNativeSimpleTypeLiteral(String javaType) {
        return switch (javaType) {
            case "void" -> "NativeSimpleType.VOID";
            case "byte" -> "NativeSimpleType.SINT8";
            case "short" -> "NativeSimpleType.SINT16";
            case "int" -> "NativeSimpleType.SINT32";
            case "long" -> "NativeSimpleType.SINT64";
            case "float" -> "NativeSimpleType.FLOAT";
            case "double" -> "NativeSimpleType.DOUBLE";
            default -> throw new IllegalArgumentException("Unexpected Java type: " + javaType);
        };
    }

    private static String toNativeSimpleTypeLiteral(NativeSimpleType nativeType) {
        return switch (nativeType) {
            case VOID -> "NativeSimpleType.VOID";
            case SINT8 -> "NativeSimpleType.SINT8";
            case SINT16 -> "NativeSimpleType.SINT16";
            case SINT32 -> "NativeSimpleType.SINT32";
            case SINT64 -> "NativeSimpleType.SINT64";
            case FLOAT -> "NativeSimpleType.FLOAT";
            case DOUBLE -> "NativeSimpleType.DOUBLE";
            case POINTER -> "NativeSimpleType.POINTER";
        };
    }

    static void emitMethodHandleField(List<String> lines, String fieldName, String returnType, List<String> argTypes) {
        StringBuilder createHandle = new StringBuilder("NativeAccessSupport.createDowncallHandle(false, false, ");
        createHandle.append(toNativeSimpleTypeLiteral(returnType));
        for (String argType : argTypes) {
            createHandle.append(", ").append(toNativeSimpleTypeLiteral(argType));
        }
        createHandle.append(")");
        lines.add("    private static final MethodHandle " + fieldName + " = " + createHandle + ";");
    }

    static void emitMethodHandleField(List<String> lines, String fieldName, NativeSimpleType returnType, List<NativeSimpleType> argTypes, boolean critical, boolean captureCallState) {
        StringBuilder createHandle = new StringBuilder("NativeAccessSupport.createDowncallHandle(");
        createHandle.append(critical).append(", ").append(captureCallState).append(", ");
        createHandle.append(toNativeSimpleTypeLiteral(returnType));
        for (NativeSimpleType argType : argTypes) {
            createHandle.append(", ").append(toNativeSimpleTypeLiteral(argType));
        }
        createHandle.append(")");
        lines.add("    private static final MethodHandle " + fieldName + " = " + createHandle + ";");
    }
}
