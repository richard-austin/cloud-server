import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import {OnlyAnonUsersService} from './guards/only-anon-users.service';
import {LoginComponent} from './login/login.component';
import {RegisterAccountComponent} from './register-account/register-account.component';
import {OnlyClientUsersService} from './guards/only-client-users.service';
import {OnlyAdminUsersService} from './guards/only-admin-users.service';
import {OnlyLoggedInService} from './guards/only-logged-in.service';
import {SetupSMTPClientComponent} from './setup-smtpclient/setup-smtpclient.component';
import {ForgottenPasswordComponent} from './login/forgotten-password/forgotten-password.component';
import {ResetPasswordComponent} from './reset-password/reset-password.component';

const routes: Routes = [
  {path: 'live/:streamName', loadChildren: () => import('./live-container/live-container.module').then(m => m.LiveContainerModule), canActivate: [OnlyClientUsersService]},
  {path: 'recording/:streamName', loadChildren: () => import('./recording-control/recording-control.module').then(m => m.RecordingControlModule), canActivate: [OnlyClientUsersService]},
  {path: 'multicam', loadChildren: () => import('./multi-cam-view/multi-cam-view.module').then(m => m.MultiCamViewModule), canActivate: [OnlyClientUsersService]},
  {path: 'changeemail', loadChildren: () => import('./change-email/change-email.module').then(m => m.ChangeEmailModule), canActivate:[OnlyLoggedInService]},
  {path: 'changepassword', loadChildren: () => import('./change-password/change-password.module').then(m => m.ChangePasswordModule), canActivate:[OnlyLoggedInService]},
  {path: 'cameraparams/:camera', loadChildren: () => import('./camera-params/cam-params.module').then(m => m.CamParamsModule), canActivate: [OnlyClientUsersService]},
  {path: 'configsetup', loadChildren: () => import('./config-setup/config-setup.module').then(m => m.ConfigSetupModule), canActivate: [OnlyClientUsersService]},
  {path: 'general', loadChildren: () => import('./general/general.module').then(m => m.GeneralModule), canActivate: [OnlyLoggedInService]},
  {path: 'wifi', loadChildren: () => import('./wifi-settings/wifi-settings.module').then(m => m.WifiSettingsModule), canActivate: [OnlyClientUsersService]},
  {path: 'accountadmin', loadChildren: () => import('./accountAdmin/account-admin.module').then(m => m.AccountAdminModule), canActivate:[OnlyAdminUsersService]},
  {path: 'account', loadChildren: () => import('./register-local-nvr-account/account.module').then(m => m.AccountModule), canActivate:[OnlyClientUsersService]},
  {path: 'setupsmtpclient', component: SetupSMTPClientComponent, canActivate: [OnlyAdminUsersService]},
  {path: 'register', component: RegisterAccountComponent, canActivate: [OnlyAnonUsersService]},
  {path: 'login', component: LoginComponent, canActivate: [OnlyAnonUsersService]},
  {path: 'forgotpassword', component: ForgottenPasswordComponent, canActivate: [OnlyAnonUsersService]},
  {path: 'resetpassword/:uniqueId', component: ResetPasswordComponent}
];

@NgModule({
  imports: [RouterModule.forRoot(routes, {useHash: true})],
  exports: [RouterModule]
})
export class AppRoutingModule { }
