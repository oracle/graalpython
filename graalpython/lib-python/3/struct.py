__all__ = [
    # Functions
    'calcsize', 'pack', 'pack_into', 'unpack', 'unpack_from',
    'iter_unpack',

    # Classes
    'Struct',

    # Exceptions
    'error'
    ]


__cstruct = None

def make_delegate(p):
    def delegate(*args ,**kwargs):
        global __cstruct
        if not __cstruct:
            from build_capi import hint
            with hint("_struct"):
                import _struct as __cstruct
        return getattr(__cstruct, p)(*args, **kwargs)
    delegate.__name__ = p
    return delegate

for p in __all__:
    globals()[p] = make_delegate(p)

#from _struct import *
#from _struct import _clearcache
#from _struct import __doc__
