import { NgModule } from '@angular/core';

import {RouterModule, Routes} from '@angular/router';
import {AccountAdminComponent} from './account-admin.component';
import {OnlyAdminUsersService} from '../guards/only-admin-users.service';

const routes: Routes = [
  {path: '', component: AccountAdminComponent, canActivate: [OnlyAdminUsersService]},
]
@NgModule({
  declarations: [],
  imports: [RouterModule.forChild(routes)],
    exports: [RouterModule]
})
export class AccountAdminRoutingModule { }
