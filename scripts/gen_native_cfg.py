# Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

"""
Generates Java source files with the values of native (mainly posix) constants.

To update the platform independent source files, execute:

python3 gen_native_cfg.py --common

This will modify the files in-place, replacing anything between '// start generated' and '//end generated'.

To generate platform-specific file, execute the script without arguments. This will generate a C source file, compile
it, execute and write the generated Java file to stdout.
In-place modification is not supported as this is meant to be executed remotely, for example:

docker run -i ol6_python3 python3 -u - <gen_native_cfg.py >../graalpython/com.oracle.graal.python/src/com/oracle/graal/python/runtime/PosixConstantsLinux.java
ssh darwin 'cd /tmp && /usr/local/bin/python3 -u -' <gen_native_cfg.py >../graalpython/com.oracle.graal.python/src/com/oracle/graal/python/runtime/PosixConstantsDarwin.java
"""

import argparse
import datetime
import os
import subprocess
import sys
import platform as plat
import re
import textwrap
import contextlib
from collections import namedtuple
from pathlib import Path

DIR = Path(__file__).parent.parent

includes = '''
#ifndef _MSC_VER
# include <dirent.h>
# include <dlfcn.h>
# include <netdb.h>
# include <netinet/in.h>
# include <netinet/tcp.h>
# include <sys/mman.h>
# include <sys/resource.h>
# include <sys/select.h>
# include <sys/socket.h>
# include <sys/un.h>
# include <sys/unistd.h>
# include <sys/utsname.h>
# include <sys/wait.h>
# include <sysexits.h>
# include <semaphore.h>
#else
# include <winsock2.h>
# include <ws2tcpip.h>
# include <windows.h>
# include <sys/stat.h>
# ifndef PATH_MAX
#  define PATH_MAX MAX_PATH
# endif
#endif
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <stddef.h>
#include <stdio.h>
#include <sys/types.h>
'''

type_defs = {
    'i': ('Int', '%d'),
    'x': ('Int', '0x%08lX'),
    'b': ('Boolean', None)
}

