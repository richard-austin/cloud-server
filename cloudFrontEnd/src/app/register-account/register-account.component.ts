import {AfterViewInit, Component, ElementRef, OnInit, ViewChild} from '@angular/core';
import {AbstractControl, FormControl, FormGroup, ValidationErrors, ValidatorFn, Validators} from "@angular/forms";
import {UtilsService} from "../shared/utils.service";

@Component({
  selector: 'app-register-account',
  templateUrl: './register-account.component.html',
  styleUrls: ['./register-account.component.scss']
})
export class RegisterAccountComponent implements OnInit, AfterViewInit {
  username: string = '';
  private productId: string = '';
  password: string = '';
  confirmPassword: string = '';
  email: string = '';
  confirmEmail: string = '';
  accountRegistrationForm!: FormGroup;
  errorMessage: string = '';
  @ViewChild('username') usernameInput!: ElementRef<HTMLInputElement>

  constructor(private utilsService: UtilsService) { }

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

  emailValidator(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      this.email = control.value;
      // Update the validation status of the confirmPassword field
      if(this.confirmPassword !== "")
      {
        let cpControl:AbstractControl | null = this.accountRegistrationForm.get("confirmEmail");
        cpControl?.updateValueAndValidity();
      }

      const ok = !new RegExp("^([a-zA-Z0-9_\\-\\.]+)@((\\[[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.)|(([a-zA-Z0-9\\-]+\\.)+))([a-zA-Z]{2,4}|[0-9]{1,3})(\\]?)$").test(control.value);
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

  confirmOnReturn($event: KeyboardEvent) {
    // Ensure password field is up-to-date for the confirmPassword validity check
    this.password = this.getFormControl('password').value;

    if($event.key == 'Enter')
      this.register();
  }

  register() {
    this.username = this.getFormControl('username').value;
    this.productId = this.getFormControl('productId').value;

    this.utilsService.register(this.username, this.productId, this.password, this.confirmPassword, this.email, this.confirmEmail).subscribe(() => {

    },
      () => {

      });

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
      confirmPassword: new FormControl(this.confirmPassword, [Validators.required, Validators.maxLength(25), this.passwordMatchValidator()]),
      email: new FormControl(this.email, [Validators.required, Validators.maxLength(40), this.emailValidator()]),
      confirmEmail: new FormControl(this.confirmEmail, [Validators.required, Validators.maxLength(40), this.emailMatchValidator()])
    }, {updateOn: "change"});

    // Ensure camera form controls highlight immediately if invalid
    this.accountRegistrationForm.markAllAsTouched();
  }

  ngAfterViewInit(): void {
    // Set the focus to the username input
    this.usernameInput.nativeElement.focus();
  }
}
