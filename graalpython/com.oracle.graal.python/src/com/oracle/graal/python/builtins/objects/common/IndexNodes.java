package com.oracle.graal.python.builtins.objects.common;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.common.IndexNodesFactory.BoundsCheckNodeGen;
import com.oracle.graal.python.builtins.objects.common.IndexNodesFactory.NormalizeIndexWithBoundsCheckNodeGen;
import com.oracle.graal.python.builtins.objects.common.IndexNodesFactory.NormalizeIndexWithoutBoundsCheckNodeGen;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;

public abstract class IndexNodes {

    public static final class NormalizeIndexNode extends Node {
        public static final String INDEX_OUT_OF_BOUNDS = "index out of range";
        public static final String RANGE_OUT_OF_BOUNDS = "range index out of range";
        public static final String TUPLE_OUT_OF_BOUNDS = "tuple index out of range";
        public static final String TUPLE_ASSIGN_OUT_OF_BOUNDS = "tuple assignment index out of range";
        public static final String LIST_OUT_OF_BOUNDS = "list index out of range";
        public static final String LIST_ASSIGN_OUT_OF_BOUNDS = "list assignment index out of range";
        public static final String ARRAY_OUT_OF_BOUNDS = "array index out of range";
        public static final String ARRAY_ASSIGN_OUT_OF_BOUNDS = "array assignment index out of range";
        public static final String BYTEARRAY_OUT_OF_BOUNDS = "bytearray index out of range";

        @Child private NormalizeIndexCustomMessageNode subNode;

        private final String errorMessage;

        public NormalizeIndexNode(String errorMessage, boolean boundsCheck) {
            this.errorMessage = errorMessage;
            if (boundsCheck) {
                subNode = NormalizeIndexWithBoundsCheckNodeGen.create();
            } else {
                subNode = NormalizeIndexWithoutBoundsCheckNodeGen.create();
            }
        }

        public int execute(Object index, int length) {
            return subNode.execute(index, length, errorMessage);
        }

        public int execute(int index, int length) {
            return subNode.execute(index, length, errorMessage);
        }

        public int execute(long index, int length) {
            return subNode.execute(index, length, errorMessage);
        }

        public int execute(boolean index, int length) {
            return subNode.execute(index, length, errorMessage);
        }

        protected final String getErrorMessage() {
            return errorMessage;
        }

        public static NormalizeIndexNode create() {
            return new NormalizeIndexNode(INDEX_OUT_OF_BOUNDS, true);
        }

        public static NormalizeIndexNode create(String errorMessage) {
            return new NormalizeIndexNode(errorMessage, true);
        }

        public static NormalizeIndexNode create(boolean boundsCheck) {
            return new NormalizeIndexNode(INDEX_OUT_OF_BOUNDS, boundsCheck);
        }

        public static NormalizeIndexNode create(String errorMessage, boolean boundsCheck) {
            return new NormalizeIndexNode(errorMessage, boundsCheck);
        }

        public static NormalizeIndexNode forList() {
            return create(LIST_OUT_OF_BOUNDS);
        }

        public static NormalizeIndexNode forListAssign() {
            return create(LIST_ASSIGN_OUT_OF_BOUNDS);
        }

        public static NormalizeIndexNode forTuple() {
            return create(TUPLE_OUT_OF_BOUNDS);
        }

        public static NormalizeIndexNode forTupleAssign() {
            return create(TUPLE_ASSIGN_OUT_OF_BOUNDS);
        }

        public static NormalizeIndexNode forArray() {
            return create(ARRAY_OUT_OF_BOUNDS);
        }

        public static NormalizeIndexNode forArrayAssign() {
            return create(ARRAY_ASSIGN_OUT_OF_BOUNDS);
        }

        public static NormalizeIndexNode forRange() {
            return create(RANGE_OUT_OF_BOUNDS);
        }

        public static NormalizeIndexNode forBytearray() {
            return create(BYTEARRAY_OUT_OF_BOUNDS);
        }
    }

    public abstract static class NormalizeIndexCustomMessageNode extends Node {
        public abstract int execute(Object index, int length, String errorMessage);

        public abstract int execute(boolean index, int length, String errorMessage);

        public abstract int execute(long index, int length, String errorMessage);

        public abstract int execute(int index, int length, String errorMessage);

        public static NormalizeIndexCustomMessageNode create() {
            return NormalizeIndexWithBoundsCheckNodeGen.create();
        }

        public static NormalizeIndexCustomMessageNode getUncached() {
            return NormalizeIndexWithBoundsCheckNodeGen.getUncached();
        }

        public static NormalizeIndexCustomMessageNode createWithoutBoundsCheck() {
            return NormalizeIndexWithoutBoundsCheckNodeGen.create();
        }

        public static NormalizeIndexCustomMessageNode getUncachedWithoutBoundsCheck() {
            return NormalizeIndexWithoutBoundsCheckNodeGen.getUncached();
        }
    }

    @GenerateUncached
    abstract static class NormalizeIndexWithBoundsCheckNode extends NormalizeIndexCustomMessageNode {

        @Specialization
        int doInt(int index, int length, String errorMessage,
                        @Shared("negativeIndexProfile") @Cached("createBinaryProfile()") ConditionProfile negativeIndexProfile,
                        @Shared("boundsCheckNode") @Cached BoundsCheckNode boundsCheckNode) {
            int normalizedIndex = index;
            if (negativeIndexProfile.profile(normalizedIndex < 0)) {
                normalizedIndex += length;
            }
            boundsCheckNode.execute(errorMessage, normalizedIndex, length);
            return normalizedIndex;
        }

