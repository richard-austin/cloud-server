import {AfterViewInit, Component, ElementRef, OnInit, ViewChild} from '@angular/core';
import {AbstractControl, FormControl, FormGroup, ValidationErrors, ValidatorFn, Validators} from "@angular/forms";
import {UtilsService} from "../shared/utils.service";
import {timer} from 'rxjs';
import {SharedModule} from '../shared/shared.module';
import {SharedAngularMaterialModule} from '../shared/shared-angular-material/shared-angular-material.module';
import {ProductIdInputComponent} from './product-id-input/product-id-input.component';
import {ReportingComponent} from '../reporting/reporting.component';

@Component({
    selector: 'app-register-account',
    templateUrl: './register-account.component.html',
    styleUrls: ['./register-account.component.scss'],
  imports: [SharedModule, SharedAngularMaterialModule, ProductIdInputComponent]
})
export class RegisterAccountComponent implements OnInit, AfterViewInit {
  username: string = '';
  private productId: string = '';
  password: string = '';
  confirmPassword: string = '';
  email: string = '';
  confirmEmail: string = '';
  accountRegistrationForm!: FormGroup;
  @ViewChild('username') usernameInput!: ElementRef<HTMLInputElement>
  @ViewChild(ReportingComponent) reporting!:ReportingComponent;
  success: boolean = false;

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

    if($event.key == 'Enter') {
      if(!this.anyInvalid())
        this.register();
    }
  }

  register() {
    this.success = false;
    this.username = this.getFormControl('username').value;
    this.productId = this.getFormControl('productId').value;

    this.utilsService.register(this.username, this.productId, this.password, this.confirmPassword, this.email, this.confirmEmail).subscribe((result) => {
      this.reporting.successMessage = result.message;
      this.success = true;
    },
      (reason) => {
          this.reporting.errorMessage = reason;
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
    timer(20).subscribe(() => {
      this.usernameInput.nativeElement.focus();
    });
  }
}
