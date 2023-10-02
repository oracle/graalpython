/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.nodes.BuiltinNames.T_NT;
import static com.oracle.graal.python.nodes.BuiltinNames.T_POSIX;
import static com.oracle.graal.python.nodes.StringLiterals.T_DOT;
import static com.oracle.graal.python.runtime.PosixConstants.AT_FDCWD;
import static com.oracle.graal.python.runtime.PosixConstants.AT_SYMLINK_FOLLOW;
import static com.oracle.graal.python.runtime.PosixConstants.O_CLOEXEC;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.NotImplementedError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

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
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
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
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.tuple.StructSequence;
import com.oracle.graal.python.lib.PyIndexCheckNode;
import com.oracle.graal.python.lib.PyLongAsIntNode;
import com.oracle.graal.python.lib.PyLongAsLongAndOverflowNode;
import com.oracle.graal.python.lib.PyLongAsLongNode;
import com.oracle.graal.python.lib.PyNumberIndexNode;
import com.oracle.graal.python.lib.PyOSFSPathNode;
import com.oracle.graal.python.lib.PyObjectAsFileDescriptor;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic;
import com.oracle.graal.python.nodes.expression.BinaryOpNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentCastNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentCastNode.ArgumentCastNodeWithRaise;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentCastNode.ArgumentCastNodeWithRaiseAndIndirectCall;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CastToJavaLongLossyNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PosixConstants;
import com.oracle.graal.python.runtime.PosixConstants.IntConstant;
import com.oracle.graal.python.runtime.PosixSupport;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PosixSupportLibrary.Buffer;
import com.oracle.graal.python.runtime.PosixSupportLibrary.OpenPtyResult;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.Timeval;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonExitException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.LongSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
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
import com.oracle.truffle.api.dsl.TypeSystemReference;
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
                    // @formatter:off The formatter joins these lines making it less readable
                    "stat_result: Result from stat, fstat, or lstat.\n\n" +
                    "This object may be accessed either as a tuple of\n" +
                    "  (mode, ino, dev, nlink, uid, gid, size, atime, mtime, ctime)\n" +
                    "or via the attributes st_mode, st_ino, st_dev, st_nlink, st_uid, and so on.\n" +
                    "\n" +
                    "Posix/windows: If your platform supports st_blksize, st_blocks, st_rdev,\n" +
                    "or st_flags, they are available as attributes only.\n" +
                    "\n" +
                    "See os.stat for more information.",
                    // @formatter:on
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
                    // @formatter:off The formatter joins these lines making it less readable
                    "statvfs_result: Result from statvfs or fstatvfs.\n\n" +
                    "This object may be accessed either as a tuple of\n" +
                    "  (bsize, frsize, blocks, bfree, bavail, files, ffree, favail, flag, namemax),\n" +
                    "or via the attributes f_bsize, f_frsize, f_blocks, f_bfree, and so on.\n" +
                    "\n" +
                    "See os.statvfs for more information.",
                    // @formatter:on
                    10,
                    new String[]{
                                    "f_bsize", "f_frsize", "f_blocks", "f_bfree", "f_bavail", "f_files",
                                    "f_ffree", "f_favail", "f_flag", "f_namemax", "f_fsid"
                    },
                    null);

    private static final StructSequence.BuiltinTypeDescriptor TERMINAL_SIZE_DESC = new StructSequence.BuiltinTypeDescriptor(
                    PythonBuiltinClassType.PTerminalSize,
                    "A tuple of (columns, lines) for holding terminal window size",
                    2,
                    new String[]{"columns", "lines"},
                    new String[]{"width of the terminal window in characters", "height of the terminal window in characters"});

    private static final StructSequence.BuiltinTypeDescriptor UNAME_RESULT_DESC = new StructSequence.BuiltinTypeDescriptor(
                    PythonBuiltinClassType.PUnameResult,
                    // @formatter:off The formatter joins these lines making it less readable
                    "uname_result: Result from os.uname().\n\n" +
                    "This object may be accessed either as a tuple of\n" +
                    "  (sysname, nodename, release, version, machine),\n" +
                    "or via the attributes sysname, nodename, release, version, and machine.\n" +
                    "\n" +
                    "See os.uname for more information.",
                    // @formatter:on
                    5,
                    new String[]{"sysname", "nodename", "release", "version", "machine"},
                    new String[]{
                                    "operating system name", "name of machine on network (implementation-defined)",
                                    "operating system release", "operating system version", "hardware identifier"
                    });

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
        addBuiltinConstant("_have_functions", core.factory().createList(haveFunctions.toArray()));
        addBuiltinConstant("environ", core.factory().createDict());

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

        // fill the environ dictionary with the current environment
        // TODO we should probably use PosixSupportLibrary to get environ
        Map<String, String> getenv = System.getenv();
        PDict environ = core.factory().createDict();
        String pyenvLauncherKey = "__PYVENV_LAUNCHER__";
        for (Entry<String, String> entry : getenv.entrySet()) {
            if (entry.getKey().equals("GRAAL_PYTHON_ARGS") && entry.getValue().endsWith("\013")) {
                // was already processed at startup in GraalPythonMain and
                // we don't want subprocesses to pick it up
                continue;
            }
            Object key, val;
            if (PythonOS.getPythonOS() == PythonOS.PLATFORM_WIN32) {
                key = toTruffleStringUncached(entry.getKey());
            } else {
                key = core.factory().createBytes(entry.getKey().getBytes());
            }
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
                if (PythonOS.getPythonOS() == PythonOS.PLATFORM_WIN32) {
                    val = value;
                } else {
                    val = core.factory().createBytes(value.toJavaStringUncached().getBytes());
                }
            } else {
                if (PythonOS.getPythonOS() == PythonOS.PLATFORM_WIN32) {
                    val = toTruffleStringUncached(entry.getValue());
                } else {
                    val = core.factory().createBytes((entry.getValue().getBytes()));
                }
            }
            environ.setItem(key, val);
        }
        if (PythonOS.getPythonOS() == PythonOS.PLATFORM_WIN32) {
            // XXX: Until we fix pip
            environ.setItem(toTruffleStringUncached("PIP_NO_CACHE_DIR"), toTruffleStringUncached("0"));
        }
        PythonModule posix;
        if (PythonOS.getPythonOS() == PythonOS.PLATFORM_WIN32) {
            posix = core.lookupBuiltinModule(T_NT);
        } else {
            posix = core.lookupBuiltinModule(T_POSIX);
        }
        Object environAttr = posix.getAttribute(tsLiteral("environ"));
        ((PDict) environAttr).setDictStorage(environ.getDictStorage());

        if (posixLib.getBackend(posixSupport).toJavaStringUncached().equals("java")) {
            posix.setAttribute(toTruffleStringUncached("statvfs"), PNone.NO_VALUE);
            posix.setAttribute(toTruffleStringUncached("geteuid"), PNone.NO_VALUE);
        }
    }

    @Builtin(name = "stat_result", minNumOfPositionalArgs = 1, parameterNames = {"$cls", "sequence", "dict"}, constructsClass = PythonBuiltinClassType.PStatResult)
    @ImportStatic(PosixModuleBuiltins.class)
    @GenerateNodeFactory
    public abstract static class StatResultNode extends PythonTernaryBuiltinNode {

        @Specialization
        public static PTuple generic(VirtualFrame frame, Object cls, Object sequence, Object dict,
                        @Cached("create(STAT_RESULT_DESC)") StructSequence.NewNode newNode) {
            PTuple p = (PTuple) newNode.execute(frame, cls, sequence, dict);
            Object[] data = CompilerDirectives.castExact(p.getSequenceStorage(), ObjectSequenceStorage.class).getInternalArray();
            for (int i = 7; i <= 9; i++) {
                if (data[i + 3] == PNone.NONE) {
                    data[i + 3] = data[i];
                }
            }
            return p;
        }
    }

    @Builtin(name = "statvfs_result", minNumOfPositionalArgs = 1, parameterNames = {"$cls", "sequence", "dict"}, constructsClass = PythonBuiltinClassType.PStatvfsResult)
    @ImportStatic(PosixModuleBuiltins.class)
    @GenerateNodeFactory
    public abstract static class StatvfsResultNode extends PythonTernaryBuiltinNode {

        @Specialization
        public static Object generic(VirtualFrame frame, Object cls, Object sequence, Object dict,
                        @Cached("create(STATVFS_RESULT_DESC)") StructSequence.NewNode newNode) {
            return newNode.execute(frame, cls, sequence, dict);
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
        PNone putenv(VirtualFrame frame, PBytes nameBytes, PBytes valueBytes,
                        @Bind("this") Node inliningTarget,
                        @Cached BytesNodes.ToBytesNode toBytesNode,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            // Unlike in other posix builtins, we go through str -> bytes -> byte[] -> String
            // conversions for emulated backend because the bytes version after fsencode conversion
            // is subject to sys.audit.
            byte[] name = toBytesNode.execute(nameBytes);
            byte[] value = toBytesNode.execute(valueBytes);
            Object nameOpaque = checkNull(posixLib.createPathFromBytes(getPosixSupport(), name));
            Object valueOpaque = checkNull(posixLib.createPathFromBytes(getPosixSupport(), value));
            checkEqualSign(name);
            auditNode.audit(inliningTarget, "os.putenv", nameBytes, valueBytes);
            try {
                posixLib.setenv(getPosixSupport(), nameOpaque, valueOpaque, true);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
            return PNone.NONE;
        }

        private Object checkNull(Object value) {
            if (value == null) {
                throw raise(ValueError, ErrorMessages.EMBEDDED_NULL_BYTE);
            }
            return value;
        }

        private void checkEqualSign(byte[] bytes) {
            for (byte b : bytes) {
                if (b == '=') {
                    throw raise(ValueError, ErrorMessages.ILLEGAL_ENVIRONMENT_VARIABLE_NAME);
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
        PNone putenv(VirtualFrame frame, PBytes nameBytes,
                        @Bind("this") Node inliningTarget,
                        @Cached BytesNodes.ToBytesNode toBytesNode,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            byte[] name = toBytesNode.execute(nameBytes);
            Object nameOpaque = checkNull(posixLib.createPathFromBytes(getPosixSupport(), name));
            auditNode.audit(inliningTarget, "os.unsetenv", nameBytes);
            try {
                posixLib.unsetenv(getPosixSupport(), nameOpaque);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
            return PNone.NONE;
        }

        private Object checkNull(Object value) {
            if (value == null) {
                throw raise(ValueError, ErrorMessages.EMBEDDED_NULL_BYTE);
            }
            return value;
        }
    }

    @Builtin(name = "execv", minNumOfPositionalArgs = 2, parameterNames = {"pathname", "argv"})
    @ArgumentClinic(name = "pathname", conversionClass = PathConversionNode.class, args = {"false", "false"})
    @GenerateNodeFactory
    @SuppressWarnings("truffle-static-method")
    public abstract static class ExecvNode extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.ExecvNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        Object execvArgsList(VirtualFrame frame, PosixPath path, PList argv,
                        @Bind("this") Node inliningTarget,
                        @Shared @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Shared @Cached ToArrayNode toArrayNode,
                        @Shared @Cached ObjectToOpaquePathNode toOpaquePathNode,
                        @Shared @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Shared @Cached GilNode gil,
                        @Shared @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            execv(frame, path, argv, argv.getSequenceStorage(), inliningTarget, posixLib, toArrayNode, toOpaquePathNode, auditNode, gil, constructAndRaiseNode);
            throw CompilerDirectives.shouldNotReachHere("execv should not return normally");
        }

        @Specialization
        Object execvArgsTuple(VirtualFrame frame, PosixPath path, PTuple argv,
                        @Bind("this") Node inliningTarget,
                        @Shared @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Shared @Cached ToArrayNode toArrayNode,
                        @Shared @Cached ObjectToOpaquePathNode toOpaquePathNode,
                        @Shared @Cached AuditNode auditNode,
                        @Shared @Cached GilNode gil,
                        @Shared @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            execv(frame, path, argv, argv.getSequenceStorage(), inliningTarget, posixLib, toArrayNode, toOpaquePathNode, auditNode, gil, constructAndRaiseNode);
            throw CompilerDirectives.shouldNotReachHere("execv should not return normally");
        }

        @Specialization(guards = {"!isList(argv)", "!isPTuple(argv)"})
        @SuppressWarnings("unused")
        Object execvInvalidArgs(VirtualFrame frame, PosixPath path, Object argv) {
            throw raise(TypeError, ErrorMessages.ARG_D_MUST_BE_S, "execv()", 2, "tuple or list");
        }

        private void execv(VirtualFrame frame, PosixPath path, Object argv, SequenceStorage argvStorage,
                        Node inliningTarget,
                        PosixSupportLibrary posixLib,
                        SequenceStorageNodes.ToArrayNode toArrayNode,
                        ObjectToOpaquePathNode toOpaquePathNode,
                        SysModuleBuiltins.AuditNode auditNode,
                        GilNode gil,
                        PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            Object[] args = toArrayNode.execute(inliningTarget, argvStorage);
            if (args.length < 1) {
                throw raise(ValueError, ErrorMessages.ARG_MUST_NOT_BE_EMPTY, "execv()", 2);
            }
            Object[] opaqueArgs = new Object[args.length];
            for (int i = 0; i < args.length; ++i) {
                opaqueArgs[i] = toOpaquePathNode.execute(frame, inliningTarget, args[i], i == 0);
            }
            // TODO ValueError "execv() arg 2 first element cannot be empty"

            auditNode.audit(inliningTarget, "os.exec", path.originalObject, argv, PNone.NONE);

            gil.release(true);
            try {
                posixLib.execv(getPosixSupport(), path.value, opaqueArgs);
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
        long getPid(@CachedLibrary(limit = "1") PosixSupportLibrary posixLib) {
            return posixLib.getpid(getPosixSupport());
        }
    }

    @Builtin(name = "getuid", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class GetUidNode extends PythonBuiltinNode {
        @Specialization
        long getUid(@CachedLibrary(limit = "1") PosixSupportLibrary posixLib) {
            return posixLib.getuid(getPosixSupport());
        }
    }

    @Builtin(name = "geteuid", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class GetEUidNode extends PythonBuiltinNode {
        @Specialization
        long getUid(@CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            return posixLib.geteuid(getPosixSupport());
        }
    }

    @Builtin(name = "getgid", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class GetGidNode extends PythonBuiltinNode {
        @Specialization
        long getGid(@CachedLibrary(limit = "1") PosixSupportLibrary posixLib) {
            return posixLib.getgid(getPosixSupport());
        }
    }

    @Builtin(name = "getppid", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class GetPpidNode extends PythonBuiltinNode {
        @Specialization
        long getPpid(@CachedLibrary(limit = "1") PosixSupportLibrary posixLib) {
            return posixLib.getppid(getPosixSupport());
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
        long getPgid(VirtualFrame frame, long pid,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                return posixLib.getpgid(getPosixSupport(), pid);
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
        Object setPgid(VirtualFrame frame, long pid, long pgid,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                posixLib.setpgid(getPosixSupport(), pid, pgid);
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
        Object getPpid(VirtualFrame frame,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                posixLib.setpgid(getPosixSupport(), 0, 0);
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
        long getPpid(@CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            return posixLib.getpgrp(getPosixSupport());
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
        long getSid(VirtualFrame frame, long pid,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                return posixLib.getsid(getPosixSupport(), pid);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    @Builtin(name = "setsid")
    @GenerateNodeFactory
    public abstract static class SetSidNode extends PythonBuiltinNode {

        @Specialization
        Object setsid(VirtualFrame frame,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                posixLib.setsid(getPosixSupport());
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
        Object getgroups(VirtualFrame frame,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PythonObjectFactory factory) {
            try {
                long[] groups = posixLib.getgroups(getPosixSupport());
                return factory.createList(new LongSequenceStorage(groups));
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    @Builtin(name = "openpty")
    @GenerateNodeFactory
    public abstract static class OpenPtyNode extends PythonBuiltinNode {

        @Specialization
        Object openpty(VirtualFrame frame,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PythonObjectFactory factory) {
            try {
                OpenPtyResult result = posixLib.openpty(getPosixSupport());
                posixLib.setInheritable(getPosixSupport(), result.masterFd(), false);
                posixLib.setInheritable(getPosixSupport(), result.slaveFd(), false);
                return factory.createTuple(new int[]{result.masterFd(), result.slaveFd()});
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    @Builtin(name = "open", minNumOfPositionalArgs = 2, parameterNames = {"path", "flags", "mode"}, keywordOnlyNames = {"dir_fd"})
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
        int open(VirtualFrame frame, PosixPath path, int flags, int mode, int dirFd,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
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
                        return posixLib.openat(getPosixSupport(), dirFd, path.value, fixedFlags, mode);
                    } catch (PosixException e) {
                        errorProfile.enter(inliningTarget);
                        if (e.getErrorCode() == OSErrorEnum.EINTR.getNumber()) {
                            PythonContext.triggerAsyncActions(this);
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
        PNone close(VirtualFrame frame, int fd,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Cached GilNode gil,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                PythonContext ctx = getContext();
                if (ctx.getSharedMultiprocessingData().decrementFDRefCount(fd)) {
                    return PNone.NONE;
                }
                gil.release(true);
                try {
                    posixLib.close(getPosixSupport(), fd);
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
        PBytes doRead(VirtualFrame frame, int fd, int length,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Cached InlinedBranchProfile errorProfile1,
                        @Cached InlinedBranchProfile errorProfile2,
                        @Cached GilNode gil,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PythonObjectFactory factory) {
            if (length < 0) {
                int error = OSErrorEnum.EINVAL.getNumber();
                throw constructAndRaiseNode.get(inliningTarget).raiseOSError(frame, error, posixLib.strerror(getPosixSupport(), error));
            }
            try {
                return read(fd, length, inliningTarget, posixLib, errorProfile1, gil, factory);
            } catch (PosixException e) {
                errorProfile2.enter(inliningTarget);
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
        }

        public PBytes read(int fd, int length,
                        Node inliningTarget,
                        PosixSupportLibrary posixLib,
                        InlinedBranchProfile errorProfile, GilNode gil, PythonObjectFactory factory) throws PosixException {
            gil.release(true);
            try {
                while (true) {
                    try {
                        Buffer result = posixLib.read(getPosixSupport(), fd, length);
                        if (result.length > Integer.MAX_VALUE) {
                            // sanity check that it is safe to cast result.length to int, to be
                            // removed once we support large arrays
                            throw CompilerDirectives.shouldNotReachHere("Posix read() returned more bytes than requested");
                        }
                        return factory.createBytes(result.data, 0, (int) result.length);
                    } catch (PosixException e) {
                        errorProfile.enter(inliningTarget);
                        if (e.getErrorCode() == OSErrorEnum.EINTR.getNumber()) {
                            PythonContext.triggerAsyncActions(this);
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
        @SuppressWarnings("truffle-static-method")
        long doWrite(VirtualFrame frame, int fd, Object dataBuffer,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary("dataBuffer") PythonBufferAccessLibrary bufferLib,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Cached InlinedBranchProfile errorProfile,
                        @Cached GilNode gil,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                return write(fd, bufferLib.getInternalOrCopiedByteArray(dataBuffer), bufferLib.getBufferLength(dataBuffer), inliningTarget, posixLib, errorProfile, gil);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            } finally {
                bufferLib.release(dataBuffer, frame, this);
            }
        }

        public long write(int fd, byte[] dataBytes,
                        int dataLen, Node inliningTarget, PosixSupportLibrary posixLib,
                        InlinedBranchProfile errorProfile, GilNode gil) throws PosixException {
            gil.release(true);
            try {
                while (true) {
                    try {
                        return posixLib.write(getPosixSupport(), fd, new Buffer(dataBytes, dataLen));
                    } catch (PosixException e) {
                        errorProfile.enter(inliningTarget);
                        if (e.getErrorCode() == OSErrorEnum.EINTR.getNumber()) {
                            PythonContext.triggerAsyncActions(this);
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
        int dup(VirtualFrame frame, int fd,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                return posixLib.dup(getPosixSupport(), fd);
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
        int dup2(VirtualFrame frame, int fd, int fd2, boolean inheritable,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            if (fd < 0 || fd2 < 0) {
                // CPython does not set errno here and raises a 'random' OSError
                // (possibly with errno=0 Success)
                int error = OSErrorEnum.EINVAL.getNumber();
                throw constructAndRaiseNode.get(inliningTarget).raiseOSError(frame, error, posixLib.strerror(getPosixSupport(), error));
            }

            try {
                return posixLib.dup2(getPosixSupport(), fd, fd2, inheritable);
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
        boolean getInheritable(VirtualFrame frame, int fd,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                return posixLib.getInheritable(getPosixSupport(), fd);
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
        PNone setInheritable(VirtualFrame frame, int fd, int inheritable,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                // not sure why inheritable is not a boolean, but that is how they do it in CPython
                posixLib.setInheritable(getPosixSupport(), fd, inheritable != 0);
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
        PTuple pipe(VirtualFrame frame,
                        @Bind("this") Node inliningTarget,
                        @Cached GilNode gil,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PythonObjectFactory factory) {
            int[] pipe;
            gil.release(true);
            try {
                pipe = posixLib.pipe(getPosixSupport());
            } catch (PosixException e) {
                gil.acquire(); // need to acquire the gil to construct the OSError object
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            } finally {
                gil.acquire();
            }
            return factory.createTuple(new Object[]{pipe[0], pipe[1]});
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
        long lseek(VirtualFrame frame, int fd, long pos, int how,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                return posixLib.lseek(getPosixSupport(), fd, pos, mapPythonSeekWhenceToPosix(how));
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
        PNone ftruncate(VirtualFrame frame, int fd, long length,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Cached GilNode gil,
                        @Cached InlinedBranchProfile errorProfile,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            auditNode.audit(inliningTarget, "os.truncate", fd, length);
            while (true) {
                try {
                    gil.release(true);
                    try {
                        posixLib.ftruncate(getPosixSupport(), fd, length);
                    } finally {
                        gil.acquire();
                    }
                    return PNone.NONE;
                } catch (PosixException e) {
                    errorProfile.enter(inliningTarget);
                    if (e.getErrorCode() == OSErrorEnum.EINTR.getNumber()) {
                        PythonContext.triggerAsyncActions(this);
                    } else {
                        throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
                    }
                }
            }
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
        PNone fsync(VirtualFrame frame, int fd,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Cached InlinedBranchProfile errorProfile,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            while (true) {
                try {
                    posixLib.fsync(getPosixSupport(), fd);
                    return PNone.NONE;
                } catch (PosixException e) {
                    errorProfile.enter(inliningTarget);
                    if (e.getErrorCode() == OSErrorEnum.EINTR.getNumber()) {
                        PythonContext.triggerAsyncActions(this);
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
        boolean getBlocking(VirtualFrame frame, int fd,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                return posixLib.getBlocking(getPosixSupport(), fd);
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
        PNone setBlocking(VirtualFrame frame, int fd, boolean blocking,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                posixLib.setBlocking(getPosixSupport(), fd, blocking);
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
        PTuple getTerminalSize(VirtualFrame frame, int fd,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PythonObjectFactory factory) {
            // TODO default value should be fileno(stdout)
            try {
                int[] result = posixLib.getTerminalSize(getPosixSupport(), fd);
                return factory.createStructSeq(TERMINAL_SIZE_DESC, result[0], result[1]);
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
        PTuple doStatPath(VirtualFrame frame, PosixPath path, int dirFd, boolean followSymlinks,
                        @Bind("this") Node inliningTarget,
                        @Shared @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Shared("positive") @Cached InlinedConditionProfile positiveLongProfile,
                        @Shared @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Shared @Cached PythonObjectFactory factory) {
            try {
                long[] out = posixLib.fstatat(getPosixSupport(), dirFd, path.value, followSymlinks);
                return createStatResult(inliningTarget, factory, positiveLongProfile, out);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e, path.originalObject);
            }
        }

        @Specialization(guards = "!isDefault(dirFd)")
        @SuppressWarnings("unused")
        PTuple doStatFdWithDirFd(PosixFd fd, int dirFd, boolean followSymlinks) {
            throw raise(ValueError, ErrorMessages.CANT_SPECIFY_DIRFD_WITHOUT_PATH, "stat");
        }

        @Specialization(guards = {"isDefault(dirFd)", "!followSymlinks"})
        @SuppressWarnings("unused")
        PTuple doStatFdWithFollowSymlinks(VirtualFrame frame, PosixFd fd, int dirFd, boolean followSymlinks) {
            throw raise(ValueError, ErrorMessages.CANNOT_USE_FD_AND_FOLLOW_SYMLINKS_TOGETHER, "stat");
        }

        @Specialization(guards = {"isDefault(dirFd)", "followSymlinks"})
        PTuple doStatFd(VirtualFrame frame, PosixFd fd, @SuppressWarnings("unused") int dirFd, @SuppressWarnings("unused") boolean followSymlinks,
                        @Bind("this") Node inliningTarget,
                        @Shared @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Shared("positive") @Cached InlinedConditionProfile positiveLongProfile,
                        @Shared @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Shared @Cached PythonObjectFactory factory) {
            try {
                long[] out = posixLib.fstat(getPosixSupport(), fd.fd);
                return createStatResult(inliningTarget, factory, positiveLongProfile, out);
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
        PTuple doStatPath(VirtualFrame frame, PosixPath path, int dirFd,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Cached InlinedConditionProfile positiveLongProfile,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PythonObjectFactory factory) {
            try {
                // TODO we used to return all zeros when the filename was equal to sys.executable
                long[] out = posixLib.fstatat(getPosixSupport(), dirFd, path.value, false);
                return createStatResult(inliningTarget, factory, positiveLongProfile, out);
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
        PTuple doStatFd(VirtualFrame frame, int fd,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Cached InlinedConditionProfile positiveLongProfile,
                        @Cached InlinedBranchProfile errorProfile,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PythonObjectFactory factory) {
            while (true) {
                try {
                    long[] out = posixLib.fstat(getPosixSupport(), fd);
                    return createStatResult(inliningTarget, factory, positiveLongProfile, out);
                } catch (PosixException e) {
                    errorProfile.enter(inliningTarget);
                    if (e.getErrorCode() == OSErrorEnum.EINTR.getNumber()) {
                        PythonContext.triggerAsyncActions(this);
                    } else {
                        throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
                    }
                }
            }
        }
    }

    @Builtin(name = "statvfs", minNumOfPositionalArgs = 1, parameterNames = {"path"})
    @ArgumentClinic(name = "path", conversionClass = PathConversionNode.class, args = {"false", "true"})
    @GenerateNodeFactory
    abstract static class StatvfsNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.StatvfsNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        PTuple doStatvfs(VirtualFrame frame, PosixFileHandle posixFileHandle,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Cached InlinedConditionProfile posixPathProfile,
                        @Cached InlinedConditionProfile positiveLongProfile,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PythonObjectFactory factory) {
            long[] out;
            try {
                if (posixPathProfile.profile(inliningTarget, posixFileHandle instanceof PosixPath)) {
                    out = posixLib.statvfs(getPosixSupport(), ((PosixPath) posixFileHandle).value);
                } else {
                    out = posixLib.fstatvfs(getPosixSupport(), ((PosixFd) posixFileHandle).fd);
                }
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e, posixFileHandle.originalObject);
            }
            Object[] res = new Object[out.length];
            for (int i = 0; i < out.length; i++) {
                res[i] = PInt.createPythonIntFromUnsignedLong(inliningTarget, factory, positiveLongProfile, out[i]);
            }
            return factory.createStructSeq(STATVFS_RESULT_DESC, res);
        }

    }

    @Builtin(name = "uname", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class UnameNode extends PythonBuiltinNode {

        @Specialization
        PTuple uname(VirtualFrame frame,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PythonObjectFactory factory) {
            try {
                return factory.createStructSeq(UNAME_RESULT_DESC, posixLib.uname(getPosixSupport()));
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    @Builtin(name = "unlink", minNumOfPositionalArgs = 1, parameterNames = {"path"}, varArgsMarker = true, keywordOnlyNames = {"dir_fd"})
    @ArgumentClinic(name = "path", conversionClass = PathConversionNode.class, args = {"false", "false"})
    @ArgumentClinic(name = "dir_fd", conversionClass = DirFdConversionNode.class)
    @GenerateNodeFactory
    abstract static class UnlinkNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.UnlinkNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        PNone unlink(VirtualFrame frame, PosixPath path, int dirFd,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Bind("this") Node inliningTarget,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            auditNode.audit(inliningTarget, "os.remove", path.originalObject, dirFdForAudit(dirFd));
            try {
                posixLib.unlinkat(getPosixSupport(), dirFd, path.value, false);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e, path.originalObject);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "remove", minNumOfPositionalArgs = 1, parameterNames = {"path"}, varArgsMarker = true, keywordOnlyNames = {"dir_fd"})
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
        PNone link(VirtualFrame frame, PosixPath src, PosixPath dst, int srcDirFd, int dstDirFd, boolean followSymlinks,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                posixLib.linkat(getPosixSupport(), srcDirFd, src.value, dstDirFd, dst.value, followSymlinks ? AT_SYMLINK_FOLLOW.value : 0);
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
        PNone symlink(VirtualFrame frame, PosixPath src, PosixPath dst, @SuppressWarnings("unused") boolean targetIsDir, int dirFd,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                posixLib.symlinkat(getPosixSupport(), src.value, dirFd, dst.value);
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
        PNone mkdir(VirtualFrame frame, PosixPath path, int mode, int dirFd,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Bind("this") Node inliningTarget,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            auditNode.audit(inliningTarget, "os.mkdir", path.originalObject, mode, dirFdForAudit(dirFd));
            try {
                posixLib.mkdirat(getPosixSupport(), dirFd, path.value, mode);
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
        PNone rmdir(VirtualFrame frame, PosixPath path, int dirFd,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Bind("this") Node inliningTarget,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            auditNode.audit(inliningTarget, "os.rmdir", path.originalObject, dirFdForAudit(dirFd));
            try {
                posixLib.unlinkat(getPosixSupport(), dirFd, path.value, true);
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
        TruffleString getcwd(VirtualFrame frame,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                return posixLib.getPathAsString(getPosixSupport(), posixLib.getcwd(getPosixSupport()));
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    @Builtin(name = "getcwdb", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class GetcwdbNode extends PythonBuiltinNode {
        @Specialization
        PBytes getcwdb(VirtualFrame frame,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PythonObjectFactory factory) {
            try {
                return opaquePathToBytes(posixLib.getcwd(getPosixSupport()), posixLib, getPosixSupport(), factory);
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
        PNone chdirPath(VirtualFrame frame, PosixPath path,
                        @Bind("this") Node inliningTarget,
                        @Shared @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Shared @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                posixLib.chdir(getPosixSupport(), path.value);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e, path.originalObject);
            }
            return PNone.NONE;
        }

        @Specialization
        PNone chdirFd(VirtualFrame frame, PosixFd fd,
                        @Bind("this") Node inliningTarget,
                        @Shared @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Shared @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                posixLib.fchdir(getPosixSupport(), fd.fd);
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
        PNone fchdir(VirtualFrame frame, int fd,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Cached InlinedBranchProfile errorProfile,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            while (true) {
                try {
                    posixLib.fchdir(getPosixSupport(), fd);
                    return PNone.NONE;
                } catch (PosixException e) {
                    errorProfile.enter(inliningTarget);
                    if (e.getErrorCode() == OSErrorEnum.EINTR.getNumber()) {
                        PythonContext.triggerAsyncActions(this);
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
        boolean isatty(int fd,
                        @Cached GilNode gil,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib) {
            gil.release(true);
            try {
                return posixLib.isatty(getPosixSupport(), fd);
            } finally {
                gil.acquire();
            }
        }
    }

    @Builtin(name = "ScandirIterator", takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PScandirIterator, isPublic = false)
    @GenerateNodeFactory
    abstract static class ScandirIteratorNode extends PythonBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        Object scandirIterator(Object args, Object kwargs) {
            throw raise(TypeError, ErrorMessages.CANNOT_CREATE_INSTANCES, "posix.ScandirIterator");
        }
    }

    @Builtin(name = "DirEntry", takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PDirEntry, isPublic = true)
    @GenerateNodeFactory
    abstract static class DirEntryNode extends PythonBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        Object dirEntry(Object args, Object kwargs) {
            throw raise(TypeError, ErrorMessages.CANNOT_CREATE_INSTANCES, "posix.DirEntry");
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
        PScandirIterator scandirPath(VirtualFrame frame, PosixPath path,
                        @Shared @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Shared @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Shared @Cached PythonObjectFactory factory) {
            auditNode.audit(inliningTarget, "os.scandir", path.originalObject == null ? PNone.NONE : path.originalObject);
            try {
                return factory.createScandirIterator(getContext(), posixLib.opendir(getPosixSupport(), path.value), path, false);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e, path.originalObject);
            }
        }

        @Specialization
        PScandirIterator scandirFd(VirtualFrame frame, PosixFd fd,
                        @Shared @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Shared @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Shared @Cached PythonObjectFactory factory) {
            auditNode.audit(inliningTarget, "os.scandir", fd.originalObject);
            Object dirStream = dupAndFdopendir(frame, inliningTarget, posixLib, getPosixSupport(), fd, constructAndRaiseNode);
            return factory.createScandirIterator(getContext(), dirStream, fd, true);
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
        PList listdirPath(VirtualFrame frame, PosixPath path,
                        @Shared @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Shared @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Shared @Cached PythonObjectFactory factory) {
            auditNode.audit(inliningTarget, "os.listdir", path.originalObject == null ? PNone.NONE : path.originalObject);
            try {
                return listdir(frame, inliningTarget, posixLib.opendir(getPosixSupport(), path.value), path.wasBufferLike, false, posixLib, constructAndRaiseNode, factory);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e, path.originalObject);
            }
        }

        @Specialization
        PList listdirFd(VirtualFrame frame, PosixFd fd,
                        @Shared @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Shared @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Shared @Cached PythonObjectFactory factory) {
            auditNode.audit(inliningTarget, "os.listdir", fd.originalObject);
            Object dirStream = dupAndFdopendir(frame, inliningTarget, posixLib, getPosixSupport(), fd, constructAndRaiseNode);
            return listdir(frame, inliningTarget, dirStream, false, true, posixLib, constructAndRaiseNode, factory);
        }

        private PList listdir(VirtualFrame frame, Node inliningTarget, Object dirStream, boolean produceBytes, boolean needsRewind, PosixSupportLibrary posixLib,
                        PConstructAndRaiseNode.Lazy constructAndRaiseNode, PythonObjectFactory factory) {
            List<Object> list = new ArrayList<>();
            try {
                while (true) {
                    Object dirEntry = posixLib.readdir(getPosixSupport(), dirStream);
                    if (dirEntry == null) {
                        return factory.createList(listToArray(list));
                    }
                    Object name = posixLib.dirEntryGetName(getPosixSupport(), dirEntry);
                    if (produceBytes) {
                        addToList(list, opaquePathToBytes(name, posixLib, getPosixSupport(), factory));
                    } else {
                        addToList(list, posixLib.getPathAsString(getPosixSupport(), name));
                    }
                }
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            } finally {
                if (needsRewind) {
                    posixLib.rewinddir(getPosixSupport(), dirStream);
                }
                try {
                    posixLib.closedir(getPosixSupport(), dirStream);
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
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached LenNode lenNode,
                        @Shared @Cached("createNotNormalized()") GetItemNode getItemNode,
                        @Cached ObjectToTimespecNode objectToTimespecNode,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            return convertToTimespec(frame, inliningTarget, times, lenNode, getItemNode, objectToTimespecNode, raiseNode);
        }

        @Specialization
        static long[] ns(VirtualFrame frame, @SuppressWarnings("unused") PNone times, PTuple ns,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached LenNode lenNode,
                        @Shared @Cached("createNotNormalized()") GetItemNode getItemNode,
                        @Cached SplitLongToSAndNsNode splitLongToSAndNsNode,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            return convertToTimespec(frame, inliningTarget, ns, lenNode, getItemNode, splitLongToSAndNsNode, raiseNode);
        }

        @Specialization(guards = {"!isPNone(times)", "!isNoValue(ns)"})
        @SuppressWarnings("unused")
        static long[] bothSpecified(VirtualFrame frame, Object times, Object ns,
                        @Shared @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(ValueError, ErrorMessages.YOU_MAY_SPECIFY_EITHER_OR_BUT_NOT_BOTH, "utime", "times", "ns");
        }

        @Specialization(guards = {"!isPNone(times)", "!isPTuple(times)", "isNoValue(ns)"})
        @SuppressWarnings("unused")
        static long[] timesNotATuple(VirtualFrame frame, Object times, PNone ns,
                        @Shared @Cached PRaiseNode raiseNode) {
            throw timesTupleError(raiseNode);
        }

        @Specialization(guards = {"!isNoValue(ns)", "!isPTuple(ns)"})
        @SuppressWarnings("unused")
        static long[] nsNotATuple(VirtualFrame frame, PNone times, Object ns,
                        @Shared @Cached PRaiseNode raiseNode) {
            // ns can actually also contain objects implementing __divmod__, but CPython produces
            // this error message
            throw raiseNode.raise(TypeError, ErrorMessages.MUST_BE, "utime", "ns", "a tuple of two ints");
        }

        private static PException timesTupleError(PRaiseNode raiseNode) {
            // times can actually also contain floats, but CPython produces this error message
            throw raiseNode.raise(TypeError, ErrorMessages.MUST_BE_EITHER_OR, "utime", "times", "a tuple of two ints", "None");
        }

        private static long[] convertToTimespec(VirtualFrame frame, Node inliningTarget, PTuple times, LenNode lenNode, GetItemNode getItemNode, ConvertToTimespecBaseNode convertToTimespecBaseNode,
                        PRaiseNode.Lazy raiseNode) {
            if (lenNode.execute(inliningTarget, times) != 2) {
                throw timesTupleError(raiseNode.get(inliningTarget));
            }
            long[] timespec = new long[4];
            convertToTimespecBaseNode.execute(frame, inliningTarget, getItemNode.execute(times.getSequenceStorage(), 0), timespec, 0);
            convertToTimespecBaseNode.execute(frame, inliningTarget, getItemNode.execute(times.getSequenceStorage(), 1), timespec, 2);
            return timespec;
        }
    }

    @Builtin(name = "utime", minNumOfPositionalArgs = 1, parameterNames = {"path", "times"}, varArgsMarker = true, keywordOnlyNames = {"ns", "dir_fd", "follow_symlinks"})
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
        PNone utimensat(VirtualFrame frame, PosixPath path, Object times, Object ns, int dirFd, boolean followSymlinks,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached UtimeArgsToTimespecNode timespecNode,
                        @Shared @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Shared @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Shared @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            long[] timespec = timespecNode.execute(frame, times, ns);
            auditNode.audit(inliningTarget, "os.utime", path.originalObject, checkNone(times), checkNone(ns), dirFdForAudit(dirFd));
            try {
                posixLib.utimensat(getPosixSupport(), dirFd, path.value, timespec, followSymlinks);
            } catch (PosixException e) {
                // filename is intentionally not included, see CPython's os_utime_impl
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
            return PNone.NONE;
        }

        @Specialization(guards = {"!HAVE_UTIMENSAT.value", "isDefault(dirFd)", "followSymlinks"})
        PNone utimes(VirtualFrame frame, PosixPath path, Object times, Object ns, int dirFd, @SuppressWarnings("unused") boolean followSymlinks,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached UtimeArgsToTimespecNode timespecNode,
                        @Shared @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Shared @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Shared @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            Timeval[] timeval = timespecNode.toTimeval(frame, times, ns);
            auditNode.audit(inliningTarget, "os.utime", path.originalObject, checkNone(times), checkNone(ns), dirFdForAudit(dirFd));
            try {
                posixLib.utimes(getPosixSupport(), path.value, timeval);
            } catch (PosixException e) {
                // filename is intentionally not included, see CPython's os_utime_impl
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
            return PNone.NONE;
        }

        @Specialization(guards = {"!HAVE_UTIMENSAT.value", "isDefault(dirFd)", "!followSymlinks"})
        PNone lutimes(VirtualFrame frame, PosixPath path, Object times, Object ns, int dirFd, @SuppressWarnings("unused") boolean followSymlinks,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached UtimeArgsToTimespecNode timespecNode,
                        @Shared @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Shared @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Shared @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            Timeval[] timeval = timespecNode.toTimeval(frame, times, ns);
            auditNode.audit(inliningTarget, "os.utime", path.originalObject, checkNone(times), checkNone(ns), dirFdForAudit(dirFd));
            try {
                posixLib.lutimes(getPosixSupport(), path.value, timeval);
            } catch (PosixException e) {
                // filename is intentionally not included, see CPython's os_utime_impl
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
            return PNone.NONE;
        }

        @Specialization(guards = {"!HAVE_UTIMENSAT.value", "!isDefault(dirFd)", "followSymlinks"})
        @SuppressWarnings("unused")
        PNone dirFdNotSupported(VirtualFrame frame, PosixPath path, Object times, Object ns, int dirFd, boolean followSymlinks) {
            throw raise(NotImplementedError, ErrorMessages.UNAVAILABLE_ON_THIS_PLATFORM_NO_FUNC, "dir_fd");
        }

        @Specialization(guards = {"!HAVE_UTIMENSAT.value", "!isDefault(dirFd)", "!followSymlinks"})
        @SuppressWarnings("unused")
        PNone dirFdAndFollowSymlinksNotSupported(VirtualFrame frame, PosixPath path, Object times, Object ns, int dirFd, boolean followSymlinks) {
            throw raise(ValueError, ErrorMessages.UTIME_CANNOT_USE_DIR_FD_AND_FOLLOW_SYMLINKS, "dir_fd");
        }

        @Specialization(guards = {"HAVE_FUTIMENS.value", "isDefault(dirFd)", "followSymlinks"})
        PNone futimens(VirtualFrame frame, PosixFd fd, Object times, Object ns, int dirFd, @SuppressWarnings("unused") boolean followSymlinks,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached UtimeArgsToTimespecNode timespecNode,
                        @Shared @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Shared @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Shared @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            long[] timespec = timespecNode.execute(frame, times, ns);
            auditNode.audit(inliningTarget, "os.utime", fd.originalObject, checkNone(times), checkNone(ns), dirFdForAudit(dirFd));
            try {
                posixLib.futimens(getPosixSupport(), fd.fd, timespec);
            } catch (PosixException e) {
                // filename is intentionally not included, see CPython's os_utime_impl
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
            return PNone.NONE;
        }

        @Specialization(guards = {"!HAVE_FUTIMENS.value", "isDefault(dirFd)", "followSymlinks"})
        PNone futimes(VirtualFrame frame, PosixFd fd, Object times, Object ns, int dirFd, @SuppressWarnings("unused") boolean followSymlinks,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached UtimeArgsToTimespecNode timespecNode,
                        @Shared @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Shared @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Shared @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            Timeval[] timeval = timespecNode.toTimeval(frame, times, ns);
            auditNode.audit(inliningTarget, "os.utime", fd.originalObject, checkNone(times), checkNone(ns), dirFdForAudit(dirFd));
            try {
                posixLib.futimes(getPosixSupport(), fd.fd, timeval);
            } catch (PosixException e) {
                // filename is intentionally not included, see CPython's os_utime_impl
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
            return PNone.NONE;
        }

        @Specialization(guards = {"isPNone(times) || isNoValue(ns)", "!isDefault(dirFd)"})
        @SuppressWarnings("unused")
        PNone fdWithDirFd(VirtualFrame frame, PosixFd fd, Object times, Object ns, int dirFd, boolean followSymlinks) {
            throw raise(ValueError, ErrorMessages.CANT_SPECIFY_DIRFD_WITHOUT_PATH, "utime");
        }

        @Specialization(guards = {"isPNone(times) || isNoValue(ns)", "isDefault(dirFd)", "!followSymlinks"})
        @SuppressWarnings("unused")
        PNone fdWithFollowSymlinks(VirtualFrame frame, PosixFd fd, Object times, Object ns, int dirFd, boolean followSymlinks) {
            throw raise(ValueError, ErrorMessages.CANNOT_USE_FD_AND_FOLLOW_SYMLINKS_TOGETHER, "utime");
        }

        protected static boolean isDefault(int dirFd) {
            return dirFd == AT_FDCWD.value;
        }
    }

    @Builtin(name = "rename", minNumOfPositionalArgs = 2, parameterNames = {"src", "dst"}, varArgsMarker = true, keywordOnlyNames = {"src_dir_fd", "dst_dir_fd"})
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
        PNone rename(VirtualFrame frame, PosixPath src, PosixPath dst, int srcDirFd, int dstDirFd,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Bind("this") Node inliningTarget,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            auditNode.audit(inliningTarget, "os.rename", src.originalObject, dst.originalObject, dirFdForAudit(srcDirFd), dirFdForAudit(dstDirFd));
            try {
                posixLib.renameat(getPosixSupport(), srcDirFd, src.value, dstDirFd, dst.value);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e, src.originalObject, dst.originalObject);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "replace", minNumOfPositionalArgs = 2, parameterNames = {"src", "dst"}, varArgsMarker = true, keywordOnlyNames = {"src_dir_fd", "dst_dir_fd"})
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

    @Builtin(name = "access", minNumOfPositionalArgs = 2, parameterNames = {"path", "mode"}, varArgsMarker = true, keywordOnlyNames = {"dir_fd", "effective_ids", "follow_symlinks"})
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
        boolean access(PosixPath path, int mode, int dirFd, boolean effectiveIds, boolean followSymlinks,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib) {
            return posixLib.faccessat(getPosixSupport(), dirFd, path.value, mode, effectiveIds, followSymlinks);
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
        PNone fchmod(VirtualFrame frame, int fd, int mode,
                        @Bind("this") Node inliningTarget,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            auditNode.audit(inliningTarget, "os.chmod", fd, mode, -1);
            try {
                posixLib.fchmod(getPosixSupport(), fd, mode);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e, fd);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "chmod", minNumOfPositionalArgs = 2, parameterNames = {"path", "mode"}, varArgsMarker = true, keywordOnlyNames = {"dir_fd", "follow_symlinks"})
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
        PNone chmodFollow(VirtualFrame frame, PosixPath path, int mode, int dirFd, boolean followSymlinks,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Shared @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Shared @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            auditNode.audit(inliningTarget, "os.chmod", path.originalObject, mode, dirFdForAudit(dirFd));
            try {
                posixLib.fchmodat(getPosixSupport(), dirFd, path.value, mode, followSymlinks);
            } catch (PosixException e) {
                // TODO CPython checks for ENOTSUP as well
                if (e.getErrorCode() == OSErrorEnum.EOPNOTSUPP.getNumber() && !followSymlinks) {
                    if (dirFd != AT_FDCWD.value) {
                        throw raise(ValueError, ErrorMessages.CANNOT_USE_FD_AND_FOLLOW_SYMLINKS_TOGETHER, "chmod");
                    } else {
                        throw raise(NotImplementedError, ErrorMessages.UNAVAILABLE_ON_THIS_PLATFORM, "chmod", "follow_symlinks");
                    }
                }
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e, path.originalObject);
            }
            return PNone.NONE;
        }

        @Specialization
        PNone chmodFollow(VirtualFrame frame, PosixFd fd, int mode, int dirFd, @SuppressWarnings("unused") boolean followSymlinks,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Shared @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Shared @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            auditNode.audit(inliningTarget, "os.chmod", fd.originalObject, mode, dirFdForAudit(dirFd));
            // Unlike stat and utime which raise CANT_SPECIFY_DIRFD_WITHOUT_PATH or
            // CANNOT_USE_FD_AND_FOLLOW_SYMLINKS_TOGETHER when an inappropriate combination of
            // arguments is used, CPython's implementation of chmod simply ignores dir_fd and
            // follow_symlinks if a fd is specified instead of a path.
            try {
                posixLib.fchmod(getPosixSupport(), fd.fd, mode);
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
        Object chown(VirtualFrame frame, int fd, long uid, long gid,
                        @Bind("this") Node inliningTarget,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Cached GilNode gil,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            auditNode.audit(inliningTarget, "os.chown", fd, uid, gid, -1);
            try {
                gil.release(true);
                try {
                    posixLib.fchown(getPosixSupport(), fd, uid, gid);
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
        Object chown(VirtualFrame frame, PosixPath path, long uid, long gid,
                        @Bind("this") Node inliningTarget,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Cached GilNode gil,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            auditNode.audit(inliningTarget, "os.chown", path.originalObject, uid, gid, -1);
            try {
                gil.release(true);
                try {
                    posixLib.fchownat(getPosixSupport(), AT_FDCWD.value, path.value, uid, gid, false);
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
        Object chown(VirtualFrame frame, PosixPath path, long uid, long gid, int dirFd, boolean followSymlinks,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Shared @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Shared @Cached GilNode gil,
                        @Shared @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            auditNode.audit(inliningTarget, "os.chown", path.originalObject, uid, gid, dirFd != AT_FDCWD.value ? dirFd : -1);
            try {
                gil.release(true);
                try {
                    posixLib.fchownat(getPosixSupport(), dirFd, path.value, uid, gid, followSymlinks);
                } finally {
                    gil.acquire();
                }
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e, path.originalObject);
            }
            return PNone.NONE;
        }

        @Specialization
        Object chown(VirtualFrame frame, PosixFd fd, long uid, long gid, int dirFd, boolean followSymlinks,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Shared @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Shared @Cached GilNode gil,
                        @Shared @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            if (dirFd != AT_FDCWD.value) {
                throw raise(ValueError, ErrorMessages.CANT_SPECIFY_BOTH_DIR_FD_AND_FD);
            }
            if (followSymlinks) {
                throw raise(ValueError, ErrorMessages.CANNOT_USE_FD_AND_FOLLOW_SYMLINKS_TOGETHER, "chown");
            }
            auditNode.audit(inliningTarget, "os.chown", fd.originalObject, uid, gid, -1);
            try {
                gil.release(true);
                try {
                    posixLib.fchown(getPosixSupport(), fd.fd, uid, gid);
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

    @Builtin(name = "readlink", minNumOfPositionalArgs = 1, parameterNames = {"path"}, varArgsMarker = true, keywordOnlyNames = {"dir_fd"}, doc = "readlink(path, *, dir_fd=None) -> path\n" +
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
        Object readlinkAsBytes(VirtualFrame frame, PosixPath path, int dirFd,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Cached InlinedConditionProfile wasBufferLikeProfile,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PythonObjectFactory factory) {
            try {
                Object link = posixLib.readlinkat(getPosixSupport(), dirFd, path.value);
                if (wasBufferLikeProfile.profile(inliningTarget, path.wasBufferLike)) {
                    return opaquePathToBytes(link, posixLib, getPosixSupport(), factory);
                } else {
                    return posixLib.getPathAsString(getPosixSupport(), link);
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
        TruffleString getStrError(int code,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib) {
            return posixLib.strerror(getPosixSupport(), code);
        }
    }

    @Builtin(name = "_exit", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class ExitNode extends PythonUnaryBuiltinNode {
        @TruffleBoundary
        @Specialization
        Object exit(int status) {
            // TODO: use a safepoint action to throw this exception to all running threads
            throw new PythonExitException(this, status);
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
        PTuple waitpid(VirtualFrame frame, long pid, int options,
                        @Bind("this") Node inliningTarget,
                        @Cached GilNode gil,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Cached InlinedBranchProfile errorProfile,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PythonObjectFactory factory) {
            gil.release(true);
            try {
                while (true) {
                    try {
                        long[] result = posixLib.waitpid(getPosixSupport(), pid, options);
                        return factory.createTuple(new Object[]{result[0], result[1]});
                    } catch (PosixException e) {
                        errorProfile.enter(inliningTarget);
                        if (e.getErrorCode() == OSErrorEnum.EINTR.getNumber()) {
                            PythonContext.triggerAsyncActions(this);
                        } else {
                            gil.acquire();
                            throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
                        }
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
        int waitstatusToExitcode(VirtualFrame frame, Object statusObj,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Bind("this") Node inliningTarget,
                        @Cached PyLongAsIntNode longAsInt) {
            int status = longAsInt.execute(frame, inliningTarget, statusObj);
            if (posixLib.wifexited(getPosixSupport(), status)) {
                int exitcode = posixLib.wexitstatus(getPosixSupport(), status);
                if (exitcode < 0) {
                    throw raise(PythonBuiltinClassType.ValueError, ErrorMessages.INVALID_WEXITSTATUS, exitcode);
                }
                return exitcode;
            }
            if (posixLib.wifsignaled(getPosixSupport(), status)) {
                int signum = posixLib.wtermsig(getPosixSupport(), status);
                if (signum <= 0) {
                    throw raise(PythonBuiltinClassType.ValueError, ErrorMessages.INVALID_WTERMSIG, signum);
                }
                return -signum;
            }
            if (posixLib.wifstopped(getPosixSupport(), status)) {
                int signum = posixLib.wstopsig(getPosixSupport(), status);
                throw raise(PythonBuiltinClassType.ValueError, ErrorMessages.PROCESS_STOPPED_BY_DELIVERY_OF_SIGNAL, signum);
            }
            throw raise(PythonBuiltinClassType.ValueError, ErrorMessages.INVALID_WAIT_STATUS, status);
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
        boolean wcoredump(int status,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib) {
            return posixLib.wcoredump(getPosixSupport(), status);
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
        boolean wifcontinued(int status,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib) {
            return posixLib.wifcontinued(getPosixSupport(), status);
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
        boolean wifstopped(int status,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib) {
            return posixLib.wifstopped(getPosixSupport(), status);
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
        boolean wifsignaled(int status,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib) {
            return posixLib.wifsignaled(getPosixSupport(), status);
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
        boolean wifexited(int status,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib) {
            return posixLib.wifexited(getPosixSupport(), status);
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
        int wexitstatus(int status,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib) {
            return posixLib.wexitstatus(getPosixSupport(), status);
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
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib) {
            return posixLib.wtermsig(getPosixSupport(), status);
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
        int wstopsig(int status,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib) {
            return posixLib.wstopsig(getPosixSupport(), status);
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
        int system(PBytes command,
                        @Bind("this") Node inliningTarget,
                        @Cached BytesNodes.ToBytesNode toBytesNode,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Cached GilNode gil) {
            // Unlike in other posix builtins, we go through str -> bytes -> byte[] -> String
            // conversions for emulated backend because the bytes version after fsencode conversion
            // is subject to sys.audit.
            auditNode.audit(inliningTarget, "os.system", command);
            byte[] bytes = toBytesNode.execute(command);
            gil.release(true);
            try {
                Object cmdOpaque = posixLib.createPathFromBytes(getPosixSupport(), bytes);
                return posixLib.system(getPosixSupport(), cmdOpaque);
            } finally {
                gil.acquire();
            }
        }
    }

    @Builtin(name = "urandom", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"size"})
    @ArgumentClinic(name = "size", conversion = ClinicConversion.Index)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class URandomNode extends PythonUnaryClinicBuiltinNode {
        @Specialization(guards = "size >= 0")
        PBytes urandom(int size,
                        @Cached PythonObjectFactory factory) {
            byte[] bytes = new byte[size];
            nextBytes(getContext().getSecureRandom(), bytes);
            return factory.createBytes(bytes);
        }

        @Specialization(guards = "size < 0")
        Object urandomNeg(@SuppressWarnings("unused") int size) {
            throw raise(ValueError, ErrorMessages.NEG_ARG_NOT_ALLOWED);
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

    @Builtin(name = "sysconf", minNumOfPositionalArgs = 1, parameterNames = {"name"})
    @ArgumentClinic(name = "name", conversion = ClinicConversion.TString)
    @GenerateNodeFactory
    abstract static class SysconfNode extends PythonUnaryClinicBuiltinNode {

        public static final TruffleString T_SC_CLK_TCK = tsLiteral("SC_CLK_TCK");

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.SysconfNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        int sysconf(TruffleString mask,
                        @Cached TruffleString.EqualNode equalNode) {
            if (equalNode.execute(mask, T_SC_CLK_TCK, TS_ENCODING)) {
                return 100; // it's 100 on most default kernel configs. TODO: use real value through
                            // NFI
            }
            throw raise(PythonBuiltinClassType.ValueError, ErrorMessages.UNRECOGNIZED_CONF_NAME, mask);
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
        int umask(VirtualFrame frame, int mask,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                return posixLib.umask(getPosixSupport(), mask);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    @Builtin(name = "ctermid", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class CtermId extends PythonBuiltinNode {
        @Specialization
        TruffleString ctermid(VirtualFrame frame,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                return posixLib.ctermid(getPosixSupport());
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
        PNone kill(VirtualFrame frame, long pid, int signal,
                        @Bind("this") Node inliningTarget,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            auditNode.audit(inliningTarget, "kill", pid, signal);
            try {
                posixLib.kill(getPosixSupport(), pid, signal);
                return PNone.NONE;
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
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
        PNone kill(VirtualFrame frame, long pid, int signal,
                        @Bind("this") Node inliningTarget,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            auditNode.audit(inliningTarget, "killpg", pid, signal);
            try {
                posixLib.killpg(getPosixSupport(), pid, signal);
                return PNone.NONE;
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    @Builtin(name = "fspath", minNumOfPositionalArgs = 1, parameterNames = {"path"})
    @GenerateNodeFactory
    // Can be used as an equivalent of PyOS_FSPath()
    public abstract static class FspathNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doTrivial(VirtualFrame frame, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached PyOSFSPathNode fsPathNode) {
            return fsPathNode.execute(frame, inliningTarget, value);
        }
    }

    @Builtin(name = "register_at_fork", keywordOnlyNames = {"before", "after_in_child", "after_in_parent"})
    @GenerateNodeFactory
    abstract static class RegisterAtForkNode extends PythonBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        Object register(Object before, Object afterInChild, Object afterInParent) {
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
                        @Cached CastToTruffleStringNode castToStringNode,
                        @Cached(inline = false) TruffleString.SwitchEncodingNode switchEncodingNode,
                        @Cached(inline = false) TruffleString.CopyToByteArrayNode copyToByteArrayNode,
                        @Cached(inline = false) PythonObjectFactory factory) {
            TruffleString str = castToStringNode.execute(inliningTarget, strObj);
            TruffleString utf8 = switchEncodingNode.execute(str, Encoding.UTF_8);
            byte[] bytes = new byte[utf8.byteLength(Encoding.UTF_8)];
            copyToByteArrayNode.execute(utf8, 0, bytes, 0, bytes.length, Encoding.UTF_8);
            return factory.createBytes(bytes);
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
                        @Shared @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            TruffleString str = castToStringNode.execute(inliningTarget, strObj);
            return checkPath(inliningTarget, posixLib.createPathFromString(PosixSupport.get(inliningTarget), str), raiseNode);
        }

        @Specialization
        static Object doBytes(Node inliningTarget, PBytes bytes,
                        @Cached(inline = false) BytesNodes.ToBytesNode toBytesNode,
                        @Shared @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            return checkPath(inliningTarget, posixLib.createPathFromBytes(PosixSupport.get(inliningTarget), toBytesNode.execute(bytes)), raiseNode);
        }

        private static Object checkPath(Node inliningTarget, Object path, PRaiseNode.Lazy raiseNode) {
            if (path == null) {
                throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.EMBEDDED_NULL_BYTE);
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
                        @Cached PRaiseNode.Lazy raiseNode) {
            Object stringOrBytes = fspathNode.execute(frame, inliningTarget, obj);
            if (sizeNode.execute(frame, inliningTarget, obj) == 0) {
                throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.EXECV_ARG2_FIRST_ELEMENT_CANNOT_BE_EMPTY);
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
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            if (Double.isNaN(value)) {
                throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.INVALID_VALUE_NAN);
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
                throw raiseNode.get(inliningTarget).raise(OverflowError, ErrorMessages.TIMESTAMP_OUT_OF_RANGE);
            }
            timespec[offset] = (long) intPart;
            timespec[offset + 1] = (long) floatPart;
            assert 0 <= timespec[offset + 1] && timespec[offset + 1] < (long) denominator;
        }

        @Specialization
        static void doPFloat(Node inliningTarget, PFloat obj, long[] timespec, int offset,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
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
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            try {
                timespec[offset] = asLongNode.execute(frame, inliningTarget, value);
            } catch (OverflowException e) {
                throw raiseNode.get(inliningTarget).raise(OverflowError, ErrorMessages.TIMESTAMP_OUT_OF_RANGE);
            }
            timespec[offset + 1] = 0;
        }
    }

    /**
     * Equivalent of {@code split_py_long_to_s_and_ns} as used in {@code os_utime_impl}.
     */
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic({BinaryArithmetic.class, PGuards.class})
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
                        @Cached(value = "DivMod.create()", inline = false) BinaryOpNode callDivmod,
                        @Cached LenNode lenNode,
                        @Cached(value = "createNotNormalized()", inline = false) GetItemNode getItemNode,
                        @Cached PyLongAsLongNode asLongNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            Object divmod = callDivmod.executeObject(frame, value, BILLION);
            if (!PGuards.isPTuple(divmod) || lenNode.execute(inliningTarget, (PSequence) divmod) != 2) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.MUST_RETURN_2TUPLE, value, divmod);
            }
            SequenceStorage storage = ((PTuple) divmod).getSequenceStorage();
            timespec[offset] = asLongNode.execute(frame, inliningTarget, getItemNode.execute(storage, 0));
            timespec[offset + 1] = asLongNode.execute(frame, inliningTarget, getItemNode.execute(storage, 1));
        }
    }

    static int dirFdForAudit(int dirFd) {
        return dirFd == AT_FDCWD.value ? -1 : dirFd;
    }

    public static PTuple createStatResult(Node inliningTarget, PythonObjectFactory factory, InlinedConditionProfile positiveLongProfile, long[] out) {
        Object[] res = new Object[16];
        for (int i = 0; i < 7; i++) {
            res[i] = PInt.createPythonIntFromUnsignedLong(inliningTarget, factory, positiveLongProfile, out[i]);
        }
        res[6] = out[6];
        for (int i = 7; i < 10; i++) {
            long seconds = out[i];
            long nsFraction = out[i + 3];
            res[i] = seconds;
            res[i + 3] = seconds + nsFraction * 1.0e-9;
            res[i + 6] = factory.createInt(convertToNanoseconds(seconds, nsFraction));
        }
        return factory.createStructSeq(STAT_RESULT_DESC, res);
    }

    @TruffleBoundary
    private static BigInteger convertToNanoseconds(long sec, long ns) {
        // TODO it may be possible to do this in long without overflow
        BigInteger r = BigInteger.valueOf(sec);
        r = r.multiply(BigInteger.valueOf(1000000000));
        return r.add(BigInteger.valueOf(ns));
    }

    public static PBytes opaquePathToBytes(Object opaquePath, PosixSupportLibrary posixLib, Object posixSupport, PythonObjectFactory factory) {
        Buffer buf = posixLib.getPathAsBytes(posixSupport, opaquePath);
        if (buf.length > Integer.MAX_VALUE) {
            // sanity check that it is safe to cast result.length to int, to be removed once
            // we support large arrays
            throw CompilerDirectives.shouldNotReachHere("Posix path cannot fit into a Java array");
        }
        return factory.createBytes(buf.data, 0, (int) buf.length);
    }

    // ------------------
    // Converters

    public abstract static class FsConverterNode extends ArgumentCastNodeWithRaise {
        @Specialization
        static PBytes convert(VirtualFrame frame, Object value,
                        @Bind("this") Node inliningTarget,
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
    public abstract static class DirFdConversionNode extends ArgumentCastNodeWithRaise {

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
        int doFdLong(long value) {
            return longToFd(value, getRaiseNode());
        }

        @Specialization
        int doFdPInt(PInt value,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached CastToJavaLongLossyNode castToLongNode) {
            return doFdLong(castToLongNode.execute(inliningTarget, value));
        }

        @Specialization(guards = {"!isPNone(value)", "!canBeInteger(value)"})
        @SuppressWarnings("truffle-static-method")
        int doIndex(VirtualFrame frame, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached PyIndexCheckNode indexCheckNode,
                        @Cached PyNumberIndexNode indexNode,
                        @Exclusive @Cached CastToJavaLongLossyNode castToLongNode) {
            if (indexCheckNode.execute(inliningTarget, value)) {
                Object o = indexNode.execute(frame, inliningTarget, value);
                return doFdLong(castToLongNode.execute(inliningTarget, o));
            } else {
                throw raise(TypeError, ErrorMessages.ARG_SHOULD_BE_INT_OR_NONE, value);
            }
        }

        private static int longToFd(long value, PRaiseNode raiseNode) {
            if (value > Integer.MAX_VALUE) {
                throw raiseNode.raise(OverflowError, ErrorMessages.FD_IS_GREATER_THAN_MAXIMUM);
            }
            if (value < Integer.MIN_VALUE) {
                throw raiseNode.raise(OverflowError, ErrorMessages.FD_IS_LESS_THAN_MINIMUM);
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
    public abstract static class PathConversionNode extends ArgumentCastNodeWithRaiseAndIndirectCall {

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
                        @Shared @CachedLibrary(limit = "1") PosixSupportLibrary posixLib) {
            return new PosixPath(null, checkPath(posixLib.createPathFromString(getPosixSupport(), T_DOT)), false);
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
        PosixFileHandle doFdLong(long value) {
            return new PosixFd(value, DirFdConversionNode.longToFd(value, getRaiseNode()));
        }

        @Specialization(guards = "allowFd")
        PosixFileHandle doFdPInt(PInt value,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached CastToJavaLongLossyNode castToLongNode) {
            return new PosixFd(value, DirFdConversionNode.longToFd(castToLongNode.execute(inliningTarget, value), getRaiseNode()));
        }

        @Specialization
        PosixFileHandle doUnicode(TruffleString value,
                        @Shared @CachedLibrary(limit = "1") PosixSupportLibrary posixLib) {
            return new PosixPath(value, checkPath(posixLib.createPathFromString(getPosixSupport(), value)), false);
        }

        @Specialization
        @SuppressWarnings("truffle-static-method")
        PosixFileHandle doUnicode(PString value,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached CastToTruffleStringNode castToStringNode,
                        @Shared @CachedLibrary(limit = "1") PosixSupportLibrary posixLib) {
            TruffleString str = castToStringNode.execute(inliningTarget, value);
            return new PosixPath(value, checkPath(posixLib.createPathFromString(getPosixSupport(), str)), false);
        }

        @Specialization
        PosixFileHandle doBytes(PBytes value,
                        @Exclusive @Cached BytesNodes.ToBytesNode toByteArrayNode,
                        @Shared @CachedLibrary(limit = "1") PosixSupportLibrary posixLib) {
            return new PosixPath(value, checkPath(posixLib.createPathFromBytes(getPosixSupport(), toByteArrayNode.execute(value))), true);
        }

        @Specialization(guards = {"!isHandled(value)", "bufferAcquireLib.hasBuffer(value)"}, limit = "3")
        PosixFileHandle doBuffer(VirtualFrame frame, Object value,
                        @CachedLibrary("value") PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                        @Shared @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Cached WarningsModuleBuiltins.WarnNode warningNode) {
            Object buffer = bufferAcquireLib.acquireReadonly(value, frame, getContext(), getLanguage(), this);
            try {
                warningNode.warnFormat(frame, null, PythonBuiltinClassType.DeprecationWarning, 1,
                                ErrorMessages.S_S_SHOULD_BE_S_NOT_P, functionNameWithColon, argumentName, getAllowedTypes(), value);
                return new PosixPath(value, checkPath(posixLib.createPathFromBytes(getPosixSupport(), bufferLib.getCopiedByteArray(value))), true);
            } finally {
                bufferLib.release(buffer, frame, getContext(), getLanguage(), this);
            }
        }

        @Specialization(guards = {"!isHandled(value)", "!bufferAcquireLib.hasBuffer(value)", "allowFd", "indexCheckNode.execute(this, value)"}, limit = "1")
        @SuppressWarnings("truffle-static-method")
        PosixFileHandle doIndex(VirtualFrame frame, Object value,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared("bufferAcquireLib") @CachedLibrary(limit = "3") PythonBufferAcquireLibrary bufferAcquireLib,
                        @SuppressWarnings("unused") @Exclusive @Cached PyIndexCheckNode indexCheckNode,
                        @Cached PyNumberIndexNode indexNode,
                        @Exclusive @Cached CastToJavaLongLossyNode castToLongNode) {
            Object o = indexNode.execute(frame, inliningTarget, value);
            return new PosixFd(value, DirFdConversionNode.longToFd(castToLongNode.execute(inliningTarget, o), getRaiseNode()));
        }

        @Specialization(guards = {"!isHandled(value)", "!bufferAcquireLib.hasBuffer(value)", "!allowFd || !indexCheckNode.execute(this, value)"}, limit = "1")
        @SuppressWarnings("truffle-static-method")
        PosixFileHandle doGeneric(VirtualFrame frame, Object value,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared("bufferAcquireLib") @CachedLibrary(limit = "3") PythonBufferAcquireLibrary bufferAcquireLib,
                        @SuppressWarnings("unused") @Exclusive @Cached PyIndexCheckNode indexCheckNode,
                        @Cached("create(T___FSPATH__)") LookupAndCallUnaryNode callFSPath,
                        @Exclusive @Cached BytesNodes.ToBytesNode toByteArrayNode,
                        @Exclusive @Cached CastToTruffleStringNode castToStringNode,
                        @Shared @CachedLibrary(limit = "1") PosixSupportLibrary posixLib) {
            Object pathObject = callFSPath.executeObject(frame, value);
            if (pathObject == PNone.NO_VALUE) {
                throw raise(TypeError, ErrorMessages.S_S_SHOULD_BE_S_NOT_P, functionNameWithColon, argumentName,
                                getAllowedTypes(), value);
            }
            // 'pathObject' replaces 'value' as the PosixPath.originalObject for auditing purposes
            // by design
            if (pathObject instanceof PBytes) {
                return doBytes((PBytes) pathObject, toByteArrayNode, posixLib);
            }
            if (pathObject instanceof PString) {
                return doUnicode((PString) pathObject, inliningTarget, castToStringNode, posixLib);
            }
            if (pathObject instanceof TruffleString) {
                return doUnicode((TruffleString) pathObject, posixLib);
            }
            throw raise(TypeError, ErrorMessages.EXPECTED_FSPATH_TO_RETURN_STR_OR_BYTES, value, pathObject);
        }

        protected boolean isHandled(Object value) {
            return PGuards.isPNone(value) && nullable || PGuards.canBeInteger(value) && allowFd || PGuards.isString(value) || PGuards.isPBytes(value);
        }

        private String getAllowedTypes() {
            return allowFd && nullable ? "string, bytes, os.PathLike, integer or None"
                            : allowFd ? "string, bytes, os.PathLike or integer" : nullable ? "string, bytes, os.PathLike or None" : "string, bytes or os.PathLike";
        }

        private Object checkPath(Object path) {
            if (path == null) {
                throw raise(ValueError, ErrorMessages.S_EMBEDDED_NULL_CHARACTER_IN_S, functionNameWithColon, argumentName);
            }
            return path;
        }

        protected final Object getPosixSupport() {
            return PythonContext.get(this).getPosixSupport();
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
    public abstract static class OffsetConversionNode extends ArgumentCastNodeWithRaise {

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
                        @Bind("this") Node inliningTarget,
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
                        @Bind("this") Node inliningTarget,
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
    public abstract static class PidtConversionNode extends ArgumentCastNodeWithRaise {

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
                        @Bind("this") Node inliningTarget,
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
    public abstract static class AbstractIdConversionNode extends ArgumentCastNodeWithRaise {

        private static final long MAX_UINT32 = (1L << 32) - 1;

        public abstract long executeLong(VirtualFrame frame, Object value);

        @Specialization
        long doInt(int value) {
            return checkValue(value);
        }

        @Specialization
        long doLong(long value) {
            return checkValue(value);
        }

        @Specialization(guards = "!isInteger(value)")
        @SuppressWarnings("truffle-static-method")
        long doGeneric(VirtualFrame frame, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached PyNumberIndexNode pyNumberIndexNode,
                        @Cached PyLongAsLongNode asLongNode) {
            Object index;
            try {
                index = pyNumberIndexNode.execute(frame, inliningTarget, value);
            } catch (PException ex) {
                throw raise(TypeError, ErrorMessages.S_SHOULD_BE_INTEGER_NOT_P, getIdName(), value);
            }
            /*
             * We have no means to distinguish overflow/underflow, so we just let any OverflowError
             * from asLongNode fall through. It will not have the same message as CPython, but still
             * correct type.
             */
            return checkValue(asLongNode.execute(frame, inliningTarget, index));
        }

        private long checkValue(long value) {
            // Note that -1 is intentionally allowed
            if (value < -1) {
                throw raise(OverflowError, ErrorMessages.S_IS_LESS_THAN_MINIMUM, getIdName());
            } else if (value > MAX_UINT32) {
                /* uid_t is uint32_t on Linux */
                throw raise(OverflowError, ErrorMessages.S_IS_GREATER_THAN_MAXIUMUM, getIdName());
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
