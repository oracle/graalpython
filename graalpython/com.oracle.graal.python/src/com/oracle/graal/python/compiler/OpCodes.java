/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.math.BigInteger;

import com.oracle.graal.python.annotations.GenerateEnumConstants;
import com.oracle.graal.python.builtins.objects.asyncio.PAsyncGenWrappedValue;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.runtime.exception.PException;

/**
 * Operation codes of our bytecode interpreter. They are similar to CPython's, but not the same. Our
 * opcodes can have multiple bytes of immediate operands. The first operand can be variably extended
 * using {@link #EXTENDED_ARG} instruction.
 */
@GenerateEnumConstants(type = GenerateEnumConstants.Type.BYTE)
public enum OpCodes {
    /** Pop a single item from the stack */
    POP_TOP(0, 1, 0),
    /** Exchange two top stack items */
    ROT_TWO(0, 2, 2),
    /** Exchange three top stack items. [a, b, c] (a is top) becomes [b, c, a] */
    ROT_THREE(0, 3, 3),
    /** Exchange N top stack items. [a, b, c, ..., N] (a is top) becomes [b, c, ..., N, a] */
    ROT_N(1, (oparg, followingArgs, withJump) -> oparg, (oparg, followingArgs, withJump) -> oparg),
    /** Duplicates the top stack item */
    DUP_TOP(0, 1, 2),
    /** Does nothing. Might still be useful to maintain a line number */
    NOP(0, 0, 0),
    /**
     * Performs a unary operation specified by the immediate operand. It has to be the ordinal of
     * one of {@link UnaryOps} constants.
     *
     * Pops: operand
     *
     * Pushes: result
     */
    UNARY_OP(1, 1, 1),
    /**
     * Performs a binary operation specified by the immediate operand. It has to be the ordinal of
     * one of {@link BinaryOps} constants.
     *
     * Pops: right operand, then left operand
     *
     * Pushes: result
     */
    BINARY_OP(1, 2, 1),
    /**
     * Performs subscript get operation - {@code a[b]}.
     *
     * Pops: {@code b}, then {@code a}
     *
     * Pushes: result
     */
    BINARY_SUBSCR(0, 2, 1),
    /**
     * Performs subscript set operation - {@code a[b] = c}.
     *
     * Pops: {@code b}, then {@code a}, then {@code c}
     */
    STORE_SUBSCR(0, 3, 0),
    /**
     * Performs subscript delete operation - {@code del a[b]}.
     *
     * Pops: {@code b}, then {@code a}
     */
    DELETE_SUBSCR(0, 2, 0),
    /**
     * Gets an iterator of an object
     *
     * Pops: object
     *
     * Pushes: iterator
     */
    GET_ITER(0, 1, 1),
    /**
     * Gets an iterator of an object, does nothing for a generator iterator or a coroutine
     *
     * Pops: object
     *
     * Pushes: iterator
     */
    GET_YIELD_FROM_ITER(0, 1, 1),
    /**
     * Gets an awaitable of an object
     *
     * Pops: object
     *
     * Pushes: awaitable
     */
    GET_AWAITABLE(0, 1, 1),
    /**
     * Gets the async iterator of an object - error if a coroutine is returned
     *
     * Pops: object
     *
     * Pushes: async iterator
     */
    GET_AITER(0, 1, 1),
    /**
     * Get the awaitable that will return the next element of an async iterator
     *
     * Pops: object
     *
     * Pushes: awaitable
     */
    GET_ANEXT(0, 1, 1),
    /**
     * Pushes: {@code __build_class__} builtin
     */
    LOAD_BUILD_CLASS(0, 0, 1),
    /**
     * Pushes: {@code AssertionError} builtin exception type
     */
    LOAD_ASSERTION_ERROR(0, 0, 1),
    /**
     * Returns the value to the caller. In generators, performs generator return.
     *
     * Pops: return value
     */
    RETURN_VALUE(0, 1, 0),
    /**
     * Reads a name from locals dict, globals or builtins determined by the immediate operand which
     * indexes the names array ({@code co_names}).
     *
     * Pushes: read object
     */
    LOAD_NAME(1, 0, 1),
    /**
     * Writes the stack top into a name in locals dict or globals determined by the immediate
     * operand which indexes the names array ({@code co_names}).
     *
     * Pops: object to be written
     */
    STORE_NAME(1, 1, 0),
    /**
     * Deletes the name in locals dict or globals determined by the immediate operand which indexes
     * the names array ({@code co_names}).
     */
    DELETE_NAME(1, 0, 0),
    /**
     * Reads an attribute - {@code a.b}. {@code b} is determined by the immediate operand which
     * indexes the names array ({@code co_names}).
     *
     * Pops: {@code a}
     *
     * Pushes: read attribute
     */
    LOAD_ATTR(1, 1, 1),
    /**
     * Reads method on an object. The method name is determined by the first immediate operand which
     * indexes the names array ({@code co_names}).
     *
     * Pushes: read method
     */
    LOAD_METHOD(1, 1, 2),
    /**
     * Writes an attribute - {@code a.b = c}. {@code b} is determined by the immediate operand which
     * indexes the names array ({@code co_names}).
     *
     * Pops: {@code c}, then {@code a}
     */
    STORE_ATTR(1, 2, 0),
    /**
     * Deletes an attribute - {@code del a.b}. {@code b} is determined by the immediate operand
     * which indexes the names array ({@code co_names}).
     *
     * Pops: {@code a}
     */
    DELETE_ATTR(1, 1, 0),
    /**
     * Reads a global variable. The name is determined by the immediate operand which indexes the
     * names array ({@code co_names}).
     *
     * Pushes: read object
     */
    LOAD_GLOBAL(1, 0, 1),
    /**
     * Writes a global variable. The name is determined by the immediate operand which indexes the
     * names array ({@code co_names}).
     *
     * Pops: value to be written
     */
    STORE_GLOBAL(1, 1, 0),
    /**
     * Deletes a global variable. The name is determined by the immediate operand which indexes the
     * names array ({@code co_names}).
     */
    DELETE_GLOBAL(1, 0, 0),
    /**
     * Reads a constant object from constants array ({@code co_consts}). Performs no conversion.
     *
     * Pushes: read constant
     */
    LOAD_CONST(1, 0, 1),
    /**
     * Reads a local variable determined by the immediate operand which indexes a stack slot and a
     * variable name in varnames array ({@code co_varnames}).
     *
     * Pushes: read value
     */
    LOAD_FAST(1, 0, 1),
    /**
     * Writes a local variable determined by the immediate operand which indexes a stack slot and a
     * variable name in varnames array ({@code co_varnames}).
     *
     * Pops: value to be writen
     */
    STORE_FAST(1, 1, 0),
    /**
     * Deletes a local variable determined by the immediate operand which indexes a stack slot and a
     * variable name in varnames array ({@code co_varnames}).
     */
    DELETE_FAST(1, 0, 0),
    /**
     * Reads a local cell variable determined by the immediate operand which indexes a stack slot
     * after celloffset and a variable name in cellvars or freevars array ({@code co_cellvars},
     * {@code co_freevars}).
     *
     * Pushes: cell contents
     */
    LOAD_DEREF(1, 0, 1),
    /**
     * Writes a local cell variable determined by the immediate operand which indexes a stack slot
     * after celloffset and a variable name in cellvars or freevars array ({@code co_cellvars},
     * {@code co_freevars}).
     *
     * Pops: value to be written into the cell contents
     */
    STORE_DEREF(1, 1, 0),
    /**
     * Deletes a local cell variable determined by the immediate operand which indexes a stack slot
     * after celloffset and a variable name in cellvars or freevars array ({@code co_cellvars},
     * {@code co_freevars}). Note that it doesn't delete the cell, just its contents.
     */
    DELETE_DEREF(1, 0, 0),
    /**
     * TODO not implemented
     */
    LOAD_CLASSDEREF(1, 0, 1),
    /**
     * Raises an exception. If the immediate operand is 0, it pops nothing and is equivalent to
     * {@code raise} without arguments. If the immediate operand is 1, it is equivalent to
     * {@code raise e} and it pops {@code e}. If the immediate operand is 2, it is equivalent to
     * {@code raise e from c} and it pops {@code c}, then {@code e}. Other immediate operand values
     * are illegal.
     */
    RAISE_VARARGS(1, (oparg, followingArgs, withJump) -> oparg, 0),
    /**
     * Creates a slice object. If the immediate argument is 2, it is equivalent to a slice
     * {@code a:b}. It pops {@code b}, then {@code a}. If the immediate argument is 3, it is
     * equivalent to a slice {@code a:b:c}. It pops {@code c}, then {@code b}, then {@code a}. Other
     * immediate operand values are illegal.
     *
     * Pushes: the created slice object
     */
    BUILD_SLICE(1, (oparg, followingArgs, withJump) -> oparg, 1),
    /**
     * Formats a value. If the immediate argument contains flag {@link FormatOptions#FVS_HAVE_SPEC},
     * it is equivalent to {@code format(conv(v), spec)}. It pops {@code spec}, then {@code v}.
     * Otherwise, it is equivalent to {@code format(conv(v), None)}. It pops {@code v}. {@code conv}
     * is determined by the immediate operand which contains one of the {@code FVC} options in
     * {@link FormatOptions}.
     *
     * Pushes: the formatted value
     */
    FORMAT_VALUE(1, (oparg, followingArgs, withJump) -> (oparg & FormatOptions.FVS_MASK) == FormatOptions.FVS_HAVE_SPEC ? 2 : 1, 1),

