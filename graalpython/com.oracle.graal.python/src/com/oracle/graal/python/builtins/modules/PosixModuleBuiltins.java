/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.nodes.SpecialMethodNames.__FSPATH__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.NotImplementedError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
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
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltins.AuditNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.BytesUtils;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes.LenNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetItemNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.ToArrayNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.posix.PScandirIterator;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.tuple.StructSequence;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithRaise;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentCastNode.ArgumentCastNodeWithRaise;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CastToJavaLongLossyNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PosixSupportLibrary.Buffer;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonExitException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(defineModule = "posix")
public class PosixModuleBuiltins extends PythonBuiltins {
    private static final int TMPFILE = 4259840;
    private static final int TEMPORARY = 4259840;
    private static final int SYNC = 1052672;
    private static final int RSYNC = 1052672;
    private static final int CLOEXEC = PosixSupportLibrary.O_CLOEXEC;
    private static final int DIRECT = 16384;
    private static final int DSYNC = 4096;
    private static final int NDELAY = 2048;
    private static final int NONBLOCK = 2048;
    private static final int APPEND = PosixSupportLibrary.O_APPEND;
    private static final int TRUNC = PosixSupportLibrary.O_TRUNC;
    private static final int EXCL = PosixSupportLibrary.O_EXCL;
    private static final int CREAT = PosixSupportLibrary.O_CREAT;
    private static final int RDWR = PosixSupportLibrary.O_RDWR;
    private static final int WRONLY = PosixSupportLibrary.O_WRONLY;
    private static final int RDONLY = PosixSupportLibrary.O_RDONLY;

    // TODO map Python's SEEK_SET, SEEK_CUR, SEEK_END values to the underlying OS values if they are
    // different
    private static final int SEEK_DATA = 3;
    private static final int SEEK_HOLE = 4;

    private static final int WNOHANG = 1;
    private static final int WUNTRACED = 3;

    private static final int F_OK = 0;
    private static final int X_OK = 1;
    private static final int W_OK = 2;
    private static final int R_OK = 4;

