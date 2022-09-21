def mydecorator(fn):
    def wrap():
        return fn() + 1
    return wrap

@mydecorator
def getNumber():
    return 1

print(getNumber())