    /**
     * Extends the immediate operand of the following instruction by its own operand shifted left by
     * a byte.
     */
    EXTENDED_ARG(1, 0, 0),

    /**
     * Imports a module by name determined by the immediate operand which indexes the names array
     * ({@code co_names}).
     *
     * Pops: fromlist (must be a constant {@code TruffleString[]}), then level (must be {@code int})
     *
     * Pushes: imported module
     */
    IMPORT_NAME(1, 2, 1),
    /**
     * Imports a name from a module. The name determined by the immediate operand which indexes the
     * names array ({@code co_names}).
     *
     * Pops: module object
     *
     * Pushes: module object, imported object
     */
    IMPORT_FROM(1, 1, 2),
    /**
     * Imports all names from a module of name determined by the immediate operand which indexes the
     * names array ({@code co_names}). The imported names are written to locals dict (can only be
     * invoked on module level).
     *
     * Pops: level (must be {@code int})
     */
    IMPORT_STAR(1, 1, 0),
    /**
     * Prints the top of the stack. Used by "single" parsing mode to echo expressions.
     *
     * Pops: the value to print
     */
    PRINT_EXPR(0, 1, 0),
    /**
     * Creates annotations dict in locals
     */
    SETUP_ANNOTATIONS(0, 0, 0),

