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

@GenerateEnumConstants
public enum OpCodes {
    POP_TOP(0, 1, 0),
    ROT_TWO(0, 2, 2),
    ROT_THREE(0, 3, 3),
    DUP_TOP(0, 1, 2),
    ROT_FOUR(0, 4, 4),
    NOP(0, 0, 0),
    UNARY_OP(1, 1, 1),
    BINARY_OP(1, 2, 1),
    BINARY_SUBSCR(0, 2, 1),
    STORE_SUBSCR(0, 3, 0),
    DELETE_SUBSCR(0, 2, 0),
    GET_ITER(0, 1, 1),
    GET_AWAITABLE(0, 1, 1),
    LOAD_BUILD_CLASS(0, 0, 1),
    LOAD_ASSERTION_ERROR(0, 0, 1),
    RETURN_VALUE(0, 1, 0),
    STORE_NAME(1, 1, 0),
    DELETE_NAME(1, 0, 0),
    STORE_ATTR(1, 2, 0),
    DELETE_ATTR(1, 1, 0),
    STORE_GLOBAL(1, 1, 0),
    DELETE_GLOBAL(1, 0, 0),
    LOAD_CONST(1, 0, 1),
    LOAD_NAME(1, 0, 1),
    LOAD_ATTR(1, 1, 1),
    LOAD_GLOBAL(1, 0, 1),
    LOAD_FAST(1, 0, 1),
    STORE_FAST(1, 1, 0),
    DELETE_FAST(1, 0, 0),
    RAISE_VARARGS(1, (oparg, followingArgs, withJump) -> oparg, 0),
    LOAD_DEREF(1, 0, 1),
    STORE_DEREF(1, 1, 0),
    DELETE_DEREF(1, 0, 0),
    LOAD_CLASSDEREF(1, 0, 1),
    BUILD_SLICE(1, (oparg, followingArgs, withJump) -> oparg, 1),
    FORMAT_VALUE(1, (oparg, followingArgs, withJump) -> (oparg & FormatOptions.FVS_MASK) == FormatOptions.FVS_HAVE_SPEC ? 2 : 1, 1),

    EXTENDED_ARG(1, 0, 0),

    IMPORT_NAME(1, 2, 1),
    IMPORT_FROM(1, 1, 2),
    IMPORT_STAR(1, 1, 0),

    // load bytecodes for special constants
    LOAD_NONE(0, 0, 1),
    LOAD_ELLIPSIS(0, 0, 1),
    LOAD_TRUE(0, 0, 1),
    LOAD_FALSE(0, 0, 1),
    LOAD_BYTE(1, 0, 1),
    LOAD_LONG(1, 0, 1),
    LOAD_DOUBLE(1, 0, 1),
    LOAD_BIGINT(1, 0, 1),
    LOAD_STRING(1, 0, 1),
    LOAD_BYTES(1, 0, 1),
    LOAD_COMPLEX(1, 0, 1),

    // calling
    // args[] => result
    CALL_METHOD_VARARGS(1, 1, 1),
    CALL_METHOD(2, (oparg, followingArgs, withJump) -> Byte.toUnsignedInt(followingArgs[0]) + 1, 1),
    CALL_FUNCTION(1, (oparg, followingArgs, withJump) -> oparg + 1, 1),
    // func, args[], keywords[] => result
    CALL_FUNCTION_KW(0, 3, 1),
    // func, args[] => result
    CALL_FUNCTION_VARARGS(0, 2, 1),

    // destructuring bytecodes
    UNPACK_EX(2, 1, (oparg, followingArgs, withJump) -> oparg + 1 + Byte.toUnsignedInt(followingArgs[0])),
    UNPACK_SEQUENCE(1, 1, (oparg, followingArgs, withJump) -> oparg),

    // jumps
    FOR_ITER(1, 1, (oparg, followingArgs, withJump) -> withJump ? 0 : 2),
    JUMP_FORWARD(1, 0, 0),
    JUMP_BACKWARD(1, 0, 0),
    JUMP_IF_FALSE_OR_POP(1, (oparg, followingArgs, withJump) -> withJump ? 0 : 1, 0),
    JUMP_IF_TRUE_OR_POP(1, (oparg, followingArgs, withJump) -> withJump ? 0 : 1, 0),
    POP_AND_JUMP_IF_TRUE(1, 1, 0),
    POP_AND_JUMP_IF_FALSE(1, 1, 0),

    // making callables
    LOAD_CLOSURE(1, 0, 1),
    CLOSURE_FROM_STACK(1, (oparg, followingArgs, withJump) -> oparg, 1),
    MAKE_FUNCTION(2, (oparg, followingArgs, withJump) -> Integer.bitCount(followingArgs[0]), 1),

