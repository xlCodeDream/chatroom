package com.abin.chatroom.common.user.service;

import com.abin.chatroom.common.common.domain.enums.NormalOrNoEnum;
import com.abin.chatroom.common.user.dao.UserDao;
import com.abin.chatroom.common.user.domain.entity.User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserDao userDao;

    public UserDetailsServiceImpl(UserDao userDao) {
        this.userDao = userDao;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userDao.getByName(username);
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在");
        }
        if (!NormalOrNoEnum.NORMAL.getStatus().equals(user.getStatus())) {
            throw new UsernameNotFoundException("用户已被封禁");
        }
        return new org.springframework.security.core.userdetails.User(
                user.getName(),
                user.getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }
}