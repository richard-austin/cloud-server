package com.cloudwebapp.securingweb

import com.cloudwebapp.dao.RoleRepository
import com.cloudwebapp.dao.UserRepository
import com.cloudwebapp.dto.UserDto
import com.cloudwebapp.model.Role
import com.cloudwebapp.services.CloudService
import com.cloudwebapp.services.UserService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class InitialAccountSetup {
    @Autowired
    CloudService cloudService
    @Autowired
    RoleRepository roleRepository
    @Autowired
    UserRepository userRepository
    @Autowired
    UserService userService

    @Bean
    CommandLineRunner run(UserService userService) {
        return (String[] args) -> {
            cloudService.start()
            List<String> authorities = ['ROLE_CLIENT', 'ROLE_ADMIN']
            authorities.each { authority ->
                if(!userService.roleExists(authority))
                    userService.addRole(authority)
             }
            if (!userService.userNameExists('admin')) {
                Role adminRole = roleRepository.findByName('ROLE_ADMIN')
                if(adminRole != null) {
                    def u = new UserDto(username: 'admin', productid: '0000-0000-0000-0000', password: 'elementary', email: 'changeme@changeme.com', role: adminRole.getId())
                    userService.registerNewUserAccount(u)
                }
            }
        }
    }

    def destroy = {
        cloudService.stop()
    }
}
