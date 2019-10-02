# Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

from mmap import mmap

_mappingpoxy = type(type.__dict__)

def default(value, default):
    return default if not value else value

def maxsize():
    import sys
    return sys.maxsize


class _RegexResult:
    def __init__(self, pattern_input, isMatch, groupCount, start, end):
        self.input = pattern_input
        self.isMatch = isMatch
        self.groupCount = groupCount
        self._start = start
        self._end = end

    def getStart(self, grpidx):
        return self._start[grpidx]

    def getEnd(self, grpidx):
        return self._end[grpidx]


def _str_to_bytes(arg):
    buffer = bytearray(len(arg))
    for i, c in enumerate(arg):
        buffer[i] = ord(c)
    return bytes(buffer)


def setup(sre_compiler, error_class, flags_table):
    global error
    error = error_class

    global FLAGS
    FLAGS = flags_table

    def configure_fallback_compiler(mode):
        # wraps a native 're.Pattern' object
        class ExecutablePattern:
            def __init__(self, sticky, compiled_pattern):
                self.__sticky__ = sticky
                self.__compiled_pattern__ = compiled_pattern

            def __call__(self, *args):
                # deprecated
                return self.exec(*args)

            def exec(self, *args):
                nargs = len(args)
                if nargs == 2:
                    # new-style signature
                    pattern_input, from_index = args
                elif nargs == 3:
                    # old-style signature; deprecated
                    _, pattern_input, from_index = args
                else:
                    raise TypeError("invalid arguments: " + repr(args))
                if self.__sticky__:
                    result = self.__compiled_pattern__.match(pattern_input, from_index)
                else:
                    result = self.__compiled_pattern__.search(pattern_input, from_index)
                is_match = result is not None
                group_count = 1 + self.__compiled_pattern__.groups
                return _RegexResult(
                    pattern_input = pattern_input,
                    isMatch = is_match,
                    groupCount = group_count if is_match else 0,
                    start = [result.start(i) for i in range(group_count)] if is_match else [],
                    end = [result.end(i) for i in range(group_count)] if is_match else []
                )

        def fallback_compiler(pattern, flags):
            sticky = False
            bit_flags = 0
            for flag in flags:
                # Handle internal stick(y) flag used to signal matching only at the start of input.
                if flag == "y":
                    sticky = True
                else:
                    bit_flags = bit_flags | FLAGS[flag]

            compiled_pattern = sre_compiler(pattern if mode == "str" else _str_to_bytes(pattern), bit_flags)

            return ExecutablePattern(sticky, compiled_pattern)

        return fallback_compiler

    engine_builder = _build_regex_engine("")

    if engine_builder:
        global TREGEX_ENGINE_STR
        global TREGEX_ENGINE_BYTES
        TREGEX_ENGINE_STR = engine_builder("Flavor=PythonStr", configure_fallback_compiler("str"))
        TREGEX_ENGINE_BYTES = engine_builder("Flavor=PythonBytes", configure_fallback_compiler("bytes"))

        def new_compile(p, flags=0):
            if isinstance(p, (str, bytes)):
                return _tcompile(p, flags)
            else:
                return sre_compiler(p, flags)
    else:
        def new_compile(p, flags=0):
            return sre_compiler(p, flags)

    return new_compile


CODESIZE = 4

MAGIC = 20171005
MAXREPEAT = 4294967295
MAXGROUPS = 2147483647
FLAG_NAMES = ["re.TEMPLATE", "re.IGNORECASE", "re.LOCALE", "re.MULTILINE",
              "re.DOTALL", "re.UNICODE", "re.VERBOSE", "re.DEBUG",
              "re.ASCII"]


