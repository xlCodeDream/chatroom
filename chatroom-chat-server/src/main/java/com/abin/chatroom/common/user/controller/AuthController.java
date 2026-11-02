package com.abin.chatroom.common.user.controller;

import cn.hutool.core.util.StrUtil;
import com.abin.chatroom.common.common.domain.vo.response.ApiResult;
import com.abin.chatroom.common.user.dao.UserDao;
import com.abin.chatroom.common.user.domain.entity.User;
import com.abin.chatroom.common.user.service.LoginService;
import com.abin.chatroom.common.user.service.UserService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Date;

@RestController
@RequestMapping("/capi/user")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final LoginService loginService;
    private final UserDao userDao;
    private final PasswordEncoder passwordEncoder;
    private final UserService userService;

    public AuthController(AuthenticationManager authenticationManager, LoginService loginService,
                          UserDao userDao, PasswordEncoder passwordEncoder, UserService userService) {
        this.authenticationManager = authenticationManager;
        this.loginService = loginService;
        this.userDao = userDao;
        this.passwordEncoder = passwordEncoder;
        this.userService = userService;
    }

    @PostMapping("/public/login")
    public ApiResult<String> login(@RequestBody LoginRequest request) {
        if (StrUtil.isBlank(request.getName()) || StrUtil.isBlank(request.getPassword())) {
            return ApiResult.fail(400, "用户名或密码不能为空");
        }

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getName(), request.getPassword())//这个居然就直接选了一个实现类用户密码tokrn
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        User user = userDao.getByName(request.getName());
        String token = loginService.login(user.getId());
        return ApiResult.success(token);
    }

    @PostMapping("/public/register")
    public ApiResult<String> register(@RequestBody RegisterRequest request) {
        if (StrUtil.isBlank(request.getName()) || StrUtil.isBlank(request.getPassword())) {
            return ApiResult.fail(400, "用户名或密码不能为空");
        }
        if (request.getName().length() > 20) {
            return ApiResult.fail(400, "用户名不能超过20个字符");
        }
        if (request.getPassword().length() < 6) {
            return ApiResult.fail(400, "密码长度不能少于6位");
        }

        User existing = userDao.getByName(request.getName());
        if (existing != null) {
            return ApiResult.fail(400, "用户名已存在");
        }

        User user = User.builder()
                .name(request.getName())
                .password(passwordEncoder.encode(request.getPassword()))
                .openId("local_" + System.currentTimeMillis())
                .status(0)
                .activeStatus(2)
                .createTime(new Date())
                .updateTime(new Date())
                .build();

        userService.register(user);//这个方法在哪saveuser应该可以在产品文档中查到
        String token = loginService.login(user.getId());
        return ApiResult.success(token);
    }

    @GetMapping("/info")
    public ApiResult<User> info(@RequestHeader("token") String token) {
        Long uid = loginService.getValidUid(token);
        if (uid == null) {
            return ApiResult.fail(400, "token无效");
        }
        User user = userDao.getById(uid);
        return ApiResult.success(user);
    }

    @PostMapping("/logout")
    public ApiResult<Void> logout(@RequestHeader("token") String token) {
        Long uid = loginService.getValidUid(token);
        if (uid != null) {
            loginService.verify(token);
        }
        return ApiResult.success();
    }

    public static class LoginRequest {
        private String name;
        private String password;

        public String getName() {
            return name;
        }

        public void setUsername(String username) {
            this.name = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class RegisterRequest {
        private String name;
        private String password;

        public String getName() {
            return name;
        }

        public void setUsername(String username) {
            this.name = name;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}