package com.oracle.graal.python.builtins.objects.common;

import com.oracle.graal.python.builtins.objects.common.SequenceNodesFactory.LenNodeGen;
import com.oracle.graal.python.builtins.objects.range.PRange;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.nodes.PBaseNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.ValueProfile;

public abstract class SequenceNodes {

    @ImportStatic(PGuards.class)
    public abstract static class LenNode extends PBaseNode {

        public abstract int execute(PSequence seq);

        @Specialization
        int doPString(PString str,
                        @Cached("createClassProfile()") ValueProfile charSequenceProfile) {
            return charSequenceProfile.profile(str.getCharSequence()).length();
        }

        @Specialization
        int doPRange(PRange range) {
            return range.len();
        }

        @Specialization(guards = {"!isPString(seq)", "!isPRange(seq)"})
        int doWithStorage(PSequence seq,
                        @Cached("create()") SequenceStorageNodes.LenNode lenNode) {
            return lenNode.execute(seq.getSequenceStorage());
        }

        public static LenNode create() {
            return LenNodeGen.create();
        }
    }

}
