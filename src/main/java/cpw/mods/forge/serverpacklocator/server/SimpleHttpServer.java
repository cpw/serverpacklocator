package cpw.mods.forge.serverpacklocator.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple Http Server for serving file and manifest requests to clients.
 */
public class SimpleHttpServer {
    private static final Logger LOGGER = LogManager.getLogger();

    private SimpleHttpServer() {
        throw new IllegalArgumentException("Can not instantiate SimpleHttpServer.");
    }

    public static void run(ServerSidedPackHandler handler) {
        EventLoopGroup masterGroup = new NioEventLoopGroup(1, (Runnable r) -> newDaemonThread("ServerPack Locator Master - ", r));
        EventLoopGroup slaveGroup = new NioEventLoopGroup(1, (Runnable r) -> newDaemonThread("ServerPack Locator Slave - ", r));

        int port = handler.getConfig().getOptionalInt("server.port").orElse(8443);
        final ServerBootstrap bootstrap = new ServerBootstrap()
                .group(masterGroup, slaveGroup)
                .channel(NioServerSocketChannel.class)
                .handler(new ChannelInitializer<ServerSocketChannel>() {
                    @Override
                    protected void initChannel(final ServerSocketChannel ch) {
                        ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelActive(final ChannelHandlerContext ctx) {
                                LOGGER.info("ServerPack server active on port {}", port);
                            }
                        });
                    }
                })
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(final SocketChannel ch) {
                        ch.pipeline().addLast("codec", new HttpServerCodec());
                        ch.pipeline().addLast("aggregator", new HttpObjectAggregator(2 << 19));
                        ch.pipeline().addLast("request", new RequestHandler(handler));
                    }
                })
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_KEEPALIVE, true);
        bootstrap.bind(port).syncUninterruptibly();
    }

    private static final AtomicInteger COUNT = new AtomicInteger(1);
    static Thread newDaemonThread(final String namePrefix, Runnable task) {
        Thread t = new Thread(task);
        t.setName(namePrefix + COUNT.getAndIncrement());
        t.setDaemon(true);
        return t;
    }
}
