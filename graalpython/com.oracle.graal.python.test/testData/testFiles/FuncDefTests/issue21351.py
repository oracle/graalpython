def outer():
    var = None
    def toInternal(obj):
        nonlocal var
        x = 10
    return toInternal

fnc = outer()
c = fnc.__code__

print("free > ", c.co_freevars)
print("cell > ", c.co_cellvars)
