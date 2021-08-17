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

import static com.oracle.graal.python.nodes.SpecialAttributeNames.__ANNOTATIONS__;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.KeywordsStorage;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgsNodeGen;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.AddNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.BitAndNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.BitOrNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.BitXorNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.FloorDivNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.LShiftNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.MatMulNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.ModNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.MulNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.PowNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.RShiftNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.SubNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.TrueDivNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmeticFactory.AddNodeGen;
import com.oracle.graal.python.nodes.expression.BinaryArithmeticFactory.BitAndNodeGen;
import com.oracle.graal.python.nodes.expression.BinaryArithmeticFactory.BitOrNodeGen;
import com.oracle.graal.python.nodes.expression.BinaryArithmeticFactory.BitXorNodeGen;
import com.oracle.graal.python.nodes.expression.BinaryArithmeticFactory.FloorDivNodeGen;
import com.oracle.graal.python.nodes.expression.BinaryArithmeticFactory.LShiftNodeGen;
import com.oracle.graal.python.nodes.expression.BinaryArithmeticFactory.MatMulNodeGen;
import com.oracle.graal.python.nodes.expression.BinaryArithmeticFactory.ModNodeGen;
import com.oracle.graal.python.nodes.expression.BinaryArithmeticFactory.MulNodeGen;
import com.oracle.graal.python.nodes.expression.BinaryArithmeticFactory.PowNodeGen;
import com.oracle.graal.python.nodes.expression.BinaryArithmeticFactory.RShiftNodeGen;
import com.oracle.graal.python.nodes.expression.BinaryArithmeticFactory.SubNodeGen;
import com.oracle.graal.python.nodes.expression.BinaryArithmeticFactory.TrueDivNodeGen;
import com.oracle.graal.python.nodes.expression.UnaryArithmetic.InvertNode;
import com.oracle.graal.python.nodes.expression.UnaryArithmetic.NegNode;
import com.oracle.graal.python.nodes.expression.UnaryArithmetic.PosNode;
import com.oracle.graal.python.nodes.expression.UnaryArithmeticFactory.InvertNodeGen;
import com.oracle.graal.python.nodes.expression.UnaryArithmeticFactory.NegNodeGen;
import com.oracle.graal.python.nodes.expression.UnaryArithmeticFactory.PosNodeGen;
import com.oracle.graal.python.nodes.frame.DeleteGlobalNode;
import com.oracle.graal.python.nodes.frame.ReadGlobalOrBuiltinNode;
import com.oracle.graal.python.nodes.frame.WriteGlobalNode;
import com.oracle.graal.python.nodes.statement.AbstractImportNode.ImportName;
import com.oracle.graal.python.nodes.statement.RaiseNode;
import com.oracle.graal.python.nodes.subscript.GetItemNode;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.ExecutionContext.CalleeContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.graal.python.util.Supplier;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.HostCompilerDirectives.BytecodeInterpreterSwitch;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;

public final class PBytecodeRootNode extends PRootNode {

    private final int stacksize;
    private final Signature signature;

    @CompilationFinal(dimensions = 1) private final byte[] bytecode;
    @CompilationFinal(dimensions = 1) private final Object[] consts;
    @CompilationFinal(dimensions = 1) private final String[] names;
    @CompilationFinal(dimensions = 1) private final String[] varnames;
    @CompilationFinal(dimensions = 1) private final String[] freevars;
    @CompilationFinal(dimensions = 1) private final String[] cellvars;

    @Children private final Node[] adoptedNodes;
    @Child private CalleeContext calleeContext = CalleeContext.create();
    @Child private PythonObjectFactory factory = PythonObjectFactory.create();

    public PBytecodeRootNode(TruffleLanguage<?> language, Signature sign, byte[] bc,
                    Object[] consts, String[] names, String[] varnames, String[] freevars, String[] cellvars,
                    int stacksize) {
        super(language);
        this.signature = sign;
        this.bytecode = bc;
        this.adoptedNodes = new Node[bc.length];
        this.consts = consts;
        this.names = names;
        this.varnames = varnames;
        this.freevars = freevars;
        this.cellvars = cellvars;
        this.stacksize = stacksize;
    }

