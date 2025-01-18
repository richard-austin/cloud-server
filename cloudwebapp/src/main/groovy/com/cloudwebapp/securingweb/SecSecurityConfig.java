package com.cloudwebapp.securingweb;

import com.cloudwebapp.beans.CloudAuthSuccessHandler;
import com.cloudwebapp.dao.UserRepository;
import com.cloudwebapp.eventlisteners.SecCamSecurityEventListener;
import com.cloudwebapp.security.MyUserDetailsService;
import com.cloudwebapp.security.TwoFactorAuthProvider;
import com.cloudwebapp.services.CloudService;
import com.cloudwebapp.services.LogService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.RememberMeServices;

@Configuration
@EnableWebSecurity
public class SecSecurityConfig {
    @Value("${spring-security.enabled}")
    boolean enabled;

    SecSecurityConfig(RememberMeServices rememberMeServices, MyUserDetailsService myUserDetailsService, SecCamSecurityEventListener secCamSecurityEventListener, LogService logService, CloudAuthSuccessHandler cloudAuthSuccessHandler) {
        this.rememberMeServices = rememberMeServices;
        this.myUserDetailsService = myUserDetailsService;
        this.secCamSecurityEventListener = secCamSecurityEventListener;
        this.logService = logService;
        this.cloudAuthSuccessHandler = cloudAuthSuccessHandler;
    }

    RememberMeServices rememberMeServices;
    MyUserDetailsService myUserDetailsService;
    LogService logService;
    SecCamSecurityEventListener secCamSecurityEventListener;
    CloudAuthSuccessHandler cloudAuthSuccessHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, CloudService cloudService, UserRepository userRepository) throws Exception {
        if (enabled) {
            http
                    .csrf(AbstractHttpConfigurer::disable)  // @TODO Makes Restful API calls available to any role, or no role
                    .authorizeHttpRequests((requests) -> requests
                            .requestMatchers("/recover/forgotPassword").anonymous()
                            .requestMatchers("/recover/sendResetPasswordLink").anonymous()
                            .requestMatchers("/recover/resetPasswordForm").anonymous()
                            .requestMatchers("/recover/resetPassword").anonymous()
                    )
                    .authorizeHttpRequests((requests) -> requests
                            .requestMatchers("/*/cloudstomp/*").permitAll()
                            .requestMatchers("/*.css").permitAll()
                            .requestMatchers("/*.js").permitAll()
                            .requestMatchers("/*.ttf").permitAll()
                            .requestMatchers("/*.woff2").permitAll()
                            .requestMatchers("/cloud/register").permitAll()
                            .requestMatchers("cloud/sendResetPasswordLink").permitAll()
                            .requestMatchers("/*/favicon.ico").permitAll()
                            .requestMatchers("/*.index.html").permitAll()
                            .requestMatchers("/javascripts/*.js").permitAll()
                            .requestMatchers("/cloud/sendResetPasswordLink").permitAll()
                            .requestMatchers("/cloud/getUserAuthorities").permitAll()
                            .requestMatchers("/cloud/resetPassword").hasAnyRole("USER", "ADMIN")
                            .requestMatchers("/cloud/isTransportActive").permitAll()
                            .anyRequest().authenticated()
                    )
                    .authenticationProvider(new TwoFactorAuthProvider(passwordEncoder(), myUserDetailsService, userRepository, logService, cloudService))
                    .rememberMe(rememberMe -> rememberMe
                            .rememberMeServices(rememberMeServices))
                    .formLogin((form) -> form
                            .successHandler(cloudAuthSuccessHandler)
                          //  .authenticationDetailsSource(authenticationDetailsSource())
                            .loginProcessingUrl("/login/authenticate")
                            .failureUrl("/login/auth?error")
                            .permitAll()
                    )
                    .logout(httpSecurityLogoutConfigurer ->
                            httpSecurityLogoutConfigurer
                                    .logoutUrl("/logout")
                                    .addLogoutHandler(secCamSecurityEventListener)
                                    .logoutSuccessUrl("/")
                                    .permitAll());
        }
        return http.build();
    }

//    public TwoFactorAuthenticationDetailsSource authenticationDetailsSource() {
//        return new TwoFactorAuthenticationDetailsSource();
//    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(11);
    }
}
