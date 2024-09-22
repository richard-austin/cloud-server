import { NgModule } from '@angular/core';
import {RouterModule, Routes} from "@angular/router";
import {SetIpComponent} from "../set-ip/set-ip.component";
import {AboutComponent} from "../about/about.component";
import {GetActiveIPAddressesComponent} from "../get-active-ipaddresses/get-active-ipaddresses.component";
import {DrawdownCalcContainerComponent} from "../drawdown-calc-container/drawdown-calc-container.component";
import {
  CreateUserAccountContainerComponent
} from "../create-user-account-container/create-user-account-container.component";

const routes: Routes = [
  {path: 'setip', component: SetIpComponent},
  {path: 'cua', component: CreateUserAccountContainerComponent},
  {path: 'getactiveipaddresses', component: GetActiveIPAddressesComponent},
  {path: 'dc', component: DrawdownCalcContainerComponent},
  {path: 'about', component: AboutComponent}
];
@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class GeneralRoutingModule {
}
