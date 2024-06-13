import sys
import unittest

if sys.implementation.name == "graalpy":
    import polyglot
    javaClassName = "java.util.ArrayList"

    class TestPyStructNumericSequenceTypes(unittest.TestCase):
        def tearDown(self):
            try:
                polyglot.remove_java_interop_type(javaClassName)
            except Exception as e:
                pass # A test did not register the java class

        def test_java_interop_assertions(self):
            """
            Test if registering java class and calling it works
            """
            import java

            class jList(__graalpython__.ForeignType):
                def append(self, element):
                    self.add(element)

            polyglot.register_java_interop_type(javaClassName, jList)
            l = java.type(javaClassName)()
            assert isinstance(l, jList)

            l.append(1)
            assert len(l) == 1
            assert l[0] == 1

        def test_java_interop_decorator_assertions(self):
            """
            Test if registering with the decorator function works
            """
            import java

            @polyglot.java_interop_type(javaClassName)
            class jList(__graalpython__.ForeignType):
                pass

            l = java.type(javaClassName)()
            assert isinstance(l, jList)

        def test_java_interop_overwrite_assertions(self):
            """
            Test if overwriting registrations works
            """
            import java

            class jList(__graalpython__.ForeignType):
                pass

            class jList2(__graalpython__.ForeignType):
                pass

            polyglot.register_java_interop_type(javaClassName, jList)
            try:
                polyglot.register_java_interop_type(javaClassName, jList2)
            except Exception as e:
                assert True
            else:
                assert False, "should throw error that class is already registered"

            # Overwriting should work now
            polyglot.register_java_interop_type(javaClassName, jList2, overwrite=True)
            l = java.type(javaClassName)()
            assert isinstance(l, jList2)

            # Test if overwrite flag works in decorator function too
            try:
                @polyglot.java_interop_type(javaClassName)
                class jList3(__graalpython__.ForeignType):
                    pass
            except Exception as e:
                assert True
            else: assert False, "should throw an error"

            @polyglot.java_interop_type(javaClassName, overwrite=True)
            class jList4(__graalpython__.ForeignType):
                pass

            assert isinstance(l, jList4)

        def test_remove_java_interop_assertions(self):
            """
            Test if removing registrations work
            """
            import java

            class jList(__graalpython__.ForeignType):
                pass

            class jList2(__graalpython__.ForeignType):
                pass

            try:
                polyglot.remove_java_interop_type(javaClassName)
            except Exception as e:
                assert True
            else: assert False, "Should throw an error"

            polyglot.register_java_interop_type(javaClassName, jList)
            polyglot.remove_java_interop_type(javaClassName)
            # register type without overwrite flag
            polyglot.register_java_interop_type(javaClassName, jList2)
            l = java.type(javaClassName)()
            assert isinstance(l, jList2)

