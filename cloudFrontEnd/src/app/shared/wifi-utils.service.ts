import {Injectable} from '@angular/core';
import {Observable, throwError} from 'rxjs';
import {catchError, tap} from 'rxjs/operators';
import {HttpClient, HttpErrorResponse, HttpHeaders} from '@angular/common/http';
import {BaseUrl} from './BaseUrl/BaseUrl';
import {IPDetails} from './IPDetails';

@Injectable({
  providedIn: 'root'
})
export class WifiUtilsService {
  readonly httpJSONOptions = {
    headers: new HttpHeaders({
      'Content-Type': 'application/json',
      'Authorization': 'my-auth-token'
    })
  };

  constructor(private http: HttpClient, private _baseUrl: BaseUrl) {
  }

  getActiveIPAddresses(): Observable<IPDetails> {
    return this.http.post<IPDetails>(this._baseUrl.getLink('wifiUtils', 'getActiveIPAddresses'), '', this.httpJSONOptions).pipe(
      tap(),
      catchError((err: HttpErrorResponse) => throwError(err))
    );
  }
}
