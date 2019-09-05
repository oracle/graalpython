def outer ():
    def inner():
        print(x )

    x = 12
    inner()

outer()