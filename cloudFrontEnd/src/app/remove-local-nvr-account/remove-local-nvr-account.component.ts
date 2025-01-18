import {Component, OnInit, ViewChild} from '@angular/core';
import {UtilsService} from "../shared/utils.service";
import {ReportingComponent} from "../reporting/reporting.component";

@Component({
  selector: 'app-remove-local-nvr',
  templateUrl: './remove-local-nvr-account.component.html',
  styleUrls: ['./remove-local-nvr-account.component.scss']
})
export class RemoveLocalNvrAccountComponent implements OnInit {

  @ViewChild(ReportingComponent) reporting!: ReportingComponent;

  constructor(public utilsService: UtilsService) {
  }

  ngOnInit(): void {
  }

  deleteAccount() {
    this.utilsService.removeLocalNVRAccount().subscribe((result) => {
        this.utilsService.getHasLocalAccount();
        this.reporting.successMessage = "Removed account " + result.username + " successfully";
      },
      reason => {
        this.reporting.errorMessage = reason;
      })
  }

  cancel() {
    this.reporting.dismiss();
    window.location.href = '#/';
  }
}
