DEFAULT_VERSION = object()


def dump(value, file, version=DEFAULT_VERSION):
    if version is DEFAULT_VERSION:
        file.write(dumps(value))
    else:
        file.write(dumps(value, version))


def load(file):
    return loads(file.read())
