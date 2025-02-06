# Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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

def _is_bytes_like(object):
    return isinstance(object, (bytes, bytearray, memoryview, array, mmap))

def _getlocale():
    from locale import getlocale
    (lang, encoding) = getlocale()
    if lang is None and charset is None:
        return 'C'
    if lang is None:
        lang = 'en_US'
    return '.'.join((lang, encoding))

def _new_compile(p, flags=0):
    if _with_tregex and isinstance(p, (str, bytes, bytearray, memoryview, array, mmap)):
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

MAGIC = 20221023
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
    # (FLAG_DEBUG, "DEBUG"), # there is no DEBUG flag in tregex
    (FLAG_ASCII, "ASCII"),
]

class Match():
    def __init__(self, pattern, pos, endpos, result, input_str, indexgroup):
        self.__result = result
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
        for arg in range(1, self.__re.groups + 1):
            lst.append(self.__group(arg, default))
        return tuple(lst)

    def __getitem__(self, item):
        return self.__group(item)

    def __class_getitem__(cls, item):
        import types
        return types.GenericAlias(cls, item)

    def __groupidx(self, idx):
        try:
            if hasattr(idx, '__index__'):
                int_idx = int(idx)
                if 0 <= int_idx <= self.__re.groups:
                    return int_idx
            else:
                return self.__re.groupindex[idx]
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
        groups = self.__re.groupindex
        if groups:
            return {name: self.__group(name, default) for name in groups.keys()}
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
        return tuple(self.span(i) for i in range(self.__re.groups + 1))

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

class Pattern():
    def __init__(self, pattern, flags):
        self.pattern = pattern
        self.flags = flags
        self.__binary = _is_bytes_like(pattern)
        self.__input_flags = flags

        tregex_init_cache(self, pattern, flags)

        compiled_regex = tregex_compile(self, _METHOD_SEARCH, False)

        regex_flags = compiled_regex.flags
        for flag, name in FLAG_NAMES:
            try:
                if getattr(regex_flags, name):
                    self.flags |= flag
            except AttributeError:
                pass

        self.groups = compiled_regex.groupCount - 1
        groups = compiled_regex.groups
        if groups is None:
            self.groupindex = {}
            self.__indexgroup = {}
        else:
            group_names = dir(groups)
            self.groupindex = _mappingproxy({name: getattr(groups, name) for name in group_names})
            self.__indexgroup = {getattr(groups, name): name for name in group_names}

    def __check_input_type(self, input):
        if not isinstance(input, str) and not _is_bytes_like(input):
            raise TypeError("expected string or bytes-like object")
        if not self.__binary and _is_bytes_like(input):
            raise TypeError("cannot use a string pattern on a bytes-like object")
        if self.__binary and isinstance(input, str):
            raise TypeError("cannot use a bytes pattern on a string-like object")

    def __fallback_compile(self):
        if self.__compiled_fallback is None:
            if not _with_sre:
                raise ValueError("regular expression not supported, no fallback engine present") from None
            self.__compiled_fallback = _sre_compile(self.pattern, self.__input_flags)
        return self.__compiled_fallback

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

    def __class_getitem__(cls, item):
        import types
        return types.GenericAlias(cls, item)

    def _search(self, string, pos, endpos, method=_METHOD_SEARCH, must_advance=False):
        return tregex_search(self, string, pos, endpos, method, must_advance)

    @__graalpython__.force_split_direct_calls
    def search(self, string, pos=0, endpos=maxsize):
        return self._search(string, pos, endpos, method=_METHOD_SEARCH)

    @__graalpython__.force_split_direct_calls
    def match(self, string, pos=0, endpos=maxsize):
        return self._search(string, pos, endpos, method=_METHOD_MATCH)

    @__graalpython__.force_split_direct_calls
    def fullmatch(self, string, pos=0, endpos=maxsize):
        return self._search(string, pos, endpos, method=_METHOD_FULLMATCH)

    def __sanitize_out_type(self, elem):
        """Helper function for findall and split. Ensures that the type of the elements of the
           returned list if always either 'str' or 'bytes'."""
        if self.__binary:
            return bytes(elem)
        elif elem is None:
            return ""
        else:
            return str(elem)

    @__graalpython__.force_split_direct_calls
    def finditer(self, string, pos=0, endpos=maxsize):
        for must_advance in [False, True]:
            if tregex_compile(self, _METHOD_SEARCH, must_advance) is None:
                return self.__fallback_compile().finditer(string, pos=pos, endpos=endpos)
        _check_pos(pos)
        self.__check_input_type(string)
        substring, pos, endpos = _normalize_bounds(string, pos, endpos)
        return self.__finditer_gen(string, substring, pos, endpos)

    def __finditer_gen(self, string, substring, pos, endpos):
        must_advance = False
        while pos <= endpos:
            compiled_regex = tregex_compile(self, _METHOD_SEARCH, must_advance)
            result = tregex_call_exec(compiled_regex, substring, pos)
            if not result.isMatch:
                break
            else:
                yield Match(self, pos, endpos, result, string, self.__indexgroup)
            pos = result.getEnd(0)
            must_advance = (result.getStart(0) == result.getEnd(0))
        return

    @__graalpython__.force_split_direct_calls
    def findall(self, string, pos=0, endpos=maxsize):
        for must_advance in [False, True]:
            if tregex_compile(self, _METHOD_SEARCH, must_advance) is None:
                return self.__fallback_compile().findall(string, pos=pos, endpos=endpos)
        _check_pos(pos)
        self.__check_input_type(string)
        substring, pos, endpos = _normalize_bounds(string, pos, endpos)
        matchlist = []
        must_advance = False
        while pos <= endpos:
            compiled_regex = tregex_compile(self, _METHOD_SEARCH, must_advance)
            result = tregex_call_exec(compiled_regex, substring, pos)
            if not result.isMatch:
                break
            elif self.groups == 0:
                matchlist.append(self.__sanitize_out_type(string[result.getStart(0):result.getEnd(0)]))
            elif self.groups == 1:
                matchlist.append(self.__sanitize_out_type(string[result.getStart(1):result.getEnd(1)]))
            else:
                matchlist.append(tuple(map(self.__sanitize_out_type, Match(self, pos, endpos, result, string, self.__indexgroup).groups())))
            pos = result.getEnd(0)
            must_advance = (result.getStart(0) == result.getEnd(0))
        return matchlist

    @__graalpython__.force_split_direct_calls
    def sub(self, repl, string, count=0):
        return self.subn(repl, string, count)[0]

    @__graalpython__.force_split_direct_calls
    def subn(self, repl, string, count=0):
        for must_advance in [False, True]:
            if tregex_compile(self, _METHOD_SEARCH, must_advance) is None:
                return self.__fallback_compile().subn(repl, string, count=count)
        self.__check_input_type(string)
        n = 0
        result = []
        pos = 0
        literal = False
        must_advance = False
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
            compiled_regex = tregex_compile(self, _METHOD_SEARCH, must_advance)
            match_result = tregex_call_exec(compiled_regex, string, pos)
            if not match_result.isMatch:
                break
            n += 1
            start = match_result.getStart(0)
            end = match_result.getEnd(0)
            result.append(string[pos:start])
            if literal:
                result.append(repl)
            else:
                _srematch = Match(self, pos, -1, match_result, string, self.__indexgroup)
                _repl = repl(_srematch)
                if _repl is not None:
                    result.append(_repl)
            pos = end
            must_advance = start == end
        result.append(string[pos:])
        if self.__binary:
            return b"".join(result), n
        else:
            return "".join(result), n

    @__graalpython__.force_split_direct_calls
    def split(self, string, maxsplit=0):
        for must_advance in [False, True]:
            if tregex_compile(self, _METHOD_SEARCH, must_advance) is None:
                return self.__fallback_compile().split(string, maxsplit=maxsplit)
        n = 0
        result = []
        collect_pos = 0
        search_pos = 0
        must_advance = False
        while (maxsplit == 0 or n < maxsplit) and search_pos <= len(string):
            compiled_regex = tregex_compile(self, _METHOD_SEARCH, must_advance)
            match_result = tregex_call_exec(compiled_regex, string, search_pos)
            if not match_result.isMatch:
                break
            n += 1
            start = match_result.getStart(0)
            end = match_result.getEnd(0)
            result.append(self.__sanitize_out_type(string[collect_pos:start]))
            # add all group strings
            for i in range(1, self.groups + 1):
                groupStart = match_result.getStart(i)
                if groupStart >= 0:
                    result.append(self.__sanitize_out_type(string[groupStart:match_result.getEnd(i)]))
                else:
                    result.append(None)
            collect_pos = end
            search_pos = end
            must_advance = start == end
        result.append(self.__sanitize_out_type(string[collect_pos:]))
        return result

    def scanner(self, string, pos=0, endpos=maxsize):
        # We cannot pass the must_advance parameter to the internal SRE implementation.
        # If TRegex cannot support must_advance in either the 'match' or the 'search' method,
        # we will need to use the original implementation of the 'scanner' method.
        for method in [_METHOD_MATCH, _METHOD_SEARCH]:
            if tregex_compile(self, method, True) is None:
                return self.__fallback_compile().scanner(string, pos=pos, endpos=endpos)
        return SREScanner(self, string, pos, endpos)


