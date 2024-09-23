/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.builtins.objects.cext.capi;

import static com.oracle.graal.python.builtins.objects.cext.capi.CApiContext.GC_LOGGER;

import java.util.logging.Level;

import com.oracle.graal.python.builtins.objects.cext.capi.CApiGCSupportFactory.GCListRemoveNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiGCSupportFactory.PyObjectGCDelNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTiming;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.HandlePointerConverter;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.CoerceNativePointerToLongNode;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.FreeNode;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructs;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

public abstract class CApiGCSupport {
    public static final CApiTiming VISIT_TIMING = CApiTiming.create(true, PExternalFunctionWrapper.VISITPROC);

    public static final long NEXT_MASK_UNREACHABLE = 1;

    /* Bit 0 is set when tp_finalize is called */
    private static final long _PyGC_PREV_MASK_FINALIZED = 1;

    /* The (N-2) most significant bits contain the real address. */
    private static final long _PyGC_PREV_SHIFT = 2;
    private static final long _PyGC_PREV_MASK = -1L << _PyGC_PREV_SHIFT;

    private CApiGCSupport() {
    }

    /**
     * See macro {@code pycore_gc.h: _PyGCHead_PREV}
     */
    static long maskPrevValue(long value) {
        return value & _PyGC_PREV_MASK;
    }

    /**
     * See macro {@code pycore_gc.h: _PyGCHead_SET_PREV}
     */
    static long computePrevValue(long curPrevValue, long newValue) {
        assert (newValue & ~_PyGC_PREV_MASK) == 0;
        return (curPrevValue & ~_PyGC_PREV_MASK) | newValue;
    }

    /**
     * Implements the logic of {@code pycore_object.h: _PyObject_GC_TRACK} without downcalls but
     * will additionally also test {@code if (!_PyObject_GC_IS_TRACKED(op))}.
     */
    @GenerateInline
    @GenerateUncached
    @GenerateCached(false)
    public abstract static class PyObjectGCTrackNode extends Node {

        public abstract void execute(Node inliningTarget, long gc);

        @Specialization
        static void doGeneric(Node inliningTarget, long gc,
                        @Cached CoerceNativePointerToLongNode coerceToLongNode,
                        @Cached(inline = false) CStructAccess.ReadPointerNode readPointerNode,
                        @Cached(inline = false) CStructAccess.ReadI64Node readI64Node,
                        @Cached(inline = false) CStructAccess.WriteLongNode writeLongNode) {

            long gcUntagged = HandlePointerConverter.pointerToStub(gc);
            // #define _PyObject_GC_IS_TRACKED(o) (_PyGCHead_UNTAG(_Py_AS_GC(o))->_gc_next != 0)
            long gcNext = readI64Node.read(gcUntagged, CFields.PyGC_Head___gc_next);
            // if (!_PyObject_GC_IS_TRACKED(op))
            if (gcNext == 0) {
                if (GC_LOGGER.isLoggable(Level.FINER)) {
                    GC_LOGGER.finer(PythonUtils.formatJString("tracking GC object 0x%x (op=0x%x)", gc, gc + CStructs.PyGC_Head.size()));
                }
                // PyGC_Head *generation0 = tstate->gc->generation0;
                Object gcState = PythonContext.get(inliningTarget).getCApiContext().getGCState();
                assert gcState != null;
                long gen0 = coerceToLongNode.execute(inliningTarget, readPointerNode.read(gcState, CFields.GCState__generation0));
                assert gen0 != 0;
                assert !HandlePointerConverter.pointsToPyHandleSpace(gen0);

                // PyGC_Head *last = (PyGC_Head*)(generation0->_gc_prev);
                long last = readI64Node.read(gen0, CFields.PyGC_Head___gc_prev);

                // _PyGCHead_SET_NEXT(last, gc);
                writeLongNode.write(HandlePointerConverter.pointerToStub(last), CFields.PyGC_Head___gc_next, gc);

                // _PyGCHead_SET_PREV(gc, last);
                long curGcPrev = readI64Node.read(gcUntagged, CFields.PyGC_Head___gc_prev);
                writeLongNode.write(gcUntagged, CFields.PyGC_Head___gc_prev, computePrevValue(curGcPrev, last));

                // _PyGCHead_SET_NEXT(gc, generation0);
                writeLongNode.write(gcUntagged, CFields.PyGC_Head___gc_next, gen0);

                // generation0->_gc_prev = (uintptr_t)gc;
                writeLongNode.write(gen0, CFields.PyGC_Head___gc_prev, gc);
            } else if (GC_LOGGER.isLoggable(Level.FINER)) {
                GC_LOGGER.finer(PythonUtils.formatJString("GC object 0x%x (op=0x%x) already tracked", gc, gc + CStructs.PyGC_Head.size()));
            }
        }
    }

    /**
     * Implements the logic of {@code gcmodule.c: gc_list_remove} without downcalls.
     * <p>
     * This operation is very similar to {@code pycore_object.h: _PyObject_GC_UNTRACK} with the
     * little difference that {@code _PyObject_GC_UNTRACK} will also clear {@code gc->_gc_prev}
     * (still rescue the {@code _PyGC_PREV_MASK_FINALIZED} flag) while this operation doesn't touch
     * that field at all.
     * </p>
     */
    @GenerateInline
    @GenerateUncached
    @GenerateCached(false)
    public abstract static class GCListRemoveNode extends Node {

