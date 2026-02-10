package com.oracle.graal.python.builtins.objects.thread;

import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

import java.util.List;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PExceptHookArgs)
public class PExceptHookArgsBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = PExceptHookArgsBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PExceptHookArgsBuiltinsFactory.getFactories();
    }

    @Slot(value = Slot.SlotKind.tp_new, isComplex = true)
    @Slot.SlotSignature(name = "_ExceptHookArgs", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ExceptHookArgsNewNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doNew(Object cls, Object args,
                            @Cached TypeNodes.GetInstanceShape getInstanceShape,
                            @Cached PRaiseNode raiseNode) {
            if (!(args instanceof PSequence))
                throw PRaiseNode.getUncached().raise(raiseNode, PythonBuiltinClassType.TypeError,
                        ErrorMessages.ARG_S_MUST_BE_A_LIST_OR_TUPLE, "_thread.ExceptHookArgs(args)");

            Object[] items = SequenceStorageNodes.ToArrayNode.executeUncached(((PSequence) args).getSequenceStorage());

            if (items.length != 4)
                throw PRaiseNode.getUncached().raise(raiseNode, PythonBuiltinClassType.TypeError,
                        ErrorMessages.TAKES_EXACTLY_D_ARGUMENTS_D_GIVEN, 4, items.length);

            return new PExceptHookArgs(cls, getInstanceShape.execute(cls), items[0], items[1], items[2], items[3]);
        }
    }

    @Builtin(name = "exc_type", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class ExcTypeNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object getExcType(PExceptHookArgs self) {
            return self.getExcType();
        }
    }


    @Builtin(name = "exc_traceback", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class ExcTracebackNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object getExcTraceback(PExceptHookArgs self) {
            return self.getExcTraceback();
        }
    }


    @Builtin(name = "exc_value", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class ExcValueNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object getExcValue(PExceptHookArgs self) {
            return self.getExcValue();
        }
    }

    @Builtin(name = "thread", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class ThreadNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object getThread(PExceptHookArgs self) {
            return self.getThread();
        }
    }
}