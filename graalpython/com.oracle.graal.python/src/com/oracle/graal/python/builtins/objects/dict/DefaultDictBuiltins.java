package com.oracle.graal.python.builtins.objects.dict;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PDefaultDict)
public final class DefaultDictBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return DefaultDictBuiltinsFactory.getFactories();
    }

    @Builtin(name = __REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object reprFunction(VirtualFrame frame, PDefaultDict self,
                                   @Cached GetClassNode getClassNode,
                                   @Cached TypeNodes.GetNameNode getNameNode,
                                   @Cached LookupAndCallUnaryNode.LookupAndCallUnaryDynamicNode reprNode,
                                   @Cached DictReprBuiltin.ReprNode dictReprNode) {
            final Object klass = getClassNode.execute(self);
            return String.format("%s(%s, %s)", getNameNode.execute(klass),
                    reprNode.executeObject(self.getDefaultFactory(), __REPR__),
                    dictReprNode.call(frame, self));
        }
    }

    @Builtin(name = __REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object reduce(VirtualFrame frame, PDefaultDict self,
                      @Cached GetClassNode getClassNode,
                      @Cached PyObjectGetIter getIter,
                      @Cached DictBuiltins.ItemsNode itemsNode) {
            final Object defaultFactory = self.getDefaultFactory();
            PTuple args = (defaultFactory == PNone.NONE) ? factory().createEmptyTuple() : factory().createTuple(new Object[]{defaultFactory});
            return factory().createTuple(new Object[]{getClassNode.execute(self), args, PNone.NONE, PNone.NONE, getIter.execute(frame, itemsNode.items(self))});
        }
    }

    // copy()
    @Builtin(name = "copy", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class CopyNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "1")
        public PDefaultDict copy(@SuppressWarnings("unused") VirtualFrame frame, PDefaultDict self,
                          @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
            return factory().createDefaultDict(self.getDefaultFactory(), lib.copy(self.getDictStorage()));
        }
    }
}
