/*
 *@Type SocketClientUsage.java
 * @Desc
 * @Author urmsone urmsone@163.com
 * @date 2024/6/13 14:07
 * @version
 */
package example;

import client.Client;
import client.SocketClient;
import shell.EasyDBShell;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class SocketClientUsage {
    public static void main(String[] args) {
        String host = "localhost";
        int port = 12345;
        Client client = new SocketClient(host, port);
//        client.get("zsy1");
//        client.set("zsy1","for test1");
//        client.get("zsy12");
//        client.set("zsy1","for test1");
//        client.set("zsy1","for test1");
//        client.get("zsy12");
//        client.rm("zsy12");
//        client.get("zsy12sy2");

//        client.set("user:1001", Map.of("name", "Tom", "age", 20));
//        client.set("list:key", Arrays.asList(1, 2, 3, "hello"));
//        client.get("user:1001");
//        client.get("list:key");

        // 存储 List 类型
//        List<String> list = Arrays.asList("x", "y", "z");
//        client.set("myList", list);
//        client.get("myList");

//        for (int i = 0; i < 100; i++)
//            client.set("zsy" + i, "test" + i);

        boolean success = client.login("admin", "admin123");
        System.out.println(success ? "登录成功" : "登录失败");

        if (success) {
            EasyDBShell.main(null);
        }

    }
}