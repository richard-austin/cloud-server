import {AfterViewInit, Component, ElementRef, OnDestroy, OnInit, ViewChild} from '@angular/core';
import {CameraService, cameraType} from '../cameras/camera.service';
import {Camera, Stream} from '../cameras/Camera';
import {ReportingComponent} from '../reporting/reporting.component';
import {HttpErrorResponse} from '@angular/common/http';
import {Subscription} from 'rxjs';
import {IdleTimeoutStatusMessage, Message, messageType, UtilsService} from '../shared/utils.service';
import {MatDialog} from '@angular/material/dialog';
import {IdleTimeoutModalComponent} from '../idle-timeout-modal/idle-timeout-modal.component';
import {MatDialogRef} from '@angular/material/dialog/dialog-ref';
import {UserIdleConfig} from '../angular-user-idle/angular-user-idle.config';
import {UserIdleService} from '../angular-user-idle/angular-user-idle.service';
import {map} from 'rxjs/operators';
import {Client, StompSubscription} from '@stomp/stompjs';

@Component({
  selector: 'app-nav',
  templateUrl: './nav.component.html',
  styleUrls: ['./nav.component.scss']
})
export class NavComponent implements OnInit, AfterViewInit, OnDestroy {

  @ViewChild(ReportingComponent) errorReporting!: ReportingComponent;
  @ViewChild('navbarCollapse') navbarCollapse!: ElementRef<HTMLDivElement>;

//  cameras: Map<string, Camera> = new Map<string, Camera>();
  confirmLogout: boolean = false;
  pingHandle!: Subscription;
  timerHandle!: Subscription;
  temperature!: number;
  noTemperature: boolean = true;
  tempAlertClass!: string;
  idleTimeoutDialogRef!: MatDialogRef<IdleTimeoutModalComponent>;
  private idleTimeoutActive: boolean = false;  // Idle time out inactive when not logged in or showing live video
  private callGetTemp: boolean = false;  // Prevent calling getTemperature while not logged in
  private callGetAuthorities: boolean = false;
  private messageSubscription!: Subscription;
  cameraTypes: typeof cameraType = cameraType;
  private client!: Client;
  talkOffSubscription!: StompSubscription;

  constructor(public cameraSvc: CameraService, public utilsService: UtilsService, private userIdle: UserIdleService, private dialog: MatDialog) {
  }

  login() {
    window.location.href = '#/login';
  }

  registerAccount() {
    window.location.href = '#/register';
  }

  setVideoStream(cam: Camera, stream: Stream): void {
    let suuid = 'suuid=';
    let uri = stream.uri;
    let index = uri.indexOf(suuid);
    let streamName = uri.substring(index + suuid.length);
    window.location.href = '#/live/' + streamName;
  }

  showRecording(cam: Camera, stream: Stream): void {
    let suuid = 'suuid=';
    let uri = stream.recording.recording_src_url;
    let index = uri.indexOf(suuid);
    let streamName = uri.substring(index + suuid.length);
    window.location.href = '#/recording/' + streamName;
  }

  cameraControl(cam: Camera) {
    window.location.href = '#/cameraparams/' + btoa(cam.address);
  }

  getActiveIPAddresses() {
    window.location.href = '#/getactiveipaddresses';
  }

  getLocalWifiDetails() {
    window.location.href = '#/getlocalwifidetails';
  }

  wifiSettings() {
    window.location.href = '#/wifisettings';
  }

  localAdminFunctionsForNVR() {
    window.location.href = '#/cua';
  }

  multiCamView() {
    window.location.href = '#/multicam';
  }

  confirmLogoff(): void {
    this.confirmLogout = true;
  }

  logOff(logoff: boolean): void {
    this.confirmLogout = false;

    if (logoff) {
      this.utilsService.logoff();
    }
  }

  about() {
    window.location.href = '#/about';
  }

  aboutCCTVCloud() {
    window.location.href = '#/about/true';
  }


  accountsAdmin() {
    window.location.href = '#/accountadmin';
  }

  private getTemperature(): void {
    this.utilsService.getTemperature().subscribe((tmp) => {
        let temperature: string = tmp.temp;
        let idx1: number = temperature.indexOf('=');
        let idx2: number = temperature.lastIndexOf('\'');
        if (idx1 !== -1 && idx2 !== -1) {
          let strTemp: string = temperature.substr(idx1 + 1, idx2 - idx1);
          this.temperature = parseFloat(strTemp);
          this.noTemperature = false;
          if (this.temperature < 50) {
            this.tempAlertClass = 'success';
          } else if (this.temperature < 70) {
            this.tempAlertClass = 'warning';
          } else {
            this.tempAlertClass = 'danger';
          }
        } else {
          this.noTemperature = false;
          this.tempAlertClass = 'danger';
        }
      },
      () => {
        this.noTemperature = true;
        this.tempAlertClass = 'alert-danger';
      });
  }

  setIp() {
    window.location.href = '#/setip';
  }

  registerLocalNVRAccount() {
    window.location.href = '#/registerlocalnvraccount';
  }

  removeLocalNVRAccount() {
    window.location.href = '#/removelocalnvraccount';
  }

  drawdownCalc() {
    window.location.href = '#/dc';
  }

  configSetup() {
    window.location.href = '#/configsetup';
  }

  openIdleTimeoutDialog(idle: number, timeout: number, count: number): void {
    let data: any = {};
    let remainingSecs: number = timeout - count;
    if (remainingSecs === timeout - 1) {
      this.idleTimeoutDialogRef = this.dialog.open(IdleTimeoutModalComponent, {
        //  width: '450px',
        data: {idle: idle, remainingSecs: remainingSecs}
      });

      // this.idleTimeoutDialogRef.afterClosed().subscribe(res => {
      // });
    } else {
      data = this.idleTimeoutDialogRef.componentInstance.data;
      data.idle = idle;
      data.remainingSecs = remainingSecs;
    }
  }

