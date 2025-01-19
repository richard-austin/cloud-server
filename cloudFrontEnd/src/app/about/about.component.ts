import {Component, OnInit, ViewChild} from '@angular/core';
import {UtilsService, Version} from "../shared/utils.service";
import {ReportingComponent} from "../reporting/reporting.component";
import {BaseUrl} from "../shared/BaseUrl/BaseUrl";
import { ActivatedRoute, UrlSegment } from '@angular/router';

@Component({
  selector: 'app-about',
  templateUrl: './about.component.html',
  styleUrls: ['./about.component.scss']
})
export class AboutComponent implements OnInit {
  @ViewChild(ReportingComponent) errorReporting!: ReportingComponent;
  version: string = "Unknown";
  isLocal: boolean = false;
  openSourceInfo: string = "Loading...";

  constructor(private utils: UtilsService, private _baseUrl: BaseUrl, private route:ActivatedRoute) {
    route.url.subscribe((u:UrlSegment[]) => {
      this.isLocal=route.snapshot.params.isLocal!==undefined?route.snapshot.params.isLocal:false;
      if(!this.isLocal) {
        this.utils.getOpenSourceInfo().subscribe((info: string) => {
            this.openSourceInfo = info;
          },
          reason => this.errorReporting.errorMessage = reason
        );
      }
    });
  }

  getOnvifUrl() {
    return this._baseUrl.getLink('assets/images', 'onvif.png')
  }

  ngOnInit(): void {
    this.utils.getVersion(this.isLocal).subscribe((version: Version) => {
        this.version = version.version;
      },
      reason => this.errorReporting.errorMessage = reason
      );
  }
}
