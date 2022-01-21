# Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
from array import array

_mappingproxy = type(type.__dict__)

from sys import maxsize


def _check_pos(pos):
    if pos > maxsize:
        raise OverflowError('Python int too large to convert to Java int')


def _normalize_bounds(string, pos, endpos):
    strlen = len(string)
    if endpos < 0:
        endpos = 0
    elif endpos > strlen:
        endpos = strlen
    if pos < 0:
        pos = 0
    elif pos > endpos:
        pos = endpos
    substring = string
    if endpos != strlen:
        substring = string[:endpos]
    return substring, pos, endpos


class _NamedCaptureGroups:
    def __init__(self, groupindex):
        self._groupindex = groupindex

    def __dir__(self):
        return self._groupindex.keys()

    def __getitem__(self, item):
        return self._groupindex[item]

class _RegexResult:
    def __init__(self, pattern_input, isMatch, start, end):
        self.input = pattern_input
        self.isMatch = isMatch
        self._start = start
        self._end = end

    def getStart(self, grpidx):
        return self._start[grpidx]

    def getEnd(self, grpidx):
        return self._end[grpidx]

class _ExecutablePattern:
    def __init__(self, compiled_pattern, flags, sticky):
        self.__compiled_pattern__ = compiled_pattern
        self.__sticky__ = sticky
        self.pattern = compiled_pattern.pattern
        self.flags = {name: bool(flags & flag) for flag, name in FLAG_NAMES}
        self.groupCount = 1 + compiled_pattern.groups
        self.groups = _NamedCaptureGroups(compiled_pattern.groupindex)

    def exec(self, pattern_input, from_index):
        if self.__sticky__:
            result = self.__compiled_pattern__.match(pattern_input, from_index)
        else:
            result = self.__compiled_pattern__.search(pattern_input, from_index)
        is_match = result is not None
        return _RegexResult(
            pattern_input = pattern_input,
            isMatch = is_match,
            start = [result.start(i) for i in range(self.groupCount)] if is_match else [],
            end = [result.end(i) for i in range(self.groupCount)] if is_match else []
        )

def fallback_compiler(pattern, flags):
    """
    :param pattern: a str or bytes with the regexp's pattern
    :param flags: string representation of the regexp's flags
    :return: an object implementing the RegexObject interface
    """
    sticky = False
    bit_flags = 0
    for flag in flags:
        # Handle internal stick(y) flag used to signal matching only at the start of input.
        if flag == "y":
            sticky = True
        else:
            bit_flags = bit_flags | FLAGS[flag]

    compiled_pattern = _sre_compile(pattern, bit_flags)

    return _ExecutablePattern(compiled_pattern, bit_flags, sticky)

def _new_compile(p, flags=0):
    if _with_tregex and isinstance(p, (str, bytes)):
        return _t_compile(p, flags)
    else:
        return _sre_compile(p, flags)

def setup(sre_compiler, error_class, flags_table):
    global error
    error = error_class

    global FLAGS
    FLAGS = flags_table

    global _sre_compile
    _sre_compile = sre_compiler

    return _new_compile

CODESIZE = 4

MAGIC = 20171005
MAXREPEAT = 4294967295
MAXGROUPS = 2147483647
FLAG_TEMPLATE = 1
FLAG_IGNORECASE = 2
FLAG_LOCALE = 4
FLAG_MULTILINE = 8
FLAG_DOTALL = 16
FLAG_UNICODE = 32
FLAG_VERBOSE = 64
FLAG_DEBUG = 128
FLAG_ASCII = 256
FLAG_NAMES = [
    (FLAG_TEMPLATE, "TEMPLATE"),
    (FLAG_IGNORECASE, "IGNORECASE"),
    (FLAG_LOCALE, "LOCALE"),
    (FLAG_MULTILINE, "MULTILINE"),
    (FLAG_DOTALL, "DOTALL"),
    (FLAG_UNICODE, "UNICODE"),
    (FLAG_VERBOSE, "VERBOSE"),
    (FLAG_DEBUG, "DEBUG"),
    (FLAG_ASCII, "ASCII"),
]


