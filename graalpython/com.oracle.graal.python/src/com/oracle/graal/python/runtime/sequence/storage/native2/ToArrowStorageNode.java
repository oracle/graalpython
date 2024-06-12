package com.oracle.graal.python.runtime.sequence.storage.native2;

import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.sequence.storage.ArrayBasedSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.IntSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

@GenerateInline
@GenerateCached(false)
@GenerateUncached
public abstract class ToArrowStorageNode extends Node {

    public abstract ArrowSequenceStorage execute(Node inliningTarget, SequenceStorage storage);

    public static ArrowSequenceStorage executeUncached(SequenceStorage storage) {
        return ToArrowStorageNodeGen.getUncached().execute(null, storage);
    }

    @Specialization
    static ArrowSequenceStorage doInt(Node inliningTarget, IntSequenceStorage storage) {
        int[] arr = storage.getInternalIntArray();
        var buffer = PythonContext.get(inliningTarget).nativeBufferContext.toNativeBuffer(arr);

        return new IntArrowSequenceStorage(buffer, storage.length());
    }

}
