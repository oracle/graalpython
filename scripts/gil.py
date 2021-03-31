# Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
import argparse
import glob
import os
import re

# detects the beginning of an exported message
PTRN_MESSAGE = re.compile(
    r"@ExportMessage(?P<header>.*?)(?P<method>\s[a-zA-Z][a-zA-Z0-9]*)\((?P<args>.*?)\)(?P<throws>\sthrows .*?)?\s\{",
    re.DOTALL | re.MULTILINE | re.UNICODE)

PTRN_SPECIALIZATION = re.compile(
    r"@(Specialization|Fallback)(?P<header>.*?)(?P<method>\s[a-zA-Z][a-zA-Z0-9]*)\((?P<args>.*?)\)(?P<throws>\sthrows .*?)?\s\{",
    re.DOTALL | re.MULTILINE | re.UNICODE)

PTRN_PACKAGE = re.compile(
    r"package\s.*?;",
    re.DOTALL | re.MULTILINE | re.UNICODE)

PTRN_GILNODE_ARG = re.compile(
    r"(?P<start>,)(?P<arg>.*?@Cached GilNode gil)",
    re.MULTILINE | re.UNICODE)

PTRN_REM_GIL_TRY_CATCH = re.compile(
    r"(boolean mustRelease = gil\.acquire\(\);\s+)?try\s\{\s(?P<body>.+?)\s+\} finally \{\s+gil\.release\(mustRelease\);\s+\}",
    re.DOTALL | re.MULTILINE | re.UNICODE)

PTRN_REM_GIL_ARGS = re.compile(
    r'(,\s+)?@((Cached\.)?Exclusive|Shared\("gil"\))\s@Cached GilNode gil',
    re.DOTALL | re.MULTILINE | re.UNICODE)

PTRN_REM_GIL_BIND = re.compile(
    r'(,\s+)?@Bind.*?\sboolean mustRelease',
    re.DOTALL | re.MULTILINE | re.UNICODE)

PTRN_LIB_MSG = re.compile(
    r'^    \w(?P<header>.*?)\s(?P<method>[a-zA-Z][a-zA-Z0-9]*)\((?P<args>.*?)\)(?P<throws>\sthrows .*?)?\s\{',
    re.MULTILINE | re.UNICODE)

PTRN_LIB_MSG_ABS = re.compile(
    r'^    \w(?P<header>.*?)\s(?P<method>[a-zA-Z][a-zA-Z0-9]*)\((?P<args>.*?)\)(?P<throws>\sthrows .*?)?;',
    re.MULTILINE | re.UNICODE)

RUNTIME_PACKAGE = "package com.oracle.graal.python.runtime;"
GIL_NODE_IMPORT = "import com.oracle.graal.python.runtime.GilNode;"
CACHED_IMPORT = "import com.oracle.truffle.api.dsl.Cached;"
SHARED_IMPORT = "import com.oracle.truffle.api.dsl.Cached.Shared;"
EXCLUSIVE_IMPORT = "import com.oracle.truffle.api.dsl.Cached.Exclusive;"
SKIP_GIL = "// skip GIL"


def find_end(match, source, is_class=False):
    end = match.end()
    cnt = 2 if is_class else 1
    i = 0
    for i, chr in enumerate(source[end:]):
        if cnt == 0:
            break
        if chr == '{':
            cnt += 1
        if chr == '}':
            cnt -= 1
    return end + i


