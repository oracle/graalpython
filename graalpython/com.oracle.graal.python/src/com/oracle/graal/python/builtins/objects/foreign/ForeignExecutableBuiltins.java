/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates.
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

package com.oracle.graal.python.builtins.objects.foreign;

import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___DOC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___NAME__;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.interop.PForeignToPTypeNode;
import com.oracle.graal.python.runtime.ExecutionContext.InteropCallContext;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.IndirectCallData.InteropCallData;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;

@CoreFunctions(extendClasses = PythonBuiltinClassType.ForeignExecutable)
public final class ForeignExecutableBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = ForeignExecutableBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ForeignExecutableBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___NAME__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class NameNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object getName(Object self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode,
                        @Cached PForeignToPTypeNode toPythonNode,
                        @CachedLibrary(limit = "2") InteropLibrary lib) {
            try {
                if (lib.isMemberReadable(self, J___NAME__)) {
                    return toPythonNode.executeConvert(lib.readMember(self, J___NAME__));
                } else if (lib.hasExecutableName(self)) {
                    return toPythonNode.executeConvert(lib.getExecutableName(self));
                } else {
                    throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.AttributeError, ErrorMessages.OBJ_P_HAS_NO_ATTR_S, self, T___NAME__);
                }
            } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    @Builtin(name = J___DOC__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class DocNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object getName(Object self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode,
                        @Cached PForeignToPTypeNode toPythonNode,
                        @CachedLibrary(limit = "2") InteropLibrary lib) {
            if (lib.isMemberReadable(self, J___DOC__)) {
                try {
                    return toPythonNode.executeConvert(lib.readMember(self, J___DOC__));
                } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            } else {
                return PNone.NONE;
            }
        }
    }

    @Slot(value = SlotKind.tp_call, isComplex = true)
    @SlotSignature(minNumOfPositionalArgs = 1, takesVarArgs = true)
    @GenerateNodeFactory
    public abstract static class CallNode extends PythonBuiltinNode {
        @Specialization
        static Object doInteropCall(VirtualFrame frame, Object callee, Object[] arguments,
                        @Bind Node inliningTarget,
                        @Cached("createFor($node)") InteropCallData callData,
                        @CachedLibrary(limit = "4") InteropLibrary lib,
                        @Cached PForeignToPTypeNode toPTypeNode,
                        @Cached GilNode.Interop gil,
                        @Cached PRaiseNode raiseNode) {
            PythonContext context = PythonContext.get(inliningTarget);
            PythonLanguage language = context.getLanguage(inliningTarget);
            try {
                Object state = InteropCallContext.enter(frame, language, context, callData);
                gil.release(context, true);
                try {
                    return toPTypeNode.executeConvert(lib.execute(callee, arguments));
                } finally {
                    gil.acquire(context, inliningTarget);
                    InteropCallContext.exit(frame, language, context, state);
                }
            } catch (ArityException | UnsupportedTypeException e) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.INVALID_INSTANTIATION_OF_FOREIGN_OBJ);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

}
