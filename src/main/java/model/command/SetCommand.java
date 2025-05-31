/*
 *@Type SetCommand.java
 * @Desc
 * @Author urmsone urmsone@163.com
 * @date 2024/6/13 01:59
 * @version
 */
package model.command;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SetCommand<T> extends AbstractCommand {
    private String key;

    private T value; // Object 类型

    public SetCommand(String key, T value) {
        super(CommandTypeEnum.SET);
        this.key = key;
        this.value = value;
    }

}
