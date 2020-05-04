/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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

import java.util.ArrayList;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.frame.ReadGlobalOrBuiltinNode;
import com.oracle.graal.python.nodes.literal.TupleLiteralNode;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes.ExceptionState;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes.SetCaughtExceptionNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.ExceptionHandledException;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.profiles.ConditionProfile;

@ExportLibrary(InteropLibrary.class)
@ImportStatic(SpecialMethodNames.class)
public class TryExceptNode extends ExceptionHandlingStatementNode implements TruffleObject {
    @Child private StatementNode body;
    @Children private final ExceptNode[] exceptNodes;
    @Child private StatementNode orelse;
    @Child private PythonObjectFactory ofactory;

    private final ConditionProfile everMatched = ConditionProfile.createBinaryProfile();

    @CompilationFinal private Boolean shouldCatchAll = null;
    @CompilationFinal private Boolean shouldCatchJavaExceptions = null;
    @CompilationFinal private ContextReference<PythonContext> contextRef;

    @CompilationFinal private CatchesFunction catchesFunction;

    public TryExceptNode(StatementNode body, ExceptNode[] exceptNodes, StatementNode orelse) {
        this.body = body;
        body.markAsTryBlock();
        this.exceptNodes = exceptNodes;
        this.orelse = orelse;
    }

    protected PythonContext getContext() {
        if (contextRef == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            contextRef = lookupContextReference(PythonLanguage.class);
        }
        return contextRef.get();
    }

    protected PythonObjectFactory factory() {
        if (ofactory == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            ofactory = insert(PythonObjectFactory.create());
        }
        return ofactory;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        // The following statement is a no-op, but it helps graal to optimize the exception handler
        // by moving the cast to PException to the beginning
        saveExceptionState(frame);

        try {
            body.executeVoid(frame);
        } catch (PException ex) {
            if (!catchException(frame, ex)) {
                throw ex;
            }
            return;
        } catch (ControlFlowException e) {
            throw e;
        } catch (Exception | StackOverflowError | AssertionError e) {
            if (shouldCatchJavaExceptions == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                shouldCatchJavaExceptions = getContext().getOption(PythonOptions.EmulateJython);
            }
            if (shouldCatchJavaExceptions && getContext().getEnv().isHostException(e)) {
                boolean handled = catchException(frame, (TruffleException) e);
                if (handled) {
                    return;
                }
            }
            if (shouldCatchAll == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                shouldCatchAll = getContext().getOption(PythonOptions.CatchAllExceptions);
            }
            if (shouldCatchAll) {
                PException pe = PException.fromObject(getBaseException(e), this);
                // Re-attach truffle stacktrace
                pe.initCause(e.getCause());
                // Host exceptions have their stacktrace already filled in, call this to set
                // the cutoff point to the catch site
                pe.getTruffleStackTrace();
                boolean handled = catchException(frame, pe);
                if (handled) {
                    return;
                } else {
                    throw pe;
                }
            }
            throw e;
        }
        orelse.executeVoid(frame);
    }

    @TruffleBoundary
    private PBaseException getBaseException(Throwable e) {
        return factory().createBaseException(PythonErrorType.ValueError, "%m", new Object[]{e});
    }

    @ExplodeLoop(kind = LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN)
    private boolean catchException(VirtualFrame frame, TruffleException exception) {
        try {
            for (ExceptNode exceptNode : exceptNodes) {
                if (everMatched.profile(exceptNode.matchesException(frame, exception))) {
                    tryChainPreexistingException(frame, exception);
                    ExceptionState exceptionState = saveExceptionState(frame);
                    if (exception instanceof PException) {
                        PException pException = (PException) exception;
                        pException.setCatchingFrameReference(frame);
                        SetCaughtExceptionNode.execute(frame, pException);
                    }
                    try {
                        exceptNode.executeExcept(frame, exception);
                    } finally {
                        restoreExceptionState(frame, exceptionState);
                    }
                }
            }
        } catch (ExceptionHandledException eh) {
            return true;
        } catch (PException handlerException) {
            tryChainExceptionFromHandler(handlerException, exception);
            throw handlerException;
        }
        return false;
    }

