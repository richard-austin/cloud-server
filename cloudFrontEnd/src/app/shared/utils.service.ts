import {Injectable} from '@angular/core';
import {HttpClient, HttpErrorResponse, HttpHeaders} from "@angular/common/http";
import {BaseUrl} from "./BaseUrl/BaseUrl";
import {Observable, Subject, throwError} from "rxjs";
import {catchError, tap} from "rxjs/operators";
import {Camera, CameraParams} from '../cameras/Camera';
import {cameraType} from '../cameras/camera.service';
import {SMTPData} from '../setup-smtpclient/setup-smtpclient.component';
import {IMessage} from '@stomp/stompjs';

export class Temperature {
  temp: string = "";
}

export class IsMQConnected {
  transportActive: boolean = false;
}

export class Version {
  version: string = "";
}

export class MyIp {
  myIp: string = "";
}

export class GuestStatus {
  guestAccount: boolean = true;

  constructor(guestAccount: boolean) {
    this.guestAccount = guestAccount;
  }
}

export enum messageType {idleTimeoutStatus, loggedIn, loggedOut}

export abstract class Message {
  protected constructor(messageType: messageType) {

    this.messageType = messageType;
  }

  messageType!: messageType;
}

export class IdleTimeoutStatusMessage extends Message {
  constructor(active: boolean) {
    super(messageType.idleTimeoutStatus);
    this.active = active;
  }

  active: boolean = true;
}

export class LoggedInMessage extends Message {
  role: string;

  constructor(role: string) {
    super(messageType.loggedIn);
    this.role = role;
  }
}

export class LoggedOutMessage extends Message {
  constructor() {
    super(messageType.loggedOut);
  }
}

export class Account {
  productId!: string;
  accountCreated!: boolean;
  accountEnabled!: boolean;
  userName!: string;
  email!: string;
  nvrConnected!: boolean;
  usersConnected!: number;
}

export class SetCameraParams {
  constructor(cameraTypes: cameraType, address: string, uri: string, infraredstat: string, cameraName: string, reboot: boolean=false, wdr?: string, lamp_mode?: string) {
    this.cameraType = cameraTypes;
    this.address = address;
    this.uri = uri;
    this.infraredstat = infraredstat;
    this.cameraName = cameraName;
    this.wdr = wdr;
    this.lamp_mode = lamp_mode;
    this.reboot = reboot;
  }
  cameraType: cameraType;
  address: string;
  uri: string;
  infraredstat: string;
  cameraName: string;
  wdr: string | undefined;
  lamp_mode: string | undefined;
  reboot: boolean;
}

export class Device {
  name!: string;
  ipAddress!: string;
  ipPort!: number;
}

@Injectable({
  providedIn: 'root'
})
export class UtilsService {
  readonly httpJSONOptions = {
    headers: new HttpHeaders({
      'Content-Type': 'application/json',
      'Authorization': 'my-auth-token'
    })
  };

  readonly httpUrlEncoded = {
    headers: new HttpHeaders({
      'Content-Type': 'application/x-www-form-urlencoded',
      'Authorization': 'my-auth-token'
    })
  }

