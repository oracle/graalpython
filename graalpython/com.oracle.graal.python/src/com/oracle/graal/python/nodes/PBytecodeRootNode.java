/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.statement.AbstractImportNode;
import com.oracle.graal.python.runtime.ExecutionContext.CalleeContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;

public final class PBytecodeRootNode extends PRootNode {

    private final int stacksize;
    private final Signature signature;

    @CompilationFinal(dimensions = 1) private final byte[] bytecode;
    @CompilationFinal(dimensions = 1) private final Object[] consts;
    @CompilationFinal(dimensions = 1) private final String[] names;
    @CompilationFinal(dimensions = 1) private final String[] varnames;
    @CompilationFinal(dimensions = 1) private final Object[] freevars;
    @CompilationFinal(dimensions = 1) private final Object[] cellvars;

    @Child private CalleeContext calleeContext = CalleeContext.create();

    public PBytecodeRootNode(TruffleLanguage<?> language, Signature sign, byte[] bc,
                    Object[] consts, String[] names, String[] varnames, Object[] freevars, Object[] cellvars,
                    int stacksize) {
        super(language);
        this.signature = sign;
        this.bytecode = bc;
        this.consts = consts;
        this.names = names;
        this.varnames = varnames;
        this.freevars = freevars;
        this.cellvars = cellvars;
        this.stacksize = stacksize;
    }

    public PBytecodeRootNode(TruffleLanguage<?> language, FrameDescriptor fd, Signature sign, byte[] bc,
                    Object[] consts, String[] names, String[] varnames, Object[] freevars, Object[] cellvars,
                    int stacksize) {
        super(language, fd);
        this.signature = sign;
        this.bytecode = bc;
        this.consts = consts;
        this.names = names;
        this.varnames = varnames;
        this.freevars = freevars;
        this.cellvars = cellvars;
        this.stacksize = stacksize;
    }

    @Override
    public Signature getSignature() {
        return signature;
    }

    @Override
    public boolean isPythonInternal() {
        return false;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        PythonContext context = lookupContextReference(PythonLanguage.class).get();
        calleeContext.enter(frame);
        try {
            int sp = -1;
            Object[] stack = new Object[stacksize];
            Object[] localNames = new Object[names.length];
            for (int i = 0; i < bytecode.length; i += 2) {
                int bc = bytecode[i];
                if (bc < 0) {
                    bc = 256 + bc;
                }
                switch (bc) {
                case POP_TOP:
                    stack[sp--] = null;
                    break;
                case LOAD_CONST:
                    stack[++sp] = consts[bytecode[i + 1]];
                    break;
                case IMPORT_NAME:
                    {
                        String name = names[bytecode[i + 1]];
                        Object fromlist = pop(sp--, stack);
                        Object level = top(sp, stack);
                        Object result = AbstractImportNode.importModule(name, fromlist, level);
                        stack[sp] = result;
                    }
                    break;
                case STORE_NAME:
                    localNames[bytecode[i + 1]] = pop(sp--, stack);
                    break;
                case LOAD_NAME:
                    {
                        int nameIdx = bytecode[i + 1];
                        Object value = localNames[nameIdx];
                        if (value == null) {
                            Object globals = PArguments.getGlobals(frame);
                            String name = names[nameIdx];
                            if (globals instanceof PythonModule) {
                                value = PyObjectLookupAttr.getUncached().execute(frame, globals, name);
                            } else {
                                // TODO: PyObjectGetItem
                                value = PNone.NO_VALUE;
                            }
                            if (value == PNone.NO_VALUE) {
                                value = PyObjectLookupAttr.getUncached().execute(frame, context.getBuiltins(), name);
                            }
                            if (value == PNone.NO_VALUE) {
                                PRaiseNode.raiseUncached(this, PythonBuiltinClassType.NameError, name);
                            }
                        }
                        stack[++sp] = value;
                    }
                    break;
                case LOAD_ATTR:
                    {
                        String name = names[bytecode[i + 1]];
                        Object owner = top(sp, stack);
                        Object value = PyObjectGetAttr.getUncached().execute(frame, owner, name);
                        stack[sp] = value;
                    }
                    break;
                case CALL_FUNCTION:
                    {
                        int oparg = bytecode[i + 1];
                        Object func = stack[sp - oparg];
                        Object[] arguments = new Object[oparg];
                        for (int j = 0; j < oparg; j++) {
                            arguments[j] = pop(sp--, stack);
                        }
                        Object result = CallNode.getUncached().execute(func, arguments);
                        stack[sp] = result;
                    }
                    break;
                case RETURN_VALUE:
                    return stack[sp];
                default:
                    throw new RuntimeException("not implemented bytecode");
                }
            }
            throw new RuntimeException("no return from bytecode");
        } finally {
            calleeContext.exit(frame, this);
        }
    }

    private static final Object top(int sp, Object[] stack) {
        return stack[sp];
    }

    private static final Object pop(int sp, Object[] stack) {
        Object result = stack[sp];
        stack[sp] = null;
        return result;
    }

