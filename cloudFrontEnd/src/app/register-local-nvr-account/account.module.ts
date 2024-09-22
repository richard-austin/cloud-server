import { NgModule } from '@angular/core';
import {RegisterLocalNvrAccountComponent} from './register-local-nvr-account.component';
import {RemoveLocalNvrAccountComponent} from '../remove-local-nvr-account/remove-local-nvr-account.component';
import {AccountRoutingModule} from './account-routing.module';
import {MatCard, MatCardContent, MatCardSubtitle, MatCardTitle} from '@angular/material/card';
import {SharedModule} from '../shared/shared.module';
import {MatError, MatFormField, MatLabel} from '@angular/material/form-field';
import {ReactiveFormsModule} from '@angular/forms';
import {MatTooltip} from '@angular/material/tooltip';
import {NgIf} from '@angular/common';
import {MatInput} from '@angular/material/input';
import {MatButton} from '@angular/material/button';

@NgModule({
  declarations: [
      RegisterLocalNvrAccountComponent,
      RemoveLocalNvrAccountComponent
  ],
  imports: [
    AccountRoutingModule,
    MatCard,
    MatCardTitle,
    MatCardContent,
    SharedModule,
    MatFormField,
    ReactiveFormsModule,
    MatTooltip,
    MatLabel,
    MatError,
    NgIf,
    MatInput,
    MatButton,
    MatCardSubtitle,
  ]
})
export class AccountModule { }
