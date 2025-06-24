package handler.action;

import dto.ActionDTO;
import dto.RespDTO;
import dto.RespStatusTypeEnum;
import service.Store;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Map;

public class LoginActionHandler implements ActionHandler {
    // 简单的硬编码凭证存储
    private static final Map<String, String> USERS = Map.of(
        "admin", "admin123",
        "user", "user123"
    );

    @Override
    public void handle(ActionDTO dto, ObjectOutputStream oos, Store store) throws IOException {
        if (USERS.containsKey(dto.getUsername()) && 
            USERS.get(dto.getUsername()).equals(dto.getPassword())) {
            oos.writeObject(new RespDTO(RespStatusTypeEnum.AUTH_SUCCESS, "Login successful"));
        } else {
            oos.writeObject(new RespDTO(RespStatusTypeEnum.AUTH_FAILED, "Invalid credentials"));
        }
    }
}