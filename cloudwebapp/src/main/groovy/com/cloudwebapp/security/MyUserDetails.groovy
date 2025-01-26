package com.cloudwebapp.security

import com.cloudwebapp.model.Role
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

interface MyUserDetails extends UserDetails, Serializable {
    Collection<? extends GrantedAuthority> getAuthorities()
    Collection<Role> getRoles()
    String getPassword()
    String getUsername()
    String getEmail()
    boolean getCloudAccount()
    String getHeader()
    boolean getEnabled()
    @Override
    boolean isCredentialsNonExpired()

}