    public PBytecodeRootNode(TruffleLanguage<?> language, FrameDescriptor fd, Signature sign, byte[] bc,
                    Object[] consts, String[] names, String[] varnames, String[] freevars, String[] cellvars,
                    int stacksize) {
        super(language, fd);
        this.signature = sign;
        this.bytecode = bc;
        this.adoptedNodes = new Node[bc.length];
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
            return executeLoop(frame, context);
        } finally {
            calleeContext.exit(frame, this);
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends Node> T insertChildNode(Supplier<T> nodeSupplier, int bytecodeIndex) {
        T node = (T) adoptedNodes[bytecodeIndex];
        if (node == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            node = nodeSupplier.get();
            adoptedNodes[bytecodeIndex] = insert(node);
        }
        return node;
    }

    @BytecodeInterpreterSwitch
    private Object executeLoop(VirtualFrame frame, PythonContext context) {
        int stackTop = -1;
        Object globals = PArguments.getGlobals(frame);
        PythonModule builtins = context.getBuiltins();

        Object locals = PArguments.getCustomLocals(frame);

        // CPython has an array of object called "localsplus" with everything. We use separate
        // arrays
        Object[] stack = new Object[stacksize];
        Object[] args = frame.getArguments();
        Object[] fastlocals = new Object[varnames.length];
        System.arraycopy(args, PArguments.USER_ARGUMENTS_OFFSET, fastlocals, 0, PArguments.getUserArgumentLength(args));
        int varargsIdx = signature.getVarargsIdx();
        if (varargsIdx >= 0) {
            fastlocals[varargsIdx] = factory.createList(PArguments.getVariableArguments(args));
        }
        if (signature.takesVarKeywordArgs()) {
            int idx = signature.getParameterIds().length + signature.getKeywordNames().length;
            if (varargsIdx >= 0) {
                idx += 1;
            }
            fastlocals[idx] = factory.createDict(PArguments.getKeywordArguments(args));
        }
        Object[] celllocals = new Object[cellvars.length];
        Object[] freelocals = new Object[freevars.length];

        for (int i = 0; i < bytecode.length; i += 2) {
            int bc = bytecode[i];
            if (bc < 0) {
                bc = 256 + bc;
            }
            int oparg = bytecode[i + 1];
            if (oparg < 0) {
                oparg = 256 + oparg;
            }

            // handle extended arg directly
            if (bc == EXTENDED_ARG) {
                i += 2;
                bc = bytecode[i];
                oparg = bytecode[i + 1] | (oparg << 8);
            }

            switch (bc) {
                case NOP:
                    break;
                case LOAD_FAST:
                    {
                        Object value = fastlocals[oparg];
                        if (value == null) {
                            throw PRaiseNode.raiseUncached(this, PythonBuiltinClassType.UnboundLocalError, ErrorMessages.LOCAL_VAR_REFERENCED_BEFORE_ASSIGMENT, varnames[oparg]);
                        }
                        stack[++stackTop] = value;
                    }
                    break;
                case LOAD_CONST:
                    stack[++stackTop] = consts[oparg];
                    break;
                case STORE_FAST:
                    fastlocals[oparg] = pop(stackTop--, stack);
                    break;
                case POP_TOP:
                    stack[stackTop--] = null;
                    break;
                case ROT_TWO:
                    {
                        Object top = stack[stackTop];
                        stack[stackTop] = stack[stackTop - 1];
                        stack[stackTop - 1] = top;
                        break;
                    }
                case ROT_THREE:
                    {
                        Object top = stack[stackTop];
                        stack[stackTop] = stack[stackTop - 1];
                        stack[stackTop - 1] = stack[stackTop - 2];
                        stack[stackTop - 2] = top;
                        break;
                    }
                case ROT_FOUR:
                    {
                        Object top = stack[stackTop];
                        stack[stackTop] = stack[stackTop - 1];
                        stack[stackTop - 1] = stack[stackTop - 2];
                        stack[stackTop - 2] = stack[stackTop - 3];
                        stack[stackTop - 3] = top;
                        break;
                    }
                case DUP_TOP:
                    stack[stackTop + 1] = stack[stackTop];
                    stackTop++;
                    break;
                case DUP_TOP_TWO:
                    stack[stackTop + 2] = stack[stackTop];
                    stack[stackTop + 1] = stack[stackTop - 1];
                    stackTop += 2;
                    break;
                case UNARY_POSITIVE:
                    {
                        PosNode posNode = insertChildNode(() -> PosNodeGen.create(null), i);
                        stack[stackTop] = posNode.execute(frame, stack[stackTop]);
                    }
                    break;
                case UNARY_NEGATIVE:
                    {
                        NegNode negNode = insertChildNode(() -> NegNodeGen.create(null), i);
                        stack[stackTop] = negNode.execute(frame, stack[stackTop]);
                    }
                    break;
                case UNARY_NOT:
                    stack[stackTop] = PyObjectIsTrueNode.getUncached().execute(frame, stack[stackTop]);
                    break;
                case UNARY_INVERT:
                    {
                        InvertNode invertNode = insertChildNode(() -> InvertNodeGen.create(null), i);
                        stack[stackTop] = invertNode.execute(frame, stack[stackTop]);
                    }
                    break;
                case BINARY_POWER:
                    {
                        PowNode powNode = insertChildNode(() -> PowNodeGen.create(null, null), i);
                        Object right = pop(stackTop--, stack);
                        stack[stackTop] = powNode.executeObject(frame, stack[stackTop], right);
                    }
                    break;
                case BINARY_MULTIPLY:
                    {
                        MulNode mulNode = insertChildNode(() -> MulNodeGen.create(null, null), i);
                        Object right = pop(stackTop--, stack);
                        stack[stackTop] = mulNode.executeObject(frame, stack[stackTop], right);
                    }
                    break;
                case BINARY_MATRIX_MULTIPLY:
                    {
                        MatMulNode matMulNode = insertChildNode(() -> MatMulNodeGen.create(null, null), i);
                        Object right = pop(stackTop--, stack);
                        stack[stackTop] = matMulNode.executeObject(frame, stack[stackTop], right);
                    }
                    break;
                case BINARY_TRUE_DIVIDE:
                    {
                        TrueDivNode trueDivNode = insertChildNode(() -> TrueDivNodeGen.create(null, null), i);
                        Object right = pop(stackTop--, stack);
                        stack[stackTop] = trueDivNode.executeObject(frame, stack[stackTop], right);
                    }
                    break;
                case BINARY_FLOOR_DIVIDE:
                    {
                        FloorDivNode floorDivNode = insertChildNode(() -> FloorDivNodeGen.create(null, null), i);
                        Object right = pop(stackTop--, stack);
                        stack[stackTop] = floorDivNode.executeObject(frame, stack[stackTop], right);
                    }
                    break;
                case BINARY_MODULO:
                    {
                        ModNode modNode = insertChildNode(() -> ModNodeGen.create(null, null), i);
                        Object right = pop(stackTop--, stack);
                        stack[stackTop] = modNode.executeObject(frame, stack[stackTop], right);
                    }
                    break;
                case BINARY_ADD:
                    {
                        AddNode addNode = insertChildNode(() -> AddNodeGen.create(null, null), i);
                        Object right = pop(stackTop--, stack);
                        stack[stackTop] = addNode.executeObject(frame, stack[stackTop], right);
                    }
                    break;
                case BINARY_SUBTRACT:
                    {
                        SubNode subNode = insertChildNode(() -> SubNodeGen.create(null, null), i);
                        Object right = pop(stackTop--, stack);
                        stack[stackTop] = subNode.executeObject(frame, stack[stackTop], right);
                    }
                    break;
                case BINARY_SUBSCR:
                    {
                        GetItemNode getItemNode = insertChildNode(() -> GetItemNode.create(), i);
                        Object slice = pop(stackTop--, stack);
                        stack[stackTop] = getItemNode.execute(frame, stack[stackTop], slice);
                    }
                    break;
                case BINARY_LSHIFT:
                    {
                        LShiftNode lShiftNode = insertChildNode(() -> LShiftNodeGen.create(null, null), i);
                        Object right = pop(stackTop--, stack);
                        stack[stackTop] = lShiftNode.executeObject(frame, stack[stackTop], right);
                    }
                    break;
                case BINARY_RSHIFT:
                    {
                        RShiftNode rShiftNode = insertChildNode(() -> RShiftNodeGen.create(null, null), i);
                        Object right = pop(stackTop--, stack);
                        stack[stackTop] = rShiftNode.executeObject(frame, stack[stackTop], right);
                    }
                    break;
                case BINARY_AND:
                    {
                        BitAndNode bitAndNode = insertChildNode(() -> BitAndNodeGen.create(null, null), i);
                        Object right = pop(stackTop--, stack);
                        stack[stackTop] = bitAndNode.executeObject(frame, stack[stackTop], right);
                    }
                    break;
                case BINARY_XOR:
                    {
                        BitXorNode bitXorNode = insertChildNode(() -> BitXorNodeGen.create(null, null), i);
                        Object right = pop(stackTop--, stack);
                        stack[stackTop] = bitXorNode.executeObject(frame, stack[stackTop], right);
                    }
                    break;
                case BINARY_OR:
                    {
                        BitOrNode bitOrNode = insertChildNode(() -> BitOrNodeGen.create(null, null), i);
                        Object right = pop(stackTop--, stack);
                        stack[stackTop] = bitOrNode.executeObject(frame, stack[stackTop], right);
                    }
                    break;
                case LIST_APPEND:
                    {
                        PyObjectCallMethodObjArgs callNode = insertChildNode(() -> PyObjectCallMethodObjArgsNodeGen.create(), i);
                        Object value = pop(stackTop--, stack);
                        callNode.execute(frame, stack[stackTop], "append", value);
                    }
                    break;
                case SET_ADD:
                    {
                        PyObjectCallMethodObjArgs callNode = insertChildNode(() -> PyObjectCallMethodObjArgsNodeGen.create(), i);
                        Object value = pop(stackTop--, stack);
                        callNode.execute(frame, stack[stackTop], "add", value);
                    }
                    break;
                case INPLACE_POWER:
                case INPLACE_MULTIPLY:
                case INPLACE_MATRIX_MULTIPLY:
                case INPLACE_TRUE_DIVIDE:
                case INPLACE_FLOOR_DIVIDE:
                case INPLACE_MODULO:
                case INPLACE_ADD:
                case INPLACE_SUBTRACT:
                case INPLACE_LSHIFT:
                case INPLACE_RSHIFT:
                case INPLACE_AND:
                case INPLACE_XOR:
                case INPLACE_OR:
                    throw new RuntimeException("inplace bytecodes");
                case STORE_SUBSCR:
                case DELETE_SUBSCR:
                    throw new RuntimeException("subscript bytecodes");
                case PRINT_EXPR:
                    throw new RuntimeException("PRINT_EXPR");
                case RAISE_VARARGS:
                    {
                        int arg = oparg;
                        Object cause;
                        Object exception;
                        if (arg > 1) {
                            cause = pop(stackTop--, stack);
                        } else {
                            cause = PNone.NO_VALUE;
                        }
                        if (arg > 0) {
                            exception = pop(stackTop--, stack);
                        } else {
                            exception = PNone.NO_VALUE;
                        }
                        RaiseNode raiseNode = insertChildNode(() -> RaiseNode.create(null, null), i);
                        raiseNode.execute(frame, exception, cause);
                    }
                    break;
                case RETURN_VALUE:
                    return stack[stackTop];
                case GET_AITER:
                case GET_ANEXT:
                case GET_AWAITABLE:
                    throw new RuntimeException("async bytecodes");
                case YIELD_FROM:
                case YIELD_VALUE:
                    throw new RuntimeException("yield bytecodes");
                case POP_EXCEPT:
                case POP_BLOCK:
                case POP_FINALLY:
                case CALL_FINALLY:
                case BEGIN_FINALLY:
                case END_FINALLY:
                    throw new RuntimeException("exception blocks");
                case END_ASYNC_FOR:
                    throw new RuntimeException("async bytecodes");
                case LOAD_BUILD_CLASS:
                    {
                        String name = BuiltinNames.__BUILD_CLASS__;
                        ReadGlobalOrBuiltinNode read = insertChildNode(() -> ReadGlobalOrBuiltinNode.create(name), i);
                        stack[++stackTop] = read.execute(frame);
                    }
                    break;
                case STORE_NAME:
                    {
                        String name = names[oparg];
                        WriteGlobalNode writeGlobalNode = insertChildNode(() -> WriteGlobalNode.create(name), i);
                        writeGlobalNode.executeObject(frame, pop(stackTop--, stack));
                    }
                    break;
                case DELETE_NAME:
                    {
                        String name = names[oparg];
                        DeleteGlobalNode deleteGlobalNode = insertChildNode(() -> DeleteGlobalNode.create(name), i);
                        deleteGlobalNode.executeVoid(frame);
                    }
                    break;
                case UNPACK_SEQUENCE:
                case UNPACK_EX:
                    throw new RuntimeException("unpack bytecodes");
                case STORE_ATTR:
                    {
                        String name = names[oparg];
                        Object owner = pop(stackTop--, stack);
                        Object value = pop(stackTop--, stack);
                        throw new RuntimeException("store attr");
                    }
                case DELETE_ATTR:
                    throw new RuntimeException("delete attr");
                case STORE_GLOBAL:
                case DELETE_GLOBAL:
                    throw new RuntimeException("global writes");
                case LOAD_NAME:
                case LOAD_GLOBAL: // we use the same node for both of these, unlike CPython
                    {
                        String name = names[oparg];
                        ReadGlobalOrBuiltinNode read = insertChildNode(() -> ReadGlobalOrBuiltinNode.create(name), i);
                        stack[++stackTop] = read.execute(frame);
                    }
                    break;
                case DELETE_FAST:
                case DELETE_DEREF:
                    throw new RuntimeException("delete locals");
                case LOAD_CLOSURE:
                    throw new RuntimeException("LOAD_CLOSURE");
                case LOAD_CLASSDEREF:
                    throw new RuntimeException("LOAD_CLASSDEREF");
                case LOAD_DEREF:
                case STORE_DEREF:
                    throw new RuntimeException("deref load/store");
                case BUILD_STRING:
                case BUILD_TUPLE:
                case BUILD_LIST:
                case BUILD_TUPLE_UNPACK_WITH_CALL:
                case BUILD_TUPLE_UNPACK:
                case BUILD_LIST_UNPACK:
                case BUILD_SET:
                case BUILD_SET_UNPACK:
                case BUILD_MAP:
                case SETUP_ANNOTATIONS:
                case BUILD_CONST_KEY_MAP:
                case BUILD_MAP_UNPACK:
                case BUILD_MAP_UNPACK_WITH_CALL:
                    throw new RuntimeException("build bytecodes");
                case MAP_ADD:
                    throw new RuntimeException("MAP_ADD");
                case LOAD_ATTR:
                    {
                        String name = names[oparg];
                        Object owner = top(stackTop, stack);
                        Object value = PyObjectGetAttr.getUncached().execute(frame, owner, name);
                        stack[stackTop] = value;
                    }
                    break;
                case COMPARE_OP:
                    throw new RuntimeException("COMARE_OP");
                case IMPORT_NAME:
                    {
                        String name = names[oparg];
                        Object fromlist = pop(stackTop--, stack);
                        String[] fromlistArg;
                        if (fromlist == PNone.NONE) {
                            fromlistArg = PythonUtils.EMPTY_STRING_ARRAY;
                        } else {
                            // import statement won't be dynamically created, so the fromlist is always
                            // from a LOAD_CONST, which will either be a tuple of strings or None.
                            assert fromlist instanceof PTuple;
                            Object[] list = ((PTuple) fromlist).getSequenceStorage().getInternalArray();
                            fromlistArg = (String[]) list;
                        }
                        CastToJavaIntExactNode castNode = insertChildNode(() -> CastToJavaIntExactNode.create(), i);
                        int level = castNode.execute(top(stackTop, stack));
                        ImportName importNode = insertChildNode(() -> ImportName.create(), i + 1);
                        Object result = importNode.execute(frame, context, builtins, name, globals, fromlistArg, level);
                        stack[stackTop] = result;
                    }
                    break;
                case IMPORT_STAR:
                case IMPORT_FROM:
                    throw new RuntimeException("import start / import from");
                case JUMP_FORWARD:
                    i = i + oparg;
                    break;
                case POP_JUMP_IF_FALSE:
                    {
                        Object cond = pop(stackTop--, stack);
                        if (!PyObjectIsTrueNode.getUncached().execute(frame, cond)) {
                            i = oparg;
                        }
                    }
                    break;
                case POP_JUMP_IF_TRUE:
                    {
                        Object cond = pop(stackTop--, stack);
                        if (PyObjectIsTrueNode.getUncached().execute(frame, cond)) {
                            i = oparg;
                        }
                    }
                    break;
                case JUMP_IF_FALSE_OR_POP:
                    {
                        Object cond = stack[stackTop];
                        if (!PyObjectIsTrueNode.getUncached().execute(frame, cond)) {
                            i = oparg;
                        } else {
                            pop(stackTop--, stack);
                        }
                    }
                    break;
                case JUMP_IF_TRUE_OR_POP:
                    {
                        Object cond = stack[stackTop];
                        if (PyObjectIsTrueNode.getUncached().execute(frame, cond)) {
                            i = oparg;
                        } else {
                            pop(stackTop--, stack);
                        }
                    }
                    break;
                case JUMP_ABSOLUTE:
                    i = oparg;
                    break;
                case GET_ITER:
                case GET_YIELD_FROM_ITER:
                case FOR_ITER:
                case SETUP_FINALLY:
                case BEFORE_ASYNC_WITH:
                case SETUP_ASYNC_WITH:
                case SETUP_WITH:
                case WITH_CLEANUP_START:
                case WITH_CLEANUP_FINISH:
                    throw new RuntimeException("loop / with / finally blocks");
                case LOAD_METHOD:
                case CALL_METHOD:
                    throw new RuntimeException("_METHOD bytecodes");
                case CALL_FUNCTION:
                    {
                        Object func = stack[stackTop - oparg];
                        Object[] arguments = new Object[oparg];
                        for (int j = 0; j < oparg; j++) {
                            arguments[j] = pop(stackTop--, stack);
                        }
                        Object result = CallNode.getUncached().execute(func, arguments);
                        stack[stackTop] = result;
                    }
                    break;
                case CALL_FUNCTION_KW:
                    {
                        String[] kwNames = (String[]) ((PTuple) pop(stackTop--, stack)).getSequenceStorage().getInternalArray();
                        Object func = stack[stackTop - oparg];
                        int nkwargs = kwNames.length;
                        int nargs = oparg - nkwargs;
                        Object[] arguments = new Object[nargs];
                        for (int j = 0; j < nargs; j++) {
                            arguments[j] = pop(stackTop--, stack);
                        }
                        PKeyword[] kwArgs = new PKeyword[nkwargs];
                        for (int j = 0; j < nkwargs; j++) {
                            kwArgs[j] = new PKeyword(kwNames[j], pop(stackTop--, stack));
                        }
                        stack[stackTop] = CallNode.getUncached().execute(func, arguments, kwArgs);
                    }
                    break;
                case CALL_FUNCTION_EX:
                    {
                        Object func, callargs, kwargs, result;
                        if ((oparg & 0x01) != 0) {
                            kwargs = pop(stackTop--, stack);
                            // unpack dict-like into PKeywords[]
                        } else {
                            kwargs = PKeyword.EMPTY_KEYWORDS;
                        }
                        callargs = pop(stackTop--, stack);
                        // todo: convert iterable to Object[]
                        func = stack[stackTop];
                        // todo: do call
                        throw new RuntimeException("CALL_FUNCTION_EX bytecodes");
                    }
                case MAKE_FUNCTION:
                    {
                        String qualname = CastToJavaStringNode.getUncached().execute(pop(stackTop--, stack));
                        PCode codeobj = (PCode) pop(stackTop--, stack);
                        PCell[] closure = null;
                        Object annotations = null;
                        PKeyword[] kwdefaults = null;
                        Object[] defaults = null;
                        if ((oparg & 0x08) != 0) {
                            closure = (PCell[]) ((PTuple) pop(stackTop--, stack)).getSequenceStorage().getInternalArray();
                        }
                        if ((oparg & 0x04) != 0) {
                            annotations = pop(stackTop--, stack);
                        }
                        if ((oparg & 0x02) != 0) {
                            PDict kwDict = (PDict) pop(stackTop--, stack);
                            HashingStorage store = kwDict.getDictStorage();
                            HashingStorageLibrary lib = HashingStorageLibrary.getFactory().getUncached(store);
                            if (store instanceof KeywordsStorage) {
                                kwdefaults = ((KeywordsStorage) store).getStore();
                            } else {
                                kwdefaults = new PKeyword[lib.length(store)];
                                int j = 0;
                                for (HashingStorage.DictEntry entry : lib.entries(store)) {
                                    kwdefaults[j++] = new PKeyword((String) entry.key, entry.value);
                                }
                            }
                        }
                        if ((oparg & 0x01) != 0) {
                            defaults = ((PTuple) pop(stackTop--, stack)).getSequenceStorage().getInternalArray();
                        }
                        stack[++stackTop] = factory.createFunction(qualname, null, codeobj, (PythonObject) globals, (Object[]) defaults, (PKeyword[]) kwdefaults, (PCell[]) closure);
                        if (annotations != null) {
                            DynamicObjectLibrary.getUncached().put((DynamicObject)stack[stackTop], __ANNOTATIONS__, annotations);
                        }
                    }
                    break;
                case BUILD_SLICE:
                    throw new RuntimeException("BUILD_SLICE");
                case FORMAT_VALUE:
                    throw new RuntimeException("FORMAT_VALUE");
                default:
                    throw new RuntimeException("not implemented bytecode");
            }
        }
        throw new RuntimeException("no return from bytecode");
    }

    private static Object top(int stackTop, Object[] stack) {
        return stack[stackTop];
    }

    private static Object pop(int stackTop, Object[] stack) {
        Object result = stack[stackTop];
        stack[stackTop] = null;
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
