package com.cloudwebapp.beans;

import org.jetbrains.annotations.NotNull;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public class AppContextManager implements ApplicationContextAware {
    private static ApplicationContext _appCtx;

    @Override
    public void setApplicationContext(@NotNull ApplicationContext ctx){
        _appCtx = ctx;
    }

    public static ApplicationContext getAppContext(){
        return _appCtx;
    }
}
