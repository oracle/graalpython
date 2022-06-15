/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.strings.TruffleString;

import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

public abstract class BuiltinNames {
    // special strings
    public static final String J_LAMBDA_NAME = "<lambda>";

    // special arg names
    public static final String J_SELF = "self";
    public static final TruffleString T_SELF = tsLiteral(J_SELF);

    // cpython internals
    public static final String J_BREAKPOINT = "breakpoint";
    public static final TruffleString T_BREAKPOINT = tsLiteral(J_BREAKPOINT);

    public static final String J_MODULE = "module";
    public static final TruffleString T_MODULE = tsLiteral(J_MODULE);

    public static final String J___BUILD_CLASS__ = "__build_class__";
    public static final TruffleString T___BUILD_CLASS__ = tsLiteral(J___BUILD_CLASS__);

    public static final String J___MAIN__ = "__main__";
    public static final TruffleString T___MAIN__ = tsLiteral(J___MAIN__);

    public static final String J___BUILTINS__ = "__builtins__";
    public static final TruffleString T___BUILTINS__ = tsLiteral(J___BUILTINS__);

    public static final String J___DEBUG__ = "__debug__";
    public static final TruffleString T___DEBUG__ = tsLiteral(J___DEBUG__);

    public static final String J___FUTURE__ = "__future__";

    // sys
    public static final String J_TRACEBACKLIMIT = "tracebacklimit";
    public static final TruffleString T_TRACEBACKLIMIT = tsLiteral(J_TRACEBACKLIMIT);

    public static final String J_DISPLAYHOOK = "displayhook";
    public static final TruffleString T_DISPLAYHOOK = tsLiteral(J_DISPLAYHOOK);

    public static final String J___DISPLAYHOOK__ = "__displayhook__";
    public static final TruffleString T___DISPLAYHOOK__ = tsLiteral(J___DISPLAYHOOK__);

    public static final String J_BREAKPOINTHOOK = "breakpointhook";
    public static final TruffleString T_BREAKPOINTHOOK = tsLiteral(J_BREAKPOINTHOOK);

    public static final String J___BREAKPOINTHOOK__ = "__breakpointhook__";
    public static final TruffleString T___BREAKPOINTHOOK__ = tsLiteral(J___BREAKPOINTHOOK__);

    public static final String J_EXCEPTHOOK = "excepthook";
    public static final TruffleString T_EXCEPTHOOK = tsLiteral(J_EXCEPTHOOK);

    public static final String J___EXCEPTHOOK__ = "__excepthook__";
    public static final TruffleString T___EXCEPTHOOK__ = tsLiteral(J___EXCEPTHOOK__);

    public static final String J_UNRAISABLEHOOK = "unraisablehook";
    public static final TruffleString T_UNRAISABLEHOOK = tsLiteral(J_UNRAISABLEHOOK);

    public static final String J___UNRAISABLEHOOK__ = "__unraisablehook__";
    public static final TruffleString T___UNRAISABLEHOOK__ = tsLiteral(J___UNRAISABLEHOOK__);

    public static final String J_LAST_TYPE = "last_type";
    public static final TruffleString T_LAST_TYPE = tsLiteral(J_LAST_TYPE);

    public static final String J_LAST_VALUE = "last_value";
    public static final TruffleString T_LAST_VALUE = tsLiteral(J_LAST_VALUE);

    public static final String J_LAST_TRACEBACK = "last_traceback";
    public static final TruffleString T_LAST_TRACEBACK = tsLiteral(J_LAST_TRACEBACK);

    public static final String J___STDERR__ = "__stderr__";
    public static final TruffleString T___STDERR__ = tsLiteral(J___STDERR__);

    public static final String J_STDERR = "stderr";
    public static final TruffleString T_STDERR = tsLiteral(J_STDERR);

    public static final String J___STDIN__ = "__stdin__";
    public static final TruffleString T___STDIN__ = tsLiteral(J___STDIN__);

    public static final String J_STDIN = "stdin";
    public static final TruffleString T_STDIN = tsLiteral(J_STDIN);

    public static final String J___STDOUT__ = "__stdout__";
    public static final TruffleString T___STDOUT__ = tsLiteral(J___STDOUT__);

    public static final String J_STDOUT = "stdout";
    public static final TruffleString T_STDOUT = tsLiteral(J_STDOUT);

