/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
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
package com.oracle.graal.python.nodes.statement;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.frame.WriteNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.statement.ExceptNodeFactory.ExceptMatchNodeGen;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.ExceptionHandledException;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;

@GenerateWrapper
public class ExceptNode extends PNodeWithContext implements InstrumentableNode {
    @Child private StatementNode body;
    @Child private WriteNode exceptName;
    @Child private StatementNode exceptNameDelete;
    @Child private ExpressionNode exceptType;

    @Child private ExceptMatchNode matchNode;

    public ExceptNode(StatementNode body, ExpressionNode exceptType, WriteNode exceptName, StatementNode exceptNameDelete) {
        this.body = body;
        this.exceptName = exceptName;
        this.exceptType = exceptType;
        this.exceptNameDelete = exceptNameDelete;
    }

    public ExceptNode(ExceptNode original) {
        this.body = original.body;
        this.exceptName = original.exceptName;
        this.exceptType = original.exceptType;
        this.exceptNameDelete = original.exceptNameDelete;
    }

    public void executeExcept(VirtualFrame frame, Throwable e) {
        if (exceptName != null) {
            if (e instanceof PException) {
                exceptName.executeObject(frame, ((PException) e).getEscapedException());
            } else {
                exceptName.executeObject(frame, e);
            }
        }
        body.executeVoid(frame);
        if (exceptName != null) {
            exceptNameDelete.executeVoid(frame);
        }
        throw ExceptionHandledException.INSTANCE;
    }

    public boolean matchesPException(VirtualFrame frame, PException e) {
        if (exceptType == null) {
            return true;
        }
        return getMatchNode().executeMatch(frame, e, exceptType.execute(frame));
    }

    public boolean matchesTruffleException(@SuppressWarnings("unused") VirtualFrame frame, AbstractTruffleException e) {
        assert !(e instanceof PException);
        if (exceptType == null) {
            return true;
        }
        return getMatchNode().executeMatch(frame, e, exceptType.execute(frame));
    }

    private ExceptMatchNode getMatchNode() {
        if (matchNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            matchNode = insert(ExceptMatchNode.create());
        }
        return matchNode;
    }

    public StatementNode getBody() {
        return body;
    }

    public ExpressionNode getExceptType() {
        return exceptType;
    }

    public WriteNode getExceptName() {
        return exceptName;
    }

    @Override
    public WrapperNode createWrapper(ProbeNode probeNode) {
        return new ExceptNodeWrapper(this, this, probeNode);
    }

    public boolean isInstrumentable() {
        return getSourceSection() != null;
    }

    @ImportStatic(PGuards.class)
    @GenerateUncached
    public abstract static class ExceptMatchNode extends Node {
        public abstract boolean executeMatch(Frame frame, Object exception, Object clause);

        private static void raiseIfNoException(VirtualFrame frame, Object clause, ValidExceptionNode isValidException, PRaiseNode raiseNode) {
            if (!isValidException.execute(frame, clause)) {
                raiseNoException(raiseNode);
            }
        }

        private static void raiseNoException(PRaiseNode raiseNode) {
            throw raiseNode.raise(PythonErrorType.TypeError, ErrorMessages.CATCHING_CLS_NOT_ALLOWED);
        }

        @Specialization(guards = "isClass(clause, lib)", limit = "3")
        static boolean matchPythonSingle(VirtualFrame frame, PException e, Object clause,
                        @SuppressWarnings("unused") @CachedLibrary("clause") InteropLibrary lib,
                        @Cached ValidExceptionNode isValidException,
                        @Cached GetClassNode getClassNode,
                        @Cached IsSubtypeNode isSubtype,
                        @Cached PRaiseNode raiseNode) {
            raiseIfNoException(frame, clause, isValidException, raiseNode);
            return isSubtype.execute(frame, getClassNode.execute(e.getUnreifiedException()), clause);
        }

