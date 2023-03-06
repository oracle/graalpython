package com.oracle.graal.python.nodes.bytecode;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
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

import static com.oracle.graal.python.nodes.ErrorMessages.ASYNC_FOR_NO_AITER;
import static com.oracle.graal.python.nodes.ErrorMessages.ASYNC_FOR_NO_ANEXT_INITIAL;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ANEXT__;

@GenerateUncached
@ImportStatic(SpecialMethodSlot.class)
public abstract class GetAIterNode extends PNodeWithContext {
    public abstract Object execute(Frame frame, Object receiver);

    public static GetAIterNode getUncached() {
        return GetAIterNodeGen.getUncached();
    }

    public static GetAIterNode create() {
        return GetAIterNodeGen.create();
    }

    @Specialization
    Object doGeneric(Frame frame, Object receiver,
                    @Cached(parameters = "AIter") LookupSpecialMethodSlotNode getAIter,
                    @Cached GetClassNode getAsyncIterType,
                    @Cached PRaiseNode raiseNoAIter,
                    @Cached TypeNodes.GetNameNode getName,
                    @Cached InlinedBranchProfile errorProfile,
                    @Cached CallUnaryMethodNode callAIter,
                    @Cached LookupInheritedAttributeNode.Dynamic lookupANext) {

        Object type = getAsyncIterType.execute(receiver);
        Object getter = getAIter.execute(frame, type, receiver);
        if (getter == PNone.NO_VALUE) {
            errorProfile.enter(this);
            raiseNoAIter.raise(PythonBuiltinClassType.TypeError, ASYNC_FOR_NO_AITER, getName.execute(type));
        }
        Object asyncIterator = callAIter.executeObject(frame, getter, receiver);
        Object anext = lookupANext.execute(asyncIterator, T___ANEXT__);
        if (anext == PNone.NO_VALUE) {
            errorProfile.enter(this);
            raiseNoAIter.raise(PythonBuiltinClassType.TypeError, ASYNC_FOR_NO_ANEXT_INITIAL, getName.execute(type));
        }
        return asyncIterator;
    }
}