    public static final String J_PYTHONBREAKPOINT = "PYTHONBREAKPOINT";
    public static final TruffleString T_PYTHONBREAKPOINT = tsLiteral(J_PYTHONBREAKPOINT);

    public static final String J_EXIT = "exit";
    public static final TruffleString T_EXIT = tsLiteral(J_EXIT);

    public static final String J_MODULES = "modules";
    public static final TruffleString T_MODULES = tsLiteral(J_MODULES);

    // built-in functions
    public static final String J_ABS = "abs";
    public static final TruffleString T_ABS = tsLiteral(J_ABS);

    public static final String J_DICT = "dict";
    public static final TruffleString T_DICT = tsLiteral(J_DICT);

    public static final String J_DEFAULTDICT = "defaultdict";

    public static final String J_PARTIAL = "partial";

    public static final String J_TUPLE_GETTER = "_tuplegetter";

    public static final String J_DEQUE = "deque";
    public static final TruffleString T_DEQUE = tsLiteral(J_DEQUE);

    public static final String J_DEQUE_ITER = "_deque_iterator";

    public static final String J_DEQUE_REV_ITER = "_deque_reverse_iterator";

    public static final String J_HELP = "help";

    public static final String J_MIN = "min";
    public static final TruffleString T_MIN = tsLiteral(J_MIN);

    public static final String J_SETATTR = "setattr";

    public static final String J_ALL = "all";

    public static final String J_DIR = "dir";

    public static final String J_HEX = "hex";

    public static final String J_NEXT = "next";
    public static final TruffleString T_NEXT = tsLiteral(J_NEXT);

    public static final String J_SLICE = "slice";

    public static final String J_ANY = "any";

    public static final String J_DIVMOD = "divmod";

    public static final String J_ID = "id";

    public static final String J_OBJECT = "object";

    public static final String J_FOREIGN = "foreign";

    public static final String J_SORTED = "sorted";

    public static final String J_ASCII = "ascii";
    public static final TruffleString T_ASCII = tsLiteral(J_ASCII);

    public static final String J_CEIL = "ceil";

    public static final String J_ENUMERATE = "enumerate";

    public static final String J_INPUT = "input";

    public static final String J_OCT = "oct";

    public static final String J_STATICMETHOD = "staticmethod";

    public static final String J_BIN = "bin";

    public static final String J_EVAL = "eval";
    public static final TruffleString T_EVAL = tsLiteral(J_EVAL);

    public static final String J_INT = "int";
    public static final TruffleString T_INT = tsLiteral(J_INT);

    public static final String J_OPEN = "open";

    public static final String J_STR = "str";
    public static final TruffleString T_STR = tsLiteral(J_STR);

    public static final String J_BOOL = "bool";

    public static final String J_EXEC = "exec";
    public static final TruffleString T_EXEC = tsLiteral(J_EXEC);

    public static final String J_ISINSTANCE = "isinstance";
    public static final TruffleString T_ISINSTANCE = tsLiteral(J_ISINSTANCE);

    public static final String J_ORD = "ord";

    public static final String J_SUM = "sum";

    public static final String J_BYTEARRAY = "bytearray";

    public static final String J_FILTER = "filter";

    public static final String J_ISSUBCLASS = "issubclass";
    public static final TruffleString T_ISSUBCLASS = tsLiteral(J_ISSUBCLASS);

    public static final String J_POW = "pow";

    public static final String J_SUPER = "super";

    public static final String J_BYTES = "bytes";
    public static final TruffleString T_BYTES = tsLiteral(J_BYTES);

    public static final String J_FLOAT = "float";
    public static final TruffleString T_FLOAT = tsLiteral(J_FLOAT);

    public static final String J_ITER = "iter";
    public static final TruffleString T_ITER = tsLiteral(J_ITER);

    public static final String J_PRINT = "print";
    public static final TruffleString T_PRINT = tsLiteral(J_PRINT);

    public static final String J_TUPLE = "tuple";
    public static final TruffleString T_TUPLE = tsLiteral(J_TUPLE);

    public static final String J_CALLABLE = "callable";
    public static final TruffleString T_CALLABLE = tsLiteral(J_CALLABLE);

