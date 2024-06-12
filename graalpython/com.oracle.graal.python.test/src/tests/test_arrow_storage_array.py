from tests.util import storage_to_arrow

def test_access():
    a = [1, 2, 3]
    storage_to_arrow(a)

    assert a[0] == 1
    assert a[1] == 2
    assert a[2] == 3

def test_modify():
    a = [1, 2, 3]
    storage_to_arrow(a)
    a[0] = 11

    assert a[0] == 11


def test_add():
    a = [1, 2, 3]
    storage_to_arrow(a)
    a.append(4)

    assert a[0] == 1
    assert a[1] == 2
    assert a[2] == 3
    assert a[3] == 4

def test_remove():
    a = [1, 2, 3]
    storage_to_arrow(a)
    a.remove(2)

    assert a[0] == 1
    assert a[1] == 3

    assert a.pop(0) == 1

    assert len(a) == 1
    assert a[0] == 3


def test_slice():
    a = [1, 2, 3, 4]
    storage_to_arrow(a)
    b = a[1:3]

    assert len(b) == 2
    assert b[0] == 2
    assert b[1] == 3


def test_reverse():
    a = [1, 2, 3]
    storage_to_arrow(a)
    b = a.reverse()

    assert b is None
    assert a[0] == 3
    assert a[1] == 2
    assert a[2] == 1



