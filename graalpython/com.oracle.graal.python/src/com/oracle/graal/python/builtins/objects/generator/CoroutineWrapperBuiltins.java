package com.oracle.graal.python.builtins.objects.generator;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PCoroutineWrapper)
public class CoroutineWrapperBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return CoroutineWrapperBuiltinsFactory.getFactories();
    }

    @Builtin(name = "__iter__", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        public Object getIter(PCoroutineWrapper self) {
            return self;
        }
    }

    @Builtin(name = "__next__", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class NextNode extends PythonUnaryBuiltinNode {
        @Specialization
        public Object doNext(VirtualFrame frame, PCoroutineWrapper self,
                        @Cached CommonGeneratorBuiltins.SendNode send) {
            return send.execute(frame, self.coroutine, PNone.NONE);
        }
    }

    @Builtin(name = "send", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class SendNode extends PythonBinaryBuiltinNode {
        @Specialization
        public Object doSend(VirtualFrame frame, PCoroutineWrapper self, Object arg,
                        @Cached CommonGeneratorBuiltins.SendNode send) {
            return send.execute(frame, self.coroutine, arg);
        }
    }

    @Builtin(name = "throw", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    public abstract static class ThrowNode extends PythonBuiltinNode {
        @Specialization
        public Object doThrow(VirtualFrame frame, PCoroutineWrapper self, Object typ, Object val, Object tp,
                        @Cached CommonGeneratorBuiltins.ThrowNode throwNode) {
            return throwNode.execute(frame, self, typ, val, tp);
        }
    }
}
