import {signal, ViewChild} from '@angular/core';
import {ElementRef} from '@angular/core';
import {Component, OnInit} from '@angular/core';
import {MatCheckboxChange} from '@angular/material/checkbox';
import {UtilsService, Account} from '../shared/utils.service';
import {AbstractControl, FormControl, FormGroup, ValidationErrors, ValidatorFn, Validators} from '@angular/forms';
import {ReportingComponent} from '../reporting/reporting.component';
import {MatSort, Sort} from '@angular/material/sort';
import {Client, StompSubscription} from '@stomp/stompjs';
import {SharedModule} from '../shared/shared.module';
import {SharedAngularMaterialModule} from '../shared/shared-angular-material/shared-angular-material.module';
import {FilterPipe} from './filter.pipe';
import {SortPipe} from './sort.pipe';

@Component({
  selector: 'app-nvradmin',
  templateUrl: './account-admin.component.html',
  styleUrls: ['./account-admin.component.scss'],
  imports: [SharedModule, SharedAngularMaterialModule, MatSort, FilterPipe, SortPipe]
})
export class AccountAdminComponent implements OnInit {
  downloading: boolean = false;
  accounts: Account[] = [];
  displayedColumns: string[] = ['changePassword', 'changeEmail', 'disableAccount', 'deleteAccount', 'productId', 'accountCreated', 'userName', 'nvrConnected', 'usersConnected'];
  changePasswordColumns: string[] = ['password'];
  @ViewChild('filter') filterEl!: ElementRef<HTMLInputElement>;
  @ViewChild(ReportingComponent) errorReporting!: ReportingComponent;

  private stompClient: any;
  filterText: string = '';
  bOnlyNVROffline: boolean = false;
  bNoAccountOnly: boolean = false;
  errorMessage: string = '';
  successMessage: string = '';
  expandedElement: Account | null | undefined = undefined;
  changePasswordForm!: FormGroup;
  changeEmailForm!: FormGroup;
  private password: string = '';
  private confirmPassword: string = '';
  private email: string = '';
  private confirmEmail: string = '';
  showChangePassword: boolean = false;
  showChangeEmail: boolean = false;
  showConfirmDeleteAccount: boolean = false;
  sortActive: string = 'productId';
  sortDirection: string = 'desc';
  client!: Client;
  accountUpdatesSubscription!: StompSubscription;

  constructor(private utilsService: UtilsService) {
    this.initializeWebSocketConnection();
  }

  animationEnter = signal('enter-animation');
  animationLeave = signal('leaving-animation');