    /**
     * Determines if a python object is a sequence.
     */
    MATCH_SEQUENCE(0, 0, 1),

    /**
     * Determines if a python object is a mapping.
     */
    MATCH_MAPPING(0, 0, 1),

    /**
     * Determines if a python object is of a particular type.
     */
    MATCH_CLASS(1, 3, 2),

    /**
     * Matches the keys (stack top) in a dict (stack second). On successful match pushes the values
     * and True, otherwise None and False.
     */
    MATCH_KEYS(0, 2, 4),

    /**
     * Creates a copy of a dict (stack second) without elements matching a tuple of keys (stack
     * top).
     */
    COPY_DICT_WITHOUT_KEYS(0, 1, 1),

    /**
     * Retrieves the length of a python object and stores it on top.
     */
    GET_LEN(0, 0, 1),

    // load bytecodes for special constants
    LOAD_NONE(0, 0, 1),
    LOAD_ELLIPSIS(0, 0, 1),
    LOAD_TRUE(0, 0, 1),
    LOAD_FALSE(0, 0, 1),
    /**
     * Loads signed byte from immediate operand.
     */
    LOAD_BYTE(1, 0, 1),
    /**
     * Loads {@code int} from primitiveConstants array indexed by the immediate operand.
     */
    LOAD_INT(1, 0, 1),
    /**
     * Loads {@code long} from primitiveConstants array indexed by the immediate operand.
     */
    LOAD_LONG(1, 0, 1),
    /**
     * Loads {@code double} from primitiveConstants array indexed by the immediate operand
     * (converted from long).
     */
    LOAD_DOUBLE(1, 0, 1),
    /**
     * Creates a {@link PInt} from a {@link BigInteger} in constants array indexed by the immediate
     * operand.
     */
    LOAD_BIGINT(1, 0, 1),
    /**
     * Currently the same as {@link #LOAD_CONST}.
     */
    LOAD_STRING(1, 0, 1),
    /**
     * Creates python {@code bytes} from a {@code byte[]} array in constants array indexed by the
     * immediate operand.
     */
    LOAD_BYTES(1, 0, 1),
    /**
     * Creates python {@code complex} from a {@code double[]} array of size 2 in constants array
     * indexed by the immediate operand.
     */
    LOAD_COMPLEX(1, 0, 1),
    /*
     * Creates a collection out of a Java array in constants array indexed by the immediate operand.
     * The second immediate operand determines the array type and kind, using values from {@link
     * CollectionBits}. The only allowed kinds are list and tuple.
     */
    LOAD_CONST_COLLECTION(2, 0, 1),

