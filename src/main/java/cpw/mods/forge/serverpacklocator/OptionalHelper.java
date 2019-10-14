package cpw.mods.forge.serverpacklocator;

import java.util.Optional;
import java.util.function.Consumer;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class OptionalHelper {
    public static <T> void ifPresentOrElse(Optional<T> optional, Consumer<T> action, Runnable orElse) {
        if (optional.isPresent()) {
            optional.ifPresent(action);
        } else {
            orElse.run();
        }
    }
}
