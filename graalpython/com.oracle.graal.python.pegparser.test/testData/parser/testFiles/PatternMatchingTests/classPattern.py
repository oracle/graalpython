match a:
    case A.B():
        pass
    case A(True):
        pass
    case A(42 | -4+2j as x,) :
        pass
    case A.B(a1=_,) | A(42, a1='abc'):
        pass
