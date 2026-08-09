package q;

import p.A;

class B {
    Runnable callback(A target) {
        return target::ping;
    }
}
