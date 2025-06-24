package client;

import java.util.Scanner;

public class CliClient {
    private static final String HOST = "localhost";
    private static final int PORT = 12345;

    public static void main(String[] args) {
        if (args.length == 0) {
            interactiveMode();
            return;
        }

        try {
            Client client = new SocketClient(HOST, PORT);
            executeCommand(client, args);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
    
    // 执行单条命令
    private static void executeCommand(Client client, String[] args) {
        String command = args[0];
        
        switch (command) {
            case "set":
                if (args.length != 3) {
                    printUsage("set <key> <value>");
                    return;
                }
                client.set(args[1], args[2]);
                System.out.println("OK");
                break;
                
            case "get":
                if (args.length != 2) {
                    printUsage("get <key>");
                    return;
                }
//                String value = client.get(args[1]);
//                System.out.println(value != null ? value : "(nil)");
                break;
                
            case "rm":
                if (args.length != 2) {
                    printUsage("rm <key>");
                    return;
                }
                client.rm(args[1]);
                System.out.println("OK");
                break;
                
            case "help":
                printUsage(null);
                break;
                
            default:
                System.err.println("Unknown command: " + command);
                printUsage(null);
        }
    }
    
    // 交互模式
    private static void interactiveMode() {
        Scanner scanner = new Scanner(System.in);
        Client client = new SocketClient(HOST, PORT);
        
        System.out.println("EasyDB CLI. Type 'help' for usage.");
        while (true) {
            System.out.print("easy-db> ");
            String input = scanner.nextLine();
            
            if (input.equalsIgnoreCase("quit")) {
                break;
            }
            
            String[] args = input.trim().split("\\s+");
            executeCommand(client, args);
        }
    }
    
    private static void printUsage(String command) {
        if (command != null) {
            System.err.println("Usage: " + command);
        }
        System.out.println("Commands:");
        System.out.println("  set <key> <value>   Set the value of a key");
        System.out.println("  get <key>           Get the value of a key");
        System.out.println("  rm <key>            Remove a key");
        System.out.println("  help                Show usage");
        System.out.println("  quit                Exit the CLI");
    }
}
