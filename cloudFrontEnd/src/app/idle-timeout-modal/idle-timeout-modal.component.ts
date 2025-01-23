import {Component, Inject, OnInit} from '@angular/core';
import {MAT_DIALOG_DATA, MatDialogRef} from "@angular/material/dialog";
import {UserIdleService} from "../angular-user-idle/angular-user-idle.service";
import {UtilsService} from "../shared/utils.service";

@Component({
    selector: 'app-idle-timeout-modal',
    templateUrl: './idle-timeout-modal.component.html',
    styleUrls: ['./idle-timeout-modal.component.scss'],
    standalone: false
})
export class IdleTimeoutModalComponent implements OnInit {

  constructor(public dialogRef: MatDialogRef<IdleTimeoutModalComponent>, @Inject(MAT_DIALOG_DATA) public data: {
    idle: number;
    remainingSecs: number;
  }, private userIdle:UserIdleService, private utilsService: UtilsService) {
    dialogRef.disableClose = true;
  }

  onClose(): void {
    this.dialogRef.close();
    this.utilsService.logout();
  }

  onContinue() {
    this.userIdle.resetTimer();
    this.dialogRef.close();
  }

  ngOnInit(): void {
  }

  protected readonly Math = Math;
  protected readonly Number = Number;
}