# Explanation of the format:
# Names in square brackets specify an option group name for all following entries.
# Normal entries:
# Column #1 - fallback specifier:
#   empty - mandatory constant
#   `*` - optional constant
#   `0`...`9` - if not defined, it will default to the number given (0 to 9)
#   `u` - if not defined, it will use the constant prefixed with an underscore
# Column #2 - type specifier:
#   `i` - int
#   `x` - int, appearing in hexadecimal in the generated source
#   `b` - boolean, will be true if the plaform is Linux (WTF?)
# Column #3 - the name of the constant
constant_defs = '''
  b HAVE_FUTIMENS
  b HAVE_UTIMENSAT

  i FD_SETSIZE
  i PATH_MAX
0 i L_ctermid
  i INET_ADDRSTRLEN
  i INET6_ADDRSTRLEN
* i HOST_NAME_MAX
0 i _POSIX_HOST_NAME_MAX
  i SOL_SOCKET
  i NI_MAXHOST
  i NI_MAXSERV

0 i AT_FDCWD
0 i AT_SYMLINK_FOLLOW

  i SEEK_SET
  i SEEK_CUR
  i SEEK_END
* i SEEK_DATA
* i SEEK_HOLE

  i SOMAXCONN

* i PIPE_BUF

* i SEM_VALUE_MAX

* i RUSAGE_CHILDREN
0 i RUSAGE_SELF
* i RUSAGE_THREAD

[openFlags]
* x O_ACCMODE
  x O_RDONLY
  x O_WRONLY
  x O_RDWR
  x O_CREAT
  x O_EXCL
  x O_TRUNC
  x O_APPEND
* x O_NONBLOCK
* x O_NOCTTY
* x O_NDELAY
* x O_DSYNC
* x O_CLOEXEC
* x O_SYNC
* x O_DIRECT
* x O_RSYNC
* x O_TMPFILE
* x O_TEMPORARY
* x O_DIRECTORY
* x O_BINARY
* x O_TEXT
* x O_XATTR
* x O_LARGEFILE
* x O_SHLOCK
* x O_EXLOCK
* x O_EXEC
* x O_SEARCH
* x O_PATH
* x O_TTY_INIT

[fileType]
u x S_IFMT
0 x S_IFSOCK
0 x S_IFLNK
u x S_IFREG
0 x S_IFBLK
u x S_IFDIR
u x S_IFCHR
0 x S_IFIFO

[mmapFlags]
1 x MAP_SHARED
2 x MAP_PRIVATE
4 x MAP_ANONYMOUS
* x MAP_DENYWRITE
* x MAP_EXECUTABLE

[mmapProtection]
0 x PROT_NONE
1 x PROT_READ
2 x PROT_WRITE
4 x PROT_EXEC

[flockOperation]
0 x LOCK_SH
0 x LOCK_EX
0 x LOCK_NB
0 x LOCK_UN

[flockType]
* i F_RDLCK
* i F_WRLCK
* i F_UNLCK

[direntType]
0 i DT_UNKNOWN
0 i DT_FIFO
0 i DT_CHR
0 i DT_DIR
0 i DT_BLK
0 i DT_REG
0 i DT_LNK
0 i DT_SOCK
0 i DT_WHT

[waitOptions]
0 i WNOHANG
0 i WUNTRACED

[accessMode]
0 x R_OK
0 x W_OK
0 x X_OK
0 x F_OK

[exitStatus]
* i EX_OK
* i EX_USAGE
* i EX_DATAERR
* i EX_NOINPUT
* i EX_NOUSER
* i EX_NOHOST
* i EX_UNAVAILABLE
* i EX_SOFTWARE
* i EX_OSERR
* i EX_OSFILE
* i EX_CANTCREAT
* i EX_IOERR
* i EX_TEMPFAIL
* i EX_PROTOCOL
* i EX_NOPERM
* i EX_CONFIG
* i EX_NOTFOUND

[rtld]
0 x RTLD_LAZY
0 x RTLD_NOW
0 x RTLD_GLOBAL
0 x RTLD_LOCAL

[socketFamily]
  i AF_UNSPEC
  i AF_INET
  i AF_INET6
* i AF_PACKET
  i AF_UNIX

[socketType]
  i SOCK_DGRAM
  i SOCK_STREAM

[ip4Address]
  x INADDR_ANY
  x INADDR_BROADCAST
  x INADDR_NONE
  x INADDR_LOOPBACK
0 x INADDR_ALLHOSTS_GROUP
0 x INADDR_MAX_LOCAL_GROUP
0 x INADDR_UNSPEC_GROUP

[gaiFlags]
  x AI_PASSIVE
  x AI_CANONNAME
  x AI_NUMERICHOST
  x AI_V4MAPPED
  x AI_ALL
  x AI_ADDRCONFIG
* x AI_IDN
* x AI_CANONIDN
  x AI_NUMERICSERV

[gaiErrors]
  i EAI_BADFLAGS
  i EAI_NONAME
  i EAI_AGAIN
  i EAI_FAIL
  i EAI_FAMILY
  i EAI_SOCKTYPE
  i EAI_SERVICE
  i EAI_MEMORY
0 i EAI_SYSTEM
0 i EAI_OVERFLOW
  i EAI_NODATA
0 i EAI_ADDRFAMILY
* i EAI_INPROGRESS
* i EAI_CANCELED
* i EAI_NOTCANCELED
* i EAI_ALLDONE
* i EAI_INTR
* i EAI_IDN_ENCODE

[niFlags]
  i NI_NUMERICHOST
  i NI_NUMERICSERV
  i NI_NOFQDN
  i NI_NAMEREQD
  i NI_DGRAM
* i NI_IDN

[ipProto]
  i IPPROTO_IP
  i IPPROTO_ICMP
  i IPPROTO_IGMP
0 i IPPROTO_IPIP
  i IPPROTO_TCP
  i IPPROTO_EGP
  i IPPROTO_PUP
  i IPPROTO_UDP
  i IPPROTO_IDP
0 i IPPROTO_TP
  i IPPROTO_IPV6
0 i IPPROTO_RSVP
0 i IPPROTO_GRE
  i IPPROTO_ESP
  i IPPROTO_AH
0 i IPPROTO_MTP
0 i IPPROTO_ENCAP
  i IPPROTO_PIM
  i IPPROTO_SCTP
  i IPPROTO_RAW

[shutdownHow]
0 i SHUT_RD
0 i SHUT_WR
0 i SHUT_RDWR

[socketOptions]
  i SO_DEBUG
  i SO_ACCEPTCONN
  i SO_REUSEADDR
* i SO_EXCLUSIVEADDRUSE
  i SO_KEEPALIVE
  i SO_DONTROUTE
  i SO_BROADCAST
* i SO_USELOOPBACK
  i SO_LINGER
  i SO_OOBINLINE
0 i SO_REUSEPORT
  i SO_SNDBUF
  i SO_RCVBUF
  i SO_SNDLOWAT
  i SO_RCVLOWAT
  i SO_SNDTIMEO
  i SO_RCVTIMEO
  i SO_ERROR
  i SO_TYPE
* i SO_SETFIB
* i SO_PASSCRED
* i SO_PEERCRED
* i SO_PASSSEC
* i SO_PEERSEC
* i SO_BINDTODEVICE
* i SO_PRIORITY
* i SO_MARK
* i SO_DOMAIN
* i SO_PROTOCOL

[tcpOptions]
* i TCP_NODELAY
* i TCP_MAXSEG
* i TCP_CORK
* i TCP_KEEPIDLE
* i TCP_KEEPINTVL
* i TCP_KEEPCNT
* i TCP_SYNCNT
* i TCP_LINGER2
* i TCP_DEFER_ACCEPT
* i TCP_WINDOW_CLAMP
* i TCP_INFO
* i TCP_QUICKACK
* i TCP_FASTOPEN
* i TCP_CONGESTION
* i TCP_USER_TIMEOUT
* i TCP_NOTSENT_LOWAT

[ipv6Options]
* i IPV6_JOIN_GROUP
* i IPV6_LEAVE_GROUP
* i IPV6_MULTICAST_HOPS
* i IPV6_MULTICAST_IF
* i IPV6_MULTICAST_LOOP
* i IPV6_UNICAST_HOPS
* i IPV6_V6ONLY
* i IPV6_CHECKSUM
* i IPV6_DONTFRAG
* i IPV6_DSTOPTS
* i IPV6_HOPLIMIT
* i IPV6_HOPOPTS
* i IPV6_NEXTHOP
* i IPV6_PATHMTU
* i IPV6_PKTINFO
* i IPV6_RECVDSTOPTS
* i IPV6_RECVHOPLIMIT
* i IPV6_RECVHOPOPTS
* i IPV6_RECVPKTINFO
* i IPV6_RECVRTHDR
* i IPV6_RECVTCLASS
* i IPV6_RTHDR
* i IPV6_RTHDRDSTOPTS
* i IPV6_RTHDR_TYPE_0
* i IPV6_RECVPATHMTU
* i IPV6_TCLASS
* i IPV6_USE_MIN_MTU
'''

