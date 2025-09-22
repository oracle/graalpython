# Copyright (c) 2018, 2025, Oracle and/or its affiliates.
# Copyright (C) 1996-2017 Python Software Foundation
#
# Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
import os
import unittest
import sys
import errno

GRAALPYTHON = sys.implementation.name == "graalpy"

def fun0(test_obj, expected_error):
    typ, val, tb = sys.exc_info()
    test_obj.assertEqual(typ, expected_error)

def fun1(test_obj, expected_error):
    fun0(test_obj, expected_error)

def fun2(test_obj, expected_error):
    test_obj.assertNotEqual(sys._getframe(2), None)
    typ, val, tb = sys.exc_info()
    test_obj.assertEqual(typ, expected_error)

def fun3(test_obj, expected_error):
    fun2(test_obj, expected_error)

def fun4(test_obj):
    try:
        raise ValueError
    except:
        fun3(test_obj, ValueError)

class ExceptionTests(unittest.TestCase):

    def test_exc_info(self):
        typ, val, tb = (None,) * 3
        try:
            raise TypeError
        except:
            typ, val, tb = sys.exc_info()
        self.assertEqual(typ, TypeError)

        typ, val, tb = (None,) * 3
        try:
            raise ValueError
        except:
            fun1(self, ValueError)

    def test_nested(self):
        typ, val, tb = (None,) * 3
        try:
            raise TypeError
        except:
            typ, val, tb = sys.exc_info()
            self.assertEqual(typ, TypeError)
            try:
                raise ValueError
            except:
                typ, val, tb = sys.exc_info()
                self.assertEqual(typ, ValueError)
            typ, val, tb = sys.exc_info()
            self.assertEqual(typ, TypeError)

    def test_exc_info_with_caller_frame(self):
        # call twice because the first time, we do a stack walk
        fun4(self)
        fun4(self)

    def testInvalidTraceback(self):
        try:
            Exception().__traceback__ = 5
        except TypeError as e:
            self.assertIn("__traceback__ must be a traceback", str(e))
        else:
            self.fail("No exception raised")

    def testNoneClearsTracebackAttr(self):
        try:
            raise IndexError(4)
        except:
            tb = sys.exc_info()[2]

        e = Exception()
        e.__traceback__ = tb
        e.__traceback__ = None
        self.assertEqual(e.__traceback__, None)

    def testWithTraceback(self):
        try:
            raise IndexError(4)
        except:
            tb = sys.exc_info()[2]

        e = BaseException().with_traceback(tb)
        self.assertIsInstance(e, BaseException)
        self.assertEqual(e.__traceback__, tb)

        e = IndexError(5).with_traceback(tb)
        self.assertIsInstance(e, IndexError)
        self.assertEqual(e.__traceback__, tb)

        class MyException(Exception):
            pass

        e = MyException().with_traceback(tb)
        self.assertIsInstance(e, MyException)
        self.assertEqual(e.__traceback__, tb)

    def test_aliases(self):
        self.assertTrue (IOError is OSError)
        self.assertTrue (EnvironmentError is OSError)

    def test_new_oserror(self):
        self.assertTrue(type(OSError(2)) is OSError)
        self.assertTrue(type(OSError(errno.EISDIR)) is OSError)
        self.assertTrue(type(OSError(2, "a message")) is FileNotFoundError)

        self.assertTrue(type(OSError(errno.EISDIR, "a message")) is IsADirectoryError)
        self.assertTrue(type(OSError(errno.EAGAIN, "a message")) is BlockingIOError)
        self.assertTrue(type(OSError(errno.EALREADY, "a message")) is BlockingIOError)
        self.assertTrue(type(OSError(errno.EINPROGRESS, "a message")) is BlockingIOError)
        self.assertTrue(type(OSError(errno.EWOULDBLOCK, "a message")) is BlockingIOError)
        self.assertTrue(type(OSError(errno.EPIPE, "a message")) is BrokenPipeError)
        self.assertTrue(type(OSError(errno.ESHUTDOWN, "a message")) is BrokenPipeError)
        self.assertTrue(type(OSError(errno.ECHILD, "a message")) is ChildProcessError)
        self.assertTrue(type(OSError(errno.ECONNABORTED, "a message")) is ConnectionAbortedError)
        self.assertTrue(type(OSError(errno.ECONNREFUSED, "a message")) is ConnectionRefusedError)
        self.assertTrue(type(OSError(errno.ECONNRESET, "a message")) is ConnectionResetError)
        self.assertTrue(type(OSError(errno.EEXIST, "a message")) is FileExistsError)
        self.assertTrue(type(OSError(errno.ENOENT, "a message")) is FileNotFoundError)
        self.assertTrue(type(OSError(errno.ENOTDIR, "a message")) is NotADirectoryError)
        self.assertTrue(type(OSError(errno.EINTR, "a message")) is InterruptedError)
        self.assertTrue(type(OSError(errno.EACCES, "a message")) is PermissionError)
        self.assertTrue(type(OSError(errno.EPERM, "a message")) is PermissionError)
        self.assertTrue(type(OSError(errno.ESRCH, "a message")) is ProcessLookupError)
        self.assertTrue(type(OSError(errno.ETIMEDOUT, "a message")) is TimeoutError)

    def test_oserror_empty_attributes(self):
        e = OSError(errno.EISDIR)
        self.assertEqual(e.errno, None)
        self.assertEqual(e.strerror, None)
        self.assertEqual(e.filename, None)
        self.assertEqual(e.filename2, None)

    def test_oserror_two_attributes(self):
        e = OSError(errno.EISDIR, "message")
        self.assertEqual(e.errno, 21)
        self.assertEqual(e.strerror, "message")
        self.assertEqual(e.filename, None)
        self.assertEqual(e.filename2, None)

    def test_oserror_four_attribute(self):
        e = OSError(errno.EISDIR, "message", "file1")
        self.assertEqual(e.errno, 21)
        self.assertEqual(e.strerror, "message")
        self.assertEqual(e.filename, "file1")
        self.assertEqual(e.filename2, None)

    def test_oserror_four_attribute_2(self):
        e = OSError(errno.EISDIR, "message", "file1", None, "file2")
        self.assertEqual(e.errno, 21)
        self.assertEqual(e.strerror, "message")
        self.assertEqual(e.filename, "file1")
        self.assertEqual(e.filename2, "file2")

    def test_exception_cleared(self):
        try:
            raise ValueError
        except ValueError as e:
            pass
        try:
            e
        except UnboundLocalError:
            pass
        else:
            assert False, "named exception should be unbound after except block"


    def test_sys_exc_info(self):
        self.assertEqual(sys.exc_info()[0], None)
        try:
            raise ValueError
        except:
            self.assertEqual(sys.exc_info()[0], ValueError)


    def test_raise_non_exception(self):
        try:
            raise object()
        except TypeError:
            pass

    def test_raise_none(self):
        try:
            raise None
        except TypeError:
            pass

    def test_generator(self):
        def gen():
            try:
                1 / 0
            except Exception as e:
                yield 1
                raise
        g = gen()
        self.assertEqual(next(g), 1)
        self.assertRaises(ZeroDivisionError, lambda: next(g))

    def test_generator_nested(self):
        def gen():
            try:
                1 / 0
            except Exception:
                yield 1
                try:
                    int("t")
                except Exception:
                    yield 2
                raise
        g = gen()
        self.assertEqual(next(g), 1)
        self.assertEqual(next(g), 2)
        self.assertRaises(ZeroDivisionError, lambda: next(g))

    def test_generator_finally(self):
        def gen():
            try:
                1 / 0
            finally:
                yield 1
                try:
                    int("t")
                except Exception:
                    yield 2

        g = gen()
        self.assertEqual(next(g), 1)
        self.assertEqual(next(g), 2)
        self.assertRaises(ZeroDivisionError, lambda: next(g))

    def test_generator_finally_return(self):
        def gen():
            try:
                raise RuntimeError
            finally:
                yield 1
                return

        self.assertEqual(list(gen()), [1])

    def test_implicit_chaining(self):
        try:
            try:
                raise OSError("first")
            except OSError:
                raise TypeError("second")
        except Exception as exc:
            e = exc
        self.assertEqual(e.args[0], "second")
        self.assertIsNone(e.__cause__)
        self.assertEqual(type(e.__context__), OSError)
        self.assertEqual(e.__context__.args[0], "first")
        self.assertIsNone(e.__context__.__context__)
        self.assertFalse(e.__suppress_context__)

    def test_implicit_chaining_reraise_overwrites_context(self):
        try:
            try:
                try:
                    raise OSError("first")
                except OSError:
                    raise TypeError("second")
            except TypeError as e:
                x = e
            try:
                raise OverflowError
            except OverflowError:
                raise x
        except Exception as exc:
            e = exc
        self.assertEqual(e.args[0], "second")
        self.assertIsNone(e.__cause__)
        self.assertEqual(type(e.__context__), OverflowError)
        self.assertIsNone(e.__context__.__context__)
        self.assertFalse(e.__suppress_context__)

    def test_no_implicit_chaining(self):
        try:
            raise OSError("first")
        except OSError:
            pass
        try:
            raise TypeError("second")
        except Exception as exc:
            e = exc
        self.assertEqual(e.args[0], "second")
        self.assertIsNone(e.__context__)

    def test_implicit_chaining_from_outer(self):
        def bar():
            try:
                raise TypeError("fourth")
            except:
                raise NameError("third")
        def baz():
            try:
                raise TypeError("second")
            except:
                raise NameError("first")
        def foo():
            try:
                bar()
            except:
                baz()
        try:
            foo()
        except Exception as exc:
            e = exc
        self.assertEqual(e.args[0], "first")
        self.assertIsNotNone(e.__context__)
        self.assertEqual(e.__context__.args[0], "second")
        self.assertIsNotNone(e.__context__.__context__)
        self.assertEqual(e.__context__.__context__.args[0], "third")
        self.assertIsNotNone(e.__context__.__context__.__context__)
        self.assertEqual(e.__context__.__context__.__context__.args[0], "fourth")
        self.assertIsNone(e.__context__.__context__.__context__.__context__)

    def test_implicit_chaining_finally(self):
        try:
            try:
                raise OSError("first")
            finally:
                raise TypeError("second")
        except Exception as exc:
            e = exc
        self.assertEqual(e.args[0], "second")
        self.assertIsNone(e.__cause__)
        self.assertEqual(type(e.__context__), OSError)
        self.assertEqual(e.__context__.args[0], "first")
        self.assertIsNone(e.__context__.__context__)
        self.assertFalse(e.__suppress_context__)

    def test_implicit_chaining_finally_from_outer(self):
        def bar():
            try:
                raise TypeError("second")
            finally:
                raise NameError("third")
        def foo():
            try:
                raise OSError("first")
            finally:
                bar()
        try:
            foo()
        except Exception as exc:
            e = exc
        self.assertEqual(e.args[0], "third")
        self.assertIsNotNone(e.__context__)
        self.assertEqual(e.__context__.args[0], "second")
        self.assertIsNotNone(e.__context__.__context__)
        self.assertEqual(e.__context__.__context__.args[0], "first")
        self.assertIsNone(e.__context__.__context__.__context__)

    def test_implicit_chaining_reraise_explicit_no_chaining(self):
        try:
            try:
                raise NameError("first")
            except Exception as e:
                raise e
        except Exception as exc:
            e = exc
        self.assertEqual(e.args[0], "first")
        self.assertIsNone(e.__context__)

    def test_implicit_chaining_reraise_no_chaining(self):
        try:
            try:
                raise NameError("first")
            except Exception:
                raise
        except Exception as exc:
            e = exc
        self.assertEqual(e.args[0], "first")
        self.assertIsNone(e.__context__)

    def test_implicit_chaining_reraise_from_outer(self):
        try:
            try:
                raise NameError("first")
            except Exception as e:
                try:
                    raise OSError("second")
                except Exception:
                    raise e
        except Exception as exc:
            e = exc
        self.assertEqual(e.args[0], "first")
        self.assertIsNotNone(e.__context__)
        self.assertEqual(e.__context__.args[0], "second")
        self.assertIsNone(e.__context__.__context__)

    def test_implicit_chaining_with(self):
        class cm:
            def __enter__(self):
                return self

            def __exit__(self, etype, e, tb):
                raise TypeError("second")

        def foo():
            with cm():
                raise OSError("first")
        try:
            foo()
        except Exception as exc:
            e = exc
        self.assertEqual(e.args[0], "second")
        self.assertIsNotNone(e.__context__)
        self.assertEqual(e.__context__.args[0], "first")
        self.assertIsNone(e.__context__.__context__)

    def test_implicit_chaining_with_from_outer(self):
        class cm:
            def __enter__(self):
                return self

            def __exit__(self, etype, e, tb):
                raise TypeError("third")

        def foo():
            try:
                raise NameError("first")
            except:
                with cm():
                    raise OSError("second")
        try:
            foo()
        except Exception as exc:
            e = exc
        self.assertEqual(e.args[0], "third")
        self.assertIsNotNone(e.__context__)
        self.assertEqual(e.__context__.args[0], "second")
        self.assertIsNotNone(e.__context__.__context__)
        self.assertEqual(e.__context__.__context__.args[0], "first")
        self.assertIsNone(e.__context__.__context__.__context__)

    def test_implicit_chaining_generator(self):
        def gen():
            try:
                yield 1
                raise NameError("first")
            except:
                yield 2
                raise NameError("second")
        try:
            list(gen())
        except Exception as exc:
            e = exc
        self.assertEqual(e.args[0], "second")
        self.assertIsNotNone(e.__context__)
        self.assertEqual(e.__context__.args[0], "first")
        self.assertIsNone(e.__context__.__context__)

    def test_implicit_chaining_generator_from_outer(self):
        def gen():
            try:
                yield 1
                raise NameError("second")
            except:
                yield 2
                raise NameError("third")
        try:
            try:
                raise NameError("first")
            except Exception:
                list(gen())
        except Exception as exc:
            e = exc
        self.assertEqual(e.args[0], "third")
        self.assertIsNotNone(e.__context__)
        self.assertEqual(e.__context__.args[0], "second")
        self.assertIsNotNone(e.__context__.__context__)
        self.assertEqual(e.__context__.__context__.args[0], "first")
        self.assertIsNone(e.__context__.__context__.__context__)

    def test_implicit_chaining_generator_finally(self):
        def gen():
            try:
                yield 1
                raise NameError("first")
            finally:
                yield 2
                raise NameError("second")
        try:
            list(gen())
        except Exception as exc:
            e = exc
        self.assertEqual(e.args[0], "second")
        self.assertIsNotNone(e.__context__)
        self.assertEqual(e.__context__.args[0], "first")
        self.assertIsNone(e.__context__.__context__)

    def test_implicit_chaining_generator_finally_from_outer(self):
        def gen():
            try:
                yield 1
                raise NameError("second")
            finally:
                yield 2
                raise NameError("third")
        try:
            try:
                raise NameError("first")
            except Exception:
                list(gen())
        except Exception as exc:
            e = exc
        self.assertEqual(e.args[0], "third")
        self.assertIsNotNone(e.__context__)
        self.assertEqual(e.__context__.args[0], "second")
        self.assertIsNotNone(e.__context__.__context__)
        self.assertEqual(e.__context__.__context__.args[0], "first")
        self.assertIsNone(e.__context__.__context__.__context__)

    def test_implicit_chaining_long(self):
        # Test that our implementation of avoiding looped chains doesn't explode on this
        e = NameError
        for i in range(10000):
            try:
                try:
                    raise e
                except:
                    raise RuntimeError
            except Exception as exc:
                e = exc

    def test_implicit_chaining_nontrivial_loop(self):
        try:
            try:
                raise TypeError
            except Exception as outer:
                try:
                    try:
                        raise OverflowError
                    except Exception:
                        raise NameError
                except Exception:
                    raise outer
        except Exception as e:
            exc = e
        self.assertEqual(type(exc), TypeError)
        self.assertEqual(type(exc.__context__), NameError)
        self.assertEqual(type(exc.__context__.__context__), OverflowError)
        # This is where it would loop back to TypeError, if not for the loop breaker
        self.assertIsNone(exc.__context__.__context__.__context__)

    def test_explicit_chaining(self):
        try:
            try:
                raise OSError("first")
            except OSError as e:
                captured = e
            try:
                raise NameError("third")
            except NameError:
                raise TypeError("second") from captured
        except Exception as exc:
            e = exc
        self.assertEqual(e.args[0], "second")
        self.assertEqual(type(e.__context__), NameError)
        self.assertEqual(e.__context__.args[0], "third")
        self.assertEqual(type(e.__cause__), OSError)
        self.assertEqual(e.__cause__.args[0], "first")
        self.assertIsNone(e.__context__.__context__)
        self.assertTrue(e.__suppress_context__)

    def test_explicit_chaining_class(self):
        try:
            try:
                raise NameError("first")
            except NameError:
                raise TypeError("second") from OSError
        except Exception as exc:
            e = exc
        self.assertEqual(e.args[0], "second")
        self.assertEqual(type(e.__context__), NameError)
        self.assertEqual(e.__context__.args[0], "first")
        self.assertEqual(type(e.__cause__), OSError)
        self.assertIsNone(e.__context__.__context__)
        self.assertTrue(e.__suppress_context__)

    def test_set_cause_manually(self):
        try:
            try:
                raise OSError("first")
            except OSError as e:
                captured = e
            try:
                raise NameError("third")
            except NameError:
                e = TypeError("second")
                e.__cause__ = captured
                raise e
        except Exception as exc:
            e = exc
        self.assertEqual(e.args[0], "second")
        self.assertEqual(type(e.__context__), NameError)
        self.assertEqual(e.__context__.args[0], "third")
        self.assertIsNone(e.__context__.__context__)
        self.assertEqual(type(e.__cause__), OSError)
        self.assertEqual(e.__cause__.args[0], "first")
        self.assertTrue(e.__suppress_context__)

    def test_suppress_implicit_chaining(self):
        try:
            try:
                raise OSError("first")
            except OSError:
                raise TypeError("second") from None
        except Exception as exc:
            e = exc
        self.assertEqual(e.args[0], "second")
        self.assertEqual(type(e.__context__), OSError)
        self.assertEqual(e.__context__.args[0], "first")
        self.assertIsNone(e.__context__.__context__)
        self.assertIsNone(e.__cause__)
        self.assertTrue(e.__suppress_context__)

    def test_encoding_err(self):
        errMsg = ""
        try:
            class A: pass
            A.__name__ = '\udcdc'
        except UnicodeEncodeError as e:
            errMsg = str(e)
        assert len(errMsg) > 0

    def test_exception_scope(self):
        g = {}
        exec("try:1\nexcept Exception as e: pass", g)
        assert 'e' not in g
        exec("try:1/0\nexcept Exception as e: pass", g)
        assert 'e' not in g

    def test_del(self):
        try:
            del notexisting
        except NameError:
            pass

    def expected_exception_in_builtins_allows_subclasses(self):
        class StopIter1(StopIteration):
            pass

        class IndexError1(IndexError):
            pass

        class AttributeError1(AttributeError):
            pass

        class Iter1:
            def __iter__(self): return self

            def __next__(self): raise StopIter1

        class Iter2:
            def __getitem__(self, i): raise IndexError1

        class Obj1:
            def __getattr__(self, i): raise AttributeError1

        assert list(Iter1()) == []
        assert list(Iter2()) == []
        sentinel = object()
        assert getattr(Obj1(), 'does_not_exist', sentinel) is sentinel


@unittest.skipUnless(GRAALPYTHON, "There is no simple way to restrict memory for CPython process")
def test_memory_error():
    import subprocess
    compiler_options = []
    if subprocess.run([sys.executable, '--engine.Compilation=true', '-c', '1'], stderr=subprocess.DEVNULL).returncode == 0:
        compiler_options = [
            '--experimental-options',
            '--engine.MultiTier=false',
            '--engine.BackgroundCompilation=false',
            '--engine.CompileImmediately',
            '--engine.CompileOnly=alloc',
        ]
    file = os.path.join(os.path.dirname(__file__), 'memoryerror.py')
    result = subprocess.check_output([sys.executable, '-S', *compiler_options, '--vm.Xmx400m', file], text=True)
    assert 'ERROR' not in result, result
    assert 'DONE' in result, result
