package com.precious.syncres.config;

import com.precious.syncres.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.UUID;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String emailOrId) throws UsernameNotFoundException {
        com.precious.syncres.entities.User user = userRepository.findByEmail(emailOrId)
                .orElseGet(() -> {
                    try {
                        return userRepository.findById(UUID.fromString(emailOrId))
                                .orElseThrow(() -> new UsernameNotFoundException("User not found with email or id : " + emailOrId));
                    } catch (IllegalArgumentException e) {
                        throw new UsernameNotFoundException("User not found with email or id : " + emailOrId);
                    }
                });

        return new User(user.getId().toString(), user.getPasswordHash(), new ArrayList<>());
    }
}
