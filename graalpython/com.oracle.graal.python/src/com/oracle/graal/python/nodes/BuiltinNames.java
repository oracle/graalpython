/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

public abstract class BuiltinNames {
    // special strings
    public static final String LAMBDA_NAME = "<lambda>";

    // special arg names
    public static final String SELF = "self";

    // cpython internals
    public static final String BREAKPOINT = "breakpoint";
    public static final String MODULE = "module";
    public static final String __BUILD_CLASS__ = "__build_class__";
    public static final String __MAIN__ = "__main__";
    public static final String __BUILTINS__ = "__builtins__";
    public static final String __DEBUG__ = "__debug__";

    // sys
    public static final String DISPLAYHOOK = "displayhook";
    public static final String BREAKPOINTHOOK = "breakpointhook";
    public static final String EXCEPTHOOK = "excepthook";
    public static final String UNRAISABLEHOOK = "unraisablehook";
    public static final String LAST_TYPE = "last_type";
    public static final String LAST_VALUE = "last_value";
    public static final String LAST_TRACEBACK = "last_traceback";

    // builtin functions
    public static final String ABS = "abs";
    public static final String DICT = "dict";
    public static final String HELP = "help";
    public static final String MIN = "min";
    public static final String SETATTR = "setattr";
    public static final String ALL = "all";
    public static final String DIR = "dir";
    public static final String HEX = "hex";
    public static final String NEXT = "next";
    public static final String SLICE = "slice";
    public static final String ANY = "any";
    public static final String DIVMOD = "divmod";
    public static final String ID = "id";
    public static final String OBJECT = "object";
    public static final String FOREIGN = "foreign";
    public static final String SORTED = "sorted";
    public static final String ASCII = "ascii";
    public static final String CEIL = "ceil";
    public static final String ENUMERATE = "enumerate";
    public static final String INPUT = "input";
    public static final String OCT = "oct";
    public static final String STATICMETHOD = "staticmethod";
    public static final String BIN = "bin";
    public static final String EVAL = "eval";
    public static final String INT = "int";
    public static final String OPEN = "open";
    public static final String STR = "str";
    public static final String BOOL = "bool";
    public static final String EXEC = "exec";
    public static final String ISINSTANCE = "isinstance";
    public static final String ORD = "ord";
    public static final String SUM = "sum";
    public static final String BYTEARRAY = "bytearray";
    public static final String FILTER = "filter";
    public static final String ISSUBCLASS = "issubclass";
    public static final String POW = "pow";
    public static final String SUPER = "super";
    public static final String BYTES = "bytes";
    public static final String FLOAT = "float";
    public static final String ITER = "iter";
    public static final String PRINT = "print";
    public static final String TUPLE = "tuple";
    public static final String CALLABLE = "callable";
    public static final String FORMAT = "format";
    public static final String LEN = "len";
    public static final String PROPERTY = "property";
    public static final String TYPE = "type";
    public static final String CHR = "chr";
    public static final String FROZENSET = "frozenset";
    public static final String LIST = "list";
    public static final String RANGE = "range";
    public static final String VARS = "vars";
    public static final String CLASSMETHOD = "classmethod";
    public static final String GETATTR = "getattr";
    public static final String LOCALS = "locals";
    public static final String REPR = "repr";
    public static final String ZIP = "zip";
    public static final String COMPILE = "compile";
    public static final String GLOBALS = "globals";
    public static final String MAP = "map";
    public static final String REVERSED = "reversed";
    public static final String __IMPORT__ = "__import__";
    public static final String COMPLEX = "complex";
    public static final String HASATTR = "hasattr";
    public static final String MAX = "max";
    public static final String ROUND = "round";
    public static final String DELATTR = "delattr";
    public static final String HASH = "hash";
    public static final String MEMORYVIEW = "memoryview";
    public static final String SET = "set";
    public static final String BUILTINS = "builtins";
    public static final String __GRAALPYTHON__ = "__graalpython__";

    public static final String DICT_KEYITERATOR = "dict_keyiterator";
    public static final String DICT_VALUEITERATOR = "dict_valueiterator";
    public static final String DICT_ITEMITERATOR = "dict_itemiterator";
    public static final String DICT_KEYS = "dict_keys";
    public static final String DICT_ITEMS = "dict_items";
    public static final String DICT_VALUES = "dict_values";
}
