/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates.
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

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltins;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.str.StringNodes;
import com.oracle.graal.python.lib.PyObjectFunctionStr;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.EmptyNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.argument.keywords.KeywordArgumentsNode;
import com.oracle.graal.python.nodes.argument.keywords.NonMappingException;
import com.oracle.graal.python.nodes.argument.keywords.SameDictKeyException;
import com.oracle.graal.python.nodes.argument.positional.PositionalArgumentsNode;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode.GetAnyAttributeNode;
import com.oracle.graal.python.nodes.call.PythonCallNodeGen.GetCallAttributeNodeGen;
import com.oracle.graal.python.nodes.call.PythonCallNodeGen.InvokeForeignNodeGen;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallQuaternaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallTernaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.frame.ReadGlobalOrBuiltinNode;
import com.oracle.graal.python.nodes.frame.ReadNameNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.interop.PForeignToPTypeNode;
import com.oracle.graal.python.nodes.literal.IntegerLiteralNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsForeignObjectNode;
import com.oracle.graal.python.nodes.subscript.GetItemNode;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes.GetCaughtExceptionNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.debug.DebuggerTags;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.strings.TruffleString;

import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

@NodeChild("calleeNode")
public abstract class PythonCallNode extends ExpressionNode {

    private static final TruffleString T_UNKNOWN = tsLiteral("~unknown");

    @Child private CallNode callNode = CallNode.create();

    /*
     * Either "argument" or "positionalArgument" needs to be non-null (but not both), and
     * "keywordArguments" may be null.
     */
    @Children protected final ExpressionNode[] argumentNodes;
    @Child private PositionalArgumentsNode positionalArguments;
    @Child private KeywordArgumentsNode keywordArguments;
    @Child private PyObjectFunctionStr pyObjectFunctionStr;
    @Child private StringNodes.CastToTruffleStringCheckedNode castToStringNode;

    protected final TruffleString calleeName;

    protected abstract ExpressionNode getCalleeNode();

    PythonCallNode(TruffleString calleeName, ExpressionNode[] argumentNodes, PositionalArgumentsNode positionalArguments, KeywordArgumentsNode keywordArguments) {
        this.calleeName = calleeName;
        this.argumentNodes = argumentNodes;
        this.positionalArguments = positionalArguments;
        this.keywordArguments = keywordArguments;
    }

    public static ExpressionNode create(ExpressionNode calleeNode, ExpressionNode[] argumentNodes, ExpressionNode[] keywords, ExpressionNode starArgs, ExpressionNode kwArgs) {
        assert !(starArgs instanceof EmptyNode) : "pass null instead";
        assert !(kwArgs instanceof EmptyNode) : "pass null instead";

        TruffleString calleeName = T_UNKNOWN;
        ExpressionNode getCallableNode = calleeNode;

        if (calleeNode instanceof ReadGlobalOrBuiltinNode) {
            calleeName = ((ReadGlobalOrBuiltinNode) calleeNode).getAttributeId();
        } else if (calleeNode instanceof ReadNameNode) {
            calleeName = ((ReadNameNode) calleeNode).getAttributeId();
        } else if (calleeNode instanceof GetAttributeNode) {
            getCallableNode = GetCallAttributeNodeGen.create(((GetAttributeNode) calleeNode).getKey(), ((GetAttributeNode) calleeNode).getObject());
        }
        KeywordArgumentsNode keywordArgumentsNode = kwArgs == null && keywords.length == 0 ? null : KeywordArgumentsNode.create(keywords, kwArgs);

        if (argumentNodes != null && keywordArgumentsNode == null && starArgs == null) {
            switch (argumentNodes.length) {
                case 1:
                    return new PythonCallUnary(getCallableNode, argumentNodes[0]);
                case 2:
                    return new PythonCallBinary(getCallableNode, argumentNodes);
                case 3:
                    return new PythonCallTernary(getCallableNode, argumentNodes);
                case 4:
                    return new PythonCallQuaternary(getCallableNode, argumentNodes);
                default:
                    // otherwise: fall through
            }
        }

        if (starArgs == null) {
            return PythonCallNodeGen.create(calleeName, argumentNodes, null, keywordArgumentsNode, getCallableNode);
        } else {
            return PythonCallNodeGen.create(calleeName, null, PositionalArgumentsNode.create(argumentNodes, starArgs), keywordArgumentsNode, getCallableNode);
        }
    }

