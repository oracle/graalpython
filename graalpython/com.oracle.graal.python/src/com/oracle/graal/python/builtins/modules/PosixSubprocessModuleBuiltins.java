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
package com.oracle.graal.python.builtins.modules;

import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum.ErrorAndMessagePair;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.expression.CastToListExpressionNode.CastToListNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.PosixResources;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.runtime.ProcessWrapper;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;

@CoreFunctions(defineModule = "_posixsubprocess")
public class PosixSubprocessModuleBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PosixSubprocessModuleBuiltinsFactory.getFactories();
    }

    @Builtin(name = "fork_exec", minNumOfPositionalArgs = 17, parameterNames = {"args", "executable_list", "close_fds",
                    "fds_to_keep", "cwd", "env", "p2cread", "p2cwrite", "c2pread", "c2pwrite", "errread", "errwrite",
                    "errpipe_read", "errpipe_write", "restore_signals", "call_setsid", "preexec_fn"}, needsFrame = true)
    @GenerateNodeFactory
    abstract static class ForkExecNode extends PythonBuiltinNode {
        private static final TruffleLogger LOGGER = PythonLanguage.getLogger(ForkExecNode.class);

        @Specialization
        int forkExec(VirtualFrame frame, PList args, @SuppressWarnings("unused") PList execList, @SuppressWarnings("unused") boolean closeFds,
                        @SuppressWarnings("unused") PList fdsToKeep, String cwd, PList env,
                        int p2cread, int p2cwrite, int c2pread, int c2pwrite,
                        int errread, int errwrite, @SuppressWarnings("unused") int errpipe_read, int errpipe_write,
                        @SuppressWarnings("unused") boolean restore_signals, @SuppressWarnings("unused") boolean call_setsid, @SuppressWarnings("unused") PNone preexec_fn) {

            PythonContext context = getContext();
            Object state = IndirectCallContext.enter(frame, context, this);
            try {
                return forkExec(args, execList, closeFds, fdsToKeep, cwd, env, p2cread, p2cwrite, c2pread, c2pwrite, errread, errwrite, errpipe_read, errpipe_write, restore_signals, call_setsid,
                                preexec_fn);
            } finally {
                IndirectCallContext.exit(frame, context, state);
            }
        }

        @TruffleBoundary
        private synchronized int forkExec(PList args, PList execList, @SuppressWarnings("unused") boolean closeFds,
                        @SuppressWarnings("unused") PList fdsToKeep, String cwd, PList env,
                        int p2cread, int p2cwrite, int c2pread, int c2pwrite,
                        int errread, int errwrite, @SuppressWarnings("unused") int errpipe_read, int errpipe_write,
                        @SuppressWarnings("unused") boolean restore_signals, @SuppressWarnings("unused") boolean call_setsid, @SuppressWarnings("unused") PNone preexec_fn) {
            PythonContext context = getContext();
            PosixResources resources = context.getResources();
            if (!context.isExecutableAccessAllowed()) {
                return -1;
            }

            SequenceStorage argsStorage = args.getSequenceStorage();
            ArrayList<String> argStrings = new ArrayList<>(argsStorage.length());
            CastToJavaStringNode castToStringNode = CastToJavaStringNode.getUncached();
            for (int i = 0; i < argsStorage.length(); i++) {
                try {
                    argStrings.add(castToStringNode.execute(argsStorage.getItemNormalized(i)));
                } catch (CannotCastException ex) {
                    throw raise(PythonBuiltinClassType.OSError, ErrorMessages.ILLEGAL_ARG);
                }
            }

            File cwdFile;
            Env truffleEnv = context.getEnv();
            if (getSafeTruffleFile(truffleEnv, cwd).exists()) {
                cwdFile = new File(cwd);
            } else {
                throw raise(PythonBuiltinClassType.OSError, ErrorMessages.WORK_DIR_NOT_ACCESSIBLE, cwd);
            }

            SequenceStorage envStorage = env.getSequenceStorage();
            HashMap<String, String> envMap = new HashMap<>(envStorage.length());
            PythonObjectLibrary pyLib = PythonObjectLibrary.getUncached();
            for (int i = 0; i < envStorage.length(); i++) {
                Object keyValue = envStorage.getItemNormalized(i);
                if (!(keyValue instanceof PBytes)) {
                    continue;
                }
                // NOTE: passing 'null' frame means we took care of the global state in the callers
                String str = checkNullBytesAndEncode(pyLib, (PBytes) keyValue);
                String[] strings = str.split("=", 2);
                if (strings.length == 2) {
                    envMap.put(strings[0], strings[1]);
                }
            }

            // The execList argument contains a list of paths to executables. They should be tried
            // one-by-one until we find one that can be executed. Unless passed explicitly as an
            // argument by the user, the list is constructed by the Python wrapper code, which
            // creates an entry for each directory in $PATH joined with the executable name

            // CPython iterates the executable list trying to call execve for each item until it
            // finds one whose execution succeeds. We do the same to be as compatible as possible.

            // Moreover, execve allows to set program arguments (argv) including argv[0] to anything
            // and independently of that choose the executable. There is nothing like that in the
            // ProcessBuilder API, so we have to replace the first argument with the right
            // executable path taken from execList

            if (argStrings.isEmpty()) {
                // CPython fails on OS level and does not raise any python level error, just the
                // message is print to stderr by the subprocess
                throw raise(PythonBuiltinClassType.OSError, "A NULL argv[0] was passed through an exec system call");
            }

            LOGGER.fine(() -> "_posixsubprocess.fork_exec: " + String.join(" ", argStrings));
            IOException firstError = null;
            SequenceStorage execListStorage = execList.getSequenceStorage();
            for (int i = 0; i < execListStorage.length(); i++) {
                Object item = execListStorage.getItemNormalized(i);
                if (!(item instanceof PBytes)) {
                    throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.EXPECTED_BYTES_P_FOUND, item);
                }
                String path = checkNullBytesAndEncode(pyLib, (PBytes) item);
                int executableListLen = 0;
                if (path.equals(context.getOption(PythonOptions.Executable))) {
                    // In case someone passed to us sys.executable that happens to be java command
                    // invocation with additional options like classpath, we split it to the
                    // individual arguments
                    String[] executableList = PythonOptions.getExecutableList(context);
                    argStrings.remove(0);
                    executableListLen = executableList.length;
                    for (int j = executableListLen - 1; j >= 0; j--) {
                        argStrings.add(0, executableList[j]);
                    }
                } else {
                    argStrings.set(0, path);
                }
                TruffleFile executableFile = getSafeTruffleFile(truffleEnv, argStrings.get(0));
                if (executableFile.isExecutable()) {
                    try {
                        return exec(argStrings, cwdFile, envMap, p2cwrite, p2cread, c2pwrite, c2pread, errwrite, errpipe_write, resources, errread);
                    } catch (IOException ex) {
                        if (firstError == null) {
                            firstError = ex;
                        }
                    }
                } else {
                    LOGGER.finest(() -> "_posixsubprocess.fork_exec not executable: " + executableFile);
                }
                for (int j = 1; j < executableListLen; j++) {
                    argStrings.remove(1);
                }
            }
            if (errpipe_write != -1) {
                handleIOError(errpipe_write, resources, firstError);
            }
            return -1;
        }

        // Tries executing given arguments, throws IOException if the executable cannot be executed,
        // any other error is handled here
        private int exec(ArrayList<String> argStrings, File cwd, Map<String, String> env,
                        int p2cwrite, int p2cread, int c2pwrite, int c2pread,
                        int errwrite, int errpipe_write, PosixResources resources, int errread) throws IOException {
            LOGGER.finest(() -> "_posixsubprocess.fork_exec trying to exec: " + String.join(" ", argStrings));
            ProcessBuilder pb = new ProcessBuilder(argStrings);
            if (p2cread != -1 && p2cwrite != -1) {
                pb.redirectInput(Redirect.PIPE);
            } else {
                pb.redirectInput(Redirect.INHERIT);
            }

            if (c2pread != -1 && c2pwrite != -1) {
                pb.redirectOutput(Redirect.PIPE);
            } else {
                pb.redirectOutput(Redirect.INHERIT);
            }

            if (errread != -1 && errwrite != -1) {
                pb.redirectError(Redirect.PIPE);
            } else {
                pb.redirectError(Redirect.INHERIT);
            }

            if (errwrite == c2pwrite) {
                pb.redirectErrorStream(true);
            }

            pb.directory(cwd);
            pb.environment().putAll(env);

            ProcessWrapper process = new ProcessWrapper(pb.start(), p2cwrite != -1, c2pread != 1, errread != -1);
            try {
                if (p2cwrite != -1) {
                    // user code is expected to close the unused ends of the pipes
                    resources.getFileChannel(p2cwrite).close();
                    resources.fdopen(p2cwrite, process.getOutputChannel());
                }
                if (c2pread != -1) {
                    resources.getFileChannel(c2pread).close();
                    resources.fdopen(c2pread, process.getInputChannel());
                }
                if (errread != -1) {
                    resources.getFileChannel(errread).close();
                    resources.fdopen(errread, process.getErrorChannel());
                }
            } catch (IOException ex) {
                // We only want to rethrow the IOException that may come out of pb.start()
                if (errpipe_write != -1) {
                    handleIOError(errpipe_write, resources, ex);
                }
                return -1;
            }

            return resources.registerChild(process);
        }

        private TruffleFile getSafeTruffleFile(Env env, String path) {
            try {
                return env.getPublicTruffleFile(path);
            } catch (SecurityException e) {
                throw raise(PythonBuiltinClassType.OSError, e);
            }
        }

        private String checkNullBytesAndEncode(PythonObjectLibrary pyLib, PBytes bytesObj) {
            byte[] bytes;
            try {
                bytes = pyLib.getBufferBytes(bytesObj);
            } catch (UnsupportedMessageException e) {
                throw new AssertionError(); // should not happen
            }
            for (byte b : bytes) {
                if (b == 0) {
                    throw raise(PythonBuiltinClassType.ValueError, ErrorMessages.EMBEDDED_NULL_BYTE);
                }
            }
            // Note: we use intentionally the default encoding for the bytes. We're most likely
            // getting bytes that the Python wrapper encoded from strings passed to it by the user
            // and we should support non-ascii characters supported by the current FS. See
            // test_warnings.test_nonascii
            return new String(bytes);
        }

        @TruffleBoundary(allowInlining = true)
        private void handleIOError(int errpipe_write, PosixResources resources, IOException e) {
            // write exec error information here. Data format: "exception name:hex
            // errno:description". The exception can be null if we did not find any file in the
            // execList that could be executed
            Channel err = resources.getFileChannel(errpipe_write);
            if (!(err instanceof WritableByteChannel)) {
                throw raise(PythonBuiltinClassType.OSError, ErrorMessages.ERROR_WRITING_FORKEXEC);
            } else {
                ErrorAndMessagePair pair;
                if (e == null) {
                    pair = new ErrorAndMessagePair(OSErrorEnum.ENOENT, OSErrorEnum.ENOENT.getMessage());
                } else {
                    pair = OSErrorEnum.fromException(e);
                }
                try {
                    ((WritableByteChannel) err).write(ByteBuffer.wrap(("OSError:" + Long.toHexString(pair.oserror.getNumber()) + ":" + pair.message).getBytes()));
                } catch (IOException e1) {
                }
            }
        }

        @Specialization(replaces = "forkExec")
        int forkExecDefault(VirtualFrame frame, Object args, Object executable_list, Object close_fds,
                        Object fdsToKeep, Object cwd, Object env,
                        Object p2cread, Object p2cwrite, Object c2pread, Object c2pwrite,
                        Object errread, Object errwrite, Object errpipe_read, Object errpipe_write,
                        Object restore_signals, Object call_setsid, Object preexec_fn,
                        @Cached CastToListNode castArgs,
                        @Cached CastToListNode castExecList,
                        @Cached CastToListNode castFdsToKeep,
                        @Cached CastToJavaStringNode castCwd,
                        @Cached CastToListNode castEnv,
                        @CachedLibrary(limit = "3") PythonObjectLibrary lib) {
            String actualCwd;
            if (cwd instanceof PNone) {
                actualCwd = getContext().getEnv().getCurrentWorkingDirectory().getPath();
            } else {
                try {
                    actualCwd = castCwd.execute(cwd);
                } catch (CannotCastException e) {
                    throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.EXPECTED_S_P_FOUND, "bytes", cwd);
                }
            }

            PList actualEnv;
            if (env instanceof PNone) {
                actualEnv = factory().createList();
            } else {
                actualEnv = castEnv.execute(frame, env);
            }

            // if we reach this point and there is a preexec_fn, we throw an error because we don't
            // support it
            if (!PGuards.isPNone(preexec_fn)) {
                throw raise(PythonBuiltinClassType.RuntimeError, "preexec_fn not supported");
            }

            return forkExec(castArgs.execute(frame, args), castExecList.execute(frame, executable_list),
                            lib.isTrueWithState(close_fds, PArguments.getThreadState(frame)),
                            castFdsToKeep.execute(frame, fdsToKeep), actualCwd, actualEnv,
                            lib.asSizeWithState(p2cread, PArguments.getThreadState(frame)),
                            lib.asSizeWithState(p2cwrite, PArguments.getThreadState(frame)),
                            lib.asSizeWithState(c2pread, PArguments.getThreadState(frame)),
                            lib.asSizeWithState(c2pwrite, PArguments.getThreadState(frame)),
                            lib.asSizeWithState(errread, PArguments.getThreadState(frame)),
                            lib.asSizeWithState(errwrite, PArguments.getThreadState(frame)),
                            lib.asSizeWithState(errpipe_read, PArguments.getThreadState(frame)),
                            lib.asSizeWithState(errpipe_write, PArguments.getThreadState(frame)),
                            lib.isTrueWithState(restore_signals, PArguments.getThreadState(frame)),
                            lib.isTrueWithState(call_setsid, PArguments.getThreadState(frame)), PNone.NO_VALUE);
        }

    }
}
