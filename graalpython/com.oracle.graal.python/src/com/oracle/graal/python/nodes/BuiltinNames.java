/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import com.oracle.truffle.api.strings.TruffleString;

public abstract class BuiltinNames {
    // special strings
    public static final String J_LAMBDA_NAME = "<lambda>";
    public static final TruffleString T_LAMBDA_NAME = tsLiteral(J_LAMBDA_NAME);

    // special arg names
    public static final TruffleString T_SELF = tsLiteral("self");

    // cpython internals
    public static final String J_BREAKPOINT = "breakpoint";
    public static final TruffleString T_BREAKPOINT = tsLiteral(J_BREAKPOINT);

    public static final String J_MODULE = "module";
    public static final TruffleString T_MODULE = tsLiteral(J_MODULE);

    public static final String J___BUILD_CLASS__ = "__build_class__";
    public static final TruffleString T___BUILD_CLASS__ = tsLiteral(J___BUILD_CLASS__);

    public static final String J___MAIN__ = "__main__";
    public static final TruffleString T___MAIN__ = tsLiteral(J___MAIN__);

    public static final TruffleString T___BUILTINS__ = tsLiteral("__builtins__");

    public static final String J___DEBUG__ = "__debug__";
    public static final TruffleString T___DEBUG__ = tsLiteral(J___DEBUG__);

    public static final String J___FUTURE__ = "__future__";

    // sys
    public static final TruffleString T_TRACEBACKLIMIT = tsLiteral("tracebacklimit");

    public static final String J_DISPLAYHOOK = "displayhook";
    public static final TruffleString T_DISPLAYHOOK = tsLiteral(J_DISPLAYHOOK);

    public static final TruffleString T___DISPLAYHOOK__ = tsLiteral("__displayhook__");

    public static final String J_BREAKPOINTHOOK = "breakpointhook";
    public static final TruffleString T_BREAKPOINTHOOK = tsLiteral(J_BREAKPOINTHOOK);

    public static final TruffleString T___BREAKPOINTHOOK__ = tsLiteral("__breakpointhook__");

    public static final String J_EXCEPTHOOK = "excepthook";
    public static final TruffleString T_EXCEPTHOOK = tsLiteral(J_EXCEPTHOOK);

    public static final TruffleString T___EXCEPTHOOK__ = tsLiteral("__excepthook__");

    public static final String J_UNRAISABLEHOOK = "unraisablehook";
    public static final TruffleString T_UNRAISABLEHOOK = tsLiteral(J_UNRAISABLEHOOK);

    public static final TruffleString T___UNRAISABLEHOOK__ = tsLiteral("__unraisablehook__");

    public static final TruffleString T_LAST_TYPE = tsLiteral("last_type");

    public static final TruffleString T_LAST_VALUE = tsLiteral("last_value");

    public static final TruffleString T_LAST_TRACEBACK = tsLiteral("last_traceback");

    public static final TruffleString T___STDERR__ = tsLiteral("__stderr__");

    public static final TruffleString T_STDERR = tsLiteral("stderr");

    public static final TruffleString T___STDIN__ = tsLiteral("__stdin__");

    public static final TruffleString T_STDIN = tsLiteral("stdin");

    public static final TruffleString T___STDOUT__ = tsLiteral("__stdout__");

    public static final TruffleString T_STDOUT = tsLiteral("stdout");

    public static final TruffleString T_PYTHONBREAKPOINT = tsLiteral("PYTHONBREAKPOINT");

    public static final String J_EXIT = "exit";

    public static final TruffleString T_MODULES = tsLiteral("modules");

    // built-in functions
    public static final String J_ABS = "abs";
    public static final TruffleString T_ABS = tsLiteral(J_ABS);

    public static final String J_DICT = "dict";
    public static final TruffleString T_DICT = tsLiteral(J_DICT);

    public static final String J_DEFAULTDICT = "defaultdict";

    public static final String J_PARTIAL = "partial";
    public static final String J_LRU_CACHE_WRAPPER = "_lru_cache_wrapper";

    public static final String J_FUNCTOOLS = "_functools";
    public static final TruffleString T_FUNCTOOLS = tsLiteral(J_FUNCTOOLS);
    public static final String J_TUPLE_GETTER = "_tuplegetter";

    public static final String J_DEQUE = "deque";
    public static final TruffleString T_DEQUE = tsLiteral(J_DEQUE);

    public static final String J_DEQUE_ITER = "_deque_iterator";

    public static final String J_DEQUE_REV_ITER = "_deque_reverse_iterator";

