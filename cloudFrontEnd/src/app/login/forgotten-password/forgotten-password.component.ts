import { Component, OnInit } from '@angular/core';
import {FormControl, FormGroup, Validators} from '@angular/forms';
import { UtilsService } from 'src/app/shared/utils.service';

@Component({
  selector: 'app-forgotten-password',
  templateUrl: './forgotten-password.component.html',
  styleUrls: ['./forgotten-password.component.scss']
})
export class ForgottenPasswordComponent implements OnInit {
  forgottenPasswordForm!: FormGroup;
  email: string = "";
  errorMessage: string = '';
  successMessage: string = '';

  constructor(private utilsService: UtilsService) { }

  getFormControl(fcName: string): FormControl {
    return this.forgottenPasswordForm.get(fcName) as FormControl;
  }

  confirmOnReturn($event: InputEvent) {
    if($event.inputType == 'insertLineBreak')
      this.sendLink();
  }

  hideForm() {
      window.location.href = "/#/login"
  }

  anyInvalid(): boolean {
    return this.forgottenPasswordForm.invalid;
  }

  sendLink() {
    this.errorMessage = this.successMessage = '';
    let email: string = this.getFormControl('email').value;
    this.utilsService.sendResetPasswordLink(email).subscribe(() => {
      this.successMessage = "Please check your email for a reset password link";
    },
      reason => {
          this.errorMessage = reason.message;
      })
  }

  ngOnInit(): void {
      this.forgottenPasswordForm = new FormGroup( {
        email: new FormControl(this.email, [Validators.email, Validators.required])
      }, {updateOn: 'change'});

      this.forgottenPasswordForm.markAllAsTouched();
  }

}
