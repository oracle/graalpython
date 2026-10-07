/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.runtime.sequence.storage;

import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;

public abstract class SequenceStorageFactory {

    private SequenceStorageFactory() {
        // no instances
    }

    public static SequenceStorage createStorage(Node inliningTarget, Object[] values) {
        assert values != null;
        assert values.getClass() == Object[].class : "cannot use non-Object array for modifiable list";

        /*
         * Try to use unboxed SequenceStorage.
         */
        if (values.length == 0) {
            return EmptySequenceStorage.INSTANCE;
        }

        if (canSpecializeToInt(inliningTarget, values)) {
            return new IntSequenceStorage(specializeToInt(inliningTarget, values));
        } else if (canSpecializeToDouble(inliningTarget, values)) {
            return new DoubleSequenceStorage(specializeToDouble(inliningTarget, values));
        } else if (canSpecializeToLong(inliningTarget, values)) {
            return new LongSequenceStorage(specializeToLong(inliningTarget, values));
        } else if (canSpecializeToBool(inliningTarget, values)) {
            return new BoolSequenceStorage(specializeToBool(inliningTarget, values));
        } else {
            return new ObjectSequenceStorage(values);
        }
    }

    private static boolean canSpecializeToInt(Node inliningTarget, Object[] values) {
        for (int i = 0; i < values.length; i++) {
            if (!(values[i] instanceof Integer)) {
                LoopNode.reportLoopCount(inliningTarget, i);
                return false;
            }
        }
        LoopNode.reportLoopCount(inliningTarget, values.length);
        return true;
    }

    private static int[] specializeToInt(Node inliningTarget, Object[] values) {
        final int[] intVals = new int[values.length];

        for (int i = 0; i < values.length; i++) {
            intVals[i] = (int) values[i];
        }

        LoopNode.reportLoopCount(inliningTarget, values.length);
        return intVals;
    }

    private static boolean canSpecializeToByte(Object[] values) {
        for (Object item : values) {
            if (!(item instanceof Byte)) {
                return false;
            }
        }

        return true;
    }

    private static byte[] specializeToByte(Object[] values) {
        final byte[] byteVals = new byte[values.length];

        for (int i = 0; i < values.length; i++) {
            byteVals[i] = (byte) values[i];
        }

        return byteVals;
    }

    private static boolean canSpecializeToLong(Node inliningTarget, Object[] values) {
        for (int i = 0; i < values.length; i++) {
            Object item = values[i];
            if (!(item instanceof Long || item instanceof Integer)) {
                LoopNode.reportLoopCount(inliningTarget, i);
                return false;
            }
        }
        LoopNode.reportLoopCount(inliningTarget, values.length);
        return true;
    }

    private static long[] specializeToLong(Node inliningTarget, Object[] values) {
        final long[] intVals = new long[values.length];
        for (int i = 0; i < values.length; i++) {
            long value = (values[i] instanceof Integer) ? (long) ((int) values[i]) : (long) values[i];
            intVals[i] = value;
        }

        LoopNode.reportLoopCount(inliningTarget, values.length);
        return intVals;
    }

    private static boolean canSpecializeToDouble(Node inliningTarget, Object[] values) {
        for (int i = 0; i < values.length; i++) {
            if (!(values[i] instanceof Double)) {
                LoopNode.reportLoopCount(inliningTarget, i);
                return false;
            }
        }
        LoopNode.reportLoopCount(inliningTarget, values.length);
        return true;
    }

    private static double[] specializeToDouble(Node inliningTarget, Object[] values) {
        final double[] doubles = new double[values.length];

        for (int i = 0; i < values.length; i++) {
            doubles[i] = (double) values[i];
        }

        LoopNode.reportLoopCount(inliningTarget, values.length);
        return doubles;
    }

    private static boolean canSpecializeToBool(Node inliningTarget, Object[] values) {
        for (int i = 0; i < values.length; i++) {
            if (!(values[i] instanceof Boolean)) {
                LoopNode.reportLoopCount(inliningTarget, i);
                return false;
            }
        }
        LoopNode.reportLoopCount(inliningTarget, values.length);
        return true;
    }

    private static boolean[] specializeToBool(Node inliningTarget, Object[] values) {
        final boolean[] bools = new boolean[values.length];

        for (int i = 0; i < values.length; i++) {
            bools[i] = (boolean) values[i];
        }

        LoopNode.reportLoopCount(inliningTarget, values.length);
        return bools;
    }
}