class ExportedMessage(object):
    def __init__(self, match, source, start_offset=0, is_class=False, shared=False):
        self.match = match
        self.full_source = source
        self._shared = shared
        self._offset = start_offset
        self._start = match.start()
        self._args_start = match.start('args')
        self._args_end = match.end('args')
        self._throws_start = match.start('throws')
        self._throws_end = match.end('throws')
        self._body_start = match.end()
        self._end = find_end(match, source, is_class=is_class)

    @property
    def start(self):
        return self._offset + self._start

    @property
    def end(self):
        return self._offset + self._end

    @property
    def source(self):
        return self.full_source[self._start: self._end]

    @property
    def header(self):
        return self.full_source[self._start: self._args_start - 1]

    @property
    def args(self):
        return self.full_source[self._args_start: self._args_end]

    @property
    def throws(self):
        return self.full_source[self._throws_start: self._throws_end]

    @property
    def body(self):
        return self.full_source[self._body_start:self._end - 1]

    @property
    def is_fallback(self):
        return '@Fallback' in self.header

    @property
    def is_with_gil(self):
        return "GilNode gil" in self.source

    @property
    def is_class(self):
        return ' class ' in self.match.group('header')

    @property
    def name(self):
        rv = self.match.group('method')
        if self.is_class:
            hdr = self.match.group('header').split()
            name = hdr[hdr.index('class') + 1]
            rv = name[:1].lower() + name[1:]
        return rv.strip()

    @property
    def source_with_gil(self):
        # handle varargs ...
        _args = self.args
        if self.is_fallback:
            _uncached_gil = "GilNode gil = GilNode.getUncached();"
        else:
            _uncached_gil = ""
            _args += ",\n " if self.args else ""
            if self._shared and ('limit = ' not in self.header or 'limit = "1"' in self.header):
                _args += '@Shared("gil")'
            else:
                _args += "@Exclusive"
            _args += "@Cached GilNode gil"
            if "..." in _args:
                _args = _args.replace("...", "[]")

        return """%s(%s) %s{
    %s boolean mustRelease = gil.acquire();
    try {
        %s
    } finally {
        gil.release(mustRelease);
    }
}""" % (self.header, _args, self.throws, _uncached_gil, self.body.strip())

    @property
    def source_without_gil(self):
        source = self.source
        source = re.sub(PTRN_REM_GIL_TRY_CATCH, lambda match: match.group('body'), source, 1)
        source = re.sub(PTRN_REM_GIL_ARGS, "", source, 1)
        source = re.sub(PTRN_REM_GIL_BIND, "", source, 1)
        return source

    def __str__(self):
        return "START: {}, ARGS {}:{}, BODY_START: {}, STOP: {}, CONTENT:\n {}".format(
            self._start, self._args_start, self._args_end, self._body_start, self._end, self.source)

    def __repr__(self):
        return 'Message({})'.format(self.name)


def message_is_class(match):
    return ' class ' in match.group('header')


def get_messages(source, pattern, start_offset=0, is_class=False, sharing=False):
    matches = list(re.finditer(pattern, source))
    messages = []
    shared = False
    if ((len(matches) > 1 and pattern == PTRN_MESSAGE) or
        (len(matches) > 2 and pattern == PTRN_SPECIALIZATION)) and sharing:
        shared = True
    for match in matches:
        if message_is_class(match):
            start = match.start()
            end = find_end(match, source, is_class=True)
            messages.extend(get_messages(source[start: end], PTRN_SPECIALIZATION, start_offset=start,
                                         sharing=sharing)[0])
        else:
            messages.append(ExportedMessage(match, source, start_offset=start_offset, is_class=is_class, shared=shared))
    return messages, shared


def add_import(source, shared=False):
    match = list(re.finditer(PTRN_PACKAGE, source))[0]
    end = match.end()
    skip_gil_import = GIL_NODE_IMPORT in source or RUNTIME_PACKAGE in source
    skip_cached_import = CACHED_IMPORT in source
    skip_shared_import = SHARED_IMPORT in source
    skip_excl_shared = EXCLUSIVE_IMPORT in source
    gil_import = "" if skip_gil_import else "\n" + GIL_NODE_IMPORT
    cached_import = "" if skip_cached_import else "\n" + CACHED_IMPORT
    if shared:
        shared_import = "" if skip_shared_import else "\n" + SHARED_IMPORT
    else:
        shared_import = "" if skip_excl_shared else "\n" + EXCLUSIVE_IMPORT
    return source[:end] + gil_import + cached_import + shared_import + source[end:]


def file_names_filter(f_name, names):
    names = names.split(",")
    for n in names:
        if n in f_name:
            return True
    return False


def fix_gilnode_arg(source):
    def repl(match):
        return match.group("start") + "\n" + match.group("arg")
    return re.sub(PTRN_GILNODE_ARG, repl, source)


def get_lib_messages(lib, files):
    if lib is None:
        return None
    lib_file = next(f for f in files if lib in f)
    print("got lib source: {}".format(lib_file))
    with open(lib_file, 'r') as SRC:
        src = SRC.read()
        messages = set()
        for m in re.finditer(PTRN_LIB_MSG, src):
            messages.add(m.group('method'))
        for m in re.finditer(PTRN_LIB_MSG_ABS, src):
            messages.add(m.group('method'))
        return messages


