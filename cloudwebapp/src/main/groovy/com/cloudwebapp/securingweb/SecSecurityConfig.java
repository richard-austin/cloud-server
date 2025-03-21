package com.cloudwebapp.securingweb;

import com.cloudwebapp.beans.CloudAuthFailureHandler;
import com.cloudwebapp.beans.CloudAuthSuccessHandler;
import com.cloudwebapp.beans.MyRememberMeServices;
import com.cloudwebapp.dao.UserRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.RememberMeServices;

@Configuration
@EnableWebSecurity
public class SecSecurityConfig {
    @Value("${spring-security.enabled}")
    boolean enabled;


    SecSecurityConfig(MyRememberMeServices rememberMeServices,
                      MyUserDetailsService myUserDetailsService,
                      LogService logService,
                      CloudAuthSuccessHandler cloudAuthSuccessHandler,
                      CloudAuthFailureHandler cloudAuthFailureHandler,
                      PasswordEncoder passwordEncoder) {
        this.rememberMeServices = rememberMeServices;
        this.myUserDetailsService = myUserDetailsService;
        this.logService = logService;
        this.cloudAuthSuccessHandler = cloudAuthSuccessHandler;
        this.cloudAuthFailureHandler = cloudAuthFailureHandler;
        this.passwordEncoder = passwordEncoder;
    }

    RememberMeServices rememberMeServices;
    MyUserDetailsService myUserDetailsService;
    LogService logService;
    CloudAuthSuccessHandler cloudAuthSuccessHandler;
    CloudAuthFailureHandler cloudAuthFailureHandler;
    PasswordEncoder passwordEncoder;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, CloudService cloudService, UserRepository userRepository) throws Exception {
        if (enabled) {
            http
                    .csrf(AbstractHttpConfigurer::disable)  // @TODO Makes Restful API calls available to any role, or no role
                    .authorizeHttpRequests((requests) -> requests
                            .requestMatchers("/cloud/sendResetPasswordLink").anonymous()
                            .requestMatchers("/cloud/resetPassword").anonymous()
                    )
                    .authorizeHttpRequests((requests) -> requests
                            .requestMatchers("/cloudstomp").permitAll()
                            .requestMatchers("/*.css").permitAll()
                            .requestMatchers("/*.js").permitAll()
                            .requestMatchers("/*.ttf").permitAll()
                            .requestMatchers("/*.woff2").permitAll()
                            .requestMatchers("/cloud/register").permitAll()
                      //      .requestMatchers("/*/favicon.ico").permitAll()
                            .requestMatchers("/*").permitAll()
                            .requestMatchers("/javascripts/*.js").permitAll()
                            .requestMatchers("/cloud/sendResetPasswordLink").permitAll()
                            .requestMatchers("/cloud/getUserAuthorities").permitAll()
                            .requestMatchers("/cloud/isTransportActive").permitAll()
                            .anyRequest().authenticated()
                    )
                    .authenticationProvider(new TwoFactorAuthProvider(passwordEncoder, myUserDetailsService, userRepository, logService, cloudService))
                    .rememberMe(rememberMe -> rememberMe
                            .rememberMeServices(rememberMeServices))
                    .formLogin((form) -> form
                            .successHandler(cloudAuthSuccessHandler)
                          //  .authenticationDetailsSource(authenticationDetailsSource())
                            .loginProcessingUrl("/login/authenticate")
                           // .failureUrl("/")
                            .failureHandler(cloudAuthFailureHandler)
                            .permitAll()
                    )
                    .logout(httpSecurityLogoutConfigurer ->
                            httpSecurityLogoutConfigurer
                                    .logoutUrl("/logout")
                                    .addLogoutHandler(cloudAuthFailureHandler)
                                    .logoutSuccessUrl("/")
                                    .permitAll());
        }
        return http.build();
    }

//    public TwoFactorAuthenticationDetailsSource authenticationDetailsSource() {
//        return new TwoFactorAuthenticationDetailsSource();
//    }

}