    // collection literals
    // add to coll underneath args the arg elements above
    COLLECTION_ADD_STACK(1, (oparg, followingArgs, withJump) -> CollectionBits.elementCount(oparg) + 1, 1),
    // build a collection from arg elements on stack
    COLLECTION_FROM_STACK(1, (oparg, followingArgs, withJump) -> CollectionBits.elementCount(oparg), 1),
    // add the collection on top of stack to the collection underneath
    COLLECTION_ADD_COLLECTION(1, 2, 1),
    // replace the collection on top of stack with a collection of another type
    COLLECTION_FROM_COLLECTION(1, 1, 1),
    // adds one element to a collection that is `oparg` deep. Used to implement comprehensions
    ADD_TO_COLLECTION(1, (oparg, followingArgs, withJump) -> CollectionBits.elementType(oparg) == CollectionBits.DICT ? 2 : 1, 0),
    // like COLLECTION_ADD_COLLECTION for Dict, but with checks for duplicate keys
    KWARGS_DICT_MERGE(0, 2, 1),
    MAKE_KEYWORD(1, 1, 1),

    // exceptions
    MATCH_EXC_OR_JUMP(1, 1, 0),
    PUSH_EXC_INFO(0, 0, 1),
    POP_EXCEPT(0, 1, 0),
    END_EXC_HANDLER(0, 2, 0),
    UNWRAP_EXC(0, 1, 1),

    // generators
    YIELD_VALUE(0, 1, 0),
    RESUME_YIELD(0, 0, 1),
    SEND(1, 2, (oparg, followingArgs, withJump) -> withJump ? 1 : 2),

    // with statements
    SETUP_WITH(0, 1, 3),
    EXIT_WITH(0, 3, 0);

    public static final class CollectionBits {
        public static final int MAX_STACK_ELEMENT_COUNT = 0b00011111;
        public static final int LIST = 0b00100000;
        public static final int TUPLE = 0b01000000;
        public static final int SET = 0b01100000;
        public static final int DICT = 0b10000000;
        public static final int KWORDS = 0b10100000;
        public static final int OBJECT = 0b11000000;

        public static int elementCount(int oparg) {
            return oparg & MAX_STACK_ELEMENT_COUNT;
        }

        public static int elementType(int oparg) {
            return oparg & ~MAX_STACK_ELEMENT_COUNT;
        }
    }

    public static final OpCodes[] VALUES = new OpCodes[values().length];

    static {
        assert values().length < 256;
        System.arraycopy(values(), 0, VALUES, 0, VALUES.length);
    }

    public final StackEffect consumesStackItems;
    public final StackEffect producesStackItems;

    /**
     * Instruction argument length in bytes
     */
    public final int argLength;

    OpCodes(int argLength, int consumesStackItems, int producesStackItems) {
        this(argLength, (oparg, followingArgs, withJump) -> consumesStackItems, (oparg, followingArgs, withJump) -> producesStackItems);
    }

    OpCodes(int argLength, StackEffect consumesStackItems, int producesStackItems) {
        this(argLength, consumesStackItems, (oparg, followingArgs, withJump) -> producesStackItems);
    }

    OpCodes(int argLength, int consumesStackItems, StackEffect producesStackItems) {
        this(argLength, (oparg, followingArgs, withJump) -> consumesStackItems, producesStackItems);
    }

    OpCodes(int argLength, StackEffect consumesStackItems, StackEffect producesStackItems) {
        this.argLength = argLength;
        this.consumesStackItems = consumesStackItems;
        this.producesStackItems = producesStackItems;
    }

    @FunctionalInterface
    private interface StackEffect {
        int stackEffect(int oparg, byte[] followingArgs, boolean withJump);
    }

    public boolean hasArg() {
        return argLength > 0;
    }

    public int length() {
        return argLength + 1;
    }

    public int getNumberOfConsumedStackItems(int oparg, byte[] followingArgs, boolean withJump) {
        return consumesStackItems.stackEffect(oparg, followingArgs, withJump);
    }

    public int getNumberOfProducedStackItems(int oparg, byte[] followingArgs, boolean withJump) {
        return producesStackItems.stackEffect(oparg, followingArgs, withJump);
    }

    public int getStackEffect(int oparg, byte[] followingArgs, boolean withJump) {
        return getNumberOfProducedStackItems(oparg, followingArgs, withJump) - getNumberOfConsumedStackItems(oparg, followingArgs, withJump);
    }
}
