match a:
    case {} | {**b}:
        pass
    case {x.y: 42 | 'abc' as z}:
        pass
    case {True: y, 1-2j: False, **z}:
        pass