    public static final String J_ENCODE = "encode";
    public static final TruffleString T_ENCODE = tsLiteral(J_ENCODE);

    public static final String J_DECODE = "decode";
    public static final TruffleString T_DECODE = tsLiteral(J_DECODE);

    public static final String J_FORMAT = "format";
    public static final TruffleString T_FORMAT = tsLiteral(J_FORMAT);

    public static final String J_FORMAT_MAP = "format_map";

    public static final String J_FORMATTER_PARSER = "formatter_parser";

    public static final String J_FORMATTER_FIELD_NAME_SPLIT = "formatter_field_name_split";

    public static final String J_LEN = "len";
    public static final TruffleString T_LEN = tsLiteral(J_LEN);

    public static final String J_PROPERTY = "property";

    public static final String J_TYPE = "type";
    public static final TruffleString T_TYPE = tsLiteral(J_TYPE);

    public static final String J_CHR = "chr";
    public static final TruffleString T_CHR = tsLiteral(J_CHR);

    public static final String J_FROZENSET = "frozenset";
    public static final TruffleString T_FROZENSET = tsLiteral(J_FROZENSET);

    public static final String J_LIST = "list";
    public static final TruffleString T_LIST = tsLiteral(J_LIST);

    public static final String J_RANGE = "range";
    public static final TruffleString T_RANGE = tsLiteral(J_RANGE);

    public static final String J_VARS = "vars";

    public static final String J_CLASSMETHOD = "classmethod";

    public static final String J_INSTANCEMETHOD = "instancemethod";

    public static final String J_GETATTR = "getattr";
    public static final TruffleString T_GETATTR = tsLiteral(J_GETATTR);

    public static final String J_LOCALS = "locals";
    public static final TruffleString T_LOCALS = tsLiteral(J_LOCALS);

    public static final String J_REPR = "repr";
    public static final TruffleString T_REPR = tsLiteral(J_REPR);

    public static final String J_ZIP = "zip";

    public static final String J_COMPILE = "compile";
    public static final TruffleString T_COMPILE = tsLiteral(J_COMPILE);

    public static final String J_GLOBALS = "globals";
    public static final TruffleString T_GLOBALS = tsLiteral(J_GLOBALS);

    public static final String J_MAP = "map";
    public static final TruffleString T_MAP = tsLiteral(J_MAP);

    public static final String J_REVERSED = "reversed";

    public static final String J___IMPORT__ = "__import__";
    public static final TruffleString T___IMPORT__ = tsLiteral(J___IMPORT__);

    public static final String J_COMPLEX = "complex";

    public static final String J_HASATTR = "hasattr";

    public static final String J_MAX = "max";

    public static final String J_ROUND = "round";

    public static final String J_DELATTR = "delattr";

    public static final String J_HASH = "hash";
    public static final TruffleString T_HASH = tsLiteral(J_HASH);

    public static final String J_MEMORYVIEW = "memoryview";

    public static final String J_SET = "set";
    public static final TruffleString T_SET = tsLiteral(J_SET);

    public static final String J_BUILTINS = "builtins";
    public static final TruffleString T_BUILTINS = tsLiteral(J_BUILTINS);

    public static final String J___GRAALPYTHON__ = "__graalpython__";
    public static final TruffleString T___GRAALPYTHON__ = tsLiteral(J___GRAALPYTHON__);

    public static final String J__CODECS = "_codecs";
    public static final TruffleString T__CODECS = tsLiteral(J__CODECS);

    public static final String J__STRING = "_string";
    public static final TruffleString T__STRING = tsLiteral(J__STRING);

    public static final String J__CODECS_TRUFFLE = "_codecs_truffle";
    public static final TruffleString T__CODECS_TRUFFLE = tsLiteral(J__CODECS_TRUFFLE);

    public static final String J_GETSET_DESCRIPTOR = "getset_descriptor";
    public static final TruffleString T_GETSET_DESCRIPTOR = tsLiteral(J_GETSET_DESCRIPTOR);

    public static final String J_MEMBER_DESCRIPTOR = "member_descriptor";
    public static final TruffleString T_MEMBER_DESCRIPTOR = tsLiteral(J_MEMBER_DESCRIPTOR);

