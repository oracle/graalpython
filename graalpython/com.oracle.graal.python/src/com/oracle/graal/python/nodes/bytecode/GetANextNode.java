package com.oracle.graal.python.nodes.bytecode;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodSlotNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;

import static com.oracle.graal.python.nodes.ErrorMessages.ASYNC_FOR_NO_ANEXT_ITERATION;

@GenerateUncached
@ImportStatic(SpecialMethodSlot.class)
public abstract class GetANextNode extends PNodeWithContext {
    public abstract Object execute(Frame frame, Object receiver);

    public static GetANextNode getUncached() {
        return GetANextNodeGen.getUncached();
    }

    public static GetANextNode create() {
        return GetANextNodeGen.create();
    }

    @Specialization
    Object doGeneric(Frame frame, Object receiver,
                    @Cached(parameters = "ANext") LookupSpecialMethodSlotNode getANext,
                    @Cached GetClassNode getAsyncIterType,
                    @Cached PRaiseNode raiseNoANext,
                    @Cached TypeNodes.GetNameNode getName,
                    @Cached InlinedBranchProfile errorProfile,
                    @Cached CallUnaryMethodNode callANext) {
        Object type = getAsyncIterType.execute(receiver);
        Object getter = getANext.execute(frame, type, receiver);
        if (getter == PNone.NO_VALUE) {
            errorProfile.enter(this);
            raiseNoANext.raise(PythonBuiltinClassType.TypeError, ASYNC_FOR_NO_ANEXT_ITERATION, getName.execute(type));
        }
        return callANext.executeObject(frame, getter, receiver);
    }
}
