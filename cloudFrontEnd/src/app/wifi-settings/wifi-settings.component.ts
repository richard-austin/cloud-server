import {Component, OnInit, ViewChild} from '@angular/core';
import {ReportingComponent} from '../reporting/reporting.component';
import {WifiUtilsService} from '../shared/wifi-utils.service';
import {CurrentWifiConnection} from '../shared/current-wifi-connection';
import {MatCheckboxChange} from '@angular/material/checkbox';
import {WifiDetails} from '../shared/BaseUrl/wifi-details';
import {OnDestroy} from '@angular/core';
import {MatSelect} from '@angular/material/select/select';
import {timer} from 'rxjs';

@Component({
  selector: 'app-wifi-settings',
  templateUrl: './wifi-settings.component.html',
  styleUrls: ['./wifi-settings.component.scss']
})
export class WifiSettingsComponent implements OnInit, OnDestroy {
  @ViewChild('selector') selector!: MatSelect;
  wifiEnabled: boolean = false;
  currentWifiConnection: CurrentWifiConnection = new CurrentWifiConnection();
  wifiList!: WifiDetails[];
  ethernetConnectionStatus!: string;
  loading: boolean = true;

  @ViewChild(ReportingComponent) reporting!: ReportingComponent;

  constructor(private wifiUtilsService: WifiUtilsService) {
  }

  showWifi() {
    this.loading = true;
    this.wifiUtilsService.getCurrentWifiConnection().subscribe((result) => {
      this.currentWifiConnection = result;
      this.loading = false;
      this.selector.value = this.currentWifiConnection.accessPoint;
    });
  }

  getLocalWifiDetails(): void {
    if (this.wifiEnabled) {
      this.wifiUtilsService.getLocalWifiDetails().subscribe((result) => {
          this.wifiList = result
            .filter(this.onlyUnique)
            .sort((a, b) => parseInt(b.signal) - parseInt(a.signal));
          this.showWifi();
        },
        reason => {
          this.reporting.errorMessage = reason;
        });
    }
  }

  setWifiStatus($event: MatCheckboxChange) {
    let status: string = $event.checked ? 'on' : 'off';
    this.loading = true;

    this.wifiUtilsService.setWifiStatus(status).subscribe((result) => {
        this.wifiEnabled = result.status === 'on';
        if (this.wifiEnabled) {

          // Allow time for the Wi-Fi connection to re-establish so iwconfig can detect it
          timer(7000).subscribe(() => {
            this.getLocalWifiDetails();
            this.loading = false;
          });
        } else {
          this.wifiList = [];
          this.loading = false;
        }
      },
      reason => {
        this.reporting.errorMessage = reason;
      });
  }

  resetConnection() {
    this.wifiUtilsService.restartCloudProxy().subscribe((result) => {
        window.location.href = '/logoff';
      },
      reason => {
        this.reporting.errorMessage = reason;
      });
  }

  onlyUnique(value: WifiDetails, index: number, self: WifiDetails[]) {
    let val: WifiDetails | undefined = self.find(a => a.ssid == value.ssid);
    if (val !== undefined && val.ssid !== '') {
      return self.indexOf(val) === index;
    }

    return false;
  }

  ngOnInit(): void {
    this.wifiUtilsService.checkConnectedThroughEthernet().subscribe((result) => {
        this.ethernetConnectionStatus = result.status;
      },
      reason => {
        this.reporting.errorMessage = reason;
      });

    this.wifiUtilsService.checkWifiStatus().subscribe((result) => {

        this.wifiEnabled = result.status === 'on';
        if(this.wifiEnabled)
          this.getLocalWifiDetails();
        else
          this.loading = false;
      },
      reason => {
        this.reporting.errorMessage = reason;
      });
  }

  ngOnDestroy(): void {
  }
}
