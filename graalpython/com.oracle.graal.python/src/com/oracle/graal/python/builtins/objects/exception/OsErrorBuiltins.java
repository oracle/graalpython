/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.objects.exception.OSErrorEnum.ECHILD;
import static com.oracle.graal.python.builtins.objects.exception.OSErrorEnum.ECONNABORTED;
import static com.oracle.graal.python.builtins.objects.exception.OSErrorEnum.ECONNREFUSED;
import static com.oracle.graal.python.builtins.objects.exception.OSErrorEnum.ECONNRESET;
import static com.oracle.graal.python.builtins.objects.exception.OSErrorEnum.EEXIST;
import static com.oracle.graal.python.builtins.objects.exception.OSErrorEnum.EINTR;
import static com.oracle.graal.python.builtins.objects.exception.OSErrorEnum.EISDIR;
import static com.oracle.graal.python.builtins.objects.exception.OSErrorEnum.ENOENT;
import static com.oracle.graal.python.builtins.objects.exception.OSErrorEnum.ENOTDIR;
import static com.oracle.graal.python.builtins.objects.exception.OSErrorEnum.ESRCH;
import static com.oracle.graal.python.builtins.objects.exception.OSErrorEnum.ETIMEDOUT;
import static com.oracle.graal.python.nodes.ErrorMessages.P_TAKES_NO_KEYWORD_ARGS;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___STR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___NEW__;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

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
import com.oracle.graal.python.builtins.objects.exception.BaseExceptionBuiltins.BaseExceptionInitNode;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.str.StringUtils.SimpleTruffleStringFormatNode;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.lib.PyArgCheckPositionalNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyNumberCheckNode;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectReprAsTruffleStringNode;
import com.oracle.graal.python.lib.PyObjectStrAsTruffleStringNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetDictIfExistsNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

@CoreFunctions(extendClasses = PythonBuiltinClassType.OSError)
public final class OsErrorBuiltins extends PythonBuiltins {
    static final int ARGS_MIN = 2;
    static final int ARGS_MAX = 5;

    public static final int IDX_ERRNO = 0;
    public static final int IDX_STRERROR = 1;
    public static final int IDX_FILENAME = 2;
    public static final int IDX_WINERROR = 3;
    public static final int IDX_FILENAME2 = 4;
    public static final int IDX_WRITTEN = 5;
    public static final int OS_ERR_NUM_ATTRS = IDX_WRITTEN + 1;

