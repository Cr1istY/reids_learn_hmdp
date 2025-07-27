package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author crist
 * @since 2025-07-10
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 不符合，报错
            log.debug("手机号格式错误");
            return Result.fail("手机号格式错误");
        }
        // 符合，生成验证码
        String code = RandomUtil.randomNumbers(6);
        // 保存验证码到redis
        // session.setAttribute("code", code);
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone, code, RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);


        // 发送验证码
        log.debug("发送短信验证码成功 {}", code);

        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {

        if (RegexUtils.isPhoneInvalid(loginForm.getPhone())) {
            return Result.fail("手机号格式错误");
        }

//        Object cacheCode = session.getAttribute("code");
        String cacheCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + loginForm.getPhone());
        String code = loginForm.getCode();
        if (cacheCode == null || code.isEmpty() || !cacheCode.equals(code)) {
            return Result.fail("验证码错误");
        }

        User user = query().eq("phone", loginForm.getPhone()).one();
        if (user == null) {
            user = createUserWithPhone(loginForm.getPhone());
        }
//        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
        // save to redis
        String token = UUID.randomUUID().toString(true);
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        String userToken = RedisConstants.LOGIN_USER_KEY + token;
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(), CopyOptions.create()
                .setIgnoreNullValue(true).setFieldValueEditor((fieldName, value) -> value.toString()));
        stringRedisTemplate.opsForHash().putAll(userToken, userMap);
        stringRedisTemplate.expire(userToken, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        return Result.ok(token);
    }

    @Override
    public Result logout(HttpServletRequest request) {
        String name = UserHolder.getUser().getNickName();
        LocalDateTime now = LocalDateTime.now();
        String token = request.getHeader("authorization");
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        stringRedisTemplate.delete(tokenKey);
        log.debug("用户{}退出登录", name);
        UserHolder.removeUser();
        return Result.ok();
    }

    @Override
    public Result sign() {
        Long userId = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String key = RedisConstants.USER_SIGN_KEY + userId + ":" + keySuffix;
        int dayOfMonth = now.getDayOfMonth();
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        Long userId = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String key = RedisConstants.USER_SIGN_KEY + userId + ":" + keySuffix;
        int dayOfMonth = now.getDayOfMonth();

//        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );
        if (result == null || result.isEmpty()) {
            return Result.ok(0);
        }
        Long num = result.get(0);
        if (num == null || num == 0) {
            return Result.ok(0);
        }
        Integer count = 0;
        while (true) {
            if ((num & 1) == 0) {
                break;
            }else {
                count++;
            }
            num >>>= 1;
        }

        return Result.ok(count);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));

        save(user);

        return user;
    }
}
