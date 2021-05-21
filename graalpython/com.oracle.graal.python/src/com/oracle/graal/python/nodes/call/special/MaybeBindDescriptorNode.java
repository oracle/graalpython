package com.oracle.graal.python.nodes.call.special;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodDescriptor;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.attributes.LookupCallableSlotInMRONode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * Bind the descriptor to the receiver unless it's one of the descriptor types that our method call
 * nodes handle unbound.
 */
@GenerateUncached
@ImportStatic(SpecialMethodSlot.class)
public abstract class MaybeBindDescriptorNode extends PNodeWithContext {

    /**
     * Wrapper for bound descriptor cases, just to be able to distinguish them from unbound
     * callables.
     */
    public static class BoundDescriptor {
        public final Object descriptor;

        public BoundDescriptor(Object descriptor) {
            this.descriptor = descriptor;
        }
    }

    public abstract Object execute(Frame frame, Object descriptor, Object receiver, Object receiverType);

    @Specialization(guards = "isNoValue(descriptor)")
    static Object doNoValue(Object descriptor, @SuppressWarnings("unused") Object receiver, @SuppressWarnings("unused") Object receiverType) {
        return descriptor;
    }

    @Specialization
    static Object doBuiltin(BuiltinMethodDescriptor descriptor, @SuppressWarnings("unused") Object receiver, @SuppressWarnings("unused") Object receiverType) {
        return descriptor;
    }

    @Specialization
    static Object doBuiltin(PBuiltinFunction descriptor, @SuppressWarnings("unused") Object receiver, @SuppressWarnings("unused") Object receiverType) {
        return descriptor;
    }

    @Specialization
    static Object doFunction(PFunction descriptor, @SuppressWarnings("unused") Object receiver, @SuppressWarnings("unused") Object receiverType) {
        return descriptor;
    }

    protected static boolean needsToBind(Object descriptor) {
        return !(descriptor == PNone.NO_VALUE || descriptor instanceof BuiltinMethodDescriptor || descriptor instanceof PBuiltinFunction || descriptor instanceof PFunction);
    }

    @Specialization(guards = "needsToBind(descriptor)")
    static Object doBind(VirtualFrame frame, Object descriptor, Object receiver, Object receiverType,
                    @Cached GetClassNode getClassNode,
                    @Cached(parameters = "Get") LookupCallableSlotInMRONode lookupGet,
                    @Cached CallTernaryMethodNode callGet) {
        Object getMethod = lookupGet.execute(getClassNode.execute(descriptor));
        if (getMethod != PNone.NO_VALUE) {
            return new BoundDescriptor(callGet.execute(frame, getMethod, descriptor, receiver, receiverType));
        }
        // CPython considers non-descriptors already bound
        return new BoundDescriptor(descriptor);
    }

    public static MaybeBindDescriptorNode create() {
        return MaybeBindDescriptorNodeGen.create();
    }

    public static MaybeBindDescriptorNode getUncached() {
        return MaybeBindDescriptorNodeGen.getUncached();
    }
}
