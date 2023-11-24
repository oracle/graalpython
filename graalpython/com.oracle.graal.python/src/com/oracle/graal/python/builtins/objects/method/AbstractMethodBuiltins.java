/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates.
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

package com.oracle.graal.python.builtins.objects.method;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.AttributeError;
import static com.oracle.graal.python.nodes.BuiltinNames.T_GETATTR;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___DOC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___MODULE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___QUALNAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___SELF__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DOC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___MODULE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___QUALNAME__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CALL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___HASH__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.str.StringUtils.SimpleTruffleStringFormatNode;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PMethod, PythonBuiltinClassType.PBuiltinFunctionOrMethod, PythonBuiltinClassType.MethodWrapper})
public final class AbstractMethodBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return AbstractMethodBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___CALL__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class CallNode extends PythonVarargsBuiltinNode {
        @Child private com.oracle.graal.python.nodes.call.CallNode callNode = com.oracle.graal.python.nodes.call.CallNode.create();

        @Specialization(guards = "isFunction(self.getFunction())")
        protected Object doIt(VirtualFrame frame, PMethod self, Object[] arguments, PKeyword[] keywords) {
            return callNode.execute(frame, self, arguments, keywords);
        }

        @Specialization(guards = "isFunction(self.getFunction())")
        protected Object doIt(VirtualFrame frame, PBuiltinMethod self, Object[] arguments, PKeyword[] keywords) {
            return callNode.execute(frame, self, arguments, keywords);
        }

        @Specialization(guards = "!isFunction(self.getFunction())")
        protected Object doItNonFunction(VirtualFrame frame, PMethod self, Object[] arguments, PKeyword[] keywords) {
            return callNode.execute(frame, self.getFunction(), PythonUtils.prependArgument(self.getSelf(), arguments), keywords);
        }

        @Specialization(guards = "!isFunction(self.getFunction())")
        protected Object doItNonFunction(VirtualFrame frame, PBuiltinMethod self, Object[] arguments, PKeyword[] keywords) {
            return callNode.execute(frame, self.getFunction(), PythonUtils.prependArgument(self.getSelf(), arguments), keywords);
        }

