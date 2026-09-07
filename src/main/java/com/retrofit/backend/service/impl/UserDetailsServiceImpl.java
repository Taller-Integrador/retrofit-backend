package com.retrofit.backend.service.impl;

import com.retrofit.backend.model.User;
import com.retrofit.backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {
        private final UserRepository userRepository;

        @Override
        @Transactional(readOnly = true)
        public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
                User user = userRepository.findByUsername(username)
                                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));

                Set<GrantedAuthority> authorities = user.getRole().getPermissions().stream()
                                .map(permission -> (GrantedAuthority) new SimpleGrantedAuthority(permission.getName()))
                                .collect(Collectors.toSet());

                authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().getName()));
                authorities.add(new SimpleGrantedAuthority(user.getRole().getName()));

                return new org.springframework.security.core.userdetails.User(
                                user.getUsername(),
                                user.getPassword(),
                                user.isActive(),
                                true, // accountNonExpired
                                true, // credentialsNonExpired
                                true, // accountNonLocked
                                authorities);
        }
}