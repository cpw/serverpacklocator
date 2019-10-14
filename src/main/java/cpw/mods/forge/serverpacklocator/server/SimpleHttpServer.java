package cpw.mods.forge.serverpacklocator.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.net.ssl.SSLException;
import java.io.UncheckedIOException;

/**
 * Simple Http Server for serving file and manifest requests to clients.
 */
public class SimpleHttpServer {
    private static final Logger LOGGER = LogManager.getLogger();
    private final ChannelFuture channel;

    private final EventLoopGroup masterGroup;
    private final EventLoopGroup slaveGroup;
    private final ServerCertificateManager certificateManager;

    SimpleHttpServer(ServerSidedPackHandler handler) {
        masterGroup = new NioEventLoopGroup(1, SimpleHttpServer::newDaemonThread);
        slaveGroup = new NioEventLoopGroup(1, SimpleHttpServer::newDaemonThread);

        int port = handler.getConfig().getOptionalInt("server.port").orElse(8443);
        certificateManager = handler.getCertificateManager();
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
                        try {
                            SslContext sslContext = SslContextBuilder
                                    .forServer(certificateManager.getPrivateKey(), certificateManager.getCertificate())
                                    .trustManager(certificateManager.getCertificate())
                                    .clientAuth(ClientAuth.REQUIRE)
                                    .build();
                            ch.pipeline().addLast("ssl", sslContext.newHandler(ch.alloc()));
                            ch.pipeline().addLast("codec", new HttpServerCodec());
                            ch.pipeline().addLast("aggregator", new HttpObjectAggregator(2 << 19));
                            ch.pipeline().addLast("request", new RequestHandler(handler));
                        } catch (SSLException e) {
                            throw new UncheckedIOException(e);
                        }
                    }
                })
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_KEEPALIVE, true);
        channel = bootstrap.bind(port).syncUninterruptibly();
    }

    static Thread newDaemonThread(Runnable r) {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    }
}
