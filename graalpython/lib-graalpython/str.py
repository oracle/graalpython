# coding=utf-8
# Copyright (c) 2017, 2020, Oracle and/or its affiliates.
# Copyright (c) 2017, The PyPy Project
#
#     The MIT License
# Permission is hereby granted, free of charge, to any person
# obtaining a copy of this software and associated documentation
# files (the "Software"), to deal in the Software without
# restriction, including without limitation the rights to use,
# copy, modify, merge, publish, distribute, sublicense, and/or
# sell copies of the Software, and to permit persons to whom the
# Software is furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included
# in all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
# OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
# THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
# FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
# DEALINGS IN THE SOFTWARE.
import _codecs
import sys


# Auto number state
ANS_INIT = 1
ANS_AUTO = 2
ANS_MANUAL = 3


class TemplateFormatter(object):
    parser_list = None

    def __init__(self, template):
        self.empty = ""
        self.template = template

    def build(self, args, kwargs):
        self.args = args
        self.kwargs = kwargs
        self.auto_numbering = 0
        self.auto_numbering_state = ANS_INIT
        return self._build_string(0, len(self.template), 2)

    def _build_string(self, start, end, level):
        out = []
        if not level:
            raise ValueError("Recursion depth exceeded")
        level -= 1
        s = self.template
        return self._do_build_string(start, end, level, out, s)

    def _do_build_string(self, start, end, level, out, s):
        last_literal = i = start
        while i < end:
            c = s[i]
            i += 1
            if c == "{" or c == "}":
                at_end = i == end
                # Find escaped "{" and "}"
                markup_follows = True
                if c == "}":
                    if at_end or s[i] != "}":
                        raise ValueError("Single '}'")
                    i += 1
                    markup_follows = False
                if c == "{":
                    if at_end:
                        raise ValueError("Single '{'")
                    if s[i] == "{":
                        i += 1
                        markup_follows = False
                # Attach literal data, ending with { or }
                out.append(s[last_literal:i - 1])
                if not markup_follows:
                    if self.parser_list is not None:
                        end_literal = i - 1
                        assert end_literal > last_literal
                        literal = self.template[last_literal:end_literal]
                        entry = (literal, None, None, None)
                        self.parser_list.append(entry)
                        self.last_end = i
                    last_literal = i
                    continue
                nested = 1
                field_start = i
                recursive = False
                while i < end:
                    c = s[i]
                    if c == "{":
                        recursive = True
                        nested += 1
                    elif c == "}":
                        nested -= 1
                        if not nested:
                            break
                    elif c == "[":
                        i += 1
                        while i < end and s[i] != "]":
                            i += 1
                        continue
                    i += 1
                if nested:
                    raise ValueError("Unmatched '{'")
                rendered = self._render_field(field_start, i, recursive, level)
                out.append(rendered)
                i += 1
                last_literal = i

        out.append(s[last_literal:end])
        return "".join(out)

    def _parse_field(self, start, end):
        s = self.template
        # Find ":" or "!"
        i = start
        while i < end:
            c = s[i]
            if c == ":" or c == "!":
                end_name = i
                if c == "!":
                    i += 1
                    if i == end:
                        raise ValueError("expected conversion")
                    conversion = s[i]
                    i += 1
                    if i < end:
                        if s[i] != ':':
                            raise ValueError("expected ':' after format specifier")
                        i += 1
                else:
                    conversion = None
                    i += 1
                return s[start:end_name], conversion, i
            elif c == "[":
                while i + 1 < end and s[i + 1] != "]":
                    i += 1
            elif c == "{":
                raise ValueError("unexpected '{' in field name")
            i += 1
        return s[start:end], None, end

    def _get_argument(self, name):
        # First, find the argument.
        i = 0
        end = len(name)
        while i < end:
            c = name[i]
            if c == "[" or c == ".":
                break
            i += 1
        empty = not i
        if empty:
            index = -1
        else:
            try:
                index = int(name[0:i])
            except ValueError:
                index = -1
        use_numeric = empty or index != -1
        if self.auto_numbering_state == ANS_INIT and use_numeric:
            if empty:
                self.auto_numbering_state = ANS_AUTO
            else:
                self.auto_numbering_state = ANS_MANUAL
        if use_numeric:
            if self.auto_numbering_state == ANS_MANUAL:
                if empty:
                    raise ValueError("switching from manual to automatic numbering")
            elif not empty:
                raise ValueError("switching from automatic to manual numbering")
        if empty:
            index = self.auto_numbering
            self.auto_numbering += 1
        if index == -1:
            kwarg = name[:i]
            arg = self.kwargs[kwarg]
        elif index > sys.maxsize:
            raise ValueError("Too many decimal digits in format string")
        else:
            if self.args is None:
                raise ValueError("Format string contains positional fields")
            if index >= len(self.args):
                raise IndexError("Replacement index %d out of range for positional args tuple" % index)
            arg = self.args[index]
        return self._resolve_lookups(arg, name, i, end)

    def _resolve_lookups(self, obj, name, start, end):
        # Resolve attribute and item lookups.
        i = start
        while i < end:
            c = name[i]
            if c == ".":
                i += 1
                start = i
                while i < end:
                    c = name[i]
                    if c == "[" or c == ".":
                        break
                    i += 1
                if start == i:
                    raise ValueError("Empty attribute in format string")
                attr = name[start:i]
                if obj is not None:
                    obj = getattr(obj, attr)
                else:
                    self.parser_list.append((True, attr))
            elif c == "[":
                got_bracket = False
                i += 1
                start = i
                while i < end:
                    c = name[i]
                    if c == "]":
                        got_bracket = True
                        break
                    i += 1
                if not got_bracket:
                    raise ValueError("Missing ']'")
                try:
                    index = int(name[start:i])
                except ValueError:
                    item = name[start:i]
                else:
                    if index > sys.maxsize:
                        raise ValueError("Too many decimal digits in format string")
                    item = index
                i += 1 # Skip "]"
                if obj is not None:
                    obj = obj[item]
                else:
                    self.parser_list.append((False, item))
            else:
                raise ValueError("Only '[' and '.' may follow ']'")
        return obj

    def formatter_field_name_split(self):
        name = self.template
        i = 0
        end = len(name)
        while i < end:
            c = name[i]
            if c == "[" or c == ".":
                break
            i += 1
        if i == 0:
            index = -1
        else:
            try:
                index = int(name[0:i])
            except ValueError:
                index = -1
        if index >= 0:
            first = int(index)
        else:
            first = name[:i]
        #
        self.parser_list = []
        self._resolve_lookups(None, name, i, end)
        #
        return (first, iter(self.parser_list))

    def _convert(self, obj, conversion):
        conv = conversion[0]
        if conv == "r":
            return repr(obj)
        elif conv == "s":
            return str(obj)
        elif conv == "a":
            return ascii(obj)
        else:
            raise ValueError("invalid conversion")

    def _render_field(self, start, end, recursive, level):
        name, conversion, spec_start = self._parse_field(start, end)
        spec = self.template[spec_start:end]
        #
        if self.parser_list is not None:
            # used from formatter_parser()
            if level == 1:    # ignore recursive calls
                startm1 = start - 1
                assert startm1 >= self.last_end
                self.parser_list.append((
                    self.template[self.last_end:startm1],
                    name,
                    spec,
                    conversion
                ))
                self.last_end = end + 1
            return self.empty
        #
        obj = self._get_argument(name)
        if conversion is not None:
            obj = self._convert(obj, conversion)
        if recursive:
            spec = self._build_string(spec_start, end, level)
        rendered = format(obj, spec)
        return rendered

    def formatter_parser(self):
        self.parser_list = []
        self.last_end = 0
        self._build_string(0, len(self.template), 2)
        #
        if self.last_end < len(self.template):
            lastentry = (
                self.template[self.last_end:],
                None,
                None,
                None
            )
            self.parser_list.append(lastentry)
        return iter(self.parser_list)


