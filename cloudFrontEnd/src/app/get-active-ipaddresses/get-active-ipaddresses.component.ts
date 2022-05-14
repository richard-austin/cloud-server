import { Component, OnInit } from '@angular/core';
import { WifiUtilsService } from '../shared/wifi-utils.service';

@Component({
  selector: 'app-get-active-ipaddresses',
  templateUrl: './get-active-ipaddresses.component.html',
  styleUrls: ['./get-active-ipaddresses.component.scss']
})
export class GetActiveIPAddressesComponent implements OnInit {

  constructor(private wifiUtilsService: WifiUtilsService) { }

  ngOnInit(): void {
    this.wifiUtilsService.getActiveIPAddresses().subscribe((result) => {
      let y = result;
    },
      reason => {

      })
  }
}
