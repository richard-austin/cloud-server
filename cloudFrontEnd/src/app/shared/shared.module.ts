import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import {ReportingComponent} from "../reporting/reporting.component";
import {MatCard, MatCardTitle} from "@angular/material/card";
import {MatTooltip} from "@angular/material/tooltip";
import {MatIcon} from "@angular/material/icon";
import {FormsModule} from "@angular/forms";
import {MatButton} from "@angular/material/button";

@NgModule({
  declarations: [
      ReportingComponent,
  ],
    imports: [
        CommonModule,
        MatCard,
        MatCardTitle,
        MatTooltip,
        MatIcon,
        FormsModule,
        MatButton,
    ],
    exports: [
        ReportingComponent,
    ]
})
export class SharedModule { }
