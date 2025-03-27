import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import {HttpClient, provideHttpClient, withInterceptorsFromDi} from '@angular/common/http';
import {BaseUrl} from "./shared/BaseUrl/BaseUrl";
import { NavComponent } from './nav/nav.component';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import {UserIdleModule} from "./angular-user-idle/angular-user-idle.module";
import {DateAdapter, MAT_DATE_LOCALE} from '@angular/material/core';
import {Platform} from "@angular/cdk/platform";
import {CustomDateAdapter} from "./cameras/camera.service";
import {MatMenu, MatMenuItem, MatMenuTrigger} from '@angular/material/menu';
import {SharedAngularMaterialModule} from './shared/shared-angular-material/shared-angular-material.module';

@NgModule({
  declarations: [
      AppComponent,
      NavComponent,
  ],
    bootstrap: [AppComponent],
    imports: [BrowserModule,
        AppRoutingModule,
        BrowserAnimationsModule,
        // Optionally you can set time for `idle`, `timeout` and `ping` in seconds.
        // Default values: `idle` is 600 (10 minutes), `timeout` is 300 (5 minutes)
        // and `ping` is 6q0 (1 minutes).
        UserIdleModule.forRoot({idle: 600, timeout: 60, ping: 60}),
        SharedAngularMaterialModule,
        MatMenu,
        MatMenuTrigger,
        MatMenuItem,
    ],
    exports: [
    ],
  providers: [    {
    provide: DateAdapter,
    useClass: CustomDateAdapter,
    deps: [MAT_DATE_LOCALE, Platform]
  },
  HttpClient, BaseUrl, provideHttpClient(withInterceptorsFromDi())]
})
export class AppModule {
}
