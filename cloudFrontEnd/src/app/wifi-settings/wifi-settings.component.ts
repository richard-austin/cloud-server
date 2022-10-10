import {Component, OnInit, ViewChild} from '@angular/core';
import {ReportingComponent} from '../reporting/reporting.component';
import {WifiUtilsService} from '../shared/wifi-utils.service';
import {CurrentWifiConnection} from '../shared/current-wifi-connection';
import {MatCheckbox, MatCheckboxChange} from '@angular/material/checkbox';
import {WifiDetails} from '../shared/BaseUrl/wifi-details';
import {OnDestroy} from '@angular/core';
import {MatSelect} from '@angular/material/select/select';
import {timer} from 'rxjs';
import {WifiConnectResult} from '../shared/wifi-connect-result';
import {HttpErrorResponse} from '@angular/common/http';
import {FormControl, FormGroup, Validators} from '@angular/forms';

@Component({
  selector: 'app-wifi-settings',
  templateUrl: './wifi-settings.component.html',
  styleUrls: ['./wifi-settings.component.scss']
})
export class WifiSettingsComponent implements OnInit, OnDestroy {
  @ViewChild('selector') selector!: MatSelect;
  @ViewChild('wifiStatusCheckbox') wifiStatusCheckbox!: MatCheckbox;
  wifiEnabled: boolean = false;
  currentWifiConnection: CurrentWifiConnection = new CurrentWifiConnection();
  wifiList!: WifiDetails[];
  ethernetConnectionStatus: string = '';
  loading: boolean = true;
  needPassword: boolean = false;
  connecting: boolean = false;

  @ViewChild(ReportingComponent) reporting!: ReportingComponent;
  enterPasswordForm!: FormGroup;
  private password: string | undefined;
  resetting: boolean = false;

  constructor(private wifiUtilsService: WifiUtilsService) {
  }

  showWifi() {
    this.loading = true;
    this.wifiUtilsService.getCurrentWifiConnection().subscribe((result) => {
        this.currentWifiConnection = result;
        this.loading = false;
        this.selector.value = this.currentWifiConnection.accessPoint;
      },
      reason => {
        this.loading = false;
        this.reporting.errorMessage = reason;
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
    if (this.ethernetConnectionStatus === 'CONNECTED_VIA_ETHERNET') {
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
          this.wifiStatusCheckbox.checked = true;
          this.loading = false;
          this.reporting.errorMessage = reason;
        });
    } else {
      this.wifiStatusCheckbox.checked = true;
      this.loading = false;
    }
  }

  resetConnection() {
    this.resetting = true;
    this.wifiUtilsService.restartCloudProxy().subscribe(() => {
        timer(7000).subscribe(() => {
            this.ngOnInit();
            this.resetting = false;
          }
        );
      },
      reason => {
        this.resetting = false;
        this.reporting.errorMessage = reason;
      });
  }


  connect() {
    if (this.needPassword) {
      this.password = this.getFormControl(this.enterPasswordForm, 'password').value;
    } else {
      this.password = undefined;
    }
    this.connecting = true;
    this.needPassword = false;
    this.wifiUtilsService.setUpWifi(this.selector.value, this.password).subscribe((result) => {
        this.reporting.successMessage = result.response;
        this.currentWifiConnection.accessPoint = this.selector.value;
        this.connecting = false;
      },
      (reason) => {
        this.connecting = false;
        let err: WifiConnectResult = reason.error;
        if (err.errorCode === 7) {
          this.needPassword = true;
          this.reporting.warningMessage = 'Please enter the password for ' + this.selector.value;
        } else if (err.errorCode == 11) {
          this.reporting.warningMessage = err.message;
        } else {
          this.reporting.errorMessage = new HttpErrorResponse({error: err.message});
        }
      });
  }

  onSelectorChange() {
    this.needPassword = false;
    this.reporting.dismiss();
  }

  cancelPasswordEntry() {
    this.needPassword = false;
    this.selector.value = this.currentWifiConnection.accessPoint;
    this.reporting.dismiss();
  }

  /**
   * onlyUnique: Show only one instance of each Wi-Fi access point name on the selector (maybe one for 2.4/5.0GHz etc)
   * @param value
   * @param index
   * @param self
   */
  onlyUnique(value: WifiDetails, index: number, self: WifiDetails[]) {
    let val: WifiDetails | undefined = self.find(a => a.ssid == value.ssid);
    if (val !== undefined && val.ssid !== '') {
      return self.indexOf(val) === index;
    }

    return false;
  }

  getFormControl(formGroup: FormGroup, fcName: string): FormControl {
    return formGroup.get(fcName) as FormControl;
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
        if (this.wifiEnabled) {
          this.getLocalWifiDetails();
        } else {
          this.loading = false;
        }
      },
      reason => {
        this.reporting.errorMessage = reason;
      });

    this.enterPasswordForm = new FormGroup({
      password: new FormControl(this.password, [Validators.required, Validators.maxLength(35)]),
    }, {updateOn: 'change'});
  }

  ngOnDestroy(): void {
  }
}