    static final StructSequence.Descriptor STAT_RESULT_DESC = new StructSequence.Descriptor(
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

    private static final StructSequence.Descriptor TERMINAL_SIZE_DESC = new StructSequence.Descriptor(
                    PythonBuiltinClassType.PTerminalSize,
                    "A tuple of (columns, lines) for holding terminal window size",
                    2,
                    new String[]{"columns", "lines"},
                    new String[]{"width of the terminal window in characters", "height of the terminal window in characters"});

    private static final StructSequence.Descriptor UNAME_RESULT_DESC = new StructSequence.Descriptor(
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
        builtinConstants.put("O_RDONLY", RDONLY);
        builtinConstants.put("O_WRONLY", WRONLY);
        builtinConstants.put("O_RDWR", RDWR);
        builtinConstants.put("O_CREAT", CREAT);
        builtinConstants.put("O_EXCL", EXCL);
        builtinConstants.put("O_TRUNC", TRUNC);
        builtinConstants.put("O_APPEND", APPEND);
        builtinConstants.put("O_NONBLOCK", NONBLOCK);
        builtinConstants.put("O_NDELAY", NDELAY);
        builtinConstants.put("O_DSYNC", DSYNC);
        builtinConstants.put("O_DIRECT", DIRECT);
        builtinConstants.put("O_CLOEXEC", CLOEXEC);
        builtinConstants.put("O_RSYNC", RSYNC);
        builtinConstants.put("O_SYNC", SYNC);
        builtinConstants.put("O_TEMPORARY", TEMPORARY);
        builtinConstants.put("O_TMPFILE", TMPFILE);

        builtinConstants.put("SEEK_DATA", SEEK_DATA);
        builtinConstants.put("SEEK_HOLE", SEEK_HOLE);

        builtinConstants.put("WNOHANG", WNOHANG);
        builtinConstants.put("WUNTRACED", WUNTRACED);

        builtinConstants.put("F_OK", F_OK);
        builtinConstants.put("X_OK", X_OK);
        builtinConstants.put("W_OK", W_OK);
        builtinConstants.put("R_OK", R_OK);
    }

    @Override
    public void initialize(PythonCore core) {
        super.initialize(core);
        builtinConstants.put("_have_functions", core.factory().createList());
        builtinConstants.put("environ", core.factory().createDict());

        StructSequence.initType(core, STAT_RESULT_DESC);
        StructSequence.initType(core, TERMINAL_SIZE_DESC);
        StructSequence.initType(core, UNAME_RESULT_DESC);

        // The stat_result and terminal_size classes are formally part of the 'os' module, although
        // they are exposed by the 'posix' module. In CPython, they are defined in posixmodule.c,
        // with their __module__ being set to 'os', and later they are imported by os.py.
        // Our infrastructure in PythonBuiltinClassType currently does not allow us to
        // define a class in one module (os) and make it public in another (posix), so we create
        // them directly in the 'os' module, and expose them in the `posix` module as well.
        // Note that the classes are still re-imported by os.py.
        PythonModule posix = core.lookupBuiltinModule("posix");
        posix.setAttribute(PythonBuiltinClassType.PStatResult.getName(), core.lookupType(PythonBuiltinClassType.PStatResult));
        posix.setAttribute(PythonBuiltinClassType.PTerminalSize.getName(), core.lookupType(PythonBuiltinClassType.PTerminalSize));

        posix.setAttribute("error", core.lookupType(PythonBuiltinClassType.OSError));
    }

    @Override
    public void postInitialize(PythonCore core) {
        super.postInitialize(core);

        // fill the environ dictionary with the current environment
        Map<String, String> getenv = System.getenv();
        PDict environ = core.factory().createDict();
        String pyenvLauncherKey = "__PYVENV_LAUNCHER__";
        for (Entry<String, String> entry : getenv.entrySet()) {
            String value;
            if (pyenvLauncherKey.equals(entry.getKey())) {
                // On Mac, the CPython launcher uses this env variable to specify the real Python
                // executable. It will be honored by packages like "site". So, if it is set, we
                // overwrite it with our executable to ensure that subprocesses will use us.
                value = core.getContext().getOption(PythonOptions.Executable);

                try {
                    PosixSupportLibrary posixLib = PosixSupportLibrary.getUncached();
                    Object posixSupport = core.getContext().getPosixSupport();
                    Object k = posixLib.createPathFromString(posixSupport, pyenvLauncherKey);
                    Object v = posixLib.createPathFromString(posixSupport, value);
                    posixLib.setenv(posixSupport, k, v, true);
                } catch (PosixException e) {
                    // TODO handle error
                }
            } else {
                value = entry.getValue();
            }
            environ.setItem(core.factory().createBytes(entry.getKey().getBytes()), core.factory().createBytes(value.getBytes()));
        }
        PythonModule posix = core.lookupBuiltinModule("posix");
        Object environAttr = posix.getAttribute("environ");
        ((PDict) environAttr).setDictStorage(environ.getDictStorage());
    }

    @Builtin(name = "stat_result", minNumOfPositionalArgs = 1, parameterNames = {"$cls", "sequence", "dict"}, constructsClass = PythonBuiltinClassType.PStatResult)
    @ImportStatic(PosixModuleBuiltins.class)
    @GenerateNodeFactory
    public abstract static class StatResultNode extends PythonTernaryBuiltinNode {

        @Specialization
        public PTuple generic(VirtualFrame frame, Object cls, Object sequence, Object dict,
                        @Cached("create(STAT_RESULT_DESC)") StructSequence.NewNode newNode) {
            PTuple p = (PTuple) newNode.call(frame, cls, sequence, dict);
            Object[] data = CompilerDirectives.castExact(p.getSequenceStorage(), ObjectSequenceStorage.class).getInternalArray();
            for (int i = 7; i <= 9; i++) {
                if (data[i + 3] == PNone.NONE) {
                    data[i + 3] = data[i];
                }
            }
            return p;
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
                        @Cached BytesNodes.ToBytesNode toBytesNode,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            // Unlike in other posix builtins, we go through str -> bytes -> byte[] -> String
            // conversions for emulated backend because the bytes version after fsencode conversion
            // is subject to sys.audit.
            byte[] name = toBytesNode.execute(nameBytes);
            byte[] value = toBytesNode.execute(valueBytes);
            Object nameOpaque = checkNull(posixLib.createPathFromBytes(getPosixSupport(), name));
            Object valueOpaque = checkNull(posixLib.createPathFromBytes(getPosixSupport(), value));
            checkEqualSign(name);
            auditNode.audit("os.putenv", nameBytes, valueBytes);
            try {
                posixLib.setenv(getPosixSupport(), nameOpaque, valueOpaque, true);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
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

    @Builtin(name = "execv", minNumOfPositionalArgs = 2, parameterNames = {"pathname", "argv"})
    @ArgumentClinic(name = "pathname", conversionClass = PathConversionNode.class, args = {"false", "false"})
    @GenerateNodeFactory
    public abstract static class ExecvNode extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.ExecvNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        Object execvArgsList(VirtualFrame frame, PosixPath path, PList argv,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached ToArrayNode toArrayNode,
                        @Cached ObjectToOpaquePathNode toOpaquePathNode,
                        @Cached SysModuleBuiltins.AuditNode auditNode) {
            execv(frame, path, argv, argv.getSequenceStorage(), posixLib, toArrayNode, toOpaquePathNode, auditNode);
            throw CompilerDirectives.shouldNotReachHere("execv should not return normally");
        }

        @Specialization
        Object execvArgsTuple(VirtualFrame frame, PosixPath path, PTuple argv,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached ToArrayNode toArrayNode,
                        @Cached ObjectToOpaquePathNode toOpaquePathNode,
                        @Cached AuditNode auditNode) {
            execv(frame, path, argv, argv.getSequenceStorage(), posixLib, toArrayNode, toOpaquePathNode, auditNode);
            throw CompilerDirectives.shouldNotReachHere("execv should not return normally");
        }

        @Specialization(guards = {"!isList(argv)", "!isTuple(argv)"})
        @SuppressWarnings("unused")
        Object execvInvalidArgs(VirtualFrame frame, PosixPath path, Object argv) {
            throw raise(TypeError, ErrorMessages.ARG_D_MUST_BE_S, "execv()", 2, "tuple or list");
        }

        private void execv(VirtualFrame frame, PosixPath path, Object argv, SequenceStorage argvStorage,
                        PosixSupportLibrary posixLib,
                        SequenceStorageNodes.ToArrayNode toArrayNode,
                        ObjectToOpaquePathNode toOpaquePathNode,
                        SysModuleBuiltins.AuditNode auditNode) {
            Object[] args = toArrayNode.execute(argvStorage);
            if (args.length < 1) {
                throw raise(ValueError, ErrorMessages.ARG_MUST_NOT_BE_EMPTY, "execv()", 2);
            }
            Object[] opaqueArgs = new Object[args.length];
            for (int i = 0; i < args.length; ++i) {
                opaqueArgs[i] = toOpaquePathNode.execute(frame, args[i]);
            }
            //TODO ValueError "execv() arg 2 first element cannot be empty"

            auditNode.audit("os.exec", path.originalObject, argv, PNone.NONE);

            try {
                posixLib.execv(getPosixSupport(), path.value, opaqueArgs);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
            throw CompilerDirectives.shouldNotReachHere("execv should not return normally");
        }
    }

    @Builtin(name = "getpid", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class GetPidNode extends PythonBuiltinNode {
        @Specialization
        long getPid(@CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            return posixLib.getpid(getPosixSupport());
        }
    }

    @Builtin(name = "getuid", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class GetUidNode extends PythonBuiltinNode {
        @Specialization
        long getUid(@CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            return posixLib.getuid(getPosixSupport());
        }
    }

    @Builtin(name = "getppid", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class GetPpidNode extends PythonBuiltinNode {
        @Specialization
        long getPpid(@CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            return posixLib.getppid(getPosixSupport());
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
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            try {
                return posixLib.getsid(getPosixSupport(), pid);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
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
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Cached BranchProfile errorProfile) {
            int fixedFlags = flags | CLOEXEC;
            auditNode.audit("open", path.originalObject, PNone.NONE, fixedFlags);
            while (true) {
                try {
                    return posixLib.openat(getPosixSupport(), dirFd, path.value, fixedFlags, mode);
                } catch (PosixException e) {
                    errorProfile.enter();
                    if (e.getErrorCode() == OSErrorEnum.EINTR.getNumber()) {
                        getContext().triggerAsyncActions(frame);
                    } else {
                        throw raiseOSErrorFromPosixException(frame, e, path.originalObject);
                    }
                }
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
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            try {
                posixLib.close(getPosixSupport(), fd);
                return PNone.NONE;
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
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
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached BranchProfile errorProfile) {
            try {
                return read(frame, fd, length, posixLib, errorProfile);
            } catch (PosixException e) {
                errorProfile.enter();
                throw raiseOSErrorFromPosixException(frame, e);
            }
        }

        public PBytes read(VirtualFrame frame, int fd, int length,
                        PosixSupportLibrary posixLib,
                        BranchProfile errorProfile) throws PosixException {
            if (length < 0) {
                int error = OSErrorEnum.EINVAL.getNumber();
                throw raiseOSError(frame, error, posixLib.strerror(getPosixSupport(), error));
            }
            while (true) {
                try {
                    Buffer result = posixLib.read(getPosixSupport(), fd, length);
                    if (result.length > Integer.MAX_VALUE) {
                        // sanity check that it is safe to cast result.length to int, to be removed
                        // once we support large arrays
                        throw CompilerDirectives.shouldNotReachHere("Posix read() returned more bytes than requested");
                    }
                    return factory().createBytes(result.data, 0, (int) result.length);
                } catch (PosixException e) {
                    errorProfile.enter();
                    if (e.getErrorCode() == OSErrorEnum.EINTR.getNumber()) {
                        getContext().triggerAsyncActions(frame);
                    } else {
                        throw e;
                    }
                }
            }
        }
    }

    @Builtin(name = "write", minNumOfPositionalArgs = 2, parameterNames = {"fd", "data"})
    @ArgumentClinic(name = "fd", conversion = ClinicConversion.Int)
    @ArgumentClinic(name = "data", conversion = ClinicConversion.Buffer)
    @GenerateNodeFactory
    public abstract static class WriteNode extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.WriteNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        long doWrite(VirtualFrame frame, int fd, byte[] data,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached BranchProfile errorProfile) {
            try {
                return write(frame, fd, data, posixLib, errorProfile);
            } catch (PosixException e) {
                errorProfile.enter();
                throw raiseOSErrorFromPosixException(frame, e);
            }
        }

        public long write(VirtualFrame frame, int fd, byte[] data,
                        PosixSupportLibrary posixLib,
                        BranchProfile errorProfile) throws PosixException {
            while (true) {
                try {
                    return posixLib.write(getPosixSupport(), fd, Buffer.wrap(data));
                } catch (PosixException e) {
                    errorProfile.enter();
                    if (e.getErrorCode() == OSErrorEnum.EINTR.getNumber()) {
                        getContext().triggerAsyncActions(frame);
                    } else {
                        throw e;
                    }
                }
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
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            try {
                return posixLib.dup(getPosixSupport(), fd);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
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
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            if (fd < 0 || fd2 < 0) {
                // CPython does not set errno here and raises a 'random' OSError
                // (possibly with errno=0 Success)
                int error = OSErrorEnum.EINVAL.getNumber();
                throw raiseOSError(frame, error, posixLib.strerror(getPosixSupport(), error));
            }

            try {
                return posixLib.dup2(getPosixSupport(), fd, fd2, inheritable);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
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
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            try {
                return posixLib.getInheritable(getPosixSupport(), fd);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
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
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            try {
                // not sure why inheritable is not a boolean, but that is how they do it in CPython
                posixLib.setInheritable(getPosixSupport(), fd, inheritable != 0);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "pipe", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class PipeNode extends PythonBuiltinNode {

        @Specialization
        PTuple pipe(VirtualFrame frame,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            int[] pipe;
            try {
                pipe = posixLib.pipe(getPosixSupport());
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
            return factory().createTuple(new Object[]{pipe[0], pipe[1]});
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
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached BranchProfile errorProfile) {
            try {
                return posixLib.lseek(getPosixSupport(), fd, pos, how);
            } catch (PosixException e) {
                errorProfile.enter();
                throw raiseOSErrorFromPosixException(frame, e);
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
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Cached BranchProfile errorProfile) {
            auditNode.audit("os.truncate", fd, length);
            while (true) {
                try {
                    posixLib.ftruncate(getPosixSupport(), fd, length);
                    return PNone.NONE;
                } catch (PosixException e) {
                    errorProfile.enter();
                    if (e.getErrorCode() == OSErrorEnum.EINTR.getNumber()) {
                        getContext().triggerAsyncActions(frame);
                    } else {
                        throw raiseOSErrorFromPosixException(frame, e);
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
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached BranchProfile errorProfile) {
            while (true) {
                try {
                    posixLib.fsync(getPosixSupport(), fd);
                    return PNone.NONE;
                } catch (PosixException e) {
                    errorProfile.enter();
                    if (e.getErrorCode() == OSErrorEnum.EINTR.getNumber()) {
                        getContext().triggerAsyncActions(frame);
                    } else {
                        throw raiseOSErrorFromPosixException(frame, e);
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
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            try {
                return posixLib.getBlocking(getPosixSupport(), fd);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
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
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            try {
                posixLib.setBlocking(getPosixSupport(), fd, blocking);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
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
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            // TODO default value should be fileno(stdout)
            try {
                int[] result = posixLib.getTerminalSize(getPosixSupport(), fd);
                return factory().createStructSeq(TERMINAL_SIZE_DESC, result[0], result[1]);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
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
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached @Shared("positive") ConditionProfile positiveLongProfile) {
            try {
                long[] out = posixLib.fstatat(getPosixSupport(), dirFd, path.value, followSymlinks);
                return createStatResult(factory(), positiveLongProfile, out);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e, path.originalObject);
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
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached @Shared("positive") ConditionProfile positiveLongProfile) {
            try {
                long[] out = posixLib.fstat(getPosixSupport(), fd.fd);
                return createStatResult(factory(), positiveLongProfile, out);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e, fd.originalObject);
            }
        }

        protected static boolean isDefault(int dirFd) {
            return dirFd == PosixSupportLibrary.DEFAULT_DIR_FD;
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
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached ConditionProfile positiveLongProfile) {
            try {
                // TODO we used to return all zeros when the filename was equal to sys.executable
                long[] out = posixLib.fstatat(getPosixSupport(), dirFd, path.value, false);
                return createStatResult(factory(), positiveLongProfile, out);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e, path.originalObject);
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
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached ConditionProfile positiveLongProfile,
                        @Cached BranchProfile errorProfile) {
            while (true) {
                try {
                    long[] out = posixLib.fstat(getPosixSupport(), fd);
                    return createStatResult(factory(), positiveLongProfile, out);
                } catch (PosixException e) {
                    errorProfile.enter();
                    if (e.getErrorCode() == OSErrorEnum.EINTR.getNumber()) {
                        getContext().triggerAsyncActions(frame);
                    } else {
                        throw raiseOSErrorFromPosixException(frame, e);
                    }
                }
            }
        }
    }

    @Builtin(name = "uname", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class UnameNode extends PythonBuiltinNode {

        @Specialization
        PTuple uname(VirtualFrame frame,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            try {
                return factory().createStructSeq(UNAME_RESULT_DESC, posixLib.uname(getPosixSupport()));
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
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
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached SysModuleBuiltins.AuditNode auditNode) {
            auditNode.audit("os.remove", path.originalObject, dirFdForAudit(dirFd));
            try {
                posixLib.unlinkat(getPosixSupport(), dirFd, path.value, false);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e, path.originalObject);
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
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            try {
                posixLib.symlinkat(getPosixSupport(), src.value, dirFd, dst.value);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e, src.originalObject, dst.originalObject);
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
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached SysModuleBuiltins.AuditNode auditNode) {
            auditNode.audit("os.mkdir", path.originalObject, mode, dirFdForAudit(dirFd));
            try {
                posixLib.mkdirat(getPosixSupport(), dirFd, path.value, mode);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e, path.originalObject);
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
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached SysModuleBuiltins.AuditNode auditNode) {
            auditNode.audit("os.rmdir", path.originalObject, dirFdForAudit(dirFd));
            try {
                posixLib.unlinkat(getPosixSupport(), dirFd, path.value, true);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e, path.originalObject);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "getcwd", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class GetcwdNode extends PythonBuiltinNode {
        @Specialization
        String getcwd(VirtualFrame frame,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            try {
                return posixLib.getPathAsString(getPosixSupport(), posixLib.getcwd(getPosixSupport()));
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    @Builtin(name = "getcwdb", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class GetcwdbNode extends PythonBuiltinNode {
        @Specialization
        PBytes getcwdb(VirtualFrame frame,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            try {
                return opaquePathToBytes(posixLib.getcwd(getPosixSupport()), posixLib, getPosixSupport(), factory());
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
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
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            try {
                posixLib.chdir(getPosixSupport(), path.value);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e, path.originalObject);
            }
            return PNone.NONE;
        }

        @Specialization
        PNone chdirFd(VirtualFrame frame, PosixFd fd,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            try {
                posixLib.fchdir(getPosixSupport(), fd.fd);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e, fd.originalObject);
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
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached BranchProfile errorProfile) {
            while (true) {
                try {
                    posixLib.fchdir(getPosixSupport(), fd);
                    return PNone.NONE;
                } catch (PosixException e) {
                    errorProfile.enter();
                    if (e.getErrorCode() == OSErrorEnum.EINTR.getNumber()) {
                        getContext().triggerAsyncActions(frame);
                    } else {
                        throw raiseOSErrorFromPosixException(frame, e);
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
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            return posixLib.isatty(getPosixSupport(), fd);
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
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached SysModuleBuiltins.AuditNode auditNode) {
            auditNode.audit("os.scandir", path.originalObject == null ? PNone.NONE : path.originalObject);
            try {
                return factory().createScandirIterator(getContext(), posixLib.opendir(getPosixSupport(), path.value), path);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e, path.originalObject);
            }
        }

        @Specialization
        PScandirIterator scandirFd(VirtualFrame frame, PosixFd fd,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached SysModuleBuiltins.AuditNode auditNode) {
            auditNode.audit("os.scandir", fd.originalObject);
            try {
                return factory().createScandirIterator(getContext(), posixLib.fdopendir(getPosixSupport(), fd.fd), fd);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e, fd.originalObject);
            }
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
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached SysModuleBuiltins.AuditNode auditNode) {
            auditNode.audit("os.listdir", path.originalObject == null ? PNone.NONE : path.originalObject);
            try {
                return listdir(frame, posixLib.opendir(getPosixSupport(), path.value), path.wasBufferLike, posixLib);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e, path.originalObject);
            }
        }

        @Specialization
        PList listdirFd(VirtualFrame frame, PosixFd fd,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached SysModuleBuiltins.AuditNode auditNode) {
            auditNode.audit("os.listdir", fd.originalObject);
            try {
                return listdir(frame, posixLib.fdopendir(getPosixSupport(), fd.fd), false, posixLib);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e, fd.originalObject);
            }
        }

        private PList listdir(VirtualFrame frame, Object dirStream, boolean produceBytes, PosixSupportLibrary posixLib) {
            List<Object> list = new ArrayList<>();
            try {
                while (true) {
                    Object dirEntry = posixLib.readdir(getPosixSupport(), dirStream);
                    if (dirEntry == null) {
                        return factory().createList(listToArray(list));
                    }
                    Object name = posixLib.dirEntryGetName(getPosixSupport(), dirEntry);
                    if (produceBytes) {
                        addToList(list, opaquePathToBytes(name, posixLib, getPosixSupport(), factory()));
                    } else {
                        addToList(list, posixLib.getPathAsString(getPosixSupport(), name));
                    }
                }
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            } finally {
                posixLib.closedir(getPosixSupport(), dirStream);
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

    @Builtin(name = "utime", minNumOfPositionalArgs = 1, parameterNames = {"path", "times"}, varArgsMarker = true, keywordOnlyNames = {"ns", "dir_fd", "follow_symlinks"})
    @ArgumentClinic(name = "path", conversionClass = PathConversionNode.class, args = {"false", "true"})
    @ArgumentClinic(name = "dir_fd", conversionClass = DirFdConversionNode.class)
    @ArgumentClinic(name = "follow_symlinks", conversion = ClinicConversion.Boolean, defaultValue = "true")
    @GenerateNodeFactory
    abstract static class UtimeNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.UtimeNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = {"isNoValue(ns)"})
        @SuppressWarnings("unused")
        PNone pathNow(VirtualFrame frame, PosixPath path, PNone times, PNone ns, int dirFd, boolean followSymlinks,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            auditNode.audit("os.utime", path.originalObject, PNone.NONE, PNone.NONE, dirFdForAudit(dirFd));
            callUtimeNsAt(frame, path, null, dirFd, followSymlinks, posixLib);
            return PNone.NONE;
        }

        @Specialization(guards = {"isNoValue(ns)", "isDefault(dirFd)", "followSymlinks"})
        @SuppressWarnings("unused")
        PNone fdNow(VirtualFrame frame, PosixFd fd, PNone times, PNone ns, int dirFd, boolean followSymlinks,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            auditNode.audit("os.utime", fd.originalObject, PNone.NONE, PNone.NONE, dirFdForAudit(dirFd));
            callFutimeNs(frame, fd, null, posixLib);
            return PNone.NONE;
        }

        @Specialization(guards = {"isNoValue(ns)"})
        PNone pathTimes(VirtualFrame frame, PosixPath path, PTuple times, @SuppressWarnings("unused") PNone ns, int dirFd, boolean followSymlinks,
                        @Cached LenNode lenNode,
                        @Cached("createNotNormalized()") GetItemNode getItemNode,
                        @Cached ObjectToTimespecNode objectToTimespecNode,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            long[] timespec = convertToTimespec(frame, times, lenNode, getItemNode, objectToTimespecNode);
            auditNode.audit("os.utime", path.originalObject, times, PNone.NONE, dirFdForAudit(dirFd));
            callUtimeNsAt(frame, path, timespec, dirFd, followSymlinks, posixLib);
            return PNone.NONE;
        }

        @Specialization(guards = {"isNoValue(ns)", "isDefault(dirFd)", "followSymlinks"})
        @SuppressWarnings("unused")
        PNone fdTimes(VirtualFrame frame, PosixFd fd, PTuple times, PNone ns, int dirFd, boolean followSymlinks,
                        @Cached LenNode lenNode,
                        @Cached("createNotNormalized()") GetItemNode getItemNode,
                        @Cached ObjectToTimespecNode objectToTimespecNode,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            long[] timespec = convertToTimespec(frame, times, lenNode, getItemNode, objectToTimespecNode);
            auditNode.audit("os.utime", fd.originalObject, times, PNone.NONE, dirFdForAudit(dirFd));
            callFutimeNs(frame, fd, timespec, posixLib);
            return PNone.NONE;
        }

        @Specialization
        PNone pathNs(VirtualFrame frame, PosixPath path, @SuppressWarnings("unused") PNone times, PTuple ns, int dirFd, boolean followSymlinks,
                        @Cached LenNode lenNode,
                        @Cached("createNotNormalized()") GetItemNode getItemNode,
                        @Cached SplitLongToSAndNsNode splitLongToSAndNsNode,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            long[] timespec = convertToTimespec(frame, ns, lenNode, getItemNode, splitLongToSAndNsNode);
            auditNode.audit("os.utime", path.originalObject, PNone.NONE, ns, dirFdForAudit(dirFd));
            callUtimeNsAt(frame, path, timespec, dirFd, followSymlinks, posixLib);
            return PNone.NONE;
        }

        @Specialization(guards = {"isDefault(dirFd)", "followSymlinks"})
        @SuppressWarnings("unused")
        PNone fdNs(VirtualFrame frame, PosixFd fd, PNone times, PTuple ns, int dirFd, boolean followSymlinks,
                        @Cached LenNode lenNode,
                        @Cached("createNotNormalized()") GetItemNode getItemNode,
                        @Cached SplitLongToSAndNsNode splitLongToSAndNsNode,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            long[] timespec = convertToTimespec(frame, ns, lenNode, getItemNode, splitLongToSAndNsNode);
            auditNode.audit("os.utime", fd.originalObject, PNone.NONE, ns, dirFdForAudit(dirFd));
            callFutimeNs(frame, fd, timespec, posixLib);
            return PNone.NONE;
        }

        @Specialization(guards = {"!isPNone(times)", "!isNoValue(ns)"})
        @SuppressWarnings("unused")
        PNone bothSpecified(VirtualFrame frame, Object path, Object times, Object ns, int dirFd, boolean followSymlinks) {
            throw raise(ValueError, ErrorMessages.YOU_MAY_SPECIFY_EITHER_OR_BUT_NOT_BOTH, "utime", "times", "ns");
        }

        @Specialization(guards = {"!isPNone(times)", "!isPTuple(times)", "isNoValue(ns)"})
        @SuppressWarnings("unused")
        PNone timesNotATuple(VirtualFrame frame, Object path, Object times, PNone ns, int dirFd, boolean followSymlinks) {
            throw timesTupleError();
        }

        @Specialization(guards = {"!isNoValue(ns)", "!isPTuple(ns)"})
        @SuppressWarnings("unused")
        PNone nsNotATuple(VirtualFrame frame, Object path, PNone times, Object ns, int dirFd, boolean followSymlinks) {
            // ns can actually also contain objects implementing __divmod__, but CPython produces
            // this error message
            throw raise(TypeError, ErrorMessages.MUST_BE, "utime", "ns", "a tuple of two ints");
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

        private PException timesTupleError() {
            // times can actually also contain floats, but CPython produces this error message
            throw raise(TypeError, ErrorMessages.MUST_BE_EITHER_OR, "utime", "times", "a tuple of two ints", "None");
        }

        private long[] convertToTimespec(VirtualFrame frame, PTuple times, LenNode lenNode, GetItemNode getItemNode, ConvertToTimespecBaseNode convertToTimespecBaseNode) {
            if (lenNode.execute(times) != 2) {
                throw timesTupleError();
            }
            long[] timespec = new long[4];
            convertToTimespecBaseNode.execute(frame, getItemNode.execute(frame, times.getSequenceStorage(), 0), timespec, 0);
            convertToTimespecBaseNode.execute(frame, getItemNode.execute(frame, times.getSequenceStorage(), 1), timespec, 2);
            return timespec;
        }

        private void callUtimeNsAt(VirtualFrame frame, PosixPath path, long[] timespec, int dirFd, boolean followSymlinks, PosixSupportLibrary posixLib) {
            try {
                posixLib.utimensat(getPosixSupport(), dirFd, path.value, timespec, followSymlinks);
            } catch (PosixException e) {
                // filename is intentionally not included, see CPython's os_utime_impl
                throw raiseOSErrorFromPosixException(frame, e);
            }
        }

        private void callFutimeNs(VirtualFrame frame, PosixFd fd, long[] timespec, PosixSupportLibrary posixLib) {
            try {
                posixLib.futimens(getPosixSupport(), fd.fd, timespec);
            } catch (PosixException e) {
                // filename is intentionally not included, see CPython's os_utime_impl
                throw raiseOSErrorFromPosixException(frame, e);
            }
        }

        protected static boolean isDefault(int dirFd) {
            return dirFd == PosixSupportLibrary.DEFAULT_DIR_FD;
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
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached SysModuleBuiltins.AuditNode auditNode) {
            auditNode.audit("os.rename", src.originalObject, dst.originalObject, dirFdForAudit(srcDirFd), dirFdForAudit(dstDirFd));
            try {
                posixLib.renameat(getPosixSupport(), srcDirFd, src.value, dstDirFd, dst.value);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e, src.originalObject, dst.originalObject);
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
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            return posixLib.faccessat(getPosixSupport(), dirFd, path.value, mode, effectiveIds, followSymlinks);
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
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            auditNode.audit("os.chmod", path.originalObject, mode, dirFdForAudit(dirFd));
            try {
                posixLib.fchmodat(getPosixSupport(), dirFd, path.value, mode, followSymlinks);
            } catch (PosixException e) {
                // TODO CPython checks for ENOTSUP as well
                if (e.getErrorCode() == OSErrorEnum.EOPNOTSUPP.getNumber() && !followSymlinks) {
                    if (dirFd != PosixSupportLibrary.DEFAULT_DIR_FD) {
                        throw raise(ValueError, ErrorMessages.CANNOT_USE_FD_AND_FOLLOW_SYMLINKS_TOGETHER, "chmod");
                    } else {
                        throw raise(NotImplementedError, ErrorMessages.UNAVAILABLE_ON_THIS_PLATFORM, "chmod", "follow_symlinks");
                    }
                }
                throw raiseOSErrorFromPosixException(frame, e, path.originalObject);
            }
            return PNone.NONE;
        }

        @Specialization
        PNone chmodFollow(VirtualFrame frame, PosixFd fd, int mode, int dirFd, @SuppressWarnings("unused") boolean followSymlinks,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            auditNode.audit("os.chmod", fd.originalObject, mode, dirFdForAudit(dirFd));
            // Unlike stat and utime which raise CANT_SPECIFY_DIRFD_WITHOUT_PATH or
            // CANNOT_USE_FD_AND_FOLLOW_SYMLINKS_TOGETHER when an inappropriate combination of
            // arguments is used, CPython's implementation of chmod simply ignores dir_fd and
            // follow_symlinks if a fd is specified instead of a path.
            try {
                posixLib.fchmod(getPosixSupport(), fd.fd, mode);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e, fd.originalObject);
            }
            return PNone.NONE;
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

        @Specialization(guards = "path.wasBufferLike")
        PBytes readlinkAsBytes(VirtualFrame frame, PosixPath path, int dirFd,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            try {
                return opaquePathToBytes(posixLib.readlinkat(getPosixSupport(), dirFd, path.value), posixLib, getPosixSupport(), factory());
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e, path.originalObject);
            }
        }

        @Specialization(guards = "!path.wasBufferLike")
        String readlinkAsString(VirtualFrame frame, PosixPath path, int dirFd,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            try {
                return posixLib.getPathAsString(getPosixSupport(), posixLib.readlinkat(getPosixSupport(), dirFd, path.value));
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e, path.originalObject);
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
        String getStrError(int code,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            return posixLib.strerror(getPosixSupport(), code);
        }
    }

    @Builtin(name = "_exit", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class ExitNode extends PythonBuiltinNode {
        @TruffleBoundary
        @Specialization
        Object exit(int status) {
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
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached BranchProfile errorProfile) {
            while (true) {
                try {
                    long[] result = posixLib.waitpid(getPosixSupport(), pid, options);
                    return factory().createTuple(new Object[]{result[0], result[1]});
                } catch (PosixException e) {
                    errorProfile.enter();
                    if (e.getErrorCode() == OSErrorEnum.EINTR.getNumber()) {
                        getContext().triggerAsyncActions(frame);
                    } else {
                        throw raiseOSErrorFromPosixException(frame, e);
                    }
                }
            }
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
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
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
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
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
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
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
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
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
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
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
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
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
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
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
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
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
        int system(VirtualFrame frame, PBytes command,
                   @Cached BytesNodes.ToBytesNode toBytesNode,
                   @Cached SysModuleBuiltins.AuditNode auditNode,
                   @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            // Unlike in other posix builtins, we go through str -> bytes -> byte[] -> String
            // conversions for emulated backend because the bytes version after fsencode conversion
            // is subject to sys.audit.
            auditNode.audit("os.system", command);
            byte[] bytes = toBytesNode.execute(command);
            Object cmdOpaque = posixLib.createPathFromBytes(getPosixSupport(), bytes);
            return posixLib.system(getPosixSupport(), cmdOpaque);
        }
    }

    @Builtin(name = "urandom", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"size"})
    @ArgumentClinic(name = "size", conversion = ClinicConversion.Index)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class URandomNode extends PythonUnaryClinicBuiltinNode {
        private static SecureRandom secureRandom;

        private static SecureRandom createRandomInstance() {
            try {
                return SecureRandom.getInstance("NativePRNGNonBlocking");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }

        @Specialization
        @TruffleBoundary(allowInlining = true)
        PBytes urandom(int size) {
            if (secureRandom == null) {
                secureRandom = createRandomInstance();
            }
            byte[] bytes = new byte[size];
            secureRandom.nextBytes(bytes);
            return factory().createBytes(bytes);
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
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            try {
                return posixLib.umask(getPosixSupport(), mask);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    @Builtin(name = "ctermid", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class CtermId extends PythonBuiltinNode {
        @Specialization
        String ctermid(VirtualFrame frame,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            try {
                return posixLib.ctermid(getPosixSupport());
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
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
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            auditNode.audit("kill", pid, signal);
            try {
                posixLib.kill(getPosixSupport(), pid, signal);
                return PNone.NONE;
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }

        }
    }

    @Builtin(name = "fspath", minNumOfPositionalArgs = 1, parameterNames = {"path"})
    @GenerateNodeFactory
    // Can be used as an equivalent of PyOS_FSPath()
    public abstract static class FspathNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = "isPath(value)")
        Object doTrivial(Object value) {
            return value;
        }

        @Specialization(guards = "!isPath(value)", limit = "3")
        Object callFspath(VirtualFrame frame, Object value,
                        @CachedLibrary("value") PythonObjectLibrary lib,
                        @CachedLibrary(limit = "2") PythonObjectLibrary methodLib) {
            Object func = lib.lookupAttributeOnType(value, __FSPATH__);
            if (func == PNone.NO_VALUE) {
                throw raise(TypeError, ErrorMessages.EXPECTED_STR_BYTE_OSPATHLIKE_OBJ, value);
            }
            Object pathObject = methodLib.callUnboundMethodWithState(func, PArguments.getThreadState(frame), value);
            if (isPath(pathObject)) {
                return pathObject;
            }
            throw raise(TypeError, ErrorMessages.EXPECTED_FSPATH_TO_RETURN_STR_OR_BYTES, value, pathObject);
        }

        protected static boolean isPath(Object obj) {
            return PGuards.isString(obj) || obj instanceof PBytes;
        }
    }

    // ------------------
    // Helpers

    /**
     * Helper node that accepts either str or bytes and converts it to {@code PBytes}.
     */
    abstract static class StringOrBytesToBytesNode extends PythonBuiltinBaseNode {
        abstract PBytes execute(Object obj);

        @Specialization
        PBytes doString(String str) {
            return factory().createBytes(BytesUtils.utf8StringToBytes(str));
        }

        @Specialization
        PBytes doPString(PString pstr,
                        @Cached CastToJavaStringNode castToJavaStringNode) {
            return doString(castToJavaStringNode.execute(pstr));
        }

        @Specialization
        PBytes doBytes(PBytes bytes) {
            return bytes;
        }
    }

    /**
     * Helper node that accepts either str or bytes and converts it to a representation specific to
     * the {@link PosixSupportLibrary} in use. Basically equivalent of
     * {@code PyUnicode_EncodeFSDefault}.
     */
    abstract static class StringOrBytesToOpaquePathNode extends PNodeWithRaise {
        abstract Object execute(Object obj);

        @Specialization(limit = "1")
        Object doString(String str,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib) {
            return checkPath(posixLib.createPathFromString(context.getPosixSupport(), str));
        }

        @Specialization(limit = "1")
        Object doPString(PString pstr,
                        @Cached CastToJavaStringNode castToJavaStringNode,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib) {
            String str = castToJavaStringNode.execute(pstr);
            return checkPath(posixLib.createPathFromString(context.getPosixSupport(), str));
        }

        @Specialization(limit = "1")
        Object doBytes(PBytes bytes,
                        @Cached BytesNodes.ToBytesNode toBytesNode,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib) {
            return checkPath(posixLib.createPathFromBytes(context.getPosixSupport(), toBytesNode.execute(bytes)));
        }

        private Object checkPath(Object path) {
            if (path == null) {
                throw raise(ValueError, ErrorMessages.EMBEDDED_NULL_BYTE);
            }
            return path;
        }
    }

    /**
     * Similar to {@code PyUnicode_FSConverter}, but the actual conversion is delegated to the
     * {@link PosixSupportLibrary} implementation.
     */
    abstract static class ObjectToOpaquePathNode extends Node {
        abstract Object execute(VirtualFrame frame, Object obj);

        @Specialization
        Object doIt(VirtualFrame frame, Object obj,
                        @Cached FspathNode fspathNode,
                        @Cached StringOrBytesToOpaquePathNode stringOrBytesToOpaquePathNode) {
            return stringOrBytesToOpaquePathNode.execute(fspathNode.call(frame, obj));
        }
    }

    abstract static class ConvertToTimespecBaseNode extends PythonBuiltinBaseNode {
        abstract void execute(VirtualFrame frame, Object obj, long[] timespec, int offset);
    }

    /**
     * Equivalent of {@code _PyTime_ObjectToTimespec} as used in {@code os_utime_impl}.
     */
    abstract static class ObjectToTimespecNode extends ConvertToTimespecBaseNode {

        @Specialization(guards = "!isNan(value)")
        void doDoubleNotNan(double value, long[] timespec, int offset) {
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
                throw raise(OverflowError, ErrorMessages.TIMESTAMP_OUT_OF_RANGE);
            }
            timespec[offset] = (long) intPart;
            timespec[offset + 1] = (long) floatPart;
            assert 0 <= timespec[offset + 1] && timespec[offset + 1] < (long) denominator;
        }

        @Specialization(guards = "isNan(value)")
        @SuppressWarnings("unused")
        void doDoubleNan(double value, long[] timespec, int offset) {
            throw raise(ValueError, ErrorMessages.INVALID_VALUE_NAN);
        }

        @Specialization
        void doPFloat(PFloat obj, long[] timespec, int offset) {
            double value = obj.getValue();
            if (Double.isNaN(value)) {
                throw raise(ValueError, ErrorMessages.INVALID_VALUE_NAN);
            }
            doDoubleNotNan(value, timespec, offset);
        }

        @Specialization
        void doInt(int value, long[] timespec, int offset) {
            timespec[offset] = value;
            timespec[offset + 1] = 0;
        }

        @Specialization
        void doLong(long value, long[] timespec, int offset) {
            timespec[offset] = value;
            timespec[offset + 1] = 0;
        }

        @Specialization(guards = {"!isDouble(value)", "!isPFloat(value)", "!isInteger(value)"}, limit = "1")
        void doGeneric(VirtualFrame frame, Object value, long[] timespec, int offset,
                        @CachedLibrary("value") PythonObjectLibrary lib,
                        @Cached IsBuiltinClassProfile overflowProfile) {
            try {
                timespec[offset] = lib.asJavaLongWithState(value, PArguments.getThreadState(frame));
            } catch (PException e) {
                e.expect(OverflowError, overflowProfile);
                throw raise(OverflowError, ErrorMessages.TIMESTAMP_OUT_OF_RANGE);
            }
            timespec[offset + 1] = 0;
        }

        protected static boolean isNan(double value) {
            return Double.isNaN(value);
        }
    }

    /**
     * Equivalent of {@code split_py_long_to_s_and_ns} as used in {@code os_utime_impl}.
     */
    abstract static class SplitLongToSAndNsNode extends ConvertToTimespecBaseNode {

        private static final long BILLION = 1000000000;

        @Specialization
        void doInt(int value, long[] timespec, int offset) {
            doLong(value, timespec, offset);
        }

        @Specialization
        void doLong(long value, long[] timespec, int offset) {
            timespec[offset] = Math.floorDiv(value, BILLION);
            timespec[offset + 1] = Math.floorMod(value, BILLION);
        }

        @Specialization(guards = {"!isInteger(value)"})
        void doGeneric(VirtualFrame frame, Object value, long[] timespec, int offset,
                        @Cached("createDivmod()") LookupAndCallBinaryNode callDivmod,
                        @Cached LenNode lenNode,
                        @Cached("createNotNormalized()") GetItemNode getItemNode,
                        @CachedLibrary(limit = "2") PythonObjectLibrary lib) {
            Object divmod = callDivmod.executeObject(frame, value, BILLION);
            if (!PGuards.isPTuple(divmod) || lenNode.execute((PSequence) divmod) != 2) {
                throw raise(TypeError, ErrorMessages.MUST_RETURN_2TUPLE, value, divmod);
            }
            SequenceStorage storage = ((PTuple) divmod).getSequenceStorage();
            timespec[offset] = lib.asJavaLongWithState(getItemNode.execute(frame, storage, 0), PArguments.getThreadState(frame));
            timespec[offset + 1] = lib.asJavaLongWithState(getItemNode.execute(frame, storage, 1), PArguments.getThreadState(frame));
        }

        protected static LookupAndCallBinaryNode createDivmod() {
            return BinaryArithmetic.DivMod.create();
        }
    }

    static int dirFdForAudit(int dirFd) {
        return dirFd == PosixSupportLibrary.DEFAULT_DIR_FD ? -1 : dirFd;
    }

    public static PTuple createStatResult(PythonObjectFactory factory, ConditionProfile positiveLongProfile, long[] out) {
        Object[] res = new Object[16];
        for (int i = 0; i < 7; i++) {
            res[i] = PInt.createPythonIntFromUnsignedLong(factory, positiveLongProfile, out[i]);
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
        PBytes convert(VirtualFrame frame, Object value,
                        @Cached FspathNode fspathNode,
                        @Cached StringOrBytesToBytesNode stringOrBytesToBytesNode) {
            return stringOrBytesToBytesNode.execute(fspathNode.call(frame, value));
        }

        @ClinicConverterFactory
        public static FsConverterNode create() {
            return PosixModuleBuiltinsFactory.FsConverterNodeGen.create();
        }
    }

    /**
     * Equivalent of CPython's {@code path_converter()}. Always returns an {@code int}. If the
     * parameter is omitted, returns {@link PosixSupportLibrary#DEFAULT_DIR_FD}.
     */
    public abstract static class DirFdConversionNode extends ArgumentCastNodeWithRaise {

        @Specialization
        int doNone(@SuppressWarnings("unused") PNone value) {
            return PosixSupportLibrary.DEFAULT_DIR_FD;
        }

        @Specialization
        int doFdBool(boolean value) {
            return PInt.intValue(value);
        }

        @Specialization
        int doFdInt(int value) {
            return value;
        }

        @Specialization
        int doFdLong(long value) {
            return longToFd(value, getRaiseNode());
        }

        @Specialization
        int doFdPInt(PInt value,
                        @Cached CastToJavaLongLossyNode castToLongNode) {
            return doFdLong(castToLongNode.execute(value));
        }

        @Specialization(guards = {"!isPNone(value)", "!canBeInteger(value)", "lib.canBeIndex(value)"}, limit = "3")
        int doIndex(VirtualFrame frame, Object value,
                        @CachedLibrary("value") PythonObjectLibrary lib,
                        @Cached CastToJavaLongLossyNode castToLongNode) {
            Object o = lib.asIndexWithState(value, PArguments.getThreadState(frame));
            return doFdLong(castToLongNode.execute(o));
        }

        @Fallback
        Object doGeneric(Object value) {
            throw raise(TypeError, ErrorMessages.ARG_SHOULD_BE_INT_OR_NONE, value);
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
        public static DirFdConversionNode create() {
            return PosixModuleBuiltinsFactory.DirFdConversionNodeGen.create();
        }
    }

    /**
     * Equivalent of CPython's {@code path_converter()}. Always returns an instance of
     * {@link PosixFileHandle}.
     */
    public abstract static class PathConversionNode extends ArgumentCastNodeWithRaise {

        private final String functionNameWithColon;
        private final String argumentName;
        protected final boolean nullable;
        protected final boolean allowFd;
        @CompilationFinal private ContextReference<PythonContext> contextRef;

        public PathConversionNode(String functionName, String argumentName, boolean nullable, boolean allowFd) {
            this.functionNameWithColon = functionName != null ? functionName + ": " : "";
            this.argumentName = argumentName != null ? argumentName : "path";
            this.nullable = nullable;
            this.allowFd = allowFd;
        }

        @Specialization(guards = "nullable")
        PosixFileHandle doNone(@SuppressWarnings("unused") PNone value,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            return new PosixPath(null, checkPath(posixLib.createPathFromString(getPosixSupport(), ".")), false);
        }

        @Specialization(guards = "allowFd")
        PosixFileHandle doFdBool(boolean value) {
            return new PosixFd(value, PInt.intValue(value));
        }

        @Specialization(guards = "allowFd")
        PosixFileHandle doFdInt(int value) {
            return new PosixFd(value, value);
        }

        @Specialization(guards = "allowFd")
        PosixFileHandle doFdLong(long value) {
            return new PosixFd(value, DirFdConversionNode.longToFd(value, getRaiseNode()));
        }

        @Specialization(guards = "allowFd")
        PosixFileHandle doFdPInt(PInt value,
                        @Cached CastToJavaLongLossyNode castToLongNode) {
            return new PosixFd(value, DirFdConversionNode.longToFd(castToLongNode.execute(value), getRaiseNode()));
        }

        @Specialization
        PosixFileHandle doUnicode(String value,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            return new PosixPath(value, checkPath(posixLib.createPathFromString(getPosixSupport(), value)), false);
        }

        @Specialization
        PosixFileHandle doUnicode(PString value,
                        @Cached CastToJavaStringNode castToJavaStringNode,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            String str = castToJavaStringNode.execute(value);
            return new PosixPath(value, checkPath(posixLib.createPathFromString(getPosixSupport(), str)), false);
        }

        @Specialization
        PosixFileHandle doBytes(PBytesLike value,
                        @Cached BytesNodes.ToBytesNode toByteArrayNode,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            return new PosixPath(value, checkPath(posixLib.createPathFromBytes(getPosixSupport(), toByteArrayNode.execute(value))), true);
        }

        @Specialization(guards = {"!isHandled(value)", "lib.isBuffer(value)"}, limit = "1")
        PosixFileHandle doBuffer(VirtualFrame frame, Object value,
                        @CachedLibrary("value") PythonObjectLibrary lib,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached WarningsModuleBuiltins.WarnNode warningNode) {
            warningNode.warnFormat(frame, null, PythonBuiltinClassType.DeprecationWarning, 1,
                            ErrorMessages.S_S_SHOULD_BE_S_NOT_P, functionNameWithColon, argumentName, getAllowedTypes(), value);
            try {
                return new PosixPath(value, checkPath(posixLib.createPathFromBytes(getPosixSupport(), lib.getBufferBytes(value))), true);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere("Object claims to be a buffer but does not implement getBufferBytes");
            }
        }

        @Specialization(guards = {"!isHandled(value)", "!lib.isBuffer(value)", "allowFd", "lib.canBeIndex(value)"}, limit = "3")
        PosixFileHandle doIndex(VirtualFrame frame, Object value,
                        @CachedLibrary("value") PythonObjectLibrary lib,
                        @Cached CastToJavaLongLossyNode castToLongNode) {
            Object o = lib.asIndexWithState(value, PArguments.getThreadState(frame));
            return new PosixFd(value, DirFdConversionNode.longToFd(castToLongNode.execute(o), getRaiseNode()));
        }

        @Specialization(guards = {"!isHandled(value)", "!lib.isBuffer(value)", "!allowFd || !lib.canBeIndex(value)"}, limit = "3")
        PosixFileHandle doGeneric(VirtualFrame frame, Object value,
                        @CachedLibrary("value") PythonObjectLibrary lib,
                        @CachedLibrary(limit = "2") PythonObjectLibrary methodLib,
                        @Cached BytesNodes.ToBytesNode toByteArrayNode,
                        @Cached CastToJavaStringNode castToJavaStringNode,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            Object func = lib.lookupAttributeOnType(value, __FSPATH__);
            if (func == PNone.NO_VALUE) {
                throw raise(TypeError, ErrorMessages.S_S_SHOULD_BE_S_NOT_P, functionNameWithColon, argumentName,
                                getAllowedTypes(), value);
            }
            Object pathObject = methodLib.callUnboundMethodWithState(func, PArguments.getThreadState(frame), value);
            // 'pathObject' replaces 'value' as the PosixPath.originalObject for auditing purposes
            // by design
            if (pathObject instanceof PBytes) {
                return doBytes((PBytes) pathObject, toByteArrayNode, posixLib);
            }
            if (pathObject instanceof PString) {
                return doUnicode((PString) pathObject, castToJavaStringNode, posixLib);
            }
            if (pathObject instanceof String) {
                return doUnicode((String) pathObject, posixLib);
            }
            throw raise(TypeError, ErrorMessages.EXPECTED_FSPATH_TO_RETURN_STR_OR_BYTES, value, pathObject);
        }

        protected boolean isHandled(Object value) {
            return PGuards.isPNone(value) && nullable || PGuards.canBeInteger(value) && allowFd || PGuards.isString(value) || PGuards.isBytes(value);
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

        private ContextReference<PythonContext> getContextRef() {
            if (contextRef == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                contextRef = lookupContextReference(PythonLanguage.class);
            }
            return contextRef;
        }

        private PythonContext getContext() {
            return getContextRef().get();
        }

        protected final Object getPosixSupport() {
            return getContext().getPosixSupport();
        }

        @ClinicConverterFactory
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

        @Specialization(limit = "3")
        static long doOthers(VirtualFrame frame, Object value,
                        @CachedLibrary("value") PythonObjectLibrary lib) {
            return lib.asJavaLongWithState(value, PArguments.getThreadState(frame));
        }

        @ClinicConverterFactory(shortCircuitPrimitive = PrimitiveType.Long)
        public static OffsetConversionNode create() {
            return PosixModuleBuiltinsFactory.OffsetConversionNodeGen.create();
        }
    }

    /**
     * Equivalent of CPython's {@code fildes_converter()}, which in turn delegates to
     * {@code PyObject_AsFileDescriptor}. Always returns an {@code int}.
     */
    public abstract static class FileDescriptorConversionNode extends ArgumentCastNodeWithRaise {
        @Specialization
        int doFdInt(int value) {
            return PInt.asFileDescriptor(value, getRaiseNode());
        }

        @Specialization(guards = "!isInt(value)", limit = "3")
        int doIndex(VirtualFrame frame, Object value,
                        @CachedLibrary("value") PythonObjectLibrary lib) {
            return lib.asFileDescriptorWithState(value, PArguments.getThreadState(frame));
        }

        protected static boolean isInt(Object value) {
            return value instanceof Integer;
        }

        @ClinicConverterFactory(shortCircuitPrimitive = PrimitiveType.Int)
        public static FileDescriptorConversionNode create() {
            return PosixModuleBuiltinsFactory.FileDescriptorConversionNodeGen.create();
        }
    }

    /**
     * Emulates of CPython's {@code pid_t_converter()}. Always returns an {@code long}.
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

        @Specialization(guards = "!isInteger(value)", limit = "3")
        static long doGeneric(VirtualFrame frame, Object value,
                        @CachedLibrary("value") PythonObjectLibrary lib) {
            return lib.asJavaLongWithState(value, PArguments.getThreadState(frame));
        }

        @ClinicConverterFactory(shortCircuitPrimitive = {PrimitiveType.Int, PrimitiveType.Long})
        public static PidtConversionNode create() {
            // TODO on platforms with sizeof(pid_t) == 4 (includes linux), the converter should
            // check for overflow
            return PosixModuleBuiltinsFactory.PidtConversionNodeGen.create();
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
     * @see PosixSupportLibrary#createPathFromString(Object, String)
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
