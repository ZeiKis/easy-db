package handler.action;

import dto.ActionTypeEnum;

import java.util.HashMap;
import java.util.Map;

/**
 * 请求处理工厂
 */
public class ActionHandlerFactory {

    // 处理类数组
    private static final Map<ActionTypeEnum, ActionHandler> handlers = new HashMap<>();

    // 初始化，提前加载各种处理类
    static {
        handlers.put(ActionTypeEnum.SET, new SetActionHandler());
        handlers.put(ActionTypeEnum.GET, new GetActionHandler());
        handlers.put(ActionTypeEnum.RM, new RmActionHandler());
        handlers.put(ActionTypeEnum.LOGIN, new LoginActionHandler());
    }

    // 通过枚举类型获取对应的处理类
    public static ActionHandler getHandler(ActionTypeEnum type) {
        return handlers.get(type);
    }
}