class SRE_Match():
    def __init__(self, pattern, pos, endpos, result, input_str, compiled_regex):
        self.result = result
        self.compiled_regex = compiled_regex
        self.re = pattern
        self.pos = pos
        self.endpos = endpos
        self.input_str = input_str

    def end(self, groupnum=0):
        return self.result.getEnd(groupnum)

    def group(self, *args):
        if not args:
            return self.__group__(0)
        elif len(args) == 1:
            return self.__group__(args[0])
        else:
            lst = []
            for arg in args:
                lst.append(self.__group__(arg))
            return tuple(lst)

    def groups(self, default=None):
        lst = []
        for arg in range(1, self.compiled_regex.groupCount):
            lst.append(self.__group__(arg))
        return tuple(lst)

    def __groupidx__(self, idx):
        if isinstance(idx, str):
            return self.compiled_regex.groups[idx]
        else:
            return idx

    def __group__(self, idx):
        idxarg = self.__groupidx__(idx)
        start = self.result.getStart(idxarg)
        if start < 0:
            return None
        else:
            return self.input_str[start:self.result.getEnd(idxarg)]

    def groupdict(self, default=None):
        d = {}
        if self.compiled_regex.groups:
            assert dir(self.compiled_regex.groups)
            for k in dir(self.compiled_regex.groups):
                idx = self.compiled_regex.groups[k]
                d[k] = self.__group__(idx)
        return d

    def span(self, groupnum=0):
        idxarg = self.__groupidx__(groupnum)
        return (self.result.getStart(idxarg), self.result.getEnd(idxarg))

    def start(self, groupnum=0):
        idxarg = self.__groupidx__(groupnum)
        return self.result.getStart(idxarg)

    @property
    def string(self):
        return self.input_str

    @property
    def lastgroup(self):
        return self.compiled_regex.groupCount

    @property
    def lastindex(self):
        return self.result.getEnd(0)

    def __repr__(self):
        return "<re.Match object; span=%r, match=%r>" % (self.span(), self.group())

def _append_end_assert(pattern):
    if isinstance(pattern, str):
        return pattern if pattern.endswith(r"\Z") else pattern + r"\Z"
    else:
        return pattern if pattern.endswith(rb"\Z") else pattern + rb"\Z"

def _is_bytes_like(object):
    return isinstance(object, (bytes, bytearray, memoryview, mmap))