    // calling
    /**
     * Calls method on an object using an array as args. The receiver is taken from the first
     * element of the array. The method name is determined by the immediate operand which indexes
     * the names array ({@code co_names}).
     *
     * Pops: args ({@code Object[]} of size >= 1) and the method
     *
     * Pushes: call result
     */
    CALL_METHOD_VARARGS(0, 2, 1),
    /**
     * Calls method on an object using a number of stack args determined by the first immediate
     * operand.
     *
     * Pops: multiple arguments depending on the first immediate operand, then the method and the
     * receiver
     *
     * Pushes: call result
     */
    CALL_METHOD(1, (oparg, followingArgs, withJump) -> oparg + 2, 1),
    /**
     * Calls a callable using a number of stack args determined by the immediate operand.
     *
     * Pops: multiple arguments depending on the immediate operand (0 - 4), then the callable
     *
     * Pushes: call result
     */
    CALL_FUNCTION(1, (oparg, followingArgs, withJump) -> oparg + 1, 1),
    /**
     * Calls a comprehension function with a single iterator argument. Comprehension functions have
     * to always have the same call target at given calls site. The instruction makes use of this
     * fact and bypasses function object inline caching which would otherwise slow down warmup since
     * comprehensions functions are always created anew and thus the cache would always miss.
     *
     * Pops: iterator, then the function
     *
     * Pushes: call result
     */
    CALL_COMPREHENSION(0, 2, 1),
    /**
     * Calls a callable using an arguments array and keywords array.
     *
     * Pops: keyword args ({@code PKeyword[]}), then args ({@code Object[]}), then callable
     *
     * Pushes: call result
     */
    CALL_FUNCTION_KW(0, 3, 1),
    /**
     * Calls a callable using an arguments array. No keywords are passed.
     *
     * Pops: args ({@code Object[]}), then callable
     *
     * Pushes: call result
     */
    CALL_FUNCTION_VARARGS(0, 2, 1),

    // destructuring bytecodes
    /**
     * Unpacks an iterable into multiple stack items.
     *
     * Pops: iterable
     *
     * Pushed: unpacked items, the count is determined by the immediate operand
     */
    UNPACK_SEQUENCE(1, 1, (oparg, followingArgs, withJump) -> oparg),
    /**
     * Unpacks an iterable into multiple stack items with a star item that gets the rest. The first
     * immediate operand determines the count before the star item, the second determines the count
     * after.
     *
     * Pops: iterable
     *
     * Pushed: unpacked items (count = first operand), star item, unpacked items (count = second
     * operand)
     */
    UNPACK_EX(2, 1, (oparg, followingArgs, withJump) -> oparg + 1 + Byte.toUnsignedInt(followingArgs[0])),

    // jumps
    /**
     * Get next value from an iterator. If the iterable is exhausted, jump forward by the offset in
     * the immediate argument.
     *
     * Pops: iterator
     *
     * Pushes (only if not jumping): the iterator, then the next value
     */
    FOR_ITER(1, 1, (oparg, followingArgs, withJump) -> withJump ? 0 : 2),
    /**
     * Jump forward by the offset in the immediate operand.
     */
    JUMP_FORWARD(1, 0, 0),
    /**
     * Jump backward by the offset in the immediate operand. May trigger OSR compilation.
     */
    JUMP_BACKWARD(1, 0, 0),
    /**
     * Jump forward by the offset in the immediate operand if the top of the stack is false (in
     * Python sense).
     *
     * Pops (if not jumping): top of the stack
     */
    JUMP_IF_FALSE_OR_POP(3, (oparg, followingArgs, withJump) -> withJump ? 0 : 1, 0),
    /**
     * Jump forward by the offset in the immediate operand if the top of the stack is true (in
     * Python sense).
     *
     * Pops (if not jumping): top of the stack
     */
    JUMP_IF_TRUE_OR_POP(3, (oparg, followingArgs, withJump) -> withJump ? 0 : 1, 0),
    /**
     * Jump forward by the offset in the immediate operand if the top of the stack is false (in
     * Python sense).
     *
     * Pops: top of the stack
     */
    POP_AND_JUMP_IF_FALSE(3, 1, 0),
    /**
     * Jump forward by the offset in the immediate operand if the top of the stack is true (in
     * Python sense).
     *
     * Pops: top of the stack
     */
    POP_AND_JUMP_IF_TRUE(3, 1, 0),

    // making callables
    /**
     * Like {@link #LOAD_DEREF}, but loads the cell itself, not the contents.
     *
     * Pushes: the cell object
     */
    LOAD_CLOSURE(1, 0, 1),
    /**
     * Reduces multiple stack items into an array of cell objects.
     *
     * Pops: multiple cells (count = immediate argument)
     *
     * Pushes: cell object array ({@code PCell[]})
     */
    CLOSURE_FROM_STACK(1, (oparg, followingArgs, withJump) -> oparg, 1),
    /**
     * Creates a function object. The first immediate argument is an index to the constants array
     * that determines the {@link CodeUnit} object that will provide the function's code.
     *
     * Pops: The second immediate arguments contains flags (defined in {@link CodeUnit}) that
     * determine whether it will need to pop (in this order): closure, annotations, keyword only
     * defaults, defaults.
     *
     * Pushes: created function
     */
    MAKE_FUNCTION(2, (oparg, followingArgs, withJump) -> Integer.bitCount(followingArgs[0]), 1),

