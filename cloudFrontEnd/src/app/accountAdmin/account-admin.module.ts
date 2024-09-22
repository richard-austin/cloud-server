import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import {AccountAdminComponent} from './account-admin.component';
import {MatCard, MatCardContent, MatCardFooter, MatCardTitle} from '@angular/material/card';
import {MatProgressSpinner} from '@angular/material/progress-spinner';
import {SharedModule} from '../shared/shared.module';
import {MatFormField, MatHint, MatLabel} from '@angular/material/form-field';
import {MatCheckbox} from '@angular/material/checkbox';
import {MatInput} from '@angular/material/input';
import {MatTooltip} from '@angular/material/tooltip';
import {MatSortModule} from '@angular/material/sort';
import {
  MatCell,
  MatCellDef,
  MatColumnDef,
  MatHeaderCell,
  MatHeaderCellDef,
  MatHeaderRow,
  MatHeaderRowDef,
  MatRow, MatRowDef,
  MatTable
} from '@angular/material/table';
import {MatButton, MatIconButton} from '@angular/material/button';
import {MatIcon} from '@angular/material/icon';
import {FilterPipe} from './filter.pipe';
import {SortPipe} from './sort.pipe';
import {ReactiveFormsModule} from '@angular/forms';
import {AccountAdminRoutingModule} from './account-admin-routing.module';


@NgModule({
  declarations: [
    AccountAdminComponent,
    FilterPipe,
    SortPipe

  ],
  imports: [
    AccountAdminRoutingModule,
    CommonModule,
    SharedModule,
    MatCard,
    MatCardTitle,
    MatCardContent,
    MatProgressSpinner,
    MatFormField,
    MatLabel,
    MatHint,
    MatCheckbox,
    MatInput,
    MatTooltip,
    MatSortModule,
    MatTable,
    MatHeaderCell,
    MatIconButton,
    MatColumnDef,
    MatIcon,
    MatCell,
    MatHeaderCellDef,
    MatCellDef,
    ReactiveFormsModule,
    MatButton,
    MatRow,
    MatHeaderRow,
    MatHeaderRowDef,
    MatRowDef,
    MatCardFooter,

  ]
})
export class AccountAdminModule { }
