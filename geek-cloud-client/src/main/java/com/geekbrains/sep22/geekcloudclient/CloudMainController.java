package com.geekbrains.sep22.geekcloudclient;

import com.geekbrains.DaemonThreadFactory;
import com.geekbrains.model.*;
import io.netty.handler.codec.serialization.ObjectDecoderInputStream;
import io.netty.handler.codec.serialization.ObjectEncoderOutputStream;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.AnchorPane;

import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class CloudMainController implements Initializable
{
    @FXML
    public ListView<String> clientView;
    @FXML
    public ListView<String> serverView;
    @FXML
    private AnchorPane DirectoryField;
    @FXML
    public TextField CreateDir;
    private String currentDirectory;


    private Network<ObjectDecoderInputStream, ObjectEncoderOutputStream> network;

    private boolean needReadMessages = true;

    private DaemonThreadFactory factory;

    public void downloadFile() throws IOException
    {
        String fileName = serverView.getSelectionModel().getSelectedItem();
        network.getOutputStream().writeObject(new FileRequest(fileName));
    }

    public void sendToServer() throws IOException
    {
        String fileName = clientView.getSelectionModel().getSelectedItem();
        network.getOutputStream().writeObject(new FileMessage(Path.of(currentDirectory).resolve(fileName)));
    }

    public void deleteFileClient()
    {
        String fileName = clientView.getSelectionModel().getSelectedItem();
        File selectedFile = new File(currentDirectory + "/" + fileName);
        if (!selectedFile.isDirectory())
        {
            try
            {
                Files.delete(Path.of(selectedFile.getAbsolutePath()));
                System.out.println("File deleted: " + fileName);
                fillView(clientView, getFiles(currentDirectory));
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
        else
        {
            System.out.println("It is not file!!!");
        }
    }

    public void deleteFileServer() throws IOException
    {
        String fileName = serverView.getSelectionModel().getSelectedItem();
        network.getOutputStream().writeObject(new Delete(fileName));
    }


    public void renameFile() {
        String fileOldName = serverView.getSelectionModel().getSelectedItem();
        if (fileOldName != null)
        {
            DirectoryField.setVisible(true);
            CreateDir.setPromptText("ENTER NEW FILE NAME");
            final String a = fileOldName;
            CreateDir.setOnKeyPressed(keyEvent -> {

                if (keyEvent.getCode() == KeyCode.ENTER)
                {
                    String fileNewName = CreateDir.getText();
                    try 
                    {
                        network.getOutputStream().writeObject(new FileRename(fileNewName, a));
                        DirectoryField.setVisible(false);
                        CreateDir.setText("");
                    }
                    catch (IOException e)
                    {

                        e.printStackTrace();
                    }
                }
            });
        }
        else
        {
            fileOldName = clientView.getSelectionModel().getSelectedItem();
            if (fileOldName != null)
            {
                File oldFile = new File(currentDirectory + "/" + fileOldName);
                if (!Files.isDirectory(oldFile.toPath()))
                {
                    DirectoryField.setVisible(true);
                    CreateDir.setPromptText("ENTER NEW FILE NAME");
                    String finalFileOldName = fileOldName;
                    CreateDir.setOnKeyPressed(keyEvent -> {
                        if (keyEvent.getCode() == KeyCode.ENTER)
                        {
                            String newName = CreateDir.getText();
                            File file = new File(currentDirectory + "/" + finalFileOldName);
                            File newNameFile = new File(currentDirectory + "/" + newName);
                            if (newNameFile.exists())
                            {
                                System.out.println("File with name " + newName + " is exist ");
                            } else
                            {
                                if (file.renameTo(newNameFile))
                                {
                                    fillView(clientView, getFiles(currentDirectory));
                                } else
                                {
                                    System.out.println("File not renamed");
                                }
                            }
                            DirectoryField.setVisible(false);
                            CreateDir.setText("");
                            fillView(clientView, getFiles(currentDirectory));
                        }
                    });


                }
            }
        }
    }

    private void readMessages()
    {
        try
        {
            while (needReadMessages)
            {

                CloudMessage message = (CloudMessage) network.getInputStream().readObject();
                if (message instanceof FileMessage fileMessage)
                {
                    Files.write(Path.of(currentDirectory).resolve(fileMessage.getFileName()), fileMessage.getBytes());
                    Platform.runLater(() -> fillView(clientView, getFiles(currentDirectory)));
                }
                else if (message instanceof ListMessage listMessage)
                {
                    Platform.runLater(() -> fillView(serverView, listMessage.getFiles()));
                }
            }
        } catch (Exception e)
        {
            System.err.println("Server off");
            e.printStackTrace();
        }
    }

    private void initNetwork()
    {
        try
        {
            Socket socket = new Socket("localhost", 8189);
            network = new Network<>(new ObjectDecoderInputStream(socket.getInputStream()), new ObjectEncoderOutputStream(socket.getOutputStream()));
            factory.getThread(this::readMessages, "cloud-client-read-thread")
                    .start();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle)
    {
        needReadMessages = true;
        factory = new DaemonThreadFactory();
        initNetwork();
        setCurrentDirectory(System.getProperty("user.home"));
        fillView(clientView, getFiles(currentDirectory));
        clientView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2)
            {
                String selected = clientView.getSelectionModel().getSelectedItem();
                File selectedFile = new File(currentDirectory + "/" + selected);
                if (selectedFile.isDirectory())
                {
                    setCurrentDirectory(currentDirectory + "/" + selected);
                }
            }
        });
        serverView.setOnMouseClicked(mouseEvent -> {
            if (mouseEvent.getClickCount() == 2)
            {
                String selected = serverView.getSelectionModel().getSelectedItem();
                if (selected != null)
                {
                    try
                    {
                        network.getOutputStream().writeObject(new PathRequest(selected));
                    }
                    catch (IOException e)
                    {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private void setCurrentDirectory(String directory)
    {
        currentDirectory = directory;
        fillView(clientView, getFiles(currentDirectory));
    }

    private void fillView(ListView<String> view, List<String> data)
    {
        view.getItems().clear();
        view.getItems().addAll(data);
    }

    private List<String> getFiles(String directory)
    {

        File dir = new File(directory);
        if (dir.isDirectory())
        {
            String[] list = dir.list();
            if (list != null)
            {
                List<String> files = new ArrayList<>(Arrays.asList(list));
                files.add(0, "..");
                return files;
            }
        }
        return List.of();
    }

    public void createFolder()
    {
        DirectoryField.setVisible(true);
        CreateDir.setPromptText("ENTER DIRECTORY NAME");
        CreateDir.setOnKeyPressed(keyEvent -> {
            if (keyEvent.getCode() == KeyCode.ENTER)
            {
                String dirName = CreateDir.getText();
                System.out.println(dirName);
                try
                {
                    network.getOutputStream().writeObject(new PathRequest(dirName));
                    DirectoryField.setVisible(false);
                    CreateDir.setText("");
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        });
    }

}

