import { Injectable } from '@angular/core';
import {HttpClient, HttpErrorResponse, HttpHeaders} from "@angular/common/http";
import {BaseUrl} from "../shared/BaseUrl/BaseUrl";
import {Observable, throwError} from "rxjs";
import {catchError, tap} from "rxjs/operators";

@Injectable({
  providedIn: 'root'
})
export class ChangePasswordService {
  readonly httpJSONOptions = {
    headers: new HttpHeaders({
      'Content-Type': 'application/json',
      'Authorization': 'my-auth-token'
    })
  };

  constructor(private http: HttpClient, private _baseUrl: BaseUrl) { }

  changePassword(oldPassword:string, newPassword:string, confirmNewPassword:string):Observable<void>
  {
    let passwordChange:{oldPassword: string, newPassword:string, confirmNewPassword: string} = {oldPassword: oldPassword, newPassword: newPassword, confirmNewPassword: confirmNewPassword};
    return this.http.post<void>(this._baseUrl.getLink("cloud", "changePassword"), JSON.stringify(passwordChange), this.httpJSONOptions).pipe(
      tap(),
      catchError((err:HttpErrorResponse) => throwError(err))
    );
  }

  resetPassword(newPassword: string, confirmNewPassword: string, uniqueId: string) {
    let passwordChange: {newPassword:string, confirmNewPassword: string, uniqueId: string} = {newPassword: newPassword, confirmNewPassword: confirmNewPassword, uniqueId: uniqueId};
    return this.http.post<void>(this._baseUrl.getLink("cloud", "resetPassword"), JSON.stringify(passwordChange), this.httpJSONOptions).pipe(
      tap(),
      catchError((err:HttpErrorResponse) => throwError(err))
    );
  }
}
