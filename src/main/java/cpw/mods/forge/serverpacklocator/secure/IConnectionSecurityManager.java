package cpw.mods.forge.serverpacklocator.secure;

import com.electronwill.nightconfig.core.file.FileConfig;
import io.netty.handler.codec.http.FullHttpRequest;

import java.net.URLConnection;

public interface IConnectionSecurityManager
{
    void onClientConnectionCreation(URLConnection connection);

    boolean onServerConnectionRequest(FullHttpRequest msg);

    default void validateConfiguration(FileConfig config) {
        //Default is no configuration needed.
    }

    default void initialize(FileConfig config) {
        //Default is no initialization needed.
    }
}
