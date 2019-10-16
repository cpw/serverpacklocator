package cpw.mods.forge.serverpacklocator.client;

import cpw.mods.forge.serverpacklocator.FileChecksumValidator;
import cpw.mods.forge.serverpacklocator.LaunchEnvironmentHandler;
import cpw.mods.forge.serverpacklocator.ServerManifest;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.*;
import io.netty.util.AttributeKey;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SimpleHttpClient {
    private static final Logger LOGGER = LogManager.getLogger();
    private final ClientCertificateManager clientCertificateManager;
    private static final AttributeKey<ServerManifest.ModFileData> CURRENT_FILE = AttributeKey.valueOf("cpw:file");
    private static final AttributeKey<MessageHandler> HANDLER = AttributeKey.valueOf("cpw:msghandler");
    private final Path outputDir;
    private ServerManifest serverManifest;
    private Iterator<ServerManifest.ModFileData> fileDownloaderIterator;
    private Channel downloadChannel;
    private final Future<Boolean> downloadJob;
    private boolean completedManifest;

    public SimpleHttpClient(final ClientSidedPackHandler packHandler) {
        this.outputDir = packHandler.getServerModsDir();
        final Optional<String> remoteServer = packHandler.getConfig().getOptional("client.remoteServer");
        clientCertificateManager = packHandler.getCertificateManager();
        downloadJob = Executors.newSingleThreadExecutor().submit(() -> remoteServer.map(this::connectAndDownload).orElse(false));
    }

    private boolean connectAndDownload(final String server) {
        final URI uri = URI.create(server);
        final InetAddress inetAddress;
        try {
            inetAddress = InetAddress.getByName(uri.getHost());
        } catch (UnknownHostException e) {
            throw new UncheckedIOException(e);
        }
        final int inetPort = uri.getPort() > 0 ? uri.getPort() : 8443;
        LaunchEnvironmentHandler.INSTANCE.addProgressMessage("Connecting to server at "+uri.getHost());
        final ChannelFuture remoteConnect = new Bootstrap()
                .group(new NioEventLoopGroup(1))
                .channel(NioSocketChannel.class)
                .remoteAddress(inetAddress, inetPort)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(final SocketChannel ch) {
                        try {
                            SslContext sslContext = SslContextBuilder.forClient()
                                    .keyManager(clientCertificateManager.getKeyPair().getPrivate(), clientCertificateManager.getCerts())
                                    .trustManager(clientCertificateManager.getCerts())
                                    .clientAuth(ClientAuth.REQUIRE)
                                    .build();
                            final SslHandler sslHandler = sslContext.newHandler(ch.alloc(), uri.getHost(), inetPort);
                            final SSLParameters sslParameters = sslHandler.engine().getSSLParameters();
                            sslParameters.setEndpointIdentificationAlgorithm("HTTPS");
                            sslHandler.engine().setSSLParameters(sslParameters);
                            ch.pipeline().addLast("ssl", sslHandler);
                            ch.pipeline().addLast("codec", new HttpClientCodec());
                            ch.pipeline().addLast("aggregator", new HttpObjectAggregator(Integer.MAX_VALUE));
                            ch.pipeline().addLast("responseHandler", new ChannelMessageHandler());
                        } catch (SSLException e) {
                            throw new RuntimeException(e);
                        }
                    }
                })
                .connect();
        remoteConnect.awaitUninterruptibly();
        if (remoteConnect.isSuccess()) {
            final String hostName = ((InetSocketAddress) remoteConnect.channel().remoteAddress()).getHostName();
            LOGGER.debug("Connected to {}", hostName);
            LaunchEnvironmentHandler.INSTANCE.addProgressMessage("Connected to server at "+hostName);
            downloadChannel = remoteConnect.channel();
        } else {
            LOGGER.debug("Error occured during connection", remoteConnect.cause());
            remoteConnect.channel().eventLoop().shutdownGracefully();
        }
        // Wait for channels to close
        remoteConnect.channel().closeFuture().syncUninterruptibly();
        if (!completedManifest) {
            LaunchEnvironmentHandler.INSTANCE.addProgressMessage("Failed to complete transaction at "+uri.getHost()+" server mods will NOT be available");
            LOGGER.error("Failed to receive successful data connection from server. Are you whitelisted?");
            return false;
        }
        LOGGER.debug("Successfully downloaded pack from server");
        LaunchEnvironmentHandler.INSTANCE.addProgressMessage("All mods downloaded successfully from server");
        return true;
    }

    private void requestManifest(final Channel channel) {
        channel.attr(HANDLER).set(this::receiveManifest);
        final DefaultFullHttpRequest defaultFullHttpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/servermanifest.json");
        final ChannelFuture channelFuture = channel.writeAndFlush(defaultFullHttpRequest);
        channelFuture.awaitUninterruptibly();
        if (!channelFuture.isSuccess()) {
            LOGGER.debug("Error sending request packet: " + channelFuture.cause());
            channel.close();
            channel.eventLoop().shutdownGracefully();
        }
    }

    protected void receiveManifest(final ChannelHandlerContext ctx, final FullHttpResponse msg) {
        ServerManifest sm = ServerManifest.loadFromString(msg.content().toString(StandardCharsets.UTF_8));
        LOGGER.debug("Received manifest");
        this.serverManifest = sm;
        buildFileFetcher();
        downloadChannel.eventLoop().execute(this::requestNextFile);
    }

    private void requestFile(final ServerManifest.ModFileData next) {
        final String existingChecksum = FileChecksumValidator.computeChecksumFor(outputDir.resolve(next.getFileName()));
        if (Objects.equals(next.getChecksum(), existingChecksum)) {
            LOGGER.debug("Found existing file {} - skipping", next.getFileName());
            requestNextFile();
            return;
        }
        Channel channel = downloadChannel;
        channel.attr(CURRENT_FILE).set(next);
        final String nextFile = next.getFileName();
        channel.attr(HANDLER).set(this::receiveFile);
        LOGGER.debug("Requesting file {}", nextFile);
        LaunchEnvironmentHandler.INSTANCE.addProgressMessage("Requesting file "+nextFile);
        final DefaultFullHttpRequest fileHttpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/files/"+nextFile);
        fileHttpRequest.headers().set(HttpHeaderNames.ACCEPT, "application/octet-stream");
        final ChannelFuture channelFuture = channel.writeAndFlush(fileHttpRequest);
        channelFuture.awaitUninterruptibly();
        if (!channelFuture.isSuccess()) {
            LOGGER.debug("Error sending request packet: " + channelFuture.cause());
            channel.close();
        }
    }

    protected void receiveFile(final ChannelHandlerContext ctx, final FullHttpResponse msg) {
        final ServerManifest.ModFileData modFileData = ctx.channel().attr(CURRENT_FILE).get();
        if (msg.status().code() == 200) {
            LOGGER.debug("Receiving {} of size {} for {}", msg.headers().getAsString("filename"), msg.content().readableBytes(), modFileData.getFileName());
            LaunchEnvironmentHandler.INSTANCE.addProgressMessage("Receiving file "+modFileData.getFileName());
            try (OutputStream os = Files.newOutputStream(outputDir.resolve(modFileData.getFileName()))) {
                msg.content().readBytes(os, msg.content().readableBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            LOGGER.debug("Recieved {} error for {}", msg.status(), modFileData.getFileName());
        }
        requestNextFile();
    }

    private void requestNextFile() {
        final Iterator<ServerManifest.ModFileData> fileDataIterator = fileDownloaderIterator;
        if (fileDataIterator.hasNext()) {
            requestFile(fileDataIterator.next());
        } else {
            LOGGER.debug("Finished downloading closing channel");
            this.completedManifest = true;
            downloadChannel.close();
            downloadChannel.eventLoop().shutdownGracefully();
        }
    }

    private void buildFileFetcher() {
        fileDownloaderIterator = serverManifest.getFiles().iterator();
    }

    boolean waitForResult() throws ExecutionException {
        try {
            return downloadJob.get();
        } catch (InterruptedException e) {
            return false;
        }
    }

    public ServerManifest getManifest() {
        return this.serverManifest;
    }

    private interface MessageHandler {
        void handle(ChannelHandlerContext ctx, FullHttpResponse response);
    }

    private class ChannelMessageHandler extends SimpleChannelInboundHandler<FullHttpResponse> {
        @Override
        protected void channelRead0(final ChannelHandlerContext ctx, final FullHttpResponse msg) {
            ctx.channel().attr(HANDLER).getAndSet(this::defaultHandler).handle(ctx, msg);
        }

        private void defaultHandler(final ChannelHandlerContext ctx, final FullHttpResponse msg) {
            ctx.close();
            ctx.channel().eventLoop().shutdownGracefully();
        }

        @Override
        public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) throws Exception {
            if (evt instanceof SslHandshakeCompletionEvent) {
                final SslHandshakeCompletionEvent sslHandshake = (SslHandshakeCompletionEvent) evt;
                if (!sslHandshake.isSuccess()) {
                    final Optional<CertificateException> maybeCertException = Optional.ofNullable(sslHandshake.cause().getCause())
                            .map(Throwable::getCause)
                            .filter(t -> t instanceof CertificateException)
                            .map(CertificateException.class::cast);
                    if (maybeCertException.isPresent()) {
                        certificateError(maybeCertException.get(), (SslHandler) ctx.pipeline().get("ssl"));
                    } else {
                        sslHandshake.cause().printStackTrace();
                    }
                } else {
                    LOGGER.debug("SSL handshake complete. Requesting manifest");
                    requestManifest(ctx.channel());
                }
            }
        }

        @Override
        public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) throws Exception {
            LOGGER.debug("Error in client");
            if (!(cause.getCause() instanceof SSLHandshakeException)) {
                LOGGER.catching(cause.getCause());
            }
            ctx.channel().close();
            ctx.channel().eventLoop().shutdownGracefully();
        }
    }
    private void certificateError(final CertificateException cert, final SslHandler ssl) {
        final Pattern errorparser = Pattern.compile("No name matching (.*) found");
        final Matcher matcher = errorparser.matcher(cert.getMessage());
        final String hostname = matcher.find() ? matcher.group(1) : "WEIRD ERROR MESSAGE " + cert.getMessage();
        LOGGER.debug("CERTIFICATE PROBLEM : Hostname {} does not match the server certificate", hostname);
        LaunchEnvironmentHandler.INSTANCE.addProgressMessage("CERTIFICATE PROBLEM: the remote host does not match it's name");
    }
}
