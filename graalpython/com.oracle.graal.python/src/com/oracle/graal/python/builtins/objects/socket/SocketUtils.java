/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.socket;

import static com.oracle.graal.python.builtins.objects.exception.OSErrorEnum.EAGAIN;
import static com.oracle.graal.python.builtins.objects.exception.OSErrorEnum.EINTR;
import static com.oracle.graal.python.builtins.objects.exception.OSErrorEnum.EWOULDBLOCK;
import static com.oracle.graal.python.builtins.objects.socket.PSocket.INVALID_FD;
import static com.oracle.graal.python.util.PythonUtils.EMPTY_INT_ARRAY;

import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.SelectResult;
import com.oracle.graal.python.runtime.PosixSupportLibrary.Timeval;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.util.TimeUtils;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.nodes.Node;

public class SocketUtils {
    @FunctionalInterface
    public interface SocketFunction<T> {
        /*
         * NB: The library and support need to be passed as arguments and shouldn't be taken from
         * the closure. Otherwise, Truffle has trouble inlining the library call.
         */
        T run(PosixSupportLibrary posixLib, Object posixSupport) throws PosixException;
    }

    /**
     * Rough equivalent of CPython's {@code sock_call}. Takes care of calling select for connections
     * with timeouts and retrying the call on EINTR. Must be called with GIL held.
     */
    public static <T> T callSocketFunctionWithRetry(Frame frame, Node inliningTarget, PConstructAndRaiseNode.Lazy constructAndRaiseNode, PosixSupportLibrary posixLib, Object posixSupport, GilNode gil,
                    PSocket socket, SocketFunction<T> function, boolean writing, boolean connect) throws PosixException {
        return callSocketFunctionWithRetry(frame, inliningTarget, constructAndRaiseNode, posixLib, posixSupport, gil, socket, function, writing, connect, null);
    }

    /**
     * Rough equivalent of CPython's {@code sock_call_ex}. Takes care of calling select for
     * connections with timeouts and retrying the call on EINTR. Must be called with GIL held.
     */
    public static <T> T callSocketFunctionWithRetry(Frame frame, Node inliningTarget, PConstructAndRaiseNode.Lazy constructAndRaiseNode, PosixSupportLibrary posixLib, Object posixSupport, GilNode gil,
                    PSocket socket, SocketFunction<T> function, boolean writing, boolean connect, TimeoutHelper timeoutHelperIn) throws PosixException {
        TimeoutHelper timeoutHelper = timeoutHelperIn;
        if (timeoutHelper == null && socket.getTimeoutNs() > 0) {
            timeoutHelper = new TimeoutHelper(socket.getTimeoutNs());
        }
        /*
         * outer loop to retry select() when select() is interrupted by a signal or to retry
         * select() and socket function on false positive
         */
        outer: while (true) {
            Timeval selectTimeout = null;
            if (timeoutHelper != null) {
                selectTimeout = timeoutHelper.checkAndGetRemainingTimeval(frame, inliningTarget, constructAndRaiseNode);
            }
            // For connect(), poll even for blocking socket. The connection runs asynchronously.
            if ((timeoutHelper != null || connect) && socket.getFd() != INVALID_FD) {
                try {
                    gil.release(true);
                    try {
                        int[] fds = new int[]{socket.getFd()};
                        int[] readfds = writing ? EMPTY_INT_ARRAY : fds;
                        int[] writefds = writing ? fds : EMPTY_INT_ARRAY;
                        SelectResult selectResult = posixLib.select(posixSupport, readfds, writefds, EMPTY_INT_ARRAY, selectTimeout);
                        boolean[] resultFds = writing ? selectResult.getWriteFds() : selectResult.getReadFds();
                        if (resultFds.length == 0 || !resultFds[0]) {
                            throw constructAndRaiseNode.get(inliningTarget).raiseTimeoutError(frame, ErrorMessages.TIMED_OUT);
                        }
                    } finally {
                        gil.acquire();
                    }
                } catch (PosixException e) {
                    if (e.getErrorCode() == EINTR.getNumber()) {
                        PythonContext.triggerAsyncActions(constructAndRaiseNode);
                        continue;
                    }
                    throw e;
                }
            }
            // inner loop to retry the socket function when interrupted by a signal
            while (true) {
                try {
                    gil.release(true);
                    try {
                        return function.run(posixLib, posixSupport);
                    } finally {
                        gil.acquire();
                    }
                } catch (PosixException e) {
                    if (e.getErrorCode() == EINTR.getNumber()) {
                        PythonContext.triggerAsyncActions(constructAndRaiseNode);
                        continue;
                    }
                    if (timeoutHelper != null && (e.getErrorCode() == EWOULDBLOCK.getNumber() || e.getErrorCode() == EAGAIN.getNumber())) {
                        /*
                         * False positive: sock_func() failed with EWOULDBLOCK or EAGAIN. For
                         * example, select() could indicate a socket is ready for reading, but the
                         * data then discarded by the OS because of a wrong checksum. Loop on
                         * select() to recheck for socket readiness.
                         */
                        continue outer;
                    }
                    throw e;
                }
            }
        }
    }

    public static class TimeoutHelper {
        long startNano;
        long initialTimeoutNs;

        public TimeoutHelper(long initialTimeoutNs) {
            this.initialTimeoutNs = initialTimeoutNs;
        }

        public long checkAndGetRemainingTimeoutNs(Frame frame, Node inliningTaget, PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            if (startNano == 0) {
                startNano = System.nanoTime();
                return initialTimeoutNs;
            } else {
                long remainingNs = initialTimeoutNs - (System.nanoTime() - startNano);
                if (remainingNs <= 0) {
                    throw constructAndRaiseNode.get(inliningTaget).raiseTimeoutError(frame, ErrorMessages.TIMED_OUT);
                }
                return remainingNs;
            }
        }

        public Timeval checkAndGetRemainingTimeval(Frame frame, Node inliningTarget, PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            return TimeUtils.pyTimeAsTimeval(checkAndGetRemainingTimeoutNs(frame, inliningTarget, constructAndRaiseNode));
        }
    }
}
