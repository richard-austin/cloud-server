import { Component, OnInit } from '@angular/core';
import {ActivatedRoute, UrlSegment} from "@angular/router";
import {AbstractControl, FormControl, FormGroup, ValidationErrors} from "@angular/forms";
import { UtilsService } from '../shared/utils.service';
import {HttpErrorResponse} from "@angular/common/http";
import {ChangePasswordService} from "../change-password/change-password.service";

@Component({
    selector: 'app-reset-password',
    templateUrl: './reset-password.component.html',
    styleUrls: ['./reset-password.component.scss'],
    standalone: false
})
export class ResetPasswordComponent implements OnInit {
  uniqueId: string = "";
  errorMessage: string = "";
  successMessage: string = "";
  resetPasswordForm!: FormGroup;
  newPassword: string = ""
  confirmNewPassword: string = "";

  constructor( private route:ActivatedRoute, private utilsService: UtilsService, private changePasswordService: ChangePasswordService) {
    route.url.subscribe((u:UrlSegment[]) => {
      this.uniqueId=route.snapshot.params.uniqueId!==undefined?route.snapshot.params.uniqueId:"";
    })
  }

  passwordValidator = (control: AbstractControl): ValidationErrors | null => {
    this.newPassword = control.value;
    // Update the validation status of the confirmPassword field
    if (this.confirmNewPassword !== "") {
      let cpControl: AbstractControl | null = this.resetPasswordForm.get("confirmNewPassword");
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

  invalidPassword() {
    let oldPasswordCtl: AbstractControl = this.resetPasswordForm.controls['oldPassword'];
    let errors: { [key: string]: any } = {pattern: {badPassword: "Password Incorrect"}};
    oldPasswordCtl.setErrors(errors);
    oldPasswordCtl.markAsTouched({onlySelf: true}); //updateValueAndValidity({onlySelf: true, emitEvent: true});
  }

  formSubmitted() {
    this.errorMessage = this.successMessage = '';
    this.changePasswordService.resetPassword(this.newPassword, this.confirmNewPassword, this.uniqueId).subscribe(() => {
        this.successMessage = "Password changed";
      },
      (reason: HttpErrorResponse) => {
        if (reason.status === 400) {
          for (const key of Object.keys(reason.error)) {
            if (key === 'oldPassword')
              this.invalidPassword();
          }
        } else
          this.errorMessage = reason.error.reason;
      });
  }

  anyInvalid(): boolean {
    return this.resetPasswordForm.invalid;
  }

  hasError = (controlName: string, errorName: string): boolean | undefined => {
    return this.resetPasswordForm.controls[controlName].hasError(errorName);
  }

  getFormControl(fcName: string): FormControl {
    return this.resetPasswordForm.get(fcName) as FormControl;
  }

  exit() {
    window.location.href = "#/"
  }

  ngOnInit(): void {
    this.resetPasswordForm = new FormGroup({
      newPassword: new FormControl(this.newPassword, [this.passwordValidator]),
      confirmNewPassword: new FormControl(this.confirmNewPassword, [this.comparePasswords])
    }, {updateOn: "change"});
    this.resetPasswordForm.markAllAsTouched();
  }
}