    // collection literals
    /**
     * Creates a collection from multiple elements from the stack. Collection type is determined by
     * {@link CollectionBits} in immediate operand.
     *
     * Pops: items for the collection (count = immediate argument)
     *
     * Pushes: new collection
     */
    COLLECTION_FROM_STACK(1, (oparg, followingArgs, withJump) -> CollectionBits.elementCount(oparg), 1),
    /**
     * Add multiple elements from the stack to the collection below them. Collection type is
     * determined by {@link CollectionBits} in immediate operand. Tuple is not supported.
     *
     * Pops: items to be added (count = immediate argument)
     */
    COLLECTION_ADD_STACK(1, (oparg, followingArgs, withJump) -> CollectionBits.elementCount(oparg) + 1, 1),
    /**
     * Concatenates two collection of the same type. Collection type is determined by
     * {@link CollectionBits} in immediate operand. Tuple is not supported.
     *
     * Pops: second collection, first collection
     *
     * Pushes: concatenated collection
     */
    COLLECTION_ADD_COLLECTION(1, 2, 1),
    /**
     * Converts collection to another type determined by {@link CollectionBits} in immediate
     * operand. The converted collection is expected to be an independent copy (they don't share
     * storage).
     *
     * Pops: original collection
     *
     * Pushes: converted collection
     */
    COLLECTION_FROM_COLLECTION(1, 1, 1),
    /**
     * Converts list to tuple by reusing the underlying storage.
     *
     * Pops: list
     *
     * Pushes: tuple
     */
    TUPLE_FROM_LIST(0, 1, 1),
    /**
     * Converts list to frozenset.
     *
     * Pops: list
     *
     * Pushes: frozenset
     */
    FROZENSET_FROM_LIST(0, 1, 1),
    /**
     * Adds an item to a collection that is multiple items deep under the top of the stack,
     * determined by the immediate argument.
     *
     * Pops: item to be added
     */
    ADD_TO_COLLECTION(1, (oparg, followingArgs, withJump) -> CollectionBits.collectionKind(oparg) == CollectionBits.KIND_DICT ? 2 : 1, 0),
    /**
     * Like {@link #COLLECTION_ADD_COLLECTION} for dicts, but with checks for duplicate keys
     * necessary for keyword arguments merge. Note it works with dicts. Keyword arrays need to be
     * converted to dicts first.
     */
    KWARGS_DICT_MERGE(0, 2, 1),
    /**
     * Create a single {@link PKeyword} object. The name is determined by the immediate operand
     * which indexes the names array ({@code co_names})
     *
     * Pops: keyword value
     *
     * Pushes: keyword object
     */
    MAKE_KEYWORD(1, 1, 1),

    // exceptions
    /**
     * Jump forward by the offset in the immediate argument if the exception doesn't match the
     * expected type. The exception object is {@link PException}, not a python exception.
     *
     * Pops: expected type, then exception
     *
     * Pushes (if jumping): the exception
     */
    MATCH_EXC_OR_JUMP(3, 2, 1),
    /**
     * Save the current exception state on the stack and set it to the exception on the stack. The
     * exception object is {@link PException}, not a python exception. The exception is pushed back
     * to the top.
     *
     * Pops: the exception
     *
     * Pushes: the saved exception state, the exception
     */
    PUSH_EXC_INFO(0, 1, 2),
    /**
     * Sets the current exception state to the saved state (by {@link #PUSH_EXC_INFO}) on the stack
     * and pop it.
     *
     * Pops: save exception state
     */
    POP_EXCEPT(0, 1, 0),
    /**
     * Restore exception state and reraise exception.
     *
     * Pops: exception to reraise, then saved exception state
     */
    END_EXC_HANDLER(0, 2, 0),
    /**
     * Gets the python-level exception object from a {@link PException}.
     *
     * Pops: a {@link PException} Pushes: python exception
     */
    UNWRAP_EXC(0, 1, 1),

