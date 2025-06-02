/*
 *@Type SocketServerHandler.java
 * @Desc
 * @Author urmsone urmsone@163.com
 * @date 2024/6/13 12:50
 * @version
 */
package controller;

import dto.ActionDTO;
import dto.RespDTO;
import dto.RespStatusTypeEnum;
import handler.action.ActionHandler;
import handler.action.ActionHandlerFactory;
import handler.action.GetActionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.Store;
import utils.LoggerUtil;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

/**
 * 处理单个客户端请求
 */
public class SlaveSocketServerHandler implements Runnable {
    private final Logger LOGGER = LoggerFactory.getLogger(SlaveSocketServerHandler.class);
    private Socket socket;
    private Store store;

    public SlaveSocketServerHandler(Socket socket, Store store) {
        this.socket = socket;
        this.store = store;
    }

    @Override
    public void run() {
        try (ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
             ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream())) {

            // 接收序列化对象，从客户端接收请求对象
            ActionDTO dto = (ActionDTO) ois.readObject();
            LoggerUtil.debug(LOGGER, "[SocketServerHandler][ActionDTO]: {}", dto.toString());
            System.out.println("" + dto);

            // 使用策略模式动态选择处理器（工厂模式）
            ActionHandler handler = ActionHandlerFactory.getHandler(dto.getType());// 通过工厂来返回对应处理类
            if (handler instanceof GetActionHandler) // 从节点不处理 get请求
                return;
            if (handler != null) {
                handler.handle(dto, oos, store);
            } else {
                // 处理未知命令类型的情况
                RespDTO resp = new RespDTO(RespStatusTypeEnum.FAIL, null);
                oos.writeObject(resp);
                oos.flush();
            }

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


}
