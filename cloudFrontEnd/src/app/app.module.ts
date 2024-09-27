import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import {HttpClient, provideHttpClient, withInterceptorsFromDi} from '@angular/common/http';
import {BaseUrl} from "./shared/BaseUrl/BaseUrl";
import { NavComponent } from './nav/nav.component';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import {ReactiveFormsModule} from "@angular/forms";
import { IdleTimeoutModalComponent } from './idle-timeout-modal/idle-timeout-modal.component';
import {UserIdleModule} from "./angular-user-idle/angular-user-idle.module";
import {DateAdapter, MAT_DATE_LOCALE, MatOption} from '@angular/material/core';
import {Platform} from "@angular/cdk/platform";
import {CustomDateAdapter} from "./cameras/camera.service";
import {ActivemqCredentialsComponent} from './activemq-credentials/activemq-credentials.component';
import {SharedModule} from './shared/shared.module';
import {MatCard, MatCardContent, MatCardHeader, MatCardSubtitle, MatCardTitle} from '@angular/material/card';
import {MatCheckbox} from '@angular/material/checkbox';
import {MatTooltip} from '@angular/material/tooltip';
import {
    MatCell,
    MatCellDef,
    MatColumnDef,
    MatHeaderCell,
    MatHeaderCellDef,
    MatHeaderRow, MatHeaderRowDef,
    MatRow,
    MatRowDef,
    MatTable
} from '@angular/material/table';
import { MatIcon } from '@angular/material/icon';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import {MatButton, MatIconAnchor, MatIconButton} from '@angular/material/button';
import {MatMenu, MatMenuItem, MatMenuTrigger} from '@angular/material/menu';
import {RouterOutlet} from '@angular/router';
import {MatError, MatFormField, MatHint, MatLabel} from '@angular/material/form-field';
import {MatInput} from '@angular/material/input';
import {MatDialogActions, MatDialogContent, MatDialogTitle} from '@angular/material/dialog';
import {SetupSMTPClientComponent} from './setup-smtpclient/setup-smtpclient.component';
import {MatSelect} from '@angular/material/select';
import {LoginComponent} from './login/login.component';
import {RegisterAccountComponent} from './register-account/register-account.component';
import {ProductIdInputComponent} from './register-account/product-id-input/product-id-input.component';
import {ForgottenPasswordComponent} from './login/forgotten-password/forgotten-password.component';

@NgModule({
  declarations: [
      AppComponent,
      ActivemqCredentialsComponent,
      SetupSMTPClientComponent,
      // CamerasComponent,
      NavComponent,
      IdleTimeoutModalComponent,
      LoginComponent,
      ForgottenPasswordComponent,
      RegisterAccountComponent,
      ProductIdInputComponent
  ],
    bootstrap: [AppComponent],
    imports: [BrowserModule,
        AppRoutingModule,
        BrowserAnimationsModule,
        // Optionally you can set time for `idle`, `timeout` and `ping` in seconds.
        // Default values: `idle` is 600 (10 minutes), `timeout` is 300 (5 minutes)
        // and `ping` is 6q0 (1 minutes).
        UserIdleModule.forRoot({idle: 600, timeout: 60, ping: 60}),
        SharedModule,
        MatCard,
        MatCardTitle,
        MatCardContent,
        MatCheckbox,
        MatTooltip,
        MatTable,
        MatHeaderCell,
        MatCell,
        MatColumnDef,
        MatHeaderRow,
        MatRow,
        MatIcon,
        MatCellDef,
        MatHeaderCellDef,
        MatProgressSpinner,
        MatIconAnchor,
        MatButton,
        MatMenuTrigger,
        MatMenu,
        MatMenuItem,
        MatCardSubtitle,
        RouterOutlet,
        MatFormField,
        ReactiveFormsModule,
        MatInput,
        MatLabel,
        MatHint,
        MatError, MatDialogTitle, MatDialogContent, MatDialogActions, MatCardHeader, MatSelect, MatOption, MatIconButton, MatRowDef, MatHeaderRowDef],
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
