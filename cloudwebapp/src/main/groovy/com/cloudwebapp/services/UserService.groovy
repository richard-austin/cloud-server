package com.cloudwebapp.services

import com.cloudwebapp.dao.RoleRepository
import com.cloudwebapp.dao.UserRepository
import com.cloudwebapp.dto.UserDto
import com.cloudwebapp.error.UserAlreadyExistException
import com.cloudwebapp.model.Role
import com.cloudwebapp.model.User
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class UserService {
    @Autowired
    UserRepository userRepository
    @Autowired
    RoleRepository roleRepository

    @Autowired
    private PasswordEncoder passwordEncoder

    User registerNewUserAccount(final UserDto accountDto) {
        if (userNameExists(accountDto.getUsername())) {
            throw new UserAlreadyExistException("There is an account with that username: " + accountDto.getUsername())
        }
        final User user = new User()
        user.setUsername(accountDto.getUsername())
        user.setPassword(passwordEncoder.encode(accountDto.password))
        user.setProductid(accountDto.productid)
        user.setCredentialsNonExpired(accountDto.credentialsNonExpired)
        user.setEmail(accountDto.email)
        user.setCloudAccount(accountDto.cloudAccount)
        user.setHeader(accountDto.header)
        user.setEnabled(true)
        user.setRoles(Collections.singletonList(roleRepository.findById(accountDto.role).get()))

        return userRepository.save(user)
    }

    Role addRole(final String roleName) {
        def role = new Role(roleName)
        return roleRepository.save(role)
    }

    boolean roleExists(String roleName) {
        return roleRepository.findByName(roleName) != null
    }

    boolean userNameExists(final String username) {
        return userRepository.findByUsername(username) != null
    }
}
