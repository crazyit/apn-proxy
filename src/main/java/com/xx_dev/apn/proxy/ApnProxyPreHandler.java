package com.xx_dev.apn.proxy;

import com.xx_dev.apn.proxy.config.ApnProxyConfig;
import com.xx_dev.apn.proxy.config.ApnProxyListenType;
import com.xx_dev.apn.proxy.config.ApnProxyRemoteRule;
import com.xx_dev.apn.proxy.utils.HostNamePortUtil;
import com.xx_dev.apn.proxy.utils.HttpErrorUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

/**
 * User: xmx
 * Date: 13-12-29
 * Time: PM11:57
 */
public class ApnProxyPreHandler extends ChannelInboundHandlerAdapter {

    public static final String HANDLER_NAME = "apnproxy.pre";

    private static final Logger logger = Logger.getLogger(ApnProxyPreHandler.class);

    private static final Logger httpRestLogger = Logger.getLogger("HTTP_REST_LOGGER");

    private static String[] forbiddenIps = new String[]{"10.", "172.16.", "172.17.",
            "172.18.", "172.19.", "172.20.", "172.21.", "172.22.", "172.23.", "172.24.", "172.25.",
            "172.26.", "172.27.", "172.28.", "172.29.", "172.30.", "172.31.", "192.168."};

    private static int[] forbiddenPorts = new int[]{20, 21, 22};

    private boolean isPacRequest = false;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (preCheck(ctx, msg)) {
            ctx.fireChannelRead(msg);
        } else {
            ReferenceCountUtil.release(msg);
        }
    }

    private boolean preCheck(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof HttpRequest) {
            HttpRequest httpRequest = (HttpRequest) msg;
            String hostHeader = httpRequest.headers().get(HttpHeaders.Names.HOST);
            String originalHost = HostNamePortUtil.getHostName(hostHeader);

            // http rest log
            if (httpRestLogger.isInfoEnabled()) {
                httpRestLogger.info(ctx.channel().remoteAddress() + " "
                        + httpRequest.getMethod().name() + " " + httpRequest.getUri()
                        + " " + httpRequest.getProtocolVersion().text() + ", "
                        + hostHeader + ", "
                        + httpRequest.headers().get(HttpHeaders.Names.USER_AGENT));
            }

            // pac request
            if (StringUtils.equals(originalHost, ApnProxyConfig.getConfig().getPacHost())) {
                isPacRequest = true;

                String pacContent = null;
                if (ApnProxyConfig.getConfig().getListenType() == ApnProxyListenType.SSL) {
                    pacContent = buildPacForSsl();
                } else {
                    pacContent = buildPacForPlain();
                }

                ByteBuf pacResponseContent = Unpooled.copiedBuffer(pacContent, CharsetUtil.UTF_8);
                FullHttpMessage pacResponseMsg = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                        HttpResponseStatus.OK, pacResponseContent);
                HttpHeaders.setContentLength(pacResponseMsg, pacResponseContent.readableBytes());
                HttpHeaders.setHeader(pacResponseMsg, "X-APN-PROXY-PAC", "OK");
                HttpHeaders.setHeader(pacResponseMsg, "X-APN-PROXY-URL", "https://github.com/apn-proxy/apn-proxy");
                HttpHeaders.setHeader(pacResponseMsg, "X-APN-PROXY-MSG", "We need more commiters!");

                ctx.write(pacResponseMsg);
                ctx.flush();
                return false;
            }

            // forbid request to proxy server internal network
            for (String forbiddenIp : forbiddenIps) {
                if (StringUtils.startsWith(originalHost, forbiddenIp)) {
                    String errorMsg = "Forbidden";
                    ctx.write(HttpErrorUtil.buildHttpErrorMessage(HttpResponseStatus.FORBIDDEN,
                            errorMsg));
                    ctx.flush();
                    return false;
                }
            }

            // forbid request to proxy server local
            if (StringUtils.equals(originalHost, "127.0.0.1")
                    || StringUtils.equals(originalHost, "localhost")) {
                String errorMsg = "Forbidden";
                ctx.write(HttpErrorUtil.buildHttpErrorMessage(HttpResponseStatus.FORBIDDEN,
                        errorMsg));
                ctx.flush();
                return false;
            }

            // forbid reqeust to some port
            int originalPort = HostNamePortUtil.getPort(hostHeader, -1);
            for (int fobiddenPort : forbiddenPorts) {
                if (originalPort == fobiddenPort) {
                    String errorMsg = "Forbidden";
                    ctx.write(HttpErrorUtil.buildHttpErrorMessage(HttpResponseStatus.FORBIDDEN,
                            errorMsg));
                    ctx.flush();
                    return false;
                }
            }

        } else {
            if (isPacRequest) {
                if (msg instanceof LastHttpContent) {
                    isPacRequest = false;
                }

                return false;
            }
        }

        return true;
    }

    private String buildPacForPlain() {

        StringBuilder sb = new StringBuilder();
        sb.append("function FindProxyForURL(url, host){var PROXY = \"PROXY ")
                .append(ApnProxyConfig.getConfig().getPacHost()).append(":")
                .append(ApnProxyConfig.getConfig().getPort()).append("\";var DEFAULT = \"DIRECT\";");

        for (ApnProxyRemoteRule remoteRule : ApnProxyConfig.getConfig().getRemoteRuleList()) {
            for (String originalHost : remoteRule.getOriginalHostList()) {
                if (StringUtils.isNotBlank(originalHost)) {
                    sb.append("if(/^[\\w\\-]+:\\/+(?!\\/)(?:[^\\/]+\\.)?")
                            .append(StringUtils.replace(originalHost, ".", "\\."))
                            .append("/i.test(url)) return PROXY;");
                }
            }
        }

        sb.append("return DEFAULT;}");

        return sb.toString();
    }

    private String buildPacForSsl() {

        StringBuilder sb = new StringBuilder();
        sb.append("function FindProxyForURL(url, host){var PROXY = \"HTTPS ")
                .append(ApnProxyConfig.getConfig().getPacHost()).append(":")
                .append(ApnProxyConfig.getConfig().getPort()).append("\";");

        sb.append("return PROXY;}");

        return sb.toString();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error(cause.getMessage(), cause);
        ctx.close();
    }

}
