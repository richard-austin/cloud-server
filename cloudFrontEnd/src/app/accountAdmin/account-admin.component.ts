import {ViewChild} from '@angular/core';
import {ElementRef} from '@angular/core';
import {Component, OnInit} from '@angular/core';
import {MatCheckboxChange} from '@angular/material/checkbox';
import {UtilsService, Account} from "../shared/utils.service";
import {animate, state, style, transition, trigger} from "@angular/animations";
import {Camera} from "../cameras/Camera";
import {AbstractControl, FormControl, FormGroup, ValidationErrors, ValidatorFn, Validators} from "@angular/forms";

declare let SockJS: any;
declare let Stomp: any;

@Component({
  selector: 'app-nvradmin',
  templateUrl: './account-admin.component.html',
  styleUrls: ['./account-admin.component.scss'],
  animations: [
    trigger('detailExpand', [
      state('collapsed', style({height: '0px', minHeight: '0'})),
      state('expanded', style({height: '*'})),
      transition('expanded <=> collapsed', animate('225ms cubic-bezier(0.4, 0.0, 0.2, 1)')),
    ]),
    trigger('openClose', [
      // ...
      state('open', style({
        transform: 'rotate(90deg)'
      })),
      state('closed', style({
        transform: 'rotate(0deg)'
      })),
      transition('open => closed', [
        animate('.2s')
      ]),
      transition('closed => open', [
        animate('.2s')
      ]),
    ])
  ],
})
export class AccountAdminComponent implements OnInit {
  downloading: boolean = false;
  accounts: Account[] = [];
  displayedColumns: string[] = ['changePassword', 'disableAccount', 'productId', 'accountCreated', 'userName', 'nvrConnected', 'usersConnected'];
  changePasswordColumns: string[] = ['password'];
  @ViewChild('filter') filterEl!: ElementRef<HTMLInputElement>
  private stompClient: any;
  filterText: string = "";
  bOnlyNVROffline: boolean = false;
  bNoAccountOnly: boolean = false;
  errorMessage: string = '';
  successMessage: string = '';
  expandedElement: Account | null | undefined = undefined;
  changePasswordForm!: FormGroup;
  private password: string = "";
  private confirmPassword: string = "";

  constructor(private utilsService: UtilsService) {
    this.initializeWebSocketConnection();
  }

  private passwordValidator(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      this.password = control.value;
      // Update the validation status of the confirmPassword field
      if(this.confirmPassword !== "")
      {
        let cpControl:AbstractControl | null = this.changePasswordForm.get("confirmPassword");
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

  initializeWebSocketConnection() {
    let serverUrl: string = window.location.origin + "/stomp";

    let ws = new SockJS(serverUrl);
    this.stompClient = Stomp.over(ws);
    this.stompClient.debug = null;
    let that = this;
    this.stompClient.connect({}, () => {
      that.stompClient.subscribe("/topic/accountUpdates", (message: any) => {
        if (message.body) {
          let msgObj = JSON.parse(message.body);
          if (msgObj.message === "update") {
            that.getAccounts();
            console.log(message.body);
          }
        }
      });
    });
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
    this.stompClient.send("/topic/accountUpdates", {}, message);
    // $('#input').val('');
  }

  updateFilter() {
    this.filterText = this.filterEl.nativeElement.value;
  }

  changePassword(account: Account) {
    this.password = this.confirmPassword = "";
    let pw: AbstractControl = this.getFormControl(this.changePasswordForm, 'password');
    let cpw: AbstractControl = this.getFormControl(this.changePasswordForm, 'confirmPassword');
    pw.setValue("");
    cpw.setValue("");
    if(this.expandedElement === undefined)
      this.expandedElement = account;
    else
      this.expandedElement = undefined;
  }

  setAccountEnabledStatus(account: Account, $event: MatCheckboxChange) {
    account.accountEnabled = $event.checked;
    this.utilsService.setAccountEnabledStatus(account).subscribe(() => {
        this.successMessage = "Account " + account.userName + " now " + (account.accountEnabled ? "enabled" : "disabled");
      },
      reason => {
        account.accountEnabled = !$event.checked;  // Roll back local copy if it failed.
        this.errorMessage = reason.error;
      })
  }

  onlyNVROffline($event: MatCheckboxChange) {
    this.bOnlyNVROffline = $event.checked
  }

  noAccountOnly($event: MatCheckboxChange) {
    this.bNoAccountOnly = $event.checked
  }

  getFormControl(formGroup:FormGroup, fcName: string): FormControl {
    return formGroup.get(fcName) as FormControl;
  }

  anyInvalid():boolean {
    return false;
  }

  ngOnInit(): void {
    this.getAccounts();

    this.changePasswordForm = new FormGroup({
      password: new FormControl(this.password, [Validators.required, Validators.maxLength(25), this.passwordValidator()]),
      confirmPassword: new FormControl(this.confirmPassword, [Validators.required, Validators.maxLength(25), this.passwordMatchValidator()]),
    }, {updateOn: "change"});

    this.changePasswordForm.markAllAsTouched();
  }

}
