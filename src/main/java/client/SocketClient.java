/*
 *@Type SocketClient.java
 * @Desc
 * @Author urmsone urmsone@163.com
 * @date 2024/6/13 13:15
 * @version
 */
package client;

import dto.ActionDTO;
import dto.ActionTypeEnum;
import dto.RespDTO;
import dto.RespStatusTypeEnum;

import java.io.*;
import java.net.Socket;

public class SocketClient implements Client {
    private String host;
    private int port;

    public SocketClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    public void set(String key, Object value) {
        try (Socket socket = new Socket(host, port);
             ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream ois = new ObjectInputStream(socket.getInputStream())) {
            // 传输序列化对象
            ActionDTO dto = new ActionDTO(ActionTypeEnum.SET, key, value);
            // 发送序列化对象
            oos.writeObject(dto);
            oos.flush();
            // 接收响应数据
            RespDTO resp = (RespDTO) ois.readObject();
            System.out.println("resp data: "+ resp.toString());
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String get(String key) {
        try (Socket socket = new Socket(host, port);
             ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream ois = new ObjectInputStream(socket.getInputStream())) {
            // 传输序列化对象
            ActionDTO dto = new ActionDTO(ActionTypeEnum.GET, key, null);
            oos.writeObject(dto);
            oos.flush();
            // 接收响应数据
            RespDTO resp = (RespDTO) ois.readObject();
            System.out.println("resp data: "+ resp.toString());
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void rm(String key) {
        try (Socket socket = new Socket(host, port);
             ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream ois = new ObjectInputStream(socket.getInputStream())) {
            // 构造删除请求对象
            ActionDTO dto = new ActionDTO(ActionTypeEnum.RM, key, null);
            oos.writeObject(dto);
            oos.flush();
            // 接收响应
            RespDTO resp = (RespDTO) ois.readObject();
            System.out.println("resp data: " + resp.toString());
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean login(String username, String password) {
        try (Socket socket = new Socket(host, port)) {
            ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());

            ActionDTO dto = new ActionDTO(ActionTypeEnum.LOGIN, null, null, username, password);
            oos.writeObject(dto);
            oos.flush();

            RespDTO resp = (RespDTO) ois.readObject();
            return RespStatusTypeEnum.AUTH_SUCCESS == resp.getStatus();
        } catch (Exception e) {
            throw new RuntimeException("Login failed: " + e.getMessage());
        }
    }

}
