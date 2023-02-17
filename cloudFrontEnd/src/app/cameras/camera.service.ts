import {EventEmitter, Injectable} from '@angular/core';
import {HttpClient, HttpErrorResponse, HttpHeaders} from "@angular/common/http";
import {BaseUrl} from "../shared/BaseUrl/BaseUrl";
import {Observable, Subject, throwError} from "rxjs";
import {catchError, map, tap} from "rxjs/operators";
import {Camera, CameraParamSpec, CameraStream, Stream} from "./Camera";
import {CameraAdminCredentials} from "../credentials-for-camera-access/credentials-for-camera-access.component";
import { NativeDateAdapter } from '@angular/material/core';


/**
 * MotionEvents as received from the server
 */
export class MotionEvents {
  events: string[] = [];
}

export class LocalMotionEvent {
  manifest!: string;
  epoch!: number;
  dateTime!: string;
}

/**
 * LocalMotionEvents: Motion events as delivered to the recordings page
 */
export class LocalMotionEvents {
  events: LocalMotionEvent[] = [];
}

export class DateSlot {
  date!: Date;
  lme: LocalMotionEvents = new LocalMotionEvents();
}

/**
 * CustomDateAdapter: For formatting the date n the datepicker on the recording page
 */
export class CustomDateAdapter extends NativeDateAdapter {
  readonly months: string[] = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

  format(date: Date, displayFormat: any): string {
    const days = date.getDate();
    const months = date.getMonth() + 1;
    const year = date.getFullYear();
    return ("00"+days).slice(-2) + '-' + this.months[months-1] + '-' + year;
  }
}
export enum cameraType {none, sv3c, zxtechMCW5B10X}

@Injectable({
  providedIn: 'root'
})
export class CameraService {
  readonly httpJSONOptions = {
    headers: new HttpHeaders({
      'Content-Type': 'application/json',
      'Authorization': 'my-auth-token'
    })
  };

  readonly httpUploadOptions = {
    headers: new HttpHeaders({
      'Authorization': 'my-auth-token'
    })
  };

  private configUpdates: Subject<void> = new Subject();

  private cameraStreams: CameraStream[] = [];
  private cameras: Camera[] = [];

  errorEmitter: EventEmitter<HttpErrorResponse> = new EventEmitter<HttpErrorResponse>();

  private _cameraParamSpecs: CameraParamSpec[] =
    [new CameraParamSpec(
      cameraType.none,
      "",
      '',
      "Not Listed"),
      new CameraParamSpec(cameraType.sv3c,
        "cmd=getinfrared&cmd=getserverinfo&cmd=getoverlayattr&-region=0&cmd=getserverinfo&cmd=getoverlayattr&-region=1",
        'web/cgi-bin/hi3510/param.cgi',
        "SV3C (General)"),
      new CameraParamSpec(
        cameraType.zxtechMCW5B10X,
        "cmd=getvideoattr&cmd=getlampattrex&cmd=getimageattr&cmd=getinfrared&cmd=getserverinfo&cmd=getoverlayattr&-region=0&cmd=getserverinfo&cmd=getoverlayattr&-region=1",
        'web/cgi-bin/hi3510/param.cgi',
        "ZTech MCW5B10X")]

  get cameraParamSpecs() {
    return this._cameraParamSpecs;
  };

  constructor(private http: HttpClient, private _baseUrl: BaseUrl) {
  }

  initialiseCameras(): void{
    this.cameraStreams = [];
    this.cameras = [];
    this.loadCameraStreams().subscribe(cameraStreams => {
        // Build up a cameraStreams array which excludes the addition guff which comes from
        // having the cameraStreams set up configured in application.yml
        for (const i in cameraStreams) {
          const c = cameraStreams[i];
          this.cameraStreams.push(c);

          if (!this.cameras.find((cs: Camera) => {
            return cs.name === c.camera.name
          }))
            this.cameras.push(c.camera);
        }
      },
      // Error messages would be shown by the nav component
      reason => this.errorEmitter.emit(reason)
    );
  }

  /**
   * configUpdated: Send message to the nav component when the camera configuration has changed.
   */
  configUpdated(): void {
    this.configUpdates.next()
  }

  /**
   * getConfigUpdates: nave component subscribes to this to be notified of camera configuration changes.
   */
  getConfigUpdates(): Observable<any> {
    return this.configUpdates.asObservable();
  }

  /**
   * getCameraStreams: Get details for all cameraStreams
   */
  public getCameraStreams(): CameraStream[] {
    return this.cameraStreams;
  }

