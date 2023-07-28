/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.PythonLanguage.J_GRAALPYTHON_ID;
import static com.oracle.graal.python.PythonLanguage.T_GRAALPYTHON_ID;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.AttributeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.DeprecationWarning;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ImportError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.RuntimeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.RuntimeWarning;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.UnicodeEncodeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.builtins.PythonOS.PLATFORM_DARWIN;
import static com.oracle.graal.python.builtins.PythonOS.PLATFORM_WIN32;
import static com.oracle.graal.python.builtins.PythonOS.getPythonOS;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_BUFFER;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_CLOSE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_ENCODING;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_MODE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_R;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_W;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_WRITE;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyObject__ob_refcnt;
import static com.oracle.graal.python.builtins.objects.str.StringUtils.cat;
import static com.oracle.graal.python.lib.PyTraceBackPrintNode.castToString;
import static com.oracle.graal.python.lib.PyTraceBackPrintNode.classNameNoDot;
import static com.oracle.graal.python.lib.PyTraceBackPrintNode.fileFlush;
import static com.oracle.graal.python.lib.PyTraceBackPrintNode.fileWriteString;
import static com.oracle.graal.python.lib.PyTraceBackPrintNode.getExceptionTraceback;
import static com.oracle.graal.python.lib.PyTraceBackPrintNode.getObjectClass;
import static com.oracle.graal.python.lib.PyTraceBackPrintNode.getTypeName;
import static com.oracle.graal.python.lib.PyTraceBackPrintNode.longAsInt;
import static com.oracle.graal.python.lib.PyTraceBackPrintNode.objectHasAttr;
import static com.oracle.graal.python.lib.PyTraceBackPrintNode.objectLookupAttr;
import static com.oracle.graal.python.lib.PyTraceBackPrintNode.objectLookupAttrAsString;
import static com.oracle.graal.python.lib.PyTraceBackPrintNode.objectRepr;
import static com.oracle.graal.python.lib.PyTraceBackPrintNode.objectStr;
import static com.oracle.graal.python.lib.PyTraceBackPrintNode.setExceptionTraceback;
import static com.oracle.graal.python.lib.PyTraceBackPrintNode.tryCastToString;
import static com.oracle.graal.python.nodes.BuiltinNames.J_BREAKPOINTHOOK;
import static com.oracle.graal.python.nodes.BuiltinNames.J_DISPLAYHOOK;
import static com.oracle.graal.python.nodes.BuiltinNames.J_EXCEPTHOOK;
import static com.oracle.graal.python.nodes.BuiltinNames.J_EXIT;
import static com.oracle.graal.python.nodes.BuiltinNames.J_UNRAISABLEHOOK;
import static com.oracle.graal.python.nodes.BuiltinNames.T_BREAKPOINTHOOK;
import static com.oracle.graal.python.nodes.BuiltinNames.T_BUILTINS;
import static com.oracle.graal.python.nodes.BuiltinNames.T_DISPLAYHOOK;
import static com.oracle.graal.python.nodes.BuiltinNames.T_EXCEPTHOOK;
import static com.oracle.graal.python.nodes.BuiltinNames.T_MODULES;
import static com.oracle.graal.python.nodes.BuiltinNames.T_PYTHONBREAKPOINT;
import static com.oracle.graal.python.nodes.BuiltinNames.T_STDERR;
import static com.oracle.graal.python.nodes.BuiltinNames.T_STDIN;
import static com.oracle.graal.python.nodes.BuiltinNames.T_STDOUT;
import static com.oracle.graal.python.nodes.BuiltinNames.T_SYS;
import static com.oracle.graal.python.nodes.BuiltinNames.T_UNRAISABLEHOOK;
import static com.oracle.graal.python.nodes.BuiltinNames.T___BREAKPOINTHOOK__;
import static com.oracle.graal.python.nodes.BuiltinNames.T___DISPLAYHOOK__;
import static com.oracle.graal.python.nodes.BuiltinNames.T___EXCEPTHOOK__;
import static com.oracle.graal.python.nodes.BuiltinNames.T___GRAALPYTHON__;
import static com.oracle.graal.python.nodes.BuiltinNames.T___STDERR__;
import static com.oracle.graal.python.nodes.BuiltinNames.T___STDIN__;
import static com.oracle.graal.python.nodes.BuiltinNames.T___STDOUT__;
import static com.oracle.graal.python.nodes.BuiltinNames.T___UNRAISABLEHOOK__;
import static com.oracle.graal.python.nodes.ErrorMessages.ARG_TYPE_MUST_BE;
import static com.oracle.graal.python.nodes.ErrorMessages.LOST_S;
import static com.oracle.graal.python.nodes.ErrorMessages.REC_LIMIT_GREATER_THAN_1;
import static com.oracle.graal.python.nodes.ErrorMessages.SWITCH_INTERVAL_MUST_BE_POSITIVE;
import static com.oracle.graal.python.nodes.ErrorMessages.S_EXPECTED_GOT_P;
import static com.oracle.graal.python.nodes.ErrorMessages.WARN_DEPRECTATED_SYS_CHECKINTERVAL;
import static com.oracle.graal.python.nodes.ErrorMessages.WARN_IGNORE_UNIMPORTABLE_BREAKPOINT_S;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___MODULE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_GET;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___SIZEOF__;
import static com.oracle.graal.python.nodes.StringLiterals.T_BACKSLASHREPLACE;
import static com.oracle.graal.python.nodes.StringLiterals.T_BIG;
import static com.oracle.graal.python.nodes.StringLiterals.T_COMMA;
import static com.oracle.graal.python.nodes.StringLiterals.T_DASH;
import static com.oracle.graal.python.nodes.StringLiterals.T_DOT;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_STRING;
import static com.oracle.graal.python.nodes.StringLiterals.T_JAVA;
import static com.oracle.graal.python.nodes.StringLiterals.T_LITTLE;
import static com.oracle.graal.python.nodes.StringLiterals.T_NEWLINE;
import static com.oracle.graal.python.nodes.StringLiterals.T_STRICT;
import static com.oracle.graal.python.nodes.StringLiterals.T_STRING_SOURCE;
import static com.oracle.graal.python.nodes.StringLiterals.T_SURROGATEESCAPE;
import static com.oracle.graal.python.nodes.StringLiterals.T_VALUE_UNKNOWN;
import static com.oracle.graal.python.nodes.StringLiterals.T_VERSION;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;
import static com.oracle.graal.python.util.PythonUtils.tsArray;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.graalvm.nativeimage.ImageInfo;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.PythonOS;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltinsClinicProviders.GetFrameNodeClinicProviderGen;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltinsClinicProviders.SetDlopenFlagsClinicProviderGen;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltinsFactory.ExcInfoNodeFactory;
import com.oracle.graal.python.builtins.modules.io.BufferedReaderBuiltins;
import com.oracle.graal.python.builtins.modules.io.BufferedWriterBuiltins;
import com.oracle.graal.python.builtins.modules.io.FileIOBuiltins;
import com.oracle.graal.python.builtins.modules.io.IONodes.IOMode;
import com.oracle.graal.python.builtins.modules.io.PBuffered;
import com.oracle.graal.python.builtins.modules.io.PFileIO;
import com.oracle.graal.python.builtins.modules.io.PTextIO;
import com.oracle.graal.python.builtins.modules.io.TextIOWrapperNodesFactory.TextIOWrapperInitNodeGen;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.common.EconomicMapStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItem;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.exception.ExceptionNodes;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.frame.PFrame.Reference;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.namespace.PSimpleNamespace;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.set.PFrozenSet;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.str.StringNodes;
import com.oracle.graal.python.builtins.objects.str.StringUtils;
import com.oracle.graal.python.builtins.objects.thread.PThread;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.tuple.StructSequence;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltins;
import com.oracle.graal.python.lib.PyExceptionInstanceCheckNode;
import com.oracle.graal.python.lib.PyFloatAsDoubleNode;
import com.oracle.graal.python.lib.PyFloatCheckExactNode;
import com.oracle.graal.python.lib.PyImportImport;
import com.oracle.graal.python.lib.PyLongAsIntNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectReprAsObjectNode;
import com.oracle.graal.python.lib.PyObjectSetAttr;
import com.oracle.graal.python.lib.PyObjectStrAsObjectNode;
import com.oracle.graal.python.lib.PyTraceBackPrintNode;
import com.oracle.graal.python.lib.PyUnicodeAsEncodedString;
import com.oracle.graal.python.lib.PyUnicodeFromEncodedObject;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode.NoAttributeHandler;
import com.oracle.graal.python.nodes.frame.ReadCallerFrameNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.InlinedGetClassNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes.GetCaughtExceptionNode;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.formatting.IntegerFormatter;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.CharsetMapping;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(defineModule = "sys", isEager = true)
public final class SysModuleBuiltins extends PythonBuiltins {
    private static final TruffleString T_LICENSE = tsLiteral(
                    "Copyright (c) Oracle and/or its affiliates. Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.");
    private static final String COMPILE_TIME;
    public static final PNone FRAMEWORK = PNone.NONE;
    public static final int MAXSIZE = Integer.MAX_VALUE;
    public static final long HASH_MULTIPLIER = 1000003L;
    public static final int HASH_BITS = 61;
    public static final long HASH_MODULUS = (1L << HASH_BITS) - 1;
    public static final long HASH_INF = 314159;
    public static final long HASH_NAN = 0;
    public static final long HASH_IMAG = HASH_MULTIPLIER;

    public static final int INT_DEFAULT_MAX_STR_DIGITS = 4300;
    public static final int INT_MAX_STR_DIGITS_THRESHOLD = 640;

    public static final TruffleString T_CACHE_TAG = tsLiteral("cache_tag");
    public static final TruffleString T__MULTIARCH = tsLiteral("_multiarch");

    static {
        String compile_time;
        try {
            compile_time = new Date(PythonBuiltins.class.getResource("PythonBuiltins.class").openConnection().getLastModified()).toString();
        } catch (IOException e) {
            compile_time = "";
        }
        COMPILE_TIME = compile_time;
    }

    private static final TruffleString[] SYS_PREFIX_ATTRIBUTES = tsArray("prefix", "exec_prefix");
    private static final TruffleString[] BASE_PREFIX_ATTRIBUTES = tsArray("base_prefix", "base_exec_prefix");

    static final StructSequence.BuiltinTypeDescriptor VERSION_INFO_DESC = new StructSequence.BuiltinTypeDescriptor(
                    PythonBuiltinClassType.PVersionInfo,
                    // @formatter:off The formatter joins these lines making it less readable
                    "sys.version_info\n" +
                    "\n" +
                    "Version information as a named tuple.",
                    // @formatter:on
                    5,
                    new String[]{
                                    "major", "minor", "micro",
                                    "releaselevel", "serial"},
                    new String[]{
                                    "Major release number", "Minor release number", "Patch release number",
                                    "'alpha', 'beta', 'candidate', or 'final'", "Serial release number"},
                    false);

