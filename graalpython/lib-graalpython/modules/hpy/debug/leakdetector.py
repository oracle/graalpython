# MIT License
# 
# Copyright (c) 2021, Oracle and/or its affiliates.
# Copyright (c) 2019 pyhandle
# 
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
# 
# The above copyright notice and this permission notice shall be included in all
# copies or substantial portions of the Software.
# 
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.

from hpy.universal import _debug

class HPyDebugError(Exception):
    pass

class HPyLeakError(HPyDebugError):
    def __init__(self, leaks):
        super().__init__()
        self.leaks = leaks

    def __str__(self):
        lines = []
        n = len(self.leaks)
        s = 's' if n != 1 else ''
        lines.append(f'{n} unclosed handle{s}:')
        for dh in self.leaks:
            lines.append('    %r' % dh)
        return '\n'.join(lines)


class LeakDetector:

    def __init__(self):
        self.generation = None

    def start(self):
        if self.generation is not None:
            raise ValueError('LeakDetector already started')
        self.generation = _debug.new_generation()

    def stop(self):
        if self.generation is None:
            raise ValueError('LeakDetector not started yet')
        leaks = _debug.get_open_handles(self.generation)
        if leaks:
            raise HPyLeakError(leaks)

    def __enter__(self):
        self.start()
        return self

    def __exit__(self, etype, evalue, tb):
        self.stop()
