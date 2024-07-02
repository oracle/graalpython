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

import os
import unittest
from unittest import skipIf, skipUnless

import sys

if sys.implementation.name == "graalpy":
    import polyglot

    try:
        polyglot.eval(language="ruby", string="1")
        polyglot.eval(language="js", string="1")
    except:
        test_polyglot_languages = False
    else:
        test_polyglot_languages = True
else:
    test_polyglot_languages = False

if sys.implementation.name == "graalpy":
    class GetterOnly():
        def __get__(self, instance, owner):
            pass

    class CustomObject():
        field = 42

        def __getitem__(self, item):
            return item * 2

        def __len__(self):
            return 21

        getter = GetterOnly()

        @property
        def setter(self):
            pass

        @setter.setter
        def setter_setter(self):
            pass

    class CustomMutable(CustomObject):
        _items = {}

        def keys(self):
            return self._items.keys()

        def items(self):
            return self._items.items()

        def values(self):
            return self._items.values()

        def __getitem__(self, key):
            return self._items[key]

        def __setitem__(self, key, item):
            self._items[key] = item

        def __delitem__(self, key):
            del self._items[key]

    class PyString(str):
        pass

@skipUnless(sys.implementation.name == "graalpy", "interop")
class InteropTests(unittest.TestCase):
    def test_import(self):
        def some_function():
            return "hello, polyglot world!"
        polyglot.export_value(some_function)
        imported_fun0 = polyglot.import_value("some_function")
        assert imported_fun0 is some_function
        assert imported_fun0() == "hello, polyglot world!"

        polyglot.export_value("same_function", some_function)
        imported_fun1 = polyglot.import_value("same_function")
        assert imported_fun1 is some_function
        assert imported_fun1() == "hello, polyglot world!"

    def test_read(self):
        o = CustomObject()
        assert polyglot.__read__(o, "field") == o.field
        assert polyglot.__read__(o, 10) == o[10]

    def test_write(self):
        o = CustomMutable()
        o2 = CustomObject()

        polyglot.__write__(o, "field", 32)
        assert o.field == 32

        polyglot.__write__(o, "__getattribute__", 321)
        assert o.__getattribute__ == 321

        polyglot.__write__(o, "grrrr", 42)
        assert hasattr(o, "grrrr")
        polyglot.__write__(o2, "grrrr", 42)
        assert o2.grrrr == 42

        try:
            non_string = bytearray(b"a fine non-string object we have here")
            polyglot.__write__(o, non_string, 12)
        except AttributeError:
            assert True
        else:
            assert False

    def test_remove(self):
        o = CustomMutable()
        o.direct_field = 111

        polyglot.__remove__(o, "direct_field")
        assert not hasattr(o, "direct_field")

        o.direct_field = 12
        o["direct_field"] = 32
        assert "direct_field" in list(o.keys())


    def test_execute(self):
        assert polyglot.__execute__(abs, -10) == 10
        o = CustomMutable()
        assert polyglot.__execute__(o.__getattribute__, "field") == o.field

    def test_invoke(self):
        o = CustomMutable()
        assert polyglot.__invoke__(o, "__getattribute__", "field") == o.field

    def test_new(self):
        assert isinstance(polyglot.__new__(CustomMutable), CustomMutable)

    def test_is_null(self):
        assert polyglot.__is_null__(None)

    def test_has_size(self):
        import array

        assert polyglot.__has_size__((0,))
        assert polyglot.__has_size__([])
        assert polyglot.__has_size__(array.array('b'))
        assert polyglot.__has_size__(bytearray(b""))
        assert polyglot.__has_size__(b"")
        assert polyglot.__has_size__(PyString(""))
        assert polyglot.__has_size__(range(10))
        assert polyglot.__has_size__(CustomObject())

        assert not polyglot.__has_size__(object())

    def test_get_size(self):
        called = False

        class LenObject():

            def __getitem__(self, k):
                if k == 0:
                    return 1
                else:
                    raise IndexError

            def __len__(self):
                nonlocal called
                called = True
                return 1

        assert polyglot.__get_size__(LenObject()) == 1
        assert called

    def test_has_keys(self):
        assert not polyglot.__has_keys__(True)
        assert polyglot.__has_keys__(None)
        assert polyglot.__has_keys__(NotImplemented)
        assert not polyglot.__has_keys__(False)
        assert polyglot.__has_keys__(object())

    def test_keys(self):
        o = CustomObject()
        inherited_keys = len(polyglot.__keys__(o))
        o.my_field = 1
        assert len(polyglot.__keys__(o)) == 1 + inherited_keys
        assert "my_field" in polyglot.__keys__(o)

    def test_key_info(self):
        o = CustomObject()
        o.my_field = 1
        o.test_exec = lambda: False

        assert polyglot.__key_info__(o, "__len__", "readable")
        assert polyglot.__key_info__(o, "__len__", "invokable")
        assert polyglot.__key_info__(o, "__len__", "modifiable")
        assert polyglot.__key_info__(o, "__len__", "removable")
        assert not polyglot.__key_info__(o, "__len__", "insertable")

        assert polyglot.__key_info__(o, "test_exec", "readable")
        assert polyglot.__key_info__(o, "test_exec", "invokable")
        assert polyglot.__key_info__(o, "test_exec", "modifiable")
        assert polyglot.__key_info__(o, "test_exec", "removable")
        assert not polyglot.__key_info__(o, "test_exec", "insertable")

        assert polyglot.__key_info__(o, "my_field", "readable")
        assert not polyglot.__key_info__(o, "my_field", "invokable")
        assert polyglot.__key_info__(o, "my_field", "modifiable")
        assert polyglot.__key_info__(o, "my_field", "removable")
        assert not polyglot.__key_info__(o, "my_field", "insertable")

        assert polyglot.__key_info__(o, "__getattribute__", "readable")
        assert polyglot.__key_info__(o, "__getattribute__", "invokable")
        assert not polyglot.__key_info__(o, "__getattribute__", "removable")
        assert not polyglot.__key_info__(o, "__getattribute__", "insertable")

        builtinObj = (1,2,3)
        assert polyglot.__key_info__(builtinObj, "__len__", "readable")
        assert polyglot.__key_info__(builtinObj, "__len__", "invokable")
        assert not polyglot.__key_info__(builtinObj, "__len__", "modifiable")
        assert not polyglot.__key_info__(builtinObj, "__len__", "removable")
        assert not polyglot.__key_info__(builtinObj, "__len__", "insertable")

    def test_java_classpath(self):
        import java
        try:
            java.add_to_classpath(1)
        except TypeError as e:
            assert "classpath argument 1 must be string, not int" in str(e)

        try:
            java.add_to_classpath('a', 1)
        except TypeError as e:
            assert "classpath argument 2 must be string, not int" in str(e)

    def test_host_lookup(self):
        import java
        strClass = java.type("java.lang.String")
        assert strClass.valueOf(True) == "true"

        try:
            java.type("this.type.does.not.exist")
        except KeyError as e:
            assert True
        else:
            assert False, "requesting a non-existing host symbol should raise KeyError"

    def test_internal_languages_dont_eval(self):
        try:
            polyglot.eval(language="nfi", string="default")
        except ValueError as e:
            assert str(e) == "polyglot language 'nfi' not found"

        assert polyglot.eval(language="python", string="21 * 2") == 42

    def test_module_eval_returns_last_expr(self):
        assert polyglot.eval(language="python", string="x = 2; x") == 2

    def test_module_eval_returns_module(self):
        mod = polyglot.eval(language="python", string="x = 2")
        assert mod.x == 2
        assert type(mod) == type(sys)

    def test_non_index_array_access(self):
        import java
        try:
            al = java.type("java.util.ArrayList")()
            assert al.size() == len(al) == 0
        except IndexError:
            assert False, "using __getitem__ to access keys of an array-like foreign object should work"

    def test_direct_call_of_truffle_object_methods(self):
        import java
        try:
            al = java.type("java.util.ArrayList")()
            assert al.__len__() == al.size() == len(al)
        except IndexError:
            assert False, "calling the python equivalents for well-known functions directly should work"

    def test_array_element_info(self):
        immutableObj = (1,2,3,4)
        assert polyglot.__element_info__(immutableObj, 0, "exists")
        assert polyglot.__element_info__(immutableObj, 0, "readable")
        assert not polyglot.__element_info__(immutableObj, 0, "removable")
        assert not polyglot.__element_info__(immutableObj, 0, "writable")
        assert not polyglot.__element_info__(immutableObj, 0, "insertable")
        assert not polyglot.__element_info__(immutableObj, 0, "modifiable")
        assert not polyglot.__element_info__(immutableObj, 4, "insertable")

        mutableObj = [1,2,3,4]
        assert polyglot.__element_info__(mutableObj, 0, "exists")
        assert polyglot.__element_info__(mutableObj, 0, "readable")
        assert polyglot.__element_info__(mutableObj, 0, "removable")
        assert polyglot.__element_info__(mutableObj, 0, "writable")
        assert not polyglot.__element_info__(mutableObj, 0, "insertable")
        assert polyglot.__element_info__(mutableObj, 0, "modifiable")
        assert polyglot.__element_info__(mutableObj, 4, "insertable")

    def test_java_imports(self):
        import java
        al = java.type("java.util.ArrayList")()
        import java.util.ArrayList
        assert repr(java.util.ArrayList()) == "[]"

        from java.util import ArrayList
        assert repr(ArrayList()) == "[]"

        assert java.util.ArrayList == ArrayList

        if __graalpython__.jython_emulation_enabled:
            import sun
            assert type(sun.misc) is type(java)

            import sun.misc.Signal
            assert sun.misc.Signal is not None

    def test_java_import_from_jar(self):
        if __graalpython__.jython_emulation_enabled:
            import tempfile
            import zipfile

            # import a single file with jar!prefix/
            tempname = tempfile.mktemp() + ".jar"
            with zipfile.ZipFile(tempname, mode="w") as z:
                with z.open("scriptDir/test_java_jar_import.py", mode="w") as member:
                    member.write(b"MEMBER = 42\n")
            try:
                sys.path.append(tempname + "!scriptDir")
                try:
                    import test_java_jar_import
                    assert test_java_jar_import.MEMBER == 42
                    assert test_java_jar_import.__path__ == tempname + "/scriptDir/test_java_jar_import.py"
                finally:
                    sys.path.pop()
            finally:
                os.unlink(tempname)

            # import a single file with jar!/prefix/
            tempname = tempfile.mktemp() + ".jar"
            with zipfile.ZipFile(tempname, mode="w") as z:
                with z.open("scriptDir/test_java_jar_import_2.py", mode="w") as member:
                    member.write(b"MEMBER = 43\n")
            try:
                sys.path.append(tempname + "!/scriptDir")
                try:
                    import test_java_jar_import_2
                    assert test_java_jar_import_2.MEMBER == 43
                    assert test_java_jar_import_2.__path__ == tempname + "/scriptDir/test_java_jar_import_2.py"
                finally:
                    sys.path.pop()
            finally:
                os.unlink(tempname)

            # import a package with jar!/prefix/
            tempname = tempfile.mktemp() + ".jar"
            with zipfile.ZipFile(tempname, mode="w") as z:
                with z.open("scriptDir/test_java_jar_pkg/__init__.py", mode="w") as member:
                    member.write(b"MEMBER = 44\n")
            try:
                sys.path.append(tempname + "!/scriptDir")
                try:
                    import test_java_jar_pkg
                    assert test_java_jar_pkg.MEMBER == 44
                    assert test_java_jar_pkg.__path__ == tempname + "/scriptDir/test_java_jar_pkg/__init__.py"
                finally:
                    sys.path.pop()
            finally:
                os.unlink(tempname)

    def test_java_class(self):
        from java.lang import Integer, NumberFormatException
        self.assertEqual(['ForeignInstantiableType', 'foreign', 'object'], [t.__name__ for t in type(Integer).mro()])
        self.assertEqual(['ForeignInstantiableType', 'foreign', 'object'], [t.__name__ for t in type(NumberFormatException).mro()])

    def test_java_exceptions(self):
        # TODO: more tests

        from java.lang import Integer, NumberFormatException
        try:
            Integer.parseInt("99", 8)
        except NumberFormatException as e:

            assert isinstance(e, BaseException)
            assert BaseException in type(e).mro()
            self.assertEqual(['ForeignException', 'BaseException', 'foreign', 'object'], [t.__name__ for t in type(e).mro()])
            self.assertEqual('java.lang.NumberFormatException: For input string: \"99\" under radix 8', str(e))
            self.assertEqual("ForeignException('java.lang.NumberFormatException: For input string: \"99\" under radix 8')", repr(e))
            assert True
        else:
            assert False

    # TODO: this currently shows no stacktrace at all, which is quite bad to find out the issue
    # it doesn't even show the Python line on which the error ocurred in this file
    # def test_java_exceptions_stacktrace(self):
    #     from java.lang import Integer
    #     Integer.parseInt("99", 8)

    def test_java_exceptions_reraise(self):
        from java.lang import Integer, NumberFormatException
        try:
            try:
                Integer.parseInt("99", 8)
            except NumberFormatException:
                raise
        except NumberFormatException:
            pass
        else:
            assert False

    def test_java_exceptions_reraise_explicit(self):
        from java.lang import Integer, NumberFormatException
        try:
            try:
                Integer.parseInt("99", 8)
            except NumberFormatException as e:
                raise e
        except NumberFormatException:
            pass
        else:
            assert False

    def test_java_exception_state(self):
        from java.lang import Integer, NumberFormatException
        try:
            Integer.parseInt("99", 8)
        except NumberFormatException as e:
            assert sys.exc_info() == (type(e), e, None)
        else:
            assert False

    def test_foreign_object_does_not_leak_Javas_toString(self):
        from java.util import ArrayList
        try:
            ArrayList(12, "12")
        except TypeError as e:
            assert "@" not in str(e) # the @ from Java's default toString

        try:
            ArrayList(12, foo="12") # keywords are not supported
        except TypeError as e:
            assert "@" not in str(e) # the @ from Java's default toString

        try:
            ArrayList.bar
        except AttributeError as e:
            assert "@" not in str(e) # the @ from Java's default toString

        try:
            del ArrayList.bar
        except AttributeError as e:
            assert "@" not in str(e) # the @ from Java's default toString

        try:
            del ArrayList.bar
        except AttributeError as e:
            assert "@" not in str(e) # the @ from Java's default toString

    def test_java_import_star(self):
        if __graalpython__.jython_emulation_enabled:
            d = {}
            exec("from java.util.logging.Logger import *", globals=d, locals=d)
            assert "getGlobal" in d
            assert d["getGlobal"]().getName() == d["GLOBAL_LOGGER_NAME"]

    def test_java_null_is_none(self):
        import java.lang.Integer as Integer
        x = Integer.getInteger("something_that_does_not_exists")
        y = Integer.getInteger("something_that_does_not_exists2")
        z = None

        assert isinstance(x, type(None))
        assert type(x) == polyglot.ForeignNone, type(x)
        assert type(None) in type(x).mro()
        self.assertEqual(['ForeignNone', 'NoneType', 'foreign', 'object'], [t.__name__ for t in type(x).mro()])
        assert repr(x) == 'None'
        assert str(x) == 'None'

        assert bool(x) == False

        assert x == None
        assert (x != None) == False
        assert x is None
        assert (x is not None) == False

        assert x == y
        assert (x != y) == False
        assert x is y
        assert (x is not y) == False

        assert x == z
        assert (x != z) == False
        assert x is z
        assert (x is not z) == False

    def test_isinstance01(self):
        import java.lang.Integer as Integer
        i = Integer(1)
        assert isinstance(i, Integer)

    def test_isinstance02(self):
        import java.util.Map as Map
        import java.util.HashMap as HashMap
        h = HashMap()
        assert isinstance(h, HashMap)
        assert isinstance(h, Map)

    def test_is_type(self):
        import java
        from java.util.logging import Handler
        from java.util import Set
        from java.util.logging import LogRecord
        from java.util.logging import Level

        assert java.is_type(Handler)
        assert java.is_type(LogRecord)
        assert java.is_type(Set)
        assert java.is_type(Level)

        lr = LogRecord(Level.ALL, "message")
        assert not java.is_type(lr)
        assert not java.is_type(Level.ALL)
        assert not java.is_type("ahoj")

    def test_extend_java_class_01(self):
        from java.util.logging import Handler
        from java.util.logging import LogRecord
        from java.util.logging import Level

        lr = LogRecord(Level.ALL, "The first message")

        # extender object
        class MyHandler (Handler):
            "This is MyHandler doc"
            counter = 0;
            def isLoggable(self, logrecord):
                self.counter = self.counter + 1
                return self.__super__.isLoggable(logrecord)
            def sayHello(self):
                return 'Hello'

        h = MyHandler()

        # accessing extender object via this property
        assert hasattr(h, 'this')
        assert hasattr(h.this, 'sayHello')
        assert hasattr(h.this, 'counter')
        assert hasattr(h.this, 'isLoggable')

        #accessing java methods or methods from extender object directly
        assert hasattr(h, 'close')
        assert hasattr(h, 'flush')
        assert hasattr(h, 'getEncoding')
        assert hasattr(h, 'setEncoding')


        assert h.this.counter == 0
        assert h.isLoggable(lr)
        assert h.this.counter == 1
        assert h.isLoggable(lr)
        assert h.isLoggable(lr)
        assert h.this.counter == 3

        assert 'Hello' == h.this.sayHello()

        h2 = MyHandler()
        assert h2.this.counter == 0
        assert h2.isLoggable(lr)
        assert h2.this.counter == 1
        assert h.this.counter == 3

    def test_extend_java_class_02(self):
        from java.math import BigDecimal
        try:
            class MyDecimal(BigDecimal):
                pass
        except TypeError:
            assert True
        else:
            assert False

    def test_extend_java_class_03(self):
        #test of java constructor
        from java.util.logging import LogRecord
        from java.util.logging import Level

        class MyLogRecord(LogRecord):
            def getLevel(self):
                if self.__super__.getLevel() == Level.FINEST:
                    self.__super__.setLevel(Level.WARNING)
                return self.__super__.getLevel()

        message = "log message"
        my_lr1 = MyLogRecord(Level.WARNING, message)
        assert my_lr1.getLevel() == Level.WARNING
        assert my_lr1.getMessage() == message

        my_lr2 = MyLogRecord(Level.FINEST, message)
        assert my_lr2.getLevel() == Level.WARNING

    def test_java_array(self):
        import java
        il = java.type("int[]")(20)

        assert isinstance(il, list)
        assert list in type(il).mro()
        self.assertEqual(['ForeignList', 'list', 'foreign', 'object'], [t.__name__ for t in type(il).mro()])
        assert repr(il) == repr([0] * 20)
        assert str(il) == str([0] * 20)

        assert il[0] == 0

        il[0] = 12
        assert il[0] == 12

        il[0:10] = [10] * 10
        assert list(il) == [10] * 10 + [0] * 10, "not equal"

        il = java.type("int[]")(3)
        il[0:3] = [1,2,3]

        try:
            il[0:2] = 1
        except TypeError:
            assert True
        else:
            assert False, "should throw TypeError: 'int' object is not iterable"

        try:
            il[0] = 1.2
        except TypeError:
            assert True
        else:
            assert False, "should throw a type error again"

        try:
            il.clear()
        except IndexError as e:
            assert "is not removable" in str(e)
        else:
            assert False, "should have thrown"

        try:
            il.insert(1, 42)
        except IndexError as e:
            assert str(e) == "invalid index 3", str(e)
        else:
            assert False, "should have thrown"

        assert il == [1, 2, 3] # unchanged

    def test_java_list(self):
        from java.util import ArrayList

        al = ArrayList()

        def l(*elements):
            mylist = ArrayList()
            mylist.extend(elements)
            return mylist

        assert isinstance(al, list)
        assert list in type(al).mro()
        self.assertEqual(['ForeignList', 'list', 'foreign', 'object'], [t.__name__ for t in type(al).mro()])
        assert repr(l(1,2)) == repr([1,2])
        assert str(l(1,2)) == str([1,2])

        assert bool(l()) == False
        assert bool(l(1)) == True

        al.append(42)
        assert al[0] == 42
        assert len(al) == 1
        al[0] = 43
        assert al[0] == 43

        al.extend([44, 45])
        assert len(al) == 3

        al += [46, 47]
        assert len(al) == 5

        al[0:3] = [1,2,3]
        assert len(al) == 5, al
        assert al[0:3] == [1,2,3]

        al2 = ArrayList()
        al2[0:3] = [1,2,3]
        assert al2 == [1,2,3], al2

        al.add(-1) # ArrayList#add()
        assert len(al) == 6

        copy = al[:]
        assert type(copy) == list
        assert copy == [1,2,3,46,47,-1], copy

        # Ensure al.clear() is list.clear and not ArrayList#clear
        self.assertEqual(list.clear.__get__(al), al.clear)
        al.clear()
        assert len(al) == 0
        assert al == []
        assert not al

        al += [1, 2, 3]
        assert al == [1, 2, 3], al

        add1 = al + [4, 5]
        assert add1 == [1, 2, 3, 4, 5], add1
        assert type(add1) is list, type(add1)

        add2 = [-1, 0] + al
        assert add2 == [-1, 0, 1, 2, 3], add2
        assert type(add2) is list

        # al is unchanged:
        assert al == [1, 2, 3], al

        al.reverse()
        assert al[-1] == 1

        # Test list.__eq__ in both directions
        assert al == [3, 2, 1], al
        assert [3, 2, 1] == al
        assert al != [1]
        assert (al != [3, 2, 1]) == False

        assert l(1) < l(2)
        assert [1] < l(2)
        assert l(1) < [2]

        assert (l(1) < l(1)) == False
        assert ([1] < l(1)) == False
        assert (l(1) < [1]) == False

        assert l(1) <= l(1)
        assert (l(1) <= l(0)) == False
        assert l(1) >= l(1)
        assert (l(0) >= l(1)) == False
        assert l(2) > l(1)
        assert (l(1) > l(1)) == False

        assert l(1,2,1).count(1) == 2

        al = l(1,2,3,4)
        del al[3]
        assert al == [1,2,3]
        del al[0]
        assert al == [2,3]
        al = l(1,2,3,4)
        del al[1:3]
        assert al == [1,4]

        al.__init__()
        assert al == []
        al.__init__([1, 2])
        assert al == [1, 2], al
        al.__init__((i for i in [3,4]))
        assert al == [3, 4]
        al.__init__("ab")
        assert al == ["a", "b"]

        al.__init__([1, 2])
        copy = al.copy()
        assert copy == [1, 2]
        assert type(copy) == list, type(copy)

        al = l(1,2,3)
        al.insert(1, 7)
        assert al == [1,7,2,3]
        al.insert(-1, 8)
        assert al == [1,7,2,8,3], al

        # Ensure al.remove() is list.remove and not ArrayList#remove
        self.assertEqual(list.remove.__get__(al), al.remove)
        # Use values which would throw if ArrayList#remove is used
        al.remove(8)
        al.remove(7)
        assert al == [1,2,3]

        assert al.pop() == 3
        assert al == [1,2]
        assert al.pop(0) == 1
        assert al == [2]

        al = l(1,2,3)
        assert 2 in al
        assert (42 in al) == False

        assert al.index(3) == 2
        self.assertRaises(ValueError, lambda: al.index(42))
        assert al.index(2, 0, 2) == 1
        self.assertRaises(ValueError, lambda: al.index(2, 0, 1))

        al = l(4,1,3,2)
        al.sort() # to avoid conflict with ArrayList#sort
        assert al == [1,2,3,4]

        assert l(1,2) * 0 == []
        assert l(1,2) * 3 == [1,2,1,2,1,2]
        assert 3 * l(1,2) == [1,2,1,2,1,2]
        al = l(1,2)
        al *= 3
        assert al == [1,2,1,2,1,2]

        al = l(1,2,3)
        r = list(reversed(al))
        assert r == [3,2,1], r
        assert [e for e in reversed(al)] == [3,2,1]
        assert [e for e in al] == [1,2,3]

    def test_java_map(self):
        from java.util import HashMap
        h = HashMap()
        h[1] = 2

        assert isinstance(h, dict)
        assert dict in type(h).mro()
        self.assertEqual(['ForeignDict', 'dict', 'foreign', 'object'], [t.__name__ for t in type(h).mro()])
        assert repr(h) == repr({1: 2})
        assert str(h) == str({1: 2}), str(h)
        assert h

        assert h[1] == 2
        with self.assertRaisesRegex(KeyError, '42'):
            h[42]

        assert len(h) == 1

        self.assertEqual([1], [k for k in h])

        del h[1]
        assert not h
        assert len(h) == 0

        h[1] = 2
        assert h.pop(1) == 2
        assert h.pop(42, 43) == 43

        h[1] = 2
        assert h.setdefault(3, 4) == 4
        assert h.setdefault(1, 42) == 2
        self.assertEqual('{1: 2, 3: 4}', repr(h))

        h.clear()
        assert not h
        assert len(h) == 0
        self.assertEqual('{}', repr(h))

        h[1] = 2
        assert 1 in h
        assert 2 not in h

        assert h == {1: 2}
        assert {1: 2} == h
        assert not (h == {})
        assert not ({} == h)

        assert h != {}
        assert {} != h
        assert not (h != {1: 2})
        assert not ({1: 2} != h)

        self.assertRaises(TypeError, lambda: h < {})
        self.assertRaises(TypeError, lambda: {} < h)
        self.assertRaises(TypeError, lambda: h <= {})
        self.assertRaises(TypeError, lambda: {} <= h)
        self.assertRaises(TypeError, lambda: h >= {})
        self.assertRaises(TypeError, lambda: {} >= h)
        self.assertRaises(TypeError, lambda: h > {})
        self.assertRaises(TypeError, lambda: {} > h)

        assert (h | {3: 4}) == {1: 2, 3: 4}
        assert type(h | {3: 4}) == dict
        assert ({-1: 0} | h) == {-1: 0, 1: 2}

        h |= {3: 4}
        assert h == {1: 2, 3: 4}
        h |= {1: 42, 3: 5}
        assert h == {1: 42, 3: 5}

        with self.assertRaisesRegex(TypeError, 'foreign object cannot be iterated in reverse'):
            reversed(h)

        copy = h.copy()
        assert copy is not h
        assert copy == h
        assert h == copy

        assert h.get(1) == 42
        assert h.get(1, "missing") == 42
        assert h.get(14, "missing") == "missing"
        assert h.get(14) == None

        h.clear()
        h |= {1: 2, 3: 4}

        self.assertEqual([1, 3], list(h.keys()))
        self.assertEqual([2, 4], list(h.values()))
        self.assertEqual([(1, 2), (3, 4)], list(h.items()))

        with self.assertRaisesRegex(TypeError, 'foreign object cannot be iterated in reverse'):
            h.popitem()

        h.clear()
        h.update({ 5: 6, 7: 8 })
        assert h == { 5: 6, 7: 8 }
        h.update({ 5: 66 })
        assert h == { 5: 66, 7: 8 }
        h.update(h)
        assert h == { 5: 66, 7: 8 }
        d = {}
        d.update(h)
        assert d == { 5: 66, 7: 8 }, d

        h.clear()
        h.__init__({1: 2, 3: 4})
        assert h == {1: 2, 3: 4}
        h.__init__({3: 42, 5: 6})
        assert h == {1: 2, 3: 42, 5: 6}

        d = {}
        d.__init__(a=1, b=2)
        assert d == {'a': 1, 'b': 2}

        h.clear()
        h.__init__(a=1, b=2)
        assert h == {'a': 1, 'b': 2}

        with self.assertRaisesRegex(TypeError, 'invalid instantiation of foreign object'):
            type(h).fromkeys(['a', 'b'], 42)

    def test_java_map_as_keywords(self):
        from java.util import HashMap, LinkedHashMap

        def foo(a, b):
            return [a, b]

        h = HashMap()
        h.__init__({'a': 1, 'b': 2})
        assert list(h.keys()) == ['a', 'b']
        assert foo(**h) == [1, 2]

        # LinkedHashMap to preserve insertion ordering for these 2 examples
        h = LinkedHashMap()
        h.__init__({'a': 1, 'b': 2})
        assert list(h.keys()) == ['a', 'b']
        assert foo(**h) == [1, 2]

        h = LinkedHashMap()
        h.__init__({'b': 2, 'a': 1})
        assert list(h.keys()) == ['b', 'a']
        assert foo(**h) == [1, 2]

    def test_foreign_repl(self):
        from java.util.logging import LogRecord
        from java.util.logging import Level

        lr = LogRecord(Level.ALL, "message")
        assert repr(LogRecord).startswith('<JavaClass[java.util.logging.LogRecord] at')
        assert repr(lr).startswith('<JavaObject[java.util.logging.LogRecord] at')

        from java.lang import Integer
        i = Integer('22')
        assert repr(Integer).startswith('<JavaClass[java.lang.Integer] at')
        assert repr(i) == '22'

    def test_jython_star_import(self):
        if __graalpython__.jython_emulation_enabled:
            g = {}
            exec('from java.lang.Byte import *', g)
            assert type(g['MAX_VALUE']) is int

    def test_jython_accessors(self):
        if __graalpython__.jython_emulation_enabled:
            from java.util.logging import LogRecord
            from java.util.logging import Level
            lr = LogRecord(Level.ALL, "message")

            assert lr.message == "message"
            lr.message = "new message"
            assert lr.message == "new message"

    @skipUnless(test_polyglot_languages, "tests other language access")
    def test_doctest(self):
        import doctest

        class Example(doctest.Example):
            """
            Subclass of doctest.Example that accepts the end of
            markdown code blocks as end of examples.
            """
            def __init__(self, source, want, *args, **kwargs):
                want = want.rstrip("```\n")
                super(Example, self).__init__(source, want, *args, **kwargs)
        doctest.Example = Example

        assert doctest.testmod(m=polyglot, verbose=getattr(unittest, "verbose"), optionflags=doctest.ELLIPSIS).failed == 0
