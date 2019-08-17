/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.socket.PSocket;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.graalvm.nativeimage.ImageInfo;

@CoreFunctions(defineModule = "_socket")
public class SocketModuleBuiltins extends PythonBuiltins {
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

    static protected Map<String, List<Service>> services;
    static protected Map<String, Integer> protocols;

    @TruffleBoundary
    private static Map<String, List<Service>> parseServices() {
        File services_file = new File("/etc/services");
        try {
            BufferedReader br = new BufferedReader(new FileReader(services_file));
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
    private static Map<String, Integer> parseProtocols() {
        File protocols_file = new File("/etc/protocols");
        try {
            BufferedReader br = new BufferedReader(new FileReader(protocols_file));
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
    private static String searchServicesForPort(int port, String protocol) {
        if (services == null) {
            services = parseServices();
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

    private static String[] cleanLine(String line) {
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

    static {
        if (ImageInfo.inImageBuildtimeCode()) {

            services = parseServices();
            protocols = parseProtocols();
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

        @Specialization(guards = {"isNoValue(family)", "isNoValue(type)", "isNoValue(proto)"})
        Object socket(LazyPythonClass cls, @SuppressWarnings("unused") PNone family, @SuppressWarnings("unused") PNone type, @SuppressWarnings("unused") PNone proto, int fileno) {
            return createSocketInternal(cls, -1, -1, -1, fileno);
        }

        @Specialization(guards = {"isNoValue(type)", "isNoValue(proto)", "isNoValue(fileno)"})
        Object socket(LazyPythonClass cls, PInt family, @SuppressWarnings("unused") PNone type, @SuppressWarnings("unused") PNone proto, @SuppressWarnings("unused") PNone fileno) {
            return createSocketInternal(cls, family.intValue(), PSocket.SOCK_STREAM, 0);
        }

        @Specialization(guards = {"isNoValue(type)", "isNoValue(proto)", "isNoValue(fileno)"})
        Object socket(LazyPythonClass cls, int family, @SuppressWarnings("unused") PNone type, @SuppressWarnings("unused") PNone proto, @SuppressWarnings("unused") PNone fileno) {
            return createSocketInternal(cls, family, PSocket.SOCK_STREAM, 0);
        }

        @Specialization(guards = {"isNoValue(proto)", "isNoValue(fileno)"})
        Object socket(LazyPythonClass cls, PInt family, PInt type, @SuppressWarnings("unused") PNone proto, @SuppressWarnings("unused") PNone fileno) {
            return createSocketInternal(cls, family.intValue(), type.intValue(), 0);
        }

        @Specialization(guards = {"isNoValue(proto)", "isNoValue(fileno)"})
        Object socket(LazyPythonClass cls, int family, PInt type, @SuppressWarnings("unused") PNone proto, @SuppressWarnings("unused") PNone fileno) {
            return createSocketInternal(cls, family, type.intValue(), 0);
        }

        @Specialization(guards = {"isNoValue(proto)", "isNoValue(fileno)"})
        Object socket(LazyPythonClass cls, PInt family, int type, @SuppressWarnings("unused") PNone proto, @SuppressWarnings("unused") PNone fileno) {
            return createSocketInternal(cls, family.intValue(), type, 0);
        }

        @Specialization(guards = {"isNoValue(proto)", "isNoValue(fileno)"})
        Object socket(LazyPythonClass cls, int family, int type, @SuppressWarnings("unused") PNone proto, @SuppressWarnings("unused") PNone fileno) {
            return createSocketInternal(cls, family, type, 0);
        }

        @Specialization(guards = {"isNoValue(proto)", "isNoValue(fileno)"})
        Object socket(LazyPythonClass cls, int family, long type, @SuppressWarnings("unused") PNone proto, @SuppressWarnings("unused") PNone fileno) {
            return createSocketInternal(cls, family, ((int) type), 0);
        }

        @Specialization(guards = {"isNoValue(proto)", "isNoValue(fileno)"})
        Object socket(LazyPythonClass cls, PInt family, long type, @SuppressWarnings("unused") PNone proto, @SuppressWarnings("unused") PNone fileno) {
            return createSocketInternal(cls, family.asInt(), ((int) type), 0);
        }

        @Specialization(guards = {"isNoValue(fileno)"})
        Object socket(LazyPythonClass cls, int family, int type, int proto, @SuppressWarnings("unused") PNone fileno) {
            return createSocketInternal(cls, family, type, proto);
        }

        @Specialization(guards = {"isNoValue(fileno)"})
        Object socket(LazyPythonClass cls, PInt family, PInt type, Integer proto, @SuppressWarnings("unused") PNone fileno) {
            return createSocketInternal(cls, family.asInt(), type.asInt(), proto);
        }

        @Specialization()
        Object socket(LazyPythonClass cls, PInt family, PInt type, PInt proto, int fileno) {
            return createSocketInternal(cls, family.intValue(), type.intValue(), proto.intValue(), fileno);
        }

        @Specialization()
        Object socket(LazyPythonClass cls, PInt family, PInt type, int proto, int fileno) {
            return createSocketInternal(cls, family.intValue(), type.intValue(), proto, fileno);
        }

        @Specialization()
        Object socket(LazyPythonClass cls, int family, int type, int proto, int fileno) {
            return createSocketInternal(cls, family, type, proto, fileno);
        }

        @Specialization()
        Object socket(LazyPythonClass cls, long family, long type, int proto, int fileno) {
            return createSocketInternal(cls, ((int) family), ((int) type), proto, fileno);
        }

        private Object createSocketInternal(LazyPythonClass cls, int family, int type, int proto) {
            if (getContext().getEnv().isNativeAccessAllowed()) {
                PSocket newSocket = factory().createSocket(cls, family, type, proto);
                int fd = getContext().getResources().openSocket(newSocket);
                newSocket.setFileno(fd);
                return newSocket;
            } else {
                throw raise(PythonErrorType.OSError, "creating sockets not allowed");
            }
        }

        @TruffleBoundary
        private Object createSocketInternal(LazyPythonClass cls, int family, int type, int proto, int fileno) {
            if (getContext().getEnv().isNativeAccessAllowed()) {
                PSocket oldSocket = getContext().getResources().getSocket(fileno);
                if (oldSocket == null) {
                    throw raise(PythonErrorType.OSError, "bad filedescriptor");
                }
                PSocket newSocket = factory().createSocket(cls, family == -1 ? oldSocket.getFamily() : family, type == -1 ? oldSocket.getType() : type, proto == -1 ? oldSocket.getProto() : proto, fileno);
                if (oldSocket.getSocket() != null) {
                    newSocket.setSocket(oldSocket.getSocket());
                } else if (oldSocket.getServerSocket() != null) {
                    newSocket.setServerSocket(oldSocket.getServerSocket());
                }
                getContext().getResources().reopenSocket(newSocket, fileno);
                return newSocket;
            } else {
                throw raise(PythonErrorType.OSError, "creating sockets not allowed");
            }
        }
    }

    @Builtin(name = "gethostname", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class GetHostnameNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        String doGeneric() {
            try {
                return InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException e) {
                throw raise(PythonBuiltinClassType.OSError);
            }
        }
    }

    @Builtin(name = "gethostbyaddr", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class GetHostByAddrNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        PTuple doGeneric(PString ip_address) {
            return doGeneric(ip_address.getValue());
        }

        @Specialization
        @TruffleBoundary
        PTuple doGeneric(String ip_address) {
            try {
                InetAddress[] adresses = InetAddress.getAllByName(ip_address);
                String hostname = null;
                Object[] strAdresses = new Object[adresses.length];
                for (int i = 0; i < adresses.length; i++) {
                    if (hostname == null) {
                        hostname = adresses[i].getCanonicalHostName();
                    }
                    strAdresses[i] = adresses[i].getHostAddress();
                }
                PList pAdresses = factory().createList(strAdresses);
                return factory().createTuple(new Object[]{hostname, factory().createList(), pAdresses});
            } catch (UnknownHostException e) {
                throw raise(PythonBuiltinClassType.OSError);
            }
        }
    }

    @Builtin(name = "gethostbyname", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class GetHostByNameNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        Object getHostByName(String name) {
            try {
                InetAddress[] adresses = InetAddress.getAllByName(name);

                if (adresses.length == 0) {
                    return PNone.NO_VALUE;
                }

                return factory().createString(adresses[0].getHostAddress());
            } catch (UnknownHostException e) {
                throw raise(PythonBuiltinClassType.OSError);
            }
        }
    }

    @Builtin(name = "getservbyname", parameterNames = {"servicename", "protocolname"})
    @GenerateNodeFactory
    public abstract static class GetServByNameNode extends PythonBuiltinNode {
        @Specialization(guards = {"isNoValue(protocolName)"})
        @TruffleBoundary
        Object getServByName(String serviceName, @SuppressWarnings("unused") PNone protocolName) {
            if (services == null) {
                services = parseServices();
            }

            List<Service> portsForService = services.get(serviceName);

            if (portsForService.size() == 0) {
                throw raise(PythonBuiltinClassType.OSError);
            } else {
                return factory().createInt(portsForService.get(0).port);
            }
        }

        @Specialization
        @TruffleBoundary
        Object getServByName(String serviceName, String protocolName) {
            if (services == null) {
                services = parseServices();
            }

            for (Service service : services.get(serviceName)) {
                if (service.protocol.equals(protocolName)) {
                    return factory().createInt(service.port);
                }
            }

            throw raise(PythonBuiltinClassType.OSError);
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
        @TruffleBoundary
        Object getServByPort(int port, @SuppressWarnings("unused") PNone protocolName) {
            return getServByPort(port, (String) null);
        }

        @Specialization
        @TruffleBoundary
        Object getServByPort(int port, String protocolName) {
            if (port < 0 || port > 65535) {
                throw raise(PythonBuiltinClassType.OverflowError);
            }
            String service = searchServicesForPort(port, protocolName);
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
        Object getNameInfo(PTuple sockaddr, PInt flags) {
            return getNameInfo(sockaddr, flags.intValue());
        }

        @Specialization
        @TruffleBoundary
        Object getNameInfo(PTuple sockaddr, int flags) {
            SequenceStorage addr = sockaddr.getSequenceStorage();
            if (addr.length() != 2 && addr.length() != 4) {
                throw raise(PythonBuiltinClassType.OSError);
            }
            String address = (String) addr.getItemNormalized(0);
            int port = (int) addr.getItemNormalized(1);

            if ((flags & PSocket.NI_NUMERICHOST) != PSocket.NI_NUMERICHOST) {
                try {
                    InetAddress inetAddress = InetAddress.getByName(address);
                    address = inetAddress.getHostName();
                } catch (UnknownHostException e) {
                    throw raise(PythonBuiltinClassType.OSError);
                }
            }

            String portServ = String.valueOf(port);
            if ((flags & PSocket.NI_NUMERICSERV) != PSocket.NI_NUMERICSERV) {
                portServ = searchServicesForPort(port, null);
                if (portServ == null) {
                    throw raise(PythonBuiltinClassType.OSError, "port/proto not found");
                }
            }

            return factory().createTuple(new Object[]{address, portServ});
        }
    }

    @Builtin(name = "getaddrinfo", parameterNames = {"host", "port", "family", "type", "proto", "flags"})
    @GenerateNodeFactory
    public abstract static class GetAddrInfoNode extends PythonBuiltinNode {
        @Specialization
        Object getAddrInfo(String host, int port, int family, int type, int proto, PInt flags) {
            return getAddrInfo(host, port, family, type, proto, flags.asInt());
        }

        @Specialization
        Object getAddrInfo(String host, Integer port, PInt family, PInt type, Integer proto, Integer flags) {
            return getAddrInfo(host, port, family.asInt(), type.asInt(), proto, flags);
        }

        @Specialization
        Object getAddrInfo(String host, String port, int family, int type, int proto, PInt flags) {
            return getAddrInfo(host, port, family, type, proto, flags.asInt());
        }

        @Specialization
        @TruffleBoundary
        Object getAddrInfo(@SuppressWarnings("unused") PNone host, Integer port, PInt family, PInt type, Integer proto, PInt flags) {
            return getAddrInfo(host, port, family.intValue(), type.intValue(), proto, flags.intValue());
        }

        @Specialization
        @TruffleBoundary
        Object getAddrInfo(@SuppressWarnings("unused") PNone host, String port, int family, int type, int proto, int flags) {
            return getAddrInfo("localhost", port, family, type, proto, flags);
        }

        @Specialization
        @TruffleBoundary
        Object getAddrInfo(@SuppressWarnings("unused") PNone host, int port, int family, int type, int proto, int flags) {
            return getAddrInfo("localhost", port, family, type, proto, flags);
        }

        @Specialization
        @TruffleBoundary
        Object getAddrInfo(String host, @SuppressWarnings("unused") PNone port, PInt family, Integer type, Integer proto, Integer flags) {
            return getAddrInfo(host, port, family.intValue(), type, proto, flags);
        }

        @Specialization
        @TruffleBoundary
        Object getAddrInfo(String host, @SuppressWarnings("unused") PNone port, Integer family, Integer type, Integer proto, PInt flags) {
            return getAddrInfo(host, port, family, type, proto, flags.intValue());
        }

        @Specialization
        @TruffleBoundary
        Object getAddrInfo(String host, @SuppressWarnings("unused") PNone port, Integer family, PInt type, Integer proto, Integer flags) {
            return getAddrInfo(host, port, family, type.intValue(), proto, flags);
        }

        @Specialization
        @TruffleBoundary
        Object getAddrInfo(String host, @SuppressWarnings("unused") PNone port, int family, int type, int proto, int flags) {
            List<Service> serviceList = new LinkedList<>();

            InetAddress[] adresses = resolveHost(host);
            return mergeAdressesAndServices(adresses, serviceList, family, type, proto, flags);
        }

        @Specialization
        @TruffleBoundary
        Object getAddrInfo(String host, int port, int family, int type, int proto, int flags) {
            InetAddress[] adresses = resolveHost(host);
            List<Service> serviceList = new ArrayList<>();
            serviceList.add(new Service(port, "tcp"));
            serviceList.add(new Service(port, "udp"));
            return mergeAdressesAndServices(adresses, serviceList, family, type, proto, flags);
        }

        @Specialization
        @TruffleBoundary
        Object getAddrInfo(String host, String port, int family, int type, int proto, int flags) {
            if (services == null) {
                services = parseServices();
            }
            List<Service> serviceList = services.get(port);

            InetAddress[] adresses = resolveHost(host);
            return mergeAdressesAndServices(adresses, serviceList, family, type, proto, flags);
        }

        @TruffleBoundary
        private Object mergeAdressesAndServices(InetAddress[] adresses, List<Service> serviceList, int family, int type, int proto, int flags) {
            if (protocols == null) {
                protocols = parseProtocols();
            }
            List<Object> addressTuples = new ArrayList<>();

            for (InetAddress addr : adresses) {
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

        InetAddress[] resolveHost(String host) {
            try {
                return InetAddress.getAllByName(host);
            } catch (UnknownHostException e) {
                throw raise(PythonBuiltinClassType.OSError);
            }
        }
    }

    @Builtin(name = "close", minNumOfPositionalArgs = 1, parameterNames = {"fd"})
    @GenerateNodeFactory
    public abstract static class moduleCloseNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        Object close(@SuppressWarnings("unused") PNone fd) {
            throw raise(PythonBuiltinClassType.TypeError);
        }

        @Specialization
        @TruffleBoundary
        Object close(int fd) {
            if (fd < 0) {
                throw raise(PythonBuiltinClassType.OSError, "Bad file descriptor");
            }

            PSocket socket = getContext().getResources().getSocket(fd);

            if (socket == null) {
                throw raise(PythonBuiltinClassType.OSError, "Bad file descriptor");
            }

            if (socket.getSocket() != null) {
                if (!socket.getSocket().isOpen()) {
                    throw raise(PythonBuiltinClassType.OSError, "Bad file descriptor");
                }

                try {
                    socket.getSocket().close();
                } catch (IOException e) {
                    throw raise(PythonBuiltinClassType.OSError, "Bad file descriptor");
                }
            }
            else if (socket.getServerSocket() != null) {
                if (!socket.getServerSocket().isOpen()) {
                    throw raise(PythonBuiltinClassType.OSError, "Bad file descriptor");
                }

                try {
                    socket.getServerSocket().close();
                } catch (IOException e) {
                    throw raise(PythonBuiltinClassType.OSError, "Bad file descriptor");
                }
            }
            getContext().getResources().closeSocket(socket.getFileno());
            return PNone.NONE;
        }
    }
}
