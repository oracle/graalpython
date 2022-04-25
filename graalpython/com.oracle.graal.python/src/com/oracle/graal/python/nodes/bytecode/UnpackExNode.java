package com.oracle.graal.python.nodes.bytecode;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;

import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.BranchProfile;

@GenerateUncached
public abstract class UnpackExNode extends PNodeWithContext {
    public abstract int execute(Frame virtualFrame, int stackTop, Frame localFrame, Object collection, int countBefore, int countAfter);

    @Specialization(guards = {"cannotBeOverridden(sequence, getClassNode)", "!isPString(sequence)"}, limit = "1")
    static int doUnpackSequence(int initialStackTop, Frame localFrame, PSequence sequence, int countBefore, int countAfter,
                    @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                    @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                    @Cached SequenceStorageNodes.LenNode lenNode,
                    @Cached SequenceStorageNodes.GetItemScalarNode getItemNode,
                    @Cached SequenceStorageNodes.GetItemSliceNode getItemSliceNode,
                    @Cached BranchProfile errorProfile,
                    @Shared("factory") @Cached PythonObjectFactory factory,
                    @Shared("raise") @Cached PRaiseNode raiseNode) {
        int resultStackTop = initialStackTop + countBefore + 1 + countAfter;
        int stackTop = resultStackTop;
        SequenceStorage storage = getSequenceStorageNode.execute(sequence);
        int len = lenNode.execute(storage);
        int starLen = len - countBefore - countAfter;
        if (starLen < 0) {
            errorProfile.enter();
            throw raiseNode.raise(ValueError, ErrorMessages.NOT_ENOUGH_VALUES_TO_UNPACK_EX, countBefore + countAfter, len);
        }
        stackTop = moveItemsToStack(storage, localFrame, stackTop, 0, countBefore, getItemNode);
        PList starList = factory.createList(getItemSliceNode.execute(storage, countBefore, countBefore + starLen, 1, starLen));
        localFrame.setObject(stackTop--, starList);
        moveItemsToStack(storage, localFrame, stackTop, len - countAfter, countAfter, getItemNode);
        return resultStackTop;
    }

    @Fallback
    static int doUnpackIterable(VirtualFrame virtualFrame, int initialStackTop, Frame localFrame, Object collection, int countBefore, int countAfter,
                    @Cached PyObjectGetIter getIter,
                    @Cached GetNextNode getNextNode,
                    @Cached IsBuiltinClassProfile notIterableProfile,
                    @Cached IsBuiltinClassProfile stopIterationProfile,
                    @Cached ListNodes.ConstructListNode constructListNode,
                    @Cached SequenceStorageNodes.LenNode lenNode,
                    @Cached SequenceStorageNodes.GetItemScalarNode getItemNode,
                    @Cached SequenceStorageNodes.GetItemSliceNode getItemSliceNode,
                    @Shared("factory") @Cached PythonObjectFactory factory,
                    @Shared("raise") @Cached PRaiseNode raiseNode) {
        int resultStackTop = initialStackTop + countBefore + 1 + countAfter;
        int stackTop = resultStackTop;
        Object iterator;
        try {
            iterator = getIter.execute(virtualFrame, collection);
        } catch (PException e) {
            e.expectTypeError(notIterableProfile);
            throw raiseNode.raise(TypeError, ErrorMessages.CANNOT_UNPACK_NON_ITERABLE, collection);
        }
        stackTop = moveItemsToStack(virtualFrame, iterator, localFrame, stackTop, 0, countBefore, countBefore + countAfter, getNextNode, stopIterationProfile, raiseNode);
        PList starAndAfter = constructListNode.execute(virtualFrame, iterator);
        SequenceStorage storage = starAndAfter.getSequenceStorage();
        int lenAfter = lenNode.execute(storage);
        if (lenAfter < countAfter) {
            throw raiseNode.raise(ValueError, ErrorMessages.NOT_ENOUGH_VALUES_TO_UNPACK_EX, countBefore + countAfter, countBefore + lenAfter);
        }
        if (countAfter == 0) {
            localFrame.setObject(stackTop, starAndAfter);
        } else {
            int starLen = lenAfter - countAfter;
            PList starList = factory.createList(getItemSliceNode.execute(storage, 0, starLen, 1, starLen));
            localFrame.setObject(stackTop--, starList);
            moveItemsToStack(storage, localFrame, stackTop, starLen, countAfter, getItemNode);
        }
        return resultStackTop;
    }

    @ExplodeLoop
    private static int moveItemsToStack(VirtualFrame virtualFrame, Object iterator, Frame localFrame, int initialStackTop, int offset, int length, int totalLength, GetNextNode getNextNode,
                    IsBuiltinClassProfile stopIterationProfile, PRaiseNode raiseNode) {
        int stackTop = initialStackTop;
        for (int i = 0; i < length; i++) {
            try {
                Object item = getNextNode.execute(virtualFrame, iterator);
                localFrame.setObject(stackTop--, item);
            } catch (PException e) {
                e.expectStopIteration(stopIterationProfile);
                throw raiseNode.raise(ValueError, ErrorMessages.NOT_ENOUGH_VALUES_TO_UNPACK_EX, totalLength, offset + i);
            }
        }
        return stackTop;
    }

    @ExplodeLoop
    private static int moveItemsToStack(SequenceStorage storage, Frame localFrame, int initialStackTop, int offset, int length, SequenceStorageNodes.GetItemScalarNode getItemNode) {
        int stackTop = initialStackTop;
        for (int i = 0; i < length; i++) {
            localFrame.setObject(stackTop--, getItemNode.execute(storage, offset + i));
        }
        return stackTop;
    }

    public static UnpackExNode create() {
        return UnpackExNodeGen.create();
    }

    public static UnpackExNode getUncached() {
        return UnpackExNodeGen.getUncached();
    }
}
