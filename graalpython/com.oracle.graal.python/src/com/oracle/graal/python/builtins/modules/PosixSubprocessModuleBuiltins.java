/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.nodes.PNodeWithGlobalState.NodeContextManager;
import com.oracle.graal.python.nodes.expression.CastToBooleanNode;
import com.oracle.graal.python.nodes.expression.CastToListExpressionNode.CastToListNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.util.CastToIndexNode;
import com.oracle.graal.python.nodes.util.CastToStringNode;
import com.oracle.graal.python.runtime.PosixResources;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

@CoreFunctions(defineModule = "_posixsubprocess")
public class PosixSubprocessModuleBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PosixSubprocessModuleBuiltinsFactory.getFactories();
    }

    @Builtin(name = "fork_exec", minNumOfPositionalArgs = 17, parameterNames = {"args", "executable_list", "close_fds",
                    "fds_to_keep", "cwd", "env", "p2cread", "p2cwrite", "c2pread", "c2pwrite", "errread", "errwrite",
                    "errpipe_read", "errpipe_write", "restore_signals", "call_setsid", "preexec_fn"})
    @GenerateNodeFactory
    abstract static class ForkExecNode extends PythonBuiltinNode {
        @Child private BytesNodes.ToBytesNode toBytes = BytesNodes.ToBytesNode.create();

        @Specialization
        @SuppressWarnings("try")
        int forkExec(VirtualFrame frame, PList args, @SuppressWarnings("unused") PList execList, @SuppressWarnings("unused") boolean closeFds,
                        @SuppressWarnings("unused") PList fdsToKeep, String cwd, PList env,
                        int p2cread, int p2cwrite, int c2pread, int c2pwrite,
                        int errread, int errwrite, @SuppressWarnings("unused") int errpipe_read, int errpipe_write,
                        @SuppressWarnings("unused") boolean restore_signals, @SuppressWarnings("unused") boolean call_setsid, @SuppressWarnings("unused") PNone preexec_fn) {

            try (NodeContextManager cm = withGlobalState(frame)) {
                return forkExec(args, execList, closeFds, fdsToKeep, cwd, env, p2cread, p2cwrite, c2pread, c2pwrite, errread, errwrite, errpipe_read, errpipe_write, restore_signals, call_setsid,
                                preexec_fn);
            }
        }

        @TruffleBoundary
        private synchronized int forkExec(PList args, @SuppressWarnings("unused") PList execList, @SuppressWarnings("unused") boolean closeFds,
                        @SuppressWarnings("unused") PList fdsToKeep, String cwd, PList env,
                        int p2cread, int p2cwrite, int c2pread, int c2pwrite,
                        int errread, int errwrite, @SuppressWarnings("unused") int errpipe_read, int errpipe_write,
                        @SuppressWarnings("unused") boolean restore_signals, @SuppressWarnings("unused") boolean call_setsid, @SuppressWarnings("unused") PNone preexec_fn) {
            PythonContext context = getContext();
            PosixResources resources = context.getResources();
            if (!context.isExecutableAccessAllowed()) {
                return -1;
            }

            ArrayList<String> argStrings = new ArrayList<>();
            Object[] copyOfInternalArray = args.getSequenceStorage().getCopyOfInternalArray();
            for (Object o : copyOfInternalArray) {
                if (o instanceof String) {
                    argStrings.add((String) o);
                } else if (o instanceof PString) {
                    argStrings.add(((PString) o).getValue());
                } else {
                    throw raise(PythonBuiltinClassType.OSError, "illegal argument");
                }
            }

            // TODO: fix this better? sys.executable is often used in subprocess tests, but on Java
            // that actually gives you a whole cmdline, which we need to split up for process
            // builder
            if (!argStrings.isEmpty()) {
                if (argStrings.get(0).equals(PythonOptions.getOption(context, PythonOptions.Executable))) {
                    String[] executableList = PythonOptions.getExecutableList();
                    argStrings.remove(0);
                    for (int i = executableList.length - 1; i >= 0; i--) {
                        argStrings.add(0, executableList[i]);
                    }
                }
            }

            PythonLanguage.getLogger().fine(() -> "_posixsubprocess.fork_exec: " + String.join(" ", argStrings));
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

            try {
                if (getContext().getEnv().getPublicTruffleFile(cwd).exists()) {
                    pb.directory(new File(cwd));
                } else {
                    throw raise(PythonBuiltinClassType.OSError, "working directory %s is not accessible", cwd);
                }
            } catch (SecurityException e) {
                throw raise(PythonBuiltinClassType.OSError, e);
            }

            Map<String, String> environment = pb.environment();
            for (Object keyValue : env.getSequenceStorage().getInternalArray()) {
                if (keyValue instanceof PBytes) {
                    // NOTE: passing 'null' frame means we took care of the global state in the
                    // callers
                    String[] string = new String(toBytes.execute(null, keyValue)).split("=", 2);
                    if (string.length == 2) {
                        environment.put(string[0], string[1]);
                    }
                }
            }

            try {
                Process process = pb.start();
                if (p2cwrite != -1) {
                    // user code is expected to close the unused ends of the pipes
                    resources.getFileChannel(p2cwrite).close();
                    resources.fdopen(p2cwrite, Channels.newChannel(process.getOutputStream()));
                }
                if (c2pread != -1) {
                    resources.getFileChannel(c2pread).close();
                    resources.fdopen(c2pread, Channels.newChannel(process.getInputStream()));
                }
                if (errread != -1) {
                    resources.getFileChannel(errread).close();
                    resources.fdopen(errread, Channels.newChannel(process.getErrorStream()));
                }

                return resources.registerChild(process);
            } catch (IOException e) {
                Channel err = null;
                if (errpipe_write != -1) {
                    // write exec error information here. Data format: "exception name:hex
                    // errno:description"
                    err = resources.getFileChannel(errpipe_write);
                    if (!(err instanceof WritableByteChannel)) {
                        throw raise(PythonBuiltinClassType.OSError, "there was an error writing the fork_exec error to the error pipe");
                    } else {
                        try {
                            ((WritableByteChannel) err).write(ByteBuffer.wrap(("SubprocessError:0:" + e.getMessage()).getBytes()));
                        } catch (IOException e1) {
                        }
                    }
                }
                return -1;
            }
        }

        @Specialization(replaces = "forkExec")
        int forkExecDefault(VirtualFrame frame, Object args, Object executable_list, Object close_fds,
                        Object fdsToKeep, Object cwd, Object env,
                        Object p2cread, Object p2cwrite, Object c2pread, Object c2pwrite,
                        Object errread, Object errwrite, Object errpipe_read, Object errpipe_write,
                        Object restore_signals, Object call_setsid, PNone preexec_fn,
                        @Cached("create()") CastToListNode castArgs,
                        @Cached("create()") CastToListNode castExecList,
                        @Cached("createIfTrueNode()") CastToBooleanNode castCloseFds,
                        @Cached("create()") CastToListNode castFdsToKeep,
                        @Cached("create()") CastToStringNode castCwd,
                        @Cached("create()") CastToListNode castEnv,
                        @Cached("create()") CastToIndexNode castP2cread,
                        @Cached("create()") CastToIndexNode castP2cwrite,
                        @Cached("create()") CastToIndexNode castC2pread,
                        @Cached("create()") CastToIndexNode castC2pwrite,
                        @Cached("create()") CastToIndexNode castErrread,
                        @Cached("create()") CastToIndexNode castErrwrite,
                        @Cached("create()") CastToIndexNode castErrpipeRead,
                        @Cached("create()") CastToIndexNode castErrpipeWrite,
                        @Cached("createIfTrueNode()") CastToBooleanNode castRestoreSignals,
                        @Cached("createIfTrueNode()") CastToBooleanNode castSetsid) {

            String actualCwd;
            if (cwd instanceof PNone) {
                actualCwd = getContext().getEnv().getCurrentWorkingDirectory().getPath();
            } else {
                actualCwd = castCwd.execute(frame, cwd);
            }

            PList actualEnv;
            if (env instanceof PNone) {
                actualEnv = factory().createList();
            } else {
                actualEnv = castEnv.execute(frame, env);
            }

            return forkExec(castArgs.execute(frame, args), castExecList.execute(frame, executable_list), castCloseFds.executeBoolean(frame, close_fds),
                            castFdsToKeep.execute(frame, fdsToKeep), actualCwd, actualEnv,
                            castP2cread.execute(p2cread), castP2cwrite.execute(p2cwrite), castC2pread.execute(c2pread), castC2pwrite.execute(c2pwrite),
                            castErrread.execute(errread), castErrwrite.execute(errwrite), castErrpipeRead.execute(errpipe_read), castErrpipeWrite.execute(errpipe_write),
                            castRestoreSignals.executeBoolean(frame, restore_signals), castSetsid.executeBoolean(frame, call_setsid), preexec_fn);
        }
    }
}
