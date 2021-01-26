package com.oracle.graal.python.builtins.objects.ssl;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.nodes.PGuards;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__STR__;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

@CoreFunctions(extendClasses = PythonBuiltinClassType.SSLError)
public class SSLErrorBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SSLErrorBuiltinsFactory.getFactories();
    }

    @Builtin(name = __STR__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class StrNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object str(VirtualFrame frame, PBaseException self,
                        @Cached("createGetStrerror()") GetAttributeNode getStrerror,
                        @Cached("createLookupArgs()") GetAttributeNode getArgs,
                        @Cached("createLookupStr()") LookupAndCallUnaryNode getStr) {
            Object str = getStrerror.executeObject(frame, self);
            if (PGuards.isString(str)) {
                return str;
            }
            return getStr.executeObject(frame, getArgs.executeObject(frame, self));
        }

        protected GetAttributeNode createGetStrerror() {
            return GetAttributeNode.create("strerror");
        }

        protected LookupAndCallUnaryNode createLookupStr() {
            return LookupAndCallUnaryNode.create(__STR__);
        }

        protected GetAttributeNode createLookupArgs() {
            return GetAttributeNode.create("args");
        }
    }

}
