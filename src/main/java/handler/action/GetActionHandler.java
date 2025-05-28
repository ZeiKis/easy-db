package handler.action;

import dto.ActionDTO;
import dto.RespDTO;
import dto.RespStatusTypeEnum;
import service.Store;

import java.io.IOException;
import java.io.ObjectOutputStream;

/**
 * get请求处理拦截器
 */
public class GetActionHandler implements ActionHandler {
    @Override
    public void handle(ActionDTO dto, ObjectOutputStream oos, Store store) throws IOException {
        String value = store.get(dto.getKey());
        RespDTO resp = new RespDTO(RespStatusTypeEnum.SUCCESS, value);
        oos.writeObject(resp);
        oos.flush();
    }
}
