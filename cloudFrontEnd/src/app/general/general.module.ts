import { NgModule } from '@angular/core';
import {CommonModule, NgOptimizedImage} from '@angular/common';
import {GeneralRoutingModule} from "./general-routing.module";
import {SharedModule} from "../shared/shared.module";
import {MatCard, MatCardContent, MatCardSubtitle, MatCardTitle} from "@angular/material/card";
import {MatCheckbox} from "@angular/material/checkbox";
import {ReactiveFormsModule} from "@angular/forms";
import {MatTooltip} from "@angular/material/tooltip";
import {MatError, MatFormField, MatHint, MatLabel} from "@angular/material/form-field";
import {MatInput} from "@angular/material/input";
import {MatButton} from "@angular/material/button";
import {SetIpComponent} from "../set-ip/set-ip.component";
import {DrawdownCalcContainerComponent} from "../drawdown-calc-container/drawdown-calc-container.component";
import {GetActiveIPAddressesComponent} from "../get-active-ipaddresses/get-active-ipaddresses.component";
import {
  MatCell,
  MatCellDef,
  MatColumnDef,
  MatHeaderCell,
  MatHeaderCellDef,
  MatHeaderRow, MatHeaderRowDef, MatRow, MatRowDef,
  MatTable
} from "@angular/material/table";
import {
  CreateUserAccountContainerComponent
} from "../create-user-account-container/create-user-account-container.component";
import {MatProgressSpinner} from "@angular/material/progress-spinner";
import {AboutComponent} from '../about/about.component';

@NgModule({
  declarations: [
      SetIpComponent,
      DrawdownCalcContainerComponent,
      GetActiveIPAddressesComponent,
      CreateUserAccountContainerComponent,
      AboutComponent
  ],
  imports: [
    CommonModule,
    GeneralRoutingModule,
    SharedModule,
    MatCard,
    MatCardTitle,
    MatCardSubtitle,
    MatCardContent,
    MatCheckbox,
    ReactiveFormsModule,
    MatTooltip,
    MatFormField,
    MatLabel,
    MatHint,
    MatError,
    MatInput,
    MatButton,
    MatTable,
    MatColumnDef,
    MatHeaderCell,
    MatProgressSpinner,
    MatCell,
    MatHeaderRow,
    MatRow,
    MatRowDef,
    MatCellDef,
    MatHeaderCellDef,
    MatHeaderRowDef,
    NgOptimizedImage,
  ]
})
export class GeneralModule { }
