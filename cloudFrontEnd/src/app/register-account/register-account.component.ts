import {AfterViewInit, Component, ElementRef, OnInit, ViewChild} from '@angular/core';
import {AbstractControl, FormControl, FormGroup, ValidationErrors, ValidatorFn, Validators} from "@angular/forms";

@Component({
  selector: 'app-register-account',
  templateUrl: './register-account.component.html',
  styleUrls: ['./register-account.component.scss']
})
export class RegisterAccountComponent implements OnInit, AfterViewInit {
  username: string = '';
  private productId: String = '';
  password: string = '';
  confirmPassword: string = '';
  accountRegistrationForm!: FormGroup;
  errorMessage: string = '';
  @ViewChild('username') usernameInput!: ElementRef<HTMLInputElement>

  constructor() { }

  passwordValidator(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
     this.password = control.value;
      // Update the validation status of the confirmPassword field
      if(this.confirmPassword !== "")
      {
        let cpControl:AbstractControl | null = this.accountRegistrationForm.get("confirmPassword");
        cpControl?.updateValueAndValidity();
      }

      const ok = !new RegExp("^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,}$").test(control.value);
      return ok ? {pattern: {value: control.value}} : null;
    };
  }

  passwordMatchValidator(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      this.confirmPassword = control.value;
      const ok = this.password !== control.value;
      return ok ? {notMatching: {value: control.value}} : null;
    };
  }

  confirmOnReturn($event: KeyboardEvent) {
    // Ensure password field is up-to-date for the confirmPassword validity check
    this.password = this.getFormControl('password').value;

    if($event.key == 'Enter')
      this.register();
  }

  register() {
    // this.username = this.getFormControl('username').value;
    // this.password = this.getFormControl('password').value;

    let x = this.password;
    let y = this.confirmPassword;
    let q = y;

  }

  getFormControl(fcName: string): FormControl {
    return this.accountRegistrationForm.get(fcName) as FormControl;
  }

  anyInvalid(): boolean {
    return this.accountRegistrationForm.invalid;
  }

  hideRegisterForm() {
    window.location.href = "#/";
  }

  ngOnInit(): void {
    this.accountRegistrationForm = new FormGroup({
      username: new FormControl(this.username, [Validators.required, Validators.maxLength(20), Validators.pattern("^[a-zA-Z0-9](_(?!(\.|_))|\.(?!(_|\.))|[a-zA-Z0-9]){3,18}[a-zA-Z0-9]$")]),
      productId: new FormControl(this.productId, [Validators.required, Validators.pattern(/^(?:[A-Z0-9]{4}-){3}[A-Z0-9]{4}/)]),
      password: new FormControl(this.password, [Validators.required, Validators.maxLength(25), this.passwordValidator()]),
      confirmPassword: new FormControl(this.confirmPassword, [Validators.required, Validators.maxLength(25), this.passwordMatchValidator()])
    }, {updateOn: "change"});

    // Ensure camera form controls highlight immediately if invalid
    this.accountRegistrationForm.markAllAsTouched();
  }

  ngAfterViewInit(): void {
    // Set the focus to the username input
    this.usernameInput.nativeElement.focus();
  }
}
