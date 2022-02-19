import {AfterViewInit, Component, ElementRef, OnInit, ViewChild} from '@angular/core';
import {FormControl, FormGroup, Validators} from "@angular/forms";
import {CameraService} from "../cameras/camera.service";
import {LoggedinMessage, UtilsService} from "../shared/utils.service";

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.scss']
})
export class LoginComponent implements OnInit, AfterViewInit {

  username: string = '';
  password: string = '';
  loginForm!: FormGroup;
  errorMessage: string = '';
  @ViewChild('username') usernameInput!: ElementRef<HTMLInputElement>

  constructor(private cameraSvc: CameraService, public utilsService: UtilsService) { }
  login()
  {
    this.errorMessage = '';
    this.username = this.getFormControl('username').value;
    this.password = this.getFormControl('password').value;

    this.utilsService.login(this.username, this.password).subscribe(() => {
        this.getFormControl('username').setValue("");
        this.getFormControl('password').setValue("");
        this.username = this.password = "";
        this.cameraSvc.initialiseCameras();
        this.utilsService.sendMessage(new LoggedinMessage());  // Tell nav component we are logged in
      },
      (reason)=> {
      this.errorMessage = reason.error;
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

  confirmOnReturn($event: KeyboardEvent) {
      if($event.key == 'Enter')
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
    this.usernameInput.nativeElement.focus();
  }
}
