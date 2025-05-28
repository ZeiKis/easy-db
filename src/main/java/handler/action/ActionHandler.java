package handler.action;

import dto.ActionDTO;
import service.Store;

import java.io.IOException;
import java.io.ObjectOutputStream;

/**
 * 请求处理接口
 */
public interface ActionHandler {
    void handle(ActionDTO dto, ObjectOutputStream oos, Store store) throws IOException;
}
