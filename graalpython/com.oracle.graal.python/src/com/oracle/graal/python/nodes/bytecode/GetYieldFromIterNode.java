package com.oracle.graal.python.nodes.bytecode;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.generator.PGenerator;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.nodes.Node;

@GenerateUncached
public abstract class GetYieldFromIterNode extends Node {
    public abstract Object execute(Frame frame, Object receiver);

    @Specialization
    public Object getGeneratorOrCoroutine(PGenerator arg) {
        // TODO check if the generator in which the yield from is an iterable or normal coroutine
        return arg;
    }

    @Specialization
    public Object getGeneric(Frame frame, Object arg,
                    @Cached PyObjectGetIter getIter,
                    @Cached IsBuiltinClassProfile isCoro) {
        if (isCoro.profileObject(arg, PythonBuiltinClassType.PCoroutine)) {
            return arg;
        } else {
            return getIter.execute(frame, arg);
        }
    }

    public static GetYieldFromIterNode create() {
        return GetYieldFromIterNodeGen.create();
    }

    public static GetYieldFromIterNode getUncached() {
        return GetYieldFromIterNodeGen.getUncached();
    }
}
