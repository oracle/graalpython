/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.compiler;

import com.oracle.graal.python.annotations.GenerateEnumConstants;

class Constants {
    static final int CALL_EFFECT = 0xcaffee00;
    static final int CALL_METHOD_EFFECT = 0xcaffee01;
    static final int UNPACK_EFFECT = 0xcaffee02;
    static final int UNPACK_EX_EFFECT = 0xcaffee03;
    static final int POP_EFFECT = 0xcaffee04;
    static final int REDUCE_EFFECT = 0xcaffee05;
    static final int POP_OR_JUMP_EFFECT = 0xcaffee06;
    static final int PUSH_OR_JUMP_EFFECT = 0xcaffee07;
    static final int POP_EFFECT5 = 0xcaffee08;
    static final int REDUCE_EFFECT5 = 0xcaffee09;
}

@GenerateEnumConstants
public enum OpCodes {
    POP_TOP(                   0,   -1),
    ROT_TWO(                   0,    0),
    ROT_THREE(                 0,    0),
    DUP_TOP(                   0,    1),
    DUP_TOP_TWO(               0,    2),
    ROT_FOUR(                  0,    0),
    NOP(                      -1,    0),
    UNARY_POSITIVE(            0,    0),
    UNARY_NEGATIVE(            0,    0),
    UNARY_NOT(                 0,    0),
    UNARY_INVERT(              0,    0),
    BINARY_MATRIX_MULTIPLY(    0,   -1),
    INPLACE_MATRIX_MULTIPLY(   0,   -1),
    BINARY_POWER(              0,   -1),
    BINARY_MULTIPLY(           0,   -1),
    BINARY_MODULO(             0,   -1),
    BINARY_ADD(                0,   -1),
    BINARY_SUBTRACT(           0,   -1),
    BINARY_SUBSCR(             0,   -1),
    BINARY_FLOOR_DIVIDE(       0,   -1),
    BINARY_TRUE_DIVIDE(        0,   -1),
    INPLACE_FLOOR_DIVIDE(      0,   -1),
    INPLACE_TRUE_DIVIDE(       0,   -1),
    INPLACE_ADD(               0,   -1),
    INPLACE_SUBTRACT(          0,   -1),
    INPLACE_MULTIPLY(          0,   -1),
    INPLACE_MODULO(            0,   -1),
    STORE_SUBSCR(              0,   -3),
    DELETE_SUBSCR(             0,   -2),
    BINARY_LSHIFT(             0,   -1),
    BINARY_RSHIFT(             0,   -1),
    BINARY_AND(                0,   -1),
    BINARY_XOR(                0,   -1),
    BINARY_OR(                 0,   -1),
    INPLACE_POWER(             0,   -1),
    GET_ITER(                  0,    0),
    LOAD_BUILD_CLASS(          0,    1),
    LOAD_ASSERTION_ERROR(      0,    1),
    INPLACE_LSHIFT(            0,   -1),
    INPLACE_RSHIFT(            0,   -1),
    INPLACE_AND(               0,   -1),
    INPLACE_XOR(               0,   -1),
    INPLACE_OR(                0,   -1),
    RETURN_VALUE(              0,   -1),
    STORE_NAME(                1,   -1),
    DELETE_NAME(               1,    0),
    STORE_ATTR(                1,   -2),
    DELETE_ATTR(               1,   -1),
    STORE_GLOBAL(              1,   -1),
    DELETE_GLOBAL(             1,    0),
    ROT_N(                     1,    0),
    LOAD_CONST(                1,    1),
    LOAD_NAME(                 1,    1),
    BUILD_TUPLE(               0,    0),
    BUILD_LIST(                0,    0),
    BUILD_SET(                 0,    0),
    BUILD_MAP(                 1,    0),
    LOAD_ATTR(                 1,    0),
    COMPARE_OP(                1,   -1),
    LOAD_GLOBAL(               1,    1),
    IS_OP(                     1,   -1),
    CONTAINS_OP(               1,   -1),
    LOAD_FAST(                 1,    1),
    STORE_FAST(                1,   -1),
    DELETE_FAST(               1,    0),
    RAISE_VARARGS(             1,    Constants.POP_EFFECT),
    LOAD_DEREF(                1,    1),
    STORE_DEREF(               1,   -1),
    DELETE_DEREF(              1,    0),
    LOAD_CLASSDEREF(           1,    1),

    IMPORT_NAME(               1,   -1),
    IMPORT_FROM(               1,    1),

    // load bytecodes for special constants
    LOAD_NONE(                 0,    1),
    LOAD_ELLIPSIS(             0,    1),
    LOAD_TRUE(                 0,    1),
    LOAD_FALSE(                0,    1),
    LOAD_BYTE(                 1,    1),
    LOAD_LONG(                 1,    1),
    LOAD_DOUBLE(               1,    1),
    LOAD_BIGINT(               1,    1),
    LOAD_STRING(               1,    1),
    LOAD_BYTES(                1,    1),
    // make a complex from two doubles on the top of stack
    MAKE_COMPLEX(              0,   -1),

    // calling
    CALL_METHOD_VARARGS(       1,   -1), // receiver, args[] => result
    CALL_METHOD(               2,    Constants.CALL_METHOD_EFFECT),
    CALL_FUNCTION(             1,    Constants.CALL_EFFECT),
    CALL_FUNCTION_KW(          0,   -2), // func, args[], keywords[] => result
    CALL_FUNCTION_VARARGS(     0,   -1), // func, args[] => result

