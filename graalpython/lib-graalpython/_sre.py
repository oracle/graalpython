# Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import polyglot as _interop

def default(value, default):
    return default if not value else value

def maxsize():
    import sys
    return sys.maxsize
try:
    TREGEX_ENGINE = _interop.eval(string="", language="regex")()
except BaseException:
    TREGEX_ENGINE = None

CODESIZE = 4

MAGIC = 20140917
MAXREPEAT = 4294967295
MAXGROUPS = 2147483647
FLAG_NAMES = ["re.TEMPLATE", "re.IGNORECASE", "re.LOCALE", "re.MULTILINE",
              "re.DOTALL", "re.UNICODE", "re.VERBOSE", "re.DEBUG",
              "re.ASCII"]

_FLAGS_TO_JS = ["", "i", "", "m",
                 "s", "u", "", "",
                 ""]


class SRE_Match():
    def __init__(self, pattern, pos, endpos, result):
        self.result = result
        self.compiled_regex = result.regex
        self.re = pattern
        self.pos = pos
        self.endpos = endpos

    def end(self, groupnum=0):
        return self.result.end[groupnum]

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
        for arg in range(1, self.result.groupCount):
            lst.append(self.__group__(arg))
        return tuple(lst)

    def __groupidx__(self, idx):
        if isinstance(idx, str):
            return self.compiled_regex.groups[idx]
        else:
            return idx

    def __group__(self, idx):
        idxarg = self.__groupidx__(idx)
        start = self.result.start[idxarg]
        if start < 0:
            return None
        else:
            return self.result.input[start:self.result.end[idxarg]]

    def groupdict(self, default=None):
        d = {}
        assert _interop.__has_keys__(self.compiled_regex.groups)
        for k in _interop.__keys__(self.compiled_regex.groups):
            idx = self.compiled_regex.groups[k]
            d[k] = self.__group__(idx)
        return d

    def span(self, groupnum=0):
        idxarg = self.__groupidx__(groupnum)
        return (self.result.start[idxarg], self.result.end[idxarg])

    def start(self, groupnum=0):
        idxarg = self.__groupidx__(groupnum)
        return self.result.start[idxarg]

    @property
    def string(self):
        return self.result.input

    @property
    def lastgroup(self):
        return self.result.groupCount

    @property
    def lastindex(self):
        return self.result.end[0]