    // generators
    /**
     * Yield value from the stack to the caller. Saves execution state. The generator will resume at
     * the next instruction.
     *
     * Pops: yielded value
     */
    YIELD_VALUE(0, 1, 0),
    /**
     * Wrap value from the stack in a {@link PAsyncGenWrappedValue}. CPython 3.11 opcode, used here
     * to avoid a runtime check
     *
     * Pops: an object Pushes: async_generator_wrapped_value
     */
    ASYNCGEN_WRAP(0, 1, 1),
    /**
     * Resume after yield. Will raise exception passed by {@code throw} if any.
     *
     * Pushes: value received from {@code send} or {@code None}.
     */
    RESUME_YIELD(0, 0, 1),
    /**
     * Send value into a generator. Jumps forward by the offset in the immediate argument if the
     * generator is exhausted. Used to implement {@code yield from}.
     *
     * Pops: value to be sent, then generator
     *
     * Pushes (if not jumping): the generator, then the yielded value
     *
     * Pushes (if jumping): the generator return value
     */
    SEND(1, 2, (oparg, followingArgs, withJump) -> withJump ? 1 : 2),
    /**
     * Exception handler for forwarding {@code throw} calls into {@code yield from}.
     *
     * Pops: exception, then the generator
     *
     * Pushes (if not jumping): the generator, then the yielded value
     *
     * Pushes (if jumping): the generator return value
     */
    THROW(1, 2, (oparg, followingArgs, withJump) -> withJump ? 1 : 2),
    /**
     * Exception handler for async for loops. If the current exception is StopAsyncIteration, handle
     * it, otherwise, reraise.
     *
     * Pops: exception, then the anext coroutine, then the async iterator
     */
    END_ASYNC_FOR(0, 3, 0),

    // with statements
    /**
     * Enter a context manager and save data for its exit.
     *
     * Pops: the context manager
     *
     * Pushes: the context manager, then maybe-bound {@code __exit__}, then the result of
     * {@code __enter__}
     */
    SETUP_WITH(0, 1, 3),
    /**
     * Run the exit handler of a context manager and reraise if necessary.
     *
     * Pops: exception or {@code None}, then maybe-bound {@code __exit__}, then the context manager
     */
    EXIT_WITH(0, 3, 0),
    /**
     * Enter a context manager and save data for its exit
     *
     * Pops: the context manager
     *
     * Pushes: the context manager, then the maybe-bound async function {@code __aexit__}, then the
     * awaitable returned by {@code __aenter__}
     */
    SETUP_AWITH(0, 1, 3),
    /**
     * Run the exit handler of a context manager
     *
     * Pops: exception or {@code None}, then maybe-bound {@code __aexit__}, then the context manager
     *
     * Pushes: the exception or {@code None}, then the awaitable returned by {@code __aexit__}
     */
    GET_AEXIT_CORO(0, 3, 2),
    /**
     * Reraise the exception passed to {@code __aexit__} if appropriate
     *
     * Pops: The result of awaiting {@code __aexit__}, then the exception
     */
    EXIT_AWITH(0, 2, 0),

