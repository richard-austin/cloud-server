import {Injectable} from '@angular/core';
import {HttpClient, HttpErrorResponse, HttpHeaders} from "@angular/common/http";
import {BaseUrl} from "./BaseUrl/BaseUrl";
import {Observable, Subject, throwError} from "rxjs";
import {catchError, tap} from "rxjs/operators";
import {CameraParams} from "../cameras/Camera";

export class Temperature {
  temp: string = "";
  isAdmin: boolean =false;
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
  public readonly passwordRegex:RegExp = new RegExp("^[A-Za-z0-9][A-Za-z0-9(){\[1*£$\\]}=@~?^]{7,31}$");

  get loggedIn(): boolean {
    return this._loggedIn;
  }

  get isAdmin()
  {
      return this._isAdmin;
  }

  constructor(private http: HttpClient, private _baseUrl: BaseUrl) {
  }

  login(username: string, password: string): Observable<{ role: string }> {
    let creds: string = "username=" + username + "&password=" + password;
    return this.http.post<{role: string}>(this._baseUrl.getLink("login", "authenticate"), creds, this.httpUrlEncoded).pipe(
      tap((result) => {
        if(result.role === 'ROLE_ADMIN')
          this._isAdmin = true;
        else
          this._loggedIn = true;
        },
        reason => {
        this._isAdmin = this._loggedIn = false;
        }),
      catchError((err: HttpErrorResponse) => throwError(err))
    );
  }

  logoff(): void{
    window.location.href = 'logoff';
    this._loggedIn = false;
  }

  getTemperature(): Observable<Temperature> {
    return this.http.post<Temperature>(this._baseUrl.getLink("cloud", "getTemperature"), '', this.httpJSONOptions).pipe(
      tap((result) => {
          this._loggedIn = !result.isAdmin;
          this._isAdmin = result.isAdmin;
        },
        (reason) => {
          this._loggedIn =this._isAdmin = false;
          this.sendMessage(new LoggedOutMessage())
        }),
      catchError((err: HttpErrorResponse) => throwError(err))
    );
  }

  register(userName: string, productId: string, password: string, confirmPassword: string, email: string, confirmEmail:string): Observable<any>
  {
    let details: {username: string, productId: string, password: string, confirmPassword: string, email: string, confirmEmail:string} =
      {username: userName, productId: productId, password: password, confirmPassword: confirmPassword, email: email, confirmEmail:confirmEmail};
    return this.http.post<any>(this._baseUrl.getLink("cloud", "register"), details, this.httpJSONOptions).pipe(
      catchError((err: HttpErrorResponse) => throwError(err))
    );
  }

  getVersion(isLocal: boolean): Observable<Version> {
    return this.http.post<Version>(this._baseUrl.getLink(isLocal?"cloud":"utils", "getVersion"), '', this.httpJSONOptions).pipe(
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

  getAccounts():Observable<Account[]>
  {
     return this.http.post<Account[]>(this._baseUrl.getLink('cloud', 'getAccounts'), '', this.httpJSONOptions).pipe(
       tap(),
       catchError((err:HttpErrorResponse) => throwError(err))
     )
  }

  setAccountEnabledStatus(account: Account) : Observable<void>
  {
    let acc:{username: string, accountEnabled: boolean} = {username: account.userName, accountEnabled: account.accountEnabled}
    return this.http.post<void>(this._baseUrl.getLink('cloud', 'setAccountEnabledStatus'), JSON.stringify(acc), this.httpJSONOptions).pipe(
      tap(),
      catchError((err:HttpErrorResponse) => throwError(err))
    )
  }

  adminChangePassword(account:Account, password: string, confirmPassword: string)
  {
    let cpw:{username:string, password: string, confirmPassword:string} = {username: account.userName, password: password, confirmPassword: confirmPassword};
    return this.http.post<void>(this._baseUrl.getLink('cloud', 'adminChangePassword'), JSON.stringify(cpw), this.httpJSONOptions).pipe(
      tap(),
      catchError((err:HttpErrorResponse) => throwError(err))
    )
  }

  adminChangeEmail(account: Account, email: string, confirmEmail: string) {
    let cpw:{username:string, email: string, confirmEmail:string} = {username: account.userName, email: email, confirmEmail: confirmEmail};
    return this.http.post<void>(this._baseUrl.getLink('cloud', 'adminChangeEmail'), JSON.stringify(cpw), this.httpJSONOptions).pipe(
      tap(),
      catchError((err:HttpErrorResponse) => throwError(err))
    );
  }

  sendResetPasswordLink(email: string): Observable<void> {
    let em: {email: string} = {email: email};
    return this.http.post<void>(this._baseUrl.getLink('cloud', 'sendResetPasswordLink'), JSON.stringify(em), this.httpJSONOptions).pipe(
      tap(),
      catchError((err:HttpErrorResponse) => throwError(err))
    );
  }

  getUserAuthorities() : Observable<{authority: string}[]>
  {
    return this.http.post<{authority: string}[]>(this._baseUrl.getLink('cloud', 'getUserAuthorities'), '', this.httpJSONOptions).pipe(
      tap(),
      catchError((err:HttpErrorResponse) => throwError(err))
    );
  }

  sendMessage(message: Message) {
    this._messaging.next(message);
  }

  getMessages(): Observable<Message> {
    return this._messaging.asObservable();
  }

}
