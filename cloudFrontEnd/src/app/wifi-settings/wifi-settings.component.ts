import {Component, OnInit, ViewChild} from '@angular/core';
import {ReportingComponent} from '../reporting/reporting.component';
import {WifiUtilsService} from '../shared/wifi-utils.service';
import {CurrentWifiConnection} from '../shared/current-wifi-connection';
import {MatCheckbox, MatCheckboxChange} from '@angular/material/checkbox';
import {WifiDetails} from '../shared/BaseUrl/wifi-details';
import {OnDestroy} from '@angular/core';
import {timer} from 'rxjs';
import {WifiConnectResult} from '../shared/wifi-connect-result';
import {HttpErrorResponse} from '@angular/common/http';
import {UntypedFormControl, UntypedFormGroup, Validators} from '@angular/forms';
import { MatSelect } from '@angular/material/select';
import {SharedModule} from '../shared/shared.module';
import {SharedAngularMaterialModule} from '../shared/shared-angular-material/shared-angular-material.module';

@Component({
    selector: 'app-wifi-settings',
    templateUrl: './wifi-settings.component.html',
    styleUrls: ['./wifi-settings.component.scss'],
    imports: [SharedModule, SharedAngularMaterialModule]
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
  enterPasswordForm!: UntypedFormGroup;
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
    this.wifiUtilsService.setUpWifi(this.selector.value, this.password).subscribe((result) => {
        this.reporting.successMessage = JSON.parse(result.response)?.message;
        this.currentWifiConnection.accessPoint = this.selector.value;
        this.connecting = false;
        this.needPassword = false;
      },
      (reason) => {
        this.connecting = false;
        let err: WifiConnectResult = reason.error;
        let response: any = JSON.parse(err.message);

        if (err.errorCode === 400) {
          if (response.returncode == 4) // nmcli return code 4: "Connection activation failed.",
          {
            if(this.needPassword)
              this.reporting.warningMessage = 'Incorrect password for ' + this.selector.value + ", Please try again";
            else {
              this.reporting.warningMessage = 'Please enter the password for ' + this.selector.value;
              this.needPassword = true;
            }
          }
          else if (response.returncode == 11)
            this.reporting.warningMessage = response.message;
        } else {
          this.reporting.errorMessage = new HttpErrorResponse({error: response.message});
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

  getFormControl(formGroup: UntypedFormGroup, fcName: string): UntypedFormControl {
    return formGroup.get(fcName) as UntypedFormControl;
  }

   hasError = (controlName: string, errorName: string): boolean => {
    return this.enterPasswordForm.controls[controlName].hasError(errorName);
  }
  anyInvalid(): boolean {
    return this.enterPasswordForm.invalid && this.needPassword;
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

    this.enterPasswordForm = new UntypedFormGroup({
      password: new UntypedFormControl(this.password, [Validators.required, Validators.minLength(8), Validators.maxLength(35)]),
    }, {updateOn: 'change'});
    this.enterPasswordForm.markAllAsTouched();
  }

  ngOnDestroy(): void {
  }
}
