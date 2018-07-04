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
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.argument.keywords.KeywordArgumentsNode;
import com.oracle.graal.python.nodes.argument.positional.PositionalArgumentsNode;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.call.PythonCallNodeGen.GetCallAttributeNodeGen;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.frame.ReadGlobalOrBuiltinNode;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.debug.DebuggerTags;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
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

@NodeChildren({@NodeChild("calleeNode"), @NodeChild(value = "arguments", type = PositionalArgumentsNode.class), @NodeChild(value = "keywords", type = KeywordArgumentsNode.class)})
public abstract class PythonCallNode extends PNode {
    @Child private CallNode callNode = CallNode.create();

    protected final String calleeName;

    PythonCallNode(String calleeName) {
        this.calleeName = calleeName;
    }

    public static PythonCallNode create(PNode calleeNode, PNode[] argumentNodes, PNode[] keywords, PNode starargs, PNode kwargs) {
        String calleeName = "~unknown";
        PNode getCallableNode = calleeNode;

        if (calleeNode instanceof ReadGlobalOrBuiltinNode) {
            calleeName = ((ReadGlobalOrBuiltinNode) calleeNode).getAttributeId();
        } else if (calleeNode instanceof GetAttributeNode) {
            getCallableNode = GetCallAttributeNodeGen.create(((GetAttributeNode) calleeNode).getObject(), ((GetAttributeNode) calleeNode).getKey());
        }

        return PythonCallNodeGen.create(calleeName, getCallableNode, PositionalArgumentsNode.create(argumentNodes, starargs), KeywordArgumentsNode.create(keywords, kwargs));
    }

    @NodeChildren({@NodeChild("object"), @NodeChild("key")})
    protected abstract static class GetCallAttributeNode extends PNode {

        @Specialization(guards = "isForeignObject(object)")
        Object getForeignInvoke(TruffleObject object, String key) {
            return new ForeignInvoke(object, key);
        }

        @Specialization(guards = "!isForeignObject(object)")
        Object getCallAttribute(Object object, Object key,
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

    protected static Node createInvoke() {
        return Message.createInvoke(0).createNode();
    }

    @Specialization
    Object call(ForeignInvoke callable, Object[] arguments, PKeyword[] keywords,
                    @Cached("create()") BranchProfile keywordsError,
                    @Cached("create()") BranchProfile nameError,
                    @Cached("create()") BranchProfile typeError,
                    @Cached("create()") BranchProfile invokeError,
                    @Cached("create(__GETATTRIBUTE__)") LookupAndCallBinaryNode getAttrNode,
                    @Cached("createInvoke()") Node invokeNode) {
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
            return callNode.execute(member, arguments, keywords);
        }
    }

    @Fallback
    Object call(Object callable, Object[] arguments, PKeyword[] keywords) {
        return callNode.execute(callable, arguments, keywords);
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
