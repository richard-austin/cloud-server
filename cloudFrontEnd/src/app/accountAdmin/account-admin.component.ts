import { ViewChild } from '@angular/core';
import { ElementRef } from '@angular/core';
import { Component, OnInit } from '@angular/core';
import { MatCheckboxChange } from '@angular/material/checkbox';
import {UtilsService, Account} from "../shared/utils.service";

declare let SockJS: any;
declare let Stomp: any;
@Component({
  selector: 'app-nvradmin',
  templateUrl: './account-admin.component.html',
  styleUrls: ['./account-admin.component.scss']
})
export class AccountAdminComponent implements OnInit {
  downloading: boolean = false;
  accounts: Account[] = [];
  displayedColumns: string[] = ['productId', 'accountCreated', 'userName', 'nvrConnected', 'usersConnected'];
  @ViewChild('filter') filterEl!: ElementRef<HTMLInputElement>
  private stompClient:any;
  filterText: string = "";
  bOnlyNVROffline: boolean = false;
  bNoAccountOnly: boolean = false;

  constructor(private utilsService: UtilsService) {
    this.initializeWebSocketConnection();
  }

  initializeWebSocketConnection(){
    let serverUrl: string = window.location.origin+"/stomp";

    let ws = new SockJS(serverUrl);
    this.stompClient = Stomp.over(ws);
    this.stompClient.debug = null;
    let that = this;
    this.stompClient.connect({}, function(frame:any) {
      that.stompClient.subscribe("/topic/accountUpdates", (message:any) => {
        if(message.body) {
          let msgObj = JSON.parse(message.body);
          if(msgObj.message === "update") {
            that.getAccounts();
            console.log(message.body);
          }
        }
      });
    });
  }

  getAccounts():void
  {
    this.utilsService.getAccounts().subscribe((result) => {
        this.accounts = result;
      },
      reason => {

      });
  }
  sendMessage(message:any){
    this.stompClient.send("/topic/accountUpdates" , {}, message);
   // $('#input').val('');
  }

  updateFilter() {
    this.filterText = this.filterEl.nativeElement.value;
  }
  onlyNVROffline($event: MatCheckboxChange) {
      this.bOnlyNVROffline = $event.checked
  }

  noAccountOnly($event: MatCheckboxChange) {
      this.bNoAccountOnly = $event.checked
  }

  ngOnInit(): void {
    this.getAccounts();

// Note that at least one consumer has to subscribe to the created subject - otherwise "nexted" values will be just buffered and not sent,
// since no connection was established!

 //   subject.next({message: 'some message'});
// This will send a message to the server once a connection is made. Remember value is serialized with JSON.stringify by default!

 //   subject.complete(); // Closes the connection.

 //   subject.error({code: 4000, reason: 'I think our app just broke!'});

  }

}