class Match():
    def __init__(self, pattern, pos, endpos, result, input_str, compiled_regex, indexgroup):
        self.__result = result
        self.__compiled_regex = compiled_regex
        self.__re = pattern
        self.__pos = pos
        self.__endpos = endpos
        self.__input_str = input_str
        self.__indexgroup = indexgroup

    def end(self, groupnum=0):
        idxarg = self.__groupidx(groupnum)
        return self.__result.getEnd(idxarg)

    def group(self, *args):
        if not args:
            return self.__group(0)
        elif len(args) == 1:
            return self.__group(args[0])
        else:
            lst = []
            for arg in args:
                lst.append(self.__group(arg))
            return tuple(lst)

    def groups(self, default=None):
        lst = []
        for arg in range(1, self.__compiled_regex.groupCount):
            lst.append(self.__group(arg, default))
        return tuple(lst)

    def __getitem__(self, item):
        return self.__group(item)

    def __groupidx(self, idx):
        try:
            if hasattr(idx, '__index__'):
                int_idx = int(idx)
                if 0 <= int_idx < self.__compiled_regex.groupCount:
                    return int_idx
            else:
                return getattr(self.__compiled_regex.groups, idx)
        except Exception:
            pass
        raise IndexError("no such group")

    def __group(self, idx, default=None):
        idxarg = self.__groupidx(idx)
        start = self.__result.getStart(idxarg)
        if start < 0:
            return default
        elif isinstance(self.__input_str, str):
            return self.__input_str[start:self.__result.getEnd(idxarg)]
        else:
            return bytes(self.__input_str[start:self.__result.getEnd(idxarg)])

    def groupdict(self, default=None):
        groups = self.__compiled_regex.groups
        if groups:
            return {name: self.__group(name, default) for name in dir(groups)}
        return {}

    def span(self, groupnum=0):
        idxarg = self.__groupidx(groupnum)
        return self.__result.getStart(idxarg), self.__result.getEnd(idxarg)

    def start(self, groupnum=0):
        idxarg = self.__groupidx(groupnum)
        return self.__result.getStart(idxarg)

    def expand(self, template):
        import re
        return re._expand(self.__re, self, template)

    @property
    def regs(self):
        return tuple(self.span(i) for i in range(self.__compiled_regex.groupCount))

    @property
    def string(self):
        return self.__input_str

    @property
    def re(self):
        return self.__re

    @property
    def pos(self):
        return self.__pos

    @property
    def endpos(self):
        return self.__endpos

    @property
    def lastgroup(self):
        lastindex = self.lastindex
        if lastindex is not None and self.__indexgroup is not None and lastindex in self.__indexgroup:
            return self.__indexgroup[lastindex]

    @property
    def lastindex(self):
        lastindex = self.__result.lastGroup
        if lastindex == -1:
            return None
        else:
            return lastindex

    def __repr__(self):
        return "<%s object; span=%r, match=%r>" % (type(self).__name__, self.span(), self.group())

    def __copy__(self):
        return self

    def __deepcopy__(self, memo):
        return self

def _append_end_assert(pattern):
    if isinstance(pattern, str):
        return pattern if pattern.endswith(r"\Z") else pattern + r"\Z"
    else:
        return pattern if pattern.endswith(rb"\Z") else pattern + rb"\Z"

def _is_bytes_like(object):
    return isinstance(object, (bytes, bytearray, memoryview, array, mmap))

