package cloudwebapp

import cloudservice.User

class BootStrap {
    CloudService cloudService
    RoleService roleService
    UserService userService
    UserRoleService userRoleService

    def init = { servletContext ->
        cloudService.start()
        List<String> authorities = ['ROLE_CLIENT', 'ROLE_ADMIN']
        authorities.each { authority ->
            if ( !roleService.findByAuthority(authority) ) {
                roleService.save(authority)
            }
        }
        if ( !userService.findByUsername('admin') ) {
            User u = new User(username: 'admin', productid: '0000-0000-0000-0000', password: 'elementary', email: 'changeme@changeme.com')
            u = userService.save(u)
            userRoleService.save(u, roleService.findByAuthority('ROLE_ADMIN'))
        }
    }
    def destroy = {
        cloudService.stop()
    }
}
