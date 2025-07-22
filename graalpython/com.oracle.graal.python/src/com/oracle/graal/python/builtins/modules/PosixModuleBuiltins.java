/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates.
 * Copyright (c) 2014, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.nodes.BuiltinNames.T_ENVIRON;
import static com.oracle.graal.python.nodes.BuiltinNames.T_NT;
import static com.oracle.graal.python.nodes.BuiltinNames.T_POSIX;
import static com.oracle.graal.python.nodes.StringLiterals.T_DOT;
import static com.oracle.graal.python.runtime.PosixConstants.AT_FDCWD;
import static com.oracle.graal.python.runtime.PosixConstants.AT_SYMLINK_FOLLOW;
import static com.oracle.graal.python.runtime.PosixConstants.O_CLOEXEC;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.NotImplementedError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OSError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.lang.management.ManagementFactory;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.annotations.ArgumentClinic.PrimitiveType;
import com.oracle.graal.python.annotations.ClinicConverterFactory;
import com.oracle.graal.python.annotations.ClinicConverterFactory.ArgumentName;
import com.oracle.graal.python.annotations.ClinicConverterFactory.BuiltinName;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.PythonOS;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltins.AuditNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes.LenNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetItemNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.ToArrayNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.posix.PScandirIterator;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.tuple.StructSequence;
import com.oracle.graal.python.lib.PyIndexCheckNode;
import com.oracle.graal.python.lib.PyLongAsIntNode;
import com.oracle.graal.python.lib.PyLongAsLongAndOverflowNode;
import com.oracle.graal.python.lib.PyLongAsLongNode;
import com.oracle.graal.python.lib.PyLongCheckNode;
import com.oracle.graal.python.lib.PyNumberDivmodNode;
import com.oracle.graal.python.lib.PyNumberIndexNode;
import com.oracle.graal.python.lib.PyOSFSPathNode;
import com.oracle.graal.python.lib.PyObjectAsFileDescriptor;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.lib.PyUnicodeCheckNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentCastNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.util.CastToJavaLongLossyNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.PosixConstants;
import com.oracle.graal.python.runtime.PosixConstants.IntConstant;
import com.oracle.graal.python.runtime.PosixSupport;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PosixSupportLibrary.Buffer;
import com.oracle.graal.python.runtime.PosixSupportLibrary.OpenPtyResult;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.Timeval;
import com.oracle.graal.python.runtime.PosixSupportLibrary.UnsupportedPosixFeatureException;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonExitException;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.LongSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.ThreadLocalAction;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.Encoding;

@CoreFunctions(defineModule = "posix", extendsModule = "nt", isEager = true)
public final class PosixModuleBuiltins extends PythonBuiltins {

    static final StructSequence.BuiltinTypeDescriptor STAT_RESULT_DESC = new StructSequence.BuiltinTypeDescriptor(
                    PythonBuiltinClassType.PStatResult,
                    10,
                    new String[]{
                                    "st_mode", "st_ino", "st_dev", "st_nlink", "st_uid", "st_gid", "st_size",
                                    null, null, null,
                                    "st_atime", "st_mtime", "st_ctime",
                                    "st_atime_ns", "st_mtime_ns", "st_ctime_ns"
                    },
                    new String[]{
                                    "protection bits", "inode", "device", "number of hard links",
                                    "user ID of owner", "group ID of owner", "total size, in bytes",
                                    "integer time of last access", "integer time of last modification", "integer time of last change",
                                    "time of last access", "time of last modification", "time of last change",
                                    "time of last access in nanoseconds", "time of last modification in nanoseconds", "time of last change in nanoseconds"
                    });

    static final StructSequence.BuiltinTypeDescriptor STATVFS_RESULT_DESC = new StructSequence.BuiltinTypeDescriptor(
                    PythonBuiltinClassType.PStatvfsResult,
                    10,
                    new String[]{
                                    "f_bsize", "f_frsize", "f_blocks", "f_bfree", "f_bavail", "f_files",
                                    "f_ffree", "f_favail", "f_flag", "f_namemax", "f_fsid"
                    },
                    null);

    private static final StructSequence.BuiltinTypeDescriptor TERMINAL_SIZE_DESC = new StructSequence.BuiltinTypeDescriptor(
                    PythonBuiltinClassType.PTerminalSize,
                    2,
                    new String[]{"columns", "lines"},
                    new String[]{"width of the terminal window in characters", "height of the terminal window in characters"});

    private static final StructSequence.BuiltinTypeDescriptor UNAME_RESULT_DESC = new StructSequence.BuiltinTypeDescriptor(
                    PythonBuiltinClassType.PUnameResult,
                    5,
                    new String[]{"sysname", "nodename", "release", "version", "machine"},
                    new String[]{
                                    "operating system name", "name of machine on network (implementation-defined)",
                                    "operating system release", "operating system version", "hardware identifier"
                    });