  private passwordValidator(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      this.password = control.value;
      // Update the validation status of the confirmPassword field
      if (this.confirmPassword !== '') {
        let cpControl: AbstractControl | null = this.changePasswordForm.get('confirmPassword');
        cpControl?.updateValueAndValidity();
      }

      const ok = !this.utilsService.passwordRegex.test(control.value);
      return ok ? {pattern: {value: control.value}} : null;
    };
  }

  private passwordMatchValidator(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      this.confirmPassword = control.value;
      const ok = this.password !== control.value;
      return ok ? {notMatching: {value: control.value}} : null;
    };
  }

  emailValidator(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      this.email = control.value;
      // Update the validation status of the confirmPassword field
      if (this.confirmEmail !== '') {
        let cpControl: AbstractControl | null = this.changeEmailForm.get('confirmEmail');
        cpControl?.updateValueAndValidity();
      }

      const ok = !new RegExp('^([a-zA-Z0-9_\\-\\.]+)@((\\[[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.)|(([a-zA-Z0-9\\-]+\\.)+))([a-zA-Z]{2,4}|[0-9]{1,3})(\\]?)$').test(control.value);
      return ok ? {pattern: {value: control.value}} : null;
    };
  }

  emailMatchValidator(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      this.confirmEmail = control.value;
      const ok = this.email !== control.value;
      return ok ? {notMatching: {value: control.value}} : null;
    };
  }

  initializeWebSocketConnection() {
    let serverUrl: string = (window.location.protocol == 'http:' ? 'ws://' : 'wss://') + window.location.host + '/cloudstomp';

    this.client = new Client({
      brokerURL: serverUrl,
      reconnectDelay: 2000,
      heartbeatOutgoing: 120000,
      heartbeatIncoming: 120000,
      onConnect: () => {
        this.accountUpdatesSubscription = this.client.subscribe('/topic/accountUpdates', (message: any) => {
          if (message.body) {
            let msgObj = JSON.parse(message.body);
            if (msgObj.message === 'update') {
              let slice = this.accounts.slice();

              const idx = slice.findIndex((acc: Account) => acc.productId === msgObj.productId);
              switch (msgObj.field) {
                case 'usersConnected':
                  if (idx != -1) {
                    slice[idx].usersConnected = msgObj.value;
                  }
                  break;
                case 'addUser':
                  if (idx != -1) { // Row already present
                    const row = slice[idx];
                    row.userName = msgObj.value;
                    row.email = msgObj.value2;
                    row.accountCreated = row.accountEnabled = true;
                  } else { // Row not preset (NVR not connected), must be added (This would never normally be used as it's not possible
                    // to add a user with the NVR offline
                    const row = new Account();
                    row.userName = msgObj.value;
                    row.email = msgObj.value2;
                    row.accountCreated = row.accountEnabled = true;
                    row.nvrConnected = true;
                    row.usersConnected = 0;
                    row.productId = msgObj.productId;
                    slice.push(row);
                  }
                  break;
                case 'removeUser':
                  if (idx != -1) {
                    const row = slice[idx];
                    if (row.nvrConnected) { // Don't remove the row if the NVR is connected, just clear the user and email
                      row.userName = row.email = '';
                      row.accountCreated = row.accountEnabled = false;
                    } else { // NVR not connected, so nothing to show on the row, we remove it
                      slice.splice(idx, 1);
                    }
                  }
                  break;
                case 'setAccountEnabledStatus':
                  if (idx != -1) {
                    const row = slice[idx];
                    row.accountEnabled = msgObj.value;
                  }
                  break;
                case 'changeEmail':
                  if (idx != -1) {
                    const row = slice[idx];
                    row.email = msgObj.value;
                    //this.ngOnInit();
                  }
                  break;
                case 'putCloudMQ':
                  if (idx != -1) {
                    const row = slice[idx];
                    row.nvrConnected = true;
                    slice[idx] = row;
                  } else {
                    let row = new Account();
                    row.nvrConnected = true;
                    row.accountCreated = false;
                    row.usersConnected = 0;
                    row.productId = msgObj.productId;
                    slice.push(row);
                  }
                  break;
                case 'removeCloudMQ':
                  if (idx != -1) {
                    const row = slice[idx];
                    if (row.accountCreated) {
                      row.nvrConnected = false;
                    } else {
                      slice.splice(idx, 1);
                    }
                  }
                  break;
              }
              this.accounts = slice;

              // this.getAccounts();
              this.expandedElement = undefined;
              console.log(message.body);
            }
          }
        });
      },
      debug: () => {
      }
    });
    this.client.activate();
  }

  getAccounts(): void {
    this.utilsService.getAccounts().subscribe((result) => {
        this.accounts = result;
      },
      reason => {
        this.errorMessage = reason.error;
      });
  }

  sendMessage(message: any) {
    this.stompClient.send('/topic/accountUpdates', {}, message);
    // $('#input').val('');
  }

  updateFilter() {
    this.filterText = this.filterEl.nativeElement.value;
  }

  showChangePasswordForm(account: Account) {
    this.showChangePassword = true;
    this.showChangeEmail = this.showConfirmDeleteAccount = false;

    this.password = this.confirmPassword = '';
    let pw: AbstractControl = this.getFormControl(this.changePasswordForm, 'password');
    let cpw: AbstractControl = this.getFormControl(this.changePasswordForm, 'confirmPassword');
    pw.setValue('');
    cpw.setValue('');
    if (this.expandedElement === undefined) {
      this.expandedElement = account;
    } else {
      this.expandedElement = undefined;
    }
  }

  showChangeEmailForm(account: Account) {
    this.showChangeEmail = true;
    this.showChangePassword = this.showConfirmDeleteAccount = false;

    this.email = account.email;

    let em: AbstractControl = this.getFormControl(this.changeEmailForm, 'email');
    let cem: AbstractControl = this.getFormControl(this.changeEmailForm, 'confirmEmail');
    em.setValue(account.email);
    cem.setValue('');

    this.email = this.confirmEmail = '';
    if (this.expandedElement === undefined) {
      this.expandedElement = account;
    } else {
      this.expandedElement = undefined;
    }
  }

  changePassword(account: Account): void {
    this.successMessage = this.errorMessage = '';
    this.utilsService.adminChangePassword(account, this.password, this.confirmPassword).subscribe(() => {
      this.successMessage = 'Password successfully updated';
      this.getAccounts();
    }, reason => {
      this.errorReporting.errorMessage = reason;
    });
  }


  changeEmail(account: Account) {
    this.successMessage = this.errorMessage = '';
    this.utilsService.adminChangeEmail(account, this.email, this.confirmEmail).subscribe(() => {
      this.successMessage = 'Email address successfully updated';
      // Update the local copy
      let local: Account | undefined = this.accounts.find(acc => acc.userName === account.userName);
      if (local !== undefined) {
        local.email = this.email;
      }
      this.getAccounts();
    }, reason => {
      this.errorReporting.errorMessage = reason;
    });
  }

  setAccountEnabledStatus(account: Account, $event: MatCheckboxChange) {
    account.accountEnabled = $event.checked;
    this.utilsService.setAccountEnabledStatus(account).subscribe(() => {
        this.successMessage = 'Account ' + account.userName + ' now ' + (account.accountEnabled ? 'enabled' : 'disabled');
      },
      reason => {
        account.accountEnabled = !$event.checked;  // Roll back local copy if it failed.
        this.errorReporting.errorMessage = reason;
      });
  }


  confirmDelete(account: Account) {
    this.showConfirmDeleteAccount = true;
    this.showChangePassword = this.showChangeEmail = false;

    if (this.expandedElement === undefined) {
      this.expandedElement = account;
    } else {
      this.expandedElement = undefined;
    }
  }

  deleteAccount(acc: Account) {
    this.utilsService.deleteAccount(acc).subscribe(() => {
        this.successMessage = 'Account ' + acc.userName + ' has been deleted';
      },
      reason => {
        this.errorReporting.errorMessage = reason;
      });
  }

  onlyNVROffline($event: MatCheckboxChange) {
    this.bOnlyNVROffline = $event.checked;
  }

  noAccountOnly($event: MatCheckboxChange) {
    this.bNoAccountOnly = $event.checked;
  }

  getFormControl(formGroup: FormGroup, fcName: string): FormControl {
    return formGroup.get(fcName) as FormControl;
  }

  anyInvalid(): boolean {
    return this.changePasswordForm.invalid;
  }

  anyInvalidEmail(): boolean {
    return this.changeEmailForm.invalid;
  }

  changeSorting(sort: Sort) {
    this.sortActive = sort.active;
    this.sortDirection = sort.direction;

    // Ensure that change email etc. can be opened on first click after sort re order.
    this.expandedElement = undefined;
    this.showConfirmDeleteAccount = this.showChangePassword = this.showChangeEmail = false;
  }

  ngOnInit(): void {
    this.getAccounts();

    this.changePasswordForm = new FormGroup({
      password: new FormControl(this.password, [Validators.required, Validators.maxLength(25), this.passwordValidator()]),
      confirmPassword: new FormControl(this.confirmPassword, [Validators.required, Validators.maxLength(25), this.passwordMatchValidator()]),
    }, {updateOn: 'change'});

    this.changeEmailForm = new FormGroup({
      email: new FormControl(this.email, [Validators.required, Validators.maxLength(70), this.emailValidator(),]),
      confirmEmail: new FormControl(this.confirmEmail, [Validators.required, Validators.maxLength(70), this.emailMatchValidator()])
    }, {updateOn: "change"});

    this.changePasswordForm.markAllAsTouched();
    this.changeEmailForm.markAllAsTouched();
  }

  protected readonly UtilsService = UtilsService;
}