    static final StructSequence.BuiltinTypeDescriptor FLAGS_DESC = new StructSequence.BuiltinTypeDescriptor(
                    PythonBuiltinClassType.PFlags,
                    // @formatter:off The formatter joins these lines making it less readable
                    "sys.flags\n" +
                    "\n" +
                    "Flags provided through command line arguments or environment vars.",
                    // @formatter:on
                    17,
                    new String[]{
                                    "debug", "inspect", "interactive", "optimize", "dont_write_bytecode",
                                    "no_user_site", "no_site", "ignore_environment", "verbose",
                                    "bytes_warning", "quiet", "hash_randomization", "isolated",
                                    "dev_mode", "utf8_mode", "warn_default_encoding", "int_max_str_digits"},
                    new String[]{
                                    "-d", "-i", "-i", "-O or -OO", "-B",
                                    "-s", "-S", "-E", "-v",
                                    "-b", "-q", "-R", "-I",
                                    "-X dev", "-X utf8", "-X warn_default_encoding", "-X int_max_str_digits"},
                    false);

    static final StructSequence.BuiltinTypeDescriptor FLOAT_INFO_DESC = new StructSequence.BuiltinTypeDescriptor(
                    PythonBuiltinClassType.PFloatInfo,
                    // @formatter:off The formatter joins these lines making it less readable
                    "sys.float_info\n" +
                    "\n" +
                    "A named tuple holding information about the float type. It contains low level\n" +
                    "information about the precision and internal representation. Please study\n" +
                    "your system's :file:`float.h` for more information.",
                    // @formatter:on
                    11,
                    new String[]{
                                    "max",
                                    "max_exp",
                                    "max_10_exp",
                                    "min",
                                    "min_exp",
                                    "min_10_exp",
                                    "dig",
                                    "mant_dig",
                                    "epsilon",
                                    "radix",
                                    "rounds"},
                    new String[]{
                                    "DBL_MAX -- maximum representable finite float",
                                    "DBL_MAX_EXP -- maximum int e such that radix**(e-1) is representable",
                                    "DBL_MAX_10_EXP -- maximum int e such that 10**e is representable",
                                    "DBL_MIN -- Minimum positive normalized float",
                                    "DBL_MIN_EXP -- minimum int e such that radix**(e-1) is a normalized float",
                                    "DBL_MIN_10_EXP -- minimum int e such that 10**e is a normalized",
                                    "DBL_DIG -- digits",
                                    "DBL_MANT_DIG -- mantissa digits",
                                    "DBL_EPSILON -- Difference between 1 and the next representable float",
                                    "FLT_RADIX -- radix of exponent",
                                    "FLT_ROUNDS -- rounding mode"});

    static final StructSequence.BuiltinTypeDescriptor INT_INFO_DESC = new StructSequence.BuiltinTypeDescriptor(
                    PythonBuiltinClassType.PIntInfo,
                    // @formatter:off The formatter joins these lines making it less readable
                    "sys.int_info\n" +
                    "\n" +
                    "A named tuple that holds information about Python's\n" +
                    "internal representation of integers.  The attributes are read only.",
                    // @formatter:on
                    4,
                    new String[]{
                                    "bits_per_digit", "sizeof_digit", "default_max_str_digits", "str_digits_check_threshold"},
                    new String[]{
                                    "size of a digit in bits",
                                    "size in bytes of the C type used to represent a digit",
                                    "maximum string conversion digits limitation",
                                    "minimum positive value for int_max_str_digits"});

    static final StructSequence.BuiltinTypeDescriptor HASH_INFO_DESC = new StructSequence.BuiltinTypeDescriptor(
                    PythonBuiltinClassType.PHashInfo,
                    // @formatter:off The formatter joins these lines making it less readable
                    "hash_info\n" +
                    "\n" +
                    "A named tuple providing parameters used for computing\n" +
                    "hashes. The attributes are read only.",
                    // @formatter:on
                    9,
                    new String[]{
                                    "width", "modulus", "inf", "nan", "imag", "algorithm", "hash_bits",
                                    "seed_bits", "cutoff"},
                    new String[]{
                                    "width of the type used for hashing, in bits",
                                    "prime number giving the modulus on which the hash function is based",
                                    "value to be used for hash of a positive infinity",
                                    "value to be used for hash of a nan",
                                    "multiplier used for the imaginary part of a complex number",
                                    "name of the algorithm for hashing of str, bytes and memoryviews",
                                    "internal output size of hash algorithm",
                                    "seed size of hash algorithm",
                                    "small string optimization cutoff"});

    static final StructSequence.BuiltinTypeDescriptor THREAD_INFO_DESC = new StructSequence.BuiltinTypeDescriptor(
                    PythonBuiltinClassType.PThreadInfo,
                    // @formatter:off The formatter joins these lines making it less readable
                    "sys.thread_info\n" +
                    "\n" +
                    "A named tuple holding information about the thread implementation.",
                    // @formatter:on
                    3,
                    new String[]{
                                    "name", "lock", "version"},
                    new String[]{
                                    "name of the thread implementation", "name of the lock implementation",
                                    "name and version of the thread library"});

    public static final StructSequence.BuiltinTypeDescriptor UNRAISABLEHOOK_ARGS_DESC = new StructSequence.BuiltinTypeDescriptor(
                    PythonBuiltinClassType.PUnraisableHookArgs,
                    // @formatter:off The formatter joins these lines making it less readable
                    "UnraisableHookArgs\n" +
                    "\n" +
                    "Type used to pass arguments to sys.unraisablehook.",
                    // @formatter:on
                    5,
                    new String[]{
                                    "exc_type", "exc_value", "exc_traceback",
                                    "err_msg", "object"},
                    new String[]{
                                    "Exception type", "Exception value", "Exception traceback",
                                    "Error message", "Object causing the exception"});

