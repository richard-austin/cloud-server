import {Component, OnInit, ViewChild} from '@angular/core';
import { ReportingComponent } from '../reporting/reporting.component';
import {WifiUtilsService} from '../shared/wifi-utils.service';

@Component({
  selector: 'app-wifi-settings',
  templateUrl: './wifi-settings.component.html',
  styleUrls: ['./wifi-settings.component.scss']
})
export class WifiSettingsComponent implements OnInit {
  wifiEnabled: boolean = false;
  @ViewChild(ReportingComponent) reporting!: ReportingComponent;
  constructor(private wifiUtilsService: WifiUtilsService) { }

  showWifi()
  {

  }

  ngOnInit(): void {
    this.wifiUtilsService.checkWifiStatus().subscribe((result) => {
      this.wifiEnabled = result.replace('\n', '') === 'enabled';
    },
      reason => {
          this.reporting.errorMessage = reason;
      })
  }
}
