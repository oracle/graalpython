/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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
package com.oracle.graal.python.builtins.objects.exception;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__CAUSE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__CONTEXT__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DICT__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__SUPPRESS_CONTEXT__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__TRACEBACK__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REDUCE__;

import java.util.IllegalFormatException;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.PHashingCollection;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.argument.ReadArgumentNode;
import com.oracle.graal.python.nodes.expression.CastToListExpressionNode.CastToListNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaBooleanNode;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.formatting.ErrorMessageFormatter;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PBaseException)
public class BaseExceptionBuiltins extends PythonBuiltins {

    protected static boolean isBaseExceptionOrNone(Object obj) {
        return obj instanceof PBaseException || PGuards.isNone(obj);
    }

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return BaseExceptionBuiltinsFactory.getFactories();
    }

    @Builtin(name = __INIT__, minNumOfPositionalArgs = 1, takesVarArgs = true)
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonBuiltinNode {
        @Specialization(guards = "args.length == 0")
        Object initNoArgs(@SuppressWarnings("unused") PBaseException self, @SuppressWarnings("unused") Object[] args) {
            return PNone.NONE;
        }

        @Specialization(guards = "args.length != 0")
        Object initArgs(PBaseException self, Object[] args) {
            self.setArgs(factory().createTuple(args));
            return PNone.NONE;
        }
    }

    @Builtin(name = "args", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    public abstract static class ArgsNode extends PythonBuiltinNode {

        private final ErrorMessageFormatter formatter = new ErrorMessageFormatter();

        @TruffleBoundary
        private String getFormattedMessage(PythonObjectLibrary lib, String format, Object... args) {
            try {
                // pre-format for custom error message formatter
                if (ErrorMessageFormatter.containsCustomSpecifier(format)) {
                    return formatter.format(lib, format, args);
                }
                return String.format(format, args);
            } catch (IllegalFormatException e) {
                throw new RuntimeException("error while formatting \"" + format + "\"", e);
            }
        }

        @Specialization(guards = "isNoValue(none)")
        public Object args(PBaseException self, @SuppressWarnings("unused") PNone none,
                        @CachedLibrary(limit = "3") PythonObjectLibrary lib,
                        @Cached("createBinaryProfile()") ConditionProfile nullArgsProfile,
                        @Cached("createBinaryProfile()") ConditionProfile hasMessageFormat) {
            PTuple args = self.getArgs();
            if (nullArgsProfile.profile(args == null)) {
                if (hasMessageFormat.profile(!self.hasMessageFormat())) {
                    args = factory().createEmptyTuple();
                } else {
                    // lazily format the exception message:
                    args = factory().createTuple(new Object[]{getFormattedMessage(lib, self.getMessageFormat(), self.getMessageArgs())});
                }
                self.setArgs(args);
            }
            return args;
        }

        @Specialization(guards = "!isNoValue(value)")
        public Object args(VirtualFrame frame, PBaseException self, Object value,
                        @Cached("create()") CastToListNode castToList) {
            PList list = castToList.execute(frame, value);
            self.setArgs(factory().createTuple(list.getSequenceStorage().getCopyOfInternalArray()));
            return PNone.NONE;
        }

        public abstract Object executeObject(VirtualFrame frame, Object excObj, Object value);

        public static ArgsNode create() {
            return BaseExceptionBuiltinsFactory.ArgsNodeFactory.create(new ReadArgumentNode[]{});
        }
    }

    @Builtin(name = __CAUSE__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    @ImportStatic(BaseExceptionBuiltins.class)
    public abstract static class CauseNode extends PythonBuiltinNode {
        @Specialization(guards = "isNoValue(value)")
        public Object getCause(PBaseException self, @SuppressWarnings("unused") PNone value) {
            return self.getCause() != null ? self.getCause() : PNone.NONE;
        }

        @Specialization
        public Object setCause(PBaseException self, PBaseException value) {
            self.setCause(value);
            return PNone.NONE;
        }

        @Specialization(guards = "isNone(value)")
        public Object setCause(PBaseException self, @SuppressWarnings("unused") PNone value) {
            self.setCause(null);
            return PNone.NONE;
        }

        @Specialization(guards = "!isBaseExceptionOrNone(value)")
        public Object cause(@SuppressWarnings("unused") PBaseException self, @SuppressWarnings("unused") Object value,
                        @Cached("create()") PRaiseNode raise) {
            throw raise.raise(TypeError, ErrorMessages.EXCEPTION_CAUSE_MUST_BE_NONE_OR_DERIVE_FROM_BASE_EX);
        }
    }

    @Builtin(name = __CONTEXT__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @ImportStatic(BaseExceptionBuiltins.class)
    @GenerateNodeFactory
    public abstract static class ContextNode extends PythonBuiltinNode {
        @Specialization(guards = "isNoValue(value)")
        public Object getContext(PBaseException self, @SuppressWarnings("unused") PNone value) {
            return self.getContext() != null ? self.getContext() : PNone.NONE;
        }

        @Specialization
        public Object setContext(PBaseException self, PBaseException value) {
            self.setContext(value);
            return PNone.NONE;
        }

        @Specialization(guards = "isNone(value)")
        public Object setContext(PBaseException self, @SuppressWarnings("unused") PNone value) {
            self.setContext(null);
            return PNone.NONE;
        }

        @Specialization(guards = "!isBaseExceptionOrNone(value)")
        public Object context(@SuppressWarnings("unused") PBaseException self, @SuppressWarnings("unused") Object value,
                        @Cached("create()") PRaiseNode raise) {
            throw raise.raise(TypeError, ErrorMessages.EXCEPTION_CAUSE_MUST_BE_NONE_OR_DERIVE_FROM_BASE_EX);
        }
    }

    @Builtin(name = __SUPPRESS_CONTEXT__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    public abstract static class SuppressContextNode extends PythonBuiltinNode {
        @Specialization(guards = "isNoValue(value)")
        public Object getSuppressContext(PBaseException self, @SuppressWarnings("unused") PNone value) {
            return self.getSuppressContext();
        }

        @Specialization
        public Object setSuppressContext(PBaseException self, boolean value) {
            self.setSuppressContext(value);
            return PNone.NONE;
        }

        @Specialization(guards = "!isBoolean(value)")
        public Object setSuppressContext(PBaseException self, Object value,
                        @Cached CastToJavaBooleanNode castToJavaBooleanNode) {
            try {
                self.setSuppressContext(castToJavaBooleanNode.execute(value));
            } catch (CannotCastException e) {
                throw raise(TypeError, ErrorMessages.ATTR_VALUE_MUST_BE_BOOL);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = __TRACEBACK__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    public abstract static class TracebackNode extends PythonBuiltinNode {

        @Specialization(guards = "isNoValue(tb)")
        public Object getTraceback(VirtualFrame frame, PBaseException self, @SuppressWarnings("unused") Object tb,
                        @Cached GetExceptionTracebackNode getExceptionTracebackNode) {
            PTraceback traceback = getExceptionTracebackNode.execute(frame, self);
            return traceback == null ? PNone.NONE : traceback;
        }

        @Specialization(guards = "!isNoValue(tb)")
        public Object setTraceback(PBaseException self, @SuppressWarnings("unused") PNone tb) {
            self.clearTraceback();
            return PNone.NONE;
        }

        @Specialization
        public Object setTraceback(PBaseException self, PTraceback tb) {
            self.setTraceback(tb);
            return PNone.NONE;
        }

        @Fallback
        public Object setTraceback(@SuppressWarnings("unused") Object self, @SuppressWarnings("unused") Object tb) {
            throw raise(PythonErrorType.TypeError, ErrorMessages.MUST_BE_S_OR_S, "__traceback__", "a traceback", "None");
        }
    }

    @Builtin(name = "with_traceback", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class WithTracebackNode extends PythonBinaryBuiltinNode {

        @Specialization
        PBaseException doClearTraceback(PBaseException self, @SuppressWarnings("unused") PNone tb) {
            self.clearTraceback();
            return self;
        }

        @Specialization
        PBaseException doSetTraceback(PBaseException self, PTraceback tb) {
            self.setTraceback(tb);
            return self;
        }
    }

    @Builtin(name = __DICT__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class DictNode extends PythonBinaryBuiltinNode {
        @Specialization(limit = "1")
        PNone dict(PBaseException self, PHashingCollection mapping,
                        @CachedLibrary("self") PythonObjectLibrary lib) {
            try {
                lib.setDict(self, mapping);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException(e);
            }
            return PNone.NONE;
        }

        @Specialization(guards = "isNoValue(mapping)", limit = "1")
        Object dict(PBaseException self, @SuppressWarnings("unused") PNone mapping,
                        @CachedLibrary("self") PythonObjectLibrary lib) {
            PHashingCollection dict = lib.getDict(self);
            if (dict == null) {
                dict = factory().createDictFixedStorage(self);
                try {
                    lib.setDict(self, dict);
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new IllegalStateException(e);
                }
            }
            return dict;
        }
    }

    @Builtin(name = __REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "1")
        Object reduce(VirtualFrame frame, PBaseException self,
                        @CachedLibrary("self") PythonObjectLibrary lib,
                        @Cached ArgsNode argsNode) {
            Object clazz = lib.getLazyPythonClass(self);
            Object args = argsNode.executeObject(frame, self, PNone.NO_VALUE);
            Object dict = lib.lookupAttribute(self, __DICT__);
            return factory().createTuple(new Object[]{clazz, args, dict});
        }
    }
}
