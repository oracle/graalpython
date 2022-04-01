package com.oracle.graal.python.nodes.bytecode;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;

import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.BranchProfile;

@GenerateUncached
public abstract class UnpackSequenceNode extends PNodeWithContext {
    public abstract void execute(Frame virtualFrame, int stackTop, Frame localFrame, Object collection, int count);

    @Specialization(guards = {"cannotBeOverridden(sequence, getClassNode)", "!isPString(sequence)"}, limit = "1")
    @ExplodeLoop
    static void doUnpackSequence(int stackTop, Frame localFrame, PSequence sequence, int count,
                    @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                    @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                    @Cached SequenceStorageNodes.LenNode lenNode,
                    @Cached SequenceStorageNodes.GetItemScalarNode getItemNode,
                    @Cached BranchProfile errorProfile,
                    @Shared("raise") @Cached PRaiseNode raiseNode) {
        SequenceStorage storage = getSequenceStorageNode.execute(sequence);
        int len = lenNode.execute(storage);
        if (len == count) {
            for (int i = 0; i < count; i++) {
                localFrame.setObject(stackTop + count - i, getItemNode.execute(storage, i));
            }
        } else {
            errorProfile.enter();
            if (len < count) {
                throw raiseNode.raise(ValueError, ErrorMessages.NOT_ENOUGH_VALUES_TO_UNPACK, count, len);
            } else {
                throw raiseNode.raise(ValueError, ErrorMessages.TOO_MANY_VALUES_TO_UNPACK, count);
            }
        }
    }

    @Fallback
    @ExplodeLoop
    static void doUnpackIterable(Frame virtualFrame, int stackTop, Frame localFrame, Object collection, int count,
                    @Cached PyObjectGetIter getIter,
                    @Cached GetNextNode getNextNode,
                    @Cached IsBuiltinClassProfile notIterableProfile,
                    @Cached IsBuiltinClassProfile stopIterationProfile1,
                    @Cached IsBuiltinClassProfile stopIterationProfile2,
                    @Shared("raise") @Cached PRaiseNode raiseNode) {
        Object iterator;
        try {
            iterator = getIter.execute(virtualFrame, collection);
        } catch (PException e) {
            e.expectTypeError(notIterableProfile);
            throw raiseNode.raise(TypeError, ErrorMessages.CANNOT_UNPACK_NON_ITERABLE, collection);
        }
        for (int i = 0; i < count; i++) {
            try {
                Object item = getNextNode.execute(virtualFrame, iterator);
                localFrame.setObject(stackTop + count - i, item);
            } catch (PException e) {
                e.expectStopIteration(stopIterationProfile1);
                throw raiseNode.raise(ValueError, ErrorMessages.NOT_ENOUGH_VALUES_TO_UNPACK, count, i);
            }
        }
        try {
            getNextNode.execute(virtualFrame, iterator);
        } catch (PException e) {
            e.expectStopIteration(stopIterationProfile2);
            return;
        }
        throw raiseNode.raise(ValueError, ErrorMessages.TOO_MANY_VALUES_TO_UNPACK, count);
    }

    public static UnpackSequenceNode create() {
        return UnpackSequenceNodeGen.create();
    }

    public static UnpackSequenceNode getUncached() {
        return UnpackSequenceNodeGen.getUncached();
    }
}
