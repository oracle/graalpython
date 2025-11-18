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

import static com.oracle.graal.python.nodes.BuiltinNames.T_POLYGLOT;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___ANNOTATIONS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___BASES__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___MODULE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___QUALNAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___TYPE_PARAMS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INSTANCECHECK__;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryFunc.MpSubscriptBuiltinNode;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.interop.PForeignToPTypeNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;

/*
 * NOTE: We are not using IndirectCallContext here in this file
 * because it seems unlikely that these interop messages would call back to Python
 * and that we would also need precise frame info for that case.
 * Adding it shouldn't hurt peak, but might be a non-trivial overhead in interpreter.
 */
@CoreFunctions(extendClasses = PythonBuiltinClassType.ForeignAbstractClass)
public final class ForeignAbstractClassBuiltins extends PythonBuiltins {
    public static TpSlots SLOTS = ForeignAbstractClassBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ForeignAbstractClassBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___BASES__, minNumOfPositionalArgs = 1, isGetter = true, isSetter = false)
    @GenerateNodeFactory
    abstract static class BasesNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "2")
        static Object getBases(VirtualFrame frame, Object self,
                        @Bind Node inliningTarget,
                        @CachedLibrary("self") InteropLibrary lib,
                        @Cached PyObjectGetIter getIter,
                        @Cached SequenceStorageNodes.CreateStorageFromIteratorNode createStorageFromIteratorNode,
                        @Bind PythonLanguage language) {
            if (lib.hasMetaParents(self)) {
                try {
                    Object parents = lib.getMetaParents(self);
                    Object iterObj = getIter.execute(frame, inliningTarget, parents);
                    SequenceStorage storage = createStorageFromIteratorNode.execute(frame, iterObj);
                    return PFactory.createTuple(language, storage);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            } else {
                return PFactory.createEmptyTuple(language);
            }
        }
    }

    @Builtin(name = J___NAME__, minNumOfPositionalArgs = 1, isGetter = true, isSetter = false)
    @GenerateNodeFactory
    abstract static class ForeignNameNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "2")
        static Object foreignName(Object self,
                        @CachedLibrary("self") InteropLibrary lib,
                        @Cached PForeignToPTypeNode castStr) {
            try {
                return castStr.executeConvert(lib.getMetaSimpleName(self));
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    @Builtin(name = J___QUALNAME__, minNumOfPositionalArgs = 1, isGetter = true, isSetter = false)
    @GenerateNodeFactory
    abstract static class ForeignQualnameNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "2")
        static Object foreignName(Object self,
                        @CachedLibrary("self") InteropLibrary lib,
                        @Cached PForeignToPTypeNode castStr) {
            try {
                return castStr.executeConvert(lib.getMetaQualifiedName(self));
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    @Builtin(name = J___MODULE__, minNumOfPositionalArgs = 1, isGetter = true, isSetter = false)
    @GenerateNodeFactory
    abstract static class ForeignModuleNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object foreignModule(@SuppressWarnings("unused") Object self) {
            return T_POLYGLOT;
        }
    }

    @Builtin(name = J___TYPE_PARAMS__, minNumOfPositionalArgs = 1, isGetter = true, isSetter = false)
    @GenerateNodeFactory
    abstract static class ForeignTypeParams extends PythonUnaryBuiltinNode {
        @Specialization
        static Object foreignTypeParams(@SuppressWarnings("unused") Object self,
                        @Bind PythonLanguage lang) {
            return PFactory.createEmptyTuple(lang);
        }
    }

    @Builtin(name = J___ANNOTATIONS__, minNumOfPositionalArgs = 1, isGetter = true, isSetter = false)
    @GenerateNodeFactory
    abstract static class ForeignAnnotations extends PythonUnaryBuiltinNode {
        @Specialization
        static Object foreignAnnotations(@SuppressWarnings("unused") Object self,
                        @Bind PythonLanguage lang) {
            // TODO: use the declared members interop API to return annotations
            return PFactory.createDict(lang);
        }
    }

    @Builtin(name = J___INSTANCECHECK__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class InstancecheckNode extends PythonBinaryBuiltinNode {
        @Specialization(limit = "3")
        static Object check(Object self, Object instance,
                        @CachedLibrary("self") InteropLibrary lib,
                        @Cached GilNode gil) {
            gil.release(true);
            try {
                return lib.isMetaInstance(self, instance);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere();
            } finally {
                gil.acquire();
            }
        }
    }

    @Slot(value = SlotKind.mp_subscript, isComplex = true)
    @GenerateNodeFactory
    abstract static class ForeignClassGetItemNode extends MpSubscriptBuiltinNode {
        @Specialization
        static Object classGetItem(Object cls, Object key,
                        @Bind PythonLanguage language) {
            return PFactory.createGenericAlias(language, cls, key);
        }
    }
}
