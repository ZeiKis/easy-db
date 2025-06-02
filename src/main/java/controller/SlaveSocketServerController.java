/*
 *@Type ServerController.java
 * @Desc
 * @Author urmsone urmsone@163.com
 * @date 2024/6/13 12:20
 * @version
 */
package controller;

import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.Store;
import utils.LoggerUtil;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

@Setter
@Getter
public class SlaveSocketServerController implements Controller {

    private final Logger LOGGER = LoggerFactory.getLogger(SlaveSocketServerController.class);
    private final String logFormat = "[SocketServerController][{}]: {}";
    private String host;
    private int port;
    private Store store;

    public static Socket slaveSocket;

    public SlaveSocketServerController(String host, int port, Store store) {
        this.host = host;
        this.port = port;
        this.store = store;
    }

    @Override
    public void set(String key, String value) {

    }

    @Override
    public String get(String key) {
        return null;
    }

    @Override
    public void rm(String key) {

    }

    /**
     * 启动服务，等待并处理客户端连接
     */
    @Override
    public void startServer() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            LoggerUtil.info(LOGGER, logFormat,"startServer","Server started, waiting for connections...");

            while (true) {
                try {
                    slaveSocket = serverSocket.accept(); // 等待客户端连接
                    LoggerUtil.info(LOGGER, logFormat,"startServer","New client connected");
                    // 为每个客户端连接创建一个新的线程
                    new Thread(new SlaveSocketServerHandler(slaveSocket, store)).start();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        
    }
}