class SRE_Pattern():
    def __init__(self, pattern, flags):
        self.__binary = isinstance(pattern, bytes)
        self.groups = 0
        self.pattern = pattern
        self.flags = flags
        flags_str = []
        for char,flag in FLAGS.items():
            if flags & flag:
                flags_str.append(char)
        self.flags_str = "".join(flags_str)
        self.__compiled_regexes = dict()
        groupindex = dict()
        if self.__tregex_compile(self.pattern).groups is not None:
            for group_name in dir(self.__tregex_compile(self.pattern).groups):
                groups = self.__tregex_compile(self.pattern).groups
                self.groups = len(dir(groups))
                groupindex[group_name] = groups[group_name]
        self.groupindex = _mappingpoxy(groupindex)

    def __check_input_type(self, input):
        if not isinstance(input, str) and not _is_bytes_like(input):
            raise TypeError("expected string or bytes-like object")
        if not self.__binary and _is_bytes_like(input):
            raise TypeError("cannot use a string pattern on a bytes-like object")
        if self.__binary and isinstance(input, str):
            raise TypeError("cannot use a bytes pattern on a string-like object")


    def __tregex_compile(self, pattern, flags=None):
        if flags is None:
            flags = self.flags_str
        if (pattern, flags) not in self.__compiled_regexes:
            tregex_engine = TREGEX_ENGINE_BYTES if self.__binary else TREGEX_ENGINE_STR
            try:
                self.__compiled_regexes[(pattern, flags)] = tregex_call_compile(tregex_engine, pattern, flags)
            except ValueError as e:
                message = str(e)
                boundary = message.rfind(" at position ")
                if boundary == -1:
                    raise error(message, pattern)
                else:
                    position = int(message[boundary + len(" at position "):])
                    message = message[:boundary]
                    raise error(message, pattern, position)
        return self.__compiled_regexes[(pattern, flags)]


    def __repr__(self):
        flags = self.flags
        flag_items = []
        for i,name in enumerate(FLAG_NAMES):
            if flags & (1 << i):
                flags -= (1 << i)
                flag_items.append(name)
        if flags != 0:
            flag_items.append("0x%x" % flags)
        if len(flag_items) == 0:
            sep = ""
            sflags = ""
        else:
            sep = ", "
            sflags = "|".join(flag_items)
        return "re.compile(%r%s%s)" % (self.pattern, sep, sflags)

    def _search(self, pattern, string, pos, endpos, sticky=False):
        pattern = self.__tregex_compile(pattern, self.flags_str + ("y" if sticky else ""))
        input_str = string
        if endpos == -1 or endpos >= len(string):
            endpos = len(string)
            result = tregex_call_exec(pattern.exec, input_str, min(pos, len(string) + 1))
        else:
            input_str = string[:endpos]
            result = tregex_call_exec(pattern.exec, input_str, min(pos, endpos % len(string) + 1))
        if result.isMatch:
            return SRE_Match(self, pos, endpos, result, input_str, pattern)
        else:
            return None

    def search(self, string, pos=0, endpos=None):
        self.__check_input_type(string)
        return self._search(self.pattern, string, pos, default(endpos, -1))

    def match(self, string, pos=0, endpos=None):
        self.__check_input_type(string)
        return self._search(self.pattern, string, pos, default(endpos, -1), sticky=True)

    def fullmatch(self, string, pos=0, endpos=None):
        self.__check_input_type(string)
        return self._search(_append_end_assert(self.pattern), string, pos, default(endpos, -1), sticky=True)

    def __sanitize_out_type(self, elem):
        """Helper function for findall and split. Ensures that the type of the elements of the
           returned list if always either 'str' or 'bytes'."""
        if self.__binary:
            return bytes(elem)
        elif elem is None:
            return None
        else:
            return str(elem)

    def finditer(self, string, pos=0, endpos=-1):
        self.__check_input_type(string)
        if endpos > len(string) or len(string) == 0:
            endpos = len(string)
        elif endpos < 0:
            endpos = endpos % len(string) + 1
        while pos < endpos:
            compiled_regex = self.__tregex_compile(self.pattern)
            result = tregex_call_exec(compiled_regex.exec, string, pos)
            if not result.isMatch:
                break
            else:
                yield SRE_Match(self, pos, endpos, result, string, compiled_regex)
            no_progress = (result.getStart(0) == result.getEnd(0))
            pos = result.getEnd(0) + no_progress
        return

    def findall(self, string, pos=0, endpos=-1):
        self.__check_input_type(string)
        if endpos > len(string):
            endpos = len(string)
        elif endpos < 0 and len(string) > 0:
            endpos = endpos % len(string) + 1
        matchlist = []
        while pos < endpos:
            compiled_regex = self.__tregex_compile(self.pattern)
            result = tregex_call_exec(compiled_regex.exec, string, pos)
            if not result.isMatch:
                break
            elif compiled_regex.groupCount == 1:
                matchlist.append(self.__sanitize_out_type(string[result.getStart(0):result.getEnd(0)]))
            elif compiled_regex.groupCount == 2:
                matchlist.append(self.__sanitize_out_type(string[result.getStart(1):result.getEnd(1)]))
            else:
                matchlist.append(tuple(map(self.__sanitize_out_type, SRE_Match(self, pos, endpos, result, string, compiled_regex).groups())))
            no_progress = (result.getStart(0) == result.getEnd(0))
            pos = result.getEnd(0) + no_progress
        return matchlist

    def __replace_groups(self, repl, string, match_result, pattern):
        def group(pattern, match_result, group_nr, string):
            if group_nr >= pattern.groupCount:
                return None
            group_start = match_result.getStart(group_nr)
            group_end = match_result.getEnd(group_nr)
            return string[group_start:group_end]

        n = len(repl)
        result = b"" if self.__binary else ""
        start = 0
        backslash = b'\\' if self.__binary else '\\'
        pos = repl.find(backslash, start)
        while pos != -1 and start < n:
            if pos+1 < n:
                if repl[pos + 1].isdigit() and pattern.groupCount > 0:
                    # TODO: Should handle backreferences longer than 1 digit and fall back to octal escapes.
                    group_nr = int(repl[pos+1].decode('ascii')) if self.__binary else int(repl[pos+1])
                    group_str = group(pattern, match_result, group_nr, string)
                    if group_str is None:
                        raise error("invalid group reference %s at position %s" % (group_nr, pos))
                    result += repl[start:pos] + group_str
                    start = pos + 2
                elif repl[pos + 1] == (b'g' if self.__binary else 'g'):
                    group_ref, group_ref_end, digits_only = self.__extract_groupname(repl, pos + 2)
                    if group_ref:
                        group_str = group(pattern, match_result, int(group_ref) if digits_only else pattern.groups[group_ref], string)
                        if group_str is None:
                            raise error("invalid group reference %s at position %s" % (group_ref, pos))
                        result += repl[start:pos] + group_str
                    start = group_ref_end + 1
                elif repl[pos + 1] == backslash:
                    result += repl[start:pos] + backslash
                    start = pos + 2
                else:
                    assert False, "unexpected escape in re.sub"
            pos = repl.find(backslash, start)
        result += repl[start:]
        return result


    def __extract_groupname(self, repl, pos):
        if repl[pos] == (b'<' if self.__binary else '<'):
            digits_only = True
            n = len(repl)
            i = pos + 1
            while i < n and repl[i] != (b'>' if self.__binary else '>'):
                digits_only = digits_only and repl[i].isdigit()
                i += 1
            if i < n:
                # found '>'
                group_ref = repl[pos + 1 : i]
                group_ref_str = group_ref.decode('ascii') if self.__binary else group_ref
                return group_ref_str, i, digits_only
        return None, pos, False


    def sub(self, repl, string, count=0):
        return self.subn(repl, string, count)[0]

    def subn(self, repl, string, count=0):
        self.__check_input_type(string)
        n = 0
        pattern = self.__tregex_compile(self.pattern)
        result = []
        pos = 0
        is_string_rep = isinstance(repl, str) or _is_bytes_like(repl)
        if is_string_rep:
            self.__check_input_type(repl)
            try:
                repl = _process_escape_sequences(repl)
            except ValueError as e:
                raise error(str(e))
        while (count == 0 or n < count) and pos <= len(string):
            match_result = tregex_call_exec(pattern.exec, string, pos)
            if not match_result.isMatch:
                break
            n += 1
            start = match_result.getStart(0)
            end = match_result.getEnd(0)
            result.append(string[pos:start])
            if is_string_rep:
                result.append(self.__replace_groups(repl, string, match_result, pattern))
            else:
                _srematch = SRE_Match(self, pos, -1, match_result, string, pattern)
                _repl = repl(_srematch)
                result.append(_repl)
            pos = end
            if start == end:
                if pos < len(string):
                    result.append(string[pos])
                pos = pos + 1
        result.append(string[pos:])
        if self.__binary:
            return (b"".join(result), n)
        else:
            return ("".join(result), n)

    def split(self, string, maxsplit=0):
        n = 0
        pattern = self.__tregex_compile(self.pattern)
        result = []
        collect_pos = 0
        search_pos = 0
        while (maxsplit == 0 or n < maxsplit) and search_pos <= len(string):
            match_result = tregex_call_exec(pattern.exec, string, search_pos)
            if not match_result.isMatch:
                break
            n += 1
            start = match_result.getStart(0)
            end = match_result.getEnd(0)
            result.append(self.__sanitize_out_type(string[collect_pos:start]))
            # add all group strings
            for i in range(1, pattern.groupCount):
                groupStart = match_result.getStart(i)
                if groupStart >= 0:
                    result.append(self.__sanitize_out_type(string[groupStart:match_result.getEnd(i)]))
                else:
                    result.append(None)
            collect_pos = end
            search_pos = end
            if start == end:
                search_pos = search_pos + 1
        result.append(self.__sanitize_out_type(string[collect_pos:]))
        return result


_tcompile = SRE_Pattern

def compile(pattern, flags, code, groups, groupindex, indexgroup):
    import _cpython_sre
    return _cpython_sre.compile(pattern, flags, code, groups, groupindex, indexgroup)


@__builtin__
def getcodesize(*args, **kwargs):
    raise NotImplementedError("_sre.getcodesize is not yet implemented")


@__builtin__
def getlower(char_ord, flags):
    import _cpython_sre
    return _cpython_sre.getlower(char_ord, flags)


@__builtin__
def unicode_iscased(codepoint):
    ch = chr(codepoint)
    return ch != ch.lower() or ch != ch.upper()


@__builtin__
def unicode_tolower(codepoint):
    return ord(chr(codepoint).lower())


@__builtin__
def ascii_iscased(codepoint):
    return codepoint < 128 and chr(codepoint).isalpha()


@__builtin__
def ascii_tolower(codepoint):
    return ord(chr(codepoint).lower()) if codepoint < 128 else codepoint
