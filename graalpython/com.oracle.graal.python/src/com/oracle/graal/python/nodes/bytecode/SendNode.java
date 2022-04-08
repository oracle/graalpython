package com.oracle.graal.python.nodes.bytecode;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.exception.StopIterationBuiltins;
import com.oracle.graal.python.builtins.objects.generator.GeneratorBuiltins;
import com.oracle.graal.python.builtins.objects.generator.PGenerator;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.attributes.LookupCallableSlotInMRONode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class SendNode extends PNodeWithContext {
    // Returns true when the generator finished
    public abstract boolean execute(VirtualFrame virtualFrame, int stackTop, Frame localFrame, Object iter, Object arg);

    @Specialization
    boolean doGenerator(VirtualFrame virtualFrame, int stackTop, Frame localFrame, PGenerator generator, Object arg,
                    @Cached GeneratorBuiltins.SendNode sendNode,
                    @Shared("profile") @Cached IsBuiltinClassProfile stopIterationProfile,
                    @Shared("getValue") @Cached StopIterationBuiltins.StopIterationValueNode getValue) {
        try {
            Object value = sendNode.execute(virtualFrame, generator, arg);
            localFrame.setObject(stackTop, value);
            return false;
        } catch (PException e) {
            handleException(e, stopIterationProfile, getValue, stackTop, localFrame);
            return true;
        }
    }

    @Specialization(guards = "pyIterCheck(iter, getClassNode, lookupNext)", limit = "1")
    boolean doIterator(VirtualFrame virtualFrame, int stackTop, Frame localFrame, Object iter, @SuppressWarnings("unused") PNone arg,
                    @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                    @SuppressWarnings("unused") @Cached(parameters = "Next") LookupCallableSlotInMRONode lookupNext,
                    @Cached GetNextNode getNextNode,
                    @Shared("profile") @Cached IsBuiltinClassProfile stopIterationProfile,
                    @Shared("getValue") @Cached StopIterationBuiltins.StopIterationValueNode getValue) {
        try {
            Object value = getNextNode.execute(virtualFrame, iter);
            localFrame.setObject(stackTop, value);
            return false;
        } catch (PException e) {
            handleException(e, stopIterationProfile, getValue, stackTop, localFrame);
            return true;
        }
    }

    @Fallback
    boolean doOther(VirtualFrame virtualFrame, int stackTop, Frame localFrame, Object obj, Object arg,
                    @Cached PyObjectCallMethodObjArgs callMethodNode,
                    @Shared("profile") @Cached IsBuiltinClassProfile stopIterationProfile,
                    @Shared("getValue") @Cached StopIterationBuiltins.StopIterationValueNode getValue) {
        try {
            Object value = callMethodNode.execute(virtualFrame, obj, "send", arg);
            localFrame.setObject(stackTop, value);
            return false;
        } catch (PException e) {
            handleException(e, stopIterationProfile, getValue, stackTop, localFrame);
            return true;
        }
    }

    private static void handleException(PException e, IsBuiltinClassProfile stopIterationProfile, StopIterationBuiltins.StopIterationValueNode getValue, int stackTop, Frame localFrame) {
        e.expectStopIteration(stopIterationProfile);
        Object value = getValue.execute(e.getUnreifiedException());
        localFrame.setObject(stackTop, null);
        localFrame.setObject(stackTop - 1, value);
    }

    protected static boolean pyIterCheck(Object obj, GetClassNode getClassNode, LookupCallableSlotInMRONode lookupIternext) {
        return !(lookupIternext.execute(getClassNode.execute(obj)) instanceof PNone);
    }

    public static SendNode create() {
        return SendNodeGen.create();
    }
}