  /**
   * getCameras: Returns an array of cameras
   */
  public getCameras(): Camera[] {
    return this.cameras;
  }

  private static convertCamsObjectToMap(cams: Object): Map<string, Camera> {
    let cameras: Map<string, Camera> = new Map<string, Camera>();

    for (let key in cams) {
      // @ts-ignore
      let cam: Camera = cams[key];
      let streams: Map<string, Stream> = new Map<string, Stream>();
      for (let j in cam.streams) {
        // @ts-ignore
        let stream: Stream = cam.streams[j] as Stream;
        stream.selected = stream.defaultOnMultiDisplay;
        streams.set(j, stream);
      }
      cam.streams = streams;  //Make the streams object into a map
      cameras.set(key, cam);
    }
    return cameras;
  }

  private static createCameraStreams(cams: Object): CameraStream[] {
    let cameraStreams: CameraStream[] = [];

    for (let i in cams) {
      // @ts-ignore
      let cam: Camera = cams[i] as Camera;

      if (cam) {
        let streams: Map<string, Stream> = new Map<string, Stream>();
        for (const j in cam.streams) {
          let cs = new CameraStream();
          cs.camera = cam;

          // @ts-ignore   // Ignore "Element implicitly has an 'any' type because type 'Map ' has no index signature"
          cs.stream = cam.streams[j];
          streams.set(j, cs.stream);
          cs.stream.selected = cs.stream.defaultOnMultiDisplay;
          cameraStreams.push(cs);
        }
        // cam.streams = streams;  // Make the streams object into a map
      }
    }
    return cameraStreams;
  }

  /**
   * loadCameras: Get camera set up details from the server
   * @private
   */
  loadCameras(): Observable<Map<string, Camera>> {
    return this.http.post<Map<string, Camera>>(this._baseUrl.getLink("cam", "getCameras"), '', this.httpJSONOptions).pipe(
      map((cams: Object) => {
          return CameraService.convertCamsObjectToMap(cams);
        }
      ),
      catchError((err: HttpErrorResponse) => throwError(err)));
  }

  /**
   * loadCameraStreams: Get camera streams from the server
   */
  loadCameraStreams(): Observable<CameraStream[]> {
    return this.http.post<Map<string, Camera>>(this._baseUrl.getLink("cam", "getCameras"), '', this.httpJSONOptions).pipe(
      map((cams: any) => {
        return CameraService.createCameraStreams(cams);
      }),
      catchError((err: HttpErrorResponse) => throwError(err)));
  }

  updateCameras(camerasJON: string):
    Observable<Map<string, Camera>> {
    let cameras = {camerasJSON: camerasJON};
    return this.http.post<any>(this._baseUrl.getLink("cam", "updateCameras"), JSON.stringify(cameras), this.httpJSONOptions).pipe(
      tap((cams) => {
        this.cameras = [];

        for (const key in cams)
          this.cameras.push(cams[key] as Camera);

        this.cameraStreams = CameraService.createCameraStreams(cams);
      }),
      map(cams => {
        return CameraService.convertCamsObjectToMap(cams);
      })
    );
  }

  discover():Observable<Map<string, Camera>> {
    return this.http.post<any>(this._baseUrl.getLink("onvif", "discover"), '', this.httpJSONOptions).pipe(
      tap((cams) => {
        this.cameras = [];

        for (const key in cams)
          this.cameras.push(cams[key] as Camera);

        this.cameraStreams = CameraService.createCameraStreams(cams);
      }),
      map(cams => {
        return CameraService.convertCamsObjectToMap(cams);
      })
    );
  }

  uploadMaskFile(uploadFile: any): Observable<any> {
    const formData: FormData = new FormData();
    formData.append('maskFile', uploadFile);
    return this.http.post<any>(this._baseUrl.getLink("cam", "uploadMaskFile"), formData, this.httpUploadOptions).pipe(
      tap(),
      catchError((err: HttpErrorResponse) => throwError(err)));
  }

  getSnapshot(url:string): Observable<Array<any>>{
    const formData: FormData = new FormData();
    formData.append('url', url);
    return this.http.post<Array<any>>(this._baseUrl.getLink("onvif", "getSnapshot"), formData, this.httpUploadOptions).pipe(
      tap(),
      catchError((err: HttpErrorResponse) => throwError(err)));
  }

  setCameraAdminCredentials(creds: CameraAdminCredentials): Observable<any> {
    return this.http.post<any>(this._baseUrl.getLink("cam", "setAccessCredentials"), creds, this.httpUploadOptions).pipe(
      tap(),
      catchError((err: HttpErrorResponse) => throwError(err)));
  }
}
