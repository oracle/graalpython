package com.oracle.graal.python.nodes.frame;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.strings.TruffleString;

@GenerateUncached
public abstract class ReadFromLocalsNode extends PNodeWithContext implements AccessNameNode {
    public abstract Object execute(VirtualFrame frame, Object locals, TruffleString name);

    @Specialization(guards = "locals == null")
    @SuppressWarnings("unused")
    static Object noLocals(VirtualFrame frame, Object locals, TruffleString name) {
        return PNone.NO_VALUE;
    }

    @Specialization(guards = "isBuiltinDict(locals)")
    static Object readFromLocalsDict(PDict locals, TruffleString name,
                    @Cached HashingStorageNodes.HashingStorageGetItem getItem) {
        Object result = getItem.execute(locals.getDictStorage(), name);
        if (result == null) {
            return PNone.NO_VALUE;
        } else {
            return result;
        }
    }

    @Fallback
    static Object readFromLocals(VirtualFrame frame, Object locals, TruffleString name,
                    @Cached PyObjectGetItem getItem,
                    @Cached IsBuiltinClassProfile errorProfile) {
        try {
            return getItem.execute(frame, locals, name);
        } catch (PException e) {
            e.expect(PythonBuiltinClassType.KeyError, errorProfile);
            return PNone.NO_VALUE;
        }
    }

    public static ReadFromLocalsNode create() {
        return ReadFromLocalsNodeGen.create();
    }

    public static ReadFromLocalsNode getUncached() {
        return ReadFromLocalsNodeGen.getUncached();
    }
}
