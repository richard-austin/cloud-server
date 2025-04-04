import {AfterViewInit, Component, ElementRef, OnInit, ViewChild} from '@angular/core';
import {FormControl, FormGroup, Validators} from "@angular/forms";
import {LoggedInMessage, UtilsService} from "../shared/utils.service";
import {timer} from 'rxjs';
import {SharedAngularMaterialModule} from '../shared/shared-angular-material/shared-angular-material.module';
import {SharedModule} from '../shared/shared.module';

@Component({
    selector: 'app-login',
    templateUrl: './user-login.component.html',
    styleUrls: ['./user-login.component.scss'],
    imports: [SharedModule, SharedAngularMaterialModule]
})
export class UserLoginComponent implements OnInit, AfterViewInit {
  username: string = '';
  password: string = '';
  rememberMe: boolean = false;
  loginForm!: FormGroup;
  errorMessage: string = '';
  @ViewChild('username') usernameInput!: ElementRef<HTMLInputElement>

  constructor(public utilsService: UtilsService) { }
  login()
  {
    this.errorMessage = '';
    this.username = this.getFormControl('username').value;
    this.password = this.getFormControl('password').value;

    this.utilsService.login(this.username, this.password, this.rememberMe).subscribe((result) => {
        this.getFormControl('username').setValue("");
        this.getFormControl('password').setValue("");
        this.username = this.password = "";

        if(result[0] !== undefined) {
         if (result[0].authority === 'ROLE_CLIENT' || result[0].authority === 'ROLE_ADMIN')
          this.utilsService.sendMessage(new LoggedInMessage(result[0].authority));  // Tell nav component we are logged in
        }
      },
      (reason)=> {
        this.errorMessage = reason.statusText + ": " + reason.error;
      });
  }

  getFormControl(fcName: string): FormControl {
    return this.loginForm.get(fcName) as FormControl;
  }

  anyInvalid(): boolean {
    return this.loginForm.invalid;
  }

  hideLoginForm() {
      window.location.href = "#/";
  }

  confirmOnReturn($event: InputEvent) {
      if($event.inputType == 'insertLineBreak' && !this.anyInvalid())
        this.login();
  }

  ngOnInit(): void {
    this.loginForm = new FormGroup({
      username: new FormControl(this.username, [Validators.required, Validators.maxLength(20), Validators.pattern("^[a-zA-Z0-9](_(?!(\.|_))|\.(?!(_|\.))|[a-zA-Z0-9]){3,18}[a-zA-Z0-9]$")]),
      password: new FormControl(this.password, [Validators.required, Validators.maxLength(32)])
    }, {updateOn: "change"});

    // Ensure camera form controls highlight immediately if invalid
    this.loginForm.markAllAsTouched();
  }

  ngAfterViewInit(): void {
    // Set the focus to the username input
    timer(20).subscribe(() => {
      this.usernameInput.nativeElement.focus();
    });
  }
}
