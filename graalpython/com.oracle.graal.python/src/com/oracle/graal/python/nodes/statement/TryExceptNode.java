/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.runtime.PythonOptions.CatchAllExceptions;

import java.util.ArrayList;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
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
import com.oracle.graal.python.nodes.util.ExceptionStateNodes.RestoreExceptionStateNode;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes.SaveExceptionStateNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.ExceptionHandledException;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
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

@ExportLibrary(InteropLibrary.class)
@ImportStatic(SpecialMethodNames.class)
public class TryExceptNode extends StatementNode implements TruffleObject {
    @Child private StatementNode body;
    @Children private final ExceptNode[] exceptNodes;
    @Child private StatementNode orelse;
    @Child private PythonObjectFactory ofactory;
    @Child private SaveExceptionStateNode saveExceptionStateNode = SaveExceptionStateNode.create();
    @Child private RestoreExceptionStateNode restoreExceptionStateNode;

    private final boolean shouldCatchAll;
    private final Assumption singleContextAssumption;
    private final ContextReference<PythonContext> contextRef;

    @CompilationFinal private CatchesFunction catchesFunction;
    @CompilationFinal boolean seenException;

    public TryExceptNode(StatementNode body, ExceptNode[] exceptNodes, StatementNode orelse) {
        this.body = body;
        body.markAsTryBlock();
        this.exceptNodes = exceptNodes;
        this.orelse = orelse;
        this.contextRef = PythonLanguage.getContextRef();
        this.shouldCatchAll = PythonOptions.getOption(contextRef.get(), CatchAllExceptions);
        this.singleContextAssumption = PythonLanguage.getCurrent().singleContextAssumption;
    }

    protected PythonContext getContext() {
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
        // store current exception state for later restore
        ExceptionState exceptionState = saveExceptionStateNode.execute(frame);
        try {
            body.executeVoid(frame);
        } catch (PException ex) {
            catchException(frame, ex, exceptionState);
            return;
        } catch (Exception e) {
            if (!seenException) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                seenException = true;
            }

            if (shouldCatchAll()) {
                if (e instanceof ControlFlowException) {
                    throw e;
                } else {
                    PException pe = PException.fromObject(getBaseException(e), this);
                    try {
                        catchException(frame, pe, exceptionState);
                    } catch (PException pe_thrown) {
                        if (pe_thrown != pe) {
                            throw e;
                        }
                    }
                }
            } else {
                throw e;
            }
        }
        orelse.executeVoid(frame);
    }

    private boolean shouldCatchAll() {
        if (singleContextAssumption.isValid()) {
            return shouldCatchAll;
        } else {
            return PythonOptions.getOption(getContext(), CatchAllExceptions);
        }
    }

    @TruffleBoundary
    private PBaseException getBaseException(Exception t) {
        return factory().createBaseException(PythonErrorType.ValueError, "%m", new Object[]{t});
    }

    @ExplodeLoop
    private void catchException(VirtualFrame frame, PException exception, ExceptionState exceptionState) {
        boolean wasHandled = false;
        for (ExceptNode exceptNode : exceptNodes) {
            // we want a constant loop iteration count for ExplodeLoop to work,
            // so we always run through all except handlers
            if (!wasHandled) {
                if (exceptNode.matchesException(frame, exception)) {
                    try {
                        exceptNode.executeExcept(frame, exception);
                    } catch (ExceptionHandledException e) {
                        wasHandled = true;
                    } catch (ControlFlowException e) {
                        // restore previous exception state, this won't happen if the except block
                        // raises an exception
                        restoreExceptionState(frame, exceptionState);
                        throw e;
                    }
                }
            }
        }
        if (!wasHandled) {
            throw exception;
        }
        // restore previous exception state, this won't happen if the except block
        // raises an exception
        restoreExceptionState(frame, exceptionState);
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

                if (isinstanceFunc instanceof PBuiltinMethod && ((PBuiltinMethod) isinstanceFunc).getFunction() instanceof PBuiltinFunction) {
                    RootCallTarget callTarget = ((PBuiltinFunction) ((PBuiltinMethod) isinstanceFunc).getFunction()).getCallTarget();
                    catchesFunction = new CatchesFunction(callTarget, caughtClasses);
                } else {
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

        @ExplodeLoop
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

    private void restoreExceptionState(VirtualFrame frame, ExceptionState e) {
        if (e != null) {
            if (restoreExceptionStateNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                restoreExceptionStateNode = insert(RestoreExceptionStateNode.create());
            }
            restoreExceptionStateNode.execute(frame, e);
        }
    }
}
