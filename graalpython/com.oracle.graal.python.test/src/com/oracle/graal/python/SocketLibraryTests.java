/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python;

import static com.oracle.graal.python.runtime.PosixConstants.AF_INET;
import static com.oracle.graal.python.runtime.PosixConstants.INADDR_LOOPBACK;
import static com.oracle.graal.python.runtime.PosixConstants.SOCK_DGRAM;
import static com.oracle.graal.python.runtime.SocketLibrary.htonl;
import static com.oracle.graal.python.runtime.SocketLibrary.htons;
import static com.oracle.graal.python.runtime.SocketLibrary.ntohl;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;

import com.oracle.graal.python.runtime.PosixSupportLibrary;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.runtime.SocketLibrary;

public class SocketLibraryTests {

    @Rule public WithPythonContextRule withPythonContextRule = new WithPythonContextRule(Collections.singletonMap("python.PosixModuleBackend", "native"));

    @Rule public CleanupRule cleanup = new CleanupRule();

    private Object posixSupport;
    private SocketLibrary lib;

    @Before
    public void setUp() {
        posixSupport = withPythonContextRule.getPythonContext().getPosixSupport();
        lib = SocketLibrary.getUncached();
    }

    @Test
    public void inet_dgram() throws PosixException {
        int srvSocket = socket(AF_INET.value, SOCK_DGRAM.value, 0);

        Object bindAddr = lib.createSockaddrIn(posixSupport);
        lib.sockaddrInSetPort(posixSupport, bindAddr, htons((short) 0));
        lib.sockaddrInSetAddr(posixSupport, bindAddr, htonl(INADDR_LOOPBACK.value));
        lib.bind(posixSupport, srvSocket, bindAddr);

        Object srvAddr = lib.createSockaddrIn(posixSupport);
        lib.getsockname(posixSupport, srvSocket, srvAddr);

        int cliSocket = socket(AF_INET.value, SOCK_DGRAM.value, 0);
        byte[] data = new byte[]{1, 2, 3};
        int cnt = lib.sendto(posixSupport, cliSocket, data, data.length, 0, srvAddr);
        assertEquals(data.length, cnt);

        byte[] buf = new byte[100];
        Object srcAddr = lib.createSockaddrIn(posixSupport);
        cnt = lib.recvfrom(posixSupport, srvSocket, buf, buf.length, 0, srcAddr);

        assertEquals(data.length, cnt);
        assertArrayEquals(data, Arrays.copyOf(buf, cnt));

        Object cliAddr = lib.createSockaddrIn(posixSupport);
        lib.getsockname(posixSupport, cliSocket, cliAddr);

        assertEquals(INADDR_LOOPBACK.value, ntohl(lib.sockaddrInGetAddr(posixSupport, srcAddr)));
        assertEquals(lib.sockaddrInGetPort(posixSupport, cliAddr), lib.sockaddrInGetPort(posixSupport, srcAddr));
    }

    private int socket(int family, int type, int protocol) throws PosixException {
        int sockfd = lib.socket(posixSupport, family, type, protocol);
        cleanup.add(() -> PosixSupportLibrary.getUncached().close(posixSupport, sockfd));
        return sockfd;
    }

}
