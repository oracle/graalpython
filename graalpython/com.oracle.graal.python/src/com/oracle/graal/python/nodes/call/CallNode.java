/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.nodes.call;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;

import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetCachedTpSlotsNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotVarargs.CallSlotTpCallNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.interop.PForeignToPTypeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;

@ImportStatic({PGuards.class, SpecialMethodNames.class})
@GenerateUncached
@SuppressWarnings("truffle-inlining")       // footprint reduction 60 -> 44
public abstract class CallNode extends PNodeWithContext {
    @NeverDefault
    public static CallNode create() {
        return CallNodeGen.create();
    }

    public static CallNode getUncached() {
        return CallNodeGen.getUncached();
    }

    protected abstract Object executeInternal(Frame frame, Object callableObject, Object[] arguments, PKeyword[] keywords);

    /**
     * To be used when this node is called uncached or when no frame is available. Note that the
     * current thread state will be read from the context, so calls through this entry point are
     * potentially slower than if a frame is available.
     */
    public final Object executeWithoutFrame(Object callableObject, Object[] arguments, PKeyword[] keywords) {
        return executeInternal(null, callableObject, arguments, keywords);
    }

    public static Object executeUncached(Object callableObject, Object[] arguments, PKeyword[] keywords) {
        return getUncached().executeInternal(null, callableObject, arguments, keywords);
    }

    /**
     * To be used when this node is called uncached or when no frame is available. Note that the
     * current thread state will be read from the context, so calls through this entry point are
     * potentially slower than if a frame is available.
     */
    public final Object executeWithoutFrame(Object callableObject, Object... arguments) {
        return executeInternal(null, callableObject, arguments, PKeyword.EMPTY_KEYWORDS);
    }

    public static Object executeUncached(Object callableObject, Object... arguments) {
        return getUncached().executeInternal(null, callableObject, arguments, PKeyword.EMPTY_KEYWORDS);
    }

    public final Object execute(Frame frame, Object callableObject, Object[] arguments, PKeyword[] keywords) {
        return executeInternal(frame, callableObject, arguments, keywords);
    }

    public final Object execute(Frame frame, Object callableObject, Object... arguments) {
        return executeInternal(frame, callableObject, arguments, PKeyword.EMPTY_KEYWORDS);
    }

    @Specialization
    static Object boundDescriptor(VirtualFrame frame, BoundDescriptor descriptor, Object[] arguments, PKeyword[] keywords,
                    @Cached CallNode subNode) {
        return subNode.executeInternal(frame, descriptor.descriptor, PythonUtils.arrayCopyOfRange(arguments, 1, arguments.length), keywords);
    }

    @Specialization
    static Object functionCall(VirtualFrame frame, PFunction callable, Object[] arguments, PKeyword[] keywords,
                    @Bind Node inliningTarget,
                    @Cached CallDispatchers.FunctionCachedCallNode callNode) {
        return callNode.execute(frame, inliningTarget, callable, arguments, keywords);
    }

    @Specialization
    static Object builtinFunctionCall(VirtualFrame frame, PBuiltinFunction callable, Object[] arguments, PKeyword[] keywords,
                    @Bind Node inliningTarget,
                    @Cached CallDispatchers.BuiltinFunctionCachedCallNode callNode) {
        return callNode.execute(frame, inliningTarget, callable, arguments, keywords);
    }

    @Specialization
    @InliningCutoff
    protected static Object doForeignMethod(ForeignMethod callable, Object[] arguments, PKeyword[] keywords,
                    @Bind Node inliningTarget,
                    @Cached PForeignToPTypeNode fromForeign,
                    @Cached InlinedBranchProfile keywordsError,
                    @Cached InlinedBranchProfile typeError,
                    @Cached GilNode gil,
                    @CachedLibrary(limit = "getCallSiteInlineCacheMaxDepth()") InteropLibrary interop) {
        if (keywords.length != 0) {
            keywordsError.enter(inliningTarget);
            throw PRaiseNode.raiseStatic(inliningTarget, PythonErrorType.TypeError, ErrorMessages.INVALID_INSTANTIATION_OF_FOREIGN_OBJ);
        }
        gil.release(true);
        try {
            return fromForeign.executeConvert(interop.invokeMember(callable.receiver, callable.methodName, arguments));
        } catch (ArityException | UnsupportedTypeException | UnsupportedMessageException e) {
            typeError.enter(inliningTarget);
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, e);
        } catch (UnknownIdentifierException e) {
            // PyObjectGetMethod is supposed to have checked isMemberInvocable
            throw CompilerDirectives.shouldNotReachHere("Cannot invoke member");
        } finally {
            gil.acquire();
        }
    }

    @Specialization
    static Object builtinMethodCall(VirtualFrame frame, PBuiltinMethod callable, Object[] arguments, PKeyword[] keywords,
                    @Bind Node inliningTarget,
                    @Cached CallDispatchers.BuiltinMethodCachedCallNode callNode) {
        return callNode.execute(frame, inliningTarget, callable, arguments, keywords);
    }

    @Fallback
    static Object doGeneric(VirtualFrame frame, Object callableObject, Object[] arguments, PKeyword[] keywords,
                    @Bind Node inliningTarget,
                    @Cached PRaiseNode raise,
                    @Cached GetClassNode getClassNode,
                    @Cached GetCachedTpSlotsNode getSlots,
                    @Cached CallSlotTpCallNode callSlot) {
        Object type = getClassNode.execute(inliningTarget, callableObject);
        TpSlots slots = getSlots.execute(inliningTarget, type);
        if (slots.tp_call() != null) {
            return callSlot.execute(frame, inliningTarget, slots.tp_call(), callableObject, arguments, keywords);
        }
        throw raise.raise(inliningTarget, TypeError, ErrorMessages.OBJ_ISNT_CALLABLE, callableObject);
    }

    @GenerateInline
    @GenerateUncached
    @GenerateCached(false)
    public abstract static class Lazy extends Node {
        public final CallNode get(Node inliningTarget) {
            return execute(inliningTarget);
        }

        abstract CallNode execute(Node inliningTarget);

        @Specialization
        protected static CallNode doIt(@Cached(inline = false) CallNode callNode) {
            return callNode;
        }
    }
}
