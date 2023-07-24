package com.oyx.redis.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oyx.redis.bean.User;
import com.oyx.redis.dto.LoginFormDTO;
import com.oyx.redis.dto.Result;
import com.oyx.redis.dto.UserDTO;
import com.oyx.redis.mapper.UserMapper;
import com.oyx.redis.service.IUserService;
import com.oyx.redis.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import javax.swing.text.FieldView;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.oyx.redis.utils.RedisConstants.*;
import static com.oyx.redis.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@SuppressWarnings("ALL")
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static ObjectMapper obj = new ObjectMapper();

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            System.out.println("手机格式不正确");
            //不符合
            return Result.fail("手机格式不正确");
        }
        String code = RandomUtil.randomNumbers(6);
        //session.setAttribute("code",code);
        //设置两分钟的验证码有效期---手机号作为redis中的key，验证码作为value
        redisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);
        System.out.println(code);
        log.debug("验证码发生成功!!!,验证码{}",code);
        return Result.ok(code);
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) throws JsonProcessingException {
        //1、校验手机号
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            System.out.println("手机格式不正确");
            //不符合
            return Result.fail("手机格式不正确");
        }
        //2、校验验证码
        //String cacheCode = (String) session.getAttribute("code");
        String cacheCode = redisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        log.info("保存到session中的验证码{}",cacheCode);
        if(cacheCode == null || !cacheCode.equals(loginForm.getCode())){
            return Result.fail("验证码不正确");
        }
        //3、根据手机号查询用户信息
        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.eq("phone",phone);
        User user = userMapper.selectOne(qw);
        //4、用户不存在则直接创建一个
        if(user == null){
            user = createUserWithPhone(phone);
        }
        //5、将用户信息保存到UserDTO中
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);

        //随机生成token，作为登录令牌
        String token = UUID.randomUUID().toString(true);

        //将User对象转为HashMap存储
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName,fieldValue) ->
                            fieldValue.toString()
                        ));

        redisTemplate.opsForHash().putAll(LOGIN_USER_KEY+token,userMap);
        //设置token有效期
        redisTemplate.expire(LOGIN_USER_KEY+token,30,TimeUnit.MINUTES);
        return Result.ok(token);

        /*// 7.保存用户信息到 redis中
        // 7.1.随机生成token，作为登录令牌
        String token = UUID.randomUUID().toString(true);
        // 7.2.将User对象转为HashMap存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        // 7.3.存储
        String tokenKey = LOGIN_USER_KEY + token;
        redisTemplate.opsForHash().putAll(tokenKey, userMap);
        // 7.4.设置token有效期
        redisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);

        // 8.返回token
        return Result.ok(token);*/
    }

    private User createUserWithPhone(String phone) {
        // 1.创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        // 2.保存用户
        save(user);
        return user;
    }

}
