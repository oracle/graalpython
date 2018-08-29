package com.oracle.graal.python.builtins.objects.common;

import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodesFactory.LenNodeGen;
import com.oracle.graal.python.nodes.PBaseNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;

public abstract class HashingCollectionNodes {

    @ImportStatic(PGuards.class)
    public abstract static class LenNode extends PBaseNode {

        public abstract int execute(PHashingCollection c);

        @Specialization(limit = "4", guards = {"c.getClass() == cachedClass"})
        int doWithStorage(PHashingCollection c,
                        @Cached("c.getClass()") Class<? extends PHashingCollection> cachedClass,
                        @Cached("create()") HashingStorageNodes.LenNode lenNode) {
            return lenNode.execute(cachedClass.cast(c).getDictStorage());
        }

        public static LenNode create() {
            return LenNodeGen.create();
        }
    }
}
