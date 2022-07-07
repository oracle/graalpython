
def outer ():
    x = 10
    def inner():
        x = 5
        print("Inner, local x:", x)

    inner()
    print("Outer, local x:", x)

outer()