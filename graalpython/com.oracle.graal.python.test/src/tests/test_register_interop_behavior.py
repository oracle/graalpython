# Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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


import sys
import unittest

EXECUTE_TESTS = sys.implementation.name == "graalpy" and not __graalpython__.is_native

if EXECUTE_TESTS:
    import polyglot
    import java
    import __graalpython__

    from java.util import ArrayList


    class TestBasicInteropRegistryBehavior(unittest.TestCase):
        def tearDown(self):
            __graalpython__.clear_interop_type_registry()

        def test_java_interop_assertions(self):
            """
            Test if registering java class and calling it works
            """
            import java

            class jList:
                def custom_append(self, element):
                    self.add(element)

                # Overwrite default list behavior
                def __len__(self):
                    return 42

                def __getitem__(self, item):
                    return 42

            polyglot.register_interop_type(ArrayList, jList)
            l = ArrayList()
            self.assertIsInstance(l, jList)

            l.custom_append(1)
            self.assertEqual(len(l), 42)
            self.assertEqual(l.size(), 1)
            self.assertEqual(l[0], 42)
            self.assertEqual(l.get(0), 1)

        def test_java_interop_decorator_assertions(self):
            """
            Test if registering with the decorator function works
            """
            import java

            @polyglot.interop_type(ArrayList)
            class jList:
                pass

            l = ArrayList()
            self.assertIsInstance(l, jList)

        def test_java_interop_overwrite_assertions(self):
            """
            Test if method overwriting works
            """

            class jList:
                def name(self):
                    return "jList"

            class jList2:
                def name(self):
                    return "jList2"

            l = ArrayList()

            polyglot.register_interop_type(ArrayList, jList)
            with self.assertRaises(KeyError):
                polyglot.register_interop_type(ArrayList, jList)

            self.assertIsInstance(l, jList)
            self.assertEqual(l.name(), "jList")

            with self.assertRaises(AttributeError):
                polyglot.register_interop_type(ArrayList, jList2)

            self.assertEqual(l.name(), "jList")

            polyglot.register_interop_type(ArrayList, jList2, allow_method_overwrites=True)
            self.assertIsInstance(l, jList)
            self.assertIsInstance(l, jList2)
            self.assertEqual(l.name(), "jList2")
            self.assertEqual(type(l).mro()[1:3], [jList2, jList])

            # Test if overwrite flag works in decorator function too
            @polyglot.interop_type(ArrayList)
            class jList3:
                def dec_name(self):
                    return "jList3"

            self.assertIsInstance(l, jList)
            self.assertIsInstance(l, jList2)
            self.assertIsInstance(l, jList3)
            self.assertEqual(l.name(), "jList2")
            self.assertEqual(l.dec_name(), "jList3")

            with self.assertRaises(AttributeError):
                @polyglot.interop_type(ArrayList)
                class jList4:
                    def dec_name(self):
                        return "jList4"

            self.assertEqual(l.dec_name(), "jList3")

            @polyglot.interop_type(ArrayList, allow_method_overwrites=True)
            class jList4:
                def dec_name(self):
                    return "jList4"

            self.assertIsInstance(l, jList)
            self.assertIsInstance(l, jList2)
            self.assertIsInstance(l, jList3)
            self.assertIsInstance(l, jList4)
            self.assertEqual(l.name(), "jList2")
            self.assertEqual(l.dec_name(), "jList4")
            self.assertEqual(type(l).mro()[1:5], [jList4, jList3, jList2, jList])

        def test_multiple_decorator(self):
            @polyglot.interop_type(java.util.LinkedList)
            @polyglot.interop_type(ArrayList)
            class jList:
                def name(self):
                    return "python list"

            l_list = java.util.LinkedList()
            a_list = ArrayList()

            self.assertEqual(l_list.name(), "python list")
            self.assertEqual(a_list.name(), "python list")

    from java.io import BufferedReader
    from java.io import StringReader
    from java.io import Reader
    from java.lang import AutoCloseable

    test_string = """Lorem
        ipsum
        dolor
        sit
        amet,
        consectetur
        adipiscing
        elit.
        """


    class TestInterfaceMerge(unittest.TestCase):

        def setUp(self):
            @polyglot.interop_type(BufferedReader)
            class JBufferedReader:
                def __next__(self):
                    line = self.readLine()
                    if line:
                        return line
                    raise StopIteration

                def __iter__(self):
                    return self

            @polyglot.interop_type(Reader)
            class JReader:
                def __enter__(self):
                    return self

            @polyglot.interop_type(AutoCloseable)
            class JClosable:
                def __exit__(self, exc_type, exc_val, exc_tb):
                    self.close()

        def tearDown(self):
            __graalpython__.clear_interop_type_registry()

        def test_interface_merge_working(self):
            with BufferedReader(StringReader(test_string)) as reader:
                assert type(reader).__name__ == "Java_java.io.BufferedReader_generated"
                result = ""
                for line in reader:
                    result += line + "\n"

                # remove last linebreak
                result = result[:-1]
                self.assertEqual(test_string, result)

        def test_build_cache_invalidation_after_new_register(self):
            reader = BufferedReader(StringReader(test_string))
            readerClass = type(reader)
            self.assertEqual(readerClass.__name__, "Java_java.io.BufferedReader_generated")
            self.assertEqual(readerClass, type(reader))

            import abc
            polyglot.register_interop_type(java.util.List, abc.ABC, allow_methods_overwrite=True)
            # new registrations clears the generated class cache
            # every class has to be regenerated, hence this is a new class instance
            self.assertNotEqual(readerClass, type(reader))

            # first element can't be the same, it's the class itself
            # The rest of the mro is the same though, because BufferedReader doesn't inherit from java.util.List
            # So the behavior of reader doesn't change
            self.assertEqual(readerClass.mro()[1:], type(reader).mro()[1:])
            self.assertEqual(type(reader).__name__, "Java_java.io.BufferedReader_generated")

        def test_build_cache_invalidation_after_delete(self):
            reader = BufferedReader(StringReader(test_string))
            readerClass = type(reader)
            self.assertEqual(readerClass.__name__, "Java_java.io.BufferedReader_generated")

            __graalpython__.clear_interop_type_registry()
            self.assertEqual(type(reader).__name__, "ForeignObject")


    from java.lang import Object
    from java.lang import StringBuilder


    class TestMultipleRegistries(unittest.TestCase):

        def tearDown(self):
            __graalpython__.clear_interop_type_registry()

        def test_second_registered_class(self):
            @polyglot.interop_type(Object)
            class Dummy:
                def test(self):
                    return 1

            test = Object()
            self.assertEqual(test.test(), 1)
            self.assertIsInstance(test, Dummy)
            with self.assertRaises(AttributeError):
                test.test2()

            @polyglot.interop_type(Object)
            class Dummy2:
                def test2(self):
                    return 2

            self.assertEqual(test.test(), 1)
            self.assertEqual(test.test2(), 2)
            self.assertIsInstance(test, Dummy)
            self.assertIsInstance(test, Dummy2)

        def test_error_when_same_method(self):
            @polyglot.interop_type(Object)
            class ClassOne:
                someAttribute = ""

                def method(self):
                    pass

            with self.assertRaises(AttributeError):
                @polyglot.interop_type(Object)
                class ClassTwo:
                    someAttribute = ""

                    def method(self):
                        pass

        def test_mro_update_after_double_registration(self):
            @polyglot.interop_type(StringBuilder)
            class DummyStringBuilder:
                def content(self):
                    return self.toString()

            @polyglot.interop_type(Object)
            class DummyObject:
                def name(self):
                    return self.toString()

            test_string = StringBuilder("lorem ipsum")
            self.assertIn(DummyStringBuilder, type(test_string).mro())
            self.assertIn(DummyObject, type(test_string).mro())
            self.assertEqual(test_string.name(), test_string.content())

            @polyglot.interop_type(Object)
            class BetterDummyObject:
                def better_name(self):
                    return "better_" + self.toString()

            self.assertIn(BetterDummyObject, type(test_string).mro())
            self.assertEqual("better_lorem ipsum", test_string.better_name())

        def test_mro_with_multiple_inheritance(self):
            object = Object()

            class D:
                pass

            @polyglot.interop_type(Object)
            class C(D):
                pass

            self.assertEqual(type(object).mro()[1:3], [C, D])

            class B:
                pass

            @polyglot.interop_type(Object)
            class A(B):
                pass

            self.assertEqual(type(object).mro()[1:5], [A, B, C, D])

        def test_registrations_for_object_effect_common_java_objects(self):
            object = Object()

            @polyglot.interop_type(Object)
            class B:
                pass

            @polyglot.interop_type(Object)
            class A:
                pass

            self.assertEqual(type(object).mro()[1:3], [A, B])
            self.assertEqual(type(ArrayList()).mro()[1:3], [A, B])
            self.assertEqual(type(java.util.HashMap()).mro()[1:3], [A, B])
            self.assertEqual(type(StringReader("test")).mro()[1:3], [A, B])

        def test_class_change_after_registration(self):
            class B:
                pass

            @polyglot.interop_type(Object)
            class A(B):
                def name(self):
                    return "A"

            object = Object()
            self.assertEqual(object.name(), "A")
            with self.assertRaises(AttributeError):
                object.foo()

            B.foo = lambda x: "bar"
            self.assertEqual(object.foo(), "bar")

        def test_classes_registered_with_same_super_class(self):
            object = Object()

            class Super:
                def name(self):
                    return "super"

            @polyglot.interop_type(Object)
            class A(Super):
                def name(self):
                    return "A"

            self.assertEqual(type(object).mro()[1:3], [A, Super])
            self.assertEqual(object.name(), "A")

            with self.assertRaises(AttributeError):
                # B overwrites method name in A, hence it should fail
                @polyglot.interop_type(Object)
                class B(Super):
                    def name(self):
                        return "B"

            @polyglot.interop_type(Object, allow_method_overwrites=True)
            class B(Super):
                def name(self):
                    return "B"

            self.assertEqual(type(object).mro()[1:4], [B, A, Super])
            self.assertEqual(object.name(), "B")

        def test_register_super_and_sub_class(self):
            object = Object()

            @polyglot.interop_type(Object)
            class Super:
                def name(self):
                    return "super"

            self.assertEqual(object.name(), "super")

            with self.assertRaises(AttributeError):
                # A overwrites method name in Super, hence it should fail
                @polyglot.interop_type(Object)
                class A(Super):
                    def name(self):
                        return "A"

            @polyglot.interop_type(Object, allow_method_overwrites=True)
            class A(Super):
                def name(self):
                    return "A"

            self.assertEqual(object.name(), "A")
            self.assertEqual(type(object).mro()[1:3], [A, Super])

        def test_register_sub_class_first(self):
            class Super:
                pass

            class A(Super):
                pass

            polyglot.register_interop_type(Object, A)
            polyglot.register_interop_type(Object, Super)

            object = Object()
            with self.assertRaises(TypeError) as typeError:
                # MRO cannot be constructed, because the registration order requires the super class Super
                # before the child class A in the MRO
                print(object)

            # Testing the wrapped error message
            self.assertIn("cannot create the python class for", str(typeError.exception))
            cause_message = str(typeError.exception.__cause__)
            # sanitize cause message, it contains line breaks
            cause_message = cause_message.replace("\n", " ")
            self.assertIn("Cannot create a consistent method resolution order", cause_message)