layout_defs = '''
[struct sockaddr]
  sa_family

[struct sockaddr_storage]

[struct sockaddr_in]
  sin_family
  sin_port
  sin_addr

[struct sockaddr_in6]
  sin6_family
  sin6_port
  sin6_flowinfo
  sin6_addr
  sin6_scope_id

[struct in_addr]
  s_addr

[struct in6_addr]
  s6_addr

[struct sockaddr_un] u
  sun_family
  sun_path
'''

java_copyright = '''/*
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
 */'''

platform_template = '''{java_copyright}
package com.oracle.graal.python.runtime;

// Auto generated by {script_name} at {timestamp}
// on {uname}
class PosixConstants{platform} {{

    private PosixConstants{platform}() {{
    }}

    static void getConstants(PosixConstants.Registry constants) {{
{output}
    }}
}}'''

Constant = namedtuple('Constant', ['name', 'optional', 'type', 'format'])
Struct = namedtuple('Struct', ['name', 'members', 'unix_only'])

c_source_file = 'gen_native_cfg.c'
c_executable_file = 'gen_native_cfg'


def parse_defs():
    regex = re.compile(r'\[(\w+)\]|(\*|u|\d?)\s*(\w+)\s+(\w+)')
    current_group = []
    groups = {}
    constants = []
    for line in constant_defs.splitlines():
        line = line.strip()
        if not line:
            current_group = []
            continue
        m = regex.fullmatch(line)
        if not m:
            raise ValueError(f'Invalid constant definition {line!r}')
        if m.group(1):
            current_group = []
            groups[m.group(1)] = current_group
        else:
            d = type_defs[m.group(3)]
            optional = m.group(2) == '*' or (m.group(2) and sys.platform.capitalize() == 'Win32')
            if sys.platform.capitalize() == 'Win32':
                optional = m.group(2)
            c = Constant(m.group(4), optional, *d)
            current_group.append(c)
            constants.append(c)

    regex = re.compile(r'(?:\[(.*?)\]\s*(u?))|(.*)')
    layouts = []
    current_struct = None
    for line in layout_defs.splitlines():
        line = line.strip()
        if not line:
            continue
        m = regex.fullmatch(line)
        if not m:
            raise ValueError(f'Invalid layout definition {line!r}')
        if m.group(1):
            current_struct = []
            layouts.append(Struct(m.group(1), current_struct, m.group(2)))
        else:
            current_struct.append(m.group(3))

    return constants, groups, layouts