def main(sources, add=True, lib=None, dry_run=True, check_style=True, single_source=False, source_filter=None,
         ignore_filter=None, count=False, sharing=False, fix_style=False):
    files = glob.glob("{}**/*.java".format(sources), recursive=True)
    lib_messages = get_lib_messages(lib, files)
    if lib:
        from pprint import pprint
        print("[{}] messages: ".format(lib))
        pprint(lib_messages)

    if ignore_filter:
        files = list(filter(lambda f: not file_names_filter(f, ignore_filter), files))
    if source_filter and not count:
        files = list(filter(lambda f: file_names_filter(f, source_filter), files))

    remove = not add
    cnt = 0
    for java_file in files:
        with open(java_file, 'r+') as SRC:
            source = SRC.read()
            if fix_style:
                if "GilNode" in source:
                    print("[process] {}".format(java_file))
                    source = fix_gilnode_arg(source)
                    SRC.seek(0)
                    SRC.write(source)
                continue
            else:
                messages, shared = get_messages(source, PTRN_MESSAGE, sharing=sharing)
                if len(messages) > 0:
                    if count:
                        cnt += 1
                        continue

                    print("[process] dry run: {}, add: {}. messages: {}, {}".format(
                        dry_run, add, len(messages), java_file))

                    def get_mod_source(msg):
                        return msg.source_with_gil if add else msg.source_without_gil

                    if (add and 'GilNode gil' in source) or \
                            (remove and 'GilNode gil' not in source) or \
                            SKIP_GIL in source:
                        print("[skipping] {}".format(java_file))
                        continue

                    if remove and '@ExportLibrary({}.class)'.format(lib) not in source:
                        print("[skipping] {}".format(java_file))
                        continue

                    if lib:
                        messages = list(filter(lambda m: m.name in lib_messages and m.is_with_gil, messages))
                        print("process messages: ", messages)

                    if len(messages) == 0:
                        continue

                    _src_parts = []
                    m = messages[0]
                    if len(messages) == 1:
                        _src_parts = [source[:m.start], get_mod_source(m), source[m.end:]]
                    else:
                        _src_parts.append(source[:m.start])
                        for m1, m2 in zip(messages[:-1], messages[1:]):
                            _src_parts.append(get_mod_source(m1))
                            _src_parts.append(source[m1.end: m2.start])
                        _src_parts.append(get_mod_source(m2))
                        _src_parts.append(source[m2.end:])

                    modified_source = ''.join(_src_parts)
                    if add:
                        modified_source = add_import(modified_source, shared=shared)

                    if dry_run:
                        print(modified_source)
                        return
                    else:
                        SRC.truncate(0)
                        SRC.seek(0)
                        if modified_source:
                            SRC.write(modified_source)
                        if single_source:
                            break

    if count:
        print("TO PROCESS: {} files".format(cnt))
    if check_style and not count:
        os.system("mx python-gate --tags style,python-license")


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry_run", help="do not write any changes, stop after the first file transform",
                        action="store_true")
    parser.add_argument("--count", help="count how many files may need the GIL", action="store_true")
    parser.add_argument("--remove", help="remove the GIL", action="store_true")
    parser.add_argument("--lib", type=str, help="the internal library for which messages to remove the GIL")
    parser.add_argument("--no_style", help="do not run the style checker", action="store_true")
    parser.add_argument("--sharing", help="use @Shared", action="store_true")
    parser.add_argument("--single", help="stop after modifying the first source", action="store_true")
    parser.add_argument("--filter", type=str, help="filter for source name(s) (comma separated)")
    parser.add_argument("--ignore", type=str, help="ignore filter for source name(s) (comma separated)")
    parser.add_argument("--fix_style", help="fix GilNode related style issue", action="store_true")
    parser.add_argument("sources", type=str, help="location of sources")
    args = parser.parse_args()

    main(args.sources, add=not args.remove, lib=args.lib, dry_run=args.dry_run, check_style=not args.no_style,
         single_source=args.single, source_filter=args.filter, ignore_filter=args.ignore, count=args.count,
         sharing=args.sharing, fix_style=args.fix_style)
