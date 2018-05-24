/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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
package com.oracle.graal.python.builtins.modules;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

final class BisectModuleBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinNode>> getNodeFactories() {
        return BisectModuleBuiltinsFactory.getFactories();
    }

    // bisect.bisect(a, x, lo=0, hi=len(a))
    @Builtin(name = "bisect", fixedNumOfArguments = 2, keywordArguments = {"lo", "hi"})
    @GenerateNodeFactory
    public abstract static class PythonBisectNode extends PythonBuiltinNode {

        @SuppressWarnings("unused")
        @Specialization
        public int bisect(Object arg1, Object arg2, PNone lo, PNone hi) {
            return bisect(arg1, arg2);
        }

        private int bisect(Object arg0, Object arg1) {
            if (arg0 instanceof PList) {
                PList plist = (PList) arg0;

                if (plist.len() == 0) {
                    return 0;
                }

                return getIndexRight(plist, arg1);
            } else {
                throw new RuntimeException("invalid arguments number for bisect() ");
            }
        }

        private int getIndexRight(PSequence seq, Object key) {
            if (key instanceof String) {
                return binarySearchRightStr(seq, 0, seq.len() - 1, (String) key);
            } else {
                return binarySearchRightDouble(seq, 0, seq.len() - 1, (double) key);
            }
        }

        @TruffleBoundary
        private int binarySearchRightDouble(PSequence seq, int start, int stop, double key) {
            if (start <= stop) {
                int middle = (stop - start) / 2 + start;
                if (((double) seq.getItem(middle)) > key) {
                    if (middle - 1 >= 0 && ((double) seq.getItem(middle - 1)) < key) {
                        return middle;
                    } else if (middle - 1 <= 0) {
                        return 0;
                    } else {
                        return binarySearchRightDouble(seq, start, middle - 1, key);
                    }
                } else if (((double) seq.getItem(middle)) < key) {
                    if (middle + 1 < seq.len() && ((double) seq.getItem(middle + 1)) > key) {
                        return middle + 1;
                    } else if (middle + 1 >= seq.len() - 1) {
                        return seq.len();
                    } else {
                        return binarySearchRightDouble(seq, middle + 1, stop, key);
                    }
                } else {
                    int i = middle + 1;
                    while (((double) seq.getItem(i)) == key && i < seq.len()) {
                        i++;
                    }
                    return i;
                }
            }
            return -1; // should not happen
        }

        @TruffleBoundary
        private int binarySearchRightStr(PSequence seq, int start, int stop, String key) {
            if (start <= stop) {
                int middle = (stop - start) / 2 + start;
                if (((String) seq.getItem(middle)).compareTo(key) > 0) {
                    if (middle - 1 >= 0 && ((String) seq.getItem(middle - 1)).compareTo(key) < 0) {
                        return middle;
                    } else if (middle - 1 <= 0) {
                        return 0;
                    } else {
                        return binarySearchRightStr(seq, start, middle - 1, key);
                    }
                } else if (((String) seq.getItem(middle)).compareTo(key) < 0) {
                    if (middle + 1 < seq.len() && ((String) seq.getItem(middle + 1)).compareTo(key) > 0) {
                        return middle + 1;
                    } else if (middle + 1 >= seq.len() - 1) {
                        return seq.len();
                    } else {
                        return binarySearchRightStr(seq, middle + 1, stop, key);
                    }
                } else {
                    int i = middle + 1;
                    while (((String) seq.getItem(i)).compareTo(key) == 0 && i < seq.len()) {
                        i++;
                    }
                    return i;
                }
            }
            return -1; // should not happen
        }
    }

}
