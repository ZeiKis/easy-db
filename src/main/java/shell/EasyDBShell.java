package shell;

import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import client.SocketClient;
import client.Client;

public class EasyDBShell {
    private static final String PROMPT = "easy-db> ";
    private static final String WELCOME_MSG = "Welcome to EasyDB Shell v1.0\nType 'help' for list of commands.";

    public static void main(String[] args) {
        try {
            Terminal terminal = TerminalBuilder.terminal();
            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .build();
            
            Client client = new SocketClient("localhost", 12345);
            System.out.println(WELCOME_MSG);
            
            String line;
            while ((line = reader.readLine(PROMPT)) != null) {
                processCommand(client, line, terminal);
            }
        } catch (Exception e) {
            System.err.println("Shell startup failed: " + e.getMessage());
        }
    }

    private static void processCommand(Client client, String line, Terminal terminal) {
        String[] args = line.trim().split(" ");
        if (args.length == 0) return;
        
        switch (args[0]) {
            case "set":
                handleSet(client, args);
                break;
            case "get":
                handleGet(client, args);
                break;
            case "rm":
                handleDelete(client, args);
                break;
            case "quit":
            case "exit":
                System.exit(0);
                break;
            default:
                System.out.println("Unknown command: " + args[0]);
                showHelp();
        }
    }

    private static void showHelp() {
        System.out.println("Shell help:");
        System.out.println("  set <key> <value>   Set the value of a key");
        System.out.println("  get <key>           Get the value of a key");
        System.out.println("  rm <key>            Remove a key");
        System.out.println("  quit                Exit the CLI");
        System.out.println("  exit                Exit the CLI");
    }

    private static void handleSet(Client client, String[] args) {
        if (args.length != 3) {
            System.out.println("Usage: set <key> <value>");
            return;
        }
        try {
            client.set(args[1], args[2]);
            System.out.println("OK");
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    private static void handleGet(Client client, String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: get <key>");
            return;
        }
        try {
            String value = client.get(args[1]);
            System.out.println(value != null ? value : "(nil)");
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    private static void handleDelete(Client client, String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: rm <key>");
            return;
        }
        try {
            client.rm(args[1]);
            System.out.println("Deleted");
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