    private static final char POP_TOP =                   1;
    private static final char ROT_TWO =                   2;
    private static final char ROT_THREE =                 3;
    private static final char DUP_TOP =                   4;
    private static final char DUP_TOP_TWO =               5;
    private static final char ROT_FOUR =                  6;
    private static final char NOP =                       9;
    private static final char UNARY_POSITIVE =           10;
    private static final char UNARY_NEGATIVE =           11;
    private static final char UNARY_NOT =                12;
    private static final char UNARY_INVERT =             15;
    private static final char BINARY_MATRIX_MULTIPLY =   16;
    private static final char INPLACE_MATRIX_MULTIPLY =  17;
    private static final char BINARY_POWER =             19;
    private static final char BINARY_MULTIPLY =          20;
    private static final char BINARY_MODULO =            22;
    private static final char BINARY_ADD =               23;
    private static final char BINARY_SUBTRACT =          24;
    private static final char BINARY_SUBSCR =            25;
    private static final char BINARY_FLOOR_DIVIDE =      26;
    private static final char BINARY_TRUE_DIVIDE =       27;
    private static final char INPLACE_FLOOR_DIVIDE =     28;
    private static final char INPLACE_TRUE_DIVIDE =      29;
    private static final char GET_AITER =                50;
    private static final char GET_ANEXT =                51;
    private static final char BEFORE_ASYNC_WITH =        52;
    private static final char BEGIN_FINALLY =            53;
    private static final char END_ASYNC_FOR =            54;
    private static final char INPLACE_ADD =              55;
    private static final char INPLACE_SUBTRACT =         56;
    private static final char INPLACE_MULTIPLY =         57;
    private static final char INPLACE_MODULO =           59;
    private static final char STORE_SUBSCR =             60;
    private static final char DELETE_SUBSCR =            61;
    private static final char BINARY_LSHIFT =            62;
    private static final char BINARY_RSHIFT =            63;
    private static final char BINARY_AND =               64;
    private static final char BINARY_XOR =               65;
    private static final char BINARY_OR =                66;
    private static final char INPLACE_POWER =            67;
    private static final char GET_ITER =                 68;
    private static final char GET_YIELD_FROM_ITER =      69;
    private static final char PRINT_EXPR =               70;
    private static final char LOAD_BUILD_CLASS =         71;
    private static final char YIELD_FROM =               72;
    private static final char GET_AWAITABLE =            73;
    private static final char INPLACE_LSHIFT =           75;
    private static final char INPLACE_RSHIFT =           76;
    private static final char INPLACE_AND =              77;
    private static final char INPLACE_XOR =              78;
    private static final char INPLACE_OR =               79;
    private static final char WITH_CLEANUP_START =       81;
    private static final char WITH_CLEANUP_FINISH =      82;
    private static final char RETURN_VALUE =             83;
    private static final char IMPORT_STAR =              84;
    private static final char SETUP_ANNOTATIONS =        85;
    private static final char YIELD_VALUE =              86;
    private static final char POP_BLOCK =                87;
    private static final char END_FINALLY =              88;
    private static final char POP_EXCEPT =               89;
    private static final char HAVE_ARGUMENT =            90;
    private static final char STORE_NAME =               90;
    private static final char DELETE_NAME =              91;
    private static final char UNPACK_SEQUENCE =          92;
    private static final char FOR_ITER =                 93;
    private static final char UNPACK_EX =                94;
    private static final char STORE_ATTR =               95;
    private static final char DELETE_ATTR =              96;
    private static final char STORE_GLOBAL =             97;
    private static final char DELETE_GLOBAL =            98;
    private static final char LOAD_CONST =              100;
    private static final char LOAD_NAME =               101;
    private static final char BUILD_TUPLE =             102;
    private static final char BUILD_LIST =              103;
    private static final char BUILD_SET =               104;
    private static final char BUILD_MAP =               105;
    private static final char LOAD_ATTR =               106;
    private static final char COMPARE_OP =              107;
    private static final char IMPORT_NAME =             108;
    private static final char IMPORT_FROM =             109;
    private static final char JUMP_FORWARD =            110;
    private static final char JUMP_IF_FALSE_OR_POP =    111;
    private static final char JUMP_IF_TRUE_OR_POP =     112;
    private static final char JUMP_ABSOLUTE =           113;
    private static final char POP_JUMP_IF_FALSE =       114;
    private static final char POP_JUMP_IF_TRUE =        115;
    private static final char LOAD_GLOBAL =             116;
    private static final char SETUP_FINALLY =           122;
    private static final char LOAD_FAST =               124;
    private static final char STORE_FAST =              125;
    private static final char DELETE_FAST =             126;
    private static final char RAISE_VARARGS =           130;
    private static final char CALL_FUNCTION =           131;
    private static final char MAKE_FUNCTION =           132;
    private static final char BUILD_SLICE =             133;
    private static final char LOAD_CLOSURE =            135;
    private static final char LOAD_DEREF =              136;
    private static final char STORE_DEREF =             137;
    private static final char DELETE_DEREF =            138;
    private static final char CALL_FUNCTION_KW =        141;
    private static final char CALL_FUNCTION_EX =        142;
    private static final char SETUP_WITH =              143;
    private static final char EXTENDED_ARG =            144;
    private static final char LIST_APPEND =             145;
    private static final char SET_ADD =                 146;
    private static final char MAP_ADD =                 147;
    private static final char LOAD_CLASSDEREF =         148;
    private static final char BUILD_LIST_UNPACK =       149;
    private static final char BUILD_MAP_UNPACK =        150;
    private static final char BUILD_MAP_UNPACK_WITH_CALL = 151;
    private static final char BUILD_TUPLE_UNPACK =      152;
    private static final char BUILD_SET_UNPACK =        153;
    private static final char SETUP_ASYNC_WITH =        154;
    private static final char FORMAT_VALUE =            155;
    private static final char BUILD_CONST_KEY_MAP =     156;
    private static final char BUILD_STRING =            157;
    private static final char BUILD_TUPLE_UNPACK_WITH_CALL = 158;
    private static final char LOAD_METHOD =             160;
    private static final char CALL_METHOD =             161;
    private static final char CALL_FINALLY =            162;
    private static final char POP_FINALLY =             163;
}
