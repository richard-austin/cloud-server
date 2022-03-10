package cloudservice

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import grails.compiler.GrailsCompileStatic

@GrailsCompileStatic
@EqualsAndHashCode(includes = 'username')
@ToString(includes = 'username', includeNames = true, includePackage = false)
class User implements Serializable {

    private static final long serialVersionUID = 1

    String username
    String password
    String productid
    String email

    boolean enabled = true
    boolean accountExpired
    boolean accountLocked
    boolean passwordExpired

    Set<Role> getAuthorities() {
        (UserRole.findAllByUser(this) as List<UserRole>)*.role as Set<Role>
    }

    static constraints = {
        password nullable: false, blank: false, password: true
        username nullable: false, blank: false, unique: true
        productid(nullable: false, blank: false, unique: true,
                validator: { String productid ->
                    if (!productid.matches(/^(?:[A-Z0-9]{4}-){3}[A-Z0-9]{4}/))
                        return "Product ID Format is Invalid"
                }
        )
        email( nullable: false, blank: false, unique: true,
              validator: { String email ->
                  if(!email.matches(/^([a-zA-Z0-9_\-\.]+)@((\[[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.)|(([a-zA-Z0-9\-]+\.)+))([a-zA-Z]{2,4}|[0-9]{1,3})(\]?)$/))
                     return "email address is invalid"
              }
        )
        enabled nullable: false, inList: [true, false]
    }

    static mapping = {
        password column: '`password`'
        dynamicUpdate true
    }
}
