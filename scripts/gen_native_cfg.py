# Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

To update the platform independent source file, execute:

python3 gen_native_cfg.py --common ../graalpython/com.oracle.graal.python/src/com/oracle/graal/python/runtime/PosixConstants.java

This will modify te file in-place, replacing anything between '// start generated' and '//end generated'.

To generate platform-specific file, execute the script without arguments. This will generate a C source file, compile
it, execute and write the generated Java file to stdout.
In-place modification is not supported as this is meant to be executed remotely, for example:

docker run -i ol6_python3 python3 -u - <gen_native_cfg.py >../graalpython/com.oracle.graal.python/src/com/oracle/graal/python/runtime/PosixConstantsLinux6.java
ssh darwin 'cd /tmp && /usr/local/bin/python3 -u -' <gen_native_cfg.py >../graalpython/com.oracle.graal.python/src/com/oracle/graal/python/runtime/PosixConstantsDarwin.java
"""

import datetime
import os
import subprocess
import sys
import re
from collections import namedtuple

includes = '''
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <stdio.h>
#include <sys/mman.h>
#include <sys/select.h>
#include <sys/types.h>
#include <sys/unistd.h>
#include <sys/utsname.h>
#include <sys/wait.h>
'''

type_defs = {
    'i': ('Int', '%d'),
    'x': ('Int', '0x%08X'),
}

# Asterisks mark optional constants
constant_defs = '''
  i FD_SETSIZE
  i PATH_MAX
  i L_ctermid

  i AT_FDCWD

  i SEEK_SET
  i SEEK_CUR
  i SEEK_END
* i SEEK_DATA
* i SEEK_HOLE

[openFlags]
  x O_ACCMODE
  x O_RDONLY
  x O_WRONLY
  x O_RDWR
  x O_CREAT
  x O_EXCL
  x O_TRUNC
  x O_APPEND
  x O_NONBLOCK
  x O_NDELAY
  x O_DSYNC
  x O_CLOEXEC
  x O_SYNC
* x O_DIRECT
* x O_RSYNC
* x O_TMPFILE

[fileType]
  x S_IFMT
  x S_IFSOCK
  x S_IFLNK
  x S_IFREG
  x S_IFBLK
  x S_IFDIR
  x S_IFCHR
  x S_IFIFO

[mmapFlags]
  x MAP_SHARED
  x MAP_PRIVATE
  x MAP_ANONYMOUS
* x MAP_DENYWRITE
* x MAP_EXECUTABLE

[mmapProtection]
  x PROT_NONE
  x PROT_READ
  x PROT_WRITE
  x PROT_EXEC

[flockOperation]
  x LOCK_SH
  x LOCK_EX
  x LOCK_NB
  x LOCK_UN

[flockType]
* i F_RDLCK
* i F_WRLCK
* i F_UNLCK

[direntType]
  i DT_UNKNOWN
  i DT_FIFO
  i DT_CHR
  i DT_DIR
  i DT_BLK
  i DT_REG
  i DT_LNK
  i DT_SOCK
  i DT_WHT

[waitOptions]
  i WNOHANG
  i WUNTRACED

[accessMode]
  x R_OK
  x W_OK
  x X_OK
  x F_OK
'''


java_copyright = '''/*
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

c_source_file = 'gen_native_cfg.c'
c_executable_file = 'gen_native_cfg'


def parse_defs():
    regex = re.compile(r'\[(\w+)\]|(\*?)\s*(\w+)\s+(\w+)')
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
            c = Constant(m.group(4), m.group(2) == '*', *d)
            current_group.append(c)
            constants.append(c)
    return constants, groups


def delete_if_exists(filename):
    try:
        os.unlink(filename)
    except FileNotFoundError:
        pass


def generate_platform():
    constants, _ = parse_defs()
    platform = sys.platform.capitalize()
    if platform not in ('Linux', 'Darwin'):
        raise ValueError(f'Unsupported platform: {platform}')
    script_name = os.path.basename(__file__)
    if script_name == '<stdin>':
        script_name = 'gen_native_cfg.py'

    with open(c_source_file, 'w') as f:
        f.write(includes)
        f.write('\nint main() {\n')
        for c in constants:
            if c.optional:
                f.write(f'#ifdef {c.name}\n')
            f.write(f'    printf("        constants.put(\\"{c.name}\\", {c.format});\\n", {c.name});\n')
            if c.optional:
                f.write(f'#endif\n')
        f.write('    return 0;\n}\n')

    flags = '-D_GNU_SOURCE' if sys.platform == 'linux' else ''
    cc = os.environ.get('CC', 'cc')
    subprocess.run(f'{cc} -Wall -Werror {flags} -o {c_executable_file} {c_source_file}', shell=True, check=True)

    output = subprocess.run(f'./{c_executable_file}', shell=False, check=True, stdout=subprocess.PIPE, universal_newlines=True).stdout[:-1]
    uname = subprocess.run('uname -srvm', shell=True, check=True, stdout=subprocess.PIPE, universal_newlines=True).stdout.strip()

    print(platform_template.format(java_copyright=java_copyright, script_name=script_name, timestamp=datetime.datetime.now(), uname=uname, platform=platform, output=output))


def generate_common(filename):
    constants, groups = parse_defs()

    decls = []
    defs = []
    for c in constants:
        prefix = 'Optional' if c.optional else 'Mandatory'
        decls.append(f'    public static final {prefix}{c.type}Constant {c.name};\n')
        defs.append(f'        {c.name} = reg.create{prefix}{c.type}("{c.name}");\n')

    decls.append('\n')
    defs.append('\n')
    for name, items in groups.items():
        types = {i.type for i in items}
        if len(types) != 1:
            raise ValueError(f'Inconsistent constant types in group {name}')
        t = types.pop()
        decls.append(f'    public static final {t}Constant[] {name};\n')
        elements = ', '.join(i.name for i in items)
        defs.append(f'        {name} = new {t}Constant[]{{{elements}}};\n')

    with open(filename, 'r') as f:
        header = []
        footer = []
        dst = header
        for line in f.readlines():
            if '// end generated' in line:
                dst = footer
            dst.append(line)
            if '// start generated' in line:
                dst = []

    with open(filename, 'w') as f:
        f.writelines(header)
        f.writelines(decls)
        f.write('\n')
        f.write('    static {\n')
        f.write('        Registry reg = Registry.create();\n')
        f.writelines(defs)
        f.write('    }\n')
        f.writelines(footer)


def main():
    if len(sys.argv) == 3 and sys.argv[1] == '--common':
        generate_common(sys.argv[2])
        return
    try:
        generate_platform()
    finally:
        delete_if_exists(c_source_file)
        delete_if_exists(c_executable_file)


if __name__ == '__main__':
    main()
