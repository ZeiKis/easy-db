/*
 *@Type CommandDTO.java
 * @Desc
 * @Author urmsone urmsone@163.com
 * @date 2024/6/13 12:57
 * @version
 */
package dto;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * 命令类（可以理解为封装请求）
 */
@Setter
@Getter
public class ActionDTO implements Serializable {//封装请求，客户端发给服务端
    private ActionTypeEnum type;
    private String key; //键
    private Object value; //值

    public ActionDTO(ActionTypeEnum type, String key, Object value) {
        this.type = type;
        this.key = key;
        this.value = value;
    }

    @Override
    public String toString() {
        return "ActionDTO{" +
                "type=" + type +
                ", key='" + key + '\'' +
                ", value='" + value + '\'' +
                '}';
    }
}
