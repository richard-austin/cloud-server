import { Component, OnInit } from '@angular/core';
import {Account} from "./account";

@Component({
  selector: 'app-nvradmin',
  templateUrl: './account-admin.component.html',
  styleUrls: ['./account-admin.component.scss']
})
export class AccountAdminComponent implements OnInit {
  downloading: boolean = false;
  accounts: Account[] = [];
  displayedColumns: string[] = ['productId', 'userName', 'nvrConnected', 'userConnected'];

  constructor() {
    this.accounts.push(new Account());
  }

  ngOnInit(): void {
  }

}
