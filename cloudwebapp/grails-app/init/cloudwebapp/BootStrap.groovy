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
            User u = new User(username: 'admin', productid: 'O3RY-HQGC-W5FP-6UYW', password: 'elementary', email: 'a@b.com')
            u = userService.save(u)
            userRoleService.save(u, roleService.findByAuthority('ROLE_CLIENT'))
//            u = new User(username: 'admin2', productid: 'TZS5-NLFF-A1PI-KEJV', password: 'elementary', email: 'changeme@changeme.com')
//            u = userService.save(u)
//            userRoleService.save(u, roleService.findByAuthority('ROLE_CLIENT'))
        }
    }
    def destroy = {
        cloudService.stop()
    }
}
