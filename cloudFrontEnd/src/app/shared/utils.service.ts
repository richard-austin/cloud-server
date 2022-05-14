import {Injectable} from '@angular/core';
import {HttpClient, HttpErrorResponse, HttpHeaders} from "@angular/common/http";
import {BaseUrl} from "./BaseUrl/BaseUrl";
import {Observable, Subject, throwError} from "rxjs";
import {catchError, map, tap} from "rxjs/operators";
import {CameraParams} from "../cameras/Camera";

export class Temperature {
  temp: string = "";
  isAdmin: boolean = false;
}

export class Version {
  version: string = "";
}

export class MyIp {
  myIp: string = "";
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

export class LoggedinMessage extends Message {
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
  public readonly passwordRegex: RegExp = new RegExp("^[A-Za-z0-9][A-Za-z0-9(){\[1*£$\\]}=@~?^]{7,31}$");

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

  constructor(private http: HttpClient, private _baseUrl: BaseUrl) {
  }

  login(username: string, password: string): Observable<{ role: string }> {
    let creds: string = "username=" + username + "&password=" + password;
    return this.http.post<{ role: string }>(this._baseUrl.getLink("login", "authenticate"), creds, this.httpUrlEncoded).pipe(
      tap((result) => {
          if (result.role === 'ROLE_ADMIN') {
            this._isAdmin = this._loggedIn = true;
          }
          else {
            this._loggedIn = true;
            this.getHasLocalAccount();
          }
        },
        () => {
          this._isAdmin = this._loggedIn = false;
        }),
      catchError((err: HttpErrorResponse) => throwError(err))
    );
  }

  logoff(): void {
    this._hasLocalAccount = this._loggedIn = false;
    window.location.href = 'logoff';
  }

  checkSessionStatus(): Observable<string> {
    return this.getUserAuthorities().pipe(
      map((auths) => {
        return auths[0].authority;
      }),
      tap((auth) => {
        switch (auth) {
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
            break;
          default:
            this._isAdmin = this._loggedIn = false;
        }
      })
    );
  }

  getTemperature(): Observable<Temperature> {
    return this.http.post<Temperature>(this._baseUrl.getLink("utils", "getTemperature"), '', this.httpJSONOptions).pipe(
      tap((result) => {
          this._loggedIn = true;
          this._isAdmin = result.isAdmin;
        },
        () => {
          this._loggedIn = this._isAdmin = false;
          // Check to see if there is a Cloud session and log off if there is
          this.getUserAuthorities().subscribe((val) => {
            if (val.find(v => v.authority === 'ROLE_CLIENT') !== undefined)
              window.location.href = '/logoff';
          })
          //window.location.href="/logoff";  // Ensure Cloud session logged out (may just be NVR offline)
          this.sendMessage(new LoggedOutMessage())
        }),
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
    return this.http.post<any>(this._baseUrl.getLink("user", "createAccount"), details, this.httpJSONOptions).pipe(
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

  setCameraParams(address: string, uri: string, infraredstat: string, cameraName: string): Observable<CameraParams> {
    let cameraParams: { address: string, uri: string, infraredstat: string, cameraName: string } = {
      address: address,
      uri: uri,
      infraredstat: infraredstat,
      cameraName: cameraName
    };
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


  getUserAuthorities(): Observable<{ authority: string }[]> {
    return this.http.post<{ authority: string }[]>(this._baseUrl.getLink('cloud', 'getUserAuthorities'), '', this.httpJSONOptions).pipe(
      tap(),
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
}
