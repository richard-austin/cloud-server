package com.cloudwebapp.securingweb

import com.cloudwebapp.configuration.Config
import com.cloudwebapp.dao.RoleRepository
import com.cloudwebapp.dao.UserRepository
import com.cloudwebapp.dto.UserDto
import com.cloudwebapp.model.Role
import com.cloudwebapp.model.User
import com.cloudwebapp.services.LogService
import com.cloudwebapp.services.UserService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class InitialAccountSetup {

    @Autowired
    RoleRepository roleRepository
    @Autowired
    UserRepository userRepository
    @Autowired
    LogService logService
    @Autowired
    Config config

    @Bean
    CommandLineRunner run(UserService userService) {
        return (String[] args) -> {
            if(!userService.roleExists('ROLE_CLIENT'))
                userService.addRole('ROLE_CLIENT')

            if(!userService.roleExists('ROLE_CLOUD'))
                userService.addRole('ROLE_CLOUD')

            if(!userService.roleExists('ROLE_GUEST'))
                userService.addRole('ROLE_GUEST')

            if(!userService.userNameExists('cloud')) {
                Role role = roleRepository.findByName("ROLE_CLOUD")
                if (role != null)
                    userService.registerNewUserAccount(new UserDto(username: "cloud", password: "DrN3yuFAtSsK2w7AtTf66FFRVveBwtjU", credentialsNonExpired: true, header: "7yk=zJu+@77x@MTJG2HD*YLJgvBthkW!",  matchingPassword: "password", email: "nonexistent2@hfytrbhxgafdj.com", cloudAccount: true, role: role.getId()))
            }

            if(!userService.userNameExists('guest')) {
                Role role = roleRepository.findByName("ROLE_GUEST")
                if (role != null)
                    userService.registerNewUserAccount(new UserDto(username: "guest", password: "", matchingPassword: "", credentialsNonExpired: false, email: "nonexistent@hfytrbhxgafdj.com", cloudAccount: false, header: "", role: role.getId()))
            }

            User u = userRepository.findByUsernameNotAndCloudAccount('guest', false)
            // Start CloudAMQProxy if enabled in the config or if there is no local web account other than guest on the NVR
            if (config.cloudProxy.enabled || u == null)
                cloudProxyService.start()
        }
    }
}
