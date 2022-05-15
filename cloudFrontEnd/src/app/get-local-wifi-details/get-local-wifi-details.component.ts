import {Component, OnInit, ViewChild} from '@angular/core';
import { WifiDetails } from '../shared/BaseUrl/wifi-details';
import { WifiUtilsService } from '../shared/wifi-utils.service';
import {ReportingComponent} from '../reporting/reporting.component';

@Component({
  selector: 'app-get-local-wifi-details',
  templateUrl: './get-local-wifi-details.component.html',
  styleUrls: ['./get-local-wifi-details.component.scss']
})
export class GetLocalWifiDetailsComponent implements OnInit {
  @ViewChild(ReportingComponent) reporting!:ReportingComponent;

  wifiDetails!: WifiDetails[];
  displayedColumns: string[] = ["InUse", "Ssid", "Rate", "Signal", "Channel", "Security", "Mode", "Bssid"];

  constructor(private wifiUtilsService: WifiUtilsService) { }

  ngOnInit(): void {
    this.wifiUtilsService.getLocalWifiDetails().subscribe((result) => {
      this.wifiDetails = result;
      this.wifiDetails = this.wifiDetails.sort((dets1, dets2) => {
        return dets1.in_use ? -1 : parseInt(dets1.signal) < parseInt(dets2.signal) ? 1 : -1;
      })
    },
      reason =>
        this.reporting.errorMessage = reason
    )
  }
}
