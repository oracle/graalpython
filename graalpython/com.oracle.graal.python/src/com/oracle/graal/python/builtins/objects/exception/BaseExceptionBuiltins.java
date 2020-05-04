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

import static com.oracle.graal.python.nodes.SpecialAttributeNames.__CAUSE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__CONTEXT__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__TRACEBACK__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INIT__;

import java.util.IllegalFormatException;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.expression.CastToListExpressionNode.CastToListNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetLazyClassNode;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.formatting.ErrorMessageFormatter;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PBaseException)
public class BaseExceptionBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return BaseExceptionBuiltinsFactory.getFactories();
    }

    @Builtin(name = __INIT__, minNumOfPositionalArgs = 1, takesVarArgs = true)
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonBuiltinNode {
        @Specialization
        Object init(PBaseException self, Object[] args) {
            self.setArgs(factory().createTuple(args));
            return PNone.NONE;
        }
    }

    @Builtin(name = "args", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    public abstract static class ArgsNode extends PythonBuiltinNode {

        @Child private GetLazyClassNode getClassNode;

        private final ErrorMessageFormatter formatter = new ErrorMessageFormatter();

        private GetLazyClassNode getGetClassNode() {
            if (getClassNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getClassNode = insert(GetLazyClassNode.create());
            }
            return getClassNode;
        }

        @TruffleBoundary
        private String getFormattedMessage(String format, Object... args) {
            try {
                // pre-format for custom error message formatter
                if (ErrorMessageFormatter.containsCustomSpecifier(format)) {
                    return formatter.format(getGetClassNode(), format, args);
                }
                return String.format(format, args);
            } catch (IllegalFormatException e) {
                throw new RuntimeException("error while formatting \"" + format + "\"", e);
            }
        }

        @Specialization(guards = "isNoValue(none)")
        public Object args(PBaseException self, @SuppressWarnings("unused") PNone none,
                        @Cached("createBinaryProfile()") ConditionProfile nullArgsProfile,
                        @Cached("createBinaryProfile()") ConditionProfile hasMessageFormat) {
            PTuple args = self.getArgs();
            if (nullArgsProfile.profile(args == null)) {
                if (hasMessageFormat.profile(!self.hasMessageFormat())) {
                    args = factory().createEmptyTuple();
                } else {
                    // lazily format the exception message:
                    args = factory().createTuple(new Object[]{getFormattedMessage(self.getMessageFormat(), self.getMessageArgs())});
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

        public abstract Object executeObject(VirtualFrame frame, Object excObj);
    }

    @Builtin(name = __CAUSE__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    public abstract static class CauseNode extends PythonBuiltinNode {
        @Specialization(guards = "isNoValue(value)")
        public Object cause(PBaseException self, @SuppressWarnings("unused") PNone value,
                        @Cached("create()") ReadAttributeFromObjectNode readCause) {
            Object cause = readCause.execute(self, __CAUSE__);
            if (cause == PNone.NO_VALUE) {
                return PNone.NONE;
            } else {
                return cause;
            }
        }

        @Specialization
        public Object cause(PBaseException self, PBaseException value,
                        @Cached("create()") WriteAttributeToObjectNode writeCause) {
            writeCause.execute(self, __CAUSE__, value);
            return PNone.NONE;
        }
    }

    @Builtin(name = __CONTEXT__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    public abstract static class ContextNode extends PythonBuiltinNode {
        @Specialization(guards = "isNoValue(value)")
        public Object context(PBaseException self, @SuppressWarnings("unused") PNone value,
                        @Cached("create()") ReadAttributeFromObjectNode readContext) {
            Object context = readContext.execute(self, __CONTEXT__);
            if (context == PNone.NO_VALUE) {
                return PNone.NONE;
            } else {
                return context;
            }
        }

        @Specialization
        public Object context(PBaseException self, PBaseException value,
                        @Cached("create()") WriteAttributeToObjectNode writeContext) {
            writeContext.execute(self, __CONTEXT__, value);
            return PNone.NONE;
        }
    }

    @Builtin(name = "__suppress_context__", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class SuppressContextNode extends PythonBuiltinNode {

        @Specialization
        public boolean suppressContext(@SuppressWarnings("unused") PBaseException self) {
            return false;
        }
    }

    @Builtin(name = __TRACEBACK__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    public abstract static class TracebackNode extends PythonBuiltinNode {

        @Specialization(guards = "isNoValue(tb)")
        public Object getTraceback(VirtualFrame frame, PBaseException self, @SuppressWarnings("unused") Object tb,
                        @Cached GetTracebackNode getTracebackNode) {
            PTraceback traceback = getTracebackNode.execute(frame, self);
            return traceback == null ? PNone.NONE : traceback;
        }

        @Specialization
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
            throw raise(PythonErrorType.TypeError, "__traceback__ must be a traceback or None");
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
}