class Pattern():
    def __init__(self, pattern, flags):
        self.__binary = isinstance(pattern, bytes)
        self.pattern = pattern
        self.__input_flags = flags
        flags_str = []
        for char, flag in FLAGS.items():
            if flags & flag:
                flags_str.append(char)
        self.__flags_str = "".join(flags_str)
        self.__compiled_regexes = {}
        compiled_regex = self.__tregex_compile(self.pattern)
        self.groups = compiled_regex.groupCount - 1
        groups = compiled_regex.groups
        if groups is None:
            self.groupindex = {}
            self.__indexgroup = {}
        else:
            group_names = dir(groups)
            if isinstance(groups, __graalpython__.ForeignType):
                # tregex groups object
                self.groupindex = _mappingproxy({name: getattr(groups, name) for name in group_names})
                self.__indexgroup = {getattr(groups, name): name for name in group_names}
            else:
                # _sre._NamedCaptureGroups
                self.groupindex = _mappingproxy({name: groups[name] for name in group_names})
                self.__indexgroup = {groups[name]: name for name in group_names}

    @property
    def flags(self):
        # Flags can be spcified both in the flag argument or inline in the regex. Extract them back from the regex
        flags = self.__input_flags
        regex_flags = self.__tregex_compile(self.pattern).flags
        for flag, name in FLAG_NAMES:
            try:
                if getattr(regex_flags, name):
                    flags |= flag
            except AttributeError:
                pass
        return flags

    def __check_input_type(self, input):
        if not isinstance(input, str) and not _is_bytes_like(input):
            raise TypeError("expected string or bytes-like object")
        if not self.__binary and _is_bytes_like(input):
            raise TypeError("cannot use a string pattern on a bytes-like object")
        if self.__binary and isinstance(input, str):
            raise TypeError("cannot use a bytes pattern on a string-like object")

    def __tregex_compile(self, pattern, flags=None):
        if flags is None:
            flags = self.__flags_str
        if (pattern, flags) not in self.__compiled_regexes:
            try:
                self.__compiled_regexes[(pattern, flags)] = tregex_compile_internal(pattern, flags, fallback_compiler)
            except ValueError as e:
                if len(e.args) == 2:
                    msg = e.args[0]
                    if msg in (
                            "cannot use UNICODE flag with a bytes pattern",
                            "cannot use LOCALE flag with a str pattern",
                            "ASCII and UNICODE flags are incompatible",
                            "ASCII and LOCALE flags are incompatible",
                    ):
                        raise ValueError(msg) from None
                    raise error(msg, pattern, e.args[1]) from None
                raise
        return self.__compiled_regexes[(pattern, flags)]

    def __repr__(self):
        flags = self.flags
        flag_items = []
        if not self.__binary:
            if (flags & (FLAG_LOCALE | FLAG_UNICODE | FLAG_ASCII)) == FLAG_UNICODE:
                flags &= ~FLAG_UNICODE
        for code, name in FLAG_NAMES:
            if flags & code:
                flags -= code
                flag_items.append(f're.{name}')
        if flags != 0:
            flag_items.append("0x%x" % flags)
        if len(flag_items) == 0:
            sep = ""
            sflags = ""
        else:
            sep = ", "
            sflags = "|".join(flag_items)
        return "re.compile(%.200r%s%s)" % (self.pattern, sep, sflags)

    def __eq__(self, other):
        if self is other:
            return True
        if type(other) != Pattern:
            return NotImplemented
        return self.pattern == other.pattern and self.flags == other.flags

    def __hash__(self):
        return hash(self.pattern) * 31 ^ hash(self.flags)

    def __copy__(self):
        return self

    def __deepcopy__(self, memo):
        return self

    def _search(self, pattern, string, pos, endpos, sticky=False):
        _check_pos(pos)
        self.__check_input_type(string)
        substring, pos, endpos = _normalize_bounds(string, pos, endpos)
        pattern = self.__tregex_compile(pattern, self.__flags_str + ("y" if sticky else ""))
        result = tregex_call_exec(pattern.exec, substring, pos)
        if result.isMatch:
            return Match(self, pos, endpos, result, string, pattern, self.__indexgroup)
        else:
            return None

    def search(self, string, pos=0, endpos=maxsize):
        return self._search(self.pattern, string, pos, endpos)

    def match(self, string, pos=0, endpos=maxsize):
        return self._search(self.pattern, string, pos, endpos, sticky=True)

    def fullmatch(self, string, pos=0, endpos=maxsize):
        return self._search(_append_end_assert(self.pattern), string, pos, endpos, sticky=True)

    def __sanitize_out_type(self, elem):
        """Helper function for findall and split. Ensures that the type of the elements of the
           returned list if always either 'str' or 'bytes'."""
        if self.__binary:
            return bytes(elem)
        elif elem is None:
            return ""
        else:
            return str(elem)

    def finditer(self, string, pos=0, endpos=maxsize):
        _check_pos(pos)
        self.__check_input_type(string)
        substring, pos, endpos = _normalize_bounds(string, pos, endpos)
        compiled_regex = self.__tregex_compile(self.pattern)
        return self.__finditer_gen(string, compiled_regex, substring, pos, endpos)

    def __finditer_gen(self, string, compiled_regex, substring, pos, endpos):
        while pos <= endpos:
            result = tregex_call_exec(compiled_regex.exec, substring, pos)
            if not result.isMatch:
                break
            else:
                yield Match(self, pos, endpos, result, string, compiled_regex, self.__indexgroup)
            no_progress = (result.getStart(0) == result.getEnd(0))
            pos = result.getEnd(0) + no_progress
        return

    def findall(self, string, pos=0, endpos=maxsize):
        _check_pos(pos)
        self.__check_input_type(string)
        substring, pos, endpos = _normalize_bounds(string, pos, endpos)
        matchlist = []
        compiled_regex = self.__tregex_compile(self.pattern)
        group_count = compiled_regex.groupCount
        while pos <= endpos:
            result = tregex_call_exec(compiled_regex.exec, substring, pos)
            if not result.isMatch:
                break
            elif group_count == 1:
                matchlist.append(self.__sanitize_out_type(string[result.getStart(0):result.getEnd(0)]))
            elif group_count == 2:
                matchlist.append(self.__sanitize_out_type(string[result.getStart(1):result.getEnd(1)]))
            else:
                matchlist.append(tuple(map(self.__sanitize_out_type, Match(self, pos, endpos, result, string, compiled_regex, self.__indexgroup).groups())))
            no_progress = (result.getStart(0) == result.getEnd(0))
            pos = result.getEnd(0) + no_progress
        return matchlist

    def sub(self, repl, string, count=0):
        return self.subn(repl, string, count)[0]

    def subn(self, repl, string, count=0):
        self.__check_input_type(string)
        n = 0
        pattern = self.__tregex_compile(self.pattern)
        result = []
        pos = 0
        literal = False
        if not callable(repl):
            self.__check_input_type(repl)
            if isinstance(repl, str):
                literal = '\\' not in repl
            else:
                literal = b'\\' not in repl
            if not literal:
                import re
                repl = re._subx(self, repl)
                if not callable(repl):
                    literal = True

        while (count == 0 or n < count) and pos <= len(string):
            match_result = tregex_call_exec(pattern.exec, string, pos)
            if not match_result.isMatch:
                break
            n += 1
            start = match_result.getStart(0)
            end = match_result.getEnd(0)
            result.append(string[pos:start])
            if literal:
                result.append(repl)
            else:
                _srematch = Match(self, pos, -1, match_result, string, pattern, self.__indexgroup)
                _repl = repl(_srematch)
                result.append(_repl)
            pos = end
            if start == end:
                if pos < len(string):
                    result.append(string[pos])
                pos = pos + 1
        result.append(string[pos:])
        if self.__binary:
            return b"".join(result), n
        else:
            return "".join(result), n

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

    def scanner(self, string, pos=0, endpos=maxsize):
        return SREScanner(self, string, pos, endpos)


