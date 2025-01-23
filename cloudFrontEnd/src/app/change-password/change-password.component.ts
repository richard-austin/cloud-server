import {Component, OnInit, } from '@angular/core';
import {AbstractControl, FormControl, FormGroup, ValidationErrors, Validators} from "@angular/forms";
import {ChangePasswordService} from "./change-password.service";
import {HttpErrorResponse} from "@angular/common/http";
import { UtilsService } from '../shared/utils.service';

@Component({
    selector: 'app-change-password',
    templateUrl: './change-password.component.html',
    styleUrls: ['./change-password.component.scss'],
    standalone: false
})
export class ChangePasswordComponent implements OnInit {

  changePasswordForm!: FormGroup;
  oldPassword: string = '';
  newPassword: string = '';
  confirmNewPassword: string = '';
  errorMessage: string = '';
  successMessage: string = '';

  constructor(private changePasswordService: ChangePasswordService, private utilsService:UtilsService) {
  }

  hasError = (controlName: string, errorName: string): boolean | undefined => {
    return this.changePasswordForm.controls[controlName].hasError(errorName);
  }

  getFormControl(fcName: string): FormControl {
    return this.changePasswordForm.get(fcName) as FormControl;
  }

  formSubmitted() {
    this.errorMessage = this.successMessage = '';
    this.oldPassword = this.getFormControl('oldPassword').value;
    this.changePasswordService.changePassword(this.oldPassword, this.newPassword, this.confirmNewPassword).subscribe(() => {
        this.successMessage = "Password changed";
      },
      (reason: HttpErrorResponse) => {
        if (reason.status === 400) {
          for (const key of Object.keys(reason.error)) {
            if (key === 'oldPassword')
              this.invalidPassword();
          }
        } else
          this.errorMessage = reason.error;
      });
  }

  invalidPassword() {
    let oldPasswordCtl: AbstractControl = this.changePasswordForm.controls['oldPassword'];
    let errors: { [key: string]: any } = {pattern: {badPassword: "Password Incorrect"}};
    oldPasswordCtl.setErrors(errors);
    oldPasswordCtl.markAsTouched({onlySelf: true}); //updateValueAndValidity({onlySelf: true, emitEvent: true});
  }

  passwordValidator = (control: AbstractControl): ValidationErrors | null => {
    this.newPassword = control.value;
    // Update the validation status of the confirmPassword field
    if (this.confirmNewPassword !== "") {
      let cpControl: AbstractControl | null = this.changePasswordForm.get("confirmNewPassword");
      cpControl?.updateValueAndValidity();
    }

    let value: string = control.value;
    if(value !== "") {
      if(value.length < 8)
        return {tooShort: {value: value}};
      const ok = !this.utilsService.passwordRegex.test(value);
      return ok ? {pattern: {value: control.value}} : null;
    }
    else
      return {required: {value: value}};
  };

  /**
   * comparePasswords: Custom form field validator to check new password and confirm new password
   *                   are equal
   * @param control
   */
  comparePasswords = (control: AbstractControl): ValidationErrors | null => {
    this.confirmNewPassword = control.value;
    let fg: FormGroup = control.parent as FormGroup;
    let ac: AbstractControl = fg?.controls['newPassword'];
    if (control.value == "")
      return {required: {value: control.value}}
    if (control.value == "" || control.value !== ac?.value) {
      return {confirmNewPassword: {value: control.value}};
    }
    return null;
  }

  anyInvalid(): boolean {
    return this.changePasswordForm.invalid;
  }

  exit() {
    window.location.href = "#/"
  }

  ngOnInit(): void {
    this.changePasswordForm = new FormGroup({
      oldPassword: new FormControl(this.oldPassword, [Validators.required]),
      newPassword: new FormControl(this.newPassword, [this.passwordValidator]),
      confirmNewPassword: new FormControl(this.confirmNewPassword, [this.comparePasswords])
    }, {updateOn: "change"});
    this.changePasswordForm.markAllAsTouched();
  }
}