  private _messaging: Subject<any> = new Subject<any>();
  private _loggedIn: boolean = false;
  private _isAdmin: boolean = false;
  private _hasLocalAccount: boolean = false;
  public readonly passwordRegex: RegExp = new RegExp(/^(?=.*[A-Za-z])(?=.*\d)(?=.*[@$!%*#?&])[A-Za-z\d@$!%*#?&]{8,64}$/);
  speakActive: boolean = true;
  private _activeMQTransportActive: boolean = false;
  readonly isGuestAccount: boolean = false;

  public readonly activeMQPasswordRegex: RegExp = new RegExp(/^$|^[A-Za-z0-9]{20}$/);
  public readonly hostNameRegex =  /^[a-zA-Z0-9][a-zA-Z0-9._-]*$/
  public readonly ipV4RegEx = /^([0-9]|[1-9][0-9]|1([0-9][0-9])|2([0-4][0-9]|5[0-5]))\.([0-9]|[1-9][0-9]|1([0-9][0-9])|2([0-4][0-9]|5[0-5]))\.([0-9]|[1-9][0-9]|1([0-9][0-9])|2([0-4][0-9]|5[0-5]))\.([0-9]|[1-9][0-9]|1([0-9][0-9])|2([0-4][0-9]|5[0-5]))$/
  public readonly ipV6RegEx = /^s*((([0-9A-Fa-f]{1,4}:){7}([0-9A-Fa-f]{1,4}|:))|(([0-9A-Fa-f]{1,4}:){6}(:[0-9A-Fa-f]{1,4}|((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3})|:))|(([0-9A-Fa-f]{1,4}:){5}(((:[0-9A-Fa-f]{1,4}){1,2})|:((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3})|:))|(([0-9A-Fa-f]{1,4}:){4}(((:[0-9A-Fa-f]{1,4}){1,3})|((:[0-9A-Fa-f]{1,4})?:((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){3}(((:[0-9A-Fa-f]{1,4}){1,4})|((:[0-9A-Fa-f]{1,4}){0,2}:((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){2}(((:[0-9A-Fa-f]{1,4}){1,5})|((:[0-9A-Fa-f]{1,4}){0,3}:((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){1}(((:[0-9A-Fa-f]{1,4}){1,6})|((:[0-9A-Fa-f]{1,4}){0,4}:((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3}))|:))|(:(((:[0-9A-Fa-f]{1,4}){1,7})|((:[0-9A-Fa-f]{1,4}){0,5}:((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3}))|:)))(%.+)?s*(\/([0-9]|[1-9][0-9]|1[0-1][0-9]|12[0-8]))?$/

  get loggedIn(): boolean {
    return this._loggedIn;
  }

  get isAdmin() {
    return this._isAdmin;
  }

  get hasLocalAccount() : boolean
  {
    return this._hasLocalAccount;
  }

  adHocDevices!: Array<Device>;
  public static readonly toolTipDelay = 1000;
  constructor(private http: HttpClient, private _baseUrl: BaseUrl) {
    // Initialise the speakActive state
    this.audioInUse().subscribe();
    this.loadAdHocDevices().subscribe();
  }

  login(username: string, password: string, rememberMe: boolean): Observable<[{ authority: string }]> {
    let creds: string = "username=" + username + "&password=" + password + (rememberMe ? "&remember-me=on" : '');
    return this.http.post<[{ authority: string }]>(this._baseUrl.getLink("login", "authenticate"), creds, this.httpUrlEncoded).pipe(
      tap((result) => {
          if (result[0].authority === 'ROLE_ADMIN') {
            this._isAdmin = this._loggedIn = true;
          }
          else {
            this._loggedIn = true;
            this.getHasLocalAccount();
          }
        },
        (reason) => {
          this._isAdmin = this._loggedIn = false;
        }),
      catchError((err: HttpErrorResponse) => throwError(err))
    );
  }

  logout(): void {
    this._hasLocalAccount = this._loggedIn = false;
    window.location.href = 'logout';
  }

  changeInstanceCount(increment: boolean) : Observable<any> {
     const params: {increment: boolean} = {"increment": increment}
     return this.http.post<any>(this._baseUrl.getLink("cloud", "changeInstanceCount"), params, this.httpJSONOptions);
  }

  getTemperature(): Observable<Temperature> {
    return this.http.post<Temperature>(this._baseUrl.getLink("cloud", "getTemperature"), '', this.httpJSONOptions).pipe(
       catchError((err: HttpErrorResponse) => throwError(err))
    );
  }

  register(userName: string, productId: string, password: string, confirmPassword: string, email: string, confirmEmail: string): Observable<any> {
    let details: { username: string, productId: string, password: string, confirmPassword: string, email: string, confirmEmail: string } =
      {
        username: userName,
        productId: productId,
        password: password,
        confirmPassword: confirmPassword,
        email: email,
        confirmEmail: confirmEmail
      };
    return this.http.post<any>(this._baseUrl.getLink("cloud", "register"), details, this.httpJSONOptions).pipe(
      catchError((err: HttpErrorResponse) => throwError(err))
    );
  }

  registerLocalNVRAccount(username: string, password: string, confirmPassword: string, email: string, confirmEmail: string) {
    let details: { username: string, password: string, confirmPassword: string, email: string, confirmEmail: string } =
      {
        username: username,
        password: password,
        confirmPassword: confirmPassword,
        email: email,
        confirmEmail: confirmEmail
      };
    return this.http.post<any>(this._baseUrl.getLink("user", "createOrUpdateAccount"), details, this.httpJSONOptions).pipe(
      catchError((err: HttpErrorResponse) => throwError(err))
    );
  }

  removeLocalNVRAccount(): Observable<{username: string}>
  {
    return this.http.post<{username: string}>(this._baseUrl.getLink("user", "removeAccount"), '', this.httpJSONOptions).pipe(
      catchError((err: HttpErrorResponse) => throwError(err))
    );
  }

  getVersion(isLocal: boolean): Observable<Version> {
    return this.http.post<Version>(this._baseUrl.getLink(isLocal ? "cloud" : "utils", "getVersion"), '', this.httpJSONOptions).pipe(
      tap(),
      catchError((err: HttpErrorResponse) => throwError(err))
    );
  }

  getOpenSourceInfo(): Observable<string> {
    return this.http.post(this._baseUrl.getLink("utils", "getOpenSourceInfo"), '', {responseType: 'text'}).pipe(
      tap(),
      catchError((err: HttpErrorResponse) => throwError(err))
    );
  }

  setIp(): Observable<MyIp> {
    return this.http.post<MyIp>(this._baseUrl.getLink("utils", "setIP"), '', this.httpJSONOptions).pipe(
      tap(),
      catchError((err: HttpErrorResponse) => throwError(err))
    );
  }

  cameraParams(address: string, uri: string, params: string): Observable<CameraParams> {
    let cameraParams: { address: string, uri: string, params: string } = {address: address, uri: uri, params: params};
    return this.http.post<CameraParams>(this._baseUrl.getLink("utils", "cameraParams"), JSON.stringify(cameraParams), this.httpJSONOptions).pipe(
      tap(),
      catchError((err: HttpErrorResponse) => throwError(err))
    );
  }

  setCameraParams(cameraParams: SetCameraParams): Observable<CameraParams> {
    return this.http.post<CameraParams>(this._baseUrl.getLink("utils", "setCameraParams"), JSON.stringify(cameraParams), this.httpJSONOptions).pipe(
      tap(),
      catchError((err: HttpErrorResponse) => throwError(err))
    );
  }

  getAccounts(): Observable<Account[]> {
    return this.http.post<Account[]>(this._baseUrl.getLink('cloud', 'getAccounts'), '', this.httpJSONOptions).pipe(
      tap(),
      catchError((err: HttpErrorResponse) => throwError(err))
    )
  }

  setAccountEnabledStatus(account: Account): Observable<void> {
    let acc: { username: string, accountEnabled: boolean } = {
      username: account.userName,
      accountEnabled: account.accountEnabled
    }
    return this.http.post<void>(this._baseUrl.getLink('cloud', 'setAccountEnabledStatus'), JSON.stringify(acc), this.httpJSONOptions).pipe(
      tap(),
      catchError((err: HttpErrorResponse) => throwError(err))
    )
  }

  deleteAccount(account: Account): Observable<void> {
    let acc: { username: string } = {username: account.userName}
    return this.http.post<void>(this._baseUrl.getLink('cloud', 'adminDeleteAccount'), JSON.stringify(acc), this.httpJSONOptions).pipe(
      tap(),
      catchError((err: HttpErrorResponse) => throwError(err))
    )
  }

  adminChangePassword(account: Account, password: string, confirmPassword: string) {
    let cpw: { username: string, password: string, confirmPassword: string } = {
      username: account.userName,
      password: password,
      confirmPassword: confirmPassword
    };
    return this.http.post<void>(this._baseUrl.getLink('cloud', 'adminChangePassword'), JSON.stringify(cpw), this.httpJSONOptions).pipe(
      tap(),
      catchError((err: HttpErrorResponse) => throwError(err))
    )
  }

  adminChangeEmail(account: Account, email: string, confirmEmail: string) {
    let cpw: { username: string, email: string, confirmEmail: string } = {
      username: account.userName,
      email: email,
      confirmEmail: confirmEmail
    };
    return this.http.post<void>(this._baseUrl.getLink('cloud', 'adminChangeEmail'), JSON.stringify(cpw), this.httpJSONOptions).pipe(
      tap(),
      catchError((err: HttpErrorResponse) => throwError(err))
    );
  }

  /**
   * sendResetPasswordLink:
   * @param email
   * @param clientUri
   */
  sendResetPasswordLink(email: string, clientUri: string): Observable<void> {
    let em: { email: string, clientUri: string } = {email: email, clientUri: clientUri};
    return this.http.post<void>(this._baseUrl.getLink('cloud', 'sendResetPasswordLink'), JSON.stringify(em), this.httpJSONOptions).pipe(
      tap(),
      catchError((err: HttpErrorResponse) => throwError(err))
    );
  }

  setupSMTPClientLocally(smtpData: SMTPData) {
    return this.http.post<boolean>(this._baseUrl.getLink("cloud", "setupSMTPClientLocally"), JSON.stringify(smtpData), this.httpJSONOptions);
  }

  getSMTPClientParamsLocally() : Observable<SMTPData> {
    return this.http.post<SMTPData>(this._baseUrl.getLink("cloud", "getSMTPClientParamsLocally"), "", this.httpJSONOptions);
  }

  getEmail(): Observable<{ email: string }> {
    return this.http.post<{ email: string }>(this._baseUrl.getLink("cloud", "getEmail"), '', this.httpJSONOptions).pipe(
      catchError((err: HttpErrorResponse) => throwError(err))
    );
  }

  changeEmail(password: string, newEmail: string, confirmNewEmail: string) {
    let passwordChange: { password: string, newEmail: string, confirmNewEmail: string } = {
      password: password,
      newEmail: newEmail,
      confirmNewEmail: confirmNewEmail
    };
    return this.http.post<void>(this._baseUrl.getLink("cloud", "changeEmail"), JSON.stringify(passwordChange), this.httpJSONOptions).pipe(
      catchError((err: HttpErrorResponse) => throwError(err))
    );
  }

  getUserAuthorities(): Observable<{ authority: string }[]> {
    return this.http.post<{ authority: string }[]>(this._baseUrl.getLink('cloud', 'getUserAuthorities'), '', this.httpJSONOptions).pipe(
      tap((auth) => {
        let strAuth: string = auth[0]?.authority;
        switch (strAuth) {
          case 'ROLE_CLIENT':
            this._isAdmin = false;
            this._loggedIn = true;
            this.getHasLocalAccount();
            break;
          case 'ROLE_ADMIN':
            this._isAdmin = true;
            this._loggedIn = true;
            break;
          case 'ROLE_ANONYMOUS':
            this._isAdmin = this._loggedIn = false;
            this.sendMessage(new LoggedOutMessage());  // Tell nav component we are logged out
            break;
          default:
            this._isAdmin = this._loggedIn = false;
            this.sendMessage(new LoggedOutMessage());  // Tell nav component we are logged out
        }
      }),
      catchError((err: HttpErrorResponse) => throwError(err))
    );
  }

  getHasLocalAccount() : void
  {
    this._hasLocalAccount = false;
    this.http.post<boolean>(this._baseUrl.getLink('user', 'hasLocalAccount'), '', this.httpJSONOptions).pipe(
      tap((result) => {
        this._hasLocalAccount = result;
      }),
      catchError((err: HttpErrorResponse) => throwError(err))
    ).subscribe()
  }

  sendMessage(message: Message) {
    this._messaging.next(message);
  }

  getMessages(): Observable<Message> {
    return this._messaging.asObservable();
  }

  getScrollableContentStyle(scrollableContent: HTMLElement | null | undefined, setMaxHeight: boolean = false): string {
    // Calculated scrollbar height, don't use or we Expression changed after it was checked error will occur
    //   scrollableContent?.offsetHeight - scrollableContent?.clientHeight;
    const scrollbarHeight = 20; //Should be the same as height in ::-webkit-scrollbar
    const extraBit = 1;  // To make browser window vertical scrollbar disappear

    if (scrollableContent !== null && scrollableContent !== undefined) {
      const boundingRect = scrollableContent.getBoundingClientRect()
      return (setMaxHeight ? 'max-' : '') + `height: calc(100dvh - ${boundingRect.top + scrollbarHeight + extraBit}px);`
    }
    else return ""
  }


  startAudioOut(cam: Camera, netcam_uri: string) {
    return this.http.post<void>(this._baseUrl.getLink("utils", "startAudioOut"), JSON.stringify({
      cam: cam,
      netcam_uri: netcam_uri
    }), this.httpJSONOptions).pipe(
      catchError((err: HttpErrorResponse) => throwError(err))
    );
  }

  stopAudioOut() {
    return this.http.post<void>(this._baseUrl.getLink("utils", "stopAudioOut"), "", this.httpJSONOptions).pipe(
      catchError((err: HttpErrorResponse) => throwError(err))
    );
  }

  audioInUse() {
    return this.http.post<{audioInUse: boolean }>(this._baseUrl.getLink("utils", "audioInUse"), "", this.httpJSONOptions).pipe(
      tap((result)=> {
        this.speakActive = result.audioInUse;
      }),
      catchError((err: HttpErrorResponse) => throwError(err)));
  }
  isTransportActive():Observable<IsMQConnected> {
    return this.http.post<IsMQConnected>(this._baseUrl.getLink("cloud", "isTransportActive"), '', this.httpJSONOptions).pipe(
      tap((status: IsMQConnected) => {
        this.activeMQTransportActive = status.transportActive;
        }),
      catchError((err: HttpErrorResponse) => throwError(err))
    );
  }

  /**
   * talkOff: Called on receipt of the talkOff websocket message. This disables audio out to any camera while the channel is in use and
   *          re-enables it when that usage has finished.
   * @param message
   */
  talkOff(message: any) {
    if (message.body) {
      let msgObj = JSON.parse(message.body);
      if (msgObj.message === 'talkOff') {
        this.speakActive = msgObj.instruction == "on";
      }
    }
  }

  addOrUpdateActiveMQCreds(username: string, password: string, confirmPassword: string, mqHost: string, updateExisting: boolean = false) : Observable<void> {
    let details: { username: string, password: string, confirmPassword: string, mqHost: string, updateExisting: boolean} =
      {
        username: username,
        password: password,
        confirmPassword: confirmPassword,
        mqHost: mqHost,
        updateExisting: updateExisting
      };
    return this.http.post<void>(this._baseUrl.getLink("cloud", "addOrUpdateActiveMQCreds"), details, this.httpJSONOptions).pipe(
      catchError((err: HttpErrorResponse) => throwError(err))
    );
  }

  checkForActiveMQCreds(): Observable<{hasActiveMQCreds: boolean, mqHost: string}> {
    return this.http.post<{hasActiveMQCreds: boolean, mqHost: string}>(this._baseUrl.getLink('cloud', 'hasActiveMQCreds'), '', this.httpJSONOptions).pipe(
      tap(() => {
      }),
      catchError((err: HttpErrorResponse) => throwError(err))
    )
  }

  setTransportStatus(message: IMessage) {
    let strMsg: string;
    if (message.isBinaryBody)
      strMsg = new TextDecoder().decode(message.binaryBody);
    else
      strMsg = message.body;
    let status: { transportActive: boolean } = JSON.parse(strMsg)
    this._activeMQTransportActive = status.transportActive;
  }

  get activeMQTransportActive(): boolean {
    return this._activeMQTransportActive
  }

  set activeMQTransportActive(value: boolean) {
    this._activeMQTransportActive = value;
  }

  /**
   * isGuest: Always returns false on the Cloud
   */
  async isGuest(): Promise<GuestStatus> {
    return new GuestStatus(this.isGuestAccount);
  }

  /**
   * expandableStreamStyle: Open and close list sections with vertical transition
   * @param bOpen Open if true, else close
   * @param div The div enclosing the list
   * @param extra Some extra headroom for if this element contains expandable elements
   */
  static expandableStreamStyle(bOpen: boolean, div: HTMLDivElement, extra:number = 0): string {
    const height = div.scrollHeight+extra;
    const transitionStyle = "; transition: max-height 225ms; transition-timing-function: cubic-bezier(0.4, 0.0, 0.2, 1)";
    const openStyle = "max-height: "+height+ "px"+transitionStyle;
    const closedStyle = "max-height: 0"+transitionStyle;

    return bOpen ? openStyle : closedStyle;
  }
  loadAdHocDevices() {
    return this.http.post<Array<Device>>(this._baseUrl.getLink("utils", "loadAdHocDevices"), '', this.httpJSONOptions).pipe(
      tap(devices => {
        this.adHocDevices = devices;
      }),
      catchError((err: HttpErrorResponse) => throwError(err))
    );
  }

  updateAdhocDeviceList(adHocDeviceListJSON: string):
    Observable<Array<Device>> {
    let devices = {adHocDeviceListJSON: adHocDeviceListJSON};
    return this.http.post<any>(this._baseUrl.getLink("utils", "updateAdHocDeviceList"), JSON.stringify(devices), this.httpJSONOptions).pipe(
      tap(devices => {
        this.adHocDevices = devices;
      })
    );
  }
}