  toggleMenu() {
    let navbarCollapse: HTMLDivElement = this.navbarCollapse.nativeElement;
    let style: string | null = navbarCollapse.getAttribute('style');

    if (style === null || style === 'max-height: 0') {
      navbarCollapse.setAttribute('style', 'max-height: 200px');
    } else {
      navbarCollapse.setAttribute('style', 'max-height: 0');
    }
  }

  menuClosed() {
    let navbarCollapse: HTMLDivElement = this.navbarCollapse.nativeElement;
    navbarCollapse.setAttribute('style', 'max-height: 0');
  }

  initialise(auth: string): void {
    switch (auth) {
      case 'ROLE_CLIENT':
        this.cameraSvc.initialiseCameras();  // Load the cameras data
        this.idleTimeoutActive = this.callGetTemp = true;
        this.callGetAuthorities = false;
        this.getTemperature();  // Ensure we show the core temperature straight away on refresh
                                // (rather than wait till the first heartbeat)
        let serverUrl: string = (window.location.protocol == 'http:' ? 'ws://' : 'wss://') + window.location.host + '/stomp';
        this.client = new Client({
          brokerURL: serverUrl,
          reconnectDelay: 2000,
          heartbeatOutgoing: 120000,
          heartbeatIncoming: 120000,
          onConnect: () => {
            this.talkOffSubscription = this.client.subscribe('/topic/talkoff', (message: any) => this.utilsService.talkOff(message));
          },
          debug: () => {
          }
        });
        this.client.activate();
        break;
      case 'ROLE_ADMIN':
        this.idleTimeoutActive = this.callGetAuthorities = true;
        this.callGetTemp = false;
        break;
      case 'ROLE_ANONYMOUS':
        this.idleTimeoutActive = this.callGetTemp = this.callGetAuthorities = false;
        break;
      default:
        this.idleTimeoutActive = this.callGetTemp = this.callGetAuthorities = false;
    }
  }

  setUpSMTPClient() {
    window.location.href = '#/setupsmtpclient';
  }

  changeEmail() {
    window.location.href = '#/changeaccountemail';
  }

  changePassword() {
    window.location.href = '#/changepassword';
  }

  ngOnInit(): void {
    this.utilsService.getUserAuthorities().pipe(
      map((auths: { authority: string }[]) => {
        return auths !== null && auths.length > 0 ? auths[0]?.authority : 'ROLE_ANONYMOUS';
      })
    ).subscribe((auth) => this.initialise(auth));

    //Start watching for user inactivity.
    this.userIdle.startWatching();
    this.userIdle.resetTimer();

    this.messageSubscription = this.utilsService.getMessages().subscribe((message: Message) => {
      if (message.messageType === messageType.idleTimeoutStatus) {
        let itos: IdleTimeoutStatusMessage = message as IdleTimeoutStatusMessage;
        this.idleTimeoutActive = itos.active;
        //    console.log("idle active = "+this.idleTimeoutActive)
      } else if (message.messageType === messageType.loggedIn) {
        console.log('Logged in message received');
        window.location.href = '#/';
        this.idleTimeoutActive = true;

        this.utilsService.getUserAuthorities().pipe(
          map((auths: { authority: string }[]) => {
            return auths !== null && auths.length > 0 ? auths[0]?.authority : 'ROLE_ANONYMOUS';
          })
        ).subscribe((auth) => this.initialise(auth));


        // if(!this.utilsService.isAdmin) {
        //   this.cameraStreams = this.cameraSvc.getCameraStreams();
        //   this.cameras = this.cameraSvc.getCameras()
        //   this.callGetTemp = true;
        //   this.callGetAuthorities = false;
        //
        //   // Get the initial core temperature
        //   this.getTemperature();
        // }
        // else {
        //   this.callGetAuthorities = true;
        //   this.callGetTemp = false;
        // }
      } else if (message.messageType == messageType.loggedOut) {
        this.idleTimeoutActive = this.callGetAuthorities = this.callGetTemp = false;
      }
    });

    // Start watching when user idle is starting.
    this.timerHandle = this.userIdle.onTimerStart().subscribe((count: number) => {
      if (this.idleTimeoutActive) {
        let config: UserIdleConfig = this.userIdle.getConfigValue();
        // @ts-ignore
        this.openIdleTimeoutDialog(config.idle, config.timeout, count);
      } else {
        this.userIdle.resetTimer();
      }
    });

    // Log off when time is up.
    this.userIdle.onTimeout().subscribe(() => {
      this.idleTimeoutDialogRef.close();
      this.utilsService.logoff();
    });

    // Gets the core temperature every minute (Raspberry pi only), and keeps the session alive
    this.pingHandle = this.userIdle.ping$.subscribe(() => {
      if (this.callGetTemp) {
        this.getTemperature();  // Gets temperature and is used as a heartbeat keep-alive call
      } else if (this.callGetAuthorities) {
        this.utilsService.getUserAuthorities().subscribe();
      }  // Used as a heartbeat keep alive call
    });
  }

  ngAfterViewInit(): void {
    // If the camera service got any errors while getting the camera setup, then we report it here.
    this.cameraSvc.errorEmitter.subscribe((error: HttpErrorResponse) => this.errorReporting.errorMessage = error);
  }

  ngOnDestroy(): void {
    this.pingHandle.unsubscribe();
    this.timerHandle.unsubscribe();
    this.messageSubscription.unsubscribe();
    this.talkOffSubscription?.unsubscribe();
    this.client?.deactivate({force: false}).then(() => {
    });
  }
}
