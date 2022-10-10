import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { CamerasComponent } from './cameras/cameras.component';
import {HttpClient, HttpClientModule} from "@angular/common/http";
import {BaseUrl} from "./shared/BaseUrl/BaseUrl";
import { NavComponent } from './nav/nav.component';
import { VideoComponent } from './video/video.component';
import { LiveContainerComponent } from './live-container/live-container.component';
import { MultiCamViewComponent } from './multi-cam-view/multi-cam-view.component';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import {MatCheckboxModule} from "@angular/material/checkbox";
import {FormsModule, ReactiveFormsModule} from "@angular/forms";
import {MatButtonModule} from "@angular/material/button";
import {MatButtonToggleModule} from "@angular/material/button-toggle";
import {MatCardModule} from "@angular/material/card";
import { RecordingControlComponent } from './recording-control/recording-control.component';
import {MatSelectModule} from "@angular/material/select";
import { ReportingComponent } from './reporting/reporting.component';
import { ChangePasswordComponent } from './change-password/change-password.component';
import {MatIconModule} from "@angular/material/icon";
import {MatFormFieldModule} from "@angular/material/form-field";
import { AboutComponent } from './about/about.component';
import { SetIpComponent } from './set-ip/set-ip.component';
import {MatDialogModule} from "@angular/material/dialog";
import { IdleTimeoutModalComponent } from './idle-timeout-modal/idle-timeout-modal.component';
import { CameraParamsComponent } from './camera-params/camera-params.component';
import { DrawdownCalcContainerComponent } from './drawdown-calc-container/drawdown-calc-container.component';
import {UserIdleModule} from "./angular-user-idle/angular-user-idle.module";
import {MatMenuModule} from "@angular/material/menu";
import { LayoutModule } from '@angular/cdk/layout';
import {MatProgressSpinnerModule} from "@angular/material/progress-spinner";
import {MatInputModule} from "@angular/material/input";
import { ConfigSetupComponent } from './config-setup/config-setup.component';
import {MatTableModule} from "@angular/material/table";
import {MatSortModule} from "@angular/material/sort";
import {MatTooltipModule} from "@angular/material/tooltip";
import { ExcludeOwnStreamPipe } from './config-setup/exclude-own-stream.pipe';
import { DisableControlDirective } from './config-setup/disable-control.directive';
import { MapToKeyValuePipe } from './config-setup/map-to-key-value.pipe';
import { CredentialsForCameraAccessComponent } from './credentials-for-camera-access/credentials-for-camera-access.component';
import { LoginComponent } from './login/login.component';
import { RegisterAccountComponent } from './register-account/register-account.component';
import { ProductIdInputComponent } from './register-account/product-id-input/product-id-input.component';
import { AccountAdminComponent } from './accountAdmin/account-admin.component';
import { FilterPipe } from './accountAdmin/filter.pipe';
import { ForgottenPasswordComponent } from './login/forgotten-password/forgotten-password.component';
import {OnlyAdminUsersService} from "./guards/only-admin-users.service";
import {OnlyClientUsersService} from "./guards/only-client-users.service";
import {OnlyAnonUsersService} from "./guards/only-anon-users.service";
import { ResetPasswordComponent } from './reset-password/reset-password.component';
import {OnlyLoggedInService} from "./guards/only-logged-in.service";
import { SortPipe } from './accountAdmin/sort.pipe';
import { RegisterLocalNvrAccountComponent } from './register-local-nvr-account/register-local-nvr-account.component';
import { RemoveLocalNvrAccountComponent } from './remove-local-nvr-account/remove-local-nvr-account.component';
import { GetActiveIPAddressesComponent } from './get-active-ipaddresses/get-active-ipaddresses.component';
import { GetLocalWifiDetailsComponent } from './get-local-wifi-details/get-local-wifi-details.component';
import { WifiSettingsComponent } from './wifi-settings/wifi-settings.component';
import { PTZControlsComponent } from './live-container/ptzcontrols/ptzcontrols.component';
import { PTZButtonComponent } from './live-container/ptzcontrols/ptzbutton/ptzbutton.component';
import { PresetButtonComponent } from './live-container/ptzcontrols/preset-button/preset-button.component';
import {MatDividerModule} from "@angular/material/divider";
import {MatSlideToggleModule} from "@angular/material/slide-toggle";

@NgModule({
  declarations: [
    AppComponent,
    CamerasComponent,
    NavComponent,
    VideoComponent,
    LiveContainerComponent,
    MultiCamViewComponent,
    RecordingControlComponent,
    ReportingComponent,
    ChangePasswordComponent,
    AboutComponent,
    SetIpComponent,
    IdleTimeoutModalComponent,
    CameraParamsComponent,
    DrawdownCalcContainerComponent,
    ConfigSetupComponent,
    ExcludeOwnStreamPipe,
    DisableControlDirective,
    MapToKeyValuePipe,
    CredentialsForCameraAccessComponent,
    LoginComponent,
    RegisterAccountComponent,
    ProductIdInputComponent,
    AccountAdminComponent,
    FilterPipe,
    ForgottenPasswordComponent,
    ResetPasswordComponent,
    SortPipe,
    RegisterLocalNvrAccountComponent,
    RemoveLocalNvrAccountComponent,
    GetActiveIPAddressesComponent,
    GetLocalWifiDetailsComponent,
    WifiSettingsComponent,
    PTZControlsComponent,
    PTZButtonComponent,
    PresetButtonComponent
  ],
    imports: [
        BrowserModule,
        AppRoutingModule,
        HttpClientModule,
        BrowserAnimationsModule,
        MatCardModule,
        ReactiveFormsModule,
        MatFormFieldModule,
        MatCheckboxModule,
        MatButtonModule,
        MatButtonToggleModule,
        MatInputModule,
        MatIconModule,
        MatSelectModule,
        MatDialogModule,
        MatMenuModule,
        MatIconModule,
        MatProgressSpinnerModule,
        FormsModule,
        // Optionally you can set time for `idle`, `timeout` and `ping` in seconds.
        // Default values: `idle` is 600 (10 minutes), `timeout` is 300 (5 minutes)
        // and `ping` is 60 (1 minutes).
        UserIdleModule.forRoot({idle: 600, timeout: 60, ping: 60}),
        LayoutModule,
        MatTableModule,
        MatSortModule,
        MatTooltipModule,
        MatDividerModule,
        MatSlideToggleModule
    ],
  providers: [HttpClient, BaseUrl, OnlyAdminUsersService, OnlyClientUsersService, OnlyAnonUsersService, OnlyLoggedInService, SortPipe],
  bootstrap: [AppComponent],
  entryComponents: [IdleTimeoutModalComponent]
})
export class AppModule {
}