def delete_if_exists(filename):
    try:
        os.unlink(filename)
    except FileNotFoundError:
        pass


def to_id(name):
    return name.upper().replace(' ', '_')


def sizeof_name(struct_name, member=None):
    s = f'SIZEOF_{to_id(struct_name)}'
    if member:
        s += '_' + to_id(member)
    return s


def offsetof_name(struct_name, member_name):
    return f'OFFSETOF_{to_id(struct_name)}_{to_id(member_name)}'


def generate_platform():
    constants = parse_defs()[0]
    platform = sys.platform.capitalize()
    if platform not in ('Linux', 'Darwin', 'Win32'):
        raise ValueError(f'Unsupported platform: {platform}')
    script_name = os.path.basename(__file__)
    if script_name == '<stdin>':
        script_name = 'gen_native_cfg.py'

    with open(c_source_file, 'w') as f:
        f.write(includes)
        f.write('\nint main() {\n')
        for c in constants:
            if c.format is None:
                val = 'true' if platform == 'Linux' else 'false'
                f.write(f'    printf("        constants.put(\\"{c.name}\\", {val});\\n");\n')
                continue
            if c.optional:
                f.write(f'#ifdef {c.name}\n')
            f.write(f'    printf("        constants.put(\\"{c.name}\\", {c.format});\\n", {c.name});\n')
            if c.optional:
                if c.optional not in ["*", True]:
                    f.write('#else\n')
                    if c.optional == "u":
                        f.write(f'    printf("        constants.put(\\"{c.name}\\", {c.format});\\n", _{c.name});\n')
                    elif ord("0") <= ord(c.optional) <= ord("9"):
                        f.write(f'    printf("        constants.put(\\"{c.name}\\", {c.optional});\\n");\n')
                    else:
                        raise ValueError(f"Unsupported fallback specifier for {c.name}: {c.optional}")
                f.write(f'#endif\n')

        f.write('    return 0;\n}\n')

    flags = '-D_GNU_SOURCE' if platform == 'Linux' else ''
    flags += '' if platform == 'Win32' else ' -Wall -Werror -Wno-format '
    cc = os.environ.get('CC', 'cl' if platform == 'Win32' else 'cc')
    subprocess.run(f'{cc} {flags} -o {c_executable_file} {c_source_file}', shell=True, check=True)

    output = subprocess.run(f'./{c_executable_file}', shell=False, check=True, stdout=subprocess.PIPE, universal_newlines=True).stdout[:-1]
    uname = " ".join(tuple(plat.uname()))

    print(platform_template.format(java_copyright=java_copyright, script_name=script_name, timestamp=datetime.datetime.now(), uname=uname, platform=platform, output=output))


