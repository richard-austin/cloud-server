import { NgModule } from '@angular/core';
import {RouterModule, Routes} from "@angular/router";
import {SetIpComponent} from "../set-ip/set-ip.component";
import {AboutComponent} from "../about/about.component";
import {GetActiveIPAddressesComponent} from "../get-active-ipaddresses/get-active-ipaddresses.component";
import {DrawdownCalcContainerComponent} from "../drawdown-calc-container/drawdown-calc-container.component";
import {
  CreateUserAccountContainerComponent
} from "../create-user-account-container/create-user-account-container.component";
import {OnlyClientUsersService} from '../guards/only-client-users.service';
import {OnlyLoggedInService} from '../guards/only-logged-in.service';
import {ActivemqCredentialsComponent} from '../activemq-credentials/activemq-credentials.component';
import {OnlyAdminUsersService} from '../guards/only-admin-users.service';

const routes: Routes = [
  {path: 'setip', component: SetIpComponent, canActivate: [OnlyClientUsersService]},
  {path: 'cua', component: CreateUserAccountContainerComponent, canActivate: [OnlyClientUsersService]},
  {path: 'registerActiveMQAccount', component: ActivemqCredentialsComponent, canActivate: [OnlyAdminUsersService]},
  {path: 'getactiveipaddresses', component: GetActiveIPAddressesComponent, canActivate: [OnlyClientUsersService]},
  {path: 'dc', component: DrawdownCalcContainerComponent, canActivate: [OnlyClientUsersService]},
  {path: 'about/:isLocal', component: AboutComponent, canActivate: [OnlyLoggedInService]},
  {path: 'about', component: AboutComponent, canActivate: [OnlyClientUsersService]}
];
@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class GeneralRoutingModule {
}
