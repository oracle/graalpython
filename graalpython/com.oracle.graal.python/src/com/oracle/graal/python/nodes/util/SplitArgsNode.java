package com.oracle.graal.python.nodes.util;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;

@GenerateUncached
public abstract class SplitArgsNode extends Node {

    public abstract Object[] execute(Object[] varargsWitSelf);

    @Specialization(guards = "varargsWithSelf.length == 1")
    Object[] doEmpty(@SuppressWarnings("unused") Object[] varargsWithSelf) {
        return new Object[0];
    }

    @Specialization(guards = {"varargsWithSelf.length == cachedLen", "varargsWithSelf.length < 32"})
    @ExplodeLoop
    Object[] doCached(Object[] varargsWithSelf,
                    @Cached("varargsWithSelf.length") int cachedLen) {
        Object[] splitArgs = new Object[cachedLen - 1];
        for (int i = 0; i < cachedLen - 1; i++) {
            splitArgs[i] = varargsWithSelf[i + 1];
        }
        return splitArgs;
    }

    @Specialization(replaces = "doCached")
    Object[] doGeneric(@SuppressWarnings("unused") Object[] varargsWithSelf) {
        Object[] splitArgs = new Object[varargsWithSelf.length - 1];
        System.arraycopy(varargsWithSelf, 1, splitArgs, 0, varargsWithSelf.length - 1);
        return splitArgs;
    }

    public static SplitArgsNode create() {
        return SplitArgsNodeGen.create();
    }

    public static SplitArgsNode getUncached() {
        return SplitArgsNodeGen.getUncached();
    }
}