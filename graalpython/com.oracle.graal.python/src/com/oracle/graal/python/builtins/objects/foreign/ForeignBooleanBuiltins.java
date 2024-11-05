/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates.
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

import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotInquiry.NbBoolBuiltinNode;
import com.oracle.graal.python.lib.PyObjectStrAsTruffleStringNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.BitAndNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.BitOrNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.BitXorNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;

import java.util.List;

import static com.oracle.graal.python.nodes.SpecialMethodNames.J___AND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INDEX__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___OR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RAND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ROR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RXOR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___STR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___XOR__;

/*
 * This class handles foreign booleans and reimplements the methods of Python bool.
 * We avoid inheriting from bool as that brings many complications, and we want to reuse the logic from ForeignNumberBuiltins.
 *
 * NOTE: We are not using IndirectCallContext here in this file
 * because it seems unlikely that these interop messages would call back to Python
 * and that we would also need precise frame info for that case.
 * Adding it shouldn't hurt peak, but might be a non-trivial overhead in interpreter.
 */
@CoreFunctions(extendClasses = PythonBuiltinClassType.ForeignBoolean)
public final class ForeignBooleanBuiltins extends PythonBuiltins {
    public static TpSlots SLOTS = ForeignBooleanBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ForeignBooleanBuiltinsFactory.getFactories();
    }

    @Slot(SlotKind.nb_bool)
    @GenerateUncached
    @GenerateNodeFactory
    abstract static class BoolNode extends NbBoolBuiltinNode {
        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()")
        static boolean bool(Object receiver,
                        @CachedLibrary("receiver") InteropLibrary lib,
                        @Cached GilNode gil) {
            gil.release(true);
            try {
                return lib.asBoolean(receiver);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            } finally {
                gil.acquire();
            }
        }
    }

    @Builtin(name = J___INDEX__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IndexNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "3")
        protected static Object doIt(Object object,
                        @CachedLibrary("object") InteropLibrary lib,
                        @Cached GilNode gil) {
            gil.release(true);
            try {
                try {
                    return PInt.intValue(lib.asBoolean(object));
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere("foreign value claims to be a boolean but isn't");
                }
            } finally {
                gil.acquire();
            }
        }
    }

    @Builtin(name = J___STR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class StrNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object str(VirtualFrame frame, Object object,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached GilNode gil,
                        @Cached PyObjectStrAsTruffleStringNode strNode) {
            final Object value;
            try {
                gil.release(true);
                try {
                    value = lib.asBoolean(object);
                } finally {
                    gil.acquire();
                }
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }

            return strNode.execute(frame, inliningTarget, value);
        }
    }

    @Builtin(name = J___REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends StrNode {
    }

    @Builtin(name = J___RAND__, minNumOfPositionalArgs = 2)
    @Builtin(name = J___AND__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class AndNode extends PythonBinaryBuiltinNode {
        @Specialization(limit = "3")
        protected static Object op(VirtualFrame frame, Object left, Object right,
                        @Cached BitAndNode andNode,
                        @CachedLibrary("left") InteropLibrary lib,
                        @Cached GilNode gil) {
            try {
                boolean leftBoolean;
                gil.release(true);
                try {
                    leftBoolean = lib.asBoolean(left);
                } finally {
                    gil.acquire();
                }
                return andNode.executeObject(frame, leftBoolean, right);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }
    }

    @Builtin(name = J___ROR__, minNumOfPositionalArgs = 2)
    @Builtin(name = J___OR__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class OrNode extends PythonBinaryBuiltinNode {
        @Specialization(limit = "3")
        protected static Object op(VirtualFrame frame, Object left, Object right,
                        @Cached BitOrNode orNode,
                        @CachedLibrary("left") InteropLibrary lib,
                        @Cached GilNode gil) {
            try {
                boolean leftBoolean;
                gil.release(true);
                try {
                    leftBoolean = lib.asBoolean(left);
                } finally {
                    gil.acquire();
                }
                return orNode.executeObject(frame, leftBoolean, right);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }
    }

    @Builtin(name = J___RXOR__, minNumOfPositionalArgs = 2)
    @Builtin(name = J___XOR__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class XorNode extends PythonBinaryBuiltinNode {
        @Specialization(limit = "3")
        protected static Object op(VirtualFrame frame, Object left, Object right,
                        @Cached BitXorNode xorNode,
                        @CachedLibrary("left") InteropLibrary lib,
                        @Cached GilNode gil) {
            try {
                boolean leftBoolean;
                gil.release(true);
                try {
                    leftBoolean = lib.asBoolean(left);
                } finally {
                    gil.acquire();
                }
                return xorNode.executeObject(frame, leftBoolean, right);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }
    }
}
