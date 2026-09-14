package org.mind.framework.helper.delayqueue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Getter;

import java.io.Serializable;

/**
 * @version 1.0
 * @author Marcus
 * @date 2025/6/12
 */
@Getter
public class AbstractTask implements Serializable {
    protected static final ObjectMapper MAPPER = new ObjectMapper();
    protected String taskId;     // uniquely identify tasks
    protected String type;       // task type (customize)

    static {
        MAPPER.registerModule(new JavaTimeModule());
        MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);// 使用 ISO-8601 字符串更易读
    }
}
