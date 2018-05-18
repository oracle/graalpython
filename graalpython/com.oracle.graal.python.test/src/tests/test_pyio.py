# Copyright (c) 2018, Oracle and/or its affiliates.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or data
# (collectively the "Software"), free of charge and under any and all copyright
# rights in the Software, and any and all patent rights owned or freely
# licensable by each licensor hereunder covering either (i) the unmodified
# Software as contributed to or provided by such licensor, or (ii) the Larger
# Works (as defined below), to deal in both
#
# (a) the Software, and
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
#     one is included with the Software (each a "Larger Work" to which the
#     Software is contributed by such licensors),
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


def assert_raises(err, fn, *args, **kwargs):
    raised = False
    try:
        fn(*args, **kwargs)
    except err:
        raised = True
    assert raised


def unlink(file_name):
    from test import support
    try:
        support.unlink(file_name)
    except OSError:
        pass


def test_import():
    pass


def test_read_write_file():
    import _pyio as pyio # Python implementation.

    file_name = "dump.txt"

    def try_one(s):
        unlink(file_name)
        f = pyio.open(file_name, "wb")
        try:
            # write once with \n and once without
            f.write(s)
            f.write(b"\n")
            f.write(s)
            f.close()

            f = pyio.open(file_name, "rb")
            line = f.readline()
            assert line == s + b"\n"

            line = f.readline()
            assert line == s

            line = f.readline()
            assert not line # Must be at EOF

            f.close()
        finally:
            unlink(file_name)

    try_one(b"1234567890")
    try_one(b"hello_world_12345")
    try_one(b'\0' * 1000)



def test_file():
    import _pyio as pyio # Python implementation.
    file_name = "dump.txt"

    unlink(file_name)
    try:
        # def testWeakRefs(self):
        # verify weak references
        from array import array
        from weakref import proxy
        from collections import UserList

        f = pyio.open(file_name, "wb")

        p = proxy(f)
        p.write(b'teststring')
        assert f.tell() == p.tell()
        f.close()
        f = None
        # breakpoint()
        # TODO: since weakref is not yet properly implemenmted this will not work
        # assert_raises(ReferenceError, getattr, p, 'tell')

        # def testAttributes(self):
        # verify expected attributes exist
        f = pyio.open(file_name, "wb")
        f.name     # merely shouldn't blow up
        f.mode     # ditto
        f.closed   # ditto
        f.close()

        # def testWritelinesUserList(self):
        # verify writelines with instance sequence
        f = pyio.open(file_name, "wb")
        l = UserList([b'1', b'2'])
        f.writelines(l)
        f.close()
        f = pyio.open(file_name, 'rb')
        buf = f.read()
        assert buf == b'12'
        f.close()

        # def testWritelinesIntegers(self):
        # verify writelines with integers
        f = pyio.open(file_name, "wb")
        assert_raises(TypeError, f.writelines, [1, 2, 3])
        f.close()

        # def testWritelinesIntegersUserList(self):
        # verify writelines with integers in UserList
        f = pyio.open(file_name, "wb")
        l = UserList([1,2,3])
        assert_raises(TypeError, f.writelines, l)
        f.close()

        # def testWritelinesNonString(self):
        # verify writelines with non-string object
        class NonString:
            pass

        f = pyio.open(file_name, "wb")
        assert_raises(TypeError, f.writelines, [NonString(), NonString()])
        f.close()

        # def testErrors(self):
        f = pyio.open(file_name, "wb")
        assert f.name == file_name
        assert not f.isatty()
        assert not f.closed

        f.close()
        assert f.closed
    finally:
        unlink(file_name)