def strformat(___self, *args, **kwargs):
    template = TemplateFormatter(___self)
    return template.build(args, kwargs)


def format_map(___self, mapping):
    template = TemplateFormatter(___self)
    return template.build(None, mapping)


strformat.__name__ = 'format'
str.format = strformat
str.format_map = format_map


def count(self, sub, start=None, end=None):
    selfLeng = len(self)
    subLeng = len(sub)
    if start == None:
        start = 0
    if selfLeng == 0:
        if subLeng == 0 and start <= 0:
            return 1
        return 0
    if end == None:
        end = selfLeng
    if subLeng == 0:
        if start <= selfLeng:
            return len(self[start:end]) + 1
        return 0

    idx = self.find(sub, start, end)
    if idx < 0:
        return 0

    cnt = 1
    while idx < selfLeng and idx >= 0 and cnt < selfLeng:
        start = idx + subLeng
        idx = self.find(sub, start, end)
        if idx >= 0:
            cnt += 1
    return cnt


str.count = count


def encode(self, encoding="utf-8", errors="strict"):
    """Decode the bytes using the codec registered for encoding.

    encoding
      The encoding with which to decode the bytes.
    errors
      The error handling scheme to use for the handling of decoding errors.
      The default is 'strict' meaning that decoding errors raise a
      UnicodeDecodeError. Other possible values are 'ignore' and 'replace'
      as well as any other name registered with codecs.register_error that
      can handle UnicodeDecodeErrors.
    """
    result = _codecs.encode(self, encoding=encoding, errors=errors)
    if not isinstance(result, bytes):
        if isinstance(result, bytearray):
            return bytes(result)
        raise TypeError("'%s' encoder returned '%s' instead of 'bytes'; use codecs.encode() to encode to arbitrary types"
                        % (encoding, type(result).__name__))
    return result


str.encode = encode


def formatter_parser(string):
    return TemplateFormatter(string).formatter_parser()


def formatter_field_name_split(string):
    return TemplateFormatter(string).formatter_field_name_split()


import _string
_string.formatter_parser = formatter_parser
_string.formatter_field_name_split = formatter_field_name_split