def load_existing_parts(path):
    with open(path, 'r') as f:
        header = []
        footer = []
        dst = header
        for line in f.readlines():
            if '// end generated' in line:
                dst = footer
            dst.append(line)
            if '// start generated' in line:
                dst = []
    return header, footer


@contextlib.contextmanager
def open_generated_segment(path):
    with open(path, 'r') as f:
        header = []
        footer = []
        dst = header
        for line in f.readlines():
            if '// end generated' in line:
                dst = footer
            dst.append(line)
            if '// start generated' in line:
                dst = []
    with open(path, 'w') as f:
        f.writelines(header)
        yield f
        f.writelines(footer)


def generate_common():
    constants, groups, layouts = parse_defs()
    generate_posix_constants(constants, groups)
    generate_native_constants(layouts)


def generate_posix_constants(constants, groups):
    posix_constants = DIR / 'graalpython/com.oracle.graal.python/src/com/oracle/graal/python/runtime/PosixConstants.java'

    decls = []
    defs = []

    def add_constant(opt, typ, name):
        prefix = 'Optional' if opt == "*" else 'Mandatory'
        decls.append(f'    public static final {prefix}{typ}Constant {name};\n')
        defs.append(f'        {name} = reg.create{prefix}{typ}("{name}");\n')

    for c in constants:
        add_constant(c.optional, c.type, c.name)

    decls.append('\n')
    defs.append('\n')
    for group_name, items in groups.items():
        types = {i.type for i in items}
        if len(types) != 1:
            raise ValueError(f'Inconsistent constant types in group {group_name}')
        t = types.pop()
        decls.append(f'    public static final {t}Constant[] {group_name};\n')
        elements = ', '.join(i.name for i in items)
        group_def = f'{group_name} = new {t}Constant[]{{{elements}}};\n'
        defs.extend(s + '\n' for s in textwrap.wrap(group_def, 200, initial_indent=' ' * 8, subsequent_indent=' ' * 24))

    with open_generated_segment(posix_constants) as f:
        f.writelines(decls)
        f.write('\n')
        f.write('    static {\n')
        f.write('        Registry reg = Registry.create();\n')
        f.writelines(defs)
        f.write('    }\n')


def generate_native_constants(layouts):
    c_filename = DIR / 'graalpython/python-libposix/src/posix.c'
    java_filename = DIR / 'graalpython/com.oracle.graal.python/src/com/oracle/graal/python/runtime/NFIPosixConstants.java'
    constants = []
    for struct in layouts:
        if struct.unix_only:
            wrap = lambda x: f'unix_or_0({x})'
        else:
            wrap = lambda x: x
        constants.append((sizeof_name(struct.name), wrap(f'sizeof({struct.name})')))
        for member in struct.members:
            constants.append((sizeof_name(struct.name, member), wrap(f'sizeof((({struct.name}*)0)->{member})')))
            constants.append((offsetof_name(struct.name, member), wrap(f'offsetof({struct.name}, {member})')))

    with open_generated_segment(c_filename) as f:
        f.write('int32_t init_constants(int64_t* out, int32_t len) {\n')
        f.write(f'    if (len != {len(constants)})\n')
        f.write('        return -1;\n')
        for i, (_, expr) in enumerate(constants):
            f.write(f'    out[{i}] = {expr};\n')
        f.write('    return 0;\n')
        f.write('}\n')

    with open_generated_segment(java_filename) as f:
        for i, (name, _) in enumerate(constants):
            f.write(f'    {name}{"," if i < len(constants) - 1 else ""}\n')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--common', action='store_true')
    args = parser.parse_args()
    if args.common:
        generate_common()
        return
    try:
        generate_platform()
    finally:
        delete_if_exists(c_source_file)
        delete_if_exists(c_executable_file)


if __name__ == '__main__':
    main()
