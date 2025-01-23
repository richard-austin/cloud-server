import { Component, OnInit } from '@angular/core';
import {AbstractControl, FormControl, FormGroup, ValidationErrors, ValidatorFn, Validators} from '@angular/forms';
import { UtilsService } from 'src/app/shared/utils.service';

@Component({
    selector: 'app-forgotten-password',
    templateUrl: './forgotten-password.component.html',
    styleUrls: ['./forgotten-password.component.scss'],
    standalone: false
})
export class ForgottenPasswordComponent implements OnInit {
  forgottenPasswordForm!: FormGroup;
  email: string = "";
  errorMessage: string = '';
  successMessage: string = '';

  constructor(private utilsService: UtilsService) { }

  emailValidator(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      this.email = control.value;

      const ok = !new RegExp("^([a-zA-Z0-9_\\-\\.]+)@((\\[[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.)|(([a-zA-Z0-9\\-]+\\.)+))([a-zA-Z]{2,4}|[0-9]{1,3})(\\]?)$").test(control.value);
      return ok ? {pattern: {value: control.value}} : null;
    };
  }

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
    let uri: string = window.location.protocol+'//'+window.location.hostname+':'+window.location.port;
    this.utilsService.sendResetPasswordLink(email, uri).subscribe(() => {
      this.successMessage = "Please check your email for a reset password link";
    },
      err => {
          this.errorMessage = err.error.reason;
      })
  }

  ngOnInit(): void {
      this.forgottenPasswordForm = new FormGroup( {
        email: new FormControl(this.email, [this.emailValidator(), Validators.required])
      }, {updateOn: 'change'});

      this.forgottenPasswordForm.markAllAsTouched();
  }

}
