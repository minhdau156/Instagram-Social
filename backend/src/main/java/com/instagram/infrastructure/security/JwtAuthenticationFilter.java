package com.instagram.infrastructure.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.instagram.domain.model.PermissionName;
import com.instagram.domain.model.Role;
import com.instagram.domain.port.out.RoleRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtTokenProvider tokenProvider;
    private final RoleRepository roleRepository;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, Optional<RoleRepository> roleRepository) {
        this.tokenProvider = tokenProvider;
        this.roleRepository = roleRepository.orElse(null);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        String token = authHeader.substring(7);
        Optional<UUID> userId = tokenProvider.validateAccessToken(token);
        if (userId.isPresent()) {
            try {
                Set<Role> roles = roleRepository.findRolesByUserId(userId.get());
                Set<PermissionName> permissions = roleRepository.findPermissionNamesByUserId(userId.get());

                List<GrantedAuthority> authorities = new ArrayList<>();
                for (Role role : roles) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getName().name()));
                }
                for (PermissionName permission : permissions) {
                    authorities.add(new SimpleGrantedAuthority(permission.name()));
                }

                UserDetails userDetails = User.withUsername(userId.get().toString())
                        .password("")
                        .authorities(authorities)
                        .build();
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception e) {
                log.warn("Could not load authorities for userId={}, leaving request unauthenticated: {}",
                        userId.get(), e.getMessage());
            }
        }
        filterChain.doFilter(request, response);
    }
}
