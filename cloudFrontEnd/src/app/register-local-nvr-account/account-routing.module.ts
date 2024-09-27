import { NgModule } from '@angular/core';
import {RouterModule, Routes} from '@angular/router';
import {RemoveLocalNvrAccountComponent} from '../remove-local-nvr-account/remove-local-nvr-account.component';
import {OnlyClientUsersService} from '../guards/only-client-users.service';
import {RegisterLocalNvrAccountComponent} from './register-local-nvr-account.component';

const routes: Routes = [
  {path: 'registerlocalnvraccount', component: RegisterLocalNvrAccountComponent},
  {path: 'removelocalnvraccount', component: RemoveLocalNvrAccountComponent, canActivate: [OnlyClientUsersService]}
]

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
  declarations: [],
 })
export class AccountRoutingModule { }
