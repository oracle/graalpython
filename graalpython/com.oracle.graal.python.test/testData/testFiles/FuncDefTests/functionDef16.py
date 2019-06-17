def foo(x):
    # inner function "bar"
    def bar(y):
        q = 10
        # inner function "baz"
        def baz(z):
            print("Locals: ", locals())
            print("Vars: ", vars())
            return x + y + q + z
        return baz
    return bar

# Locals: {'y': 20, 'x': 10, 'z': 30, 'q': 10}
# Vars: {'y': 20, 'x': 10, 'z': 30, 'q': 10}
print(foo(10)(20)(30)) # 70
