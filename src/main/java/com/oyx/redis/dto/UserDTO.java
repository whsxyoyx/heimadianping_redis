package com.oyx.redis.dto;

import com.oyx.redis.bean.User;
import lombok.Data;

@Data
public class UserDTO{
    private Long id;
    private String nickName;
    private String icon;
}
