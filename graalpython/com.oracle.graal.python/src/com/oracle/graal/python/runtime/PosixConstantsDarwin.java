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

// Auto generated by gen_native_cfg.py at 2023-10-26 12:57:29.030972
// on Darwin ol-mac-mini2-prague.cz.oracle.com 21.6.0 Darwin Kernel Version 21.6.0: Wed Oct  4 23:55:28 PDT 2023; root:xnu-8020.240.18.704.15~1/RELEASE_X86_64 x86_64 i386
class PosixConstantsDarwin {

    private PosixConstantsDarwin() {
    }

    static void getConstants(PosixConstants.Registry constants) {
        constants.put("HAVE_FUTIMENS", false);
        constants.put("HAVE_UTIMENSAT", false);
        constants.put("FD_SETSIZE", 1024);
        constants.put("PATH_MAX", 1024);
        constants.put("L_ctermid", 1024);
        constants.put("INET_ADDRSTRLEN", 16);
        constants.put("INET6_ADDRSTRLEN", 46);
        constants.put("_POSIX_HOST_NAME_MAX", 255);
        constants.put("SOL_SOCKET", 65535);
        constants.put("NI_MAXHOST", 1025);
        constants.put("NI_MAXSERV", 32);
        constants.put("AT_FDCWD", -2);
        constants.put("AT_SYMLINK_FOLLOW", 64);
        constants.put("SEEK_SET", 0);
        constants.put("SEEK_CUR", 1);
        constants.put("SEEK_END", 2);
        constants.put("SEEK_DATA", 4);
        constants.put("SEEK_HOLE", 3);
        constants.put("SOMAXCONN", 128);
        constants.put("PIPE_BUF", 512);
        constants.put("SEM_VALUE_MAX", 32767);
        constants.put("RUSAGE_CHILDREN", -1);
        constants.put("RUSAGE_SELF", 0);
        constants.put("O_ACCMODE", 0x00000003);
        constants.put("O_RDONLY", 0x00000000);
        constants.put("O_WRONLY", 0x00000001);
        constants.put("O_RDWR", 0x00000002);
        constants.put("O_CREAT", 0x00000200);
        constants.put("O_EXCL", 0x00000800);
        constants.put("O_TRUNC", 0x00000400);
        constants.put("O_APPEND", 0x00000008);
        constants.put("O_NONBLOCK", 0x00000004);
        constants.put("O_NOCTTY", 0x00020000);
        constants.put("O_NDELAY", 0x00000004);
        constants.put("O_DSYNC", 0x00400000);
        constants.put("O_CLOEXEC", 0x01000000);
        constants.put("O_SYNC", 0x00000080);
        constants.put("O_DIRECTORY", 0x00100000);
        constants.put("O_SHLOCK", 0x00000010);
        constants.put("O_EXLOCK", 0x00000020);
        constants.put("O_EXEC", 0x40000000);
        constants.put("O_SEARCH", 0x40100000);
        constants.put("S_IFMT", 0x0000F000);
        constants.put("S_IFSOCK", 0x0000C000);
        constants.put("S_IFLNK", 0x0000A000);
        constants.put("S_IFREG", 0x00008000);
        constants.put("S_IFBLK", 0x00006000);
        constants.put("S_IFDIR", 0x00004000);
        constants.put("S_IFCHR", 0x00002000);
        constants.put("S_IFIFO", 0x00001000);
        constants.put("MAP_SHARED", 0x00000001);
        constants.put("MAP_PRIVATE", 0x00000002);
        constants.put("MAP_ANONYMOUS", 0x00001000);
        constants.put("PROT_NONE", 0x00000000);
        constants.put("PROT_READ", 0x00000001);
        constants.put("PROT_WRITE", 0x00000002);
        constants.put("PROT_EXEC", 0x00000004);
        constants.put("LOCK_SH", 0x00000001);
        constants.put("LOCK_EX", 0x00000002);
        constants.put("LOCK_NB", 0x00000004);
        constants.put("LOCK_UN", 0x00000008);
        constants.put("F_RDLCK", 1);
        constants.put("F_WRLCK", 3);
        constants.put("F_UNLCK", 2);
        constants.put("DT_UNKNOWN", 0);
        constants.put("DT_FIFO", 1);
        constants.put("DT_CHR", 2);
        constants.put("DT_DIR", 4);
        constants.put("DT_BLK", 6);
        constants.put("DT_REG", 8);
        constants.put("DT_LNK", 10);
        constants.put("DT_SOCK", 12);
        constants.put("DT_WHT", 14);
        constants.put("WNOHANG", 1);
        constants.put("WUNTRACED", 2);
        constants.put("R_OK", 0x00000004);
        constants.put("W_OK", 0x00000002);
        constants.put("X_OK", 0x00000001);
        constants.put("F_OK", 0x00000000);
        constants.put("EX_OK", 0);
        constants.put("EX_USAGE", 64);
        constants.put("EX_DATAERR", 65);
        constants.put("EX_NOINPUT", 66);
        constants.put("EX_NOUSER", 67);
        constants.put("EX_NOHOST", 68);
        constants.put("EX_UNAVAILABLE", 69);
        constants.put("EX_SOFTWARE", 70);
        constants.put("EX_OSERR", 71);
        constants.put("EX_OSFILE", 72);
        constants.put("EX_CANTCREAT", 73);
        constants.put("EX_IOERR", 74);
        constants.put("EX_TEMPFAIL", 75);
        constants.put("EX_PROTOCOL", 76);
        constants.put("EX_NOPERM", 77);
        constants.put("EX_CONFIG", 78);
        constants.put("RTLD_LAZY", 0x00000001);
        constants.put("RTLD_NOW", 0x00000002);
        constants.put("RTLD_GLOBAL", 0x00000008);
        constants.put("RTLD_LOCAL", 0x00000004);
        constants.put("AF_UNSPEC", 0);
        constants.put("AF_INET", 2);
        constants.put("AF_INET6", 30);
        constants.put("AF_UNIX", 1);
        constants.put("SOCK_DGRAM", 2);
        constants.put("SOCK_STREAM", 1);
        constants.put("INADDR_ANY", 0x00000000);
        constants.put("INADDR_BROADCAST", 0xFFFFFFFF);
        constants.put("INADDR_NONE", 0xFFFFFFFF);
        constants.put("INADDR_LOOPBACK", 0x7F000001);
        constants.put("INADDR_ALLHOSTS_GROUP", 0xE0000001);
        constants.put("INADDR_MAX_LOCAL_GROUP", 0xE00000FF);
        constants.put("INADDR_UNSPEC_GROUP", 0xE0000000);
        constants.put("AI_PASSIVE", 0x00000001);
        constants.put("AI_CANONNAME", 0x00000002);
        constants.put("AI_NUMERICHOST", 0x00000004);
        constants.put("AI_V4MAPPED", 0x00000800);
        constants.put("AI_ALL", 0x00000100);
        constants.put("AI_ADDRCONFIG", 0x00000400);
        constants.put("AI_NUMERICSERV", 0x00001000);
        constants.put("EAI_BADFLAGS", 3);
        constants.put("EAI_NONAME", 8);
        constants.put("EAI_AGAIN", 2);
        constants.put("EAI_FAIL", 4);
        constants.put("EAI_FAMILY", 5);
        constants.put("EAI_SOCKTYPE", 10);
        constants.put("EAI_SERVICE", 9);
        constants.put("EAI_MEMORY", 6);
        constants.put("EAI_SYSTEM", 11);
        constants.put("EAI_OVERFLOW", 14);
        constants.put("EAI_NODATA", 7);
        constants.put("EAI_ADDRFAMILY", 1);
        constants.put("NI_NUMERICHOST", 2);
        constants.put("NI_NUMERICSERV", 8);
        constants.put("NI_NOFQDN", 1);
        constants.put("NI_NAMEREQD", 4);
        constants.put("NI_DGRAM", 16);
        constants.put("IPPROTO_IP", 0);
        constants.put("IPPROTO_ICMP", 1);
        constants.put("IPPROTO_IGMP", 2);
        constants.put("IPPROTO_IPIP", 4);
        constants.put("IPPROTO_TCP", 6);
        constants.put("IPPROTO_EGP", 8);
        constants.put("IPPROTO_PUP", 12);
        constants.put("IPPROTO_UDP", 17);
        constants.put("IPPROTO_IDP", 22);
        constants.put("IPPROTO_TP", 29);
        constants.put("IPPROTO_IPV6", 41);
        constants.put("IPPROTO_RSVP", 46);
        constants.put("IPPROTO_GRE", 47);
        constants.put("IPPROTO_ESP", 50);
        constants.put("IPPROTO_AH", 51);
        constants.put("IPPROTO_MTP", 92);
        constants.put("IPPROTO_ENCAP", 98);
        constants.put("IPPROTO_PIM", 103);
        constants.put("IPPROTO_SCTP", 132);
        constants.put("IPPROTO_RAW", 255);
        constants.put("SHUT_RD", 0);
        constants.put("SHUT_WR", 1);
        constants.put("SHUT_RDWR", 2);
        constants.put("SO_DEBUG", 1);
        constants.put("SO_ACCEPTCONN", 2);
        constants.put("SO_REUSEADDR", 4);
        constants.put("SO_KEEPALIVE", 8);
        constants.put("SO_DONTROUTE", 16);
        constants.put("SO_BROADCAST", 32);
        constants.put("SO_USELOOPBACK", 64);
        constants.put("SO_LINGER", 128);
        constants.put("SO_OOBINLINE", 256);
        constants.put("SO_REUSEPORT", 512);
        constants.put("SO_SNDBUF", 4097);
        constants.put("SO_RCVBUF", 4098);
        constants.put("SO_SNDLOWAT", 4099);
        constants.put("SO_RCVLOWAT", 4100);
        constants.put("SO_SNDTIMEO", 4101);
        constants.put("SO_RCVTIMEO", 4102);
        constants.put("SO_ERROR", 4103);
        constants.put("SO_TYPE", 4104);
        constants.put("TCP_NODELAY", 1);
        constants.put("TCP_MAXSEG", 2);
        constants.put("TCP_KEEPINTVL", 257);
        constants.put("TCP_KEEPCNT", 258);
        constants.put("TCP_FASTOPEN", 261);
        constants.put("TCP_NOTSENT_LOWAT", 513);
        constants.put("IPV6_JOIN_GROUP", 12);
        constants.put("IPV6_LEAVE_GROUP", 13);
        constants.put("IPV6_MULTICAST_HOPS", 10);
        constants.put("IPV6_MULTICAST_IF", 9);
        constants.put("IPV6_MULTICAST_LOOP", 11);
        constants.put("IPV6_UNICAST_HOPS", 4);
        constants.put("IPV6_V6ONLY", 27);
        constants.put("IPV6_CHECKSUM", 26);
        constants.put("IPV6_RECVTCLASS", 35);
        constants.put("IPV6_RTHDR_TYPE_0", 0);
        constants.put("IPV6_TCLASS", 36);
        constants.put("_SC_ARG_MAX", 1);
        constants.put("_SC_CHILD_MAX", 2);
        constants.put("_SC_LOGIN_NAME_MAX", 73);
        constants.put("_SC_NGROUPS_MAX", 4);
        constants.put("_SC_CLK_TCK", 3);
        constants.put("_SC_OPEN_MAX", 5);
        constants.put("_SC_PAGESIZE", 29);
        constants.put("_SC_PAGE_SIZE", 29);
        constants.put("_SC_RE_DUP_MAX", 16);
        constants.put("_SC_STREAM_MAX", 26);
        constants.put("_SC_TTY_NAME_MAX", 101);
        constants.put("_SC_TZNAME_MAX", 27);
        constants.put("_SC_VERSION", 8);
        constants.put("_SC_SEM_NSEMS_MAX", 49);
        constants.put("_SC_PHYS_PAGES", 200);
        constants.put("_SC_NPROCESSORS_CONF", 83);
        constants.put("_SC_NPROCESSORS_ONLN", 84);
    }
}
