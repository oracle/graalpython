/* Copyright (c) 2020, 2024, Oracle and/or its affiliates.
 * Copyright (C) 1996-2020 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
package com.oracle.graal.python.builtins.objects.struct;

import static com.oracle.graal.python.nodes.ErrorMessages.CANNOT_CREATE_P_OBJECTS;
import static com.oracle.graal.python.builtins.objects.struct.StructBuiltins.unpackInternal;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LENGTH_HINT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEXT__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.iterator.PStructUnpackIterator;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
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
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return StructUnpackIteratorBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___NEW__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    protected abstract static class NewNode extends PythonBuiltinNode {
        @Specialization
        static Object createNew(Object type, @SuppressWarnings("unused") Object[] args, @SuppressWarnings("unused") PKeyword[] kwds,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(PythonBuiltinClassType.TypeError, CANNOT_CREATE_P_OBJECTS, type);
        }
    }

    @Builtin(name = J___ITER__, minNumOfPositionalArgs = 1)
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

    @Builtin(name = J___NEXT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class NextNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "self.isExhausted()")
        static Object nextExhausted(@SuppressWarnings("unused") PStructUnpackIterator self,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(PythonBuiltinClassType.StopIteration);
        }

        @Specialization(guards = "!self.isExhausted()", limit = "3")
        static Object next(VirtualFrame frame, PStructUnpackIterator self,
                        @Bind("this") Node inliningTarget,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @Cached StructNodes.UnpackValueNode unpackValueNode,
                        @CachedLibrary("self.getBuffer()") PythonBufferAccessLibrary bufferLib,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            final PStruct struct = self.getStruct();
            final Object buffer = self.getBuffer();
            final int bufferLen = bufferLib.getBufferLength(buffer);

            if (struct == null || self.index >= bufferLen) {
                self.setExhausted();
                bufferLib.release(buffer, frame, indirectCallData);
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.StopIteration);
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
            Object result = factory.createTuple(unpackInternal(struct, unpackValueNode, bytes, offset));
            self.index += struct.getSize();
            return result;
        }
    }
}
