/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.runtime;

// Auto generated by gen_native_cfg.py at 2024-12-11 11:43:19.772900
// on Windows FrankTheTank 11 10.0.22631 AMD64 Intel64 Family 6 Model 186 Stepping 2, GenuineIntel
class PosixConstantsWin32 {

    private PosixConstantsWin32() {
    }

    static void getConstants(PosixConstants.Registry constants) {
        constants.put("HAVE_FUTIMENS", false);
        constants.put("HAVE_UTIMENSAT", false);
        constants.put("FD_SETSIZE", 64);
        constants.put("PATH_MAX", 260);
        constants.put("L_ctermid", 0);
        constants.put("INET_ADDRSTRLEN", 22);
        constants.put("INET6_ADDRSTRLEN", 65);
        constants.put("_POSIX_HOST_NAME_MAX", 0);
        constants.put("SOL_SOCKET", 65535);
        constants.put("NI_MAXHOST", 1025);
        constants.put("NI_MAXSERV", 32);
        constants.put("AT_FDCWD", 0);
        constants.put("AT_SYMLINK_FOLLOW", 0);
        constants.put("SEEK_SET", 0);
        constants.put("SEEK_CUR", 1);
        constants.put("SEEK_END", 2);
        constants.put("SOMAXCONN", 2147483647);
        constants.put("RUSAGE_SELF", 0);
        constants.put("O_RDONLY", 0x00000000);
        constants.put("O_WRONLY", 0x00000001);
        constants.put("O_RDWR", 0x00000002);
        constants.put("O_CREAT", 0x00000100);
        constants.put("O_EXCL", 0x00000400);
        constants.put("O_TRUNC", 0x00000200);
        constants.put("O_APPEND", 0x00000008);
        constants.put("O_TEMPORARY", 0x00000040);
        constants.put("O_BINARY", 0x00008000);
        constants.put("O_TEXT", 0x00004000);
        constants.put("S_IFMT", 0x0000F000);
        constants.put("S_IFSOCK", 0);
        constants.put("S_IFLNK", 0);
        constants.put("S_IFREG", 0x00008000);
        constants.put("S_IFBLK", 0);
        constants.put("S_IFDIR", 0x00004000);
        constants.put("S_IFCHR", 0x00002000);
        constants.put("S_IFIFO", 0);
        constants.put("MAP_SHARED", 1);
        constants.put("MAP_PRIVATE", 2);
        constants.put("MAP_ANONYMOUS", 4);
        constants.put("PROT_NONE", 0);
        constants.put("PROT_READ", 1);
        constants.put("PROT_WRITE", 2);
        constants.put("PROT_EXEC", 4);
        constants.put("LOCK_SH", 0);
        constants.put("LOCK_EX", 0);
        constants.put("LOCK_NB", 0);
        constants.put("LOCK_UN", 0);
        constants.put("DT_UNKNOWN", 0);
        constants.put("DT_FIFO", 0);
        constants.put("DT_CHR", 0);
        constants.put("DT_DIR", 0);
        constants.put("DT_BLK", 0);
        constants.put("DT_REG", 0);
        constants.put("DT_LNK", 0);
        constants.put("DT_SOCK", 0);
        constants.put("DT_WHT", 0);
        constants.put("WNOHANG", 0);
        constants.put("WUNTRACED", 0);
        constants.put("R_OK", 0);
        constants.put("W_OK", 0);
        constants.put("X_OK", 0);
        constants.put("F_OK", 0);
        constants.put("RTLD_LAZY", 0);
        constants.put("RTLD_NOW", 0);
        constants.put("RTLD_GLOBAL", 0);
        constants.put("RTLD_LOCAL", 0);
        constants.put("AF_UNSPEC", 0);
        constants.put("AF_INET", 2);
        constants.put("AF_INET6", 23);
        constants.put("AF_UNIX", 1);
        constants.put("SOCK_DGRAM", 2);
        constants.put("SOCK_STREAM", 1);
        constants.put("INADDR_ANY", 0x00000000);
        constants.put("INADDR_BROADCAST", 0xFFFFFFFF);
        constants.put("INADDR_NONE", 0xFFFFFFFF);
        constants.put("INADDR_LOOPBACK", 0x7F000001);
        constants.put("INADDR_ALLHOSTS_GROUP", 0);
        constants.put("INADDR_MAX_LOCAL_GROUP", 0);
        constants.put("INADDR_UNSPEC_GROUP", 0);
        constants.put("AI_PASSIVE", 0x00000001);
        constants.put("AI_CANONNAME", 0x00000002);
        constants.put("AI_NUMERICHOST", 0x00000004);
        constants.put("AI_V4MAPPED", 0x00000800);
        constants.put("AI_ALL", 0x00000100);
        constants.put("AI_ADDRCONFIG", 0x00000400);
        constants.put("AI_NUMERICSERV", 0x00000008);
        constants.put("EAI_BADFLAGS", 10022);
        constants.put("EAI_NONAME", 11001);
        constants.put("EAI_AGAIN", 11002);
        constants.put("EAI_FAIL", 11003);
        constants.put("EAI_FAMILY", 10047);
        constants.put("EAI_SOCKTYPE", 10044);
        constants.put("EAI_SERVICE", 10109);
        constants.put("EAI_MEMORY", 8);
        constants.put("EAI_SYSTEM", 0);
        constants.put("EAI_OVERFLOW", 0);
        constants.put("EAI_NODATA", 11001);
        constants.put("EAI_ADDRFAMILY", 0);
        constants.put("NI_NUMERICHOST", 2);
        constants.put("NI_NUMERICSERV", 8);
        constants.put("NI_NOFQDN", 1);
        constants.put("NI_NAMEREQD", 4);
        constants.put("NI_DGRAM", 16);
        constants.put("IPPROTO_IP", 0);
        constants.put("IPPROTO_ICMP", 1);
        constants.put("IPPROTO_IGMP", 2);
        constants.put("IPPROTO_IPIP", 0);
        constants.put("IPPROTO_TCP", 6);
        constants.put("IPPROTO_EGP", 8);
        constants.put("IPPROTO_PUP", 12);
        constants.put("IPPROTO_UDP", 17);
        constants.put("IPPROTO_IDP", 22);
        constants.put("IPPROTO_TP", 0);
        constants.put("IPPROTO_IPV6", 41);
        constants.put("IPPROTO_RSVP", 0);
        constants.put("IPPROTO_GRE", 0);
        constants.put("IPPROTO_ESP", 50);
        constants.put("IPPROTO_AH", 51);
        constants.put("IPPROTO_MTP", 0);
        constants.put("IPPROTO_ENCAP", 0);
        constants.put("IPPROTO_PIM", 103);
        constants.put("IPPROTO_SCTP", 132);
        constants.put("IPPROTO_RAW", 255);
        constants.put("SHUT_RD", 0);
        constants.put("SHUT_WR", 0);
        constants.put("SHUT_RDWR", 0);
        constants.put("SO_DEBUG", 1);
        constants.put("SO_ACCEPTCONN", 2);
        constants.put("SO_REUSEADDR", 4);
        constants.put("SO_EXCLUSIVEADDRUSE", -5);
        constants.put("SO_KEEPALIVE", 8);
        constants.put("SO_DONTROUTE", 16);
        constants.put("SO_BROADCAST", 32);
        constants.put("SO_USELOOPBACK", 64);
        constants.put("SO_LINGER", 128);
        constants.put("SO_OOBINLINE", 256);
        constants.put("SO_REUSEPORT", 0);
        constants.put("SO_SNDBUF", 4097);
        constants.put("SO_RCVBUF", 4098);
        constants.put("SO_SNDLOWAT", 4099);
        constants.put("SO_RCVLOWAT", 4100);
        constants.put("SO_SNDTIMEO", 4101);
        constants.put("SO_RCVTIMEO", 4102);
        constants.put("SO_ERROR", 4103);
        constants.put("SO_TYPE", 4104);
        constants.put("TCP_NODELAY", 1);
        constants.put("TCP_MAXSEG", 4);
        constants.put("TCP_KEEPIDLE", 3);
        constants.put("TCP_KEEPINTVL", 17);
        constants.put("TCP_KEEPCNT", 16);
        constants.put("TCP_FASTOPEN", 15);
        constants.put("IPV6_JOIN_GROUP", 12);
        constants.put("IPV6_LEAVE_GROUP", 13);
        constants.put("IPV6_MULTICAST_HOPS", 10);
        constants.put("IPV6_MULTICAST_IF", 9);
        constants.put("IPV6_MULTICAST_LOOP", 11);
        constants.put("IPV6_UNICAST_HOPS", 4);
        constants.put("IPV6_V6ONLY", 27);
        constants.put("IPV6_CHECKSUM", 26);
        constants.put("IPV6_DONTFRAG", 14);
        constants.put("IPV6_HOPLIMIT", 21);
        constants.put("IPV6_HOPOPTS", 1);
        constants.put("IPV6_PKTINFO", 19);
        constants.put("IPV6_RECVRTHDR", 38);
        constants.put("IPV6_RECVTCLASS", 40);
        constants.put("IPV6_RTHDR", 32);
        constants.put("IPV6_TCLASS", 39);
        constants.put("_SC_ARG_MAX", 0);
        constants.put("_SC_CHILD_MAX", 1);
        constants.put("_SC_LOGIN_NAME_MAX", 2);
        constants.put("_SC_CLK_TCK", 3);
        constants.put("_SC_OPEN_MAX", 4);
        constants.put("_SC_PAGESIZE", 5);
        constants.put("_SC_PAGE_SIZE", 5);
        constants.put("_SC_SEM_NSEMS_MAX", 7);
        constants.put("_SC_PHYS_PAGES", 8);
        constants.put("_SC_NPROCESSORS_CONF", 9);
        constants.put("_SC_NPROCESSORS_ONLN", 9);
    }
}
