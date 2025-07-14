package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

public class RefreshTokenInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        HandlerInterceptor.super.postHandle(request, response, handler, modelAndView);
        UserHolder.removeUser();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//        // 1. 获取session
//        HttpSession session = request.getSession();
//        // 2. 获取session中的用户
//        Object user = session.getAttribute("user");
//        // 3. 判断用户是否存在
//        // 4. 不存在则拦截
//        if (user == null) {
//            response.setStatus(401);
//            return false;
//        }
//        // 5. 存在，则保存信息到ThreadLocal
//        UserHolder.saveUser((UserDTO) user);
        // 6. 放行

        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            return true;
        }

        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries("login:token:" + token);
        if (userMap.isEmpty()) {
            return true;
        }

        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        UserHolder.saveUser(userDTO);

        stringRedisTemplate.expire("login:token:" + token, LOGIN_USER_TTL, TimeUnit.MINUTES);

        return true;
    }
}
