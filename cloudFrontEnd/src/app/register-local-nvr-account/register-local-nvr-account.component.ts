import {AfterViewInit, Component, ElementRef, OnInit, ViewChild} from '@angular/core';
import {
  AbstractControl,
  UntypedFormControl,
  UntypedFormGroup,
  ValidationErrors,
  ValidatorFn,
  Validators
} from '@angular/forms';
import {UtilsService} from "../shared/utils.service";
import {ReportingComponent} from "../reporting/reporting.component";
import {SharedModule} from '../shared/shared.module';
import {SharedAngularMaterialModule} from '../shared/shared-angular-material/shared-angular-material.module';

@Component({
    selector: 'app-register-local-nvr-account',
    templateUrl: './register-local-nvr-account.component.html',
    styleUrls: ['./register-local-nvr-account.component.scss'],
    imports: [SharedModule, SharedAngularMaterialModule]
})
export class RegisterLocalNvrAccountComponent implements OnInit, AfterViewInit {
  username: string = '';
  password: string = '';
  confirmPassword: string = '';
  email: string = '';
  confirmEmail: string = '';
  nvrAccountRegistrationForm!: UntypedFormGroup;
  success: boolean = true;
  // errorMessage: string = '';
  // successMessage: string = '';
  @ViewChild('username') usernameInput!: ElementRef<HTMLInputElement>;
  @ViewChild(ReportingComponent) reporting!: ReportingComponent;

  constructor(private utilsService: UtilsService) {
    if(utilsService.hasLocalAccount)
        window.location.href = '/#';
  }

  passwordValidator(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      this.password = control.value;
      // Update the validation status of the confirmPassword field
      if (this.confirmPassword !== "") {
        let cpControl: AbstractControl | null = this.nvrAccountRegistrationForm.get("confirmPassword");
        cpControl?.updateValueAndValidity();
      }

      const ok = !this.utilsService.passwordRegex.test(control.value);
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
      if (this.confirmPassword !== "") {
        let cpControl: AbstractControl | null = this.nvrAccountRegistrationForm.get("confirmEmail");
        cpControl?.updateValueAndValidity();
      }

      const ok = !new RegExp("^([a-zA-Z0-9_\\-.]+)@((\\[[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.)|(([a-zA-Z0-9\\-]+\\.)+))([a-zA-Z]{2,4}|[0-9]{1,3})(]?)$").test(control.value);
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

  confirmOnReturn($event: InputEvent) {
    // Ensure password field is up-to-date for the confirmPassword validity check
    this.password = this.getFormControl('password').value;

    if ($event.inputType == 'insertLineBreak' && !this.anyInvalid())
       this.register();
  }

  register() {
    this.reporting.dismiss();

    this.username = this.getFormControl('username').value;

    this.utilsService.registerLocalNVRAccount(this.username, this.password, this.confirmPassword, this.email, this.confirmEmail).subscribe(() => {
        this.utilsService.getHasLocalAccount();
        this.reporting.successMessage = "Account " + this.username + " created successfully";
        this.success = true;
      },
      (reason) => {
        this.reporting.errorMessage = reason;
        this.success = false;
      });
  }

  getFormControl(fcName: string): UntypedFormControl {
    return this.nvrAccountRegistrationForm.get(fcName) as UntypedFormControl;
  }

  anyInvalid(): boolean {
    return this.nvrAccountRegistrationForm.invalid;
  }

  hideRegisterForm() {
    window.location.href = "#/";
  }

  ngOnInit(): void {
    this.nvrAccountRegistrationForm = new UntypedFormGroup({
      username: new UntypedFormControl(this.username, [Validators.required, Validators.maxLength(20), Validators.pattern("^[a-zA-Z0-9](_(?!(\.|_))|\.(?!(_|\.))|[a-zA-Z0-9]){3,18}[a-zA-Z0-9]$")]),
      password: new UntypedFormControl(this.password, [Validators.required, Validators.maxLength(25), this.passwordValidator()]),
      confirmPassword: new UntypedFormControl(this.confirmPassword, [Validators.required, Validators.maxLength(25), this.passwordMatchValidator()]),
      email: new UntypedFormControl(this.email, [Validators.maxLength(40), this.emailValidator()]),
      confirmEmail: new UntypedFormControl(this.confirmEmail, [Validators.required, Validators.maxLength(40), this.emailMatchValidator()])
    }, {updateOn: "change"});

    // Ensure camera form controls highlight immediately if invalid
    this.nvrAccountRegistrationForm.markAllAsTouched();
  }

  ngAfterViewInit(): void {
    // Set the focus to the username input
    this.usernameInput.nativeElement.focus();
  }
}