    /*
     * Quickened bytecodes
     */
    LOAD_TRUE_O(LOAD_TRUE, 0, QuickeningTypes.OBJECT),
    LOAD_TRUE_B(LOAD_TRUE, 0, QuickeningTypes.BOOLEAN, LOAD_TRUE_O),
    LOAD_FALSE_O(LOAD_FALSE, 0, QuickeningTypes.OBJECT),
    LOAD_FALSE_B(LOAD_FALSE, 0, QuickeningTypes.BOOLEAN, LOAD_FALSE_O),
    LOAD_BYTE_O(LOAD_BYTE, 0, QuickeningTypes.OBJECT),
    LOAD_BYTE_I(LOAD_BYTE, 0, QuickeningTypes.INT, LOAD_BYTE_O),
    LOAD_INT_O(LOAD_INT, 0, QuickeningTypes.OBJECT),
    LOAD_INT_I(LOAD_INT, 0, QuickeningTypes.INT, LOAD_INT_O),
    LOAD_LONG_O(LOAD_LONG, 0, QuickeningTypes.OBJECT),
    LOAD_LONG_L(LOAD_LONG, 0, QuickeningTypes.LONG, LOAD_LONG_O),
    LOAD_DOUBLE_O(LOAD_DOUBLE, 0, QuickeningTypes.OBJECT),
    LOAD_DOUBLE_D(LOAD_DOUBLE, 0, QuickeningTypes.DOUBLE, LOAD_DOUBLE_O),
    LOAD_FAST_O(LOAD_FAST, 0, QuickeningTypes.OBJECT),
    LOAD_FAST_I_BOX(LOAD_FAST, 0, QuickeningTypes.OBJECT),
    LOAD_FAST_I(LOAD_FAST, 0, QuickeningTypes.INT, LOAD_FAST_I_BOX),
    LOAD_FAST_L_BOX(LOAD_FAST, 0, QuickeningTypes.OBJECT),
    LOAD_FAST_L(LOAD_FAST, 0, QuickeningTypes.LONG, LOAD_FAST_L_BOX),
    LOAD_FAST_D_BOX(LOAD_FAST, 0, QuickeningTypes.OBJECT),
    LOAD_FAST_D(LOAD_FAST, 0, QuickeningTypes.DOUBLE, LOAD_FAST_D_BOX),
    LOAD_FAST_B_BOX(LOAD_FAST, 0, QuickeningTypes.OBJECT),
    LOAD_FAST_B(LOAD_FAST, 0, QuickeningTypes.BOOLEAN, LOAD_FAST_B_BOX),
    STORE_FAST_O(STORE_FAST, QuickeningTypes.OBJECT, 0),
    STORE_FAST_UNBOX_I(STORE_FAST, QuickeningTypes.OBJECT, 0),
    STORE_FAST_BOXED_I(STORE_FAST, QuickeningTypes.OBJECT, 0),
    STORE_FAST_I(STORE_FAST, QuickeningTypes.INT, 0),
    STORE_FAST_UNBOX_L(STORE_FAST, QuickeningTypes.OBJECT, 0),
    STORE_FAST_BOXED_L(STORE_FAST, QuickeningTypes.OBJECT, 0),
    STORE_FAST_L(STORE_FAST, QuickeningTypes.LONG, 0),
    STORE_FAST_UNBOX_D(STORE_FAST, QuickeningTypes.OBJECT, 0),
    STORE_FAST_BOXED_D(STORE_FAST, QuickeningTypes.OBJECT, 0),
    STORE_FAST_D(STORE_FAST, QuickeningTypes.DOUBLE, 0),
    STORE_FAST_UNBOX_B(STORE_FAST, QuickeningTypes.OBJECT, 0),
    STORE_FAST_BOXED_B(STORE_FAST, QuickeningTypes.OBJECT, 0),
    STORE_FAST_B(STORE_FAST, QuickeningTypes.BOOLEAN, 0),
    UNARY_OP_O_O(UNARY_OP, QuickeningTypes.OBJECT, QuickeningTypes.OBJECT),
    UNARY_OP_I_O(UNARY_OP, QuickeningTypes.INT, QuickeningTypes.OBJECT),
    UNARY_OP_I_I(UNARY_OP, QuickeningTypes.INT, QuickeningTypes.INT, UNARY_OP_I_O),
    UNARY_OP_D_O(UNARY_OP, QuickeningTypes.DOUBLE, QuickeningTypes.OBJECT),
    UNARY_OP_D_D(UNARY_OP, QuickeningTypes.DOUBLE, QuickeningTypes.DOUBLE, UNARY_OP_D_O),
    UNARY_OP_B_O(UNARY_OP, QuickeningTypes.BOOLEAN, QuickeningTypes.OBJECT),
    UNARY_OP_B_B(UNARY_OP, QuickeningTypes.BOOLEAN, QuickeningTypes.BOOLEAN, UNARY_OP_I_O),
    BINARY_OP_OO_O(BINARY_OP, QuickeningTypes.OBJECT, QuickeningTypes.OBJECT),
    BINARY_OP_II_O(BINARY_OP, QuickeningTypes.INT, QuickeningTypes.OBJECT),
    BINARY_OP_II_I(BINARY_OP, QuickeningTypes.INT, QuickeningTypes.INT, BINARY_OP_II_O),
    BINARY_OP_II_B(BINARY_OP, QuickeningTypes.INT, QuickeningTypes.BOOLEAN, BINARY_OP_II_O),
    BINARY_OP_DD_O(BINARY_OP, QuickeningTypes.DOUBLE, QuickeningTypes.OBJECT),
    BINARY_OP_DD_D(BINARY_OP, QuickeningTypes.DOUBLE, QuickeningTypes.DOUBLE, BINARY_OP_DD_O),
    BINARY_OP_DD_B(BINARY_OP, QuickeningTypes.DOUBLE, QuickeningTypes.BOOLEAN, BINARY_OP_DD_O),
    FOR_ITER_O(FOR_ITER, 0, QuickeningTypes.OBJECT),
    FOR_ITER_I(FOR_ITER, 0, QuickeningTypes.INT, FOR_ITER_O),
    BINARY_SUBSCR_SEQ_O_O(BINARY_SUBSCR, QuickeningTypes.OBJECT, QuickeningTypes.OBJECT),
    BINARY_SUBSCR_SEQ_I_O(BINARY_SUBSCR, QuickeningTypes.INT, QuickeningTypes.OBJECT),
    BINARY_SUBSCR_SEQ_I_I(BINARY_SUBSCR, QuickeningTypes.INT, QuickeningTypes.INT, BINARY_SUBSCR_SEQ_I_O),
    BINARY_SUBSCR_SEQ_I_D(BINARY_SUBSCR, QuickeningTypes.INT, QuickeningTypes.DOUBLE, BINARY_SUBSCR_SEQ_I_O),
    STORE_SUBSCR_OOO(STORE_SUBSCR, QuickeningTypes.OBJECT, 0),
    /*
     * The index and collection inputs are handled manually in the compiler, the input type
     * parameter is used just for the value type.
     */
    STORE_SUBSCR_SEQ_IOO(STORE_SUBSCR, QuickeningTypes.OBJECT, 0),
    STORE_SUBSCR_SEQ_IIO(STORE_SUBSCR, QuickeningTypes.INT, 0),
    STORE_SUBSCR_SEQ_IDO(STORE_SUBSCR, QuickeningTypes.DOUBLE, 0),
    POP_AND_JUMP_IF_FALSE_O(POP_AND_JUMP_IF_FALSE, QuickeningTypes.OBJECT, 0),
    POP_AND_JUMP_IF_FALSE_B(POP_AND_JUMP_IF_FALSE, QuickeningTypes.BOOLEAN, 0, POP_AND_JUMP_IF_FALSE_O),
    POP_AND_JUMP_IF_TRUE_O(POP_AND_JUMP_IF_TRUE, QuickeningTypes.OBJECT, 0),
    POP_AND_JUMP_IF_TRUE_B(POP_AND_JUMP_IF_TRUE, QuickeningTypes.BOOLEAN, 0, POP_AND_JUMP_IF_TRUE_O);

