import {Injectable} from '@angular/core';
import {Observable, throwError} from 'rxjs';
import {catchError, map} from 'rxjs/operators';
import {HttpClient, HttpErrorResponse, HttpHeaders} from '@angular/common/http';
import {BaseUrl} from './BaseUrl/BaseUrl';
import {IPDetails} from './IPDetails';
import { WifiDetails } from './BaseUrl/wifi-details';

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

  readonly httpTextOptions = {
    headers: new HttpHeaders({
      'Content-Type': 'text/plain',
      'Authorization': 'my-auth-token',
    }),
    responseType: 'text'
  };

  readonly httpTextHeaders =  new HttpHeaders({
  'Content-Type': 'text/plain',
  'Authorization': 'my-auth-token',
});


constructor(private http: HttpClient, private _baseUrl: BaseUrl) {
  }

  getActiveIPAddresses(): Observable<IPDetails[]> {
    return this.http.post<any>(this._baseUrl.getLink('wifiUtils', 'getActiveIPAddresses'), '', this.httpJSONOptions).pipe(
      map((ocr) => (ocr.responseObject as IPDetails[])),
      catchError((err: HttpErrorResponse) => throwError(err))
    );
  }

  getLocalWifiDetails(): Observable<WifiDetails[]> {
    return this.http.post<WifiDetails[]>(this._baseUrl.getLink('wifiUtils', 'scanWifi'), '', this.httpJSONOptions).pipe(
      catchError((err: HttpErrorResponse) => throwError(err))
    );
  }

  checkWifiStatus():Observable<string>
  {
    // @ts-ignore  Otherwise httpTextOptions gives problems with IntelliJ parsing.
    return this.http.post<string>(this._baseUrl.getLink('wifiUtils', 'checkWifiStatus'), '', this.httpTextOptions).pipe(
      catchError((err: HttpErrorResponse) => throwError(err))
    );
  }
}
