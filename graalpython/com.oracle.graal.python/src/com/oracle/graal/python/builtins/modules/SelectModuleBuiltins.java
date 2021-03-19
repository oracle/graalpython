/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.nodes.ErrorMessages.INVALID_VALUE_NAN;
import static com.oracle.graal.python.nodes.ErrorMessages.TOO_LARGE_TO_CONVERT_TO;
import static com.oracle.graal.python.runtime.PosixConstants.FD_SETSIZE;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithRaise;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.builtins.ListNodes.FastConstructListNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.runtime.EmulatedPosixSupport;
import com.oracle.graal.python.runtime.PosixResources;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PosixSupportLibrary.ChannelNotSelectableException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.SelectResult;
import com.oracle.graal.python.runtime.PosixSupportLibrary.Timeval;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.util.ArrayBuilder;
import com.oracle.graal.python.util.IntArrayBuilder;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;

@CoreFunctions(defineModule = "select")
public class SelectModuleBuiltins extends PythonBuiltins {

    public SelectModuleBuiltins() {
        builtinConstants.put("error", PythonErrorType.OSError);
    }

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SelectModuleBuiltinsFactory.getFactories();
    }

    @Builtin(name = "select", minNumOfPositionalArgs = 3, parameterNames = {"rlist", "wlist", "xlist", "timeout"})
    @GenerateNodeFactory
    abstract static class SelectNode extends PythonBuiltinNode {

        @Specialization(limit = "3")
        PTuple doWithoutTimeout(VirtualFrame frame, Object rlist, Object wlist, Object xlist, @SuppressWarnings("unused") PNone timeout,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @CachedLibrary("rlist") PythonObjectLibrary rlistLibrary,
                        @CachedLibrary("wlist") PythonObjectLibrary wlistLibrary,
                        @CachedLibrary("xlist") PythonObjectLibrary xlistLibrary,
                        @Cached("createGetItem()") LookupAndCallBinaryNode callGetItemNode,
                        @Cached FastConstructListNode constructListNode,
                        @Cached PyTimeFromObjectNode pyTimeFromObjectNode,
                        @CachedLibrary(limit = "3") PythonObjectLibrary itemLib,
                        @Cached BranchProfile notSelectableBranch,
                        @Cached GilNode gil) {
            return doGeneric(frame, rlist, wlist, xlist, PNone.NONE, posixLib, rlistLibrary, wlistLibrary, xlistLibrary,
                            callGetItemNode, constructListNode, pyTimeFromObjectNode, itemLib, notSelectableBranch, gil);
        }

        @Specialization(replaces = "doWithoutTimeout", limit = "3")
        PTuple doGeneric(VirtualFrame frame, Object rlist, Object wlist, Object xlist, Object timeout,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @CachedLibrary("rlist") PythonObjectLibrary rlistLibrary,
                        @CachedLibrary("wlist") PythonObjectLibrary wlistLibrary,
                        @CachedLibrary("xlist") PythonObjectLibrary xlistLibrary,
                        @Cached("createGetItem()") LookupAndCallBinaryNode callGetItemNode,
                        @Cached FastConstructListNode constructListNode,
                        @Cached PyTimeFromObjectNode pyTimeFromObjectNode,
                        @CachedLibrary(limit = "3") PythonObjectLibrary itemLib,
                        @Cached BranchProfile notSelectableBranch,
                        @Cached GilNode gil) {
            EmulatedPosixSupport emulatedPosixSupport = getContext().getResources();
            ObjAndFDList readFDs = seq2set(frame, rlist, rlistLibrary, itemLib, callGetItemNode, constructListNode, emulatedPosixSupport);
            ObjAndFDList writeFDs = seq2set(frame, wlist, wlistLibrary, itemLib, callGetItemNode, constructListNode, emulatedPosixSupport);
            ObjAndFDList xFDs = seq2set(frame, xlist, xlistLibrary, itemLib, callGetItemNode, constructListNode, emulatedPosixSupport);

            Timeval timeoutval = null;
            if (!PGuards.isPNone(timeout)) {
                timeoutval = timeAsTimeval(pyTimeFromObjectNode.execute(frame, timeout, SEC_TO_NS));
                if (timeoutval.getSeconds() < 0) {
                    throw raise(PythonBuiltinClassType.ValueError, ErrorMessages.MUST_BE_NON_NEGATIVE, "timeout");
                }
            }

            SelectResult result;
            try {
                gil.release(true);
                try {
                    if (readFDs.containsSocket || writeFDs.containsSocket || xFDs.containsSocket) {
                        // TODO remove this once native sockets are supported
                        result = PosixSupportLibrary.getUncached().select(emulatedPosixSupport, readFDs.fds, writeFDs.fds, xFDs.fds, timeoutval);
                    } else {
                        result = posixLib.select(getPosixSupport(), readFDs.fds, writeFDs.fds, xFDs.fds, timeoutval);
                    }
                } finally {
                    gil.acquire();
                }
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            } catch (ChannelNotSelectableException e) {
                // GraalPython hack: if one of the channels is not selectable (can happen only in
                // the emulated mode), we just return everything.
                notSelectableBranch.enter();
                return factory().createTuple(new Object[]{rlist, wlist, xlist});
            }
            return factory().createTuple(new PList[]{
                            toList(result.getReadFds(), readFDs),
                            toList(result.getWriteFds(), writeFDs),
                            toList(result.getErrorFds(), xFDs)});
        }

        /**
         * Also maps the returned FDs back to their original Python level objects.
         */
        private PList toList(boolean[] result, ObjAndFDList fds) {
            Object[] resultObjs = new Object[result.length];
            int resultObjsIdx = 0;
            for (int i = 0; i < fds.fds.length; i++) {
                if (result[i]) {
                    resultObjs[resultObjsIdx++] = fds.objects[i];
                }
            }
            return factory().createList(PythonUtils.arrayCopyOf(resultObjs, resultObjsIdx));
        }

        private ObjAndFDList seq2set(VirtualFrame frame, Object sequence, PythonObjectLibrary sequenceLib, PythonObjectLibrary itemLib, LookupAndCallBinaryNode callGetItemNode,
                        FastConstructListNode constructListNode, PosixResources resources) {
            PArguments.ThreadState threadState = PArguments.getThreadState(frame);
            // We cannot assume any size of those two arrays, because the sequence may change as a
            // side effect of the invocation of fileno. We also need to call lengthWithState
            // repeatedly in the loop condition
            ArrayBuilder<Object> objects = new ArrayBuilder<>();
            IntArrayBuilder fds = new IntArrayBuilder();
            PSequence pSequence = constructListNode.execute(frame, sequence);
            boolean containsSocket = false;
            for (int i = 0; i < sequenceLib.lengthWithState(sequence, threadState); i++) {
                Object pythonObject = callGetItemNode.executeObject(frame, pSequence, i);
                objects.add(pythonObject);
                int fd = itemLib.asFileDescriptorWithState(pythonObject, threadState);
                if (fd >= FD_SETSIZE.value) {
                    throw raise(ValueError, ErrorMessages.FILE_DESCRIPTOR_OUT_OF_RANGE_IN_SELECT);
                }
                fds.add(fd);
                containsSocket |= resources.isSocket(fd);
            }
            return new ObjAndFDList(objects.toArray(new Object[0]), fds.toArray(), containsSocket);
        }

        @ValueType
        private static final class ObjAndFDList {
            private final Object[] objects;
            private final int[] fds;
            private final boolean containsSocket; // TODO remove when native sockets are supported

            private ObjAndFDList(Object[] objects, int[] fds, boolean containsSocket) {
                this.objects = objects;
                this.fds = fds;
                this.containsSocket = containsSocket;
            }
        }

        static LookupAndCallBinaryNode createGetItem() {
            return LookupAndCallBinaryNode.create(SpecialMethodNames.__GETITEM__);
        }
    }

    static final long US_TO_NS = 1000L;
    static final long MS_TO_US = 1000L;
    static final long SEC_TO_MS = 1000L;
    static final long MS_TO_NS = MS_TO_US * US_TO_NS;
    static final long SEC_TO_NS = SEC_TO_MS * MS_TO_NS;
    static final long SEC_TO_US = SEC_TO_MS * MS_TO_US;

    static Timeval timeAsTimeval(long t) {
        long secs = t / SEC_TO_NS;
        long ns = t % SEC_TO_NS;
        // Note: we cannot really have secs == Long.MIN_VALUE or Long.MAX_VALUE like it is possible
        // in CPython if the C types of 't' and 'secs' do not match
        long usec = pyTimeDivide(ns, US_TO_NS);
        if (usec < 0) {
            usec += SEC_TO_US;
            secs -= 1;
        } else if (usec >= SEC_TO_US) {
            usec -= SEC_TO_US;
            secs += 1;
        }
        assert 0 <= usec && usec < SEC_TO_US;
        return new Timeval(secs, usec);
    }

    static long pyTimeDivide(long t, long k) {
        // _PyTime_Divide, for now hard-coded mode HALP_UP
        assert k > 1;
        if (t >= 0) {
            return (t + k - 1) / k;
        } else {
            return (t - (k - 1)) / k;
        }
    }

    /**
     * Equivalent of {@code _PyTime_FromObject} from CPython.
     */
    abstract static class PyTimeFromObjectNode extends PNodeWithRaise {
        abstract long execute(VirtualFrame frame, Object obj, long unitToNs);

        @Specialization
        long doDouble(double d, long unitToNs) {
            // Implements _PyTime_FromDouble, rounding mode (HALF_UP) is hard-coded for now
            if (Double.isNaN(d)) {
                throw raise(PythonBuiltinClassType.ValueError, INVALID_VALUE_NAN);
            }
            double value = d * unitToNs;
            value = value >= 0.0 ? Math.ceil(value) : Math.floor(value);
            if (value < Long.MIN_VALUE || value > Long.MAX_VALUE) {
                throw raiseTimeOverflow();
            }
            return (long) value;
        }

        @Specialization(limit = "1")
        long doFloat(VirtualFrame frame, PFloat value, long unitToNs,
                        @CachedLibrary("value") PythonObjectLibrary pol) {
            return doDouble(pol.asJavaDoubleWithFrame(value, frame), unitToNs);
        }

        @Specialization(limit = "1")
        long doOther(VirtualFrame frame, Object value, long unitToNs,
                        @CachedLibrary("value") PythonObjectLibrary pol) {
            try {
                return PythonUtils.multiplyExact(pol.asJavaLong(value, frame), unitToNs);
            } catch (OverflowException e) {
                throw raiseTimeOverflow();
            }
        }

        private PException raiseTimeOverflow() {
            throw raise(PythonBuiltinClassType.OverflowError, TOO_LARGE_TO_CONVERT_TO, "timestamp", "long");
        }
    }
}