    // destructuring bytecodes
    UNPACK_EX(                 1,    Constants.UNPACK_EX_EFFECT),
    UNPACK_EX_LARGE(           2,    Constants.UNPACK_EX_EFFECT),
    UNPACK_SEQUENCE(           1,    Constants.UNPACK_EFFECT),
    UNPACK_SEQUENCE_LARGE(     2,    Constants.UNPACK_EFFECT),

    // jumps
    FOR_ITER(                  1,    Constants.PUSH_OR_JUMP_EFFECT),
    FOR_ITER_FAR(              2,    Constants.PUSH_OR_JUMP_EFFECT),
    JUMP_FORWARD(              1,    0),
    JUMP_FORWARD_FAR(          2,    0),
    JUMP_BACKWARD(             1,    0),
    JUMP_BACKWARD_FAR(         2,    0),
    JUMP_IF_FALSE_OR_POP(      1,    Constants.POP_OR_JUMP_EFFECT),
    JUMP_IF_FALSE_OR_POP_FAR(  2,    Constants.POP_OR_JUMP_EFFECT),
    JUMP_IF_TRUE_OR_POP(       1,    Constants.POP_OR_JUMP_EFFECT),
    JUMP_IF_TRUE_OR_POP_FAR(   2,    Constants.POP_OR_JUMP_EFFECT),
    POP_AND_JUMP_IF_TRUE(      1,   -1),
    POP_AND_JUMP_IF_TRUE_FAR(  2,   -1),
    POP_AND_JUMP_IF_FALSE(     1,   -1),
    POP_AND_JUMP_IF_FALSE_FAR( 2,   -1),

    // making callables
    LOAD_CLOSURE(              1,    1),
    CLOSURE_FROM_STACK(        1,    Constants.REDUCE_EFFECT),
    MAKE_FUNCTION(             1,    Constants.REDUCE_EFFECT),

    // collection literals
    COLLECTION_ADD_STACK(      1,    Constants.POP_EFFECT5), // add to coll underneath args the arg elements above
    COLLECTION_FROM_STACK(     1,    Constants.REDUCE_EFFECT5), // build a collection from arg elements on stack
    COLLECTION_ADD_COLLECTION( 1,   -1), // add the collection on top of stack to the collection underneath
    COLLECTION_FROM_COLLECTION(1,    0), // replace the collection on top of stack with a collection of another type
    MAKE_KEYWORD(              1,    0),

    // exceptions
    MATCH_EXC_OR_JUMP(         1,   -1),
    MATCH_EXC_OR_JUMP_FAR(     2,   -1),
    END_FINALLY(               0,   -1);

    public static final class CollectionBits {
        public static final byte MAX_STACK_ELEMENT_COUNT = 0b00011111;
        public static final byte LIST   = 0b001;
        public static final byte TUPLE  = 0b010;
        public static final byte SET    = 0b011;
        public static final byte DICT   = 0b100;
        public static final byte KWORDS = 0b101;
        public static final byte OBJECT = 0b110;
    }

    static {
        assert values().length < 256;
    }

    public final int stackEffect;

    /**
     * Instruction argument length in bytes
     */
    public final int argLength;

    private OpCodes(int argLength, int stackEffect) {
        this.argLength = argLength;
        this.stackEffect = stackEffect;
    }

    public boolean hasArg() {
        return argLength > 0;
    }

    public int length() {
        return argLength + 1;
    }

    public int getStackEffect(int oparg, boolean withJump) {
        switch (stackEffect) {
            case Constants.CALL_EFFECT:
                // arg is argcount, pop args, pop function, push result
                return -oparg - 1 + 1;
            case Constants.CALL_METHOD_EFFECT:
                // arg is argcount | method name const idx, pop args, pop receiver, push result
                return -(oparg >> 8) - 1 + 1;
            case Constants.POP_EFFECT:
                // arg is number of elements that get popped pop
                return -oparg;
            case Constants.REDUCE_EFFECT:
                // arg is number of elements that get popped and reduced into a new element
                return -oparg + 1;
            case Constants.POP_EFFECT5:
                return -(oparg & 0b11111);
            case Constants.REDUCE_EFFECT5:
                // arg is number of elements that get popped and reduced into a new element
                return -(oparg & 0b11111) + 1;
            case Constants.UNPACK_EFFECT:
                // arg is number of elements to unpack the list at the top of stack into, removing the
                // list
                return -1 + oparg;
            case Constants.UNPACK_EX_EFFECT:
                // arg is split evenly in bits into into number of args before and number of args after
                // the stararg to expand into, then we also get the stararg list, and we pop the list
                // that we are unpacking from
                if (oparg <= 0xff) {
                    return (oparg & 0xf) + (oparg >> 4) + 1 - 1;
                } else if (oparg <= 0xffff) {
                    return (oparg & 0xff) + (oparg >> 8) + 1 - 1;
                } else {
                    throw new IllegalStateException("not supported");
                }
            case Constants.POP_OR_JUMP_EFFECT:
                return withJump ? 0 : -1;
            case Constants.PUSH_OR_JUMP_EFFECT:
                return withJump ? 0 : 1;
            default:
                return stackEffect;
        }
    }
}
