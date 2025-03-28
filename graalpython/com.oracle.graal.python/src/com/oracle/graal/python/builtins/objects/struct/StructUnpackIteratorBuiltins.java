/* Copyright (c) 2020, 2025, Oracle and/or its affiliates.
 * Copyright (C) 1996-2020 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
package com.oracle.graal.python.builtins.objects.struct;

import static com.oracle.graal.python.builtins.objects.struct.StructBuiltins.unpackInternal;
import static com.oracle.graal.python.nodes.ErrorMessages.CANNOT_CREATE_P_OBJECTS;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LENGTH_HINT__;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.iterator.PStructUnpackIterator;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotIterNext.TpIterNextBuiltin;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PStructUnpackIterator)
public class StructUnpackIteratorBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = StructUnpackIteratorBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return StructUnpackIteratorBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    protected abstract static class NewNode extends PythonBuiltinNode {
        @Specialization
        static Object createNew(Object type, @SuppressWarnings("unused") Object[] args, @SuppressWarnings("unused") PKeyword[] kwds,
                        @Bind("this") Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, PythonBuiltinClassType.TypeError, CANNOT_CREATE_P_OBJECTS, type);
        }
    }

    @Slot(value = SlotKind.tp_iter, isComplex = true)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object iter(PStructUnpackIterator self) {
            return self;
        }
    }

    @Builtin(name = J___LENGTH_HINT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class LengthHintNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "3")
        static int lenHint(PStructUnpackIterator self,
                        @CachedLibrary("self.getBuffer()") PythonBufferAccessLibrary bufferLib) {
            if (self.getStruct() == null) {
                return 0;
            }
            int bufferLen = bufferLib.getBufferLength(self.getBuffer());
            return (bufferLen - self.index) / self.getStruct().getSize();
        }
    }

    @Slot(value = SlotKind.tp_iternext, isComplex = true)
    @GenerateNodeFactory
    public abstract static class NextNode extends TpIterNextBuiltin {
        @Specialization(guards = "self.isExhausted()")
        static Object nextExhausted(@SuppressWarnings("unused") PStructUnpackIterator self) {
            throw iteratorExhausted();
        }

        @Specialization(guards = "!self.isExhausted()", limit = "3")
        static Object next(VirtualFrame frame, PStructUnpackIterator self,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @Cached StructNodes.UnpackValueNode unpackValueNode,
                        @CachedLibrary("self.getBuffer()") PythonBufferAccessLibrary bufferLib,
                        @Bind PythonLanguage language) {
            final PStruct struct = self.getStruct();
            final Object buffer = self.getBuffer();
            final int bufferLen = bufferLib.getBufferLength(buffer);

            if (struct == null || self.index >= bufferLen) {
                self.setExhausted();
                bufferLib.release(buffer, frame, indirectCallData);
                throw iteratorExhausted();
            }

            assert self.index + struct.getSize() <= bufferLen;

            byte[] bytes;
            int offset;
            if (bufferLib.hasInternalByteArray(buffer)) {
                offset = self.index;
                bytes = bufferLib.getInternalByteArray(buffer);
            } else {
                offset = 0;
                bytes = new byte[struct.getSize()];
                bufferLib.readIntoByteArray(buffer, self.index, bytes, 0, bytes.length);
            }

            // TODO: GR-54860 handle buffers directly in unpack
            Object result = PFactory.createTuple(language, unpackInternal(struct, unpackValueNode, bytes, offset));
            self.index += struct.getSize();
            return result;
        }
    }
}
