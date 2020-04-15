/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedReader;
import java.io.IOException;
import java.net.IDN;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.socket.PSocket;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CastToJavaIntNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;

import org.graalvm.nativeimage.ImageInfo;

@CoreFunctions(defineModule = "_socket")
public class SocketModuleBuiltins extends PythonBuiltins {
    // address families
    private static final int AF_UNSPEC = 0;
    private static final int AF_INET = 2;
    private static final int AF_INET6 = 30;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SocketModuleBuiltinsFactory.getFactories();
    }

    private static class Service {
        int port;
        String protocol;

        public Service(int port, String protocol) {
            this.port = port;
            this.protocol = protocol;
        }
    }

    protected static Map<String, List<Service>> services;
    protected static Map<String, Integer> protocols;

    @TruffleBoundary
    private static Map<String, List<Service>> parseServices(TruffleLanguage.Env env) {
        TruffleFile services_file = env.getPublicTruffleFile("/etc/services");
        try {
            BufferedReader br = services_file.newBufferedReader();
            String line;
            Map<String, List<Service>> parsedServices = new HashMap<>();
            while ((line = br.readLine()) != null) {
                String[] service = cleanLine(line);
                if (service == null) {
                    continue;
                }
                String[] portAndProtocol = service[1].split("/");
                List<Service> serviceObj = parsedServices.computeIfAbsent(service[0], k -> new LinkedList<>());
                Service newService = new Service(Integer.parseInt(portAndProtocol[0]), portAndProtocol[1]);
                serviceObj.add(newService);
                if (service.length > 2) {
                    for (int i = 2; i < service.length; i++) {
                        serviceObj = parsedServices.computeIfAbsent(service[i], k -> new LinkedList<>());
                        serviceObj.add(newService);
                    }
                }
            }
            return parsedServices;
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    @TruffleBoundary
    private static Map<String, Integer> parseProtocols(TruffleLanguage.Env env) {
        TruffleFile protocols_file = env.getPublicTruffleFile("/etc/protocols");
        try {
            BufferedReader br = protocols_file.newBufferedReader();
            String line;
            Map<String, Integer> parsedProtocols = new HashMap<>();
            while ((line = br.readLine()) != null) {
                String[] protocol = cleanLine(line);
                if (protocol == null) {
                    continue;
                }
                String protocolString = protocol[0];
                int protocolId = Integer.valueOf(protocol[1]);
                parsedProtocols.put(protocolString, protocolId);
            }
            return parsedProtocols;
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    @TruffleBoundary
    private static String searchServicesForPort(TruffleLanguage.Env env, int port, String protocol) {
        if (services == null) {
            services = parseServices(env);
        }

        Set<String> servicesNames = services.keySet();

        for (String servName : servicesNames) {
            List<Service> serv = services.get(servName);
            for (Service servProto : serv) {
                if (servProto.port == port && (protocol == null || protocol.equals(servProto.protocol))) {
                    return servName;
                }
            }
        }
        return null;
    }

    private static String[] cleanLine(String input) {
        String line = input;
        if (line.startsWith("#")) {
            return null;
        }
        line = line.replaceAll("\\s+", " ");
        if (line.startsWith(" ")) {
            return null;
        }
        line = line.split("#")[0];
        String[] words = line.split(" ");
        if (words.length < 2) {
            return null;
        }
        return words;
    }

    @Override
    public void initialize(PythonCore core) {
        super.initialize(core);
        builtinConstants.put("AF_UNSPEC", AF_UNSPEC);
        builtinConstants.put("AF_INET", AF_INET);
        builtinConstants.put("AF_INET6", AF_INET6);
        if (ImageInfo.inImageBuildtimeCode()) {
            // we do this eagerly for SVM images
            services = parseServices(core.getContext().getEnv());
            protocols = parseProtocols(core.getContext().getEnv());
        }
    }

    // socket(family=AF_INET, type=SOCK_STREAM, proto=0, fileno=None)
    @Builtin(name = "socket", minNumOfPositionalArgs = 1, parameterNames = {"cls", "family", "type", "proto", "fileno"}, constructsClass = PythonBuiltinClassType.PSocket)
    @GenerateNodeFactory
    public abstract static class SocketNode extends PythonBuiltinNode {
        @Specialization(guards = {"isNoValue(family)", "isNoValue(type)", "isNoValue(proto)", "isNoValue(fileno)"})
        Object socket(LazyPythonClass cls, @SuppressWarnings("unused") PNone family, @SuppressWarnings("unused") PNone type, @SuppressWarnings("unused") PNone proto,
                        @SuppressWarnings("unused") PNone fileno) {
            return createSocketInternal(cls, PSocket.AF_INET, PSocket.SOCK_STREAM, 0);
        }

        @Specialization(guards = {"isNoValue(family)", "isNoValue(type)", "isNoValue(proto)", "!isNoValue(fileno)"})
        Object socket(VirtualFrame frame, LazyPythonClass cls, @SuppressWarnings("unused") PNone family, @SuppressWarnings("unused") PNone type, @SuppressWarnings("unused") PNone proto, Object fileno,
                        @Cached CastToJavaIntNode cast) {
            return createSocketInternal(frame, cls, -1, -1, -1, cast.execute(fileno));
        }

        @Specialization(guards = {"!isNoValue(family)", "isNoValue(type)", "isNoValue(proto)", "isNoValue(fileno)"})
        Object socket(LazyPythonClass cls, Object family, @SuppressWarnings("unused") PNone type, @SuppressWarnings("unused") PNone proto, @SuppressWarnings("unused") PNone fileno,
                        @Cached CastToJavaIntNode cast) {
            return createSocketInternal(cls, cast.execute(family), PSocket.SOCK_STREAM, 0);
        }

        @Specialization(guards = {"!isNoValue(family)", "!isNoValue(type)", "isNoValue(proto)", "isNoValue(fileno)"})
        Object socket(LazyPythonClass cls, Object family, Object type, @SuppressWarnings("unused") PNone proto, @SuppressWarnings("unused") PNone fileno,
                        @Cached CastToJavaIntNode cast) {
            return createSocketInternal(cls, cast.execute(family), cast.execute(type), 0);
        }

        @Specialization(guards = {"!isNoValue(family)", "!isNoValue(type)", "!isNoValue(proto)", "isNoValue(fileno)"})
        Object socket(LazyPythonClass cls, Object family, Object type, Object proto, @SuppressWarnings("unused") PNone fileno,
                        @Cached CastToJavaIntNode cast) {
            return createSocketInternal(cls, cast.execute(family), cast.execute(type), cast.execute(proto));
        }

        @Specialization(guards = {"!isNoValue(family)", "!isNoValue(type)", "!isNoValue(proto)", "!isNoValue(fileno)"})
        Object socket(VirtualFrame frame, LazyPythonClass cls, Object family, Object type, Object proto, Object fileno,
                        @Cached CastToJavaIntNode cast) {
            return createSocketInternal(frame, cls, cast.execute(family), cast.execute(type), cast.execute(proto), cast.execute(fileno));
        }

        private Object createSocketInternal(LazyPythonClass cls, int family, int type, int proto) {
            if (getContext().getEnv().isNativeAccessAllowed()) {
                PSocket newSocket = factory().createSocket(cls, family, type, proto);
                int fd = getContext().getResources().openSocket(newSocket);
                newSocket.setFileno(fd);
                return newSocket;
            } else {
                throw raise(PythonErrorType.RuntimeError, "creating sockets not allowed");
            }
        }

        private Object createSocketInternal(VirtualFrame frame, LazyPythonClass cls, int family, int type, int proto, int fileno) {
            if (getContext().getEnv().isNativeAccessAllowed()) {
                PSocket oldSocket = getContext().getResources().getSocket(fileno);
                if (oldSocket == null) {
                    throw raiseOSError(frame, OSErrorEnum.EBADF);
                }
                PSocket newSocket = factory().createSocket(cls, family == -1 ? oldSocket.getFamily() : family, type == -1 ? oldSocket.getType() : type, proto == -1 ? oldSocket.getProto() : proto,
                                fileno);
                if (oldSocket.getSocket() != null) {
                    newSocket.setSocket(oldSocket.getSocket());
                } else if (oldSocket.getServerSocket() != null) {
                    newSocket.setServerSocket(oldSocket.getServerSocket());
                }
                getContext().getResources().reopenSocket(newSocket, fileno);
                return newSocket;
            } else {
                throw raise(PythonErrorType.RuntimeError, "creating sockets not allowed");
            }
        }
    }

    @Builtin(name = "gethostname", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class GetHostnameNode extends PythonBuiltinNode {
        @Specialization
        String doGeneric() {
            try {
                return getHostName(getLocalHost());
            } catch (UnknownHostException e) {
                throw raise(PythonBuiltinClassType.OSError);
            }
        }
    }

    @TruffleBoundary
    private static InetAddress getLocalHost() throws UnknownHostException {
        return InetAddress.getLocalHost();
    }

    @Builtin(name = "gethostbyaddr", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class GetHostByAddrNode extends PythonBuiltinNode {
        @Specialization
        PTuple doGeneric(VirtualFrame frame, PString ip_address) {
            return doGeneric(frame, ip_address.getValue());
        }

        @Specialization
        PTuple doGeneric(VirtualFrame frame, String ip_address) {
            try {
                InetAddress[] adresses = getAllByName(ip_address);
                Object hostname = PNone.NONE;
                Object[] strAdresses = new Object[adresses.length];
                for (int i = 0; i < adresses.length; i++) {
                    if (hostname == PNone.NONE) {
                        hostname = getCanonicalHostName(adresses[i]);
                    }
                    strAdresses[i] = getHostAddress(adresses[i]);
                }
                PList pAdresses = factory().createList(strAdresses);
                return factory().createTuple(new Object[]{hostname, factory().createList(), pAdresses});
            } catch (UnknownHostException e) {
                throw raiseOSError(frame, OSErrorEnum.EHOSTUNREACH, e);
            }
        }
    }

    @TruffleBoundary
    private static String getHostAddress(InetAddress address) {
        return address.getHostAddress();
    }

    @TruffleBoundary
    private static String getCanonicalHostName(InetAddress address) {
        return address.getCanonicalHostName();
    }

    @TruffleBoundary
    private static InetAddress[] getAllByName(String ip_address) throws UnknownHostException {
        return InetAddress.getAllByName(ip_address);
    }

    @Builtin(name = "gethostbyname", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class GetHostByNameNode extends PythonBuiltinNode {
        @Specialization
        Object getHostByName(String name) {
            try {
                InetAddress[] adresses = getAllByName(IDNtoASCII(name));
                if (adresses.length == 0) {
                    return PNone.NONE;
                }
                return factory().createString(getHostAddress(adresses[0]));
            } catch (UnknownHostException e) {
                throw raise(PythonBuiltinClassType.OSError);
            }
        }

        @TruffleBoundary
        private String IDNtoASCII(String name) {
            try {
                return IDN.toASCII(name);
            } catch (IllegalArgumentException e) {
                throw raise(PythonBuiltinClassType.UnicodeError, "IDN encoding failed: %s", e.getMessage());
            }
        }
    }

    @Builtin(name = "getservbyname", parameterNames = {"servicename", "protocolname"})
    @GenerateNodeFactory
    public abstract static class GetServByNameNode extends PythonBuiltinNode {
        @TruffleBoundary
        @Specialization(guards = {"isNoValue(protocolName)"})
        Object getServByName(String serviceName, @SuppressWarnings("unused") PNone protocolName) {
            if (services == null) {
                services = parseServices(getContext().getEnv());
            }

            List<Service> portsForService = services.get(serviceName);

            if (portsForService.size() == 0) {
                throw raise(PythonBuiltinClassType.OSError);
            } else {
                return factory().createInt(portsForService.get(0).port);
            }
        }

        @Specialization
        Object getServByName(String serviceName, String protocolName) {
            if (services == null) {
                services = parseServices(getContext().getEnv());
            }
            int port = op(serviceName, protocolName);
            if (port >= 0) {
                return port;
            } else {
                throw raise(PythonBuiltinClassType.OSError);
            }
        }

        @TruffleBoundary
        private static int op(String serviceName, String protocolName) {
            for (Service service : services.get(serviceName)) {
                if (service.protocol.equals(protocolName)) {
                    return service.port;
                }
            }
            return -1;
        }
    }

    @Builtin(name = "getservbyport", parameterNames = {"port", "protocolname"})
    @GenerateNodeFactory
    public abstract static class GetServByPortNode extends PythonBuiltinNode {

        @Specialization(guards = {"isNoValue(protocolName)"})
        Object getServByPort(long port, @SuppressWarnings("unused") PNone protocolName) {
            return getServByPort((int) port, protocolName);
        }

        @Specialization
        Object getServByPort(long port, String protocolName) {
            return getServByPort((int) port, protocolName);
        }

        @Specialization(guards = {"isNoValue(protocolName)"})
        Object getServByPort(PInt port, @SuppressWarnings("unused") PNone protocolName) {
            return getServByPort(port.intValue(), protocolName);
        }

        @Specialization
        Object getServByPort(PInt port, String protocolName) {
            return getServByPort(port.intValue(), protocolName);
        }

        @Specialization(guards = {"isNoValue(protocolName)"})
        Object getServByPort(int port, @SuppressWarnings("unused") PNone protocolName) {
            return getServByPort(port, (String) null);
        }

        @Specialization
        Object getServByPort(int port, String protocolName) {
            if (port < 0 || port > 65535) {
                throw raise(PythonBuiltinClassType.OverflowError);
            }
            String service = searchServicesForPort(getContext().getEnv(), port, protocolName);
            if (service != null) {
                return service;
            }
            throw raise(PythonBuiltinClassType.OSError, "port/proto not found");
        }
    }

    @Builtin(name = "getnameinfo", minNumOfPositionalArgs = 2, parameterNames = {"sockaddr", "flags"})
    @GenerateNodeFactory
    public abstract static class GetNameInfoNode extends PythonBuiltinNode {
        @Specialization
        Object getNameInfo(VirtualFrame frame, PTuple sockaddr, Object flagArg,
                        @Cached CastToJavaIntNode castFlags,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached SequenceStorageNodes.GetItemNode getItem,
                        @Cached CastToJavaStringNode castAddress,
                        @Cached CastToJavaIntNode castPort) {
            int flags = castFlags.execute(flagArg);
            SequenceStorage addr = sockaddr.getSequenceStorage();
            int addLen = lenNode.execute(addr);
            if (addLen != 2 && addLen != 4) {
                throw raise(PythonBuiltinClassType.OSError);
            }
            String address = castAddress.execute(getItem.execute(frame, addr, 0));
            int port = castPort.execute(getItem.execute(frame, addr, 1));

            if ((flags & PSocket.NI_NUMERICHOST) != PSocket.NI_NUMERICHOST) {
                try {
                    address = getHostName(getByName(address));
                } catch (UnknownHostException e) {
                    throw raise(PythonBuiltinClassType.OSError);
                }
            }

            String portServ = String.valueOf(port);
            if ((flags & PSocket.NI_NUMERICSERV) != PSocket.NI_NUMERICSERV) {
                portServ = searchServicesForPort(getContext().getEnv(), port, null);
                if (portServ == null) {
                    throw raise(PythonBuiltinClassType.OSError, "port/proto not found");
                }
            }

            return factory().createTuple(new Object[]{address, portServ});
        }
    }

    @TruffleBoundary
    private static InetAddress getByName(String address) throws UnknownHostException {
        return InetAddress.getByName(address);
    }

    @TruffleBoundary
    private static String getHostName(InetAddress address) {
        return address.getHostName();
    }

    @Builtin(name = "getaddrinfo", parameterNames = {"host", "port", "family", "type", "proto", "flags"})
    @GenerateNodeFactory
    public abstract static class GetAddrInfoNode extends PythonBuiltinNode {
        private final BranchProfile stringPortProfile = BranchProfile.create();
        private final BranchProfile nonePortProfile = BranchProfile.create();
        private final BranchProfile intPortProfile = BranchProfile.create();

        @Specialization
        Object getAddrInfoPString(PString host, Object port, Object family, Object type, Object proto, Object flags,
                        @Cached CastToJavaIntNode cast) {
            return getAddrInfoString(host.getValue(), port, family, type, proto, flags, cast);
        }

        @Specialization
        Object getAddrInfoNone(@SuppressWarnings("unused") PNone host, Object port, Object family, Object type, Object proto, Object flags,
                        @Cached CastToJavaIntNode cast) {
            return getAddrInfoString("localhost", port, family, type, proto, flags, cast);
        }

        @Specialization
        Object getAddrInfoString(String host, Object port, Object family, Object type, Object proto, Object flags,
                        @Cached CastToJavaIntNode cast) {
            InetAddress[] addresses;
            try {
                addresses = getAllByName(host);
            } catch (UnknownHostException e) {
                throw raise(PythonBuiltinClassType.OSError);
            }

            String stringPort = null;
            if (port instanceof PString) {
                stringPort = ((PString) port).getValue();
            } else if (port instanceof String) {
                stringPort = (String) port;
            }

            if (stringPort != null) {
                stringPortProfile.enter();
                return getAddrInfo(addresses, stringPort, cast.execute(family), cast.execute(type), cast.execute(proto), cast.execute(flags));
            } else if (port instanceof PNone) {
                nonePortProfile.enter();
                return mergeAdressesAndServices(addresses, null, cast.execute(family), cast.execute(type), cast.execute(proto), cast.execute(flags));
            } else {
                intPortProfile.enter();
                return getAddrInfo(addresses, cast.execute(port), cast.execute(family), cast.execute(type), cast.execute(proto), cast.execute(flags));
            }
        }

        @TruffleBoundary
        private Object getAddrInfo(InetAddress[] addresses, int port, int family, int type, int proto, int flags) {
            List<Service> serviceList = new ArrayList<>();
            serviceList.add(new Service(port, "tcp"));
            serviceList.add(new Service(port, "udp"));
            return mergeAdressesAndServices(addresses, serviceList, family, type, proto, flags);
        }

        @TruffleBoundary
        private Object getAddrInfo(InetAddress[] addresses, String port, int family, int type, int proto, int flags) {
            if (!StandardCharsets.US_ASCII.newEncoder().canEncode(port)) {
                throw raise(PythonBuiltinClassType.UnicodeEncodeError);
            }
            if (services == null) {
                services = parseServices(getContext().getEnv());
            }
            List<Service> serviceList = services.get(port);
            return mergeAdressesAndServices(addresses, serviceList, family, type, proto, flags);
        }

        @TruffleBoundary
        private Object mergeAdressesAndServices(InetAddress[] adresses, List<Service> serviceList, int family, int type, int proto, int flags) {
            if (protocols == null) {
                protocols = parseProtocols(getContext().getEnv());
            }
            List<Object> addressTuples = new ArrayList<>();
            for (InetAddress addr : adresses) {
                if (serviceList != null) {
                    for (Service srv : serviceList) {
                        int protocol = protocols.get(srv.protocol);
                        if (proto != 0 && proto != protocol) {
                            continue;
                        }
                        PTuple addrTuple = createAddressTuple(addr, srv.port, family, type, protocol, flags);
                        if (addrTuple != null) {
                            addressTuples.add(addrTuple);
                        }
                    }
                }
            }
            return factory().createList(addressTuples.toArray());
        }

        private PTuple createAddressTuple(InetAddress address, int port, int family, int type, int proto, int flags) {
            int addressFamily;
            Object sockAddr;
            int addressType = proto == PSocket.IPPROTO_TCP ? 1 : 2;
            if (type != 0 && addressType != type) {
                return null;
            }
            if (address instanceof Inet4Address) {
                addressFamily = 2;
                if (family != 0 && family != addressFamily) {
                    return null;
                }
                sockAddr = factory().createTuple(new Object[]{address.getHostAddress(), port});
            } else {
                addressFamily = 30;
                if (family != 0 && family != addressFamily) {
                    return null;
                }
                sockAddr = factory().createTuple(new Object[]{address.getHostAddress(), port, 0, 0});
            }
            String canonname = (flags & PSocket.AI_CANONNAME) == PSocket.AI_CANONNAME ? address.getHostName() : "";
            return factory().createTuple(new Object[]{addressFamily, addressType, proto, canonname, sockAddr});
        }
    }

    @Builtin(name = "close", minNumOfPositionalArgs = 1, parameterNames = {"fd"})
    @GenerateNodeFactory
    public abstract static class moduleCloseNode extends PythonBuiltinNode {
        @Specialization
        Object close(VirtualFrame frame, int fd) {
            if (fd < 0) {
                throw raise(PythonBuiltinClassType.OSError, "Bad file descriptor");
            }

            PSocket socket = getContext().getResources().getSocket(fd);

            if (socket == null) {
                throw raiseOSError(frame, OSErrorEnum.EBADF);
            }

            if (!socket.isOpen()) {
                throw raiseOSError(frame, OSErrorEnum.EBADF);
            }

            try {
                socket.close();
            } catch (IOException e) {
                throw raiseOSError(frame, OSErrorEnum.EBADF);
            }
            getContext().getResources().close(socket.getFileno());
            return PNone.NONE;
        }

        @Fallback
        Object close(@SuppressWarnings("unused") Object fd) {
            throw raise(PythonBuiltinClassType.TypeError);
        }
    }

    @Builtin(name = "inet_aton", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class InetAtoNNode extends PythonUnaryBuiltinNode {
        @Specialization
        PBytes doConvert(String addr) {
            return factory().createBytes(aton(addr));
        }

        @TruffleBoundary
        private byte[] aton(String s) {
            try {
                return Inet4Address.getByName(s).getAddress();
            } catch (UnknownHostException e) {
                throw raise(PythonBuiltinClassType.OSError, "illegal IP address string passed to inet_aton");
            }
        }

        @Fallback
        PBytes doError(Object obj) {
            throw raise(PythonBuiltinClassType.TypeError, "inet_aton() argument 1 must be str, not %p", obj);
        }
    }

    @Builtin(name = "inet_ntoa", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class InetNtoANode extends PythonUnaryBuiltinNode {
        @Specialization
        String doGeneric(VirtualFrame frame, Object obj,
                        @Cached("createToBytes()") BytesNodes.ToBytesNode toBytesNode) {
            return ntoa(toBytesNode.execute(frame, obj));
        }

        @TruffleBoundary
        private String ntoa(byte[] bytes) {
            try {
                return InetAddress.getByAddress(bytes).toString();
            } catch (UnknownHostException e) {
                // the exception will only be thrown if 'bytes' has the wrong length
                throw raise(PythonBuiltinClassType.OSError, "packed IP wrong length for inet_ntoa");
            }
        }

        static BytesNodes.ToBytesNode createToBytes() {
            return BytesNodes.ToBytesNode.create(true, PythonBuiltinClassType.TypeError, "a bytes-like object is required, not '%p'");
        }
    }

    @Builtin(name = "inet_pton", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class InetPtoNNode extends PythonBinaryBuiltinNode {
        @Specialization
        PBytes doConvert(@SuppressWarnings("unused") VirtualFrame frame, Object addrFamily, String addr,
                        @Cached CastToJavaIntNode castToJavaIntNode) {
            return factory().createBytes(aton(castToJavaIntNode.execute(addrFamily), addr));
        }

        @TruffleBoundary
        private byte[] aton(int addrFamily, String s) {
            try {
                if (addrFamily != AF_INET && addrFamily != AF_INET6) {
                    throw raise(PythonBuiltinClassType.ValueError, "unknown address family %d", addrFamily);
                }

                byte[] bytes = InetAddress.getByName(s).getAddress();

                // we also need to check the size otherwise one could parse an IPv4 address even if
                // he specified AF_INET6 (and vice versa)
                int ip4Len = Inet4Address.getLoopbackAddress().getAddress().length;
                int ip6Len = Inet6Address.getLoopbackAddress().getAddress().length;
                if (addrFamily == AF_INET && bytes.length == ip4Len || addrFamily == AF_INET6 && bytes.length == ip6Len) {
                    return bytes;
                }
            } catch (UnknownHostException e) {
                // fall through
            }
            throw raise(PythonBuiltinClassType.OSError, "illegal IP address string passed to inet_pton");
        }

        @Fallback
        PBytes doError(@SuppressWarnings("unused") Object addrFamily, Object obj) {
            throw raise(PythonBuiltinClassType.TypeError, "inet_aton() argument 1 must be str, not %p", obj);
        }
    }

    @Builtin(name = "inet_ntop", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class InetNtoPNode extends PythonBinaryBuiltinNode {
        @Specialization
        String doGeneric(VirtualFrame frame, int addrFamily, Object obj,
                        @Cached("createToBytes()") BytesNodes.ToBytesNode toBytesNode) {
            return ntoa(addrFamily, toBytesNode.execute(frame, obj));
        }

        @TruffleBoundary
        private String ntoa(int addrFamily, byte[] bytes) {
            if (addrFamily != AF_INET && addrFamily != AF_INET6) {
                throw raise(PythonBuiltinClassType.ValueError, "unknown address family %d", addrFamily);
            }
            // we also need to check the size otherwise one could convert an IPv4 address even if
            // he specified AF_INET6 (and vice versa)
            int ip4Len = Inet4Address.getLoopbackAddress().getAddress().length;
            int ip6Len = Inet6Address.getLoopbackAddress().getAddress().length;
            if (addrFamily == AF_INET && bytes.length != ip4Len || addrFamily == AF_INET6 && bytes.length != ip6Len) {
                throw raise(PythonBuiltinClassType.OSError, "packed IP wrong length for inet_ntoa");
            }
            try {
                return InetAddress.getByAddress(bytes).toString();
            } catch (UnknownHostException e) {
                // should not be reached
                throw new IllegalStateException("should not be reached");
            }
        }

        static BytesNodes.ToBytesNode createToBytes() {
            return BytesNodes.ToBytesNode.create(true, PythonBuiltinClassType.TypeError, "a bytes-like object is required, not '%p'");
        }
    }

}
