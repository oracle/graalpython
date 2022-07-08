def mydecorator(delta):
    def wrap(fn):
        def wrapped_f(*args):
            return fn(*args) + delta
        return wrapped_f
    return wrap

@mydecorator(3)
def getNumber():
    return 1

print(getNumber())