import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import {OnlyAnonUsersService} from './guards/only-anon-users.service';
import {OnlyClientUsersService} from './guards/only-client-users.service';
import {OnlyAdminUsersService} from './guards/only-admin-users.service';
import {OnlyLoggedInService} from './guards/only-logged-in.service';
import {canDeactivateGuard} from './guards/can-deactivate.guard';

const routes: Routes = [
  {path: 'live/:streamName', loadComponent: () => import('./live-container/live-container.component').then(m => m.LiveContainerComponent)},
  {path: 'recording/:streamName', loadComponent: () => import('./recording-control/recording-control.component').then(m => m.RecordingControlComponent)},
  {path: 'multicam', loadComponent: () => import('./multi-cam-view/multi-cam-view.component').then(m => m.MultiCamViewComponent)},
  {path: 'changeemail', loadComponent: () => import('./change-email/change-email.component').then(m => m.ChangeEmailComponent)},
  {path: 'changepassword', loadComponent: () => import('./change-password/change-password.component').then(m => m.ChangePasswordComponent)},
  {path: 'cameraparams/:camera', loadComponent: () => import('./camera-params/camera-params.component').then(m => m.CameraParamsComponent)},
  {path: 'configsetup', canDeactivate: [canDeactivateGuard], loadComponent: () => import('./config-setup/config-setup.component').then(m => m.ConfigSetupComponent)},
  {path: 'setip', loadComponent: () => import('./set-ip/set-ip.component').then(m => m.SetIpComponent), canActivate: [OnlyClientUsersService]},
  {path: 'cua', loadComponent: () => import('./create-user-account-container/create-user-account-container.component').then(m => m.CreateUserAccountContainerComponent), canActivate: [OnlyClientUsersService]},
  {path: 'registerActiveMQAccount', loadComponent: () => import('./activemq-credentials/activemq-credentials.component').then(m=>m.ActivemqCredentialsComponent), canActivate: [OnlyAdminUsersService]},
  {path: 'getactiveipaddresses', loadComponent: () => import('./get-active-ipaddresses/get-active-ipaddresses.component').then(m => m.GetActiveIPAddressesComponent), canActivate: [OnlyClientUsersService]},
  {path: 'dc', loadComponent: () => import('./drawdown-calc-container/drawdown-calc-container.component').then(m => m.DrawdownCalcContainerComponent), canActivate: [OnlyClientUsersService]},
  {path: 'about/:isLocal', loadComponent: () => import('./about/about.component').then(m => m.AboutComponent), canActivate: [OnlyLoggedInService]},
  {path: 'about', loadComponent: () => import('./about/about.component').then(m => m.AboutComponent), canActivate: [OnlyClientUsersService]},
  {path: 'wifisettings', loadComponent: () => import('./wifi-settings/wifi-settings.component').then(m => m.WifiSettingsComponent)},
  {path: 'getlocalwifidetails', loadComponent: () => import('./get-local-wifi-details/get-local-wifi-details.component').then(m => m.GetLocalWifiDetailsComponent)},
  {path: 'accountadmin', loadComponent: () => import('./accountAdmin/account-admin.component').then(m => m.AccountAdminComponent), canActivate:[OnlyAdminUsersService]},
  {path: 'registerlocalnvraccount', loadComponent: () => import('./register-local-nvr-account/register-local-nvr-account.component').then(m => m.RegisterLocalNvrAccountComponent)},
  {path: 'removelocalnvraccount', loadComponent: () => import('./remove-local-nvr-account/remove-local-nvr-account.component').then(m => m.RemoveLocalNvrAccountComponent), canActivate: [OnlyClientUsersService]},
  {path: 'setupsmtpclient', loadComponent:() => import('./setup-smtpclient/setup-smtpclient.component').then(m => m.SetupSMTPClientComponent), canActivate: [OnlyAdminUsersService]},
  {path: 'register', loadComponent: () => import('./register-account/register-account.component').then(m => m.RegisterAccountComponent), canActivate: [OnlyAnonUsersService]},
  {path: 'forgotpassword', loadComponent: () => import('./user-login/forgotten-password/forgotten-password.component').then(m => m.ForgottenPasswordComponent), canActivate: [OnlyAnonUsersService]},
  {path: 'resetpassword/:uniqueId', loadComponent: () => import('./reset-password/reset-password.component').then(m => m.ResetPasswordComponent), canActivate: [OnlyAnonUsersService]},
  {path: 'login', loadComponent: () => import('./user-login/user-login.component').then(m => m.UserLoginComponent), canActivate: [OnlyAnonUsersService]}
];

@NgModule({
  imports: [RouterModule.forRoot(routes, {useHash: true})],
  exports: [RouterModule]
})
export class AppRoutingModule { }
