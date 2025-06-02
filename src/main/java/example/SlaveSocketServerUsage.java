/*
 *@Type SocketServerUsage.java
 * @Desc
 * @Author urmsone urmsone@163.com
 * @date 2024/6/13 14:08
 * @version
 */
package example;

import controller.SlaveSocketServerController;
import controller.SocketServerController;
import service.NormalStore;
import service.SlaveNormalStore;
import service.Store;

import java.io.File;
import java.io.IOException;

public class SlaveSocketServerUsage {
    public static void main(String[] args) throws IOException {
        String host = "localhost";
        int port = 12346;
        String dataDir = "data" + File.separator + "slave" + File.separator;
        Store store = new SlaveNormalStore(dataDir);//创建存储引擎
        SlaveSocketServerController slaveController = new SlaveSocketServerController(host, port, store);//传入ip、端口、存储引擎，创建服务控制器
        slaveController.startServer();//启动服务
    }
}
