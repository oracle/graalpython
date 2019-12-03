package com.oracle.graal.python.builtins.objects.str;

import com.oracle.graal.python.builtins.objects.cext.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

public abstract class StringNodes {

    @GenerateUncached
    public abstract static class StringMaterializeNode extends Node {

        public abstract String execute(PString materialize);

        @Specialization(guards = "isNativeCharSequence(x)")
        static String doNative(PString x,
                        @Cached PCallCapiFunction callCStringToStringNode) {
            // cast guaranteed by the guard
            NativeCharSequence nativeCharSequence = (NativeCharSequence) x.getCharSequence();
            String materialized = (String) callCStringToStringNode.call(NativeCAPISymbols.FUN_PY_TRUFFLE_CSTR_TO_STRING, nativeCharSequence.getPtr());
            x.setCharSequence(materialized);
            return materialized;
        }

        @Specialization(guards = "isLazyCharSequence(x)")
        static String doLazyString(PString x) {
            // cast guaranteed by the guard
            String materialized = ((LazyString) x.getCharSequence()).materialize();
            x.setCharSequence(materialized);
            return materialized;
        }

        @Specialization(guards = "isMaterialized(x)")
        static String doMaterialized(PString x) {
            // cast guaranteed by the guard
            return (String) x.getCharSequence();
        }

        static boolean isNativeCharSequence(PString x) {
            return x.getCharSequence() instanceof NativeCharSequence;
        }

        static boolean isLazyCharSequence(PString x) {
            return x.getCharSequence() instanceof LazyString;
        }

        static boolean isMaterialized(PString x) {
            return x.getCharSequence() instanceof String;
        }

    }

    public abstract static class StringLenNode extends Node {

        public abstract int execute(Object str);

        @Specialization
        static int doString(String str) {
            return str.length();
        }

        @Specialization
        static int doPString(PString x,
                        @Cached StringMaterializeNode materializeNode) {
            return materializeNode.execute(x).length();
        }
    }

}
