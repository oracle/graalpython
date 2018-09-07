/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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
package com.oracle.graal.python.nodes.call;

import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.EmptyNode;
import com.oracle.graal.python.nodes.argument.keywords.KeywordArgumentsNode;
import com.oracle.graal.python.nodes.argument.positional.PositionalArgumentsNode;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.call.PythonCallNodeGen.GetCallAttributeNodeGen;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.frame.ReadGlobalOrBuiltinNode;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.debug.DebuggerTags;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;

@NodeChild("calleeNode")
public abstract class PythonCallNode extends ExpressionNode {

    @Child private CallNode callNode = CallNode.create();

    /*
     * Either "argument" or "positionalArgument" needs to be non-null (but not both), and
     * "keywordArguments" may be null.
     */
    @Children private final ExpressionNode[] argumentNodes;
    @Child private PositionalArgumentsNode positionalArguments;
    @Child private KeywordArgumentsNode keywordArguments;

    protected final String calleeName;

    PythonCallNode(String calleeName, ExpressionNode[] argumentNodes, PositionalArgumentsNode positionalArguments, KeywordArgumentsNode keywordArguments) {
        this.calleeName = calleeName;
        this.argumentNodes = argumentNodes;
        this.positionalArguments = positionalArguments;
        this.keywordArguments = keywordArguments;
    }

    public static PythonCallNode create(ExpressionNode calleeNode, ExpressionNode[] argumentNodes, ExpressionNode[] keywords, ExpressionNode starArgs, ExpressionNode kwArgs) {
        assert !(starArgs instanceof EmptyNode) : "pass null instead";
        assert !(kwArgs instanceof EmptyNode) : "pass null instead";

        String calleeName = "~unknown";
        ExpressionNode getCallableNode = calleeNode;

        if (calleeNode instanceof ReadGlobalOrBuiltinNode) {
            calleeName = ((ReadGlobalOrBuiltinNode) calleeNode).getAttributeId();
        } else if (calleeNode instanceof GetAttributeNode) {
            getCallableNode = GetCallAttributeNodeGen.create(((GetAttributeNode) calleeNode).getKey(), ((GetAttributeNode) calleeNode).getObject());
        }
        KeywordArgumentsNode keywordArgumentsNode = kwArgs == null && keywords.length == 0 ? null : KeywordArgumentsNode.create(keywords, kwArgs);
        if (starArgs == null) {
            return PythonCallNodeGen.create(calleeName, argumentNodes, null, keywordArgumentsNode, getCallableNode);
        } else {
            return PythonCallNodeGen.create(calleeName, null, PositionalArgumentsNode.create(argumentNodes, starArgs), keywordArgumentsNode, getCallableNode);
        }
    }

    @NodeChild("object")
    protected abstract static class GetCallAttributeNode extends ExpressionNode {

        private final String key;

        protected GetCallAttributeNode(String key) {
            this.key = key;
        }

        @Specialization(guards = "isForeignObject(object)")
        Object getForeignInvoke(TruffleObject object) {
            return new ForeignInvoke(object, key);
        }

        @Specialization(guards = "!isForeignObject(object)")
        Object getCallAttribute(Object object,
                        @Cached("create(__GETATTRIBUTE__)") LookupAndCallBinaryNode getAttributeNode) {
            return getAttributeNode.executeObject(object, key);
        }
    }

    protected static final class ForeignInvoke {
        private final TruffleObject receiver;
        private final String identifier;

        public ForeignInvoke(TruffleObject object, String key) {
            this.receiver = object;
            this.identifier = key;
        }
    }

    public final String getCalleeName() {
        return calleeName;
    }

    @Override
    public boolean hasSideEffectAsAnExpression() {
        return true;
    }

    private Object[] evaluateArguments(VirtualFrame frame) {
        return argumentNodes != null ? PositionalArgumentsNode.evaluateArguments(frame, argumentNodes) : positionalArguments.execute(frame);
    }

    private PKeyword[] evaluateKeywords(VirtualFrame frame) {
        return keywordArguments == null ? PKeyword.EMPTY_KEYWORDS : keywordArguments.execute(frame);
    }

    protected static Node createInvoke() {
        return Message.INVOKE.createNode();
    }

    @Specialization
    Object call(VirtualFrame frame, ForeignInvoke callable,
                    @Cached("create()") BranchProfile keywordsError,
                    @Cached("create()") BranchProfile nameError,
                    @Cached("create()") BranchProfile typeError,
                    @Cached("create()") BranchProfile invokeError,
                    @Cached("create(__GETATTRIBUTE__)") LookupAndCallBinaryNode getAttrNode,
                    @Cached("createInvoke()") Node invokeNode) {
        Object[] arguments = evaluateArguments(frame);
        PKeyword[] keywords = evaluateKeywords(frame);
        if (keywords.length != 0) {
            keywordsError.enter();
            throw raise(PythonErrorType.TypeError, "foreign invocation does not support keyword arguments");
        }
        try {
            return ForeignAccess.sendInvoke(invokeNode, callable.receiver, callable.identifier, arguments);
        } catch (UnknownIdentifierException e) {
            nameError.enter();
            throw raise(PythonErrorType.NameError, e.getMessage());
        } catch (ArityException | UnsupportedTypeException e) {
            typeError.enter();
            throw raise(PythonErrorType.TypeError, e.getMessage());
        } catch (UnsupportedMessageException e) {
            invokeError.enter();
            // the interop contract is to revert to READ and then EXECUTE
            Object member = getAttrNode.executeObject(callable.receiver, callable.identifier);
            return callNode.execute(frame, member, arguments, keywords);
        }
    }

    @Fallback
    Object call(VirtualFrame frame, Object callable) {
        Object[] arguments = evaluateArguments(frame);
        PKeyword[] keywords = evaluateKeywords(frame);
        return callNode.execute(frame, callable, arguments, keywords);
    }

    @Override
    public boolean hasTag(Class<? extends Tag> tag) {
        return isBreakpoint(tag) || isCall(tag) || super.hasTag(tag);
    }

    private static boolean isCall(Class<?> tag) {
        return tag == StandardTags.CallTag.class;
    }

    private boolean isBreakpoint(Class<?> tag) {
        return tag == DebuggerTags.AlwaysHalt.class && calleeName.equals(BuiltinNames.__BREAKPOINT__);
    }
}
