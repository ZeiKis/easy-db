/*
 *@Type RespDTO.java
 * @Desc
 * @Author urmsone urmsone@163.com
 * @date 2024/6/13 13:40
 * @version
 */
package dto;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * 响应类
 */
@Setter
@Getter
public class RespDTO implements Serializable {//封装响应结果，服务端返回给客户端
    private RespStatusTypeEnum status;//响应状态
    private Object value;//查询结果值（仅GET）

    public RespDTO(RespStatusTypeEnum status, Object value) {
        this.status = status;
        this.value = value;
    }

    @Override
    public String toString() {
        return "RespDTO{" +
                "status=" + status +
                ", value='" + value + '\'' +
                '}';
    }
}