    public ExpressionNode[] getArgumentNodes() {
        return argumentNodes;
    }

    private static final class PythonCallUnary extends ExpressionNode {
        @Child CallUnaryMethodNode callUnary = CallUnaryMethodNode.create();
        @Child ExpressionNode getCallable;
        @Child ExpressionNode argumentNode;

        @Child InvokeForeign invokeForeign;

        PythonCallUnary(ExpressionNode getCallable, ExpressionNode argumentNode) {
            this.getCallable = getCallable;
            this.argumentNode = argumentNode;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object callable = getCallable.execute(frame);
            Object argument = argumentNode.execute(frame);
            if (callable instanceof ForeignInvoke) {
                if (invokeForeign == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    invokeForeign = insert(InvokeForeignNodeGen.create());
                }
                return invokeForeign.execute(frame, (ForeignInvoke) callable, new Object[]{argument}, PKeyword.EMPTY_KEYWORDS);
            }
            return callUnary.executeObject(frame, callable, argument);
        }
    }

    private static final class PythonCallBinary extends ExpressionNode {
        @Child CallBinaryMethodNode callBinary = CallBinaryMethodNode.create();
        @Child ExpressionNode getCallable;
        @Children final ExpressionNode[] argumentNodes;

        @Child InvokeForeign invokeForeign;

        PythonCallBinary(ExpressionNode getCallable, ExpressionNode[] argumentNodes) {
            this.getCallable = getCallable;
            this.argumentNodes = argumentNodes;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object callable = getCallable.execute(frame);
            Object argument1 = argumentNodes[0].execute(frame);
            Object argument2 = argumentNodes[1].execute(frame);
            if (callable instanceof ForeignInvoke) {
                if (invokeForeign == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    invokeForeign = insert(InvokeForeignNodeGen.create());
                }
                return invokeForeign.execute(frame, (ForeignInvoke) callable, new Object[]{argument1, argument2}, PKeyword.EMPTY_KEYWORDS);
            }
            return callBinary.executeObject(frame, callable, argument1, argument2);
        }
    }

    private static final class PythonCallTernary extends ExpressionNode {
        @Child CallTernaryMethodNode callTernary = CallTernaryMethodNode.create();
        @Child ExpressionNode getCallable;
        @Children final ExpressionNode[] argumentNodes;

        @Child InvokeForeign invokeForeign;

        PythonCallTernary(ExpressionNode getCallable, ExpressionNode[] argumentNodes) {
            this.getCallable = getCallable;
            this.argumentNodes = argumentNodes;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object callable = getCallable.execute(frame);
            Object argument1 = argumentNodes[0].execute(frame);
            Object argument2 = argumentNodes[1].execute(frame);
            Object argument3 = argumentNodes[2].execute(frame);
            if (callable instanceof ForeignInvoke) {
                if (invokeForeign == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    invokeForeign = insert(InvokeForeignNodeGen.create());
                }
                return invokeForeign.execute(frame, (ForeignInvoke) callable, new Object[]{argument1, argument2, argument3}, PKeyword.EMPTY_KEYWORDS);
            }
            return callTernary.execute(frame, callable, argument1, argument2, argument3);
        }
    }

    private static final class PythonCallQuaternary extends ExpressionNode {
        @Child CallQuaternaryMethodNode callQuaternary = CallQuaternaryMethodNode.create();
        @Child ExpressionNode getCallable;
        @Children final ExpressionNode[] argumentNodes;

        @Child InvokeForeign invokeForeign;