    public static final String J_ORDERED_DICT = "OrderedDict";

    public static final String J_MIN = "min";
    public static final TruffleString T_MIN = tsLiteral(J_MIN);

    public static final String J_SETATTR = "setattr";

    public static final String J_ALL = "all";

    public static final String J_DIR = "dir";

    public static final String J_HEX = "hex";

    public static final String J_NEXT = "next";

    public static final String J_ANY = "any";

    public static final String J_DIVMOD = "divmod";

    public static final String J_ID = "id";

    public static final String J_OBJECT = "object";

    public static final String J_FOREIGN = "foreign";

    public static final String J_SORTED = "sorted";

    public static final String J_ASCII = "ascii";
    public static final TruffleString T_ASCII = tsLiteral(J_ASCII);

    public static final TruffleString T_CP437 = tsLiteral("cp437");

    public static final String J_ENUMERATE = "enumerate";

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
    public static final TruffleString T_EXEC = StringLiterals.T_EXEC;

    public static final String J_ISINSTANCE = "isinstance";
    public static final TruffleString T_ISINSTANCE = tsLiteral(J_ISINSTANCE);

    public static final String J_ORD = "ord";

    public static final String J_SUM = "sum";

    public static final String J_BYTEARRAY = "bytearray";

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

    public static final String J_ENCODE = "encode";
    public static final TruffleString T_ENCODE = tsLiteral(J_ENCODE);

    public static final String J_ENCODINGS = "encodings";
    public static final TruffleString T_ENCODINGS = tsLiteral(J_ENCODINGS);

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

    public static final String J_TYPES = "types";

    public static final String J_TYPING = "typing";
    public static final TruffleString T_TYPING = tsLiteral(J_TYPING);

    public static final String J_CHR = "chr";

    public static final String J_FROZENSET = "frozenset";

    public static final String J_LIST = "list";
    public static final TruffleString T_LIST = tsLiteral(J_LIST);

    public static final String J_RANGE = "range";

    public static final String J_CLASSMETHOD = "classmethod";

    public static final String J_INSTANCEMETHOD = "instancemethod";

    public static final String J_GETATTR = "getattr";
    public static final TruffleString T_GETATTR = tsLiteral(J_GETATTR);

    public static final TruffleString T_LOCALS = tsLiteral("locals");

    public static final String J_REMOVEPREFIX = "removeprefix";
    public static final String J_REMOVESUFFIX = "removesuffix";

    public static final String J_REPR = "repr";
    public static final TruffleString T_REPR = tsLiteral(J_REPR);

    public static final String J_ZIP = "zip";
    public static final TruffleString T_ZIP = tsLiteral("zip");

    public static final String J_COMPILE = "compile";
    public static final TruffleString T_COMPILE = tsLiteral(J_COMPILE);

    public static final TruffleString T_GLOBALS = tsLiteral("globals");

    public static final String J_MAP = "map";

    public static final String J_REVERSED = "reversed";

    public static final TruffleString T___IMPORT__ = tsLiteral("__import__");

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

    public static final String J_REGISTER_HOST_INTEROP_BEHAVIOR = "register_host_interop_behavior";
    public static final TruffleString T_REGISTER_HOST_INTEROP_BEHAVIOR = tsLiteral(J_REGISTER_HOST_INTEROP_BEHAVIOR);

    public static final String J_GET_REGISTERED_HOST_INTEROP_BEHAVIOR = "get_registered_host_interop_behavior";
    public static final TruffleString T_GET_REGISTERED_HOST_INTEROP_BEHAVIOR = tsLiteral(J_GET_REGISTERED_HOST_INTEROP_BEHAVIOR);

    public static final String J___GRAALPYTHON_HOST_INTEROP_BEHAVIOR__ = "__graalpython_host_interop_behavior__";
    public static final TruffleString T___GRAALPYTHON_HOST_INTEROP_BEHAVIOR__ = tsLiteral(J___GRAALPYTHON_HOST_INTEROP_BEHAVIOR__);

    public static final String J__CODECS = "_codecs";
    public static final TruffleString T__CODECS = tsLiteral(J__CODECS);

    public static final String J__STRING = "_string";

    public static final String J_LOCALE = "locale";
    public static final TruffleString T_LOCALE = tsLiteral(J_LOCALE);

    public static final String J__CODECS_TRUFFLE = "_codecs_truffle";
    public static final TruffleString T__CODECS_TRUFFLE = tsLiteral(J__CODECS_TRUFFLE);