class SREScanner(object):
    def __init__(self, pattern, string, start, end):
        self.pattern = pattern
        self.__string = string
        self.__start = start
        self.__end = end
        self.__must_advance = False

    def __match_search(self, method):
        if self.__start > len(self.__string):
            return None
        match = self.pattern._search(self.__string, self.__start, self.__end, method=method, must_advance=self.__must_advance)
        if match is None:
            self.__start += 1
        else:
            self.__start = match.end()
            self.__must_advance = match.start() == self.__start
        return match

    @__graalpython__.force_split_direct_calls
    def match(self):
        return self.__match_search(_METHOD_MATCH)

    @__graalpython__.force_split_direct_calls
    def search(self):
        return self.__match_search(_METHOD_SEARCH)


_t_compile = Pattern

def compile(pattern, flags, code, groups, groupindex, indexgroup):
    import _cpython_sre
    return _cpython_sre.compile(pattern, flags, code, groups, groupindex, indexgroup)


@__graalpython__.builtin
def getcodesize(module, *args, **kwargs):
    raise NotImplementedError("_sre.getcodesize is not yet implemented")


@__graalpython__.builtin
def getlower(module, char_ord, flags):
    import _cpython_sre
    return _cpython_sre.getlower(char_ord, flags)


@__graalpython__.builtin
def unicode_iscased(module, codepoint):
    ch = chr(codepoint)
    return ch != ch.lower() or ch != ch.upper()


@__graalpython__.builtin
def unicode_tolower(module, codepoint):
    return ord(chr(codepoint).lower())


@__graalpython__.builtin
def ascii_iscased(module, codepoint):
    return codepoint < 128 and chr(codepoint).isalpha()


@__graalpython__.builtin
def ascii_tolower(module, codepoint):
    return ord(chr(codepoint).lower()) if codepoint < 128 else codepoint
