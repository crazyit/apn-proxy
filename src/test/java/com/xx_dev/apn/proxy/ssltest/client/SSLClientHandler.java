/*
 * Copyright 2012 The Netty Project
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.xx_dev.apn.proxy.ssltest.client;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundByteHandlerAdapter;
import io.netty.util.CharsetUtil;

import org.apache.log4j.Logger;

/**
 * Handler implementation for the echo client. It initiates the ping-pong
 * traffic between the echo client and server by sending the first message to
 * the server.
 */
public class SSLClientHandler extends ChannelInboundByteHandlerAdapter {

    private static final Logger logger        = Logger.getLogger(SSLClientHandler.class);

    private long                allBytesCount = 0;

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        logger.info("client channel active");
        ctx.write(Unpooled.copiedBuffer("aaa", CharsetUtil.UTF_8));
    }

    @Override
    public void inboundBufferUpdated(ChannelHandlerContext ctx, ByteBuf in) {
        allBytesCount += in.readableBytes();
        logger.info("Recived all: " + allBytesCount + ", total: " + 10 * 1024 * 1204);
        in.clear();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // Close the connection when an exception is raised.
        logger.error(cause.getMessage(), cause);
        ctx.close();
    }

    /** 
     * @see io.netty.channel.ChannelStateHandlerAdapter#channelInactive(io.netty.channel.ChannelHandlerContext)
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.info("client channel inactive");
    }

}
