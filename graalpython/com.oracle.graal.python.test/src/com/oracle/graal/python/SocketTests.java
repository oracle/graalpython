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
import static com.oracle.graal.python.runtime.PosixConstants.AF_INET6;
import static com.oracle.graal.python.runtime.PosixConstants.AF_UNSPEC;
import static com.oracle.graal.python.runtime.PosixConstants.AI_CANONNAME;
import static com.oracle.graal.python.runtime.PosixConstants.AI_PASSIVE;
import static com.oracle.graal.python.runtime.PosixConstants.EAI_NONAME;
import static com.oracle.graal.python.runtime.PosixConstants.IN6ADDR_LOOPBACK;
import static com.oracle.graal.python.runtime.PosixConstants.INADDR_ANY;
import static com.oracle.graal.python.runtime.PosixConstants.INADDR_LOOPBACK;
import static com.oracle.graal.python.runtime.PosixConstants.IPPROTO_TCP;
import static com.oracle.graal.python.runtime.PosixConstants.IPPROTO_UDP;
import static com.oracle.graal.python.runtime.PosixConstants.SOCK_DGRAM;
import static com.oracle.graal.python.runtime.PosixConstants.SOCK_STREAM;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.runtime.PosixConstants.MandatoryIntConstant;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PosixSupportLibrary.AddrInfoCursor;
import com.oracle.graal.python.runtime.PosixSupportLibrary.AddrInfoCursorLibrary;
import com.oracle.graal.python.runtime.PosixSupportLibrary.Buffer;
import com.oracle.graal.python.runtime.PosixSupportLibrary.GetAddrInfoException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.Inet4SockAddr;
import com.oracle.graal.python.runtime.PosixSupportLibrary.Inet6SockAddr;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.UniversalSockAddr;
import com.oracle.graal.python.runtime.PosixSupportLibrary.UniversalSockAddrLibrary;

@RunWith(Parameterized.class)
public class SocketTests {

    @Parameter(0) public String backendName;

    @Parameters(name = "{0}")
    public static String[] params() {
        return new String[]{"native"};
    }

    @Rule public WithPythonContextRule withPythonContextRule = new WithPythonContextRule((o) -> o.put("python.PosixModuleBackend", backendName));

    @Rule public CleanupRule cleanup = new CleanupRule();
    @Rule public ExpectedException expectedException = ExpectedException.none();

    private Object posixSupport;
    private PosixSupportLibrary lib;
    private UniversalSockAddrLibrary usaLib;
    private AddrInfoCursorLibrary aicLib;

