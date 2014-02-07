package kilim.test.ex;

import kilim.Pausable;

public interface ExInterfaceGeneric<T> {
    T get() throws Pausable;
}
