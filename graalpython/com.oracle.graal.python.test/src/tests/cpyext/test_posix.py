# Copyright (c) 2018, Oracle and/or its affiliates.
#
# All rights reserved.
try:
    import posix as os
except ImportError:
    import nt as os
from . import CPyExtTestCase, GRAALPYTHON


class TestPosix(CPyExtTestCase):
    def test_graalpython_posixmodule(self):
        if not GRAALPYTHON:
            return
        from . import posixmodule
        posixenv = posixmodule.environ
        osenv = os.environ
        for k in posixenv:
            assert osenv[k] == posixenv[k], "os.environ[%s] == %s != posixenv[%s] == %s" % (
                k, osenv[k], k, posixenv[k]
            )
        assert "environ" in dir(posixmodule), dir(posixmodule)
        assert b"/" in posixmodule.getcwdb()
