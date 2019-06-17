
def outer ():
    def inner(y):
        print(y)
        if (y > 0):
            inner(y-1)

    x = 4
    inner(x)

outer()