    // see stdlib_modules_names.h
    private static final String[] STDLIB_MODULE_NAMES = new String[]{"__future__", "_abc", "_aix_support", "_ast", "_asyncio", "_bisect", "_blake2", "_bootsubprocess", "_bz2", "_codecs", "_codecs_cn",
                    "_codecs_hk", "_codecs_iso2022", "_codecs_jp", "_codecs_kr", "_codecs_tw", "_collections", "_collections_abc", "_compat_pickle", "_compression", "_contextvars", "_crypt", "_csv",
                    "_ctypes", "_curses", "_curses_panel", "_datetime", "_dbm", "_decimal", "_elementtree", "_frozen_importlib", "_frozen_importlib_external", "_functools", "_gdbm", "_hashlib",
                    "_heapq", "_imp", "_io", "_json", "_locale", "_lsprof", "_lzma", "_markupbase", "_md5", "_msi", "_multibytecodec", "_multiprocessing", "_opcode", "_operator", "_osx_support",
                    "_overlapped", "_pickle", "_posixshmem", "_posixsubprocess", "_py_abc", "_pydecimal", "_pyio", "_queue", "_random", "_scproxy", "_sha1", "_sha256", "_sha3", "_sha512", "_signal",
                    "_sitebuiltins", "_socket", "_sqlite3", "_sre", "_ssl", "_stat", "_statistics", "_string", "_strptime", "_struct", "_symtable", "_thread", "_threading_local", "_tkinter",
                    "_tracemalloc", "_uuid", "_warnings", "_weakref", "_weakrefset", "_winapi", "_zoneinfo", "abc", "aifc", "antigravity", "argparse", "array", "ast", "asynchat", "asyncio",
                    "asyncore", "atexit", "audioop", "base64", "bdb", "binascii", "binhex", "bisect", "builtins", "bz2", "cProfile", "calendar", "cgi", "cgitb", "chunk", "cmath", "cmd", "code",
                    "codecs", "codeop", "collections", "colorsys", "compileall", "concurrent", "configparser", "contextlib", "contextvars", "copy", "copyreg", "crypt", "csv", "ctypes", "curses",
                    "dataclasses", "datetime", "dbm", "decimal", "difflib", "dis", "distutils", "doctest", "email", "encodings", "ensurepip", "enum", "errno", "faulthandler", "fcntl", "filecmp",
                    "fileinput", "fnmatch", "fractions", "ftplib", "functools", "gc", "genericpath", "getopt", "getpass", "gettext", "glob", "graphlib", "grp", "gzip", "hashlib", "heapq", "hmac",
                    "html", "http", "idlelib", "imaplib", "imghdr", "imp", "importlib", "inspect", "io", "ipaddress", "itertools", "json", "keyword", "lib2to3", "linecache", "locale", "logging",
                    "lzma", "mailbox", "mailcap", "marshal", "math", "mimetypes", "mmap", "modulefinder", "msilib", "msvcrt", "multiprocessing", "netrc", "nis", "nntplib", "nt", "ntpath",
                    "nturl2path", "numbers", "opcode", "operator", "optparse", "os", "ossaudiodev", "pathlib", "pdb", "pickle", "pickletools", "pipes", "pkgutil", "platform", "plistlib", "poplib",
                    "posix", "posixpath", "pprint", "profile", "pstats", "pty", "pwd", "py_compile", "pyclbr", "pydoc", "pydoc_data", "pyexpat", "queue", "quopri", "random", "re", "readline",
                    "reprlib", "resource", "rlcompleter", "runpy", "sched", "secrets", "select", "selectors", "shelve", "shlex", "shutil", "signal", "site", "smtpd", "smtplib", "sndhdr", "socket",
                    "socketserver", "spwd", "sqlite3", "sre_compile", "sre_constants", "sre_parse", "ssl", "stat", "statistics", "string", "stringprep", "struct", "subprocess", "sunau", "symtable",
                    "sys", "sysconfig", "syslog", "tabnanny", "tarfile", "telnetlib", "tempfile", "termios", "textwrap", "this", "threading", "time", "timeit", "tkinter", "token", "tokenize", "trace",
                    "traceback", "tracemalloc", "tty", "turtle", "turtledemo", "types", "typing", "unicodedata", "unittest", "urllib", "uu", "uuid", "venv", "warnings", "wave", "weakref",
                    "webbrowser", "winreg", "winsound", "wsgiref", "xdrlib", "xml", "xmlrpc", "zipapp", "zipfile", "zipimport", "zlib", "zoneinfo"};

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SysModuleBuiltinsFactory.getFactories();
    }

    protected static PSimpleNamespace makeImplementation(PythonObjectFactory factory, PTuple versionInfo, TruffleString gmultiarch) {
        final PSimpleNamespace ns = factory.createSimpleNamespace();
        ns.setAttribute(tsLiteral("name"), T_GRAALPYTHON_ID);
        ns.setAttribute(T_CACHE_TAG,
                        toTruffleStringUncached(J_GRAALPYTHON_ID + PythonLanguage.GRAALVM_MAJOR + PythonLanguage.GRAALVM_MINOR + "-" + PythonLanguage.MAJOR + PythonLanguage.MINOR));
        ns.setAttribute(T_VERSION, versionInfo);
        ns.setAttribute(T__MULTIARCH, gmultiarch);
        ns.setAttribute(tsLiteral("hexversion"), PythonLanguage.VERSION_HEX);
        return ns;
    }

    @Override
    public void initialize(Python3Core core) {
        StructSequence.initType(core, VERSION_INFO_DESC);
        StructSequence.initType(core, FLAGS_DESC);
        StructSequence.initType(core, FLOAT_INFO_DESC);
        StructSequence.initType(core, INT_INFO_DESC);
        StructSequence.initType(core, HASH_INFO_DESC);
        StructSequence.initType(core, THREAD_INFO_DESC);
        StructSequence.initType(core, UNRAISABLEHOOK_ARGS_DESC);

        addBuiltinConstant("abiflags", T_EMPTY_STRING);
        addBuiltinConstant("byteorder", ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN ? T_LITTLE : T_BIG);
        addBuiltinConstant("copyright", T_LICENSE);
        final PythonObjectFactory factory = PythonObjectFactory.getUncached();
        addBuiltinConstant(T_MODULES, factory.createDict());
        addBuiltinConstant("path", factory.createList());
        addBuiltinConstant("builtin_module_names", factory.createTuple(core.builtinModuleNames()));
        addBuiltinConstant("maxsize", MAXSIZE);
        final PTuple versionInfo = factory.createStructSeq(VERSION_INFO_DESC, PythonLanguage.MAJOR, PythonLanguage.MINOR, PythonLanguage.MICRO, PythonLanguage.RELEASE_LEVEL_STRING,
                        PythonLanguage.RELEASE_SERIAL);
        addBuiltinConstant("version_info", versionInfo);
        addBuiltinConstant("api_version", PythonLanguage.API_VERSION);
        addBuiltinConstant("version", toTruffleStringUncached(PythonLanguage.VERSION +
                        " (" + COMPILE_TIME + ")" +
                        "\n[Graal, " + Truffle.getRuntime().getName() + ", Java " + System.getProperty("java.version") + "]"));
        addBuiltinConstant("float_info", factory.createStructSeq(FLOAT_INFO_DESC,
                        Double.MAX_VALUE,           // DBL_MAX
                        Double.MAX_EXPONENT + 1,    // DBL_MAX_EXP
                        308,                        // DBL_MIN_10_EXP
                        Double.MIN_NORMAL,          // DBL_MIN
                        Double.MIN_EXPONENT + 1,    // DBL_MIN_EXP
                        -307,                       // DBL_MIN_10_EXP
                        15,                         // DBL_DIG
                        53,                         // DBL_MANT_DIG
                        Math.ulp(1.0),              // DBL_EPSILON
                        2,                          // FLT_RADIX
                        1                           // FLT_ROUNDS
        ));
        addBuiltinConstant("int_info", factory.createStructSeq(INT_INFO_DESC, 32, 4, INT_DEFAULT_MAX_STR_DIGITS, INT_MAX_STR_DIGITS_THRESHOLD));
        addBuiltinConstant("hash_info", factory.createStructSeq(HASH_INFO_DESC,
                        64,                         // width
                        HASH_MODULUS,               // modulus
                        HASH_INF,                   // inf
                        HASH_NAN,                   // nan
                        HASH_IMAG,                  // imag
                        T_JAVA,                     // algorithm
                        64,                         // hash_bits
                        0,                          // seed_bits
                        0                           // cutoff
        ));
        addBuiltinConstant("thread_info", factory.createStructSeq(THREAD_INFO_DESC, PNone.NONE, PNone.NONE, PNone.NONE));
        addBuiltinConstant("maxunicode", IntegerFormatter.LIMIT_UNICODE.intValue() - 1);

        PythonOS os = getPythonOS();
        addBuiltinConstant("platform", os.getName());
        if (os == PLATFORM_DARWIN) {
            addBuiltinConstant("_framework", FRAMEWORK);
        }
        final TruffleString gmultiarch = cat(PythonUtils.getPythonArch(), T_DASH, os.getName());
        addBuiltinConstant("__gmultiarch", gmultiarch);

        PFileIO stdin = factory.createFileIO(PythonBuiltinClassType.PFileIO);
        FileIOBuiltins.FileIOInit.internalInit(stdin, toTruffleStringUncached("<stdin>"), 0, IOMode.R);
        addBuiltinConstant(T_STDIN, stdin);
        addBuiltinConstant(T___STDIN__, stdin);

        PFileIO stdout = factory.createFileIO(PythonBuiltinClassType.PFileIO);
        FileIOBuiltins.FileIOInit.internalInit(stdout, toTruffleStringUncached("<stdout>"), 1, IOMode.W);
        addBuiltinConstant(T_STDOUT, stdout);
        addBuiltinConstant(T___STDOUT__, stdout);

        PFileIO stderr = factory.createFileIO(PythonBuiltinClassType.PFileIO);
        stderr.setUTF8Write(true);
        FileIOBuiltins.FileIOInit.internalInit(stderr, toTruffleStringUncached("<stderr>"), 2, IOMode.W);
        addBuiltinConstant(T_STDERR, stderr);
        addBuiltinConstant(T___STDERR__, stderr);
        addBuiltinConstant("implementation", makeImplementation(factory, versionInfo, gmultiarch));
        addBuiltinConstant("hexversion", PythonLanguage.VERSION_HEX);

        if (os == PLATFORM_WIN32) {
            addBuiltinConstant("winver", toTruffleStringUncached(PythonLanguage.MAJOR + "." + PythonLanguage.MINOR));
        }

        addBuiltinConstant("float_repr_style", "short");
        addBuiltinConstant("meta_path", factory.createList());
        addBuiltinConstant("path_hooks", factory.createList());
        addBuiltinConstant("path_importer_cache", factory.createDict());

        // default prompt for interactive shell
        addBuiltinConstant("ps1", ">>> ");
        // continue prompt for interactive shell
        addBuiltinConstant("ps2", "... ");
        // CPython builds for distros report empty strings too, because they are built from
        // tarballs, not git
        addBuiltinConstant("_git", factory.createTuple(new Object[]{T_GRAALPYTHON_ID, T_EMPTY_STRING, T_EMPTY_STRING}));

        super.initialize(core);

        // we need these during core initialization, they are re-set in postInitialize
        postInitialize0(core);
    }

    public void postInitialize0(Python3Core core) {
        super.postInitialize(core);
        PythonModule sys = core.lookupBuiltinModule(T_SYS);
        PythonContext context = core.getContext();
        String[] args = context.getEnv().getApplicationArguments();
        final PythonObjectFactory factory = PythonObjectFactory.getUncached();
        sys.setAttribute(tsLiteral("argv"), factory.createList(convertToObjectArray(args)));
        sys.setAttribute(tsLiteral("orig_argv"), factory.createList(convertToObjectArray(PythonOptions.getOrigArgv(core.getContext()))));

        sys.setAttribute(tsLiteral("stdlib_module_names"), createStdLibModulesSet(factory));

        TruffleString prefix = context.getSysPrefix();
        for (TruffleString name : SysModuleBuiltins.SYS_PREFIX_ATTRIBUTES) {
            sys.setAttribute(name, prefix);
        }

        TruffleString base_prefix = context.getSysBasePrefix();
        for (TruffleString name : SysModuleBuiltins.BASE_PREFIX_ATTRIBUTES) {
            sys.setAttribute(name, base_prefix);
        }

        sys.setAttribute(tsLiteral("platlibdir"), tsLiteral("lib"));

        TruffleString coreHome = context.getCoreHome();
        TruffleString stdlibHome = context.getStdlibHome();
        TruffleString capiHome = context.getCAPIHome();

        if (!ImageInfo.inImageBuildtimeCode()) {
            sys.setAttribute(tsLiteral("executable"), context.getOption(PythonOptions.Executable));
            sys.setAttribute(tsLiteral("_base_executable"), context.getOption(PythonOptions.Executable));
        }
        sys.setAttribute(tsLiteral("dont_write_bytecode"), context.getOption(PythonOptions.DontWriteBytecodeFlag));
        TruffleString pycachePrefix = context.getOption(PythonOptions.PyCachePrefix);
        sys.setAttribute(tsLiteral("pycache_prefix"), pycachePrefix.isEmpty() ? PNone.NONE : pycachePrefix);

        TruffleString strWarnoption = context.getOption(PythonOptions.WarnOptions);
        Object[] warnoptions;
        if (!strWarnoption.isEmpty()) {
            TruffleString[] strWarnoptions = StringUtils.split(strWarnoption, T_COMMA, TruffleString.CodePointLengthNode.getUncached(), TruffleString.IndexOfStringNode.getUncached(),
                            TruffleString.SubstringNode.getUncached(), TruffleString.EqualNode.getUncached());
            warnoptions = PythonUtils.convertToObjectArray(strWarnoptions);
        } else {
            warnoptions = PythonUtils.EMPTY_OBJECT_ARRAY;
        }
        sys.setAttribute(tsLiteral("warnoptions"), factory.createList(warnoptions));

        Env env = context.getEnv();
        TruffleString option = context.getOption(PythonOptions.PythonPath);

        boolean capiSeparate = !capiHome.equalsUncached(coreHome, TS_ENCODING);

        Object[] path;
        int pathIdx = 0;
        int defaultPathsLen = 2;
        if (capiSeparate) {
            defaultPathsLen++;
        }
        if (!option.isEmpty()) {
            TruffleString sep = toTruffleStringUncached(context.getEnv().getPathSeparator());
            TruffleString[] split = StringUtils.split(option, sep, TruffleString.CodePointLengthNode.getUncached(), TruffleString.IndexOfStringNode.getUncached(),
                            TruffleString.SubstringNode.getUncached(), TruffleString.EqualNode.getUncached());
            path = new Object[split.length + defaultPathsLen];
            for (int i = 0; i < split.length; ++i) {
                path[i] = split[i];
            }
            pathIdx = split.length;
        } else {
            path = new Object[defaultPathsLen];
        }
        path[pathIdx++] = stdlibHome;
        path[pathIdx++] = toTruffleStringUncached(coreHome + env.getFileNameSeparator() + "modules");
        if (capiSeparate) {
            // include our native modules on the path
            path[pathIdx++] = toTruffleStringUncached(capiHome + env.getFileNameSeparator() + "modules");
        }
        PList sysPaths = factory.createList(path);
        sys.setAttribute(tsLiteral("path"), sysPaths);
        sys.setAttribute(tsLiteral("flags"), factory.createStructSeq(SysModuleBuiltins.FLAGS_DESC,
                        PInt.intValue(!context.getOption(PythonOptions.PythonOptimizeFlag)), // debug
                        PInt.intValue(context.getOption(PythonOptions.InspectFlag)), // inspect
                        PInt.intValue(context.getOption(PythonOptions.TerminalIsInteractive)), // interactive
                        PInt.intValue(context.getOption(PythonOptions.PythonOptimizeFlag)), // optimize
                        PInt.intValue(context.getOption(PythonOptions.DontWriteBytecodeFlag)),  // dont_write_bytecode
                        PInt.intValue(context.getOption(PythonOptions.NoUserSiteFlag)), // no_user_site
                        PInt.intValue(context.getOption(PythonOptions.NoSiteFlag)), // no_site
                        PInt.intValue(context.getOption(PythonOptions.IgnoreEnvironmentFlag)), // ignore_environment
                        PInt.intValue(context.getOption(PythonOptions.VerboseFlag)), // verbose
                        0, // bytes_warning
                        PInt.intValue(context.getOption(PythonOptions.QuietFlag)), // quiet
                        0, // hash_randomization
                        PInt.intValue(context.getOption(PythonOptions.IsolateFlag)), // isolated
                        false, // dev_mode
                        0, // utf8_mode
                        PInt.intValue(context.getOption(PythonOptions.WarnDefaultEncodingFlag)), // warn_default_encoding
                        context.getOption(PythonOptions.IntMaxStrDigits) // int_max_str_digits
        ));
        sys.setAttribute(T___EXCEPTHOOK__, sys.getAttribute(T_EXCEPTHOOK));
        sys.setAttribute(T___UNRAISABLEHOOK__, sys.getAttribute(T_UNRAISABLEHOOK));
        sys.setAttribute(T___DISPLAYHOOK__, sys.getAttribute(T_DISPLAYHOOK));
        sys.setAttribute(T___BREAKPOINTHOOK__, sys.getAttribute(T_BREAKPOINTHOOK));
    }

    private static PFrozenSet createStdLibModulesSet(PythonObjectFactory factory) {
        EconomicMapStorage storage = EconomicMapStorage.create(STDLIB_MODULE_NAMES.length);
        for (String s : STDLIB_MODULE_NAMES) {
            TruffleString ts = toTruffleStringUncached(s);
            storage.putUncached(ts, PNone.NONE);
        }
        return factory.createFrozenSet(storage);
    }

    /**
     * Like {@link PythonUtils#toTruffleStringArrayUncached(String[])}, but creates an array of
     * {@link Object}'s. The intended use of this method is in slow-path in calls to methods like
     * {@link PythonObjectFactory#createTuple(Object[])}.
     */
    private static Object[] convertToObjectArray(String[] src) {
        if (src == null) {
            return null;
        }
        if (src.length == 0) {
            return PythonUtils.EMPTY_OBJECT_ARRAY;
        }
        Object[] result = new Object[src.length];
        for (int i = 0; i < src.length; ++i) {
            result[i] = toTruffleStringUncached(src[i]);
        }
        return result;
    }

    private static Object[] convertToObjectArray(TruffleString[] arr) {
        if (arr.length == 0) {
            return PythonUtils.EMPTY_OBJECT_ARRAY;
        }
        return Arrays.copyOf(arr, arr.length, Object[].class);
    }

    @Override
    public void postInitialize(Python3Core core) {
        postInitialize0(core);
        initStd(core);
    }

    @TruffleBoundary
    public void initStd(Python3Core core) {
        PythonObjectFactory factory = core.factory();

        // wrap std in/out/err
        GraalPythonModuleBuiltins gp = (GraalPythonModuleBuiltins) core.lookupBuiltinModule(T___GRAALPYTHON__).getBuiltins();
        TruffleString stdioEncoding = gp.getStdIOEncoding();
        TruffleString stdioError = gp.getStdIOError();
        Object posixSupport = core.getContext().getPosixSupport();
        PosixSupportLibrary posixLib = PosixSupportLibrary.getUncached();
        PythonModule sysModule = core.lookupBuiltinModule(T_SYS);

        PBuffered reader = factory.createBufferedReader(PythonBuiltinClassType.PBufferedReader);
        BufferedReaderBuiltins.BufferedReaderInit.internalInit(reader, (PFileIO) getBuiltinConstant(T_STDIN), BufferedReaderBuiltins.DEFAULT_BUFFER_SIZE, factory, posixSupport,
                        posixLib);
        setWrapper(T_STDIN, T___STDIN__, T_R, stdioEncoding, stdioError, reader, sysModule, factory);

        PBuffered writer = factory.createBufferedWriter(PythonBuiltinClassType.PBufferedWriter);
        BufferedWriterBuiltins.BufferedWriterInit.internalInit(writer, (PFileIO) getBuiltinConstant(T_STDOUT), BufferedReaderBuiltins.DEFAULT_BUFFER_SIZE, factory, posixSupport,
                        posixLib);
        PTextIO stdout = setWrapper(T_STDOUT, T___STDOUT__, T_W, stdioEncoding, stdioError, writer, sysModule, factory);

        writer = factory.createBufferedWriter(PythonBuiltinClassType.PBufferedWriter);
        BufferedWriterBuiltins.BufferedWriterInit.internalInit(writer, (PFileIO) getBuiltinConstant(T_STDERR), BufferedReaderBuiltins.DEFAULT_BUFFER_SIZE, factory, posixSupport,
                        posixLib);
        PTextIO stderr = setWrapper(T_STDERR, T___STDERR__, T_W, stdioEncoding, T_BACKSLASHREPLACE, writer, sysModule, factory);

        // register atexit close std out/err
        core.getContext().registerAtexitHook((ctx) -> {
            callClose(stdout);
            callClose(stderr);
        });
    }

    private static PTextIO setWrapper(TruffleString name, TruffleString specialName, TruffleString mode, TruffleString encoding, TruffleString error, PBuffered buffered, PythonModule sysModule,
                    PythonObjectFactory factory) {
        PTextIO textIOWrapper = factory.createTextIO(PythonBuiltinClassType.PTextIOWrapper);
        TextIOWrapperInitNodeGen.getUncached().execute(null, null, textIOWrapper, buffered, encoding, error, PNone.NONE, true, true);

        setAttribute(textIOWrapper, T_MODE, mode);
        setAttribute(sysModule, name, textIOWrapper);
        setAttribute(sysModule, specialName, textIOWrapper);

        return textIOWrapper;
    }

    private static void setAttribute(PythonObject obj, TruffleString key, Object value) {
        obj.setAttribute(key, value);
    }

    private static void callClose(Object obj) {
        try {
            PyObjectCallMethodObjArgs.getUncached().execute(null, obj, T_CLOSE);
        } catch (PException e) {
        }
    }

    public PDict getModules() {
        return (PDict) getBuiltinConstant(T_MODULES);
    }

    @TruffleBoundary
    public Object getStdErr() {
        return getBuiltinConstant(T_STDERR);
    }

    @TruffleBoundary
    public Object getStdOut() {
        return getBuiltinConstant(T_STDOUT);
    }

    @Builtin(name = "exc_info", needsFrame = true)
    @GenerateNodeFactory
    public abstract static class ExcInfoNode extends PythonBuiltinNode {

        @Override
        public abstract PTuple execute(VirtualFrame frame);

        @Specialization
        public PTuple run(VirtualFrame frame,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedGetClassNode getClassNode,
                        @Cached GetCaughtExceptionNode getCaughtExceptionNode,
                        @Cached ExceptionNodes.GetTracebackNode getTracebackNode) {
            PException currentException = getCaughtExceptionNode.execute(frame);
            assert currentException != PException.NO_EXCEPTION;
            if (currentException == null) {
                return factory().createTuple(new PNone[]{PNone.NONE, PNone.NONE, PNone.NONE});
            } else {
                Object exception = currentException.getEscapedException();
                Object traceback = getTracebackNode.execute(inliningTarget, exception);
                return factory().createTuple(new Object[]{getClassNode.execute(inliningTarget, exception), exception, traceback});
            }
        }

        @NeverDefault
        public static ExcInfoNode create() {
            return ExcInfoNodeFactory.create(null);
        }

    }

    // ATTENTION: this is intentionally a PythonBuiltinNode and not PythonUnaryBuiltinNode,
    // because we need a guarantee that this builtin will get its own stack frame in order to
    // be able to count how many frames down the call stack we need to walk
    @Builtin(name = "_getframe", parameterNames = "depth", minNumOfPositionalArgs = 0, needsFrame = true, alwaysNeedsCallerFrame = true)
    @ArgumentClinic(name = "depth", defaultValue = "0", conversion = ClinicConversion.Int)
    @GenerateNodeFactory
    public abstract static class GetFrameNode extends PythonClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return GetFrameNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        PFrame counted(VirtualFrame frame, int num,
                        @Bind("this") Node inliningTarget,
                        @Cached ReadCallerFrameNode readCallerNode,
                        @Cached InlinedConditionProfile callStackDepthProfile) {
            PFrame requested = escapeFrame(frame, num, readCallerNode);
            if (callStackDepthProfile.profile(inliningTarget, requested == null)) {
                throw raiseCallStackDepth();
            }
            return requested;
        }

        private static PFrame escapeFrame(VirtualFrame frame, int num, ReadCallerFrameNode readCallerNode) {
            Reference currentFrameInfo = PArguments.getCurrentFrameInfo(frame);
            currentFrameInfo.markAsEscaped();
            return readCallerNode.executeWith(currentFrameInfo, num);
        }

        private PException raiseCallStackDepth() {
            return raise(ValueError, ErrorMessages.CALL_STACK_NOT_DEEP_ENOUGH);
        }
    }

    @Builtin(name = "_current_frames")
    @GenerateNodeFactory
    abstract static class CurrentFrames extends PythonBuiltinNode {
        @Specialization
        Object currentFrames(VirtualFrame frame,
                        @Cached AuditNode auditNode,
                        @Cached WarningsModuleBuiltins.WarnNode warnNode,
                        @Cached ReadCallerFrameNode readCallerFrameNode,
                        @Cached HashingStorageSetItem setHashingStorageItem) {
            auditNode.audit("sys._current_frames");
            if (!getLanguage().singleThreadedAssumption.isValid()) {
                warnNode.warn(frame, RuntimeWarning, ErrorMessages.WARN_CURRENT_FRAMES_MULTITHREADED);
            }
            PFrame currentFrame = readCallerFrameNode.executeWith(frame, 0);
            PDict result = factory().createDict();
            result.setDictStorage(setHashingStorageItem.execute(frame, result.getDictStorage(), PThread.getThreadId(Thread.currentThread()), currentFrame));
            return result;
        }
    }

    @Builtin(name = "getfilesystemencoding", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class GetFileSystemEncodingNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        public static TruffleString getFileSystemEncoding() {
            String javaEncoding = System.getProperty("file.encoding");
            TruffleString pythonEncoding = CharsetMapping.getPythonEncodingNameFromJavaName(javaEncoding);
            // Fallback on returning the property value if no mapping found
            return pythonEncoding != null ? pythonEncoding : toTruffleStringUncached(javaEncoding);
        }
    }

    @Builtin(name = "getfilesystemencodeerrors", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class GetFileSystemEncodeErrorsNode extends PythonBuiltinNode {
        @Specialization
        protected static TruffleString getFileSystemEncoding() {
            return T_SURROGATEESCAPE;
        }
    }

    @Builtin(name = "intern", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class InternNode extends PythonUnaryBuiltinNode {
        private PString doIntern(Object str, StringNodes.InternStringNode internNode) {
            final PString interned = internNode.execute(str);
            if (interned == null) {
                throw raise(TypeError, ErrorMessages.CANNOT_INTERN_P, str);
            }
            return interned;
        }

        @Specialization
        Object doString(TruffleString s,
                        @Shared("internNode") @Cached StringNodes.InternStringNode internNode) {
            return doIntern(s, internNode);
        }

        @Specialization
        Object doPString(PString s,
                        @Shared("internNode") @Cached StringNodes.InternStringNode internNode) {
            return doIntern(s, internNode);
        }

        @Fallback
        Object doOthers(Object obj) {
            throw raise(TypeError, ErrorMessages.S_ARG_MUST_BE_S_NOT_P, "intern()", "str", obj);
        }
    }

    @Builtin(name = "getdefaultencoding", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class GetDefaultEncodingNode extends PythonBuiltinNode {
        @Specialization
        protected static TruffleString getFileSystemEncoding(
                        @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
            return fromJavaStringNode.execute(defaultCharsetName(), TS_ENCODING);
        }

        @TruffleBoundary
        private static String defaultCharsetName() {
            return Charset.defaultCharset().name();
        }
    }

    @Builtin(name = "getrefcount", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class GetrefcountNode extends PythonUnaryBuiltinNode {

        @Specialization
        protected long doGeneric(PythonAbstractObject object,
                        @Cached CStructAccess.ReadI64Node read) {
            if (object instanceof PythonAbstractNativeObject nativeKlass) {
                return read.readFromObj(nativeKlass, PyObject__ob_refcnt);
            }

            PythonNativeWrapper wrapper = object.getNativeWrapper();
            if (wrapper == null) {
                return -1;
            } else {
                return wrapper.getRefCount();
            }
        }

        @Fallback
        protected long doGeneric(@SuppressWarnings("unused") Object object) {
            return -1;
        }
    }

    @Builtin(name = "getsizeof", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class GetsizeofNode extends PythonBinaryBuiltinNode {
        @Child PyNumberAsSizeNode asSizeNode;

        @Specialization(guards = "isNoValue(dflt)")
        protected Object doGeneric(VirtualFrame frame, Object object, @SuppressWarnings("unused") PNone dflt,
                        @Cached("createWithError()") LookupAndCallUnaryNode callSizeofNode) {
            return checkResult(frame, callSizeofNode.executeObject(frame, object));
        }

        @Specialization(guards = "!isNoValue(dflt)")
        protected Object doGeneric(VirtualFrame frame, Object object, Object dflt,
                        @Cached("createWithoutError()") LookupAndCallUnaryNode callSizeofNode) {
            Object result = callSizeofNode.executeObject(frame, object);
            if (result == PNone.NO_VALUE) {
                return dflt;
            }
            return checkResult(frame, result);
        }

        private Object checkResult(VirtualFrame frame, Object result) {
            int value = getAsSizeNode().executeExact(frame, result);
            if (value < 0) {
                throw raise(ValueError, ErrorMessages.SHOULD_RETURN, "__sizeof__()", ">= 0");
            }
            return value;
        }

        private PyNumberAsSizeNode getAsSizeNode() {
            if (asSizeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                asSizeNode = insert(PyNumberAsSizeNode.create());
            }
            return asSizeNode;
        }

        @NeverDefault
        protected LookupAndCallUnaryNode createWithError() {
            return LookupAndCallUnaryNode.create(T___SIZEOF__, () -> new NoAttributeHandler() {
                @Override
                public Object execute(Object receiver) {
                    throw raise(TypeError, ErrorMessages.TYPE_DOESNT_DEFINE_METHOD, receiver, T___SIZEOF__);
                }
            });
        }

        @NeverDefault
        protected static LookupAndCallUnaryNode createWithoutError() {
            return LookupAndCallUnaryNode.create(T___SIZEOF__);
        }
    }

    // TODO implement support for audit events
    @GenerateUncached
    public abstract static class AuditNode extends Node {
        protected abstract void executeInternal(Object event, Object[] arguments);

        public void audit(String event, Object... arguments) {
            executeInternal(event, arguments);
        }

        public void audit(TruffleString event, Object... arguments) {
            executeInternal(event, arguments);
        }

        @Specialization
        void doAudit(@SuppressWarnings("unused") TruffleString event, @SuppressWarnings("unused") Object[] arguments) {
        }

        @Specialization
        void doAudit(@SuppressWarnings("unused") String event, @SuppressWarnings("unused") Object[] arguments) {
        }
    }

    @Builtin(name = "audit", minNumOfPositionalArgs = 1, takesVarArgs = true, doc = "audit(event, *args)\n" +
                    "\n" +
                    "Passes the event to any audit hooks that are attached.")
    @GenerateNodeFactory
    abstract static class SysAuditNode extends PythonBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        Object doAudit(VirtualFrame frame, Object event, Object[] args) {
            // TODO: Stub audit hooks implementation for PEP 578
            return PNone.NONE;
        }
    }

    @Builtin(name = "addaudithook", minNumOfPositionalArgs = 1, doc = "addaudithook($module, /, hook)\n" +
                    "--\n" +
                    "\n" +
                    "Adds a new audit hook callback.")
    @GenerateNodeFactory
    abstract static class SysAuditHookNode extends PythonBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        Object doAudit(VirtualFrame frame, Object hook) {
            // TODO: Stub audit hooks implementation for PEP 578
            return PNone.NONE;
        }
    }

    @Builtin(name = "is_finalizing")
    @GenerateNodeFactory
    public abstract static class IsFinalizingNode extends PythonBuiltinNode {
        @Specialization
        boolean doGeneric() {
            return getContext().isFinalizing();
        }
    }

    @Builtin(name = "settrace", minNumOfPositionalArgs = 1, parameterNames = {"function"}, doc = "Set the global debug tracing function.  It will be called on each\n" +
                    "function call.  See the debugger chapter in the library manual.")
    @GenerateNodeFactory
    abstract static class SetTrace extends PythonBuiltinNode {
        @Specialization
        Object settrace(Object function) {
            PythonContext ctx = getContext();
            PythonLanguage language = getLanguage();
            PythonContext.PythonThreadState state = ctx.getThreadState(language);
            if (function == PNone.NONE) {
                state.setTraceFun(null, language);
            } else {
                state.setTraceFun(function, language);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "setprofile", minNumOfPositionalArgs = 1, parameterNames = {
                    "function"}, doc = "Set the profiling function.  It will be called on each function call\nand return.  See the profiler chapter in the library manual.")
    @GenerateNodeFactory
    abstract static class SetProfile extends PythonBuiltinNode {
        @Specialization
        Object settrace(Object function) {
            PythonContext ctx = getContext();
            PythonLanguage language = getLanguage();
            PythonContext.PythonThreadState state = ctx.getThreadState(language);
            if (function == PNone.NONE) {
                state.setProfileFun(null, language);
            } else {
                state.setProfileFun(function, language);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "gettrace")
    @GenerateNodeFactory
    abstract static class GetTrace extends PythonBuiltinNode {
        @Specialization
        Object gettrace() {
            PythonContext ctx = getContext();
            PythonContext.PythonThreadState state = ctx.getThreadState(getLanguage());
            Object trace = state.getTraceFun();
            return trace == null ? PNone.NONE : trace;

        }
    }

    @Builtin(name = "getprofile")
    @GenerateNodeFactory
    abstract static class GetProfile extends PythonBuiltinNode {
        @Specialization
        Object getProfile() {
            PythonContext ctx = getContext();
            PythonContext.PythonThreadState state = ctx.getThreadState(getLanguage());
            Object trace = state.getProfileFun();
            return trace == null ? PNone.NONE : trace;
        }
    }

    @Builtin(name = "set_asyncgen_hooks", parameterNames = {"firstiter", "finalizer"})
    @GenerateNodeFactory
    abstract static class SetAsyncgenHooks extends PythonBuiltinNode {
        @Specialization
        Object setAsyncgenHooks(Object firstIter, Object finalizer) {
            if (firstIter != PNone.NO_VALUE && firstIter != PNone.NONE) {
                getContext().getThreadState(getLanguage()).setAsyncgenFirstIter(firstIter);
            } else if (firstIter == PNone.NONE) {
                getContext().getThreadState(getLanguage()).setAsyncgenFirstIter(null);
            }
            // Ignore finalizer, since we don't have a useful place to call it
            return PNone.NONE;
        }
    }

    @Builtin(name = "get_asyncgen_hooks")
    @GenerateNodeFactory
    abstract static class GetAsyncgenHooks extends PythonBuiltinNode {
        @Specialization
        Object setAsyncgenHooks() {
            // TODO: use asyncgen_hooks object
            PythonContext.PythonThreadState threadState = getContext().getThreadState(getLanguage());
            Object firstiter = threadState.getAsyncgenFirstIter();
            return factory().createTuple(new Object[]{firstiter == null ? PNone.NONE : firstiter, PNone.NONE});
        }
    }

    @Builtin(name = "get_coroutine_origin_tracking_depth")
    @GenerateNodeFactory
    abstract static class GetCoroOriginTrackingDepth extends PythonBuiltinNode {

        @Specialization
        Object getCoroDepth() {
            // TODO: Implement
            return 0;
        }
    }

    @Builtin(name = "set_coroutine_origin_tracking_depth", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class SetCoroOriginTrackingDepth extends PythonUnaryBuiltinNode {

        @Specialization
        Object setCoroDepth(Object newValue) {
            // TODO: Implement
            return PNone.NONE;
        }
    }

    @Builtin(name = J_UNRAISABLEHOOK, minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 2, declaresExplicitSelf = true, doc = "unraisablehook($module, unraisable, /)\n" +
                    "--\n" +
                    "\n" +
                    "Handle an unraisable exception.\n" +
                    "\n" +
                    "The unraisable argument has the following attributes:\n" +
                    "\n" +
                    "* exc_type: Exception type.\n" +
                    "* exc_value: Exception value, can be None.\n" +
                    "* exc_traceback: Exception traceback, can be None.\n" +
                    "* err_msg: Error message, can be None.\n" +
                    "* object: Object causing the exception, can be None.")
    @GenerateNodeFactory
    abstract static class UnraisableHookNode extends PythonBuiltinNode {
        @Child private PyTraceBackPrintNode pyTraceBackPrintNode;

        private void printTraceBack(VirtualFrame frame, PythonModule sys, Object out, Object tb) {
            if (pyTraceBackPrintNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                pyTraceBackPrintNode = insert(PyTraceBackPrintNode.create());
            }
            pyTraceBackPrintNode.execute(frame, sys, out, tb);
        }

        private void writeUnraisableExc(MaterializedFrame frame, PythonModule sys, Object out,
                        Object excType, Object excValue, Object excTb, Object errMsg, Object obj) {
            if (obj != PNone.NONE) {
                if (errMsg != PNone.NONE) {
                    PyTraceBackPrintNode.fileWriteObject(frame, out, errMsg, true);
                    fileWriteString(frame, out, ": ");
                } else {
                    fileWriteString(frame, out, "Exception ignored in: ");
                }

                if (!PyTraceBackPrintNode.fileWriteObject(frame, out, obj, false)) {
                    fileWriteString(frame, out, "<object repr() failed>");
                }
                fileWriteString(frame, out, T_NEWLINE);
            } else if (errMsg != PNone.NONE) {
                PyTraceBackPrintNode.fileWriteObject(frame, out, errMsg, true);
                fileWriteString(frame, out, ":\n");
            }

            if (excTb != PNone.NONE) {
                printTraceBack(frame, sys, out, excTb);
            }

            if (excType == PNone.NONE) {
                return;
            }

            TruffleString className;
            try {
                className = getTypeName(excType);
                className = classNameNoDot(className);
            } catch (PException pe) {
                className = null;
            }
            TruffleString moduleName;
            Object v = objectLookupAttr(frame, excType, T___MODULE__);
            if (v == PNone.NO_VALUE || !PGuards.isString(v)) {
                fileWriteString(frame, out, T_VALUE_UNKNOWN);
            } else {
                moduleName = castToString(v);
                if (!moduleName.equalsUncached(T_BUILTINS, TS_ENCODING)) {
                    fileWriteString(frame, out, moduleName);
                    fileWriteString(frame, out, T_DOT);
                }
            }
            if (className == null) {
                fileWriteString(frame, out, T_VALUE_UNKNOWN);
            } else {
                fileWriteString(frame, out, className);
            }

            if (excValue != PNone.NONE) {
                // only print colon if the str() of the object is not the empty string
                fileWriteString(frame, out, ": ");
                if (!PyTraceBackPrintNode.fileWriteObject(frame, out, excValue, true)) {
                    fileWriteString(frame, out, "<exception str() failed>");
                }
            }

            fileWriteString(frame, out, T_NEWLINE);
        }

        @Specialization
        Object doit(VirtualFrame frame, PythonModule sys, Object args,
                        @Cached TupleBuiltins.GetItemNode getItemNode) {
            final Object cls = getObjectClass(args);
            if (cls != PythonBuiltinClassType.PUnraisableHookArgs) {
                throw raise(TypeError, ARG_TYPE_MUST_BE, "sys.unraisablehook", "UnraisableHookArgs");
            }
            final Object excType = getItemNode.execute(frame, args, 0);
            final Object excValue = getItemNode.execute(frame, args, 1);
            final Object excTb = getItemNode.execute(frame, args, 2);
            final Object errMsg = getItemNode.execute(frame, args, 3);
            final Object obj = getItemNode.execute(frame, args, 4);

            Object stdErr = objectLookupAttr(frame, sys, T_STDERR);
            final MaterializedFrame materializedFrame = frame.materialize();
            writeUnraisableExc(materializedFrame, sys, stdErr, excType, excValue, excTb, errMsg, obj);
            fileFlush(materializedFrame, stdErr);
            return PNone.NONE;
        }
    }

    @Builtin(name = J_EXCEPTHOOK, minNumOfPositionalArgs = 4, maxNumOfPositionalArgs = 4, declaresExplicitSelf = true, doc = "excepthook($module, exctype, value, traceback, /)\n" +
                    "--\n" +
                    "\n" +
                    "Handle an exception by displaying it with a traceback on sys.stderr.")
    @GenerateNodeFactory
    abstract static class ExceptHookNode extends PythonBuiltinNode {
        static final TruffleString T_CAUSE_MESSAGE = tsLiteral("\nThe above exception was the direct cause of the following exception:\n\n");
        static final TruffleString T_CONTEXT_MESSAGE = tsLiteral("\nDuring handling of the above exception, another exception occurred:\n\n");
        static final TruffleString T_ATTR_PRINT_FILE_AND_LINE = tsLiteral("print_file_and_line");
        static final TruffleString T_ATTR_MSG = tsLiteral("msg");
        static final TruffleString T_ATTR_FILENAME = tsLiteral("filename");
        static final TruffleString T_ATTR_LINENO = tsLiteral("lineno");
        static final TruffleString T_ATTR_OFFSET = tsLiteral("offset");
        static final TruffleString T_ATTR_TEXT = tsLiteral("text");

        @Child private PyTraceBackPrintNode pyTraceBackPrintNode;

        private void printTraceBack(VirtualFrame frame, PythonModule sys, Object out, Object tb) {
            if (pyTraceBackPrintNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                pyTraceBackPrintNode = insert(PyTraceBackPrintNode.create());
            }
            pyTraceBackPrintNode.execute(frame, sys, out, tb);
        }

        @CompilerDirectives.ValueType
        static final class SyntaxErrData {
            final Object message;
            final TruffleString fileName;
            final int lineNo;
            final int offset;
            final TruffleString text;
            final boolean err;

            SyntaxErrData(Object message, TruffleString fileName, int lineNo, int offset, TruffleString text, boolean err) {
                this.message = message;
                this.fileName = fileName;
                this.lineNo = lineNo;
                this.offset = offset;
                this.text = text;
                this.err = err;
            }
        }

        private static SyntaxErrData parseSyntaxError(VirtualFrame frame, Object err) {
            Object v, msg;
            TruffleString fileName = null, text = null;
            int lineNo = 0, offset = 0, hold;

            // new style errors. `err' is an instance
            msg = objectLookupAttr(frame, err, T_ATTR_MSG);
            if (msg == PNone.NO_VALUE) {
                return new SyntaxErrData(null, fileName, lineNo, offset, text, true);
            }

            v = objectLookupAttr(frame, err, T_ATTR_FILENAME);
            if (v == PNone.NO_VALUE) {
                return new SyntaxErrData(msg, fileName, lineNo, offset, text, true);
            }
            if (v == PNone.NONE) {
                fileName = T_STRING_SOURCE;
            } else {
                fileName = castToString(objectStr(frame, v));
            }

            v = objectLookupAttr(frame, err, T_ATTR_LINENO);
            if (v == PNone.NO_VALUE) {
                return new SyntaxErrData(msg, fileName, lineNo, offset, text, true);
            }
            try {
                hold = longAsInt(frame, v);
            } catch (PException pe) {
                return new SyntaxErrData(msg, fileName, lineNo, offset, text, true);
            }

            lineNo = hold;

            v = objectLookupAttr(frame, err, T_ATTR_OFFSET);
            if (v == PNone.NO_VALUE) {
                return new SyntaxErrData(msg, fileName, lineNo, offset, text, true);
            }
            if (v == PNone.NONE) {
                offset = -1;
            } else {
                try {
                    hold = longAsInt(frame, v);
                } catch (PException pe) {
                    return new SyntaxErrData(msg, fileName, lineNo, offset, text, true);
                }
                offset = hold;
            }

            v = objectLookupAttr(frame, err, T_ATTR_TEXT);
            if (v == PNone.NO_VALUE) {
                return new SyntaxErrData(msg, fileName, lineNo, offset, text, true);
            }
            if (v == PNone.NONE) {
                text = null;
            } else {
                text = castToString(v);
            }

            return new SyntaxErrData(msg, fileName, lineNo, offset, text, false);
        }

        private static void printErrorText(VirtualFrame frame, Object out, SyntaxErrData syntaxErrData) {
            TruffleString text = castToString(objectStr(frame, syntaxErrData.text));
            int textLen = text.codePointLengthUncached(TS_ENCODING);
            int offset = syntaxErrData.offset;

            if (offset >= 0) {
                if (offset > 0 && offset == textLen && text.codePointAtIndexUncached(offset - 1, TS_ENCODING) == '\n') {
                    offset--;
                }
                int nl;
                while (true) {
                    nl = text.lastIndexOfCodePointUncached('\n', textLen, 0, TS_ENCODING);
                    if (nl < 0 || nl >= offset) {
                        break;
                    }
                    offset -= nl + 1;
                    text = text.substringUncached(nl + 1, textLen, TS_ENCODING, true);
                    textLen = text.codePointLengthUncached(TS_ENCODING);
                }
                if (!text.isEmpty()) {
                    int idx = 0;
                    while (true) {
                        int cp = text.codePointAtIndexUncached(idx, TS_ENCODING);
                        if (!(cp == ' ' || cp == '\t' || cp == '\f')) {
                            break;
                        }
                        idx++;
                        offset--;
                    }
                    text = text.substringUncached(idx, textLen - idx, TS_ENCODING, true);
                }
            }

            fileWriteString(frame, out, "    ");
            fileWriteString(frame, out, text);
            if (text.isEmpty() || text.codePointAtIndexUncached(text.codePointLengthUncached(TS_ENCODING) - 1, TS_ENCODING) != '\n') {
                fileWriteString(frame, out, T_NEWLINE);
            }
            if (offset == -1) {
                return;
            }
            fileWriteString(frame, out, "    ");
            while (--offset > 0) {
                fileWriteString(frame, out, " ");
            }
            fileWriteString(frame, out, "^\n");
        }

        @TruffleBoundary
        void printExceptionRecursive(MaterializedFrame frame, PythonModule sys, Object out, Object value, Set<Object> seen) {
            if (seen != null) {
                // Exception chaining
                add(seen, value);
                if (PyExceptionInstanceCheckNode.executeUncached(value)) {
                    Object cause = ExceptionNodes.GetCauseNode.executeUncached(value);
                    Object context = ExceptionNodes.GetContextNode.executeUncached(value);

                    if (cause != PNone.NONE) {
                        if (notSeen(seen, cause)) {
                            printExceptionRecursive(frame, sys, out, cause, seen);
                            fileWriteString(frame, out, T_CAUSE_MESSAGE);
                        }
                    } else if (context != PNone.NONE && !ExceptionNodes.GetSuppressContextNode.executeUncached(value)) {
                        if (notSeen(seen, context)) {
                            printExceptionRecursive(frame, sys, out, context, seen);
                            fileWriteString(frame, out, T_CONTEXT_MESSAGE);
                        }
                    }
                }
            }
            printException(frame, sys, out, value);
        }

        protected void printException(VirtualFrame frame, PythonModule sys, Object out, Object excValue) {
            Object value = excValue;
            final Object type = getObjectClass(value);
            if (!PGuards.isPBaseException(value)) {
                fileWriteString(frame, out, "TypeError: print_exception(): Exception expected for value, ");
                fileWriteString(frame, out, getTypeName(type));
                fileWriteString(frame, out, " found\n");
                return;
            }

            final PBaseException exc = (PBaseException) value;
            final Object tb = getExceptionTraceback(exc);
            if (tb instanceof PTraceback) {
                printTraceBack(frame, sys, out, tb);
            }

            if (objectHasAttr(frame, value, T_ATTR_PRINT_FILE_AND_LINE)) {
                // SyntaxError case
                final SyntaxErrData syntaxErrData = parseSyntaxError(frame, value);
                if (!syntaxErrData.err) {
                    value = syntaxErrData.message;
                    StringBuilder sb = newStringBuilder("  File \"");
                    append(sb, castToString(objectStr(frame, syntaxErrData.fileName)), "\", line ", syntaxErrData.lineNo, "\n");
                    fileWriteString(frame, out, sbToString(sb));

                    // Can't be bothered to check all those PyFile_WriteString() calls
                    if (syntaxErrData.text != null) {
                        printErrorText(frame, out, syntaxErrData);
                    }
                }
            }

            TruffleString className;
            try {
                className = getTypeName(type);
                className = classNameNoDot(className);
            } catch (PException pe) {
                className = null;
            }
            TruffleString moduleName;
            Object v = objectLookupAttr(frame, type, T___MODULE__);
            if (v == PNone.NO_VALUE || !PGuards.isString(v)) {
                fileWriteString(frame, out, T_VALUE_UNKNOWN);
            } else {
                moduleName = castToString(v);
                if (!moduleName.equalsUncached(T_BUILTINS, TS_ENCODING)) {
                    fileWriteString(frame, out, moduleName);
                    fileWriteString(frame, out, T_DOT);
                }
            }
            if (className == null) {
                fileWriteString(frame, out, T_VALUE_UNKNOWN);
            } else {
                fileWriteString(frame, out, className);
            }

            if (value != PNone.NONE) {
                // only print colon if the str() of the object is not the empty string
                v = objectStr(frame, value);
                TruffleString s = tryCastToString(v);
                if (v == null) {
                    fileWriteString(frame, out, ": <exception str() failed>");
                } else if (!PGuards.isString(v) || (s != null && !s.isEmpty())) {
                    fileWriteString(frame, out, ": ");
                }
                if (s != null) {
                    fileWriteString(frame, out, s);
                }
            }

            fileWriteString(frame, out, T_NEWLINE);
        }

        @TruffleBoundary(allowInlining = true)
        private static StringBuilder newStringBuilder(String str) {
            return new StringBuilder(str);
        }

        @TruffleBoundary(allowInlining = true)
        private static String sbToString(StringBuilder sb) {
            return sb.toString();
        }

        @TruffleBoundary(allowInlining = true)
        private static StringBuilder append(StringBuilder sb, Object... args) {
            for (Object arg : args) {
                sb.append(arg);
            }
            return sb;
        }

        @TruffleBoundary
        static void add(Set<Object> set, Object value) {
            set.add(value);
        }

        @TruffleBoundary
        static boolean notSeen(Set<Object> set, Object value) {
            return !set.contains(value);
        }

        @TruffleBoundary
        static Set<Object> createSet() {
            return new HashSet<>();
        }

        @Specialization
        Object doHookWithTb(VirtualFrame frame, PythonModule sys, @SuppressWarnings("unused") Object excType, Object value, PTraceback traceBack) {
            setExceptionTraceback(value, traceBack);
            final MaterializedFrame materializedFrame = frame.materialize();
            Object stdErr = objectLookupAttr(materializedFrame, sys, T_STDERR);
            printExceptionRecursive(materializedFrame, sys, stdErr, value, createSet());
            fileFlush(materializedFrame, stdErr);

            return PNone.NONE;
        }

        @Specialization(guards = "!isPTraceback(traceBack)")
        Object doHookWithoutTb(VirtualFrame frame, PythonModule sys, @SuppressWarnings("unused") Object excType, Object value, @SuppressWarnings("unused") Object traceBack) {
            final MaterializedFrame materializedFrame = frame.materialize();
            Object stdErr = objectLookupAttr(materializedFrame, sys, T_STDERR);
            printExceptionRecursive(materializedFrame, sys, stdErr, value, createSet());
            fileFlush(materializedFrame, stdErr);

            return PNone.NONE;
        }
    }

    @Builtin(name = J_DISPLAYHOOK, minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 2, declaresExplicitSelf = true, doc = "displayhook($module, object, /)\n" +
                    "--\n" +
                    "\n" +
                    "Print an object to sys.stdout and also save it in builtins._")
    @GenerateNodeFactory
    abstract static class DisplayHookNode extends PythonBuiltinNode {

        @Specialization
        Object doHook(VirtualFrame frame, PythonModule sys, Object obj,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectSetAttr setAttr,
                        @Cached IsBuiltinObjectProfile unicodeEncodeErrorProfile,
                        @Cached PyObjectLookupAttr lookupAttr,
                        @Cached PyObjectCallMethodObjArgs callMethodObjArgs,
                        @Cached PyObjectGetAttr getAttr,
                        @Cached CallNode callNode,
                        @Cached PyObjectReprAsObjectNode reprAsObjectNode,
                        @Cached PyObjectStrAsObjectNode strAsObjectNode,
                        @Cached CastToTruffleStringNode castToStringNode,
                        @Cached PyUnicodeAsEncodedString pyUnicodeAsEncodedString,
                        @Cached PyUnicodeFromEncodedObject pyUnicodeFromEncodedObject) {
            final PythonModule builtins = getContext().getBuiltins();
            if (builtins == null) {
                throw raise(RuntimeError, LOST_S, "builtins module");
            }
            // Print value except if None
            // After printing, also assign to '_'
            // Before, set '_' to None to avoid recursion
            if (obj == PNone.NONE) {
                return PNone.NONE;
            }

            setAttr.execute(frame, builtins, T___, PNone.NONE);
            Object stdOut = objectLookupAttr(frame, sys, T_STDOUT, lookupAttr);
            if (PGuards.isPNone(stdOut)) {
                throw raise(RuntimeError, LOST_S, "sys.stdout");
            }

            boolean reprWriteOk = false;
            boolean unicodeEncodeError = false;
            try {
                Object reprVal = objectRepr(frame, obj, reprAsObjectNode);
                if (reprVal == null) {
                    reprWriteOk = false;
                } else {
                    reprWriteOk = true;
                    fileWriteString(frame, stdOut, castToString(reprVal, castToStringNode), getAttr, callNode);
                }
            } catch (PException pe) {
                pe.expect(inliningTarget, UnicodeEncodeError, unicodeEncodeErrorProfile);
                // repr(o) is not encodable to sys.stdout.encoding with sys.stdout.errors error
                // handler (which is probably 'strict')
                unicodeEncodeError = true;
            }
            if (!reprWriteOk && unicodeEncodeError) {
                // inlined sysDisplayHookUnencodable
                final TruffleString stdoutEncoding = objectLookupAttrAsString(frame, stdOut, T_ENCODING, lookupAttr, castToStringNode);
                final Object reprStr = objectRepr(frame, obj, reprAsObjectNode);
                final Object encoded = pyUnicodeAsEncodedString.execute(frame, reprStr, stdoutEncoding, T_BACKSLASHREPLACE);

                final Object buffer = objectLookupAttr(frame, stdOut, T_BUFFER, lookupAttr);
                if (buffer != null) {
                    callMethodObjArgs.execute(frame, buffer, T_WRITE, encoded);
                } else {
                    Object escapedStr = pyUnicodeFromEncodedObject.execute(frame, encoded, stdoutEncoding, T_STRICT);
                    final Object str = objectStr(frame, escapedStr, strAsObjectNode);
                    fileWriteString(frame, stdOut, castToString(str, castToStringNode), getAttr, callNode);
                }
            }

            fileWriteString(frame, stdOut, T_NEWLINE, getAttr, callNode);
            setAttr.execute(frame, builtins, T___, obj);
            return PNone.NONE;
        }
    }

    @Builtin(name = J_BREAKPOINTHOOK, takesVarKeywordArgs = true, takesVarArgs = true, doc = "breakpointhook(*args, **kws)\n" +
                    "\n" +
                    "This hook function is called by built-in breakpoint().\n")
    @GenerateNodeFactory
    abstract static class BreakpointHookNode extends PythonBuiltinNode {
        static final TruffleString T_VAL_PDB_SETTRACE = tsLiteral("pdb.set_trace");
        static final TruffleString T_MOD_OS = tsLiteral("os");
        static final TruffleString T_ATTR_ENVIRON = tsLiteral("environ");

        private static TruffleString getEnvVar(VirtualFrame frame, PyImportImport importNode, PyObjectGetAttr getAttr, PyObjectCallMethodObjArgs callMethodObjArgs,
                        CastToTruffleStringNode castToStringNode) {
            Object os = importNode.execute(frame, T_MOD_OS);
            final Object environ = getAttr.execute(frame, os, T_ATTR_ENVIRON);
            Object var = callMethodObjArgs.execute(frame, environ, T_GET, T_PYTHONBREAKPOINT);
            try {
                return castToStringNode.execute(var);
            } catch (CannotCastException cce) {
                return null;
            }
        }

        @Specialization
        Object doHook(VirtualFrame frame, Object[] args, PKeyword[] keywords,
                        @Bind("this") Node inliningTarget,
                        @Cached CallNode callNode,
                        @Cached PyObjectGetAttr getAttr,
                        @Cached PyImportImport importNode,
                        @Cached PyObjectCallMethodObjArgs callMethodObjArgs,
                        @Cached IsBuiltinObjectProfile attrErrorProfile,
                        @Cached CastToTruffleStringNode castToStringNode,
                        @Cached BuiltinFunctions.IsInstanceNode isInstanceNode,
                        @Cached WarningsModuleBuiltins.WarnNode warnNode,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.CodePointAtIndexNode codePointAtIndexNode,
                        @Cached TruffleString.LastIndexOfCodePointNode lastIndexOfCodePointNode,
                        @Cached TruffleString.SubstringNode substringNode) {
            TruffleString hookName = getEnvVar(frame, importNode, getAttr, callMethodObjArgs, castToStringNode);
            if (hookName == null || hookName.isEmpty()) {
                hookName = T_VAL_PDB_SETTRACE;
            }

            int hookNameLen = codePointLengthNode.execute(hookName, TS_ENCODING);
            if (hookNameLen == 1 && codePointAtIndexNode.execute(hookName, 0, TS_ENCODING) == '0') {
                // The breakpoint is explicitly no-op'd.
                return PNone.NONE;
            }

            final int lastDot = lastIndexOfCodePointNode.execute(hookName, '.', hookNameLen, 0, TS_ENCODING);
            final TruffleString modPath;
            final TruffleString attrName;
            if (lastDot < 0) {
                // The breakpoint is a built-in, e.g. PYTHONBREAKPOINT=int
                modPath = T_BUILTINS;
                attrName = hookName;
            } else if (lastDot != 0) {
                // Split on the last dot
                modPath = substringNode.execute(hookName, 0, lastDot, TS_ENCODING, true);
                attrName = substringNode.execute(hookName, lastDot + 1, hookNameLen - (lastDot + 1), TS_ENCODING, true);
            } else {
                warnNode.warnFormat(frame, RuntimeWarning, WARN_IGNORE_UNIMPORTABLE_BREAKPOINT_S, hookName);
                return PNone.NONE;
            }

            final Object module;
            try {
                module = importNode.execute(frame, modPath);
            } catch (PException pe) {
                if (isInstanceNode.executeWith(frame, pe.getUnreifiedException(), ImportError)) {
                    warnNode.warnFormat(frame, RuntimeWarning, WARN_IGNORE_UNIMPORTABLE_BREAKPOINT_S, hookName);
                }
                return PNone.NONE;
            }

            final Object hook;
            try {
                hook = getAttr.execute(frame, module, attrName);
            } catch (PException pe) {
                if (attrErrorProfile.profileException(inliningTarget, pe, AttributeError)) {
                    warnNode.warnFormat(frame, RuntimeWarning, WARN_IGNORE_UNIMPORTABLE_BREAKPOINT_S, hookName);
                }
                return PNone.NONE;
            }

            return callNode.execute(hook, args, keywords);
        }
    }

    @Builtin(name = "getrecursionlimit", minNumOfPositionalArgs = 1, declaresExplicitSelf = true, doc = "getrecursionlimit($module, /)\n" +
                    "--\n" +
                    "\n" +
                    "Return the current value of the recursion limit.\n" +
                    "\n" +
                    "The recursion limit is the maximum depth of the Python interpreter\n" +
                    "stack.  This limit prevents infinite recursion from causing an overflow\n" +
                    "of the C stack and crashing Python.")
    @GenerateNodeFactory
    abstract static class GetRecursionLimitNode extends PythonBuiltinNode {
        @Specialization
        Object getRecLim(@SuppressWarnings("unused") PythonModule sys) {
            return getContext().getSysModuleState().getRecursionLimit();
        }
    }

    @Builtin(name = "setrecursionlimit", minNumOfPositionalArgs = 2, declaresExplicitSelf = true, doc = "setrecursionlimit($module, limit, /)\n" +
                    "--\n" +
                    "\n" +
                    "Set the maximum depth of the Python interpreter stack to n.\n" +
                    "\n" +
                    "This limit prevents infinite recursion from causing an overflow of the C\n" +
                    "stack and crashing Python.  The highest possible limit is platform-\n" +
                    "dependent.")
    @GenerateNodeFactory
    abstract static class SetRecursionLimitNode extends PythonBuiltinNode {
        @Specialization
        Object setRecLim(VirtualFrame frame, @SuppressWarnings("unused") PythonModule sys, Object limit,
                        @Cached PyLongAsIntNode longAsIntNode,
                        @Cached PyFloatCheckExactNode floatCheckExactNode) {
            if (floatCheckExactNode.execute(limit)) {
                throw raise(TypeError, S_EXPECTED_GOT_P, "integer", limit);
            }

            int newLimit;
            try {
                newLimit = longAsIntNode.execute(frame, limit);
            } catch (PException pe) {
                newLimit = -1;
            }

            if (newLimit < 1) {
                throw raise(ValueError, REC_LIMIT_GREATER_THAN_1);
            }

            // TODO: check to see if Issue #25274 applies
            getContext().getSysModuleState().setRecursionLimit(newLimit);
            return PNone.NONE;
        }
    }

    @Builtin(name = "getcheckinterval", minNumOfPositionalArgs = 1, declaresExplicitSelf = true, doc = "getcheckinterval($module, /)\n" +
                    "--\n" +
                    "\n" +
                    "Return the current check interval; see sys.setcheckinterval().")
    @GenerateNodeFactory
    abstract static class GetCheckIntervalNode extends PythonBuiltinNode {
        @Specialization
        Object getCheckInterval(VirtualFrame frame, @SuppressWarnings("unused") PythonModule sys,
                        @Cached WarningsModuleBuiltins.WarnNode warnNode) {
            warnNode.warnFormat(frame, DeprecationWarning, WARN_DEPRECTATED_SYS_CHECKINTERVAL);
            return getContext().getSysModuleState().getCheckInterval();
        }
    }

    @Builtin(name = "setcheckinterval", minNumOfPositionalArgs = 2, declaresExplicitSelf = true, doc = "setcheckinterval($module, n, /)\n" +
                    "--\n" +
                    "\n" +
                    "Set the async event check interval to n instructions.\n" +
                    "\n" +
                    "This tells the Python interpreter to check for asynchronous events\n" +
                    "every n instructions.\n" +
                    "\n" +
                    "This also affects how often thread switches occur.")
    @GenerateNodeFactory
    abstract static class SetCheckIntervalNode extends PythonBuiltinNode {
        @Specialization
        Object setCheckInterval(VirtualFrame frame, @SuppressWarnings("unused") PythonModule sys, Object arg,
                        @Cached WarningsModuleBuiltins.WarnNode warnNode,
                        @Cached PyLongAsIntNode longAsIntNode,
                        @Cached PyFloatCheckExactNode floatCheckExactNode) {
            if (floatCheckExactNode.execute(arg)) {
                throw raise(TypeError, S_EXPECTED_GOT_P, "integer", arg);
            }

            try {
                final int n = longAsIntNode.execute(frame, arg);
                warnNode.warnFormat(frame, DeprecationWarning, WARN_DEPRECTATED_SYS_CHECKINTERVAL);
                getContext().getSysModuleState().setCheckInterval(n);
            } catch (PException ignore) {
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "getswitchinterval", minNumOfPositionalArgs = 1, declaresExplicitSelf = true, doc = "getswitchinterval($module, /)\n" +
                    "--\n" +
                    "\n" +
                    "Return the current thread switch interval; see sys.setswitchinterval().")
    @GenerateNodeFactory
    abstract static class GetSwitchIntervalNode extends PythonBuiltinNode {
        private static final double FACTOR = 1.e-6;

        @Specialization
        Object getCheckInterval(@SuppressWarnings("unused") PythonModule sys) {
            return FACTOR * getContext().getSysModuleState().getSwitchInterval();
        }
    }

    @Builtin(name = "setswitchinterval", minNumOfPositionalArgs = 2, declaresExplicitSelf = true, doc = "setswitchinterval($module, interval, /)\n" +
                    "--\n" +
                    "\n" +
                    "Set the ideal thread switching delay inside the Python interpreter.\n" +
                    "\n" +
                    "The actual frequency of switching threads can be lower if the\n" +
                    "interpreter executes long sequences of uninterruptible code" +
                    "(this is implementation-specific and workload-dependent).\n" +
                    "\n" +
                    "The parameter must represent the desired switching delay in seconds\n" +
                    "A typical value is 0.005 (5 milliseconds).")
    @GenerateNodeFactory
    abstract static class SetSwitchIntervalNode extends PythonBuiltinNode {
        private static final double FACTOR = 1.e6;

        @Specialization
        Object setCheckInterval(VirtualFrame frame, @SuppressWarnings("unused") PythonModule sys, Object arg,
                        @Cached PyFloatAsDoubleNode floatAsDoubleNode) {
            double interval = floatAsDoubleNode.execute(frame, arg);
            if (interval <= 0.0) {
                throw raise(ValueError, SWITCH_INTERVAL_MUST_BE_POSITIVE);
            }
            getContext().getSysModuleState().setSwitchInterval(FACTOR * interval);
            return PNone.NONE;
        }
    }

    @Builtin(name = J_EXIT, declaresExplicitSelf = true, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, doc = "exit($module, status=None, /)\n" +
                    "--\n" +
                    "\n" +
                    "Exit the interpreter by raising SystemExit(status).\n" +
                    "\n" +
                    "If the status is omitted or None, it defaults to zero (i.e., success).\n" +
                    "If the status is an integer, it will be used as the system exit status.\n" +
                    "If it is another kind of object, it will be printed and the system\n" +
                    "exit status will be one (i.e., failure).")
    @GenerateNodeFactory
    abstract static class ExitNode extends PythonBinaryBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        Object exitNoCode(PythonModule sys, PNone status) {
            throw raiseSystemExit(PNone.NONE);
        }

        @Specialization(guards = "!isPNone(status)")
        Object exit(VirtualFrame frame, @SuppressWarnings("unused") PythonModule sys, Object status,
                        @Cached TupleBuiltins.GetItemNode getItemNode) {
            Object code = status;
            if (status instanceof PTuple) {
                if (((PTuple) status).getSequenceStorage().length() == 1) {
                    code = getItemNode.execute(frame, status, 0);
                }
            }
            throw raiseSystemExit(code);
        }
    }

    @Builtin(name = "get_int_max_str_digits")
    @GenerateNodeFactory
    abstract static class GetIntMaxStrDigits extends PythonBuiltinNode {
        @Specialization
        int get() {
            return getContext().getIntMaxStrDigits();
        }
    }

    @Builtin(name = "set_int_max_str_digits", minNumOfPositionalArgs = 1, parameterNames = {"maxdigits"})
    @ArgumentClinic(name = "maxdigits", conversion = ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class SetIntMaxStrDigits extends PythonUnaryClinicBuiltinNode {
        @Specialization
        Object set(int value) {
            getContext().setIntMaxStrDigits(value);
            return PNone.NONE;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SysModuleBuiltinsClinicProviders.SetIntMaxStrDigitsClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "getdlopenflags")
    @GenerateNodeFactory
    abstract static class GetDlopenFlags extends PythonBuiltinNode {
        @Specialization
        Object get() {
            return getContext().getDlopenFlags();
        }
    }

    @Builtin(name = "setdlopenflags", minNumOfPositionalArgs = 1, parameterNames = {"flags"}, numOfPositionalOnlyArgs = 1)
    @ArgumentClinic(name = "flags", conversion = ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class SetDlopenFlags extends PythonUnaryClinicBuiltinNode {
        @Specialization
        Object set(int flags) {
            getContext().setDlopenFlags(flags);
            return PNone.NONE;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SetDlopenFlagsClinicProviderGen.INSTANCE;
        }
    }
}
