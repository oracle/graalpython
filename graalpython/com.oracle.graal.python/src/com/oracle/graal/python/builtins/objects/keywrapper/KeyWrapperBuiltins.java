package com.oracle.graal.python.builtins.objects.keywrapper;

import static com.oracle.graal.python.nodes.ErrorMessages.OTHER_ARG_MUST_BE_KEY;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__CALL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LT__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PKeyWrapper)
public class KeyWrapperBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return KeyWrapperBuiltinsFactory.getFactories();
    }

    abstract static class WrapperKeyCompareNode extends PythonBinaryBuiltinNode {
        @Child private BinaryComparisonNode comparisonNode;
        @Child private PyObjectIsTrueNode isTrueNode;
        @Child private CallNode callNode;

        protected BinaryComparisonNode ensureComparisonNode() {
            if (comparisonNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                comparisonNode = insert(createCmp());
            }
            return comparisonNode;
        }

        protected PyObjectIsTrueNode ensureIsTrueNode() {
            if (isTrueNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isTrueNode = insert(PyObjectIsTrueNode.create());
            }
            return isTrueNode;
        }

        protected CallNode ensureCallNode() {
            if (callNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callNode = insert(CallNode.create());
            }
            return callNode;
        }

        BinaryComparisonNode createCmp() {
            throw CompilerDirectives.shouldNotReachHere();
        }

        @Specialization
        public boolean doCompare(VirtualFrame frame, PKeyWrapper self, PKeyWrapper other) {
            final Object cmpResult = ensureCallNode().execute(frame, self.getCmp(), self.getObject(), other.getObject());
            return ensureIsTrueNode().execute(frame, ensureComparisonNode().executeObject(frame, cmpResult, 0));
        }

        @Fallback
        @SuppressWarnings("unused")
        public boolean fallback(Object self, Object other) {
            throw raise(PythonBuiltinClassType.TypeError, OTHER_ARG_MUST_BE_KEY);
        }
    }

    @Builtin(name = __LE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class KWLeNode extends WrapperKeyCompareNode {
        @Override
        BinaryComparisonNode createCmp() {
            return BinaryComparisonNode.LeNode.create();
        }
    }

    @Builtin(name = __LT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class KWLtNode extends WrapperKeyCompareNode {
        @Override
        BinaryComparisonNode createCmp() {
            return BinaryComparisonNode.LtNode.create();
        }
    }

    @Builtin(name = __GE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class KWGeNode extends WrapperKeyCompareNode {
        @Override
        BinaryComparisonNode createCmp() {
            return BinaryComparisonNode.GeNode.create();
        }
    }

    @Builtin(name = __GT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class KWGtNode extends WrapperKeyCompareNode {
        @Override
        BinaryComparisonNode createCmp() {
            return BinaryComparisonNode.GtNode.create();
        }
    }

    @Builtin(name = __EQ__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class KWEqNode extends WrapperKeyCompareNode {
        @Override
        BinaryComparisonNode createCmp() {
            return BinaryComparisonNode.EqNode.create();
        }
    }

    @Builtin(name = __CALL__, minNumOfPositionalArgs = 1, parameterNames = {"obj"})
    @GenerateNodeFactory
    public abstract static class KWCallNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object call(PKeyWrapper self, Object obj) {
            final PKeyWrapper keyWrapper = factory().createKeyWrapper(self.getCmp());
            keyWrapper.setObject(obj);
            return keyWrapper;
        }
    }

    @Builtin(name = "obj", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, doc = "Value wrapped by a key function.")
    @GenerateNodeFactory
    public abstract static class KeyWrapperObjNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(value)")
        static Object doGet(PKeyWrapper self, @SuppressWarnings("unused") PNone value) {
            return self.getObject();
        }

        @Specialization
        static Object doSet(PKeyWrapper self, Object value) {
            self.setObject(value);
            return PNone.NONE;
        }
    }
}