    public static final String J_GETSET_DESCRIPTOR = "getset_descriptor";
    public static final TruffleString T_GETSET_DESCRIPTOR = tsLiteral(J_GETSET_DESCRIPTOR);

    public static final String J_MEMBER_DESCRIPTOR = "member_descriptor";
    public static final TruffleString T_MEMBER_DESCRIPTOR = tsLiteral(J_MEMBER_DESCRIPTOR);

    public static final String J_WRAPPER_DESCRIPTOR = "wrapper_descriptor";
    public static final TruffleString T_WRAPPER_DESCRIPTOR = tsLiteral(J_WRAPPER_DESCRIPTOR);

    public static final String J_SIMPLE_QUEUE = "SimpleQueue";

    public static final TruffleString T_EMPTY_CLASS_NAME = tsLiteral("Empty");

    public static final String J__CONTEXTVARS = "_contextvars";

    public static final TruffleString T_THREADING = tsLiteral("threading");

    public static final String J_DICT_KEYITERATOR = "dict_keyiterator";
    public static final String J_DICT_VALUEITERATOR = "dict_valueiterator";
    public static final String J_DICT_ITEMITERATOR = "dict_itemiterator";
    public static final String J_DICT_REVERSE_KEYITERATOR = "dict_reversekeyiterator";
    public static final String J_DICT_REVERSE_VALUEITERATOR = "dict_reversevalueiterator";
    public static final String J_DICT_REVERSE_ITEMITERATOR = "dict_reverseitemiterator";
    public static final String J_DICT_KEYS = "dict_keys";
    public static final String J_DICT_ITEMS = "dict_items";
    public static final String J_DICT_VALUES = "dict_values";

    public static final TruffleString T_SYS = tsLiteral("sys");

    public static final TruffleString T__SIGNAL = tsLiteral("_signal");

    public static final String J__WEAKREF = "_weakref";
    public static final TruffleString T__WEAKREF = tsLiteral(J__WEAKREF);

    public static final String J__WARNINGS = "_warnings";
    public static final TruffleString T__WARNINGS = tsLiteral(J__WARNINGS);

    public static final String J__TRACEMALLOC = "_tracemalloc";
    public static final TruffleString T__TRACEMALLOC = tsLiteral(J__TRACEMALLOC);

    public static final String J_POSIX = "posix";
    public static final TruffleString T_POSIX = tsLiteral(J_POSIX);

    public static final String J_NT = "nt";
    public static final TruffleString T_NT = tsLiteral(J_NT);

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

    public static final TruffleString T__SYSCONFIG = tsLiteral("_sysconfig");

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

    public static final String J_TYPE_VAR = "TypeVar";
    public static final TruffleString T_TYPE_VAR = tsLiteral(J_TYPE_VAR);

    public static final String J__SRE = "_sre";
    public static final TruffleString T__SRE = tsLiteral(J__SRE);

    // function names
    public static final String J_ADD = "add";
    public static final TruffleString T_ADD = tsLiteral(J_ADD);

    public static final String J_DISCARD = "discard";
    public static final TruffleString T_DISCARD = tsLiteral(J_DISCARD);

    public static final String J_APPEND = "append";
    public static final TruffleString T_APPEND = tsLiteral(J_APPEND);

    public static final String J_EXTEND = "extend";
    public static final TruffleString T_EXTEND = tsLiteral(J_EXTEND);

    public static final String J_BZ2 = "_bz2";
    public static final TruffleString T_BZ2 = tsLiteral(J_BZ2);

    public static final String J__ASYNCIO = "_asyncio";
    public static final TruffleString T__ASYNCIO = tsLiteral(J__ASYNCIO);

    public static final String J__CODECS_CN = "_codecs_cn";
    public static final TruffleString T__CODECS_CN = tsLiteral(J__CODECS_CN);

    public static final String J__CODECS_HK = "_codecs_hk";
    public static final TruffleString T__CODECS_HK = tsLiteral(J__CODECS_HK);

    public static final String J__CODECS_ISO2022 = "_codecs_iso2022";
    public static final TruffleString T__CODECS_ISO2022 = tsLiteral(J__CODECS_ISO2022);

    public static final String J__CODECS_JP = "_codecs_jp";
    public static final TruffleString T__CODECS_JP = tsLiteral(J__CODECS_JP);

    public static final String J__CODECS_KR = "_codecs_kr";
    public static final TruffleString T__CODECS_KR = tsLiteral(J__CODECS_KR);

    public static final String J__CODECS_TW = "_codecs_tw";
    public static final TruffleString T__CODECS_TW = tsLiteral(J__CODECS_TW);

}