    public StatementNode getBody() {
        return body;
    }

    public ExceptNode[] getExceptNodes() {
        return exceptNodes;
    }

    public StatementNode getOrelse() {
        return orelse;
    }

    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return getContext().getEnv().asGuestValue(new String[]{StandardTags.TryBlockTag.CATCHES});
    }

    @ExportMessage
    boolean isMemberReadable(String name) {
        return name.equals(StandardTags.TryBlockTag.CATCHES);
    }

    @ExportMessage
    boolean isMemberInvocable(String name) {
        return name.equals(StandardTags.TryBlockTag.CATCHES);
    }

    @ExportMessage
    CatchesFunction readMember(String name,
                    @Shared("getAttr") @Cached ReadAttributeFromObjectNode getattr) throws UnknownIdentifierException {
        if (name.equals(StandardTags.TryBlockTag.CATCHES)) {
            if (catchesFunction == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                ArrayList<Object> literalCatches = new ArrayList<>();
                PythonModule builtins = getContext().getBuiltins();
                if (builtins == null) {
                    return new CatchesFunction(null, null);
                }

                for (ExceptNode node : exceptNodes) {
                    PNode exceptType = node.getExceptType();
                    if (exceptType instanceof ReadGlobalOrBuiltinNode) {
                        try {
                            literalCatches.add(getattr.execute(builtins, ((ReadGlobalOrBuiltinNode) exceptType).getAttributeId()));
                        } catch (PException e) {
                        }
                    } else if (exceptType instanceof TupleLiteralNode) {
                        for (PNode tupleValue : ((TupleLiteralNode) exceptType).getValues()) {
                            if (tupleValue instanceof ReadGlobalOrBuiltinNode) {
                                try {
                                    literalCatches.add(getattr.execute(builtins, ((ReadGlobalOrBuiltinNode) tupleValue).getAttributeId()));
                                } catch (PException e) {
                                }
                            }
                        }
                    }
                }

                Object isinstanceFunc = getattr.execute(builtins, BuiltinNames.ISINSTANCE);
                PTuple caughtClasses = factory().createTuple(literalCatches.toArray());

                if (isinstanceFunc instanceof PBuiltinMethod) {
                    RootCallTarget callTarget = ((PBuiltinMethod) isinstanceFunc).getFunction().getCallTarget();
                    catchesFunction = new CatchesFunction(callTarget, caughtClasses);
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new IllegalStateException("isinstance was redefined, cannot check exceptions");
                }
            }
            return catchesFunction;
        } else {
            throw UnknownIdentifierException.create(name);
        }
    }

    @ExportMessage
    Object invokeMember(String name, Object[] arguments,
                    @Shared("getAttr") @Cached ReadAttributeFromObjectNode getattr) throws ArityException, UnknownIdentifierException {
        if (arguments.length != 1) {
            throw ArityException.create(1, arguments.length);
        }
        return readMember(name, getattr).catches(arguments[0]);
    }

    static class CatchesFunction implements TruffleObject {
        private final RootCallTarget isInstance;
        private final Object[] args = PArguments.create(2);

        CatchesFunction(RootCallTarget callTarget, PTuple caughtClasses) {
            this.isInstance = callTarget;
            PArguments.setArgument(args, 1, caughtClasses);
        }

        boolean catches(Object exception) {
            if (isInstance == null) {
                return false;
            }
            if (exception instanceof PBaseException) {
                PArguments.setArgument(args, 0, exception);
                try {
                    return isInstance.call(args) == Boolean.TRUE;
                } catch (PException e) {
                }
            }
            return false;
        }
    }

    public void setCatchesFunction(CatchesFunction catchesFunction) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        this.catchesFunction = catchesFunction;
    }

}
