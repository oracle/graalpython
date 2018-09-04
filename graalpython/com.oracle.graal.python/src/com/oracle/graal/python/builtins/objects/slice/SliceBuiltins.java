/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
 * Copyright (c) 2014, Regents of the University of California
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
package com.oracle.graal.python.builtins.objects.slice;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.slice.SliceBuiltinsFactory.StartNodeFactory;
import com.oracle.graal.python.builtins.objects.slice.SliceBuiltinsFactory.StepNodeFactory;
import com.oracle.graal.python.builtins.objects.slice.SliceBuiltinsFactory.StopNodeFactory;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PSlice)
public class SliceBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SliceBuiltinsFactory.getFactories();
    }

    @Builtin(name = __REPR__, fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReprNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        public String repr(PSlice self) {
            return self.toString();
        }
    }

    @Builtin(name = SpecialMethodNames.__EQ__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class EqNode extends PythonBuiltinNode {
        @Specialization
        boolean doPRange(PSlice left, PSlice right) {
            return left.equals(right);
        }
    }

    @Builtin(name = "start", fixedNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    @ImportStatic(PSlice.class)
    abstract static class StartNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = "self.getStart() != MISSING_INDEX")
        protected int get(PSlice self) {
            return self.getStart();
        }

        @Specialization(guards = "self.getStart() == MISSING_INDEX")
        protected Object getNone(@SuppressWarnings("unused") PSlice self) {
            return PNone.NONE;
        }

        public static StartNode create() {
            return StartNodeFactory.create();
        }
    }

    @Builtin(name = "stop", fixedNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    @ImportStatic(PSlice.class)
    abstract static class StopNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = "self.getStop() != MISSING_INDEX")
        protected int get(PSlice self) {
            return self.getStop();
        }

        @Specialization(guards = "self.getStop() == MISSING_INDEX")
        protected Object getNone(@SuppressWarnings("unused") PSlice self) {
            return PNone.NONE;
        }

        public static StopNode create() {
            return StopNodeFactory.create();
        }
    }

    @Builtin(name = "step", fixedNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    @ImportStatic(PSlice.class)
    abstract static class StepNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = "self.getStep() != MISSING_INDEX")
        protected int get(PSlice self) {
            return self.getStep();
        }

        @Specialization(guards = "self.getStep() == MISSING_INDEX")
        protected Object getNone(@SuppressWarnings("unused") PSlice self) {
            return PNone.NONE;
        }

        public static StepNode create() {
            return StepNodeFactory.create();
        }
    }

    @Builtin(name = "indices", fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @ImportStatic(PSlice.class)
    abstract static class IndicesNode extends PythonBinaryBuiltinNode {

        private static Object[] adjustIndices(int length, int start, int stop, int step) {
            int _start = start;
            int _stop = stop;

            if (start < 0) {
                _start += length;

                if (_start < 0) {
                    _start = (step < 0) ? -1 : 0;
                }
            } else if (start >= length) {
                _start = (step < 0) ? length - 1 : length;
            }

            if (stop < 0) {
                _stop += length;

                if (_stop < 0) {
                    _stop = (step < 0) ? -1 : 0;
                }
            } else if (_stop >= length) {
                _stop = (step < 0) ? length - 1 : length;
            }

            // if (step < 0) {
            // if (_stop < _start) {
            // return (_start - _stop - 1) / (-step) + 1;
            // }
            // }
            // else {
            // if (_start < _stop) {
            // return (_stop - _start - 1) / step + 1;
            // }
            // }

            return new Object[]{_start, _stop, step};
        }

        @Specialization()
        protected PTuple get(PSlice self, int length,
                        @Cached("create()") StartNode startNode,
                        @Cached("create()") StopNode stopNode,
                        @Cached("create()") StepNode stepNode) {
            Object start = startNode.execute(self);
            Object stop = stopNode.execute(self);
            Object step = stepNode.execute(self);

            int _start = -1;
            int _stop = -1;
            int _step = -1;

            if (step == PNone.NONE) {
                _step = 1;
            } else if (step instanceof Integer) {
                _step = (int) step;
            }

            if (start == PNone.NONE) {
                _start = _step < 0 ? length - 1 : 0;
            } else if (start instanceof Integer) {
                _start = (int) start;
                if (_start < 0) {
                    _start += length;
                }
            }

            if (stop == PNone.NONE) {
                _stop = _step < 0 ? -1 : length;
            } else if (stop instanceof Integer) {
                _stop = (int) stop;
                if (_stop < 0) {
                    _stop += length;
                }
            }

            return factory().createTuple(adjustIndices(length, _start, _stop, _step));
        }
    }
}
