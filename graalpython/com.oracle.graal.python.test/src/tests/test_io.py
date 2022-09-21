# Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

import unittest
import _io


class IOBaseTests(unittest.TestCase):

    def test_iobase_ctor_accepts_anything(self):
        _io._IOBase()
        _io._IOBase(1, '2', kw=3)

    def test_close(self):
        x = _io._IOBase()
        self.assertFalse(x.closed)
        self.assertFalse('__IOBase_closed' in dir(x))
        x.close()
        self.assertTrue(x.closed)
        self.assertEqual(True, getattr(x, '__IOBase_closed'))

    def test_flush(self):
        x = _io._IOBase()
        x.flush()
        x.close()
        self.assertRaises(ValueError, x.flush)
        # the value of __IOBase_closed does not matter, only its presence
        setattr(x, '__IOBase_closed', False)
        self.assertRaises(ValueError, x.flush)
        delattr(x, '__IOBase_closed')
        x.flush()

    def test_close_calls_flush_once(self):
        flush_called = 0

        class X(_io._IOBase):
            def flush(self):
                nonlocal flush_called
                flush_called += 1

        x = X()
        x.close()
        self.assertEqual(1, flush_called)
        x.close()
        self.assertEqual(1, flush_called)
        # the value of __IOBase_closed does not matter, only its presence
        setattr(x, '__IOBase_closed', False)
        x.close()
        self.assertEqual(1, flush_called)
        delattr(x, '__IOBase_closed')
        x.close()
        self.assertEqual(2, flush_called)

    def test_close_chains_exceptions(self):
        class X(_io._IOBase):
            def flush(self):
                raise ValueError('abc')

            def __setattr__(self, key, value):
                raise ValueError('xyz')

        try:
            X().close()
            self.fail('close() did not raise an exception')
        except ValueError as e:
            self.assertEqual('xyz', e.args[0])
            self.assertEqual('abc', e.__context__.args[0])

    def test_unsupported(self):
        self.assertRaises(_io.UnsupportedOperation, _io._IOBase().seek)
        self.assertRaises(_io.UnsupportedOperation, _io._IOBase().truncate)
        self.assertRaises(_io.UnsupportedOperation, _io._IOBase().fileno)

    def test_tell_call_seek(self):
        check = self.assertEqual

        class X(_io._IOBase):
            def seek(self, *args):
                check((0, 1), args)
                return 42

        self.assertEqual(42, X().tell())

    def test_check_closed(self):
        ret_val = False

        class X(_io._IOBase):
            @property
            def closed(self):
                return ret_val

        X()._checkClosed()
        ret_val = True
        self.assertRaises(ValueError, X()._checkClosed)
        ret_val = 42    # _checkClosed accepts anything that evaluates as True
        self.assertRaises(ValueError, X()._checkClosed)
        ret_val = (1, )
        self.assertRaises(ValueError, X()._checkClosed)

    def test_check_seekable(self):
        self.assertFalse(_io._IOBase().seekable())
        ret_val = False

        class X(_io._IOBase):
            def seekable(self):
                return ret_val

        self.assertRaises(_io.UnsupportedOperation, X()._checkSeekable)
        ret_val = True
        self.assertTrue(X()._checkSeekable())
        ret_val = 42    # _checkSeekable accepts only explicit True
        self.assertRaises(_io.UnsupportedOperation, X()._checkSeekable)
        ret_val = (1, )
        self.assertRaises(_io.UnsupportedOperation, X()._checkSeekable)

    def test_check_readable(self):
        self.assertFalse(_io._IOBase().readable())
        ret_val = False

        class X(_io._IOBase):
            def readable(self):
                return ret_val

        self.assertRaises(_io.UnsupportedOperation, X()._checkReadable)
        ret_val = True
        self.assertTrue(X()._checkReadable())
        ret_val = 42    # _checkReadable accepts only explicit True
        self.assertRaises(_io.UnsupportedOperation, X()._checkReadable)
        ret_val = (1, )
        self.assertRaises(_io.UnsupportedOperation, X()._checkReadable)

    def test_check_writable(self):
        self.assertFalse(_io._IOBase().writable())
        ret_val = False

        class X(_io._IOBase):
            def writable(self):
                return ret_val

        self.assertRaises(_io.UnsupportedOperation, X()._checkWritable)
        ret_val = True
        self.assertTrue(X()._checkWritable())
        ret_val = 42    # _checkWritable accepts only explicit True
        self.assertRaises(_io.UnsupportedOperation, X()._checkWritable)
        ret_val = (1, )
        self.assertRaises(_io.UnsupportedOperation, X()._checkWritable)

    def test_check_properties(self):
        class X(_io._IOBase):
            @property
            def seekable(self):
                return True
            @property
            def readable(self):
                return True
            @property
            def writable(self):
                return True
        # _checkSeekable calls seekable(), but we define it as a property by mistake
        self.assertRaises(TypeError, X()._checkSeekable)
        self.assertRaises(TypeError, X()._checkReadable)
        self.assertRaises(TypeError, X()._checkWritable)

    def test_enter(self):
        x = _io._IOBase()
        self.assertIs(x, x.__enter__())
        x.close()
        self.assertRaises(ValueError, x.__enter__)

    def test_exit_dispatches_to_close(self):
        class X(_io._IOBase):
            def close(self):
                return 42

        self.assertEqual(42, X().__exit__())

    def test_exit_accepts_varargs(self):
        x = _io._IOBase()
        x.__exit__(1, 2, 3)
        with self.assertRaises(TypeError):
            x.__exit__(kw=1)

    def test_isatty(self):
        x = _io._IOBase()
        self.assertFalse(x.isatty())
        x.close()
        self.assertRaises(ValueError, x.isatty)

    def test_iter(self):
        x = _io._IOBase()
        self.assertIs(x, x.__iter__())
        x.close()
        self.assertRaises(ValueError, x.__iter__)

    def test_methods_do_not_dispatch_to_checkClosed(self):
        class X(_io._IOBase):
            def _checkClosed(self):
                raise NotImplementedError()

        x = X()
        self.assertIs(x, x.__enter__())
        self.assertIs(x, x.__iter__())
        self.assertFalse(x.isatty())
        x.writelines([])

    def test_next(self):
        it = iter(['aaa', 'bbb', ''])

        class X(_io._IOBase):
            def readline(self, limit=-1):
                return next(it)
        x = iter(X())
        self.assertEqual('aaa', next(x))
        self.assertEqual('bbb', next(x))
        self.assertRaises(StopIteration, next, x)

    def test_writelines(self):
        buf = []

        class X(_io._IOBase):
            def write(self, x):
                buf.append(x)
        X().writelines(['aaa', 'bbb'])
        self.assertEqual(['aaa', 'bbb'], buf)

    def test_writelines_err(self):
        self.assertRaises(AttributeError, _io._IOBase().writelines, ['aaa', 'bbb'])

    def test_bytesio_unsharing(self):
        f = _io.BytesIO()
        f.write(b"1234")
        first_pickled = f.getvalue()
        f.seek(0)
        f.truncate()
        f.write(b"1234")
        second_pickled = f.getvalue()
        f.seek(0)
        f.truncate()
        f.write(b"abcd")
        third_pickled = f.getvalue()
        self.assertEqual(first_pickled, b'1234')
        self.assertEqual(second_pickled, b'1234')
        self.assertEqual(third_pickled, b'abcd')

    def test_stringio_overwrite(self):
        s = _io.StringIO('hello')
        s.seek(2)
        s.write('ab')
        self.assertEqual('heabo', s.getvalue())

    def test_cr_not_ignored(self):
        d = _io.IncrementalNewlineDecoder(None, translate=False)
        d.decode("h\rello")
        self.assertEqual('\r', d.newlines)

    def test_cr_not_ignored2(self):
        d = _io.IncrementalNewlineDecoder(None, translate=False)
        d.decode("h\n\r")
        d.decode("\n")
        self.assertEqual(('\n', '\r\n'), d.newlines)

    def test_find_non_universal_line_ending(self):
        import io

        class MockRawIO(io.RawIOBase):
            def __init__(self):
                self.src = [b'ab\r', b'\ncd']

            def readable(self):
                return True

            def read(self, n=None):
                return self.src.pop(0) if self.src else b''

        t = _io.TextIOWrapper(MockRawIO(), newline="\r\n")
        self.assertEqual(["ab\r\n", "cd"], t.readlines())


if __name__ == '__main__':
    unittest.main()
