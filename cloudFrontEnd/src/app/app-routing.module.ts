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

const routes: Routes = [
  {path: 'live', component: LiveContainerComponent, canActivate: [OnlyClientUsersService]},
  {path: 'recording', component: RecordingControlComponent, canActivate: [OnlyClientUsersService]},
  {path: 'multicam', component: MultiCamViewComponent, canActivate: [OnlyClientUsersService]},
  {path: 'changepassword', component: ChangePasswordComponent, canActivate: [OnlyLoggedInService]},  // Change password while logged in
  {path: 'about/:isLocal', component: AboutComponent, canActivate: [OnlyAdminUsersService]},
  {path: 'about', component: AboutComponent, canActivate: [OnlyClientUsersService]},
  {path: 'setip', component: SetIpComponent, canActivate: [OnlyClientUsersService]},
  {path: 'cameraparams', component: CameraParamsComponent, canActivate: [OnlyClientUsersService]},
  {path: 'configsetup', component: ConfigSetupComponent, canActivate: [OnlyClientUsersService]},
  {path: 'login', component: LoginComponent, canActivate: [OnlyAnonUsersService]},
  {path: 'register', component: RegisterAccountComponent, canActivate: [OnlyAnonUsersService]},
  {path: 'accountadmin', component: AccountAdminComponent, canActivate: [OnlyAdminUsersService]},
  {path: 'dc', component: DrawdownCalcContainerComponent, canActivate: [OnlyClientUsersService]},
  {path: 'forgotpassword', component: ForgottenPasswordComponent, canActivate: [OnlyAnonUsersService]}, //Request an email link to reset password
  {path: 'resetpassword/:uniqueId', component: ResetPasswordComponent, canActivate: [OnlyAnonUsersService]},  // Reset the password after following email link
  {path: 'registerlocalnvraccount', component: RegisterLocalNvrAccountComponent, canActivate: [OnlyClientUsersService]}
];

@NgModule({
  imports: [RouterModule.forRoot(routes, {useHash: true})],
  exports: [RouterModule]
})
export class AppRoutingModule { }
