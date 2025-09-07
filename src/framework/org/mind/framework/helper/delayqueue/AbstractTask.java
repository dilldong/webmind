package org.mind.framework.helper.delayqueue;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;

import java.io.Serializable;

/**
 * @version 1.0
 * @auther Marcus
 * @date 2025/6/12
 */
@Getter
public class AbstractTask implements Serializable {
    protected static final ObjectMapper MAPPER = new ObjectMapper();
    protected String taskId;     // uniquely identify tasks
    protected String type;       // task type (customize)
}