        PythonCallQuaternary(ExpressionNode getCallable, ExpressionNode[] argumentNodes) {
            this.getCallable = getCallable;
            this.argumentNodes = argumentNodes;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object callable = getCallable.execute(frame);
            Object argument1 = argumentNodes[0].execute(frame);
            Object argument2 = argumentNodes[1].execute(frame);
            Object argument3 = argumentNodes[2].execute(frame);
            Object argument4 = argumentNodes[3].execute(frame);
            if (callable instanceof ForeignInvoke) {
                if (invokeForeign == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    invokeForeign = insert(InvokeForeignNodeGen.create());
                }
                return invokeForeign.execute(frame, (ForeignInvoke) callable, new Object[]{argument1, argument2, argument3, argument4}, PKeyword.EMPTY_KEYWORDS);
            }
            return callQuaternary.execute(frame, callable, argument1, argument2, argument3, argument4);
        }
    }

    @NodeChild("object")
    protected abstract static class GetCallAttributeNode extends ExpressionNode {

        protected final TruffleString key;

        protected GetCallAttributeNode(TruffleString key) {
            this.key = key;
        }

        @Specialization(guards = "isForeignObjectNode.execute(object)", limit = "1")
        Object getForeignInvoke(Object object,
                        @SuppressWarnings("unused") @Shared("isForeign") @Cached IsForeignObjectNode isForeignObjectNode) {
            return new ForeignInvoke(object, key);
        }

        @Specialization(guards = "!isForeignObjectNode.execute(object)", limit = "1")
        static Object getCallAttribute(VirtualFrame frame, Object object,
                        @SuppressWarnings("unused") @Shared("isForeign") @Cached IsForeignObjectNode isForeignObjectNode,
                        @Cached("create(key)") GetAttributeNode getAttributeNode) {
            return getAttributeNode.executeObject(frame, object);
        }
    }

    protected static final class ForeignInvoke {
        private final Object receiver;
        private final TruffleString identifier;

        public ForeignInvoke(Object object, TruffleString key) {
            this.receiver = object;
            this.identifier = key;
        }
    }

    public final TruffleString getCalleeName() {
        return calleeName;
    }

    @Override
    public boolean hasSideEffectAsAnExpression() {
        return true;
    }

    private Object[] evaluateArguments(VirtualFrame frame) {
        return argumentNodes != null ? PositionalArgumentsNode.evaluateArguments(frame, argumentNodes) : positionalArguments.execute(frame);
    }

    private PKeyword[] evaluateKeywords(VirtualFrame frame, Object callable, PRaiseNode raise, BranchProfile keywordsError) {
        PKeyword[] result;
        if (keywordArguments == null) {
            result = PKeyword.EMPTY_KEYWORDS;
        } else {
            try {
                result = keywordArguments.execute(frame);
            } catch (SameDictKeyException ex) {
                keywordsError.enter();
                if (castToStringNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    castToStringNode = insert(StringNodes.CastToTruffleStringCheckedNode.create());
                }
                TruffleString functionName = getFunctionName(frame, callable);
                TruffleString keyName = castToStringNode.execute(ex.getKey(), ErrorMessages.KEYWORDS_S_MUST_BE_STRINGS, new Object[]{functionName});
                throw raise.raise(PythonBuiltinClassType.TypeError, ErrorMessages.GOT_MULTIPLE_VALUES_FOR_KEYWORD_ARG, functionName, keyName);
            } catch (NonMappingException ex) {
                keywordsError.enter();
                TruffleString functionName = getFunctionName(frame, callable);
                throw raise.raise(PythonBuiltinClassType.TypeError, ErrorMessages.ARG_AFTER_MUST_BE_MAPPING, functionName, ex.getObject());
            }
        }
        return result;
    }

    private TruffleString getFunctionName(VirtualFrame frame, Object callable) {
        if (pyObjectFunctionStr == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            pyObjectFunctionStr = insert(PyObjectFunctionStr.create());
        }
        return pyObjectFunctionStr.execute(frame, callable);
    }

    @ImportStatic({PythonOptions.class})
    abstract static class InvokeForeign extends Node {

        @Child private CallNode callNode = CallNode.create();

