import {Injectable} from '@angular/core';
import {HttpClient, HttpErrorResponse, HttpHeaders} from "@angular/common/http";
import {BaseUrl} from "./BaseUrl/BaseUrl";
import {Observable, Subject, throwError, timer} from "rxjs";
import {catchError, delay, tap, timeout} from "rxjs/operators";
import {CameraParams} from "../cameras/Camera";

export class Temperature {
  temp: string = "";
}

export class Version {
  version: string = "";
}

export class MyIp {
  myIp: string = "";
}

export enum messageType {idleTimeoutStatus, loggedIn}

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
   constructor() {
     super(messageType.loggedIn);
   }
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
  get loggedIn(): boolean {
    return this._loggedIn;
  }

  constructor(private http: HttpClient, private _baseUrl: BaseUrl) {
    // timer(1000).subscribe(
    //   () => this.getTemperature()  // This will set the logged in or out status depending on the result of calling getTemperature
    // );
  }

  login(username: string, password: string): Observable<void> {
    let creds: string = "username=" + username + "&password=" + password;
    return this.http.post<void>(this._baseUrl.getLink("login", "authenticate"), creds, this.httpUrlEncoded).pipe(
      tap(() => {
          this._loggedIn = true;
        },
        reason => {
          this._loggedIn = false;
        }),
      catchError((err: HttpErrorResponse) => throwError(err))
    );
  }

  logoff(): void{
    window.location.href = 'logoff';
    this._loggedIn = false;
  }

  getTemperature(): Observable<Temperature> {
    return this.http.post<Temperature>(this._baseUrl.getLink("utils", "getTemperature"), '', this.httpJSONOptions).pipe(
      tap(() => {
          this._loggedIn = true;
        },
        (reason) => {
          this._loggedIn = false;
        }),
      catchError((err: HttpErrorResponse) => throwError(err))
    );
  }

  getVersion(): Observable<Version> {
    return this.http.post<Version>(this._baseUrl.getLink("utils", "getVersion"), '', this.httpJSONOptions).pipe(
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

  sendMessage(message: Message) {
    this._messaging.next(message);
  }

  getMessages(): Observable<Message> {
    return this._messaging.asObservable();
  }
}
