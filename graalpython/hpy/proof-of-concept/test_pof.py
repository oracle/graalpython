import pof
import pofpackage.foo
import pofcpp
import pofpackage.bar

def test_do_nothing():
    assert pof.do_nothing() is None

def test_double():
    assert pof.double(21) == 42

def test_add_ints():
    assert pof.add_ints(30, 12) == 42

def test_add_ints_kw():
    assert pof.add_ints_kw(b=30, a=12) == 42

def test_point():
    p = pof.Point(1, 2)
    assert repr(p) == 'Point(1, 2)' # fixme when we have HPyFloat_FromDouble

def test_pofpackage():
    assert pofpackage.foo.__name__ == "pofpackage.foo"
    assert pofpackage.foo.hello() == 'hello from pofpackage.foo'

def test_cpp_do_nothing():
    assert pofcpp.do_nothing() is None

def test_cpp_double():
    assert pofcpp.double(21) == 42

def test_cpp_add_ints():
    assert pofcpp.add_ints(30, 12) == 42

def test_cpp_add_ints_kw():
    assert pofcpp.add_ints_kw(b=30, a=12) == 42

def test_cpp_point():
    p = pofcpp.Point(1, 2)
    assert repr(p) == 'Point(1, 2)' # fixme when we have HPyFloat_FromDouble

def test_cpp_pofpackage():
    assert pofpackage.bar.__name__ == "pofpackage.bar"
    assert pofpackage.bar.hello(21) == 42