    public static final class CollectionBits {
        public static final int KIND_MASK = 0b00011111;
        public static final int KIND_LIST = 0b00100000;
        public static final int KIND_TUPLE = 0b01000000;
        public static final int KIND_SET = 0b01100000;
        public static final int KIND_DICT = 0b10000000;
        public static final int KIND_KWORDS = 0b10100000;
        public static final int KIND_OBJECT = 0b11000000;

        public static final byte ELEMENT_INT = 1;
        public static final byte ELEMENT_LONG = 2;
        public static final byte ELEMENT_BOOLEAN = 3;
        public static final byte ELEMENT_DOUBLE = 4;
        public static final byte ELEMENT_OBJECT = 5;

        public static int elementCount(int oparg) {
            return oparg & KIND_MASK;
        }

        public static int elementType(int oparg) {
            return oparg & KIND_MASK;
        }

        public static int collectionKind(int oparg) {
            return oparg & ~KIND_MASK;
        }
    }

    public static final class MakeFunctionFlags {
        public static final int HAS_DEFAULTS = 0x1;
        public static final int HAS_KWONLY_DEFAULTS = 0x2;
        public static final int HAS_ANNOTATIONS = 0x04;
        public static final int HAS_CLOSURE = 0x08;
    }

    private static final OpCodes[] VALUES = new OpCodes[values().length];

    public static OpCodes fromOpCode(byte opcode) {
        return VALUES[Byte.toUnsignedInt(opcode)];
    }

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
    public final OpCodes quickens;
    public final OpCodes generalizesTo;
    private byte quickenInputTypes;
    private byte quickenOutputTypes;

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
        this.quickens = null;
        this.generalizesTo = null;
    }

    OpCodes(OpCodes quickens, int inputType, int outputType) {
        this(quickens, inputType, outputType, null);
    }

    OpCodes(OpCodes quickens, int inputType, int outputType, OpCodes generalizesTo) {
        this.argLength = quickens.argLength;
        this.consumesStackItems = quickens.consumesStackItems;
        this.producesStackItems = quickens.producesStackItems;
        this.generalizesTo = generalizesTo;
        this.quickens = quickens;
        quickens.quickenInputTypes |= (byte) inputType;
        quickens.quickenOutputTypes |= (byte) outputType;
    }

    public byte canQuickenInputTypes() {
        return quickenInputTypes;
    }

    public byte canQuickenOutputTypes() {
        return quickenOutputTypes;
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

    public int getNextBci(int bci, int oparg, boolean withJump) {
        if (this.quickens != null) {
            return this.quickens.getNextBci(bci, oparg, withJump);
        }
        switch (this) {
            case JUMP_FORWARD:
                return bci + oparg;
            case JUMP_BACKWARD:
                return bci - oparg;
            case POP_AND_JUMP_IF_FALSE:
            case POP_AND_JUMP_IF_TRUE:
            case JUMP_IF_FALSE_OR_POP:
            case JUMP_IF_TRUE_OR_POP:
            case FOR_ITER:
            case MATCH_EXC_OR_JUMP:
                return withJump ? bci + oparg : bci + length();
            case RETURN_VALUE:
            case RAISE_VARARGS:
            case END_EXC_HANDLER:
                return -1;
            default:
                return bci + length();
        }
    }
}
