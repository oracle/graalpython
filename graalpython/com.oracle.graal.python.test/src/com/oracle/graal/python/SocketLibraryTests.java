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
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.runtime.SocketLibrary;
import com.oracle.graal.python.runtime.SocketLibrary.Inet4SockAddr;
import com.oracle.graal.python.runtime.SocketLibrary.UniversalSockAddr;

public class SocketLibraryTests {

    @Rule public WithPythonContextRule withPythonContextRule = new WithPythonContextRule(Collections.singletonMap("python.PosixModuleBackend", "native"));

    @Rule public CleanupRule cleanup = new CleanupRule();

    private Object posixSupport;
    private SocketLibrary lib;
    private SocketLibrary.UniversalSockAddrLibrary usaLib;

    @Before
    public void setUp() {
        posixSupport = withPythonContextRule.getPythonContext().getPosixSupport();
        lib = SocketLibrary.getUncached();
        usaLib = SocketLibrary.UniversalSockAddrLibrary.getUncached();
    }

    @Test
    public void fillUniversalSockAddr() {
        Inet4SockAddr addr = new Inet4SockAddr(12345, INADDR_LOOPBACK.value);

        UniversalSockAddr usa = createUsa();
        usaLib.fill(usa, addr);
        assertEquals(AF_INET.value, usaLib.getFamily(usa));
        Inet4SockAddr addr2 = usaLib.asInet4SockAddr(usa);
        assertEquals(addr.getPort(), addr2.getPort());
        assertEquals(addr.getAddress(), addr2.getAddress());

        UniversalSockAddr usaCopy = createUsa();
        usaLib.fill(usaCopy, usa);
        assertEquals(AF_INET.value, usaLib.getFamily(usaCopy));
        Inet4SockAddr addr3 = usaLib.asInet4SockAddr(usaCopy);
        assertEquals(addr.getPort(), addr3.getPort());
        assertEquals(addr.getAddress(), addr3.getAddress());
    }

    @Test
    public void bindGetsocknameFamilySpecific() throws PosixException {
        int s = createSocket(AF_INET.value, SOCK_DGRAM.value, 0);
        lib.bind(posixSupport, s, new Inet4SockAddr(0, INADDR_LOOPBACK.value));

        Inet4SockAddr boundAddr = new Inet4SockAddr();
        lib.getsockname(posixSupport, s, boundAddr);
        assertTrue(boundAddr.getPort() != 0);
        assertEquals(INADDR_LOOPBACK.value, boundAddr.getAddress());
    }

    @Test
    public void bindGetsocknameUniversal() throws PosixException {
        int s = createSocket(AF_INET.value, SOCK_DGRAM.value, 0);
        UniversalSockAddr bindUsa = createUsa();
        usaLib.fill(bindUsa, new Inet4SockAddr(0, INADDR_LOOPBACK.value));
        lib.bind(posixSupport, s, bindUsa);

        UniversalSockAddr boundUsa = createUsa();
        lib.getsockname(posixSupport, s, boundUsa);
        assertEquals(AF_INET.value, usaLib.getFamily(boundUsa));
        Inet4SockAddr boundAddr = usaLib.asInet4SockAddr(boundUsa);
        assertTrue(boundAddr.getPort() != 0);
        assertEquals(INADDR_LOOPBACK.value, boundAddr.getAddress());
    }

    @Test
    public void sendtoRecvfromFamilySpecific() throws PosixException {
        int srvSocket = createSocket(AF_INET.value, SOCK_DGRAM.value, 0);
        lib.bind(posixSupport, srvSocket, new Inet4SockAddr(0, INADDR_LOOPBACK.value));

        Inet4SockAddr srvAddr = new Inet4SockAddr();
        lib.getsockname(posixSupport, srvSocket, srvAddr);

        int cliSocket = createSocket(AF_INET.value, SOCK_DGRAM.value, 0);
        byte[] data = new byte[]{1, 2, 3};
        int sentCount = lib.sendto(posixSupport, cliSocket, data, data.length, 0, srvAddr);
        assertEquals(data.length, sentCount);

        byte[] buf = new byte[100];
        Inet4SockAddr srcAddr = new Inet4SockAddr();
        int recvCount = lib.recvfrom(posixSupport, srvSocket, buf, buf.length, 0, srcAddr);

        assertEquals(data.length, recvCount);
        assertArrayEquals(data, Arrays.copyOf(buf, recvCount));

        Inet4SockAddr cliAddr = new Inet4SockAddr();
        lib.getsockname(posixSupport, cliSocket, cliAddr);

        assertEquals(INADDR_LOOPBACK.value, srcAddr.getAddress());
        assertEquals(cliAddr.getPort(), srcAddr.getPort());
    }

    @Test
    public void sendtoRecvfromUniversal() throws PosixException {
        int srvSocket = createSocket(AF_INET.value, SOCK_DGRAM.value, 0);
        UniversalSockAddr bindUsa = createUsa();
        usaLib.fill(bindUsa, new Inet4SockAddr(0, INADDR_LOOPBACK.value));
        lib.bind(posixSupport, srvSocket, bindUsa);

        UniversalSockAddr srvUsa = createUsa();
        lib.getsockname(posixSupport, srvSocket, srvUsa);

        int cliSocket = createSocket(AF_INET.value, SOCK_DGRAM.value, 0);
        byte[] data = new byte[]{1, 2, 3};
        int sentCount = lib.sendto(posixSupport, cliSocket, data, data.length, 0, srvUsa);
        assertEquals(data.length, sentCount);

        byte[] buf = new byte[100];
        UniversalSockAddr srcUsa = createUsa();
        int recvCount = lib.recvfrom(posixSupport, srvSocket, buf, buf.length, 0, srcUsa);

        assertEquals(data.length, recvCount);
        assertArrayEquals(data, Arrays.copyOf(buf, recvCount));

        assertEquals(AF_INET.value, usaLib.getFamily(srcUsa));
        Inet4SockAddr srcAddr = usaLib.asInet4SockAddr(srcUsa);

        UniversalSockAddr cliUsa = createUsa();
        lib.getsockname(posixSupport, cliSocket, cliUsa);
        assertEquals(AF_INET.value, usaLib.getFamily(cliUsa));
        Inet4SockAddr cliAddr = usaLib.asInet4SockAddr(cliUsa);

        assertEquals(INADDR_LOOPBACK.value, srcAddr.getAddress());
        assertEquals(cliAddr.getPort(), srcAddr.getPort());
    }

    private int createSocket(int family, int type, int protocol) throws PosixException {
        int sockfd = lib.socket(posixSupport, family, type, protocol);
        cleanup.add(() -> PosixSupportLibrary.getUncached().close(posixSupport, sockfd));
        return sockfd;
    }

    private UniversalSockAddr createUsa() {
        UniversalSockAddr universalSockAddr = lib.allocUniversalSockAddr(posixSupport);
        cleanup.add(() -> usaLib.release(universalSockAddr));
        return universalSockAddr;
    }
}
