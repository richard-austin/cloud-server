import { Component, OnInit } from '@angular/core';
import {Account} from "./account";
import {UtilsService} from "../shared/utils.service";

@Component({
  selector: 'app-nvradmin',
  templateUrl: './account-admin.component.html',
  styleUrls: ['./account-admin.component.scss']
})
export class AccountAdminComponent implements OnInit {
  downloading: boolean = false;
  accounts: Account[] = [];
  displayedColumns: string[] = ['productId', 'userName', 'nvrConnected', 'userConnected'];

  constructor(private utilsService: UtilsService) {
  }

  ngOnInit(): void {
    this.utilsService.getAccounts().subscribe((result) => {
      this.accounts = result;
    },
      reason => {

      });
  }
}
