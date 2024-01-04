package com.oracle.graal.python.builtins.modules.pickle;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.BufferError;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.memoryview.MemoryViewNodes;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.lib.PyMemoryViewFromObject;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.BufferFormat;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PickleBuffer)
public class PickleBufferBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PickleBufferBuiltinsFactory.getFactories();
    }

    // functions
    @Builtin(name = "raw", minNumOfPositionalArgs = 1, parameterNames = {"$self"})
    @GenerateNodeFactory
    public abstract static class PickleBufferRawNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object raw(VirtualFrame frame, PPickleBuffer self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyMemoryViewFromObject memoryViewFromObject,
                        @Cached MemoryViewNodes.ReleaseNode releaseNode,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            final Object view = self.getView();
            if (view == null) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.ValueError, ErrorMessages.OP_FORBIDDEN_ON_OBJECT, "PickleBuffer");
            }
            PMemoryView mv = memoryViewFromObject.execute(frame, self);
            // Make it into raw (1-dimensional bytes) memoryview
            try {
                if (!mv.isCContiguous() && !mv.isFortranContiguous()) {
                    throw raiseNode.get(inliningTarget).raise(BufferError, ErrorMessages.CANNOT_EXTRACT_RAW_BUFFER_FROM_NON_CONTIGUOUS);
                }
                int[] shape = new int[]{mv.getLength()};
                int[] strides = new int[]{1};
                return factory.createMemoryView(getContext(), mv.getLifecycleManager(), mv.getBuffer(), mv.getOwner(), mv.getLength(),
                                mv.isReadOnly(), 1, BufferFormat.UINT_8, BufferFormat.T_UINT_8_TYPE_CODE, 1,
                                mv.getBufferPointer(), mv.getOffset(), shape, strides,
                                null, PMemoryView.FLAG_C | PMemoryView.FLAG_FORTRAN);
            } finally {
                releaseNode.execute(frame, mv);
            }
        }
    }

    @Builtin(name = "release", minNumOfPositionalArgs = 1, parameterNames = {"$self"})
    @GenerateNodeFactory
    public abstract static class PickleBufferReleaseNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object release(PPickleBuffer self,
                        @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib) {
            Object view = self.getView();
            if (view != null) {
                bufferLib.release(view);
                self.release();
            }
            return PNone.NONE;
        }
    }
}