    // WNOHANG is not defined on windows, but emulated backend should support it even there
    public static final int EMULATED_WNOHANG = 1;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PosixModuleBuiltinsFactory.getFactories();
    }

    public PosixModuleBuiltins() {
        addConstants(PosixConstants.openFlags);
        addConstants(PosixConstants.waitOptions);
        addConstants(PosixConstants.accessMode);
        addConstants(PosixConstants.exitStatus);
        addConstants(PosixConstants.rtld);

        addConstant(PosixConstants.SEEK_DATA);
        addConstant(PosixConstants.SEEK_HOLE);
    }

    private void addConstant(IntConstant c) {
        if (c.defined) {
            addBuiltinConstant(c.name, c.getValueIfDefined());
        }
    }

    private void addConstants(IntConstant[] constants) {
        for (IntConstant c : constants) {
            addConstant(c);
        }
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
        ArrayList<TruffleString> haveFunctions = new ArrayList<>();
        if (PythonOS.getPythonOS() != PythonOS.PLATFORM_WIN32) {
            Collections.addAll(haveFunctions, tsLiteral("HAVE_FACCESSAT"), tsLiteral("HAVE_FCHDIR"), tsLiteral("HAVE_FCHMOD"), tsLiteral("HAVE_FCHMODAT"), tsLiteral("HAVE_FDOPENDIR"),
                            tsLiteral("HAVE_FSTATAT"), tsLiteral("HAVE_FTRUNCATE"), tsLiteral("HAVE_FUTIMES"), tsLiteral("HAVE_LUTIMES"),
                            tsLiteral("HAVE_MKDIRAT"), tsLiteral("HAVE_OPENAT"), tsLiteral("HAVE_READLINKAT"), tsLiteral("HAVE_RENAMEAT"), tsLiteral("HAVE_SYMLINKAT"), tsLiteral("HAVE_UNLINKAT"));
            // Not implemented yet:
            // "HAVE_FCHOWN", "HAVE_FCHOWNAT", "HAVE_FEXECVE", "HAVE_FPATHCONF", "HAVE_FSTATVFS",
            // "HAVE_FUTIMESAT", "HAVE_LINKAT", "HAVE_LCHFLAGS", "HAVE_LCHMOD", "HAVE_LCHOWN",
            // "HAVE_LSTAT", "HAVE_MEMFD_CREATE", "HAVE_MKFIFOAT", "HAVE_MKNODAT"
            if (PosixConstants.HAVE_FUTIMENS.value) {
                haveFunctions.add(tsLiteral("HAVE_FUTIMENS"));
            }
            if (PosixConstants.HAVE_UTIMENSAT.value) {
                haveFunctions.add(tsLiteral("HAVE_UTIMENSAT"));
            }
        } else {
            haveFunctions.add(tsLiteral("HAVE_FTRUNCATE"));
            haveFunctions.add(tsLiteral("MS_WINDOWS"));
        }
        PythonLanguage language = core.getLanguage();
        addBuiltinConstant("_have_functions", PFactory.createList(language, haveFunctions.toArray()));
        addBuiltinConstant(T_ENVIRON, PFactory.createDict(language));

        LinkedHashMap<String, Object> sysconfigNames = new LinkedHashMap<>();
        for (IntConstant name : PosixConstants.sysconfigNames) {
            if (name.defined) {
                // add the constant without the leading underscore
                String pythonName;
                if (name.name.startsWith("_")) {
                    pythonName = name.name.substring(1);
                } else {
                    pythonName = name.name;
                }
                sysconfigNames.put(pythonName, name.getValueIfDefined());
            }
        }
        addBuiltinConstant("sysconf_names", PFactory.createDictFromMap(language, sysconfigNames));

        StructSequence.initType(core, STAT_RESULT_DESC);
        StructSequence.initType(core, STATVFS_RESULT_DESC);
        StructSequence.initType(core, TERMINAL_SIZE_DESC);
        StructSequence.initType(core, UNAME_RESULT_DESC);

        // Some classes (e.g. stat_result, see below) are formally part of the 'os' module, although
        // they are exposed by the 'posix' module. In CPython, they are defined in posixmodule.c,
        // with their __module__ being set to 'os', and later they are imported by os.py.
        // Our infrastructure in PythonBuiltinClassType currently does not allow us to
        // define a class in one module (os) and make it public in another (posix), so we create
        // them directly in the 'os' module, and expose them in the `posix` module as well.
        // Note that the classes are still re-imported by os.py.
        PythonModule posix;
        if (PythonOS.getPythonOS() == PythonOS.PLATFORM_WIN32) {
            posix = core.lookupBuiltinModule(T_NT);
        } else {
            posix = core.lookupBuiltinModule(T_POSIX);
        }
        posix.setAttribute(PythonBuiltinClassType.PStatResult.getName(), core.lookupType(PythonBuiltinClassType.PStatResult));
        posix.setAttribute(PythonBuiltinClassType.PStatvfsResult.getName(), core.lookupType(PythonBuiltinClassType.PStatvfsResult));
        posix.setAttribute(PythonBuiltinClassType.PTerminalSize.getName(), core.lookupType(PythonBuiltinClassType.PTerminalSize));

        posix.setAttribute(tsLiteral("error"), core.lookupType(PythonBuiltinClassType.OSError));
    }

    @Override
    public void postInitialize(Python3Core core) {
        super.postInitialize(core);

        PosixSupportLibrary posixLib = PosixSupportLibrary.getUncached();
        Object posixSupport = core.getContext().getPosixSupport();
        PythonLanguage language = core.getLanguage();

        // fill the environ dictionary with the current environment
        // TODO we should probably use PosixSupportLibrary to get environ
        Map<String, String> getenv = core.getContext().getEnv().getEnvironment();
        PDict environ = PFactory.createDict(language);
        String pyenvLauncherKey = "__PYVENV_LAUNCHER__";
        for (Entry<String, String> entry : getenv.entrySet()) {
            if ((entry.getKey().equals("GRAAL_PYTHON_ARGS") || entry.getKey().equals("GRAAL_PYTHON_VM_ARGS")) && entry.getValue().endsWith("\013")) {
                // was already processed at startup in GraalPythonMain and
                // we don't want subprocesses to pick it up
                continue;
            }
            if (PythonOS.getPythonOS() == PythonOS.PLATFORM_WIN32 && entry.getKey().startsWith("=")) {
                // Hidden variable, shouldn't be visible to python
                continue;
            }
            Object key = toEnv(language, entry.getKey());
            Object val = toEnv(language, entry.getValue());
            if (pyenvLauncherKey.equals(entry.getKey())) {
                // On Mac, the CPython launcher uses this env variable to specify the real Python
                // executable. It will be honored by packages like "site". So, if it is set, we
                // overwrite it with our executable to ensure that subprocesses will use us.
                TruffleString value = core.getContext().getOption(PythonOptions.Executable);
                try {
                    Object k = posixLib.createPathFromString(posixSupport, toTruffleStringUncached(pyenvLauncherKey));
                    Object v = posixLib.createPathFromString(posixSupport, value);
                    posixLib.setenv(posixSupport, k, v, true);
                } catch (PosixException ignored) {
                }
                val = toEnv(language, value);
            }
            environ.setItem(key, val);
        }
        if (PythonOS.getPythonOS() == PythonOS.PLATFORM_WIN32) {
            // XXX: Until we fix pip
            environ.setItem(toEnv(language, "PIP_NO_CACHE_DIR"), toEnv(language, "0"));
        }
        // XXX: Until a pyo3 version that doesn't have a different maximum version for GraalPy than
        // CPython gets widespread
        environ.setItem(toEnv(language, "UNSAFE_PYO3_SKIP_VERSION_CHECK"), toEnv(language, "1"));
        PythonModule posix;
        if (PythonOS.getPythonOS() == PythonOS.PLATFORM_WIN32) {
            posix = core.lookupBuiltinModule(T_NT);
            posix.setAttribute(toTruffleStringUncached("chown"), PNone.NO_VALUE);
            posix.setAttribute(toTruffleStringUncached("fchown"), PNone.NO_VALUE);
            posix.setAttribute(toTruffleStringUncached("lchown"), PNone.NO_VALUE);
        } else {
            posix = core.lookupBuiltinModule(T_POSIX);
        }
        Object environAttr = posix.getAttribute(T_ENVIRON);
        ((PDict) environAttr).setDictStorage(environ.getDictStorage());

        if (posixLib.getBackend(posixSupport).toJavaStringUncached().equals("java")) {
            posix.setAttribute(toTruffleStringUncached("geteuid"), PNone.NO_VALUE);
            posix.setAttribute(toTruffleStringUncached("getegid"), PNone.NO_VALUE);

            posix.setAttribute(toTruffleStringUncached("WNOHANG"), EMULATED_WNOHANG);
        }
    }

    private static Object toEnv(PythonLanguage language, String value) {
        if (PythonOS.getPythonOS() == PythonOS.PLATFORM_WIN32) {
            return toTruffleStringUncached(value);
        } else {
            return PFactory.createBytes(language, value.getBytes());
        }
    }

    private static Object toEnv(PythonLanguage language, TruffleString value) {
        if (PythonOS.getPythonOS() == PythonOS.PLATFORM_WIN32) {
            return value;
        } else {
            return PFactory.createBytes(language, value.toJavaStringUncached().getBytes());
        }
    }

    @Builtin(name = "putenv", minNumOfPositionalArgs = 2, parameterNames = {"name", "value"})
    @ArgumentClinic(name = "name", conversionClass = FsConverterNode.class)
    @ArgumentClinic(name = "value", conversionClass = FsConverterNode.class)
    @GenerateNodeFactory
    public abstract static class PutenvNode extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.PutenvNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static PNone putenv(VirtualFrame frame, PBytes nameBytes, PBytes valueBytes,
                        @Bind Node inliningTarget,
                        @Cached BytesNodes.ToBytesNode toBytesNode,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PRaiseNode raiseNode) {
            // Unlike in other posix builtins, we go through str -> bytes -> byte[] -> String
            // conversions for emulated backend because the bytes version after fsencode conversion
            // is subject to sys.audit.
            byte[] name = toBytesNode.execute(nameBytes);
            byte[] value = toBytesNode.execute(valueBytes);
            PosixSupport posixSupport = context.getPosixSupport();
            Object nameOpaque = checkNull(inliningTarget, posixLib.createPathFromBytes(posixSupport, name), raiseNode);
            Object valueOpaque = checkNull(inliningTarget, posixLib.createPathFromBytes(posixSupport, value), raiseNode);
            checkEqualSign(inliningTarget, name, raiseNode);
            auditNode.audit(inliningTarget, "os.putenv", nameBytes, valueBytes);
            try {
                posixLib.setenv(posixSupport, nameOpaque, valueOpaque, true);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
            return PNone.NONE;
        }

        private static Object checkNull(Node inliningTarget, Object value, PRaiseNode raiseNode) {
            if (value == null) {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.EMBEDDED_NULL_BYTE);
            }
            return value;
        }

        private static void checkEqualSign(Node inliningTarget, byte[] bytes, PRaiseNode raiseNode) {
            for (byte b : bytes) {
                if (b == '=') {
                    throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.ILLEGAL_ENVIRONMENT_VARIABLE_NAME);
                }
            }
        }
    }

    @Builtin(name = "unsetenv", minNumOfPositionalArgs = 1, parameterNames = {"name"})
    @ArgumentClinic(name = "name", conversionClass = FsConverterNode.class)
    @GenerateNodeFactory
    public abstract static class UnsetenvNode extends PythonUnaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.UnsetenvNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static PNone putenv(VirtualFrame frame, PBytes nameBytes,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @Cached BytesNodes.ToBytesNode toBytesNode,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PRaiseNode raiseNode) {
            byte[] name = toBytesNode.execute(nameBytes);
            Object nameOpaque = checkNull(inliningTarget, posixLib.createPathFromBytes(context.getPosixSupport(), name), raiseNode);
            auditNode.audit(inliningTarget, "os.unsetenv", nameBytes);
            try {
                posixLib.unsetenv(context.getPosixSupport(), nameOpaque);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
            return PNone.NONE;
        }

        private static Object checkNull(Node inliningTarget, Object value, PRaiseNode raiseNode) {
            if (value == null) {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.EMBEDDED_NULL_BYTE);
            }
            return value;
        }
    }

    @Builtin(name = "execv", minNumOfPositionalArgs = 2, parameterNames = {"pathname", "argv"})
    @ArgumentClinic(name = "pathname", conversionClass = PathConversionNode.class, args = {"false", "false"})
    @GenerateNodeFactory
    public abstract static class ExecvNode extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.ExecvNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object execvArgsList(VirtualFrame frame, PosixPath path, PList argv,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Shared @Cached ToArrayNode toArrayNode,
                        @Shared @Cached ObjectToOpaquePathNode toOpaquePathNode,
                        @Shared @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Shared @Cached GilNode gil,
                        @Shared @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Shared @Cached PRaiseNode raiseNode) {
            execv(frame, path, argv, argv.getSequenceStorage(), inliningTarget, posixLib, context.getPosixSupport(), toArrayNode, toOpaquePathNode, auditNode, gil, constructAndRaiseNode, raiseNode);
            throw CompilerDirectives.shouldNotReachHere("execv should not return normally");
        }

        @Specialization
        static Object execvArgsTuple(VirtualFrame frame, PosixPath path, PTuple argv,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Shared @Cached ToArrayNode toArrayNode,
                        @Shared @Cached ObjectToOpaquePathNode toOpaquePathNode,
                        @Shared @Cached AuditNode auditNode,
                        @Shared @Cached GilNode gil,
                        @Shared @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Shared @Cached PRaiseNode raiseNode) {
            execv(frame, path, argv, argv.getSequenceStorage(), inliningTarget, posixLib, context.getPosixSupport(), toArrayNode, toOpaquePathNode, auditNode, gil, constructAndRaiseNode, raiseNode);
            throw CompilerDirectives.shouldNotReachHere("execv should not return normally");
        }

        @Specialization(guards = {"!isList(argv)", "!isPTuple(argv)"})
        @SuppressWarnings("unused")
        static Object execvInvalidArgs(VirtualFrame frame, PosixPath path, Object argv,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.ARG_D_MUST_BE_S, "execv()", 2, "tuple or list");
        }

        private static void execv(VirtualFrame frame, PosixPath path, Object argv, SequenceStorage argvStorage,
                        Node inliningTarget,
                        PosixSupportLibrary posixLib,
                        PosixSupport posixSupport,
                        SequenceStorageNodes.ToArrayNode toArrayNode,
                        ObjectToOpaquePathNode toOpaquePathNode,
                        SysModuleBuiltins.AuditNode auditNode,
                        GilNode gil,
                        PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        PRaiseNode raiseNode) {
            Object[] args = toArrayNode.execute(inliningTarget, argvStorage);
            if (args.length < 1) {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.ARG_MUST_NOT_BE_EMPTY, "execv()", 2);
            }
            Object[] opaqueArgs = new Object[args.length];
            for (int i = 0; i < args.length; ++i) {
                opaqueArgs[i] = toOpaquePathNode.execute(frame, inliningTarget, args[i], i == 0);
            }
            // TODO ValueError "execv() arg 2 first element cannot be empty"

            auditNode.audit(inliningTarget, "os.exec", path.originalObject, argv, PNone.NONE);

            gil.release(true);
            try {
                posixLib.execv(posixSupport, path.value, opaqueArgs);
            } catch (PosixException e) {
                gil.acquire();
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            } finally {
                gil.acquire();
            }
            throw CompilerDirectives.shouldNotReachHere("execv should not return normally");
        }
    }

    @Builtin(name = "getpid", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class GetPidNode extends PythonBuiltinNode {
        @Specialization
        static long getPid(@Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib) {
            return posixLib.getpid(context.getPosixSupport());
        }
    }

    @Builtin(name = "getuid", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class GetUidNode extends PythonBuiltinNode {
        @Specialization
        static long getUid(@Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib) {
            return posixLib.getuid(context.getPosixSupport());
        }
    }

    @Builtin(name = "geteuid", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class GetEUidNode extends PythonBuiltinNode {
        @Specialization
        static long getUid(@Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib) {
            return posixLib.geteuid(context.getPosixSupport());
        }
    }

    @Builtin(name = "getgid", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class GetGidNode extends PythonBuiltinNode {
        @Specialization
        static long getGid(@Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib) {
            return posixLib.getgid(context.getPosixSupport());
        }
    }

    @Builtin(name = "getegid", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class GetEGidNode extends PythonBuiltinNode {
        @Specialization
        static long getGid(@Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib) {
            return posixLib.getegid(context.getPosixSupport());
        }
    }

    @Builtin(name = "getppid", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class GetPpidNode extends PythonBuiltinNode {
        @Specialization
        static long getPpid(@Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib) {
            return posixLib.getppid(context.getPosixSupport());
        }
    }

    @Builtin(name = "getloadavg", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class GetLoadAvgNode extends PythonBuiltinNode {

        /*
         * Return average recent system load information.
         *
         * Return the number of processes in the system run queue averaged over the last 1, 5, and
         * 15 minutes as a tuple of three floats. Raises OSError if the load average was
         * unobtainable.
         */
        @TruffleBoundary
        @Specialization
        static PTuple getloadavg(@Bind Node inliningTarget,
                        @Bind PythonLanguage language) {
            double load = -1.0;
            // (mq) without native call we can only obtain system load average for the last minute.
            if (ManagementFactory.getOperatingSystemMXBean() != null) {
                load = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
            }
            if (load < 0) {
                PRaiseNode.raiseStatic(inliningTarget, OSError);
            }
            return PFactory.createTuple(language, new Object[]{load, load, load});
        }
    }

    @Builtin(name = "getpgid", minNumOfPositionalArgs = 1, parameterNames = {"pid"})
    @ArgumentClinic(name = "pid", conversionClass = PidtConversionNode.class)
    @GenerateNodeFactory
    public abstract static class GetPgidNode extends PythonUnaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.GetPgidNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static long getPgid(VirtualFrame frame, long pid,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                return posixLib.getpgid(context.getPosixSupport(), pid);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    @Builtin(name = "setpgid", minNumOfPositionalArgs = 2, parameterNames = {"pid", "pgid"})
    @ArgumentClinic(name = "pid", conversionClass = PidtConversionNode.class)
    @ArgumentClinic(name = "pgid", conversionClass = PidtConversionNode.class)
    @GenerateNodeFactory
    public abstract static class SetPgidNode extends PythonBinaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.SetPgidNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object setPgid(VirtualFrame frame, long pid, long pgid,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                posixLib.setpgid(context.getPosixSupport(), pid, pgid);
                return PNone.NONE;
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    @Builtin(name = "setpgrp", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class SetPgrpdNode extends PythonBuiltinNode {
        @Specialization
        static Object getPpid(VirtualFrame frame,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                posixLib.setpgid(context.getPosixSupport(), 0, 0);
                return PNone.NONE;
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    @Builtin(name = "getpgrp", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class GetPgrpNode extends PythonBuiltinNode {
        @Specialization
        static long getPpid(@Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib) {
            return posixLib.getpgrp(context.getPosixSupport());
        }
    }

    @Builtin(name = "getsid", minNumOfPositionalArgs = 1, parameterNames = {"pid"})
    @ArgumentClinic(name = "pid", conversionClass = PidtConversionNode.class)
    @GenerateNodeFactory
    public abstract static class GetSidNode extends PythonUnaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.GetSidNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static long getSid(VirtualFrame frame, long pid,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                return posixLib.getsid(context.getPosixSupport(), pid);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    @Builtin(name = "setsid")
    @GenerateNodeFactory
    public abstract static class SetSidNode extends PythonBuiltinNode {

        @Specialization
        static Object setsid(VirtualFrame frame,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                posixLib.setsid(context.getPosixSupport());
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "getgroups")
    @GenerateNodeFactory
    abstract static class GetGroupsNode extends PythonBuiltinNode {
        @Specialization
        static Object getgroups(VirtualFrame frame,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                long[] groups = posixLib.getgroups(context.getPosixSupport());
                return PFactory.createList(context.getLanguage(inliningTarget), new LongSequenceStorage(groups));
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    @Builtin(name = "openpty")
    @GenerateNodeFactory
    public abstract static class OpenPtyNode extends PythonBuiltinNode {

        @Specialization
        static Object openpty(VirtualFrame frame,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                OpenPtyResult result = posixLib.openpty(context.getPosixSupport());
                posixLib.setInheritable(context.getPosixSupport(), result.masterFd(), false);
                posixLib.setInheritable(context.getPosixSupport(), result.slaveFd(), false);
                return PFactory.createTuple(context.getLanguage(inliningTarget), new int[]{result.masterFd(), result.slaveFd()});
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    @Builtin(name = "open", numOfPositionalOnlyArgs = 0, minNumOfPositionalArgs = 2, parameterNames = {"path", "flags", "mode"}, keywordOnlyNames = {"dir_fd"})
    @ArgumentClinic(name = "path", conversionClass = PathConversionNode.class, args = {"false", "false"})
    @ArgumentClinic(name = "flags", conversion = ClinicConversion.Int)
    @ArgumentClinic(name = "mode", conversion = ClinicConversion.Int, defaultValue = "0777")
    @ArgumentClinic(name = "dir_fd", conversionClass = DirFdConversionNode.class)
    @GenerateNodeFactory
    public abstract static class OpenNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.OpenNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static int open(VirtualFrame frame, PosixPath path, int flags, int mode, int dirFd,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Cached InlinedBranchProfile errorProfile,
                        @Cached GilNode gil,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            int fixedFlags = flags;
            if (O_CLOEXEC.defined) {
                fixedFlags |= O_CLOEXEC.getValueIfDefined();
            }
            auditNode.audit(inliningTarget, "open", path.originalObject, PNone.NONE, fixedFlags);
            gil.release(true);
            try {
                while (true) {
                    try {
                        return posixLib.openat(context.getPosixSupport(), dirFd, path.value, fixedFlags, mode);
                    } catch (PosixException e) {
                        errorProfile.enter(inliningTarget);
                        if (e.getErrorCode() == OSErrorEnum.EINTR.getNumber()) {
                            PythonContext.triggerAsyncActions(inliningTarget);
                        } else {
                            gil.acquire(); // need GIL to construct OSError
                            throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e, path.originalObject);
                        }
                    }
                }
            } finally {
                gil.acquire();
            }
        }
    }

    @Builtin(name = "close", minNumOfPositionalArgs = 1, parameterNames = {"fd"})
    @ArgumentClinic(name = "fd", conversion = ClinicConversion.Int)
    @GenerateNodeFactory
    public abstract static class CloseNode extends PythonUnaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.CloseNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static PNone close(VirtualFrame frame, int fd,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached GilNode gil,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                if (context.getSharedMultiprocessingData().decrementFDRefCount(fd)) {
                    return PNone.NONE;
                }
                gil.release(true);
                try {
                    posixLib.close(context.getPosixSupport(), fd);
                } finally {
                    gil.acquire();
                }
                return PNone.NONE;
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    @Builtin(name = "read", minNumOfPositionalArgs = 2, parameterNames = {"fd", "length"})
    @ArgumentClinic(name = "fd", conversion = ClinicConversion.Int)
    @ArgumentClinic(name = "length", conversion = ClinicConversion.Index)
    @GenerateNodeFactory
    public abstract static class ReadNode extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.ReadNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static PBytes doRead(VirtualFrame frame, int fd, int length,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached InlinedBranchProfile errorProfile1,
                        @Cached InlinedBranchProfile errorProfile2,
                        @Cached GilNode gil,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            if (length < 0) {
                int error = OSErrorEnum.EINVAL.getNumber();
                throw constructAndRaiseNode.get(inliningTarget).raiseOSError(frame, error, posixLib.strerror(context.getPosixSupport(), error));
            }
            try {
                return read(fd, length, inliningTarget, posixLib, context.getPosixSupport(), errorProfile1, gil);
            } catch (PosixException e) {
                errorProfile2.enter(inliningTarget);
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
        }

        public static PBytes read(int fd, int length,
                        Node inliningTarget,
                        PosixSupportLibrary posixLib,
                        PosixSupport posixSupport,
                        InlinedBranchProfile errorProfile, GilNode gil) throws PosixException {
            gil.release(true);
            try {
                while (true) {
                    try {
                        Buffer result = posixLib.read(posixSupport, fd, length);
                        if (result.length > Integer.MAX_VALUE) {
                            // sanity check that it is safe to cast result.length to int, to be
                            // removed once we support large arrays
                            throw CompilerDirectives.shouldNotReachHere("Posix read() returned more bytes than requested");
                        }
                        return PFactory.createBytes(PythonLanguage.get(inliningTarget), result.data, (int) result.length);
                    } catch (PosixException e) {
                        errorProfile.enter(inliningTarget);
                        if (e.getErrorCode() == OSErrorEnum.EINTR.getNumber()) {
                            PythonContext.triggerAsyncActions(inliningTarget);
                        } else {
                            throw e;
                        }
                    }
                }
            } finally {
                gil.acquire();
            }
        }
    }

    @Builtin(name = "write", minNumOfPositionalArgs = 2, parameterNames = {"fd", "data"})
    @ArgumentClinic(name = "fd", conversion = ClinicConversion.Int)
    @ArgumentClinic(name = "data", conversion = ClinicConversion.ReadableBuffer)
    @GenerateNodeFactory
    public abstract static class WriteNode extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.WriteNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(limit = "3")
        static long doWrite(VirtualFrame frame, int fd, Object dataBuffer,
                        @Bind Node inliningTarget,
                        @Cached("createFor($node)") IndirectCallData indirectCallData,
                        @CachedLibrary("dataBuffer") PythonBufferAccessLibrary bufferLib,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached InlinedBranchProfile errorProfile,
                        @Cached GilNode gil,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                return write(fd, bufferLib.getInternalOrCopiedByteArray(dataBuffer), bufferLib.getBufferLength(dataBuffer), inliningTarget, posixLib, context.getPosixSupport(), errorProfile, gil);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            } finally {
                bufferLib.release(dataBuffer, frame, indirectCallData);
            }
        }

        public static long write(int fd, byte[] dataBytes,
                        int dataLen, Node inliningTarget, PosixSupportLibrary posixLib, PosixSupport posixSupport,
                        InlinedBranchProfile errorProfile, GilNode gil) throws PosixException {
            gil.release(true);
            try {
                while (true) {
                    try {
                        return posixLib.write(posixSupport, fd, new Buffer(dataBytes, dataLen));
                    } catch (PosixException e) {
                        errorProfile.enter(inliningTarget);
                        if (e.getErrorCode() == OSErrorEnum.EINTR.getNumber()) {
                            PythonContext.triggerAsyncActions(inliningTarget);
                        } else {
                            throw e;
                        }
                    }
                }
            } finally {
                gil.acquire();
            }
        }
    }

    @Builtin(name = "dup", minNumOfPositionalArgs = 1, parameterNames = {"fd"})
    @ArgumentClinic(name = "fd", conversion = ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class DupNode extends PythonUnaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.DupNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static int dup(VirtualFrame frame, int fd,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                return posixLib.dup(context.getPosixSupport(), fd);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    @Builtin(name = "dup2", minNumOfPositionalArgs = 2, parameterNames = {"fd", "fd2", "inheritable"})
    @ArgumentClinic(name = "fd", conversion = ClinicConversion.Int)
    @ArgumentClinic(name = "fd2", conversion = ClinicConversion.Int)
    @ArgumentClinic(name = "inheritable", conversion = ClinicConversion.Boolean, defaultValue = "true")
    @GenerateNodeFactory
    abstract static class Dup2Node extends PythonTernaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.Dup2NodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static int dup2(VirtualFrame frame, int fd, int fd2, boolean inheritable,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            if (fd < 0 || fd2 < 0) {
                // CPython does not set errno here and raises a 'random' OSError
                // (possibly with errno=0 Success)
                int error = OSErrorEnum.EINVAL.getNumber();
                throw constructAndRaiseNode.get(inliningTarget).raiseOSError(frame, error, posixLib.strerror(context.getPosixSupport(), error));
            }

            try {
                return posixLib.dup2(context.getPosixSupport(), fd, fd2, inheritable);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    @Builtin(name = "get_inheritable", minNumOfPositionalArgs = 1, parameterNames = {"fd"})
    @ArgumentClinic(name = "fd", conversion = ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class GetInheritableNode extends PythonUnaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.GetInheritableNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static boolean getInheritable(VirtualFrame frame, int fd,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                return posixLib.getInheritable(context.getPosixSupport(), fd);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    @Builtin(name = "set_inheritable", minNumOfPositionalArgs = 2, parameterNames = {"fd", "inheritable"})
    @ArgumentClinic(name = "fd", conversion = ClinicConversion.Int)
    @ArgumentClinic(name = "inheritable", conversion = ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class SetInheritableNode extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.SetInheritableNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static PNone setInheritable(VirtualFrame frame, int fd, int inheritable,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                // not sure why inheritable is not a boolean, but that is how they do it in CPython
                posixLib.setInheritable(context.getPosixSupport(), fd, inheritable != 0);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "pipe", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class PipeNode extends PythonBuiltinNode {

        @Specialization
        static PTuple pipe(VirtualFrame frame,
                        @Bind Node inliningTarget,
                        @Cached GilNode gil,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            int[] pipe;
            gil.release(true);
            try {
                pipe = posixLib.pipe(context.getPosixSupport());
            } catch (PosixException e) {
                gil.acquire(); // need to acquire the gil to construct the OSError object
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            } finally {
                gil.acquire();
            }
            return PFactory.createTuple(context.getLanguage(inliningTarget), new Object[]{pipe[0], pipe[1]});
        }
    }

    public static int mapPythonSeekWhenceToPosix(int pythonWhence) {
        // See os.py
        switch (pythonWhence) {
            case 0:
                return PosixConstants.SEEK_SET.value;
            case 1:
                return PosixConstants.SEEK_CUR.value;
            case 2:
                return PosixConstants.SEEK_END.value;
            default:
                return pythonWhence;
        }
    }

    @Builtin(name = "lseek", minNumOfPositionalArgs = 3, parameterNames = {"fd", "pos", "how"})
    @ArgumentClinic(name = "fd", conversion = ClinicConversion.Int)
    @ArgumentClinic(name = "pos", conversionClass = OffsetConversionNode.class)
    @ArgumentClinic(name = "how", conversion = ClinicConversion.Int)
    @GenerateNodeFactory
    public abstract static class LseekNode extends PythonTernaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.LseekNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static long lseek(VirtualFrame frame, int fd, long pos, int how,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                return posixLib.lseek(context.getPosixSupport(), fd, pos, mapPythonSeekWhenceToPosix(how));
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    @Builtin(name = "ftruncate", minNumOfPositionalArgs = 2, parameterNames = {"fd", "length"})
    @ArgumentClinic(name = "fd", conversion = ClinicConversion.Int)
    @ArgumentClinic(name = "length", conversionClass = OffsetConversionNode.class)
    @GenerateNodeFactory
    public abstract static class FtruncateNode extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.FtruncateNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static PNone ftruncate(VirtualFrame frame, int fd, long length,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Cached GilNode gil,
                        @Cached InlinedBranchProfile errorProfile,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            auditNode.audit(inliningTarget, "os.truncate", fd, length);
            while (true) {
                try {
                    gil.release(true);
                    try {
                        posixLib.ftruncate(context.getPosixSupport(), fd, length);
                    } finally {
                        gil.acquire();
                    }
                    return PNone.NONE;
                } catch (PosixException e) {
                    errorProfile.enter(inliningTarget);
                    if (e.getErrorCode() == OSErrorEnum.EINTR.getNumber()) {
                        PythonContext.triggerAsyncActions(inliningTarget);
                    } else {
                        throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
                    }
                }
            }
        }
    }

    @Builtin(name = "truncate", minNumOfPositionalArgs = 2, parameterNames = {"path", "length"})
    @ArgumentClinic(name = "path", conversionClass = PathConversionNode.class, args = {"false", "true"})
    @ArgumentClinic(name = "length", conversionClass = OffsetConversionNode.class)
    @GenerateNodeFactory
    public abstract static class TruncateNode extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.TruncateNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static PNone truncate(VirtualFrame frame, PosixPath path, long length,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Shared @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Shared @Cached GilNode gil,
                        @Shared @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            auditNode.audit(inliningTarget, "os.truncate", path.originalObject, length);
            try {
                gil.release(true);
                try {
                    posixLib.truncate(context.getPosixSupport(), path.value, length);
                } finally {
                    gil.acquire();
                }
                return PNone.NONE;
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e, path.originalObject);
            }
        }

        @Specialization
        static PNone ftruncate(VirtualFrame frame, PosixFd fd, long length,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Shared @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Shared @Cached GilNode gil,
                        @Cached InlinedBranchProfile errorProfile,
                        @Shared @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            return FtruncateNode.ftruncate(frame, fd.fd, length, inliningTarget, context, posixLib, auditNode, gil, errorProfile, constructAndRaiseNode);
        }
    }

    @Builtin(name = "fsync", minNumOfPositionalArgs = 1, parameterNames = "fd")
    @ArgumentClinic(name = "fd", conversionClass = FileDescriptorConversionNode.class)
    @GenerateNodeFactory
    abstract static class FSyncNode extends PythonUnaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.FSyncNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static PNone fsync(VirtualFrame frame, int fd,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached InlinedBranchProfile errorProfile,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            while (true) {
                try {
                    posixLib.fsync(context.getPosixSupport(), fd);
                    return PNone.NONE;
                } catch (PosixException e) {
                    errorProfile.enter(inliningTarget);
                    if (e.getErrorCode() == OSErrorEnum.EINTR.getNumber()) {
                        PythonContext.triggerAsyncActions(inliningTarget);
                    } else {
                        throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
                    }
                }
            }
        }
    }

    @Builtin(name = "get_blocking", minNumOfPositionalArgs = 1, parameterNames = {"fd"})
    @ArgumentClinic(name = "fd", conversion = ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class GetBlockingNode extends PythonUnaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.GetBlockingNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static boolean getBlocking(VirtualFrame frame, int fd,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                return posixLib.getBlocking(context.getPosixSupport(), fd);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    @Builtin(name = "set_blocking", minNumOfPositionalArgs = 2, parameterNames = {"fd", "blocking"})
    @ArgumentClinic(name = "fd", conversion = ClinicConversion.Int)
    @ArgumentClinic(name = "blocking", conversion = ClinicConversion.IntToBoolean)
    @GenerateNodeFactory
    abstract static class SetBlockingNode extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.SetBlockingNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static PNone setBlocking(VirtualFrame frame, int fd, boolean blocking,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                posixLib.setBlocking(context.getPosixSupport(), fd, blocking);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "get_terminal_size", minNumOfPositionalArgs = 0, parameterNames = {"fd"})
    @ArgumentClinic(name = "fd", conversion = ClinicConversion.Int, defaultValue = "1")
    @GenerateNodeFactory
    abstract static class GetTerminalSizeNode extends PythonUnaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.GetTerminalSizeNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static PTuple getTerminalSize(VirtualFrame frame, int fd,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            // TODO default value should be fileno(stdout)
            try {
                int[] result = posixLib.getTerminalSize(context.getPosixSupport(), fd);
                return PFactory.createStructSeq(context.getLanguage(inliningTarget), TERMINAL_SIZE_DESC, result[0], result[1]);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    @Builtin(name = "stat", minNumOfPositionalArgs = 1, parameterNames = {"path"}, keywordOnlyNames = {"dir_fd", "follow_symlinks"})
    @ArgumentClinic(name = "path", conversionClass = PathConversionNode.class, args = {"false", "true"})
    @ArgumentClinic(name = "dir_fd", conversionClass = DirFdConversionNode.class)
    @ArgumentClinic(name = "follow_symlinks", conversion = ClinicConversion.Boolean, defaultValue = "true")
    @GenerateNodeFactory
    abstract static class StatNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.StatNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static PTuple doStatPath(VirtualFrame frame, PosixPath path, int dirFd, boolean followSymlinks,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Shared("positive") @Cached InlinedConditionProfile positiveLongProfile,
                        @Shared @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                long[] out = posixLib.fstatat(context.getPosixSupport(), dirFd, path.value, followSymlinks);
                return createStatResult(inliningTarget, context.getLanguage(inliningTarget), positiveLongProfile, out);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e, path.originalObject);
            }
        }

        @Specialization(guards = "!isDefault(dirFd)")
        @SuppressWarnings("unused")
        static PTuple doStatFdWithDirFd(PosixFd fd, int dirFd, boolean followSymlinks,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.CANT_SPECIFY_DIRFD_WITHOUT_PATH, "stat");
        }

        @Specialization(guards = {"isDefault(dirFd)", "!followSymlinks"})
        @SuppressWarnings("unused")
        static PTuple doStatFdWithFollowSymlinks(VirtualFrame frame, PosixFd fd, int dirFd, boolean followSymlinks,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.CANNOT_USE_FD_AND_FOLLOW_SYMLINKS_TOGETHER, "stat");
        }

        @Specialization(guards = {"isDefault(dirFd)", "followSymlinks"})
        static PTuple doStatFd(VirtualFrame frame, PosixFd fd, @SuppressWarnings("unused") int dirFd, @SuppressWarnings("unused") boolean followSymlinks,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Shared("positive") @Cached InlinedConditionProfile positiveLongProfile,
                        @Shared @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                long[] out = posixLib.fstat(context.getPosixSupport(), fd.fd);
                return createStatResult(inliningTarget, context.getLanguage(inliningTarget), positiveLongProfile, out);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e, fd.originalObject);
            }
        }

        protected static boolean isDefault(int dirFd) {
            return dirFd == AT_FDCWD.value;
        }
    }

    @Builtin(name = "lstat", minNumOfPositionalArgs = 1, parameterNames = {"path"}, keywordOnlyNames = {"dir_fd"})
    @ArgumentClinic(name = "path", conversionClass = PathConversionNode.class, args = {"false", "false"})
    @ArgumentClinic(name = "dir_fd", conversionClass = DirFdConversionNode.class)
    @GenerateNodeFactory
    abstract static class LStatNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.LStatNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static PTuple doStatPath(VirtualFrame frame, PosixPath path, int dirFd,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached InlinedConditionProfile positiveLongProfile,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                // TODO we used to return all zeros when the filename was equal to sys.executable
                long[] out = posixLib.fstatat(context.getPosixSupport(), dirFd, path.value, false);
                return createStatResult(inliningTarget, context.getLanguage(inliningTarget), positiveLongProfile, out);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e, path.originalObject);
            }
        }
    }

    @Builtin(name = "fstat", minNumOfPositionalArgs = 1, parameterNames = {"fd"})
    @ArgumentClinic(name = "fd", conversion = ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class FStatNode extends PythonUnaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.FStatNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static PTuple doStatFd(VirtualFrame frame, int fd,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached InlinedConditionProfile positiveLongProfile,
                        @Cached InlinedBranchProfile errorProfile,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            while (true) {
                try {
                    long[] out = posixLib.fstat(context.getPosixSupport(), fd);
                    return createStatResult(inliningTarget, context.getLanguage(inliningTarget), positiveLongProfile, out);
                } catch (PosixException e) {
                    errorProfile.enter(inliningTarget);
                    if (e.getErrorCode() == OSErrorEnum.EINTR.getNumber()) {
                        PythonContext.triggerAsyncActions(inliningTarget);
                    } else {
                        throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
                    }
                }
            }
        }
    }

    private static PTuple createStatvfsResult(Node inliningTarget, long[] out, InlinedConditionProfile positiveLongProfile, PythonLanguage language) {
        Object[] res = new Object[out.length];
        for (int i = 0; i < out.length; i++) {
            res[i] = PInt.createPythonIntFromUnsignedLong(inliningTarget, language, positiveLongProfile, out[i]);
        }
        return PFactory.createStructSeq(language, STATVFS_RESULT_DESC, res);
    }

    @Builtin(name = "statvfs", minNumOfPositionalArgs = 1, parameterNames = {"path"})
    @ArgumentClinic(name = "path", conversionClass = PathConversionNode.class, args = {"false", "true"})
    @GenerateNodeFactory
    abstract static class StatvfsNode extends PythonUnaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.StatvfsNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static PTuple doStatvfs(VirtualFrame frame, PosixFileHandle posixFileHandle,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached InlinedConditionProfile posixPathProfile,
                        @Cached InlinedConditionProfile positiveLongProfile,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            long[] out;
            try {
                if (posixPathProfile.profile(inliningTarget, posixFileHandle instanceof PosixPath)) {
                    out = posixLib.statvfs(context.getPosixSupport(), ((PosixPath) posixFileHandle).value);
                } else {
                    out = posixLib.fstatvfs(context.getPosixSupport(), ((PosixFd) posixFileHandle).fd);
                }
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e, posixFileHandle.originalObject);
            }
            return createStatvfsResult(inliningTarget, out, positiveLongProfile, context.getLanguage(inliningTarget));
        }
    }

    @Builtin(name = "fstatvfs", minNumOfPositionalArgs = 1, parameterNames = {"fd"})
    @ArgumentClinic(name = "fd", conversion = ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class FStatvfsNode extends PythonUnaryClinicBuiltinNode {

        @Specialization
        static PTuple doStatvfs(VirtualFrame frame, int fd,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached InlinedConditionProfile positiveLongProfile,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            long[] out;
            try {
                out = posixLib.fstatvfs(context.getPosixSupport(), fd);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e, fd);
            }
            return createStatvfsResult(inliningTarget, out, positiveLongProfile, context.getLanguage(inliningTarget));
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.FStatvfsNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "uname", minNumOfPositionalArgs = 0, os = PythonOS.PLATFORM_LINUX)
    @Builtin(name = "uname", minNumOfPositionalArgs = 0, os = PythonOS.PLATFORM_DARWIN)
    @GenerateNodeFactory
    abstract static class UnameNode extends PythonBuiltinNode {

        @Specialization
        static PTuple uname(VirtualFrame frame,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                return PFactory.createStructSeq(context.getLanguage(inliningTarget), UNAME_RESULT_DESC, posixLib.uname(context.getPosixSupport()));
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    @Builtin(name = "unlink", minNumOfPositionalArgs = 1, parameterNames = {"path"}, keywordOnlyNames = {"dir_fd"})
    @ArgumentClinic(name = "path", conversionClass = PathConversionNode.class, args = {"false", "false"})
    @ArgumentClinic(name = "dir_fd", conversionClass = DirFdConversionNode.class)
    @GenerateNodeFactory
    abstract static class UnlinkNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.UnlinkNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static PNone unlink(VirtualFrame frame, PosixPath path, int dirFd,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Bind Node inliningTarget,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            auditNode.audit(inliningTarget, "os.remove", path.originalObject, dirFdForAudit(dirFd));
            try {
                posixLib.unlinkat(context.getPosixSupport(), dirFd, path.value, false);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e, path.originalObject);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "remove", minNumOfPositionalArgs = 1, parameterNames = {"path"}, keywordOnlyNames = {"dir_fd"})
    @ArgumentClinic(name = "path", conversionClass = PathConversionNode.class, args = {"false", "false"})
    @ArgumentClinic(name = "dir_fd", conversionClass = DirFdConversionNode.class)
    @GenerateNodeFactory
    abstract static class RemoveNode extends UnlinkNode {

        // although this built-in is the same as unlink(), we need to provide our own
        // ArgumentClinicProvider because the error messages contain the name of the built-in

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.RemoveNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "link", minNumOfPositionalArgs = 2, parameterNames = {"src", "dst"}, keywordOnlyNames = {"src_dir_fd", "dst_dir_fd", "follow_symlinks"})
    @ArgumentClinic(name = "src", conversionClass = PathConversionNode.class, args = {"false", "false"})
    @ArgumentClinic(name = "dst", conversionClass = PathConversionNode.class, args = {"false", "false"})
    @ArgumentClinic(name = "src_dir_fd", conversionClass = DirFdConversionNode.class)
    @ArgumentClinic(name = "dst_dir_fd", conversionClass = DirFdConversionNode.class)
    @ArgumentClinic(name = "follow_symlinks", conversion = ClinicConversion.Boolean, defaultValue = "false")
    @GenerateNodeFactory
    abstract static class LinkNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.LinkNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static PNone link(VirtualFrame frame, PosixPath src, PosixPath dst, int srcDirFd, int dstDirFd, boolean followSymlinks,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                posixLib.linkat(context.getPosixSupport(), srcDirFd, src.value, dstDirFd, dst.value, followSymlinks ? AT_SYMLINK_FOLLOW.value : 0);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e, src.originalObject, dst.originalObject);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "symlink", minNumOfPositionalArgs = 2, parameterNames = {"src", "dst", "target_is_directory"}, keywordOnlyNames = {"dir_fd"})
    @ArgumentClinic(name = "src", conversionClass = PathConversionNode.class, args = {"false", "false"})
    @ArgumentClinic(name = "dst", conversionClass = PathConversionNode.class, args = {"false", "false"})
    @ArgumentClinic(name = "target_is_directory", conversion = ClinicConversion.Boolean, defaultValue = "false")
    @ArgumentClinic(name = "dir_fd", conversionClass = DirFdConversionNode.class)
    @GenerateNodeFactory
    abstract static class SymlinkNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.SymlinkNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static PNone symlink(VirtualFrame frame, PosixPath src, PosixPath dst, @SuppressWarnings("unused") boolean targetIsDir, int dirFd,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                posixLib.symlinkat(context.getPosixSupport(), src.value, dirFd, dst.value);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e, src.originalObject, dst.originalObject);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "mkdir", minNumOfPositionalArgs = 1, parameterNames = {"path", "mode"}, keywordOnlyNames = {"dir_fd"})
    @ArgumentClinic(name = "path", conversionClass = PathConversionNode.class, args = {"false", "false"})
    @ArgumentClinic(name = "mode", conversion = ClinicConversion.Int, defaultValue = "0777")
    @ArgumentClinic(name = "dir_fd", conversionClass = DirFdConversionNode.class)
    @GenerateNodeFactory
    abstract static class MkdirNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.MkdirNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static PNone mkdir(VirtualFrame frame, PosixPath path, int mode, int dirFd,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Bind Node inliningTarget,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            auditNode.audit(inliningTarget, "os.mkdir", path.originalObject, mode, dirFdForAudit(dirFd));
            try {
                posixLib.mkdirat(context.getPosixSupport(), dirFd, path.value, mode);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e, path.originalObject);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "rmdir", minNumOfPositionalArgs = 1, parameterNames = {"path"}, keywordOnlyNames = {"dir_fd"})
    @ArgumentClinic(name = "path", conversionClass = PathConversionNode.class, args = {"false", "false"})
    @ArgumentClinic(name = "dir_fd", conversionClass = DirFdConversionNode.class)
    @GenerateNodeFactory
    abstract static class RmdirNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.RmdirNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static PNone rmdir(VirtualFrame frame, PosixPath path, int dirFd,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Bind Node inliningTarget,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            auditNode.audit(inliningTarget, "os.rmdir", path.originalObject, dirFdForAudit(dirFd));
            try {
                posixLib.unlinkat(context.getPosixSupport(), dirFd, path.value, true);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e, path.originalObject);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "getcwd", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class GetcwdNode extends PythonBuiltinNode {
        @Specialization
        static TruffleString getcwd(VirtualFrame frame,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                return posixLib.getPathAsString(context.getPosixSupport(), posixLib.getcwd(context.getPosixSupport()));
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    @Builtin(name = "getcwdb", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class GetcwdbNode extends PythonBuiltinNode {
        @Specialization
        static PBytes getcwdb(VirtualFrame frame,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                Object path = posixLib.getcwd(context.getPosixSupport());
                return opaquePathToBytes(path, posixLib, context.getPosixSupport(), context.getLanguage(inliningTarget));
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    @Builtin(name = "chdir", minNumOfPositionalArgs = 1, parameterNames = {"path"})
    @ArgumentClinic(name = "path", conversionClass = PathConversionNode.class, args = {"false", "true"})
    @GenerateNodeFactory
    abstract static class ChdirNode extends PythonUnaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.ChdirNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static PNone chdirPath(VirtualFrame frame, PosixPath path,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Shared @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                posixLib.chdir(context.getPosixSupport(), path.value);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e, path.originalObject);
            }
            return PNone.NONE;
        }

        @Specialization
        static PNone chdirFd(VirtualFrame frame, PosixFd fd,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Shared @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                posixLib.fchdir(context.getPosixSupport(), fd.fd);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e, fd.originalObject);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "fchdir", minNumOfPositionalArgs = 1, parameterNames = {"fd"})
    @ArgumentClinic(name = "fd", conversionClass = FileDescriptorConversionNode.class)
    @GenerateNodeFactory
    abstract static class FchdirNode extends PythonUnaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.FchdirNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static PNone fchdir(VirtualFrame frame, int fd,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached InlinedBranchProfile errorProfile,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            while (true) {
                try {
                    posixLib.fchdir(context.getPosixSupport(), fd);
                    return PNone.NONE;
                } catch (PosixException e) {
                    errorProfile.enter(inliningTarget);
                    if (e.getErrorCode() == OSErrorEnum.EINTR.getNumber()) {
                        PythonContext.triggerAsyncActions(inliningTarget);
                    } else {
                        throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
                    }
                }
            }
        }
    }

    @Builtin(name = "isatty", minNumOfPositionalArgs = 1, parameterNames = {"fd"})
    @ArgumentClinic(name = "fd", conversion = ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class IsattyNode extends PythonUnaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.IsattyNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static boolean isatty(int fd,
                        @Cached GilNode gil,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib) {
            gil.release(true);
            try {
                return posixLib.isatty(context.getPosixSupport(), fd);
            } finally {
                gil.acquire();
            }
        }
    }

    @Builtin(name = "scandir", minNumOfPositionalArgs = 0, parameterNames = {"path"})
    @ArgumentClinic(name = "path", conversionClass = PathConversionNode.class, args = {"true", "true"})
    @GenerateNodeFactory
    abstract static class ScandirNode extends PythonUnaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.ScandirNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static PScandirIterator scandirPath(VirtualFrame frame, PosixPath path,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Bind Node inliningTarget,
                        @Shared @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Shared @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            auditNode.audit(inliningTarget, "os.scandir", path.originalObject == null ? PNone.NONE : path.originalObject);
            try {
                return PFactory.createScandirIterator(context.getLanguage(inliningTarget), context, posixLib.opendir(context.getPosixSupport(), path.value), path, false);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e, path.originalObject);
            }
        }

        @Specialization
        static PScandirIterator scandirFd(VirtualFrame frame, PosixFd fd,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Bind Node inliningTarget,
                        @Shared @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Shared @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            auditNode.audit(inliningTarget, "os.scandir", fd.originalObject);
            Object dirStream = dupAndFdopendir(frame, inliningTarget, posixLib, context.getPosixSupport(), fd, constructAndRaiseNode);
            return PFactory.createScandirIterator(context.getLanguage(inliningTarget), context, dirStream, fd, true);
        }
    }

    @Builtin(name = "listdir", minNumOfPositionalArgs = 0, parameterNames = {"path"})
    @ArgumentClinic(name = "path", conversionClass = PathConversionNode.class, args = {"true", "true"})
    @GenerateNodeFactory
    abstract static class ListdirNode extends PythonUnaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.ListdirNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static PList listdirPath(VirtualFrame frame, PosixPath path,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Shared @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Shared @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            auditNode.audit(inliningTarget, "os.listdir", path.originalObject == null ? PNone.NONE : path.originalObject);
            try {
                return listdir(frame, inliningTarget, posixLib.opendir(context.getPosixSupport(), path.value), path.wasBufferLike, false, posixLib, constructAndRaiseNode,
                                context.getLanguage(inliningTarget), context.getPosixSupport());
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e, path.originalObject);
            }
        }

        @Specialization
        static PList listdirFd(VirtualFrame frame, PosixFd fd,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Shared @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Shared @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            auditNode.audit(inliningTarget, "os.listdir", fd.originalObject);
            Object dirStream = dupAndFdopendir(frame, inliningTarget, posixLib, context.getPosixSupport(), fd, constructAndRaiseNode);
            return listdir(frame, inliningTarget, dirStream, false, true, posixLib, constructAndRaiseNode, context.getLanguage(inliningTarget), context.getPosixSupport());
        }

        private static PList listdir(VirtualFrame frame, Node inliningTarget, Object dirStream, boolean produceBytes, boolean needsRewind, PosixSupportLibrary posixLib,
                        PConstructAndRaiseNode.Lazy constructAndRaiseNode, PythonLanguage language, PosixSupport posixSupport) {
            List<Object> list = new ArrayList<>();
            try {
                while (true) {
                    Object dirEntry = posixLib.readdir(posixSupport, dirStream);
                    if (dirEntry == null) {
                        return PFactory.createList(language, listToArray(list));
                    }
                    Object name = posixLib.dirEntryGetName(posixSupport, dirEntry);
                    if (produceBytes) {
                        addToList(list, opaquePathToBytes(name, posixLib, posixSupport, language));
                    } else {
                        addToList(list, posixLib.getPathAsString(posixSupport, name));
                    }
                }
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            } finally {
                if (needsRewind) {
                    posixLib.rewinddir(posixSupport, dirStream);
                }
                try {
                    posixLib.closedir(posixSupport, dirStream);
                } catch (PosixException e) {
                    // ignored (CPython does not check the return value of closedir)
                }
            }
        }

        @TruffleBoundary
        private static void addToList(List<Object> list, Object element) {
            list.add(element);
        }

        @TruffleBoundary
        private static Object[] listToArray(List<Object> list) {
            return list.toArray();
        }
    }

    static Object dupAndFdopendir(VirtualFrame frame, Node inliningTarget, PosixSupportLibrary posixLib, Object posixSupport, PosixFd fd, PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
        int dupFd = -1;
        try {
            dupFd = posixLib.dup(posixSupport, fd.fd);
            // when fdopenddir succeeds, we are no longer responsible for closing dupFd
            return posixLib.fdopendir(posixSupport, dupFd);
        } catch (PosixException e) {
            if (dupFd != -1) {
                try {
                    posixLib.close(posixSupport, dupFd);
                } catch (PosixException e1) {
                    // ignored
                }
            }
            throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e, fd.originalObject);
        }
    }

    @ImportStatic(PGuards.class)
    @SuppressWarnings("truffle-inlining")       // footprint reduction 36 -> 17
    abstract static class UtimeArgsToTimespecNode extends Node {
        abstract long[] execute(VirtualFrame frame, Object times, Object ns);

        final Timeval[] toTimeval(VirtualFrame frame, Object times, Object ns) {
            long[] timespec = execute(frame, times, ns);
            return timespec == null ? null : new Timeval[]{new Timeval(timespec[0], timespec[1] / 1000), new Timeval(timespec[2], timespec[3] / 1000)};
        }

        @Specialization(guards = {"isNoValue(ns)"})
        @SuppressWarnings("unused")
        static long[] now(VirtualFrame frame, PNone times, PNone ns) {
            return null;
        }

        @Specialization(guards = {"isNoValue(ns)"})
        static long[] times(VirtualFrame frame, PTuple times, @SuppressWarnings("unused") PNone ns,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached LenNode lenNode,
                        @Shared @Cached("createNotNormalized()") GetItemNode getItemNode,
                        @Cached ObjectToTimespecNode objectToTimespecNode,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            return convertToTimespec(frame, inliningTarget, times, lenNode, getItemNode, objectToTimespecNode, raiseNode);
        }

        @Specialization
        static long[] ns(VirtualFrame frame, @SuppressWarnings("unused") PNone times, PTuple ns,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached LenNode lenNode,
                        @Shared @Cached("createNotNormalized()") GetItemNode getItemNode,
                        @Cached SplitLongToSAndNsNode splitLongToSAndNsNode,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            return convertToTimespec(frame, inliningTarget, ns, lenNode, getItemNode, splitLongToSAndNsNode, raiseNode);
        }

        @Specialization(guards = {"!isPNone(times)", "!isNoValue(ns)"})
        @SuppressWarnings("unused")
        static long[] bothSpecified(VirtualFrame frame, Object times, Object ns,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.YOU_MAY_SPECIFY_EITHER_OR_BUT_NOT_BOTH, "utime", "times", "ns");
        }

        @Specialization(guards = {"!isPNone(times)", "!isPTuple(times)", "isNoValue(ns)"})
        @SuppressWarnings("unused")
        static long[] timesNotATuple(VirtualFrame frame, Object times, PNone ns,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            throw timesTupleError(inliningTarget, raiseNode);
        }

        @Specialization(guards = {"!isNoValue(ns)", "!isPTuple(ns)"})
        @SuppressWarnings("unused")
        static long[] nsNotATuple(VirtualFrame frame, PNone times, Object ns,
                        @Bind Node inliningTarget) {
            // ns can actually also contain objects implementing __divmod__, but CPython produces
            // this error message
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.MUST_BE, "utime", "ns", "a tuple of two ints");
        }

        private static PException timesTupleError(Node inliningTarget, PRaiseNode raiseNode) {
            // times can actually also contain floats, but CPython produces this error message
            throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.MUST_BE_EITHER_OR, "utime", "times", "a tuple of two ints", "None");
        }

        private static long[] convertToTimespec(VirtualFrame frame, Node inliningTarget, PTuple times, LenNode lenNode, GetItemNode getItemNode, ConvertToTimespecBaseNode convertToTimespecBaseNode,
                        PRaiseNode raiseNode) {
            if (lenNode.execute(inliningTarget, times) != 2) {
                throw timesTupleError(inliningTarget, raiseNode);
            }
            long[] timespec = new long[4];
            convertToTimespecBaseNode.execute(frame, inliningTarget, getItemNode.execute(times.getSequenceStorage(), 0), timespec, 0);
            convertToTimespecBaseNode.execute(frame, inliningTarget, getItemNode.execute(times.getSequenceStorage(), 1), timespec, 2);
            return timespec;
        }
    }

    @Builtin(name = "utime", minNumOfPositionalArgs = 1, parameterNames = {"path", "times"}, keywordOnlyNames = {"ns", "dir_fd", "follow_symlinks"})
    @ArgumentClinic(name = "path", conversionClass = PathConversionNode.class, args = {"false", "true"})
    @ArgumentClinic(name = "dir_fd", conversionClass = DirFdConversionNode.class)
    @ArgumentClinic(name = "follow_symlinks", conversion = ClinicConversion.Boolean, defaultValue = "true")
    @GenerateNodeFactory
    @ImportStatic(PosixConstants.class)
    abstract static class UtimeNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.UtimeNodeClinicProviderGen.INSTANCE;
        }

        private static Object checkNone(Object o) {
            return PGuards.isPNone(o) ? PNone.NONE : o;
        }

        @Specialization(guards = "HAVE_UTIMENSAT.value")
        static PNone utimensat(VirtualFrame frame, PosixPath path, Object times, Object ns, int dirFd, boolean followSymlinks,
                        @Bind Node inliningTarget,
                        @Shared @Cached UtimeArgsToTimespecNode timespecNode,
                        @Shared @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Shared @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            long[] timespec = timespecNode.execute(frame, times, ns);
            auditNode.audit(inliningTarget, "os.utime", path.originalObject, checkNone(times), checkNone(ns), dirFdForAudit(dirFd));
            try {
                posixLib.utimensat(context.getPosixSupport(), dirFd, path.value, timespec, followSymlinks);
            } catch (PosixException e) {
                // filename is intentionally not included, see CPython's os_utime_impl
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
            return PNone.NONE;
        }

        @Specialization(guards = {"!HAVE_UTIMENSAT.value", "isDefault(dirFd)", "followSymlinks"})
        static PNone utimes(VirtualFrame frame, PosixPath path, Object times, Object ns, int dirFd, @SuppressWarnings("unused") boolean followSymlinks,
                        @Bind Node inliningTarget,
                        @Shared @Cached UtimeArgsToTimespecNode timespecNode,
                        @Shared @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Shared @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            Timeval[] timeval = timespecNode.toTimeval(frame, times, ns);
            auditNode.audit(inliningTarget, "os.utime", path.originalObject, checkNone(times), checkNone(ns), dirFdForAudit(dirFd));
            try {
                posixLib.utimes(context.getPosixSupport(), path.value, timeval);
            } catch (PosixException e) {
                // filename is intentionally not included, see CPython's os_utime_impl
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
            return PNone.NONE;
        }

        @Specialization(guards = {"!HAVE_UTIMENSAT.value", "isDefault(dirFd)", "!followSymlinks"})
        static PNone lutimes(VirtualFrame frame, PosixPath path, Object times, Object ns, int dirFd, @SuppressWarnings("unused") boolean followSymlinks,
                        @Bind Node inliningTarget,
                        @Shared @Cached UtimeArgsToTimespecNode timespecNode,
                        @Shared @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Shared @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            Timeval[] timeval = timespecNode.toTimeval(frame, times, ns);
            auditNode.audit(inliningTarget, "os.utime", path.originalObject, checkNone(times), checkNone(ns), dirFdForAudit(dirFd));
            try {
                posixLib.lutimes(context.getPosixSupport(), path.value, timeval);
            } catch (PosixException e) {
                // filename is intentionally not included, see CPython's os_utime_impl
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
            return PNone.NONE;
        }

        @Specialization(guards = {"!HAVE_UTIMENSAT.value", "!isDefault(dirFd)", "followSymlinks"})
        @SuppressWarnings("unused")
        static PNone dirFdNotSupported(VirtualFrame frame, PosixPath path, Object times, Object ns, int dirFd, boolean followSymlinks,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, NotImplementedError, ErrorMessages.UNAVAILABLE_ON_THIS_PLATFORM_NO_FUNC, "dir_fd");
        }

        @Specialization(guards = {"!HAVE_UTIMENSAT.value", "!isDefault(dirFd)", "!followSymlinks"})
        @SuppressWarnings("unused")
        static PNone dirFdAndFollowSymlinksNotSupported(VirtualFrame frame, PosixPath path, Object times, Object ns, int dirFd, boolean followSymlinks,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.UTIME_CANNOT_USE_DIR_FD_AND_FOLLOW_SYMLINKS, "dir_fd");
        }

        @Specialization(guards = {"HAVE_FUTIMENS.value", "isDefault(dirFd)", "followSymlinks"})
        static PNone futimens(VirtualFrame frame, PosixFd fd, Object times, Object ns, int dirFd, @SuppressWarnings("unused") boolean followSymlinks,
                        @Bind Node inliningTarget,
                        @Shared @Cached UtimeArgsToTimespecNode timespecNode,
                        @Shared @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Shared @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            long[] timespec = timespecNode.execute(frame, times, ns);
            auditNode.audit(inliningTarget, "os.utime", fd.originalObject, checkNone(times), checkNone(ns), dirFdForAudit(dirFd));
            try {
                posixLib.futimens(context.getPosixSupport(), fd.fd, timespec);
            } catch (PosixException e) {
                // filename is intentionally not included, see CPython's os_utime_impl
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
            return PNone.NONE;
        }

        @Specialization(guards = {"!HAVE_FUTIMENS.value", "isDefault(dirFd)", "followSymlinks"})
        static PNone futimes(VirtualFrame frame, PosixFd fd, Object times, Object ns, int dirFd, @SuppressWarnings("unused") boolean followSymlinks,
                        @Bind Node inliningTarget,
                        @Shared @Cached UtimeArgsToTimespecNode timespecNode,
                        @Shared @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Shared @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            Timeval[] timeval = timespecNode.toTimeval(frame, times, ns);
            auditNode.audit(inliningTarget, "os.utime", fd.originalObject, checkNone(times), checkNone(ns), dirFdForAudit(dirFd));
            try {
                posixLib.futimes(context.getPosixSupport(), fd.fd, timeval);
            } catch (PosixException e) {
                // filename is intentionally not included, see CPython's os_utime_impl
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
            return PNone.NONE;
        }

        @Specialization(guards = {"isPNone(times) || isNoValue(ns)", "!isDefault(dirFd)"})
        @SuppressWarnings("unused")
        static PNone fdWithDirFd(VirtualFrame frame, PosixFd fd, Object times, Object ns, int dirFd, boolean followSymlinks,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.CANT_SPECIFY_DIRFD_WITHOUT_PATH, "utime");
        }

        @Specialization(guards = {"isPNone(times) || isNoValue(ns)", "isDefault(dirFd)", "!followSymlinks"})
        @SuppressWarnings("unused")
        static PNone fdWithFollowSymlinks(VirtualFrame frame, PosixFd fd, Object times, Object ns, int dirFd, boolean followSymlinks,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.CANNOT_USE_FD_AND_FOLLOW_SYMLINKS_TOGETHER, "utime");
        }

        protected static boolean isDefault(int dirFd) {
            return dirFd == AT_FDCWD.value;
        }
    }

    @Builtin(name = "rename", minNumOfPositionalArgs = 2, parameterNames = {"src", "dst"}, keywordOnlyNames = {"src_dir_fd", "dst_dir_fd"})
    @ArgumentClinic(name = "src", conversionClass = PathConversionNode.class, args = {"false", "false"})
    @ArgumentClinic(name = "dst", conversionClass = PathConversionNode.class, args = {"false", "false"})
    @ArgumentClinic(name = "src_dir_fd", conversionClass = DirFdConversionNode.class)
    @ArgumentClinic(name = "dst_dir_fd", conversionClass = DirFdConversionNode.class)
    @GenerateNodeFactory
    abstract static class RenameNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.RenameNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static PNone rename(VirtualFrame frame, PosixPath src, PosixPath dst, int srcDirFd, int dstDirFd,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Bind Node inliningTarget,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            auditNode.audit(inliningTarget, "os.rename", src.originalObject, dst.originalObject, dirFdForAudit(srcDirFd), dirFdForAudit(dstDirFd));
            try {
                posixLib.renameat(context.getPosixSupport(), srcDirFd, src.value, dstDirFd, dst.value);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e, src.originalObject, dst.originalObject);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "replace", minNumOfPositionalArgs = 2, parameterNames = {"src", "dst"}, keywordOnlyNames = {"src_dir_fd", "dst_dir_fd"})
    @ArgumentClinic(name = "src", conversionClass = PathConversionNode.class, args = {"false", "false"})
    @ArgumentClinic(name = "dst", conversionClass = PathConversionNode.class, args = {"false", "false"})
    @ArgumentClinic(name = "src_dir_fd", conversionClass = DirFdConversionNode.class)
    @ArgumentClinic(name = "dst_dir_fd", conversionClass = DirFdConversionNode.class)
    @GenerateNodeFactory
    abstract static class ReplaceNode extends RenameNode {

        // although this built-in is the same as rename(), we need to provide our own
        // ArgumentClinicProvider because the error messages contain the name of the built-in

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.ReplaceNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "access", minNumOfPositionalArgs = 2, parameterNames = {"path", "mode"}, keywordOnlyNames = {"dir_fd", "effective_ids", "follow_symlinks"})
    @ArgumentClinic(name = "path", conversionClass = PathConversionNode.class, args = {"false", "false"})
    @ArgumentClinic(name = "mode", conversion = ClinicConversion.Int)
    @ArgumentClinic(name = "dir_fd", conversionClass = DirFdConversionNode.class)
    @ArgumentClinic(name = "effective_ids", defaultValue = "false", conversion = ClinicConversion.Boolean)
    @ArgumentClinic(name = "follow_symlinks", defaultValue = "true", conversion = ClinicConversion.Boolean)
    @GenerateNodeFactory
    abstract static class AccessNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.AccessNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static boolean access(PosixPath path, int mode, int dirFd, boolean effectiveIds, boolean followSymlinks,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib) {
            return posixLib.faccessat(context.getPosixSupport(), dirFd, path.value, mode, effectiveIds, followSymlinks);
        }
    }

    @Builtin(name = "fchmod", minNumOfPositionalArgs = 2, parameterNames = {"fd", "mode"})
    @ArgumentClinic(name = "fd", conversion = ClinicConversion.Int)
    @ArgumentClinic(name = "mode", conversion = ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class FChmodNode extends PythonBinaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.FChmodNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static PNone fchmod(VirtualFrame frame, int fd, int mode,
                        @Bind Node inliningTarget,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            auditNode.audit(inliningTarget, "os.chmod", fd, mode, -1);
            try {
                posixLib.fchmod(context.getPosixSupport(), fd, mode);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e, fd);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "chmod", minNumOfPositionalArgs = 2, parameterNames = {"path", "mode"}, keywordOnlyNames = {"dir_fd", "follow_symlinks"})
    @ArgumentClinic(name = "path", conversionClass = PathConversionNode.class, args = {"false", "true"})
    @ArgumentClinic(name = "mode", conversion = ClinicConversion.Int)
    @ArgumentClinic(name = "dir_fd", conversionClass = DirFdConversionNode.class)
    @ArgumentClinic(name = "follow_symlinks", defaultValue = "true", conversion = ClinicConversion.Boolean)
    @GenerateNodeFactory
    abstract static class ChmodNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.ChmodNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static PNone chmodFollow(VirtualFrame frame, PosixPath path, int mode, int dirFd, boolean followSymlinks,
                        @Bind Node inliningTarget,
                        @Shared @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Shared @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Shared @Cached PRaiseNode raiseNode) {
            auditNode.audit(inliningTarget, "os.chmod", path.originalObject, mode, dirFdForAudit(dirFd));
            try {
                posixLib.fchmodat(context.getPosixSupport(), dirFd, path.value, mode, followSymlinks);
            } catch (PosixException e) {
                // TODO CPython checks for ENOTSUP as well
                if (e.getErrorCode() == OSErrorEnum.EOPNOTSUPP.getNumber() && !followSymlinks) {
                    if (dirFd != AT_FDCWD.value) {
                        throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.CANNOT_USE_FD_AND_FOLLOW_SYMLINKS_TOGETHER, "chmod");
                    } else {
                        throw raiseNode.raise(inliningTarget, NotImplementedError, ErrorMessages.UNAVAILABLE_ON_THIS_PLATFORM, "chmod", "follow_symlinks");
                    }
                }
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e, path.originalObject);
            }
            return PNone.NONE;
        }

        @Specialization
        static PNone chmodFollow(VirtualFrame frame, PosixFd fd, int mode, int dirFd, @SuppressWarnings("unused") boolean followSymlinks,
                        @Bind Node inliningTarget,
                        @Shared @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Shared @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        // unused node to avoid mixing shared and non-shared inlined nodes
                        @SuppressWarnings("unused") @Shared @Cached PRaiseNode raiseNode) {
            auditNode.audit(inliningTarget, "os.chmod", fd.originalObject, mode, dirFdForAudit(dirFd));
            // Unlike stat and utime which raise CANT_SPECIFY_DIRFD_WITHOUT_PATH or
            // CANNOT_USE_FD_AND_FOLLOW_SYMLINKS_TOGETHER when an inappropriate combination of
            // arguments is used, CPython's implementation of chmod simply ignores dir_fd and
            // follow_symlinks if a fd is specified instead of a path.
            try {
                posixLib.fchmod(context.getPosixSupport(), fd.fd, mode);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e, fd.originalObject);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "fchown", parameterNames = {"fd", "uid", "gid"})
    @ArgumentClinic(name = "fd", conversion = ClinicConversion.Int)
    @ArgumentClinic(name = "uid", conversionClass = UidConversionNode.class)
    @ArgumentClinic(name = "gid", conversionClass = GidConversionNode.class)
    @GenerateNodeFactory
    abstract static class FChownNode extends PythonTernaryClinicBuiltinNode {
        @Specialization
        static Object chown(VirtualFrame frame, int fd, long uid, long gid,
                        @Bind Node inliningTarget,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached GilNode gil,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            auditNode.audit(inliningTarget, "os.chown", fd, uid, gid, -1);
            try {
                gil.release(true);
                try {
                    posixLib.fchown(context.getPosixSupport(), fd, uid, gid);
                } finally {
                    gil.acquire();
                }
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e, fd);
            }
            return PNone.NONE;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.FChownNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "lchown", parameterNames = {"path", "uid", "gid"})
    @ArgumentClinic(name = "path", conversionClass = PathConversionNode.class, args = {"false", "false"})
    @ArgumentClinic(name = "uid", conversionClass = UidConversionNode.class)
    @ArgumentClinic(name = "gid", conversionClass = GidConversionNode.class)
    @GenerateNodeFactory
    abstract static class LChownNode extends PythonTernaryClinicBuiltinNode {
        @Specialization
        static Object chown(VirtualFrame frame, PosixPath path, long uid, long gid,
                        @Bind Node inliningTarget,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached GilNode gil,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            auditNode.audit(inliningTarget, "os.chown", path.originalObject, uid, gid, -1);
            try {
                gil.release(true);
                try {
                    posixLib.fchownat(context.getPosixSupport(), AT_FDCWD.value, path.value, uid, gid, false);
                } finally {
                    gil.acquire();
                }
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e, path.originalObject);
            }
            return PNone.NONE;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.LChownNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "chown", minNumOfPositionalArgs = 3, parameterNames = {"path", "uid", "gid"}, keywordOnlyNames = {"dir_fd", "follow_symlinks"})
    @ArgumentClinic(name = "path", conversionClass = PathConversionNode.class, args = {"false", "true"})
    @ArgumentClinic(name = "uid", conversionClass = UidConversionNode.class)
    @ArgumentClinic(name = "gid", conversionClass = GidConversionNode.class)
    @ArgumentClinic(name = "dir_fd", conversionClass = DirFdConversionNode.class)
    @ArgumentClinic(name = "follow_symlinks", conversion = ClinicConversion.Boolean, defaultValue = "true")
    @GenerateNodeFactory
    abstract static class ChownNode extends PythonClinicBuiltinNode {
        @Specialization
        static Object chown(VirtualFrame frame, PosixPath path, long uid, long gid, int dirFd, boolean followSymlinks,
                        @Bind Node inliningTarget,
                        @Shared @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Shared @Cached GilNode gil,
                        @Shared @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        // unused node to avoid mixing shared and non-shared inlined nodes
                        @Shared @Cached PRaiseNode raiseNode) {
            auditNode.audit(inliningTarget, "os.chown", path.originalObject, uid, gid, dirFd != AT_FDCWD.value ? dirFd : -1);
            try {
                gil.release(true);
                try {
                    posixLib.fchownat(context.getPosixSupport(), dirFd, path.value, uid, gid, followSymlinks);
                } finally {
                    gil.acquire();
                }
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e, path.originalObject);
            }
            return PNone.NONE;
        }

        @Specialization
        static Object chown(VirtualFrame frame, PosixFd fd, long uid, long gid, int dirFd, boolean followSymlinks,
                        @Bind Node inliningTarget,
                        @Shared @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Shared @Cached GilNode gil,
                        @Shared @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Shared @Cached PRaiseNode raiseNode) {
            if (dirFd != AT_FDCWD.value) {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.CANT_SPECIFY_BOTH_DIR_FD_AND_FD);
            }
            if (followSymlinks) {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.CANNOT_USE_FD_AND_FOLLOW_SYMLINKS_TOGETHER, "chown");
            }
            auditNode.audit(inliningTarget, "os.chown", fd.originalObject, uid, gid, -1);
            try {
                gil.release(true);
                try {
                    posixLib.fchown(context.getPosixSupport(), fd.fd, uid, gid);
                } finally {
                    gil.acquire();
                }
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e, fd.originalObject);
            }
            return PNone.NONE;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.ChownNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "readlink", minNumOfPositionalArgs = 1, parameterNames = {"path"}, keywordOnlyNames = {"dir_fd"}, doc = "readlink(path, *, dir_fd=None) -> path\n" +
                    "\nReturn a string representing the path to which the symbolic link points.\n")
    @ArgumentClinic(name = "path", conversionClass = PathConversionNode.class, args = {"false", "false"})
    @ArgumentClinic(name = "dir_fd", conversionClass = DirFdConversionNode.class)
    @GenerateNodeFactory
    abstract static class ReadlinkNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.ReadlinkNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object readlinkAsBytes(VirtualFrame frame, PosixPath path, int dirFd,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached InlinedConditionProfile wasBufferLikeProfile,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                Object link = posixLib.readlinkat(context.getPosixSupport(), dirFd, path.value);
                if (wasBufferLikeProfile.profile(inliningTarget, path.wasBufferLike)) {
                    return opaquePathToBytes(link, posixLib, context.getPosixSupport(), context.getLanguage(inliningTarget));
                } else {
                    return posixLib.getPathAsString(context.getPosixSupport(), link);
                }
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e, path.originalObject);
            }
        }
    }

    @Builtin(name = "strerror", minNumOfPositionalArgs = 1, parameterNames = {"code"})
    @ArgumentClinic(name = "code", conversion = ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class StrErrorNode extends PythonUnaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.StrErrorNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static TruffleString getStrError(int code,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib) {
            return posixLib.strerror(context.getPosixSupport(), code);
        }
    }

    @Builtin(name = "_exit", minNumOfPositionalArgs = 1, parameterNames = {"status"})
    @ArgumentClinic(name = "status", conversion = ClinicConversion.Int)
    @GenerateNodeFactory
    public abstract static class ExitNode extends PythonUnaryClinicBuiltinNode {
        @TruffleBoundary
        @Specialization
        public static Object exit(int status,
                        @Bind Node node) {
            PythonContext context = PythonContext.get(node);
            if (context.getOption(PythonOptions.RunViaLauncher)) {
                Runtime.getRuntime().halt(status);
            }
            List<Thread> otherThreads = new ArrayList<>(Arrays.asList(context.getThreads()));
            otherThreads.remove(context.getMainThread());
            otherThreads.remove(Thread.currentThread());
            context.getEnv().submitThreadLocal(otherThreads.toArray(new Thread[0]), new ThreadLocalAction(true, false) {
                @Override
                protected void perform(Access access) {
                    throw new ThreadDeath();
                }
            });
            if (Thread.currentThread() == context.getMainThread()) {
                throw new PythonExitException(node, status);
            } else {
                context.getEnv().submitThreadLocal(new Thread[]{context.getMainThread()}, new ThreadLocalAction(true, false) {
                    @Override
                    protected void perform(Access access) {
                        throw new PythonExitException(node, status);
                    }
                });
            }
            throw new ThreadDeath();
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.ExitNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "waitpid", minNumOfPositionalArgs = 2, parameterNames = {"pid", "options"})
    @ArgumentClinic(name = "pid", conversionClass = PidtConversionNode.class)
    @ArgumentClinic(name = "options", conversion = ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class WaitpidNode extends PythonBinaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.WaitpidNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static PTuple waitpid(VirtualFrame frame, long pid, int options,
                        @Bind Node inliningTarget,
                        @Cached GilNode gil,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached InlinedBranchProfile errorProfile,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            gil.release(true);
            try {
                while (true) {
                    try {
                        long[] result = posixLib.waitpid(context.getPosixSupport(), pid, options);
                        return PFactory.createTuple(context.getLanguage(inliningTarget), new Object[]{result[0], result[1]});
                    } catch (PosixException e) {
                        errorProfile.enter(inliningTarget);
                        if (e.getErrorCode() == OSErrorEnum.EINTR.getNumber()) {
                            PythonContext.triggerAsyncActions(inliningTarget);
                        } else {
                            gil.acquire();
                            throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
                        }
                    } catch (UnsupportedPosixFeatureException e) {
                        throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorUnsupported(frame, e);
                    }
                }
            } finally {
                gil.acquire();
            }
        }
    }

    @Builtin(name = "waitstatus_to_exitcode", minNumOfPositionalArgs = 1, parameterNames = {"status"})
    @GenerateNodeFactory
    abstract static class WaitstatusToExitcodeNode extends PythonUnaryBuiltinNode {
        @Specialization
        static int waitstatusToExitcode(VirtualFrame frame, Object statusObj,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Bind Node inliningTarget,
                        @Cached PyLongAsIntNode longAsInt,
                        @Cached PRaiseNode raiseNode) {
            int status = longAsInt.execute(frame, inliningTarget, statusObj);
            PosixSupport posixSupport = context.getPosixSupport();
            if (posixLib.wifexited(posixSupport, status)) {
                int exitcode = posixLib.wexitstatus(posixSupport, status);
                if (exitcode < 0) {
                    throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.ValueError, ErrorMessages.INVALID_WEXITSTATUS, exitcode);
                }
                return exitcode;
            }
            if (posixLib.wifsignaled(posixSupport, status)) {
                int signum = posixLib.wtermsig(posixSupport, status);
                if (signum <= 0) {
                    throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.ValueError, ErrorMessages.INVALID_WTERMSIG, signum);
                }
                return -signum;
            }
            if (posixLib.wifstopped(posixSupport, status)) {
                int signum = posixLib.wstopsig(posixSupport, status);
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.ValueError, ErrorMessages.PROCESS_STOPPED_BY_DELIVERY_OF_SIGNAL, signum);
            }
            throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.ValueError, ErrorMessages.INVALID_WAIT_STATUS, status);
        }
    }

    @Builtin(name = "WCOREDUMP", minNumOfPositionalArgs = 1, parameterNames = {"status"})
    @ArgumentClinic(name = "status", conversion = ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class WcoredumpNode extends PythonUnaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.WcoredumpNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static boolean wcoredump(int status,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib) {
            return posixLib.wcoredump(context.getPosixSupport(), status);
        }
    }

    @Builtin(name = "WIFCONTINUED", minNumOfPositionalArgs = 1, parameterNames = {"status"})
    @ArgumentClinic(name = "status", conversion = ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class WifcontinuedNode extends PythonUnaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.WifcontinuedNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static boolean wifcontinued(int status,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib) {
            return posixLib.wifcontinued(context.getPosixSupport(), status);
        }
    }

    @Builtin(name = "WIFSTOPPED", minNumOfPositionalArgs = 1, parameterNames = {"status"})
    @ArgumentClinic(name = "status", conversion = ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class WifstoppedNode extends PythonUnaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.WifstoppedNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static boolean wifstopped(int status,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib) {
            return posixLib.wifstopped(context.getPosixSupport(), status);
        }
    }

    @Builtin(name = "WIFSIGNALED", minNumOfPositionalArgs = 1, parameterNames = {"status"})
    @ArgumentClinic(name = "status", conversion = ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class WifsignaledNode extends PythonUnaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.WifsignaledNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static boolean wifsignaled(int status,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib) {
            return posixLib.wifsignaled(context.getPosixSupport(), status);
        }
    }

    @Builtin(name = "WIFEXITED", minNumOfPositionalArgs = 1, parameterNames = {"status"})
    @ArgumentClinic(name = "status", conversion = ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class WifexitedNode extends PythonUnaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.WifexitedNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static boolean wifexited(int status,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib) {
            return posixLib.wifexited(context.getPosixSupport(), status);
        }
    }

    @Builtin(name = "WEXITSTATUS", minNumOfPositionalArgs = 1, parameterNames = {"status"})
    @ArgumentClinic(name = "status", conversion = ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class WexitstatusNode extends PythonUnaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.WexitstatusNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static int wexitstatus(int status,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib) {
            return posixLib.wexitstatus(context.getPosixSupport(), status);
        }
    }

    @Builtin(name = "WTERMSIG", minNumOfPositionalArgs = 1, parameterNames = {"status"})
    @ArgumentClinic(name = "status", conversion = ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class WtermsigNode extends PythonUnaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.WtermsigNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        int wtermsig(int status,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib) {
            return posixLib.wtermsig(context.getPosixSupport(), status);
        }
    }

    @Builtin(name = "WSTOPSIG", minNumOfPositionalArgs = 1, parameterNames = {"status"})
    @ArgumentClinic(name = "status", conversion = ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class WstopsigNode extends PythonUnaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.WstopsigNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static int wstopsig(int status,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib) {
            return posixLib.wstopsig(context.getPosixSupport(), status);
        }
    }

    @Builtin(name = "system", minNumOfPositionalArgs = 1, parameterNames = {"command"})
    @ArgumentClinic(name = "command", conversionClass = FsConverterNode.class)
    @GenerateNodeFactory
    abstract static class SystemNode extends PythonUnaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.SystemNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static int system(PBytes command,
                        @Bind Node inliningTarget,
                        @Cached BytesNodes.ToBytesNode toBytesNode,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached GilNode gil) {
            // Unlike in other posix builtins, we go through str -> bytes -> byte[] -> String
            // conversions for emulated backend because the bytes version after fsencode conversion
            // is subject to sys.audit.
            auditNode.audit(inliningTarget, "os.system", command);
            byte[] bytes = toBytesNode.execute(command);
            gil.release(true);
            try {
                Object cmdOpaque = posixLib.createPathFromBytes(context.getPosixSupport(), bytes);
                return posixLib.system(context.getPosixSupport(), cmdOpaque);
            } finally {
                gil.acquire();
            }
        }
    }

    @Builtin(name = "urandom", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"size"})
    @ArgumentClinic(name = "size", conversion = ClinicConversion.Index)
    @GenerateNodeFactory
    abstract static class URandomNode extends PythonUnaryClinicBuiltinNode {
        @Specialization(guards = "size >= 0")
        static PBytes urandom(int size,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context) {
            byte[] bytes = new byte[size];
            nextBytes(context.getSecureRandom(), bytes);
            return PFactory.createBytes(context.getLanguage(inliningTarget), bytes);
        }

        @Specialization(guards = "size < 0")
        static Object urandomNeg(@SuppressWarnings("unused") int size,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.NEG_ARG_NOT_ALLOWED);
        }

        @TruffleBoundary
        private static void nextBytes(SecureRandom secureRandom, byte[] bytes) {
            secureRandom.nextBytes(bytes);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.URandomNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "cpu_count", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class CpuCountNode extends PythonBuiltinNode {
        @TruffleBoundary
        @Specialization
        static int getCpuCount() {
            return Runtime.getRuntime().availableProcessors();
        }
    }

    @Builtin(name = "sysconf", minNumOfPositionalArgs = 2, parameterNames = {"$self", "name"}, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class SysconfNode extends PythonBinaryBuiltinNode {

        private static final TruffleString T_SYSCONF_NAMES = tsLiteral("sysconf_names");

        @Specialization
        static long sysconf(VirtualFrame frame, PythonModule self, Object arg,
                        @Bind Node inliningTarget,
                        @Cached PyLongCheckNode longCheckNode,
                        @Cached PyLongAsIntNode asIntNode,
                        @Cached PyUnicodeCheckNode unicodeCheckNode,
                        @Cached PyObjectGetAttr getAttr,
                        @Cached PyObjectGetItem getItem,
                        @Cached PRaiseNode raiseNode,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            int id;
            if (longCheckNode.execute(inliningTarget, arg)) {
                id = asIntNode.execute(frame, inliningTarget, arg);
            } else if (unicodeCheckNode.execute(inliningTarget, arg)) {
                try {
                    Object sysconfigNamesObject = getAttr.execute(frame, inliningTarget, self, T_SYSCONF_NAMES);
                    Object idObj = getItem.execute(frame, inliningTarget, sysconfigNamesObject, arg);
                    id = asIntNode.execute(frame, inliningTarget, idObj);
                } catch (PException e) {
                    throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.UNRECOGNIZED_CONF_NAME);
                }
            } else {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.CONFIGURATION_NAMES_MUST_BE_STRINGS_OR_INTEGERS);
            }
            try {
                return posixLib.sysconf(context.getPosixSupport(), id);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    @Builtin(name = "umask", minNumOfPositionalArgs = 1, parameterNames = {"mask"})
    @ArgumentClinic(name = "mask", conversion = ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class UmaskNode extends PythonUnaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.UmaskNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static int umask(VirtualFrame frame, int mask,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                return posixLib.umask(context.getPosixSupport(), mask);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    @Builtin(name = "ctermid", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class CtermId extends PythonBuiltinNode {
        @Specialization
        static TruffleString ctermid(VirtualFrame frame,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                return posixLib.ctermid(context.getPosixSupport());
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    @Builtin(name = "kill", minNumOfPositionalArgs = 2, parameterNames = {"pid", "signal"})
    @ArgumentClinic(name = "pid", conversionClass = PidtConversionNode.class)
    @ArgumentClinic(name = "signal", conversion = ClinicConversion.Index)
    @GenerateNodeFactory
    abstract static class KillNode extends PythonBinaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.KillNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static PNone kill(VirtualFrame frame, long pid, int signal,
                        @Bind Node inliningTarget,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            auditNode.audit(inliningTarget, "kill", pid, signal);
            try {
                posixLib.kill(context.getPosixSupport(), pid, signal);
                return PNone.NONE;
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            } catch (UnsupportedPosixFeatureException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorUnsupported(frame, e);
            }
        }
    }

    @Builtin(name = "killpg", minNumOfPositionalArgs = 2, parameterNames = {"pgid", "signal"})
    @ArgumentClinic(name = "pgid", conversionClass = PidtConversionNode.class)
    @ArgumentClinic(name = "signal", conversion = ClinicConversion.Index)
    @GenerateNodeFactory
    abstract static class KillPgNode extends PythonBinaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.KillNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static PNone kill(VirtualFrame frame, long pid, int signal,
                        @Bind Node inliningTarget,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            auditNode.audit(inliningTarget, "killpg", pid, signal);
            try {
                posixLib.killpg(context.getPosixSupport(), pid, signal);
                return PNone.NONE;
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            } catch (UnsupportedPosixFeatureException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorUnsupported(frame, e);
            }
        }
    }

    @Builtin(name = "fspath", minNumOfPositionalArgs = 1, parameterNames = {"path"})
    @GenerateNodeFactory
    // Can be used as an equivalent of PyOS_FSPath()
    public abstract static class FspathNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doTrivial(VirtualFrame frame, Object value,
                        @Bind Node inliningTarget,
                        @Cached PyOSFSPathNode fsPathNode) {
            return fsPathNode.execute(frame, inliningTarget, value);
        }
    }

    @Builtin(name = "register_at_fork", keywordOnlyNames = {"before", "after_in_child", "after_in_parent"})
    @GenerateNodeFactory
    abstract static class RegisterAtForkNode extends PythonBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        static Object register(Object before, Object afterInChild, Object afterInParent) {
            // TODO should we at least call multiprocessing.util.register_after_fork?
            return PNone.NONE;
        }
    }

    // ------------------
    // Helpers

    /**
     * Helper node that accepts either str or bytes and converts it to {@code PBytes}.
     */
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic(PGuards.class)
    public abstract static class StringOrBytesToBytesNode extends Node {
        public abstract PBytes execute(Node inliningTarget, Object obj);

        @Specialization(guards = "isString(strObj)")
        static PBytes doString(Node inliningTarget, Object strObj,
                        @Bind PythonLanguage language,
                        @Cached CastToTruffleStringNode castToStringNode,
                        @Cached(inline = false) TruffleString.SwitchEncodingNode switchEncodingNode,
                        @Cached(inline = false) TruffleString.CopyToByteArrayNode copyToByteArrayNode) {
            TruffleString str = castToStringNode.execute(inliningTarget, strObj);
            TruffleString utf8 = switchEncodingNode.execute(str, Encoding.UTF_8);
            byte[] bytes = new byte[utf8.byteLength(Encoding.UTF_8)];
            copyToByteArrayNode.execute(utf8, 0, bytes, 0, bytes.length, Encoding.UTF_8);
            return PFactory.createBytes(language, bytes);
        }

        @Specialization
        static PBytes doBytes(PBytes bytes) {
            return bytes;
        }
    }

    /**
     * Helper node that accepts either str or bytes and converts it to a representation specific to
     * the {@link PosixSupportLibrary} in use. Basically equivalent of
     * {@code PyUnicode_EncodeFSDefault}.
     */
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic(PGuards.class)
    abstract static class StringOrBytesToOpaquePathNode extends Node {
        abstract Object execute(Node inliningTarget, Object obj);

        @Specialization(guards = "isString(strObj)")
        static Object doString(Node inliningTarget, Object strObj,
                        @Cached CastToTruffleStringNode castToStringNode,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            TruffleString str = castToStringNode.execute(inliningTarget, strObj);
            return checkPath(inliningTarget, posixLib.createPathFromString(context.getPosixSupport(), str), raiseNode);
        }

        @Specialization
        static Object doBytes(Node inliningTarget, PBytes bytes,
                        @Cached(inline = false) BytesNodes.ToBytesNode toBytesNode,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            return checkPath(inliningTarget, posixLib.createPathFromBytes(context.getPosixSupport(), toBytesNode.execute(bytes)), raiseNode);
        }

        private static Object checkPath(Node inliningTarget, Object path, PRaiseNode raiseNode) {
            if (path == null) {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.EMBEDDED_NULL_BYTE);
            }
            return path;
        }
    }

    /**
     * Similar to {@code PyUnicode_FSConverter}, but the actual conversion is delegated to the
     * {@link PosixSupportLibrary} implementation.
     */
    @GenerateInline
    @GenerateCached(false)
    abstract static class ObjectToOpaquePathNode extends Node {
        abstract Object execute(VirtualFrame frame, Node inliningTarget, Object obj, boolean checkEmpty);

        @Specialization(guards = "!checkEmpty")
        static Object noCheck(VirtualFrame frame, Node inliningTarget, Object obj, @SuppressWarnings("unused") boolean checkEmpty,
                        @Exclusive @Cached PyOSFSPathNode fspathNode,
                        @Exclusive @Cached StringOrBytesToOpaquePathNode stringOrBytesToOpaquePathNode) {
            return stringOrBytesToOpaquePathNode.execute(inliningTarget, fspathNode.execute(frame, inliningTarget, obj));
        }

        @Specialization(guards = "checkEmpty")
        static Object withCheck(VirtualFrame frame, Node inliningTarget, Object obj, @SuppressWarnings("unused") boolean checkEmpty,
                        @Exclusive @Cached PyOSFSPathNode fspathNode,
                        @Cached PyObjectSizeNode sizeNode,
                        @Exclusive @Cached StringOrBytesToOpaquePathNode stringOrBytesToOpaquePathNode,
                        @Cached PRaiseNode raiseNode) {
            Object stringOrBytes = fspathNode.execute(frame, inliningTarget, obj);
            if (sizeNode.execute(frame, inliningTarget, obj) == 0) {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.EXECV_ARG2_FIRST_ELEMENT_CANNOT_BE_EMPTY);
            }
            return stringOrBytesToOpaquePathNode.execute(inliningTarget, stringOrBytes);
        }
    }

    abstract static class ConvertToTimespecBaseNode extends Node {
        abstract void execute(VirtualFrame frame, Node inliningTarget, Object obj, long[] timespec, int offset);
    }

    /**
     * Equivalent of {@code _PyTime_ObjectToTimespec} as used in {@code os_utime_impl}.
     */
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic(PGuards.class)
    abstract static class ObjectToTimespecNode extends ConvertToTimespecBaseNode {

        @Specialization
        static void doDouble(Node inliningTarget, double value, long[] timespec, int offset,
                        @Shared @Cached PRaiseNode raiseNode) {
            if (Double.isNaN(value)) {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.INVALID_VALUE_NAN);
            }

            double denominator = 1000000000.0;
            double floatPart = value % 1;
            double intPart = value - floatPart;

            floatPart = Math.floor(floatPart * denominator);
            if (floatPart >= denominator) {
                floatPart -= denominator;
                intPart += 1.0;
            } else if (floatPart < 0) {
                floatPart += denominator;
                intPart -= 1.0;
            }
            assert 0.0 <= floatPart && floatPart < denominator;
            if (!MathGuards.fitLong(intPart)) {
                throw raiseNode.raise(inliningTarget, OverflowError, ErrorMessages.TIMESTAMP_OUT_OF_RANGE);
            }
            timespec[offset] = (long) intPart;
            timespec[offset + 1] = (long) floatPart;
            assert 0 <= timespec[offset + 1] && timespec[offset + 1] < (long) denominator;
        }

        @Specialization
        static void doPFloat(Node inliningTarget, PFloat obj, long[] timespec, int offset,
                        @Shared @Cached PRaiseNode raiseNode) {
            doDouble(inliningTarget, obj.getValue(), timespec, offset, raiseNode);
        }

        @Specialization
        static void doInt(int value, long[] timespec, int offset) {
            timespec[offset] = value;
            timespec[offset + 1] = 0;
        }

        @Specialization
        static void doLong(long value, long[] timespec, int offset) {
            timespec[offset] = value;
            timespec[offset + 1] = 0;
        }

        @Specialization(guards = {"!isDouble(value)", "!isPFloat(value)", "!isInteger(value)"})
        static void doGeneric(VirtualFrame frame, Node inliningTarget, Object value, long[] timespec, int offset,
                        @Cached PyLongAsLongAndOverflowNode asLongNode,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            try {
                timespec[offset] = asLongNode.execute(frame, inliningTarget, value);
            } catch (OverflowException e) {
                throw raiseNode.raise(inliningTarget, OverflowError, ErrorMessages.TIMESTAMP_OUT_OF_RANGE);
            }
            timespec[offset + 1] = 0;
        }
    }

    /**
     * Equivalent of {@code split_py_long_to_s_and_ns} as used in {@code os_utime_impl}.
     */
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic(PGuards.class)
    abstract static class SplitLongToSAndNsNode extends ConvertToTimespecBaseNode {

        private static final long BILLION = 1000000000;

        @Specialization
        static void doInt(int value, long[] timespec, int offset) {
            doLong(value, timespec, offset);
        }

        @Specialization
        static void doLong(long value, long[] timespec, int offset) {
            timespec[offset] = Math.floorDiv(value, BILLION);
            timespec[offset + 1] = Math.floorMod(value, BILLION);
        }

        @Specialization(guards = {"!isInteger(value)"})
        static void doGeneric(VirtualFrame frame, Node inliningTarget, Object value, long[] timespec, int offset,
                        @Cached PyNumberDivmodNode divmodNode,
                        @Cached LenNode lenNode,
                        @Cached(value = "createNotNormalized()", inline = false) GetItemNode getItemNode,
                        @Cached PyLongAsLongNode asLongNode,
                        @Cached PRaiseNode raiseNode) {
            Object divmod = divmodNode.execute(frame, inliningTarget, value, BILLION);
            if (!PGuards.isPTuple(divmod) || lenNode.execute(inliningTarget, (PSequence) divmod) != 2) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.MUST_RETURN_2TUPLE, value, divmod);
            }
            SequenceStorage storage = ((PTuple) divmod).getSequenceStorage();
            timespec[offset] = asLongNode.execute(frame, inliningTarget, getItemNode.execute(storage, 0));
            timespec[offset + 1] = asLongNode.execute(frame, inliningTarget, getItemNode.execute(storage, 1));
        }
    }

    static int dirFdForAudit(int dirFd) {
        return dirFd == AT_FDCWD.value ? -1 : dirFd;
    }

    public static PTuple createStatResult(Node inliningTarget, PythonLanguage language, InlinedConditionProfile positiveLongProfile, long[] out) {
        Object[] res = new Object[16];
        for (int i = 0; i < 7; i++) {
            res[i] = PInt.createPythonIntFromUnsignedLong(inliningTarget, language, positiveLongProfile, out[i]);
        }
        res[6] = out[6];
        for (int i = 7; i < 10; i++) {
            long seconds = out[i];
            long nsFraction = out[i + 3];
            res[i] = seconds;
            res[i + 3] = seconds + nsFraction * 1.0e-9;
            res[i + 6] = PFactory.createInt(language, convertToNanoseconds(seconds, nsFraction));
        }
        return PFactory.createStructSeq(language, STAT_RESULT_DESC, res);
    }

    @TruffleBoundary
    private static BigInteger convertToNanoseconds(long sec, long ns) {
        // TODO it may be possible to do this in long without overflow
        BigInteger r = BigInteger.valueOf(sec);
        r = r.multiply(BigInteger.valueOf(1000000000));
        return r.add(BigInteger.valueOf(ns));
    }

    public static PBytes opaquePathToBytes(Object opaquePath, PosixSupportLibrary posixLib, Object posixSupport, PythonLanguage language) {
        Buffer buf = posixLib.getPathAsBytes(posixSupport, opaquePath);
        if (buf.length > Integer.MAX_VALUE) {
            // sanity check that it is safe to cast result.length to int, to be removed once
            // we support large arrays
            throw CompilerDirectives.shouldNotReachHere("Posix path cannot fit into a Java array");
        }
        return PFactory.createBytes(language, buf.data, (int) buf.length);
    }

    // ------------------
    // Converters

    public abstract static class FsConverterNode extends ArgumentCastNode {
        @Specialization
        static PBytes convert(VirtualFrame frame, Object value,
                        @Bind Node inliningTarget,
                        @Cached PyOSFSPathNode fspathNode,
                        @Cached StringOrBytesToBytesNode stringOrBytesToBytesNode) {
            return stringOrBytesToBytesNode.execute(inliningTarget, fspathNode.execute(frame, inliningTarget, value));
        }

        @ClinicConverterFactory
        @NeverDefault
        public static FsConverterNode create() {
            return PosixModuleBuiltinsFactory.FsConverterNodeGen.create();
        }
    }

    /**
     * Equivalent of CPython's {@code dir_fd_converter()}. Always returns an {@code int}. If the
     * parameter is omitted, returns {@link PosixConstants#AT_FDCWD}.
     */
    public abstract static class DirFdConversionNode extends ArgumentCastNode {

        @Specialization
        static int doNone(@SuppressWarnings("unused") PNone value) {
            return AT_FDCWD.value;
        }

        @Specialization
        static int doFdBool(boolean value) {
            return PInt.intValue(value);
        }

        @Specialization
        static int doFdInt(int value) {
            return value;
        }

        @Specialization
        static int doFdLong(long value,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            return longToFd(inliningTarget, value, raiseNode);
        }

        @Specialization
        @SuppressWarnings("truffle-static-method")
        static int doFdPInt(PInt value,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached CastToJavaLongLossyNode castToLongNode,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            return doFdLong(castToLongNode.execute(inliningTarget, value), inliningTarget, raiseNode);
        }

        @Specialization(guards = {"!isPNone(value)", "!canBeInteger(value)"})
        @SuppressWarnings("truffle-static-method")
        static int doIndex(VirtualFrame frame, Object value,
                        @Bind Node inliningTarget,
                        @Cached PyIndexCheckNode indexCheckNode,
                        @Cached PyNumberIndexNode indexNode,
                        @Exclusive @Cached CastToJavaLongLossyNode castToLongNode,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            if (indexCheckNode.execute(inliningTarget, value)) {
                Object o = indexNode.execute(frame, inliningTarget, value);
                return doFdLong(castToLongNode.execute(inliningTarget, o), inliningTarget, raiseNode);
            } else {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.ARG_SHOULD_BE_INT_OR_NONE_T, value);
            }
        }

        private static int longToFd(Node inliningTarget, long value, PRaiseNode raiseNode) {
            if (value > Integer.MAX_VALUE) {
                throw raiseNode.raise(inliningTarget, OverflowError, ErrorMessages.FD_IS_GREATER_THAN_MAXIMUM);
            }
            if (value < Integer.MIN_VALUE) {
                throw raiseNode.raise(inliningTarget, OverflowError, ErrorMessages.FD_IS_LESS_THAN_MINIMUM);
            }
            return (int) value;
        }

        @ClinicConverterFactory(shortCircuitPrimitive = PrimitiveType.Int)
        @NeverDefault
        public static DirFdConversionNode create() {
            return PosixModuleBuiltinsFactory.DirFdConversionNodeGen.create();
        }
    }

    /**
     * Equivalent of CPython's {@code path_converter()}. Always returns an instance of
     * {@link PosixFileHandle}.
     */
    public abstract static class PathConversionNode extends ArgumentCastNode {

        private final String functionNameWithColon;
        private final String argumentName;
        protected final boolean nullable;
        protected final boolean allowFd;

        public PathConversionNode(String functionName, String argumentName, boolean nullable, boolean allowFd) {
            this.functionNameWithColon = functionName != null ? functionName + ": " : "";
            this.argumentName = argumentName != null ? argumentName : "path";
            this.nullable = nullable;
            this.allowFd = allowFd;
        }

        @Specialization(guards = "nullable")
        PosixFileHandle doNone(@SuppressWarnings("unused") PNone value,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            Object path = posixLib.createPathFromString(context.getPosixSupport(), T_DOT);
            return new PosixPath(null, checkPath(inliningTarget, path, raiseNode), false);
        }

        @Specialization(guards = "allowFd")
        static PosixFileHandle doFdBool(boolean value) {
            return new PosixFd(value, PInt.intValue(value));
        }

        @Specialization(guards = "allowFd")
        static PosixFileHandle doFdInt(int value) {
            return new PosixFd(value, value);
        }

        @Specialization(guards = "allowFd")
        static PosixFileHandle doFdLong(long value,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            return new PosixFd(value, DirFdConversionNode.longToFd(inliningTarget, value, raiseNode));
        }

        @Specialization(guards = "allowFd")
        static PosixFileHandle doFdPInt(PInt value,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached CastToJavaLongLossyNode castToLongNode,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            return new PosixFd(value, DirFdConversionNode.longToFd(inliningTarget, castToLongNode.execute(inliningTarget, value), raiseNode));
        }

        @Specialization(guards = "isString(value)")
        @SuppressWarnings("truffle-static-method")
        PosixFileHandle doUnicode(Object value,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached CastToTruffleStringNode castToStringNode,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            TruffleString str = castToStringNode.execute(inliningTarget, value);
            Object path = posixLib.createPathFromString(context.getPosixSupport(), str);
            return new PosixPath(value, checkPath(inliningTarget, path, raiseNode), false);
        }

        @Specialization
        @SuppressWarnings("truffle-static-method")
        PosixFileHandle doBytes(PBytes value,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached BytesNodes.ToBytesNode toByteArrayNode,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            Object path = posixLib.createPathFromBytes(context.getPosixSupport(), toByteArrayNode.execute(value));
            return new PosixPath(value, checkPath(inliningTarget, path, raiseNode), true);
        }

        @Specialization(guards = {"!isHandled(value)", "allowFd", "indexCheckNode.execute(this, value)"}, limit = "1")
        @SuppressWarnings("truffle-static-method")
        PosixFileHandle doIndex(VirtualFrame frame, Object value,
                        @Bind Node inliningTarget,
                        @SuppressWarnings("unused") @Exclusive @Cached PyIndexCheckNode indexCheckNode,
                        @Cached PyNumberIndexNode indexNode,
                        @Exclusive @Cached CastToJavaLongLossyNode castToLongNode,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            Object o = indexNode.execute(frame, inliningTarget, value);
            return new PosixFd(value, DirFdConversionNode.longToFd(inliningTarget, castToLongNode.execute(inliningTarget, o), raiseNode));
        }

        @Specialization(guards = {"!isHandled(value)", "!allowFd || !indexCheckNode.execute(this, value)"}, limit = "1")
        @SuppressWarnings("truffle-static-method")
        PosixFileHandle doGeneric(VirtualFrame frame, Object value,
                        @Bind Node inliningTarget,
                        @SuppressWarnings("unused") @Exclusive @Cached PyIndexCheckNode indexCheckNode,
                        @Cached("create(T___FSPATH__)") LookupAndCallUnaryNode callFSPath,
                        @Exclusive @Cached BytesNodes.ToBytesNode toByteArrayNode,
                        @Exclusive @Cached CastToTruffleStringNode castToStringNode,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            Object pathObject = callFSPath.executeObject(frame, value);
            if (pathObject == PNone.NO_VALUE) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.S_S_SHOULD_BE_S_NOT_P, functionNameWithColon, argumentName,
                                getAllowedTypes(), value);
            }
            // 'pathObject' replaces 'value' as the PosixPath.originalObject for auditing purposes
            // by design
            if (pathObject instanceof PBytes) {
                return doBytes((PBytes) pathObject, inliningTarget, toByteArrayNode, context, posixLib, raiseNode);
            }
            if (PGuards.isString(pathObject)) {
                return doUnicode(pathObject, inliningTarget, castToStringNode, context, posixLib, raiseNode);
            }
            throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.EXPECTED_FSPATH_TO_RETURN_STR_OR_BYTES, value, pathObject);
        }

        protected boolean isHandled(Object value) {
            return PGuards.isPNone(value) && nullable || PGuards.canBeInteger(value) && allowFd || PGuards.isString(value) || PGuards.isPBytes(value);
        }

        private String getAllowedTypes() {
            return allowFd && nullable ? "string, bytes, os.PathLike, integer or None"
                            : allowFd ? "string, bytes, os.PathLike or integer" : nullable ? "string, bytes, os.PathLike or None" : "string, bytes or os.PathLike";
        }

        private Object checkPath(Node inliningTarget, Object path, PRaiseNode raiseNode) {
            if (path == null) {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.S_EMBEDDED_NULL_CHARACTER_IN_S, functionNameWithColon, argumentName);
            }
            return path;
        }

        @ClinicConverterFactory
        @NeverDefault
        public static PathConversionNode create(@BuiltinName String functionName, @ArgumentName String argumentName, boolean nullable, boolean allowFd) {
            return PosixModuleBuiltinsFactory.PathConversionNodeGen.create(functionName, argumentName, nullable, allowFd);
        }
    }

    /**
     * Equivalent of CPython's {@code Py_off_t_converter()}. Always returns a {@code long}.
     */
    public abstract static class OffsetConversionNode extends ArgumentCastNode {

        @Specialization
        static long doInt(int i) {
            return i;
        }

        @Specialization
        static long doLong(long l) {
            return l;
        }

        @Specialization
        static long doOthers(VirtualFrame frame, Object value,
                        @Bind Node inliningTarget,
                        @Cached PyLongAsLongNode asLongNode) {
            return asLongNode.execute(frame, inliningTarget, value);
        }

        @ClinicConverterFactory(shortCircuitPrimitive = PrimitiveType.Long)
        @NeverDefault
        public static OffsetConversionNode create() {
            return PosixModuleBuiltinsFactory.OffsetConversionNodeGen.create();
        }
    }

    /**
     * Equivalent of CPython's {@code fildes_converter()}, which in turn delegates to
     * {@code PyObject_AsFileDescriptor}. Always returns an {@code int}.
     */
    @GenerateInline(false) // cast nodes cannot be inlined yet
    public abstract static class FileDescriptorConversionNode extends ArgumentCastNode {
        @Specialization
        static int doIndex(VirtualFrame frame, Object value,
                        @Bind Node inliningTarget,
                        @Cached PyObjectAsFileDescriptor asFileDescriptor) {
            return asFileDescriptor.execute(frame, inliningTarget, value);
        }

        @ClinicConverterFactory(shortCircuitPrimitive = PrimitiveType.Int)
        @NeverDefault
        public static FileDescriptorConversionNode create() {
            return PosixModuleBuiltinsFactory.FileDescriptorConversionNodeGen.create();
        }
    }

    /**
     * Emulates CPython's {@code pid_t_converter()}. Always returns an {@code long}.
     */
    public abstract static class PidtConversionNode extends ArgumentCastNode {

        @Specialization
        static long doInt(int value) {
            return value;
        }

        @Specialization
        static long doLong(long value) {
            return value;
        }

        @Specialization(guards = "!isInteger(value)")
        static long doGeneric(VirtualFrame frame, Object value,
                        @Bind Node inliningTarget,
                        @Cached PyLongAsLongNode asLongNode) {
            return asLongNode.execute(frame, inliningTarget, value);
        }

        @ClinicConverterFactory(shortCircuitPrimitive = {PrimitiveType.Int, PrimitiveType.Long})
        @NeverDefault
        public static PidtConversionNode create() {
            // TODO on platforms with sizeof(pid_t) == 4 (includes linux), the converter should
            // check for overflow
            return PosixModuleBuiltinsFactory.PidtConversionNodeGen.create();
        }
    }

    @GenerateCached(false)
    public abstract static class AbstractIdConversionNode extends ArgumentCastNode {

        private static final long MAX_UINT32 = (1L << 32) - 1;

        public abstract long executeLong(VirtualFrame frame, Object value);

        @Specialization
        long doInt(int value,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode raiseNode) {
            return checkValue(inliningTarget, value, raiseNode);
        }

        @Specialization
        long doLong(long value,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode raiseNode) {
            return checkValue(inliningTarget, value, raiseNode);
        }

        @Specialization(guards = "!isInteger(value)")
        @SuppressWarnings("truffle-static-method")
        long doGeneric(VirtualFrame frame, Object value,
                        @Bind Node inliningTarget,
                        @Cached PyNumberIndexNode pyNumberIndexNode,
                        @Cached PyLongAsLongNode asLongNode,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            Object index;
            try {
                index = pyNumberIndexNode.execute(frame, inliningTarget, value);
            } catch (PException ex) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.S_SHOULD_BE_INTEGER_NOT_P, getIdName(), value);
            }
            /*
             * We have no means to distinguish overflow/underflow, so we just let any OverflowError
             * from asLongNode fall through. It will not have the same message as CPython, but still
             * correct type.
             */
            return checkValue(inliningTarget, asLongNode.execute(frame, inliningTarget, index), raiseNode);
        }

        private long checkValue(Node inliningTarget, long value, PRaiseNode raiseNode) {
            // Note that -1 is intentionally allowed
            if (value < -1) {
                throw raiseNode.raise(inliningTarget, OverflowError, ErrorMessages.S_IS_LESS_THAN_MINIMUM, getIdName());
            } else if (value > MAX_UINT32) {
                /* uid_t is uint32_t on Linux */
                throw raiseNode.raise(inliningTarget, OverflowError, ErrorMessages.S_IS_GREATER_THAN_MAXIUMUM, getIdName());
            }
            return value;
        }

        protected abstract String getIdName();
    }

    /**
     * Emulates CPython's {@code _Py_Uid_Converter()}. Always returns a {@code long}.
     */
    public abstract static class UidConversionNode extends AbstractIdConversionNode {

        @Override
        protected String getIdName() {
            return "uid";
        }

        @ClinicConverterFactory(shortCircuitPrimitive = {PrimitiveType.Int, PrimitiveType.Long})
        @NeverDefault
        public static UidConversionNode create() {
            return PosixModuleBuiltinsFactory.UidConversionNodeGen.create();
        }
    }

    /**
     * Emulates CPython's {@code _Py_Gid_Converter()}. Always returns a {@code long}.
     */
    public abstract static class GidConversionNode extends AbstractIdConversionNode {

        @Override
        protected String getIdName() {
            return "gid";
        }

        @ClinicConverterFactory(shortCircuitPrimitive = {PrimitiveType.Int, PrimitiveType.Long})
        @NeverDefault
        public static GidConversionNode create() {
            return PosixModuleBuiltinsFactory.GidConversionNodeGen.create();
        }
    }

    /**
     * Represents the result of {@code path_t} conversion. Similar to CPython's {@code path_t}
     * structure, but only contains the results of the conversion, not the conversion parameters.
     */
    public abstract static class PosixFileHandle {

        /**
         * Contains the original object (or the object returned by {@code __fspath__}) for auditing
         * purposes. This field is {code null} iff the path parameter was optional and the caller
         * did not provide it.
         */
        public final Object originalObject;

        protected PosixFileHandle(Object originalObject) {
            this.originalObject = originalObject;
        }
    }

    /**
     * Contains the path converted to the representation used by the {@code PosixSupportLibrary}
     * implementation
     *
     * @see PosixSupportLibrary#createPathFromString(Object, TruffleString)
     * @see PosixSupportLibrary#createPathFromBytes(Object, byte[])
     */
    public static class PosixPath extends PosixFileHandle {
        public final Object value;
        public final boolean wasBufferLike;

        public PosixPath(Object originalObject, Object value, boolean wasBufferLike) {
            super(originalObject);
            this.value = value;
            this.wasBufferLike = wasBufferLike;
        }
    }

    /**
     * Contains the file descriptor if it was allowed in the argument conversion node and the caller
     * provided an integer instead of a path.
     */
    public static class PosixFd extends PosixFileHandle {
        public final int fd;

        public PosixFd(Object originalObject, int fd) {
            super(originalObject);
            this.fd = fd;
        }
    }
}
