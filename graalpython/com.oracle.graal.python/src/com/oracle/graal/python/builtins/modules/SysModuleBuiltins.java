/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.PythonLanguage.RELEASE_LEVEL;
import static com.oracle.graal.python.PythonLanguage.RELEASE_SERIAL;
import static com.oracle.graal.python.PythonLanguage.T_GRAALPYTHON_ID;
import static com.oracle.graal.python.PythonLanguage.getPythonOS;
import static com.oracle.graal.python.annotations.PythonOS.PLATFORM_DARWIN;
import static com.oracle.graal.python.annotations.PythonOS.PLATFORM_WIN32;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.AttributeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.DeprecationWarning;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ImportError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.RuntimeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.RuntimeWarning;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.UnicodeEncodeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_BUFFER;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_ENCODING;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_MODE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_R;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_W;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_WRITE;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyObject__ob_refcnt;
import static com.oracle.graal.python.builtins.objects.str.StringUtils.cat;
import static com.oracle.graal.python.lib.PyTraceBackPrint.castToString;
import static com.oracle.graal.python.lib.PyTraceBackPrint.classNameNoDot;
import static com.oracle.graal.python.lib.PyTraceBackPrint.fileFlush;
import static com.oracle.graal.python.lib.PyTraceBackPrint.fileWriteString;
import static com.oracle.graal.python.lib.PyTraceBackPrint.getExceptionTraceback;
import static com.oracle.graal.python.lib.PyTraceBackPrint.getObjectClass;
import static com.oracle.graal.python.lib.PyTraceBackPrint.getTypeName;
import static com.oracle.graal.python.lib.PyTraceBackPrint.objectHasAttr;
import static com.oracle.graal.python.lib.PyTraceBackPrint.objectLookupAttr;
import static com.oracle.graal.python.lib.PyTraceBackPrint.objectLookupAttrAsString;
import static com.oracle.graal.python.lib.PyTraceBackPrint.objectRepr;
import static com.oracle.graal.python.lib.PyTraceBackPrint.objectStr;
import static com.oracle.graal.python.lib.PyTraceBackPrint.setExceptionTraceback;
import static com.oracle.graal.python.lib.PyTraceBackPrint.tryCastToString;
import static com.oracle.graal.python.nodes.BuiltinNames.J_BREAKPOINTHOOK;
import static com.oracle.graal.python.nodes.BuiltinNames.J_DISPLAYHOOK;
import static com.oracle.graal.python.nodes.BuiltinNames.J_EXCEPTHOOK;
import static com.oracle.graal.python.nodes.BuiltinNames.J_EXIT;
import static com.oracle.graal.python.nodes.BuiltinNames.J_MD5;
import static com.oracle.graal.python.nodes.BuiltinNames.J_SHA1;
import static com.oracle.graal.python.nodes.BuiltinNames.J_SHA2;
import static com.oracle.graal.python.nodes.BuiltinNames.J_SHA3;
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
import static com.oracle.graal.python.nodes.BuiltinNames.T___NOTES__;
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
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___SIZEOF__;
import static com.oracle.graal.python.nodes.StringLiterals.J_NEWLINE;
import static com.oracle.graal.python.nodes.StringLiterals.T_BACKSLASHREPLACE;
import static com.oracle.graal.python.nodes.StringLiterals.T_BASE_PREFIX;
import static com.oracle.graal.python.nodes.StringLiterals.T_BIG;
import static com.oracle.graal.python.nodes.StringLiterals.T_COMMA;
import static com.oracle.graal.python.nodes.StringLiterals.T_DASH;
import static com.oracle.graal.python.nodes.StringLiterals.T_DOT;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_STRING;
import static com.oracle.graal.python.nodes.StringLiterals.T_JAVA;
import static com.oracle.graal.python.nodes.StringLiterals.T_LITTLE;
import static com.oracle.graal.python.nodes.StringLiterals.T_NEWLINE;
import static com.oracle.graal.python.nodes.StringLiterals.T_PREFIX;
import static com.oracle.graal.python.nodes.StringLiterals.T_SPACE;
import static com.oracle.graal.python.nodes.StringLiterals.T_STRICT;
import static com.oracle.graal.python.nodes.StringLiterals.T_STRING_SOURCE;
import static com.oracle.graal.python.nodes.StringLiterals.T_SURROGATEESCAPE;
import static com.oracle.graal.python.nodes.StringLiterals.T_VALUE_UNKNOWN;
import static com.oracle.graal.python.nodes.StringLiterals.T_VERSION;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;
import static com.oracle.graal.python.util.PythonUtils.tsInternedLiteral;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.annotations.PythonOS;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltinsClinicProviders.GetFrameNodeClinicProviderGen;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltinsClinicProviders.SetDlopenFlagsClinicProviderGen;
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
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper.PythonAbstractObjectNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.common.EconomicMapStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItem;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.exception.ExceptionNodes;
import com.oracle.graal.python.builtins.objects.exception.GetEscapedExceptionNode;
import com.oracle.graal.python.builtins.objects.exception.PBaseExceptionGroup;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.iterator.IteratorNodes;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.namespace.PSimpleNamespace;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.set.PFrozenSet;
import com.oracle.graal.python.builtins.objects.str.StringNodes;
import com.oracle.graal.python.builtins.objects.str.StringUtils;
import com.oracle.graal.python.builtins.objects.thread.PThread;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback;
import com.oracle.graal.python.builtins.objects.traceback.TracebackBuiltins;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.tuple.StructSequence;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltins;
import com.oracle.graal.python.lib.OsEnvironGetNode;
import com.oracle.graal.python.lib.PyExceptionGroupInstanceCheckNode;
import com.oracle.graal.python.lib.PyExceptionInstanceCheckNode;
import com.oracle.graal.python.lib.PyFloatAsDoubleNode;
import com.oracle.graal.python.lib.PyFloatCheckExactNode;
import com.oracle.graal.python.lib.PyImportImport;
import com.oracle.graal.python.lib.PyLongAsIntNode;
import com.oracle.graal.python.lib.PyLongAsIntNodeGen;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectReprAsObjectNode;
import com.oracle.graal.python.lib.PyObjectSetAttr;
import com.oracle.graal.python.lib.PyObjectStrAsObjectNode;
import com.oracle.graal.python.lib.PyTraceBackPrint;
import com.oracle.graal.python.lib.PyTupleCheckNode;
import com.oracle.graal.python.lib.PyTupleGetItem;
import com.oracle.graal.python.lib.PyUnicodeAsEncodedString;
import com.oracle.graal.python.lib.PyUnicodeFromEncodedObject;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.StringLiterals;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.call.special.SpecialMethodNotFound;
import com.oracle.graal.python.nodes.frame.ReadFrameNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes.GetCaughtExceptionNode;
import com.oracle.graal.python.runtime.CallerFlags;
import com.oracle.graal.python.runtime.ExecutionContext.BoundaryCallContext;
import com.oracle.graal.python.runtime.IndirectCallData.BoundaryCallData;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.formatting.IntegerFormatter;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.util.CharsetMapping;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
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

    private static final TruffleString[] SYS_PREFIX_ATTRIBUTES = new TruffleString[]{T_PREFIX, tsLiteral("exec_prefix")};
    private static final TruffleString[] BASE_PREFIX_ATTRIBUTES = new TruffleString[]{T_BASE_PREFIX, tsLiteral("base_exec_prefix")};

    static final StructSequence.BuiltinTypeDescriptor VERSION_INFO_DESC = new StructSequence.BuiltinTypeDescriptor(
                    PythonBuiltinClassType.PVersionInfo,
                    5,
                    new String[]{
                                    "major", "minor", "micro",
                                    "releaselevel", "serial"},
                    new String[]{
                                    "Major release number", "Minor release number", "Patch release number",
                                    "'alpha', 'beta', 'candidate', or 'final'", "Serial release number"});

    static final StructSequence.BuiltinTypeDescriptor WINDOWS_VER_DESC = new StructSequence.BuiltinTypeDescriptor(
                    PythonBuiltinClassType.PWindowsVersion,
                    5,
                    new String[]{
                                    "major", "minor", "build",
                                    "platform", "service_pack",
                                    "service_pack_major", "service_pack_minor",
                                    "suite_mask", "product_type", "platform_version"},
                    new String[]{
                                    "Major version number", "Minor version number", "Build number",
                                    "Operating system platform", "Latest Service Pack installed on the system",
                                    "Service Pack major version number", "Service Pack minor version number",
                                    "Bit mask identifying available product suites",
                                    "System product type", "Diagnostic version number"});

    static final StructSequence.BuiltinTypeDescriptor FLAGS_DESC = new StructSequence.BuiltinTypeDescriptor(
                    PythonBuiltinClassType.PFlags,
                    18,
                    new String[]{
                                    "debug",
                                    "inspect",
                                    "interactive",
                                    "optimize",
                                    "dont_write_bytecode",
                                    "no_user_site",
                                    "no_site",
                                    "ignore_environment",
                                    "verbose",
                                    "bytes_warning",
                                    "quiet",
                                    "hash_randomization",
                                    "isolated",
                                    "dev_mode",
                                    "utf8_mode",
                                    "warn_default_encoding",
                                    "safe_path",
                                    "int_max_str_digits",
                    },
                    new String[]{
                                    "-d",
                                    "-i",
                                    "-i",
                                    "-O or -OO",
                                    "-B",
                                    "-s",
                                    "-S",
                                    "-E",
                                    "-v",
                                    "-b",
                                    "-q",
                                    "-R",
                                    "-I",
                                    "-X dev",
                                    "-X utf8",
                                    "-X warn_default_encoding",
                                    "-P",
                                    "-X int_max_str_digits",
                    });

    static final StructSequence.BuiltinTypeDescriptor FLOAT_INFO_DESC = new StructSequence.BuiltinTypeDescriptor(
                    PythonBuiltinClassType.PFloatInfo,
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
                    3,
                    new String[]{
                                    "name", "lock", "version"},
                    new String[]{
                                    "name of the thread implementation", "name of the lock implementation",
                                    "name and version of the thread library"});

    public static final StructSequence.BuiltinTypeDescriptor UNRAISABLEHOOK_ARGS_DESC = new StructSequence.BuiltinTypeDescriptor(
                    PythonBuiltinClassType.PUnraisableHookArgs,
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
                    "_heapq", "_imp", "_io", "_json", "_locale", "_lsprof", "_lzma", "_markupbase", J_MD5, "_msi", "_multibytecodec", "_multiprocessing", "_opcode", "_operator", "_osx_support",
                    "_overlapped", "_pickle", "_posixshmem", "_posixsubprocess", "_py_abc", "_pydecimal", "_pyio", "_queue", "_random", "_scproxy", J_SHA1, J_SHA2, J_SHA3, "_signal",
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

    private static PSimpleNamespace makeImplementation(PythonLanguage language, PTuple graalpyVersionInfo, TruffleString gmultiarch) {
        final PSimpleNamespace ns = PFactory.createSimpleNamespace(language);
        ns.setAttribute(StringLiterals.T_NAME, T_GRAALPYTHON_ID);
        /*- 'cache_tag' must match the format of mx.graalpython/mx_graalpython.py:graalpy_ext */
        ns.setAttribute(T_CACHE_TAG, toTruffleStringUncached(J_GRAALPYTHON_ID +
                        PythonLanguage.GRAALVM_MAJOR + PythonLanguage.GRAALVM_MINOR + PythonLanguage.DEV_TAG +
                        "-" + PythonLanguage.MAJOR + PythonLanguage.MINOR));
        ns.setAttribute(T_VERSION, graalpyVersionInfo);
        ns.setAttribute(T__MULTIARCH, gmultiarch);
        ns.setAttribute(tsLiteral("hexversion"), PythonLanguage.GRAALVM_MAJOR << 24 | PythonLanguage.GRAALVM_MINOR << 16 | PythonLanguage.GRAALVM_MICRO << 8 | RELEASE_LEVEL << 4 | RELEASE_SERIAL);
        return ns;
    }

    @Override
    public void initialize(Python3Core core) {
        PythonLanguage language = core.getLanguage();
        StructSequence.initType(core, VERSION_INFO_DESC);
        if (getPythonOS() == PLATFORM_WIN32) {
            StructSequence.initType(core, WINDOWS_VER_DESC);
        }
        StructSequence.initType(core, FLAGS_DESC);
        StructSequence.initType(core, FLOAT_INFO_DESC);
        StructSequence.initType(core, INT_INFO_DESC);
        StructSequence.initType(core, HASH_INFO_DESC);
        StructSequence.initType(core, THREAD_INFO_DESC);
        StructSequence.initType(core, UNRAISABLEHOOK_ARGS_DESC);

        addBuiltinConstant("abiflags", T_EMPTY_STRING);
        addBuiltinConstant("byteorder", ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN ? T_LITTLE : T_BIG);
        addBuiltinConstant("copyright", T_LICENSE);
        addBuiltinConstant(T_MODULES, PFactory.createDict(language));
        addBuiltinConstant("path", PFactory.createList(language));
        addBuiltinConstant("builtin_module_names", PFactory.createTuple(language, core.builtinModuleNames()));
        addBuiltinConstant("maxsize", MAXSIZE);
        final PTuple versionInfo = PFactory.createStructSeq(language, VERSION_INFO_DESC, PythonLanguage.MAJOR, PythonLanguage.MINOR, PythonLanguage.MICRO, PythonLanguage.RELEASE_LEVEL_STRING,
                        PythonLanguage.RELEASE_SERIAL);
        addBuiltinConstant("version_info", versionInfo);
        addBuiltinConstant("api_version", PythonLanguage.API_VERSION);
        addBuiltinConstant("version", toTruffleStringUncached(PythonLanguage.VERSION +
                        " (" + COMPILE_TIME + ")" +
                        "\n[Graal, " + Truffle.getRuntime().getName() + ", Java " + System.getProperty("java.version") + " (" + System.getProperty("os.arch") + ")]"));
        addBuiltinConstant("float_info", PFactory.createStructSeq(language, FLOAT_INFO_DESC,
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
        addBuiltinConstant("int_info", PFactory.createStructSeq(language, INT_INFO_DESC, 32, 4, INT_DEFAULT_MAX_STR_DIGITS, INT_MAX_STR_DIGITS_THRESHOLD));
        addBuiltinConstant("hash_info", PFactory.createStructSeq(language, HASH_INFO_DESC,
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
        addBuiltinConstant("thread_info", PFactory.createStructSeq(language, THREAD_INFO_DESC, PNone.NONE, PNone.NONE, PNone.NONE));
        addBuiltinConstant("maxunicode", IntegerFormatter.LIMIT_UNICODE.intValue() - 1);

        PythonOS os = getPythonOS();
        TruffleString osName = toTruffleStringUncached(os.getName());
        addBuiltinConstant("platform", osName);
        if (os == PLATFORM_DARWIN) {
            addBuiltinConstant("_framework", FRAMEWORK);
        }
        final TruffleString gmultiarch = cat(PythonUtils.getPythonArch(), T_DASH, osName);
        addBuiltinConstant("__gmultiarch", gmultiarch);

        // Initialized later in postInitialize
        addBuiltinConstant(T_STDIN, PNone.NONE);
        addBuiltinConstant(T_STDOUT, PNone.NONE);
        addBuiltinConstant(T_STDERR, PNone.NONE);

        PTuple graalpyVersion = PFactory.createStructSeq(language, VERSION_INFO_DESC, PythonLanguage.GRAALVM_MAJOR, PythonLanguage.GRAALVM_MINOR, PythonLanguage.GRAALVM_MICRO,
                        PythonLanguage.RELEASE_LEVEL_STRING, PythonLanguage.RELEASE_SERIAL);
        addBuiltinConstant("graalpy_version_info", graalpyVersion);
        addBuiltinConstant("implementation", makeImplementation(language, graalpyVersion, gmultiarch));
        addBuiltinConstant("hexversion", PythonLanguage.VERSION_HEX);

        if (os == PLATFORM_WIN32) {
            addBuiltinConstant("winver", toTruffleStringUncached(PythonLanguage.MAJOR + "." + PythonLanguage.MINOR));
        }

        addBuiltinConstant("float_repr_style", "short");
        addBuiltinConstant("meta_path", PFactory.createList(language));
        addBuiltinConstant("path_hooks", PFactory.createList(language));
        addBuiltinConstant("path_importer_cache", PFactory.createDict(language));

        // default prompt for interactive shell
        addBuiltinConstant("ps1", ">>> ");
        // continue prompt for interactive shell
        addBuiltinConstant("ps2", "... ");
        // CPython builds for distros report empty strings too, because they are built from
        // tarballs, not git
        addBuiltinConstant("_git", PFactory.createTuple(language, new Object[]{T_GRAALPYTHON_ID, T_EMPTY_STRING, T_EMPTY_STRING}));

        if (os == PLATFORM_WIN32) {
            addBuiltinConstant("_vpath", "");
        }

        super.initialize(core);

        // we need these during core initialization, they are re-set in postInitialize
        postInitialize0(core);
    }

    public void postInitialize0(Python3Core core) {
        super.postInitialize(core);
        PythonModule sys = core.lookupBuiltinModule(T_SYS);
        PythonContext context = core.getContext();
        PythonLanguage language = core.getLanguage();
        String[] args = context.getEnv().getApplicationArguments();
        sys.setAttribute(tsInternedLiteral("argv"), PFactory.createList(language, convertToObjectArray(args)));
        sys.setAttribute(tsInternedLiteral("orig_argv"), PFactory.createList(language, convertToObjectArray(PythonOptions.getOrigArgv(context))));

        sys.setAttribute(tsInternedLiteral("stdlib_module_names"), createStdLibModulesSet(language));

        TruffleString prefix = context.getSysPrefix();
        for (TruffleString name : SysModuleBuiltins.SYS_PREFIX_ATTRIBUTES) {
            sys.setAttribute(name, prefix);
        }

        TruffleString base_prefix = context.getSysBasePrefix();
        for (TruffleString name : SysModuleBuiltins.BASE_PREFIX_ATTRIBUTES) {
            sys.setAttribute(name, base_prefix);
        }

        sys.setAttribute(tsInternedLiteral("platlibdir"), tsInternedLiteral("lib"));

        TruffleString coreHome = context.getCoreHome();
        TruffleString stdlibHome = context.getStdlibHome();
        TruffleString capiHome = context.getCAPIHome();

        if (!context.getEnv().isPreInitialization()) {
            TruffleString executable = context.getOption(PythonOptions.Executable);
            TruffleString baseExecutable = context.getOption(PythonOptions.BaseExecutable);
            sys.setAttribute(tsInternedLiteral("executable"), executable);
            sys.setAttribute(tsInternedLiteral("_base_executable"), baseExecutable.isEmpty() ? executable : baseExecutable);
        }
        sys.setAttribute(tsInternedLiteral("dont_write_bytecode"), context.getOption(PythonOptions.DontWriteBytecodeFlag));
        TruffleString pycachePrefix = context.getOption(PythonOptions.PyCachePrefix);
        if (pycachePrefix.isEmpty() && PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER && System.getenv("GRAALPY_BYTECODE_DSL_PYTHONPYCACHEPREFIX") != null) {
            pycachePrefix = PythonUtils.toTruffleStringUncached(System.getenv("GRAALPY_BYTECODE_DSL_PYTHONPYCACHEPREFIX"));
        }
        sys.setAttribute(tsInternedLiteral("pycache_prefix"), pycachePrefix.isEmpty() ? PNone.NONE : pycachePrefix);
        sys.setAttribute(tsInternedLiteral("_stdlib_dir"), stdlibHome);

        TruffleString strWarnoption = context.getOption(PythonOptions.WarnOptions);
        Object[] warnoptions;
        if (!strWarnoption.isEmpty()) {
            TruffleString[] strWarnoptions = StringUtils.split(strWarnoption, T_COMMA, TruffleString.CodePointLengthNode.getUncached(), TruffleString.IndexOfStringNode.getUncached(),
                            TruffleString.SubstringNode.getUncached(), TruffleString.EqualNode.getUncached());
            warnoptions = PythonUtils.convertToObjectArray(strWarnoptions);
        } else {
            warnoptions = PythonUtils.EMPTY_OBJECT_ARRAY;
        }
        sys.setAttribute(tsInternedLiteral("warnoptions"), PFactory.createList(language, warnoptions));

        Env env = context.getEnv();
        TruffleString pythonPath = context.getOption(PythonOptions.PythonPath);

        boolean capiSeparate = !capiHome.equalsUncached(coreHome, TS_ENCODING);

        Object[] path;
        int pathIdx = 0;
        int defaultPathsLen = 2;
        if (capiSeparate) {
            defaultPathsLen++;
        }
        if (!pythonPath.isEmpty()) {
            TruffleString sep = toTruffleStringUncached(context.getEnv().getPathSeparator());
            TruffleString[] split = StringUtils.split(pythonPath, sep, TruffleString.CodePointLengthNode.getUncached(), TruffleString.IndexOfStringNode.getUncached(),
                            TruffleString.SubstringNode.getUncached(), TruffleString.EqualNode.getUncached());
            path = new Object[split.length + defaultPathsLen];
            System.arraycopy(split, 0, path, 0, split.length);
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
        PList sysPaths = PFactory.createList(language, path);
        sys.setAttribute(tsInternedLiteral("path"), sysPaths);
        sys.setAttribute(tsInternedLiteral("flags"), PFactory.createStructSeq(language, SysModuleBuiltins.FLAGS_DESC,
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
                        context.getOption(PythonOptions.SafePathFlag), // safe_path
                        context.getOption(PythonOptions.IntMaxStrDigits) // int_max_str_digits
        ));
        sys.setAttribute(T___EXCEPTHOOK__, sys.getAttribute(T_EXCEPTHOOK));
        sys.setAttribute(T___UNRAISABLEHOOK__, sys.getAttribute(T_UNRAISABLEHOOK));
        sys.setAttribute(T___DISPLAYHOOK__, sys.getAttribute(T_DISPLAYHOOK));
        sys.setAttribute(T___BREAKPOINTHOOK__, sys.getAttribute(T_BREAKPOINTHOOK));
    }

    private static PFrozenSet createStdLibModulesSet(PythonLanguage language) {
        EconomicMapStorage storage = EconomicMapStorage.create(STDLIB_MODULE_NAMES.length);
        for (String s : STDLIB_MODULE_NAMES) {
            storage.putUncached(s, PNone.NONE);
        }
        return PFactory.createFrozenSet(language, storage);
    }

    /**
     * Like {@link PythonUtils#toTruffleStringArrayUncached(String[])}, but creates an array of
     * {@link Object}'s. The intended use of this method is in slow-path in calls to methods like
     * {@link PFactory#createTuple}.
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
        PythonModule sys = core.lookupBuiltinModule(T_SYS);
        core.getContext().registerCApiHook(() -> {
            sys.setAttribute(toTruffleStringUncached("_dllhandle_name"), toTruffleStringUncached(core.getContext().getCApiContext().getLibraryName()));
        });
    }

    @TruffleBoundary
    static void initStd(Python3Core core) {
        PythonContext context = core.getContext();
        PythonLanguage language = core.getLanguage();

        // wrap std in/out/err
        GraalPythonModuleBuiltins gp = (GraalPythonModuleBuiltins) core.lookupBuiltinModule(T___GRAALPYTHON__).getBuiltins();
        TruffleString stdioEncoding = gp.getStdIOEncoding();
        TruffleString stdioError = gp.getStdIOError();
        Object posixSupport = context.getPosixSupport();
        PosixSupportLibrary posixLib = PosixSupportLibrary.getUncached();
        PythonModule sysModule = core.lookupBuiltinModule(T_SYS);

        // Note that stdin is always buffered, this only applies to stdout and stderr
        boolean buffering = !context.getOption(PythonOptions.UnbufferedIO);

        PFileIO stdinFileIO = PFactory.createFileIO(language);
        FileIOBuiltins.FileIOInit.internalInit(stdinFileIO, toTruffleStringUncached("<stdin>"), 0, IOMode.RB);
        PBuffered stdinBuffer = PFactory.createBufferedReader(language);
        BufferedReaderBuiltins.BufferedReaderInit.internalInit(stdinBuffer, stdinFileIO, BufferedReaderBuiltins.DEFAULT_BUFFER_SIZE, language, posixSupport, posixLib);
        setWrapper(T_STDIN, T___STDIN__, T_R, stdioEncoding, stdioError, stdinBuffer, sysModule, language, true);

        PFileIO stdoutFileIO = PFactory.createFileIO(language);
        FileIOBuiltins.FileIOInit.internalInit(stdoutFileIO, toTruffleStringUncached("<stdout>"), 1, IOMode.WB);
        Object stdoutBuffer = createBufferedIO(buffering, language, stdoutFileIO, posixSupport, posixLib);
        setWrapper(T_STDOUT, T___STDOUT__, T_W, stdioEncoding, stdioError, stdoutBuffer, sysModule, language, buffering);

        PFileIO stderr = PFactory.createFileIO(language);
        FileIOBuiltins.FileIOInit.internalInit(stderr, toTruffleStringUncached("<stderr>"), 2, IOMode.WB);
        Object stderrBuffer = createBufferedIO(buffering, language, stderr, posixSupport, posixLib);
        setWrapper(T_STDERR, T___STDERR__, T_W, stdioEncoding, T_BACKSLASHREPLACE, stderrBuffer, sysModule, language, buffering);
    }

    private static Object createBufferedIO(boolean buffering, PythonLanguage language, PFileIO fileIo, Object posixSupport, PosixSupportLibrary posixLib) {
        if (!buffering) {
            return fileIo;
        }
        PBuffered writer = PFactory.createBufferedWriter(language);
        BufferedWriterBuiltins.BufferedWriterInit.internalInit(writer, fileIo, BufferedReaderBuiltins.DEFAULT_BUFFER_SIZE, language, posixSupport, posixLib);
        return writer;
    }

    private static PTextIO setWrapper(TruffleString name, TruffleString specialName, TruffleString mode, TruffleString encoding, TruffleString error, Object buffer, PythonModule sysModule,
                    PythonLanguage language, boolean buffering) {
        PTextIO textIOWrapper = PFactory.createTextIO(language);
        TextIOWrapperInitNodeGen.getUncached().execute(null, null, textIOWrapper, buffer, encoding, error, PNone.NONE,
                        /* line_buffering */ buffering, /* write_through */ !buffering);

        setAttribute(textIOWrapper, T_MODE, mode);
        setAttribute(sysModule, name, textIOWrapper);
        setAttribute(sysModule, specialName, textIOWrapper);

        return textIOWrapper;
    }

    private static void setAttribute(PythonObject obj, TruffleString key, Object value) {
        obj.setAttribute(key, value);
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
        static PTuple run(VirtualFrame frame,
                        @Bind Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached GetEscapedExceptionNode getEscapedExceptionNode,
                        @Cached GetCaughtExceptionNode getCaughtExceptionNode,
                        @Cached ExceptionNodes.GetTracebackNode getTracebackNode,
                        @Bind PythonLanguage language) {
            AbstractTruffleException currentException = getCaughtExceptionNode.execute(frame);
            assert currentException != PException.NO_EXCEPTION;
            if (currentException == null) {
                return PFactory.createTuple(language, new PNone[]{PNone.NONE, PNone.NONE, PNone.NONE});
            } else {
                Object exceptionObject = getEscapedExceptionNode.execute(inliningTarget, currentException);
                Object traceback = getTracebackNode.execute(inliningTarget, exceptionObject);
                return PFactory.createTuple(language, new Object[]{getClassNode.execute(inliningTarget, exceptionObject), exceptionObject, traceback});
            }
        }
    }

    @Builtin(name = "exception", needsFrame = true)
    @GenerateNodeFactory
    abstract static class ExceptionNode extends PythonBuiltinNode {

        @Specialization
        static Object run(VirtualFrame frame,
                        @Bind Node inliningTarget,
                        @Cached GetEscapedExceptionNode getEscapedExceptionNode,
                        @Cached GetCaughtExceptionNode getCaughtExceptionNode) {
            AbstractTruffleException currentException = getCaughtExceptionNode.execute(frame);
            assert currentException != PException.NO_EXCEPTION;
            if (currentException == null) {
                return PNone.NONE;
            } else {
                return getEscapedExceptionNode.execute(inliningTarget, currentException);
            }
        }
    }

    @Builtin(name = "_getframe", parameterNames = "depth", minNumOfPositionalArgs = 0, needsFrame = true, callerFlags = CallerFlags.NEEDS_PFRAME)
    @ArgumentClinic(name = "depth", defaultValue = "0", conversion = ClinicConversion.Int)
    @GenerateNodeFactory
    public abstract static class GetFrameNode extends PythonUnaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return GetFrameNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static PFrame counted(VirtualFrame frame, int depth,
                        @Bind Node inliningTarget,
                        @Cached ReadFrameNode readFrameNode,
                        @Cached PRaiseNode raiseNode) {
            PFrame requested = readFrameNode.getFrameForReference(frame, PArguments.getCurrentFrameInfo(frame), ReadFrameNode.AllPythonFramesSelector.INSTANCE, depth, 0);
            if (requested == null) {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.CALL_STACK_NOT_DEEP_ENOUGH);
            }
            requested.getRef().markAsEscaped();
            return requested;
        }
    }

    @Builtin(name = "_current_frames")
    @GenerateNodeFactory
    abstract static class CurrentFrames extends PythonBuiltinNode {
        @Specialization
        Object currentFrames(VirtualFrame frame,
                        @Bind Node inliningTarget,
                        @Cached AuditNode auditNode,
                        @Cached WarningsModuleBuiltins.WarnNode warnNode,
                        @Cached ReadFrameNode readFrameNode,
                        @Cached HashingStorageSetItem setHashingStorageItem,
                        @Bind PythonLanguage language) {
            auditNode.audit(inliningTarget, "sys._current_frames");
            if (!getLanguage().singleThreadedAssumption.isValid()) {
                warnNode.warn(frame, RuntimeWarning, ErrorMessages.WARN_CURRENT_FRAMES_MULTITHREADED);
            }
            PFrame currentFrame = readFrameNode.getCurrentPythonFrame(frame);
            PDict result = PFactory.createDict(language);
            result.setDictStorage(setHashingStorageItem.execute(frame, inliningTarget, result.getDictStorage(), PThread.getThreadId(Thread.currentThread()), currentFrame));
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

        @Specialization(guards = "isPString(s) || isTruffleString(s)")
        static Object doPString(Object s,
                        @Bind Node inliningTarget,
                        @Cached StringNodes.InternStringNode internNode,
                        @Cached PRaiseNode raiseNode) {
            final Object interned = internNode.execute(inliningTarget, s);
            if (interned == null) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.CANNOT_INTERN_P, s);
            }
            return interned;
        }

        @Fallback
        static Object doOthers(Object obj,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.S_ARG_MUST_BE_S_NOT_P, "intern()", "str", obj);
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
        static long doGeneric(PythonAbstractObject object,
                        @Cached CStructAccess.ReadI64Node read) {
            if (object instanceof PythonAbstractNativeObject nativeKlass) {
                return read.readFromObj(nativeKlass, PyObject__ob_refcnt);
            }

            PythonAbstractObjectNativeWrapper wrapper = object.getNativeWrapper();
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
        @Specialization(guards = "isNoValue(dflt)")
        static Object doGeneric(VirtualFrame frame, Object object, @SuppressWarnings("unused") PNone dflt,
                        @Bind Node inliningTarget,
                        @Shared @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached("createWithError()") LookupAndCallUnaryNode callSizeofNode,
                        @Shared @Cached PRaiseNode raiseNode) {
            Object result;
            try {
                result = callSizeofNode.executeObject(frame, object);
            } catch (SpecialMethodNotFound e) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.TYPE_DOESNT_DEFINE_METHOD, object, T___SIZEOF__);
            }
            return checkResult(frame, inliningTarget, asSizeNode, result, raiseNode);
        }

        @Specialization(guards = "!isNoValue(dflt)")
        static Object doGeneric(VirtualFrame frame, Object object, Object dflt,
                        @Bind Node inliningTarget,
                        @Shared @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached("createWithoutError()") LookupAndCallUnaryNode callSizeofNode,
                        @Shared @Cached PRaiseNode raiseNode) {
            Object result;
            try {
                result = callSizeofNode.executeObject(frame, object);
            } catch (SpecialMethodNotFound e) {
                return dflt;
            }
            return checkResult(frame, inliningTarget, asSizeNode, result, raiseNode);
        }

        private static Object checkResult(VirtualFrame frame, Node inliningTarget, PyNumberAsSizeNode asSizeNode, Object result, PRaiseNode raiseNode) {
            int value = asSizeNode.executeExact(frame, inliningTarget, result);
            if (value < 0) {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.SHOULD_RETURN, "__sizeof__()", ">= 0");
            }
            return value;
        }

        @NeverDefault
        protected LookupAndCallUnaryNode createWithError() {
            return LookupAndCallUnaryNode.create(T___SIZEOF__);
        }

        @NeverDefault
        protected static LookupAndCallUnaryNode createWithoutError() {
            return LookupAndCallUnaryNode.create(T___SIZEOF__);
        }
    }

    // TODO implement support for audit events
    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class AuditNode extends Node {
        protected abstract void executeInternal(Node inliningTarget, Object event, Object[] arguments);

        public void audit(Node inliningTarget, String event, Object... arguments) {
            executeInternal(inliningTarget, event, arguments);
        }

        public static void auditUncached(String event, Object... arguments) {
            SysModuleBuiltinsFactory.AuditNodeGen.getUncached().executeInternal(null, event, arguments);
        }

        public void audit(Node inliningTarget, TruffleString event, Object... arguments) {
            executeInternal(inliningTarget, event, arguments);
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
        static Object settrace(Object function,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context) {
            PythonLanguage language = context.getLanguage(inliningTarget);
            PythonContext.PythonThreadState state = context.getThreadState(language);
            if (function == PNone.NONE) {
                state.setTraceFun(inliningTarget, null, language);
            } else {
                state.setTraceFun(inliningTarget, function, language);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "setprofile", minNumOfPositionalArgs = 1, parameterNames = {
                    "function"}, doc = "Set the profiling function.  It will be called on each function call\nand return.  See the profiler chapter in the library manual.")
    @GenerateNodeFactory
    abstract static class SetProfile extends PythonBuiltinNode {
        @Specialization
        static Object settrace(Object function,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context) {
            PythonLanguage language = context.getLanguage(inliningTarget);
            PythonContext.PythonThreadState state = context.getThreadState(language);
            if (function == PNone.NONE) {
                state.setProfileFun(inliningTarget, null, language);
            } else {
                state.setProfileFun(inliningTarget, function, language);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "gettrace")
    @GenerateNodeFactory
    abstract static class GetTrace extends PythonBuiltinNode {
        @Specialization
        static Object gettrace(
                        @Bind Node inliningTarget,
                        @Bind PythonContext context) {
            PythonContext.PythonThreadState state = context.getThreadState(context.getLanguage(inliningTarget));
            Object trace = state.getTraceFun();
            return trace == null ? PNone.NONE : trace;

        }
    }

    @Builtin(name = "getprofile")
    @GenerateNodeFactory
    abstract static class GetProfile extends PythonBuiltinNode {
        @Specialization
        static Object getProfile(
                        @Bind Node inliningTarget,
                        @Bind PythonContext context) {
            PythonContext.PythonThreadState state = context.getThreadState(context.getLanguage(inliningTarget));
            Object trace = state.getProfileFun();
            return trace == null ? PNone.NONE : trace;
        }
    }

    @Builtin(name = "set_asyncgen_hooks", parameterNames = {"firstiter", "finalizer"})
    @GenerateNodeFactory
    abstract static class SetAsyncgenHooks extends PythonBuiltinNode {
        @Specialization
        static Object setAsyncgenHooks(Object firstIter, Object finalizer,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context) {
            if (firstIter != PNone.NO_VALUE && firstIter != PNone.NONE) {
                context.getThreadState(context.getLanguage(inliningTarget)).setAsyncgenFirstIter(firstIter);
            } else if (firstIter == PNone.NONE) {
                context.getThreadState(context.getLanguage(inliningTarget)).setAsyncgenFirstIter(null);
            }
            // Ignore finalizer, since we don't have a useful place to call it
            return PNone.NONE;
        }
    }

    @Builtin(name = "get_asyncgen_hooks")
    @GenerateNodeFactory
    abstract static class GetAsyncgenHooks extends PythonBuiltinNode {
        @Specialization
        static Object setAsyncgenHooks(
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @Bind PythonLanguage language) {
            // TODO: use asyncgen_hooks object
            PythonContext.PythonThreadState threadState = context.getThreadState(context.getLanguage(inliningTarget));
            Object firstiter = threadState.getAsyncgenFirstIter();
            return PFactory.createTuple(language, new Object[]{firstiter == null ? PNone.NONE : firstiter, PNone.NONE});
        }
    }

    @Builtin(name = "get_coroutine_origin_tracking_depth")
    @GenerateNodeFactory
    abstract static class GetCoroOriginTrackingDepth extends PythonBuiltinNode {

        @Specialization
        static Object getCoroDepth() {
            // TODO: Implement
            return 0;
        }
    }

    @Builtin(name = "set_coroutine_origin_tracking_depth", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class SetCoroOriginTrackingDepth extends PythonUnaryBuiltinNode {

        @Specialization
        static Object setCoroDepth(Object newValue) {
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

        private void writeUnraisableExc(Node inliningTarget, TracebackBuiltins.GetTracebackFrameNode getTbFrameNode, TracebackBuiltins.MaterializeTruffleStacktraceNode materializeStNode,
                        PythonModule sys, Object out, Object excType, Object excValue, Object excTb, Object errMsg, Object obj) {
            if (obj != PNone.NONE) {
                if (errMsg != PNone.NONE) {
                    PyTraceBackPrint.fileWriteObject(out, errMsg, true);
                    PyTraceBackPrint.fileWriteString(out, ": ");
                } else {
                    PyTraceBackPrint.fileWriteString(out, "Exception ignored in: ");
                }

                if (!PyTraceBackPrint.fileWriteObject(out, obj, false)) {
                    PyTraceBackPrint.fileWriteString(out, "<object repr() failed>");
                }
                fileWriteString(out, T_NEWLINE);
            } else if (errMsg != PNone.NONE) {
                PyTraceBackPrint.fileWriteObject(out, errMsg, true);
                PyTraceBackPrint.fileWriteString(out, ":\n");
            }

            if (excTb != PNone.NONE) {
                PyTraceBackPrint.print(inliningTarget, getTbFrameNode, materializeStNode, sys, out, excTb, false, false, 0, null);
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
            Object v = objectLookupAttr(excType, T___MODULE__);
            if (v == PNone.NO_VALUE || !PGuards.isString(v)) {
                fileWriteString(out, T_VALUE_UNKNOWN);
            } else {
                moduleName = castToString(v);
                if (!moduleName.equalsUncached(T_BUILTINS, TS_ENCODING)) {
                    fileWriteString(out, moduleName);
                    fileWriteString(out, T_DOT);
                }
            }
            if (className == null) {
                fileWriteString(out, T_VALUE_UNKNOWN);
            } else {
                fileWriteString(out, className);
            }

            if (excValue != PNone.NONE) {
                // only print colon if the str() of the object is not the empty string
                PyTraceBackPrint.fileWriteString(out, ": ");
                if (!PyTraceBackPrint.fileWriteObject(out, excValue, true)) {
                    PyTraceBackPrint.fileWriteString(out, "<exception str() failed>");
                }
            }

            fileWriteString(out, T_NEWLINE);
        }

        @Specialization
        Object doit(VirtualFrame frame, PythonModule sys, Object args,
                        @Bind Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached PyTupleGetItem getItemNode,
                        @Cached PRaiseNode raiseNode,
                        @Cached("createFor($node)") BoundaryCallData boundaryCallData,
                        @Cached TracebackBuiltins.GetTracebackFrameNode getTbFrameNode,
                        @Cached TracebackBuiltins.MaterializeTruffleStacktraceNode materializeStNode) {
            final Object cls = getClassNode.execute(inliningTarget, args);
            if (cls != PythonBuiltinClassType.PUnraisableHookArgs) {
                throw raiseNode.raise(inliningTarget, TypeError, ARG_TYPE_MUST_BE, "sys.unraisablehook", "UnraisableHookArgs");
            }
            final Object excType = getItemNode.execute(inliningTarget, args, 0);
            final Object excValue = getItemNode.execute(inliningTarget, args, 1);
            final Object excTb = getItemNode.execute(inliningTarget, args, 2);
            final Object errMsg = getItemNode.execute(inliningTarget, args, 3);
            final Object obj = getItemNode.execute(inliningTarget, args, 4);

            Object saved = BoundaryCallContext.enter(frame, boundaryCallData);
            try {
                writeUnraisableExc(inliningTarget, getTbFrameNode, materializeStNode, sys, excType, excValue, excTb, errMsg, obj);
            } finally {
                BoundaryCallContext.exit(frame, boundaryCallData, saved);
            }
            return PNone.NONE;
        }

        @TruffleBoundary
        private void writeUnraisableExc(Node inliningTarget, TracebackBuiltins.GetTracebackFrameNode getTbFrameNode, TracebackBuiltins.MaterializeTruffleStacktraceNode materializeStNode,
                        PythonModule sys, Object excType, Object excValue, Object excTb, Object errMsg, Object obj) {
            Object stdErr = objectLookupAttr(sys, T_STDERR);
            writeUnraisableExc(inliningTarget, getTbFrameNode, materializeStNode, sys, stdErr, excType, excValue, excTb, errMsg, obj);
            fileFlush(stdErr);
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
        static final TruffleString T_ATTR_MSG = tsInternedLiteral("msg");
        static final TruffleString T_ATTR_FILENAME = tsInternedLiteral("filename");
        static final TruffleString T_ATTR_LINENO = tsInternedLiteral("lineno");
        static final TruffleString T_ATTR_OFFSET = tsInternedLiteral("offset");
        static final TruffleString T_ATTR_TEXT = tsInternedLiteral("text");
        static final TruffleString T_EG_MARGIN = tsInternedLiteral("| ");

        protected static final int INT_MAX_GROUP_WIDTH = 15;
        protected static final int INT_MAX_GROUP_DEPTH = 10;
        protected static final int INT_INDENT_SIZE = 2;

        @ValueType
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

        static class ExceptionPrintContext {
            public int depthMax;
            public int depthCurrent;
            public int widthMax;
            public boolean needsToEnd;

            ExceptionPrintContext() {
                this.depthMax = INT_MAX_GROUP_DEPTH;
                this.depthCurrent = 0;
                this.widthMax = INT_MAX_GROUP_WIDTH;
                this.needsToEnd = false;
            }

            public TruffleString getMargin() {
                if (this.depthCurrent > 0) {
                    return T_EG_MARGIN;
                } else {
                    return tsLiteral("");
                }
            }

            public int getIndent() {
                return this.depthCurrent * INT_INDENT_SIZE;
            }

            public void increaseDepth() {
                this.depthCurrent++;
            }

            public void decreaseDepth() {
                if (depthCurrent > 0) {
                    this.depthCurrent--;
                }
            }
        }

        private static SyntaxErrData parseSyntaxError(Object err) {
            Object v, msg;
            TruffleString fileName = null, text = null;
            int lineNo = 0, offset = 0, hold;

            // new style errors. `err' is an instance
            msg = objectLookupAttr(err, T_ATTR_MSG);
            if (msg == PNone.NO_VALUE) {
                return new SyntaxErrData(null, fileName, lineNo, offset, text, true);
            }

            v = objectLookupAttr(err, T_ATTR_FILENAME);
            if (v == PNone.NO_VALUE) {
                return new SyntaxErrData(msg, fileName, lineNo, offset, text, true);
            }
            if (v == PNone.NONE) {
                fileName = T_STRING_SOURCE;
            } else {
                fileName = castToString(objectStr(v));
            }

            v = objectLookupAttr(err, T_ATTR_LINENO);
            if (v == PNone.NO_VALUE) {
                return new SyntaxErrData(msg, fileName, lineNo, offset, text, true);
            }
            try {
                hold = PyLongAsIntNodeGen.getUncached().execute(null, null, v);
            } catch (PException pe) {
                return new SyntaxErrData(msg, fileName, lineNo, offset, text, true);
            }

            lineNo = hold;

            v = objectLookupAttr(err, T_ATTR_OFFSET);
            if (v == PNone.NO_VALUE) {
                return new SyntaxErrData(msg, fileName, lineNo, offset, text, true);
            }
            if (v == PNone.NONE) {
                offset = -1;
            } else {
                try {
                    hold = PyLongAsIntNodeGen.getUncached().execute(null, null, v);
                } catch (PException pe) {
                    return new SyntaxErrData(msg, fileName, lineNo, offset, text, true);
                }
                offset = hold;
            }

            v = objectLookupAttr(err, T_ATTR_TEXT);
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

        private static void printErrorText(Object out, SyntaxErrData syntaxErrData) {
            TruffleString text = castToString(objectStr(syntaxErrData.text));
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
                    while (idx < textLen) {
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

            PyTraceBackPrint.fileWriteString(out, "    ");
            fileWriteString(out, text);
            if (text.isEmpty() || text.codePointAtIndexUncached(text.codePointLengthUncached(TS_ENCODING) - 1, TS_ENCODING) != '\n') {
                fileWriteString(out, T_NEWLINE);
            }
            if (offset == -1) {
                return;
            }
            PyTraceBackPrint.fileWriteString(out, "    ");
            while (--offset > 0) {
                PyTraceBackPrint.fileWriteString(out, " ");
            }
            PyTraceBackPrint.fileWriteString(out, "^\n");
        }

        @TruffleBoundary
        void printExceptionRecursive(Node inliningTarget, TracebackBuiltins.GetTracebackFrameNode getTbFrameNode, TracebackBuiltins.MaterializeTruffleStacktraceNode materializeStNode,
                        PythonModule sys, Object out, Object value, Set<Object> seen, IteratorNodes.ToArrayNode toArrayNode) {
            printExceptionRecursive(inliningTarget, getTbFrameNode, materializeStNode, sys, out, value, seen, new ExceptionPrintContext(), toArrayNode);
        }

        @TruffleBoundary
        void printExceptionRecursive(Node inliningTarget, TracebackBuiltins.GetTracebackFrameNode getTbFrameNode, TracebackBuiltins.MaterializeTruffleStacktraceNode materializeStNode,
                        PythonModule sys, Object out, Object value, Set<Object> seen, ExceptionPrintContext ctx, IteratorNodes.ToArrayNode toArrayNode) {
            if (seen != null) {
                // Exception chaining
                add(seen, value);
                if (PyExceptionInstanceCheckNode.executeUncached(value)) {
                    Object cause = ExceptionNodes.GetCauseNode.executeUncached(value);
                    Object context = ExceptionNodes.GetContextNode.executeUncached(value);

                    boolean needsToEnd = ctx.needsToEnd;
                    if (cause != PNone.NONE) {
                        if (notSeen(seen, cause)) {
                            printExceptionRecursive(inliningTarget, getTbFrameNode, materializeStNode, sys, out, cause, seen, ctx, toArrayNode);
                            fileWriteString(out, T_CAUSE_MESSAGE);
                        }
                    } else if (context != PNone.NONE && !ExceptionNodes.GetSuppressContextNode.executeUncached(value)) {
                        if (notSeen(seen, context)) {
                            printExceptionRecursive(inliningTarget, getTbFrameNode, materializeStNode, sys, out, context, seen, ctx, toArrayNode);
                            fileWriteString(out, T_CONTEXT_MESSAGE);
                        }
                    }
                    ctx.needsToEnd = needsToEnd;
                }
            }
            if (value instanceof PBaseExceptionGroup) {
                printExceptionGroup(inliningTarget, getTbFrameNode, materializeStNode, sys, out, value, seen, ctx, toArrayNode);
            } else {
                printException(inliningTarget, getTbFrameNode, materializeStNode, sys, out, value, ctx, toArrayNode);
            }
        }

        protected static TruffleString getIndent(int indent) {
            return T_SPACE.repeatUncached(indent, TS_ENCODING);
        }

        protected static void fileWriteIndentedString(Object file, String string, int indent) {
            fileWriteIndentedString(file, tsLiteral(string), indent);
        }

        protected static void fileWriteIndentedString(Object file, TruffleString string, int indent) {
            fileWriteString(file, getIndent(indent));
            fileWriteString(file, string);
        }

        protected void printException(Node inliningTarget, TracebackBuiltins.GetTracebackFrameNode getTbFrameNode, TracebackBuiltins.MaterializeTruffleStacktraceNode materializeStNode,
                        PythonModule sys, Object out, Object excValue, ExceptionPrintContext ctx, IteratorNodes.ToArrayNode toArrayNode) {
            Object value = excValue;
            final Object type = getObjectClass(value);
            if (!PyExceptionInstanceCheckNode.executeUncached(value)) {
                PyTraceBackPrint.fileWriteString(out, "TypeError: print_exception(): Exception expected for value, ");
                fileWriteString(out, getTypeName(type));
                PyTraceBackPrint.fileWriteString(out, " found\n");
                return;
            }

            final Object tb = getExceptionTraceback(value);
            if (tb instanceof PTraceback) {
                if (value instanceof PBaseExceptionGroup pbeg) {
                    PyTraceBackPrint.print(inliningTarget, getTbFrameNode, materializeStNode, sys, out, tb, true, ctx.depthCurrent > 1, ctx.getIndent(), ctx.getMargin());
                } else {
                    PyTraceBackPrint.print(inliningTarget, getTbFrameNode, materializeStNode, sys, out, tb, false, ctx.depthCurrent == 0, ctx.getIndent(), ctx.getMargin());
                }
            }

            if (objectHasAttr(value, T_ATTR_PRINT_FILE_AND_LINE)) {
                // SyntaxError case
                final SyntaxErrData syntaxErrData = parseSyntaxError(value);
                if (!syntaxErrData.err) {
                    value = syntaxErrData.message;
                    StringBuilder sb = newStringBuilder("  File \"");
                    append(sb, castToString(objectStr(syntaxErrData.fileName)), "\", line ", syntaxErrData.lineNo, "\n");
                    PyTraceBackPrint.fileWriteString(out, sbToString(sb));

                    // Can't be bothered to check all those PyFile_WriteString() calls
                    if (syntaxErrData.text != null) {
                        printErrorText(out, syntaxErrData);
                    }
                }
            }

            fileWriteIndentedString(out, ctx.getMargin(), ctx.getIndent());

            TruffleString className;
            try {
                className = getTypeName(type);
                className = classNameNoDot(className);
            } catch (PException pe) {
                className = null;
            }
            TruffleString moduleName;
            Object v = objectLookupAttr(type, T___MODULE__);
            if (v == PNone.NO_VALUE || !PGuards.isString(v)) {
                fileWriteString(out, T_VALUE_UNKNOWN);
            } else {
                moduleName = castToString(v);
                if (!moduleName.equalsUncached(T_BUILTINS, TS_ENCODING)) {
                    fileWriteString(out, moduleName);
                    fileWriteString(out, T_DOT);
                }
            }
            if (className == null) {
                fileWriteString(out, T_VALUE_UNKNOWN);
            } else {
                fileWriteString(out, className);
            }

            if (value != PNone.NONE) {
                // only print colon if the str() of the object is not the empty string
                v = objectStr(value);
                TruffleString s = tryCastToString(v);
                if (v == PNone.NONE) {
                    PyTraceBackPrint.fileWriteString(out, ": <exception str() failed>");
                } else if (!PGuards.isString(v) || (s != null && !s.isEmpty())) {
                    PyTraceBackPrint.fileWriteString(out, ": ");
                }
                if (s != null) {
                    fileWriteString(out, s);
                }
            }

            fileWriteString(out, T_NEWLINE);

            if (objectHasAttr(value, T___NOTES__)) {
                // print notes
                Object notes = objectLookupAttr(value, T___NOTES__);
                if (notes instanceof PList noteList) {
                    Object[] arr = toArrayNode.execute(null, noteList);
                    for (Object oStr : arr) {
                        if (oStr instanceof TruffleString note) {
                            String n = note.toString();
                            if (n.contains(J_NEWLINE)) {
                                String[] lines = n.split(J_NEWLINE);
                                for (String line : lines) {
                                    fileWriteIndentedString(out, ctx.getMargin(), ctx.getIndent());
                                    fileWriteString(out, line);
                                    fileWriteString(out, T_NEWLINE);
                                }
                            } else {
                                fileWriteIndentedString(out, ctx.getMargin(), ctx.getIndent());
                                fileWriteString(out, note);
                                fileWriteString(out, T_NEWLINE);
                            }
                        }
                    }
                }
            }
        }

        protected void printExceptionGroup(Node inliningTarget, TracebackBuiltins.GetTracebackFrameNode getTbFrameNode, TracebackBuiltins.MaterializeTruffleStacktraceNode materializeStNode,
                        PythonModule sys, Object out, Object excValue, Set<Object> seen, ExceptionPrintContext ctx, IteratorNodes.ToArrayNode toArrayNode) {
            Object value = excValue;
            final Object type = getObjectClass(value);
            if (!PyExceptionGroupInstanceCheckNode.executeUncached(value)) {
                PyTraceBackPrint.fileWriteString(out, "TypeError: print_exception_group(): Exception group expected for value, ");
                fileWriteString(out, getTypeName(type));
                PyTraceBackPrint.fileWriteString(out, " found\n");
            }

            if (ctx.depthCurrent > ctx.depthMax) {
                fileWriteIndentedString(out, ctx.getMargin(), ctx.getIndent());
                fileWriteString(out, String.format("... (max_group_depth is %d)", ctx.depthMax));
                fileWriteString(out, T_NEWLINE);
                return;
            }

            ctx.needsToEnd = false;

            if (ctx.depthCurrent == 0) {
                ctx.increaseDepth();
            }

            printException(inliningTarget, getTbFrameNode, materializeStNode, sys, out, excValue, ctx, toArrayNode);

            PBaseExceptionGroup exceptionGroup = (PBaseExceptionGroup) excValue;
            int counter = 1;
            boolean lastException = false;
            for (Object exception : exceptionGroup.getExceptions()) {
                if (counter == exceptionGroup.getExceptions().length) {
                    lastException = true;
                    ctx.needsToEnd = true;
                }
                if (counter == 1) {
                    fileWriteIndentedString(out, "+".concat("-".repeat(INT_INDENT_SIZE - 1)), ctx.getIndent());
                } else {
                    fileWriteString(out, getIndent(ctx.getIndent() + INT_INDENT_SIZE));
                }
                if (counter <= ctx.widthMax) {
                    fileWriteString(out, String.format("+---------------- %d ----------------", counter));
                    fileWriteString(out, T_NEWLINE);
                    ctx.increaseDepth();
                    printExceptionRecursive(inliningTarget, getTbFrameNode, materializeStNode, sys, out, exception, seen, ctx, toArrayNode);
                    ctx.decreaseDepth();
                } else {
                    fileWriteString(out, "+---------------- ... ----------------");
                    fileWriteString(out, T_NEWLINE);
                    fileWriteIndentedString(out, ctx.getMargin(), ctx.getIndent() + INT_INDENT_SIZE);
                    int exceptionsRemaining = exceptionGroup.getExceptions().length - ctx.widthMax;
                    fileWriteString(out, String.format("and %d more exception%s", exceptionsRemaining, exceptionsRemaining > 1 ? "s" : ""));
                    fileWriteString(out, T_NEWLINE);

                    // this makes this exception in this exception group essentially last
                    lastException = true;
                    ctx.needsToEnd = true;
                    break;
                }
                counter++;
            }

            if (lastException && ctx.needsToEnd) {
                fileWriteString(out, getIndent(ctx.getIndent() + INT_INDENT_SIZE));
                fileWriteString(out, "+------------------------------------");
                fileWriteString(out, T_NEWLINE);
                // let only the innermost exception print the end of an exception group cascade
                ctx.needsToEnd = false;
            }

            if (ctx.depthCurrent == 1) {
                ctx.decreaseDepth();
            }
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
        Object doHookWithTb(VirtualFrame frame, PythonModule sys, @SuppressWarnings("unused") Object excType, Object value, PTraceback traceBack,
                        @Bind Node inliningTarget,
                        @Shared @Cached TracebackBuiltins.GetTracebackFrameNode getTbFrameNode,
                        @Shared @Cached TracebackBuiltins.MaterializeTruffleStacktraceNode materializeStNode,
                        @Shared @Cached("createFor($node)") BoundaryCallData boundaryCallData,
                        @Shared @Cached IteratorNodes.ToArrayNode toArrayNode) {
            Object saved = BoundaryCallContext.enter(frame, boundaryCallData);
            try {
                doHookWithTbImpl(inliningTarget, getTbFrameNode, materializeStNode, sys, value, traceBack, toArrayNode);
            } finally {
                BoundaryCallContext.exit(frame, boundaryCallData, saved);
            }
            return PNone.NONE;
        }

        @TruffleBoundary
        private void doHookWithTbImpl(Node inliningTarget, TracebackBuiltins.GetTracebackFrameNode getTbFrameNode, TracebackBuiltins.MaterializeTruffleStacktraceNode materializeStNode,
                        PythonModule sys, Object value, PTraceback traceBack, IteratorNodes.ToArrayNode toArrayNode) {
            setExceptionTraceback(value, traceBack);
            Object stdErr = objectLookupAttr(sys, T_STDERR);
            printExceptionRecursive(inliningTarget, getTbFrameNode, materializeStNode, sys, stdErr, value, createSet(), toArrayNode);
            fileFlush(stdErr);
        }

        @Specialization(guards = "!isPTraceback(traceBack)")
        Object doHookWithoutTb(VirtualFrame frame, PythonModule sys, @SuppressWarnings("unused") Object excType, Object value, @SuppressWarnings("unused") Object traceBack,
                        @Bind Node inliningTarget,
                        @Shared @Cached TracebackBuiltins.GetTracebackFrameNode getTbFrameNode,
                        @Shared @Cached TracebackBuiltins.MaterializeTruffleStacktraceNode materializeStNode,
                        @Shared @Cached("createFor($node)") BoundaryCallData boundaryCallData,
                        @Shared @Cached IteratorNodes.ToArrayNode toArrayNode) {
            Object saved = BoundaryCallContext.enter(frame, boundaryCallData);
            try {
                doHookWithoutTbImpl(inliningTarget, getTbFrameNode, materializeStNode, sys, value, toArrayNode);
            } finally {
                BoundaryCallContext.exit(frame, boundaryCallData, saved);
            }
            return PNone.NONE;
        }

        @TruffleBoundary
        private void doHookWithoutTbImpl(Node inliningTarget, TracebackBuiltins.GetTracebackFrameNode getTbFrameNode, TracebackBuiltins.MaterializeTruffleStacktraceNode materializeStNode,
                        PythonModule sys, Object value, IteratorNodes.ToArrayNode toArrayNode) {
            Object stdErr = objectLookupAttr(sys, T_STDERR);
            printExceptionRecursive(inliningTarget, getTbFrameNode, materializeStNode, sys, stdErr, value, createSet(), toArrayNode);
            fileFlush(stdErr);
        }
    }

    @Builtin(name = J_DISPLAYHOOK, minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 2, declaresExplicitSelf = true, doc = "displayhook($module, object, /)\n" +
                    "--\n" +
                    "\n" +
                    "Print an object to sys.stdout and also save it in builtins._")
    @GenerateNodeFactory
    abstract static class DisplayHookNode extends PythonBuiltinNode {

        @Specialization
        static Object doHook(VirtualFrame frame, PythonModule sys, Object obj,
                        @Bind Node inliningTarget,
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
                        @Cached PyUnicodeFromEncodedObject pyUnicodeFromEncodedObject,
                        @Cached PRaiseNode raiseNode) {
            final PythonModule builtins = PythonContext.get(inliningTarget).getBuiltins();
            if (builtins == null) {
                throw raiseNode.raise(inliningTarget, RuntimeError, LOST_S, "builtins module");
            }
            // Print value except if None
            // After printing, also assign to '_'
            // Before, set '_' to None to avoid recursion
            if (obj == PNone.NONE) {
                return PNone.NONE;
            }

            setAttr.execute(frame, inliningTarget, builtins, T___, PNone.NONE);
            Object stdOut = objectLookupAttr(frame, inliningTarget, sys, T_STDOUT, lookupAttr);
            if (PGuards.isPNone(stdOut)) {
                throw raiseNode.raise(inliningTarget, RuntimeError, LOST_S, "sys.stdout");
            }

            Object reprVal = null;
            try {
                reprVal = objectRepr(frame, inliningTarget, obj, reprAsObjectNode);
            } catch (PException pe) {
                pe.expect(inliningTarget, UnicodeEncodeError, unicodeEncodeErrorProfile);
                // repr(o) is not encodable to sys.stdout.encoding with sys.stdout.errors error
                // handler (which is probably 'strict')
                // inlined sysDisplayHookUnencodable
                final TruffleString stdoutEncoding = objectLookupAttrAsString(frame, inliningTarget, stdOut, T_ENCODING, lookupAttr, castToStringNode);
                final Object reprStr = objectRepr(frame, inliningTarget, obj, reprAsObjectNode);
                final Object encoded = pyUnicodeAsEncodedString.execute(frame, inliningTarget, reprStr, stdoutEncoding, T_BACKSLASHREPLACE);

                final Object buffer = objectLookupAttr(frame, inliningTarget, stdOut, T_BUFFER, lookupAttr);
                if (buffer != null) {
                    callMethodObjArgs.execute(frame, inliningTarget, buffer, T_WRITE, encoded);
                } else {
                    Object escapedStr = pyUnicodeFromEncodedObject.execute(frame, inliningTarget, encoded, stdoutEncoding, T_STRICT);
                    final Object str = objectStr(frame, inliningTarget, escapedStr, strAsObjectNode);
                    fileWriteString(frame, inliningTarget, stdOut, castToStringNode.execute(inliningTarget, str), getAttr, callNode);
                }
            }
            if (reprVal != null) {
                fileWriteString(frame, inliningTarget, stdOut, castToStringNode.execute(inliningTarget, reprVal), getAttr, callNode);
            }

            fileWriteString(frame, inliningTarget, stdOut, T_NEWLINE, getAttr, callNode);
            setAttr.execute(frame, inliningTarget, builtins, T___, obj);
            return PNone.NONE;
        }
    }

    @Builtin(name = J_BREAKPOINTHOOK, takesVarKeywordArgs = true, takesVarArgs = true, doc = "breakpointhook(*args, **kws)\n" +
                    "\n" +
                    "This hook function is called by built-in breakpoint().\n")
    @GenerateNodeFactory
    abstract static class BreakpointHookNode extends PythonBuiltinNode {
        static final TruffleString T_VAL_PDB_SETTRACE = tsLiteral("pdb.set_trace");

        @Specialization
        Object doHook(VirtualFrame frame, Object[] args, PKeyword[] keywords,
                        @Bind Node inliningTarget,
                        @Cached("createFor($node)") BoundaryCallData boundaryCallData,
                        @Cached CallNode callNode,
                        @Cached PyObjectGetAttr getAttr,
                        @Cached PyImportImport importNode,
                        @Cached IsBuiltinObjectProfile attrErrorProfile,
                        @Cached BuiltinFunctions.IsInstanceNode isInstanceNode,
                        @Cached WarningsModuleBuiltins.WarnNode warnNode,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.CodePointAtIndexUTF32Node codePointAtIndexNode,
                        @Cached TruffleString.LastIndexOfCodePointNode lastIndexOfCodePointNode,
                        @Cached TruffleString.SubstringNode substringNode) {
            TruffleString hookName = OsEnvironGetNode.lookup(frame, boundaryCallData, T_PYTHONBREAKPOINT);
            if (hookName == null || hookName.isEmpty()) {
                hookName = T_VAL_PDB_SETTRACE;
            }

            int hookNameLen = codePointLengthNode.execute(hookName, TS_ENCODING);
            if (hookNameLen == 1 && codePointAtIndexNode.execute(hookName, 0) == '0') {
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
                module = importNode.execute(frame, inliningTarget, modPath);
            } catch (PException pe) {
                if (isInstanceNode.executeWith(frame, pe.getUnreifiedException(), ImportError)) {
                    warnNode.warnFormat(frame, RuntimeWarning, WARN_IGNORE_UNIMPORTABLE_BREAKPOINT_S, hookName);
                }
                return PNone.NONE;
            }

            final Object hook;
            try {
                hook = getAttr.execute(frame, inliningTarget, module, attrName);
            } catch (PException pe) {
                if (attrErrorProfile.profileException(inliningTarget, pe, AttributeError)) {
                    warnNode.warnFormat(frame, RuntimeWarning, WARN_IGNORE_UNIMPORTABLE_BREAKPOINT_S, hookName);
                }
                return PNone.NONE;
            }

            return callNode.execute(frame, hook, args, keywords);
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
        static Object setRecLim(VirtualFrame frame, @SuppressWarnings("unused") PythonModule sys, Object limit,
                        @Bind Node inliningTarget,
                        @Cached PyLongAsIntNode longAsIntNode,
                        @Cached PyFloatCheckExactNode floatCheckExactNode,
                        @Cached PRaiseNode raiseNode) {
            if (floatCheckExactNode.execute(inliningTarget, limit)) {
                throw raiseNode.raise(inliningTarget, TypeError, S_EXPECTED_GOT_P, "integer", limit);
            }

            int newLimit;
            try {
                newLimit = longAsIntNode.execute(frame, inliningTarget, limit);
            } catch (PException pe) {
                newLimit = -1;
            }

            if (newLimit < 1) {
                throw raiseNode.raise(inliningTarget, ValueError, REC_LIMIT_GREATER_THAN_1);
            }

            // TODO: check to see if Issue #25274 applies
            PythonContext.get(inliningTarget).getSysModuleState().setRecursionLimit(newLimit);
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
        static Object setCheckInterval(VirtualFrame frame, @SuppressWarnings("unused") PythonModule sys, Object arg,
                        @Bind Node inliningTarget,
                        @Cached WarningsModuleBuiltins.WarnNode warnNode,
                        @Cached PyLongAsIntNode longAsIntNode,
                        @Cached PyFloatCheckExactNode floatCheckExactNode,
                        @Cached PRaiseNode raiseNode) {
            if (floatCheckExactNode.execute(inliningTarget, arg)) {
                throw raiseNode.raise(inliningTarget, TypeError, S_EXPECTED_GOT_P, "integer", arg);
            }

            try {
                final int n = longAsIntNode.execute(frame, inliningTarget, arg);
                warnNode.warnFormat(frame, DeprecationWarning, WARN_DEPRECTATED_SYS_CHECKINTERVAL);
                PythonContext.get(inliningTarget).getSysModuleState().setCheckInterval(n);
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
        static Object setCheckInterval(VirtualFrame frame, @SuppressWarnings("unused") PythonModule sys, Object arg,
                        @Bind Node inliningTarget,
                        @Cached PyFloatAsDoubleNode floatAsDoubleNode,
                        @Cached PRaiseNode raiseNode) {
            double interval = floatAsDoubleNode.execute(frame, inliningTarget, arg);
            if (interval <= 0.0) {
                throw raiseNode.raise(inliningTarget, ValueError, SWITCH_INTERVAL_MUST_BE_POSITIVE);
            }
            PythonContext.get(inliningTarget).getSysModuleState().setSwitchInterval(FACTOR * interval);
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
        static Object exitNoCode(PythonModule sys, PNone status,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseSystemExitStatic(inliningTarget, PNone.NONE);
        }

        @Specialization(guards = "!isPNone(status)")
        static Object exit(VirtualFrame frame, @SuppressWarnings("unused") PythonModule sys, Object status,
                        @Bind Node inliningTarget,
                        @Cached PyTupleCheckNode tupleCheckNode,
                        @Cached TupleBuiltins.LenNode tupleLenNode,
                        @Cached PyTupleGetItem getItemNode) {
            Object code = status;
            if (tupleCheckNode.execute(inliningTarget, status)) {
                if (tupleLenNode.executeInt(frame, status) == 1) {
                    code = getItemNode.execute(inliningTarget, status, 0);
                }
            }
            throw PRaiseNode.raiseSystemExitStatic(inliningTarget, code);
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

    @Builtin(name = "getwindowsversion", minNumOfPositionalArgs = 0, os = PLATFORM_WIN32)
    @GenerateNodeFactory
    abstract static class Getwindowsversion extends PythonBuiltinNode {
        static int[] CACHED_VERSION_INFO = null;
        static int PLATFORM = 2;

        @Specialization
        PTuple getVersion(
                        @Bind PythonLanguage language) {
            if (CACHED_VERSION_INFO == null) {
                cacheVersion();
            }
            return PFactory.createStructSeq(language, WINDOWS_VER_DESC,
                            CACHED_VERSION_INFO[0], CACHED_VERSION_INFO[1], CACHED_VERSION_INFO[2],
                            PLATFORM, T_EMPTY_STRING, 0, 0, 0, 1,
                            PFactory.createTuple(language, CACHED_VERSION_INFO));
        }

        @TruffleBoundary
        static void cacheVersion() {
            String[] winvers = System.getProperty("os.version", "10.0.20000").split("\\.");
            int major = 0;
            int minor = 0;
            int build = 0;
            if (winvers.length > 0) {
                try {
                    major = Integer.parseInt(winvers[0]);
                } catch (NumberFormatException e) {
                    // use default
                }
            }
            if (winvers.length > 1) {
                try {
                    minor = Integer.parseInt(winvers[1]);
                } catch (NumberFormatException e) {
                    // use default
                }
            }
            if (winvers.length > 2) {
                try {
                    build = Integer.parseInt(winvers[2]);
                } catch (NumberFormatException e) {
                    // use default
                }
            }
            CACHED_VERSION_INFO = new int[]{major, minor, build};
        }
    }
}
