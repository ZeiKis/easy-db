package handler.action;

import dto.ActionDTO;
import dto.RespDTO;
import dto.RespStatusTypeEnum;
import service.Store;

import java.io.IOException;
import java.io.ObjectOutputStream;

/**
 * set请求处理拦截器
 */
public class SetActionHandler implements ActionHandler {
    @Override
    public void handle(ActionDTO dto, ObjectOutputStream oos, Store store) throws IOException {
        store.set(dto.getKey(), dto.getValue());
        RespDTO resp = new RespDTO(RespStatusTypeEnum.SUCCESS, null);
        oos.writeObject(resp);
        oos.flush();
    }
}