    public static final String J_WRAPPER_DESCRIPTOR = "wrapper_descriptor";
    public static final TruffleString T_WRAPPER_DESCRIPTOR = tsLiteral(J_WRAPPER_DESCRIPTOR);

    public static final String J_SIMPLE_QUEUE = "SimpleQueue";

    public static final String J_EMPTY_CLASS_NAME = "Empty";
    public static final TruffleString T_EMPTY_CLASS_NAME = tsLiteral(J_EMPTY_CLASS_NAME);

    public static final String J__CONTEXTVARS = "_contextvars";

    public static final String J_THREADING = "threading";
    public static final TruffleString T_THREADING = tsLiteral(J_THREADING);

    public static final String J_DICT_KEYITERATOR = "dict_keyiterator";
    public static final String J_DICT_VALUEITERATOR = "dict_valueiterator";
    public static final String J_DICT_ITEMITERATOR = "dict_itemiterator";
    public static final String J_DICT_REVERSE_KEYITERATOR = "dict_reversekeyiterator";
    public static final String J_DICT_REVERSE_VALUEITERATOR = "dict_reversevalueiterator";
    public static final String J_DICT_REVERSE_ITEMITERATOR = "dict_reverseitemiterator";
    public static final String J_DICT_KEYS = "dict_keys";
    public static final String J_DICT_ITEMS = "dict_items";
    public static final String J_DICT_VALUES = "dict_values";

    public static final String J_SYS = "sys";
    public static final TruffleString T_SYS = tsLiteral(J_SYS);

    public static final String J__SIGNAL = "_signal";
    public static final TruffleString T__SIGNAL = tsLiteral(J__SIGNAL);

    public static final String J__WEAKREF = "_weakref";
    public static final TruffleString T__WEAKREF = tsLiteral(J__WEAKREF);

    public static final String J__WARNINGS = "_warnings";
    public static final TruffleString T__WARNINGS = tsLiteral(J__WARNINGS);

    public static final String J_POSIX = "posix";
    public static final TruffleString T_POSIX = tsLiteral(J_POSIX);

    public static final String J_ARRAY = "array";
    public static final TruffleString T_ARRAY = tsLiteral(J_ARRAY);

    public static final String J__CTYPES = "_ctypes";
    public static final TruffleString T__CTYPES = tsLiteral(J__CTYPES);

    public static final String J__SOCKET = "_socket";
    public static final TruffleString T__SOCKET = tsLiteral(J__SOCKET);

    public static final String J__THREAD = "_thread";
    public static final TruffleString T__THREAD = tsLiteral(J__THREAD);

    public static final String J__SSL = "_ssl";
    public static final TruffleString T__SSL = tsLiteral(J__SSL);

    public static final String J__SYSCONFIG = "_sysconfig";
    public static final TruffleString T__SYSCONFIG = tsLiteral(J__SYSCONFIG);

    public static final String J_READLINE = "readline";
    public static final TruffleString T_READLINE = tsLiteral(J_READLINE);

    public static final String J__STRUCT = "_struct";
    public static final TruffleString T__STRUCT = tsLiteral(J__STRUCT);

    public static final String J_ENDSWITH = "endswith";
    public static final TruffleString T_ENDSWITH = tsLiteral(J_ENDSWITH);

    public static final String J_STARTSWITH = "startswith";
    public static final TruffleString T_STARTSWITH = tsLiteral(J_STARTSWITH);

    public static final String J_NOT_IMPLEMENTED = "NotImplemented";
    public static final TruffleString T_NOT_IMPLEMENTED = tsLiteral(J_NOT_IMPLEMENTED);

    // function names
    public static final String J_ADD = "add";
    public static final TruffleString T_ADD = tsLiteral(J_ADD);

    public static final String J_APPEND = "append";
    public static final TruffleString T_APPEND = tsLiteral(J_APPEND);

    public static final String J_EXTEND = "extend";
    public static final TruffleString T_EXTEND = tsLiteral(J_EXTEND);

    // built-in modules
    public static final String BUILTINS = "builtins";
    public static final String __GRAALPYTHON__ = "__graalpython__";
    public static final String _CODECS = "_codecs";
    public static final String _CODECS_TRUFFLE = "_codecs_truffle";
    public static final String _STRING = "_string";
    public static final String CONTEXTVARS = "_contextvars";
    public static final String BZ2 = "_bz2";
}