        public static long executeUncached(long opUntagged) {
            return GCListRemoveNodeGen.getUncached().execute(null, opUntagged);
        }

        public abstract long execute(Node inliningTarget, long opUntagged);

        @Specialization
        static long doGeneric(long opUntagged,
                        @Cached(inline = false) CStructAccess.ReadI64Node readI64Node,
                        @Cached(inline = false) CStructAccess.WriteLongNode writeLongNode) {
            // issue a log message before doing the first memory access
            if (GC_LOGGER.isLoggable(Level.FINER)) {
                GC_LOGGER.finer(PythonUtils.formatJString("attempting to remove 0x%x from GC generation", opUntagged));
            }

            /*
             * We expect a real (untagged) pointer here because this operation is usually used in
             * conjunction with other operations that already untag pointers before.
             */
            assert !HandlePointerConverter.pointsToPyHandleSpace(opUntagged) : "expected real (untagged) pointer";

            long gcUntagged = opUntagged - CStructs.PyGC_Head.size();

            /*
             * The following implements the section of 'PyObject_GC_Del' that removes the node from
             * the GC generation if it was tracked by the GC.
             */

            // #define _PyObject_GC_IS_TRACKED(o) (_PyGCHead_UNTAG(_Py_AS_GC(o))->_gc_next != 0)
            long gcNext = readI64Node.read(gcUntagged, CFields.PyGC_Head___gc_next);
            // if (_PyObject_GC_IS_TRACKED(op))
            if (gcNext != 0) {
                // gc_list_remove
                if (GC_LOGGER.isLoggable(Level.FINE)) {
                    GC_LOGGER.fine(PythonUtils.formatJString("removing 0x%x from GC generation", opUntagged));
                }

                // PyGC_Head *prev = GC_PREV(gc)
                long prev = maskPrevValue(readI64Node.read(gcUntagged, CFields.PyGC_Head___gc_prev));

                // PyGC_Head *next = GC_NEXT(gc)
                long next = readI64Node.read(gcUntagged, CFields.PyGC_Head___gc_next);

                // _PyGCHead_SET_NEXT(prev, next)
                writeLongNode.write(HandlePointerConverter.pointerToStub(prev), CFields.PyGC_Head___gc_next, next);

                // _PyGCHead_SET_PREV(next, prev)
                long curNextPrev = readI64Node.read(HandlePointerConverter.pointerToStub(next), CFields.PyGC_Head___gc_prev);
                writeLongNode.write(HandlePointerConverter.pointerToStub(next), CFields.PyGC_Head___gc_prev, computePrevValue(curNextPrev, prev));

                // UNTAG(gc)->_gc_next = 0
                writeLongNode.write(gcUntagged, CFields.PyGC_Head___gc_next, 0);
            } else {
                /*
                 * This is a valid case because objects can manually be untracked or removed from GC
                 * lists and if then 'GraalPyObject_GC_Del' is called on the native object stub, it
                 * is checked if that still needs to be done.
                 */
                if (GC_LOGGER.isLoggable(Level.FINER)) {
                    GC_LOGGER.finer(PythonUtils.formatJString("removing 0x%x from GC generation skipped; not tracked", opUntagged));
                }
            }
            return gcUntagged;
        }
    }

    /**
     * Implements the logic of {@code gcmodule.c: PyObject_GC_Del} without downcalls but to be used
     * for native object stubs.
     */
    @GenerateInline
    @GenerateUncached
    @GenerateCached(false)
    public abstract static class PyObjectGCDelNode extends Node {

        public static void executeUncached(long op) {
            PyObjectGCDelNodeGen.getUncached().execute(null, op);
        }

        public abstract void execute(Node inliningTarget, long op);

        @Specialization
        static void doGeneric(Node inliningTarget, long op,
                        @Cached GCListRemoveNode gcListRemoveNode,
                        @Cached(inline = false) CStructAccess.GetElementPtrNode getElementPtrNode,
                        @Cached(inline = false) CStructAccess.ReadI32Node readI32Node,
                        @Cached(inline = false) CStructAccess.WriteIntNode writeIntNode) {
            if (GC_LOGGER.isLoggable(Level.FINE)) {
                GC_LOGGER.fine(PythonUtils.formatJString("releasing native object stub 0x%x", op));
            }

            /*
             * We expect a tagged pointer here because otherwise, the native 'PyObject_GC_Del'
             * function should be used.
             */
            assert HandlePointerConverter.pointsToPyHandleSpace(op) : "expected tagged pointer";

            long opUntagged = HandlePointerConverter.pointerToStub(op);
            long gcUntagged = gcListRemoveNode.execute(inliningTarget, opUntagged);

            // GCState *gcstate = get_gc_state();
            Object gcState = PythonContext.get(inliningTarget).getCApiContext().getGCState();
            assert gcState != null;
            // compute start address of embedded array; essentially '&gcstate->generations[0]'
            Object generations = getElementPtrNode.getElementPtr(gcState, CFields.GCState__generations);
            // if (gcstate->generations[0].count > 0) {
            int count = readI32Node.read(generations, CFields.GCGeneration__count);
            if (count > 0) {
                // gcstate->generations[0].count--;
                writeIntNode.write(generations, CFields.GCGeneration__count, count - 1);
            }

            // PyObject_Free(((char *)op)-presize)
            FreeNode.executeUncached(gcUntagged);
        }
    }
}
