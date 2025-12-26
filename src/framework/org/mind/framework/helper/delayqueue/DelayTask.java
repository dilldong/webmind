package org.mind.framework.helper.delayqueue;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.mind.framework.util.RandomCodeUtil;

/**
 * @version 1.0
 * @author Marcus
 * @date 2025/5/22
 */
@Getter
@NoArgsConstructor
public class DelayTask<T> extends AbstractTask {
    private T payload; // Task data

    public static <T> DelayTask<T> of(T payload) {
        DelayTask<T> task = new DelayTask<>();
        task.taskId = RandomCodeUtil.randomString(16, true, true);
        task.payload = payload;
        return task;
    }

    public static <T> DelayTask<T> of(T payload, String taskId) {
        DelayTask<T> task = new DelayTask<>();
        task.payload = payload;
        task.taskId = taskId;
        return task;
    }

    public static <T> DelayTask<T> of(T payload, String taskId, String type) {
        DelayTask<T> task = of(payload, taskId);
        task.type = type;
        return task;
    }

    public <R> R getPayloadAs(Class<R> clazz) {
        if (clazz.isInstance(payload)) {
            return clazz.cast(payload);
        } else {
            return MAPPER.convertValue(payload, clazz);
        }
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.NO_CLASS_NAME_STYLE)
                .append("taskId", taskId)
                .append("type", type)
                .append("payload", payload)
                .toString();
    }
}
