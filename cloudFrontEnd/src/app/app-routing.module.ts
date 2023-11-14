import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import {LiveContainerComponent} from "./live-container/live-container.component";
import {MultiCamViewComponent} from "./multi-cam-view/multi-cam-view.component";
import {RecordingControlComponent} from "./recording-control/recording-control.component";
import {ChangePasswordComponent} from "./change-password/change-password.component";
import {AboutComponent} from "./about/about.component";
import {SetIpComponent} from "./set-ip/set-ip.component";
import {CameraParamsComponent} from "./camera-params/camera-params.component";
import {DrawdownCalcContainerComponent} from "./drawdown-calc-container/drawdown-calc-container.component";
import {ConfigSetupComponent} from "./config-setup/config-setup.component";
import {LoginComponent} from "./login/login.component";
import {RegisterAccountComponent} from "./register-account/register-account.component";
import { AccountAdminComponent } from './accountAdmin/account-admin.component';
import {ForgottenPasswordComponent} from "./login/forgotten-password/forgotten-password.component";
import {OnlyAdminUsersService} from "./guards/only-admin-users.service";
import { OnlyClientUsersService } from './guards/only-client-users.service';
import {OnlyAnonUsersService} from "./guards/only-anon-users.service";
import {OnlyLoggedInService} from "./guards/only-logged-in.service";
import {ResetPasswordComponent} from "./reset-password/reset-password.component";
import {RegisterLocalNvrAccountComponent} from "./register-local-nvr-account/register-local-nvr-account.component";
import {RemoveLocalNvrAccountComponent} from "./remove-local-nvr-account/remove-local-nvr-account.component";
import {GetActiveIPAddressesComponent} from './get-active-ipaddresses/get-active-ipaddresses.component';
import {GetLocalWifiDetailsComponent} from './get-local-wifi-details/get-local-wifi-details.component';
import {WifiSettingsComponent} from './wifi-settings/wifi-settings.component';
import {SetupSMTPClientComponent} from './setup-smtpclient/setup-smtpclient.component';
import {ChangeEmailComponent} from './change-email/change-email.component';
import {CreateUserAccountContainerComponent} from './create-user-account-container/create-user-account-container.component';

const routes: Routes = [
  {path: 'live/:streamName', component: LiveContainerComponent},
  {path: 'recording/:streamName', component: RecordingControlComponent},
  {path: 'multicam', component: MultiCamViewComponent, canActivate: [OnlyClientUsersService]},
  {path: 'changepassword', component: ChangePasswordComponent, canActivate: [OnlyLoggedInService]},  // Change password while logged in
  {path: 'about/:isLocal', component: AboutComponent, canActivate: [OnlyAdminUsersService]},
  {path: 'about', component: AboutComponent, canActivate: [OnlyClientUsersService]},
  {path: 'setip', component: SetIpComponent, canActivate: [OnlyClientUsersService]},
  {path: 'cameraparams/:camera', component: CameraParamsComponent},
  {path: 'configsetup', component: ConfigSetupComponent, canActivate: [OnlyClientUsersService]},
  {path: 'login', component: LoginComponent, canActivate: [OnlyAnonUsersService]},
  {path: 'register', component: RegisterAccountComponent, canActivate: [OnlyAnonUsersService]},
  {path: 'accountadmin', component: AccountAdminComponent, canActivate: [OnlyAdminUsersService]},
  {path: 'dc', component: DrawdownCalcContainerComponent, canActivate: [OnlyClientUsersService]},
  {path: 'forgotpassword', component: ForgottenPasswordComponent, canActivate: [OnlyAnonUsersService]}, //Request an email link to reset password
  {path: 'resetpassword/:uniqueId', component: ResetPasswordComponent, canActivate: [OnlyAnonUsersService]},  // Reset the password after following email link
  {path: 'registerlocalnvraccount', component: RegisterLocalNvrAccountComponent, canActivate: [OnlyClientUsersService]},
  {path: 'removelocalnvraccount', component: RemoveLocalNvrAccountComponent, canActivate: [OnlyClientUsersService]},
  {path: 'getactiveipaddresses', component: GetActiveIPAddressesComponent, canActivate: [OnlyClientUsersService]},
  {path: 'getlocalwifidetails', component: GetLocalWifiDetailsComponent, canActivate: [OnlyClientUsersService]},
  {path: 'wifisettings', component: WifiSettingsComponent, canActivate: [OnlyClientUsersService]},
  {path: 'setupsmtpclient', component: SetupSMTPClientComponent, canActivate: [OnlyAdminUsersService]},
  {path: 'changeadminaccountemail', component: ChangeEmailComponent, canActivate: [OnlyAdminUsersService]},
  {path: 'cua', component: CreateUserAccountContainerComponent},
];

@NgModule({
  imports: [RouterModule.forRoot(routes, {useHash: true})],
  exports: [RouterModule]
})
export class AppRoutingModule { }