        @Override
        public Object varArgExecute(VirtualFrame frame, @SuppressWarnings("unused") Object self, Object[] arguments, PKeyword[] keywords) throws VarargsBuiltinDirectInvocationNotSupported {
            Object[] argsWithoutSelf = new Object[arguments.length - 1];
            PythonUtils.arraycopy(arguments, 1, argsWithoutSelf, 0, argsWithoutSelf.length);
            return execute(frame, arguments[0], argsWithoutSelf, keywords);
        }
    }

    @Builtin(name = J___SELF__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class SelfNode extends PythonBuiltinNode {
        @Specialization
        protected static Object doIt(PMethod self) {
            return self.getSelf() != PNone.NO_VALUE ? self.getSelf() : PNone.NONE;
        }

        @Specialization
        protected static Object doIt(PBuiltinMethod self) {
            if (self.getBuiltinFunction().isStatic()) {
                return PNone.NONE;
            }
            return self.getSelf() != PNone.NO_VALUE ? self.getSelf() : PNone.NONE;
        }
    }

    @Builtin(name = J___EQ__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class EqNode extends PythonBinaryBuiltinNode {

        @Child private InteropLibrary identicalLib = InteropLibrary.getFactory().createDispatched(3);
        @Child private InteropLibrary identicalLib2 = InteropLibrary.getFactory().createDispatched(3);

        private boolean eq(Object function1, Object function2, Object self1, Object self2) {
            if (function1 != function2) {
                return false;
            }
            if (self1 != self2) {
                // CPython compares PyObject* pointers:
                if (self1 instanceof PythonAbstractNativeObject && self2 instanceof PythonAbstractNativeObject) {
                    if (identicalLib.isIdentical(((PythonAbstractNativeObject) self1).getPtr(), ((PythonAbstractNativeObject) self2).getPtr(), identicalLib2)) {
                        return true;
                    }
                }
                return false;
            }
            return true;
        }

        @Specialization
        boolean eq(PMethod self, PMethod other) {
            return eq(self.getFunction(), other.getFunction(), self.getSelf(), other.getSelf());
        }

        @Specialization
        boolean eq(PBuiltinMethod self, PBuiltinMethod other) {
            return eq(self.getFunction(), other.getFunction(), self.getSelf(), other.getSelf());
        }

        @Fallback
        static boolean eq(@SuppressWarnings("unused") Object self, @SuppressWarnings("unused") Object other) {
            return false;
        }
    }

    @Builtin(name = J___HASH__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class HashNode extends PythonUnaryBuiltinNode {
        @Specialization
        static long hash(PMethod self) {
            return PythonAbstractObject.systemHashCode(self.getSelf()) ^ PythonAbstractObject.systemHashCode(self.getFunction());
        }

        @Specialization
        static long hash(PBuiltinMethod self) {
            return PythonAbstractObject.systemHashCode(self.getSelf()) ^ PythonAbstractObject.systemHashCode(self.getFunction());
        }
    }

    @Builtin(name = J___MODULE__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class GetModuleNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "isNoValue(none)", limit = "2")
        static Object getModule(VirtualFrame frame, PBuiltinMethod self, @SuppressWarnings("unused") PNone none,
                        @Bind("this") Node inliningTarget,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @Cached PyObjectLookupAttr lookup,
                        @CachedLibrary("self") DynamicObjectLibrary dylib) {
            // No profiling, performance here is not very important
            Object module = dylib.getOrDefault(self, T___MODULE__, PNone.NO_VALUE);
            if (module != PNone.NO_VALUE) {
                return module;
            }
            if (self.getSelf() instanceof PythonModule) {
                PythonLanguage language = PythonLanguage.get(inliningTarget);
                PythonContext context = PythonContext.get(inliningTarget);
                Object state = IndirectCallContext.enter(frame, language, context, indirectCallData);
                try {
                    return lookup.execute(null, inliningTarget, self.getSelf(), T___NAME__);
                } finally {
                    IndirectCallContext.exit(frame, language, context, state);
                }
            }
            return PNone.NONE;
        }

        @Specialization(guards = "!isNoValue(value)", limit = "2")
        static Object getModule(PBuiltinMethod self, Object value,
                        @CachedLibrary("self") DynamicObjectLibrary dylib) {
            dylib.put(self.getStorage(), T___MODULE__, value);
            return PNone.NONE;
        }

        @Specialization(guards = "isNoValue(value)")
        static Object getModule(VirtualFrame frame, PMethod self, @SuppressWarnings("unused") Object value,
                        @Cached("create(T___MODULE__)") GetAttributeNode getAttributeNode) {
            return getAttributeNode.executeObject(frame, self.getFunction());
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object getModule(@SuppressWarnings("unused") PMethod self, @SuppressWarnings("unused") Object value,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(AttributeError, ErrorMessages.OBJ_S_HAS_NO_ATTR_S, "method", T___MODULE__);
        }
    }

    @Builtin(name = J___DOC__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class DocNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object getDoc(PMethod self,
                        @Shared @Cached ReadAttributeFromObjectNode readNode) {
            Object doc = readNode.execute(self.getFunction(), T___DOC__);
            if (doc == PNone.NO_VALUE) {
                return PNone.NONE;
            }
            return doc;
        }

        @Specialization
        static Object getDoc(PBuiltinMethod self,
                        @Shared @Cached ReadAttributeFromObjectNode readNode) {
            Object doc = readNode.execute(self.getFunction(), T___DOC__);
            if (doc == PNone.NO_VALUE) {
                return PNone.NONE;
            }
            return doc;
        }
    }

    @Builtin(name = J___NAME__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class NameNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object getName(VirtualFrame frame, PBuiltinMethod method,
                        @Bind("this") Node inliningTarget,
                        @Shared("toStringNode") @Cached CastToTruffleStringNode toStringNode,
                        @Shared("getAttr") @Cached PyObjectGetAttr getAttr) {
            try {
                return toStringNode.execute(inliningTarget, getAttr.execute(frame, inliningTarget, method.getFunction(), T___NAME__));
            } catch (CannotCastException cce) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

        @Specialization
        static Object getName(VirtualFrame frame, PMethod method,
                        @Bind("this") Node inliningTarget,
                        @Shared("toStringNode") @Cached CastToTruffleStringNode toStringNode,
                        @Shared("getAttr") @Cached PyObjectGetAttr getAttr) {
            try {
                return toStringNode.execute(inliningTarget, getAttr.execute(frame, inliningTarget, method.getFunction(), T___NAME__));
            } catch (CannotCastException cce) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }
    }

    @Builtin(name = J___QUALNAME__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class QualNameNode extends PythonUnaryBuiltinNode {

        protected static boolean isSelfModuleOrNull(PMethod method) {
            return method.getSelf() == PNone.NO_VALUE || PGuards.isPythonModule(method.getSelf());
        }

        protected static boolean isSelfModuleOrNull(PBuiltinMethod method) {
            return method.getSelf() == PNone.NO_VALUE || PGuards.isPythonModule(method.getSelf());
        }

        @Specialization(guards = "isSelfModuleOrNull(method)")
        static TruffleString doSelfIsModule(VirtualFrame frame, PMethod method,
                        @Bind("this") Node inliningTarget,
                        @Shared("toStringNode") @Cached CastToTruffleStringNode toStringNode,
                        @Shared("lookupName") @Cached PyObjectLookupAttr lookupName) {
            return getName(frame, inliningTarget, method.getFunction(), toStringNode, lookupName);
        }

        @Specialization(guards = "isSelfModuleOrNull(method)")
        static TruffleString doSelfIsModule(VirtualFrame frame, PBuiltinMethod method,
                        @Bind("this") Node inliningTarget,
                        @Shared("toStringNode") @Cached CastToTruffleStringNode toStringNode,
                        @Shared("lookupName") @Cached PyObjectLookupAttr lookupName) {
            return getName(frame, inliningTarget, method.getFunction(), toStringNode, lookupName);
        }

        @Specialization(guards = "!isSelfModuleOrNull(method)")
        static TruffleString doSelfIsObject(VirtualFrame frame, PMethod method,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached GetClassNode getClassNode,
                        @Shared @Cached TypeNodes.IsTypeNode isTypeNode,
                        @Shared("toStringNode") @Cached CastToTruffleStringNode toStringNode,
                        @Shared("getQualname") @Cached PyObjectGetAttr getQualname,
                        @Shared("lookupName") @Cached PyObjectLookupAttr lookupName,
                        @Shared("formatter") @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return getQualName(frame, inliningTarget, method.getSelf(), method.getFunction(), getClassNode, isTypeNode, toStringNode, getQualname, lookupName, simpleTruffleStringFormatNode,
                            raiseNode);
        }

        @Specialization(guards = "!isSelfModuleOrNull(method)")
        static TruffleString doSelfIsObject(VirtualFrame frame, PBuiltinMethod method,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached GetClassNode getClassNode,
                        @Shared @Cached TypeNodes.IsTypeNode isTypeNode,
                        @Shared("toStringNode") @Cached CastToTruffleStringNode toStringNode,
                        @Shared("getQualname") @Cached PyObjectGetAttr getQualname,
                        @Shared("lookupName") @Cached PyObjectLookupAttr lookupName,
                        @Shared("formatter") @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return getQualName(frame, inliningTarget, method.getSelf(), method.getFunction(), getClassNode, isTypeNode, toStringNode, getQualname, lookupName, simpleTruffleStringFormatNode,
                            raiseNode);
        }

        private static TruffleString getQualName(VirtualFrame frame, Node inliningTarget, Object self, Object func, GetClassNode getClassNode, TypeNodes.IsTypeNode isTypeNode,
                        CastToTruffleStringNode toStringNode, PyObjectGetAttr getQualname, PyObjectLookupAttr lookupName, SimpleTruffleStringFormatNode simpleTruffleStringFormatNode,
                        PRaiseNode.Lazy raiseNode) {
            Object type = isTypeNode.execute(inliningTarget, self) ? self : getClassNode.execute(inliningTarget, self);

            try {
                TruffleString typeQualName = toStringNode.execute(inliningTarget, getQualname.execute(frame, inliningTarget, type, T___QUALNAME__));
                return simpleTruffleStringFormatNode.format("%s.%s", typeQualName, getName(frame, inliningTarget, func, toStringNode, lookupName));
            } catch (CannotCastException cce) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.IS_NOT_A_UNICODE_OBJECT, T___QUALNAME__);
            }
        }

        private static TruffleString getName(VirtualFrame frame, Node inliningTarget, Object func, CastToTruffleStringNode toStringNode, PyObjectLookupAttr lookupName) {
            return toStringNode.execute(inliningTarget, lookupName.execute(frame, inliningTarget, func, T___NAME__));
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonBuiltinNode {
        protected static boolean isSelfModuleOrNull(PMethod method) {
            return method.getSelf() == PNone.NO_VALUE || PGuards.isPythonModule(method.getSelf());
        }

        protected static boolean isSelfModuleOrNull(PBuiltinMethod method) {
            return method.getSelf() == PNone.NO_VALUE || PGuards.isPythonModule(method.getSelf());
        }

        @Specialization(guards = "isSelfModuleOrNull(method)")
        static TruffleString doSelfIsModule(VirtualFrame frame, PMethod method, @SuppressWarnings("unused") Object obj,
                        @Bind("this") Node inliningTarget,
                        @Shared("toStringNode") @Cached CastToTruffleStringNode toStringNode,
                        @Shared("getName") @Cached PyObjectGetAttr getName) {
            return getName(frame, inliningTarget, method.getFunction(), toStringNode, getName);
        }

        @Specialization(guards = "isSelfModuleOrNull(method)")
        static TruffleString doSelfIsModule(VirtualFrame frame, PBuiltinMethod method, @SuppressWarnings("unused") Object obj,
                        @Bind("this") Node inliningTarget,
                        @Shared("toStringNode") @Cached CastToTruffleStringNode toStringNode,
                        @Shared("getName") @Cached PyObjectGetAttr getName) {
            return getName(frame, inliningTarget, method.getFunction(), toStringNode, getName);
        }

        @Specialization(guards = "!isSelfModuleOrNull(method)")
        PTuple doSelfIsObject(VirtualFrame frame, PMethod method, @SuppressWarnings("unused") Object obj,
                        @Bind("this") Node inliningTarget,
                        @Shared("toStringNode") @Cached CastToTruffleStringNode toStringNode,
                        @Shared("getGetAttr") @Cached PyObjectGetAttr getGetAttr,
                        @Shared("getName") @Cached PyObjectGetAttr getName,
                        @Shared @Cached PythonObjectFactory factory) {
            PythonModule builtins = getContext().getBuiltins();
            Object getattr = getGetAttr.execute(frame, inliningTarget, builtins, T_GETATTR);
            PTuple args = factory.createTuple(new Object[]{method.getSelf(), getName(frame, inliningTarget, method.getFunction(), toStringNode, getName)});
            return factory.createTuple(new Object[]{getattr, args});
        }

        @Specialization(guards = "!isSelfModuleOrNull(method)")
        PTuple doSelfIsObject(VirtualFrame frame, PBuiltinMethod method, @SuppressWarnings("unused") Object obj,
                        @Bind("this") Node inliningTarget,
                        @Shared("toStringNode") @Cached CastToTruffleStringNode toStringNode,
                        @Shared("getGetAttr") @Cached PyObjectGetAttr getGetAttr,
                        @Shared("getName") @Cached PyObjectGetAttr getName,
                        @Shared @Cached PythonObjectFactory factory) {
            PythonModule builtins = getContext().getBuiltins();
            Object getattr = getGetAttr.execute(frame, inliningTarget, builtins, T_GETATTR);
            PTuple args = factory.createTuple(new Object[]{method.getSelf(), getName(frame, inliningTarget, method.getFunction(), toStringNode, getName)});
            return factory.createTuple(new Object[]{getattr, args});
        }

        private static TruffleString getName(VirtualFrame frame, Node inliningTarget, Object func, CastToTruffleStringNode toStringNode, PyObjectGetAttr getName) {
            return toStringNode.execute(inliningTarget, getName.execute(frame, inliningTarget, func, T___NAME__));
        }
    }
}
