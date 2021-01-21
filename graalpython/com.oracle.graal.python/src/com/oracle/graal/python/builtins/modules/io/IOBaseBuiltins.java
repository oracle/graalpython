package com.oracle.graal.python.builtins.modules.io;

import static com.oracle.graal.python.nodes.SpecialMethodNames.FILENO;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ENTER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EXIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEXT__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.StopIteration;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.SetAttributeNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PIOBase)
public class IOBaseBuiltins extends PythonBuiltins {

    static final String IOBASE_CLOSED = "__IOBase_closed";
    static final String CLOSED = "closed";
    static final String SEEKABLE = "seekable";
    static final String READABLE = "readable";
    static final String WRITABLE = "writable";
    static final String CLOSE = "close";
    static final String FLUSH = "flush";
    static final String SEEK = "seek";
    static final String TELL = "tell";
    static final String TRUNCATE = "truncate";
    static final String READLINE = "readline";
    static final String WRITE = "write";

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return IOBaseBuiltinsFactory.getFactories();
    }

    @Builtin(name = CLOSED, minNumOfPositionalArgs = 1, parameterNames = {"$self"}, isGetter = true)
    @GenerateNodeFactory
    abstract static class ClosedNode extends PythonUnaryBuiltinNode {

        @Specialization(limit = "1")
        static boolean closed(VirtualFrame frame, PythonObject self,
                        @CachedLibrary("self") PythonObjectLibrary lib) {
            return isClosed(self, frame, lib);
        }
    }

    @Builtin(name = SEEKABLE, minNumOfPositionalArgs = 1, parameterNames = {"$self"})
    @GenerateNodeFactory
    abstract static class SeekableNode extends PythonUnaryBuiltinNode {

        @Specialization
        static boolean seekable(@SuppressWarnings("unused") PythonObject self) {
            return false;
        }
    }

    @Builtin(name = READABLE, minNumOfPositionalArgs = 1, parameterNames = {"$self"})
    @GenerateNodeFactory
    abstract static class ReadableNode extends PythonUnaryBuiltinNode {

        @Specialization
        static boolean readable(@SuppressWarnings("unused") PythonObject self) {
            return false;
        }
    }

    @Builtin(name = WRITABLE, minNumOfPositionalArgs = 1, parameterNames = {"$self"})
    @GenerateNodeFactory
    abstract static class WritableNode extends PythonUnaryBuiltinNode {

        @Specialization
        static boolean writable(@SuppressWarnings("unused") PythonObject self) {
            return false;
        }
    }

    @Builtin(name = "_checkClosed", minNumOfPositionalArgs = 1, parameterNames = {"$self"})
    @ImportStatic(IOBaseBuiltins.class)
    @GenerateNodeFactory
    abstract static class CheckClosedNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "3")
        Object doCheckClosed(VirtualFrame frame, PythonObject self,
                        @CachedLibrary("self") PythonObjectLibrary selfLib,
                        @CachedLibrary(limit = "1") PythonObjectLibrary resultLib) {
            if (resultLib.isTrue(selfLib.lookupAttributeStrict(self, frame, CLOSED), frame)) {
                throw raise(ValueError, ErrorMessages.IO_CLOSED);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "_checkSeekable", minNumOfPositionalArgs = 1, parameterNames = {"$self"})
    @ImportStatic(IOBaseBuiltins.class)
    @GenerateNodeFactory
    abstract static class CheckSeekableNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "3")
        boolean doCheckSeekable(VirtualFrame frame, PythonObject self,
                        @CachedLibrary("self") PythonObjectLibrary lib,
                        @Cached ReadAttributeFromObjectNode readNode) {
            Object v = lib.lookupAndCallRegularMethod(self, frame, SEEKABLE);
            if (v instanceof Boolean && (Boolean) v) {
                return true;
            }
            throw unsupported(getRaiseNode(), getCore(), readNode, ErrorMessages.FILE_OR_STREAM_IS_NOT_SEEKABLE);
        }
    }

    @Builtin(name = "_checkReadable", minNumOfPositionalArgs = 1, parameterNames = {"$self"})
    @ImportStatic(IOBaseBuiltins.class)
    @GenerateNodeFactory
    abstract static class CheckReadableNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "3")
        boolean doCheckReadable(VirtualFrame frame, PythonObject self,
                        @CachedLibrary("self") PythonObjectLibrary lib,
                        @Cached ReadAttributeFromObjectNode readNode) {
            Object v = lib.lookupAndCallRegularMethod(self, frame, READABLE);
            if (v instanceof Boolean && (Boolean) v) {
                return true;
            }
            throw unsupported(getRaiseNode(), getCore(), readNode, ErrorMessages.FILE_OR_STREAM_IS_NOT_READABLE);
        }
    }

    @Builtin(name = "_checkWritable", minNumOfPositionalArgs = 1, parameterNames = {"$self"})
    @ImportStatic(IOBaseBuiltins.class)
    @GenerateNodeFactory
    abstract static class CheckWritableNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "3")
        boolean doCheckWritable(VirtualFrame frame, PythonObject self,
                        @CachedLibrary("self") PythonObjectLibrary lib,
                        @Cached ReadAttributeFromObjectNode readNode) {
            Object v = lib.lookupAndCallRegularMethod(self, frame, WRITABLE);
            if (v instanceof Boolean && (Boolean) v) {
                return true;
            }
            // TODO create readNode lazily? (Also in CheckReadable, CheckSeekable)
            throw unsupported(getRaiseNode(), getCore(), readNode, ErrorMessages.FILE_OR_STREAM_IS_NOT_WRITABLE);
        }
    }

    @Builtin(name = CLOSE, minNumOfPositionalArgs = 1, parameterNames = {"$self"})
    @ImportStatic(IOBaseBuiltins.class)
    @GenerateNodeFactory
    abstract static class CloseNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "3")
        PNone close(VirtualFrame frame, PythonObject self,
                        @CachedLibrary("self") PythonObjectLibrary lib,
                        @Cached("create(IOBASE_CLOSED)") SetAttributeNode setAttributeNode,
                        @Cached BranchProfile errorProfile) {
            if (!isClosed(self, frame, lib)) {
                try {
                    lib.lookupAndCallRegularMethod(self, frame, FLUSH);
                } catch (PException e) {
                    errorProfile.enter();
                    try {
                        setAttributeNode.executeVoid(frame, self, true);
                    } catch (PException e1) {
                        PBaseException ee = e1.getEscapedException();
                        ee.setContext(e.setCatchingFrameAndGetEscapedException(frame, this));
                        throw getRaiseNode().raiseExceptionObject(ee, getContext().getLanguage());
                    }
                    throw e;
                }
                setAttributeNode.executeVoid(frame, self, true);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = FLUSH, minNumOfPositionalArgs = 1, parameterNames = {"$self"})
    @ImportStatic(IOBaseBuiltins.class)
    @GenerateNodeFactory
    abstract static class FlushNode extends PythonUnaryBuiltinNode {

        @Specialization(limit = "1")
        PNone flush(VirtualFrame frame, PythonObject self,
                        @CachedLibrary("self") PythonObjectLibrary lib,
                        @Cached ConditionProfile closedProfile) {
            if (closedProfile.profile(isClosed(self, frame, lib))) {
                throw raise(ValueError, ErrorMessages.IO_CLOSED);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = SEEK, minNumOfPositionalArgs = 1, takesVarArgs = true)
    @GenerateNodeFactory
    abstract static class SeekNode extends PythonBuiltinNode {
        @Specialization
        Object seek(@SuppressWarnings("unused") PythonObject self,
                        @Cached ReadAttributeFromObjectNode readNode) {
            throw unsupported(getRaiseNode(), getCore(), readNode, SEEK);
        }
    }

    @Builtin(name = TRUNCATE, minNumOfPositionalArgs = 1, takesVarArgs = true)
    @GenerateNodeFactory
    abstract static class TruncateNode extends PythonBuiltinNode {
        @Specialization
        Object truncate(@SuppressWarnings("unused") PythonObject self,
                        @Cached ReadAttributeFromObjectNode readNode) {
            throw unsupported(getRaiseNode(), getCore(), readNode, TRUNCATE);
        }
    }

    @Builtin(name = TELL, minNumOfPositionalArgs = 1, parameterNames = {"$self"})
    @ImportStatic(IOBaseBuiltins.class)
    @GenerateNodeFactory
    abstract static class TellNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "1")
        Object tell(VirtualFrame frame, PythonObject self,
                        @CachedLibrary("self") PythonObjectLibrary lib) {
            return lib.lookupAndCallRegularMethod(self, frame, SEEK, 0, 1);
        }
    }

    @Builtin(name = __ENTER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class EnterNode extends PythonUnaryBuiltinNode {
        @Specialization
        PythonObject enter(VirtualFrame frame, PythonObject self,
                        @Cached CheckClosedNode checkClosedNode) {
            checkClosedNode.call(frame, self);
            return self;
        }
    }

    @Builtin(name = __EXIT__, minNumOfPositionalArgs = 1, takesVarArgs = true)
    @ImportStatic(IOBaseBuiltins.class)
    @GenerateNodeFactory
    abstract static class ExitNode extends PythonBuiltinNode {
        @Specialization(limit = "3")
        Object exit(VirtualFrame frame, PythonObject self,
                        @CachedLibrary("self") PythonObjectLibrary lib) {
            return lib.lookupAndCallRegularMethod(self, frame, CLOSE);
        }
    }

    @Builtin(name = FILENO, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class FilenoNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object fileno(@SuppressWarnings("unused") PythonObject self,
                        @Cached ReadAttributeFromObjectNode readNode) {
            throw unsupported(getRaiseNode(), getCore(), readNode, FILENO);
        }
    }

    @Builtin(name = "isatty", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsattyNode extends PythonUnaryBuiltinNode {
        @Specialization
        boolean isatty(VirtualFrame frame, PythonObject self,
                        @Cached CheckClosedNode checkClosedNode) {
            checkClosedNode.call(frame, self);
            return false;
        }
    }

    @Builtin(name = __ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        PythonObject iter(VirtualFrame frame, PythonObject self,
                        @Cached CheckClosedNode checkClosedNode) {
            checkClosedNode.call(frame, self);
            return self;
        }
    }

    @Builtin(name = __NEXT__, minNumOfPositionalArgs = 1)
    @ImportStatic(IOBaseBuiltins.class)
    @GenerateNodeFactory
    abstract static class NextNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "3")
        Object next(VirtualFrame frame, PythonObject self,
                        @CachedLibrary("self") PythonObjectLibrary selfLib,
                        @CachedLibrary(limit = "1") PythonObjectLibrary lineLib) {
            Object line = selfLib.lookupAndCallRegularMethod(self, frame, READLINE);
            if (lineLib.lengthWithState(line, PArguments.getThreadState(frame)) <= 0) {
                throw raise(StopIteration);
            }
            return line;
        }
    }

    @Builtin(name = "writelines", minNumOfPositionalArgs = 2, parameterNames = {"$self", "lines"})
    @ImportStatic(IOBaseBuiltins.class)
    @GenerateNodeFactory
    abstract static class WriteLinesNode extends PythonBinaryBuiltinNode {
        @Specialization(limit = "2")
        Object writeLines(VirtualFrame frame, PythonObject self, Object lines,
                        @Cached CheckClosedNode checkClosedNode,
                        @Cached GetNextNode getNextNode,
                        @Cached IsBuiltinClassProfile errorProfile,
                        @CachedLibrary("self") PythonObjectLibrary libSelf,
                        @CachedLibrary("lines") PythonObjectLibrary libLines) {
            checkClosedNode.call(frame, self);
            Object iter = libLines.getIteratorWithFrame(lines, frame);
            while (true) {
                Object line;
                try {
                    line = getNextNode.execute(frame, iter);
                } catch (PException e) {
                    e.expectStopIteration(errorProfile);
                    break;
                }
                libSelf.lookupAndCallRegularMethod(self, frame, WRITE, line);
                // TODO _PyIO_trap_eintr (see _io__IOBase_writelines) (signal and EINTR handling
                // needs to be reviewed & fixed anyway, even in the posix module)
            }
            return PNone.NONE;
        }
    }

    /**
     * Equivalent of {@code iobase_is_closed}.
     * 
     * @param self the IOBase instance
     * @return true if the {@link #IOBASE_CLOSED} attribute exists
     */
    private static boolean isClosed(PythonObject self, VirtualFrame frame, PythonObjectLibrary lib) {
        return !PGuards.isNoValue(lib.lookupAttribute(self, frame, IOBASE_CLOSED));
    }

    /**
     * Equivalent of {@code iobase_unsupported}.
     */
    private static PException unsupported(PRaiseNode raiseNode, PythonCore core, ReadAttributeFromObjectNode readNode, String message) {
        // TODO for now we look up UnsupportedOperation in _io everytime, but CPython does this only
        // once (i.e. ignores changes to _io.UnsupportedOpertaion made by the user)
        // we should do this in postinitialize of the _io module and store the resolved class
        // somewhere (in a hidden key?).
        // This method will still need to use lookupBuiltinModule, but that should be fine for now.
        throw raiseNode.raise(readNode.execute(core.lookupBuiltinModule("_io"), "UnsupportedOperation"), message);
    }
}
