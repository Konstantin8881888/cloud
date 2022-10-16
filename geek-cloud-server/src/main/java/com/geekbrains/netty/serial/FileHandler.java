package com.geekbrains.netty.serial;

import com.geekbrains.model.*;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
public class FileHandler extends SimpleChannelInboundHandler<CloudMessage>
{
    private Path serverDir;

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception
    {
        serverDir = Path.of("server_files").toAbsolutePath();
        ctx.writeAndFlush(new ListMessage(serverDir));
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, CloudMessage cloudMessage) throws Exception
    {
        log.debug("Received: {}", cloudMessage.getType());
        if (cloudMessage instanceof FileMessage fileMessage)
        {
            Files.write(serverDir.resolve(fileMessage.getFileName()), fileMessage.getBytes());
            ctx.writeAndFlush(new ListMessage(serverDir));
        }
        else if (cloudMessage instanceof FileRequest fileRequest)
        {
            ctx.writeAndFlush(new FileMessage(serverDir.resolve(fileRequest.getFileName())));
        }
        else if (cloudMessage instanceof Delete delete)
        {
            Files.delete(serverDir.resolve(delete.getFileName()));
            ctx.writeAndFlush(new ListMessage(serverDir));
        }

        else if (cloudMessage instanceof PathRequest paths)
        {
            if (new File(serverDir.toString() + File.separator + paths.getPathName()).isDirectory()) {
                serverDir = serverDir.resolve(paths.getPathName()).normalize();
                log.debug("Path: {}", serverDir);
                ctx.writeAndFlush(new ListMessage(serverDir));
            }
        }
    }
}
