/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates.
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
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.frame.ReadGlobalOrBuiltinNode;
import com.oracle.graal.python.nodes.frame.ReadNameNode;
import com.oracle.graal.python.nodes.literal.TupleLiteralNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes.ExceptionState;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes.SetCaughtExceptionNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.exception.ExceptionHandledException;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.interop.InteropArray;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.exception.AbstractTruffleException;
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
    @Child private InteropLibrary excLib;

    private final ConditionProfile everMatched = ConditionProfile.createBinaryProfile();

    @CompilationFinal private CatchesFunction catchesFunction;

    public TryExceptNode(StatementNode body, ExceptNode[] exceptNodes, StatementNode orelse) {
        this.body = body;
        body.markAsTryBlock();
        this.exceptNodes = exceptNodes;
        this.orelse = orelse;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        // The following statement is a no-op, but it helps graal to optimize the exception handler
        // by moving the cast to PException to the beginning
        saveExceptionState(frame);

        try {
            body.executeVoid(frame);
        } catch (PException ex) {
            if (!catchPException(frame, ex)) {
                throw ex;
            }
            return;
        } catch (AbstractTruffleException e) {
            if (excLib == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                excLib = insert(InteropLibrary.getFactory().createDispatched(3));
            }
            if (excLib.isException(e) && catchTruffleException(frame, e)) {
                return;
            }
            throw e;
        } catch (ControlFlowException e) {
            throw e;
        } catch (Exception | StackOverflowError | AssertionError e) {
            PException pe = wrapJavaExceptionIfApplicable(e);
            if (pe != null) {
                boolean handled = catchPException(frame, pe);
                if (handled) {
                    return;
                } else {
                    throw pe.getExceptionForReraise();
                }
            }
            throw e;
        }
        orelse.executeVoid(frame);
    }

    @ExplodeLoop(kind = LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN)
    private boolean catchPException(VirtualFrame frame, PException exception) {
        try {
            for (ExceptNode exceptNode : exceptNodes) {
                if (everMatched.profile(exceptNode.matchesPException(frame, exception))) {
                    tryChainPreexistingException(frame, exception);
                    ExceptionState exceptionState = saveExceptionState(frame);
                    exception.setCatchingFrameReference(frame, this);
                    SetCaughtExceptionNode.execute(frame, exception);
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
        } catch (Exception | StackOverflowError | AssertionError e) {
            PException handlerException = wrapJavaExceptionIfApplicable(e);
            if (handlerException == null) {
                throw e;
            }
            tryChainExceptionFromHandler(handlerException, exception);
            throw handlerException.getExceptionForReraise();
        }
        return false;
    }

    @ExplodeLoop(kind = LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN)
    private boolean catchTruffleException(VirtualFrame frame, AbstractTruffleException exception) {
        assert !(exception instanceof PException);
        try {
            for (ExceptNode exceptNode : exceptNodes) {
                if (everMatched.profile(exceptNode.matchesTruffleException(frame, exception))) {
                    ExceptionState exceptionState = saveExceptionState(frame);
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
            throw handlerException;
        } catch (Exception | StackOverflowError | AssertionError e) {
            PException handlerException = wrapJavaExceptionIfApplicable(e);
            if (handlerException == null) {
                throw e;
            }
            throw handlerException.getExceptionForReraise();
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
        return new InteropArray(new String[]{StandardTags.TryBlockTag.CATCHES});
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
                    @Exclusive @Cached GilNode gil) throws UnknownIdentifierException {
        boolean mustRelease = gil.acquire();
        try {
            if (name.equals(StandardTags.TryBlockTag.CATCHES)) {
                if (catchesFunction == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    ArrayList<Object> literalCatches = new ArrayList<>();
                    for (ExceptNode node : exceptNodes) {
                        PNode exceptType = node.getExceptType();
                        if (exceptType instanceof ReadNameNode) {
                            literalCatches.add(((ReadNameNode) exceptType).getAttributeId());
                        } else if (exceptType instanceof ReadGlobalOrBuiltinNode) {
                            literalCatches.add(((ReadGlobalOrBuiltinNode) exceptType).getAttributeId());
                        } else if (exceptType instanceof TupleLiteralNode) {
                            for (PNode tupleValue : ((TupleLiteralNode) exceptType).getValues()) {
                                if (tupleValue instanceof ReadNameNode) {
                                    literalCatches.add(((ReadNameNode) tupleValue).getAttributeId());
                                } else if (tupleValue instanceof ReadGlobalOrBuiltinNode) {
                                    literalCatches.add(((ReadGlobalOrBuiltinNode) tupleValue).getAttributeId());
                                }
                            }
                        } else {
                            literalCatches.add("BaseException");
                        }
                    }
                    catchesFunction = new CatchesFunction(literalCatches.toArray(PythonUtils.EMPTY_STRING_ARRAY));
                }
                return catchesFunction;
            } else {
                throw UnknownIdentifierException.create(name);
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    Object invokeMember(String name, Object[] arguments,
                    @Exclusive @Cached GilNode gil) throws ArityException, UnknownIdentifierException {
        boolean mustRelease = gil.acquire();
        try {
            if (arguments.length != 1) {
                throw ArityException.create(1, 1, arguments.length);
            }
            return readMember(name, gil).catches(new Object[]{arguments[0]}, gil);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static class CatchesFunction implements TruffleObject {
        private String[] caughtClasses;

        CatchesFunction(String[] caughtClasses) {
            this.caughtClasses = caughtClasses;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isExecutable() {
            return true;
        }

        @ExportMessage(name = "execute")
        @TruffleBoundary
        boolean catches(Object[] arguments,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            try {
                if (arguments.length != 1) {
                    throw ArityException.create(1, 1, arguments.length);
                }
                Object exception = arguments[0];
                if (exception instanceof PBaseException) {
                    PythonObjectLibrary lib = PythonObjectLibrary.getUncached();
                    IsSubtypeNode isSubtype = IsSubtypeNode.getUncached();
                    ReadAttributeFromObjectNode readAttr = ReadAttributeFromObjectNode.getUncached();

                    for (String c : caughtClasses) {
                        Object cls = readAttr.execute(PythonLanguage.getContext().getBuiltins(), c);
                        if (lib.isLazyPythonClass(cls)) {
                            if (isSubtype.execute(GetClassNode.getUncached().execute(exception), cls)) {
                                return true;
                            }
                        }
                    }
                }
                return false;
            } finally {
                gil.release(mustRelease);
            }
        }
    }

    public void setCatchesFunction(CatchesFunction catchesFunction) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        this.catchesFunction = catchesFunction;
    }
}
