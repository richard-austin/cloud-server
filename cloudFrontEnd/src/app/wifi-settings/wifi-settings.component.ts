import {Component, OnInit, ViewChild} from '@angular/core';
import {ReportingComponent} from '../reporting/reporting.component';
import {WifiUtilsService} from '../shared/wifi-utils.service';
import {CurrentWifiConnection} from '../shared/current-wifi-connection';
import {MatCheckboxChange} from '@angular/material/checkbox';

@Component({
  selector: 'app-wifi-settings',
  templateUrl: './wifi-settings.component.html',
  styleUrls: ['./wifi-settings.component.scss']
})
export class WifiSettingsComponent implements OnInit {
  wifiEnabled: boolean = false;
  currentWifiConnection!: CurrentWifiConnection;

  @ViewChild(ReportingComponent) reporting!: ReportingComponent;

  constructor(private wifiUtilsService: WifiUtilsService) {
  }

  showWifi() {
    this.wifiUtilsService.getCurrentWifiConnection().subscribe((result) => {
      this.currentWifiConnection = result;
    });
  }

  setWifiStatus($event: MatCheckboxChange) {
    let status: string = $event.checked ? 'on' : 'off';

    this.wifiUtilsService.setWifiStatus(status).subscribe((result) => {
        this.wifiEnabled = result.status === 'on';
      },
      reason => {
        this.reporting.errorMessage = reason;
      });
  }

  ngOnInit(): void {
    this.wifiUtilsService.checkWifiStatus().subscribe((result) => {
        this.showWifi();
        this.wifiEnabled = result.status === 'on';
      },
      reason => {
        this.reporting.errorMessage = reason;
      });
  }

}