        public abstract Object execute(VirtualFrame frame, ForeignInvoke callable, Object[] arguments, PKeyword[] keywords);

        @Specialization
        Object call(VirtualFrame frame, ForeignInvoke callable, Object[] arguments, PKeyword[] keywords,
                        @Cached PRaiseNode raise,
                        @Cached PForeignToPTypeNode fromForeign,
                        @Cached BranchProfile typeError,
                        @Cached BranchProfile invokeError,
                        @Cached GetAnyAttributeNode getAttrNode,
                        @Cached TruffleString.ToJavaStringNode toJavaStringNode,
                        @CachedLibrary(limit = "getCallSiteInlineCacheMaxDepth()") InteropLibrary interop) {
            try {
                return fromForeign.executeConvert(interop.invokeMember(callable.receiver, toJavaStringNode.execute(callable.identifier), arguments));
            } catch (ArityException | UnsupportedTypeException e) {
                typeError.enter();
                throw raise.raise(PythonErrorType.TypeError, e);
            } catch (UnknownIdentifierException | UnsupportedMessageException e) {
                invokeError.enter();
                // the interop contract is to revert to readMember and then execute
                Object member = getAttrNode.executeObject(frame, callable.receiver, callable.identifier);
                return callNode.execute(frame, member, arguments, keywords);
            }
        }
    }

    @Specialization
    Object call(VirtualFrame frame, ForeignInvoke callable,
                    @Cached PRaiseNode raise,
                    @Cached BranchProfile keywordsError,
                    @Cached InvokeForeign invoke) {
        Object[] arguments = evaluateArguments(frame);
        PKeyword[] keywords = evaluateKeywords(frame, callable, raise, keywordsError);
        if (keywords.length != 0) {
            keywordsError.enter();
            throw raise.raise(PythonErrorType.TypeError, ErrorMessages.FOREIGN_INVOCATION_DOESNT_SUPPORT_KEYWORD_ARG);
        }
        return invoke.execute(frame, callable, arguments, keywords);
    }

    protected static boolean isSysExcInfo(Class<? extends PythonBuiltinBaseNode> nodeClass) {
        CompilerAsserts.neverPartOfCompilation();
        return nodeClass == SysModuleBuiltins.ExcInfoNode.class;
    }

    protected boolean canDoFastSysExcInfo() {
        if (getParent() instanceof GetItemNode) {
            return ((GetItemNode) getParent()).getSlice() instanceof IntegerLiteralNode && ((IntegerLiteralNode) ((GetItemNode) getParent()).getSlice()).getValue() == 0;
        }
        return false;
    }

    @Specialization(limit = "1", guards = {"callable == callableCached", "isSysExcInfo(nodeClass)", "canDoFastSysExcInfo()"})
    static Object fastSysExcInfoCached(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinMethod callable,
                    @SuppressWarnings("unused") @Cached("callable") PBuiltinMethod callableCached,
                    @SuppressWarnings("unused") @Cached("callableCached.getFunction()") PBuiltinFunction func,
                    @SuppressWarnings("unused") @Cached("func.getNodeClass()") Class<? extends PythonBuiltinBaseNode> nodeClass,
                    @Cached PythonObjectFactory factory,
                    @Cached GetClassNode getClassNode,
                    @Cached GetCaughtExceptionNode getCaughtExceptionNode) {
        return SysModuleBuiltins.ExcInfoNode.fast(frame, getClassNode, getCaughtExceptionNode, factory);
    }

    public static boolean isForeignInvoke(Object obj) {
        return obj instanceof ForeignInvoke;
    }

    @Specialization(guards = "!isForeignInvoke(callable)")
    Object call(VirtualFrame frame, Object callable, @Cached PRaiseNode raise,
                    @Cached BranchProfile keywordsError) {
        Object[] arguments = evaluateArguments(frame);
        PKeyword[] keywords = evaluateKeywords(frame, callable, raise, keywordsError);
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
        return tag == DebuggerTags.AlwaysHalt.class && calleeName.equalsUncached(BuiltinNames.T_BREAKPOINT, TS_ENCODING);
    }
}
