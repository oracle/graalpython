# Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or
# data (collectively the "Software"), free of charge and under any and all
# copyright rights in the Software, and any and all patent rights owned or
# freely licensable by each licensor hereunder covering either (i) the
# unmodified Software as contributed to or provided by such licensor, or (ii)
# the Larger Works (as defined below), to deal in both
#
# (a) the Software, and
#
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
# one is included with the Software each a "Larger Work" to which the Software
# is contributed by such licensors),
#
# without restriction, including without limitation the rights to copy, create
# derivative works of, display, perform, and distribute the Software and make,
# use, sell, offer for sale, import, export, have made, and have sold the
# Software and the Larger Work(s), and to sublicense the foregoing rights on
# either these or other terms.
#
# This license is subject to the following condition:
#
# The above copyright notice and either this complete permission notice or at a
# minimum a reference to the UPL must be included in all copies or substantial
# portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.

# Flag values for getaddrinfo()
AI_PASSIVE = 1  # get address to use bind()
AI_CANONNAME = 2  # fill ai_canonname
AI_NUMERICHOST = 4  # prevent name resolution
AI_MASK = (AI_PASSIVE | AI_CANONNAME | AI_NUMERICHOST)
AI_ALL = 256  # IPv6 and IPv4-mapped (with AI_V4MAPPED)
AI_V4MAPPED_CFG = 512  # accept IPv4-mapped if kernel supports
AI_ADDRCONFIG = 1024  # only if any address is assigned
AI_V4MAPPED = 2048  # accept IPv4-mapped IPv6 address
AI_DEFAULT = (AI_V4MAPPED_CFG | AI_ADDRCONFIG)

SOL_SOCKET = 0
SOL_TCP = 6
SO_REUSEADDR = 0

# Socket types
SOCK_DGRAM = 2
SOCK_STREAM = 1
SOCK_RAW = 3
SOCK_RDM = 4
SOCK_SEQPACKET = 5

# Flag values for getnameinfo()
NI_NOFQDN = 1
NI_NUMERICHOST = 2
NI_NAMEREQD = 4
NI_NUMERICSERV = 8
NI_DGRAM = 10

# Protocol types
IPPROTO_IP = 0
IPPROTO_HOPOPTS = 0
IPPROTO_ICMP = 1
IPPROTO_IGMP = 2
IPPROTO_GGP = 3
IPPROTO_IPV4 = 4
IPPROTO_IPIP = IPPROTO_IPV4
IPPROTO_TCP = 6
IPPROTO_EGP = 8
IPPROTO_PUP = 12
IPPROTO_UDP = 17
IPPROTO_IDP = 22
IPPROTO_TP = 29
IPPROTO_XTP = 36
IPPROTO_ROUTING = 43
IPPROTO_FRAGMENT = 44
IPPROTO_RSVP = 46
IPPROTO_GRE = 47
IPPROTO_ESP = 50
IPPROTO_AH = 51
IPPROTO_NONE = 59
IPPROTO_DSTOPTS = 60
IPPROTO_HELLO = 63
IPPROTO_ND = 77
IPPROTO_EON = 80
IPPROTO_PIM = 103
IPPROTO_IPCOMP = 108
IPPROTO_SCTP = 132
IPPROTO_RAW = 255
IPPROTO_MAX = 256

TCP_NODELAY = 1

SHUT_RD = 0
SHUT_WR = 1
SHUT_RDWR = 2

has_ipv6 = False  #: TODO implement me
error = OSError


class timeout(OSError):
    pass


__default_timeout = None


def getdefaulttimeout():
    return __default_timeout


def setdefaulttimeout(timeout):
    global __default_timeout
    __default_timeout = timeout


try:
    _sock = socket()
    SocketType = type(_sock)
    del _sock
except:
    pass
