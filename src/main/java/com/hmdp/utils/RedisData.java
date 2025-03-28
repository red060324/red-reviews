package com.hmdp.utils;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class RedisData implements Serializable {
    private static final long serialVersionUID = 1L;
    private LocalDateTime expireTime;
    private Object data;
}