    @Before
    public void setUp() {
        posixSupport = withPythonContextRule.getPythonContext().getPosixSupport();
        lib = PosixSupportLibrary.getUncached();
        usaLib = UniversalSockAddrLibrary.getUncached();
        aicLib = AddrInfoCursorLibrary.getUncached();
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
    public void sendtoRecvfromInet6FamilySpecific() throws PosixException {
        int srvSocket = createSocket(AF_INET6.value, SOCK_DGRAM.value, 0);
        lib.bind(posixSupport, srvSocket, new Inet6SockAddr(0, IN6ADDR_LOOPBACK, 0, 0));

        Inet6SockAddr srvAddr = new Inet6SockAddr();
        lib.getsockname(posixSupport, srvSocket, srvAddr);

        int cliSocket = createSocket(AF_INET6.value, SOCK_DGRAM.value, 0);
        byte[] data = new byte[]{1, 2, 3};
        int sentCount = lib.sendto(posixSupport, cliSocket, data, data.length, 0, srvAddr);
        assertEquals(data.length, sentCount);

        byte[] buf = new byte[100];
        Inet6SockAddr srcAddr = new Inet6SockAddr();
        int recvCount = lib.recvfrom(posixSupport, srvSocket, buf, buf.length, 0, srcAddr);

        assertEquals(data.length, recvCount);
        assertArrayEquals(data, Arrays.copyOf(buf, recvCount));

        Inet6SockAddr cliAddr = new Inet6SockAddr();
        lib.getsockname(posixSupport, cliSocket, cliAddr);

        assertArrayEquals(IN6ADDR_LOOPBACK, srcAddr.getAddress());
        assertEquals(cliAddr.getPort(), srcAddr.getPort());
    }

    @Test
    public void sendtoRecvfromInetUniversal() throws PosixException {
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

    @Test
    public void acceptConnectInetFamilySpecific() throws PosixException {
        int listenSocket = createSocket(AF_INET.value, SOCK_STREAM.value, 0);
        lib.bind(posixSupport, listenSocket, new Inet4SockAddr(0, INADDR_LOOPBACK.value));
        lib.listen(posixSupport, listenSocket, 5);

        Inet4SockAddr listenAddr = new Inet4SockAddr();
        lib.getsockname(posixSupport, listenSocket, listenAddr);

        int cliSocket = createSocket(AF_INET.value, SOCK_STREAM.value, 0);
        lib.connect(posixSupport, cliSocket, listenAddr);

        Inet4SockAddr cliAddrOnClient = new Inet4SockAddr();
        lib.getsockname(posixSupport, cliSocket, cliAddrOnClient);

        Inet4SockAddr cliAddrOnServer = new Inet4SockAddr();
        int srvSocket = lib.accept(posixSupport, listenSocket, cliAddrOnServer);
        cleanup.add(() -> lib.close(posixSupport, srvSocket));

        assertEquals(cliAddrOnServer.getPort(), cliAddrOnClient.getPort());

        Inet4SockAddr srvAddrOnServer = new Inet4SockAddr();
        lib.getsockname(posixSupport, srvSocket, srvAddrOnServer);

        Inet4SockAddr srvAddrOnClient = new Inet4SockAddr();
        lib.getpeername(posixSupport, cliSocket, srvAddrOnClient);

        assertEquals(srvAddrOnServer.getPort(), srvAddrOnClient.getPort());

        byte[] data = new byte[]{1, 2, 3};
        assertEquals(data.length, lib.write(posixSupport, srvSocket, Buffer.wrap(data)));

        Buffer buf = lib.read(posixSupport, cliSocket, 100);
        assertEquals(data.length, buf.length);

        assertArrayEquals(data, Arrays.copyOf(buf.data, data.length));
    }

    @Test
    public void acceptConnectInet6FamilySpecific() throws PosixException {
        int listenSocket = createSocket(AF_INET6.value, SOCK_STREAM.value, 0);
        lib.bind(posixSupport, listenSocket, new Inet6SockAddr(0, IN6ADDR_LOOPBACK, 0, 0));
        lib.listen(posixSupport, listenSocket, 5);

        Inet6SockAddr listenAddr = new Inet6SockAddr();
        lib.getsockname(posixSupport, listenSocket, listenAddr);

        int cliSocket = createSocket(AF_INET6.value, SOCK_STREAM.value, 0);
        lib.connect(posixSupport, cliSocket, listenAddr);

        Inet6SockAddr cliAddrOnClient = new Inet6SockAddr();
        lib.getsockname(posixSupport, cliSocket, cliAddrOnClient);

        Inet6SockAddr cliAddrOnServer = new Inet6SockAddr();
        int srvSocket = lib.accept(posixSupport, listenSocket, cliAddrOnServer);
        cleanup.add(() -> lib.close(posixSupport, srvSocket));

        assertEquals(cliAddrOnServer.getPort(), cliAddrOnClient.getPort());

        Inet6SockAddr srvAddrOnServer = new Inet6SockAddr();
        lib.getsockname(posixSupport, srvSocket, srvAddrOnServer);

        Inet6SockAddr srvAddrOnClient = new Inet6SockAddr();
        lib.getpeername(posixSupport, cliSocket, srvAddrOnClient);

        assertEquals(srvAddrOnServer.getPort(), srvAddrOnClient.getPort());

        byte[] data = new byte[]{1, 2, 3};
        assertEquals(data.length, lib.write(posixSupport, srvSocket, Buffer.wrap(data)));

        Buffer buf = lib.read(posixSupport, cliSocket, 100);
        assertEquals(data.length, buf.length);

        assertArrayEquals(data, Arrays.copyOf(buf.data, data.length));
    }

    @Test
    public void acceptConnectInet6Universal() throws PosixException {
        int listenSocket = createSocket(AF_INET6.value, SOCK_STREAM.value, 0);
        UniversalSockAddr bindUsa = createUsa();
        usaLib.fill(bindUsa, new Inet6SockAddr(0, IN6ADDR_LOOPBACK, 0, 0));
        lib.bind(posixSupport, listenSocket, bindUsa);
        lib.listen(posixSupport, listenSocket, 5);

        UniversalSockAddr listenAddrUsa = createUsa();
        lib.getsockname(posixSupport, listenSocket, listenAddrUsa);

        assertEquals(AF_INET6.value, usaLib.getFamily(listenAddrUsa));

        int cliSocket = createSocket(AF_INET6.value, SOCK_STREAM.value, 0);
        lib.connect(posixSupport, cliSocket, listenAddrUsa);

        UniversalSockAddr cliAddrOnClientUsa = createUsa();
        lib.getsockname(posixSupport, cliSocket, cliAddrOnClientUsa);

        UniversalSockAddr cliAddrOnServerUsa = createUsa();
        int srvSocket = lib.accept(posixSupport, listenSocket, cliAddrOnServerUsa);
        cleanup.add(() -> lib.close(posixSupport, srvSocket));

        assertEquals(usaLib.asInet6SockAddr(cliAddrOnServerUsa).getPort(), usaLib.asInet6SockAddr(cliAddrOnClientUsa).getPort());

        UniversalSockAddr srvAddrOnServerUsa = createUsa();
        lib.getsockname(posixSupport, srvSocket, srvAddrOnServerUsa);

        UniversalSockAddr srvAddrOnClientUsa = createUsa();
        lib.getpeername(posixSupport, cliSocket, srvAddrOnClientUsa);

        assertEquals(usaLib.asInet6SockAddr(srvAddrOnServerUsa).getPort(), usaLib.asInet6SockAddr(srvAddrOnClientUsa).getPort());

        byte[] data = new byte[]{1, 2, 3};
        assertEquals(data.length, lib.write(posixSupport, srvSocket, Buffer.wrap(data)));

        Buffer buf = lib.read(posixSupport, cliSocket, 100);
        assertEquals(data.length, buf.length);

        assertArrayEquals(data, Arrays.copyOf(buf.data, data.length));
    }

    @Test
    public void getpeernameNotConnected() throws PosixException {
        expectErrno(OSErrorEnum.ENOTCONN);
        int s = createSocket(AF_INET.value, SOCK_STREAM.value, 0);
        lib.getpeername(posixSupport, s, new Inet4SockAddr());
    }

    @Test
    public void getaddrinfoNoInput() throws GetAddrInfoException {
        expectGetAddrInfoException(EAI_NONAME);
        lib.getaddrinfo(posixSupport, null, null, AF_UNSPEC.value, 0, 0, 0);
    }

    @Test
    public void getaddrinfoServiceOnly() throws GetAddrInfoException {
        Object service = lib.createPathFromString(posixSupport, "https");
        AddrInfoCursor aic = lib.getaddrinfo(posixSupport, null, service, AF_UNSPEC.value, SOCK_STREAM.value, 0, 0);
        cleanup.add(() -> aicLib.release(aic));
        do {
            int family = aicLib.getFamily(aic);

            assertEquals(SOCK_STREAM.value, aicLib.getSockType(aic));
            assertNull(aicLib.getCanonName(aic));

            UniversalSockAddr usa = createUsa();
            aicLib.getSockAddr(aic, usa);
            assertEquals(family, usaLib.getFamily(usa));
            if (family == AF_INET.value) {
                Inet4SockAddr addr2 = usaLib.asInet4SockAddr(usa);
                assertEquals(INADDR_LOOPBACK.value, addr2.getAddress());
                assertEquals(443, addr2.getPort());
            }
        } while (aicLib.next(aic));
    }

    @Test
    public void getaddrinfoPassive() throws GetAddrInfoException {
        Object service = lib.createPathFromString(posixSupport, "https");
        AddrInfoCursor aic = lib.getaddrinfo(posixSupport, null, service, AF_INET.value, 0, IPPROTO_TCP.value, AI_PASSIVE.value);
        cleanup.add(() -> aicLib.release(aic));
        assertEquals(AF_INET.value, aicLib.getFamily(aic));
        assertEquals(SOCK_STREAM.value, aicLib.getSockType(aic));
        assertEquals(IPPROTO_TCP.value, aicLib.getProtocol(aic));
        assertNull(aicLib.getCanonName(aic));

        Inet4SockAddr addr = new Inet4SockAddr();
        aicLib.getSockAddr(aic, addr);
        assertEquals(INADDR_ANY.value, addr.getAddress());
        assertEquals(443, addr.getPort());
    }

    @Test
    public void getaddrinfoServerOnlyNoCanon() throws GetAddrInfoException {
        Object node = lib.createPathFromString(posixSupport, "localhost");
        AddrInfoCursor aic = lib.getaddrinfo(posixSupport, node, null, AF_UNSPEC.value, SOCK_DGRAM.value, 0, 0);
        cleanup.add(() -> aicLib.release(aic));
        do {
            assertEquals(SOCK_DGRAM.value, aicLib.getSockType(aic));
            assertEquals(IPPROTO_UDP.value, aicLib.getProtocol(aic));
            assertNull(aicLib.getCanonName(aic));

            if (aicLib.getFamily(aic) == AF_INET.value) {
                Inet4SockAddr addr = new Inet4SockAddr();
                aicLib.getSockAddr(aic, addr);
                assertEquals(INADDR_LOOPBACK.value, addr.getAddress());
                assertEquals(0, addr.getPort());
            }
        } while (aicLib.next(aic));
    }

    @Test
    public void getaddrinfo() throws GetAddrInfoException {
        Object node = lib.createPathFromString(posixSupport, "localhost");
        Object service = lib.createPathFromString(posixSupport, "https");
        AddrInfoCursor aic = lib.getaddrinfo(posixSupport, node, service, AF_INET.value, 0, IPPROTO_TCP.value, AI_CANONNAME.value);
        cleanup.add(() -> aicLib.release(aic));
        assertEquals(AF_INET.value, aicLib.getFamily(aic));
        assertEquals(SOCK_STREAM.value, aicLib.getSockType(aic));
        assertEquals(IPPROTO_TCP.value, aicLib.getProtocol(aic));
        assertEquals("localhost", lib.getPathAsString(posixSupport, aicLib.getCanonName(aic)));

        Inet4SockAddr addr = new Inet4SockAddr();
        aicLib.getSockAddr(aic, addr);
        assertEquals(INADDR_LOOPBACK.value, addr.getAddress());
        assertEquals(443, addr.getPort());

        UniversalSockAddr usa = createUsa();
        aicLib.getSockAddr(aic, usa);
        assertEquals(AF_INET.value, usaLib.getFamily(usa));
        Inet4SockAddr addr2 = usaLib.asInet4SockAddr(usa);
        assertEquals(INADDR_LOOPBACK.value, addr2.getAddress());
        assertEquals(443, addr2.getPort());
    }

    private void expectErrno(OSErrorEnum expectedErrorCode) {
        expectedException.expect(new TypeSafeMatcher<PosixException>() {
            @Override
            protected boolean matchesSafely(PosixException item) {
                return item.getErrorCode() == expectedErrorCode.getNumber();
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("PosixException with error code ").appendValue(expectedErrorCode.name());
            }

            @Override
            protected void describeMismatchSafely(PosixException item, Description mismatchDescription) {
                mismatchDescription.appendText("the actual error code was ").appendValue(item.getErrorCode()).appendText(" (").appendValue(item).appendText(")");
            }
        });
    }

    private void expectGetAddrInfoException(MandatoryIntConstant expectedErrorCode) {
        expectedException.expect(new TypeSafeMatcher<GetAddrInfoException>() {
            @Override
            protected boolean matchesSafely(GetAddrInfoException item) {
                return item.getErrorCode() == expectedErrorCode.value;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("GetAddrInfoException with error code ").appendValue(expectedErrorCode.name);
            }

            @Override
            protected void describeMismatchSafely(GetAddrInfoException item, Description mismatchDescription) {
                mismatchDescription.appendText("the actual error code was ").appendValue(item.getErrorCode()).appendText(" (").appendValue(item).appendText(")");
            }
        });
    }

    private int createSocket(int family, int type, int protocol) throws PosixException {
        int sockfd = lib.socket(posixSupport, family, type, protocol);
        cleanup.add(() -> lib.close(posixSupport, sockfd));
        return sockfd;
    }

    private UniversalSockAddr createUsa() {
        UniversalSockAddr universalSockAddr = lib.allocUniversalSockAddr(posixSupport);
        cleanup.add(() -> usaLib.release(universalSockAddr));
        return universalSockAddr;
    }
}
