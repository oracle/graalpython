/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.exception;

import static com.oracle.graal.python.nodes.ErrorMessages.P_TAKES_NO_KEYWORD_ARGS;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__STR__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.PythonOS;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.lib.PyArgCheckPositionalNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyNumberCheckNode;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectReprAsJavaStringNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetDictIfExistsNode;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;

@CoreFunctions(extendClasses = PythonBuiltinClassType.OSError)
public final class OsErrorBuiltins extends PythonBuiltins {
    static final int ARGS_MIN = 2;
    static final int ARGS_MAX = 5;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return OsErrorBuiltinsFactory.getFactories();
    }

    static boolean osErrorUseInit(VirtualFrame frame, Python3Core core, Object type, PyObjectGetAttr getAttr) {
        // When __init__ is defined in an OSError subclass, we want any extraneous argument
        // to __new__ to be ignored. The only reasonable solution, given __new__ takes a
        // variable number of arguments, is to defer arg parsing and initialization to __init__.
        // But when __new__ is overridden as well, it should call our __new__ with the right
        // arguments.
        //
        // (see http://bugs.python.org/issue12555#msg148829 )
        final PythonBuiltinClass osErrorType = core.lookupType(PythonBuiltinClassType.OSError);
        final Object tpInit = getAttr.execute(frame, type, __INIT__);
        final Object tpNew = getAttr.execute(frame, type, __NEW__);
        final Object osErrInit = getAttr.execute(frame, osErrorType, __INIT__);
        final Object osErrNew = getAttr.execute(frame, osErrorType, __NEW__);
        return tpInit != osErrInit && tpNew == osErrNew;
    }

    static PythonBuiltinClassType errno2errorType(int errno) {
        final OSErrorEnum osErrorEnum = OSErrorEnum.fromNumber(errno);
        if (osErrorEnum == null) {
            return null;
        }
        switch (osErrorEnum) {
            case EISDIR:
                return PythonBuiltinClassType.IsADirectoryError;
            case EAGAIN:
            case EWOULDBLOCK:
            case EALREADY:
            case EINPROGRESS:
                return PythonBuiltinClassType.BlockingIOError;
            case EPIPE:
            case ESHUTDOWN:
                return PythonBuiltinClassType.BrokenPipeError;
            case ECHILD:
                return PythonBuiltinClassType.ChildProcessError;
            case ECONNABORTED:
                return PythonBuiltinClassType.ConnectionAbortedError;
            case ECONNREFUSED:
                return PythonBuiltinClassType.ConnectionRefusedError;
            case ECONNRESET:
                return PythonBuiltinClassType.ConnectionResetError;
            case EEXIST:
                return PythonBuiltinClassType.FileExistsError;
            case ENOENT:
                return PythonBuiltinClassType.FileNotFoundError;
            case ENOTDIR:
                return PythonBuiltinClassType.NotADirectoryError;
            case EINTR:
                return PythonBuiltinClassType.InterruptedError;
            case EACCES:
            case EPERM:
                return PythonBuiltinClassType.PermissionError;
            case ESRCH:
                return PythonBuiltinClassType.ProcessLookupError;
            case ETIMEDOUT:
                return PythonBuiltinClassType.TimeoutError;
            default:
                return null;
        }
    }

    static void osErrorInit(Frame frame, PBaseException self, Object type, Object[] args, OSErrorData parsedArgs, PyNumberCheckNode pyNumberCheckNode,
                    PyNumberAsSizeNode pyNumberAsSizeNode, BaseExceptionBuiltins.BaseExceptionInitNode baseInitNode) {
        OSErrorData data = new OSErrorData();
        Object[] pArgs = args;

        // filename will remain None otherwise
        Object filename = parsedArgs.getFilename();
        Object filename2 = parsedArgs.getFilename2();
        if (filename != null && filename != PNone.NONE) {
            if (type == PythonBuiltinClassType.BlockingIOError &&
                            pyNumberCheckNode.execute(frame, filename)) {
                // BlockingIOError's 3rd argument can be the number of characters written.
                data.setWritten(pyNumberAsSizeNode.executeExact(frame, filename, PythonBuiltinClassType.ValueError));
            } else {
                data.setFilename(filename);
                if (filename2 != null && filename2 != PNone.NONE) {
                    data.setFilename2(filename2);
                }
                if (args.length >= 2 && args.length <= 5) {
                    // filename, filename2, and winerror are removed from the args tuple (for
                    // compatibility purposes, see test_exceptions.py)
                    pArgs = PythonUtils.arrayCopyOfRange(args, 0, 2);
                }
            }
        }
        data.setMyerrno(parsedArgs.getMyerrno());
        data.setStrerror(parsedArgs.getStrerror());
        data.setWinerror(parsedArgs.getWinerror());

        baseInitNode.execute(self, pArgs);
        self.setData(parsedArgs);
    }

    static OSErrorData osErrorParseArgs(Object[] args, PyArgCheckPositionalNode checkPositionalNode) {
        if (args.length >= 2 && args.length <= 5) {
            checkPositionalNode.execute(PythonBuiltinClassType.OSError.getPrintName(), args, ARGS_MIN, ARGS_MAX);
            return OSErrorData.create(args);
        }
        return OSErrorData.create();
    }

    @CompilerDirectives.ValueType
    public static final class OSErrorData extends PBaseException.Data {
        private Object myerrno;
        private Object strerror;
        private Object filename;
        private Object filename2;
        private Object winerror;
        // only for BlockingIOError, -1 otherwise
        private int written = -1;

        private OSErrorData() {

        }

        public Object getMyerrno() {
            return myerrno;
        }

        public void setMyerrno(Object myerrno) {
            this.myerrno = myerrno;
        }

        public Object getStrerror() {
            return strerror;
        }

        public void setStrerror(Object strerror) {
            this.strerror = strerror;
        }

        public Object getFilename() {
            return filename;
        }

        public void setFilename(Object filename) {
            this.filename = filename;
        }

        public Object getFilename2() {
            return filename2;
        }

        public void setFilename2(Object filename2) {
            this.filename2 = filename2;
        }

        public Object getWinerror() {
            return winerror;
        }

        public void setWinerror(Object winerror) {
            this.winerror = winerror;
        }

        public int getWritten() {
            return written;
        }

        public void setWritten(int written) {
            this.written = written;
        }

        public static OSErrorData create() {
            return new OSErrorData();
        }

        public static OSErrorData create(Object[] args) {
            assert args.length >= 2 && args.length <= 5;
            final OSErrorData data = new OSErrorData();
            data.setMyerrno(args[0]);
            data.setStrerror(args[1]);
            if (args.length >= 3) {
                data.setFilename(args[2]);
            }
            if (args.length >= 4) {
                data.setFilename2(args[3]);
            }
            if (args.length == 5) {
                data.setWinerror(args[4]);
            }
            return data;
        }

        public static OSErrorData create(Object myerrno, Object strerror, Object filename, Object filename2, Object winerror) {
            final OSErrorData data = new OSErrorData();
            data.setMyerrno(myerrno);
            data.setStrerror(strerror);
            data.setFilename(filename);
            data.setFilename2(filename2);
            data.setWinerror(winerror);
            return data;
        }
    }

    @Builtin(name = __NEW__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    protected abstract static class OSErrorNewNode extends PythonBuiltinNode {
        @Specialization
        protected Object newCData(VirtualFrame frame, Object subType, Object[] args, PKeyword[] kwds,
                        @Cached PyObjectGetAttr getAttr,
                        @Cached PyNumberCheckNode pyNumberCheckNode,
                        @Cached PyNumberAsSizeNode pyNumberAsSizeNode,
                        @Cached PyArgCheckPositionalNode checkPositionalNode,
                        @Cached BaseExceptionBuiltins.BaseExceptionInitNode baseInitNode) {
            Object type = subType;
            OSErrorData parsedArgs = OSErrorData.create();
            final Python3Core core = getCore();
            if (!osErrorUseInit(frame, core, type, getAttr)) {
                if (kwds.length != 0) {
                    throw raise(PythonBuiltinClassType.TypeError, P_TAKES_NO_KEYWORD_ARGS, type);
                }

                parsedArgs = osErrorParseArgs(args, checkPositionalNode);
                final Object myerrno = parsedArgs.getMyerrno();
                if (myerrno != null && PGuards.canBeInteger(myerrno) &&
                                subType == PythonBuiltinClassType.OSError) {
                    final int errno = pyNumberAsSizeNode.executeExact(frame, myerrno);
                    Object newType = errno2errorType(errno);
                    if (newType != null) {
                        type = newType;
                    }
                }
            }

            PBaseException self = factory().createBaseException(type);
            if (!osErrorUseInit(frame, core, type, getAttr)) {
                osErrorInit(frame, self, type, args, parsedArgs, pyNumberCheckNode, pyNumberAsSizeNode, baseInitNode);
            } else {
                self.setArgs(factory().createEmptyTuple());
            }
            return self;
        }
    }

    @Builtin(name = __INIT__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class OSErrorInitNode extends PythonBuiltinNode {
        @Specialization
        Object initNoArgs(VirtualFrame frame, PBaseException self, Object[] args, PKeyword[] kwds,
                        @Cached GetClassNode getClassNode,
                        @Cached PyObjectGetAttr getAttr,
                        @Cached PyNumberCheckNode pyNumberCheckNode,
                        @Cached PyNumberAsSizeNode pyNumberAsSizeNode,
                        @Cached PyArgCheckPositionalNode checkPositionalNode,
                        @Cached BaseExceptionBuiltins.BaseExceptionInitNode baseInitNode) {
            final Object type = getClassNode.execute(self);
            if (!osErrorUseInit(frame, getCore(), type, getAttr)) {
                // Everything already done in OSError_new
                return PNone.NONE;
            }

            if (kwds.length != 0) {
                throw raise(PythonBuiltinClassType.TypeError, P_TAKES_NO_KEYWORD_ARGS, type);
            }

            OSErrorData parsedArgs = osErrorParseArgs(args, checkPositionalNode);
            osErrorInit(frame, self, type, args, parsedArgs, pyNumberCheckNode, pyNumberAsSizeNode, baseInitNode);
            return PNone.NONE;
        }
    }

    @Builtin(name = "errno", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, doc = "POSIX exception code")
    @GenerateNodeFactory
    public abstract static class OSErrorErrnoNode extends BaseExceptionDataAttrNode {
        @Override
        protected Object get(PBaseException.Data data) {
            assert data instanceof OSErrorData;
            return ((OSErrorData) data).getMyerrno();
        }

        @Override
        protected void set(PBaseException.Data data, Object value) {
            assert data instanceof OSErrorData;
            ((OSErrorData) data).setMyerrno(value);
        }
    }

    @Builtin(name = "strerror", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, doc = "exception strerror")
    @GenerateNodeFactory
    public abstract static class OSErrorStrerrorNode extends BaseExceptionDataAttrNode {
        @Override
        protected Object get(PBaseException.Data data) {
            assert data instanceof OSErrorData;
            return ((OSErrorData) data).getStrerror();
        }

        @Override
        protected void set(PBaseException.Data data, Object value) {
            assert data instanceof OSErrorData;
            ((OSErrorData) data).setStrerror(value);
        }
    }

    @Builtin(name = "filename", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, doc = "exception filename")
    @GenerateNodeFactory
    public abstract static class OSErrorFilenameNode extends BaseExceptionDataAttrNode {
        @Override
        protected Object get(PBaseException.Data data) {
            assert data instanceof OSErrorData;
            return ((OSErrorData) data).getFilename();
        }

        @Override
        protected void set(PBaseException.Data data, Object value) {
            assert data instanceof OSErrorData;
            ((OSErrorData) data).setFilename(value);
        }
    }

    @Builtin(name = "filename2", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, doc = "exception filename2")
    @GenerateNodeFactory
    public abstract static class OSErrorFilename2Node extends BaseExceptionDataAttrNode {
        @Override
        protected Object get(PBaseException.Data data) {
            assert data instanceof OSErrorData;
            return ((OSErrorData) data).getFilename2();
        }

        @Override
        protected void set(PBaseException.Data data, Object value) {
            assert data instanceof OSErrorData;
            ((OSErrorData) data).setFilename2(value);
        }
    }

    @Builtin(name = "winerror", os = PythonOS.PLATFORM_WIN32, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, doc = "Win32 exception code")
    @GenerateNodeFactory
    public abstract static class OSErrorWinerrorNode extends BaseExceptionDataAttrNode {
        @Override
        protected Object get(PBaseException.Data data) {
            assert data instanceof OSErrorData;
            return ((OSErrorData) data).getWinerror();
        }

        @Override
        protected void set(PBaseException.Data data, Object value) {
            assert data instanceof OSErrorData;
            ((OSErrorData) data).setWinerror(value);
        }
    }

    @Builtin(name = __STR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class OSErrorStrNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object str(VirtualFrame frame, PBaseException self,
                        @Cached BaseExceptionBuiltins.StrNode baseStrNode,
                        @Cached PyObjectReprAsJavaStringNode reprNode) {
            assert self.getData() instanceof OSErrorData;
            OSErrorData data = (OSErrorData) self.getData();
            // TODO: missing windows code
            final Object filename = data.getFilename();
            final Object filename2 = data.getFilename2();
            final Object myerrno = data.getMyerrno();
            final Object strerror = data.getStrerror();
            if (filename != null) {
                if (filename2 != null) {
                    return PythonUtils.format("[Error %s] %s: %s -> %s",
                                    myerrno != null ? myerrno : PNone.NONE,
                                    strerror != null ? strerror : PNone.NONE,
                                    reprNode.execute(frame, filename),
                                    reprNode.execute(frame, filename2));
                } else {
                    return PythonUtils.format("[Error %s] %s: %s",
                                    myerrno != null ? myerrno : PNone.NONE,
                                    strerror != null ? strerror : PNone.NONE,
                                    reprNode.execute(frame, filename));
                }
            }
            if (myerrno != null && strerror != null) {
                return PythonUtils.format("[Error %s] %s", myerrno, strerror);
            }
            return baseStrNode.execute(frame, self);
        }
    }

    @Builtin(name = __REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class OSErrorReduceNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object reduce(VirtualFrame frame, PBaseException self,
                        @Cached GetClassNode getClassNode,
                        @Cached GetDictIfExistsNode getDictNode,
                        @Cached SequenceStorageNodes.GetItemNode getItemNode,
                        @Cached SequenceStorageNodes.LenNode lenNode) {
            assert self.getData() instanceof OSErrorData;
            OSErrorData data = (OSErrorData) self.getData();
            PTuple args = self.getArgs();
            if (lenNode.execute(args.getSequenceStorage()) == 2 && data.getFilename() != null) {
                Object[] argData = new Object[data.getFilename2() != null ? 5 : 3];
                argData[0] = getItemNode.execute(frame, args.getSequenceStorage(), 0);
                argData[1] = getItemNode.execute(frame, args.getSequenceStorage(), 1);
                argData[2] = data.getFilename();
                if (data.getFilename2() != null) {
                    // This tuple is essentially used as OSError(*args). So, to recreate filename2,
                    // we need to pass in winerror as well
                    argData[3] = PNone.NONE;
                    argData[4] = data.getFilename2();
                }
                args = factory().createTuple(argData);
            }

            final Object type = getClassNode.execute(self);
            final PDict dict = getDictNode.execute(self);
            if (dict != null) {
                return factory().createTuple(new Object[]{type, args, dict});
            } else {
                return factory().createTuple(new Object[]{type, args});
            }
        }
    }
}
