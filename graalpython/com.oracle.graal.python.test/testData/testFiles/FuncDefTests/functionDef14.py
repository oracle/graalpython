
def outer ():
    x = 10
    def inner():
        y = 1
        print(y)
        
        def extraInner():
            print(x)
        print('extraInner cellvars:', extraInner.__code__.co_cellvars)
        print('extraInner freevars:', extraInner.__code__.co_freevars)
        extraInner()

    print('inner cellvars:', inner.__code__.co_cellvars)
    print('inner freevars:', inner.__code__.co_freevars)
    inner()
    print(x)

print('outer cellvars:', outer.__code__.co_cellvars)
print('outer freevars:', outer.__code__.co_freevars)
outer()