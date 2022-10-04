package com.oracle.graal.python.builtins.objects.types;

import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___ARGS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___OR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REPR__;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.nodes.StringLiterals;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PUnionType)
public class UnionTypeBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return UnionTypeBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___ARGS__, numOfPositionalOnlyArgs = 1, isGetter = true)
    @GenerateNodeFactory
    static abstract class ArgsNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object args(PUnionType self) {
            return self.getArgs();
        }
    }

    @Builtin(name = J___OR__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class OrNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object union(Object self, Object other,
                        @Cached GenericTypeNodes.UnionTypeOrNode orNode) {
            return orNode.execute(self, other);
        }
    }

    @Builtin(name = J___REPR__, numOfPositionalOnlyArgs = 1)
    @GenerateNodeFactory
    static abstract class ReprNode extends PythonUnaryBuiltinNode {
        private static final TruffleString SEPARATOR = tsLiteral(" | ");

        @Specialization
        @TruffleBoundary
        Object repr(PUnionType self) {
            TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING);
            SequenceStorage argsStorage = self.getArgs().getSequenceStorage();
            for (int i = 0; i < argsStorage.length(); i++) {
                if (i > 0) {
                    sb.appendStringUncached(SEPARATOR);
                }
                reprItem(sb, argsStorage.getItemNormalized(i));
            }
            return sb.toStringUncached();
        }

        // Equivalent of union_repr_item in CPython
        private static void reprItem(TruffleStringBuilder sb, Object obj) {
            TypeNodes.IsSameTypeNode sameType = TypeNodes.IsSameTypeNode.getUncached();
            if (sameType.execute(obj, PythonBuiltinClassType.PNone)) {
                sb.appendStringUncached(StringLiterals.T_NONE);
                return;
            }
            GenericTypeNodes.reprItem(sb, obj);
        }
    }
}
