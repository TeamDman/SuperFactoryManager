package q;
import p.Widget;
class Use {
    Widget create(String key, Object[] args) {
        return new Widget(key, args);
    }
}
