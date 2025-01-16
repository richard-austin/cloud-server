package com.cloudwebapp.dao;

import com.cloudwebapp.model.Role;
import com.cloudwebapp.model.User;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

public interface UserRepository extends JpaRepository<User, Long> {
    User findByEmail(String email);
    User findByUsername(String username);
    User findByProductid(String productid);
    User findByUsernameAndCloudAccount (String username, boolean cloudAccount);
    User findByUsernameNotAndCloudAccount(String username, boolean cloudAccount);
    User findByRoles(Collection<Role> roles);

    @Override
    void delete(@NotNull User user);

}
