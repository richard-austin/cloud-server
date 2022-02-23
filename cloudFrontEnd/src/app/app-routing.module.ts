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

const routes: Routes = [
  {path: 'live', component: LiveContainerComponent},
  {path: 'recording', component: RecordingControlComponent},
  {path: 'multicam', component: MultiCamViewComponent},
  {path: 'changepassword', component: ChangePasswordComponent},
  {path: 'about/:isLocal', component: AboutComponent},
  {path: 'about', component: AboutComponent},
  {path: 'setip', component: SetIpComponent},
  {path: 'cameraparams', component: CameraParamsComponent},
  {path: 'configsetup', component: ConfigSetupComponent},
  {path: 'login', component: LoginComponent},
  {path: 'register', component: RegisterAccountComponent},
  {path: 'accountadmin', component: AccountAdminComponent},
  {path: 'dc', component: DrawdownCalcContainerComponent}
];

@NgModule({
  imports: [RouterModule.forRoot(routes, {useHash: true})],
  exports: [RouterModule]
})
export class AppRoutingModule { }