        @Specialization
        int doBool(boolean bIndex, int length, String errorMessage,
                        @Shared("boundsCheckNode") @Cached BoundsCheckNode boundsCheckNode) {
            int index = PInt.intValue(bIndex);
            boundsCheckNode.execute(errorMessage, index, length);
            return index;
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        int doLong(long lIndex, int length, String errorMessage,
                        @Shared("negativeIndexProfile") @Cached("createBinaryProfile()") ConditionProfile negativeIndexProfile,
                        @Shared("boundsCheckNode") @Cached BoundsCheckNode boundsCheckNode) {
            int index = PInt.intValueExact(lIndex);
            return doInt(index, length, errorMessage, negativeIndexProfile, boundsCheckNode);
        }

        @Specialization(replaces = "doLong")
        int doLongOvf(long index, int length, String errorMessage,
                        @Shared("negativeIndexProfile") @Cached("createBinaryProfile()") ConditionProfile negativeIndexProfile,
                        @Shared("boundsCheckNode") @Cached BoundsCheckNode boundsCheckNode,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            try {
                return doLong(index, length, errorMessage, negativeIndexProfile, boundsCheckNode);
            } catch (ArithmeticException e) {
                throw raiseNode.raiseIndexError();
            }
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        int doPInt(PInt index, int length, String errorMessage,
                        @Shared("negativeIndexProfile") @Cached("createBinaryProfile()") ConditionProfile negativeIndexProfile,
                        @Shared("boundsCheckNode") @Cached BoundsCheckNode boundsCheckNode) {
            int idx = index.intValueExact();
            return doInt(idx, length, errorMessage, negativeIndexProfile, boundsCheckNode);
        }

        @Specialization(replaces = "doPInt")
        int doPIntOvf(PInt index, int length, String errorMessage,
                        @Shared("negativeIndexProfile") @Cached("createBinaryProfile()") ConditionProfile negativeIndexProfile,
                        @Shared("boundsCheckNode") @Cached BoundsCheckNode boundsCheckNode,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            try {
                return doPInt(index, length, errorMessage, negativeIndexProfile, boundsCheckNode);
            } catch (ArithmeticException e) {
                throw raiseNode.raiseIndexError();
            }
        }

    }

    @GenerateUncached
    abstract static class NormalizeIndexWithoutBoundsCheckNode extends NormalizeIndexCustomMessageNode {

        @Specialization
        int doInt(int index, int length, @SuppressWarnings("unused") String errorMessage,
                        @Shared("negativeIndexProfile") @Cached("createBinaryProfile()") ConditionProfile negativeIndexProfile) {
            int idx = index;
            if (negativeIndexProfile.profile(idx < 0)) {
                idx += length;
            }
            return idx;
        }

        @Specialization
        int doBool(boolean index, @SuppressWarnings("unused") int length, @SuppressWarnings("unused") String errorMessage) {
            int idx = PInt.intValue(index);
            return idx;
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        int doLong(long index, int length, String errorMessage,
                        @Shared("negativeIndexProfile") @Cached("createBinaryProfile()") ConditionProfile negativeIndexProfile) {
            int idx = PInt.intValueExact(index);
            return doInt(idx, length, errorMessage, negativeIndexProfile);
        }

        @Specialization(replaces = "doLong")
        int doLongOvf(long index, int length, String errorMessage,
                        @Shared("negativeIndexProfile") @Cached("createBinaryProfile()") ConditionProfile negativeIndexProfile,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            try {
                return doLong(index, length, errorMessage, negativeIndexProfile);
            } catch (ArithmeticException e) {
                throw raiseNode.raiseIndexError();
            }
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        int doPInt(PInt index, int length, String errorMessage,
                        @Shared("negativeIndexProfile") @Cached("createBinaryProfile()") ConditionProfile negativeIndexProfile) {
            int idx = index.intValueExact();
            return doInt(idx, length, errorMessage, negativeIndexProfile);
        }

        @Specialization(replaces = "doPInt")
        int doPIntOvf(PInt index, int length, String errorMessage,
                        @Shared("negativeIndexProfile") @Cached("createBinaryProfile()") ConditionProfile negativeIndexProfile,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            try {
                return doPInt(index, length, errorMessage, negativeIndexProfile);
            } catch (ArithmeticException e) {
                throw raiseNode.raiseIndexError();
            }
        }
    }

    @GenerateUncached
    public abstract static class BoundsCheckNode extends Node {

        public abstract void execute(String errorMessage, int idx, int length);

        @Specialization
        void doBoundsCheck(String errorMessage, int idx, int length,
                        @Cached("createBinaryProfile()") ConditionProfile outOfBoundsProfile,
                        @Cached PRaiseNode raiseNode) {
            if (outOfBoundsProfile.profile(idx < 0 || idx >= length)) {
                throw raiseNode.raise(PythonBuiltinClassType.IndexError, errorMessage);
            }
        }

        public static BoundsCheckNode create() {
            return BoundsCheckNodeGen.create();
        }

        public static BoundsCheckNode getUncached() {
            return BoundsCheckNodeGen.getUncached();
        }
    }

}
