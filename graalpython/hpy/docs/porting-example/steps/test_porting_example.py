# -*- coding: utf-8 -*-

""" Porting example tests. """

import pytest
import math
import types


class TestPorting:
    def test_load_module(self, step):
        mod = step.import_step()
        assert isinstance(mod, types.ModuleType)
        assert mod.__name__ == step.name
        assert mod.__doc__.startswith("Point module (Step ")
        assert type(mod.Point) == type
        assert mod.Point.__doc__.startswith("Point (Step ")
        assert isinstance(mod.dot, types.BuiltinFunctionType)
        assert mod.dot.__doc__ == "Dot product."

    def test_create_point(self, step):
        mod = step.import_step()
        p = mod.Point(1, 2)
        assert type(p) == mod.Point

    def test_norm(self, step):
        mod = step.import_step()
        assert mod.Point(1, 2).norm() == math.sqrt(5.0)
        assert mod.Point(1.5).norm() == 1.5

    def test_dot(self, step):
        mod = step.import_step()
        p1 = mod.Point(1, 2)
        p2 = mod.Point(3, 2)
        assert mod.dot(p1, p2) == 7.0

    def test_object(self, step):
        mod = step.import_step()
        p1 = mod.Point(23, 42, ...)
        assert p1.obj is ...
        p2 = mod.Point(23, 42)
        assert p2.obj is None

    def test_leak_checker(self, step):
        if "hpy_final" not in step.name:
            pytest.skip("Can only check for leaks in universal mode")
        mod = step.import_step()
        import hpy.debug
        hpy.debug.set_handle_stack_trace_limit(10)
        with hpy.debug.LeakDetector():
            p1 = mod.Point(1, 2, ...)
            p2 = mod.Point(3, 2)

            assert p1.obj is ...
            assert p2.obj is None
            assert p1.norm() == math.sqrt(5.0)
            assert mod.dot(p1, p2) == 7.0

            del p1
            del p2