    public static final BaseExceptionAttrNode.StorageFactory OS_ERROR_ATTR_FACTORY = (args, factory) -> {
        final Object[] attrs = new Object[OS_ERR_NUM_ATTRS];
        attrs[IDX_WRITTEN] = -1;
        return attrs;
    };

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return OsErrorBuiltinsFactory.getFactories();
    }

    @Override
    public void postInitialize(Python3Core core) {
        super.postInitialize(core);
        core.registerTypeInBuiltins(tsLiteral("EnvironmentError"), PythonBuiltinClassType.OSError);
        core.registerTypeInBuiltins(tsLiteral("IOError"), PythonBuiltinClassType.OSError);
    }

    static boolean osErrorUseInit(VirtualFrame frame, Node inliningTarget, Python3Core core, Object type, PyObjectGetAttr getAttr) {
        // When __init__ is defined in an OSError subclass, we want any extraneous argument
        // to __new__ to be ignored. The only reasonable solution, given __new__ takes a
        // variable number of arguments, is to defer arg parsing and initialization to __init__.
        // But when __new__ is overridden as well, it should call our __new__ with the right
        // arguments.
        //
        // (see http://bugs.python.org/issue12555#msg148829 )
        final PythonBuiltinClass osErrorType = core.lookupType(PythonBuiltinClassType.OSError);
        final Object tpInit = getAttr.execute(frame, inliningTarget, type, T___INIT__);
        final Object tpNew = getAttr.execute(frame, inliningTarget, type, T___NEW__);
        final Object osErrInit = getAttr.execute(frame, inliningTarget, osErrorType, T___INIT__);
        final Object osErrNew = getAttr.execute(frame, inliningTarget, osErrorType, T___NEW__);
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

    public static OSErrorEnum errorType2errno(PythonBuiltinClassType type) {
        switch (type) {
            case IsADirectoryError:
                return EISDIR;
            case ChildProcessError:
                return ECHILD;
            case ConnectionAbortedError:
                return ECONNABORTED;
            case ConnectionRefusedError:
                return ECONNREFUSED;
            case ConnectionResetError:
                return ECONNRESET;
            case FileExistsError:
                return EEXIST;
            case FileNotFoundError:
                return ENOENT;
            case NotADirectoryError:
                return ENOTDIR;
            case InterruptedError:
                return EINTR;
            case ProcessLookupError:
                return ESRCH;
            case TimeoutError:
                return ETIMEDOUT;
            default:
                return null;
        }
    }

    static void osErrorInit(Frame frame, Node inliningTarget, PBaseException self, Object type, Object[] args, Object[] parsedArgs, PyNumberCheckNode pyNumberCheckNode,
                    PyNumberAsSizeNode pyNumberAsSizeNode, BaseExceptionInitNode baseInitNode) {
        Object[] pArgs = args;

        // filename will remain None otherwise
        Object filename = parsedArgs[IDX_FILENAME];
        Object filename2 = parsedArgs[IDX_FILENAME2];
        if (filename != null && filename != PNone.NONE) {
            if (type == PythonBuiltinClassType.BlockingIOError &&
                            pyNumberCheckNode.execute(inliningTarget, filename)) {
                // BlockingIOError's 3rd argument can be the number of characters written.
                parsedArgs[IDX_WRITTEN] = (pyNumberAsSizeNode.executeExact(frame, inliningTarget, filename, PythonBuiltinClassType.ValueError));
            } else {
                parsedArgs[IDX_FILENAME] = filename;
                if (filename2 != null && filename2 != PNone.NONE) {
                    parsedArgs[IDX_FILENAME2] = filename2;
                }
                if (args.length >= 2 && args.length <= 5) {
                    // filename, filename2, and winerror are removed from the args tuple (for
                    // compatibility purposes, see test_exceptions.py)
                    pArgs = PythonUtils.arrayCopyOfRange(args, 0, 2);
                }
            }
        }
        baseInitNode.execute(self, pArgs);
        self.setExceptionAttributes(parsedArgs);
    }

    static Object[] osErrorParseArgs(Object[] args, Node inliningTarget, PyArgCheckPositionalNode checkPositionalNode) {
        Object[] parsed = new Object[OS_ERR_NUM_ATTRS];
        if (args.length >= 2 && args.length <= 5) {
            checkPositionalNode.execute(inliningTarget, PythonBuiltinClassType.OSError.getPrintName(), args, ARGS_MIN, ARGS_MAX);
            PythonUtils.arraycopy(args, 0, parsed, 0, args.length);
        }
        parsed[IDX_WRITTEN] = -1;
        return parsed;
    }

    @Builtin(name = J___NEW__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    protected abstract static class OSErrorNewNode extends PythonBuiltinNode {
        @Specialization
        Object newCData(VirtualFrame frame, Object subType, Object[] args, PKeyword[] kwds,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetAttr getAttr,
                        @Cached PyNumberCheckNode pyNumberCheckNode,
                        @Cached PyNumberAsSizeNode pyNumberAsSizeNode,
                        @Cached PyArgCheckPositionalNode checkPositionalNode,
                        @Cached BaseExceptionBuiltins.BaseExceptionInitNode baseInitNode,
                        @Cached PythonObjectFactory factory) {
            Object type = subType;
            Object[] parsedArgs = new Object[IDX_WRITTEN + 1];
            final Python3Core core = getContext();
            if (!osErrorUseInit(frame, inliningTarget, core, type, getAttr)) {
                if (kwds.length != 0) {
                    throw raise(PythonBuiltinClassType.TypeError, P_TAKES_NO_KEYWORD_ARGS, type);
                }

                parsedArgs = osErrorParseArgs(args, inliningTarget, checkPositionalNode);
                final Object errnoVal = parsedArgs[IDX_ERRNO];
                if (errnoVal != null && PGuards.canBeInteger(errnoVal) &&
                                subType == PythonBuiltinClassType.OSError) {
                    final int errno = pyNumberAsSizeNode.executeExact(frame, inliningTarget, errnoVal);
                    Object newType = errno2errorType(errno);
                    if (newType != null) {
                        type = newType;
                    }
                }
            }

            PBaseException self = factory.createBaseException(type);
            if (!osErrorUseInit(frame, inliningTarget, core, type, getAttr)) {
                osErrorInit(frame, inliningTarget, self, type, args, parsedArgs, pyNumberCheckNode, pyNumberAsSizeNode, baseInitNode);
            } else {
                self.setArgs(factory.createEmptyTuple());
            }
            return self;
        }
    }

    @Builtin(name = J___INIT__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class OSErrorInitNode extends PythonBuiltinNode {
        public abstract Object execute(VirtualFrame frame, PBaseException self, Object[] args, PKeyword[] kwds);

        @Specialization
        Object initNoArgs(VirtualFrame frame, PBaseException self, Object[] args, PKeyword[] kwds,
                        @Bind("this") Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached PyObjectGetAttr getAttr,
                        @Cached PyNumberCheckNode pyNumberCheckNode,
                        @Cached PyNumberAsSizeNode pyNumberAsSizeNode,
                        @Cached PyArgCheckPositionalNode checkPositionalNode,
                        @Cached BaseExceptionBuiltins.BaseExceptionInitNode baseInitNode) {
            final Object type = getClassNode.execute(inliningTarget, self);
            if (!osErrorUseInit(frame, inliningTarget, getContext(), type, getAttr)) {
                // Everything already done in OSError_new
                return PNone.NONE;
            }

            if (kwds.length != 0) {
                throw raise(PythonBuiltinClassType.TypeError, P_TAKES_NO_KEYWORD_ARGS, type);
            }

            Object[] parsedArgs = osErrorParseArgs(args, inliningTarget, checkPositionalNode);
            osErrorInit(frame, inliningTarget, self, type, args, parsedArgs, pyNumberCheckNode, pyNumberAsSizeNode, baseInitNode);
            return PNone.NONE;
        }

        @NeverDefault
        public static OSErrorInitNode create() {
            return OsErrorBuiltinsFactory.OSErrorInitNodeFactory.create(null);
        }
    }

    @Builtin(name = "errno", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, allowsDelete = true, doc = "POSIX exception code")
    @GenerateNodeFactory
    public abstract static class OSErrorErrnoNode extends PythonBuiltinNode {
        @Specialization
        Object generic(PBaseException self, Object value,
                        @Cached BaseExceptionAttrNode attrNode) {
            return attrNode.execute(self, value, IDX_ERRNO, OS_ERROR_ATTR_FACTORY);
        }
    }

    @Builtin(name = "strerror", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, allowsDelete = true, doc = "exception strerror")
    @GenerateNodeFactory
    public abstract static class OSErrorStrerrorNode extends PythonBuiltinNode {
        @Specialization
        Object generic(PBaseException self, Object value,
                        @Cached BaseExceptionAttrNode attrNode) {
            return attrNode.execute(self, value, IDX_STRERROR, OS_ERROR_ATTR_FACTORY);
        }
    }

    @Builtin(name = "filename", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, allowsDelete = true, doc = "exception filename")
    @GenerateNodeFactory
    public abstract static class OSErrorFilenameNode extends PythonBuiltinNode {
        @Specialization
        Object generic(PBaseException self, Object value,
                        @Cached BaseExceptionAttrNode attrNode) {
            return attrNode.execute(self, value, IDX_FILENAME, OS_ERROR_ATTR_FACTORY);
        }
    }

    @Builtin(name = "filename2", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, allowsDelete = true, doc = "exception filename2")
    @GenerateNodeFactory
    public abstract static class OSErrorFilename2Node extends PythonBuiltinNode {
        @Specialization
        Object generic(PBaseException self, Object value,
                        @Cached BaseExceptionAttrNode attrNode) {
            return attrNode.execute(self, value, IDX_FILENAME2, OS_ERROR_ATTR_FACTORY);
        }
    }

    @Builtin(name = "winerror", os = PythonOS.PLATFORM_WIN32, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, allowsDelete = true, doc = "Win32 exception code")
    @GenerateNodeFactory
    public abstract static class OSErrorWinerrorNode extends PythonBuiltinNode {
        @Specialization
        Object generic(PBaseException self, Object value,
                        @Cached BaseExceptionAttrNode attrNode) {
            Object result = attrNode.execute(self, value, IDX_WINERROR, OS_ERROR_ATTR_FACTORY);
            if (result instanceof PNone) {
                // TODO: fallback to errno for now
                return attrNode.execute(self, value, IDX_ERRNO, OS_ERROR_ATTR_FACTORY);
            } else {
                return result;
            }
        }
    }

    @Builtin(name = "characters_written", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, allowsDelete = true, doc = "exception characters written")
    @GenerateNodeFactory
    public abstract static class OSErrorCharsWrittenNode extends PythonBuiltinNode {
        protected boolean isInvalid(PBaseException self) {
            final Object[] attrs = self.getExceptionAttributes();
            return attrs != null && attrs[IDX_WRITTEN] instanceof Integer && (int) attrs[IDX_WRITTEN] == -1;
        }

        @Specialization(guards = "isInvalid(self)")
        @SuppressWarnings("unused")
        Object generic(PBaseException self, Object value) {
            throw raise(PythonBuiltinClassType.AttributeError, ErrorMessages.CHARACTERS_WRITTEN);
        }

        @Specialization(guards = "!isInvalid(self)")
        Object generic(PBaseException self, Object value,
                        @Cached BaseExceptionAttrNode attrNode) {
            final Object retVal = attrNode.execute(self, value, IDX_WRITTEN, OS_ERROR_ATTR_FACTORY);
            if (PGuards.isDeleteMarker(value)) {
                // reset the internal state
                self.getExceptionAttributes()[IDX_WRITTEN] = -1;
            }
            return retVal;
        }
    }

    @Builtin(name = J___STR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class OSErrorStrNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object str(VirtualFrame frame, PBaseException self,
                        @Bind("this") Node inliningTarget,
                        @Cached BaseExceptionAttrNode attrNode,
                        @Cached BaseExceptionBuiltins.StrNode baseStrNode,
                        @Cached PyObjectStrAsTruffleStringNode strNode,
                        @Cached PyObjectReprAsTruffleStringNode reprNode,
                        @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode) {
            // TODO: missing windows code
            final Object filename = attrNode.get(self, IDX_FILENAME, OS_ERROR_ATTR_FACTORY);
            final Object filename2 = attrNode.get(self, IDX_FILENAME2, OS_ERROR_ATTR_FACTORY);
            final Object errno = attrNode.get(self, IDX_ERRNO, OS_ERROR_ATTR_FACTORY);
            final Object strerror = attrNode.get(self, IDX_STRERROR, OS_ERROR_ATTR_FACTORY);
            if (filename != PNone.NONE) {
                if (filename2 != PNone.NONE) {
                    return simpleTruffleStringFormatNode.format("[Errno %s] %s: %s -> %s",
                                    strNode.execute(frame, inliningTarget, errno != null ? errno : PNone.NONE),
                                    strNode.execute(frame, inliningTarget, strerror != null ? strerror : PNone.NONE),
                                    reprNode.execute(frame, inliningTarget, filename),
                                    reprNode.execute(frame, inliningTarget, filename2));
                } else {
                    return simpleTruffleStringFormatNode.format("[Errno %s] %s: %s",
                                    strNode.execute(frame, inliningTarget, errno != null ? errno : PNone.NONE),
                                    strNode.execute(frame, inliningTarget, strerror != null ? strerror : PNone.NONE),
                                    reprNode.execute(frame, inliningTarget, filename));
                }
            }
            if (errno != PNone.NONE && strerror != PNone.NONE) {
                return simpleTruffleStringFormatNode.format("[Errno %s] %s", strNode.execute(frame, inliningTarget, errno), strNode.execute(frame, inliningTarget, strerror));
            }
            return baseStrNode.execute(frame, self);
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class OSErrorReduceNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object reduce(PBaseException self,
                        @Bind("this") Node inliningTarget,
                        @Cached ExceptionNodes.GetArgsNode getArgsNode,
                        @Cached BaseExceptionAttrNode attrNode,
                        @Cached GetClassNode getClassNode,
                        @Cached GetDictIfExistsNode getDictNode,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItemNode,
                        @Cached PythonObjectFactory factory) {
            PTuple args = getArgsNode.execute(inliningTarget, self);
            final Object filename = attrNode.get(self, IDX_FILENAME, OS_ERROR_ATTR_FACTORY);
            final Object filename2 = attrNode.get(self, IDX_FILENAME2, OS_ERROR_ATTR_FACTORY);
            SequenceStorage argsStorage = args.getSequenceStorage();
            if (argsStorage.length() == 2 && filename != PNone.NONE) {
                Object[] argData = new Object[filename2 != PNone.NONE ? 5 : 3];
                argData[0] = getItemNode.execute(inliningTarget, argsStorage, 0);
                argData[1] = getItemNode.execute(inliningTarget, argsStorage, 1);
                argData[2] = filename;
                if (filename2 != PNone.NONE) {
                    // This tuple is essentially used as OSError(*args). So, to recreate filename2,
                    // we need to pass in winerror as well
                    argData[3] = PNone.NONE;
                    argData[4] = filename2;
                }
                args = factory.createTuple(argData);
            }

            final Object type = getClassNode.execute(inliningTarget, self);
            final PDict dict = getDictNode.execute(self);
            if (dict != null) {
                return factory.createTuple(new Object[]{type, args, dict});
            } else {
                return factory.createTuple(new Object[]{type, args});
            }
        }
    }
}
