package com.oracle.graal.python.builtins.objects.generator;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.iterator.IteratorNodes;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodSlotNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.nodes.Node;

@GenerateUncached
@ImportStatic(SpecialMethodSlot.class)
public abstract class GetAwaitableNode extends Node {
    public abstract Object execute(Frame frame, Object arg);

    @Specialization
    public Object doGenerator(PGenerator generator,
                    @Cached PRaiseNode raise) {
        if (generator.isCoroutine) {
            return generator;
        } else {
            throw raise.raise(PythonBuiltinClassType.TypeError, ErrorMessages.CANNOT_BE_USED_AWAIT, "generator");
        }
    }

    @Specialization
    public Object doGeneric(Frame frame, Object awaitable,
                    @Cached PRaiseNode raiseNoAwait,
                    @Cached PRaiseNode raiseNotIter,
                    @Cached(parameters = "Await") LookupSpecialMethodSlotNode findAwait,
                    @Cached TypeNodes.GetNameNode getName,
                    @Cached GetClassNode getAwaitableType,
                    @Cached GetClassNode getIteratorType,
                    @Cached CallUnaryMethodNode callAwait,
                    @Cached IteratorNodes.IsIteratorObjectNode isIterator) {
        Object type = getAwaitableType.execute(awaitable);
        Object getter = findAwait.execute(frame, type, awaitable);
        if (getter == PNone.NO_VALUE) {
            throw raiseNoAwait.raise(PythonBuiltinClassType.TypeError, ErrorMessages.CANNOT_BE_USED_AWAIT, getName.execute(type));
        }
        Object iterator = callAwait.executeObject(getter, awaitable);
        if (isIterator.execute(iterator)) {
            return iterator;
        }
        Object itType = getIteratorType.execute(iterator);
        if (itType == PythonBuiltinClassType.PCoroutine) {
            throw raiseNotIter.raise(PythonBuiltinClassType.TypeError, ErrorMessages.AWAIT_RETURN_COROUTINE);
        } else {
            throw raiseNotIter.raise(PythonBuiltinClassType.TypeError, ErrorMessages.AWAIT_RETURN_NON_ITER, getName.execute(itType));
        }
    }

    public static GetAwaitableNode create() {
        return GetAwaitableNodeGen.create();
    }

    public static GetAwaitableNode getUncached() {
        return GetAwaitableNodeGen.getUncached();
    }
}