class SRE_Pattern():
    def __init__(self, pattern, flags, code, groups=0, groupindex=None, indexgroup=None):
        self.__was_bytes = isinstance(pattern, bytes)
        self.pattern = self._decode_pattern(pattern, flags)
        self.flags = flags
        self.code = code
        self.num_groups = groups
        self.groupindex = groupindex
        self.indexgroup = indexgroup
        self.__compiled_sre_pattern = None
        jsflags = []
        for i,jsflag in enumerate(_FLAGS_TO_JS):
            if flags & (1 << i):
                jsflags.append(jsflag)
        self.jsflags = "".join(jsflags)


    def __tregex_compile(self, pattern):
        if TREGEX_ENGINE is not None:
            return tregex_call_safe(TREGEX_ENGINE, pattern, self.jsflags)
        raise RuntimeError("TREGEX engine not available")


    def __compile_cpython_sre(self):
        if not self.__compiled_sre_pattern:
            import _cpython_sre
            self.__compiled_sre_pattern = _cpython_sre.compile(self._emit(self.pattern), self.flags, self.code, self.num_groups, self.groupindex, self.indexgroup)
        return self.__compiled_sre_pattern


    def _decode_string(self, string, flags=0):
        if isinstance(string, str):
            return string
        elif isinstance(string, bytes):
            return string.decode()
        elif isinstance(string, bytearray):
            return string.decode()
        elif isinstance(string, memoryview):
            # return bytes(string).decode()
            raise TypeError("'memoryview' is currently unsupported as search pattern")
        raise TypeError("invalid search pattern {!r}".format(string))


    def _decode_pattern(self, string, flags=0):
        pattern = self._decode_string(string, flags)

        # TODO: fix this in the regex engine
        pattern = pattern.replace(r'\"', '"').replace(r"\'", "'")

        # TODO: that's not nearly complete but should be sufficient for now
        from sre_compile import SRE_FLAG_VERBOSE
        if flags & SRE_FLAG_VERBOSE:
            pattern = tregex_preprocess_for_verbose(pattern)
        return tregex_preprocess_default(pattern)


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
        return "re.compile(%s%s%s)" % (self.pattern, sep, sflags)

    def _search(self, pattern, string, pos, endpos):
        pattern = self.__tregex_compile(pattern)
        string = self._decode_string(string)
        if endpos == -1 or endpos >= len(string):
            result = tregex_call_safe(pattern.exec, string, pos)
        else:
            result = tregex_call_safe(pattern.exec, string[:endpos], pos)
        if result.isMatch:
            return SRE_Match(self, pos, endpos, result)
        else:
            return None

    def search(self, string, pos=0, endpos=None):
        try:
            return self._search(self.pattern, string, pos, default(endpos, -1))
        except RuntimeError:
            return self.__compile_cpython_sre().search(string, pos, default(endpos, maxsize()))

    def match(self, string, pos=0, endpos=None):
        try:
            if not self.pattern.startswith("^"):
                return self._search("^" + self.pattern, string, pos, default(endpos, -1))
            else:
                return self._search(self.pattern, string, pos, default(endpos, -1))
        except RuntimeError:
            return self.__compile_cpython_sre().match(string, pos, default(endpos, maxsize()))

    def fullmatch(self, string, pos=0, endpos=None):
        try:
            pattern = self.pattern
            if not pattern.startswith("^"):
                pattern = "^" + pattern
            if not pattern.endswith("$"):
                pattern = pattern + "$"
            return self._search(pattern, string, pos, default(endpos, -1))
        except RuntimeError:
            return self.__compile_cpython_sre().fullmatch(string, pos, default(endpos, maxsize()))

    def findall(self, string, pos=0, endpos=-1):
        try:
            pattern = self.__tregex_compile(self.pattern)
            string = self._decode_string(string)
            if endpos > len(string):
                endpos = len(string)
            elif endpos < 0:
                endpos = endpos % len(string) + 1
            matchlist = []
            while pos < endpos:
                result = tregex_call_safe(pattern.exec, string, pos)
                if not result.isMatch:
                    break
                elif self.num_groups == 0:
                    matchlist.append("")
                elif self.num_groups == 1:
                    matchlist.append(string[result.start[1]:result.end[1]])
                else:
                    matchlist.append(SRE_Match(self, pos, endpos, result).groups())
                no_progress = (result.start[0] == result.end[0])
                pos = result.end[0] + no_progress
            return matchlist
        except RuntimeError:
            return self.__compile_cpython_sre().findall(string, pos, maxsize() if endpos == -1 else endpos)


    def __replace_groups(self, repl, string, match_result, pattern):
        def group(match_result, group_nr, string):
            if group_nr >= match_result.groupCount:
                return None
            group_start = match_result.start[group_nr]
            group_end = match_result.end[group_nr]
            return string[group_start:group_end]

        n = len(repl)
        result = self._emit("")
        start = 0
        backslash = self._emit('\\')
        pos = repl.find(backslash, start)
        while pos != -1 and start < n:
            if pos+1 < n:
                if repl[pos + 1].isdigit() and match_result.groupCount > 0:
                    group_nr = int(repl[pos+1])
                    group_str = group(match_result, group_nr, string)
                    if group_str is None:
                        raise ValueError("invalid group reference %s at position %s" % (group_nr, pos))
                    result += repl[start:pos] + self._emit(group_str)
                    start = pos + 2
                elif repl[pos + 1] == 'g':
                    group_ref, group_ref_end, digits_only = self.__extract_groupname(repl, pos + 2)
                    if group_ref:
                        group_str = group(match_result, int(group_ref) if digits_only else pattern.groups[group_ref], string)
                        if group_str is None:
                            raise ValueError("invalid group reference %s at position %s" % (group_ref, pos))
                        result += repl[start:pos] + self._emit(group_str)
                    start = group_ref_end + 1
                elif repl[pos + 1] == backslash:
                    result += repl[start:pos] + backslash
                    start = pos + 2
                else:
                    result += repl[start:pos + 2]
                    start = pos + 2
            pos = repl.find(backslash, start)
        result += repl[start:]
        return result


    def __extract_groupname(self, repl, pos):
        if repl[pos] == '<':
            digits_only = True
            n = len(repl)
            i = pos + 1
            while i < n and repl[i] != '>':
                digits_only = digits_only and repl[i].isdigit()
                i += 1
            if i < n:
                # found '>'
                return repl[pos + 1 : i], i, digits_only
        return None, pos, False


    def sub(self, repl, string, count=0):
        n = 0
        try:
            pattern = self.__tregex_compile(self.pattern)
            string = self._decode_string(string)
            result = []
            pos = 0
            is_string_rep = isinstance(repl, str) or isinstance(repl, bytes) or isinstance(repl, bytearray)
            if is_string_rep:
                repl = _process_escape_sequences(repl)
            progress = True
            while (count == 0 or n < count) and pos <= len(string) and progress:
                match_result = tregex_call_safe(pattern.exec, string, pos)
                if not match_result.isMatch:
                    break
                n += 1
                start = match_result.start[0]
                end = match_result.end[0]
                result.append(self._emit(string[pos:start]))
                if is_string_rep:
                    result.append(self.__replace_groups(repl, string, match_result, pattern))
                else:
                    _srematch = SRE_Match(self, pos, -1, match_result)
                    _repl = repl(_srematch)
                    result.append(_repl)
                pos = end
                progress = (start != end)
            result.append(self._emit(string[pos:]))
            return self._emit("").join(result)
        except BaseException:
            return self.__compile_cpython_sre().sub(repl, string, count)

    def _emit(self, str_like_obj):
        assert isinstance(str_like_obj, str) or isinstance(str_like_obj, bytes)
        if self.__was_bytes != isinstance(str_like_obj, bytes):
            return str_like_obj.encode()
        return str_like_obj


compile = SRE_Pattern


def getcodesize(*args, **kwargs):
    raise NotImplementedError("_sre.getcodesize is not yet implemented")


def getlower(char_ord, flags):
    return ord(chr(char_ord).lower())

def unicode_iscased(codepoint):
    ch = chr(codepoint)
    return ch != ch.lower() or ch != ch.upper()

def unicode_tolower(codepoint):
    return ord(chr(codepoint).lower())
