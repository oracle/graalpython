package com.oracle.graal.python.nodes.bytecode;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.AttributeError;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ENTER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EXIT__;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodSlotNode;
import com.oracle.graal.python.nodes.call.special.MaybeBindDescriptorNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;

@GenerateUncached
@ImportStatic(SpecialMethodSlot.class)
public abstract class SetupWithNode extends PNodeWithContext {
    public abstract int execute(Frame frame, int stackTop);

    @Specialization
    static int setup(VirtualFrame frame, int stackTopIn,
                    @Cached GetClassNode getClassNode,
                    @Cached(parameters = "Enter") LookupSpecialMethodSlotNode lookupEnter,
                    @Cached(parameters = "Exit") LookupSpecialMethodSlotNode lookupExit,
                    @Cached MaybeBindDescriptorNode bindEnter,
                    @Cached MaybeBindDescriptorNode bindExit,
                    @Cached CallUnaryMethodNode callEnter,
                    @Cached BranchProfile errorProfile,
                    @Cached PRaiseNode raiseNode) {
        int stackTop = stackTopIn;
        Object contextManager = frame.getObject(stackTop);
        Object type = getClassNode.execute(contextManager);
        Object enter = lookupEnter.execute(frame, type, contextManager);
        if (enter == PNone.NO_VALUE) {
            errorProfile.enter();
            throw raiseNode.raise(AttributeError, new Object[]{__ENTER__});
        }
        enter = bindEnter.execute(frame, enter, contextManager, type);
        Object exit = lookupExit.execute(frame, type, contextManager);
        if (exit == PNone.NO_VALUE) {
            errorProfile.enter();
            throw raiseNode.raise(AttributeError, new Object[]{__EXIT__});
        }
        exit = bindExit.execute(frame, exit, contextManager, type);
        Object res = callEnter.executeObject(frame, enter, contextManager);
        frame.setObject(++stackTop, exit);
        frame.setObject(++stackTop, res);
        return stackTop;
    }

    public static SetupWithNode create() {
        return SetupWithNodeGen.create();
    }

    public static SetupWithNode getUncached() {
        return SetupWithNodeGen.getUncached();
    }
}
