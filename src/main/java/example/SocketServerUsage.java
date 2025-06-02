/*
 *@Type SocketServerUsage.java
 * @Desc
 * @Author urmsone urmsone@163.com
 * @date 2024/6/13 14:08
 * @version
 */
package example;

import controller.SocketServerController;
import service.NormalStore;
import service.Store;

import java.io.File;
import java.io.IOException;

public class SocketServerUsage {
    public static void main(String[] args) throws IOException {
        String host = "localhost";
        int port = 12345;
        int slavePort = 12346;
        String dataDir = "data" + File.separator + "master" + File.separator;
        Store store = new NormalStore(dataDir);//创建存储引擎
        SocketServerController controller = new SocketServerController(host, port, store, slavePort);//传入ip、端口、存储引擎，创建服务控制器
        controller.startServer();//启动服务
    }
}
