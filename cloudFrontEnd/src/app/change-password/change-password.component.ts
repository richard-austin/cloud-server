import {Component, OnInit, ViewChild} from '@angular/core';
import {AbstractControl, FormControl, FormGroup, ValidationErrors, ValidatorFn, Validators} from "@angular/forms";
import {ChangePasswordService} from "./change-password.service";
import {ReportingComponent} from "../reporting/reporting.component";
import {HttpErrorResponse} from "@angular/common/http";

@Component({
  selector: 'app-change-password',
  templateUrl: './change-password.component.html',
  styleUrls: ['./change-password.component.scss']
})
export class ChangePasswordComponent implements OnInit{

  changePasswordForm!: FormGroup;
  oldPassword: string = '';
  newPassword: string = '';
  confirmNewPassword: string = '';
  @ViewChild(ReportingComponent) reporting!:ReportingComponent;

  constructor(private changePasswordService:ChangePasswordService) { }

  hasError = (controlName: string, errorName: string):boolean | undefined =>{
    return this.changePasswordForm.controls[controlName].hasError(errorName);
  }

  getFormControl(fcName: string): FormControl {
    return this.changePasswordForm.get(fcName) as FormControl;
  }

  formSubmitted() {
    this.oldPassword = this.getFormControl('oldPassword').value;
    this.changePasswordService.changePassword(this.oldPassword, this.newPassword, this.confirmNewPassword).subscribe(() => {
      this.reporting.successMessage="Password changed";
    },
    (reason: HttpErrorResponse) => {
      if(reason.status === 400)
      {
        for(const key of Object.keys(reason.error)) {
            if(key === 'oldPassword')
              this.invalidPassword();
        }
      }
      else
        this.reporting.error = reason;
    });
  }

  invalidPassword()
  {
      let oldPasswordCtl: AbstractControl = this.changePasswordForm.controls['oldPassword'];
      let errors:{[key: string]: any} = {pattern: {badPassword:"Password Incorrect"}};
      oldPasswordCtl.setErrors(errors);
      oldPasswordCtl.markAsTouched({onlySelf: true}); //updateValueAndValidity({onlySelf: true, emitEvent: true});
  }

  passwordValidator(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      this.newPassword = control.value;
      // Update the validation status of the confirmPassword field
      if(this.confirmNewPassword !== "")
      {
        let cpControl:AbstractControl | null = this.changePasswordForm.get("confirmNewPassword");
        cpControl?.updateValueAndValidity();
      }

      const ok = !new RegExp("^[A-Za-z0-9][A-Za-z0-9(){\[1*£$\\]}=@~?^]{7,31}$").test(control.value);
      return ok ? {pattern: {value: control.value}} : null;
    };
  }
  /**
   * comparePasswords: Custom form field validator to check new password and confirm new password
   *                   are equal
   * @param control
   */
  comparePasswords = (control: AbstractControl): ValidationErrors | null => {
    this.confirmNewPassword = control.value;
    let fg: FormGroup=control.parent as FormGroup;
    let ac: AbstractControl = fg?.controls['newPassword'];
    if (control.value == "" || control.value !== ac?.value) {
      return {confirmNewPassword:  {value: control.value }};
    }
    return null;
  }

  anyInvalid(): boolean {
    return this.changePasswordForm.invalid;
  }

  ngOnInit(): void {
    this.changePasswordForm = new FormGroup({
      oldPassword: new FormControl(this.oldPassword, [Validators.required]),
      newPassword: new FormControl(this.newPassword, [Validators.required, this.passwordValidator()]),
      confirmNewPassword: new FormControl(this.confirmNewPassword, [this.comparePasswords])
    }, {updateOn: "change"});
    this.changePasswordForm.markAllAsTouched();
  }
}