class SREScanner(object):
    def __init__(self, pattern, string, start, end):
        self.pattern = pattern
        self._string = string
        self._start = start
        self._end = end

    def _match_search(self, matcher):
        if self._start > len(self._string):
            return None
        match = matcher(self._string, self._start, self._end)
        if match is None:
            self._start += 1
        else:
            self._start = match.end()
            if match.start() == self._start:
                self._start += 1
        return match

    def match(self):
        return self._match_search(self.pattern.match)

    def search(self):
        return self._match_search(self.pattern.search)


_t_compile = Pattern

def compile(pattern, flags, code, groups, groupindex, indexgroup):
    import _cpython_sre
    return _cpython_sre.compile(pattern, flags, code, groups, groupindex, indexgroup)


@__graalpython__.builtin
def getcodesize(*args, **kwargs):
    raise NotImplementedError("_sre.getcodesize is not yet implemented")


@__graalpython__.builtin
def getlower(char_ord, flags):
    import _cpython_sre
    return _cpython_sre.getlower(char_ord, flags)


@__graalpython__.builtin
def unicode_iscased(codepoint):
    ch = chr(codepoint)
    return ch != ch.lower() or ch != ch.upper()


@__graalpython__.builtin
def unicode_tolower(codepoint):
    return ord(chr(codepoint).lower())


@__graalpython__.builtin
def ascii_iscased(codepoint):
    return codepoint < 128 and chr(codepoint).isalpha()


@__graalpython__.builtin
def ascii_tolower(codepoint):
    return ord(chr(codepoint).lower()) if codepoint < 128 else codepoint
