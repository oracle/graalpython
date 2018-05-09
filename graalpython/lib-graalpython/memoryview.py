# memoryview is implemented in C
import sys


class memoryview():
    def __new__(cls, *args, **kwds):
        import _memoryview
        import sys
        sys.modules['builtins'].memoryview = _memoryview.memoryview
        return _memoryview.memoryview(*args, **kwds)


sys.modules['builtins'].memoryview = memoryview