        @Specialization(guards = {"eLib.isException(e)", "clauseLib.isMetaObject(clause)"}, limit = "3", replaces = "matchPythonSingle")
        @SuppressWarnings("unused")
        static boolean matchJava(VirtualFrame frame, AbstractTruffleException e, Object clause,
                        @Cached ValidExceptionNode isValidException,
                        @CachedLibrary("e") InteropLibrary eLib,
                        @CachedLibrary("clause") InteropLibrary clauseLib,
                        @Cached PRaiseNode raiseNode) {
            // n.b.: we can only allow Java exceptions in clauses, because we cannot tell for other
            // foreign exception types if they *are* exception types
            raiseIfNoException(frame, clause, isValidException, raiseNode);
            try {
                return clauseLib.isMetaInstance(clause, e);
            } catch (UnsupportedMessageException e1) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

        @Specialization
        static boolean matchTuple(VirtualFrame frame, Object e, PTuple clause,
                        @Cached ExceptMatchNode recursiveNode,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItemNode) {
            // check for every type in the tuple
            SequenceStorage storage = clause.getSequenceStorage();
            int length = storage.length();
            for (int i = 0; i < length; i++) {
                Object clauseType = getItemNode.execute(storage, i);
                if (recursiveNode.executeMatch(frame, e, clauseType)) {
                    return true;
                }
            }
            return false;
        }

        @Fallback
        @SuppressWarnings("unused")
        static boolean fallback(VirtualFrame frame, Object e, Object clause,
                        @Cached PRaiseNode raiseNode) {
            raiseNoException(raiseNode);
            return false;
        }

        public static ExceptMatchNode create() {
            return ExceptMatchNodeGen.create();
        }

        public static ExceptMatchNode getUncached() {
            return ExceptMatchNodeGen.getUncached();
        }
    }
}

@GenerateUncached
abstract class ValidExceptionNode extends Node {
    protected abstract boolean execute(Frame frame, Object type);

    protected boolean emulateJython() {
        return PythonLanguage.get(this).getEngineOption(PythonOptions.EmulateJython);
    }

    protected static boolean isPythonExceptionType(PythonBuiltinClassType type) {
        PythonBuiltinClassType base = type;
        while (base != null) {
            if (base == PythonBuiltinClassType.PBaseException) {
                return true;
            }
            base = base.getBase();
        }
        return false;
    }

    @Specialization(guards = "cachedType == type", limit = "3")
    static boolean isPythonExceptionTypeCached(@SuppressWarnings("unused") PythonBuiltinClassType type,
                    @SuppressWarnings("unused") @Cached("type") PythonBuiltinClassType cachedType,
                    @Cached("isPythonExceptionType(type)") boolean isExceptionType) {
        return isExceptionType;
    }

    @Specialization(guards = "cachedType == klass.getType()", limit = "3")
    static boolean isPythonExceptionClassCached(@SuppressWarnings("unused") PythonBuiltinClass klass,
                    @SuppressWarnings("unused") @Cached("klass.getType()") PythonBuiltinClassType cachedType,
                    @Cached("isPythonExceptionType(cachedType)") boolean isExceptionType) {
        return isExceptionType;
    }

    @Specialization(guards = "isTypeNode.execute(type)", limit = "1", replaces = {"isPythonExceptionTypeCached", "isPythonExceptionClassCached"})
    static boolean isPythonException(VirtualFrame frame, Object type,
                    @SuppressWarnings("unused") @Cached TypeNodes.IsTypeNode isTypeNode,
                    @Cached IsSubtypeNode isSubtype) {
        return isSubtype.execute(frame, type, PythonBuiltinClassType.PBaseException);
    }

    protected boolean isHostObject(Object object) {
        return PythonContext.get(this).getEnv().isHostObject(object);
    }

    @Specialization(guards = {"emulateJython()", "isHostObject(type)"})
    @SuppressWarnings("unused")
    boolean isJavaException(@SuppressWarnings("unused") VirtualFrame frame, Object type) {
        Object hostType = PythonContext.get(this).getEnv().asHostObject(type);
        return hostType instanceof Class && Throwable.class.isAssignableFrom((Class<?>) hostType);
    }

    @Fallback
    static boolean isAnException(@SuppressWarnings("unused") VirtualFrame frame, @SuppressWarnings("unused") Object type) {
        return false;
    }

    static ValidExceptionNode create() {
        return ValidExceptionNodeGen.create();
    }
}
