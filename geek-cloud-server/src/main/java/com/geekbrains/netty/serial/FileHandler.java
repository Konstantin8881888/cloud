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

        else if (cloudMessage instanceof FileRename fr)
        {
            Path path = serverDir.resolve(fr.getOldFileName());
            File oldFile = new File(path.toString());
            path = serverDir.resolve(fr.getNewFileName());
            File newFIle = new File(path.toString());
            if (Files.exists(oldFile.toPath()) && !Files.isDirectory(oldFile.toPath()))
            {
                oldFile.renameTo(newFIle);
            }
            ctx.writeAndFlush(new ListMessage(serverDir));
        }

        else if (cloudMessage instanceof PathRequest paths)
        {
            File createPath = new File(serverDir + File.separator + paths.getPathName());
            if (createPath.exists())
            {
                System.out.println("Folder " + paths.getPathName() + " is exist! ");
                return;
            }
            if (!createPath.mkdir()) {
                System.out.println("Folder " + paths.getPathName() + " is not created! ");
            }
            ctx.writeAndFlush(new ListMessage(serverDir));
        }
    }
}