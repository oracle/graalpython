package com.oracle.graal.python.lib;

import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GETSTATE__;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

@GenerateInline
@GenerateCached(false)
@GenerateUncached
public abstract class PyObjectGetStateNode extends Node {

    public abstract Object execute(VirtualFrame frame, Node inliningTarget, Object self);

    @Specialization
    static Object get(VirtualFrame frame, Node inliningTarget, Object self,
                    @Cached PyObjectCallMethodObjArgs callMethod) {
        return callMethod.execute(frame, inliningTarget, self, T___GETSTATE__);
    }
}
